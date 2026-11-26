package de.nicolasmahn.momentum.domain.mo

import de.nicolasmahn.momentum.domain.compatibility.moFeeling
import de.nicolasmahn.momentum.domain.compatibility.contentKey

import de.nicolasmahn.momentum.domain.compatibility.MoAsked

import de.nicolasmahn.momentum.domain.compatibility.kept

import de.nicolasmahn.momentum.domain.compatibility.thriving

import de.nicolasmahn.momentum.domain.compatibility.needs

import de.nicolasmahn.momentum.domain.compatibility.mood

import de.nicolasmahn.momentum.domain.compatibility.MoNeed

import de.nicolasmahn.momentum.domain.trackerRef

import de.nicolasmahn.momentum.domain.reminder.Bedtime
import java.time.LocalDateTime
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MoFeelingTest {
    private val afternoon = LocalDateTime.parse("2026-09-16T16:00:00")

    /** Up at seven: getting ready for bed from 22:00, asleep by 23:00. */
    private val bedtime = Bedtime(LocalTime.of(7, 0), fixedBedtime = null)

    @Test
    fun hisAngerStartsSmallAndClimbsWithLatenessAndNumbers() {
        val open = mood(missing = listOf(MoNeed.Reading, MoNeed.Care, MoNeed.Weight))
        // Anger is one entry of the pool, so this looks at the first day it is drawn.
        fun feeling(overdueBy: Map<MoNeed, Int>, mood: MoMood = open): MoFeeling = (0 until 60)
            .map { moFeeling(mood, MoAsked(overdueBy.mapValues { listOf(it.value) }, overdueBy), afternoon.plusDays(it.toLong()), bedtime) }
            .first { it.occasion == MoOccasion.Angry }

        assertEquals(MoFeeling(MoOccasion.Angry, MoIntensity.Mild), feeling(mapOf(MoNeed.Reading to 45)))
        assertEquals(MoFeeling(MoOccasion.Angry, MoIntensity.Medium), feeling(mapOf(MoNeed.Reading to 150)))
        assertEquals(MoFeeling(MoOccasion.Angry, MoIntensity.Medium), feeling(mapOf(MoNeed.Reading to 20, MoNeed.Care to 20)))
        assertEquals(MoFeeling(MoOccasion.Angry, MoIntensity.Strong), feeling(mapOf(MoNeed.Reading to 320)))
        assertEquals(
            MoFeeling(MoOccasion.Angry, MoIntensity.Strong),
            feeling(mapOf(MoNeed.Reading to 5, MoNeed.Care to 5, MoNeed.Weight to 5)),
        )
        // A task he has been let down on all week needs no waiting to see red.
        assertEquals(
            MoFeeling(MoOccasion.Angry, MoIntensity.Strong),
            feeling(mapOf(MoNeed.Reading to 5), open.copy(trackerNeglect = mapOf(MoNeed.Reading.view.trackerRef to MoNeglect.Mild))),
        )
    }

    @Test
    fun aWaitingTaskMakesHimMildlyOffThenMoreSoAndNeglectStartsHimHigher() {
        val hungry = mood(missing = listOf(MoNeed.Eating))
        fun intensityWhenHungry(mood: MoMood, waited: Int = 20): MoIntensity? = (0 until 60)
            .map { moFeeling(mood, MoAsked(mapOf(MoNeed.Eating to listOf(waited))), afternoon.plusDays(it.toLong()), bedtime) }
            .first { it.occasion == MoOccasion.Hungry }.intensity

        assertEquals(MoIntensity.Mild, intensityWhenHungry(hungry))
        assertEquals(MoIntensity.Medium, intensityWhenHungry(hungry, waited = 150))
        assertEquals(MoIntensity.Strong, intensityWhenHungry(hungry, waited = 320))
        assertEquals(MoIntensity.Medium, intensityWhenHungry(hungry.copy(trackerNeglect = mapOf(MoNeed.Eating.view.trackerRef to MoNeglect.Mild))))
        assertEquals(MoIntensity.Strong, intensityWhenHungry(hungry.copy(trackerNeglect = mapOf(MoNeed.Eating.view.trackerRef to MoNeglect.Dire))))
    }

    @Test
    fun joyClimbsFromAnOrdinaryDayToARoundStreak() {
        fun joy(streakDays: Int, thriving: Set<MoNeed> = emptySet()): Set<MoIntensity?> {
            val done = mood(kept = listOf(MoNeed.Reading), streakDays = streakDays).copy(thrivingTrackers = thriving.mapTo(linkedSetOf()) { it.view.trackerRef })
            return (0 until 80).map { moFeeling(done, MoAsked(), afternoon.plusDays(it.toLong()), bedtime) }
                .filter { it.occasion == MoOccasion.Happy || it.occasion == MoOccasion.Proud }.map { it.intensity }.toSet()
        }

        assertEquals(setOf<MoIntensity?>(MoIntensity.Mild), joy(streakDays = 3))
        assertEquals(setOf<MoIntensity?>(MoIntensity.Medium), joy(streakDays = 12))
        assertEquals(setOf<MoIntensity?>(MoIntensity.Medium), joy(streakDays = 2, thriving = setOf(MoNeed.Care)))
        assertEquals(setOf<MoIntensity?>(MoIntensity.Strong), joy(streakDays = 30))
    }

    @Test
    fun sleepinessClimbsFromAYawnToBeingInBed() {
        val done = mood(kept = listOf(MoNeed.Reading))
        fun at(time: String) = moFeeling(done, MoAsked(), LocalDateTime.of(afternoon.toLocalDate(), LocalTime.parse(time)), bedtime)

        assertEquals(MoFeeling(MoOccasion.Sleepy, MoIntensity.Mild), at("22:10"))
        assertEquals(MoFeeling(MoOccasion.Sleepy, MoIntensity.Medium), at("22:40"))
        assertEquals(MoFeeling(MoOccasion.Sleepy, MoIntensity.Strong), at("23:00"))
        assertEquals(MoFeeling(MoOccasion.Sleepy, MoIntensity.Strong), at("04:00"))
    }

    @Test
    fun occasionsWithoutDegreesHaveNoIntensity() {
        assertEquals(null, moFeeling(mood().copy(state = MoState.Husk), MoAsked(), afternoon, bedtime).intensity)
        assertEquals(null, moFeeling(mood().copy(state = MoState.Cold), MoAsked(), afternoon, bedtime).intensity)
    }

    @Test
    fun theStreaksWorthAFussAreTheFirstWeeksAndTheNumbersPeopleBragAbout() {
        assertTrue(listOf(7, 14, 21, 30, 50, 100, 365, 400, 1000).all { it.isRoundStreak() })
        assertTrue(listOf(0, 1, 6, 8, 29, 31, 99, 101, 366, 450).none { it.isRoundStreak() })
    }

    @Test
    fun aRoundStreakIsAnEventOnlyOnceTodayIsComplete() {
        val reached = mood(kept = listOf(MoNeed.Reading), streakDays = 50)
        val notYet = mood(missing = listOf(MoNeed.Care), kept = listOf(MoNeed.Reading), streakDays = 50)

        assertEquals(listOf(MoEvent.StreakMilestone(50)), moEvents(reached))
        // A round number that is a level has the level's own screen instead.
        assertEquals(emptyList<MoEvent>(), moEvents(mood(kept = listOf(MoNeed.Reading), streakDays = 30)))
        assertEquals(emptyList<MoEvent>(), moEvents(notYet))
        assertEquals(emptyList<MoEvent>(), moEvents(mood(kept = listOf(MoNeed.Reading), streakDays = 31)))
    }

    @Test
    fun aPerfectWeekIsAnEventOnTheSundayThatClosesIt() {
        val sunday = mood(kept = listOf(MoNeed.Care), streakDays = 9).withWeek(List(7) { MoDayMark.Perfect })
        val saturday = mood(kept = listOf(MoNeed.Care), streakDays = 8)
            .withWeek(List(6) { MoDayMark.Perfect } + MoDayMark.Upcoming)
        val withAFrozenDay = sunday.withWeek(listOf(MoDayMark.Frozen) + List(6) { MoDayMark.Perfect })

        assertEquals(listOf(MoEvent.PerfectWeek), moEvents(sunday))
        assertEquals(emptyList<MoEvent>(), moEvents(saturday))
        assertEquals(emptyList<MoEvent>(), moEvents(withAFrozenDay))
    }

    @Test
    fun finishingTodayAfterABadYesterdayIsAComeback() {
        val week = listOf(MoDayMark.Perfect, MoDayMark.Missed, MoDayMark.Perfect) + List(4) { MoDayMark.Upcoming }
        val back = mood(kept = listOf(MoNeed.Care), streakDays = 1).withWeek(week)
        val stillOpen = mood(missing = listOf(MoNeed.Care), streakDays = 0)
            .withWeek(listOf(MoDayMark.Perfect, MoDayMark.Missed, MoDayMark.Open) + List(4) { MoDayMark.Upcoming })

        assertEquals(listOf(MoEvent.Comeback), moEvents(back))
        assertEquals(emptyList<MoEvent>(), moEvents(stillOpen))
    }

    @Test
    fun reachingAWeeksGoalTodayIsAnEventEvenOnAnUnfinishedDay() {
        val fifthActiveDay = mood(missing = listOf(MoNeed.Care), kept = listOf(MoNeed.Activity)).copy(
            trackerWeekProgress = mapOf(MoNeed.Activity.view.trackerRef to MoWeekProgress(done = 5, expected = 5), MoNeed.Care.view.trackerRef to MoWeekProgress(4, 7)),
        )
        val reachedYesterday = fifthActiveDay.copy(keptTrackersToday = emptyList())

        assertEquals(listOf(MoEvent.WeekGoalReached(MoNeed.Activity.view.trackerRef)), moEvents(fifthActiveDay))
        assertEquals(emptyList<MoEvent>(), moEvents(reachedYesterday))
    }

    private fun MoMood.withWeek(week: List<MoDayMark>) = copy(streak = streak.copy(week = week))

    /** The one place this file builds a mood, so a new field is a one-line fix. */
    private fun mood(
        missing: List<MoNeed> = emptyList(),
        kept: List<MoNeed> = emptyList(),
        streakDays: Int = 0,
    ) = MoMood(
        state = MoState.Steady,
        dueToday = missing.size + kept.size,
        daysCold = 0,
        streak = MoStreak(
            days = streakDays,
            freezes = 1,
            freezeCapacity = 3,
            week = listOf(MoDayMark.Open) + List(6) { MoDayMark.Upcoming },
            weekCanEarnFreeze = true,
        ),
        trackerContent = MoNeed.entries.associate { it.view.trackerRef to it.contentKey(null) },
        missingTrackersToday = missing.map { it.view.trackerRef },
        keptTrackersToday = kept.map { it.view.trackerRef },
        latestTrackerToday = kept.lastOrNull()?.view?.trackerRef,
        weeklyGoalTrackers = MoNeed.entries.filter { it.hasWeekGoal }.mapTo(LinkedHashSet()) { it.view.trackerRef },
    )
}

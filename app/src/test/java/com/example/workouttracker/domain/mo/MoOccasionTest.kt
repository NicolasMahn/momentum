package com.example.workouttracker.domain.mo

import com.example.workouttracker.domain.compatibility.moOccasion
import com.example.workouttracker.domain.compatibility.contentKey

import com.example.workouttracker.domain.compatibility.MoAsked

import com.example.workouttracker.domain.compatibility.kept

import com.example.workouttracker.domain.compatibility.needs

import com.example.workouttracker.domain.compatibility.mood

import com.example.workouttracker.domain.compatibility.MoNeed

import com.example.workouttracker.domain.trackerRef

import com.example.workouttracker.domain.reminder.Bedtime
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MoOccasionTest {
    private val noon = LocalDateTime.parse("2026-09-16T12:00:00")

    /** Up at seven: getting ready for bed from 22:00, asleep by 23:00. */
    private val bedtime = Bedtime(LocalTime.of(7, 0), fixedBedtime = null)

    private val readingAsked = MoAsked(waited = mapOf(MoNeed.Reading to listOf(20)))
    private val readingLate = MoAsked(mapOf(MoNeed.Reading to listOf(90)), mapOf(MoNeed.Reading to 90))
    private val careAsked = MoAsked(waited = mapOf(MoNeed.Care to listOf(20)))

    @Test
    fun hisStateDecidesTheOccasionOutrightWhateverIsOverdue() {
        val open = mood(missing = listOf(MoNeed.Reading))

        assertEquals(MoOccasion.Out, moOccasion(open.copy(state = MoState.Husk), readingLate, noon, bedtime))
        assertEquals(MoOccasion.Cold, moOccasion(open.copy(state = MoState.Cold), readingLate, noon, bedtime))
    }

    @Test
    fun anOverdueTaskMakesHimAngryAndNothingElse() {
        val open = mood(missing = listOf(MoNeed.Reading))
        fun drawn(asked: MoAsked) = (0 until 100).map { moOccasion(open, asked, noon.plusDays(it.toLong()), bedtime) }.toSet()

        assertEquals(setOf(MoOccasion.Angry), drawn(readingLate))
        assertFalse(MoOccasion.Angry in drawn(readingAsked))
    }

    /**
     * Eating asked for at 08:15 and still open: on 2026-10-07 at 12:00 he held a salt shaker, the
     * Hungry act, instead of fuming. Overdue, he is angry in every part of every day he is awake,
     * the other tasks done or not.
     */
    @Test
    fun anOverdueTaskMakesHimAngryInEveryPartOfTheDay() {
        val eatingOpen = mood(missing = listOf(MoNeed.Eating), kept = listOf(MoNeed.Weight, MoNeed.Supplements))
        val days = (0 until 60).map { LocalDateTime.parse("2026-10-07T00:00:00").plusDays(it.toLong()) }

        listOf(10, 12, 16, 20).forEach { hour ->
            val drawn = days.map { day ->
                val now = day.withHour(hour)
                val waited = Duration.between(day.withHour(8).withMinute(15), now).toMinutes().toInt()
                moOccasion(eatingOpen, MoAsked(mapOf(MoNeed.Eating to listOf(waited)), mapOf(MoNeed.Eating to waited)), now, bedtime)
            }.toSet()
            assertEquals("at $hour:00", setOf(MoOccasion.Angry), drawn)
        }
    }

    @Test
    fun aTaskIsOnlyActedOutOnceItsReminderHasAskedForIt() {
        // Care's morning is done and its evening is still to come, as on most afternoons.
        val careOpen = mood(missing = listOf(MoNeed.Care, MoNeed.Reading))
        fun drawn(asked: MoAsked) = (0 until 200).map { moOccasion(careOpen, asked, noon.plusDays(it.toLong()), bedtime) }.toSet()

        assertFalse(MoOccasion.Stinky in drawn(readingAsked))
        assertTrue(MoOccasion.Stinky in drawn(readingAsked.copy(waited = readingAsked.waited + (MoNeed.Care to listOf(20)))))
    }

    @Test
    fun aFreezeSpentYesterdayKeepsHimFrozenUntilTheFirstRecord() {
        val frozen = mood(missing = listOf(MoNeed.Care), frozenYesterday = listOf(MoNeed.Care))
        val thawed = frozen.copy(latestTrackerToday = MoNeed.Reading.view.trackerRef, keptTrackersToday = listOf(MoNeed.Reading.view.trackerRef))
        val careLate = MoAsked(mapOf(MoNeed.Care to listOf(90)), mapOf(MoNeed.Care to 90))

        assertEquals(MoOccasion.Frozen, moOccasion(frozen, careLate, noon, bedtime))
        assertFalse(moOccasion(thawed, careLate, noon, bedtime) == MoOccasion.Frozen)
    }

    @Test
    fun theIceComesBeforeWhateverIsInsideIt() {
        val frozen = mood(missing = listOf(MoNeed.Care), frozenYesterday = listOf(MoNeed.Care))

        assertEquals(MoOccasion.Frozen, moOccasion(frozen.copy(state = MoState.Husk), careAsked, noon, bedtime))
        assertEquals(MoOccasion.Frozen, moOccasion(frozen.copy(state = MoState.Cold), careAsked, noon, bedtime))
    }

    @Test
    fun heIsScaredOnlyLateWithSomethingOpenAStreakToLoseAndNoFreeze() {
        val late = noon.withHour(22)
        val atRisk = mood(missing = listOf(MoNeed.Care), streakDays = 12, freezes = 0)

        assertEquals(MoOccasion.Scared, moOccasion(atRisk, careAsked, late, bedtime))
        assertFalse(moOccasion(atRisk, careAsked, noon, bedtime) == MoOccasion.Scared)
        assertFalse(moOccasion(atRisk.withFreezes(1), careAsked, late, bedtime) == MoOccasion.Scared)
        assertFalse(moOccasion(mood(missing = listOf(MoNeed.Care), streakDays = 0), careAsked, late, bedtime) == MoOccasion.Scared)
    }

    @Test
    fun aTaskNotAskedForYetCannotScareHim() {
        val careTonight = mood(missing = listOf(MoNeed.Care), streakDays = 4, freezes = 0)

        assertFalse(moOccasion(careTonight, MoAsked(), noon.withHour(21).withMinute(44), bedtime) == MoOccasion.Scared)
    }

    @Test
    fun theSameMomentAlwaysDrawsTheSameOccasionAndThePartsOfADayCanDiffer() {
        val hungryAndUnread = mood(missing = listOf(MoNeed.Eating, MoNeed.Reading))
        val asked = MoAsked(waited = mapOf(MoNeed.Eating to listOf(20), MoNeed.Reading to listOf(20)))

        assertEquals(moOccasion(hungryAndUnread, asked, noon, bedtime), moOccasion(hungryAndUnread, asked, noon.plusMinutes(50), bedtime))
        val overAMonth = (0 until 30).flatMap { day -> listOf(8, 12, 16, 20).map { noon.plusDays(day.toLong()).withHour(it) } }
            .map { moOccasion(hungryAndUnread, asked, it, bedtime) }
        assertTrue("a month should bring variety, saw ${overAMonth.toSet()}", overAMonth.toSet().size >= 4)
        assertTrue(MoOccasion.Hungry in overAMonth && MoOccasion.Dumb in overAMonth)
    }

    @Test
    fun onlyWhatAppliesCanBeDrawn() {
        val allDone = mood(kept = listOf(MoNeed.Reading, MoNeed.Care))
        val drawn = (0 until 200).map { moOccasion(allDone, MoAsked(), noon.plusDays(it.toLong()), bedtime) }.toSet()

        assertTrue(MoOccasion.Happy in drawn)
        val needs = setOf(MoOccasion.Hungry, MoOccasion.Dumb, MoOccasion.Lazy, MoOccasion.Stinky, MoOccasion.Sickly, MoOccasion.Unweighed)
        assertEquals(emptySet<MoOccasion>(), drawn intersect needs)
        assertFalse(MoOccasion.Sleepy in drawn)
    }

    @Test
    fun theCalendarBringsItsOwnOccasions() {
        val quiet = mood()
        fun drawnOn(date: String) = (6..22).map { moOccasion(quiet, MoAsked(), LocalDateTime.parse("${date}T00:00:00").withHour(it), bedtime) }.toSet()
        val overDecember = (1..26).flatMap { drawnOn("2026-12-%02d".format(it)) }.toSet()

        assertTrue(MoOccasion.Festive in overDecember)
        assertFalse(MoOccasion.Festive in drawnOn("2026-09-16"))
    }

    @Test
    fun pastTheHourToBeAsleepHeIsInBedWhateverIsStillOpen() {
        val open = mood(missing = listOf(MoNeed.Reading), streakDays = 12, freezes = 1)

        listOf("23:00", "23:59", "00:00", "03:30", "06:59").forEach { time ->
            val now = LocalDateTime.of(noon.toLocalDate(), LocalTime.parse(time))
            assertEquals(time, MoOccasion.Sleepy, moOccasion(open, readingLate, now, bedtime))
        }
        assertFalse(moOccasion(open, readingLate, noon.withHour(7), bedtime) == MoOccasion.Sleepy)
    }

    @Test
    fun aStreakThatDiesTonightKeepsHimUpUntilItIsSaved() {
        val atRisk = mood(missing = listOf(MoNeed.Care), streakDays = 12, freezes = 0)
        val saved = mood(kept = listOf(MoNeed.Care), streakDays = 12, freezes = 0)
        val late = noon.withHour(23).withMinute(30)

        assertEquals(MoOccasion.Scared, moOccasion(atRisk, careAsked, late, bedtime))
        assertEquals(MoOccasion.Sleepy, moOccasion(saved, MoAsked(), late, bedtime))
    }

    @Test
    fun gettingReadyForBedMakesHimSleepyOnceNothingAskedForIsMissing() {
        val done = mood(kept = listOf(MoNeed.Reading))
        val open = mood(missing = listOf(MoNeed.Reading), freezes = 1)
        val windingDown = noon.withHour(22).withMinute(15)

        assertEquals(MoOccasion.Sleepy, moOccasion(done, MoAsked(), windingDown, bedtime))
        assertEquals(MoOccasion.Sleepy, moOccasion(open, MoAsked(), windingDown, bedtime))
        assertFalse(moOccasion(open, readingAsked, windingDown, bedtime) == MoOccasion.Sleepy)
        assertFalse(moOccasion(done, MoAsked(), noon.withHour(21).withMinute(59), bedtime) == MoOccasion.Sleepy)
    }

    @Test
    fun aLaterAlarmMovesTheWholeNight() {
        val done = mood(kept = listOf(MoNeed.Reading))
        val upAtTen = Bedtime(LocalTime.of(10, 0), fixedBedtime = null)

        assertFalse(moOccasion(done, MoAsked(), noon.withHour(23), upAtTen) == MoOccasion.Sleepy)
        assertEquals(MoOccasion.Sleepy, moOccasion(done, MoAsked(), noon.withHour(1), upAtTen))
        assertEquals(MoOccasion.Sleepy, moOccasion(done, MoAsked(), noon.withHour(9), upAtTen))
    }

    private fun MoMood.withFreezes(count: Int) = copy(streak = streak.copy(freezes = count))

    /** The one place this file builds a mood, so a new field is a one-line fix. */
    private fun mood(
        missing: List<MoNeed> = emptyList(),
        kept: List<MoNeed> = emptyList(),
        streakDays: Int = 0,
        freezes: Int = 0,
        frozenYesterday: List<MoNeed> = emptyList(),
    ) = MoMood(
        state = MoState.Steady,
        missingTrackersToday = missing.map { it.view.trackerRef },
        freeTrackersToday = emptyList(),
        keptTrackersToday = kept.map { it.view.trackerRef },
        latestTrackerToday = kept.lastOrNull()?.view?.trackerRef,
        dueToday = missing.size + kept.size,
        daysCold = 0,
        streak = MoStreak(
            days = streakDays,
            freezes = freezes,
            freezeCapacity = 3,
            week = listOf(MoDayMark.Open) + List(6) { MoDayMark.Upcoming },
            weekCanEarnFreeze = true,
            frozenTrackersYesterday = frozenYesterday.map { it.view.trackerRef },
        ),
        trackerContent = MoNeed.entries.associate { it.view.trackerRef to it.contentKey(null) },
    )
}

package com.example.workouttracker.domain.mo

import com.example.workouttracker.domain.compatibility.kept

import com.example.workouttracker.domain.compatibility.needs

import com.example.workouttracker.domain.compatibility.mood

import com.example.workouttracker.domain.compatibility.MoNeed

import com.example.workouttracker.domain.settings.showingOnly
import com.example.workouttracker.app.featuresLoggedOn
import com.example.workouttracker.app.substanceProgressOn
import com.example.workouttracker.domain.AppState
import com.example.workouttracker.domain.SubstancesTrackerRef
import com.example.workouttracker.domain.DayProgress
import com.example.workouttracker.domain.checklist.WhenAway
import com.example.workouttracker.domain.holiday.DayRange
import com.example.workouttracker.domain.holiday.HolidaySchedule
import com.example.workouttracker.domain.measurement.asMeasurementLogs
import com.example.workouttracker.domain.settings.DashboardFeature
import com.example.workouttracker.domain.settings.FeatureVisibilitySettings
import com.example.workouttracker.domain.substance.SubstanceKind
import com.example.workouttracker.domain.substance.SubstanceLog
import com.example.workouttracker.domain.substance.SubstanceNoneDay
import com.example.workouttracker.domain.substance.SubstanceSettings
import com.example.workouttracker.domain.substance.awayRuleFrom
import com.example.workouttracker.domain.substance.joiningStreak
import com.example.workouttracker.domain.substance.leavingStreak
import com.example.workouttracker.domain.measurement.MeasurementRecord
import com.example.workouttracker.domain.mo.compatibility.InheritedMoEvaluation
import com.example.workouttracker.domain.mo.compatibility.withInheritedTrackerConfigurations
import java.time.LocalDate
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Substances in the streak: only the act of logging is ever rewarded. A day with a use and a day
 * with "none" are judged identically everywhere, taking a substance can never cost the streak, and
 * nothing changes until the switch is on.
 */
class SubstanceStreakTest {
    private val monday = LocalDate.parse("2026-09-28")
    private val joined = SubstanceSettings.Legacy.joiningStreak(monday)
    private fun visible(vararg needs: MoNeed, substances: Boolean = true) = showingOnly(*needs, features = if (substances) setOf(DashboardFeature.Substances) else emptySet())
    private val substancesOnly = visible()

    // Substances is about yesterday: each helper takes the day that judges a record and dates the
    // record the day before, the day it is about.
    private fun use(day: LocalDate, substance: SubstanceKind = SubstanceKind.CANNABIS, amount: Double? = null) =
        SubstanceLog("use-$day-${substance.id}", substance.id, day.minusDays(1).toString(), 1L, amount, amount?.let { substance.unit })

    private fun none(day: LocalDate) = SubstanceNoneDay(day.minusDays(1).toString(), 1L)

    /**
     * The acceptance check that matters most: generated histories that differ only in the content
     * of the logs, uses of any substance and amount against "none" against a mix, give identical
     * streaks, tokens, Home badges and Mo moods on every day, sick and away days included.
     */
    @Test fun aDayWithAUseAndADayWithNoneAreJudgedIdenticallyEverywhere() {
        val random = Random(21)
        val days = (0 until 42).map { monday.plusDays(it.toLong()) }
        repeat(40) {
            val logged = days.filter { random.nextBoolean() }
            val weights = days.filter { random.nextInt(3) > 0 }.map { MeasurementRecord(it.toString(), 1L, 80.0) }
            val away = DayRange(days[random.nextInt(20)], days[20 + random.nextInt(8)])
            val sick = DayRange(days[28 + random.nextInt(6)], days[34 + random.nextInt(6)])
            val daysOff = HolidaySchedule(listOf(away)).withSickDays(sick)
            val visibility = visible(MoNeed.Weight)
            val settings = if (random.nextBoolean()) joined else joined.awayRuleFrom(monday, WhenAway.Excused)
            val uses = AppState(
                substances = settings,
                measurementLogs = weights.asMeasurementLogs("weight"),
                substanceLogs = logged.map { use(it, SubstanceKind.entries.random(random), if (random.nextBoolean()) random.nextDouble(0.5, 9.0) else null) },
            ).withInheritedTrackerConfigurations()
            val nones = uses.copy(substanceLogs = emptyList(), substanceNoneDays = logged.map(::none))
            val mixed = uses.copy(
                substanceLogs = logged.filterIndexed { index, _ -> index % 2 == 0 }.map { use(it, SubstanceKind.GHB) },
                substanceNoneDays = logged.filterIndexed { index, _ -> index % 2 == 1 }.map(::none),
            )
            days.forEach { today ->
                val expected = habitStreak(uses, visibility, today, daysOff)
                listOf(nones, mixed).forEach { other ->
                    val streak = habitStreak(other, visibility, today, daysOff)
                    assertEquals("streak on $today", expected, streak)
                    assertEquals("mood on $today", moMood(uses, visibility, today, daysOff).outcome(), moMood(other, visibility, today, daysOff).outcome())
                    assertEquals("badge on $today", substanceProgressOn(expected), substanceProgressOn(streak))
                    assertEquals("home on $today", featuresLoggedOn(uses, today.toString(), daysOff), featuresLoggedOn(other, today.toString(), daysOff))
                }
            }
        }
    }

    /** With the switch off, records change nothing anywhere: the streak, Mo, Home and the report read as if there were none. */
    @Test fun withTheSwitchOffRecordsChangeNothingAnywhere() {
        val days = (0 until 21).map { monday.plusDays(it.toLong()) }
        val quiet = AppState(
            substances = SubstanceSettings.Legacy,
            measurementLogs = days.filterIndexed { index, _ -> index % 3 != 0 }.map { MeasurementRecord(it.toString(), 1L, 80.0) }.asMeasurementLogs("weight"),
            substanceLogs = days.filterIndexed { index, _ -> index % 2 == 0 }.map { use(it, amount = 2.0) },
            substanceNoneDays = days.filterIndexed { index, _ -> index % 4 == 1 }.map(::none),
        ).withInheritedTrackerConfigurations()
        val empty = quiet.copy(substanceLogs = emptyList(), substanceNoneDays = emptyList())
        days.forEach { today ->
            val evaluation = InheritedMoEvaluation(quiet, FeatureVisibilitySettings(), today)
            val streak = evaluation.current.streak
            assertEquals(habitStreak(empty, FeatureVisibilitySettings(), today), streak)
            assertEquals(moMood(empty, FeatureVisibilitySettings(), today).outcome(), moMood(quiet, FeatureVisibilitySettings(), today).outcome())
            assertEquals(featuresLoggedOn(empty, today.toString(), HolidaySchedule()), featuresLoggedOn(quiet, today.toString(), HolidaySchedule()))
            assertNull(streak.standings[SubstancesTrackerRef])
            assertNull(substanceProgressOn(streak))
            assertNull(evaluation.streak.substances)
        }
    }

    /** Only a day left unlogged is a gap; a use, whatever it was, keeps the day as "none" does, and the bank fills with logged days. */
    @Test fun onlyAnUnloggedDayIsAGapAndTheBankFillsWithLoggedDays() {
        val friday = monday.plusDays(4)
        val state = AppState(
            substances = joined,
            substanceLogs = listOf(use(monday), use(monday.plusDays(3), SubstanceKind.ALCOHOL, 9.0)),
            substanceNoneDays = listOf(none(monday.plusDays(1))),
        )

        val streak = habitStreak(state, substancesOnly, friday)
        assertEquals(listOf(MoDayMark.Perfect, MoDayMark.Perfect, MoDayMark.Protected, MoDayMark.Perfect, MoDayMark.Open, MoDayMark.Upcoming, MoDayMark.Upcoming), streak.week)
        assertEquals(3, streak.days)
        val standing = requireNotNull(streak.standings[SubstancesTrackerRef])
        // Wednesday spent the starting pass; three logged days count toward the first token of seven.
        assertEquals(MoStreakStanding(passes = 0, passCapacity = 3, nextToken = MoTokenProgress(3, 7), openToday = true, keptToday = false), standing)
        assertEquals(DayProgress(done = 0, parts = 1), substanceProgressOn(streak))

        val fridayLogged = habitStreak(state.copy(substanceNoneDays = state.substanceNoneDays + none(friday)), substancesOnly, friday)
        assertEquals(4, fridayLogged.days)
        assertEquals(MoStreakStanding(passes = 0, passCapacity = 3, nextToken = MoTokenProgress(4, 7), openToday = false, keptToday = true), fridayLogged.standings[SubstancesTrackerRef])
        assertEquals(DayProgress(done = 1, parts = 1), substanceProgressOn(fridayLogged))
    }

    /** A sick day asks nothing; an away day stays due unless the person said it is not, and then costs nothing. */
    @Test fun aSickDayAsksNothingAndAnAwayDayAsksWhatThePersonSaid() {
        val tuesday = monday.plusDays(1)
        val wednesday = monday.plusDays(2)
        val state = AppState(substances = joined, substanceNoneDays = listOf(none(monday)))

        val sick = habitStreak(state, substancesOnly, wednesday, HolidaySchedule().withSickDays(DayRange(tuesday, tuesday)))
        assertEquals(MoDayMark.Sick, sick.week[1])
        assertEquals(1, requireNotNull(sick.standings[SubstancesTrackerRef]).passes)

        // Away is read on the day a record is about: Monday's, which Tuesday judges.
        val awayMonday = HolidaySchedule(listOf(DayRange(monday, monday)))
        val due = habitStreak(state, substancesOnly, wednesday, awayMonday)
        assertEquals(MoDayMark.Protected, due.week[1])
        assertEquals(0, requireNotNull(due.standings[SubstancesTrackerRef]).passes)

        val excused = habitStreak(state.copy(substances = joined.awayRuleFrom(monday, WhenAway.Excused)), substancesOnly, wednesday, awayMonday)
        assertEquals(MoDayMark.Perfect, excused.week[1])
        assertEquals(1, requireNotNull(excused.standings[SubstancesTrackerRef]).passes)

        // Sick today: nothing is asked, so Home shows no badge, kept or not.
        val sickToday = HolidaySchedule().withSickDays(DayRange(wednesday, wednesday))
        assertNull(substanceProgressOn(habitStreak(state, substancesOnly, wednesday, sickToday)))
        assertNull(substanceProgressOn(habitStreak(state.copy(substanceNoneDays = state.substanceNoneDays + none(wednesday)), substancesOnly, wednesday, sickToday)))
    }

    /**
     * An excused away day counts for nothing when it is judged, so its record, made today, counts
     * for nothing either: no coin, no progress. Friday judges Thursday's record, so Thursday is away.
     */
    @Test fun aRecordOfAnExcusedAwayDayCountsForNothingWhenItIsJudged() {
        val excusedAway = joined.awayRuleFrom(monday, WhenAway.Excused)
        val awayThursday = HolidaySchedule(listOf(DayRange(monday.plusDays(3), monday.plusDays(3))))
        val fourLogged = (0L until 4L).map { none(monday.plusDays(it)) }
        val friday = monday.plusDays(4)

        val unlogged = habitStreak(AppState(substances = excusedAway, substanceNoneDays = fourLogged), substancesOnly, friday, awayThursday)
        val logged = habitStreak(AppState(substances = excusedAway, substanceNoneDays = fourLogged + none(friday)), substancesOnly, friday, awayThursday)
        listOf(unlogged, logged).forEach { streak ->
            val standing = requireNotNull(streak.standings[SubstancesTrackerRef])
            assertFalse(standing.keptToday)
            assertFalse(standing.openToday)
            assertEquals(MoTokenProgress(4, 7), standing.nextToken)
            assertNull(substanceProgressOn(streak))
        }
        // Tomorrow still counts four days toward the token: Thursday stays excused.
        assertEquals(MoTokenProgress(4, 7), requireNotNull(habitStreak(AppState(substances = excusedAway, substanceNoneDays = fourLogged + none(friday)), substancesOnly, friday.plusDays(1), awayThursday).standings[SubstancesTrackerRef]).nextToken)
    }

    /** Joining judges from the join day, leaving keeps the days judged and takes the bank out of sight, as a checklist's does. */
    @Test fun joiningJudgesFromTheJoinDayAndLeavingKeepsTheDaysJudged() {
        val wednesday = monday.plusDays(2)
        val friday = monday.plusDays(4)
        val state = AppState(substances = SubstanceSettings.Legacy.joiningStreak(wednesday), substanceNoneDays = listOf(none(wednesday)))

        val joinedToday = habitStreak(state, substancesOnly, wednesday)
        assertEquals(listOf(MoDayMark.NothingDue, MoDayMark.NothingDue, MoDayMark.Perfect, MoDayMark.Upcoming, MoDayMark.Upcoming, MoDayMark.Upcoming, MoDayMark.Upcoming), joinedToday.week)
        assertTrue(requireNotNull(joinedToday.standings[SubstancesTrackerRef]).keptToday)

        val left = habitStreak(state.copy(substances = state.substances.leavingStreak(friday)), substancesOnly, friday)
        assertNull(left.standings[SubstancesTrackerRef])
        assertNull(substanceProgressOn(left))
        assertEquals(listOf(MoDayMark.NothingDue, MoDayMark.NothingDue, MoDayMark.Perfect, MoDayMark.Protected, MoDayMark.NothingDue, MoDayMark.Upcoming, MoDayMark.Upcoming), left.week)

        // Hidden, Substances is never due and keeps its bank out of sight.
        assertNull(habitStreak(state, visible(MoNeed.Weight, substances = false), friday).standings[SubstancesTrackerRef])
        assertEquals(MoDayMark.NothingDue, habitStreak(state, visible(MoNeed.Weight, substances = false), friday).week[3])
    }
}

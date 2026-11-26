package de.nicolasmahn.momentum.domain.mo

import de.nicolasmahn.momentum.domain.AppState
import de.nicolasmahn.momentum.domain.SubstancesTrackerRef
import de.nicolasmahn.momentum.domain.settings.DashboardFeature
import de.nicolasmahn.momentum.domain.substance.SubstanceKind
import de.nicolasmahn.momentum.domain.substance.SubstanceLog
import de.nicolasmahn.momentum.domain.substance.SubstanceNoneDay
import de.nicolasmahn.momentum.domain.substance.SubstanceSettings
import de.nicolasmahn.momentum.domain.substance.joiningStreak
import de.nicolasmahn.momentum.domain.substance.canSayNoSubstanceOn
import de.nicolasmahn.momentum.domain.substance.substanceAskedDay
import de.nicolasmahn.momentum.domain.substance.substanceJudgmentDayFor
import de.nicolasmahn.momentum.domain.substance.substanceRecordJudgedOn
import de.nicolasmahn.momentum.domain.settings.showingOnly
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Substances is always about yesterday: each day judges the record of the day before, and only that. */
class SubstancesAboutYesterdayTest {
    private val today = LocalDate.of(2026, 11, 18)
    private val monday = today.minusDays(today.dayOfWeek.value - 1L)
    private val substancesOnly = showingOnly(features = setOf(DashboardFeature.Substances))
    private val joined = SubstanceSettings.Legacy.joiningStreak(monday.minusWeeks(1))

    private fun none(day: LocalDate) = SubstanceNoneDay(day.toString(), 1L)
    private fun use(day: LocalDate) = SubstanceLog("use-$day", SubstanceKind.CANNABIS.id, day.toString(), 1L)
    private fun weekOf(state: AppState, today: LocalDate) = habitStreak(state, substancesOnly, today).week

    @Test fun eachRecordIsJudgedOnceByTheDayAfterIt() {
        (-3L..3L).map { today.plusDays(it) }.forEach { recorded ->
            assertEquals(recorded.plusDays(1), substanceJudgmentDayFor(recorded))
            assertEquals(recorded, substanceRecordJudgedOn(substanceJudgmentDayFor(recorded)))
        }
    }

    /** A day is kept by the record of the day before, and its own record waits for tomorrow. */
    @Test fun todayIsKeptByYesterdaysRecord() {
        val yesterday = today.minusDays(1)
        val onlyToday = habitStreak(AppState(substances = joined, substanceNoneDays = listOf(none(today))), substancesOnly, today)
        val yesterdayLogged = habitStreak(AppState(substances = joined, substanceNoneDays = listOf(none(yesterday))), substancesOnly, today)

        assertFalse(requireNotNull(onlyToday.standings[SubstancesTrackerRef]).keptToday)
        assertTrue(requireNotNull(yesterdayLogged.standings[SubstancesTrackerRef]).keptToday)
    }

    /** A use of a substance stayed away from is a gap on the day after it, the day that judges it. */
    @Test fun aStayedAwayUseIsAGapOnTheDayAfterIt() {
        val stayingAway = joined.copy(goal = de.nicolasmahn.momentum.domain.checklist.Dated.always(de.nicolasmahn.momentum.domain.substance.SubstanceGoal.StayingAway))
        val used = monday.plusDays(1)
        val week = weekOf(AppState(substances = stayingAway, substanceLogs = listOf(use(used))), today)
        val i = used.dayOfWeek.value - 1

        assertEquals(MoDayMark.Perfect, week[i])
        assertTrue(week[i + 1] != MoDayMark.Perfect)
    }

    /** The day asked is the day that is over, and only such a day may be "none". */
    @Test fun theAskedDayIsYesterday() {
        listOf(today, today.plusDays(1), today.plusDays(30)).forEach { day ->
            assertEquals(day.minusDays(1), substanceAskedDay(day))
            assertTrue(canSayNoSubstanceOn(day.minusDays(1), day))
            assertFalse(canSayNoSubstanceOn(day, day))
        }
    }

    /** A use moved to yesterday is what today judges, so the day it was moved to closes today. */
    @Test fun aUseMovedToYesterdayClosesToday() {
        val movedToYesterday = AppState(substances = joined, substanceLogs = listOf(use(today.minusDays(1))))
        assertTrue(requireNotNull(habitStreak(movedToYesterday, substancesOnly, today).standings[SubstancesTrackerRef]).keptToday)
    }
}

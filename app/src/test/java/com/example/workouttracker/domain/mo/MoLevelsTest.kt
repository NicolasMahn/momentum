package com.example.workouttracker.domain.mo

import com.example.workouttracker.domain.checklist.AboutDay
import com.example.workouttracker.domain.checklist.Dated
import com.example.workouttracker.domain.checklist.recordDayFor
import com.example.workouttracker.domain.checklist.recordJudgedOn
import com.example.workouttracker.domain.substance.substanceRecordJudgedOn
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Levels and a lost streak are read from the streak's history exactly as the walk counts it: a
 * kept day adds one, a protected day carries the count, an unprotected miss ends the run.
 */
class MoLevelsTest {
    private val today = LocalDate.parse("2026-11-20")

    /** Days ending yesterday with [marks], then today with [todayMark]. */
    private fun history(vararg marks: MoDayMark, todayMark: MoDayMark = MoDayMark.Open): List<MoDayRecord> {
        val first = today.minusDays(marks.size.toLong())
        return marks.mapIndexed { index, mark -> MoDayRecord(first.plusDays(index.toLong()), mark) } + MoDayRecord(today, todayMark)
    }

    private val p = MoDayMark.Perfect
    private val x = MoDayMark.Missed

    @Test fun twoMissesInARowStillFindTheFirstLoss() {
        val loss = streakLoss(history(p, p, p, p, p, x, x), today)
        assertEquals(MoStreakLoss(5, today.minusDays(2)), loss)
    }

    @Test fun aProtectedDayCarriesTheCountWithoutAddingToIt() {
        assertEquals(3, streakLoss(history(p, MoDayMark.Protected, p, MoDayMark.Frozen, p, x), today)?.days)
        assertEquals(3, streakLoss(history(p, MoDayMark.Sick, p, MoDayMark.NothingDue, p, x), today)?.days)
    }

    /** The loss is read from the records every time: filling in the day that broke it, if that keeps it, takes it away. */
    @Test fun aBackfillThatKeepsTheDayTakesTheLossAway() {
        assertEquals(4, streakLoss(history(p, p, p, p, x), today)?.days)
        assertNull(streakLoss(history(p, p, p, p, p), today))
    }

    @Test fun aLossAlreadyOutgrownByTheRunSinceIsNoLongerShown() {
        assertNull(streakLoss(history(p, p, p, p, x, p, p, p, p), today))
        assertEquals(4, streakLoss(history(p, p, p, p, x, p, p, p), today)?.days)
    }

    @Test fun aRunShorterThanThreeDaysIsNoLossAndAnOldLossIsNoNews() {
        assertNull(streakLoss(history(p, p, x), today))
        assertNull(streakLoss(history(p, p, p, x, *Array(8) { MoDayMark.NothingDue }), today))
    }

    @Test fun todaysOpenDayNeverEndsAnything() {
        assertNull(streakLoss(history(p, p, p, todayMark = MoDayMark.Missed), today))
    }

    /** A level reached when the day closed overnight is an arrival on that day, shown the morning after. */
    @Test fun aLevelReachedOvernightArrivesOnTheDayThatClosed() {
        val arrival = levelArrival(history(*Array(7) { p }))
        assertEquals(MoLevel.Spark, arrival?.level)
        assertEquals(today.minusDays(1), arrival?.reachedOn)
        assertEquals(MoLevel.Spark, levelArrival(history(*Array(6) { p }, todayMark = p))?.level)
        assertNull(levelArrival(history(*Array(6) { p })))
    }

    @Test fun sixtyDaysIsALevelThoughNoRoundNumberSaysSo() {
        assertEquals(MoLevel.Flame, levelArrival(history(*Array(60) { p }))?.level)
        assertEquals(MoLevel.Flame, MoLevel.of(99))
        assertNull(MoLevel.of(6))
    }

    /** Each run reaches its levels anew, so a level is shown once per run and level. */
    @Test fun eachRunReachesALevelUnderItsOwnKey() {
        val first = levelArrival(history(*Array(7) { p }, x, x))
        val second = levelArrival(history(*Array(7) { p }, x, *Array(7) { p }))
        assertNull(first)
        assertEquals(MoLevel.Spark, second?.level)
        // The second run began the day after the miss, and its key names that start, not the first run's.
        assertEquals(today.minusDays(7), second?.runStart)
        assertNotEquals("${today.minusDays(15)}/Spark", second?.seenKey)
    }

    /** A view about yesterday is filled in on the day its record is about, not on the day that judged it. */
    @Test fun theDayToFillInIsTheRecordsDayForAViewAboutYesterday() {
        val aboutYesterday = Dated.always(AboutDay.Yesterday)
        assertEquals(today.minusDays(1), aboutYesterday.recordDayFor(today))
        assertEquals(today.minusDays(1), aboutYesterday.recordJudgedOn(today))
        assertEquals(today.minusDays(1), substanceRecordJudgedOn(today))
    }
}

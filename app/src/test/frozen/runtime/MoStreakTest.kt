package com.example.workouttracker.domain.mo

import com.example.workouttracker.domain.compatibility.missed

import com.example.workouttracker.domain.compatibility.free

import com.example.workouttracker.domain.compatibility.kept

import com.example.workouttracker.domain.compatibility.history

import com.example.workouttracker.domain.compatibility.MoNeed

import com.example.workouttracker.domain.mo.compatibility.InheritedMoEvaluation
import com.example.workouttracker.domain.compatibility.ReadingTimeId
import com.example.workouttracker.domain.time.TimeLog
import com.example.workouttracker.domain.AppState
import com.example.workouttracker.domain.trackerRef
import com.example.workouttracker.domain.settings.FeatureVisibilitySettings
import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Weeks are written as seven characters, Monday first. Records: `x` for a day with one and `.` for a
 * day without. Marks: `P` perfect, `F` frozen, `M` missed, `O` open, `U` upcoming and `-` nothing due.
 *
 * Care has no free days, so every `.` of a Care week is a miss.
 */
class MoStreakTest {
    private val firstMonday = LocalDate.parse("2026-01-05")
    private val onlyCare = only(MoNeed.Care)
    private val perfectWeek = "xxxxxxx"

    @Test
    fun aPerfectWeekEarnsItsFreezeWhenItsSundayIsOverAndNotBefore() {
        val week = cared(perfectWeek)

        val sundayEvening = week.streakOn(day(week = 1, DayOfWeek.SUNDAY))
        val mondayMorning = week.streakOn(day(week = 2, DayOfWeek.MONDAY))

        assertEquals(Tally(days = 7, freezes = 0, freezeCapacity = 3), sundayEvening.tally())
        assertEquals("PPPPPPP", sundayEvening.marks())
        assertTrue(sundayEvening.weekCanEarnFreeze)
        assertEquals(Tally(days = 7, freezes = 1, freezeCapacity = 3), mondayMorning.tally())
        assertEquals("OUUUUUU", mondayMorning.marks())
    }

    @Test
    fun aWeekEarnsOneFreezeAtMost() {
        val twoWeeks = cared(perfectWeek, perfectWeek)

        DayOfWeek.entries.forEach { dayOfWeek ->
            assertEquals("on $dayOfWeek", 1, twoWeeks.streakOn(day(week = 2, dayOfWeek)).freezes)
        }
        assertEquals(2, twoWeeks.streakOn(day(week = 3, DayOfWeek.MONDAY)).freezes)
    }

    @Test
    fun sevenPerfectDaysAcrossTwoWeeksEarnNothingUntilAWholeWeekIsPerfect() {
        val thursdayToWednesday = cared("...xxxx", "xxx....").streakOn(day(week = 2, DayOfWeek.THURSDAY))
        val throughTheSecondWeek = cared("...xxxx", perfectWeek).streakOn(day(week = 3, DayOfWeek.MONDAY))

        assertEquals(Tally(days = 7, freezes = 0, freezeCapacity = 3), thursdayToWednesday.tally())
        assertEquals(Tally(days = 11, freezes = 1, freezeCapacity = 3), throughTheSecondWeek.tally())
    }

    @Test
    fun aWeekWithAFrozenDayEarnsNothingWhicheverDayItIs() {
        listOf(".xxxxxx", "xxx.xxx", "xxxxxx.").forEach { secondWeek ->
            val streak = cared(perfectWeek, secondWeek).streakOn(day(week = 3, DayOfWeek.MONDAY))

            // The first week's freeze paid for the bad day, and the second week brought no new one.
            assertEquals(secondWeek, Tally(days = 13, freezes = 0, freezeCapacity = 3), streak.tally())
        }
        assertEquals(2, cared(perfectWeek, perfectWeek).streakOn(day(week = 3, DayOfWeek.MONDAY)).freezes)
    }

    @Test
    fun aWeekThatBrokeTheStreakEarnsNothing() {
        val streak = cared("xx.xxxx").streakOn(day(week = 2, DayOfWeek.MONDAY))

        assertEquals(Tally(days = 4, freezes = 0, freezeCapacity = 3), streak.tally())
    }

    @Test
    fun aWeekWithAFreeDayStillEarnsButOneWithAMissBeyondTheAllowanceDoesNot() {
        val nextMonday = day(week = 2, DayOfWeek.MONDAY)
        fun streakOf(need: MoNeed, week: String) =
            AppState().keepingWeeks(need, firstMonday, week).streakOn(nextMonday, only(need))

        assertEquals(Tally(days = 7, freezes = 1, freezeCapacity = 3), streakOf(MoNeed.Reading, "xxx.xxx").tally())
        assertEquals(Tally(days = 7, freezes = 1, freezeCapacity = 3), streakOf(MoNeed.Activity, "x..xxxx").tally())
        assertEquals(Tally(days = 3, freezes = 0, freezeCapacity = 3), streakOf(MoNeed.Reading, "xx..xxx").tally())
    }

    @Test
    fun theWeekTheFirstTaskWasTakenUpInEarnsNothing() {
        val afterTheFirstWeek = cared(".xxxxxx").streakOn(day(week = 2, DayOfWeek.MONDAY))
        val afterTheSecondWeek = cared(".xxxxxx", perfectWeek).streakOn(day(week = 3, DayOfWeek.MONDAY))

        assertEquals(Tally(days = 6, freezes = 0, freezeCapacity = 3), afterTheFirstWeek.tally())
        assertEquals(Tally(days = 13, freezes = 1, freezeCapacity = 3), afterTheSecondWeek.tally())
    }

    @Test
    fun aSecondTaskTakenUpInTheMiddleOfAWeekDoesNotSpoilIt() {
        val supplementsFromThursday = cared(perfectWeek).keepingWeeks(MoNeed.Supplements, firstMonday, "...xxxx")
        val careAndSupplements = only(MoNeed.Care, MoNeed.Supplements)

        val streak = supplementsFromThursday.streakOn(day(week = 2, DayOfWeek.MONDAY), careAndSupplements)

        assertEquals(Tally(days = 7, freezes = 1, freezeCapacity = 3), streak.tally())
    }

    @Test
    fun theBankIsFilledAWeekAtATimeAndHoldsMoreFromThirtyAndAHundredDays() {
        // Read on a Monday after so many perfect days, so the last of them closed a week. That week is
        // judged by the streak its Sunday left behind: 30 days may bank a fourth freeze, 29 may not.
        val expected = listOf(
            6 to Tally(days = 6, freezes = 0, freezeCapacity = 3),
            7 to Tally(days = 7, freezes = 1, freezeCapacity = 3),
            8 to Tally(days = 8, freezes = 1, freezeCapacity = 3),
            21 to Tally(days = 21, freezes = 3, freezeCapacity = 3),
            28 to Tally(days = 28, freezes = 3, freezeCapacity = 3),
            29 to Tally(days = 29, freezes = 3, freezeCapacity = 3),
            30 to Tally(days = 30, freezes = 4, freezeCapacity = 5),
            36 to Tally(days = 36, freezes = 4, freezeCapacity = 5),
            37 to Tally(days = 37, freezes = 5, freezeCapacity = 5),
            44 to Tally(days = 44, freezes = 5, freezeCapacity = 5),
            99 to Tally(days = 99, freezes = 5, freezeCapacity = 5),
            100 to Tally(days = 100, freezes = 6, freezeCapacity = 7),
            107 to Tally(days = 107, freezes = 7, freezeCapacity = 7),
            114 to Tally(days = 114, freezes = 7, freezeCapacity = 7),
        )

        expected.forEach { (perfectDays, streak) ->
            assertEquals("after $perfectDays perfect days", streak, streakAfter(perfectDays).tally())
        }
    }

    @Test
    fun aFullBankEarnsNothingButItsWeekStillCounts() {
        val threeWeeks = arrayOf(perfectWeek, perfectWeek, perfectWeek)
        val inTheFourthWeek = cared(*threeWeeks, "xxx....").streakOn(day(week = 4, DayOfWeek.WEDNESDAY))
        val afterTheFourthWeek = cared(*threeWeeks, perfectWeek).streakOn(day(week = 5, DayOfWeek.MONDAY))

        assertEquals(Tally(days = 24, freezes = 3, freezeCapacity = 3), inTheFourthWeek.tally())
        assertTrue(inTheFourthWeek.weekCanEarnFreeze)
        assertEquals(Tally(days = 28, freezes = 3, freezeCapacity = 3), afterTheFourthWeek.tally())
    }

    @Test
    fun theTierTableIsTheOneTheWalkUses() {
        assertEquals(listOf(MoFreezeTier(0, 3), MoFreezeTier(30, 5), MoFreezeTier(100, 7)), MoFreezeTiers)

        MoFreezeTiers.zipWithNext().forEach { (tier, next) ->
            assertEquals(tier.capacity, streakAfter(next.fromStreakDays - 1).freezeCapacity)
            assertEquals(next.capacity, streakAfter(next.fromStreakDays).freezeCapacity)
        }
    }

    @Test
    fun theWeekMarksEveryDayByWhatItMeantForTheStreak() {
        val saturday = day(week = 2, DayOfWeek.SATURDAY)
        // Last week's freeze paid for Tuesday, so Thursday found the bank empty.
        val open = cared(perfectWeek, "x.x.x..").streakOn(saturday)
        val complete = cared(perfectWeek, "x.x.xx.").streakOn(saturday)

        assertEquals("PFPMPOU", open.marks())
        assertEquals(Tally(days = 1, freezes = 0, freezeCapacity = 3), open.tally())
        assertEquals("PFPMPPU", complete.marks())
        assertEquals(Tally(days = 2, freezes = 0, freezeCapacity = 3), complete.tally())
        assertFalse(open.weekCanEarnFreeze)
        assertFalse(complete.weekCanEarnFreeze)
    }

    @Test
    fun aFrozenDayAloneAndAMissedDayAloneEachRuleTheWeekOut() {
        val wednesday = day(week = 2, DayOfWeek.WEDNESDAY)
        val frozen = cared(perfectWeek, "x.x....").streakOn(wednesday)
        val missed = cared(".xxxxxx", "x.x....").streakOn(wednesday)

        assertEquals("PFPUUUU", frozen.marks())
        assertFalse(frozen.weekCanEarnFreeze)
        assertEquals("PMPUUUU", missed.marks())
        assertFalse(missed.weekCanEarnFreeze)
    }

    @Test
    fun aWeekThatStartedBeforeTheFirstRecordCannotEarn() {
        val takenUpOnWednesday = cared("..xx...").streakOn(day(week = 1, DayOfWeek.FRIDAY))

        assertEquals("--PPOUU", takenUpOnWednesday.marks())
        assertFalse(takenUpOnWednesday.weekCanEarnFreeze)
        assertEquals(Tally(days = 2, freezes = 0, freezeCapacity = 3), takenUpOnWednesday.tally())
    }

    @Test
    fun aWeekWithNothingDueIsMarkedSoAndCannotEarn() {
        val wednesday = day(week = 1, DayOfWeek.WEDNESDAY)
        val withoutRecords = AppState().streakOn(wednesday, FeatureVisibilitySettings())
        val everythingHidden = cared(perfectWeek).streakOn(wednesday, only())

        listOf(withoutRecords, everythingHidden).forEach { streak ->
            assertEquals("---UUUU", streak.marks())
            assertFalse(streak.weekCanEarnFreeze)
            assertEquals(Tally(days = 0, freezes = 0, freezeCapacity = 3), streak.tally())
        }
    }

    @Test
    fun aMondayCanEarnAsSoonAsItAsksForSomething() {
        val monday = day(week = 2, DayOfWeek.MONDAY)
        val neverStarted = AppState().streakOn(monday)
        val startedToday = cared(".......", "x......").streakOn(monday)
        val untouched = cared(perfectWeek).streakOn(monday)

        assertEquals("-UUUUUU", neverStarted.marks())
        assertFalse(neverStarted.weekCanEarnFreeze)
        assertEquals("PUUUUUU", startedToday.marks())
        assertTrue(startedToday.weekCanEarnFreeze)
        assertEquals("OUUUUUU", untouched.marks())
        assertTrue(untouched.weekCanEarnFreeze)
    }

    @Test
    fun aTaskLeftForItsFreeDayKeepsTodayOpenAndTheWeekInReach() {
        val restsOnThursday = AppState().keepingWeeks(MoNeed.Reading, firstMonday, "xxx....")
        val thursday = day(week = 1, DayOfWeek.THURSDAY)

        val beforeMidnight = restsOnThursday.streakOn(thursday, only(MoNeed.Reading))
        val afterMidnight = restsOnThursday.streakOn(thursday.plusDays(1), only(MoNeed.Reading))

        assertEquals("PPPOUUU", beforeMidnight.marks())
        assertTrue(beforeMidnight.weekCanEarnFreeze)
        // Friday is open too, but the week's one free day is spent, so without a record it is a miss.
        assertEquals("PPPPOUU", afterMidnight.marks())
        assertTrue(afterMidnight.weekCanEarnFreeze)
    }

    @Test
    fun aWeekThatCanStillEarnDoesOnceTheRestOfItIsKeptAndNoOtherWeekDoes() {
        // Every way the second week can have gone up to today, on every day of it. The first week's
        // freeze makes room for a frozen day and, after it, for one that breaks the streak.
        DayOfWeek.entries.forEach { todayOfWeek ->
            val daysSoFar = todayOfWeek.value
            (0 until (1 shl daysSoFar)).forEach { pattern ->
                val soFar = (0 until daysSoFar).joinToString("") { if (pattern and (1 shl it) != 0) "x" else "." }
                val restKept = soFar.dropLast(1) + "x".repeat(perfectWeek.length - daysSoFar + 1)

                val now = cared(perfectWeek, soFar).streakOn(day(week = 2, todayOfWeek))
                val afterTheWeek = cared(perfectWeek, restKept).streakOn(day(week = 3, DayOfWeek.MONDAY))

                assertEquals("$soFar on $todayOfWeek", now.weekCanEarnFreeze, afterTheWeek.freezes == now.freezes + 1)
            }
        }
    }

    /**
     * A task hidden from the beginning takes no part at all, and the walk starts without it: it is
     * never due, so a bank that fills by kept days, Weight's week bank and Eating's daily bank,
     * never earns, while Reading's weekly amount still earns by its minutes, as a recorded amount
     * always has. A dated hide is pinned beside the ledger; this is what an undated one keeps.
     */
    @Test
    fun aTaskHiddenFromTheBeginningIsOutOfTheStartNeverDueAndOnlyItsWeeklyAmountStillEarns() {
        val weekBefore = firstMonday.minusWeeks(1)
        val minutes = listOf(1L, 8L).map { days -> TimeLog(ReadingTimeId, firstMonday.plusDays(days).toString(), days, minutes = 60) }
        val state = cared(perfectWeek, perfectWeek)
            .keepingWeeks(MoNeed.Weight, weekBefore, perfectWeek, perfectWeek, perfectWeek)
            .keepingWeeks(MoNeed.Eating, weekBefore, perfectWeek, perfectWeek, perfectWeek)
            .let { it.copy(timeLogs = (it.timeLogs.filter { it.viewId == ReadingTimeId } + minutes)) }

        // Under the token rules, which keep the banks; the rest of this file pins the rules before them.
        val evaluation = InheritedMoEvaluation(state, onlyCare, day(week = 3, DayOfWeek.MONDAY), rewardStart = weekBefore)
        val streak = evaluation.streak

        assertEquals(firstMonday, streak.history.first().date)
        assertEquals(setOf(MoNeed.Care), streak.nextToken.keys)
        assertEquals(Tally(days = 14, freezes = 1, freezeCapacity = 2), evaluation.current.streak.tally())
        assertEquals(1, streak.passes[MoNeed.Weight])
        assertEquals(1, streak.passes[MoNeed.Eating])
        assertEquals(3, streak.passes[MoNeed.Reading])
        assertEquals(setOf(MoNeed.Care.view.trackerRef), evaluation.current.streak.standings.keys)
    }

    private fun cared(vararg weeks: String): AppState = AppState().keepingWeeks(MoNeed.Care, firstMonday, *weeks)

    private fun day(week: Int, dayOfWeek: DayOfWeek): LocalDate = firstMonday.plusWeeks(week - 1L).with(dayOfWeek)

    private fun AppState.streakOn(today: LocalDate, visibility: FeatureVisibilitySettings = onlyCare): MoStreak =
        moMood(this, visibility, today).streak

    /** The streak on a Monday after [perfectDays] perfect days, the last of them the Sunday before. */
    private fun streakAfter(perfectDays: Int): MoStreak =
        (1..perfectDays.toLong())
            .fold(AppState()) { state, ago -> state.keeping(MoNeed.Care, firstMonday.minusDays(ago)) }
            .streakOn(firstMonday)

    private fun MoStreak.marks(): String = week.joinToString(separator = "") {
        when (it) {
            MoDayMark.Perfect -> "P"
            MoDayMark.Frozen -> "F"
            MoDayMark.Protected -> "R"
            MoDayMark.Missed -> "M"
            MoDayMark.Open -> "O"
            MoDayMark.Upcoming -> "U"
            MoDayMark.NothingDue -> "-"
            MoDayMark.Sick -> "S"
        }
    }
}

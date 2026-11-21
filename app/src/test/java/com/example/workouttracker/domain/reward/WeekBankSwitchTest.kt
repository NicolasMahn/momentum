package com.example.workouttracker.domain.reward

import com.example.workouttracker.domain.AppState
import com.example.workouttracker.domain.compatibility.MoNeed
import com.example.workouttracker.domain.compatibility.WeightMeasurementId
import com.example.workouttracker.domain.measurement.MeasurementBank
import com.example.workouttracker.domain.measurement.MeasurementEdit
import com.example.workouttracker.domain.measurement.MeasurementLog
import com.example.workouttracker.domain.measurement.MeasurementRewards
import com.example.workouttracker.domain.measurement.applyingMeasurementEdits
import com.example.workouttracker.domain.measurement.testWeightMeasurement
import com.example.workouttracker.domain.mo.MoDayMark
import com.example.workouttracker.domain.mo.MoDayRecord
import com.example.workouttracker.domain.mo.MoStreak
import com.example.workouttracker.domain.mo.MoTokenProgress
import com.example.workouttracker.domain.mo.MoWeekDay
import com.example.workouttracker.domain.mo.habitStreak
import com.example.workouttracker.domain.mo.moMood
import com.example.workouttracker.domain.settings.showingOnly
import com.example.workouttracker.domain.trackerRef
import com.example.workouttracker.feature.statistics.StatisticsRange
import com.example.workouttracker.feature.statistics.statisticsSummary
import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Weight as it was stored earns a token for a Monday-to-Sunday week with six logged days. Saving
 * its numbers makes it earn for every so many kept days from that day on, and reaches no day
 * before it: every mark, every token earned or spent and the streak stay what the week rule made
 * them, and the days already logged in the week of the change count toward the first token.
 */
class WeekBankSwitchTest {
    private val monday = LocalDate.parse("2026-10-05")
    private val weightRef = MoNeed.Weight.view.trackerRef
    private val onlyWeight = showingOnly(MoNeed.Weight)

    private fun on(day: Int): LocalDate = monday.plusDays(day.toLong())

    /** The Wednesday of the sixth week, with its Monday and Tuesday logged. */
    private val switchDay = 37

    /**
     * Nine weeks of Weight, logged every day but eight. Before the change a token covers days 3,
     * 9 and 30, a freeze day 10, and nothing day 17, which ends the streak; after it tokens cover
     * days 43, 44 and 46.
     */
    private val gaps = setOf(3, 9, 10, 17, 30, 43, 44, 46)
    private val weekBank: AppState = AppState(
        measurements = listOf(testWeightMeasurement()),
        measurementLogs = (0 until 63).filterNot { it in gaps }.map { MeasurementLog(WeightMeasurementId, on(it).toString(), it.toLong(), 80.0 - it * 0.1) },
    )

    /** The same records with the change saved on [switchDay]: a token for every six kept days, in the bank of seven it had. */
    private val byKeptDays: AppState = weekBank.switchedOn(switchDay, everyKeptDays = 6)

    private fun AppState.switchedOn(day: Int, everyKeptDays: Int, maxPasses: Int = 7): AppState =
        applyingMeasurementEdits(WeightMeasurementId, listOf(MeasurementEdit.SetBankRules(maxPasses, everyKeptDays)), on(day))

    private fun streak(state: AppState, day: Int): MoStreak = habitStreak(state, onlyWeight, on(day))

    private fun MoStreak.passes(): Int = standings.getValue(weightRef).passes

    private fun MoStreak.nextToken(): MoTokenProgress? = standings.getValue(weightRef).nextToken

    private fun MoStreak.before(day: Int): List<MoDayRecord> = history.filter { it.date < on(day) }

    /** The stored bank is the owner's: `"rewards":{"maxPasses":7,"earn":{"keptDaysInWeek":6}}`. */
    @Test fun theHistoryIsJudgedByTheWeekRuleAsItWasStored() {
        assertEquals(MeasurementBank.Week(MeasurementRewards(maxPasses = 7, keptDaysInWeek = 6)), weekBank.measurements.single().rewards)

        val marks = streak(weekBank, switchDay).before(switchDay).associate { it.date to it.mark }
        assertEquals(MoDayMark.Protected, marks[on(3)])
        assertEquals(MoDayMark.Protected, marks[on(9)])
        assertEquals(MoDayMark.Frozen, marks[on(10)])
        assertEquals(MoDayMark.Missed, marks[on(17)])
        assertEquals(MoDayMark.Protected, marks[on(30)])
        assertEquals(32, marks.values.count { it == MoDayMark.Perfect })
        // Eighteen days since the miss, and two tokens: one to begin with, four weeks earned one, three were spent.
        assertEquals(19, streak(weekBank, switchDay).days)
        assertEquals(2, streak(weekBank, switchDay).passes())
    }

    /** Seen from any day before the change, the saved change is invisible: the whole streak, Mo's mood with it, is what it was. */
    @Test fun untilTheDayBeforeTheChangeNothingIsDifferent() {
        (0 until switchDay).forEach { today ->
            assertEquals("day $today", streak(weekBank, today), streak(byKeptDays, today))
            assertEquals("day $today", moMood(weekBank, onlyWeight, on(today)), moMood(byKeptDays, onlyWeight, on(today)))
        }
    }

    /**
     * On the day of the change and on every later day, the days before it keep their marks, with
     * what covered each, and the day of the change begins with the same tokens, freezes and streak.
     */
    @Test fun noDayBeforeTheChangeIsJudgedAgainLater() {
        val asItWas = streak(weekBank, switchDay)
        val onTheDay = streak(byKeptDays, switchDay)
        assertEquals(asItWas.before(switchDay), onTheDay.before(switchDay))
        assertEquals(asItWas.passes(), onTheDay.passes())
        assertEquals(asItWas.freezes, onTheDay.freezes)
        assertEquals(asItWas.days, onTheDay.days)
        assertEquals(asItWas.week, onTheDay.week)

        (switchDay until 63).forEach { today ->
            assertEquals("day $today", asItWas.before(switchDay), streak(byKeptDays, today).before(switchDay))
        }
        // Whatever numbers are saved, and saved again later, the days before the first change stay.
        val changedAgain = weekBank.switchedOn(switchDay, everyKeptDays = 1, maxPasses = 1).switchedOn(50, everyKeptDays = 30, maxPasses = 9)
        assertEquals(asItWas.before(switchDay), streak(changedAgain, 62).before(switchDay))
    }

    /** The change moves no record, so nothing Statistics counts moves either. */
    @Test fun theChangeMovesNoRecordedFact() {
        assertEquals(weekBank.measurementLogs, byKeptDays.measurementLogs)
        val today = on(62).toString()
        StatisticsRange.entries.forEach { range ->
            val asItWas = statisticsSummary(weekBank, today, range, onlyWeight, null)
            val changed = statisticsSummary(byKeptDays, today, range, onlyWeight, null)
            assertEquals(range.toString(), asItWas.consistency, changed.consistency)
            assertEquals(
                range.toString(),
                asItWas.measurements.map { listOf(it.configuration.id, it.window, it.latestValue, it.change) },
                changed.measurements.map { listOf(it.configuration.id, it.window, it.latestValue, it.change) },
            )
        }
    }

    /**
     * From the day of the change the bank counts kept days wherever in a week they fall. The
     * Monday and Tuesday of that week come along, so the sixth kept day is the Saturday, which
     * earns the token at its close; by the week rule the same week earns it on its Sunday.
     */
    @Test fun fromTheDayOfTheChangeTheBankEarnsByKeptDays() {
        // On the day itself: two days carried and today's, of six, with no week to show.
        assertEquals(MoTokenProgress(done = 3, goal = 6), streak(byKeptDays, switchDay).nextToken())
        assertEquals(
            MoTokenProgress(3, 6, listOf(MoWeekDay.Kept, MoWeekDay.Kept, MoWeekDay.Kept) + List(4) { MoWeekDay.Upcoming }),
            streak(weekBank, switchDay).nextToken(),
        )

        val sunday = 41
        assertEquals(DayOfWeek.SUNDAY, on(sunday).dayOfWeek)
        assertEquals(listOf(weightRef), streak(byKeptDays, sunday).earnedTrackersYesterday)
        assertEquals(3, streak(byKeptDays, sunday).passes())
        assertEquals(emptyList<Any>(), streak(weekBank, sunday).earnedTrackersYesterday)
        assertEquals(2, streak(weekBank, sunday).passes())

        // The Sunday closes a week no more: nothing is earned at its close, where the week rule earns.
        assertEquals(emptyList<Any>(), streak(byKeptDays, sunday + 1).earnedTrackersYesterday)
        assertEquals(3, streak(byKeptDays, sunday + 1).passes())
        assertEquals(listOf(weightRef), streak(weekBank, sunday + 1).earnedTrackersYesterday)

        // Missed days are covered by tokens and take nothing from the count: it stands at two before them and after.
        assertEquals(MoTokenProgress(2, 6), streak(byKeptDays, 43).nextToken())
        assertEquals(MoTokenProgress(2, 6), streak(byKeptDays, 44).nextToken())
        assertEquals(MoTokenProgress(3, 6), streak(byKeptDays, 45).nextToken())
        assertEquals(MoTokenProgress(3, 6), streak(byKeptDays, 46).nextToken())
        assertEquals(List(3) { MoDayMark.Protected }, streak(byKeptDays, 47).history.filter { it.date in setOf(on(43), on(44), on(46)) }.map { it.mark })
        assertEquals(0, streak(byKeptDays, 47).passes())

        // The sixth kept day since the last token is a Monday, and earns at its close.
        assertEquals(DayOfWeek.MONDAY, on(49).dayOfWeek)
        assertEquals(MoTokenProgress(6, 6), streak(byKeptDays, 49).nextToken())
        assertEquals(listOf(weightRef), streak(byKeptDays, 50).earnedTrackersYesterday)
        assertEquals(1, streak(byKeptDays, 50).passes())
        assertEquals(MoTokenProgress(1, 6), streak(byKeptDays, 50).nextToken())
        // By the week rule the week before, with four logged days, earns nothing.
        assertEquals(emptyList<Any>(), streak(weekBank, 49).earnedTrackersYesterday)
        assertEquals(0, streak(weekBank, 50).passes())
    }

    /**
     * The days of the week in progress count toward the first token and stop one short of it:
     * they were logged under the week rule, so they earn nothing by themselves, however small the
     * new number is. The next kept day earns the token.
     */
    @Test fun theDaysOfTheWeekInProgressStopOneShortOfAToken() {
        val everyDay = AppState(
            measurements = listOf(testWeightMeasurement()),
            measurementLogs = (0 until 21).map { MeasurementLog(WeightMeasurementId, on(it).toString(), it.toLong(), 80.0) },
        )
        val saturday = 12
        assertEquals(DayOfWeek.SATURDAY, on(saturday).dayOfWeek)
        val everyTwo = everyDay.switchedOn(saturday, everyKeptDays = 2)
        assertEquals(2, streak(everyTwo, saturday).passes())
        // Five days were logged that week: one of them counts, and today's makes two.
        assertEquals(MoTokenProgress(2, 2), streak(everyTwo, saturday).nextToken())
        assertEquals(3, streak(everyTwo, saturday + 1).passes())
        assertEquals(MoTokenProgress(1, 2), streak(everyTwo, saturday + 1).nextToken())

        // With one kept day a token nothing is carried: the day of the change earns its own.
        val everyOne = everyDay.switchedOn(saturday, everyKeptDays = 1)
        assertEquals(3, streak(everyOne, saturday + 1).passes())

        // Saved on a Monday, the week before closed by the week rule and nothing is in progress.
        val onMonday = everyDay.switchedOn(7, everyKeptDays = 7)
        assertEquals(streak(everyDay, 7).passes(), streak(onMonday, 7).passes())
        assertEquals(MoTokenProgress(1, 7), streak(onMonday, 7).nextToken())
        assertTrue(streak(onMonday, 7).nextToken()!!.week.isEmpty())
    }
}

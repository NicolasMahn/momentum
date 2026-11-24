package de.nicolasmahn.momentum.domain.mo

import de.nicolasmahn.momentum.domain.compatibility.MoNeed
import de.nicolasmahn.momentum.domain.mo.compatibility.InheritedMoEvaluation
import de.nicolasmahn.momentum.domain.mo.compatibility.withInheritedTrackerConfigurations
import de.nicolasmahn.momentum.domain.measurement.recordsOf

import de.nicolasmahn.momentum.domain.AppState
import de.nicolasmahn.momentum.domain.trackerRef
import de.nicolasmahn.momentum.domain.holiday.DayRange
import de.nicolasmahn.momentum.domain.holiday.HolidaySchedule
import de.nicolasmahn.momentum.domain.measurement.MeasurementStep
import de.nicolasmahn.momentum.domain.measurement.asMeasurementLogs
import de.nicolasmahn.momentum.domain.measurement.testWeightMeasurement
import de.nicolasmahn.momentum.domain.measurement.withWeight
import de.nicolasmahn.momentum.domain.reminder.ReminderSettings
import de.nicolasmahn.momentum.domain.reminder.ReminderTime
import de.nicolasmahn.momentum.domain.settings.FeatureVisibilitySettings
import de.nicolasmahn.momentum.domain.measurement.MeasurementRecord
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The edges of how Weight is judged that neither the archive report nor the golden digests reach,
 * with the values the hard-coded rules gave before Weight's bank and its away rule were read from
 * its configuration. Each holds for a state seeded with the default configuration and for one that
 * was never loaded through the repository, which is judged by the same default.
 */
class WeightFromItsConfigurationTest {
    private val monday = LocalDate.parse("2026-10-05")
    private fun on(day: Int) = monday.plusDays(day.toLong())
    private fun weighed(vararg days: Int) = AppState(measurementLogs = days.map { MeasurementRecord(on(it).toString(), it.toLong(), 75.0) }.asMeasurementLogs("weight"))
    private fun away(from: Int, to: Int = from) = HolidaySchedule(listOf(DayRange(on(from), on(to))))
    private fun sick(from: Int, to: Int = from) = HolidaySchedule().withSickDays(DayRange(on(from), on(to)))
    private val onlyWeight = only(MoNeed.Weight)
    private val weightRef = MoNeed.Weight.view.trackerRef

    /** Runs [check] against the records alone and against the same records with the default configuration stored. */
    private fun bothWays(records: AppState, check: (AppState) -> Unit) {
        check(records.withInheritedTrackerConfigurations())
        check(records.withWeight())
    }

    /** Away, nothing is asked of Weight, but it stays in the walk: the day has no gap, costs no token and grows the streak. */
    @Test fun anExcusedMeasurementDayAsksNothingAndDoesNotGrowTheStreak() = bothWays(weighed(0, 1, 3)) { state ->
        val streak = habitStreak(state, onlyWeight, on(4), away(2))
        assertEquals(listOf(MoDayMark.Perfect, MoDayMark.Perfect, MoDayMark.NothingDue, MoDayMark.Perfect, MoDayMark.Open), streak.week.take(5))
        assertEquals(3, streak.days)
        assertEquals(MoWeekDay.Rested, streak.standings.getValue(weightRef).nextToken!!.week[2])
        val ledger = MoLedger.of(state, onlyWeight, on(4), away(2)).history(weightRef)
        assertEquals(listOf(on(0), on(1), on(3), on(4)), ledger.keys.toList())
        val afterAWeekAway = habitStreak(state, onlyWeight, on(14), away(7, 13))
        assertEquals(List(7) { MoDayMark.NothingDue }, afterAWeekAway.history.filter { it.date in on(7)..on(13) }.map { it.mark })
        assertTrue(afterAWeekAway.earnedTrackersYesterday.isEmpty())
        assertEquals(3, state.recordsOf("weight").size)
    }

    /** A weigh-in made away is a record, never a kept day: six records with one of them away are five days, which earn nothing. */
    @Test fun aWeighInOnAnAwayDayEarnsNothing() = bothWays(weighed(0, 1, 2, 3, 4, 5)) { state ->
        val earned = habitStreak(state, onlyWeight, on(7))
        assertEquals(listOf(weightRef), earned.earnedTrackersYesterday)
        // Sunday's gap spent the starting token; the week's six days earned one back.
        assertEquals(1, earned.standings.getValue(weightRef).passes)

        val wednesdayAway = habitStreak(state, onlyWeight, on(7), away(2))
        assertEquals(emptyList<de.nicolasmahn.momentum.domain.TrackerRef>(), wednesdayAway.earnedTrackersYesterday)
        assertEquals(0, wednesdayAway.standings.getValue(weightRef).passes)
        assertEquals(6, state.recordsOf("weight").size)

        val saturday = habitStreak(state, onlyWeight, on(5), away(2)).standings.getValue(weightRef).nextToken!!
        assertEquals(5, saturday.done)
        assertEquals(6, saturday.goal)
    }

    /** A record made on a sick day is no kept day either. */
    @Test fun aRecordOnASickDayIsNoKeptDay() = bothWays(weighed(0, 1, 2, 3, 4, 5)) { state ->
        val wednesdaySick = habitStreak(state, onlyWeight, on(7), sick(2))
        assertEquals(emptyList<de.nicolasmahn.momentum.domain.TrackerRef>(), wednesdaySick.earnedTrackersYesterday)
        assertEquals(0, wednesdaySick.standings.getValue(weightRef).passes)
        assertEquals(MoDayMark.Sick, wednesdaySick.history.single { it.date == on(2) }.mark)
        // Five kept days and the covered Sunday: the sick day left the streak as it was.
        assertEquals(5, wednesdaySick.days)
        assertNull(MoLedger.of(state, onlyWeight, on(7), sick(2)).history(weightRef)[on(2)])
    }

    /** The week closes on its Sunday also when that Sunday counts nothing: six weigh-ins before a sick Sunday earn at its close. */
    @Test fun sixWeighInsBeforeASickSundayEarnAtItsClose() = bothWays(weighed(0, 1, 2, 3, 4, 5)) { state ->
        val streak = habitStreak(state, onlyWeight, on(7), sick(6))
        assertEquals(MoDayMark.Sick, streak.history.single { it.date == on(6) }.mark)
        assertEquals(listOf(weightRef), streak.earnedTrackersYesterday)
        assertEquals(2, streak.standings.getValue(weightRef).passes)
        assertEquals(6, streak.days)

        // Five before it earn nothing, sick Sunday or not.
        val five = habitStreak(weighed(0, 1, 2, 3, 4).copy(measurements = state.measurements), onlyWeight, on(7), sick(5, 6))
        assertEquals(emptyList<de.nicolasmahn.momentum.domain.TrackerRef>(), five.earnedTrackersYesterday)
        assertEquals(1, five.standings.getValue(weightRef).passes)
    }

    /** Hidden, Weight is out of the whole history: no day asks for it, and its bank neither earns nor spends. */
    @Test fun aHiddenWeightIsOutOfTheWholeHistory() = bothWays(weighed(0, 1, 2, 3, 4, 5, 8)) { state ->
        val hiddenEvaluation = InheritedMoEvaluation(state, only(), on(10))
        val hidden = hiddenEvaluation.streak
        assertFalse(weightRef in hiddenEvaluation.current.streak.standings)
        assertTrue(hidden.history.all { it.mark == MoDayMark.NothingDue })
        assertEquals(0, hidden.days)
        assertEquals(1, hidden.passes[MoNeed.Weight])
        assertEquals(1, hidden.freezes)
        assertFalse(MoNeed.Weight in hidden.nextToken)
        assertFalse(weightRef in MoLedger.of(state, only(), on(10)).trackers)
        val mood = moMood(state, only(), on(10))
        assertEquals(0, mood.dueToday)
        assertFalse(weightRef in mood.trackerWeekProgress)

        // Shown again it is judged over all of it, as if it had never been hidden: the starting
        // token covers Sunday, the one its week earned covers Monday, and the freeze pays for Wednesday.
        val shown = habitStreak(state, onlyWeight, on(10))
        assertEquals(MoDayMark.Protected, shown.history.single { it.date == on(6) }.mark)
        assertEquals(MoDayMark.Protected, shown.history.single { it.date == on(7) }.mark)
        assertEquals(MoDayMark.Frozen, shown.history.single { it.date == on(9) }.mark)
        assertEquals(0, shown.standings.getValue(weightRef).passes)
    }

    /**
     * Weight is due at its checkpoint, five minutes after waking, while its reminder is on, and at
     * the check-in's time while it is off or where no configuration holds one.
     */
    @Test fun mosDueTimeForWeightFollowsItsCheckpointOrTheCheckIn() {
        val wakingAtHalfPastSix = ReminderSettings(fallbackAlarmTime = ReminderTime(6, 30))
        val reminded = moMood(weighed(0).withWeight(testWeightMeasurement().let { it.copy(checkpoint = it.checkpoint.copy(remind = true)) }), onlyWeight, on(1))
        assertEquals(listOf(weightRef), reminded.missingTrackersToday)
        assertEquals(listOf(LocalTime.of(6, 35)), reminded.trackerObligations(wakingAtHalfPastSix).getValue(weightRef).map { it.since })
        assertEquals(TrackerAsked(), moAskedTrackers(reminded, wakingAtHalfPastSix, LocalTime.of(6, 49)))
        assertEquals(TrackerAsked(waited = mapOf(weightRef to listOf(15))), moAskedTrackers(reminded, wakingAtHalfPastSix, LocalTime.of(6, 50)))
        assertEquals(emptyList<de.nicolasmahn.momentum.domain.TrackerRef>(), moOverdueTrackers(reminded, wakingAtHalfPastSix, LocalTime.of(7, 34)))
        assertEquals(listOf(weightRef), moOverdueTrackers(reminded, wakingAtHalfPastSix, LocalTime.of(7, 35)))

        bothWays(weighed(0)) { state ->
            val unreminded = moMood(state, onlyWeight, on(1))
            assertEquals(listOf(LocalTime.of(20, 0)), unreminded.trackerObligations(wakingAtHalfPastSix).getValue(weightRef).map { it.since })
            assertEquals(emptyList<de.nicolasmahn.momentum.domain.TrackerRef>(), moOverdueTrackers(unreminded, wakingAtHalfPastSix, LocalTime.of(20, 59)))
            assertEquals(listOf(weightRef), moOverdueTrackers(unreminded, wakingAtHalfPastSix, LocalTime.of(21, 0)))
        }
    }

    /** What the token page shows for Weight: a bank of seven and a week that needs six logged days. */
    @Test fun theTokenPageShowsABankOfSevenAndAWeekOfSixDays() = bothWays(weighed(0, 1, 2)) { state ->
        val streak = habitStreak(state, onlyWeight, on(2))
        assertEquals(7, streak.standings.getValue(weightRef).passCapacity)
        assertEquals(MoTokenProgress(3, 6, listOf(MoWeekDay.Kept, MoWeekDay.Kept, MoWeekDay.Kept) + List(4) { MoWeekDay.Upcoming }), streak.standings.getValue(weightRef).nextToken)
        // The bank fills to seven and no further: eight weeks of six weigh-ins, each with one covered Sunday.
        val weeks = AppState(measurementLogs = (0 until 70).filter { it % 7 != 6 }.map { MeasurementRecord(on(it).toString(), it.toLong(), 75.0) }.asMeasurementLogs("weight"), measurements = state.measurements)
        assertEquals(1, habitStreak(weeks.withInheritedTrackerConfigurations(), onlyWeight, on(70)).standings.getValue(weightRef).passes)
        val everyDay = AppState(measurementLogs = (0 until 70).map { MeasurementRecord(on(it).toString(), it.toLong(), 75.0) }.asMeasurementLogs("weight"), measurements = state.measurements)
        assertEquals(7, habitStreak(everyDay.withInheritedTrackerConfigurations(), onlyWeight, on(70)).standings.getValue(weightRef).passes)
    }

    /**
     * The unit and the step are the screen's. Weight in pounds and steps of 0.5, without a unit in
     * steps of 2.5, or in kilograms and steps of 0.05 is judged day for day as Weight in kilograms
     * and steps of 0.1 is: the streak, the bank and what a week earns, Mo's mood and his ledger.
     */
    @Test fun weightsUnitAndStepChangeNoOutcome() {
        val asItStarts = weighed(0, 1, 2, 3, 4, 5, 8, 9, 11).withWeight()
        listOf(
            testWeightMeasurement().copy(unit = "lb", step = MeasurementStep("0.5")),
            testWeightMeasurement().copy(unit = "", step = MeasurementStep("2.5")),
            testWeightMeasurement().copy(step = MeasurementStep("0.05")),
        ).forEach { weight ->
            val changed = asItStarts.withWeight(weight)
            assertEquals(asItStarts.measurementLogs, changed.measurementLogs)
            listOf(HolidaySchedule(), away(2), sick(6)).forEach { daysOff ->
                (0..14).forEach { day ->
                    val what = "${weight.unit} ${weight.step} day $day"
                    assertEquals(what, outcomeText(habitStreak(asItStarts, onlyWeight, on(day), daysOff)), outcomeText(habitStreak(changed, onlyWeight, on(day), daysOff)))
                    assertEquals(what, moMood(asItStarts, onlyWeight, on(day), daysOff).outcome(), moMood(changed, onlyWeight, on(day), daysOff).outcome())
                    assertEquals(
                        what,
                        MoLedger.of(asItStarts, onlyWeight, on(day), daysOff).history(weightRef),
                        MoLedger.of(changed, onlyWeight, on(day), daysOff).history(weightRef),
                    )
                }
            }
        }
    }
}

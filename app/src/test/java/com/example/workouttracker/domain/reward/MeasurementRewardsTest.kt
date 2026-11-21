package com.example.workouttracker.domain.reward

import com.example.workouttracker.domain.measurement.recordsOf

import com.example.workouttracker.domain.settings.showingOnly
import com.example.workouttracker.domain.AppState
import com.example.workouttracker.domain.trackerRef
import com.example.workouttracker.domain.checklist.RewardPeriod
import com.example.workouttracker.domain.checklist.WhenAway
import com.example.workouttracker.domain.holiday.DayRange
import com.example.workouttracker.domain.holiday.HolidaySchedule
import com.example.workouttracker.domain.measurement.MeasurementEdit
import com.example.workouttracker.domain.measurement.MeasurementLog
import com.example.workouttracker.domain.compatibility.WeightMeasurementId
import com.example.workouttracker.domain.measurement.applyingMeasurementEdits
import com.example.workouttracker.domain.measurement.testMadeMeasurement
import com.example.workouttracker.domain.measurement.testWeightMeasurement
import com.example.workouttracker.domain.mo.MoDayMark
import com.example.workouttracker.domain.compatibility.MoNeed
import com.example.workouttracker.domain.mo.MoStreak
import com.example.workouttracker.domain.mo.MoStreakStanding
import com.example.workouttracker.domain.mo.MoTokenProgress
import com.example.workouttracker.domain.mo.habitStreak
import com.example.workouttracker.domain.mo.moMood
import com.example.workouttracker.domain.mo.compatibility.InheritedMoEvaluation
import com.example.workouttracker.domain.settings.FeatureVisibilitySettings
import com.example.workouttracker.domain.ref
import com.example.workouttracker.domain.trackerRef
import com.example.workouttracker.feature.mo.todayIsSetAside
import com.example.workouttracker.feature.statistics.StatisticsRange
import com.example.workouttracker.feature.statistics.statisticsSummary
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A measurement a person made that joins the streak is in the walk what a made checklist is: judged
 * from the day it joined by any record of it, covered by its own bank, never reaching back, and out
 * of sight once it left or is hidden.
 */
class MeasurementRewardsTest {
    private val monday = LocalDate.parse("2026-10-05")
    private val waist = testMadeMeasurement()
    private val waistRef = waist.ref.trackerRef
    private val weightRef = MoNeed.Weight.view.trackerRef
    private val onlyWeight = showingOnly(MoNeed.Weight)
    private val nothingElse = showingOnly()

    private fun on(day: Long): LocalDate = monday.plusDays(day)

    /** Weight logged every day of three weeks but [withoutWeight], the waist on every day but [missed]. */
    private fun history(vararg missed: Long, withoutWeight: Set<Long> = emptySet()): AppState = AppState(
        measurements = listOf(testWeightMeasurement().copy(whenAway = com.example.workouttracker.domain.checklist.Dated.always(WhenAway.Due)), waist),
        measurementLogs = (0L until 21L).filterNot { it in withoutWeight }.map { MeasurementLog(WeightMeasurementId, on(it).toString(), it, 80.0) } +
            (0L until 21L).filterNot { it in missed }.map { MeasurementLog(waist.id, on(it).toString(), it, 90.0 + it) },
    )

    private fun AppState.edited(vararg edits: MeasurementEdit, day: Long): AppState = applyingMeasurementEdits(waist.id, edits.toList(), on(day))

    private fun AppState.joined(day: Long = 0): AppState = edited(MeasurementEdit.JoinRewards, day = day)

    private fun AppState.withoutTheWaist(): AppState =
        copy(measurements = measurements.filterNot { it.id == waist.id }, measurementLogs = measurementLogs.filterNot { it.measurementId == waist.id })

    private fun streak(state: AppState, day: Long, visibility: FeatureVisibilitySettings = onlyWeight, daysOff: HolidaySchedule = HolidaySchedule()) =
        habitStreak(state, visibility, on(day), daysOff)

    private fun MoStreak.markOn(day: Long) = history.single { it.date == on(day) }.mark

    private fun MoStreak.waist(): MoStreakStanding? = standings[waistRef]

    @Test fun aMeasurementOutsideTheStreakChangesNothing() {
        val without = streak(history(3, 4).withoutTheWaist(), 20)
        assertEquals(without, streak(history(3, 4), 20))
        // Nor does one that joined and left again on the same day, whose bank holds no day.
        val undone = history(3, 4).edited(MeasurementEdit.JoinRewards, MeasurementEdit.LeaveRewards, day = 10)
        assertEquals(emptyList<RewardPeriod>(), undone.measurements.single { it.id == waist.id }.standardRewards?.periods)
        assertEquals(without, streak(undone, 20))
    }

    @Test fun joiningNeverReachesBackIntoDaysAlreadyJudged() {
        val before = streak(history(3, 4), 20)
        val after = streak(history(3, 4).joined(day = 10), 20)
        // The days without a record lie before joining, so they stay as they were.
        assertEquals(before.history.filter { it.date < on(10) }, after.history.filter { it.date < on(10) })
        assertEquals(before.history, after.history)
        assertEquals(3, after.waist()?.passCapacity)
    }

    /** A day is kept by its record, whatever the number and however many: zero, a negative number and two of one day keep it as any other does. */
    @Test fun aDayIsKeptByItsRecordWhateverTheNumber() {
        val odd = history().let { state ->
            state.copy(
                measurementLogs = state.measurementLogs.filterNot { it.measurementId == waist.id && it.dateKey in setOf(on(3).toString(), on(4).toString(), on(5).toString()) } +
                    listOf(
                        MeasurementLog(waist.id, on(3).toString(), 1, 0.0),
                        MeasurementLog(waist.id, on(4).toString(), 1, -12.5),
                        MeasurementLog(waist.id, on(5).toString(), 1, 91.0),
                        MeasurementLog(waist.id, on(5).toString(), 2, 91.0),
                    ),
            )
        }
        val result = streak(odd.joined(), 20)
        assertEquals(result.history, streak(history().joined(), 20).history)
        assertTrue(result.history.dropLast(1).all { it.mark == MoDayMark.Perfect })
        // A record of another measurement on the day keeps nothing for this one.
        val othersRecord = history(6).let { it.copy(measurementLogs = it.measurementLogs + MeasurementLog("msr_m7xd", on(6).toString(), 1, 7.0)) }
        assertEquals(setOf(waistRef), streak(othersRecord.joined(), 20).history.single { it.date == on(6) }.coveredTrackers)
    }

    @Test fun aGapIsCoveredFromItsOwnBankAndEverySevenKeptDaysEarnAToken() {
        val joined = history(12).joined()
        val day12 = streak(joined, 20).history.single { it.date == on(12) }
        assertEquals(MoDayMark.Protected, day12.mark)
        assertEquals(setOf(waistRef), day12.coveredTrackers)
        // One to start, one for the 7th kept day, one spent on day 12, one for the 14th kept day.
        assertEquals(2, streak(joined, 20).waist()?.passes)
        assertEquals(2, streak(joined, 7).waist()?.passes)
        assertEquals(1, streak(joined, 13).waist()?.passes)
        // Weight's bank is its own: it spent nothing on a day only the waist missed.
        assertEquals(streak(history(12).withoutTheWaist(), 20).participantBanks[weightRef], streak(joined, 20).participantBanks[weightRef])
    }

    /**
     * Its standard bank earns by every seventh kept day, counted across Sundays, and keeps its
     * periods: joined on a Thursday, the seventh kept day is the next Wednesday, and the token
     * arrives when that day closes.
     */
    @Test fun itsBankEarnsAcrossASundayByEveryKeptDayAndKeepsItsPeriods() {
        val joined = history().joined(3)
        assertEquals(listOf(RewardPeriod(on(3))), joined.measurements.single { it.id == waist.id }.standardRewards?.periods)
        assertEquals(MoTokenProgress(4, 7), streak(joined, 6).waist()?.nextToken)
        assertEquals(1, streak(joined, 9).waist()?.passes)
        assertEquals(2, streak(joined, 10).waist()?.passes)
        assertEquals(MoTokenProgress(1, 7), streak(joined, 10).waist()?.nextToken)
    }

    /** A second gap finds the bank empty and takes the freeze, which covers the whole day; a third finds neither and ends the streak. */
    @Test fun aGapItsBankCannotCoverTakesAFreezeAndThenTheStreak() {
        val result = streak(history(2, 3, 4).joined(), 6)
        assertEquals(listOf(MoDayMark.Perfect, MoDayMark.Perfect, MoDayMark.Protected, MoDayMark.Frozen, MoDayMark.Missed, MoDayMark.Perfect), result.history.dropLast(1).map { it.mark })
        assertEquals(setOf(waistRef), result.history.single { it.date == on(3) }.coveredTrackers)
        assertFalse(waistRef in result.history.single { it.date == on(4) }.coveredTrackers)
        assertEquals(0, result.freezes)
        assertEquals(0, result.waist()?.passes)
        // The streak restarted on the missed day, then grew on day 5 and on today, which is logged.
        assertEquals(2, result.days)
        assertEquals(1, streak(history(2).joined(), 6).freezes)
    }

    /**
     * A changed bank counts from its day: the days before keep their judgments and tokens, and a
     * smaller bank holds no more than it allows from its first day on.
     */
    @Test fun aChangedBankStartsOnItsDayAndKeepsWhatWasEarned() {
        val joined = history().joined()
        val before = streak(joined, 20)
        assertEquals(3, before.waist()?.passes)
        assertEquals(7, before.waist()?.nextToken?.goal)

        val changed = joined.edited(MeasurementEdit.SetBankRules(maxPasses = 1, everyKeptDays = 3), day = 15)
        val after = streak(changed, 20)
        assertEquals(before.history.filter { it.date < on(15) }, after.history.filter { it.date < on(15) })
        // Seen from the day before, nothing changed.
        assertEquals(streak(joined, 14), streak(changed, 14))
        assertEquals(1, after.waist()?.passes)
        assertEquals(1, after.waist()?.passCapacity)
        assertEquals(3, after.waist()?.nextToken?.goal)
    }

    /** Leaving keeps its closed period: the days it took part are judged as they were, and none after. */
    @Test fun leavingKeepsTheDaysItWasJudgedOnAndJudgesNoneAfter() {
        val joined = history(5, 6, 15, 16).joined()
        val stayed = streak(joined, 20)
        val left = joined.edited(MeasurementEdit.LeaveRewards, day = 10)
        val leftStreak = streak(left, 20)

        assertEquals(listOf(RewardPeriod(on(0), on(9))), left.measurements.single { it.id == waist.id }.standardRewards?.periods)
        assertEquals(stayed.history.filter { it.date < on(10) }, leftStreak.history.filter { it.date < on(10) })
        assertEquals(setOf(waistRef), leftStreak.history.single { it.date == on(5) }.coveredTrackers)
        assertEquals(MoDayMark.Perfect, leftStreak.markOn(15))
        assertTrue(stayed.markOn(15) != MoDayMark.Perfect)
        // Out of the streak its bank is out of sight, and today asks nothing of it.
        assertNull(leftStreak.waist())
    }

    /** The days between two periods stay outside the streak; rejoining on the day it left leaves no day outside. */
    @Test fun rejoiningALaterDayLeavesTheDaysBetweenOutsideAndTheSameDayLeavesNone() {
        val first = history(8, 9, 16).joined()
        val left = first.edited(MeasurementEdit.LeaveRewards, day = 7)
        val again = left.edited(MeasurementEdit.JoinRewards, day = 14)
        assertEquals(listOf(RewardPeriod(on(0), on(6)), RewardPeriod(on(14))), again.measurements.single { it.id == waist.id }.standardRewards?.periods)
        val result = streak(again, 20)
        // Days 8 and 9 are between the periods and judge nothing; day 16 is in the second and is a gap.
        assertEquals(MoDayMark.Perfect, result.markOn(8))
        assertEquals(MoDayMark.Perfect, result.markOn(9))
        assertEquals(setOf(waistRef), result.history.single { it.date == on(16) }.coveredTrackers)
        assertTrue(result.waist() != null)

        // Rejoined on the day it left, it is the measurement that never left: days 8 and 9 are gaps again.
        val sameDay = left.edited(MeasurementEdit.JoinRewards, day = 7)
        assertEquals(first, sameDay)
        assertEquals(streak(first, 20), streak(sameDay, 20))
        assertEquals(setOf(waistRef), streak(sameDay, 20).history.single { it.date == on(8) }.coveredTrackers)
    }

    /** A measurement hidden from the beginning is out of the whole history, and with its entry forgotten it is back in all of it. */
    @Test fun aHiddenMeasurementIsOutOfTheWholeHistoryAndBackWhenShown() {
        val joined = history(12).joined()
        val hidden = onlyWeight.hiding(waist.ref)

        val out = streak(joined, 20, hidden)
        assertEquals(MoDayMark.Perfect, out.markOn(12))
        assertNull(out.waist())
        assertEquals(streak(history(12).withoutTheWaist(), 20), out)

        val back = streak(joined, 20, hidden.forgetting(waist.ref))
        assertEquals(streak(joined, 20), back)
        assertEquals(MoDayMark.Protected, back.markOn(12))
        assertEquals(2, back.waist()?.passes)
    }

    /** A sick day asks nothing of it: no gap, no token spent, nothing earned, and a record made anyway counts for nothing. */
    @Test fun aSickDayAsksNothingOfIt() {
        val sick = HolidaySchedule().withSickDays(DayRange(on(12), on(12)))
        val joined = history(12).joined()
        val result = streak(joined, 20, daysOff = sick)
        assertEquals(MoDayMark.Sick, result.markOn(12))
        assertFalse(waistRef in result.history.single { it.date == on(12) }.coveredTrackers)
        // Nothing was spent: one to start and one each for the 7th and the 14th kept day.
        assertEquals(3, result.waist()?.passes)

        // Logged on the sick day anyway, it has thirteen kept days before day 14, one fewer than without the sick day.
        val loggedAnyway = history(14).joined()
        assertEquals(6, streak(loggedAnyway, 14, daysOff = sick).waist()?.nextToken?.done)
        assertEquals(0, streak(loggedAnyway, 14).waist()?.nextToken?.done)
    }

    /** Its bank is on show while it is shown and in a period: on an ordinary day open or kept, and on a sick day or an away day it is not due on neither, with its bank and its progress as they are. */
    @Test fun itsStandingIsOnShowOnOrdinarySickAndNotDueDays() {
        val joined = history(20).joined()
        val open = streak(joined, 20).waist()
        assertEquals(MoStreakStanding(passes = 3, passCapacity = 3, nextToken = MoTokenProgress(done = 6, goal = 7), openToday = true, keptToday = false), open)

        val kept = streak(history().joined(), 20).waist()
        assertEquals(MoStreakStanding(passes = 3, passCapacity = 3, nextToken = MoTokenProgress(done = 7, goal = 7), openToday = false, keptToday = true), kept)

        val sickToday = HolidaySchedule().withSickDays(DayRange(on(20), on(20)))
        listOf(joined, history().joined()).forEach { state ->
            assertEquals(MoStreakStanding(passes = 3, passCapacity = 3, nextToken = MoTokenProgress(done = 6, goal = 7), openToday = false, keptToday = false), streak(state, 20, daysOff = sickToday).waist())
        }

        val awayToday = HolidaySchedule(listOf(DayRange(on(20), on(20))))
        val notDue = joined.edited(MeasurementEdit.SetWhenAway(WhenAway.Excused), day = 0)
        assertEquals(MoStreakStanding(passes = 3, passCapacity = 3, nextToken = MoTokenProgress(done = 6, goal = 7), openToday = false, keptToday = false), streak(notDue, 20, daysOff = awayToday).waist())
        // Still due, an away day is an ordinary day.
        assertEquals(open, streak(joined, 20, daysOff = awayToday).waist())

        // Several are listed in the order they are configured.
        val mood = testMadeMeasurement(id = "msr_m7xd", label = "Mood", unit = "", step = "1")
        val two = joined.copy(measurements = listOf(mood) + joined.measurements).applyingMeasurementEdits(mood.id, listOf(MeasurementEdit.JoinRewards), on(18))
        assertEquals(listOf(mood.ref.trackerRef, waistRef), streak(two, 20).standings.keys.filter { it.type.value == "measurement" && it != weightRef })
    }

    /** Away and still due, a day without a record is a gap as on any day; away and not due, nothing is asked. */
    @Test fun anAwayDayIsDueUnlessTheMeasurementSaysNotDue() {
        val away = HolidaySchedule(listOf(DayRange(on(12), on(13))))
        val stillDue = history(12, 13).joined()
        val notDue = stillDue.edited(MeasurementEdit.SetWhenAway(WhenAway.Excused), day = 0)

        val due = streak(stillDue, 20, daysOff = away)
        assertEquals(setOf(waistRef), due.history.single { it.date == on(12) }.coveredTrackers)
        assertEquals(setOf(waistRef), due.history.single { it.date == on(13) }.coveredTrackers)

        val excused = streak(notDue, 20, daysOff = away)
        assertFalse(waistRef in excused.history.single { it.date == on(12) }.coveredTrackers)
        assertEquals(MoDayMark.Perfect, excused.markOn(12))
        assertEquals(3, excused.waist()?.passes)
        // Away days change no record, and the rule changes none either.
        assertEquals(stillDue.measurementLogs, notDue.measurementLogs)
    }

    /**
     * Not due is a checklist's not due, never Weight's excused: alone in the streak, an away day it
     * is not due on is a day with nothing due, so the streak does not grow on it, and a record
     * made on it earns nothing toward the next token.
     */
    @Test fun aNotDueAwayDayNeitherKeepsNorGrowsTheStreak() {
        val away = HolidaySchedule(listOf(DayRange(on(0), on(1))))
        val alone = AppState(measurements = listOf(testWeightMeasurement(), waist)).joined().edited(MeasurementEdit.SetWhenAway(WhenAway.Excused), day = 0)

        val result = habitStreak(alone, nothingElse, on(2), away)
        assertEquals(listOf(MoDayMark.NothingDue, MoDayMark.NothingDue), result.history.filter { it.date < on(2) }.map { it.mark })
        assertEquals(0, result.days)

        val loggedAway = alone.copy(measurementLogs = listOf(MeasurementLog(waist.id, on(1).toString(), 1, 91.0)))
        val logged = habitStreak(loggedAway, nothingElse, on(2), away)
        assertEquals(0, logged.waist()?.nextToken?.done)
        assertEquals(0, logged.days)
        // Still due, the same two days are judged: the first costs the token, the second, logged, is kept.
        val stillDue = habitStreak(loggedAway.edited(MeasurementEdit.SetWhenAway(WhenAway.Due), day = 0), nothingElse, on(2), away)
        assertEquals(listOf(MoDayMark.Protected, MoDayMark.Perfect), stillDue.history.filter { it.date < on(2) }.map { it.mark })
        assertEquals(1, stillDue.days)
    }

    /** A rule changed during a holiday starts on its day: an away day before it keeps the rule it had. */
    @Test fun aHolidayRuleChangedDuringAHolidayStartsOnItsDay() {
        val away = HolidaySchedule(listOf(DayRange(on(11), on(14))))
        val joined = history(11, 12, 13, 14).joined()
        val notDueFrom13 = joined.edited(MeasurementEdit.SetWhenAway(WhenAway.Excused), day = 13)
        val days = streak(notDueFrom13, 20, daysOff = away).history.associateBy { it.date }
        // Days 11 and 12 were still due and cost its two tokens; from day 13 nothing is asked.
        assertEquals(setOf(waistRef), days.getValue(on(11)).coveredTrackers)
        assertEquals(setOf(waistRef), days.getValue(on(12)).coveredTrackers)
        assertEquals(listOf(MoDayMark.Protected, MoDayMark.Protected, MoDayMark.Perfect, MoDayMark.Perfect), (11L..14L).map { days.getValue(on(it)).mark })
        assertEquals(0, streak(notDueFrom13, 15, daysOff = away).waist()?.passes)

        // Changed back on day 14, that day is due again, finds the bank empty and takes the freeze; days 11 to 13 keep what they had.
        val dueAgainFrom14 = notDueFrom13.edited(MeasurementEdit.SetWhenAway(WhenAway.Due), day = 14)
        val again = streak(dueAgainFrom14, 20, daysOff = away).history.associateBy { it.date }
        (11L..13L).forEach { assertEquals(days.getValue(on(it)), again.getValue(on(it))) }
        assertEquals(MoDayMark.Frozen, again.getValue(on(14)).mark)
        assertEquals(setOf(waistRef), again.getValue(on(14)).coveredTrackers)
    }

    /** On a day both sick and away the sick day wins: the streak asks nothing, whatever the holiday rule says. */
    @Test fun aSickDayWinsOverAnAwayDay() {
        val both = HolidaySchedule(listOf(DayRange(on(12), on(12)))).withSickDays(DayRange(on(12), on(12)))
        listOf(WhenAway.Due, WhenAway.Excused).forEach { rule ->
            val joined = history(12).joined().edited(MeasurementEdit.SetWhenAway(rule), day = 0)
            val result = streak(joined, 20, daysOff = both)
            assertEquals(rule.toString(), MoDayMark.Sick, result.markOn(12))
            assertEquals(rule.toString(), 3, result.waist()?.passes)
        }
    }

    /** Outside the streak the holiday rule is kept and judges nothing. */
    @Test fun outsideTheStreakTheHolidayRuleJudgesNothing() {
        val away = HolidaySchedule(listOf(DayRange(on(12), on(13))))
        val notDue = history(12, 13).edited(MeasurementEdit.SetWhenAway(WhenAway.Excused), day = 0)
        assertEquals(streak(history(12, 13).withoutTheWaist(), 20, daysOff = away), streak(notDue, 20, daysOff = away))
        // Joining later finds the rule as it was chosen.
        assertEquals(WhenAway.Excused, notDue.joined(day = 15).measurements.single { it.id == waist.id }.whenAway.on(on(15)))
    }

    /** Only Mo's tasks can be set aside, so a joined measurement left open keeps today from looking covered. */
    @Test fun anOpenJoinedMeasurementKeepsTodayFromLookingSetAside() {
        val today = on(20)
        fun setAside(state: AppState) = moMood(state, onlyWeight, today, chosenTrackers = setOf(weightRef)).todayIsSetAside

        val openToday = history(20, withoutWeight = setOf(20)).joined()
        assertEquals(true, streak(openToday, 20).waist()?.openToday)
        assertFalse(setAside(openToday))
        // Logged, or outside the streak, it holds nothing open.
        assertTrue(setAside(history(withoutWeight = setOf(20)).joined()))
        assertTrue(setAside(history(20, withoutWeight = setOf(20))))
    }

    /** Joining changes reward settings, never the recorded counts or values in any tracker's statistics. */
    @Test fun theStreakNeverChangesARecordedFactOrAStatistic() {
        val plain = history(2, 9, 12)
        val joined = plain.joined()
        assertEquals(plain.measurementLogs, joined.measurementLogs)
        StatisticsRange.entries.forEach { range ->
            val today = on(20).toString()
            val before = statisticsSummary(plain, today, range, onlyWeight, null)
            val after = statisticsSummary(joined, today, range, onlyWeight, null)
            assertEquals(before.consistency, after.consistency)
            assertEquals(before.measurements.map { listOf(it.configuration.id, it.window, it.latestValue, it.change) }, after.measurements.map { listOf(it.configuration.id, it.window, it.latestValue, it.change) })
            val inherited = statisticsSummary(plain.withoutTheWaist(), today, range, onlyWeight, streak(plain.withoutTheWaist(), 20).history)
            val allTrackers = statisticsSummary(joined, today, range, onlyWeight, streak(plain.withoutTheWaist(), 20).history)
            assertEquals(inherited, allTrackers.copy(
                consistency = allTrackers.consistency.filterNot { it.ref == waist.ref.trackerRef },
                measurements = allTrackers.measurements.filterNot { it.configuration.id == waist.id },
            ))
        }
    }



    /**
     * What the shared walk does when both have a gap on one day. A day's mark, the freeze and what
     * each bank spends are decided for the day as a whole: while each bank covers its own gap the
     * day is protected and both spend, and once the measurement's is empty the same day takes the
     * freeze instead, so Weight's token stays where it was. Weight's target and its week's earning
     * are untouched either way.
     */
    @Test fun whenBothHaveAGapTheDayIsDecidedAsAWhole() {
        // Weight misses days 8 and 15. The waist misses day 8, or days 13 and 14, which spend its two tokens, and then day 15.
        val weightAlone = history(withoutWeight = setOf(8, 15)).withoutTheWaist()
        val both = history(8, withoutWeight = setOf(8, 15)).joined()
        val bothAgain = history(13, 14, 15, withoutWeight = setOf(8, 15)).joined()

        // Day 8, each bank holding a token: protected by both, and both spend.
        val covered = streak(both, 14)
        assertEquals(MoDayMark.Protected, covered.markOn(8))
        assertEquals(setOf(weightRef, waistRef), covered.history.single { it.date == on(8) }.coveredTrackers)
        assertEquals(streak(weightAlone, 14).participantBanks[weightRef], covered.participantBanks[weightRef])

        // Day 15 alone, Weight's token protects it. Beside a measurement whose bank is empty, it is frozen.
        val alone = streak(weightAlone, 20)
        val shared = streak(bothAgain, 20)
        assertEquals(MoDayMark.Protected, alone.markOn(15))
        assertEquals(MoDayMark.Frozen, shared.markOn(15))
        assertEquals(alone.freezes - 1, shared.freezes)
        assertEquals(setOf(weightRef, waistRef), shared.history.single { it.date == on(15) }.coveredTrackers)
        // The freeze paid, so Weight's token was not spent on that day.
        assertEquals(alone.participantBanks.getValue(weightRef) + 1, shared.participantBanks.getValue(weightRef))
        assertEquals(0, streak(bothAgain, 15).waist()?.passes)
        // What Weight asks and how its week earns are the same: the logged days of the week and the days it takes.
        assertEquals(alone.participantNextToken.getValue(weightRef).let { it.done to it.goal }, shared.participantNextToken.getValue(weightRef).let { it.done to it.goal })
        assertEquals(alone.participantCapacities.getValue(weightRef), shared.participantCapacities.getValue(weightRef))
    }
}

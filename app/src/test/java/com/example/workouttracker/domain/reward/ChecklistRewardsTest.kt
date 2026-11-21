package com.example.workouttracker.domain.reward

import com.example.workouttracker.domain.measurement.recordsOf

import com.example.workouttracker.domain.settings.showingOnly
import com.example.workouttracker.domain.AppState
import com.example.workouttracker.domain.trackerRef
import com.example.workouttracker.domain.settings.ViewRef
import com.example.workouttracker.domain.checklist.ChecklistConfiguration
import com.example.workouttracker.domain.checklist.ChecklistDay
import com.example.workouttracker.domain.checklist.ChecklistEdit
import com.example.workouttracker.domain.checklist.applyEdit
import com.example.workouttracker.domain.checklist.ChecklistItem
import com.example.workouttracker.domain.checklist.Checkpoint
import com.example.workouttracker.domain.checklist.CheckpointTime
import com.example.workouttracker.domain.checklist.RewardPeriod
import com.example.workouttracker.domain.checklist.ViewHue
import com.example.workouttracker.domain.checklist.ViewIcon
import com.example.workouttracker.domain.checklist.Dated
import com.example.workouttracker.domain.checklist.WhenAway
import com.example.workouttracker.domain.holiday.DayRange
import com.example.workouttracker.domain.holiday.HolidaySchedule
import com.example.workouttracker.domain.checklist.DefaultChecklistRewards
import com.example.workouttracker.domain.checklist.joinRewards
import com.example.workouttracker.domain.checklist.leaveRewards
import com.example.workouttracker.domain.measurement.asMeasurementLogs
import com.example.workouttracker.domain.mo.MoDayMark
import com.example.workouttracker.domain.compatibility.MoNeed
import com.example.workouttracker.domain.mo.MoStreak
import com.example.workouttracker.domain.mo.habitStreak
import com.example.workouttracker.domain.settings.FeatureVisibilitySettings
import com.example.workouttracker.domain.mo.moMood
import com.example.workouttracker.domain.mo.compatibility.withInheritedTrackerConfigurations
import com.example.workouttracker.domain.measurement.MeasurementRecord
import com.example.workouttracker.feature.mo.todayIsSetAside
import com.example.workouttracker.persistence.json.AppStateJsonCodec
import org.json.JSONObject
import com.example.workouttracker.feature.statistics.StatisticsRange
import com.example.workouttracker.feature.statistics.statisticsSummary
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** A configured checklist that joins rewards: judged from the join day, by its own bank, and never reaching back. */
class ChecklistRewardsTest {
    private val monday = LocalDate.parse("2026-10-05")
    private val medication = ChecklistConfiguration(
        id = "chk_med",
        label = "Medication",
        icon = ViewIcon.Checklist,
        hue = ViewHue.Sky,
        checkpoints = listOf(Checkpoint("morning", CheckpointTime.Fixed(LocalTime.of(8, 0)), remind = false)),
        items = listOf(ChecklistItem("levo", "Levothyroxine")),
    )
    private val onlyWeight = showingOnly(MoNeed.Weight)
    private val medicationRef = ViewRef.Checklist(medication.id).trackerRef
    private val weightRef = MoNeed.Weight.view.trackerRef

    /** The medication with its every item excused while away. */
    private val excusedMedication = medication.copy(items = medication.items.map { it.copy(whenAway = Dated.always(WhenAway.Excused)) })

    /** Weight logged every day of three weeks, the medication ticked on every day but [missed]. */
    private fun history(vararg missed: Long, checklist: ChecklistConfiguration = medication): AppState = AppState(
        measurements = listOf(com.example.workouttracker.domain.measurement.testWeightMeasurement().copy(whenAway = Dated.always(WhenAway.Due))),
        measurementLogs = (0L until 21L).map { MeasurementRecord(monday.plusDays(it).toString(), it, 80.0) }.asMeasurementLogs("weight"),
        checklists = listOf(checklist),
        checklistDays = (0L until 21L).filterNot { it in missed }.map { ChecklistDay(checklist.id, monday.plusDays(it).toString(), it, setOf("morning/levo")) },
    )

    private fun streak(state: AppState, day: Long, visibility: FeatureVisibilitySettings = onlyWeight) =
        habitStreak(state, visibility, monday.plusDays(day))

    private fun MoStreak.medication() = standings[medicationRef]

    @Test fun aChecklistOutsideRewardsChangesNothing() {
        val without = streak(history(3, 4).copy(checklists = emptyList(), checklistDays = emptyList()), 20)
        assertEquals(without, streak(history(3, 4), 20))
    }

    @Test fun joiningNeverReachesBackIntoDaysAlreadyJudged() {
        val before = streak(history(3, 4), 20)
        val joined = joinRewards(history(3, 4), medication.id, monday.plusDays(10))
        val after = streak(joined, 20)
        // The missed days 3 and 4 lie before joining, so they stay as they were.
        assertEquals(before.history.filter { it.date < monday.plusDays(10) }, after.history.filter { it.date < monday.plusDays(10) })
        assertEquals(DefaultChecklistRewards.maxPasses.current, after.medication()?.passCapacity)
    }

    @Test fun aGapIsCoveredFromTheChecklistsOwnBankAndKeptDaysEarnPasses() {
        val joined = joinRewards(history(12), medication.id, monday)
        val day12 = streak(joined, 20).history.single { it.date == monday.plusDays(12) }
        assertEquals(MoDayMark.Protected, day12.mark)
        assertEquals(setOf(medicationRef), day12.coveredTrackers)
        // One to start, one for the 7th kept day, one spent on day 12, one for the 14th kept day.
        assertEquals(2, streak(joined, 20).medication()?.passes)
        assertEquals(2, streak(joined, 7).medication()?.passes)
    }

    @Test fun leavingKeepsTheDaysItWasJudgedAndJudgesNoneAfter() {
        val joined = joinRewards(history(5, 6, 15, 16), medication.id, monday)
        val stayed = streak(joined, 20)
        val left = streak(leaveRewards(joined, medication.id, monday.plusDays(10)), 20)
        assertEquals(stayed.history.filter { it.date < monday.plusDays(10) }, left.history.filter { it.date < monday.plusDays(10) })
        assertEquals(MoDayMark.Perfect, left.history.single { it.date == monday.plusDays(15) }.mark)
        assertTrue(stayed.history.single { it.date == monday.plusDays(15) }.mark != MoDayMark.Perfect)
    }

    @Test fun rejoiningOpensANewPeriodAndLeavesTheDaysBetweenOutside() {
        val first = joinRewards(history(), medication.id, monday)
        val left = leaveRewards(first, medication.id, monday.plusDays(7))
        val again = joinRewards(left, medication.id, monday.plusDays(14))
        assertEquals(
            listOf(RewardPeriod(monday, monday.plusDays(6)), RewardPeriod(monday.plusDays(14))),
            again.checklists.single().rewards?.periods,
        )
        // A period that starts and ends on the same habit day leaves no period behind, only the settings.
        assertEquals(emptyList<RewardPeriod>(), leaveRewards(joinRewards(history(), medication.id, monday), medication.id, monday).checklists.single().rewards?.periods)
        // Joining again on the day of a leave reopens the period, so the leave taken back leaves nothing.
        assertEquals(first, joinRewards(left, medication.id, monday.plusDays(7)))
    }

    @Test fun aHiddenChecklistIsNeverDueButKeepsItsBank() {
        val joined = joinRewards(history(12), medication.id, monday)
        val hidden = streak(joined, 20, onlyWeight.hidingChecklist(medication.id))
        assertEquals(MoDayMark.Perfect, hidden.history.single { it.date == monday.plusDays(12) }.mark)
        assertFalse(hidden.standings.containsKey(medicationRef))
        assertEquals(DefaultChecklistRewards.maxPasses, joined.checklists.single().rewards?.maxPasses)
    }

    /** Protection is never a record: statistics read the same days whatever the reward rules. */
    @Test fun rewardsNeverChangeARecordedFactOrAStatistic() {
        val plain = history(2, 9, 12)
        val joined = joinRewards(plain, medication.id, monday)
        assertEquals(plain.checklistDays, joined.checklistDays)
        StatisticsRange.entries.forEach { range ->
            val today = monday.plusDays(20).toString()
            assertEquals(
                statisticsSummary(plain, today, range, onlyWeight, null),
                statisticsSummary(joined, today, range, onlyWeight, null),
            )
        }
    }

    @Test fun anExplicitEmptyParticipationPeriodSurvivesStorageAndStaysOutsideTheWalk() {
        val tickedOnce = history(*(0L until 21L).filter { it != 3L }.toLongArray(), checklist = medication.copy(rewards = DefaultChecklistRewards.copy(periods = emptyList())))
        val decoded = AppStateJsonCodec.decodeVersion32(AppStateJsonCodec.encode(tickedOnce))
        assertEquals(DefaultChecklistRewards.copy(periods = emptyList()), decoded.checklists.single().rewards)
        val withoutChecklist = tickedOnce.copy(checklists = emptyList(), checklistDays = emptyList())
        val result = streak(decoded, 7)
        assertFalse(result.standings.containsKey(medicationRef))
        assertEquals(streak(withoutChecklist, 7), result)
    }

    /** A checklist that joined is due from that day, so a day before its first tick is still judged. */
    @Test fun joiningMakesItDueFromThatDayWhateverItRecords() {
        val ticksFromDay3 = history(0, 1, 2)
        val joined = joinRewards(ticksFromDay3, medication.id, monday)
        val days = streak(joined, 20).history.associateBy { it.date }
        (0L..2L).forEach { assertTrue(days.getValue(monday.plusDays(it)).mark != MoDayMark.Perfect) }
        assertEquals(setOf(medicationRef), days.getValue(monday).coveredTrackers)

        // Removing its earliest tick does not move the day it began.
        val fewer = joined.copy(checklistDays = joined.checklistDays.filterNot { it.dateKey == monday.plusDays(3).toString() })
        assertEquals(setOf(medicationRef), streak(fewer, 20).history.single { it.date == monday }.coveredTrackers)
    }

    /** Outside its season nothing is due, so the day is neither kept nor a gap and earns nothing. */
    @Test fun aDayWithNothingDueIsNotJudged() {
        val outOfSeason = medication.copy(items = medication.items.map { it.copy(months = setOf(java.time.Month.JANUARY)) })
        val joined = joinRewards(history(checklist = outOfSeason), outOfSeason.id, monday)
        val withoutChecklist = history().copy(checklists = emptyList(), checklistDays = emptyList())
        val result = streak(joined, 20)

        val outOfSeasonRef = ViewRef.Checklist(outOfSeason.id).trackerRef
        assertEquals(1, result.standings[outOfSeasonRef]?.passes)
        assertEquals(streak(withoutChecklist, 20), result.copy(
            standings = result.standings - outOfSeasonRef,
            freeDaysLeftByTracker = result.freeDaysLeftByTracker - outOfSeasonRef,
        ))
    }

    /** An entry without periods took part over its whole history; leaving keeps that and ends it yesterday. */
    @Test fun leavingAnUndatedEntryEndsItAfterYesterday() {
        val undated = history(5, 6, 15, 16, checklist = medication.copy(rewards = DefaultChecklistRewards))
        val left = leaveRewards(undated, medication.id, monday.plusDays(10))
        assertEquals(listOf(RewardPeriod(monday, monday.plusDays(9))), left.checklists.single().rewards?.periods)
        assertEquals(
            streak(undated, 20).history.filter { it.date < monday.plusDays(10) },
            streak(left, 20).history.filter { it.date < monday.plusDays(10) },
        )
        val nothingRecorded = undated.copy(checklistDays = emptyList())
        assertEquals(emptyList<RewardPeriod>(), leaveRewards(nothingRecorded, medication.id, monday.plusDays(10)).checklists.single().rewards?.periods)
    }

    /** Only Mo's tasks can be set aside, so an open joined checklist keeps today from looking covered. */
    @Test fun anOpenJoinedChecklistKeepsTodayFromLookingSetAside() {
        val today = monday.plusDays(20)
        val noWeightToday = history().let { it.copy(measurementLogs = it.recordsOf("weight").filterNot { log -> log.dateKey == today.toString() }.asMeasurementLogs("weight")) }
        fun setAside(state: AppState) = moMood(state, onlyWeight, today, chosenTrackers = setOf(weightRef)).todayIsSetAside

        val openToday = joinRewards(noWeightToday.copy(checklistDays = noWeightToday.checklistDays.filterNot { it.dateKey == today.toString() }), medication.id, monday)
        assertTrue(streak(openToday, 20).medication()?.openToday == true)
        assertFalse(setAside(openToday))
        assertTrue(setAside(joinRewards(noWeightToday, medication.id, monday)))
    }

    /** A checklist that left or is hidden keeps its bank for later, out of sight. */
    @Test fun onlyAShownChecklistThatTakesPartTodayShowsItsBank() {
        val joined = joinRewards(history(), medication.id, monday)
        assertTrue(streak(joined, 20).standings.containsKey(medicationRef))
        assertFalse(streak(leaveRewards(joined, medication.id, monday.plusDays(20)), 20).standings.containsKey(medicationRef))
        assertFalse(streak(joined, 20, onlyWeight.hidingChecklist(medication.id)).standings.containsKey(medicationRef))
    }

    /** Away, an item that is excused is not due, so a day it missed there is no gap; one that stays due is judged as ever. */
    @Test fun awayDaysExcuseOnlyAnItemThatSaysSo() {
        val away = HolidaySchedule(listOf(DayRange(monday.plusDays(12), monday.plusDays(13))))
        fun day12(checklist: ChecklistConfiguration): com.example.workouttracker.domain.mo.MoDayRecord {
            val joined = joinRewards(history(12, 13, checklist = checklist), checklist.id, monday)
            return habitStreak(joined, onlyWeight, monday.plusDays(20), away).history.single { it.date == monday.plusDays(12) }
        }
        assertEquals(MoDayMark.Perfect, day12(excusedMedication).mark)
        assertEquals(setOf(medicationRef), day12(medication).coveredTrackers)
        // Away days change no record.
        assertEquals(history(12, 13).checklistDays, joinRewards(history(12, 13), medication.id, monday).checklistDays)
    }

    /** Away, a checklist whose every item is excused asks nothing: with nothing else shown, the days are NothingDue and the streak does not grow. */
    @Test fun anExcusedChecklistAloneGrowsNothingWhileAway() {
        val away = HolidaySchedule(listOf(DayRange(monday, monday.plusDays(1))))
        val nothingElse = showingOnly()
        val excused = excusedMedication
        val joined = joinRewards(AppState(checklists = listOf(excused)), excused.id, monday)
        val result = habitStreak(joined, nothingElse, monday.plusDays(1), away)
        assertEquals(listOf(MoDayMark.NothingDue), result.history.filter { it.date < monday.plusDays(1) }.map { it.mark })
        assertEquals(0, result.days)
        // A tick on an excused day earns nothing toward the next pass, which its bank still shows.
        val ticked = joined.copy(checklistDays = listOf(ChecklistDay(excused.id, monday.plusDays(1).toString(), 1L, setOf("morning/levo"))))
        val excusedRef = ViewRef.Checklist(excused.id).trackerRef
        assertEquals(0, habitStreak(ticked, nothingElse, monday.plusDays(2), away).standings.getValue(excusedRef).nextToken?.done)
    }

    /**
     * A changed earning rule starts on its day: the days before keep their judgments and passes,
     * and a smaller bank holds no more than it allows from its first day on.
     */
    @Test fun changedRewardRulesStartOnTheirDayAndKeepWhatWasEarned() {
        val joined = joinRewards(history(), medication.id, monday)
        val before = streak(joined, 20)
        assertEquals(3, before.medication()?.passes)

        val changeDay = monday.plusDays(15)
        val changed = joined.applyEdit(medication.id, ChecklistEdit.SetRewardRules(maxPasses = 1, everyKeptDays = 3), changeDay)
        val after = streak(changed, 20)
        assertEquals(before.history.filter { it.date < changeDay }, after.history.filter { it.date < changeDay })
        // Seen from the day before, nothing changed.
        assertEquals(streak(joined, 14), streak(changed, 14))
        assertEquals(1, after.medication()?.passes)
        assertEquals(1, after.medication()?.passCapacity)
        assertEquals(3, after.medication()?.nextToken?.goal)
    }

    /** Excusing an item while away starts on its day; an away day before it stays due. */
    @Test fun excusingWhileAwayStartsOnItsDay() {
        val away = HolidaySchedule(listOf(DayRange(monday.plusDays(12), monday.plusDays(13))))
        val joined = joinRewards(history(12, 13), medication.id, monday)
        val excusedFrom13 = joined.applyEdit(medication.id, ChecklistEdit.SetItemWhenAway("levo", WhenAway.Excused), monday.plusDays(13))
        val days = habitStreak(excusedFrom13, onlyWeight, monday.plusDays(20), away).history.associateBy { it.date }
        assertEquals(setOf(medicationRef), days.getValue(monday.plusDays(12)).coveredTrackers)
        assertEquals(MoDayMark.Perfect, days.getValue(monday.plusDays(13)).mark)
    }
}

package com.example.workouttracker.domain.mo

import com.example.workouttracker.domain.compatibility.free

import com.example.workouttracker.domain.compatibility.kept

import com.example.workouttracker.domain.compatibility.history

import com.example.workouttracker.domain.AppState
import com.example.workouttracker.domain.settings.ViewRef
import com.example.workouttracker.domain.trackerRef
import com.example.workouttracker.domain.checklist.AboutDay
import com.example.workouttracker.domain.checklist.ChecklistConfiguration
import com.example.workouttracker.domain.checklist.ChecklistDay
import com.example.workouttracker.domain.checklist.ChecklistEdit
import com.example.workouttracker.domain.checklist.ChecklistItem
import com.example.workouttracker.domain.checklist.ChecklistRewards
import com.example.workouttracker.domain.checklist.Checkpoint
import com.example.workouttracker.domain.checklist.CheckpointTime
import com.example.workouttracker.domain.checklist.Dated
import com.example.workouttracker.domain.checklist.RewardPeriod
import com.example.workouttracker.domain.checklist.ViewHue
import com.example.workouttracker.domain.checklist.ViewIcon
import com.example.workouttracker.domain.checklist.applyEdit
import com.example.workouttracker.domain.checklist.checklist
import com.example.workouttracker.domain.checklist.progressOf
import com.example.workouttracker.domain.holiday.DayRange
import com.example.workouttracker.domain.holiday.HolidaySchedule
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A checklist about yesterday, ticked the morning after: the reward walk judges due day T by the
 * record of T−1, as it judges Eating by yesterday's reflection, and the record stays T−1's.
 */
class LookBackRewardsTest {
    private val monday = LocalDate.parse("2026-10-12")
    private fun day(offset: Int): LocalDate = monday.plusDays(offset.toLong())
    private val lastNight = ChecklistConfiguration(
        id = "chk_night",
        label = "Yesterday",
        icon = ViewIcon.Journal,
        hue = ViewHue.Plum,
        checkpoints = listOf(Checkpoint("cpt_morning", CheckpointTime.Fixed(LocalTime.of(8, 0)), remind = true)),
        items = listOf(ChecklistItem("itm_phone", "No phone in bed"), ChecklistItem("itm_violin", "Practiced violin")),
        rewards = ChecklistRewards(maxPasses = 3, everyKeptDays = 7, periods = listOf(RewardPeriod(monday))),
        about = Dated.always(AboutDay.Yesterday),
    )
    private val lastNightRef = ViewRef.Checklist(lastNight.id).trackerRef
    private val complete = setOf("cpt_morning/itm_phone", "cpt_morning/itm_violin")

    private fun recorded(vararg days: Pair<Int, Set<String>>) =
        days.map { (offset, ticks) -> ChecklistDay(lastNight.id, day(offset).toString(), offset.toLong() + 10, ticks) }

    private fun streak(checklist: ChecklistConfiguration, records: List<ChecklistDay>, today: LocalDate): MoStreak =
        habitStreak(AppState(checklists = listOf(checklist), checklistDays = records), only(), today)

    private fun marks(streak: MoStreak): Map<LocalDate, MoDayMark> = streak.history.associate { it.date to it.mark }

    @Test fun aDueDayIsJudgedByTheRecordOfTheDayBefore() {
        val records = recorded(-1 to complete, 0 to complete, 1 to setOf("cpt_morning/itm_phone"))
        val walk = streak(lastNight, records, day(3))
        val marks = marks(walk)
        assertEquals("Monday asks for Sunday's record", MoDayMark.Perfect, marks[day(0)])
        assertEquals("Tuesday asks for Monday's", MoDayMark.Perfect, marks[day(1)])
        assertEquals("Wednesday asks for Tuesday's, the violin missing", MoDayMark.Protected, marks[day(2)])
        assertEquals(setOf(lastNightRef), walk.history.single { it.date == day(2) }.coveredTrackers)
        assertEquals(2, walk.days)
        assertEquals(0, walk.standings[lastNightRef]?.passes)
        // Thursday asks for Wednesday, which has no record yet; a tick for Wednesday answers it.
        assertEquals(true, walk.standings[lastNightRef]?.openToday)
        assertEquals(false, streak(lastNight, records + recorded(2 to complete), day(3)).standings[lastNightRef]?.openToday)
        // The same records on a checklist about today are judged on their own day.
        val aboutToday = marks(streak(lastNight.copy(about = Dated.always(AboutDay.Today)), records, day(3)))
        assertEquals(listOf(MoDayMark.Perfect, MoDayMark.Protected, MoDayMark.Frozen), (0..2).map { aboutToday[day(it)] })
    }

    /** The record day's rules judge it: a due day asking about an away day reads that day's holiday allowance. */
    @Test fun aDueDayAskingAboutAnAwayDayReadsThatDaysAllowance() {
        val lowered = lastNight.copy(items = listOf(lastNight.items[0], lastNight.items[1].copy(awayTimes = 3, whenAway = Dated.always(com.example.workouttracker.domain.checklist.WhenAway.LowerTarget))))
        val away = HolidaySchedule(listOf(DayRange(monday, day(6))))
        // Sunday complete, then the phone kept out of bed every night and the violin never touched: Monday to Thursday are its free days, Friday to Sunday gaps.
        val records = recorded(-1 to complete, *(0..6).map { it to setOf("cpt_morning/itm_phone") }.toTypedArray())
        val state = AppState(checklists = listOf(lowered), checklistDays = records)
        val marks = marks(habitStreak(state, only(), day(7), away))
        assertEquals(List(5) { MoDayMark.Perfect }, (0..4).map { marks[day(it)] })
        assertTrue((5..6).none { marks[day(it)] == MoDayMark.Perfect })
        assertTrue(state.progressOf(lowered, day(3).toString(), away).weekItems.single().isFree)
        assertTrue(state.progressOf(lowered, day(4).toString(), away).weekItems.single().isGap)
    }

    /** Created on Monday, it never asks for a day before it existed: Monday asks about Sunday, which had nothing due. */
    @Test fun theFirstDueDayIsTheDayAfterTheChecklistStarts() {
        val created = lastNight.copy(items = lastNight.items.map { it.copy(from = monday) })
        val walk = streak(created, recorded(0 to complete), day(2))
        val marks = marks(walk)
        assertEquals(MoDayMark.NothingDue, marks[day(0)])
        assertEquals(MoDayMark.Perfect, marks[day(1)])
        assertEquals(1, walk.days)
        assertEquals(true, walk.standings[ViewRef.Checklist(created.id).trackerRef]?.openToday)
    }

    /**
     * Switching on Thursday: Thursday asks for Wednesday's record, which Wednesday already asked
     * for, so Thursday is not judged; Thursday's own record is judged once, on Friday.
     */
    @Test fun theDayOfASwitchToYesterdayIsNotJudgedAndNoRecordIsJudgedTwice() {
        val aboutToday = lastNight.copy(about = Dated.always(AboutDay.Today))
        val thursday = day(3)
        val switched = AppState(checklists = listOf(aboutToday)).applyEdit(aboutToday.id, ChecklistEdit.SetAbout(AboutDay.Yesterday), thursday).checklist(aboutToday.id)!!
        val records = recorded(0 to complete, 1 to complete, 2 to complete)
        val walk = streak(switched, records, day(5))
        val marks = marks(walk)
        assertEquals(List(3) { MoDayMark.Perfect }, (0..2).map { marks[day(it)] })
        assertEquals(MoDayMark.NothingDue, marks[thursday])
        assertEquals("Friday asks for Thursday's empty record", MoDayMark.Protected, marks[day(4)])
        assertEquals(3, walk.days)
        // Staying about today, Thursday would have been judged by its own empty record.
        assertEquals(MoDayMark.Protected, marks(streak(aboutToday, records, day(5)))[thursday])
    }

    /** Switching back on Monday: Monday asks for its own record again, and Sunday's record is asked for by no day, so an empty Sunday costs nothing. */
    @Test fun switchingBackLeavesTheRecordOfTheDayBeforeUnasked() {
        val nextMonday = day(7)
        val back = AppState(checklists = listOf(lastNight)).applyEdit(lastNight.id, ChecklistEdit.SetAbout(AboutDay.Today), nextMonday).checklist(lastNight.id)!!
        val records = recorded(*(-1..8).filter { it != 6 }.map { it to complete }.toTypedArray())
        val walk = streak(back, records, day(9))
        assertTrue(walk.history.all { it.mark == MoDayMark.Perfect || it.mark == MoDayMark.Open })
        assertEquals(9, walk.days)
        // Sunday's record is incomplete as a fact; only nobody asked for it.
        assertEquals(false, AppState(checklists = listOf(back), checklistDays = records).progressOf(back, day(6).toString(), HolidaySchedule()).isComplete)
    }

    /**
     * A sick day asks nothing, whichever day asks about it: Tuesday asking about a sick Monday is
     * not judged, by its daily items either, and a sick Tuesday is the streak's sick day as for
     * every view, so the healthy Monday it would have asked about is asked for by no day.
     */
    @Test fun aSickRecordDayIsNotJudgedAndASickDueDayIsTheStreaksSickDay() {
        val sickMonday = HolidaySchedule().withSickDays(DayRange(monday, monday))
        fun walk(records: List<ChecklistDay>, daysOff: HolidaySchedule) =
            habitStreak(AppState(checklists = listOf(lastNight), checklistDays = records), only(), day(3), daysOff)
        // Monday's record empty: healthy, Tuesday would spend the pass on it.
        val mondayMissed = recorded(-1 to complete, 1 to complete)
        assertEquals(MoDayMark.Protected, marks(walk(mondayMissed, HolidaySchedule()))[day(1)])
        val spared = walk(mondayMissed, sickMonday)
        // Monday is the streak's sick day as a due day too, and Tuesday, asking about it, is not judged.
        assertEquals(MoDayMark.Sick to MoDayMark.NothingDue, marks(spared)[day(0)] to marks(spared)[day(1)])
        assertEquals(1, spared.standings[lastNightRef]?.passes)
        // Monday's record complete: sick, it earns nothing on Tuesday either; healthy, every day would have kept.
        val allComplete = recorded(-1 to complete, 0 to complete, 1 to complete)
        assertEquals(3, walk(allComplete, HolidaySchedule()).days)
        val keptWhileSick = walk(allComplete, sickMonday)
        assertEquals(MoDayMark.NothingDue, marks(keptWhileSick)[day(1)])
        assertEquals(1, keptWhileSick.days)
        // A sick Tuesday: Monday's empty record is asked for by no day, and nothing is spent.
        val sickTuesday = walk(mondayMissed, HolidaySchedule().withSickDays(DayRange(day(1), day(1))))
        assertEquals(MoDayMark.Sick, marks(sickTuesday)[day(1)])
        assertEquals(1, sickTuesday.standings[lastNightRef]?.passes)
    }

    /** Facts stay on their day: the progress of every day reads the same whatever the checklist is about; only the judgement moves. */
    @Test fun whatAChecklistIsAboutNeverMovesARecord() {
        val records = recorded(-1 to complete, 0 to setOf("cpt_morning/itm_phone"), 2 to complete)
        val aboutToday = lastNight.copy(about = Dated.always(AboutDay.Today))
        (-2..4).map(::day).forEach { date ->
            val yesterdays = AppState(checklists = listOf(lastNight), checklistDays = records).progressOf(lastNight, date.toString(), HolidaySchedule())
            val todays = AppState(checklists = listOf(aboutToday), checklistDays = records).progressOf(aboutToday, date.toString(), HolidaySchedule())
            assertEquals("Progress on $date", todays, yesterdays)
        }
    }
}

package com.example.workouttracker.app

import com.example.workouttracker.domain.TrackerRef
import com.example.workouttracker.domain.TrackerTypeId
import com.example.workouttracker.domain.AppState
import com.example.workouttracker.domain.DayProgress
import com.example.workouttracker.domain.checklist.AboutDay
import com.example.workouttracker.domain.checklist.ChecklistConfiguration
import com.example.workouttracker.domain.checklist.ChecklistDay
import com.example.workouttracker.domain.checklist.ChecklistEdit
import com.example.workouttracker.domain.checklist.ChecklistItem
import com.example.workouttracker.domain.checklist.ChecklistRewards
import com.example.workouttracker.domain.checklist.ChecklistTarget
import com.example.workouttracker.domain.checklist.Checkpoint
import com.example.workouttracker.domain.checklist.CheckpointTime
import com.example.workouttracker.domain.checklist.Dated
import com.example.workouttracker.domain.checklist.RewardPeriod
import com.example.workouttracker.domain.checklist.ViewHue
import com.example.workouttracker.domain.checklist.ViewIcon
import com.example.workouttracker.domain.checklist.WhenAway
import com.example.workouttracker.domain.checklist.applyEdit
import com.example.workouttracker.domain.checklist.leaveRewards
import com.example.workouttracker.domain.holiday.DayRange
import com.example.workouttracker.domain.holiday.HolidaySchedule
import com.example.workouttracker.domain.mo.MoEvaluation
import com.example.workouttracker.domain.checklist.MoMotif
import com.example.workouttracker.domain.mo.TrackerMoPolicy
import com.example.workouttracker.domain.mo.habitStreak
import com.example.workouttracker.feature.tracker.TrackerHomeStatus
import com.example.workouttracker.domain.mo.only
import com.example.workouttracker.domain.settings.FeatureVisibilitySettings
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A checklist a person made wears the badge a built-in task does once it takes part in the streak,
 * and Home reads it the way the streak judges it, so the two never disagree.
 */
class HomeChecklistBadgeTest {
    private val monday = LocalDate.parse("2026-10-12")
    private val tuesday = monday.plusDays(1)
    private val medication = ChecklistConfiguration(
        id = "chk_med",
        label = "Medication",
        icon = ViewIcon.Checklist,
        hue = ViewHue.Sky,
        checkpoints = listOf(
            Checkpoint("cpt_morning", CheckpointTime.Fixed(LocalTime.of(8, 0)), remind = true),
            Checkpoint("cpt_evening", CheckpointTime.Fixed(LocalTime.of(21, 0)), remind = true),
        ),
        items = listOf(ChecklistItem("itm_levo", "Levothyroxine"), ChecklistItem("itm_iron", "Iron")),
        rewards = ChecklistRewards(maxPasses = 3, everyKeptDays = 7, periods = listOf(RewardPeriod(monday))),
    )
    private val morning = setOf("cpt_morning/itm_levo", "cpt_morning/itm_iron")
    private val complete = morning + setOf("cpt_evening/itm_levo", "cpt_evening/itm_iron")

    private fun state(checklist: ChecklistConfiguration = medication, vararg records: Pair<LocalDate, Set<String>>) = AppState(
        checklists = listOf(checklist),
        checklistDays = records.map { (day, ticks) -> ChecklistDay(checklist.id, day.toString(), 10L, ticks) },
    )

    /** Home's reading for the medication on [today], checked against the walk: a socket exactly when the walk holds it open. */
    private fun badge(
        state: AppState,
        today: LocalDate,
        daysOff: HolidaySchedule = HolidaySchedule(),
        visibility: FeatureVisibilitySettings = only(),
    ): DayProgress? {
        val streak = habitStreak(state, visibility, today, daysOff)
        val progress = checklistProgressOn(state, streak, today.toString(), daysOff)[medication.id]
        assertEquals("open on $today", streak.standings[TrackerRef(TrackerTypeId("checklist"), medication.id)]?.openToday == true, progress?.isComplete == false)
        return progress
    }

    @Test fun aCompleteDayWearsTheCoinAndAPartlyDoneDayShowsItsCheckpoints() {
        assertEquals(DayProgress(done = 0, parts = 2), badge(state(), monday))
        assertEquals(DayProgress(done = 1, parts = 2), badge(state(medication, monday to morning), monday))
        assertEquals(DayProgress(done = 2, parts = 2), badge(state(medication, monday to complete), monday))
        // One checkpoint is one whole part, as for Supplements.
        val oneCheckpoint = medication.copy(checkpoints = medication.checkpoints.take(1))
        assertEquals(DayProgress(done = 0, parts = 1), badge(state(oneCheckpoint), monday))
        assertEquals(DayProgress(done = 1, parts = 1), badge(state(oneCheckpoint, monday to morning), monday))
    }

    /** The tile Home draws comes from Mo's own progress, so a partly ticked checkpoint can never wear the coin while he waits for the rest. */
    @Test fun aPartlyTickedCheckpointIsOpenOnHomeAndForMo() {
        val oneCheckpoint = medication.copy(checkpoints = medication.checkpoints.take(1), mo = TrackerMoPolicy(true, MoMotif.Pills))
        val ref = TrackerRef(TrackerTypeId("checklist"), medication.id)
        fun homeAndMo(ticks: Set<String>): Pair<TrackerHomeStatus, Boolean> {
            val mood = MoEvaluation(state(oneCheckpoint, monday to ticks), FeatureVisibilitySettings(), monday).moodWithTrackerChoices(emptySet())
            return trackerHomeStatus(mood.trackerProgress[ref]) to (ref in mood.missingTrackersToday)
        }
        assertEquals(TrackerHomeStatus.Open(done = 0, parts = 1) to true, homeAndMo(setOf("cpt_morning/itm_levo")))
        assertEquals(TrackerHomeStatus.Done to false, homeAndMo(morning))
    }

    @Test fun aDayItAsksNothingHasNoBadgeThoughItTakesPart() {
        val weekends = medication.copy(items = medication.items.map { it.copy(target = ChecklistTarget.Weekdays(setOf(DayOfWeek.SATURDAY))) })
        val excused = medication.copy(items = medication.items.map { it.copy(whenAway = Dated.always(WhenAway.Excused)) })
        val away = HolidaySchedule(listOf(DayRange(monday, monday)))
        listOf(state(weekends) to HolidaySchedule(), state(excused) to away).forEach { (state, daysOff) ->
            assertNull(badge(state, monday, daysOff))
            assertTrue(TrackerRef(TrackerTypeId("checklist"), medication.id) in habitStreak(state, only(), monday, daysOff).standings)
        }
    }

    @Test fun aSickDayHasNoBadgeWhateverWasRecorded() {
        val sickMonday = HolidaySchedule().withSickDays(DayRange(monday, monday))
        assertNull(badge(state(), monday, sickMonday))
        assertNull(badge(state(medication, monday to complete), monday, sickMonday))
        assertEquals(DayProgress(done = 0, parts = 2), badge(state(medication, monday to complete), tuesday, sickMonday))
    }

    @Test fun aChecklistAboutYesterdayWearsTheBadgeOfTheRecordTodayAsksFor() {
        val lastNight = medication.copy(about = Dated.always(AboutDay.Yesterday))
        assertEquals(DayProgress(done = 2, parts = 2), badge(state(lastNight, monday to complete), tuesday))
        assertEquals(DayProgress(done = 1, parts = 2), badge(state(lastNight, monday to morning, tuesday to complete), tuesday))
        // A sick day asks nothing, whichever day asks about it.
        val sickMonday = HolidaySchedule().withSickDays(DayRange(monday, monday))
        assertNull(badge(state(lastNight, monday to complete), tuesday, sickMonday))
        assertNull(badge(state(lastNight, monday to complete), tuesday, HolidaySchedule().withSickDays(DayRange(tuesday, tuesday))))
    }

    /** Monday asked for its own record; switching on Tuesday leaves Tuesday unjudged, so Home shows nothing it would not count. */
    @Test fun theDayOfASwitchToYesterdayShowsNoBadgeAsTheStreakJudgesNothing() {
        val switched = state(medication, monday to complete).applyEdit(medication.id, ChecklistEdit.SetAbout(AboutDay.Yesterday), tuesday)
        assertNull(badge(switched, tuesday))
        assertEquals(DayProgress(done = 0, parts = 2), badge(switched, tuesday.plusDays(1)))
    }

    @Test fun aChecklistOutsideTheStreakHasNoBadge() {
        val records = arrayOf(monday to complete, tuesday to morning)
        assertNull(badge(state(medication.copy(rewards = null), *records), tuesday))
        assertNull(badge(leaveRewards(state(medication, *records), medication.id, tuesday), tuesday))
        val hidden = FeatureVisibilitySettings().hidingChecklist(medication.id)
        assertNull(badge(state(medication, *records), tuesday, visibility = hidden))
        assertFalse(TrackerRef(TrackerTypeId("checklist"), medication.id) in habitStreak(state(medication, *records), hidden, tuesday).standings)
    }

    /** Without periods a checklist is judged from its first record, as a built-in task is; until then the walk holds nothing open, and nor does Home. */
    @Test fun aChecklistJudgedFromItsFirstRecordHasNoBadgeBeforeIt() {
        val sinceFirstRecord = medication.copy(rewards = ChecklistRewards(maxPasses = 3, everyKeptDays = 7))
        assertNull(badge(state(sinceFirstRecord), monday))
        assertEquals(DayProgress(done = 0, parts = 2), badge(state(sinceFirstRecord, monday to complete), tuesday))
    }
}

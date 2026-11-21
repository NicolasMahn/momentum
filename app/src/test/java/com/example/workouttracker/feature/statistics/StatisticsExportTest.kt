package com.example.workouttracker.feature.statistics

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.workouttracker.core.text.resolve
import com.example.workouttracker.domain.ActivityTrackerRef
import com.example.workouttracker.domain.AppState
import com.example.workouttracker.domain.LoggedViewKind
import com.example.workouttracker.domain.LoggedViewRef
import com.example.workouttracker.domain.activity.ActivityEntry
import com.example.workouttracker.domain.activity.GymActivityId
import com.example.workouttracker.domain.activity.withActivity
import com.example.workouttracker.domain.TrackerRef
import com.example.workouttracker.domain.TrackerTypeId
import com.example.workouttracker.domain.checklist.ChecklistConfiguration
import com.example.workouttracker.domain.checklist.ChecklistDay
import com.example.workouttracker.domain.checklist.ChecklistItem
import com.example.workouttracker.domain.checklist.ChecklistTarget
import com.example.workouttracker.domain.checklist.Checkpoint
import com.example.workouttracker.domain.checklist.CheckpointTime
import com.example.workouttracker.domain.checklist.Dated
import com.example.workouttracker.domain.checklist.ViewHue
import com.example.workouttracker.domain.checklist.ViewIcon
import com.example.workouttracker.domain.holiday.DayRange
import com.example.workouttracker.domain.holiday.HolidaySchedule
import com.example.workouttracker.domain.mo.MoDayMark
import com.example.workouttracker.domain.mo.MoDayRecord
import com.example.workouttracker.domain.mo.MoEvaluation
import com.example.workouttracker.domain.reminder.ReminderSettings
import com.example.workouttracker.domain.settings.FeatureVisibilitySettings
import com.example.workouttracker.domain.time.TimeLog
import com.example.workouttracker.domain.time.testMadeTime
import com.example.workouttracker.domain.trackerRef
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.Locale
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The export says what the app judged, by the plan each day had, over the whole history: a rest
 * day is no miss while the week has rest days left, a covered miss stays a miss, and a timer week
 * is read against that week's own goal.
 */
@RunWith(RobolectricTestRunner::class)
class StatisticsExportTest {
    private val resources = ApplicationProvider.getApplicationContext<Context>().resources
    private val today = LocalDate.parse("2026-10-14")
    private val monday = LocalDate.parse("2026-10-05")
    private val longAgo = LocalDate.parse("2026-03-02")

    // An hour a week, then two hours from the week of the 5th: that week is judged by its own goal.
    private val piano = testMadeTime(goal = 60).let { it.copy(goal = Dated.always(60).changedFrom(monday, 120)) }

    private fun trained(day: LocalDate) = ActivityEntry("e-$day", GymActivityId, "Gym", day.toString(), 1L, durationMinutes = 50)
    private fun played(day: LocalDate, minutes: Int) = TimeLog(piano.id, day.toString(), 1L, minutes)

    private val state = AppState(
        // Monday trained; the seeded week asks five days, so two of the rest are rest days.
        activityEntries = listOf(trained(monday), trained(longAgo)),
        timeLogs = listOf(played(monday.minusWeeks(1), 70), played(monday, 90)),
        time = listOf(piano),
    ).withActivity()

    private fun export(
        history: List<MoDayRecord> = emptyList(),
        state: AppState = this.state,
        views: List<com.example.workouttracker.domain.TrackerRef> = listOf(ActivityTrackerRef, LoggedViewRef(LoggedViewKind.Time, piano.id).trackerRef),
        daysOff: HolidaySchedule = HolidaySchedule(),
    ): String {
        val evaluation = MoEvaluation(state, FeatureVisibilitySettings(), today, holidays = daysOff)
        val input = StatisticsExportInput(
            state = state,
            today = today,
            exportedAt = LocalDateTime.of(today, java.time.LocalTime.of(8, 14)),
            daysOff = daysOff,
            reminders = ReminderSettings(),
            observations = evaluation.observations,
            history = history,
            currentStreak = 0,
            views = views,
        )
        return statisticsExport(input, text = { it.resolve(resources) }, locale = Locale.ENGLISH)
    }

    private fun assertLine(export: String, line: String) = assertTrue("Missing:\n$line\nin:\n$export", line in export.lines())

    @Test fun eachPlanNamesItsGoalAndWhenItChanged() {
        val export = export()
        assertLine(export, "- Goal: 120 minutes a week since 2026-10-05; 60 minutes a week before.")
        assertTrue(export, export.lines().any { it.startsWith("- Goal: 5 training days a week, Monday to Sunday, so up to 2 rest days.") })
    }

    /** A view about yesterday says so, or a reader would take a morning's answer for that morning's day. */
    @Test fun aViewAboutYesterdaySaysWhichDayItsRecordsAreAbout() {
        val eating = com.example.workouttracker.domain.questions.testEatingQuestions().let {
            it.copy(about = Dated.always(com.example.workouttracker.domain.checklist.AboutDay.Today).changedFrom(monday, com.example.workouttracker.domain.checklist.AboutDay.Yesterday))
        }
        val export = export(state = state.copy(questions = listOf(eating)), views = listOf(LoggedViewRef(LoggedViewKind.Questions, eating.id).trackerRef))
        assertLine(export, "- Recorded about: the day before, recorded the morning after since 2026-10-05; the day itself before.")
    }

    @Test fun aDayWithoutTrainingIsARestDayWhileTheWeekHasRestDaysLeftAndMissedAfter() {
        val export = export()
        assertLine(export, "| 2026-10-05 | Gym (50 min) | kept |")
        assertLine(export, "| 2026-10-06 |  | rest day |")
        assertLine(export, "| 2026-10-07 |  | rest day |")
        assertLine(export, "| 2026-10-08 |  | missed |")
    }

    /** A walk with no time logged, then a timed gym visit: the minutes stay with the gym. */
    @Test fun eachActivityOfADayKeepsItsOwnMinutes() {
        val walk = ActivityEntry("e-walk", "act_walk", "Walk", monday.toString(), 0L)
        val export = export(state = state.copy(activityEntries = listOf(walk, trained(monday))))
        assertLine(export, "| 2026-10-05 | Walk; Gym (50 min) | kept |")
    }

    @Test fun aCoveredMissStaysAMissThatWasProtected() {
        val export = export(history = listOf(MoDayRecord(LocalDate.parse("2026-10-09"), MoDayMark.Protected, coveredTrackers = setOf(ActivityTrackerRef))))
        assertLine(export, "| 2026-10-09 |  | missed, protected (token) |")
    }

    @Test fun aTimerWeekIsReadAgainstItsOwnWeeksGoal() {
        val export = export()
        assertTrue(export, export.lines().any { it.startsWith("| 2026-09-28 |") && "70 of 60" in it && it.endsWith("| kept |") })
        assertTrue(export, export.lines().any { it.startsWith("| 2026-10-05 |") && "90 of 120" in it && it.endsWith("| missed |") })
    }

    /**
     * Brush every day and Run three times a week: the goal lets each item say how often it is due,
     * and a day names what was ticked, never "all" while the week item was not done.
     */
    @Test fun aChecklistWithItsOwnTargetsSaysWhatEachAsksAndWhatWasDone() {
        val checklist = ChecklistConfiguration(
            id = "chk_habits",
            label = "Habits",
            icon = ViewIcon.Checklist,
            hue = ViewHue.Sky,
            checkpoints = listOf(Checkpoint("day", CheckpointTime.Fixed(LocalTime.of(9, 0)), remind = false)),
            items = listOf(ChecklistItem("itm_brush", "Brush"), ChecklistItem("itm_run", "Run", target = ChecklistTarget.PerWeek(3))),
        )
        val records = listOf(
            ChecklistDay(checklist.id, "2026-10-05", 1L, setOf("day/itm_brush", "day/itm_run")),
            ChecklistDay(checklist.id, "2026-10-06", 1L, setOf("day/itm_brush")),
        )
        val export = export(state = state.copy(checklists = listOf(checklist), checklistDays = records), views = listOf(TrackerRef(TrackerTypeId("checklist"), checklist.id)))
        assertLine(export, "- Goal: each item as often as stated beside it.")
        assertLine(export, "- Items: Brush (every day), Run (3 times a week).")
        assertLine(export, "| 2026-10-05 | all |  | kept |")
        assertTrue(export, export.lines().any { it.startsWith("| 2026-10-06 | Brush |  |") })
    }

    /** A tick stays in the export after its item's season is moved away from that day. */
    @Test fun aTickOutsideItsItemsSeasonIsStillNamed() {
        val checklist = ChecklistConfiguration(
            id = "chk_teeth",
            label = "Teeth",
            icon = ViewIcon.Checklist,
            hue = ViewHue.Sky,
            checkpoints = listOf(Checkpoint("day", CheckpointTime.Fixed(LocalTime.of(9, 0)), remind = false)),
            items = listOf(ChecklistItem("itm_brush", "Brush"), ChecklistItem("itm_floss", "Floss", months = setOf(java.time.Month.JANUARY))),
        )
        val records = listOf(ChecklistDay(checklist.id, "2026-10-06", 1L, setOf("day/itm_brush", "day/itm_floss")))
        val export = export(state = state.copy(checklists = listOf(checklist), checklistDays = records), views = listOf(TrackerRef(TrackerTypeId("checklist"), checklist.id)))
        assertTrue(export, export.lines().any { it.startsWith("| 2026-10-06 | Brush, Floss |") })
    }

    /** An item whose target changed is kept as two dated versions of one ID; its tick is named once. */
    @Test fun aTickOfAnItemWithSeveralVersionsIsNamedOnce() {
        val checklist = ChecklistConfiguration(
            id = "chk_teeth",
            label = "Teeth",
            icon = ViewIcon.Checklist,
            hue = ViewHue.Sky,
            checkpoints = listOf(Checkpoint("day", CheckpointTime.Fixed(LocalTime.of(9, 0)), remind = false)),
            items = listOf(
                ChecklistItem("itm_brush", "Brush", until = LocalDate.parse("2026-10-09")),
                ChecklistItem("itm_brush", "Brush", from = LocalDate.parse("2026-10-10"), target = ChecklistTarget.PerWeek(3)),
                ChecklistItem("itm_floss", "Floss"),
            ),
        )
        val records = listOf(ChecklistDay(checklist.id, "2026-10-06", 1L, setOf("day/itm_brush")))
        val export = export(state = state.copy(checklists = listOf(checklist), checklistDays = records), views = listOf(TrackerRef(TrackerTypeId("checklist"), checklist.id)))
        assertTrue(export, export.lines().any { it.startsWith("| 2026-10-06 | Brush | Floss |") })
    }

    /** A sick day takes a seventh off its week, as the timer's screen and its judgment read the week. */
    @Test fun aTimerWeekWithASickDayIsReadAgainstWhatTheWeekAsked() {
        val sick = HolidaySchedule(sickDays = listOf(DayRange(LocalDate.parse("2026-10-06"), LocalDate.parse("2026-10-06"))))
        val export = export(daysOff = sick)
        // 120 minutes less a seventh, rounded up.
        assertTrue(export, export.lines().any { it.startsWith("| 2026-10-05 |") && "90 of 103" in it })
    }

    @Test fun theWholeHistoryIsExportedHoweverOldItIs() {
        val export = export()
        assertTrue(export, export.startsWith("# Momentum export, 2026-03-02 to 2026-10-14"))
        assertLine(export, "| 2026-03-02 | Gym (50 min) | kept |")
    }
}

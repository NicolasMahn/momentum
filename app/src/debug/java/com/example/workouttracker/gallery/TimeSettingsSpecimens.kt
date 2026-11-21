package com.example.workouttracker.gallery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import com.example.workouttracker.core.time.parseDateKey
import com.example.workouttracker.core.ui.ViewTheme
import com.example.workouttracker.domain.AppState
import com.example.workouttracker.domain.checklist.CheckpointTime
import com.example.workouttracker.domain.checklist.MoMotif
import com.example.workouttracker.domain.checklist.WhenAway
import com.example.workouttracker.domain.holiday.HolidaySchedule
import com.example.workouttracker.domain.reminder.Bedtime
import com.example.workouttracker.domain.settings.DashboardFeature
import com.example.workouttracker.domain.time.TimeConfiguration
import com.example.workouttracker.domain.time.TimeEdit
import com.example.workouttracker.domain.time.TimeLog
import com.example.workouttracker.domain.time.TimeLogOrder
import com.example.workouttracker.domain.time.TimerMotifs
import com.example.workouttracker.domain.time.TokenStep
import com.example.workouttracker.domain.time.TokenCover
import com.example.workouttracker.domain.time.TokenSteps
import com.example.workouttracker.domain.time.applyingEdits
import com.example.workouttracker.domain.time.applyingTimeEdits
import com.example.workouttracker.domain.time.starterTime
import com.example.workouttracker.feature.checklist.presentation
import com.example.workouttracker.feature.settings.ChecklistPage
import com.example.workouttracker.feature.settings.MadeTimeSettingsPage
import com.example.workouttracker.feature.settings.MoFlavorPicker
import com.example.workouttracker.feature.settings.TimeGoalPart
import com.example.workouttracker.feature.settings.TimeSettingsDraft
import com.example.workouttracker.feature.settings.TimeSetup
import com.example.workouttracker.feature.settings.TimeSetupState
import com.example.workouttracker.feature.settings.TimeStreak
import com.example.workouttracker.feature.settings.ViewWrite
import com.example.workouttracker.reminder.TestReminder
import java.time.LocalDate
import java.time.LocalTime

/**
 * A time view in Settings: its setup at each of its four steps, a made view's page and Reading's
 * on each of their four parts, and the two parts of its own on their own, Goal and Streak, the
 * Goal with one step and with five as well, and the Streak as a made view has it, as Reading has
 * it, with one step, with five and with a percent its step may not have; and Mo's flavor, the
 * control a checklist's Look part and a time view's share, with the motifs each offers.
 */
internal val timeSettingsSpecimens = listOf(
    // The setup as it opens, then on Goal with its name, on Reminders with ninety minutes a week,
    // and on Streak with the reminder on and counting toward the streak, where Create waits.
    GallerySpecimen("TimeSetup, step 1 Look") { TimeSetupSpecimen(ChecklistPage.Look) },
    GallerySpecimen("TimeSetup, step 2 Goal") { TimeSetupSpecimen(ChecklistPage.Items) },
    GallerySpecimen("TimeSetup, step 3 Reminders") { TimeSetupSpecimen(ChecklistPage.Reminders) },
    GallerySpecimen("TimeSetup, step 4 Streak") { TimeSetupSpecimen(ChecklistPage.Streak) },
    // The page on each of its parts: Look and Goal as it was made, Reminders with the reminder on
    // before sleep, and Streak a week after it joined, not due on holiday.
    GallerySpecimen("MadeTimeSettingsPage, Look") { MadeTimeSettingsPageSpecimen(ChecklistPage.Look) },
    GallerySpecimen("MadeTimeSettingsPage, Goal") { MadeTimeSettingsPageSpecimen(ChecklistPage.Items) },
    GallerySpecimen("MadeTimeSettingsPage, Reminders") { MadeTimeSettingsPageSpecimen(ChecklistPage.Reminders) },
    GallerySpecimen("MadeTimeSettingsPage, Reminders, hidden") { MadeTimeSettingsPageSpecimen(ChecklistPage.Reminders, hidden = true) },
    GallerySpecimen("MadeTimeSettingsPage, Streak") { MadeTimeSettingsPageSpecimen(ChecklistPage.Streak) },
    // Reading's page on each of its parts, the same four a made view's has: its look, its hour a
    // week, its reminder, and whether it counts with its bank and its holiday rule.
    GallerySpecimen("MadeTimeSettingsPage, Reading, Look") { ReadingSettingsPageSpecimen(ChecklistPage.Look) },
    GallerySpecimen("MadeTimeSettingsPage, Reading, Goal") { ReadingSettingsPageSpecimen(ChecklistPage.Items) },
    GallerySpecimen("MadeTimeSettingsPage, Reading, Reminders") { ReadingSettingsPageSpecimen(ChecklistPage.Reminders) },
    GallerySpecimen("MadeTimeSettingsPage, Reading, Streak") { ReadingSettingsPageSpecimen(ChecklistPage.Streak) },
    GallerySpecimen("TimeGoalPart, a time view's Goal part") { TimeGoalPartSpecimen() },
    GallerySpecimen("TimeGoalPart, one step") { TimeGoalPartSpecimen(steps = OneStep) },
    GallerySpecimen("TimeGoalPart, five steps") { TimeGoalPartSpecimen(steps = FiveSteps) },
    GallerySpecimen("TimeStreak, a made view's Streak part") { TimeStreakSpecimen(reading = false) },
    GallerySpecimen("TimeStreak, Reading's Streak part") { TimeStreakSpecimen(reading = true) },
    GallerySpecimen("TimeStreak, one step") { TimeStreakSpecimen(reading = false, steps = OneStep) },
    GallerySpecimen("TimeStreak, five steps") { TimeStreakSpecimen(reading = false, steps = FiveSteps) },
    GallerySpecimen("TimeStreak, a refused percent") { TimeStreakSpecimen(reading = false, refusedPercent = "50") },
    GallerySpecimen("TimeStreak, a token covering 20 min") { TimeStreakSpecimen(reading = true, tokenMinutes = 20) },
    GallerySpecimen("MoFlavorPicker, a time view's flavors") { MoFlavorPickerSpecimen(TimerMotifs, testTag = "gallery_time_flavor") },
    GallerySpecimen("MoFlavorPicker, a view Mo does not watch") { MoFlavorPickerSpecimen(MoMotif.entries, testTag = "gallery_unwatched_flavor", watched = false) },
    GallerySpecimen("MoFlavorPicker, a checklist's flavors") { MoFlavorPickerSpecimen(MoMotif.entries, testTag = "gallery_checklist_flavor") },
)

/**
 * The real setup of a new time view at one of its four steps, from the starter the app would hand
 * it beside Reading alone: the timer, an hour a week, the first starter color. On Look it is as it
 * opens, with nothing typed, so Next waits and the three steps ahead are held back. On Goal it has
 * its name; on Reminders ninety minutes a week too; on Streak the reminder is on before sleep as
 * well and it counts toward the streak, so Create is ready. Every step can be walked on from
 * there, and Create keeps the result on show.
 */
@Composable
private fun TimeSetupSpecimen(step: ChecklistPage) {
    val today = requireNotNull(parseDateKey(FixtureTodayKey))
    val starter = remember { AppState(time = listOf(DemoReadingTime)).starterTime("Time") }
    val state = remember {
        val named = listOf(TimeEdit.Rename("Piano"))
        val asking = named + TimeEdit.SetGoal(90)
        val reminding = listOf(TimeEdit.SetRemind(true), TimeEdit.SetReminderTime(CheckpointTime.BeforeSleep(60)), TimeEdit.JoinRewards)
        when (step) {
            ChecklistPage.Look -> TimeSetupState(starter, today)
            ChecklistPage.Items -> TimeSetupState(TimeSettingsDraft(starter, today, named), name = "Piano", nameTouched = true, step = step)
            ChecklistPage.Reminders -> TimeSetupState(TimeSettingsDraft(starter, today, asking), name = "Piano", nameTouched = true, step = step)
            ChecklistPage.Streak -> TimeSetupState(TimeSettingsDraft(starter, today, asking + reminding), name = "Piano", nameTouched = true, step = step)
        }
    }
    var created by remember { mutableStateOf<TimeConfiguration?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ScreenFrame(height = 860.dp) {
            TimeSetup(
                starter = { starter },
                today = today,
                bedtime = Bedtime(LocalTime.of(7, 0), fixedBedtime = null),
                daysOff = HolidaySchedule(),
                onCreate = { made, edits ->
                    created = made.applyingEdits(edits, today)
                    ViewWrite.Stored
                },
                onClose = {},
                reminderRow = { edited, _, onRemindChange -> SpecimenReminderRow(edited.checkpoint?.remind == true, onRemindChange) },
                // The gallery posts nothing: Settings owns the sending and its permission.
                onSendTest = {},
                // The specimen is its own page: nothing asks it to leave.
                leaveRequested = false,
                onLeaveAnswered = {},
                onUnsavedWorkChange = {},
                state = state,
            )
        }
        created?.let { view ->
            Text(
                text = "Created ${view.label}, ${view.goal.current} minutes a week, " +
                    "reminder ${if (view.checkpoint?.remind == true) "on" else "off"}, ${if (view.rewards != null) "in" else "outside"} the streak",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * The real page of a time view a person made, opened on one of its four parts, over a
 * configuration held here: Look, which ends with Mo's flavor on Plain; Goal, with its minutes a
 * week and the line that says what reaching it earns; Reminders, with the reminder on before
 * sleep, its preview and its test; and Streak, a week after it joined and not due on holiday, so
 * the bank's size shows, then the steps a week earns tokens by. Save stores the edits from
 * the fixture's today, and Delete asks first, naming the view and twelve recorded days.
 * [hidden], no reminder of it can come, so there is no test to send; the row that says so is
 * Settings' own.
 */
@Composable
private fun MadeTimeSettingsPageSpecimen(part: ChecklistPage, hidden: Boolean = false) {
    val today = requireNotNull(parseDateKey(FixtureTodayKey))
    var stored by remember {
        mutableStateOf(
            when (part) {
                ChecklistPage.Reminders -> DemoPiano.applyingEdits(listOf(TimeEdit.SetRemind(true), TimeEdit.SetReminderTime(CheckpointTime.BeforeSleep(60))), today)
                ChecklistPage.Streak -> demoPianoInTheStreak(today)
                else -> DemoPiano
            },
        )
    }
    ScreenFrame(height = 860.dp) {
        MadeTimeSettingsPage(
            view = stored,
            today = today,
            loggedDays = 12,
            bedtime = Bedtime(LocalTime.of(7, 0), fixedBedtime = null),
            daysOff = HolidaySchedule(),
            onSave = { edits ->
                stored = stored.applyingEdits(edits, today)
                ViewWrite.Stored
            },
            reminderRow = { edited, _, onRemindChange -> SpecimenReminderRow(edited.checkpoint?.remind == true, onRemindChange) },
            // The gallery posts nothing: Settings owns the sending and its permission.
            onSendTest = ({ _: TestReminder.AtCheckpoint -> }).takeUnless { hidden },
            // The specimen holds one view, so deleting it only closes the dialog.
            onDelete = { true },
            // The specimen is its own page: it has no list to return to, and nothing asks it to leave.
            onClose = {},
            leaveRequested = false,
            onLeaveAnswered = {},
            onUnsavedWorkChange = {},
            opensOn = part,
        )
    }
}

/**
 * Reading's real page, the page a time view a person made has, opened on one of its four parts,
 * over a configuration held here ([LoggedPageSpecimen]): Look, which ends with Mo's flavor on
 * Reading; Goal, its minutes a week with the line that says what reaching it earns; Reminders,
 * when it comes and its switch; and Streak, whether it counts, its bank's size, its holiday rule
 * and the steps a week earns tokens by. Save stores the edits, and Delete asks first, naming
 * Reading and forty logged days. Renamed or recolored, the page wears the change as it is made.
 */
@Composable
private fun ReadingSettingsPageSpecimen(part: ChecklistPage) {
    LoggedPageSpecimen(DemoReadingTime, applying = { edits, today -> applyingEdits(edits, today) }) { stored, today, onSave, reminderRow, bedtime ->
        MadeTimeSettingsPage(
            view = stored,
            today = today,
            loggedDays = 40,
            bedtime = bedtime,
            daysOff = HolidaySchedule(),
            onSave = onSave,
            reminderRow = reminderRow,
            onSendTest = {},
            // The specimen holds one view, so deleting it only closes the dialog.
            onDelete = { true },
            // The specimen is its own page: it has no list to return to, and nothing asks it to leave.
            onClose = {},
            leaveRequested = false,
            onLeaveAnswered = {},
            onUnsavedWorkChange = {},
            opensOn = part,
        )
    }
}

/** The demo piano after it joined the streak a week before [today] and said a holiday is not due. */
private fun demoPianoInTheStreak(today: LocalDate): TimeConfiguration =
    DemoPiano.applyingEdits(listOf(TimeEdit.JoinRewards, TimeEdit.SetWhenAway(WhenAway.Excused)), today.minusWeeks(1))

/** One step, the goal alone adding three tokens, and five, each adding tokens of its own, up to ten times the goal. */
private val OneStep = TokenSteps(listOf(TokenStep(100, 3)))
private val FiveSteps = TokenSteps(listOf(TokenStep(100, 1), TokenStep(150, 1), TokenStep(200, 2), TokenStep(400, 1), TokenStep(1000, 5)))

/**
 * The Goal part a made time view's page and its setup share, on its own, over the piano's draft:
 * its minutes a week as a typed field and the line under it, which follows what is typed. Text
 * that is no whole number of minutes a week can ask stays in the field, which says why in its
 * label's place. With [steps] the piano is in the streak with a bank that earns by them, and the
 * line says what the goal earns and, with more than one, the most a week can earn.
 */
@Composable
private fun TimeGoalPartSpecimen(steps: TokenSteps? = null) {
    val today = requireNotNull(parseDateKey(FixtureTodayKey))
    val draft = remember {
        val view = if (steps == null) DemoPiano else demoPianoInTheStreak(today).applyingEdits(listOf(TimeEdit.SetTokenSteps(steps)), today)
        TimeSettingsDraft(view, today)
    }
    val shown = draft.edited
    ViewTheme(shown.presentation().colors) {
        TimeGoalPart(view = shown, draft = draft, today = today, testTag = "gallery_time")
    }
}

/**
 * The Streak part of a time view on its own, each edit applied from the fixture's today. A made
 * view's is over the piano's draft a week after it joined: the switch, how many tokens its bank
 * holds, the steps a week earns tokens by, one row each with the line that adds them up, and last
 * the holiday choice; no number of kept days. Reading's is the same, counting over its whole
 * history as it always has. With [steps] the bank earns by them instead
 * of the two every bank starts with; with [refusedPercent] that text was typed into the second
 * step's field, which keeps it and says the range the step may have, with Save and "Add a step"
 * held back.
 */
@Composable
private fun TimeStreakSpecimen(reading: Boolean, steps: TokenSteps? = null, refusedPercent: String? = null, tokenMinutes: Int? = null) {
    val today = requireNotNull(parseDateKey(FixtureTodayKey))
    val draft = remember {
        val edits = listOfNotNull(steps?.let(TimeEdit::SetTokenSteps), tokenMinutes?.let { TimeEdit.SetTokenCover(TokenCover.Minutes(it)) })
        val view = (if (reading) DemoReadingTime else demoPianoInTheStreak(today)).applyingEdits(edits, today)
        TimeSettingsDraft(view, today).also { draft -> refusedPercent?.let { draft.typeStepPercent(1, it) } }
    }
    val shown = draft.edited
    ViewTheme(shown.presentation().colors) {
        TimeStreak(view = shown, draft = draft, today = today, testTag = if (reading) "gallery_reading_streak" else "gallery_time_streak")
    }
}

/**
 * "Mo's flavor" on its own, over a choice held here that starts on Plain: Plain alone above the
 * [motifs] it is handed, three to a row. A time view offers the one Mo has something for while a
 * timer runs, and a checklist all six. A view Mo does not watch ([watched] off) says under them
 * what would change that.
 */
@Composable
private fun MoFlavorPickerSpecimen(motifs: List<MoMotif>, testTag: String, watched: Boolean = true) {
    var chosen by remember { mutableStateOf<MoMotif?>(null) }
    ViewTheme(DemoPiano.presentation().colors) {
        MoFlavorPicker(selected = chosen, motifs = motifs, testTag = testTag, watched = watched) { chosen = it }
    }
}

/**
 * The fixture with the demo piano in the streak since three weeks before [today]: twenty-five
 * minutes on every second day since, yesterday among them and today not, so its weeks are short of
 * their ninety minutes by a gap or two and this week's minutes show toward the next token.
 */
internal fun fixtureWithDemoTimeJoined(today: LocalDate): AppState {
    val logged = (1L..21L).filter { it % 2 == 1L }.map { TimeLog(DemoPiano.id, today.minusDays(it).toString(), it, 25) }
    return fixtureAppState
        .copy(time = fixtureAppState.time + DemoPiano, timeLogs = (fixtureAppState.timeLogs + logged).sortedWith(TimeLogOrder))
        .applyingTimeEdits(DemoPiano.id, listOf(TimeEdit.JoinRewards), today.minusDays(21))
}

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
import com.example.workouttracker.domain.checklist.AboutDay
import com.example.workouttracker.domain.checklist.CheckpointTime
import com.example.workouttracker.domain.checklist.WhenAway
import com.example.workouttracker.domain.holiday.HolidaySchedule
import com.example.workouttracker.domain.questions.QuestionEntry
import com.example.workouttracker.domain.questions.QuestionEntryOrder
import com.example.workouttracker.domain.questions.QuestionsConfiguration
import com.example.workouttracker.domain.questions.QuestionsEdit
import com.example.workouttracker.domain.questions.applyingEdits
import com.example.workouttracker.domain.questions.applyingQuestionsEdits
import com.example.workouttracker.domain.questions.starterQuestion
import com.example.workouttracker.domain.questions.starterQuestions
import com.example.workouttracker.domain.reminder.Bedtime
import com.example.workouttracker.domain.settings.DashboardFeature
import com.example.workouttracker.feature.checklist.presentation
import com.example.workouttracker.feature.questions.presentedQuestions
import com.example.workouttracker.feature.settings.ChecklistPage
import com.example.workouttracker.feature.settings.MadeQuestionsSettingsPage
import com.example.workouttracker.feature.settings.NamedQuestionIds
import com.example.workouttracker.feature.settings.QuestionsPart
import com.example.workouttracker.feature.settings.QuestionsSettingsDraft
import com.example.workouttracker.feature.settings.QuestionsSetup
import com.example.workouttracker.feature.settings.QuestionsSetupState
import com.example.workouttracker.feature.settings.ViewWrite
import com.example.workouttracker.reminder.TestReminder
import java.time.LocalDate
import java.time.LocalTime

/**
 * A questions view in Settings: its setup at each of its four steps, a made view's page and
 * Eating's on each of their four parts, and the Questions part on its own, as a made view's draft
 * changes it and with long fully stored prompts.
 */
internal val questionsSettingsSpecimens = listOf(
    // The setup as it opens, then on Questions with its name and one question without its text,
    // where Next waits, then on Reminders and on Streak with the question typed.
    GallerySpecimen("QuestionsSetup, step 1 Look") { QuestionsSetupSpecimen(ChecklistPage.Look) },
    GallerySpecimen("QuestionsSetup, step 2 Questions") { QuestionsSetupSpecimen(ChecklistPage.Items) },
    GallerySpecimen("QuestionsSetup, step 3 Reminders") { QuestionsSetupSpecimen(ChecklistPage.Reminders) },
    GallerySpecimen("QuestionsSetup, step 4 Streak") { QuestionsSetupSpecimen(ChecklistPage.Streak) },
    // The page on each of its parts: Look, its three questions, the reminder on after waking, and
    // the streak a week after it joined.
    GallerySpecimen("MadeQuestionsSettingsPage, Look") { MadeQuestionsSettingsPageSpecimen(ChecklistPage.Look) },
    GallerySpecimen("MadeQuestionsSettingsPage, Questions") { MadeQuestionsSettingsPageSpecimen(ChecklistPage.Items) },
    GallerySpecimen("MadeQuestionsSettingsPage, Reminders") { MadeQuestionsSettingsPageSpecimen(ChecklistPage.Reminders) },
    GallerySpecimen("MadeQuestionsSettingsPage, Streak") { MadeQuestionsSettingsPageSpecimen(ChecklistPage.Streak) },
    // Eating's page on each of its parts, the same four a made view's has, with its questions fixed.
    GallerySpecimen("MadeQuestionsSettingsPage, Eating, Look") { EatingSettingsPageSpecimen(ChecklistPage.Look) },
    GallerySpecimen("MadeQuestionsSettingsPage, Eating, Questions") { EatingSettingsPageSpecimen(ChecklistPage.Items) },
    GallerySpecimen("MadeQuestionsSettingsPage, Eating, Reminders") { EatingSettingsPageSpecimen(ChecklistPage.Reminders) },
    GallerySpecimen("MadeQuestionsSettingsPage, Eating, Streak") { EatingSettingsPageSpecimen(ChecklistPage.Streak) },
    GallerySpecimen("QuestionsPart, a made view's questions") { QuestionsPartSpecimen(longPrompts = false) },
    GallerySpecimen("QuestionsPart, long custom prompts") { QuestionsPartSpecimen(longPrompts = true) },
)

private val FiveAnswers = listOf("Very bad", "Bad", "Okay", "Good", "Very good")

/**
 * The real setup of a new questions view at one of its four steps, from the starter the app would
 * hand it: the journal, about today, one question with five answers and no text. On Look it is as
 * it opens. On Questions it has its name, and the question still waits for its text, so Next
 * waits and the bar says why. On Reminders and on Streak the question is typed; on Streak the
 * reminder is on and it counts toward the streak, so Create is ready. Create keeps the result on
 * show.
 */
@Composable
private fun QuestionsSetupSpecimen(step: ChecklistPage) {
    val today = requireNotNull(parseDateKey(FixtureTodayKey))
    val starter = remember { AppState(questions = listOf(DemoEatingQuestions)).starterQuestions("Questions", starterQuestion("Question", FiveAnswers), today) }
    val state = remember {
        val question = starter.questions.single()
        val named = listOf(QuestionsEdit.Rename("Sleep"))
        val asked = named + QuestionsEdit.RenameQuestion(question.id, "How rested do you feel?")
        val counting = asked + listOf(QuestionsEdit.SetRemind(true), QuestionsEdit.SetReminderTime(CheckpointTime.AfterWake(15)), QuestionsEdit.JoinRewards)
        when (step) {
            ChecklistPage.Items -> QuestionsSetupState(QuestionsSettingsDraft(starter, today, named, unsettled = setOf(question.id)), name = "Sleep", nameTouched = true, step = step)
            ChecklistPage.Reminders -> QuestionsSetupState(QuestionsSettingsDraft(starter, today, asked), name = "Sleep", nameTouched = true, step = step)
            ChecklistPage.Streak -> QuestionsSetupState(QuestionsSettingsDraft(starter, today, counting), name = "Sleep", nameTouched = true, step = step)
            ChecklistPage.Look -> QuestionsSetupState(starter, today)
        }
    }
    var created by remember { mutableStateOf<QuestionsConfiguration?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ScreenFrame(height = 860.dp) {
            QuestionsSetup(
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
                text = "Created ${view.label}, ${view.questions.size} question(s), reminder ${if (view.checkpoint?.remind == true) "on" else "off"}, " +
                    "${if (view.standardRewards != null) "in" else "outside"} the streak",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** The demo sleep view after it joined the streak a week before [today] and said a holiday is not due. */
private fun demoSleepInTheStreak(today: LocalDate): QuestionsConfiguration =
    DemoSleepQuestions.applyingEdits(listOf(QuestionsEdit.JoinRewards, QuestionsEdit.SetWhenAway(WhenAway.Excused)), today.minusWeeks(1))

/**
 * The real page of a questions view a person made, opened on one of its four parts, over a
 * configuration held here: Look; Questions, with its three questions of three, two and five
 * answers and a help text; Reminders, with the reminder on after waking, its preview and its
 * test; and Streak, a week after it joined and not due on holiday. Save stores the edits from the
 * fixture's today, and Delete asks first, naming the view and twelve answered days.
 */
@Composable
private fun MadeQuestionsSettingsPageSpecimen(part: ChecklistPage) {
    val today = requireNotNull(parseDateKey(FixtureTodayKey))
    var stored by remember {
        mutableStateOf(
            when (part) {
                ChecklistPage.Reminders -> DemoSleepQuestions.applyingEdits(listOf(QuestionsEdit.SetRemind(true), QuestionsEdit.SetReminderTime(CheckpointTime.AfterWake(15))), today)
                ChecklistPage.Streak -> demoSleepInTheStreak(today)
                else -> DemoSleepQuestions
            },
        )
    }
    ScreenFrame(height = 860.dp) {
        MadeQuestionsSettingsPage(
            view = stored,
            today = today,
            answeredDays = 12,
            named = NamedQuestionIds(),
            bedtime = Bedtime(LocalTime.of(7, 0), fixedBedtime = null),
            daysOff = HolidaySchedule(),
            onSave = { edits ->
                stored = stored.applyingEdits(edits, today)
                ViewWrite.Stored
            },
            reminderRow = { edited, _, onRemindChange -> SpecimenReminderRow(edited.checkpoint?.remind == true, onRemindChange) },
            // The gallery posts nothing: Settings owns the sending and its permission.
            onSendTest = { _: TestReminder.AtCheckpoint -> },
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
 * Eating's real page, the page a questions view a person made has, opened on one of its four
 * parts, over a configuration held here ([LoggedPageSpecimen]): Look; Questions, its two
 * questions shown in its words and fixed; Reminders, when it comes and its switch; and Streak,
 * whether it counts, its bank's two numbers and its holiday rule. Save stores the edits, and
 * Delete asks first, naming Eating and thirty answered days. Renamed or recolored, the page wears
 * the change as it is made.
 */
@Composable
private fun EatingSettingsPageSpecimen(part: ChecklistPage) {
    LoggedPageSpecimen(DemoEatingQuestions, applying = { edits, today -> applyingEdits(edits, today) }) { stored, today, onSave, reminderRow, bedtime ->
        MadeQuestionsSettingsPage(
            view = stored,
            today = today,
            answeredDays = 30,
            named = NamedQuestionIds(),
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

/** Both specimens edit fully stored prompts, including text longer than the field's visible line. */
@Composable
private fun QuestionsPartSpecimen(longPrompts: Boolean) {
    val today = requireNotNull(parseDateKey(FixtureTodayKey))
    val configured = if (longPrompts) DemoEatingQuestions.copy(
        questions = DemoEatingQuestions.questions.map { question ->
            if (question.id == "quality") question.copy(text = "How would you rate the overall quality of what you ate across the whole day?") else question
        },
    ) else DemoSleepQuestions
    val draft = remember { QuestionsSettingsDraft(configured, today) }
    val shown = draft.edited
    ViewTheme(shown.presentation().colors) {
        QuestionsPart(
            view = shown,
            draft = draft,
            today = today,
            testTag = if (longPrompts) "gallery_questions_part_long" else "gallery_questions_part",
        )
    }
}

/**
 * The fixture with the demo sleep view, about today, in the streak since three weeks before
 * [today]: answered for each day since but the fourth last, and open today.
 */
internal fun fixtureWithDemoQuestionsJoined(today: LocalDate): AppState {
    val answered = (1L..21L).filterNot { it == 4L }.map { QuestionEntry("demo-sleep-joined-$it", DemoSleepQuestions.id, today.minusDays(it).toString(), it, mapOf("qn_rest" to "an_mid")) }
    return fixtureAppState
        .copy(questions = fixtureAppState.questions + DemoSleepQuestions, questionEntries = (fixtureAppState.questionEntries + answered).sortedWith(QuestionEntryOrder))
        .applyingQuestionsEdits(DemoSleepQuestions.id, listOf(QuestionsEdit.JoinRewards, QuestionsEdit.SetAbout(AboutDay.Today)), today.minusDays(21))
}

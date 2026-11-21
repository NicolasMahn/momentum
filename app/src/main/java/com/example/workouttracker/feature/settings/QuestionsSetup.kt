package com.example.workouttracker.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.example.workouttracker.R
import com.example.workouttracker.domain.LoggedView
import com.example.workouttracker.domain.holiday.HolidaySchedule
import com.example.workouttracker.domain.questions.QuestionsConfiguration
import com.example.workouttracker.domain.questions.QuestionsEdit
import com.example.workouttracker.domain.reminder.Bedtime
import com.example.workouttracker.reminder.TestReminder
import java.time.LocalDate

/**
 * The state of a new questions view's guided setup ([LoggedViewSetupState]), which walks the four
 * parts of its page. Look waits for a name. Questions waits for every question to have its text
 * and every answer its label; there is always a question with two answers to give them to, since
 * the starter brings one and the last of each cannot be removed. The other two ask for nothing.
 *
 * The starter's question has five answers and no text of its own yet, so its field starts
 * unsettled, which alone is nothing typed or chosen.
 */
internal class QuestionsSetupState(
    form: QuestionsSettingsDraft,
    name: String? = null,
    nameTouched: Boolean = false,
    step: ChecklistPage = LoggedViewParts.first(),
) : LoggedViewSetupState<QuestionsConfiguration, QuestionsEdit, QuestionsSettingsDraft>(form, LoggedViewParts, name, nameTouched, step) {
    constructor(starter: QuestionsConfiguration, today: LocalDate) : this(QuestionsSettingsDraft(starter, today, unsettled = starter.untypedFields))

    override val formTouched: Boolean get() = form.isDirty || form.unsettled != starter.untypedFields

    override fun isComplete(step: ChecklistPage): Boolean = when (step) {
        ChecklistPage.Look -> name != null
        ChecklistPage.Items -> form.unsettled.isEmpty()
        ChecklistPage.Reminders, ChecklistPage.Streak -> true
    }

    /** Whether [step] waits for a question's or an answer's text: the Questions step, and the last one, which creates the whole form. */
    fun waitsForText(step: ChecklistPage): Boolean = (step == ChecklistPage.Items || steps.isLast(step)) && !isComplete(ChecklistPage.Items)

    companion object {
        val Saver: Saver<QuestionsSetupState, Any> = setupStateSaver(QuestionsSettingsDraft.Saver, ::QuestionsSetupState)
    }
}

/** The fields of a starter that hold no text yet: each of its questions', whose text is still to be typed. */
private val QuestionsConfiguration.untypedFields: Set<String> get() = questions.mapTo(LinkedHashSet()) { it.id }

/**
 * A new questions view set up in four numbered steps, the four parts of its page in order
 * ([MadeQuestionsPart]) over one draft ([LoggedViewSetup]): Look, Questions, Reminders, Streak. It
 * starts from [starter], a one-time copy of sensible defaults under the ID it will keep, with one
 * question that has five answers and no text yet, and nothing is stored until [onCreate] says it
 * is. Look's step waits for a name, the Questions step for every question's and every answer's
 * text, which the bar says while it waits.
 *
 * [reminderRow], [onSendTest], [bedtime] and [daysOff] are the page's. A test sent here is of a
 * view that does not exist yet, so nothing says it opens and its tap opens the app where it was.
 *
 * The setup keeps its own [state], across process death too. A host that shows a setup already
 * under way, as the gallery shows each step, hands one in, and [starter] is then not asked.
 */
@Composable
internal fun QuestionsSetup(
    starter: () -> QuestionsConfiguration,
    today: LocalDate,
    bedtime: Bedtime,
    daysOff: HolidaySchedule,
    onCreate: (starter: QuestionsConfiguration, edits: List<QuestionsEdit>) -> ViewWrite,
    onClose: () -> Unit,
    reminderRow: @Composable (LoggedView, String, (Boolean) -> Unit) -> Unit,
    onSendTest: (TestReminder.AtCheckpoint) -> Unit,
    leaveRequested: Boolean,
    onLeaveAnswered: (discard: Boolean) -> Unit,
    onUnsavedWorkChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    message: String? = null,
    state: QuestionsSetupState = rememberSaveable(saver = QuestionsSetupState.Saver) { QuestionsSetupState(starter(), today) },
) {
    val textNeeded = stringResource(R.string.questions_setup_text_needed)
    LoggedViewSetup(
        state = state,
        today = today,
        testTag = "questions_setup",
        pageKey = SettingsPage.NewQuestions.key,
        createLabel = stringResource(R.string.questions_setup_create),
        discard = DiscardQuestion(
            title = stringResource(R.string.questions_setup_discard_title),
            message = stringResource(R.string.questions_setup_discard_message),
        ),
        onCreate = onCreate,
        onClose = onClose,
        leaveRequested = leaveRequested,
        onLeaveAnswered = onLeaveAnswered,
        onUnsavedWorkChange = onUnsavedWorkChange,
        modifier = modifier,
        message = message,
        hint = textNeeded.takeIf { state.waitsForText(state.steps.step) }?.let { BarLine(it, "questions_setup_hint") },
        stepName = { QuestionsOwnPart.nameOf(it) },
        partTagName = QuestionsOwnPart::tagNameOf,
    ) { step, draft, onPickTime, nameField ->
        MadeQuestionsPart(
            part = step,
            view = draft,
            draft = state.form,
            today = today,
            // Nothing of the view is stored yet, so nothing names an ID its questions could take.
            named = NamedQuestionIds(),
            bedtime = bedtime,
            daysOff = daysOff,
            testTag = "questions_setup",
            reminderRow = reminderRow,
            onSendTest = onSendTest,
            onPickTime = onPickTime,
            nameField = nameField,
        )
    }
}

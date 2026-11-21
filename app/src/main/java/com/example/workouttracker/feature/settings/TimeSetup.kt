package com.example.workouttracker.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.example.workouttracker.R
import com.example.workouttracker.domain.LoggedView
import com.example.workouttracker.domain.holiday.HolidaySchedule
import com.example.workouttracker.domain.reminder.Bedtime
import com.example.workouttracker.domain.time.TimeConfiguration
import com.example.workouttracker.domain.time.TimeEdit
import com.example.workouttracker.reminder.TestReminder
import java.time.LocalDate

/**
 * The state of a new time view's guided setup ([LoggedViewSetupState]), which walks the four
 * parts of a made time view's page. Look waits for a name, Goal for a goal that can be stored and
 * Streak for every step's percent to be one; Reminders asks for nothing.
 */
internal class TimeSetupState(
    form: TimeSettingsDraft,
    name: String? = null,
    nameTouched: Boolean = false,
    step: ChecklistPage = LoggedViewParts.first(),
) : LoggedViewSetupState<TimeConfiguration, TimeEdit, TimeSettingsDraft>(form, LoggedViewParts, name, nameTouched, step) {
    constructor(starter: TimeConfiguration, today: LocalDate) : this(TimeSettingsDraft(starter, today))

    override fun isComplete(step: ChecklistPage): Boolean = when (step) {
        ChecklistPage.Look -> name != null
        ChecklistPage.Items -> !form.goalRefused
        ChecklistPage.Reminders -> true
        ChecklistPage.Streak -> !form.stepsRefused
    }

    companion object {
        val Saver: Saver<TimeSetupState, Any> = setupStateSaver(TimeSettingsDraft.Saver, ::TimeSetupState)
    }
}

/**
 * A new time view set up in four numbered steps, the four parts of its page in order
 * ([MadeTimePart]) over one draft ([LoggedViewSetup]): Look, Goal, Reminders, Streak. It starts
 * from [starter], a one-time copy of sensible defaults under the ID it will keep, and nothing is
 * stored until [onCreate] says it is, given the starter and the edits made; [onClose] is the way
 * out after creating. Look's step waits for a name and Goal's for minutes a week can ask, and each
 * field says itself what it still needs.
 *
 * [reminderRow], [onSendTest], [bedtime] and [daysOff] are the page's. A test sent here is of a
 * view that does not exist yet, so nothing says it opens and its tap opens the app where it was.
 *
 * The setup keeps its own [state], across process death too. A host that shows a setup already
 * under way, as the gallery shows each step, hands one in, and [starter] is then not asked.
 */
@Composable
internal fun TimeSetup(
    starter: () -> TimeConfiguration,
    today: LocalDate,
    bedtime: Bedtime,
    daysOff: HolidaySchedule,
    onCreate: (starter: TimeConfiguration, edits: List<TimeEdit>) -> ViewWrite,
    onClose: () -> Unit,
    reminderRow: @Composable (LoggedView, String, (Boolean) -> Unit) -> Unit,
    onSendTest: (TestReminder.AtCheckpoint) -> Unit,
    leaveRequested: Boolean,
    onLeaveAnswered: (discard: Boolean) -> Unit,
    onUnsavedWorkChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    message: String? = null,
    state: TimeSetupState = rememberSaveable(saver = TimeSetupState.Saver) { TimeSetupState(starter(), today) },
) {
    LoggedViewSetup(
        state = state,
        today = today,
        testTag = "time_setup",
        pageKey = SettingsPage.NewTime.key,
        createLabel = stringResource(R.string.time_setup_create),
        discard = DiscardQuestion(
            title = stringResource(R.string.time_setup_discard_title),
            message = stringResource(R.string.time_setup_discard_message),
        ),
        onCreate = onCreate,
        onClose = onClose,
        leaveRequested = leaveRequested,
        onLeaveAnswered = onLeaveAnswered,
        onUnsavedWorkChange = onUnsavedWorkChange,
        modifier = modifier,
        message = message,
        stepName = { TimeOwnPart.nameOf(it) },
        partTagName = TimeOwnPart::tagNameOf,
    ) { step, draft, onPickTime, nameField ->
        MadeTimePart(
            part = step,
            view = draft,
            draft = state.form,
            today = today,
            bedtime = bedtime,
            daysOff = daysOff,
            testTag = "time_setup",
            reminderRow = reminderRow,
            onSendTest = onSendTest,
            onPickTime = onPickTime,
            nameField = nameField,
        )
    }
}

package de.nicolasmahn.momentum.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import de.nicolasmahn.momentum.R
import de.nicolasmahn.momentum.domain.LoggedView
import de.nicolasmahn.momentum.domain.holiday.HolidaySchedule
import de.nicolasmahn.momentum.domain.measurement.MeasurementConfiguration
import de.nicolasmahn.momentum.domain.measurement.MeasurementEdit
import de.nicolasmahn.momentum.domain.reminder.Bedtime
import de.nicolasmahn.momentum.reminder.TestReminder
import java.time.LocalDate

/**
 * The state of a new measurement's guided setup ([LoggedViewSetupState]), which walks the four
 * parts of a made measurement's page. Look waits for a name, and Unit for a unit that can be
 * stored and a step that is one; the other two ask for nothing.
 */
internal class MeasurementSetupState(
    form: MeasurementDraft,
    name: String? = null,
    nameTouched: Boolean = false,
    step: ChecklistPage = LoggedViewParts.first(),
) : LoggedViewSetupState<MeasurementConfiguration, MeasurementEdit, MeasurementDraft>(form, LoggedViewParts, name, nameTouched, step) {
    constructor(starter: MeasurementConfiguration, today: LocalDate) : this(MeasurementDraft(starter, today))

    override fun isComplete(step: ChecklistPage): Boolean = when (step) {
        ChecklistPage.Look -> name != null
        // Neither field holds text the store would refuse.
        ChecklistPage.Items -> !form.unitRefused && !form.stepRefused
        ChecklistPage.Reminders, ChecklistPage.Streak -> true
    }

    companion object {
        val Saver: Saver<MeasurementSetupState, Any> = setupStateSaver(MeasurementDraft.Saver, ::MeasurementSetupState)
    }
}

/**
 * A new measurement set up in four numbered steps, the four parts of its page in order
 * ([MadeMeasurementPart]) over one draft ([LoggedViewSetup]): Look, Unit, Reminders, Streak. It
 * starts from [starter], a one-time copy of sensible defaults under the ID it will keep, and
 * nothing is stored until [onCreate] says it is, given the starter and the edits made; [onClose]
 * is the way out after creating. Look's step waits for a name, Unit's for a unit that can be
 * stored and a step that is one, and each field says itself what it still needs.
 *
 * [reminderRow], [onSendTest], [bedtime] and [daysOff] are the page's. A test sent here is of a
 * measurement that does not exist yet, so nothing says it opens and its tap opens the app where
 * it was.
 *
 * The setup keeps its own [state], across process death too. A host that shows a setup already
 * under way, as the gallery shows each step, hands one in, and [starter] is then not asked.
 */
@Composable
internal fun MeasurementSetup(
    starter: () -> MeasurementConfiguration,
    today: LocalDate,
    bedtime: Bedtime,
    daysOff: HolidaySchedule,
    onCreate: (starter: MeasurementConfiguration, edits: List<MeasurementEdit>) -> ViewWrite,
    onClose: () -> Unit,
    reminderRow: @Composable (LoggedView, String, (Boolean) -> Unit) -> Unit,
    onSendTest: (TestReminder.AtCheckpoint) -> Unit,
    leaveRequested: Boolean,
    onLeaveAnswered: (discard: Boolean) -> Unit,
    onUnsavedWorkChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    message: String? = null,
    state: MeasurementSetupState = rememberSaveable(saver = MeasurementSetupState.Saver) { MeasurementSetupState(starter(), today) },
) {
    LoggedViewSetup(
        state = state,
        today = today,
        testTag = "measurement_setup",
        pageKey = SettingsPage.NewMeasurement.key,
        createLabel = stringResource(R.string.measurement_setup_create),
        discard = DiscardQuestion(
            title = stringResource(R.string.measurement_setup_discard_title),
            message = stringResource(R.string.measurement_setup_discard_message),
        ),
        onCreate = onCreate,
        onClose = onClose,
        leaveRequested = leaveRequested,
        onLeaveAnswered = onLeaveAnswered,
        onUnsavedWorkChange = onUnsavedWorkChange,
        modifier = modifier,
        message = message,
        stepName = { MeasurementOwnPart.nameOf(it) },
        partTagName = MeasurementOwnPart::tagNameOf,
    ) { step, draft, onPickTime, nameField ->
        MadeMeasurementPart(
            part = step,
            measurement = draft,
            draft = state.form,
            today = today,
            bedtime = bedtime,
            daysOff = daysOff,
            testTag = "measurement_setup",
            reminderRow = reminderRow,
            onSendTest = onSendTest,
            onPickTime = onPickTime,
            nameField = nameField,
        )
    }
}

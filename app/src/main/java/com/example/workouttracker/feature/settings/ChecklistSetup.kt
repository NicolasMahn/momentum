package com.example.workouttracker.feature.settings

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.example.workouttracker.R
import com.example.workouttracker.domain.checklist.isSchedule
import com.example.workouttracker.domain.checklist.ChecklistConfiguration
import com.example.workouttracker.domain.checklist.ChecklistEdit
import com.example.workouttracker.domain.checklist.ChecklistProgress
import com.example.workouttracker.domain.checklist.Checkpoint
import com.example.workouttracker.domain.checklist.emptyCheckpoints
import com.example.workouttracker.domain.checklist.isInUse
import com.example.workouttracker.domain.mo.MoMood
import com.example.workouttracker.domain.reminder.Bedtime
import com.example.workouttracker.feature.checklist.presentation
import com.example.workouttracker.reminder.TestReminder
import java.time.LocalDate

/**
 * The guided setup's state: the step shown, the name as typed, and the checklist as it will be
 * created, which is the [starter] with the edits made on the steps ([form]). Nothing reaches
 * storage until [create] hands both over; a step is left forward only once it is complete, and any
 * step already passed can be shown again. It is kept across process death like a page.
 */
internal class ChecklistSetupState(
    val form: ChecklistDraft,
    name: String? = null,
    step: ChecklistPage = ChecklistPage.Look,
    nameTouched: Boolean = false,
) {
    constructor(starter: ChecklistConfiguration) : this(ChecklistDraft(starter))

    /** The defaults the setup started from; the store dates its checkpoint the day it is created. */
    val starter: ChecklistConfiguration get() = form.stored

    /** The name typed, trimmed, or null while the field is blank; the draft keeps the last one. */
    var name: String? by mutableStateOf(name)
        private set

    /** The four parts of a checklist's page, walked in their order. */
    val steps = SetupSteps(ChecklistPage.entries, step)

    val step: ChecklistPage get() = steps.step

    /** Whether the name field was typed into, so a field nobody touched yet is not told it is missing. */
    var nameTouched: Boolean by mutableStateOf(nameTouched)
        private set

    private var created = false

    val isLastStep: Boolean get() = steps.isLast

    /** Whether anything was typed or chosen, so leaving an untouched setup has nothing to ask about. */
    val touched: Boolean get() = nameTouched || form.hasUnsavedWork

    /** The name is said to be missing once the person typed and left nothing, never before. */
    val showsMissingName: Boolean get() = name == null && nameTouched

    fun draft(today: LocalDate): ChecklistConfiguration = form.draft(today, recordedDates = emptySet())

    fun rename(typed: String?, today: LocalDate) {
        nameTouched = true
        name = typed
        form.named(starter.id, typed)
        if (typed != null) form.edit(ChecklistEdit.Rename(typed), today, recordedDates = emptySet())
    }

    fun edit(edit: ChecklistEdit, today: LocalDate): Boolean = form.edit(edit, today, recordedDates = emptySet())

    /** A step is complete once what it must settle is settled: a name, then an item at every checkpoint. */
    fun isComplete(step: ChecklistPage, today: LocalDate): Boolean = when (step) {
        ChecklistPage.Look -> name != null
        ChecklistPage.Items -> draft(today).let { it.isInUse(today) && it.emptyCheckpoints(today).isEmpty() }
        ChecklistPage.Reminders, ChecklistPage.Streak -> true
    }

    /** Items whose name field was cleared; the checklist's own name is Look's to settle. */
    val blankItemNames: Set<String> get() = form.blankNames - starter.id

    /** Whether a checklist of several checkpoints has one without a name, which its day could not tell apart. */
    fun unnamedCheckpoints(today: LocalDate): Boolean = draft(today).checkpoints.let { it.size > 1 && it.any { checkpoint -> checkpoint.label == null } }

    /** Whether every field is settled: no item without a name, and no text typed but not added. */
    val fieldsSettled: Boolean get() = blankItemNames.isEmpty() && form.pendingText.isEmpty()

    /**
     * What keeps [step] from being left forward, or the last step from creating: only the fields
     * the step owns hold it, so a name cleared on Items never locks Look, whose Next leads back to
     * where it is fixed; the last step waits for the whole form.
     */
    fun waitingFor(step: ChecklistPage, today: LocalDate): SetupWait? {
        val wholeForm = steps.isLast(step)
        return when {
            step == ChecklistPage.Items && !isComplete(step, today) ->
                if (draft(today).isInUse(today)) SetupWait.CheckpointItems else SetupWait.AnItem
            blankItemNames.isNotEmpty() && (step == ChecklistPage.Items || wholeForm) -> SetupWait.ItemNames
            unnamedCheckpoints(today) && (step == ChecklistPage.Items || wholeForm) -> SetupWait.CheckpointNames
            form.pendingText.any(step::owns) || (wholeForm && form.pendingText.isNotEmpty()) -> SetupWait.TypedText
            else -> null
        }
    }

    /** Whether the step can be left forward: complete, with nothing of its own left hanging. */
    fun canContinue(step: ChecklistPage, today: LocalDate): Boolean = isComplete(step, today) && waitingFor(step, today) == null

    fun canCreate(today: LocalDate): Boolean = steps.parts.all { isComplete(it, today) } && fieldsSettled && !unnamedCheckpoints(today)

    /** The steps that can be shown: this one, and the ones completed to get here. */
    fun canShow(target: ChecklistPage): Boolean = steps.canShow(target)

    fun show(target: ChecklistPage) = steps.show(target)

    fun next(today: LocalDate) {
        if (canContinue(step, today)) steps.next()
    }

    fun back() = steps.back()

    /**
     * Stores the checklist through [onCreate], as the starter and the edits made to it, once every
     * step is complete and every field settled, and never twice: a second press after the first
     * succeeded is answered yes without creating anything more. A refused store leaves the setup
     * ready to try again.
     */
    fun create(today: LocalDate, onCreate: (starter: ChecklistConfiguration, edits: List<ChecklistEdit>) -> Boolean): Boolean {
        if (created) return true
        if (!canCreate(today)) return false
        created = onCreate(starter, form.edits)
        return created
    }

    companion object {
        val Saver: Saver<ChecklistSetupState, Any> = listSaver(
            save = { state ->
                val scope = this
                listOf(requireNotNull(with(ChecklistDraft.Saver) { scope.save(state.form) }), state.name.orEmpty(), state.step.name, state.nameTouched)
            },
            restore = {
                ChecklistSetupState(
                    form = requireNotNull(ChecklistDraft.Saver.restore(it[0])),
                    name = (it[1] as String).ifEmpty { null },
                    step = ChecklistPage.valueOf(it[2] as String),
                    nameTouched = it[3] as Boolean,
                )
            },
        )
    }
}

/** What a step of the setup waits for, said on the bar under it ([hint]). */
internal enum class SetupWait(@StringRes val hint: Int) {
    AnItem(R.string.checklist_setup_items_needed),
    ItemNames(R.string.checklist_setup_names_needed),
    TypedText(R.string.checklist_setup_text_pending),
    CheckpointNames(R.string.checklist_setup_checkpoint_names_needed),
    CheckpointItems(R.string.checklist_setup_checkpoint_items_needed),
}

/** Whether [field]'s typed text is this step's to settle before it is left forward: Items owns every "New item". */
private fun ChecklistPage.owns(field: String): Boolean = when (this) {
    ChecklistPage.Items -> field.startsWith(AddItemField)
    ChecklistPage.Look, ChecklistPage.Reminders, ChecklistPage.Streak -> false
}

/**
 * A new checklist set up in four numbered steps, the four pages of its editor in order, with Back
 * and Next at the bottom and the checklist created on the last step. It starts from [starter], a
 * one-time copy of sensible defaults without items, and nothing is stored until [onCreate] says it
 * is, given the starter and the edits made; [onClose] is the way out after creating. Leaving a
 * setup something was typed into asks first, through [leaveRequested] and [onLeaveAnswered] as
 * the editor does, and [onUnsavedWorkChange] says when there is something to ask about.
 * [reminderRow], [onSendTest], [bedtime], [mood] and [progress] are the editor's. The page is the
 * one every setup is on ([SetupFrame]), whose bar carries Back and Next and says what the step
 * still needs or what went wrong, in Settings too ([message]). A test sent here is of a checklist
 * that does not exist yet, so nothing says it opens and its tap opens the app where it was.
 */
@Composable
internal fun ChecklistSetup(
    starter: () -> ChecklistConfiguration,
    today: LocalDate,
    bedtime: Bedtime,
    mood: MoMood?,
    progress: (ChecklistConfiguration) -> ChecklistProgress,
    onCreate: (starter: ChecklistConfiguration, edits: List<ChecklistEdit>) -> Boolean,
    onClose: () -> Unit,
    reminderRow: @Composable (ChecklistConfiguration, Checkpoint, (Boolean) -> Unit) -> Unit,
    onSendTest: (TestReminder.AtCheckpoint) -> Unit,
    leaveRequested: Boolean,
    onLeaveAnswered: (discard: Boolean) -> Unit,
    onUnsavedWorkChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    message: String? = null,
) {
    val state = rememberSaveable(saver = ChecklistSetupState.Saver) { ChecklistSetupState(starter()) }
    val draft = remember(state.form.stored, state.form.edits, today) { state.draft(today) }
    var error by remember { mutableStateOf<Int?>(null) }

    SetupFrame(
        testTag = "checklist_setup",
        pageKey = SettingsPage.NewChecklist.key,
        colors = draft.presentation().colors,
        steps = state.steps,
        stepTestTag = { "checklist_setup_step_${it.tagName}" },
        unsavedWork = state.touched,
        discard = DiscardQuestion(
            title = stringResource(if (draft.isSchedule(today)) R.string.checklist_schedule_discard_title else R.string.checklist_setup_discard_title),
            message = stringResource(R.string.checklist_setup_discard_message),
        ),
        leaveRequested = leaveRequested,
        onLeaveAnswered = onLeaveAnswered,
        onUnsavedWorkChange = onUnsavedWorkChange,
        error = (error?.let { stringResource(it) } ?: message)?.let { BarLine(it, "checklist_setup_error") },
        createLabel = stringResource(if (draft.isSchedule(today)) R.string.checklist_schedule_create else R.string.checklist_setup_create),
        canContinue = state.canContinue(state.step, today),
        canCreate = state.canCreate(today),
        onNext = { state.next(today) },
        onCreate = { error = if (state.create(today, onCreate).also { if (it) onClose() }) null else R.string.checklist_error_save_failed },
        modifier = modifier,
        // Next and Create wait for the step; while they do, the bar says what for. Look's own
        // name field says so itself.
        hint = state.waitingFor(state.step, today)?.let { BarLine(stringResource(it.hint), "checklist_setup_hint") },
    ) { step ->
        ChecklistPageContent(
            page = step,
            checklist = draft,
            today = today,
            bedtime = bedtime,
            mood = mood,
            progress = progress(draft),
            form = state.form,
            onEdit = { state.edit(it, today) },
            reminderRow = reminderRow,
            onSendTest = onSendTest,
            nameField = {
                NameField(
                    name = state.name,
                    key = draft.id,
                    label = stringResource(R.string.checklist_editor_name),
                    testTag = "checklist_setup_name",
                    onName = { state.rename(it, today) },
                    settled = state.form.nameStored(draft.id),
                    missingShown = state.showsMissingName,
                )
            },
        )
    }
}

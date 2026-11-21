package com.example.workouttracker.feature.settings

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
import com.example.workouttracker.domain.LoggedView
import com.example.workouttracker.feature.checklist.presentation
import java.time.LocalDate

/**
 * The state of the guided setup of a logged view, a measurement's, a questions view's or a time
 * view's: the step shown ([steps]), the name as typed, and the view as it will be created, which
 * is the [starter] with the edits made on the steps ([form]). The starter carries the ID the view
 * will have, made once when the setup opens: it is kept with the rest across process death and
 * through every press of Create, so the store can refuse a second creation.
 *
 * Nothing reaches storage until [create] hands the starter and the edits over. A step is left
 * forward only once it is complete, which is the kind's to say ([isComplete]).
 */
internal abstract class LoggedViewSetupState<C : LoggedView, E, D : LoggedViewDraft<C, E>>(
    val form: D,
    parts: List<ChecklistPage>,
    name: String?,
    nameTouched: Boolean,
    step: ChecklistPage,
) {
    /** The parts of the view's page, walked in their order. */
    val steps = SetupSteps(parts, step)

    /** The defaults the setup started from, under the ID the view is created with. */
    val starter: C get() = form.stored

    /** The name typed, trimmed, or null while the field is blank; the draft keeps the last one. */
    var name: String? by mutableStateOf(name)
        private set

    /** Whether the name field was typed into, so a field nobody touched yet is not told it is missing. */
    var nameTouched: Boolean by mutableStateOf(nameTouched)
        private set

    /**
     * Whether the view is stored: Create made it, with its alarm or, where that could neither be
     * set nor the creation taken back, without. From then on nothing here is left to create or to
     * lose, and the setup closes.
     */
    var created: Boolean by mutableStateOf(false)
        private set

    /** Whether the form holds something typed or chosen that leaving would lose. */
    protected open val formTouched: Boolean get() = form.hasUnsavedWork

    /** Whether anything was typed or chosen that leaving would lose, so leaving an untouched setup, or one that created, has nothing to ask about. */
    val touched: Boolean get() = !created && (nameTouched || formTouched)

    /** The name is said to be missing once the person typed and left nothing, never before. */
    val showsMissingName: Boolean get() = name == null && nameTouched

    /** Whether [step] has settled what it must settle. Each field says itself what it still needs. */
    abstract fun isComplete(step: ChecklistPage): Boolean

    /** Whether the open step can be left forward. */
    val canContinue: Boolean get() = isComplete(steps.step)

    /** Create waits for every step to be complete. */
    val canCreate: Boolean get() = steps.parts.all(::isComplete)

    fun next() {
        if (canContinue) steps.next()
    }

    fun rename(typed: String?) {
        nameTouched = true
        name = typed
        form.named(starter.id, typed)
        if (typed != null) form.rename(typed)
    }

    /**
     * Stores the view through [onCreate], as the starter and the edits made to it, once every step
     * is complete, and never twice: a second press after the first stored it is answered that it
     * is stored without creating anything more. A creation that left nothing stored leaves the
     * setup as it is, with its ID, ready to try again.
     */
    fun create(onCreate: (starter: C, edits: List<E>) -> ViewWrite): ViewWrite {
        if (created) return ViewWrite.Stored
        if (!canCreate) return ViewWrite.NotStored
        return onCreate(starter, form.edits).also { created = it != ViewWrite.NotStored }
    }
}

/**
 * How such a setup is kept across process death, whichever kind it sets up: its draft by
 * [formSaver], the name as typed, whether the name field was typed into, and the open step.
 * [restored] makes the kind's own state of them.
 */
internal fun <D : LoggedViewDraft<*, *>, S : LoggedViewSetupState<*, *, D>> setupStateSaver(
    formSaver: Saver<D, Any>,
    restored: (form: D, name: String?, nameTouched: Boolean, step: ChecklistPage) -> S,
): Saver<S, Any> = listSaver(
    save = { state ->
        val scope = this
        listOf(requireNotNull(with(formSaver) { scope.save(state.form) }), state.name.orEmpty(), state.nameTouched, state.steps.step.name)
    },
    restore = {
        restored(
            requireNotNull(formSaver.restore(it[0])),
            (it[1] as String).ifEmpty { null },
            it[2] as Boolean,
            ChecklistPage.valueOf(it[3] as String),
        )
    },
)

/**
 * A new logged view set up in numbered steps, the parts of its page in order over one draft, with
 * Back and Next at the bottom and the view created on the last step ([SetupFrame]).
 * It starts from the starter [state] holds, a one-time copy of sensible defaults under the ID it
 * will keep, and nothing is stored until [onCreate] says it is, given the starter and the edits
 * made; [onClose] is the way out after creating. A view that is stored closes the setup, with the
 * alarm it asked for or without: one that could not be set is set the next time the app starts, as
 * every alarm is.
 *
 * A kind brings the content of a [step]: it is handed the step, the view as the draft is changing
 * it, what opens the picker of the reminder's fixed time, and Look's name field, which says it is
 * missing only once something was typed. What the open step still waits for is the [hint].
 *
 * Leaving a setup something was typed or chosen in asks first, in the kind's words ([discard]),
 * through [leaveRequested] and [onLeaveAnswered] as every page does, and [onUnsavedWorkChange]
 * says when there is something to ask about. The bar says what went wrong, in Settings too
 * ([message]). Every tag follows from [testTag]; a step is called by its part's word unless the
 * kind has another ([stepName], and [partTagName] in its tab's tag).
 */
@Composable
internal fun <C : LoggedView, E> LoggedViewSetup(
    state: LoggedViewSetupState<C, E, *>,
    today: LocalDate,
    testTag: String,
    pageKey: String,
    createLabel: String,
    discard: DiscardQuestion,
    onCreate: (starter: C, edits: List<E>) -> ViewWrite,
    onClose: () -> Unit,
    leaveRequested: Boolean,
    onLeaveAnswered: (discard: Boolean) -> Unit,
    onUnsavedWorkChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    message: String? = null,
    hint: BarLine? = null,
    stepName: @Composable (ChecklistPage) -> String = { stringResource(it.label) },
    partTagName: (ChecklistPage) -> String = { it.tagName },
    step: @Composable (shown: ChecklistPage, draft: C, onPickTime: () -> Unit, nameField: @Composable () -> Unit) -> Unit,
) {
    val form = state.form
    // The starter is the setup's own, and what is shown of it follows the day as a page's draft does.
    form.follow(form.stored, today)
    val draft = remember(form.stored, form.edits, today) { form.edited }
    var failure by remember { mutableStateOf<Int?>(null) }
    var pickingTime by rememberSaveable { mutableStateOf(false) }

    SetupFrame(
        testTag = testTag,
        pageKey = pageKey,
        colors = draft.presentation().colors,
        steps = state.steps,
        stepTestTag = { "${testTag}_tab_${partTagName(it)}" },
        unsavedWork = state.touched,
        discard = discard,
        leaveRequested = leaveRequested,
        onLeaveAnswered = onLeaveAnswered,
        onUnsavedWorkChange = onUnsavedWorkChange,
        error = (failure?.let { stringResource(it) } ?: message)?.let { BarLine(it, "${testTag}_error") },
        createLabel = createLabel,
        canContinue = state.canContinue,
        canCreate = state.canCreate && !state.created,
        onNext = state::next,
        onCreate = {
            if (state.create(onCreate) == ViewWrite.NotStored) failure = R.string.settings_error_view else onClose()
        },
        modifier = modifier,
        hint = hint,
        stepName = stepName,
        dialogs = {
            if (pickingTime) LoggedViewReminderTimePicker(draft, form, onDismiss = { pickingTime = false })
        },
    ) { shown ->
        step(shown, draft, { pickingTime = true }) {
            NameField(
                name = state.name,
                key = draft.id,
                label = stringResource(R.string.checklist_editor_name),
                testTag = "${testTag}_name",
                onName = state::rename,
                settled = form.nameStored(draft.id),
                missingShown = state.showsMissingName,
            )
        }
    }
}

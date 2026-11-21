package com.example.workouttracker.feature.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.example.workouttracker.R
import com.example.workouttracker.core.ui.MomentumBarAction
import com.example.workouttracker.core.ui.MomentumButton
import com.example.workouttracker.core.ui.ViewColors

/**
 * Where a guided setup stands: the [parts] it walks as its numbered steps, and the [step] that is
 * open. Which step is open, Back and Next, the last step, which steps can be opened and what
 * number each wears all follow from that list by position, whatever kind of view is set up.
 *
 * A kind brings what the list cannot know: when a step is complete, and what it still waits for.
 * So a step is left forward only where its setup says it may be ([next]).
 */
internal class SetupSteps(val parts: List<ChecklistPage>, step: ChecklistPage = parts.first()) {
    init {
        // A page shows its parts in the one order every view has them in, and so do the steps.
        require(parts == ChecklistPage.entries.filter { it in parts }) { "A setup walks its parts in the order a page shows them." }
    }

    /** The step that is open; one saved under a part this setup does not walk comes back on the first. */
    var step: ChecklistPage by mutableStateOf(step.takeIf { it in parts } ?: parts.first())
        private set

    val isFirst: Boolean get() = step == parts.first()

    val isLast: Boolean get() = isLast(step)

    fun isLast(step: ChecklistPage): Boolean = step == parts.last()

    /** The number [step] wears, from 1. */
    fun numberOf(step: ChecklistPage): Int = parts.indexOf(step) + 1

    /** The steps that can be shown: the open one, and the ones passed to get here. */
    fun canShow(target: ChecklistPage): Boolean = target in parts && parts.indexOf(target) <= parts.indexOf(step)

    fun show(target: ChecklistPage) {
        if (canShow(target)) step = target
    }

    /** Opens the step after this one, where there is one. Whether this one may be left is its setup's to say. */
    fun next() {
        if (!isLast) step = parts[parts.indexOf(step) + 1]
    }

    fun back() {
        if (!isFirst) step = parts[parts.indexOf(step) - 1]
    }
}

/**
 * The page of a guided setup, whatever kind of view it sets up: the page every view's settings
 * are on ([ViewSettingsFrame]) with the [steps] as numbered tabs, the ones ahead held back until
 * their turn, and a bar with Next, or Create on the last step; Back walks the steps back. It has no Delete, since
 * nothing exists yet.
 *
 * The setup says whether the open step can be left forward ([canContinue]) and whether everything
 * is ready to be created ([canCreate]); [onNext] and [onCreate] are its own, as are the line it
 * says while a step waits ([hint]) and the content of each [step]. A step is named by its part's
 * word unless the kind has one of its own ([stepName]). Tags follow from [testTag]: the buttons
 * are `<testTag>_next` and `<testTag>_create`; a step's tab comes from
 * [stepTestTag].
 */
@Composable
internal fun SetupFrame(
    testTag: String,
    pageKey: String,
    colors: ViewColors,
    steps: SetupSteps,
    stepTestTag: (ChecklistPage) -> String,
    unsavedWork: Boolean,
    discard: DiscardQuestion,
    leaveRequested: Boolean,
    onLeaveAnswered: (discard: Boolean) -> Unit,
    onUnsavedWorkChange: (Boolean) -> Unit,
    error: BarLine?,
    createLabel: String,
    canContinue: Boolean,
    canCreate: Boolean,
    onNext: () -> Unit,
    onCreate: () -> Unit,
    modifier: Modifier = Modifier,
    hint: BarLine? = null,
    stepName: @Composable (ChecklistPage) -> String = { stringResource(it.label) },
    dialogs: @Composable () -> Unit = {},
    step: @Composable (ChecklistPage) -> Unit,
) {
    val stepAfter = steps.parts.getOrNull(steps.parts.indexOf(steps.step) + 1)
    // Back, the header's arrow or the gesture, walks the steps back before it leaves the setup:
    // a Back button in the bar would only repeat it and take the room the keyboard needs.
    BackHandler(enabled = !steps.isFirst, onBack = steps::back)
    ViewSettingsFrame(
        testTag = testTag,
        pageKey = pageKey,
        colors = colors,
        parts = steps.parts,
        selected = steps.step,
        // The step after the open one is reached by its tab as Next reaches it,
        // once the open step is complete; the steps before are shown as they are.
        onSelect = { if (it == stepAfter) onNext() else steps.show(it) },
        partTestTag = stepTestTag,
        unsavedWork = unsavedWork,
        discard = discard,
        leaveRequested = leaveRequested,
        onLeaveAnswered = onLeaveAnswered,
        onUnsavedWorkChange = onUnsavedWorkChange,
        error = error,
        delete = null,
        buttons = {
            if (steps.isLast) {
                MomentumBarAction(
                    text = createLabel,
                    onClick = onCreate,
                    ready = canCreate,
                    testTag = "${testTag}_create",
                )
            } else {
                MomentumBarAction(
                    text = stringResource(R.string.checklist_setup_next),
                    onClick = onNext,
                    ready = canContinue,
                    testTag = "${testTag}_next",
                )
            }
        },
        modifier = modifier,
        hint = hint,
        partLabel = { stringResource(R.string.checklist_setup_step, steps.numberOf(it), stepName(it)) },
        partEnabled = { steps.canShow(it) || (canContinue && it == stepAfter) },
        dialogs = dialogs,
        // A setup walks its steps with its own Next and Create.
        walksParts = false,
        part = step,
    )
}

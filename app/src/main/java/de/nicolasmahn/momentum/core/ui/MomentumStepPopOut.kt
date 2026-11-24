package de.nicolasmahn.momentum.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import de.nicolasmahn.momentum.R

/** Why a typed step was not stored. */
internal enum class StepRefusal {
    /** The text is no step this view can have. */
    Invalid,

    /** It is one, but the store did not take it. */
    NotStored,
}

/**
 * A row of quick changes by a step, which [open] turns in place into the field for that step. It
 * pops out as every folded setting does ([MomentumPopOut]), so the row it replaces and everything
 * under it stay where they were. The row itself is [closed]; its way in is a [StepEditButton].
 *
 * The field opens on [step] as written on screen. ✓ or the keyboard's Done hands the text to
 * [onSave], which stores it and answers null, or says why not; a refusal keeps the field open with
 * the reason in its label, [invalidMessage] for text that is no step. Back, or another pop-out
 * opening, closes it through [onClose] and drops what was typed.
 *
 * Tags: the field is `${testTag}_field` and ✓ is `${testTag}_save`.
 */
@Composable
internal fun MomentumStepPopOut(
    open: Boolean,
    onClose: () -> Unit,
    step: String,
    unit: String?,
    label: String,
    invalidMessage: String,
    onSave: (String) -> StepRefusal?,
    testTag: String,
    modifier: Modifier = Modifier,
    closed: @Composable () -> Unit,
) {
    MomentumPopOut(open = open, onClose = onClose, modifier = modifier) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            if (open) {
                StepField(step, unit, label, invalidMessage, onSave, onClose, testTag)
            } else {
                closed()
            }
        }
    }
}

@Composable
private fun StepField(
    step: String,
    unit: String?,
    label: String,
    invalidMessage: String,
    onSave: (String) -> StepRefusal?,
    onClose: () -> Unit,
    testTag: String,
) {
    var text by rememberSaveable { mutableStateOf(step) }
    var refusal by remember { mutableStateOf<StepRefusal?>(null) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    fun save() {
        refusal = onSave(text)
        if (refusal == null) onClose()
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MomentumTextField(
            value = text,
            onValueChange = {
                text = it
                refusal = null
            },
            label = label,
            error = when (refusal) {
                StepRefusal.Invalid -> invalidMessage
                StepRefusal.NotStored -> stringResource(R.string.core_step_not_stored)
                null -> null
            },
            modifier = Modifier.weight(1f).focusRequester(focus),
            testTag = "${testTag}_field",
            suffix = unit,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { save() }),
        )
        MomentumButton(
            onClick = ::save,
            modifier = Modifier.width(MomentumButtonDefaults.MinHeight),
            testTag = "${testTag}_save",
            contentPadding = PaddingValues(0.dp),
        ) {
            Icon(Icons.Rounded.Check, contentDescription = stringResource(R.string.core_step_save))
        }
    }
}

/** The way into a [MomentumStepPopOut]: a raised pencil beside the buttons the step drives. */
@Composable
internal fun StepEditButton(
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier,
) {
    MomentumButton(
        onClick = onClick,
        // Square, as wide as its face is tall; the icon needs none of a label's padding.
        modifier = modifier.width(MomentumButtonDefaults.MinHeight),
        variant = MomentumButtonVariant.Secondary,
        testTag = testTag,
        contentPadding = PaddingValues(0.dp),
    ) {
        Icon(Icons.Rounded.Edit, contentDescription = stringResource(R.string.core_step_edit))
    }
}

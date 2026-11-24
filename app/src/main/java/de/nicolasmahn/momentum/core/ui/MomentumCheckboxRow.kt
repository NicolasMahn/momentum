package de.nicolasmahn.momentum.core.ui

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp

/**
 * A checkbox with a label, where the whole row is the touch target and TalkBack reads one element.
 * [testTag] lands on that row, so a test toggles what the user touches.
 *
 * [look] is one of the box's four ([MomentumCheckboxLook]), and [stateDescription] is what TalkBack
 * says of the row's state in place of checked or not, such as that the check is assumed or the
 * item was marked missed: a look that only the eye can tell from another needs its words.
 *
 * The label is a slot because each screen gives it the type its own list needs.
 */
@Composable
internal fun MomentumCheckboxRow(
    look: MomentumCheckboxLook,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    stateDescription: String? = null,
    testTag: String? = null,
    label: @Composable RowScope.() -> Unit,
) {
    // The box shares the row's interaction source so it still sinks while the row is held.
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
            .toggleable(
                value = look.isChecked,
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                enabled = enabled,
                role = Role.Checkbox,
                onValueChange = onCheckedChange,
            )
            .then(if (stateDescription != null) Modifier.semantics { this.stateDescription = stateDescription } else Modifier)
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MomentumCheckbox(
            look = look,
            onCheckedChange = null,
            enabled = enabled,
            interactionSource = interactionSource,
        )
        label()
    }
}

/** The row as most callers need it: a recorded check, or none, which TalkBack says as it is. */
@Composable
internal fun MomentumCheckboxRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    testTag: String? = null,
    label: @Composable RowScope.() -> Unit,
) {
    MomentumCheckboxRow(
        look = MomentumCheckboxLook(checked),
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        enabled = enabled,
        testTag = testTag,
        label = label,
    )
}

package com.example.workouttracker.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/** One action in a [MomentumDialog]: always a [MomentumButton], never a bare piece of text. */
internal data class MomentumDialogAction(
    val text: String,
    val onClick: () -> Unit,
    /** Error-colored, for an action that throws work away. Never the view's own identity. */
    val destructive: Boolean = false,
    val testTag: String? = null,
    /** False while the dialog still lacks what the action needs, such as a chosen day. */
    val enabled: Boolean = true,
)

internal object MomentumDialogDefaults {
    /** The same radius family as [MomentumButton] and [MomentumCard], so one app, one shape. */
    val CornerRadius = MomentumButtonDefaults.CornerRadius
    val ContentSpacing = 10.dp
}

/**
 * The single dialog surface of the app: container, shape, type scale and buttons are decided here
 * so no screen has to reassemble a Material `AlertDialog` and drift.
 *
 * A dialog is a real interruption, so the app keeps very few of them. Day edits never confirm —
 * removal is the two-step [MomentumRemoveButton] instead. What is left is only: guarding unsaved
 * work before it is thrown away, an irreversible bulk action such as restoring a backup over every
 * current record, and help the user asked for.
 *
 * Inside a [ViewTheme] the buttons take the view's identity, exactly like the rest of that screen.
 * [message] is the plain body; [content] adds richer body content under it, such as a scrollable
 * explanation with links. [modifier] lands on the dialog node, where a screen's own test tag goes.
 */
@Composable
internal fun MomentumDialog(
    onDismissRequest: () -> Unit,
    confirm: MomentumDialogAction,
    modifier: Modifier = Modifier,
    title: String? = null,
    message: String? = null,
    dismiss: MomentumDialogAction? = null,
    content: (@Composable ColumnScope.() -> Unit)? = null,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        shape = RoundedCornerShape(MomentumDialogDefaults.CornerRadius),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        title = title?.let {
            { Text(text = it, style = MaterialTheme.typography.titleLarge) }
        },
        text = if (message != null || content != null) {
            {
                Column(
                    verticalArrangement = Arrangement.spacedBy(
                        MomentumDialogDefaults.ContentSpacing,
                    ),
                ) {
                    if (message != null) {
                        Text(text = message, style = MaterialTheme.typography.bodyMedium)
                    }
                    content?.invoke(this)
                }
            }
        } else {
            null
        },
        confirmButton = { DialogAction(confirm) },
        dismissButton = dismiss?.let { { DialogAction(it) } },
    )
}

/**
 * The dialog without actions or a way out, for the moments in which the app must not be touched,
 * such as while a restore replaces every record. It wears the same container and shape as
 * [MomentumDialog]. The caller removes it when the work is over; back and outside taps do nothing.
 */
@Composable
internal fun MomentumBlockingDialog(
    message: String,
    modifier: Modifier = Modifier,
) {
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
    ) {
        Surface(
            modifier = modifier,
            shape = RoundedCornerShape(MomentumDialogDefaults.CornerRadius),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Text(text = message, modifier = Modifier.padding(BlockingDialogPadding))
        }
    }
}

private val BlockingDialogPadding = 24.dp

@Composable
private fun DialogAction(action: MomentumDialogAction) {
    MomentumButton(
        text = action.text,
        onClick = action.onClick,
        variant = MomentumButtonVariant.Quiet,
        colors = if (action.destructive) {
            MomentumButtonDefaults.destructiveColors()
        } else {
            MomentumButtonDefaults.colors(MomentumButtonVariant.Quiet)
        },
        enabled = action.enabled,
        testTag = action.testTag,
    )
}

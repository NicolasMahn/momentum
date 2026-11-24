package de.nicolasmahn.momentum.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import de.nicolasmahn.momentum.R

/**
 * How a day screen is left while it holds unsaved work: it reports the work through
 * [onUnsavedWorkChange], and once a way away waits ([leaveRequested]) it asks the person, who
 * answers through [onLeaveAnswered]. [None] guards nothing, for a screen shown on its own.
 */
internal class LeaveGuard(
    val leaveRequested: Boolean,
    val onLeaveAnswered: (discard: Boolean) -> Unit,
    val onUnsavedWorkChange: (Boolean) -> Unit,
) {
    companion object {
        val None = LeaveGuard(leaveRequested = false, onLeaveAnswered = {}, onUnsavedWorkChange = {})
    }
}

/** Reports whether the screen holds [unsaved] work, and asks before a way away that waits drops it. */
@Composable
internal fun GuardUnsavedWork(unsaved: Boolean, guard: LeaveGuard, testTag: String) {
    ReportUnsavedWork(unsaved, guard.onUnsavedWorkChange)
    if (guard.leaveRequested && unsaved) {
        DiscardChangesDialog(testTag = testTag, onDiscard = { guard.onLeaveAnswered(true) }, onKeep = { guard.onLeaveAnswered(false) })
    }
}

/**
 * Tells whoever owns the navigation whether this page holds work that would be lost, and that
 * nothing is left once the page is gone.
 */
@Composable
internal fun ReportUnsavedWork(unsaved: Boolean, onChange: (Boolean) -> Unit) {
    val report by rememberUpdatedState(onChange)
    LaunchedEffect(unsaved) { report(unsaved) }
    DisposableEffect(Unit) { onDispose { report(false) } }
}

/**
 * The one question every day screen asks before its unsaved changes go, whether Back or another
 * day in its calendar would drop them. The confirm is `<testTag>_confirm`, staying `<testTag>_stay`.
 */
@Composable
internal fun DiscardChangesDialog(testTag: String, onDiscard: () -> Unit, onKeep: () -> Unit) {
    MomentumDialog(
        onDismissRequest = onKeep,
        modifier = Modifier.testTag(testTag),
        title = stringResource(R.string.core_discard_title),
        message = stringResource(R.string.core_discard_message),
        confirm = MomentumDialogAction(
            text = stringResource(R.string.core_discard_confirm),
            onClick = onDiscard,
            destructive = true,
            testTag = "${testTag}_confirm",
        ),
        dismiss = MomentumDialogAction(
            text = stringResource(R.string.core_discard_stay),
            onClick = onKeep,
            testTag = "${testTag}_stay",
        ),
    )
}

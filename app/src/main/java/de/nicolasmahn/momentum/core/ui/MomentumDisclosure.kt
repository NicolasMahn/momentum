package de.nicolasmahn.momentum.core.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.clickable
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.nicolasmahn.momentum.R

/**
 * The app's one way to fold away inputs most entries never need. The toggle is always rendered,
 * so it never appears out of nowhere; opening it is the user's own act, which is the one case
 * where content below may move.
 *
 * The toggle reads as a setting: its [label] on the left, and on the right [summary], what the
 * folded section is set to, so a hidden value can never be saved unnoticed. Callers pass
 * `expanded = true` while the section holds a validation error, so an error cannot hide behind
 * the fold. Wrapped with its owner in a [MomentumPopOut], the open fold pops out.
 *
 * Tags: the toggle is [testTag], the revealed content is `${testTag}_content`.
 */
@Composable
internal fun MomentumDisclosure(
    label: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    testTag: String,
    modifier: Modifier = Modifier,
    summary: String? = null,
    enabled: Boolean = true,
    motionEnabled: Boolean = platformAnimationsEnabled(),
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        // A plain row, not a button face: pressing it shows nothing of its own, since the fold
        // opening (and a pop-out rising around it) is the answer.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = MomentumButtonDefaults.QuietMinHeight)
                .testTag(testTag)
                .clickable(
                    interactionSource = null,
                    indication = null,
                    enabled = enabled,
                    role = Role.Button,
                    onClick = { onExpandedChange(!expanded) },
                )
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Name on the left, what it is set to on the right: the row reads as a setting, not an action.
            Text(
                text = label,
                modifier = Modifier.padding(end = 12.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
            if (summary != null) {
                Text(
                    text = summary,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            } else {
                Spacer(Modifier.weight(1f))
            }
            Spacer(Modifier.width(8.dp))
            Icon(
                imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                contentDescription = stringResource(
                    if (expanded) R.string.core_disclosure_hide else R.string.core_disclosure_show,
                    label,
                ),
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = if (motionEnabled) expandVertically() + fadeIn() else EnterTransition.None,
            exit = if (motionEnabled) shrinkVertically() + fadeOut() else ExitTransition.None,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("${testTag}_content"),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                content = content,
            )
        }
    }
}

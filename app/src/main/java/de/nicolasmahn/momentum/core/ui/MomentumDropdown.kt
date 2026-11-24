package de.nicolasmahn.momentum.core.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

private const val ChevronTurnMillis = 150

/**
 * A field you open to pick one of several options, for a choice whose list may grow, such as the
 * app language. It is pressed, never typed into, so it is a clickable [MomentumCard] with an
 * outline and a ledge rather than a text field; it takes the width it is given and its menu matches
 * that width.
 *
 * The closed field is one element for TalkBack: its [label], the current value and that it opens a
 * list. [onSelect] receives the option tapped, the current one included. [testTag] lands on the
 * field and [optionTestTag] on each option of the open menu.
 */
@Composable
internal fun <T> MomentumDropdown(
    label: String,
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    optionLabel: @Composable (T) -> String,
    modifier: Modifier = Modifier,
    testTag: String? = null,
    optionTestTag: ((T) -> String)? = null,
    motionEnabled: Boolean = platformAnimationsEnabled(),
) {
    var expanded by remember { mutableStateOf(false) }
    var fieldWidth by remember { mutableIntStateOf(0) }
    val chevronTurn by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = if (motionEnabled) tween(ChevronTurnMillis) else snap(),
        label = "dropdown chevron turn",
    )
    val viewColors = LocalViewColors.current
    val scheme = MaterialTheme.colorScheme

    Box(modifier = modifier.onSizeChanged { fieldWidth = it.width }) {
        MomentumCard(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
            role = Role.DropdownList,
            motionEnabled = motionEnabled,
            testTag = testTag,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    // As tall as a button, so the field lines up with one beside it.
                    .heightIn(min = MomentumButtonDefaults.MinHeight)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        color = scheme.onSurfaceVariant,
                    )
                    Text(text = optionLabel(selected), style = MaterialTheme.typography.bodyLarge)
                }
                Icon(
                    imageVector = Icons.Rounded.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.graphicsLayer { rotationZ = chevronTurn },
                    tint = viewColors.label,
                )
            }
        }

        MomentumMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.width(with(LocalDensity.current) { fieldWidth.toDp() }),
        ) {
            options.forEach { option ->
                MomentumMenuItem(
                    text = optionLabel(option),
                    onClick = {
                        expanded = false
                        onSelect(option)
                    },
                    selected = option == selected,
                    testTag = optionTestTag?.invoke(option),
                )
            }
        }
    }
}

/**
 * The list that drops open: under a [MomentumDropdown], or at anything else it shares a [Box] with,
 * such as a link that offers several things to open. It is the one menu surface, so every list
 * that opens looks alike. It is a popup, so it takes no room in the layout, open or closed.
 */
@Composable
internal fun MomentumMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        shape = RoundedCornerShape(MomentumCardDefaults.CornerRadius),
        containerColor = scheme.surfaceContainerHigh,
        border = BorderStroke(MomentumCardDefaults.BorderWidth, scheme.outlineVariant),
        content = content,
    )
}

/**
 * One line of a [MomentumMenu]. In a list with a current choice, [selected] says whether this is
 * it. A list of things to open has none and leaves it null, so no line is announced as not selected.
 */
@Composable
internal fun MomentumMenuItem(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean? = null,
    testTag: String? = null,
) {
    val viewColors = LocalViewColors.current
    DropdownMenuItem(
        text = { Text(text = text, style = MaterialTheme.typography.bodyLarge) },
        onClick = onClick,
        modifier = modifier
            .then(if (selected != null) Modifier.semantics { this.selected = selected } else Modifier)
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier),
        trailingIcon = if (selected == true) {
            { Icon(imageVector = Icons.Rounded.Check, contentDescription = null) }
        } else {
            null
        },
        colors = MenuDefaults.itemColors(
            textColor = if (selected == true) viewColors.label else MaterialTheme.colorScheme.onSurface,
            trailingIconColor = viewColors.label,
        ),
    )
}

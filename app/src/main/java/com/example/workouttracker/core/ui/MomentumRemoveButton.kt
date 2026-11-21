package com.example.workouttracker.core.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.workouttracker.R
import kotlinx.coroutines.delay

/**
 * Removal without a popup: the first tap arms the button, the second removes. It disarms itself
 * after a moment and whenever [resetKey] changes, so a forgotten tap never leaves a loaded
 * trigger waiting on a different day or record.
 *
 * The button is a square icon: a trash can needs no caption. Arming fills the face with the
 * error color, so the second tap is unmistakable while the size never changes. Where there is
 * nothing to remove, pass `enabled = false`: the button stays in place, greyed out, so switching
 * between days never moves the layout.
 *
 * [variant] is `Secondary` beside a primary action and `Quiet` inside a list row.
 */
@Composable
internal fun MomentumRemoveButton(
    description: String,
    onRemove: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier,
    resetKey: Any? = null,
    variant: MomentumButtonVariant = MomentumButtonVariant.Secondary,
    enabled: Boolean = true,
) {
    var armed by remember(resetKey) { mutableStateOf(false) }
    LaunchedEffect(armed, enabled) {
        if (!enabled) {
            armed = false
        } else if (armed) {
            delay(ArmedTimeoutMillis)
            armed = false
        }
    }

    val armedDescription = stringResource(R.string.core_remove_confirm, description)
    val resting = MomentumButtonDefaults.destructiveColors(variant)
    val scheme = MaterialTheme.colorScheme
    MomentumButton(
        onClick = {
            if (armed) {
                armed = false
                onRemove()
            } else {
                armed = true
            }
        },
        modifier = modifier.width(
            if (variant == MomentumButtonVariant.Quiet) {
                MomentumButtonDefaults.QuietMinHeight
            } else {
                MomentumButtonDefaults.MinHeight
            },
        ),
        variant = variant,
        colors = if (armed) {
            resting.copy(container = scheme.error, content = scheme.onError, border = scheme.error)
        } else {
            resting
        },
        enabled = enabled,
        contentPadding = PaddingValues(0.dp),
        testTag = testTag,
        faceModifier = Modifier.semantics {
            contentDescription = if (armed) armedDescription else description
        },
    ) {
        Icon(
            imageVector = if (armed) Icons.Rounded.DeleteForever else Icons.Rounded.DeleteOutline,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
        )
    }
}

private const val ArmedTimeoutMillis = 3_000L

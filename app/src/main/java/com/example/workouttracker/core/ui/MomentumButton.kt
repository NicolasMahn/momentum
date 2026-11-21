package com.example.workouttracker.core.ui

import android.animation.ValueAnimator
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.workouttracker.core.ui.theme.MomentumTransparent

internal enum class MomentumButtonVariant {
    /** The one action a screen most wants taken: a filled face on a ledge. */
    Primary,

    /** A real alternative next to a primary action: an outlined face on a ledge. */
    Secondary,

    /** Low-stakes actions such as dialog dismissals: flat text, no ledge. */
    Quiet,
}

internal data class MomentumButtonColors(
    val container: Color,
    val content: Color,
    val ledge: Color,
    val disabledContainer: Color,
    val disabledContent: Color,
    val border: Color? = null,
)

/** How far the face compresses and sinks toward its ledge while pressed. */
internal data class MomentumPressFeedback(
    val scale: Float,
    val offset: Dp,
)

internal object MomentumButtonDefaults {
    val CornerRadius = 18.dp
    val LedgeHeight = 5.dp

    // The ledge already gives a surface its weight; the outline only has to separate it.
    val BorderWidth = 1.5.dp
    val MinHeight = 59.dp
    val QuietMinHeight = 48.dp
    val PressFeedback = MomentumPressFeedback(scale = 0.99f, offset = 2.dp)

    /** For actions that remove data. Destruction is never a view's identity, so it stays error-colored. */
    @Composable
    fun destructiveColors(variant: MomentumButtonVariant = MomentumButtonVariant.Quiet): MomentumButtonColors =
        colors(variant).copy(content = MaterialTheme.colorScheme.error)

    /**
     * Inside a [ViewTheme] buttons take the view's identity; elsewhere [LocalViewColors] hands them
     * the neutral chrome.
     */
    @Composable
    fun colors(variant: MomentumButtonVariant): MomentumButtonColors {
        val scheme = MaterialTheme.colorScheme
        // Text on a neutral surface needs the contrast-safe label role, not the vivid accent.
        val viewColors = LocalViewColors.current
        val textColor = viewColors.label
        return when (variant) {
            MomentumButtonVariant.Primary -> viewColors.momentumButtonColors()

            MomentumButtonVariant.Secondary -> MomentumButtonColors(
                container = scheme.surfaceContainerLowest,
                content = textColor,
                ledge = scheme.outlineVariant,
                disabledContainer = scheme.surfaceContainerLowest,
                disabledContent = scheme.onSurfaceVariant,
                border = scheme.outlineVariant,
            )

            MomentumButtonVariant.Quiet -> MomentumButtonColors(
                container = MomentumTransparent,
                content = textColor,
                ledge = MomentumTransparent,
                disabledContainer = MomentumTransparent,
                disabledContent = scheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * The single button surface of the app. Feature code uses this instead of Material buttons so
 * shape, ledge, and press motion stay one decision.
 *
 * [raised] hides the ledge without changing the footprint, for a face that has settled into place.
 * [testTag] lands on the pressable face; [modifier] applies to the whole footprint including the ledge.
 */
@Composable
internal fun MomentumButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: MomentumButtonVariant = MomentumButtonVariant.Primary,
    colors: MomentumButtonColors = MomentumButtonDefaults.colors(variant),
    enabled: Boolean = true,
    raised: Boolean = true,
    pressFeedback: MomentumPressFeedback = MomentumButtonDefaults.PressFeedback,
    motionEnabled: Boolean = platformAnimationsEnabled(),
    testTag: String? = null,
    faceModifier: Modifier = Modifier,
    contentPadding: PaddingValues? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val hasLedge = variant != MomentumButtonVariant.Quiet
    val shape = RoundedCornerShape(MomentumButtonDefaults.CornerRadius)
    val interactionSource = remember { MutableInteractionSource() }
    val press = rememberMomentumPressState(
        interactionSource = interactionSource,
        enabled = enabled,
        hasLedge = hasLedge,
        feedback = pressFeedback,
        motionEnabled = motionEnabled,
    )

    Box(
        modifier = modifier.padding(
            bottom = if (hasLedge) MomentumButtonDefaults.LedgeHeight else 0.dp,
        ),
        propagateMinConstraints = true,
    ) {
        // A disabled button keeps a grey ledge, so a greyed-out button beside a live one still
        // reads as the same kind of object; the grey face and ink already say it cannot be pressed.
        if (hasLedge && raised) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .offset(y = MomentumButtonDefaults.LedgeHeight)
                    .clip(shape)
                    .background(if (enabled) colors.ledge else MaterialTheme.colorScheme.outlineVariant),
            )
        }

        Button(
            onClick = onClick,
            modifier = Modifier
                .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
                .heightIn(
                    min = if (hasLedge) {
                        MomentumButtonDefaults.MinHeight
                    } else {
                        MomentumButtonDefaults.QuietMinHeight
                    },
                )
                .momentumPress(press)
                .then(faceModifier),
            enabled = enabled,
            shape = shape,
            colors = ButtonDefaults.buttonColors(
                containerColor = colors.container,
                contentColor = colors.content,
                disabledContainerColor = colors.disabledContainer,
                disabledContentColor = colors.disabledContent,
            ),
            elevation = null,
            border = colors.border?.let { BorderStroke(MomentumButtonDefaults.BorderWidth, it) },
            contentPadding = contentPadding ?: if (hasLedge) {
                ButtonDefaults.ContentPadding
            } else {
                PaddingValues(horizontal = 12.dp)
            },
            interactionSource = interactionSource,
            content = content,
        )
    }
}

@Composable
internal fun MomentumButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: MomentumButtonVariant = MomentumButtonVariant.Primary,
    colors: MomentumButtonColors = MomentumButtonDefaults.colors(variant),
    icon: ImageVector? = null,
    enabled: Boolean = true,
    motionEnabled: Boolean = platformAnimationsEnabled(),
    testTag: String? = null,
) {
    MomentumButton(
        onClick = onClick,
        modifier = modifier,
        variant = variant,
        colors = colors,
        enabled = enabled,
        motionEnabled = motionEnabled,
        testTag = testTag,
    ) {
        if (icon != null) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(10.dp))
        }
        MomentumButtonLabel(text)
    }
}

@Composable
internal fun MomentumButtonLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
    )
}

@Composable
internal fun platformAnimationsEnabled(): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        ValueAnimator.areAnimatorsEnabled()
    } else {
        Settings.Global.getFloat(
            LocalContext.current.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) > 0f
    }

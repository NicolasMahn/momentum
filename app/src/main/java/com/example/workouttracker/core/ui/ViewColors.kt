package com.example.workouttracker.core.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Circle
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.workouttracker.core.ui.theme.MomentumDarkBackground
import com.example.workouttracker.core.ui.theme.MomentumDarkOnSurface
import com.example.workouttracker.core.ui.theme.MomentumDarkOnSurfaceVariant
import com.example.workouttracker.core.ui.theme.MomentumDarkOutline
import com.example.workouttracker.core.ui.theme.MomentumDarkSurfaceHighest
import com.example.workouttracker.core.ui.theme.MomentumHue
import com.example.workouttracker.core.ui.theme.MomentumHues
import com.example.workouttracker.core.ui.theme.MomentumReward
import com.example.workouttracker.core.ui.theme.MomentumOnVibrant
import com.example.workouttracker.domain.TrackerRef

/**
 * The six color roles every view identity provides. A feature's presentation mapping fills these
 * from theme tokens; shared components read roles instead of per-component color sets, so every
 * view carries the same visual weight.
 */
internal data class ViewColors(
    /** The identity itself: icons, markers, emphasis, and the face of a raised button. */
    val accent: Color,
    /** Ink for anything drawn on [accent]. */
    val onAccent: Color,
    /** Soft tinted surface such as the Home card icon well, a selected chip or a disabled face. */
    val container: Color,
    val onContainer: Color,
    /** Identity-colored text on neutral surfaces, where [accent] itself would lack contrast. */
    val label: Color,
    /** Darker shade of [accent] that shows under a raised face. */
    val ledge: Color,
)

/**
 * What a view wears wherever it shows up: its Home tile, its screen header, its gallery section.
 * A feature's presentation function returns this unless the feature owns more than an identity.
 */
internal data class ViewIdentity(
    val icon: ImageVector,
    val colors: ViewColors,
)

/** The one formula that turns a palette hue into a view identity. */
internal fun MomentumHue.viewColors(): ViewColors = ViewColors(
    accent = face,
    onAccent = ink,
    container = container,
    onContainer = label,
    label = label,
    ledge = ledge,
)

/**
 * What a control wears outside every view: the app's hueless chrome. The face is the scheme's
 * neutral `primary`, near-white, and this is the one place that decides which shade reads as depth
 * under it: the grey outline.
 */
internal val neutralViewColors: ViewColors = ViewColors(
    accent = MomentumDarkOnSurface,
    onAccent = MomentumDarkBackground,
    container = MomentumDarkSurfaceHighest,
    onContainer = MomentumDarkOnSurfaceVariant,
    label = MomentumDarkOnSurface,
    ledge = MomentumDarkOutline,
)

/**
 * The identity each of Mo's tasks wears, its view's stored look, which the app provides. What shows
 * a task of another view, such as Mo's tokens, reads it here instead of depending on the app.
 * Where nothing provides it, every task wears the neutral face.
 */
internal val LocalTrackerIdentity = staticCompositionLocalOf<@Composable (TrackerRef) -> ViewIdentity> {
    { ViewIdentity(Icons.Rounded.Circle, LocalViewColors.current) }
}

/** The identity of the enclosing [ViewTheme], or the neutral chrome when there is none. */
internal val LocalViewColors = staticCompositionLocalOf { neutralViewColors }

/**
 * Tints everything inside a view with its identity: Material controls that default to `primary`
 * (switches, text field focus, chips) and [MomentumButton] defaults follow the view instead of
 * the app-wide scheme.
 */
@Composable
internal fun ViewTheme(colors: ViewColors, content: @Composable () -> Unit) {
    val baseScheme = MaterialTheme.colorScheme
    // A fresh ColorScheme on every recomposition would invalidate everyone who reads the theme.
    val scheme = remember(baseScheme, colors) {
        baseScheme.copy(
            primary = colors.accent,
            onPrimary = colors.onAccent,
            primaryContainer = colors.container,
            onPrimaryContainer = colors.onContainer,
            secondaryContainer = colors.container,
            onSecondaryContainer = colors.onContainer,
        )
    }
    MaterialTheme(colorScheme = scheme) {
        CompositionLocalProvider(LocalViewColors provides colors, content = content)
    }
}

internal fun ViewColors.momentumButtonColors(): MomentumButtonColors = MomentumButtonColors(
    container = accent,
    content = onAccent,
    ledge = ledge,
    disabledContainer = container,
    disabledContent = onContainer,
)

/** Completion is gold in every view so a reward always reads as the same reward. */
internal fun ViewColors.completionButtonColors(): CompletionButtonColors = CompletionButtonColors(
    actionContainer = accent,
    actionContent = onAccent,
    completedContainer = MomentumReward.face,
    completedContent = MomentumReward.ink,
    disabledContainer = container,
    disabledContent = onContainer,
    depth = ledge,
    burstPrimary = ledge,
    burstSecondary = MomentumHues.Orange.ledge,
    burstTertiary = MomentumOnVibrant,
)

/**
 * What the app's own places wear, Settings and Statistics: the neutral chrome with the quieter
 * control grey as its accent, so neither competes with a view's color nor takes one for itself.
 */
@Composable
internal fun appPlaceViewColors(): ViewColors {
    val scheme = MaterialTheme.colorScheme
    return neutralViewColors.copy(
        accent = scheme.onSurfaceVariant,
        onAccent = scheme.surfaceContainerLowest,
        container = scheme.surfaceVariant,
        onContainer = scheme.onSurfaceVariant,
    )
}

package com.example.workouttracker.core.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

// The app chrome has no hue of its own: `primary` is neutral so an unthemed control can never
// borrow a view's identity. Views bring their color through ViewTheme.
private val MomentumColorScheme = darkColorScheme(
    primary = MomentumDarkOnSurface,
    onPrimary = MomentumDarkBackground,
    primaryContainer = MomentumDarkSurfaceHighest,
    onPrimaryContainer = MomentumDarkOnSurface,
    secondary = MomentumDarkOnSurfaceVariant,
    onSecondary = MomentumDarkBackground,
    secondaryContainer = MomentumDarkSurfaceHigh,
    onSecondaryContainer = MomentumDarkOnSurface,
    tertiary = MomentumHues.Indigo.label,
    onTertiary = MomentumHues.Indigo.ink,
    tertiaryContainer = MomentumHues.Indigo.container,
    onTertiaryContainer = MomentumHues.Indigo.label,
    error = MomentumHues.Red.label,
    onError = MomentumHues.Red.ink,
    errorContainer = MomentumHues.Red.container,
    onErrorContainer = MomentumHues.Red.label,
    background = MomentumDarkBackground,
    onBackground = MomentumDarkOnSurface,
    surface = MomentumDarkBackground,
    onSurface = MomentumDarkOnSurface,
    surfaceVariant = MomentumDarkSurfaceVariant,
    onSurfaceVariant = MomentumDarkOnSurfaceVariant,
    outline = MomentumDarkOutline,
    outlineVariant = MomentumDarkSurfaceVariant,
    surfaceDim = MomentumDarkBackground,
    surfaceBright = MomentumDarkSurfaceBright,
    surfaceContainerLowest = MomentumDarkSurfaceLowest,
    surfaceContainerLow = MomentumDarkSurfaceLow,
    surfaceContainer = MomentumDarkSurface,
    surfaceContainerHigh = MomentumDarkSurfaceHigh,
    surfaceContainerHighest = MomentumDarkSurfaceHighest,
)

/**
 * Momentum's one look. It is dark whatever the phone is set to: Mo, his props and every view's
 * colors are drawn and judged on the dark ground, and there is no light scheme to fall into.
 */
@Composable
fun WorkoutTrackerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MomentumColorScheme,
        typography = Typography,
        content = content,
    )
}

package com.example.workouttracker.feature.checklist

import androidx.compose.runtime.Composable
import com.example.workouttracker.core.ui.ViewIdentity
import com.example.workouttracker.domain.LoggedViewKind
import com.example.workouttracker.domain.ViewLook

/**
 * A view wears the icon and hue its configuration names, as a checklist does: a measurement, a
 * questions view, timer and Activity configuration.
 */
@Composable
internal fun ViewLook.presentation(): ViewIdentity = viewIdentity(icon, hue)

/**
 * The kind's name inside a test tag, which is never translated: a made view's tile is
 * `home_<kind>_<id>`, its bank `streak_<kind>_<id>`, its row in Settings `settings_group_<kind>_<id>`
 * and its page `<kind>_settings`.
 */
internal val LoggedViewKind.tagName: String
    get() = when (this) {
        LoggedViewKind.Measurement -> "measurement"
        LoggedViewKind.Questions -> "questions"
        LoggedViewKind.Time -> "time"
    }

package com.example.workouttracker.feature.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.runtime.Composable
import com.example.workouttracker.core.ui.ViewIdentity
import com.example.workouttracker.core.ui.appPlaceViewColors

/** Settings deliberately has no hue of its own: it wears the app's places' neutral chrome. */
@Composable
internal fun settingsPresentation(): ViewIdentity = ViewIdentity(icon = Icons.Rounded.Settings, colors = appPlaceViewColors())

package com.example.workouttracker.feature.tracker

import androidx.compose.runtime.Composable
import com.example.workouttracker.core.ui.ViewIdentity
import com.example.workouttracker.core.ui.neutralViewColors
import com.example.workouttracker.domain.checklist.ViewIcon
import com.example.workouttracker.feature.checklist.vector

/** A deleted tracker has no configured identity to render. */
@Composable
internal fun missingTrackerIdentity(): ViewIdentity =
    ViewIdentity(ViewIcon.Checklist.vector, neutralViewColors)

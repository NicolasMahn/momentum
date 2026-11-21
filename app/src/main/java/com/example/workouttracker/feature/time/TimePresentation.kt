package com.example.workouttracker.feature.time

import androidx.compose.runtime.Composable
import com.example.workouttracker.domain.mo.MoState
import com.example.workouttracker.domain.mo.saveAct
import com.example.workouttracker.domain.time.TimeConfiguration
import com.example.workouttracker.feature.checklist.presentation
import com.example.workouttracker.feature.mo.MoLook

internal val TimeConfiguration.testTagPrefix: String get() = "time_$id"

/** The timer's configuration owns its identity; its motif only chooses Mo's activity. */
@Composable
internal fun TimeConfiguration.sessionAppearance() = TimeSessionAppearance(
    words = words(),
    colors = presentation().colors,
    mo = motif?.let { MoLook(state = MoState.Steady, doing = it.saveAct) } ?: CalmMo,
    testTagPrefix = testTagPrefix,
)

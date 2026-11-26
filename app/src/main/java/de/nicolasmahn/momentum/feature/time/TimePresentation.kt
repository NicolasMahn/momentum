package de.nicolasmahn.momentum.feature.time

import androidx.compose.runtime.Composable
import de.nicolasmahn.momentum.domain.mo.MoState
import de.nicolasmahn.momentum.domain.mo.saveAct
import de.nicolasmahn.momentum.domain.time.TimeConfiguration
import de.nicolasmahn.momentum.feature.checklist.presentation
import de.nicolasmahn.momentum.feature.mo.MoLook

internal val TimeConfiguration.testTagPrefix: String get() = "time_$id"

/** The timer's configuration owns its identity; its motif only chooses Mo's activity. */
@Composable
internal fun TimeConfiguration.sessionAppearance() = TimeSessionAppearance(
    words = words(),
    colors = presentation().colors,
    mo = motif?.let { MoLook(state = MoState.Steady, doing = it.saveAct) } ?: CalmMo,
    testTagPrefix = testTagPrefix,
)

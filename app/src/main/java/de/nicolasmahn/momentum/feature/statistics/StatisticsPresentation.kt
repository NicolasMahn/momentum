package de.nicolasmahn.momentum.feature.statistics

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.runtime.Composable
import de.nicolasmahn.momentum.core.ui.ViewIdentity
import de.nicolasmahn.momentum.core.ui.appPlaceViewColors

/** Statistics is a place of the app, like Settings, so it wears no view's color: only its icon is its own. */
@Composable
internal fun statisticsPresentation(): ViewIdentity = ViewIdentity(icon = Icons.Rounded.BarChart, colors = appPlaceViewColors())

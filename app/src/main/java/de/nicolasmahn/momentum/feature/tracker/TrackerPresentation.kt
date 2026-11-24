package de.nicolasmahn.momentum.feature.tracker

import androidx.compose.runtime.Composable
import de.nicolasmahn.momentum.core.ui.ViewIdentity
import de.nicolasmahn.momentum.core.ui.neutralViewColors
import de.nicolasmahn.momentum.domain.checklist.ViewIcon
import de.nicolasmahn.momentum.feature.checklist.vector

/** A deleted tracker has no configured identity to render. */
@Composable
internal fun missingTrackerIdentity(): ViewIdentity =
    ViewIdentity(ViewIcon.Checklist.vector, neutralViewColors)

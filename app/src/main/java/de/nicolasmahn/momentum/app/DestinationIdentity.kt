package de.nicolasmahn.momentum.app

import androidx.compose.runtime.Composable
import de.nicolasmahn.momentum.core.ui.ViewIdentity
import de.nicolasmahn.momentum.domain.settings.DashboardFeature
import de.nicolasmahn.momentum.feature.logactivity.logActivityPresentation
import de.nicolasmahn.momentum.feature.mo.streakPresentation
import de.nicolasmahn.momentum.feature.settings.settingsPresentation
import de.nicolasmahn.momentum.feature.statistics.statisticsPresentation
import de.nicolasmahn.momentum.feature.substances.substanceFeaturePresentation

/**
 * The one table from a destination to the identity it wears. The screen header, the Home tile and
 * the debug gallery all read it, and the `when` is exhaustive, so a new view cannot ship without
 * an identity. Its title is [AppDestination.label].
 *
 * Home owns no identity of its own; where one is needed it wears the same neutral chrome as
 * Settings.
 */
@Composable
internal fun AppDestination.viewIdentity(): ViewIdentity = when (this) {
    AppDestination.Activity -> logActivityPresentation()
    AppDestination.Substances -> substanceFeaturePresentation()
    AppDestination.Statistics -> statisticsPresentation()
    AppDestination.Streak -> streakPresentation()
    // Tracker screens obtain the selected instance's configured identity
    // from its module. Without an instance, the destination can supply
    // only neutral page chrome.
    AppDestination.Checklist,
    AppDestination.Measurement,
    AppDestination.Questions,
    AppDestination.Time,
    AppDestination.Tracker,
    AppDestination.SettingsPage,
    AppDestination.Home,
    AppDestination.Settings,
    -> settingsPresentation()
}

/** Where a dashboard feature lives. Exhaustive, so a new feature has to name its destination. */
internal val DashboardFeature.destination: AppDestination
    get() = when (this) {
        DashboardFeature.Activity -> AppDestination.Activity
        DashboardFeature.Substances -> AppDestination.Substances
        DashboardFeature.Statistics -> AppDestination.Statistics
    }

/**
 * The feature a user can hide, or null for the places that are always there. Statistics is one of
 * those now: its old switch may still be stored as off, and is read by nothing.
 */
internal val AppDestination.dashboardFeature: DashboardFeature?
    get() = DashboardFeature.entries.firstOrNull { it.destination == this && it != DashboardFeature.Statistics }

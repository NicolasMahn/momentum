package com.example.workouttracker.domain.settings

import com.example.workouttracker.domain.compatibility.MoNeed

/** These settings with the view of each of [needs] hidden from the beginning, as a test names Mo's tasks. */
internal fun FeatureVisibilitySettings.hidingTasks(vararg needs: MoNeed): FeatureVisibilitySettings =
    needs.fold(this) { settings, need -> settings.hiding(need.view) }

/**
 * Every view of Mo's tasks and every feature hidden from the beginning but the views of [shown]
 * and [features]: what a test that judges some tasks alone starts from.
 */
internal fun showingOnly(vararg shown: MoNeed, features: Set<DashboardFeature> = emptySet()): FeatureVisibilitySettings {
    val everything = DashboardFeature.entries.map { ViewRef.Feature(it) } + MoNeed.entries.map { it.view }
    val kept = shown.map { it.view } + features.map { ViewRef.Feature(it) }
    return FeatureVisibilitySettings((everything - kept.toSet()).associateWith { HiddenFromTheBeginning })
}

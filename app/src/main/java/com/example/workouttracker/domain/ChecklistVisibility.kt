package com.example.workouttracker.domain

import com.example.workouttracker.domain.checklist.ChecklistConfiguration
import com.example.workouttracker.domain.checklist.isInUse
import com.example.workouttracker.domain.settings.FeatureVisibilitySettings
import com.example.workouttracker.domain.settings.ViewRef
import java.time.LocalDate

/** Whether this checklist is currently visible. */
fun FeatureVisibilitySettings.shows(checklist: ChecklistConfiguration): Boolean = isChecklistVisible(checklist.id)

/**
 * [visibility] with every checklist that has no item in use on [today] hidden: Home, the alarms
 * and the streak read this one, so a checklist with nothing on it is nowhere in use. It is hidden
 * from the beginning, as it always was: this is no choice of the person's to date, and hiding it
 * from today would bring its unticked days back into the streak as misses. Settings reads the
 * switches as they are, and says what such a checklist lacks.
 */
fun AppState.visibilityInUse(visibility: FeatureVisibilitySettings, today: LocalDate): FeatureVisibilitySettings =
    checklists.filterNot { it.isInUse(today) }.fold(visibility) { shown, checklist -> shown.hidingChecklist(checklist.id) }

/**
 * Every shown checklist with an item in use on [today], irrespective of how it was created.
 * Settings still lists configurations whose items have ended.
 */
fun AppState.checklistsInUse(visibility: FeatureVisibilitySettings, today: LocalDate): List<ChecklistConfiguration> =
    visibilityInUse(visibility, today).let { shown -> checklists.filter { shown.isChecklistVisible(it.id) } }

/**
 * [visibility] without the entry of any checklist, measurement, questions view or time view no
 * configuration has. Deleting one drops its entry in a second write, the visibility settings
 * being another file, and where that write failed the entry is left behind. It hides nothing, and
 * whoever next saves the settings with the configurations at hand saves them without it.
 */
fun AppState.withoutLeftoverEntries(visibility: FeatureVisibilitySettings): FeatureVisibilitySettings {
    val configured = checklists.mapTo(HashSet<ViewRef>()) { ViewRef.Checklist(it.id) } + loggedViews.map { ViewRef.Logged(it.ref) }
    val leftover = visibility.hidden.keys.filter { it !is ViewRef.Feature && it !in configured }
    return leftover.fold(visibility) { kept, key -> kept.forgetting(key) }
}

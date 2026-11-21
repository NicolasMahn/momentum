package com.example.workouttracker.domain.compatibility

import com.example.workouttracker.domain.AppState
import com.example.workouttracker.domain.DailyView
import com.example.workouttracker.domain.LoggedView
import com.example.workouttracker.domain.LoggedViewRef
import com.example.workouttracker.domain.checklist.AboutDay
import com.example.workouttracker.domain.checklist.ChecklistConfiguration
import com.example.workouttracker.domain.checklist.Dated
import com.example.workouttracker.domain.compatibility.MoNeed
import com.example.workouttracker.domain.compatibility.moNeed
import com.example.workouttracker.domain.ref
import com.example.workouttracker.domain.settings.ViewRef

private val InheritedChecklistIds = setOf(CareChecklistId, SupplementsChecklistId)

/** Changing ownership would reinterpret existing records; inherited schedules keep their meaning. */
internal fun requireCompatibleChecklistAbout(id: String, about: Dated<AboutDay>) {
    require(id !in InheritedChecklistIds || about == Dated.always(AboutDay.Today)) { "An inherited checklist keeps its recorded-day ownership." }
}

/** Stable old aliases used only by historical presentation and tests. Current discovery uses every configuration. */
val ChecklistConfiguration.moNeed: MoNeed? get() = ViewRef.Checklist(id).moNeed
val AppState.configuredChecklists: List<ChecklistConfiguration> get() = checklists.filter { it.id !in InheritedChecklistIds }
val AppState.madeDailyViews: List<DailyView> get() = madeMeasurements + madeQuestions
val AppState.madeLoggedViews: List<LoggedView> get() = madeDailyViews + madeTime
fun AppState.madeLoggedView(ref: LoggedViewRef): LoggedView? = madeLoggedViews.firstOrNull { it.ref == ref }

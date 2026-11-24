package de.nicolasmahn.momentum.domain.compatibility

import de.nicolasmahn.momentum.domain.AppState
import de.nicolasmahn.momentum.domain.DailyView
import de.nicolasmahn.momentum.domain.LoggedView
import de.nicolasmahn.momentum.domain.LoggedViewRef
import de.nicolasmahn.momentum.domain.checklist.AboutDay
import de.nicolasmahn.momentum.domain.checklist.ChecklistConfiguration
import de.nicolasmahn.momentum.domain.checklist.Dated
import de.nicolasmahn.momentum.domain.compatibility.MoNeed
import de.nicolasmahn.momentum.domain.compatibility.moNeed
import de.nicolasmahn.momentum.domain.ref
import de.nicolasmahn.momentum.domain.settings.ViewRef

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

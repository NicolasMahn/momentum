package de.nicolasmahn.momentum.gallery

import de.nicolasmahn.momentum.domain.loggedViews
import de.nicolasmahn.momentum.domain.ref
import de.nicolasmahn.momentum.domain.trackerRef
import de.nicolasmahn.momentum.domain.AppState
import de.nicolasmahn.momentum.domain.LoggedViewKind
import de.nicolasmahn.momentum.domain.LoggedViewRef
import de.nicolasmahn.momentum.domain.settings.DashboardFeature
import de.nicolasmahn.momentum.domain.settings.ViewRef

internal val AppState.weightMeasurement get() = measurements.firstOrNull { it.id == DemoWeightId }
internal val AppState.eatingQuestions get() = questions.firstOrNull { it.id == DemoEatingId }
internal val AppState.readingTime get() = time.firstOrNull { it.id == DemoReadingId }
internal val AppState.careChecklist get() = checklists.firstOrNull { it.id == DemoCareId }
internal val AppState.supplementsChecklist get() = checklists.firstOrNull { it.id == DemoSupplementsId }

/** Selects explicitly configured examples for the fixture display only. */
internal enum class DemoTask(val view: ViewRef) {
    Activity(ViewRef.Feature(DashboardFeature.Activity)),
    Reading(ViewRef.Logged(LoggedViewRef(LoggedViewKind.Time, DemoReadingId))),
    Care(ViewRef.Checklist(DemoCareId)),
    Supplements(ViewRef.Checklist(DemoSupplementsId)),
    Eating(ViewRef.Logged(LoggedViewRef(LoggedViewKind.Questions, DemoEatingId))),
    Weight(ViewRef.Logged(LoggedViewRef(LoggedViewKind.Measurement, DemoWeightId))),
}

@androidx.compose.runtime.Composable
internal fun demoTrackerIdentity(ref: de.nicolasmahn.momentum.domain.TrackerRef): de.nicolasmahn.momentum.core.ui.ViewIdentity {
    val view = fixtureAppState.loggedViews.firstOrNull { it.ref.trackerRef == ref }
    val checklist = fixtureAppState.checklists.firstOrNull { de.nicolasmahn.momentum.domain.TrackerRef(de.nicolasmahn.momentum.domain.TrackerTypeId("checklist"), it.id) == ref }
    val look = when {
        ref == de.nicolasmahn.momentum.domain.ActivityTrackerRef -> fixtureAppState.activity
        else -> view
    }
    return de.nicolasmahn.momentum.feature.checklist.viewIdentity(
        checklist?.icon ?: look?.icon ?: de.nicolasmahn.momentum.domain.checklist.ViewIcon.Checklist,
        checklist?.hue ?: look?.hue ?: de.nicolasmahn.momentum.domain.checklist.ViewHue.Sky,
    )
}

@androidx.compose.runtime.Composable
internal fun demoTrackerIdentity(task: DemoTask) = demoTrackerIdentity(task.view.trackerRef)

internal fun demoTrackerLabel(task: DemoTask): de.nicolasmahn.momentum.core.text.UiText {
    val ref = task.view.trackerRef
    val label = fixtureAppState.checklists.firstOrNull { it.id == ref.id }?.label
        ?: fixtureAppState.loggedViews.firstOrNull { it.ref.trackerRef == ref }?.label
        ?: fixtureAppState.activity?.label.orEmpty()
    return de.nicolasmahn.momentum.core.text.UiText.Verbatim(label)
}

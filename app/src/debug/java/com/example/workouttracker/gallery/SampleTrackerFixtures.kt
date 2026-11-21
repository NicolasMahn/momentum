package com.example.workouttracker.gallery

import com.example.workouttracker.domain.loggedViews
import com.example.workouttracker.domain.ref
import com.example.workouttracker.domain.trackerRef
import com.example.workouttracker.domain.AppState
import com.example.workouttracker.domain.LoggedViewKind
import com.example.workouttracker.domain.LoggedViewRef
import com.example.workouttracker.domain.settings.DashboardFeature
import com.example.workouttracker.domain.settings.ViewRef

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
internal fun demoTrackerIdentity(ref: com.example.workouttracker.domain.TrackerRef): com.example.workouttracker.core.ui.ViewIdentity {
    val view = fixtureAppState.loggedViews.firstOrNull { it.ref.trackerRef == ref }
    val checklist = fixtureAppState.checklists.firstOrNull { com.example.workouttracker.domain.TrackerRef(com.example.workouttracker.domain.TrackerTypeId("checklist"), it.id) == ref }
    val look = when {
        ref == com.example.workouttracker.domain.ActivityTrackerRef -> fixtureAppState.activity
        else -> view
    }
    return com.example.workouttracker.feature.checklist.viewIdentity(
        checklist?.icon ?: look?.icon ?: com.example.workouttracker.domain.checklist.ViewIcon.Checklist,
        checklist?.hue ?: look?.hue ?: com.example.workouttracker.domain.checklist.ViewHue.Sky,
    )
}

@androidx.compose.runtime.Composable
internal fun demoTrackerIdentity(task: DemoTask) = demoTrackerIdentity(task.view.trackerRef)

internal fun demoTrackerLabel(task: DemoTask): com.example.workouttracker.core.text.UiText {
    val ref = task.view.trackerRef
    val label = fixtureAppState.checklists.firstOrNull { it.id == ref.id }?.label
        ?: fixtureAppState.loggedViews.firstOrNull { it.ref.trackerRef == ref }?.label
        ?: fixtureAppState.activity?.label.orEmpty()
    return com.example.workouttracker.core.text.UiText.Verbatim(label)
}

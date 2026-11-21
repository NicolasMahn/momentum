package com.example.workouttracker.domain

import com.example.workouttracker.domain.settings.DashboardFeature
import com.example.workouttracker.domain.settings.ViewRef

val ActivityTrackerRef = TrackerRef(TrackerTypeId("activity"), "activity")
val SubstancesTrackerRef = TrackerRef(TrackerTypeId("substances"), "substances")

val LoggedViewRef.trackerRef: TrackerRef
    get() = TrackerRef(TrackerTypeId(when (kind) {
        LoggedViewKind.Measurement -> "measurement"
        LoggedViewKind.Questions -> "questions"
        LoggedViewKind.Time -> "time"
    }), id)

val ViewRef.trackerRef: TrackerRef
    get() = when (this) {
        is ViewRef.Checklist -> TrackerRef(TrackerTypeId("checklist"), id)
        is ViewRef.Logged -> view.trackerRef
        is ViewRef.Feature -> when (feature) {
            DashboardFeature.Activity -> ActivityTrackerRef
            DashboardFeature.Substances -> SubstancesTrackerRef
            DashboardFeature.Statistics -> error("Statistics is an application destination, not a tracker.")
        }
    }

/** Adapts the existing visibility wire model; an extension namespace has no legacy representation. */
fun TrackerRef.viewRefOrNull(): ViewRef? = when (type.value) {
    "activity" -> ViewRef.Feature(DashboardFeature.Activity).takeIf { this == ActivityTrackerRef }
    "substances" -> ViewRef.Feature(DashboardFeature.Substances).takeIf { this == SubstancesTrackerRef }
    "checklist" -> ViewRef.Checklist(id)
    "measurement" -> ViewRef.Logged(LoggedViewRef(LoggedViewKind.Measurement, id))
    "questions" -> ViewRef.Logged(LoggedViewRef(LoggedViewKind.Questions, id))
    "time" -> ViewRef.Logged(LoggedViewRef(LoggedViewKind.Time, id))
    else -> null
}

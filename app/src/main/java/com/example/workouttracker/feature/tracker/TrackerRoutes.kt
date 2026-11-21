package com.example.workouttracker.feature.tracker

import com.example.workouttracker.domain.LoggedViewKind
import com.example.workouttracker.domain.trackerRef
import com.example.workouttracker.domain.LoggedViewRef
import com.example.workouttracker.domain.TrackerRef
import com.example.workouttracker.domain.TrackerTypeId
import com.example.workouttracker.feature.checklist.tagName
import com.example.workouttracker.feature.settings.SettingsPage

internal fun TrackerRef.loggedViewRefOrNull(): LoggedViewRef? =
    LoggedViewKind.entries.firstOrNull { it.tagName == type.value }?.let { LoggedViewRef(it, id) }

internal val SettingsPage.trackerRef: TrackerRef?
    get() = when (this) {
        is SettingsPage.Tracker -> ref
        is SettingsPage.Checklist -> TrackerRef(TrackerTypeId("checklist"), checklistId)
        is SettingsPage.Measurement -> LoggedViewRef(LoggedViewKind.Measurement, measurementId).trackerRef
        is SettingsPage.Questions -> LoggedViewRef(LoggedViewKind.Questions, viewId).trackerRef
        is SettingsPage.Time -> LoggedViewRef(LoggedViewKind.Time, viewId).trackerRef
        else -> null
    }

internal val SettingsPage.trackerCreationType: TrackerTypeId?
    get() = when (this) {
        is SettingsPage.NewTracker -> type
        SettingsPage.NewChecklist -> TrackerTypeId("checklist")
        SettingsPage.NewMeasurement -> TrackerTypeId("measurement")
        SettingsPage.NewQuestions -> TrackerTypeId("questions")
        SettingsPage.NewTime -> TrackerTypeId("time")
        else -> null
    }

/** Which way of starting [trackerCreationType] the page opens, where its kind has more than one. */
internal val SettingsPage.trackerCreationVariant: String?
    get() = (this as? SettingsPage.NewTracker)?.variant

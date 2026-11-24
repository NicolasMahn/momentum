package de.nicolasmahn.momentum.feature.tracker

import de.nicolasmahn.momentum.domain.LoggedViewKind
import de.nicolasmahn.momentum.domain.trackerRef
import de.nicolasmahn.momentum.domain.LoggedViewRef
import de.nicolasmahn.momentum.domain.TrackerRef
import de.nicolasmahn.momentum.domain.TrackerTypeId
import de.nicolasmahn.momentum.feature.checklist.tagName
import de.nicolasmahn.momentum.feature.settings.SettingsPage

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

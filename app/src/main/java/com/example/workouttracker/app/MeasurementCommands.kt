package com.example.workouttracker.app

import com.example.workouttracker.core.time.currentHabitDate
import com.example.workouttracker.core.time.nowMillis
import com.example.workouttracker.domain.AppState
import com.example.workouttracker.domain.measurement.MeasurementConfiguration
import com.example.workouttracker.domain.measurement.MeasurementEdit
import com.example.workouttracker.domain.measurement.addMeasurement
import com.example.workouttracker.domain.measurement.applyingMeasurementEdits
import com.example.workouttracker.domain.measurement.deleteMeasurement
import com.example.workouttracker.feature.measurement.MeasurementDayChange
import com.example.workouttracker.persistence.TrackerRepository
import java.time.LocalDate

/**
 * Stores edits and records from a measurement's settings, setup and screen ([LoggedViewCommands]).
 * Reconciliation refreshes a posted reminder when the configured unit changes.
 */
internal class MeasurementCommands(
    repository: TrackerRepository,
    reconcile: (AppState) -> Boolean,
    exactAlarmsAllowed: () -> Boolean,
    refresh: () -> Unit,
    stored: (AppState) -> Unit,
    hidden: () -> Set<String>,
    forgetHidden: (measurementId: String) -> Unit,
    today: () -> LocalDate = ::currentHabitDate,
    now: () -> Long = ::nowMillis,
) : LoggedViewCommands<MeasurementConfiguration, MeasurementEdit, MeasurementDayChange>(repository, reconcile, exactAlarmsAllowed, refresh, stored, hidden, forgetHidden, today, now) {
    override fun AppState.view(viewId: String): MeasurementConfiguration? = measurements.firstOrNull { it.id == viewId }

    override fun AppState.applyingEdits(viewId: String, edits: List<MeasurementEdit>, day: LocalDate): AppState = applyingMeasurementEdits(viewId, edits, day)

    override fun AppState.adding(starter: MeasurementConfiguration, hidden: Set<String>, day: LocalDate): AppState = addMeasurement(starter, hidden)

    override fun AppState.deleting(viewId: String): AppState = deleteMeasurement(viewId)

    override fun AppState.with(view: MeasurementConfiguration): AppState = copy(measurements = measurements.map { if (it.id == view.id) view else it })
}

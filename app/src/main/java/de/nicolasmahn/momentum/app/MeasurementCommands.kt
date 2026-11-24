package de.nicolasmahn.momentum.app

import de.nicolasmahn.momentum.core.time.currentHabitDate
import de.nicolasmahn.momentum.core.time.nowMillis
import de.nicolasmahn.momentum.domain.AppState
import de.nicolasmahn.momentum.domain.measurement.MeasurementConfiguration
import de.nicolasmahn.momentum.domain.measurement.MeasurementEdit
import de.nicolasmahn.momentum.domain.measurement.addMeasurement
import de.nicolasmahn.momentum.domain.measurement.applyingMeasurementEdits
import de.nicolasmahn.momentum.domain.measurement.deleteMeasurement
import de.nicolasmahn.momentum.feature.measurement.MeasurementDayChange
import de.nicolasmahn.momentum.persistence.TrackerRepository
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

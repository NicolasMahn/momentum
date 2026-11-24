package de.nicolasmahn.momentum.app

import de.nicolasmahn.momentum.core.time.currentHabitDate
import de.nicolasmahn.momentum.core.time.nowMillis
import de.nicolasmahn.momentum.domain.AppState
import de.nicolasmahn.momentum.domain.DayChangeResult
import de.nicolasmahn.momentum.domain.time.TimeConfiguration
import de.nicolasmahn.momentum.domain.time.TimeEdit
import de.nicolasmahn.momentum.domain.time.TimeSession
import de.nicolasmahn.momentum.domain.time.addTime
import de.nicolasmahn.momentum.domain.time.applyingTimeEdits
import de.nicolasmahn.momentum.domain.time.deleteTime
import de.nicolasmahn.momentum.feature.time.TimeDayChange
import de.nicolasmahn.momentum.feature.time.TimedMinutes
import de.nicolasmahn.momentum.persistence.TrackerRepository
import java.time.LocalDate

/**
 * Stores a timer's settings, daily minutes and timed sessions through [LoggedViewCommands].
 */
internal class TimeCommands(
    repository: TrackerRepository,
    reconcile: (AppState) -> Boolean,
    exactAlarmsAllowed: () -> Boolean,
    refresh: () -> Unit,
    stored: (AppState) -> Unit,
    hidden: () -> Set<String>,
    forgetHidden: (viewId: String) -> Unit,
    today: () -> LocalDate = ::currentHabitDate,
    now: () -> Long = ::nowMillis,
) : LoggedViewCommands<TimeConfiguration, TimeEdit, TimeDayChange>(repository, reconcile, exactAlarmsAllowed, refresh, stored, hidden, forgetHidden, today, now) {
    override fun AppState.view(viewId: String): TimeConfiguration? = time.firstOrNull { it.id == viewId }

    override fun AppState.applyingEdits(viewId: String, edits: List<TimeEdit>, day: LocalDate): AppState = applyingTimeEdits(viewId, edits, day)

    override fun AppState.adding(starter: TimeConfiguration, hidden: Set<String>, day: LocalDate): AppState = addTime(starter, hidden)

    override fun AppState.deleting(viewId: String): AppState = deleteTime(viewId)

    override fun AppState.with(view: TimeConfiguration): AppState = copy(time = time.map { if (it.id == view.id) view else it })

    /**
     * Stores what a timed session timed as it ends, decided against the logs as they are stored
     * as a day's typed minutes are ([TimedMinutes]): refused where its view was deleted meanwhile.
     */
    fun saveSession(session: TimeSession, endMillis: Long): DayChangeResult = storeDay(TimedMinutes(session, endMillis))
}

package com.example.workouttracker.app

import com.example.workouttracker.core.time.currentHabitDate
import com.example.workouttracker.core.time.nowMillis
import com.example.workouttracker.domain.AppState
import com.example.workouttracker.domain.DayChangeResult
import com.example.workouttracker.domain.time.TimeConfiguration
import com.example.workouttracker.domain.time.TimeEdit
import com.example.workouttracker.domain.time.TimeSession
import com.example.workouttracker.domain.time.addTime
import com.example.workouttracker.domain.time.applyingTimeEdits
import com.example.workouttracker.domain.time.deleteTime
import com.example.workouttracker.feature.time.TimeDayChange
import com.example.workouttracker.feature.time.TimedMinutes
import com.example.workouttracker.persistence.TrackerRepository
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

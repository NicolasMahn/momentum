package com.example.workouttracker.domain

import com.example.workouttracker.domain.checklist.ChecklistRewards
import com.example.workouttracker.domain.checklist.Checkpoint
import com.example.workouttracker.domain.checklist.Dated
import com.example.workouttracker.domain.checklist.WhenAway
import com.example.workouttracker.domain.holiday.DayOff
import com.example.workouttracker.domain.holiday.HolidaySchedule
import com.example.workouttracker.domain.measurement.isMeasurementLogged
import com.example.workouttracker.domain.questions.questionsEntry
import com.example.workouttracker.domain.time.timeLog
import java.time.LocalDate

/** The kinds of view whose day holds one log: a number, answers to questions, or minutes. */
enum class LoggedViewKind { Measurement, Questions, Time }

/** Date ownership, away policy and reminder shared by the three configured record families. */
interface LoggedView : ViewLook {
    val kind: LoggedViewKind

    /** What an away day means, due or excused, by the rule each day had. */
    val whenAway: Dated<WhenAway>

    /** The one reminder of the view, the checkpoint `day`: whether it reminds, and when. */
    val checkpoint: Checkpoint

    /**
     * The day due day [dueDay] asks about, or null where it asks about none. The reminder and
     * where its tap opens the view read the day through this. A measurement and a time view answer
     * with that day, a time view's reminder asking about the week it is in; a questions view
     * answers with that day or the one before, and with nothing for a day before it was made.
     */
    fun recordDayFor(dueDay: LocalDate): LocalDate?
}

/** A tracker judged by the daily record selected by its configuration. */
interface DailyView : LoggedView {
    /** The configured daily bank, or null when daily completion earns no protection. */
    val standardRewards: ChecklistRewards?

    /**
     * [recordDayFor], or null on a day whose record an earlier due day already asked for: a record
     * is asked for once, so that day is not judged. The reward walk reads it this way, and Home's
     * badge follows the walk.
     */
    fun recordJudgedOn(dueDay: LocalDate): LocalDate?
}

/**
 * Names one configuration within its stored record family. A measurement's, a questions view's and a time view's IDs
 * are each unique only among their own kind, so the kind is part of the name.
 */
data class LoggedViewRef(val kind: LoggedViewKind, val id: String)

val LoggedView.ref: LoggedViewRef get() = LoggedViewRef(kind, id)

/**
 * Whether [date] is away and this view is not due then, by the rule of that day. A sick day wins
 * over an away day: it asks nothing of the streak, yet a reminder still comes, so a day that is
 * both is not excused. The configuration owns what an away day means ([LoggedView.whenAway]).
 */
fun LoggedView.isExcusedOn(date: LocalDate, daysOff: HolidaySchedule): Boolean =
    daysOff.dayOff(date) == DayOff.Away && whenAway.on(date) == WhenAway.Excused

/** Configured measurements, questions and timers, in family order. */
val AppState.loggedViews: List<LoggedView> get() = measurements + questions + time

/** The configuration named by [ref], or null when it no longer exists. */
fun AppState.loggedView(ref: LoggedViewRef): LoggedView? = loggedViews.firstOrNull { it.ref == ref }

/**
 * Whether [dateKey] holds a record of [view]: any number for a measurement, however many, an
 * entry for a questions view, whatever it says and also with no answer at all, and a log for a
 * time view, whatever minutes it holds.
 */
fun AppState.isLoggedOn(view: LoggedViewRef, dateKey: String): Boolean = when (view.kind) {
    LoggedViewKind.Measurement -> isMeasurementLogged(view.id, dateKey)
    LoggedViewKind.Questions -> questionsEntry(view.id, dateKey) != null
    LoggedViewKind.Time -> timeLog(view.id, dateKey) != null
}

package de.nicolasmahn.momentum.domain.activity

import de.nicolasmahn.momentum.core.time.parseDateKey
import de.nicolasmahn.momentum.domain.AppState
import de.nicolasmahn.momentum.domain.activity.routine.WorkoutSession
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * One thing a person did on a day, naming its activity by the ID it has in Activity's
 * configuration ([activityId]) and keeping the name it was logged under ([name]). An
 * entry of an activity the configuration never knew is kept as it is and shown by its name.
 */
data class ActivityEntry(
    val id: String,
    val activityId: String,
    val name: String,
    val dateKey: String,
    val timestampMillis: Long,
    val durationMinutes: Int? = null,
    val notes: String? = null,
)

/** A session of a routine is an activity too, so either kind of record makes the day logged. */
fun isActivityLoggedFor(
    entries: List<ActivityEntry>,
    sessions: List<WorkoutSession>,
    todayKey: String,
): Boolean = entries.any { it.dateKey == todayKey } || sessions.any { it.dateKey == todayKey }

/**
 * Whether anything that keeps the day was logged on [dateKey]: an entry, or a session with a set.
 * The workout reminder goes quiet on such a day, whatever was logged.
 */
fun AppState.hasActivityOn(dateKey: String): Boolean =
    activityEntries.any { it.dateKey == dateKey } || sessions.any { it.dateKey == dateKey && it.totalLoggedSets > 0 }

/** Actual distinct activity days, irrespective of the current plan or any rest-day tokens. */
fun activityDaysForWeek(
    entries: List<ActivityEntry>,
    sessions: List<WorkoutSession>,
    monday: LocalDate,
): Int {
    require(monday.dayOfWeek == DayOfWeek.MONDAY)
    val sunday = monday.plusDays(6)
    return (entries.map { it.dateKey } + sessions.map { it.dateKey })
        .mapNotNull(::parseDateKey)
        .countDistinctIn(monday, sunday)
}

private fun List<LocalDate>.countDistinctIn(first: LocalDate, last: LocalDate): Int =
    asSequence().filter { !it.isBefore(first) && !it.isAfter(last) }.distinct().count()

/**
 * What Activity records must be, whether their optional configuration still exists or not: records
 * that name what they must. An entry has an ID, an activity, the name it was logged under, a day
 * and a time, and minutes above zero if any; a session has an ID, an activity, a day of its
 * routine, a date and a time, minutes above zero if any, and logs that each name an exercise and
 * the name it was recorded under. Nothing is asked of a set: its reps are a number and its
 * weight one or none, whatever an older version recorded. The decoder and the store's write
 * guard both ask this, so a state that could not be read back is never written. Everything the
 * configuration must be, it refuses itself.
 */
fun requireStorableActivity(activity: ActivityConfiguration?, entries: List<ActivityEntry>, sessions: List<WorkoutSession>) {
    entries.forEach { entry ->
        require(entry.id.isNotBlank() && entry.activityId.isNotBlank() && entry.name.isNotBlank() && entry.dateKey.isNotBlank()) { "An entry names its ID, its activity, its name and its day." }
        require(entry.durationMinutes == null || entry.durationMinutes >= 1) { "An entry's minutes are above zero." }
    }
    sessions.forEach { session ->
        require(session.id.isNotBlank() && session.activityId.isNotBlank() && session.day.isNotBlank() && session.dateKey.isNotBlank()) { "A session names its ID, its activity, its day and its date." }
        require(session.durationMinutes == null || session.durationMinutes >= 1) { "A session's minutes are above zero." }
        session.exerciseLogs.forEach { log -> require(log.exerciseId.isNotBlank() && log.exerciseName.isNotBlank()) { "A log names its exercise and the name it was recorded under." } }
    }
}

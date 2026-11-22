package com.example.workouttracker.feature.logactivity

import androidx.annotation.StringRes
import com.example.workouttracker.R
import com.example.workouttracker.core.time.isValidDateKey
import com.example.workouttracker.core.time.nowMillis
import com.example.workouttracker.domain.AppState
import com.example.workouttracker.domain.activity.ActivityConfiguration
import com.example.workouttracker.domain.activity.ActivityEntry
import com.example.workouttracker.domain.activity.ListedActivity
import com.example.workouttracker.domain.activity.OtherActivityIcon
import com.example.workouttracker.domain.activity.activityNameKey
import com.example.workouttracker.domain.activity.activityRules
import com.example.workouttracker.domain.activity.freshActivityId
import com.example.workouttracker.domain.activity.route.Route
import com.example.workouttracker.domain.activity.route.RouteRecording
import java.util.UUID

sealed interface RecordActivityResult {
    data class Success(
        val state: AppState,
        val entry: ActivityEntry,
    ) : RecordActivityResult

    data class Invalid(val problem: ActivityProblem) : RecordActivityResult
}

/** Why an activity could not be recorded, in words the form can show. */
enum class ActivityProblem(@StringRes val message: Int) {
    MissingName(R.string.activity_error_missing_name),
    InvalidDuration(R.string.activity_error_duration),
    // Only a bug reaches these two: the day is one the calendar offers or a stored one, and the
    // ids are fresh UUIDs or stored ones, which are never blank. The person hears that the save
    // failed; the case keeps the reason for whoever debugs it.
    InvalidDate(R.string.activity_save_failed),
    BlankEntryId(R.string.activity_save_failed),
    NotFound(R.string.activity_error_not_found),
}

sealed interface DeleteActivityResult {
    data class Success(val state: AppState) : DeleteActivityResult
    data class Invalid(val message: String) : DeleteActivityResult
}

/**
 * Records [rawName] on [dateKey] under the activity of that name, by the one rule names are
 * compared by: a listed one, or a removed one, which comes back at the end of the list; a name no
 * activity has adds one at the end of the list, with Other's icon, under [newActivityId] or an ID
 * nothing names yet. The entry keeps the activity's name as the name it was logged under.
 */
fun recordActivity(
    state: AppState,
    rawName: String,
    dateKey: String,
    durationMinutes: Int?,
    notes: String?,
    newActivityId: String? = null,
    newEntryId: String = UUID.randomUUID().toString(),
    timestampMillis: Long = nowMillis(),
): RecordActivityResult {
    val name = rawName.trim()
    if (name.isEmpty()) return RecordActivityResult.Invalid(ActivityProblem.MissingName)
    problemWith(dateKey, durationMinutes)?.let { return RecordActivityResult.Invalid(it) }
    if (newEntryId.isBlank()) return RecordActivityResult.Invalid(ActivityProblem.BlankEntryId)

    val (configuration, activity) = state.activityRules.taking(name) { newActivityId ?: state.freshActivityId() }
    return state.recording(configuration, activity, dateKey, durationMinutes, notes, newEntryId, timestampMillis)
}

/** Records the activity [activityId], one the configuration lists and still offers, on [dateKey], with the [route] it went if it was recorded with GPS. */
fun recordActivityOf(
    state: AppState,
    activityId: String,
    dateKey: String,
    durationMinutes: Int?,
    notes: String?,
    newEntryId: String = UUID.randomUUID().toString(),
    timestampMillis: Long = nowMillis(),
    route: Route? = null,
): RecordActivityResult {
    val activity = state.activityRules.offered(activityId) ?: return RecordActivityResult.Invalid(ActivityProblem.NotFound)
    problemWith(dateKey, durationMinutes)?.let { return RecordActivityResult.Invalid(it) }
    if (newEntryId.isBlank()) return RecordActivityResult.Invalid(ActivityProblem.BlankEntryId)
    return state.recording(state.activityRules, activity, dateKey, durationMinutes, notes, newEntryId, timestampMillis, route)
}

/**
 * A finished [recording] as an entry of its activity on [dateKey], the habit day it started on,
 * at the moment it started: with its route and its moving time as the entry's minutes. A
 * recording that never kept a point, because the phone found no GPS, is still the activity done,
 * and is saved as a plain entry without a route or minutes. An activity removed while it was
 * recorded keeps the run, as it keeps every entry it had, without coming back to the list.
 *
 * The entry's ID is the recording's own ([routeEntryId]), so finishing the same recording twice,
 * as after the app was stopped between saving it and letting it go, replaces the first save.
 */
fun recordRoute(
    state: AppState,
    recording: RouteRecording,
    dateKey: String,
): RecordActivityResult {
    val activity = state.activityRules.activity(recording.activityId) ?: return RecordActivityResult.Invalid(ActivityProblem.NotFound)
    val route = recording.route.takeUnless { it.isEmpty }
    val minutes = route?.let { ((it.movingSeconds + 30) / 60).toInt() }?.takeIf { it >= 1 }
    problemWith(dateKey, minutes)?.let { return RecordActivityResult.Invalid(it) }
    return state.recording(state.activityRules, activity, dateKey, minutes, notes = null, recording.routeEntryId, recording.startedAtMillis, route)
}

/** The ID a recording's entry is saved under: one per recording, as each starts at its own moment. */
val RouteRecording.routeEntryId: String get() = "route-$activityId-$startedAtMillis"

/** What makes a day or a duration unfit for any entry, or null when both can be stored. */
private fun problemWith(dateKey: String, durationMinutes: Int?): ActivityProblem? = when {
    !isValidDateKey(dateKey) -> ActivityProblem.InvalidDate
    durationMinutes != null && durationMinutes <= 0 -> ActivityProblem.InvalidDuration
    else -> null
}

/** The activity [activityId] while it is listed and still offered. */
private fun ActivityConfiguration.offered(activityId: String): ListedActivity? = activity(activityId)?.takeUnless { it.removed }

/**
 * This configuration with the activity named [name] offered, and that activity: itself where it
 * lists one by that name, with a removed one brought back at the end of the list, and otherwise
 * one added at the end under [freshId], with Other's icon.
 */
private fun ActivityConfiguration.taking(name: String, freshId: () -> String): Pair<ActivityConfiguration, ListedActivity> {
    val existing = activityNamed(name)
    val configuration = when {
        existing == null -> copy(activities = activities + ListedActivity(freshId(), name, OtherActivityIcon))
        existing.removed -> copy(activities = activities.filterNot { it.id == existing.id } + existing.copy(removed = false))
        else -> this
    }
    return configuration to requireNotNull(configuration.activityNamed(name))
}

/** The entry of [activity] under [configuration], which may be this state's own or one it gained an activity in; the entry keeps the activity's name. */
private fun AppState.recording(
    configuration: ActivityConfiguration,
    activity: ListedActivity,
    dateKey: String,
    durationMinutes: Int?,
    notes: String?,
    newEntryId: String,
    timestampMillis: Long,
    route: Route? = null,
): RecordActivityResult {
    val entry = ActivityEntry(
        id = newEntryId.trim(),
        activityId = activity.id,
        name = activity.label,
        dateKey = dateKey,
        timestampMillis = timestampMillis,
        durationMinutes = durationMinutes,
        notes = notes?.trim()?.takeIf { it.isNotEmpty() },
        route = route,
    )
    return RecordActivityResult.Success(replacing(entry, configuration), entry)
}

/**
 * The entry [entryId] on [dateKey] with [durationMinutes], under the activity named [rawName] as
 * [recordActivity] finds or adds one. The name the entry already shows, its activity's label or
 * the name it keeps for one the configuration never knew, is no choice of another activity: then
 * the entry keeps its activity and the name it was logged under, and a removed activity is not
 * brought back. Its notes stay as they are either way.
 */
fun updateActivity(
    state: AppState,
    entryId: String,
    rawName: String,
    dateKey: String,
    durationMinutes: Int?,
    newActivityId: String? = null,
): RecordActivityResult {
    val existing = state.entry(entryId) ?: return RecordActivityResult.Invalid(ActivityProblem.NotFound)
    val name = rawName.trim()
    if (name.isEmpty()) return RecordActivityResult.Invalid(ActivityProblem.MissingName)
    problemWith(dateKey, durationMinutes)?.let { return RecordActivityResult.Invalid(it) }

    val rules = state.activityRules
    if (activityNameKey(name) == activityNameKey(rules.activityLabel(existing.activityId, existing.name))) {
        return state.updating(existing.copy(dateKey = dateKey, durationMinutes = durationMinutes), rules)
    }
    val (configuration, activity) = rules.taking(name) { newActivityId ?: state.freshActivityId() }
    return state.updating(existing.copy(activityId = activity.id, name = activity.label, dateKey = dateKey, durationMinutes = durationMinutes), configuration)
}

/**
 * [updateActivity], for an entry under the activity [activityId]: its own, which it keeps as it
 * is whether the configuration still offers it, or another listed one it moves to, whose label
 * becomes its name.
 */
fun updateActivityOf(
    state: AppState,
    entryId: String,
    activityId: String,
    dateKey: String,
    durationMinutes: Int?,
): RecordActivityResult {
    val existing = state.entry(entryId) ?: return RecordActivityResult.Invalid(ActivityProblem.NotFound)
    val rules = state.activityRules
    val moveTo = when (activityId) {
        existing.activityId -> null
        else -> rules.offered(activityId) ?: return RecordActivityResult.Invalid(ActivityProblem.NotFound)
    }
    problemWith(dateKey, durationMinutes)?.let { return RecordActivityResult.Invalid(it) }
    val updated = existing.copy(activityId = moveTo?.id ?: existing.activityId, name = moveTo?.label ?: existing.name, dateKey = dateKey, durationMinutes = durationMinutes)
    return state.updating(updated, rules)
}

private fun AppState.entry(entryId: String): ActivityEntry? = activityEntries.singleOrNull { it.id == entryId }

private fun AppState.updating(entry: ActivityEntry, configuration: ActivityConfiguration): RecordActivityResult =
    RecordActivityResult.Success(replacing(entry, configuration), entry)

/** This state with [entry] in place of any entry of its ID, under [configuration] where that is not its own. */
private fun AppState.replacing(entry: ActivityEntry, configuration: ActivityConfiguration): AppState = copy(
    activity = if (configuration == activityRules) activity else configuration,
    activityEntries = (activityEntries.filterNot { it.id == entry.id } + entry).sortedByDescending { it.timestampMillis },
)

fun deleteActivity(
    state: AppState,
    entryId: String,
): DeleteActivityResult {
    if (state.activityEntries.count { it.id == entryId } != 1) {
        return DeleteActivityResult.Invalid("Activity not found.")
    }
    return DeleteActivityResult.Success(
        state.copy(activityEntries = state.activityEntries.filterNot { it.id == entryId }),
    )
}

fun deleteWorkoutSession(
    state: AppState,
    sessionId: String,
): DeleteActivityResult {
    if (state.sessions.count { it.id == sessionId } != 1) {
        return DeleteActivityResult.Invalid("Workout not found.")
    }
    return DeleteActivityResult.Success(
        state.copy(sessions = state.sessions.filterNot { it.id == sessionId }),
    )
}

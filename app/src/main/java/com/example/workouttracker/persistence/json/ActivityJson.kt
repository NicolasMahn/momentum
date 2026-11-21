package com.example.workouttracker.persistence.json

import com.example.workouttracker.domain.activity.ActivityConfiguration
import com.example.workouttracker.domain.activity.ActivityEntry
import com.example.workouttracker.domain.activity.ActivityRewards
import com.example.workouttracker.domain.activity.ActivityTokenStep
import com.example.workouttracker.domain.activity.ActivityTokenSteps
import com.example.workouttracker.domain.activity.ActivityViewId
import com.example.workouttracker.domain.activity.ListedActivity
import com.example.workouttracker.domain.activity.PlannedDay
import com.example.workouttracker.domain.activity.WeekPlan
import com.example.workouttracker.domain.activity.routine.ExerciseWeight
import com.example.workouttracker.domain.activity.routine.Routine
import com.example.workouttracker.domain.activity.routine.RoutineDay
import com.example.workouttracker.domain.activity.routine.RoutineExercise
import com.example.workouttracker.domain.checklist.ViewHue
import com.example.workouttracker.domain.checklist.WhenAway
import org.json.JSONArray
import org.json.JSONObject

/** Activity's explicit configuration and stable record IDs; no record is repaired or dropped. */

internal fun ActivityConfiguration.encode(): JSONObject = JSONObject()
    .put("view", ActivityView)
    .put("id", ActivityViewId)
    .put("label", label)
    .put("icon", icon.storageId)
    .put("hue", hue.storageId)
    .put("mo", mo.encode())
    .put("week", week.encode { it.encode() })
    .put("whenAway", whenAway.encode { it.storageId })
    .put("awayDays", awayDays.encode { it })
    .put("rewards", JSONObject().put("maxPasses", rewards.maxPasses.encode { it }).put("tokenSteps", rewards.tokenSteps.encode { it.encode() }).putOptional("periods", rewards.periods?.encode()))
    .put("activities", JSONArray().apply { activities.forEach { put(it.encode()) } })
    .put(LogsDurationField, logsDuration)

internal fun WeekPlan.encode(): JSONObject = JSONObject()
    .put("days", JSONArray().apply { days.forEach { put(JSONObject().put("day", it.day.encodeWeekday()).putOptional("activity", it.activityId)) } })

internal fun JSONObject.decodeWeekPlan(): WeekPlan {
    requireFields(required = setOf("days"))
    return WeekPlan(
        array("days").objects().map { day ->
            day.requireFields(required = setOf("day"), optional = setOf("activity"))
            PlannedDay(day = decodeWeekday(day.text("day")), activityId = if (day.has("activity")) day.text("activity") else null)
        },
    )
}

internal fun ActivityTokenSteps.encode(): JSONObject =
    JSONObject().put("steps", JSONArray().apply { steps.forEach { put(JSONObject().put("daysMore", it.daysMore).put("tokens", it.tokens)) } })

internal fun JSONObject.decodeActivityTokenSteps(): ActivityTokenSteps {
    requireFields(required = setOf("steps"))
    return ActivityTokenSteps(
        array("steps").objects().map { step ->
            step.requireFields(required = setOf("daysMore", "tokens"))
            ActivityTokenStep(daysMore = wholeNumber(step.get("daysMore")), tokens = wholeNumber(step.get("tokens")))
        },
    )
}

private fun ListedActivity.encode(): JSONObject = JSONObject()
    .put("id", id)
    .put("label", label)
    .put("icon", icon.storageId)
    .putOptional(RemovedField, removed.takeIf { it })
    .putOptional(RoutineField, routine?.encode())

private fun Routine.encode(): JSONObject = JSONObject()
    .put("days", JSONArray().apply { days.forEach { put(it.encode()) } })

private fun RoutineDay.encode(): JSONObject = JSONObject()
    .put("id", id)
    .put("label", label)
    .putOptional(RemovedField, removed.takeIf { it })
    .put("exercises", JSONArray().apply { exercises.forEach { put(it.encode()) } })

private fun RoutineExercise.encode(): JSONObject = JSONObject()
    .put("id", id)
    .put("label", label)
    .putOptional(RemovedField, removed.takeIf { it })
    .put("sets", sets)
    .put("reps", JSONObject().put("from", reps.first).put("to", reps.last))
    .put("start", JSONObject().putOptional("weightKg", weight?.startKg).put("reps", startReps))
    .putOptional("stepKg", weight?.stepKg)

private const val ActivityView = "activity"
private const val RemovedField = "removed"
private const val RoutineField = "routine"

/** The `activity` object: Activity's one configuration, read by what it must hold. What the fields may say, the configuration refuses itself. */
internal fun JSONObject.decodeActivityConfiguration(): ActivityConfiguration {
    requireFields(required = setOf("view", "id", "label", "icon", "hue", "week", "whenAway", "awayDays", "rewards", "activities", "mo"), optional = setOf(LogsDurationField))
    require(text("view") == ActivityView) { "Unsupported view kind" }
    require(text("id") == ActivityViewId) { "Activity's configuration is $ActivityViewId" }
    val rewards = getJSONObject("rewards").apply { requireFields(required = setOf("maxPasses", "tokenSteps"), optional = setOf("periods")) }
    return ActivityConfiguration(
        label = text("label"),
        icon = decodeIcon(),
        hue = ViewHue.fromStorageId(text("hue")) ?: error("Unknown hue"),
        mo = getJSONObject("mo").decodeMoPolicy(),
        week = decodeDated("week") { raw -> (raw as? JSONObject ?: error("A week must be an object")).decodeWeekPlan() },
        whenAway = decodeDated("whenAway") { raw -> WhenAway.fromStorageId(raw as? String ?: error("whenAway must be text")) ?: error("Unknown whenAway") },
        awayDays = decodeDated("awayDays", ::wholeNumber),
        rewards = ActivityRewards(
            maxPasses = rewards.decodeDated("maxPasses", ::wholeNumber),
            tokenSteps = rewards.decodeDated("tokenSteps") { raw -> (raw as? JSONObject ?: error("Token steps must be an object")).decodeActivityTokenSteps() },
            periods = rewards.decodePeriods(),
        ),
        activities = array("activities").objects().map { it.decodeListedActivity() },
        logsDuration = setting(LogsDurationField, LogsDurationWhenAbsent) { it as? Boolean ?: error("$LogsDurationField must be a Boolean") },
    )
}

private fun JSONObject.decodeListedActivity(): ListedActivity {
    requireFields(required = setOf("id", "label", "icon"), optional = setOf(RemovedField, RoutineField))
    return ListedActivity(
        id = text("id"),
        label = text("label"),
        icon = decodeIcon(),
        removed = removed(),
        routine = if (has(RoutineField)) (get(RoutineField) as? JSONObject ?: error("A routine must be an object")).decodeRoutine() else null,
    )
}

private fun JSONObject.decodeRoutine(): Routine {
    requireFields(required = setOf("days"))
    return Routine(array("days").objects().map { it.decodeRoutineDay() })
}

private fun JSONObject.decodeRoutineDay(): RoutineDay {
    requireFields(required = setOf("id", "label", "exercises"), optional = setOf(RemovedField))
    return RoutineDay(id = text("id"), label = text("label"), exercises = array("exercises").objects().map { it.decodeRoutineExercise() }, removed = removed())
}

/** An exercise's weight is its start's `weightKg` with `stepKg` beside it, both numbers or both absent; one without the other, or either as null or text, is refused. */
private fun JSONObject.decodeRoutineExercise(): RoutineExercise {
    requireFields(required = setOf("id", "label", "sets", "reps", "start"), optional = setOf(RemovedField, "stepKg"))
    val reps = getJSONObject("reps").apply { requireFields(required = setOf("from", "to")) }
    val start = getJSONObject("start").apply { requireFields(required = setOf("reps"), optional = setOf("weightKg")) }
    require(start.has("weightKg") == has("stepKg")) { "An exercise has a starting weight and a step, or neither" }
    return RoutineExercise(
        id = text("id"),
        label = text("label"),
        sets = wholeNumber(get("sets")),
        reps = wholeNumber(reps.get("from"))..wholeNumber(reps.get("to")),
        weight = if (has("stepKg")) ExerciseWeight(startKg = decimalNumber(start.get("weightKg")), stepKg = decimalNumber(get("stepKg"))) else null,
        startReps = wholeNumber(start.get("reps")),
        removed = removed(),
    )
}

/** `removed` is written only while true, so a stored false is something the app never wrote. */
private fun JSONObject.removed(): Boolean = if (has(RemovedField)) (get(RemovedField) as? Boolean ?: error("removed must be a Boolean")).also { require(it) { "removed is written only while true" } } else false

/** A number as JSON holds it, whole or not, never text. */
internal fun decimalNumber(raw: Any): Double = (raw as? Number)?.toDouble() ?: error("Expected a number")

internal fun encodeActivityEntries(activityEntries: List<ActivityEntry>) = JSONArray().apply {
    activityEntries.forEach { put(it.encode()) }
}

internal fun ActivityEntry.encode(): JSONObject = JSONObject()
    .put("id", id)
    .put("activity", activityId)
    .put("name", name)
    .put("dateKey", dateKey)
    .put("timestampMillis", timestampMillis)
    .putOptional("durationMinutes", durationMinutes)
    .putOptional("notes", notes)

/**
 * The `activityEntries` list from version 30, every entry taken exactly as written: nothing is
 * trimmed, coerced, merged or left out, so a list that holds anything but entries cannot be read
 * at all. They come back newest first, as the state holds them. What an entry may say is the
 * caller's to ask.
 */
internal fun JSONObject.decodeActivityEntries(): List<ActivityEntry> =
    array("activityEntries").objects().map { it.decodeActivityEntry() }

internal fun JSONObject.decodeActivityEntry(): ActivityEntry {
    requireFields(required = setOf("id", "activity", "name", "dateKey", "timestampMillis"), optional = setOf("durationMinutes", "notes"))
    return ActivityEntry(
        id = text("id"),
        activityId = text("activity"),
        name = text("name"),
        dateKey = recordDateKey(),
        timestampMillis = recordTimestamp(),
        durationMinutes = if (has("durationMinutes")) wholeNumber(get("durationMinutes")) else null,
        notes = if (has("notes")) text("notes") else null,
    )
}

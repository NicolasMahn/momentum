package de.nicolasmahn.momentum.persistence.json

import de.nicolasmahn.momentum.domain.activity.routine.ExerciseLog
import de.nicolasmahn.momentum.domain.activity.routine.LoggedSet
import de.nicolasmahn.momentum.domain.activity.routine.WorkoutSession
import org.json.JSONArray
import org.json.JSONObject

/*
 * Sessions of an activity's routine: `{ "id": "2026-10-12-A", "activity": "gym", "day": "A",
 * "dateKey": "2026-10-12", "timestampMillis": 1760248800000, "durationMinutes": 55,
 * "exerciseLogs": [ { "exerciseId": "bench_press", "exerciseName": "Bench Press", "sets": [ {
 * "weightKg": 22.5, "reps": 10 } ] } ] }`, `activity` the ID of the activity, `day` the ID of a
 * day of its routine, the minutes only where the session has them, and a set's `weightKg` only
 * where it recorded one. Every session, exercise and set is read exactly as written.
 */

internal fun JSONObject.decodeWorkoutSessions(): List<WorkoutSession> =
    array("sessions").objects().map { it.decodeWorkoutSession() }

internal fun JSONObject.decodeWorkoutSession(): WorkoutSession {
    requireFields(required = setOf("id", "activity", "day", "dateKey", "timestampMillis", "exerciseLogs"), optional = setOf("durationMinutes"))
    return WorkoutSession(
        id = text("id"),
        activityId = text("activity"),
        day = text("day"),
        dateKey = recordDateKey(),
        timestampMillis = recordTimestamp(),
        exerciseLogs = array("exerciseLogs").objects().map { log ->
            log.requireFields(required = setOf("exerciseId", "exerciseName", "sets"))
            ExerciseLog(
                exerciseId = log.text("exerciseId"),
                exerciseName = log.text("exerciseName"),
                sets = log.array("sets").objects().map { set ->
                    set.requireFields(required = setOf("reps"), optional = setOf("weightKg"))
                    LoggedSet(weightKg = if (set.has("weightKg")) decimalNumber(set.get("weightKg")) else null, reps = decimalNumber(set.get("reps")))
                },
            )
        },
        durationMinutes = if (has("durationMinutes")) wholeNumber(get("durationMinutes")) else null,
    )
}

internal fun encodeWorkoutSessions(sessions: List<WorkoutSession>) = JSONArray().apply {
    sessions.forEach { session ->
        put(
            JSONObject()
                .put("id", session.id)
                .put("activity", session.activityId)
                .put("day", session.day)
                .put("dateKey", session.dateKey)
                .put("timestampMillis", session.timestampMillis)
                .putOptional("durationMinutes", session.durationMinutes)
                .put(
                    "exerciseLogs",
                    JSONArray().apply {
                        session.exerciseLogs.forEach { exerciseLog ->
                            put(
                                JSONObject()
                                    .put("exerciseId", exerciseLog.exerciseId)
                                    .put("exerciseName", exerciseLog.exerciseName)
                                    .put(
                                        "sets",
                                        JSONArray().apply {
                                            exerciseLog.sets.forEach { set ->
                                                put(
                                                    JSONObject()
                                                        .putOptional("weightKg", set.weightKg)
                                                        .put("reps", set.reps),
                                                )
                                            }
                                        },
                                    ),
                            )
                        }
                    },
                ),
        )
    }
}

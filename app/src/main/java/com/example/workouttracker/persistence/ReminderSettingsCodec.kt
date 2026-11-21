package com.example.workouttracker.persistence

import com.example.workouttracker.domain.reminder.MoCheckInSettings
import com.example.workouttracker.domain.reminder.ReminderSettings
import com.example.workouttracker.domain.reminder.ReminderTime
import com.example.workouttracker.domain.reminder.WorkoutReminderSettings
import com.example.workouttracker.persistence.json.parseObject
import org.json.JSONObject

internal object ReminderSettingsCodec {
    /**
     * Version 13 holds what these settings own: the wake time of a day without an alarm, the
     * fixed bedtime, null for eight hours before waking, the workout reminder's switch and time,
     * and Mo's check-in. Every view's switch is its checkpoint's `remind`, and the days the
     * workout reminder comes on are Activity's week, both with the records.
     */
    fun encode(settings: ReminderSettings): String = JSONObject()
        .put("version", 13)
        .put("fallbackAlarm", settings.fallbackAlarmTime.encode())
        .put("fixedBedtime", settings.fixedBedtime?.encode() ?: JSONObject.NULL)
        .put(
            "workout",
            JSONObject()
                .put("enabled", settings.workout.enabled)
                .put("hour", settings.workout.fixedTime.hour)
                .put("minute", settings.workout.fixedTime.minute),
        )
        .put(
            "moCheckIn",
            JSONObject()
                .put("enabled", settings.moCheckIn.enabled)
                .put("hour", settings.moCheckIn.fixedTime.hour)
                .put("minute", settings.moCheckIn.fixedTime.minute),
        )
        .toString()

    /** Exactly the fields [encode] writes, so a version 13 holding anything else is refused. */
    fun decode(raw: String): ReminderSettings? = runCatching {
        val json = parseObject(raw)
        require(json.get("version") == 13)
        json.requireKeys("version", "fallbackAlarm", "fixedBedtime", "workout", "moCheckIn")
        json.getJSONObject("moCheckIn").requireKeys("enabled", "hour", "minute")
        ReminderSettings(
            workout = json.getJSONObject("workout").decodeWorkout(),
            moCheckIn = json.decodeMoCheckIn(),
            fallbackAlarmTime = json.getJSONObject("fallbackAlarm").decodeExactTime(),
            fixedBedtime = json.decodeNullableTime("fixedBedtime"),
        )
    }.getOrNull()

    private fun ReminderTime.encode(): JSONObject = JSONObject().put("hour", hour).put("minute", minute)

    private fun JSONObject.decodeMoCheckIn(): MoCheckInSettings = getJSONObject("moCheckIn").let { moCheckIn ->
        MoCheckInSettings(enabled = moCheckIn.requiredBoolean("enabled"), fixedTime = moCheckIn.decodeTime())
    }

    private fun JSONObject.decodeWorkout(): WorkoutReminderSettings {
        require(keys().asSequence().toSet() == setOf("enabled", "hour", "minute")) { "The workout reminder holds its switch and its time" }
        return WorkoutReminderSettings(
            enabled = requiredBoolean("enabled"),
            fixedTime = ReminderTime(
                hour = requiredInt("hour"),
                minute = requiredInt("minute"),
            ),
        )
    }

    private fun JSONObject.decodeTime() = ReminderTime(
        hour = requiredInt("hour"),
        minute = requiredInt("minute"),
    )

    private fun JSONObject.decodeExactTime(): ReminderTime {
        requireKeys("hour", "minute")
        return decodeTime()
    }

    private fun JSONObject.decodeNullableTime(key: String): ReminderTime? =
        when (val rawTime = get(key)) {
            JSONObject.NULL -> null
            is JSONObject -> rawTime.decodeExactTime()
            else -> error("$key must be an object or null")
        }

    private fun JSONObject.requireKeys(vararg keys: String) {
        require(keys().asSequence().toSet() == keys.toSet()) { "Unsupported reminder fields" }
    }

    private fun JSONObject.requiredBoolean(key: String): Boolean =
        (get(key) as? Boolean) ?: error("$key must be a Boolean")

    private fun JSONObject.requiredInt(key: String): Int =
        get(key) as? Int ?: error("$key must be an integer")
}

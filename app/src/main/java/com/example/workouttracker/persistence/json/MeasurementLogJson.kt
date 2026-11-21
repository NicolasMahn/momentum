package com.example.workouttracker.persistence.json

import com.example.workouttracker.core.time.isValidDateKey
import com.example.workouttracker.domain.measurement.MeasurementLog
import org.json.JSONArray
import org.json.JSONObject

internal fun JSONObject.decodeMeasurementLogs(): List<MeasurementLog> =
    array("measurementLogs").objects().map { json ->
        json.requireFields(required = setOf("measurement", "dateKey", "timestampMillis", "value"))
        val owner = json.text("measurement").also { require(it.isNotBlank()) { "A measurement record needs an owner" } }
        val date = json.text("dateKey").also { require(isValidDateKey(it)) { "Invalid measurement date" } }
        val timestamp = json.optLongOrNull("timestampMillis")?.takeIf { it >= 0 } ?: error("Invalid timestamp")
        val value = decimalNumber(json.get("value")).also { require(it.isFinite()) { "A measurement must be finite" } }
        MeasurementLog(owner, date, timestamp, value)
    }

internal fun encodeMeasurementLogs(logs: List<MeasurementLog>) = JSONArray().apply {
    logs.forEach { log ->
        put(JSONObject().put("measurement", log.measurementId).put("dateKey", log.dateKey).put("timestampMillis", log.timestampMillis).put("value", log.value))
    }
}

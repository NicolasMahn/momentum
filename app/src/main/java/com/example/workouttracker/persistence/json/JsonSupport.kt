package com.example.workouttracker.persistence.json

import com.example.workouttracker.core.time.isValidDateKey
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

internal fun parseObject(raw: String): JSONObject = parseJson(raw) as? JSONObject ?: error("Expected an object")

internal fun parseArray(raw: String): JSONArray = parseJson(raw) as? JSONArray ?: error("Expected an array")

private fun parseJson(raw: String): Any {
    require('\u0000' !in raw) { "Invalid JSON control character" }
    val parser = JSONTokener(raw)
    val value = parser.nextValue()
    require(parser.nextClean() == '\u0000') { "Unexpected trailing content" }
    return value
}

internal fun JSONObject.optLongOrNull(name: String): Long? = when (val value = opt(name)) {
    is Int -> value.toLong()
    is Long -> value
    else -> null
}

internal fun JSONArray.textAt(index: Int): String = get(index) as? String ?: error("Expected text")

internal fun JSONObject.putOptional(name: String, value: Any?): JSONObject = apply {
    if (value != null) put(name, value)
}

/*
 * Strict reading, for a stored shape the app wrote itself: a configuration or a setting. Anything
 * it would not write back unchanged is refused, so a newer field never slips through as silence.
 */

internal fun JSONObject.requireFields(required: Set<String>, optional: Set<String> = emptySet()) {
    val keys = keys().asSequence().toSet()
    require(keys.containsAll(required) && (required + optional).containsAll(keys)) { "Unsupported fields: $keys" }
}

internal fun JSONObject.text(name: String): String = get(name) as? String ?: error("$name must be text")

internal fun JSONObject.array(name: String): JSONArray = get(name) as? JSONArray ?: error("$name must be an array")

internal fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { get(it) as? JSONObject ?: error("Expected an object") }

/** One edit a page made and has not saved yet, as every kind of draft writes it: named by its [kind], with what it changes put beside. */
internal fun edit(kind: String): JSONObject = JSONObject().put("edit", kind)

internal fun JSONObject.recordDateKey(): String = text("dateKey").also {
    require(isValidDateKey(it)) { "Invalid record date" }
}

internal fun JSONObject.recordTimestamp(): Long =
    optLongOrNull("timestampMillis")?.takeIf { it >= 0 } ?: error("Invalid record timestamp")

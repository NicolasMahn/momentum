package com.example.workouttracker.persistence

import org.json.JSONArray
import org.json.JSONObject

/** JSON as maps and lists, so two payloads compare by content and not by the order of their keys. */
internal fun plainJson(value: Any?): Any? = when (value) {
    is JSONObject -> value.keys().asSequence().associateWith { plainJson(value.get(it)) }
    is JSONArray -> (0 until value.length()).map { plainJson(value.get(it)) }
    else -> value
}

package com.example.workouttracker.persistence.json

import com.example.workouttracker.domain.activity.route.Route
import com.example.workouttracker.domain.activity.route.RoutePoint
import com.example.workouttracker.domain.activity.route.RouteRecording
import org.json.JSONArray
import org.json.JSONObject

/*
 * A route as it is stored: `{"stretches": ["…", "…"]}`, each stretch one text in the polyline
 * format maps use, extended by a third number per point. A point is its latitude and longitude in
 * hundred-thousandths of a degree and its seconds since the start, each written as the difference
 * from the point before it. An hour's run is a few kilobytes, small enough to stay inside its
 * entry, so rows, backups and sync carry it without a store of its own.
 */

internal fun Route.encode(): JSONObject =
    JSONObject().put(StretchesField, JSONArray().apply { stretches.forEach { put(encodeStretch(it)) } })

/** A stored route, refused unless it is exactly what [encode] writes: stretches of points in time order. */
internal fun JSONObject.decodeRoute(): Route {
    requireFields(required = setOf(StretchesField))
    val stretches = array(StretchesField).let { array -> (0 until array.length()).map { array.get(it) as? String ?: error("A stretch is text") } }
    require(stretches.isNotEmpty()) { "A stored route has a stretch" }
    return Route(stretches.map(::decodeStretch))
}

private fun encodeStretch(points: List<RoutePoint>): String = buildString {
    var previous = longArrayOf(0, 0, 0)
    points.forEach { point ->
        val current = longArrayOf(RoutePoint.fixUnits(point.latitude), RoutePoint.fixUnits(point.longitude), point.secondsIn)
        current.indices.forEach { appendNumber(current[it] - previous[it]) }
        previous = current
    }
}

private fun decodeStretch(text: String): List<RoutePoint> {
    require(text.isNotEmpty()) { "A stretch holds a point" }
    val numbers = mutableListOf<Long>()
    var index = 0
    while (index < text.length) {
        var shift = 0
        var value = 0L
        while (true) {
            require(index < text.length && shift < 64) { "A stretch ends inside a number" }
            val chunk = text[index++].code - CharOffset
            require(chunk in 0..63) { "A stretch holds a character outside its alphabet" }
            value = value or ((chunk and 0x1f).toLong() shl shift)
            shift += 5
            if (chunk < 0x20) break
        }
        numbers += if (value and 1L != 0L) (value shr 1).inv() else value shr 1
    }
    require(numbers.size % 3 == 0) { "A point has three numbers" }
    var latitude = 0L
    var longitude = 0L
    var seconds = 0L
    val points = numbers.chunked(3).map { (dLatitude, dLongitude, dSeconds) ->
        latitude += dLatitude
        longitude += dLongitude
        seconds += dSeconds
        RoutePoint.of(RoutePoint.fromFixUnits(latitude), RoutePoint.fromFixUnits(longitude), seconds)
    }
    // A stretch has one spelling, the one encodeStretch writes; any other is damage, read or not.
    require(encodeStretch(points) == text) { "A stretch is not spelled as it is written" }
    return points
}

/** One signed number in the polyline alphabet: zig-zag, then five bits per character, low bits first. */
private fun StringBuilder.appendNumber(number: Long) {
    var value = if (number < 0) (number shl 1).inv() else number shl 1
    while (value >= 0x20) {
        append(((0x20 or (value and 0x1f).toInt()) + CharOffset).toChar())
        value = value shr 5
    }
    append((value.toInt() + CharOffset).toChar())
}

private const val CharOffset = 63
private const val StretchesField = "stretches"

/*
 * The recording under way, as the recorder keeps it in a file of its own until it is saved or
 * discarded: what it is of, when it started, the route so far, and whether it was paused. It is
 * no record yet, so it is not in the rows or in a backup.
 */

internal fun RouteRecording.encodeJournal(): String = JSONObject()
    .put("activity", activityId)
    .put("startedAtMillis", startedAtMillis)
    .put("paused", paused)
    .put("stretchEnded", stretchEnded)
    .putOptional(RouteField, route.takeUnless { it.isEmpty }?.encode())
    .toString()

internal fun decodeRecordingJournal(raw: String): RouteRecording = parseObject(raw).run {
    requireFields(required = setOf("activity", "startedAtMillis", "paused", "stretchEnded"), optional = setOf(RouteField))
    RouteRecording(
        activityId = text("activity"),
        startedAtMillis = (get("startedAtMillis") as? Number)?.toLong() ?: error("startedAtMillis must be a number"),
        route = if (has(RouteField)) getJSONObject(RouteField).decodeRoute() else Route.Empty,
        paused = get("paused") as? Boolean ?: error("paused must be a Boolean"),
        stretchEnded = get("stretchEnded") as? Boolean ?: error("stretchEnded must be a Boolean"),
    )
}

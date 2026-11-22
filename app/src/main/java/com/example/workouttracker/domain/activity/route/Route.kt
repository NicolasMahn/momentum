package com.example.workouttracker.domain.activity.route

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * One position on a route, [secondsIn] seconds after its recording started. Coordinates are kept
 * to five decimals, about a metre, which is all a phone's GPS knows and what storage keeps: a
 * route measures the same live as it does once saved.
 */
data class RoutePoint private constructor(val latitude: Double, val longitude: Double, val secondsIn: Long) {
    companion object {
        fun of(latitude: Double, longitude: Double, secondsIn: Long): RoutePoint {
            require(latitude in -90.0..90.0 && longitude in -180.0..180.0) { "A position lies on the globe." }
            require(secondsIn >= 0) { "A point is at or after the start." }
            return RoutePoint(roundToFix(latitude), roundToFix(longitude), secondsIn)
        }

        /** A coordinate in the units storage keeps, hundred-thousandths of a degree. */
        fun fixUnits(degrees: Double): Long = (degrees * FixUnitsPerDegree).roundToLong()

        fun fromFixUnits(units: Long): Double = units / FixUnitsPerDegree

        private fun roundToFix(degrees: Double): Double = fromFixUnits(fixUnits(degrees))

        private const val FixUnitsPerDegree = 1e5
    }
}

/**
 * Where a person went during one recording: its stretches, each the points between a start or a
 * resume and the next pause, in time order. Nothing is measured across a pause, so standing at a
 * crossing with the recording paused adds neither distance nor time.
 *
 * The distance and the moving time are derived here and nowhere else, so a route reads the same
 * on every screen, and a better rule applies to every route alike.
 */
data class Route(val stretches: List<List<RoutePoint>>) {
    init {
        require(stretches.none { it.isEmpty() }) { "A stretch holds at least one point." }
        val times = stretches.flatten().map { it.secondsIn }
        require(times.zipWithNext().all { (earlier, later) -> earlier <= later }) { "A route's points are in time order." }
    }

    val points: List<RoutePoint> get() = stretches.flatten()

    val isEmpty: Boolean get() = stretches.isEmpty()

    /**
     * The metres of the moving steps: between consecutive points of a stretch, covered at least at
     * [MovingMetersPerSecond]. A slower step is GPS wandering around someone standing still.
     */
    val distanceMeters: Double get() = steps().filter { it.isMoving }.sumOf { it.meters }

    /**
     * The seconds of the same moving steps. A wait at a red light, or the phone left lying on a
     * bench, is not moving time, whether or not the recording was paused.
     */
    val movingSeconds: Long get() = steps().filter { it.isMoving }.sumOf { it.seconds }

    /**
     * The moving time at [secondsIn], for a recording still running: [movingSeconds], plus the time
     * since the last point while the last step was a moving one and that point is recent. GPS fixes
     * come every second or so, and a point is only kept once the person moved on from the last, so
     * without this the clock would stand still between points.
     */
    fun movingSecondsAt(secondsIn: Long, recording: Boolean): Long {
        val last = stretches.lastOrNull()?.lastOrNull() ?: return 0
        val sinceLast = (secondsIn - last.secondsIn).coerceAtLeast(0)
        val stillMoving = recording && sinceLast <= StillMovingSeconds && steps(stretches.last()).lastOrNull()?.isMoving == true
        return movingSeconds + if (stillMoving) sinceLast else 0
    }

    /** This route with [point] added to its last stretch, or to a new one after a pause ([newStretch]). */
    fun adding(point: RoutePoint, newStretch: Boolean): Route = Route(
        if (newStretch || stretches.isEmpty()) stretches + listOf(listOf(point)) else stretches.dropLast(1) + listOf(stretches.last() + point),
    )

    private fun steps(): List<Step> = stretches.flatMap(::steps)

    private fun steps(stretch: List<RoutePoint>): List<Step> = stretch.zipWithNext { from, to ->
        Step(meters = metersBetween(from, to), seconds = to.secondsIn - from.secondsIn)
    }

    /**
     * A step moved when it was covered at least at [MovingMetersPerSecond], within [LongestGapSeconds].
     * A longer gap had no fix at all: a tunnel is passed sooner, so it is a stop indoors, whatever
     * the distance across it says.
     */
    private data class Step(val meters: Double, val seconds: Long) {
        val isMoving: Boolean get() = seconds in 1..LongestGapSeconds && meters / seconds >= MovingMetersPerSecond
    }

    companion object {
        val Empty = Route(emptyList())

        /** Slower than a slow walk is standing about with the phone, not moving. */
        const val MovingMetersPerSecond = 0.5

        const val LongestGapSeconds = 120L

        /** How long after the last point a running clock still counts the person as moving. */
        const val StillMovingSeconds = 10L
    }
}

/** Metres between two points along the globe's surface (haversine), close enough for anything on foot or by bike. */
fun metersBetween(from: RoutePoint, to: RoutePoint): Double {
    val lat1 = Math.toRadians(from.latitude)
    val lat2 = Math.toRadians(to.latitude)
    val dLat = lat2 - lat1
    val dLon = Math.toRadians(to.longitude - from.longitude)
    val h = sin(dLat / 2).let { it * it } + cos(lat1) * cos(lat2) * sin(dLon / 2).let { it * it }
    return 2 * EarthRadiusMeters * asin(sqrt(h.coerceIn(0.0, 1.0)))
}

private const val EarthRadiusMeters = 6_371_008.8

/**
 * Seconds a kilometre took at the route's moving pace, or null before it went anywhere: what a
 * runner reads. A cyclist reads [kilometersPerHour] instead; both come from the same two numbers.
 */
fun Route.secondsPerKilometer(movingSeconds: Long = this.movingSeconds): Long? =
    distanceMeters.takeIf { it >= MeasurableMeters && movingSeconds > 0 }?.let { (movingSeconds * 1000 / it).roundToLong() }

fun Route.kilometersPerHour(movingSeconds: Long = this.movingSeconds): Double? =
    distanceMeters.takeIf { it >= MeasurableMeters && movingSeconds > 0 }?.let { it / movingSeconds * 3.6 }

/** Below this, a pace says more about GPS noise than about the person. */
private const val MeasurableMeters = 50.0

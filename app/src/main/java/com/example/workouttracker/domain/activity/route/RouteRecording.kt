package com.example.workouttracker.domain.activity.route

/** One fix as the phone reports it: where, how sure (metres), and when. */
data class LocationFix(val latitude: Double, val longitude: Double, val accuracyMeters: Float, val timeMillis: Long)

/**
 * A recording under way, of the activity [activityId], started at [startedAtMillis]: the route so
 * far, and whether it is paused. It is plain data so the recorder can keep it in a file and pick
 * it up again after the system stopped the app.
 */
data class RouteRecording(
    val activityId: String,
    val startedAtMillis: Long,
    val route: Route = Route.Empty,
    val paused: Boolean = false,
    /** Whether the next kept point begins a new stretch: true after a resume, so no step spans the pause. */
    val stretchEnded: Boolean = false,
    /** Fixes refused in a row as too far from the last point; enough of them say the last point was the wrong one. */
    val jumpsInARow: Int = 0,
) {
    fun secondsIn(timeMillis: Long): Long = ((timeMillis - startedAtMillis) / 1000).coerceAtLeast(0)

    /**
     * This recording with [fix] kept where it says something: not while paused, not when the phone
     * is unsure by more than [MaxAccuracyMeters], not when reaching it would have taken more than
     * [MaxMetersPerSecond], which no one runs or cycles, and not when it lies within [MinStepMeters]
     * of the last point less than [StillPointSeconds] after it. GPS wanders by a few metres while a
     * person stands still; those wanders are dropped, but a point is still kept every few seconds
     * of standing, so a wait shows in the route as the slow steps it is and is not mistaken for the
     * moving step after it.
     *
     * A jump is usually one wrong fix, and is dropped. But when [JumpsToReanchor] fixes in a row
     * are too far, the last point was the wrong one, such as a stale first fix from where the
     * phone was last: the route goes on from here in a new stretch, so nothing is measured across
     * the gap, and a lone point the jump left behind is dropped.
     */
    fun taking(fix: LocationFix): RouteRecording {
        if (paused || fix.accuracyMeters > MaxAccuracyMeters || fix.timeMillis < startedAtMillis) return this
        val point = RoutePoint.of(fix.latitude, fix.longitude, secondsIn(fix.timeMillis))
        val last = route.stretches.lastOrNull()?.lastOrNull()
        if (last != null) {
            if (point.secondsIn < last.secondsIn) return this
            val meters = metersBetween(last, point)
            val seconds = point.secondsIn - last.secondsIn
            if (meters < MinStepMeters && seconds < StillPointSeconds) return this
            if (meters > MaxMetersPerSecond * seconds.coerceAtLeast(1)) {
                if (jumpsInARow + 1 < JumpsToReanchor) return copy(jumpsInARow = jumpsInARow + 1)
                val kept = Route(route.stretches.filterNot { it.size == 1 && it.single() == last })
                return copy(route = kept.adding(point, newStretch = true), stretchEnded = false, jumpsInARow = 0)
            }
        }
        return copy(route = route.adding(point, newStretch = stretchEnded), stretchEnded = false, jumpsInARow = 0)
    }

    fun pausing(): RouteRecording = copy(paused = true, stretchEnded = true)

    fun resuming(): RouteRecording = copy(paused = false)

    companion object {
        const val MaxAccuracyMeters = 25f
        const val MinStepMeters = 5.0
        const val StillPointSeconds = 10L

        /** About 108 km/h: faster than anyone on foot or by bike, slower than most GPS jumps. */
        const val MaxMetersPerSecond = 30.0

        const val JumpsToReanchor = 3
    }
}

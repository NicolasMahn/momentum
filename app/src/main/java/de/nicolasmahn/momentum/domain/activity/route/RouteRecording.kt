package de.nicolasmahn.momentum.domain.activity.route

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
    /**
     * The last fix taken in, kept or not, and not itself a jump. A jump is judged against it, where
     * and when it was: after a long stop the last kept point is old, and a wrong fix would look
     * reachable from it. It only steers the judging, so the recorder's file leaves it out.
     */
    val lastSeen: RoutePoint? = null,
    /** The seconds of the last fix taken in at all, refused ones included: a spike of two fixes is judged a second apart, not two. */
    val lastFixSecondsIn: Long? = null,
) {
    fun secondsIn(timeMillis: Long): Long = ((timeMillis - startedAtMillis) / 1000).coerceAtLeast(0)

    /**
     * This recording with [fix] kept where it says something: not while paused, not when the phone
     * is unsure by more than [MaxAccuracyMeters], not before the last point, not when reaching it
     * would have taken more than [MaxMetersPerSecond], which no one runs or cycles, and not when
     * it lies closer to the last point than the phone is sure of, or than [MinStepMeters]. GPS
     * wanders within its accuracy while a person stands still, and those wanders are dropped.
     * When the person moves on, the first fix outside that circle is kept, and the step to it is
     * a slow one: the wait is not mistaken for moving. The first fix after a start or a resume is
     * kept however near: it begins a stretch, and no step leads to it.
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
        // Time runs forward across stretches too, whatever the clock did meanwhile.
        if (last != null && point.secondsIn < last.secondsIn) return this
        if (last != null && !stretchEnded) {
            val previous = lastSeen ?: last
            val seconds = (point.secondsIn - maxOf(previous.secondsIn, lastFixSecondsIn ?: previous.secondsIn)).coerceAtLeast(1)
            if (metersBetween(previous, point) > MaxMetersPerSecond * seconds) {
                if (jumpsInARow + 1 < JumpsToReanchor) return copy(jumpsInARow = jumpsInARow + 1, lastFixSecondsIn = point.secondsIn)
                val kept = Route(route.stretches.filterNot { it.size == 1 && it.single() == last })
                return copy(route = kept.adding(point, newStretch = true), stretchEnded = false, jumpsInARow = 0, lastSeen = point, lastFixSecondsIn = point.secondsIn)
            }
            // Inside the circle: it agrees with the last point, so jumps before it were stray ones.
            if (metersBetween(last, point) < maxOf(MinStepMeters, fix.accuracyMeters.toDouble())) {
                return copy(jumpsInARow = 0, lastSeen = point, lastFixSecondsIn = point.secondsIn)
            }
            // A step within one second measures no time, so its metres would count for nothing: the
            // next point, a second on, measures them instead.
            if (point.secondsIn == last.secondsIn) return copy(jumpsInARow = 0, lastSeen = point, lastFixSecondsIn = point.secondsIn)
        }
        return copy(route = route.adding(point, newStretch = stretchEnded), stretchEnded = false, jumpsInARow = 0, lastSeen = point, lastFixSecondsIn = point.secondsIn)
    }

    fun pausing(): RouteRecording = copy(paused = true, stretchEnded = true)

    fun resuming(): RouteRecording = copy(paused = false)

    companion object {
        const val MaxAccuracyMeters = 25f
        const val MinStepMeters = 5.0

        /** About 108 km/h: faster than anyone on foot or by bike, slower than most GPS jumps. */
        const val MaxMetersPerSecond = 30.0

        const val JumpsToReanchor = 3
    }
}

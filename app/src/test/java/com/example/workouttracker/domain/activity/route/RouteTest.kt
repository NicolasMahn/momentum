package com.example.workouttracker.domain.activity.route

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteTest {
    private val start = 1_700_000_000_000L

    /** A fix [meters] north of the start line, [seconds] in, as sure as a phone outdoors is. */
    private fun north(meters: Double, seconds: Long, accuracy: Float = 5f) =
        LocationFix(Origin.latitude + meters / MetersPerDegreeLatitude, Origin.longitude, accuracy, start + seconds * 1000)

    private fun recorded(vararg fixes: LocationFix): RouteRecording =
        fixes.fold(RouteRecording("act_run", start)) { recording, fix -> recording.taking(fix) }

    @Test
    fun aRunMeasuresItsDistanceAndTheTimeItMoved() {
        // One kilometre north in 300 seconds: a 5:00 pace, 12 km/h.
        val route = recorded(*(0..100).map { north(it * 10.0, it * 3L) }.toTypedArray()).route
        assertEquals(1000.0, route.distanceMeters, 2.0)
        assertEquals(300L, route.movingSeconds)
        assertEquals(300L, route.secondsPerKilometer())
        assertEquals(12.0, requireNotNull(route.kilometersPerHour()), 0.05)
    }

    @Test
    fun standingStillAddsNeitherDistanceNorMovingTime() {
        // At 500 m the runner waits two minutes at a crossing while GPS wanders by a few metres.
        val wander = (1..40).map { north(500.0 + (it % 3), 150L + it * 3) }
        val route = recorded(north(0.0, 0), north(250.0, 75), north(500.0, 150), *wander.toTypedArray(), north(750.0, 345), north(1000.0, 420)).route
        assertEquals(1000.0, route.distanceMeters, 2.0)
        // Four moving quarters of 75 s; the wait is neither moving time nor folded into the step after it.
        assertEquals(300L, route.movingSeconds)
        assertEquals(300L, route.secondsPerKilometer())

        // A phone left on a bench drifts slowly: kept points, but neither distance nor moving time.
        val drifting = recorded(north(0.0, 0), north(100.0, 30), north(106.0, 60), north(112.0, 90)).route
        assertEquals(30L, drifting.movingSeconds)
        assertEquals(100.0, drifting.distanceMeters, 1.0)
    }

    @Test
    fun nothingIsMeasuredAcrossAPause() {
        val beforePause = recorded(north(0.0, 0), north(300.0, 90))
        val paused = beforePause.pausing()
        // Fixes while paused are not kept; after the resume the first point starts a new stretch.
        val resumed = paused.taking(north(400.0, 120)).resuming().taking(north(2000.0, 600)).taking(north(2300.0, 690))
        assertEquals(2, resumed.route.stretches.size)
        assertEquals(600.0, resumed.route.distanceMeters, 2.0)
        assertEquals(180L, resumed.route.movingSeconds)
    }

    @Test
    fun unsureAndImpossibleFixesAreNotKept() {
        val recording = recorded(
            north(0.0, 0),
            north(50.0, 10, accuracy = 40f),
            // 2 km in 10 s is a GPS jump, not a sprint.
            north(2000.0, 20),
            north(30.0, 30),
        )
        assertEquals(listOf(0L, 30L), recording.route.points.map { it.secondsIn })
    }

    @Test
    fun aWrongFirstFixDoesNotHoldTheRecordingHostage() {
        // The first fix is where the phone last was, 9 km away; every true fix after it looks like a jump.
        val recording = recorded(north(9000.0, 0), north(0.0, 2), north(10.0, 4), north(20.0, 6), north(30.0, 8), north(40.0, 10))
        // After three jumps in a row the route starts again from the true fixes, without the stale one.
        assertEquals(listOf(6L, 8L, 10L), recording.route.points.map { it.secondsIn })
        assertEquals(20.0, recording.route.distanceMeters, 1.0)

        // One wrong fix in the middle of a run is just dropped.
        val run = recorded(north(0.0, 0), north(10.0, 3), north(5000.0, 6), north(20.0, 9), north(30.0, 12))
        assertEquals(listOf(0L, 3L, 9L, 12L), run.route.points.map { it.secondsIn })
        assertEquals(1, run.route.stretches.size)
    }

    @Test
    fun theLiveClockRunsBetweenPointsOnlyWhileTheRunnerMoves() {
        val recording = recorded(north(0.0, 0), north(30.0, 10))
        assertEquals(10L, recording.route.movingSeconds)
        assertEquals(15L, recording.route.movingSecondsAt(15, recording = true))
        // Long without a point, or paused, the clock stands at what the points measured.
        assertEquals(10L, recording.route.movingSecondsAt(40, recording = true))
        assertEquals(10L, recording.route.movingSecondsAt(15, recording = false))
    }

    @Test
    fun aRouteTooShortToMeasureHasNoPace() {
        val route = recorded(north(0.0, 0), north(20.0, 8)).route
        assertNull(route.secondsPerKilometer())
        assertNull(route.kilometersPerHour())
        assertTrue(Route.Empty.isEmpty)
        assertEquals(0L, Route.Empty.movingSecondsAt(100, recording = true))
    }

    private companion object {
        val Origin = RoutePoint.of(48.7758, 9.1829, 0)
        const val MetersPerDegreeLatitude = 111_195.0
    }
}

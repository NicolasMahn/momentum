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
        // 500 m at 10 m every 3 s, two minutes at a crossing while GPS wanders by a few metres, 500 m more.
        val out = (0..50).map { north(it * 10.0, it * 3L) }
        val wander = (1..40).map { north(500.0 + (it % 3), 150L + it * 3) }
        val back = (1..50).map { north(500.0 + it * 10.0, 270L + it * 3) }
        val route = recorded(*(out + wander + back).toTypedArray()).route
        // The first step out of the wait is slow, so the wait is not moving, and those 10 m and 3 s go with it.
        assertEquals(990.0, route.distanceMeters, 2.0)
        assertEquals(297L, route.movingSeconds)

        // A phone left on a bench drifts slowly: kept points, but neither distance nor moving time.
        val drifting = recorded(north(0.0, 0), north(100.0, 30), north(106.0, 60), north(112.0, 90)).route
        assertEquals(30L, drifting.movingSeconds)
        assertEquals(100.0, drifting.distanceMeters, 1.0)
    }

    @Test
    fun aSteadyJogUnderPoorGpsKeepsItsTime() {
        // Two minutes at 2 m/s, a fix a second, the phone sure only to 23 m.
        val jog = recorded(*(0..120).map { north(it * 2.0, it.toLong(), accuracy = 23f) }.toTypedArray()).route
        assertEquals(240.0, jog.distanceMeters, 25.0)
        assertTrue(jog.movingSeconds >= 108)
    }

    @Test
    fun aWalkUnderPoorGpsKeepsItsTime() {
        // Ten minutes at 1.4 m/s, a fix a second, the phone sure only to 15 m.
        val walk = recorded(*(0..600).map { north(it * 1.4, it.toLong(), accuracy = 15f) }.toTypedArray()).route
        assertEquals(840.0, walk.distanceMeters, 16.0)
        assertTrue(walk.movingSeconds >= 585)
    }

    @Test
    fun aLongGapWithoutAnyFixIsNotMoving() {
        // Four minutes without a fix, 150 m on: a stop indoors, not a run through a tunnel.
        val gap = recorded(north(0.0, 0), north(30.0, 10), north(180.0, 250), north(210.0, 260)).route
        assertEquals(60.0, gap.distanceMeters, 1.0)
        assertEquals(20L, gap.movingSeconds)
        // A minute through an underpass still counts.
        val underpass = recorded(north(0.0, 0), north(30.0, 10), north(180.0, 70)).route
        assertEquals(70L, underpass.movingSeconds)
    }

    @Test
    fun aFastRideUnderPoorGpsIsNotMistakenForJumps() {
        // A minute at 12 m/s, a fix a second, the phone sure only to 25 m.
        val ride = recorded(*(0..60).map { north(it * 12.0, it.toLong(), accuracy = 25f) }.toTypedArray()).route
        assertEquals(1, ride.stretches.size)
        assertEquals(720.0, ride.distanceMeters, 30.0)
        assertTrue(ride.movingSeconds >= 57)
    }

    @Test
    fun aLongRoadRideUnderPoorGpsKeepsItsDistance() {
        // Ten minutes at 8 m/s, a fix a second, the phone sure only to 25 m.
        val ride = recorded(*(0..600).map { north(it * 8.0, it.toLong(), accuracy = 25f) }.toTypedArray()).route
        assertEquals(1, ride.stretches.size)
        assertEquals(4800.0, ride.distanceMeters, 96.0)
    }

    @Test
    fun aSlowClimbKeepsItsTime() {
        val climb = recorded(*(0..100).map { north(it * 0.6, it.toLong()) }.toTypedArray()).route
        // Every step of it is moving, from the start to the last point kept.
        assertEquals(climb.points.last().secondsIn, climb.movingSeconds)
        assertTrue(climb.movingSeconds >= 85)
        assertEquals(climb.movingSeconds * 0.6, climb.distanceMeters, 3.0)
    }

    @Test
    fun aWrongFixAfterAStopIsStillAJump() {
        // 85 s standing, then one fix 1 km off, then the true fixes walking on.
        val stand = (1..85).map { north(30.0 + (it % 2), 10L + it) }
        val route = recorded(north(0.0, 0), north(30.0, 10), *stand.toTypedArray(), north(1030.0, 96), north(40.0, 97), north(50.0, 98)).route
        // The outlier is refused; the slow step out of the stop adds nothing, the 10 m after it do.
        assertEquals(40.0, route.distanceMeters, 2.0)
        assertTrue(route.points.none { it.latitude > 48.78 })
    }

    @Test
    fun aMultipathFixAtACrossingIsDropped() {
        // Walk 100 m, wait 30 s, one fix 150 m off, walk on 100 m.
        val walk = (0..20).map { north(it * 5.0, it * 4L) }
        val wait = (1..30).map { north(100.0, 80L + it) }
        val on = (1..20).map { north(100.0 + it * 5.0, 111L + it * 4) }
        val route = recorded(*(walk + wait).toTypedArray(), north(250.0, 111, accuracy = 15f), *on.toTypedArray()).route
        assertEquals(1, route.stretches.size)
        assertEquals(195.0, route.distanceMeters, 6.0)
    }

    @Test
    fun aResumedFixBeforeTheLastPointIsNotKept() {
        val resumed = recorded(north(0.0, 0), north(30.0, 10)).pausing().resuming().taking(north(40.0, 5))
        assertEquals(listOf(0L, 10L), resumed.route.points.map { it.secondsIn })
    }

    @Test
    fun wanderingWithinTheFixesAccuracyIsNotMovement() {
        // Standing still with a phone sure only to 12 m: fixes jump 6 to 10 m each second.
        val wander = (1..30).map { north(if (it % 2 == 0) 0.0 else 6.0 + it % 5, it.toLong(), accuracy = 12f) }
        val standing = recorded(north(0.0, 0, accuracy = 12f), *wander.toTypedArray()).route
        assertEquals(0.0, standing.distanceMeters, 0.0)
        assertEquals(0L, standing.movingSeconds)
    }

    @Test
    fun theFirstFixAfterAResumeBeginsTheNewStretchWhereverItIs() {
        val resumed = recorded(north(0.0, 0), north(100.0, 30)).pausing().resuming().taking(north(101.0, 33))
        assertEquals(listOf(listOf(0L, 30L), listOf(33L)), resumed.route.stretches.map { stretch -> stretch.map { it.secondsIn } })
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

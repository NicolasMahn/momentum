package de.nicolasmahn.momentum.persistence.json

import de.nicolasmahn.momentum.domain.activity.ActivityEntry
import de.nicolasmahn.momentum.domain.activity.route.Route
import de.nicolasmahn.momentum.domain.activity.route.RoutePoint
import de.nicolasmahn.momentum.domain.activity.route.RouteRecording
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteJsonTest {
    private val route = Route(listOf(
        listOf(RoutePoint.of(48.77586, 9.18293, 0), RoutePoint.of(48.77612, 9.18355, 4), RoutePoint.of(48.77601, 9.18401, 9)),
        listOf(RoutePoint.of(-33.86882, 151.20929, 3_600), RoutePoint.of(-33.86900, 151.20950, 3_603)),
    ))

    @Test
    fun aRouteReadsBackAsItWasWrittenAndStaysSmall() {
        val stored = route.encode()
        assertEquals(route, stored.decodeRoute())
        assertEquals(route.distanceMeters, stored.decodeRoute().distanceMeters, 0.0)

        // An hour at a point every three seconds stays within a dozen bytes a point; docs/data-durability.md says what that means for a backup's cap.
        val hour = Route(listOf((0 until 1200).map { RoutePoint.of(48.7 + it * 0.00003, 9.1 + it * 0.00002, it * 3L) }))
        assertEquals(hour, hour.encode().decodeRoute())
        assertTrue(hour.encode().toString().length < 1200 * 12)
    }

    @Test
    fun aDamagedRouteIsRefused() {
        val damaged = listOf(
            """{"stretches":[]}""",
            """{"stretches":[""]}""",
            """{"stretches":[7]}""",
            """{"stretches":["_p~iF~ps|U"]}""", // two numbers: a point has three
            """{"stretches":["_p~iF~ps|U_"]}""", // ends inside a number
            """{"stretches":["???"],"extra":1}""",
            """{}""",
        )
        damaged.forEach { text -> assertTrue(text, runCatching { JSONObject(text).decodeRoute() }.isFailure) }
        // Time runs backwards from the second point on.
        val backwards = Route(listOf(listOf(RoutePoint.of(1.0, 1.0, 10)))).encode().toString().replace("\"]", "??@\"]")
        assertTrue(runCatching { JSONObject(backwards).decodeRoute() }.isFailure)
    }

    @Test
    fun anEntryKeepsItsRouteAndOneWithoutStaysWithout() {
        val run = ActivityEntry("run", "act_run", "Running", "2026-11-22", 1, durationMinutes = 31, route = route)
        assertEquals(run, run.encode().decodeActivityEntry())
        val plain = run.copy(route = null)
        assertFalse(plain.encode().has(RouteField))
        assertEquals(plain, plain.encode().decodeActivityEntry())
    }

    @Test
    fun aRecordingUnderWayReadsBackFromItsFile() {
        val recording = RouteRecording("act_run", 1_700_000_000_000, route, paused = true, stretchEnded = true)
        assertEquals(recording, decodeRecordingJournal(recording.encodeJournal()))
        val justStarted = RouteRecording("act_run", 1_700_000_000_000)
        assertEquals(justStarted, decodeRecordingJournal(justStarted.encodeJournal()))
    }
}

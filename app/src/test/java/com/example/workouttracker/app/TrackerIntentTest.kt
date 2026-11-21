package com.example.workouttracker.app

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.example.workouttracker.domain.TrackerRef
import com.example.workouttracker.domain.TrackerTypeId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class TrackerIntentTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val counter = TrackerRef(TrackerTypeId("counter"), "水:daily/🫗")

    @Test
    fun anArbitraryKindAndUnicodeViewIdRoundTripWithTheirDate() {
        val intent = trackerDestinationIntent(context, counter, "2026-10-17")

        assertEquals(
            AppNavigationRequest(AppDestination.Tracker, tracker = counter, loggedDateKey = "2026-10-17"),
            intent.requestedAppNavigation(),
        )
        assertEquals(listOf("counter", counter.id, "2026-10-17"), intent.data!!.pathSegments)
    }

    @Test
    fun aGenericTrackerActionCanStartItsTrackerWithoutARequestedDay() {
        val timer = TrackerRef(TrackerTypeId("time"), "tim_k2p9")
        val intent = trackerDestinationIntent(context, timer, dateKey = null, startSession = true)

        assertEquals(
            AppNavigationRequest(AppDestination.Tracker, tracker = timer, startSession = true),
            intent.requestedAppNavigation(),
        )
        assertEquals("momentum://tracker/time/tim_k2p9?start=true", intent.dataString)
    }

    @Test
    fun pendingIntentIdentityIncludesKindInstanceAndDate() {
        val original = trackerDestinationPendingIntent(context, counter, "2026-10-17")
        val others = listOf(
            trackerDestinationPendingIntent(context, counter.copy(type = TrackerTypeId("events")), "2026-10-17"),
            trackerDestinationPendingIntent(context, counter.copy(id = "other"), "2026-10-17"),
            trackerDestinationPendingIntent(context, counter, "2026-10-18"),
        )

        others.forEach { other ->
            assertNotEquals(original, other)
            assertFalse(shadowOf(original).savedIntent.filterEquals(shadowOf(other).savedIntent))
        }
        assertEquals(original, trackerDestinationPendingIntent(context, counter, "2026-10-17"))
        assertEquals(
            AppNavigationRequest(AppDestination.Tracker, tracker = counter, loggedDateKey = "2026-10-17"),
            shadowOf(original).savedIntent.requestedAppNavigation(),
        )
    }

    @Test
    fun missingOrInvalidTrackerReferencesAreRejectedAndInvalidDatesAreNotForwarded() {
        val valid = trackerDestinationIntent(context, counter, "2026-10-17")
        val malformed = listOf(
            Intent(valid).apply { removeExtra(ExtraTrackerType) },
            Intent(valid).apply { removeExtra(ExtraTrackerId) },
            Intent(valid).putExtra(ExtraTrackerType, "Counter"),
            Intent(valid).putExtra(ExtraTrackerType, ""),
            Intent(valid).putExtra(ExtraTrackerId, "   "),
        )
        malformed.forEach { assertNull(it.requestedAppNavigation()) }
        assertEquals(
            AppNavigationRequest(AppDestination.Tracker, tracker = counter),
            Intent(valid).putExtra(ExtraTrackerDateKey, "2026-02-30").requestedAppNavigation(),
        )
        assertThrows(IllegalArgumentException::class.java) {
            trackerDestinationIntent(context, counter, "2026-02-30")
        }
    }
}

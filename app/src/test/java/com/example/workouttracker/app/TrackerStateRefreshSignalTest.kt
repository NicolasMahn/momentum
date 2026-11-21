package com.example.workouttracker.app

import org.junit.Assert.assertEquals
import org.junit.Test

class TrackerStateRefreshSignalTest {
    @Test
    fun notifiesRegisteredListenersAndStopsAfterRemoval() {
        var calls = 0
        val listener = { calls += 1 }

        TrackerStateRefreshSignal.addListener(listener)
        try {
            TrackerStateRefreshSignal.notifyChanged()
            assertEquals(1, calls)
        } finally {
            TrackerStateRefreshSignal.removeListener(listener)
        }

        TrackerStateRefreshSignal.notifyChanged()
        assertEquals(1, calls)
    }
}

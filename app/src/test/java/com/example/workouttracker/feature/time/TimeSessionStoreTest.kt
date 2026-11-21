package com.example.workouttracker.feature.time

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.workouttracker.domain.time.TimeSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TimeSessionStoreTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val file = context.getSharedPreferences("reading_session_runtime", Context.MODE_PRIVATE)

    @Test fun aSessionWithoutAnExplicitTrackerIsNotAssignedToAnOriginalView() {
        val started = 1_760_292_000_000
        file.edit().putLong("started_at", started).putString("date_key", "2026-10-12")
            .putLong("ended_at", started + 40 * 60_000L).commit()

        assertNull(PreferencesTimeSessionStore(context).session)
        assertEquals(started, file.getLong("started_at", -1L))
    }

    @Test fun aNoteWithoutAnExplicitTrackerIsNotAssignedToAnOriginalView() {
        file.edit().putString("note_date_key", "2026-10-12").putInt("note_minutes", 7)
            .putBoolean("note_left_app", true).putBoolean("focus_asked", true).commit()

        val store = PreferencesTimeSessionStore(context)
        assertNull(store.note)
        assertTrue(store.focusAsked)
        assertEquals(7, file.getInt("note_minutes", 0))
    }

    @Test fun aSessionAndNoteKeepTheirExplicitTrackerAndStoppedTime() {
        val store = PreferencesTimeSessionStore(context)
        val session = TimeSession("practice", 1_760_292_000_000, "2026-10-12")
        assertTrue(store.writeSession(session))
        store.note = TimeSessionNote("practice", "2026-10-12", minutes = 0, leftApp = false)

        val reopened = PreferencesTimeSessionStore(context)
        assertEquals(session, reopened.session)
        assertEquals(TimeSessionNote("practice", "2026-10-12", minutes = 0, leftApp = false), reopened.note)
        assertEquals("practice", file.getString("view_id", null))

        val stopped = session.copy(viewId = "reading", endedAtMillis = 1_760_292_600_000)
        assertTrue(reopened.writeSession(stopped))
        assertEquals(stopped, PreferencesTimeSessionStore(context).session)

        assertTrue(reopened.writeSession(null))
        reopened.note = null
        assertNull(PreferencesTimeSessionStore(context).session)
        assertNull(PreferencesTimeSessionStore(context).note)
        assertNull(file.getString("view_id", null))
    }

    @Test fun blankSessionAndNoteTrackerIdsAreNotRestored() {
        file.edit().putLong("started_at", 1_760_292_000_000).putString("date_key", "2026-10-12")
            .putString("view_id", " ").putString("note_date_key", "2026-10-12")
            .putString("note_view_id", "").commit()

        val store = PreferencesTimeSessionStore(context)
        assertNull(store.session)
        assertNull(store.note)
    }
}

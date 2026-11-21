package com.example.workouttracker.feature.time

import android.app.NotificationManager.INTERRUPTION_FILTER_ALARMS
import android.app.NotificationManager.INTERRUPTION_FILTER_ALL
import android.app.NotificationManager.INTERRUPTION_FILTER_PRIORITY
import com.example.workouttracker.R
import com.example.workouttracker.core.text.pluralText
import com.example.workouttracker.core.text.uiText
import com.example.workouttracker.domain.AppState
import com.example.workouttracker.domain.compatibility.ReadingTimeId
import com.example.workouttracker.domain.time.TimeSession
import com.example.workouttracker.domain.time.addTimeMinutes
import com.example.workouttracker.domain.time.finishTimeSession
import com.example.workouttracker.domain.time.testReadingTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TimeSessionsTest {
    private val store = FakeStore()
    private val phone = FakeDoNotDisturb()
    private var clock = 1_000_000L

    private fun sessions() = TimeSessions(store, phone, now = { clock })

    private val saved = mutableListOf<Pair<TimeSession, Long>>()
    private val saveMinutes: (TimeSession, Long) -> Int? = { session, end ->
        saved += session to end
        ((end - session.startedAtMillis) / 60_000).toInt()
    }

    @Test
    fun leavingTheAppEndsReadingButTurningTheScreenOffDoesNot() {
        assertFalse(sessionContinuesInBackground(screenInteractive = true))
        assertTrue(sessionContinuesInBackground(screenInteractive = false))
    }

    @Test
    fun aSessionQuietsThePhoneAndPutsBackExactlyWhatWasThere() {
        val sessions = sessions()
        sessions.start(ReadingTimeId)
        assertEquals(INTERRUPTION_FILTER_PRIORITY, phone.filter)

        clock += 23 * 60_000L + 10_000L
        assertTrue(sessions.end(leftApp = false, save = saveMinutes))

        assertEquals(INTERRUPTION_FILTER_ALL, phone.filter)
        assertNull(store.focusChange)
        assertNull(store.session)
        assertEquals(1_000_000L + 23 * 60_000L + 10_000L, saved.single().second)
    }

    @Test
    fun doNotDisturbHeAlreadyChoseIsLeftAlone() {
        phone.filter = INTERRUPTION_FILTER_ALARMS
        val sessions = sessions()
        sessions.start(ReadingTimeId)
        assertEquals(INTERRUPTION_FILTER_ALARMS, phone.filter)

        sessions.end(leftApp = false, save = saveMinutes)
        assertEquals(INTERRUPTION_FILTER_ALARMS, phone.filter)
    }

    @Test
    fun aFilterHeChangedWhileReadingIsHisAndStays() {
        val sessions = sessions()
        sessions.start(ReadingTimeId)
        phone.filter = INTERRUPTION_FILTER_ALARMS

        sessions.end(leftApp = true, save = saveMinutes)

        assertEquals(INTERRUPTION_FILTER_ALARMS, phone.filter)
        assertNull(store.focusChange)
    }

    @Test
    fun aSessionSurvivesTheProcessAndStillRestoresThePhone() {
        sessions().start(ReadingTimeId)
        clock += 40 * 60_000L

        val afterProcessDeath = sessions()

        assertEquals(1_000_000L, afterProcessDeath.session?.startedAtMillis)
        assertEquals(INTERRUPTION_FILTER_PRIORITY, phone.filter)
        afterProcessDeath.end(leftApp = false, save = saveMinutes)
        assertEquals(INTERRUPTION_FILTER_ALL, phone.filter)
        assertEquals(40 * 60_000L, saved.single().second - saved.single().first.startedAtMillis)
    }

    @Test
    fun aChangeLeftBehindWithoutASessionIsPutBackOnTheNextStart() {
        store.focusChange = FocusChange(INTERRUPTION_FILTER_ALL, INTERRUPTION_FILTER_PRIORITY)
        phone.filter = INTERRUPTION_FILTER_PRIORITY

        sessions()

        assertEquals(INTERRUPTION_FILTER_ALL, phone.filter)
        assertNull(store.focusChange)
    }

    @Test
    fun accessIsAskedForOnceAndReadingWorksWithoutIt() {
        phone.hasAccess = false
        val sessions = sessions()

        sessions.requestStart(ReadingTimeId)
        assertEquals(ReadingTimeId, sessions.askingForFocus)
        assertNull(sessions.session)

        sessions.answerFocusQuestion()
        sessions.start(ReadingTimeId)
        assertEquals(INTERRUPTION_FILTER_ALL, phone.filter)
        sessions.end(leftApp = false, save = saveMinutes)

        sessions.requestStart(ReadingTimeId)
        assertNull(sessions.askingForFocus)
        assertTrue(sessions.session != null)
    }

    @Test
    fun aFailedSaveKeepsTheSessionStoppedWhereItEnded() {
        val sessions = sessions()
        sessions.start(ReadingTimeId)
        clock += 30 * 60_000L

        assertFalse(sessions.end(leftApp = true, save = { _, _ -> null }))
        // Reading is over: the phone has its sound back even though the minutes wait for a retry.
        assertEquals(INTERRUPTION_FILTER_ALL, phone.filter)
        val stopped = requireNotNull(sessions.session)
        assertEquals(clock, stopped.endedAtMillis)

        clock += 3 * 60 * 60_000L
        assertTrue(sessions.end(leftApp = false, save = saveMinutes))
        assertEquals(stopped.endedAtMillis, saved.single().second)
    }

    @Test
    fun leavingLeavesOneQuietLineAndAFullSessionLeavesNone() {
        val sessions = sessions()
        sessions.start(ReadingTimeId)
        clock += 23 * 60_000L
        sessions.end(leftApp = true, save = saveMinutes)
        assertEquals(pluralText(R.plurals.time_note_left_app_saved, 23), sessions.note?.let(testReadingTime().words()::sessionNote))

        sessions.start(ReadingTimeId)
        assertNull(sessions.note)
        clock += 10 * 60_000L
        sessions.end(leftApp = false, save = saveMinutes)
        assertNull(sessions.note)

        sessions.start(ReadingTimeId)
        clock += 30_000L
        sessions.end(leftApp = false, save = saveMinutes)
        assertEquals(uiText(R.string.time_note_nothing_saved), sessions.note?.let(testReadingTime().words()::sessionNote))
    }

    /** Saves through the real record rules into [records], as the app does. */
    private var records = AppState()
    private val saveRecords: (TimeSession, Long) -> Int? = { session, end ->
        finishTimeSession(records, session, end).also { records = it.state }.savedMinutes
    }

    @Test
    fun aCompletionInterruptedAfterSavingNeverCreditsTheMinutesTwice() {
        sessions().start(ReadingTimeId)
        clock += 25 * 60_000L

        // The minutes are saved, then the process dies before the session is forgotten.
        runCatching {
            sessions().end(leftApp = false, save = { session, end -> saveRecords(session, end); error("process died") })
        }
        clock += 2 * 60 * 60_000L
        val afterProcessDeath = sessions()

        assertEquals(1_000_000L + 25 * 60_000L, afterProcessDeath.session?.endedAtMillis)
        assertTrue(afterProcessDeath.end(leftApp = false, save = saveRecords))
        assertEquals(25, records.timeLogs.filter { it.viewId == ReadingTimeId }.single().minutes)
        assertNull(store.session)
    }

    @Test
    fun anEndThatCannotBeStoredSavesNothingAndKeepsTheMinutes() {
        val sessions = sessions()
        sessions.start(ReadingTimeId)
        clock += 30 * 60_000L
        val endedAt = clock
        store.acceptWrites = false

        assertFalse(sessions.end(leftApp = false, save = saveRecords))
        assertEquals(emptyList<Any>(), records.timeLogs.filter { it.viewId == ReadingTimeId })
        assertEquals(endedAt, sessions.session?.endedAtMillis)
        // What survives a process death is the running session: the minutes are still there.
        assertEquals(1_000_000L, store.session?.startedAtMillis)

        store.acceptWrites = true
        clock += 60 * 60_000L
        assertTrue(sessions.end(leftApp = false, save = saveRecords))
        assertEquals(30, records.timeLogs.filter { it.viewId == ReadingTimeId }.single().minutes)
    }

    @Test
    fun aSessionThatCannotBeForgottenAfterSavingAddsNothingWhenItComesBack() {
        val sessions = sessions()
        sessions.start(ReadingTimeId)
        clock += 20 * 60_000L
        store.acceptClear = false

        assertTrue(sessions.end(leftApp = false, save = saveRecords))
        assertNull(sessions.session)
        // Other reading saved in between changes the day's record, not what was recorded.
        records = records.copy(timeLogs = addTimeMinutes(records.timeLogs, ReadingTimeId, records.timeLogs.filter { it.viewId == ReadingTimeId }.single().dateKey, 5, clock + 60_000L))

        store.acceptClear = true
        assertTrue(sessions().end(leftApp = false, save = saveRecords))
        assertEquals(25, records.timeLogs.filter { it.viewId == ReadingTimeId }.single().minutes)
        assertNull(store.session)
    }

    private class FakeStore : TimeSessionStore {
        var acceptWrites = true
        var acceptClear = true
        override var session: TimeSession? = null
            private set

        override fun writeSession(session: TimeSession?): Boolean {
            if (!acceptWrites || (session == null && !acceptClear)) return false
            this.session = session
            return true
        }

        override var focusChange: FocusChange? = null
        override var focusAsked: Boolean = false
        override var note: TimeSessionNote? = null
    }

    private class FakeDoNotDisturb : DoNotDisturb {
        override var hasAccess: Boolean = true
        override var filter: Int = INTERRUPTION_FILTER_ALL
            set(value) {
                check(hasAccess) { "Android refuses without access." }
                field = value
            }
    }

    /** A session names the view it was started for, in the owner and in the store, and its note names it too. */
    @Test
    fun aSessionNamesItsViewAndSoDoesItsNote() {
        val sessions = sessions()
        sessions.start("tim_k2p9")
        assertEquals("tim_k2p9", sessions.session?.viewId)
        assertEquals("tim_k2p9", store.session?.viewId)
        assertEquals("tim_k2p9", sessions().session?.viewId)

        clock += 12 * 60_000L
        assertTrue(sessions.end(leftApp = true, save = saveMinutes))
        assertEquals("tim_k2p9", saved.single().first.viewId)
        assertEquals(TimeSessionNote("tim_k2p9", saved.single().first.dateKey, minutes = 12, leftApp = true), sessions.note)
        assertEquals(sessions.note, store.note)
    }

    /** One session runs at a time in the whole app: while one runs, a start of another view, or of the same, is not taken. */
    @Test
    fun onlyOneSessionRunsAtATime() {
        val sessions = sessions()
        sessions.start(ReadingTimeId)
        val running = sessions.session
        clock += 5 * 60_000L

        sessions.start("tim_k2p9")
        sessions.requestStart("tim_k2p9")
        sessions.requestStart(ReadingTimeId)

        assertEquals(running, sessions.session)
        assertEquals(running, store.session)
        assertNull(sessions.askingForFocus)

        // A stopped session whose minutes are not saved yet still holds the place.
        assertFalse(sessions.end(leftApp = false, save = { _, _ -> null }))
        sessions.requestStart("tim_k2p9")
        assertEquals(ReadingTimeId, sessions.session?.viewId)

        // Once it is saved, another view's session starts.
        assertTrue(sessions.end(leftApp = false, save = saveMinutes))
        sessions.requestStart("tim_k2p9")
        assertEquals("tim_k2p9", sessions.session?.viewId)
    }

    /** The question about Do Not Disturb is asked once for the app, by whichever view starts first, and names that view until it is answered. */
    @Test
    fun theFocusQuestionIsAskedOnceForTheAppWhicheverViewAsks() {
        phone.hasAccess = false
        val sessions = sessions()

        sessions.requestStart("tim_k2p9")
        assertEquals("tim_k2p9", sessions.askingForFocus)
        assertNull(sessions.session)
        sessions.dismissFocusQuestion()
        assertNull(sessions.askingForFocus)
        assertFalse(store.focusAsked)

        sessions.requestStart("tim_k2p9")
        sessions.answerFocusQuestion()
        assertNull(sessions.askingForFocus)
        sessions.start("tim_k2p9")
        assertTrue(sessions.end(leftApp = false, save = saveMinutes))

        // Answered for a made view, it is not asked again for Reading.
        sessions.requestStart(ReadingTimeId)
        assertNull(sessions.askingForFocus)
        assertEquals(ReadingTimeId, sessions.session?.viewId)
    }

    /** The phone is quieted and put back the same way whichever view the session is of. */
    @Test
    fun aMadeViewsSessionQuietsThePhoneAndPutsItBack() {
        val sessions = sessions()
        sessions.start("tim_k2p9")
        assertEquals(INTERRUPTION_FILTER_PRIORITY, phone.filter)
        clock += 3 * 60_000L
        assertTrue(sessions.end(leftApp = false, save = saveMinutes))
        assertEquals(INTERRUPTION_FILTER_ALL, phone.filter)
        // Ended on purpose with its minutes saved, it leaves nothing to explain.
        assertNull(sessions.note)
    }

    @Test
    fun anUnlockThatDoesNotComeBackToTheAppEndsReading() {
        assertEquals(true, sessionEndsAfterUnlock(appInForeground = false))
        assertEquals(false, sessionEndsAfterUnlock(appInForeground = true))
    }
}

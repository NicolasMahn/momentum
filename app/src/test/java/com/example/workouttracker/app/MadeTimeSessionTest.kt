package com.example.workouttracker.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import com.example.workouttracker.core.ui.theme.WorkoutTrackerTheme
import com.example.workouttracker.domain.AppState
import com.example.workouttracker.domain.measurement.withWeight
import com.example.workouttracker.domain.questions.withEating
import com.example.workouttracker.domain.ref
import com.example.workouttracker.domain.settings.FeatureVisibilitySettings
import com.example.workouttracker.domain.time.TimeLog
import com.example.workouttracker.domain.time.TimeSession
import com.example.workouttracker.domain.time.deleteTime
import com.example.workouttracker.domain.time.testMadeTime
import com.example.workouttracker.domain.time.withReading
import com.example.workouttracker.domain.activity.withActivity
import com.example.workouttracker.feature.time.TimeSessionNote
import com.example.workouttracker.feature.time.TimeSessions
import com.example.workouttracker.feature.time.testTimeSessions
import com.example.workouttracker.feature.weight.transcript
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * A made time view's timed session through the app: started from its own screen, shown full
 * screen under its name, saved as its own minutes and answered to its reminder, and found again
 * after the process died. One session runs in the whole app. A session whose view has no screen
 * any more is let go of: hidden, it ends and keeps what it timed; deleted, there is nothing to
 * credit.
 */
@RunWith(RobolectricTestRunner::class)
class MadeTimeSessionTest {
    @get:Rule
    val compose = createComposeRule()

    private val piano = testMadeTime()
    private val made = AppState().withWeight().withEating().withReading().withActivity().let { it.copy(time = it.time + piano) }
    private var state by mutableStateOf(made)
    private var visibility by mutableStateOf(FeatureVisibilitySettings())
    private var clock = System.currentTimeMillis()
    private val logged = mutableListOf<List<Any>>()

    @Composable
    private fun App(sessions: TimeSessions) {
        WorkoutTrackerTheme {
            MomentumApp(
                state = state,
                onStateChange = { state = it; true },
                onActivitySave = ActivitySaveNotExpected,
                featureVisibility = visibility,
                onDayLogged = { view, dateKey, before, after ->
                    fun AppState.minutes() = timeLogs.filter { it.viewId == view.id && it.dateKey == dateKey }.map { it.minutes }
                    logged += listOf(view, dateKey, before.minutes(), after.minutes())
                },
                timeSessions = sessions,
            )
        }
    }

    private fun click(tag: String) {
        compose.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
    }

    private fun has(tag: String) = compose.transcript().any { it.startsWith("[$tag]") }

    private val tag = "time_${piano.id}"

    /** Started on its screen, it covers the app under the view's name; Done saves its minutes to the day it started on and lands on its screen. */
    @Test
    fun aSessionStartedOnItsScreenIsSavedAsItsOwnMinutesAndAnswersItsReminder() {
        val sessions = testTimeSessions(hasDoNotDisturbAccess = true, now = { clock })
        compose.setContent { App(sessions) }

        click("home_time_${piano.id}")
        click("${tag}_start")

        val session = requireNotNull(sessions.session)
        assertEquals(piano.id, session.viewId)
        assertTrue(compose.transcript().toString(), has("${tag}_session_minutes"))
        // The session is all there is: no tile, no other view's Start, and a second start is not taken.
        compose.onNodeWithTag("home_time_reading").assertDoesNotExist()
        compose.onNodeWithTag("${tag}_start").assertDoesNotExist()
        sessions.requestStart("reading")
        assertEquals(session, sessions.session)

        clock += 25 * Minute + 30_000
        click("${tag}_session_done")

        assertNull(sessions.session)
        assertEquals(listOf(TimeLog(piano.id, session.dateKey, clock, 25)), state.timeLogs)
        assertEquals(setOf(session.startedAtMillis), state.recordedTimeSessions)
        assertEquals(listOf(listOf(piano.ref, session.dateKey, emptyList<Int?>(), listOf(25))), logged)
        // Back on its own screen, with the week's bar holding what was timed.
        assertEquals("[${tag}_week_minutes] text=\"25 / 60 min\"", compose.transcript().single { it.startsWith("[${tag}_week_minutes]") })
    }

    /** Cut short under a minute, it saves nothing and its screen says so. */
    @Test
    fun aSessionUnderAMinuteSavesNothingAndItsScreenSaysSo() {
        val sessions = testTimeSessions(hasDoNotDisturbAccess = true, now = { clock })
        compose.setContent { App(sessions) }
        click("home_time_${piano.id}")
        click("${tag}_start")
        val started = requireNotNull(sessions.session)

        clock += 40_000
        click("${tag}_session_done")

        assertNull(sessions.session)
        assertEquals(emptyList<TimeLog>(), state.timeLogs)
        assertEquals(emptyList<List<Any>>(), logged)
        assertEquals(TimeSessionNote(piano.id, started.dateKey, minutes = 0, leftApp = false), sessions.note)
        assertTrue(compose.transcript().toString(), "[${tag}_session_note] text=\"Under a minute · nothing saved\"" in compose.transcript())
    }

    /** A session an earlier process left behind is found again: the app opens on it, and it ends on its view's screen. */
    @Test
    fun aSessionIsFoundAgainAfterTheProcessDied() {
        val started = TimeSession.startingAt(piano.id, clock - 12 * Minute)
        val sessions = testTimeSessions(hasDoNotDisturbAccess = true, now = { clock }, stored = started)
        compose.setContent { App(sessions) }
        compose.waitForIdle()

        assertTrue(compose.transcript().toString(), "[${tag}_session_minutes] text=\"⁨Piano⁩ · 12 min\"" in compose.transcript())
        click("${tag}_session_done")

        assertEquals(listOf(TimeLog(piano.id, started.dateKey, clock, 12)), state.timeLogs)
        compose.onNodeWithTag("${tag}_start").assertExists()
    }

    /** Hidden while its session waits, the view has no screen to run it on: the session ends, what it timed is kept, and the app is on Home. */
    @Test
    fun aSessionOfAViewHiddenMeanwhileEndsAndKeepsWhatItTimed() {
        val started = TimeSession.startingAt(piano.id, clock - 12 * Minute)
        val sessions = testTimeSessions(hasDoNotDisturbAccess = true, now = { clock }, stored = started)
        visibility = FeatureVisibilitySettings().hiding(piano.ref)
        compose.setContent { App(sessions) }
        compose.waitForIdle()

        assertNull(sessions.session)
        assertEquals(listOf(TimeLog(piano.id, started.dateKey, clock, 12)), state.timeLogs)
        compose.onNodeWithTag("home_settings").assertExists()
        compose.onNodeWithTag("home_time_${piano.id}").assertDoesNotExist()
        // The one session of the app is free again.
        sessions.requestStart("reading")
        assertEquals("reading", sessions.session?.viewId)
    }

    /** Deleted while its session waits, the view has nothing to credit: the session is let go of and nothing is stored. */
    @Test
    fun aSessionOfAViewDeletedMeanwhileIsLetGoOfWithNothingStored() {
        val started = TimeSession.startingAt(piano.id, clock - 12 * Minute)
        val sessions = testTimeSessions(hasDoNotDisturbAccess = true, now = { clock }, stored = started)
        state = made.deleteTime(piano.id)
        compose.setContent { App(sessions) }
        compose.waitForIdle()

        assertNull(sessions.session)
        assertEquals(emptyList<TimeLog>(), state.timeLogs)
        assertEquals(emptySet<Long>(), state.recordedTimeSessions)
        assertEquals(emptyList<List<Any>>(), logged)
        compose.onNodeWithTag("home_settings").assertExists()
        sessions.requestStart("reading")
        assertEquals("reading", sessions.session?.viewId)
    }

    private companion object {
        const val Minute = 60_000L
    }
}

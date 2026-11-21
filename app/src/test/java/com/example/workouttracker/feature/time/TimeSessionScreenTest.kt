package com.example.workouttracker.feature.time

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import com.example.workouttracker.core.ui.theme.WorkoutTrackerTheme
import com.example.workouttracker.domain.checklist.MoMotif
import com.example.workouttracker.domain.mo.MoContentKey
import com.example.workouttracker.domain.mo.MoSaveAct
import com.example.workouttracker.domain.mo.MoState
import com.example.workouttracker.domain.mo.MoViewKind
import com.example.workouttracker.domain.mo.figure
import com.example.workouttracker.domain.compatibility.ReadingTimeId
import com.example.workouttracker.domain.time.TimeConfiguration
import com.example.workouttracker.domain.time.TimeSession
import com.example.workouttracker.domain.time.testMadeTime
import com.example.workouttracker.domain.time.testReadingTime
import com.example.workouttracker.feature.mo.MoLook
import com.example.workouttracker.feature.weight.transcript
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Timer sessions use their configured name, identity, motif and record ownership. */
@RunWith(RobolectricTestRunner::class)
class TimeSessionScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val reading = testReadingTime()
    private val piano = testMadeTime()
    private val tag = "time_tim_k2p9"
    private var content by mutableStateOf<@Composable () -> Unit>({})

    private fun show(shown: @Composable () -> Unit) {
        content = shown
        compose.waitForIdle()
    }

    private fun start() = compose.setContent { WorkoutTrackerTheme { content() } }

    private fun click(tag: String) {
        compose.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
    }

    private fun has(tag: String): Boolean = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    /** The name is shown as typed, kept apart from the sentence around it whichever way it is written. */
    private fun name(view: TimeConfiguration) = "⁨${view.label}⁩"

    @Test
    fun aMadeViewsSessionShowsItsNameAndMoAsHeIs() {
        start()
        var done = 0
        var looks: Pair<TimeSessionAppearance, TimeSessionAppearance>? = null
        fun session(minutes: Int, saveFailed: Boolean = false) = show {
            looks = piano.sessionAppearance() to reading.sessionAppearance()
            TimeSessionScreen(minutes = minutes, saveFailed = saveFailed, appearance = piano.sessionAppearance(), onDone = { done += 1 })
        }
        session(minutes = 0)
        assertEquals(listOf("[${tag}_session_minutes] text=\"${name(piano)} · 0 min\"", "text=\"Done\""), compose.transcript())
        assertTrue(has("${tag}_session"))
        session(minutes = 1)
        assertEquals("[${tag}_session_minutes] text=\"${name(piano)} · 1 min\"", compose.transcript().first())
        session(minutes = 23, saveFailed = true)
        assertEquals(
            listOf("[${tag}_session_minutes] text=\"${name(piano)} · 23 min\"", "[${tag}_session_error] text=\"Couldn't save. Try again.\"", "text=\"Done\""),
            compose.transcript(),
        )
        click("${tag}_session_done")
        assertEquals(1, done)

        // The preset and the plain timer retain their configured motifs and identities.
        val (pianos, readings) = looks!!
        assertEquals(CalmMo, pianos.mo)
        assertNull(pianos.mo.doing)
        assertNotEquals(CalmMo, readings.mo)
        assertEquals("time_reading", readings.testTagPrefix)
        assertNotEquals(readings.colors, pianos.colors)
    }

    /**
     * Mo keeps company by the view's flavor and by nothing else. Reading's own is Mo with his
     * book, as it always was; a made view set to Reading has the same Mo, and Reading set to Plain
     * has Mo as he is. Neither changes a word, a color or a tag of the session.
     */
    @Test
    fun moKeepsCompanyByTheViewsFlavorAndNothingElseFollowsIt() {
        start()
        var looks = emptyList<TimeSessionAppearance>()
        show { looks = listOf(piano, piano.copy(mo = piano.mo.copy(motif = MoMotif.Book)), reading, reading.copy(mo = reading.mo.copy(motif = null))).map { it.sessionAppearance() } }
        val (plainPiano, readingPiano, readingsOwn, plainReading) = looks

        val moWithHisBook = MoLook(state = MoState.Steady, doing = MoContentKey(MoViewKind.Time, MoMotif.Book).figure.saveAct)
        assertEquals(MoSaveAct.Reading, moWithHisBook.doing)
        assertEquals(moWithHisBook, readingsOwn.mo)
        assertEquals(moWithHisBook, readingPiano.mo)
        assertEquals(CalmMo, plainPiano.mo)
        assertEquals(CalmMo, plainReading.mo)

        assertEquals(plainPiano.colors, readingPiano.colors)
        assertEquals(plainPiano.testTagPrefix, readingPiano.testTagPrefix)
        assertEquals(plainPiano.words.start, readingPiano.words.start)
        assertEquals(readingsOwn.colors, plainReading.colors)
        assertEquals("time_reading", plainReading.testTagPrefix)
        assertEquals(readingsOwn.words.sessionMinutes(23), plainReading.words.sessionMinutes(23))
    }

    @Test
    fun theReadingPresetUsesTheSameTimerWordingAndItsCurrentName() {
        start()
        val renamed = reading.copy(label = "Evening practice")
        show { TimeSessionScreen(minutes = 23, saveFailed = false, appearance = renamed.sessionAppearance(), onDone = {}) }
        assertEquals("[time_reading_session_minutes] text=\"${name(renamed)} · 23 min\"", compose.transcript().first())
        assertEquals(piano.words().start, renamed.words().start)
        assertEquals(piano.words().focusMessage, renamed.words().focusMessage)
    }

    @Test
    @Config(qualifiers = "de")
    fun aMadeViewsSessionSpeaksGerman() {
        start()
        show { TimeSessionScreen(minutes = 23, saveFailed = true, appearance = piano.sessionAppearance(), onDone = {}) }
        assertEquals(
            listOf(
                "[${tag}_session_minutes] text=\"${name(piano)} · 23 min\"",
                "[${tag}_session_error] text=\"Der Eintrag konnte nicht gespeichert werden. Versuch es noch einmal.\"",
                "text=\"Fertig\"",
            ),
            compose.transcript(),
        )
    }

    /** The question is asked in the kind's words for the view whose start waits, and the session it starts is that view's. */
    @Test
    fun theQuestionBeforeAMadeViewsFirstSessionStartsThatViewsSession() {
        start()
        val sessions = testTimeSessions()
        val asked = mutableListOf<String>()
        show {
            TimeFocusQuestion(sessions) { viewId ->
                asked += viewId
                piano.takeIf { it.id == viewId }?.sessionAppearance()
            }
        }
        assertEquals(emptyList<String>(), compose.transcript())

        sessions.requestStart(piano.id)
        compose.waitForIdle()
        assertEquals(
            listOf(
                "text=\"Momentum can turn on Do Not Disturb while the timer runs and put it back when you finish. Android asks you to allow that once, in its settings.\"",
                "text=\"Start without Do Not Disturb\"",
                "text=\"Allow\"",
            ),
            compose.transcript(),
        )
        assertTrue(has("${tag}_focus_dialog") && has("${tag}_focus_allow"))
        assertEquals(piano.id, asked.last())
        assertNull(sessions.session)

        click("${tag}_focus_without")
        assertEquals(piano.id, sessions.session?.viewId)
        assertEquals(emptyList<String>(), compose.transcript())
    }

    /** A start that waits for a view nothing knows any more asks nothing. */
    @Test
    fun aQuestionForAViewThatIsGoneIsNotShown() {
        start()
        val sessions = testTimeSessions().apply { requestStart("tim_gone") }
        show { TimeFocusQuestion(sessions) { viewId -> piano.takeIf { it.id == viewId }?.sessionAppearance() } }
        assertEquals(emptyList<String>(), compose.transcript())
        assertNull(sessions.session)
    }

    /** Done ends the running session and hands it to be saved as its own view's, and the note it leaves names that view. */
    @Test
    fun doneEndsAMadeViewsSessionAndSavesItAsItsOwn() {
        start()
        var clock = 1_000_000L
        val sessions = testTimeSessions(hasDoNotDisturbAccess = true, now = { clock })
        sessions.start(piano.id)
        // A second session is not started beside it, whichever view asks.
        sessions.requestStart(ReadingTimeId)
        assertEquals(piano.id, sessions.session?.viewId)
        val saved = mutableListOf<TimeSession>()
        show {
            sessions.session?.let { session ->
                TimeSessionHost(sessions, session, piano.sessionAppearance()) { ended, _ ->
                    saved += ended
                    0
                }
            }
        }
        assertTrue(has("${tag}_session"))

        clock += 20_000L
        click("${tag}_session_done")
        assertEquals(piano.id, saved.single().viewId)
        assertNull(sessions.session)
        assertFalse(has("${tag}_session"))
        assertEquals(TimeSessionNote(piano.id, saved.single().dateKey, minutes = 0, leftApp = false), sessions.note)
    }
}

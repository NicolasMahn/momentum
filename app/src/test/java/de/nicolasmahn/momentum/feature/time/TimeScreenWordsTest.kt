package de.nicolasmahn.momentum.feature.time

import de.nicolasmahn.momentum.domain.time.TimeLog
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import de.nicolasmahn.momentum.core.ui.DayCover
import de.nicolasmahn.momentum.core.ui.LocalDayCovers
import de.nicolasmahn.momentum.core.ui.theme.WorkoutTrackerTheme
import de.nicolasmahn.momentum.domain.AppState
import de.nicolasmahn.momentum.domain.DayChangeResult
import de.nicolasmahn.momentum.domain.changingDay
import de.nicolasmahn.momentum.domain.holiday.HolidaySchedule
import de.nicolasmahn.momentum.domain.ref
import de.nicolasmahn.momentum.domain.trackerRef
import de.nicolasmahn.momentum.feature.weight.transcript
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import de.nicolasmahn.momentum.domain.compatibility.ReadingTimeId
import de.nicolasmahn.momentum.domain.time.testReadingTime
import de.nicolasmahn.momentum.domain.time.withReading
import de.nicolasmahn.momentum.domain.activity.withActivity

/** The timer, session and focus prompt in both languages, exercised through one user flow. */
@RunWith(RobolectricTestRunner::class)
class TimeScreenWordsTest : TimeScreenStage {
    @get:Rule
    val compose = createComposeRule()

    private var shown by mutableStateOf<Shown?>(null)
    private var scene by mutableIntStateOf(0)
    private var state by mutableStateOf(AppState())
    private val reading = testReadingTime()
    private val scriptCovers = mapOf(
        reading.ref.trackerRef to mapOf("2026-09-13" to DayCover.Token, "2026-09-06" to DayCover.Freeze),
    )
    private val lines = mutableListOf<String>()

    /** The time a save is stored at, later than every record of the script. */
    private var clock = 1_000L

    private sealed interface Shown {
        data class Screen(val refuse: Boolean, val daysOff: HolidaySchedule, val note: TimeScriptNote?, val covered: Boolean) : Shown

        data class Session(val minutes: Int, val saveFailed: Boolean) : Shown

        data object FocusQuestion : Shown
    }

    @Test fun english() = compareWithTheRecordedScreen("en")

    @Test
    @Config(qualifiers = "de")
    fun german() = compareWithTheRecordedScreen("de")

    private fun compareWithTheRecordedScreen(language: String) {
        compose.setContent {
            WorkoutTrackerTheme {
                key(scene) {
                    when (val showing = shown) {
                        is Shown.Screen -> CompositionLocalProvider(LocalDayCovers provides if (showing.covered) scriptCovers else emptyMap()) {
                            TimeScreen(
                                state = state,
                                view = reading,
                                words = reading.words(),
                                onDayChange = { change ->
                                    if (showing.refuse) DayChangeResult.Failed else state.changingDay(change, timestampMillis = clock++) { updated -> state = updated; true }
                                },
                                todayKey = TimeScriptToday,
                                daysOff = showing.daysOff,
                                sessionNote = showing.note?.let { note ->
                                    when (note) {
                                        TimeScriptNote.LeftAppSaved -> TimeSessionNote(ReadingTimeId, TimeScriptToday, minutes = 12, leftApp = true)
                                        TimeScriptNote.LeftAppNothing -> TimeSessionNote(ReadingTimeId, TimeScriptToday, minutes = 0, leftApp = true)
                                        TimeScriptNote.NothingSaved -> TimeSessionNote(ReadingTimeId, TimeScriptToday, minutes = 0, leftApp = false)
                                    }
                                },
                                testTagPrefix = reading.testTagPrefix,
                                covers = LocalDayCovers.current[reading.ref.trackerRef].orEmpty(),
                            )
                        }
                        is Shown.Session -> TimeSessionScreen(minutes = showing.minutes, saveFailed = showing.saveFailed, appearance = reading.sessionAppearance(), onDone = {})
                        Shown.FocusQuestion -> TimeFocusQuestion(remember { askingSessions() }) { reading.sessionAppearance() }
                        null -> Unit
                    }
                }
            }
        }
        playTimeScreen()
        val actual = lines.joinToString("\n", postfix = "\n")
        val expectedFile = File("src/test/frozen/time/screen-$language.txt")
        File("build/time-screen").apply { mkdirs() }.resolve("screen-$language.txt").writeText(actual)
        if (System.getenv("TIME_SCREEN_WRITE") == "1") expectedFile.apply { parentFile.mkdirs() }.writeText(actual)
        assertEquals(expectedFile.readText(), actual)
    }

    override fun open(records: List<TimeLog>, refuse: Boolean, daysOff: HolidaySchedule, note: TimeScriptNote?, covered: Boolean) {
        state = AppState(timeLogs = records).withReading().withActivity()
        show(Shown.Screen(refuse, daysOff, note, covered))
    }

    override fun openSession(minutes: Int, saveFailed: Boolean) = show(Shown.Session(minutes, saveFailed))

    override fun openFocusQuestion() = show(Shown.FocusQuestion)

    private fun show(next: Shown) {
        shown = next
        scene += 1
        compose.waitForIdle()
    }

    override fun select(dateKey: String) = click("time_reading_history_$dateKey")

    override fun click(tag: String) {
        compose.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
    }

    override fun type(tag: String, text: String) {
        compose.onNodeWithTag(tag).performTextReplacement(text)
        compose.waitForIdle()
    }

    override fun see(vararg tags: String) = tags.forEach { compose.onNodeWithTag(it, useUnmergedTree = true).assertExists() }

    override fun waitAMoment() {
        compose.mainClock.advanceTimeBy(2_000L)
        compose.waitForIdle()
    }

    override fun say(title: String) {
        lines += "## $title"
        lines += compose.transcript()
    }

    /** A session owner on a phone without Do Not Disturb access, asked to start for the first time. */
    private fun askingSessions() = testTimeSessions().apply { requestStart(ReadingTimeId) }
}

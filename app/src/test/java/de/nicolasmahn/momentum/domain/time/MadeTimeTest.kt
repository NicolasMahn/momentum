package de.nicolasmahn.momentum.domain.time

import de.nicolasmahn.momentum.domain.compatibility.madeTime

import de.nicolasmahn.momentum.domain.compatibility.readingTime

import de.nicolasmahn.momentum.domain.compatibility.ReadingTimeId

import de.nicolasmahn.momentum.domain.AppState
import de.nicolasmahn.momentum.domain.activity.withActivity
import de.nicolasmahn.momentum.domain.LoggedViewKind
import de.nicolasmahn.momentum.domain.LoggedViewRef
import de.nicolasmahn.momentum.domain.checklist.Checkpoint
import de.nicolasmahn.momentum.domain.checklist.CheckpointTime
import de.nicolasmahn.momentum.domain.checklist.Dated
import de.nicolasmahn.momentum.domain.checklist.ViewIcon
import de.nicolasmahn.momentum.domain.checklist.WhenAway
import de.nicolasmahn.momentum.domain.isLoggedOn
import de.nicolasmahn.momentum.domain.compatibility.madeDailyViews
import de.nicolasmahn.momentum.domain.compatibility.madeLoggedView
import de.nicolasmahn.momentum.domain.compatibility.madeLoggedViews
import de.nicolasmahn.momentum.domain.measurement.testMadeMeasurement
import de.nicolasmahn.momentum.domain.questions.testMadeQuestions
import de.nicolasmahn.momentum.domain.ref
import de.nicolasmahn.momentum.domain.settings.FeatureVisibilitySettings
import de.nicolasmahn.momentum.domain.withoutLeftoverEntries
import java.time.LocalDate
import java.time.LocalTime
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** A time view a person makes: the ID it is given, how it is added, and what its edits are read against. */
class MadeTimeTest {
    private val today = LocalDate.parse("2026-10-14")
    private val piano = testMadeTime()

    /**
     * An ID is taken if anything names it: a configuration, a stored log of a view no
     * configuration knows, whose minutes a new view under that ID would be handed, or an entry
     * among the hidden views of the visibility settings, under which it would start out hidden.
     */
    @Test fun aNewViewsIdIsOneThatNoConfigurationNoLogAndNoHiddenEntryNames() {
        val state = AppState(timeLogs = listOf(TimeLog("tim_bbbb", "2026-10-13", 1L, 20))).withReading().withActivity()
            .let { it.copy(time = it.time + testMadeTime(id = "tim_aaaa")) }
        assertEquals(setOf("reading", "tim_aaaa", "tim_bbbb"), state.namedTimeIds)

        // Draws "aaaa", then "bbbb", then "cccc", then "dddd".
        assertEquals("tim_cccc", state.freshTimeId(random = Repeating(0, 1, 2, 3)))
        assertEquals("tim_dddd", state.freshTimeId(hidden = setOf("tim_cccc"), random = Repeating(0, 1, 2, 3)))
        // Whatever is drawn is an ID a made view may have.
        repeat(20) { assertTrue(testMadeTime(id = AppState().withReading().withActivity().freshTimeId()).id.matches(Regex("tim_[a-z2-9]{4}"))) }
    }

    /** Adding is refused under an ID anything names, so a creation retried or restored is never made twice. */
    @Test fun addingRefusesAnIdAConfigurationALogOrAHiddenEntryNames() {
        val state = AppState(timeLogs = listOf(TimeLog("tim_gone", "2026-10-13", 1L, 20))).withReading().withActivity()

        val added = state.addTime(piano)
        assertEquals(listOf(testReadingTime(), piano), added.time)
        assertEquals(state.copy(time = added.time), added)
        assertEquals(listOf(piano), added.madeTime)
        assertEquals(testReadingTime(), added.readingTime)

        assertThrows(IllegalArgumentException::class.java) { added.addTime(piano) }
        assertThrows(IllegalArgumentException::class.java) { added.addTime(piano.copy(label = "Another")) }
        assertThrows(IllegalArgumentException::class.java) { state.addTime(testMadeTime(id = "tim_gone")) }
        assertThrows(IllegalArgumentException::class.java) { state.addTime(piano, hidden = setOf(piano.id)) }
        assertEquals(2, state.addTime(piano, hidden = setOf("tim_m7xd")).time.size)
    }

    /** A page's edits go over the stored view they name and no other; a view the state does not hold leaves it as it is. */
    @Test fun editsGoOverTheStoredViewTheyName() {
        val state = AppState().withReading().withActivity().addTime(piano)

        val edited = state.applyingTimeEdits(piano.id, listOf(TimeEdit.Rename("Guitar"), TimeEdit.SetGoal(90)), today)
        assertEquals(listOf(testReadingTime(), piano.copy(label = "Guitar", goal = Dated.always(90))), edited.time)

        val readings = state.applyingTimeEdits(ReadingTimeId, listOf(TimeEdit.SetGoal(90)), today)
        assertEquals(Dated(listOf(Dated.Revision(60), Dated.Revision(90, today))), readings.readingTime!!.goal)
        assertEquals(listOf(piano), readings.madeTime)

        assertSame(state, state.applyingTimeEdits("tim_gone", listOf(TimeEdit.Rename("Guitar")), today))
        assertSame(state, state.applyingTimeEdits(piano.id, listOf(TimeEdit.Rename("Piano"), TimeEdit.SetGoal(0)), today))
        assertSame(state, state.applyingTimeEdits(piano.id, emptyList(), today))
    }

    /** A state that was never loaded through the repository holds no configuration for Reading, and one a person made is not it. */
    @Test fun aStateWithoutReadingsConfigurationSaysSo() {
        assertEquals(null, AppState().readingTime)
        assertEquals(null, AppState(time = listOf(piano)).readingTime)
        assertEquals(listOf(piano), AppState(time = listOf(piano)).madeTime)
    }

    /**
     * A view starts as the setup shows it first: the timer, an hour a week, outside the streak,
     * still due on holiday, its reminder off at 09:00, in a starter color nothing wears yet, under
     * an ID nothing names, a hidden entry included.
     */
    @Test fun aStarterIsAnHourAWeekOutsideTheStreakUnderAnIdNothingNames() {
        val state = AppState(timeLogs = listOf(TimeLog("tim_bbbb", "2026-10-13", 1L, 20))).withReading().withActivity()
            .let { it.copy(time = it.time + testMadeTime(id = "tim_aaaa"), measurements = listOf(testMadeMeasurement()), questions = listOf(testMadeQuestions())) }

        val starter = state.starterTime("Time", hidden = setOf("tim_cccc"), random = Repeating(0, 1, 2, 3))

        assertEquals(
            TimeConfiguration(
                id = "tim_dddd",
                label = "Time",
                icon = ViewIcon.Timer,
                hue = starter.hue,
                goal = Dated.always(60),
                rewards = null,
                whenAway = Dated.always(WhenAway.Due),
                checkpoint = Checkpoint("day", CheckpointTime.Fixed(LocalTime.of(9, 0)), remind = false),
            ),
            starter,
        )
        // Not Reading's yellow, nor the color of a view of any other kind.
        assertFalse(starter.hue in (state.time.map { it.hue } + state.measurements.map { it.hue } + state.questions.map { it.hue }))
        assertEquals(state.time + starter, state.addTime(starter, hidden = setOf("tim_cccc")).time)
    }

    /** Deleting takes the configuration and every log that names it, those of days after today too, and nothing else; Reading stays. */
    @Test fun deletingTakesTheViewWithEveryLogOfItAndNothingElse() {
        val guitar = testMadeTime(id = "tim_m7xd", label = "Guitar")
        val pianos = listOf(TimeLog(piano.id, "2026-12-24", 6L, 30), TimeLog(piano.id, "2026-10-13", 4L, 20))
        val others = listOf(TimeLog(guitar.id, "2026-10-13", 4L, 45), TimeLog("reading", "2026-10-13", 3L), TimeLog("tim_gone", "2026-10-12", 2L, 5))
        val state = AppState(timeLogs = (pianos + others).sortedWith(TimeLogOrder), recordedTimeSessions = setOf(7L)).withReading().withActivity()
            .let { it.copy(time = it.time + piano + guitar) }
        assertEquals(2, state.loggedDaysOf(piano.id))
        assertEquals(1, state.loggedDaysOf(guitar.id))
        assertEquals(0, state.loggedDaysOf("tim_none"))

        val deleted = state.deleteTime(piano.id)

        assertEquals(listOf(testReadingTime(), guitar), deleted.time)
        assertEquals(others.sortedWith(TimeLogOrder), deleted.timeLogs)
        assertEquals(state.copy(time = deleted.time, timeLogs = deleted.timeLogs), deleted)
        assertThrows(IllegalArgumentException::class.java) { deleted.deleteTime(piano.id) }
        // Reading is deleted like any other, with every log of its own, its marks without minutes included.
        val withoutReading = state.deleteTime("reading")
        assertEquals(listOf(piano, guitar), withoutReading.time)
        assertEquals(state.timeLogs.filterNot { it.viewId == "reading" }, withoutReading.timeLogs)
    }

    /**
     * A time view a person made is a logged view beside the measurements and the questions views,
     * after them, named with its kind, and never among the daily ones: it is not kept by logging a
     * day. Reading is built in and among neither.
     */
    @Test fun aMadeTimeViewIsALoggedViewAndNoDailyOne() {
        val waist = testMadeMeasurement()
        val sleep = testMadeQuestions()
        val state = AppState(measurements = listOf(waist), questions = listOf(sleep)).withReading().withActivity().addTime(piano)
            .copy(timeLogs = listOf(TimeLog(piano.id, "2026-10-13", 1L, 20)))

        assertEquals(listOf(waist, sleep, piano), state.madeLoggedViews)
        assertEquals(listOf(waist, sleep), state.madeDailyViews)
        assertEquals(LoggedViewRef(LoggedViewKind.Time, piano.id), piano.ref)
        assertEquals(piano, state.madeLoggedView(piano.ref))
        assertNull(state.madeLoggedView(LoggedViewRef(LoggedViewKind.Time, "reading")))
        assertNull(state.madeLoggedView(LoggedViewRef(LoggedViewKind.Measurement, piano.id)))
        // Its reminder asks about the day it rings on, and that day holds a log or none.
        assertEquals(today, piano.recordDayFor(today))
        assertTrue(state.isLoggedOn(piano.ref, "2026-10-13"))
        assertFalse(state.isLoggedOn(piano.ref, "2026-10-14"))
        assertFalse(state.isLoggedOn(LoggedViewRef(LoggedViewKind.Questions, piano.id), "2026-10-13"))
    }

    /**
     * A time view is hidden among its own kind, whatever another kind's view of that ID is. A
     * delete whose second write failed leaves a hidden entry behind: it hides nothing, and whoever
     * saves the visibility settings with the configurations at hand drops it.
     */
    @Test fun aTimeViewIsHiddenAmongItsOwnKindAndALeftoverEntryIsDropped() {
        val state = AppState().withReading().withActivity().addTime(piano)
        val visibility = FeatureVisibilitySettings(hiddenMeasurements = setOf(piano.id), hiddenQuestions = setOf("tim_gone"), hiddenTime = setOf(piano.id, "tim_gone", "reading"))

        assertFalse(visibility.shows(piano.ref))
        assertEquals(setOf(piano.id, "tim_gone", "reading"), visibility.namedIds(LoggedViewKind.Time))
        // Reading is a time view with its configuration, so its entry stays with Piano's; the rest name nothing configured.
        assertEquals(FeatureVisibilitySettings(hiddenTime = setOf(piano.id, "reading")), state.withoutLeftoverEntries(visibility))
        assertEquals(FeatureVisibilitySettings(hiddenMeasurements = setOf(piano.id), hiddenQuestions = setOf("tim_gone"), hiddenTime = setOf("tim_gone", "reading")), visibility.forgetting(piano.ref))
        assertEquals(FeatureVisibilitySettings(hiddenTime = setOf(piano.id)), FeatureVisibilitySettings().hiding(piano.ref))
        assertTrue(FeatureVisibilitySettings(hiddenMeasurements = setOf(piano.id), hiddenQuestions = setOf(piano.id)).shows(piano.ref))
    }

    /** Draws the same letter for each of an ID's four characters, one letter after another per ID. */
    private class Repeating(private vararg val letters: Int) : Random() {
        private var drawn = 0
        override fun nextBits(bitCount: Int): Int = error("An ID is drawn a character at a time.")
        override fun nextInt(until: Int): Int = letters[(drawn++ / 4) % letters.size]
    }
}

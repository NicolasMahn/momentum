package com.example.workouttracker.feature.time

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import com.example.workouttracker.core.ui.theme.WorkoutTrackerTheme
import com.example.workouttracker.domain.AppState
import com.example.workouttracker.domain.DayChangeResult
import com.example.workouttracker.domain.changingDay
import com.example.workouttracker.domain.checklist.Dated
import com.example.workouttracker.domain.checklist.WhenAway
import com.example.workouttracker.domain.holiday.DayRange
import com.example.workouttracker.domain.holiday.HolidaySchedule
import com.example.workouttracker.domain.compatibility.ReadingTimeId
import com.example.workouttracker.domain.time.TimeConfiguration
import com.example.workouttracker.domain.time.TimeLog
import com.example.workouttracker.domain.time.addTimeMinutes
import com.example.workouttracker.domain.time.testMadeTime
import com.example.workouttracker.domain.time.testReadingTime
import com.example.workouttracker.feature.weight.selectedHistoryDay
import com.example.workouttracker.feature.weight.transcript
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import com.example.workouttracker.domain.time.withReading

/**
 * The time screen for a view a person made, and for two views side by side: its own name among the
 * kind's generic words and no word of Reading's, its tags under its own ID, each view reading and
 * writing its own logs alone, the week's bar by the view's own goal and holiday rule, a day's
 * minutes decided against the store as it is, and what is typed belonging to the view's ID and the
 * selected day.
 */
@RunWith(RobolectricTestRunner::class)
class TimeScreenTest {
    @get:Rule
    val compose = createComposeRule()

    /** A Wednesday. */
    private val today = "2026-10-21"
    private val reading = testReadingTime()

    /** Ninety minutes a week, not due on holiday. */
    private val piano = testMadeTime(goal = 90).copy(whenAway = Dated.always(WhenAway.Excused))
    private val tag = "time_tim_k2p9"

    /** Piano and Reading logged on the same days, with a log of a view nothing knows among them. */
    private val logged = AppState(
        time = listOf(reading, piano),
        timeLogs = listOf(
            TimeLog(piano.id, "2026-10-20", 9, 40),
            TimeLog(ReadingTimeId, "2026-10-20", 8, 25),
            TimeLog("tim_gone", "2026-10-20", 7, 300),
            TimeLog(piano.id, "2026-10-19", 2, 20),
            TimeLog(ReadingTimeId, "2026-10-19", 1),
            TimeLog(piano.id, "2026-10-12", 0, 1440),
        ),
    )

    private var state by mutableStateOf(logged)
    private var shown by mutableStateOf<TimeConfiguration?>(null)
    private var todayKey by mutableStateOf(today)
    private var daysOff by mutableStateOf(HolidaySchedule())
    private var note by mutableStateOf<TimeSessionNote?>(null)
    private var starts = 0
    private var isShown = false

    /** What the store does with a change: null stores it against the records as they are. */
    private var answer: ((TimeDayChange) -> DayChangeResult)? = null

    /** The time a change is stored at, later than every log here. */
    private var clock = 1_000L

    /** The screen is shown through this, so a test can take the process away and bring the saved state back. */
    private val restoration by lazy { StateRestorationTester(compose) }

    private fun restore() {
        restoration.emulateSavedInstanceStateRestore()
        compose.waitForIdle()
    }

    /** Shows [view] over [records]; a second call hands the same screen another view, as the app does. */
    private fun show(view: TimeConfiguration, records: AppState = logged, day: () -> String = { todayKey }) {
        state = records
        shown = view
        if (isShown) return compose.waitForIdle()
        isShown = true
        restoration.setContent {
            WorkoutTrackerTheme {
                shown?.let { view ->
                    TimeScreen(
                        state = state,
                        view = view,
                        words = view.words(),
                        onDayChange = { change -> answer?.invoke(change) ?: state.changingDay(change, timestampMillis = clock++) { updated -> state = updated; true } },
                        todayKey = day(),
                        daysOff = daysOff,
                        onStartSession = { starts += 1 },
                        sessionNote = note?.takeIf { it.viewId == view.id },
                        testTagPrefix = view.testTagPrefix,
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    /** Picks a day as a person does: by its cell in the calendar. */
    private fun select(dateKey: String, prefix: String = tag) = click("${prefix}_history_$dateKey")

    private fun selectedDay(prefix: String = tag): String = said().selectedHistoryDay(prefix)

    private fun click(tag: String) {
        compose.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
    }

    private fun type(minutes: String, prefix: String = tag) {
        compose.onNodeWithTag("${prefix}_minutes_input").performTextReplacement(minutes)
        compose.waitForIdle()
    }

    private fun said(): List<String> = compose.transcript()

    /** A column heading of the calendar: one word, spoken, in either language. */
    private val WeekdayHeading = Regex("""spoken="\p{L}+"""")
    private fun line(tag: String): String = said().single { it.startsWith("[$tag]") }
    private fun has(tag: String): Boolean = compose.onAllNodes(androidx.compose.ui.test.hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun logsOf(view: TimeConfiguration) = state.timeLogs.filter { it.viewId == view.id }

    /** Everything the screen says outside its calendar, which is long and has tests of its own. */
    private fun form(): List<String> = said().filterNot { it.startsWith("[${tag}_history_") || it.matches(Regex("""text="\d+"""")) || it.matches(WeekdayHeading) }

    @Test
    fun aMadeViewsScreenSpeaksTheKindsWordsUnderItsOwnTags() {
        show(piano)
        assertEquals(
            listOf(
                "spoken=\"60 / 90 min this week\"",
                "[${tag}_week_minutes] text=\"60 / 90 min\"",
                "text=\"Start the timer\"",
                "[${tag}_type_minutes] spoken=\"Add minutes without the timer\"",
                "[${tag}_remove_day] spoken=\"Remove the entry for Wed, Oct 21\" disabled",
            ),
            form(),
        )
        assertTrue(has("${tag}_start"))
        assertEquals("[${tag}_history_2026-10-20] spoken=\"Tue, Oct 20: minutes recorded. Select date.\" not selected click=\"Select date\"", line("${tag}_history_2026-10-20"))
        assertEquals("[${tag}_history_2026-10-21] spoken=\"Today, Wed, Oct 21: no entry. Select date.\" selected click=\"Select date\"", line("${tag}_history_2026-10-21"))

        click("${tag}_type_minutes")
        assertEquals(
            listOf(
                "[${tag}_minutes_input] field=\"\"",
                "text=\"Minutes\"",
                "[${tag}_log_today] state=\"Not completed\"",
                "text=\"Add\"",
                "[${tag}_back_to_timer] spoken=\"Back to the timer\"",
            ),
            form().drop(2),
        )
        click("${tag}_log_today")
        assertEquals("[${tag}_minutes_input] field=\"\" error=\"1–1440\"", line("${tag}_minutes_input"))

        // Another day is named above the week; today is not.
        assertFalse(has("${tag}_selected_day"))
        select("2026-10-20")
        assertEquals("[${tag}_selected_day] text=\"Tue, Oct 20\"", line("${tag}_selected_day"))
        assertEquals("[${tag}_remove_day] spoken=\"Remove 40 min for Tue, Oct 20\"", line("${tag}_remove_day"))
        select("2026-10-12")
        assertEquals("[${tag}_week_minutes] text=\"1440 / 90 min\"", line("${tag}_week_minutes"))
        type("1")
        click("${tag}_log_today")
        assertEquals("[${tag}_minutes_input] field=\"1\" error=\"Over 24 h\"", line("${tag}_minutes_input"))

        // No word of Reading's is among them.
        show(piano)
        listOf("reading", "Reading", "read").forEach { word -> assertFalse(word, said().any { word in it }) }
    }

    @Test
    @Config(qualifiers = "de")
    fun aMadeViewsScreenSpeaksGerman() {
        show(piano)
        assertEquals(
            listOf(
                "spoken=\"60 von 90 Minuten diese Woche\"",
                "[${tag}_week_minutes] text=\"60 / 90 min\"",
                "text=\"Timer starten\"",
                "[${tag}_type_minutes] spoken=\"Minuten ohne Timer eintragen\"",
                "[${tag}_remove_day] spoken=\"Eintrag für Mi., 21. Okt. entfernen\" disabled",
            ),
            form(),
        )
        select("2026-10-20")
        assertEquals("[${tag}_selected_day] text=\"Di., 20. Okt.\"", line("${tag}_selected_day"))
        assertEquals("[${tag}_remove_day] spoken=\"40 Minuten für Di., 20. Okt. entfernen\"", line("${tag}_remove_day"))
        assertEquals("text=\"Eintragen\"", form()[form().indexOfFirst { it.startsWith("[${tag}_log_today]") } + 1])
        assertEquals("[${tag}_history_2026-10-20] spoken=\"Di., 20. Okt.: Minuten erfasst. Datum wählen.\" selected click=\"Datum wählen\"", line("${tag}_history_2026-10-20"))
        listOf("Lese", "lese", "Lesen").forEach { word -> assertFalse(word, said().any { word in it }) }
    }

    /** Minutes are typed and added, added to again, and the day removed with two taps; Reading's logs are never touched. */
    @Test
    fun aDaysMinutesAreAddedAndTheDayRemoved() {
        show(piano)
        val readings = logsOf(reading)
        click("${tag}_type_minutes")
        type("30")
        click("${tag}_log_today")
        assertEquals(TimeLog(piano.id, today, 1_000L, 30), logsOf(piano).single { it.dateKey == today })
        assertEquals("[${tag}_week_minutes] text=\"90 / 90 min\"", line("${tag}_week_minutes"))
        assertEquals("[${tag}_log_today] state=\"Completed\" disabled", line("${tag}_log_today"))

        select("2026-10-20")
        type("5")
        click("${tag}_log_today")
        assertEquals(TimeLog(piano.id, "2026-10-20", 1_001L, 45), logsOf(piano).single { it.dateKey == "2026-10-20" })
        assertEquals("[${tag}_remove_day] spoken=\"Remove 45 min for Tue, Oct 20\"", line("${tag}_remove_day"))

        click("${tag}_remove_day")
        assertEquals("[${tag}_remove_day] spoken=\"Confirm: Remove 45 min for Tue, Oct 20\"", line("${tag}_remove_day"))
        assertEquals(4, logsOf(piano).size)
        click("${tag}_remove_day")
        assertEquals(listOf(today, "2026-10-19", "2026-10-12"), logsOf(piano).map { it.dateKey })
        assertEquals("[${tag}_remove_day] spoken=\"Remove the entry for Tue, Oct 20\" disabled", line("${tag}_remove_day"))
        assertEquals("[${tag}_week_minutes] text=\"50 / 90 min\"", line("${tag}_week_minutes"))

        assertEquals(readings, logsOf(reading))
        assertEquals(TimeLog("tim_gone", "2026-10-20", 7, 300), state.timeLogs.single { it.viewId == "tim_gone" })
    }

    /** Start is offered for today alone, and it starts nothing itself: the session is its owner's. */
    @Test
    fun startIsOfferedForTodayAlone() {
        show(piano)
        click("${tag}_start")
        assertEquals(1, starts)
        select("2026-10-20")
        assertFalse(has("${tag}_start"))
        assertTrue(has("${tag}_minutes_input"))
        assertEquals(logged, state)
    }

    /**
     * The bar shows what the week asks: the goal its Sunday has, less a seventh for each day that
     * is sick or an away day the view is not due on, a day that is both counted once. Reading,
     * still due on holiday, is lowered by the sick day alone.
     */
    @Test
    fun theWeeksBarShowsWhatTheWeekAsks() {
        fun day(key: String) = DayRange(LocalDate.parse(key), LocalDate.parse(key))
        // Monday away, Tuesday sick and away: two days off for Piano, one for Reading.
        daysOff = HolidaySchedule(holidays = listOf(DayRange(LocalDate.parse("2026-10-19"), LocalDate.parse("2026-10-20"))), sickDays = listOf(day("2026-10-20")))
        show(piano)
        assertEquals("[${tag}_week_minutes] text=\"60 / 65 min\"", line("${tag}_week_minutes"))
        // The week before was an ordinary one.
        select("2026-10-12")
        assertEquals("[${tag}_week_minutes] text=\"1440 / 90 min\"", line("${tag}_week_minutes"))

        show(reading)
        assertEquals("[time_reading_week_minutes] text=\"25 / 52 min\"", line("time_reading_week_minutes"))

        // A goal raised on a day is the goal of the week it was raised in and of no week before.
        val raised = piano.copy(goal = Dated(listOf(Dated.Revision(90), Dated.Revision(120, LocalDate.parse("2026-10-21")))))
        daysOff = HolidaySchedule()
        show(raised, logged.copy(time = listOf(reading, raised)))
        assertEquals("[${tag}_week_minutes] text=\"60 / 120 min\"", line("${tag}_week_minutes"))
        select("2026-10-12")
        assertEquals("[${tag}_week_minutes] text=\"1440 / 90 min\"", line("${tag}_week_minutes"))
    }

    /** A change is decided against the store as it is: one refused or failed adds nothing and says so, and the screen stays retryable. */
    @Test
    fun aChangeTheStoreDoesNotTakeLeavesTheDayAsItWasAndSaysSo() {
        show(piano)
        select("2026-10-20")
        type("5")

        // The day's log changed elsewhere since the screen showed it: the store refuses, and nothing is written.
        val elsewhere = logged.copy(timeLogs = addTimeMinutes(logged.timeLogs, piano.id, "2026-10-20", 10, 500))
        var asked: TimeDayChange? = null
        answer = { change ->
            asked = change
            elsewhere.changingDay(change, timestampMillis = clock++) { error("A refused change is never stored") }
        }
        click("${tag}_log_today")
        assertEquals(TimeDayChange.Add(piano.id, "2026-10-20", TimeLog(piano.id, "2026-10-20", 9, 40), minutes = 5), asked)
        assertEquals("[${tag}_save_error] text=\"Couldn't save. Try again.\"", line("${tag}_save_error"))
        assertEquals("[${tag}_minutes_input] field=\"5\"", line("${tag}_minutes_input"))
        assertEquals(logged, state)

        answer = { DayChangeResult.Failed }
        click("${tag}_remove_day")
        click("${tag}_remove_day")
        assertEquals("[${tag}_delete_error] text=\"Couldn't remove. Try again.\"", line("${tag}_delete_error"))
        assertEquals(logged, state)

        // Once the store takes it, the same tap stores it.
        answer = null
        click("${tag}_log_today")
        assertEquals(45, logsOf(piano).single { it.dateKey == "2026-10-20" }.minutes)
    }

    /** A deleted view's day is refused by the store, and so is a day whose log is gone. */
    @Test
    fun theStoreRefusesADeletedViewAndADayThatChanged() {
        val add = TimeDayChange.Add(piano.id, "2026-10-20", TimeLog(piano.id, "2026-10-20", 9, 40), minutes = 5)
        val withoutPiano = logged.copy(time = listOf(reading))
        assertEquals(com.example.workouttracker.domain.DayChangeRefusal.Deleted, add.refusalIn(withoutPiano))
        assertEquals(com.example.workouttracker.domain.DayChangeRefusal.ChangedElsewhere, add.copy(opened = null).refusalIn(logged))
        assertEquals(null, add.refusalIn(logged))
        // Reading's day is asked for like any other: taken while its configuration is there, refused without one.
        val readings = TimeDayChange.Add(ReadingTimeId, "2026-10-21", opened = null, minutes = 5)
        assertEquals(null, readings.refusalIn(AppState().withReading()))
        assertEquals(com.example.workouttracker.domain.DayChangeRefusal.Deleted, readings.refusalIn(AppState()))
        assertEquals(listOf(TimeLog(ReadingTimeId, "2026-10-21", 3L, 5)), readings.appliedTo(AppState(), 3L).timeLogs.filter { it.viewId == ReadingTimeId })
        // Minutes a day cannot hold are never applied.
        assertTrue(runCatching { add.copy(dateKey = "2026-10-12", opened = TimeLog(piano.id, "2026-10-12", 0, 1440)).appliedTo(logged, 1L) }.isFailure)
        val removed = TimeDayChange.Remove(piano.id, "2026-10-20", TimeLog(piano.id, "2026-10-20", 9, 40)).appliedTo(logged, 1L)
        assertEquals(logged.timeLogs - TimeLog(piano.id, "2026-10-20", 9, 40), removed.timeLogs)
    }

    /** The note a session left is shown on its view's screen in the words of the kind, and an error takes its place. */
    @Test
    fun theLastSessionsNoteIsSaidInTheKindsWords() {
        note = TimeSessionNote(piano.id, today, minutes = 12, leftApp = true)
        show(piano)
        assertEquals("[${tag}_session_note] text=\"The timer ended when you left the app · 12 min saved\"", line("${tag}_session_note"))
        note = TimeSessionNote(piano.id, today, minutes = 0, leftApp = true)
        compose.waitForIdle()
        assertEquals("[${tag}_session_note] text=\"The timer ended when you left the app · under a minute, nothing saved\"", line("${tag}_session_note"))
        note = TimeSessionNote(piano.id, today, minutes = 0, leftApp = false)
        compose.waitForIdle()
        assertEquals("[${tag}_session_note] text=\"Under a minute · nothing saved\"", line("${tag}_session_note"))

        // A timer only shows its own session note.
        show(reading)
        assertFalse(has("time_reading_session_note"))
        note = TimeSessionNote(ReadingTimeId, today, minutes = 12, leftApp = true)
        compose.waitForIdle()
        assertEquals("[time_reading_session_note] text=\"The timer ended when you left the app · 12 min saved\"", line("time_reading_session_note"))
    }

    /** Each view reads its own logs and keeps what is typed for itself: Reading beside Piano shows Reading's week and an empty field. */
    @Test
    fun eachViewReadsItsOwnLogsAndKeepsItsOwnTypedMinutes() {
        show(piano)
        select("2026-10-19")
        type("15")
        assertEquals("[${tag}_remove_day] spoken=\"Remove 20 min for Mon, Oct 19\"", line("${tag}_remove_day"))

        shown = reading
        compose.waitForIdle()
        assertEquals("2026-10-21", selectedDay("time_reading"))
        assertEquals("[time_reading_week_minutes] text=\"25 / 60 min\"", line("time_reading_week_minutes"))
        select("2026-10-19", prefix = "time_reading")
        assertEquals("[time_reading_minutes_input] field=\"\"", line("time_reading_minutes_input"))
        // Reading's old mark holds no minutes, and its removal claims none.
        assertEquals("[time_reading_remove_day] spoken=\"Remove the entry for Mon, Oct 19\"", line("time_reading_remove_day"))
        assertEquals("[time_reading_history_2026-10-19] spoken=\"Mon, Oct 19: minutes recorded. Select date.\" selected click=\"Select date\"", line("time_reading_history_2026-10-19"))
        type("10", prefix = "time_reading")
        click("time_reading_log_today")
        assertEquals(TimeLog(ReadingTimeId, "2026-10-19", 1_000L, 10), logsOf(reading).single { it.dateKey == "2026-10-19" })
        assertEquals(logged.timeLogs.filter { it.viewId != ReadingTimeId }, state.timeLogs.filter { it.viewId != ReadingTimeId })
    }

    /** The selected day and what is typed for it survive process death, for a made view as for Reading. */
    @Test
    fun theSelectedDayAndWhatIsTypedSurviveProcessDeath() {
        show(piano)
        select("2026-10-18")
        type("35")

        restore()
        assertEquals("2026-10-18", selectedDay())
        assertEquals("[${tag}_minutes_input] field=\"35\"", line("${tag}_minutes_input"))
        click("${tag}_log_today")
        assertEquals(TimeLog(piano.id, "2026-10-18", 1_000L, 35), logsOf(piano).single { it.dateKey == "2026-10-18" })

        // Today's detour into typed minutes comes back as it was left.
        select(today)
        click("${tag}_type_minutes")
        type("7")
        restore()
        assertEquals("2026-10-21", selectedDay())
        assertEquals("[${tag}_minutes_input] field=\"7\"", line("${tag}_minutes_input"))
        assertTrue(has("${tag}_back_to_timer"))

        show(reading)
        select("2026-10-17", prefix = "time_reading")
        type("12", prefix = "time_reading")
        restore()
        assertEquals("2026-10-17", selectedDay("time_reading"))
        assertEquals("[time_reading_minutes_input] field=\"12\"", line("time_reading_minutes_input"))
    }

    /**
     * Only an untouched screen moves on when the day changes, while it is open and when it is
     * brought back on a later day. Minutes typed and not added belong to the day they were typed
     * for, so the screen stays with them, and a day chosen on purpose stays chosen.
     */
    @Test
    fun anUntouchedScreenFollowsTheDayAndTypedMinutesStayWithTheirs() {
        show(piano)
        todayKey = "2026-10-22"
        compose.waitForIdle()
        assertEquals("2026-10-22", selectedDay())
        assertTrue(has("${tag}_start"))

        click("${tag}_type_minutes")
        type("20")
        todayKey = "2026-10-23"
        compose.waitForIdle()
        assertEquals("2026-10-22", selectedDay())
        assertEquals("[${tag}_minutes_input] field=\"20\"", line("${tag}_minutes_input"))
        // They are added to the day they were typed for.
        click("${tag}_log_today")
        assertEquals(TimeLog(piano.id, "2026-10-22", 1_000L, 20), logsOf(piano).single { it.dateKey == "2026-10-22" })
    }

    @Test
    fun anUntouchedScreenBroughtBackOnALaterDayMovesOnAndAStartedOneStays() {
        // Not snapshot state: the day changes while nothing is composed, as it does for a process that died.
        var dayAtStart = today
        show(piano, day = { dayAtStart })
        assertEquals("2026-10-21", selectedDay())

        dayAtStart = "2026-10-23"
        restore()
        assertEquals("2026-10-23", selectedDay())

        click("${tag}_type_minutes")
        type("9")
        dayAtStart = "2026-10-24"
        restore()
        assertEquals("2026-10-23", selectedDay())
        assertEquals("[${tag}_minutes_input] field=\"9\"", line("${tag}_minutes_input"))
    }
}

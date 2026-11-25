package de.nicolasmahn.momentum.reminder

import de.nicolasmahn.momentum.R
import de.nicolasmahn.momentum.core.text.UiText
import de.nicolasmahn.momentum.core.text.pluralText
import de.nicolasmahn.momentum.domain.AppState
import de.nicolasmahn.momentum.domain.LoggedView
import de.nicolasmahn.momentum.domain.checklist.MoMotif
import de.nicolasmahn.momentum.domain.checklist.Dated
import de.nicolasmahn.momentum.domain.checklist.WhenAway
import de.nicolasmahn.momentum.domain.holiday.DayRange
import de.nicolasmahn.momentum.domain.holiday.HolidaySchedule
import de.nicolasmahn.momentum.domain.measurement.testMadeMeasurement
import de.nicolasmahn.momentum.domain.measurement.withWeight
import de.nicolasmahn.momentum.domain.questions.testMadeQuestions
import de.nicolasmahn.momentum.domain.questions.withEating
import de.nicolasmahn.momentum.domain.ref
import de.nicolasmahn.momentum.domain.reminder.ReminderSettings
import de.nicolasmahn.momentum.domain.reminder.ReminderTime
import de.nicolasmahn.momentum.domain.settings.FeatureVisibilitySettings
import de.nicolasmahn.momentum.domain.time.TimeConfiguration
import de.nicolasmahn.momentum.domain.time.TimeEdit
import de.nicolasmahn.momentum.domain.time.TimeLog
import de.nicolasmahn.momentum.domain.time.TimeLogOrder
import de.nicolasmahn.momentum.domain.time.applyEdit
import de.nicolasmahn.momentum.domain.time.minutesToGo
import de.nicolasmahn.momentum.domain.time.testReadingTime
import de.nicolasmahn.momentum.domain.time.testMadeTime
import de.nicolasmahn.momentum.domain.time.withReading
import de.nicolasmahn.momentum.domain.activity.withActivity
import de.nicolasmahn.momentum.persistence.TrackerRepository
import de.nicolasmahn.momentum.persistence.TrackerUpdateResult
import java.time.LocalDate
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A time view a person made reminds through the machinery a made measurement reminds through, by
 * the same code: its slot is in a namespace of its own, with an alarm, a notification and the
 * bookkeeping of every slot. What it has of its own is what it asks for: it speaks while the week
 * is short of its goal and the day holds nothing of it, or on Sunday while the week is short, and
 * says the minutes still to go; minutes saved for the day it rang on take its notification down,
 * whatever the week still asks for.
 */
class TimeRemindersTest {
    private val wednesday = LocalDate.parse("2026-10-14")
    private val monday = wednesday.minusDays(2)
    private val piano = testMadeTime().reminding()
    private val key = "time/tim_k2p9/day"

    private fun TimeConfiguration.reminding() = copy(checkpoint = checkpoint.copy(remind = true))

    private fun state(vararg views: TimeConfiguration, logs: List<TimeLog> = emptyList()) =
        AppState(timeLogs = logs.sortedWith(TimeLogOrder)).withWeight().withEating().withReading().withActivity().let { it.copy(time = it.time + views) }

    private fun minutes(view: TimeConfiguration, day: LocalDate, logged: Int) = TimeLog(view.id, day.toString(), 1L, logged)

    @Test
    fun aRenamedReadingPresetUsesItsSavedLabelAndGenericRemainingMinutes() {
        val renamed = testReadingTime().copy(label = "Evening practice")
        val stored = AppState(time = listOf(renamed)).time.single()
        val expected = MoReminderMessage(
            title = UiText.Verbatim("Evening practice"),
            text = pluralText(R.plurals.time_reminder_week_remaining, 25),
        )
        val days = ReminderDays(wednesday, wednesday)
        assertEquals(expected, loggedReminderMessage(stored, days, minutesToGo = 25))
        assertEquals(expected, loggedReminderMessage(stored.copy(mo = stored.mo.copy(motif = null)), days, minutesToGo = 25))
    }

    @Test
    fun aTimerNotificationUsesMoOnlyWhenItsConfigurationHasAMotif() {
        assertFalse(testReadingTime().copy(mo = testReadingTime().mo.copy(motif = null)).speaksWithMo)
        assertTrue(piano.copy(mo = piano.mo.copy(motif = MoMotif.Book)).speaksWithMo)
        assertFalse(piano.copy(mo = piano.mo.copy(motif = null)).speaksWithMo)
        assertTrue(testReadingTime().speaksWithMo)
    }

    @Test fun aViewShownAndRemindingHasItsSlotInANamespaceOfItsOwn() {
        val slot = ReminderSlot(ReminderViewKind.Time, "tim_k2p9", "day", requireNotNull(piano.checkpoint).at)
        assertEquals(key, slot.key)
        assertEquals(listOf(slot), loggedReminderSlots(state(piano), FeatureVisibilitySettings()))
        // Switched off or hidden it has none, and Reading's reminder is never one of these.
        assertEquals(emptyList<ReminderSlot>(), loggedReminderSlots(state(testMadeTime()), FeatureVisibilitySettings()))
        assertEquals(emptyList<ReminderSlot>(), loggedReminderSlots(state(piano), FeatureVisibilitySettings().hiding(piano.ref)))
        assertEquals(emptyList<ReminderSlot>(), loggedReminderSlots(state(), FeatureVisibilitySettings()))

        // A measurement, a questions view and a time view under one ID are three keys, each in its kind's namespace.
        val sameMeasurement = testMadeMeasurement(id = "tim_k2p9", label = "Mood", unit = "", step = "1").let { it.copy(checkpoint = it.checkpoint.copy(remind = true)) }
        val all = state(piano).let { it.copy(measurements = it.measurements + sameMeasurement) }
        assertEquals(listOf("measurement/tim_k2p9/day", "time/tim_k2p9/day"), reminderSlots(all, FeatureVisibilitySettings(), wednesday).map { it.key })
        // Hiding the measurement of that ID hides no time view.
        assertEquals(listOf(slot), loggedReminderSlots(all, FeatureVisibilitySettings(hiddenMeasurements = setOf(piano.id))))
        assertEquals("time/same", slotKey(ReminderViewKind.Checklist, "time", "same"))
    }

    /** It speaks while the week is short of its goal, saying the minutes still to go, and always sets tomorrow. */
    @Test fun itSpeaksWhileTheWeekIsShortOfItsGoalAndSaysTheMinutesToGo() {
        val fixture = Fixture(state(piano, logs = listOf(minutes(piano, monday, 15), minutes(piano, monday.minusDays(1), 300))))
        assertTrue(fixture.coordinator.onReminderDue(ReminderViewKind.Time, "tim_k2p9", "day"))
        assertEquals(listOf(Said("Piano", 45, key, ReminderDays(wednesday, wednesday), quietly = false)), fixture.said)
        assertEquals(mapOf(key to ReminderDays(wednesday, wednesday)), fixture.runtime.posted())
        assertEquals(wednesday.plusDays(1), fixture.gateway.earliest.last())

        // The goal reached, it is quiet and still sets tomorrow.
        fixture.state = state(piano, logs = listOf(minutes(piano, monday, 60)))
        assertTrue(fixture.coordinator.onReminderDue(ReminderViewKind.Time, "tim_k2p9", "day"))
        assertEquals(1, fixture.said.size)
        assertEquals(wednesday.plusDays(1), fixture.gateway.earliest.last())
        // Another view's minutes, Reading's among them, reach nothing for it.
        fixture.state = state(piano, logs = listOf(TimeLog("reading", monday.toString(), 1L, 60), TimeLog("tim_m7xd", monday.toString(), 1L, 60)))
        fixture.coordinator.onReminderDue(ReminderViewKind.Time, "tim_k2p9", "day")
        assertEquals(Said("Piano", 60, key, ReminderDays(wednesday, wednesday), quietly = false), fixture.said.last())
    }

    /**
     * A day that holds a record of it is done, as its Home row shows, so it is quiet though the week
     * is still short. On Sunday, the day the week is judged, it still asks while the week is short.
     */
    @Test fun itIsQuietOnADayWithARecordButOnSundayAsksWhileTheWeekIsShort() {
        val readToday = Fixture(state(piano, logs = listOf(minutes(piano, monday, 15), minutes(piano, wednesday, 5))))
        assertTrue(readToday.coordinator.onReminderDue(ReminderViewKind.Time, "tim_k2p9", "day"))
        assertEquals(emptyList<Said>(), readToday.said)
        assertEquals(wednesday.plusDays(1), readToday.gateway.earliest.last())

        val sunday = monday.plusDays(6)
        val readSunday = Fixture(state(piano, logs = listOf(minutes(piano, monday, 15), minutes(piano, sunday, 5))), todayKey = sunday.toString())
        readSunday.coordinator.onReminderDue(ReminderViewKind.Time, "tim_k2p9", "day")
        assertEquals(listOf(Said("Piano", 40, key, ReminderDays(sunday, sunday), quietly = false)), readSunday.said)
    }

    /** Tokens set aside for the week stop its asking, though the week is still short; the alarm is still set for tomorrow. */
    @Test fun aWeekWithItsTokensSetAsideStaysQuiet() {
        val ref = de.nicolasmahn.momentum.domain.TrackerRef(de.nicolasmahn.momentum.domain.TrackerTypeId("time"), "tim_k2p9")
        val fixture = Fixture(state(piano, logs = listOf(minutes(piano, monday, 15))), quietedByChoice = QuietedByChoice { asked, checkpoint -> asked == ref && checkpoint == null })
        assertTrue(fixture.coordinator.onReminderDue(ReminderViewKind.Time, "tim_k2p9", "day"))
        assertTrue(fixture.said.isEmpty())
        assertEquals(wednesday.plusDays(1), fixture.gateway.earliest.last())
    }

    /** A reminder already on show when the tokens are set aside in the app is taken down: it would ask for what is settled. */
    @Test fun aNotificationOnShowGoesOnceTokensAreSetAside() {
        var setAside = false
        val fixture = Fixture(state(piano, logs = listOf(minutes(piano, monday, 15))), quietedByChoice = QuietedByChoice { _, _ -> setAside })
        fixture.coordinator.onReminderDue(ReminderViewKind.Time, "tim_k2p9", "day")
        assertEquals(1, fixture.said.size)

        setAside = true
        fixture.coordinator.refreshPosted()
        assertEquals(listOf(key), fixture.gateway.dismissed)
        assertEquals(emptyMap<String, ReminderDays>(), fixture.runtime.posted())
    }

    @Test fun aViewHiddenGoneOrSwitchedOffNeverSpeaks() {
        val hidden = Fixture(state(piano), FeatureVisibilitySettings().hiding(piano.ref))
        val gone = Fixture(state())
        val off = Fixture(state(testMadeTime()))
        // A measurement or a questions view of its ID rings nothing of it, and Reading has no slot to ring.
        val otherKind = Fixture(state(piano))
        otherKind.coordinator.onReminderDue(ReminderViewKind.Measurement, "tim_k2p9", "day")
        otherKind.coordinator.onReminderDue(ReminderViewKind.Questions, "tim_k2p9", "day")
        otherKind.coordinator.onReminderDue(ReminderViewKind.Time, "reading", "day")
        listOf(hidden, gone, off).forEach { it.coordinator.onReminderDue(ReminderViewKind.Time, "tim_k2p9", "day") }

        listOf(hidden, gone, off, otherKind).forEach { fixture ->
            assertEquals(emptyList<Said>(), fixture.said)
            assertEquals(emptyMap<String, ReminderDays>(), fixture.runtime.posted())
        }
    }

    /**
     * On an away day it is not due on it is quiet; still due, and on a sick day, also one that is
     * away too, it comes. The minutes it names are what the week asks less for its days off: a
     * seventh less for a sick day and for an away day it is not due on.
     */
    @Test fun anAwayDayItIsNotDueOnIsQuietAndTheMinutesFollowTheDaysOff() {
        val seventy = testMadeTime(goal = 70).reminding()
        val notDue = seventy.copy(whenAway = Dated.always(WhenAway.Excused))
        val awayToday = HolidaySchedule(listOf(DayRange(wednesday, wednesday)))
        val awayMonday = HolidaySchedule(listOf(DayRange(monday, monday)))
        fun said(view: TimeConfiguration, daysOff: HolidaySchedule) = Fixture(state(view), awayDays = daysOff).also { it.coordinator.onReminderDue(ReminderViewKind.Time, "tim_k2p9", "day") }.said.map { it.minutesToGo }

        assertEquals(emptyList<Int?>(), said(notDue, awayToday))
        assertEquals(listOf<Int?>(70), said(seventy, awayToday))
        // Away on Monday and not due then, today asks as ever, for a seventh less.
        assertEquals(listOf<Int?>(60), said(notDue, awayMonday))
        assertEquals(listOf<Int?>(70), said(seventy, awayMonday))
        // Sick wins over away: it still comes, and the day lowers the week once.
        assertEquals(listOf<Int?>(60), said(notDue, awayToday.withSickDays(DayRange(wednesday, wednesday))))
    }

    /**
     * Minutes saved for the day it rang on take its notification down, although the week is still
     * short, as any minutes read today take Reading's down. Minutes for another day, another view's
     * and a measurement's of the same ID leave it.
     */
    @Test fun minutesSavedForTheDayItRangOnTakeItsNotificationDown() {
        val guitar = testMadeTime(id = "tim_m7xd", label = "Guitar").reminding()
        val twin = testMadeMeasurement(id = piano.id, label = "Twin").let { it.copy(checkpoint = it.checkpoint.copy(remind = true)) }
        val state = state(piano, guitar).let { it.copy(measurements = it.measurements + twin) }
        fun AppState.adding(view: TimeConfiguration, day: LocalDate, logged: Int) =
            copy(timeLogs = (timeLogs.filterNot { it.viewId == view.id && it.dateKey == day.toString() } + minutes(view, day, logged)).sortedWith(TimeLogOrder))
        val fixture = Fixture(state)
        fixture.coordinator.onReminderDue(ReminderViewKind.Time, piano.id, "day")
        fixture.coordinator.onReminderDue(ReminderViewKind.Time, guitar.id, "day")
        fixture.coordinator.onReminderDue(ReminderViewKind.Measurement, twin.id, "day")
        val onShow = mapOf(
            key to ReminderDays(wednesday, wednesday),
            "time/tim_m7xd/day" to ReminderDays(wednesday, wednesday),
            "measurement/tim_k2p9/day" to ReminderDays(wednesday, wednesday),
        )
        assertEquals(onShow, fixture.runtime.posted())

        // Minutes for Monday are not minutes for the day it rang on.
        val mondays = state.adding(piano, monday, 10)
        fixture.state = mondays
        fixture.coordinator.onDayLogged(state, mondays, piano.ref, monday.toString())
        assertEquals(onShow, fixture.runtime.posted())
        assertEquals(emptyList<String>(), fixture.gateway.dismissed)

        // Ten minutes today, forty still to go this week: it goes, and no other does.
        val saved = mondays.adding(piano, wednesday, 10)
        fixture.state = saved
        fixture.coordinator.onDayLogged(mondays, saved, piano.ref, wednesday.toString())
        assertEquals(listOf(key), fixture.gateway.dismissed)
        assertEquals(onShow - key, fixture.runtime.posted())
        assertEquals(40, saved.minutesToGo(piano, wednesday, HolidaySchedule()))
        assertEquals(3, fixture.said.size)
    }

    /**
     * After a settings change a notification on show is said again, quietly, with the name the view
     * has now and the minutes its week still asks for now. It goes once the week asks nothing any
     * more: the goal lowered to what is logged, the view deleted, or the day no longer due.
     */
    @Test fun aNotificationOnShowFollowsWhatIsStoredAndGoesOnceItAsksNothing() {
        val logs = listOf(minutes(piano, monday, 20))
        val fixture = Fixture(state(piano, logs = logs))
        fixture.coordinator.onReminderDue(ReminderViewKind.Time, "tim_k2p9", "day")

        fixture.state = state(piano.applyEdit(TimeEdit.Rename("Keys"), wednesday).applyEdit(TimeEdit.SetGoal(90), wednesday), logs = logs)
        fixture.coordinator.refreshPosted()
        assertEquals(Said("Keys", 70, key, ReminderDays(wednesday, wednesday), quietly = true), fixture.said.last())
        assertEquals(emptyList<String>(), fixture.gateway.dismissed)

        fixture.state = state(piano.applyEdit(TimeEdit.SetGoal(20), wednesday), logs = logs)
        fixture.coordinator.refreshPosted()
        assertEquals(listOf(key), fixture.gateway.dismissed)
        assertEquals(emptyMap<String, ReminderDays>(), fixture.runtime.posted())

        val gone = Fixture(state(piano))
        gone.coordinator.onReminderDue(ReminderViewKind.Time, "tim_k2p9", "day")
        gone.state = state()
        gone.coordinator.refreshPosted()
        assertEquals(listOf(key), gone.gateway.dismissed)

        val noLongerDue = Fixture(state(piano), awayDays = HolidaySchedule(listOf(DayRange(wednesday, wednesday))))
        noLongerDue.coordinator.onReminderDue(ReminderViewKind.Time, "tim_k2p9", "day")
        noLongerDue.state = state(piano.copy(whenAway = Dated.always(WhenAway.Excused)))
        noLongerDue.coordinator.refreshPosted()
        assertEquals(listOf(key), noLongerDue.gateway.dismissed)
    }

    /**
     * The words name the view and the minutes its week still asks for, and the preview and the test
     * say what the reminder says with all the week asks still to go, as if nothing were logged,
     * with the switch off too. There is nothing to send on an away day it is not due on, nor in a
     * week that asks nothing.
     */
    @Test fun theWordsNameTheMinutesToGoAndThePreviewSaysTheWholeWeek() {
        val fortyToGo = MoReminderMessage(title = UiText.Verbatim("Piano"), text = pluralText(R.plurals.time_reminder_week_remaining, 40))
        assertEquals(fortyToGo, loggedReminderMessage(piano, ReminderDays(wednesday, wednesday), minutesToGo = 40))
        assertTrue(runCatching { loggedReminderMessage(piano, ReminderDays(wednesday, wednesday)) }.isFailure)

        val off = testMadeTime(goal = 70)
        val test = requireNotNull(loggedTestReminder(off, wednesday, HolidaySchedule()))
        assertEquals(TestReminder.Logged(off, ReminderDays(wednesday, wednesday), opens = false, minutesToGo = 70), test)
        assertEquals(MoReminderMessage(title = UiText.Verbatim("Piano"), text = pluralText(R.plurals.time_reminder_week_remaining, 70)), test.words(mood = null))
        assertEquals(test.words(mood = null), test.opening(true).words(mood = null))

        val notDue = off.copy(whenAway = Dated.always(WhenAway.Excused))
        val awayToday = HolidaySchedule(listOf(DayRange(wednesday, wednesday)))
        assertNull(loggedTestReminder(notDue, wednesday, awayToday))
        assertEquals(70, loggedTestReminder(off, wednesday, awayToday)?.minutesToGo)
        // Away on Monday alone, the week asks a seventh less; away all week, it asks nothing.
        assertEquals(60, loggedTestReminder(notDue, wednesday, HolidaySchedule(listOf(DayRange(monday, monday))))?.minutesToGo)
        assertNull(loggedTestReminder(notDue, wednesday, HolidaySchedule().withSickDays(DayRange(monday, monday.plusDays(6)))))
        // The other kinds' name no number.
        assertNull(loggedTestReminder(testMadeMeasurement(), wednesday, HolidaySchedule())?.minutesToGo)
        assertNull(loggedTestReminder(testMadeQuestions(madeOn = monday), wednesday, HolidaySchedule())?.minutesToGo)
    }

    /** What a view's notification was posted with. */
    private data class Said(val label: String, val minutesToGo: Int?, val key: String, val days: ReminderDays, val quietly: Boolean)

    private inner class Fixture(
        var state: AppState,
        var visibility: FeatureVisibilitySettings = FeatureVisibilitySettings(),
        awayDays: HolidaySchedule = HolidaySchedule(),
        todayKey: String = wednesday.toString(),
        quietedByChoice: QuietedByChoice = QuietedByChoice { _, _ -> false },
    ) {
        val runtime = MemoryReminderRuntime()
        val gateway = RecordingReminderGateway(runtime)
        val said = mutableListOf<Said>()
        val coordinator = ChecklistReminderCoordinator(
            trackerRepository = object : TrackerRepository {
                override fun load() = state
                override fun save(state: AppState) = error("read only")
                override fun update(transform: (AppState) -> AppState): TrackerUpdateResult = error("read only")
            },
            featureVisibility = FeatureVisibilitySource { visibility },
            gateway = gateway,
            runtime = runtime,
            notifier = { _, _, _, _, _, _ -> error("No checklist reminds here.") },
            loggedNotifier = object : LoggedReminderNotifier {
                override fun show(view: LoggedView, key: String, days: ReminderDays, quietly: Boolean): Boolean = show(view, null, key, days, quietly)

                override fun show(view: LoggedView, minutesToGo: Int?, key: String, days: ReminderDays, quietly: Boolean): Boolean {
                    said += Said(view.label, minutesToGo, key, days, quietly)
                    return true
                }
            },
            reminderSettings = { ReminderSettings(fallbackAlarmTime = ReminderTime(6, 45)) },
            todayKey = { todayKey },
            awayDays = { awayDays },
            quietedByChoice = quietedByChoice,
        )
    }
}

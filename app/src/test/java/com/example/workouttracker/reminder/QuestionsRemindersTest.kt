package com.example.workouttracker.reminder

import com.example.workouttracker.R
import com.example.workouttracker.core.text.UiText
import com.example.workouttracker.core.text.uiText
import com.example.workouttracker.domain.AppState
import com.example.workouttracker.domain.checklist.AboutDay
import com.example.workouttracker.domain.checklist.ChecklistConfiguration
import com.example.workouttracker.domain.checklist.ChecklistItem
import com.example.workouttracker.domain.checklist.Checkpoint
import com.example.workouttracker.domain.checklist.CheckpointTime
import com.example.workouttracker.domain.checklist.Dated
import com.example.workouttracker.domain.checklist.ViewHue
import com.example.workouttracker.domain.checklist.ViewIcon
import com.example.workouttracker.domain.checklist.WhenAway
import com.example.workouttracker.domain.holiday.DayRange
import com.example.workouttracker.domain.holiday.HolidaySchedule
import com.example.workouttracker.domain.measurement.testMadeMeasurement
import com.example.workouttracker.domain.measurement.withWeight
import com.example.workouttracker.domain.questions.QuestionEntry
import com.example.workouttracker.domain.questions.QuestionEntryOrder
import com.example.workouttracker.domain.questions.QuestionsConfiguration
import com.example.workouttracker.domain.questions.QuestionsEdit
import com.example.workouttracker.domain.questions.applyEdit
import com.example.workouttracker.domain.questions.testMadeQuestions
import com.example.workouttracker.domain.questions.withEating
import com.example.workouttracker.domain.ref
import com.example.workouttracker.domain.reminder.ReminderSettings
import com.example.workouttracker.domain.reminder.ReminderTime
import com.example.workouttracker.domain.settings.FeatureVisibilitySettings
import com.example.workouttracker.persistence.TrackerRepository
import com.example.workouttracker.persistence.TrackerUpdateResult
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import com.example.workouttracker.domain.time.withReading
import com.example.workouttracker.domain.activity.withActivity

/**
 * A questions view a person made reminds through the machinery a made measurement reminds through,
 * by the same code: its slot is in a namespace of its own, and it speaks while the day today asks
 * about has no entry. What it has of its own is that day, today or yesterday, and none on the day
 * a view about yesterday was made.
 */
class QuestionsRemindersTest {
    private val today = LocalDate.parse("2026-10-14")
    private val yesterday = today.minusDays(1)
    private val nine = CheckpointTime.Fixed(LocalTime.of(9, 0))
    private val sleep = testMadeQuestions(madeOn = today.minusDays(30)).reminding()
    private val lookBack = testMadeQuestions(about = AboutDay.Yesterday, madeOn = today.minusDays(30)).reminding()
    private val key = "questions/qst_k2p9/day"

    /** A checklist, a measurement and a questions view under one ID, each with a checkpoint called `day`. */
    private val sameChecklist = ChecklistConfiguration(
        id = "qst_same",
        label = "Plants",
        icon = ViewIcon.Plant,
        hue = ViewHue.Forest,
        checkpoints = listOf(Checkpoint("day", CheckpointTime.Fixed(LocalTime.of(8, 0)), remind = true)),
        items = listOf(ChecklistItem("itm_water", "Water the plants")),
    )
    private val sameMeasurement = testMadeMeasurement(id = "qst_same", label = "Mood", unit = "", step = "1").let { it.copy(checkpoint = it.checkpoint.copy(remind = true)) }
    private val sameQuestions = testMadeQuestions(id = "qst_same", label = "Evening", madeOn = today.minusDays(30)).reminding()

    private fun QuestionsConfiguration.reminding() = copy(checkpoint = checkpoint.copy(remind = true))

    private fun state(vararg views: QuestionsConfiguration, entries: List<QuestionEntry> = emptyList()) =
        AppState(questionEntries = entries.sortedWith(QuestionEntryOrder)).withWeight().withEating().withReading().withActivity().let { it.copy(questions = it.questions + views) }

    private fun answered(view: QuestionsConfiguration, day: LocalDate) = QuestionEntry("${view.id}-$day", view.id, day.toString(), 1L)

    @Test fun aViewShownAndRemindingHasItsSlotInANamespaceOfItsOwn() {
        val slot = ReminderSlot(ReminderViewKind.Questions, "qst_k2p9", "day", nine)
        assertEquals(key, slot.key)
        assertEquals(listOf(slot), loggedReminderSlots(state(sleep), FeatureVisibilitySettings()))
        // Switched off or hidden it has none, and Eating's reminder is never one of these.
        assertEquals(emptyList<ReminderSlot>(), loggedReminderSlots(state(testMadeQuestions()), FeatureVisibilitySettings()))
        assertEquals(emptyList<ReminderSlot>(), loggedReminderSlots(state(sleep), FeatureVisibilitySettings().hiding(sleep.ref)))
        assertEquals(emptyList<ReminderSlot>(), loggedReminderSlots(state(), FeatureVisibilitySettings()))
        // Hiding a measurement or a checklist of that ID hides no questions view.
        assertEquals(listOf(slot), loggedReminderSlots(state(sleep), FeatureVisibilitySettings(hiddenChecklists = setOf(sleep.id), hiddenMeasurements = setOf(sleep.id))))

        // The three kinds under one ID are three keys, the checklist's and the measurement's the ones they had.
        assertEquals(
            listOf("qst_same/day", "measurement/qst_same/day", "questions/qst_same/day"),
            reminderSlots(state(sameQuestions).let { it.copy(checklists = listOf(sameChecklist), measurements = it.measurements + sameMeasurement) }, FeatureVisibilitySettings(), today).map { it.key },
        )
        assertEquals("questions/same", slotKey(ReminderViewKind.Checklist, "questions", "same"))
    }

    @Test fun reconcilingSetsEveryKindAndAnAlarmRingsTheViewOfItsKindAlone() {
        val all = state(sameQuestions).let { it.copy(checklists = listOf(sameChecklist), measurements = it.measurements + sameMeasurement) }
        val fixture = Fixture(all)
        assertTrue(fixture.coordinator.reconcile())
        assertEquals(setOf("qst_same/day", "measurement/qst_same/day", "questions/qst_same/day"), fixture.runtime.keys)

        fixture.coordinator.onReminderDue(ReminderViewKind.Questions, "qst_same", "day")
        assertEquals(listOf("Evening"), fixture.said.map { it.label })
        assertEquals(emptyList<String>(), fixture.checklistKeys)
        fixture.coordinator.onReminderDue(ReminderViewKind.Measurement, "qst_same", "day")
        fixture.coordinator.onReminderDue(ReminderViewKind.Checklist, "qst_same", "day")
        assertEquals(listOf("Evening", "Mood"), fixture.said.map { it.label })
        assertEquals(setOf("qst_same/day", "measurement/qst_same/day", "questions/qst_same/day"), fixture.runtime.posted().keys)

        // The questions view is deleted: its alarm and its notification go, and the others keep theirs.
        fixture.state = all.copy(questions = all.questions.filterNot { it.id == "qst_same" })
        assertTrue(fixture.coordinator.reconcile())
        assertEquals(listOf("questions/qst_same/day"), fixture.gateway.cancelled)
        assertEquals(setOf("qst_same/day", "measurement/qst_same/day"), fixture.runtime.keys)
    }

    /** About today it asks about today: it speaks until today has an entry, whatever the entry says, and always sets tomorrow. */
    @Test fun aboutTodayItSpeaksWhileTodayHasNoEntry() {
        val fixture = Fixture(state(sleep, entries = listOf(answered(sleep, yesterday), QuestionEntry("eating", "eating", today.toString(), 1L))))
        assertTrue(fixture.coordinator.onReminderDue(ReminderViewKind.Questions, "qst_k2p9", "day"))
        assertEquals(listOf(Said("Sleep", key, ReminderDays(today, today), quietly = false)), fixture.said)
        assertEquals(mapOf(key to ReminderDays(today, today)), fixture.runtime.posted())
        assertEquals(today.plusDays(1), fixture.gateway.earliest.last())

        fixture.state = state(sleep, entries = listOf(answered(sleep, today)))
        assertTrue(fixture.coordinator.onReminderDue(ReminderViewKind.Questions, "qst_k2p9", "day"))
        assertEquals(1, fixture.said.size)
        assertEquals(today.plusDays(1), fixture.gateway.earliest.last())
    }

    /** About yesterday it asks about yesterday: today's entry answers nothing, yesterday's does. */
    @Test fun aboutYesterdayItSpeaksWhileYesterdayHasNoEntry() {
        val fixture = Fixture(state(lookBack, entries = listOf(answered(lookBack, today))))
        fixture.coordinator.onReminderDue(ReminderViewKind.Questions, "qst_k2p9", "day")
        assertEquals(listOf(Said("Sleep", key, ReminderDays(today, yesterday), quietly = false)), fixture.said)
        assertEquals(mapOf(key to ReminderDays(today, yesterday)), fixture.runtime.posted())

        fixture.state = state(lookBack, entries = listOf(answered(lookBack, yesterday)))
        fixture.coordinator.onReminderDue(ReminderViewKind.Questions, "qst_k2p9", "day")
        assertEquals(1, fixture.said.size)
    }

    /** A view about yesterday made today asks about no day today: it is silent, and tomorrow it asks about today. */
    @Test fun aViewAboutYesterdayIsSilentOnTheDayItWasMade() {
        val madeToday = lookBack.copy(madeOn = today)
        val first = Fixture(state(madeToday))
        assertTrue(first.coordinator.onReminderDue(ReminderViewKind.Questions, "qst_k2p9", "day"))
        assertEquals(emptyList<Said>(), first.said)
        assertEquals(today.plusDays(1), first.gateway.earliest.last())

        val next = Fixture(state(madeToday), todayKey = today.plusDays(1).toString())
        next.coordinator.onReminderDue(ReminderViewKind.Questions, "qst_k2p9", "day")
        assertEquals(listOf(ReminderDays(today.plusDays(1), today)), next.said.map { it.days })
    }

    @Test fun aViewHiddenGoneOrSwitchedOffNeverSpeaks() {
        val hidden = Fixture(state(sleep), FeatureVisibilitySettings().hiding(sleep.ref))
        val gone = Fixture(state())
        val off = Fixture(state(testMadeQuestions()))
        // A measurement of its ID rings nothing of it, and Eating has no slot to ring.
        val otherKind = Fixture(state(sleep))
        otherKind.coordinator.onReminderDue(ReminderViewKind.Measurement, "qst_k2p9", "day")
        otherKind.coordinator.onReminderDue(ReminderViewKind.Questions, "eating", "day")
        listOf(hidden, gone, off).forEach { it.coordinator.onReminderDue(ReminderViewKind.Questions, "qst_k2p9", "day") }

        listOf(hidden, gone, off, otherKind).forEach { fixture ->
            assertEquals(emptyList<Said>(), fixture.said)
            assertEquals(emptyMap<String, ReminderDays>(), fixture.runtime.posted())
        }
    }

    /**
     * "Not due" is read on the day the reminder asks about. About yesterday that is yesterday: away
     * yesterday and not due, it is silent today; away today alone, it asks as ever. A sick day
     * still reminds.
     */
    @Test fun anAwayDayIsReadOnTheDayAskedAbout() {
        val notDue = lookBack.copy(whenAway = Dated.always(WhenAway.Excused))
        val awayYesterday = HolidaySchedule(listOf(DayRange(yesterday, yesterday)))
        val awayToday = HolidaySchedule(listOf(DayRange(today, today)))
        fun said(view: QuestionsConfiguration, daysOff: HolidaySchedule) = Fixture(state(view), awayDays = daysOff).also { it.coordinator.onReminderDue(ReminderViewKind.Questions, "qst_k2p9", "day") }.said.size

        assertEquals(0, said(notDue, awayYesterday))
        assertEquals(1, said(notDue, awayToday))
        assertEquals(1, said(lookBack, awayYesterday))
        assertEquals(1, said(notDue, awayYesterday.withSickDays(DayRange(yesterday, yesterday))))
        // About today, the day asked about is today, as a measurement's is.
        assertEquals(0, said(sleep.copy(whenAway = Dated.always(WhenAway.Excused)), awayToday))
        assertEquals(1, said(sleep.copy(whenAway = Dated.always(WhenAway.Excused)), awayYesterday))
    }

    /**
     * A day's answers saved from the view's screen take down the notification of that view about
     * that day. One about another day, another view's and a measurement's of the same ID stay.
     */
    @Test fun savingADayTakesDownTheNotificationAboutThatDayAndNoOther() {
        val mood = testMadeQuestions(id = "qst_m00d", label = "Mood", madeOn = today.minusDays(30)).reminding()
        val twin = testMadeMeasurement(id = lookBack.id, label = "Twin").let { it.copy(checkpoint = it.checkpoint.copy(remind = true)) }
        val state = state(lookBack, mood).let { it.copy(measurements = it.measurements + twin) }
        fun AppState.answering(view: QuestionsConfiguration, day: LocalDate) = copy(questionEntries = (questionEntries + answered(view, day)).sortedWith(QuestionEntryOrder))
        val fixture = Fixture(state)
        fixture.coordinator.onReminderDue(ReminderViewKind.Questions, lookBack.id, "day")
        fixture.coordinator.onReminderDue(ReminderViewKind.Questions, mood.id, "day")
        fixture.coordinator.onReminderDue(ReminderViewKind.Measurement, twin.id, "day")
        val onShow = mapOf(
            key to ReminderDays(today, yesterday),
            "questions/qst_m00d/day" to ReminderDays(today, today),
            "measurement/qst_k2p9/day" to ReminderDays(today, today),
        )
        assertEquals(onShow, fixture.runtime.posted())

        // Today's answers say nothing about yesterday, which is what it asked about.
        fixture.coordinator.onDayLogged(state, state.answering(lookBack, today), lookBack.ref, today.toString())
        assertEquals(onShow, fixture.runtime.posted())

        // Yesterday's do, and touch no other notification; nothing was said again along the way.
        val saved = state.answering(lookBack, yesterday)
        fixture.state = saved
        fixture.coordinator.onDayLogged(state, saved, lookBack.ref, yesterday.toString())
        assertEquals(listOf(key), fixture.gateway.dismissed)
        assertEquals(onShow - key, fixture.runtime.posted())
        assertEquals(3, fixture.said.size)

        // Removing the entry brings nothing back.
        fixture.state = state
        fixture.coordinator.onDayLogged(saved, state, lookBack.ref, yesterday.toString())
        assertEquals(onShow - key, fixture.runtime.posted())
    }

    /**
     * After a settings change a notification on show is said again, quietly, with the name the view
     * has now, about the days it asked about. It goes once that day asks nothing any more: the
     * view deleted, or the day no longer due.
     */
    @Test fun aNotificationOnShowFollowsWhatIsStoredAndGoesOnceItAsksNothing() {
        val fixture = Fixture(state(lookBack))
        fixture.coordinator.onReminderDue(ReminderViewKind.Questions, "qst_k2p9", "day")

        // Renamed and switched to about today: it still asks about the day it asked about.
        fixture.state = state(lookBack.applyEdit(QuestionsEdit.Rename("Nights"), today).applyEdit(QuestionsEdit.SetAbout(AboutDay.Today), today))
        fixture.coordinator.refreshPosted()
        assertEquals(Said("Nights", key, ReminderDays(today, yesterday), quietly = true), fixture.said.last())
        assertEquals(emptyList<String>(), fixture.gateway.dismissed)

        fixture.state = state()
        fixture.coordinator.refreshPosted()
        assertEquals(listOf(key), fixture.gateway.dismissed)
        assertEquals(emptyMap<String, ReminderDays>(), fixture.runtime.posted())

        val noLongerDue = Fixture(state(lookBack), awayDays = HolidaySchedule(listOf(DayRange(yesterday, yesterday))))
        noLongerDue.coordinator.onReminderDue(ReminderViewKind.Questions, "qst_k2p9", "day")
        noLongerDue.state = state(lookBack.copy(whenAway = Dated.always(WhenAway.Excused)))
        noLongerDue.coordinator.refreshPosted()
        assertEquals(listOf(key), noLongerDue.gateway.dismissed)
    }

    /**
     * The words name the view and the day asked about, and the preview and the test say what the
     * reminder says, read from a configuration as if nothing were answered, with the switch off
     * too. There is nothing to send where today asks about no day or about one it is not due on.
     */
    @Test fun theWordsSayTodayOrYesterdayAndThePreviewSaysWhatTheReminderSays() {
        val aboutToday = MoReminderMessage(title = UiText.Verbatim("Sleep"), text = uiText(R.string.questions_reminder_open))
        val aboutYesterday = MoReminderMessage(title = UiText.Verbatim("Sleep"), text = uiText(R.string.questions_reminder_open_yesterday))
        assertEquals(aboutToday, loggedReminderMessage(sleep, ReminderDays(today, today)))
        assertEquals(aboutYesterday, loggedReminderMessage(lookBack, ReminderDays(today, yesterday)))

        val off = testMadeQuestions(madeOn = today.minusDays(30))
        val test = requireNotNull(loggedTestReminder(off, today, HolidaySchedule()))
        assertEquals(TestReminder.Logged(off, ReminderDays(today, today), opens = false), test)
        assertEquals(aboutToday, test.words(mood = null))
        assertEquals(aboutYesterday, requireNotNull(loggedTestReminder(lookBack, today, HolidaySchedule())).opening(true).words(mood = null))

        assertNull(loggedTestReminder(lookBack.copy(madeOn = today), today, HolidaySchedule()))
        val awayYesterday = HolidaySchedule(listOf(DayRange(yesterday, yesterday)))
        assertNull(loggedTestReminder(lookBack.copy(whenAway = Dated.always(WhenAway.Excused)), today, awayYesterday))
        assertEquals(ReminderDays(today, yesterday), loggedTestReminder(lookBack, today, awayYesterday)?.days)
    }

    /** What a view's notification was posted with. */
    private data class Said(val label: String, val key: String, val days: ReminderDays, val quietly: Boolean)

    private inner class Fixture(
        var state: AppState,
        var visibility: FeatureVisibilitySettings = FeatureVisibilitySettings(),
        awayDays: HolidaySchedule = HolidaySchedule(),
        todayKey: String = today.toString(),
    ) {
        val runtime = MemoryReminderRuntime()
        val gateway = RecordingReminderGateway(runtime)
        val said = mutableListOf<Said>()

        /** The keys a checklist's notification was posted under. */
        val checklistKeys = mutableListOf<String>()
        val coordinator = ChecklistReminderCoordinator(
            trackerRepository = object : TrackerRepository {
                override fun load() = state
                override fun save(state: AppState) = error("read only")
                override fun update(transform: (AppState) -> AppState): TrackerUpdateResult = error("read only")
            },
            featureVisibility = FeatureVisibilitySource { visibility },
            gateway = gateway,
            runtime = runtime,
            notifier = { _, _, _, key, _, _ -> checklistKeys += key; true },
            loggedNotifier = { view, key, days, quietly -> said += Said(view.label, key, days, quietly); true },
            reminderSettings = { ReminderSettings(fallbackAlarmTime = ReminderTime(6, 45)) },
            todayKey = { todayKey },
            awayDays = { awayDays },
        )
    }
}

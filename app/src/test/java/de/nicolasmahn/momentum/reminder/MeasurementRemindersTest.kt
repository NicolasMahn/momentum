package de.nicolasmahn.momentum.reminder

import de.nicolasmahn.momentum.domain.LoggedViewKind
import de.nicolasmahn.momentum.domain.LoggedViewRef
import de.nicolasmahn.momentum.domain.ref
import de.nicolasmahn.momentum.R
import de.nicolasmahn.momentum.core.text.UiText
import de.nicolasmahn.momentum.core.text.uiText
import de.nicolasmahn.momentum.domain.AppState
import de.nicolasmahn.momentum.domain.checklist.ChecklistConfiguration
import de.nicolasmahn.momentum.domain.checklist.ChecklistDay
import de.nicolasmahn.momentum.domain.checklist.ChecklistItem
import de.nicolasmahn.momentum.domain.checklist.Checkpoint
import de.nicolasmahn.momentum.domain.checklist.CheckpointTime
import de.nicolasmahn.momentum.domain.checklist.ViewHue
import de.nicolasmahn.momentum.domain.checklist.Dated
import de.nicolasmahn.momentum.domain.checklist.ViewIcon
import de.nicolasmahn.momentum.domain.checklist.WhenAway
import de.nicolasmahn.momentum.domain.holiday.DayRange
import de.nicolasmahn.momentum.domain.holiday.HolidaySchedule
import de.nicolasmahn.momentum.domain.measurement.MeasurementConfiguration
import de.nicolasmahn.momentum.domain.measurement.MeasurementEdit
import de.nicolasmahn.momentum.domain.measurement.MeasurementLog
import de.nicolasmahn.momentum.domain.compatibility.WeightMeasurementId
import de.nicolasmahn.momentum.domain.measurement.applyEdit
import de.nicolasmahn.momentum.domain.isExcusedOn
import de.nicolasmahn.momentum.domain.measurement.testMadeMeasurement
import de.nicolasmahn.momentum.domain.measurement.withWeight
import de.nicolasmahn.momentum.domain.reminder.ReminderSettings
import de.nicolasmahn.momentum.domain.reminder.ReminderTime
import de.nicolasmahn.momentum.domain.settings.FeatureVisibilitySettings
import de.nicolasmahn.momentum.persistence.TrackerRepository
import de.nicolasmahn.momentum.persistence.TrackerUpdateResult
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A measurement a person made reminds through the machinery a checklist's checkpoints remind
 * through. Its slot stands beside a checklist's, under a key no checklist's can be, so the two
 * kinds never set, cancel, answer or take down each other's, whatever their IDs.
 */
class MeasurementRemindersTest {
    private val today = LocalDate.parse("2026-10-14")
    private val nine = CheckpointTime.Fixed(LocalTime.of(9, 0))
    private val waist = testMadeMeasurement().reminding()

    /** A checklist and a measurement under one ID, each with a checkpoint called `day`. */
    private val sameChecklist = ChecklistConfiguration(
        id = "same",
        label = "Plants",
        icon = ViewIcon.Plant,
        hue = ViewHue.Forest,
        checkpoints = listOf(Checkpoint("day", CheckpointTime.Fixed(LocalTime.of(8, 0)), remind = true)),
        items = listOf(ChecklistItem("itm_water", "Water the plants")),
    )
    private val sameMeasurement = testMadeMeasurement(id = "same", label = "Mood", unit = "", step = "1").reminding()

    @Test fun aMeasurementShownAndRemindingHasItsSlotBesideAChecklists() {
        val state = AppState(checklists = listOf(sameChecklist)).withWeight().with(waist)
        val slot = ReminderSlot(ReminderViewKind.Measurement, "msr_k2p9", "day", nine)

        assertEquals(listOf(slot), loggedReminderSlots(state, FeatureVisibilitySettings()))
        assertEquals(
            listOf(ReminderSlot(ReminderViewKind.Checklist, "same", "day", CheckpointTime.Fixed(LocalTime.of(8, 0))), slot),
            reminderSlots(state, FeatureVisibilitySettings(), today),
        )
        // Switched off or hidden it has none, and Weight's reminder is never one of these.
        assertEquals(emptyList<ReminderSlot>(), loggedReminderSlots(AppState().withWeight().with(testMadeMeasurement()), FeatureVisibilitySettings()))
        assertEquals(emptyList<ReminderSlot>(), loggedReminderSlots(state, FeatureVisibilitySettings(hiddenMeasurements = setOf("msr_k2p9"))))
        assertEquals(emptyList<ReminderSlot>(), loggedReminderSlots(AppState().withWeight(), FeatureVisibilitySettings()))
        // Hiding a checklist of that ID hides no measurement.
        assertEquals(listOf(slot), loggedReminderSlots(state, FeatureVisibilitySettings(hiddenChecklists = setOf("msr_k2p9"))))
    }

    /** A checklist's key is what it always was; a measurement's holds a slash more, so no IDs can make the two one key. */
    @Test fun aMeasurementsKeyIsInANamespaceNoChecklistsKeyCanBeIn() {
        assertEquals("same/day", ReminderSlot(ReminderViewKind.Checklist, "same", "day", nine).key)
        assertEquals("same/day", slotKey("same", "day"))
        assertEquals("measurement/same/day", ReminderSlot(ReminderViewKind.Measurement, "same", "day", nine).key)
        // A checklist may be called what the namespace is called: its key still holds one slash.
        assertEquals("measurement/same", slotKey(ReminderViewKind.Checklist, "measurement", "same"))
        assertNotEquals(slotKey(ReminderViewKind.Checklist, "measurement", "same"), slotKey(ReminderViewKind.Measurement, "same", "day"))
    }

    @Test fun reconcilingSetsBothKindsAndCancelsOnlyTheSlotNothingAsksFor() {
        val both = AppState(checklists = listOf(sameChecklist)).withWeight().with(sameMeasurement)
        val fixture = Fixture(both)
        assertTrue(fixture.coordinator.reconcile())
        assertEquals(listOf("same/day", "measurement/same/day"), fixture.gateway.scheduled.map { it.key })
        assertEquals(setOf("same/day", "measurement/same/day"), fixture.runtime.keys)

        // The measurement is deleted: its alarm goes, and the checklist of its ID keeps its own.
        fixture.state = AppState(checklists = listOf(sameChecklist)).withWeight()
        assertTrue(fixture.coordinator.reconcile())
        assertEquals(listOf("measurement/same/day"), fixture.gateway.cancelled)
        assertEquals(setOf("same/day"), fixture.runtime.keys)

        // The checklist is deleted instead: only its alarm goes.
        val other = Fixture(both)
        other.coordinator.reconcile()
        other.state = AppState().withWeight().with(sameMeasurement)
        assertTrue(other.coordinator.reconcile())
        assertEquals(listOf("same/day"), other.gateway.cancelled)
        assertEquals(setOf("measurement/same/day"), other.runtime.keys)
    }

    /** Alarms recorded before a measurement could remind are a checklist's: the first reconcile after keeps them and adds the measurement's. */
    @Test fun aChecklistsAlarmRecordedBeforeThisBlockIsKeptBesideAMeasurements() {
        val fixture = Fixture(AppState(checklists = listOf(sameChecklist)).withWeight().with(sameMeasurement))
        fixture.runtime.keys = setOf("same/day", "chk_gone/cpt_morning")

        assertTrue(fixture.coordinator.reconcile())

        assertEquals(listOf("chk_gone/cpt_morning"), fixture.gateway.cancelled)
        assertEquals(setOf("same/day", "measurement/same/day"), fixture.runtime.keys)
    }

    /**
     * A checklist's save answers its own notification and no measurement's, under the same ID or
     * under an ID that reads like the namespace; a settings change leaves a measurement's on show.
     */
    @Test fun aChecklistsSaveNeverAnswersAMeasurementsNotification() {
        val namedLikeTheNamespace = sameChecklist.copy(id = "measurement", checkpoints = listOf(sameChecklist.checkpoints.single().copy(id = "same")))
        val state = AppState(checklists = listOf(sameChecklist, namedLikeTheNamespace)).withWeight().with(sameMeasurement)
        val fixture = Fixture(state)
        fixture.coordinator.onReminderDue(ReminderViewKind.Checklist, "same", "day")
        fixture.coordinator.onReminderDue(ReminderViewKind.Checklist, "measurement", "same")
        fixture.runtime.posted("measurement/same/day", ReminderDays(today, today))
        fun watered(checklistId: String, checkpointId: String) = state.copy(checklistDays = listOf(ChecklistDay(checklistId, today.toString(), 1L, setOf("$checkpointId/itm_water"))))

        fixture.coordinator.onRecordsSaved(state, watered("same", "day"), "same")
        fixture.coordinator.onRecordsSaved(state, watered("measurement", "same"), "measurement")
        fixture.coordinator.refreshPosted()

        assertEquals(listOf("same/day", "measurement/same"), fixture.gateway.dismissed)
        assertEquals(setOf("measurement/same/day"), fixture.runtime.posted().keys)
    }

    @Test fun itSpeaksWhileTodayIsNotLoggedAndAlwaysSetsTomorrow() {
        val fixture = Fixture(AppState().withWeight().with(waist))
        assertTrue(fixture.coordinator.onReminderDue(ReminderViewKind.Measurement, "msr_k2p9", "day"))
        assertEquals(listOf(Said("Waist", "measurement/msr_k2p9/day", ReminderDays(today, today), quietly = false)), fixture.said)
        assertEquals(mapOf("measurement/msr_k2p9/day" to ReminderDays(today, today)), fixture.runtime.posted())
        assertEquals(today.plusDays(1), fixture.gateway.earliest.last())
        // It never joined the streak: its reminder asks for the day's number either way.
        assertNull(waist.rewards)

        // Another day's record, Weight's of today and another measurement's of today do not answer for it.
        fixture.state = fixture.state.copy(
            measurementLogs = listOf(
                MeasurementLog(waist.id, today.minusDays(1).toString(), 1L, 91.5),
                MeasurementLog(WeightMeasurementId, today.toString(), 2L, 81.4),
                MeasurementLog("msr_other", today.toString(), 3L, 7.0),
            ),
        )
        fixture.coordinator.onReminderDue(ReminderViewKind.Measurement, "msr_k2p9", "day")
        assertEquals(2, fixture.said.size)

        // Logged, whatever the number: it is silent, and tomorrow's alarm is still set.
        fixture.state = fixture.state.copy(measurementLogs = fixture.state.measurementLogs + MeasurementLog(waist.id, today.toString(), 4L, 0.0))
        assertTrue(fixture.coordinator.onReminderDue(ReminderViewKind.Measurement, "msr_k2p9", "day"))
        assertEquals(2, fixture.said.size)
        assertEquals(today.plusDays(1), fixture.gateway.earliest.last())
    }

    @Test fun aMeasurementHiddenGoneOrSwitchedOffNeverSpeaks() {
        val hidden = Fixture(AppState().withWeight().with(waist), FeatureVisibilitySettings(hiddenMeasurements = setOf("msr_k2p9")))
        val gone = Fixture(AppState().withWeight())
        val off = Fixture(AppState().withWeight().with(testMadeMeasurement()))
        // An alarm for a checkpoint it does not have rings nothing either.
        val otherCheckpoint = Fixture(AppState().withWeight().with(waist))
        otherCheckpoint.coordinator.onReminderDue(ReminderViewKind.Measurement, "msr_k2p9", "evening")
        listOf(hidden, gone, off).forEach { it.coordinator.onReminderDue(ReminderViewKind.Measurement, "msr_k2p9", "day") }

        listOf(hidden, gone, off, otherCheckpoint).forEach { fixture ->
            assertEquals(emptyList<Said>(), fixture.said)
            assertEquals(emptyMap<String, ReminderDays>(), fixture.runtime.posted())
        }
    }

    /** "Not due" is read on the day the reminder asks about: silent on an away day under it, speaking on one still due. */
    @Test fun onAnAwayDayItSpeaksOnlyWhileItIsStillDueByTheRuleOfThatDay() {
        val away = HolidaySchedule(listOf(DayRange(today.minusDays(3), today.plusDays(3))))
        val stillDue = Fixture(AppState().withWeight().with(waist), awayDays = away)
        stillDue.coordinator.onReminderDue(ReminderViewKind.Measurement, "msr_k2p9", "day")
        assertEquals(1, stillDue.said.size)

        val notDue = Fixture(AppState().withWeight().with(waist.copy(whenAway = Dated.always(WhenAway.Excused))), awayDays = away)
        assertTrue(notDue.coordinator.onReminderDue(ReminderViewKind.Measurement, "msr_k2p9", "day"))
        assertEquals(emptyList<Said>(), notDue.said)
        assertEquals(today.plusDays(1), notDue.gateway.earliest.last())
        // At home the same rule asks as ever.
        val home = Fixture(AppState().withWeight().with(waist.copy(whenAway = Dated.always(WhenAway.Excused))))
        home.coordinator.onReminderDue(ReminderViewKind.Measurement, "msr_k2p9", "day")
        assertEquals(1, home.said.size)

        // The rule changed during the holiday: each day is asked about under its own.
        val changedTomorrow = waist.applyEdit(MeasurementEdit.SetWhenAway(WhenAway.Excused), today.plusDays(1))
        val before = Fixture(AppState().withWeight().with(changedTomorrow), awayDays = away)
        before.coordinator.onReminderDue(ReminderViewKind.Measurement, "msr_k2p9", "day")
        assertEquals(1, before.said.size)
        val after = Fixture(AppState().withWeight().with(changedTomorrow), awayDays = away, todayKey = today.plusDays(1).toString())
        after.coordinator.onReminderDue(ReminderViewKind.Measurement, "msr_k2p9", "day")
        assertEquals(emptyList<Said>(), after.said)
    }

    /** A sick day asks nothing of the streak, but its reminder still comes, also on a day that is away too. */
    @Test fun aSickDayStillRemindsAlsoOneThatIsAwayToo() {
        val notDueAway = waist.copy(whenAway = Dated.always(WhenAway.Excused))
        val sick = HolidaySchedule().withSickDays(DayRange(today, today))
        val sickAndAway = HolidaySchedule(listOf(DayRange(today, today))).withSickDays(DayRange(today, today))
        listOf(sick, sickAndAway).forEach { daysOff ->
            val fixture = Fixture(AppState().withWeight().with(notDueAway), awayDays = daysOff)
            fixture.coordinator.onReminderDue(ReminderViewKind.Measurement, "msr_k2p9", "day")
            assertEquals(1, fixture.said.size)
        }
        assertTrue(notDueAway.isExcusedOn(today, HolidaySchedule(listOf(DayRange(today, today)))))
        assertFalse(notDueAway.isExcusedOn(today, sickAndAway))
        assertFalse(waist.isExcusedOn(today, HolidaySchedule(listOf(DayRange(today, today)))))
    }

    /** An alarm rings the view of its own kind and never the other kind's view of that ID. */
    @Test fun anAlarmRingsTheViewOfItsKindAndNeverTheOtherOfItsId() {
        val fixture = Fixture(AppState(checklists = listOf(sameChecklist)).withWeight().with(sameMeasurement))
        fixture.coordinator.onReminderDue(ReminderViewKind.Measurement, "same", "day")
        assertEquals(listOf("Mood"), fixture.said.map { it.label })
        assertEquals(emptyList<String>(), fixture.checklistKeys)

        fixture.coordinator.onReminderDue(ReminderViewKind.Checklist, "same", "day")
        assertEquals(listOf("same/day"), fixture.checklistKeys)
        assertEquals(1, fixture.said.size)
        assertEquals(setOf("measurement/same/day", "same/day"), fixture.runtime.posted().keys)

        // Deleted, each leaves the other's alarm to ring as ever.
        val checklistOnly = Fixture(AppState(checklists = listOf(sameChecklist)).withWeight())
        checklistOnly.coordinator.onReminderDue(ReminderViewKind.Measurement, "same", "day")
        assertEquals(0, checklistOnly.said.size + checklistOnly.checklistKeys.size)
        val measurementOnly = Fixture(AppState().withWeight().with(sameMeasurement))
        measurementOnly.coordinator.onReminderDue(ReminderViewKind.Checklist, "same", "day")
        assertEquals(0, measurementOnly.said.size + measurementOnly.checklistKeys.size)
    }

    /**
     * After a settings change a notification on show is said again, quietly, with the name the
     * measurement has now, about the day it asked about. It goes once that day asks nothing any
     * more: logged, the measurement deleted, or the day no longer due. Swiped away, nothing
     * brings it back.
     */
    @Test fun aNotificationOnShowFollowsWhatIsStoredAndGoesOnceItAsksNothing() {
        val fixture = Fixture(AppState().withWeight().with(waist))
        fixture.coordinator.onReminderDue(ReminderViewKind.Measurement, "msr_k2p9", "day")

        fixture.state = AppState().withWeight().with(waist.copy(label = "Hips"))
        fixture.coordinator.refreshPosted()
        assertEquals(Said("Hips", "measurement/msr_k2p9/day", ReminderDays(today, today), quietly = true), fixture.said.last())
        assertEquals(emptyList<String>(), fixture.gateway.dismissed)

        fixture.state = fixture.state.copy(measurementLogs = listOf(MeasurementLog(waist.id, today.toString(), 1L, 91.5)))
        fixture.coordinator.refreshPosted()
        assertEquals(listOf("measurement/msr_k2p9/day"), fixture.gateway.dismissed)
        assertEquals(emptyMap<String, ReminderDays>(), fixture.runtime.posted())
        assertEquals(2, fixture.said.size)

        val deleted = Fixture(AppState().withWeight().with(waist))
        deleted.coordinator.onReminderDue(ReminderViewKind.Measurement, "msr_k2p9", "day")
        deleted.state = AppState().withWeight()
        deleted.coordinator.refreshPosted()
        assertEquals(listOf("measurement/msr_k2p9/day"), deleted.gateway.dismissed)

        val away = HolidaySchedule(listOf(DayRange(today, today)))
        val noLongerDue = Fixture(AppState().withWeight().with(waist), awayDays = away)
        noLongerDue.coordinator.onReminderDue(ReminderViewKind.Measurement, "msr_k2p9", "day")
        noLongerDue.state = AppState().withWeight().with(waist.applyEdit(MeasurementEdit.SetWhenAway(WhenAway.Excused), today))
        noLongerDue.coordinator.refreshPosted()
        assertEquals(listOf("measurement/msr_k2p9/day"), noLongerDue.gateway.dismissed)

        val swiped = Fixture(AppState().withWeight().with(waist))
        swiped.coordinator.onReminderDue(ReminderViewKind.Measurement, "msr_k2p9", "day")
        swiped.gateway.swiped += "measurement/msr_k2p9/day"
        swiped.state = AppState().withWeight().with(waist.copy(label = "Hips"))
        swiped.coordinator.refreshPosted()
        assertEquals(1, swiped.said.size)
        assertEquals(emptyMap<String, ReminderDays>(), swiped.runtime.posted())
    }

    /** Hidden or switched off, its alarm is cancelled and its notification taken down with it, as a checklist's are. */
    @Test fun hidingOrSwitchingOffCancelsItsAlarmAndItsNotification() {
        listOf<(Fixture) -> Unit>(
            { it.visibility = FeatureVisibilitySettings(hiddenMeasurements = setOf("msr_k2p9")) },
            { it.state = AppState().withWeight().with(testMadeMeasurement()) },
        ).forEach { change ->
            val fixture = Fixture(AppState().withWeight().with(waist))
            fixture.coordinator.onReminderDue(ReminderViewKind.Measurement, "msr_k2p9", "day")
            change(fixture)
            assertTrue(fixture.coordinator.reconcile())
            assertEquals(listOf("measurement/msr_k2p9/day"), fixture.gateway.cancelled)
            assertEquals(emptyMap<String, ReminderDays>(), fixture.runtime.posted())
            assertEquals(emptySet<String>(), fixture.runtime.keys)
        }
    }

    /**
     * A day's number saved from the measurement's screen takes down the notification of that
     * measurement about that day. One about another day, another measurement's and a checklist's
     * of the same ID stay on show.
     */
    @Test fun loggingADayTakesDownTheNotificationAboutThatDayAndNoOther() {
        val yesterday = today.minusDays(1)
        val mood = testMadeMeasurement(id = "msr_m7xd", label = "Mood", unit = "", step = "1").reminding()
        val checklist = sameChecklist.copy(id = waist.id)
        val state = AppState(checklists = listOf(checklist)).withWeight().with(waist).with(mood)
        fun AppState.logging(measurementId: String, day: LocalDate) = copy(measurementLogs = measurementLogs + MeasurementLog(measurementId, day.toString(), 1L, 91.5))

        // Waist's notification is yesterday's, still on show; Mood's and the checklist's are today's.
        val before = Fixture(state, todayKey = yesterday.toString())
        before.coordinator.onReminderDue(ReminderViewKind.Measurement, waist.id, "day")
        val fixture = Fixture(state, runtime = before.runtime)
        fixture.coordinator.onReminderDue(ReminderViewKind.Measurement, mood.id, "day")
        fixture.coordinator.onReminderDue(ReminderViewKind.Checklist, waist.id, "day")
        val onShow = mapOf(
            "measurement/msr_k2p9/day" to ReminderDays(yesterday, yesterday),
            "measurement/msr_m7xd/day" to ReminderDays(today, today),
            "msr_k2p9/day" to ReminderDays(today, today),
        )
        assertEquals(onShow, fixture.runtime.posted())

        // Today's number answers nothing about yesterday.
        fixture.coordinator.onDayLogged(state, state.logging(waist.id, today), waist.ref, today.toString())
        assertEquals(onShow, fixture.runtime.posted())
        assertEquals(emptyList<String>(), fixture.gateway.dismissed)

        // Yesterday's does, late as it is, and touches no other notification.
        val logged = state.logging(waist.id, today).logging(waist.id, yesterday)
        fixture.coordinator.onDayLogged(state.logging(waist.id, today), logged, waist.ref, yesterday.toString())
        assertEquals(listOf("measurement/msr_k2p9/day"), fixture.gateway.dismissed)
        assertEquals(onShow - "measurement/msr_k2p9/day", fixture.runtime.posted())
        // Nothing was said again along the way.
        assertEquals(listOf("Waist", "Mood"), (before.said + fixture.said).map { it.label })
        assertEquals(listOf("msr_k2p9/day"), fixture.checklistKeys)
    }

    /** A notification taken down is forgotten: removing the record brings none back, and neither does a later settings change. */
    @Test fun removingTheRecordBringsNothingBack() {
        val state = AppState().withWeight().with(waist)
        val logged = state.copy(measurementLogs = listOf(MeasurementLog(waist.id, today.toString(), 1L, 91.5)))
        val fixture = Fixture(state)
        fixture.coordinator.onReminderDue(ReminderViewKind.Measurement, waist.id, "day")
        fixture.coordinator.onDayLogged(state, logged, waist.ref, today.toString())
        assertEquals(listOf("measurement/msr_k2p9/day"), fixture.gateway.dismissed)

        fixture.coordinator.onDayLogged(logged, state, waist.ref, today.toString())
        fixture.coordinator.refreshPosted()

        assertEquals(1, fixture.said.size)
        assertEquals(emptyMap<String, ReminderDays>(), fixture.runtime.posted())
        assertEquals(1, fixture.gateway.dismissed.size)
    }

    /**
     * A weight stored on the same date, as the reply in Weight's notification stores it, neither
     * takes a made measurement's notification down nor says it again, whether nothing is told of
     * it, as the reply tells nothing, or it were told as Weight's day.
     */
    @Test fun aWeightOfTheSameDateTouchesNoMadeMeasurementsNotification() {
        val state = AppState().withWeight().with(waist)
        val fixture = Fixture(state)
        fixture.coordinator.onReminderDue(ReminderViewKind.Measurement, waist.id, "day")
        val weighed = state.copy(measurementLogs = listOf(MeasurementLog(WeightMeasurementId, today.toString(), 1L, 81.4)))
        fixture.state = weighed

        fixture.coordinator.onDayLogged(state, weighed, LoggedViewRef(LoggedViewKind.Measurement, WeightMeasurementId), today.toString())

        assertEquals(emptyList<String>(), fixture.gateway.dismissed)
        assertEquals(mapOf("measurement/msr_k2p9/day" to ReminderDays(today, today)), fixture.runtime.posted())
        assertEquals(1, fixture.said.size)
        // Its own measurement's record then answers it as ever.
        val both = weighed.copy(measurementLogs = weighed.measurementLogs + MeasurementLog(waist.id, today.toString(), 2L, 91.5))
        fixture.coordinator.onDayLogged(weighed, both, waist.ref, today.toString())
        assertEquals(listOf("measurement/msr_k2p9/day"), fixture.gateway.dismissed)
    }

    /**
     * The preview and the test say what the reminder says, read from a configuration as if nothing
     * were logged. They work with the switch off, and there is nothing to send on an away day the
     * measurement is not due on.
     */
    @Test fun thePreviewAndTheTestSayWhatTheReminderSays() {
        val message = MoReminderMessage(title = UiText.Verbatim("Waist"), text = uiText(R.string.measurement_reminder_open))
        assertEquals(message, loggedReminderMessage(waist, ReminderDays(today, today)))

        val off = testMadeMeasurement()
        val test = requireNotNull(loggedTestReminder(off, today, HolidaySchedule()))
        assertEquals(TestReminder.Logged(off, ReminderDays(today, today), opens = false), test)
        assertEquals(message, test.words(mood = null))
        assertEquals(message, test.opening(true).words(mood = null))

        val away = HolidaySchedule(listOf(DayRange(today, today)))
        assertEquals(test, loggedTestReminder(off, today, away))
        assertNull(loggedTestReminder(off.copy(whenAway = Dated.always(WhenAway.Excused)), today, away))
        assertEquals(test.copy(view = off.copy(whenAway = Dated.always(WhenAway.Excused))), loggedTestReminder(off.copy(whenAway = Dated.always(WhenAway.Excused)), today, away.withSickDays(DayRange(today, today))))
    }

    private fun MeasurementConfiguration.reminding() = copy(checkpoint = checkpoint.copy(remind = true))

    private fun AppState.with(measurement: MeasurementConfiguration) = copy(measurements = measurements + measurement)

    /** What a measurement's notification was posted with. */
    private data class Said(val label: String, val key: String, val days: ReminderDays, val quietly: Boolean)

    private inner class Fixture(
        var state: AppState,
        var visibility: FeatureVisibilitySettings = FeatureVisibilitySettings(),
        awayDays: HolidaySchedule = HolidaySchedule(),
        todayKey: String = today.toString(),
        /** Shared with another day's fixture, as the phone's is from one day to the next. */
        val runtime: MemoryReminderRuntime = MemoryReminderRuntime(),
    ) {
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

/** Every notification posted is on show until it is [swiped] away or taken down. */
internal class RecordingReminderGateway(private val runtime: MemoryReminderRuntime) : ChecklistReminderGateway {
    val swiped = mutableSetOf<String>()
    val scheduled = mutableListOf<ReminderSlot>()
    val earliest = mutableListOf<LocalDate?>()
    val cancelled = mutableListOf<String>()
    val dismissed = mutableListOf<String>()
    /** Each notification said again where an answer met records it could not read, with why. */
    val keptUnsaved = mutableListOf<Pair<String, UiText>>()
    override fun canScheduleExact() = true
    override fun shown() = runtime.posted().keys - swiped
    override fun schedule(slot: ReminderSlot, earliestDate: LocalDate?, fallbackAlarmTime: ReminderTime, fixedBedtime: ReminderTime?): Boolean {
        scheduled += slot
        earliest += earliestDate
        return true
    }
    override fun cancel(key: String) {
        cancelled += key
    }
    override fun dismiss(key: String) {
        dismissed += key
    }
    override fun keepUnsaved(key: String, why: UiText): Boolean {
        keptUnsaved += key to why
        return key in shown()
    }
}

internal class MemoryReminderRuntime : ChecklistReminderRuntime {
    var keys = emptySet<String>()
    private val days = mutableMapOf<String, ReminderDays>()
    private val bounds = mutableMapOf<String, LocalDate>()
    override fun earliest() = bounds.toMap()
    override fun earliest(key: String, day: LocalDate) {
        bounds[key] = day
    }
    override fun scheduled() = keys
    override fun record(keys: Set<String>) {
        this.keys = keys
    }
    override fun posted() = days.toMap()
    override fun posted(key: String, days: ReminderDays) {
        this.days[key] = days
    }
    override fun dismissed(key: String) {
        days.remove(key)
    }
}

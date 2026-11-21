package com.example.workouttracker.reminder

import com.example.workouttracker.domain.settings.hidingTasks
import com.example.workouttracker.domain.compatibility.MoNeed
import com.example.workouttracker.R
import com.example.workouttracker.core.text.UiText
import com.example.workouttracker.core.text.pluralText
import com.example.workouttracker.core.text.uiText
import com.example.workouttracker.domain.AppState
import com.example.workouttracker.domain.LoggedView
import com.example.workouttracker.domain.compatibility.LegacyCareAction
import com.example.workouttracker.domain.compatibility.CareChecklistId
import com.example.workouttracker.domain.care.CareDone
import com.example.workouttracker.domain.compatibility.LegacyCareSession
import com.example.workouttracker.domain.care.careState
import com.example.workouttracker.domain.checklist.ChecklistItem
import com.example.workouttracker.domain.checklist.Checkpoint
import com.example.workouttracker.domain.checklist.CheckpointTime
import com.example.workouttracker.domain.checklist.Dated
import com.example.workouttracker.domain.checklist.WhenAway
import com.example.workouttracker.domain.holiday.DayRange
import com.example.workouttracker.domain.holiday.HolidaySchedule
import com.example.workouttracker.domain.measurement.MeasurementConfiguration
import com.example.workouttracker.domain.measurement.MeasurementLog
import com.example.workouttracker.domain.compatibility.WeightMeasurementId
import com.example.workouttracker.domain.measurement.testMadeMeasurement
import com.example.workouttracker.domain.measurement.testWeightMeasurement
import com.example.workouttracker.domain.measurement.withWeight
import com.example.workouttracker.domain.mo.reflectionAbout
import com.example.workouttracker.domain.questions.testEatingQuestions
import com.example.workouttracker.domain.questions.withEating
import com.example.workouttracker.domain.ref
import com.example.workouttracker.domain.reminder.ReminderSettings
import com.example.workouttracker.domain.settings.FeatureVisibilitySettings
import com.example.workouttracker.domain.compatibility.StandardSupplementIds
import com.example.workouttracker.domain.compatibility.SupplementsChecklistId
import com.example.workouttracker.domain.supplement.supplementDay
import com.example.workouttracker.domain.supplement.supplementsState
import com.example.workouttracker.domain.compatibility.ReadingTimeId
import com.example.workouttracker.domain.time.TimeLog
import com.example.workouttracker.domain.time.testReadingTime
import com.example.workouttracker.domain.time.withReading
import com.example.workouttracker.persistence.TrackerRepository
import com.example.workouttracker.persistence.TrackerUpdateResult
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Weight, Eating, Reading, Supplements and Care remind through the coordinator every view reminds
 * through: under the keys their kind gives them, at their checkpoints, asking what their kind
 * asks, and answered by the saves and actions any view of their kind is answered by. Mo speaks
 * only while a view is a task of his.
 */
class TheFiveRemindersTest {
    /** A Wednesday. */
    private val today = LocalDate.parse("2026-10-14")
    private val yesterday = today.minusDays(1)
    private val weight = testWeightMeasurement().let { it.copy(checkpoint = it.checkpoint.copy(remind = true)) }
    private val eating = testEatingQuestions().let { it.copy(checkpoint = it.checkpoint.copy(remind = true)) }
    private val reading = testReadingTime().let { it.copy(checkpoint = it.checkpoint.copy(remind = true)) }

    /** Each of the five that reminds has its slot under its kind's key, at its checkpoint's time; switched off or hidden it has none. */
    @Test fun eachRemindsUnderItsKindsKeyAtItsCheckpoint() {
        val state = careState(remindMorning = true, remindEvening = true).withSupplements(remind = true).withWeight(weight).withEating(eating).withReading(reading)

        assertEquals(
            mapOf(
                "supplements/midday" to CheckpointTime.Fixed(LocalTime.of(12, 30)),
                "care/morning" to CheckpointTime.AfterWake(5),
                "care/evening" to CheckpointTime.BeforeSleep(15),
                "measurement/weight/day" to CheckpointTime.AfterWake(5),
                "questions/eating/day" to CheckpointTime.AfterWake(15),
                "time/reading/day" to CheckpointTime.Fixed(LocalTime.of(18, 0)),
            ),
            reminderSlots(state, FeatureVisibilitySettings(), today).associate { it.key to it.at },
        )
        val evening = CheckpointTime.Fixed(LocalTime.of(21, 0))
        assertEquals(evening, loggedReminderSlots(AppState().withWeight(weight.copy(checkpoint = weight.checkpoint.copy(at = evening))), FeatureVisibilitySettings()).single().at)

        val hidden = FeatureVisibilitySettings().hidingTasks(MoNeed.Weight, MoNeed.Supplements)
        assertEquals(setOf("care/morning", "care/evening", "questions/eating/day", "time/reading/day"), reminderSlots(state, hidden, today).mapTo(HashSet()) { it.key })
        assertEquals(emptyList<ReminderSlot>(), reminderSlots(careState().withSupplements(remind = false).withWeight().withEating().withReading(), FeatureVisibilitySettings(), today))
    }

    /** Weight's reminder asks while today holds no weight and is quiet once it is logged, while hidden, and on an away day it is excused on. */
    @Test fun weightAsksWhileTodayHoldsNoWeight() {
        val asking = Fixture(AppState().withWeight(weight))
        assertTrue(asking.ring(weight))
        assertEquals(listOf(Said("measurement/weight/day", ReminderDays(today, today), minutesToGo = null)), asking.said)

        val logged = Fixture(AppState(measurementLogs = listOf(MeasurementLog(WeightMeasurementId, today.toString(), 1L, 81.0))).withWeight(weight))
        logged.ring(weight)
        assertEquals(emptyList<Said>(), logged.said)

        val hidden = Fixture(AppState().withWeight(weight), FeatureVisibilitySettings(disabledFeatures = emptySet()).hidingTasks(MoNeed.Weight))
        hidden.ring(weight)
        assertEquals(emptyList<Said>(), hidden.said)

        val away = HolidaySchedule(listOf(DayRange(today, today)))
        val excused = Fixture(AppState().withWeight(weight), awayDays = away)
        excused.ring(weight)
        assertEquals(emptyList<Said>(), excused.said)
        // Due on holiday, it asks then too.
        val due = weight.copy(whenAway = Dated.always(WhenAway.Due))
        val stillDue = Fixture(AppState().withWeight(due), awayDays = away)
        stillDue.ring(due)
        assertEquals(1, stillDue.said.size)
    }

    /** A number replied to Weight's reminder is stored as Weight's for the day it asked about, and the notification goes. */
    @Test fun aReplyToWeightsReminderStoresTheWeight() {
        val fixture = Fixture(AppState().withWeight(weight))
        fixture.ring(weight)

        assertTrue(fixture.coordinator.onReply(WeightMeasurementId, "81,4", ReminderDays(today, today)))
        assertEquals(listOf(MeasurementLog(WeightMeasurementId, today.toString(), 9L, 81.4)), fixture.state.measurementLogs)
        assertEquals(listOf("measurement/weight/day"), fixture.gateway.dismissed)
    }

    /** Eating's reminder asks about yesterday until yesterday is answered; a reflection saved for that day takes it down, one for today does not. */
    @Test fun eatingAsksAboutYesterdayAndAReflectionForThatDayTakesItDown() {
        val fixture = Fixture(AppState().withEating(eating))
        fixture.ring(eating)
        assertEquals(listOf(Said("questions/eating/day", ReminderDays(today, yesterday), minutesToGo = null)), fixture.said)

        val before = fixture.state
        val aboutToday = before.copy(questionEntries = listOf(reflectionAbout(today)))
        fixture.state = aboutToday
        fixture.coordinator.onDayLogged(before, aboutToday, eating.ref, today.toString())
        assertEquals(emptyList<String>(), fixture.gateway.dismissed)

        val answered = aboutToday.copy(questionEntries = listOf(reflectionAbout(today), reflectionAbout(yesterday)))
        fixture.state = answered
        fixture.coordinator.onDayLogged(aboutToday, answered, eating.ref, yesterday.toString())
        assertEquals(listOf("questions/eating/day"), fixture.gateway.dismissed)

        val quiet = Fixture(answered)
        quiet.ring(eating)
        assertEquals(emptyList<Said>(), quiet.said)
    }

    /** Reading's reminder asks while its week is short, naming the minutes still to go, and is quiet once the week is read. */
    @Test fun readingAsksWhileTheWeekIsShort() {
        val monday = today.minusDays(2)
        val short = Fixture(AppState(timeLogs = listOf(TimeLog(ReadingTimeId, monday.toString(), 1L, 20))).withReading(reading))
        short.ring(reading)
        assertEquals(listOf(Said("time/reading/day", ReminderDays(today, today), minutesToGo = 40)), short.said)

        val read = Fixture(AppState(timeLogs = listOf(TimeLog(ReadingTimeId, monday.toString(), 1L, 60))).withReading(reading))
        read.ring(reading)
        assertEquals(emptyList<Said>(), read.said)
    }

    /** Supplements' reminder names what is still open; "Mark all done" ticks it, and then there is nothing to remind of. */
    @Test fun supplementsNamesWhatIsOpenAndMarkAllDoneTicksIt() {
        val oneTaken = Fixture(supplementsState(supplementDay(today.toString(), 1L, setOf(StandardSupplementIds.MULTIVITAMIN)), remind = true))
        assertTrue(oneTaken.ringChecklist(SupplementsChecklistId, "midday"))
        val (key, open) = oneTaken.checklistsSaid.single()
        assertEquals("supplements/midday", key)
        assertTrue(open.isNotEmpty())
        assertFalse(StandardSupplementIds.MULTIVITAMIN in open.map { it.id })

        assertTrue(oneTaken.coordinator.onMarkAllDone(SupplementsChecklistId, "midday", ReminderDays(today, today)))
        assertEquals(listOf("supplements/midday"), oneTaken.gateway.dismissed.distinct())
        val allTaken = Fixture(oneTaken.state)
        allTaken.ringChecklist(SupplementsChecklistId, "midday")
        assertEquals(emptyList<Pair<String, List<ChecklistItem>>>(), allTaken.checklistsSaid)
    }

    /** Each of Care's sessions reminds on its own, and only while something is open at it. */
    @Test fun careRemindsEachSessionWhileSomethingIsOpenThere() {
        val morning = LegacyCareAction.entries.filter { it != LegacyCareAction.CLEAN_BETWEEN_TEETH }.map { CareDone(today.toString(), LegacyCareSession.MORNING, it, 1L) }
        val fixture = Fixture(careState(*morning.toTypedArray(), remindMorning = true, remindEvening = true))
        fixture.ringChecklist(CareChecklistId, "morning")
        fixture.ringChecklist(CareChecklistId, "evening")
        assertEquals(listOf("care/evening"), fixture.checklistsSaid.map { it.first })
    }

    /** Timer reminders use their configured label and the shared remaining-minutes wording. */
    @Test fun configuredRemindersUseKindWordingAndMotifArtwork() {
        assertFalse(weight.speaksWithMo)
        assertEquals(MoReminderMessage(UiText.Verbatim(weight.label), uiText(R.string.measurement_reminder_open)), loggedReminderMessage(weight, ReminderDays(today, today)))
        assertEquals(MoReminderMessage(UiText.Verbatim(eating.label), uiText(R.string.questions_reminder_open_yesterday)), loggedReminderMessage(eating, ReminderDays(today, yesterday)))
        assertEquals(
            MoReminderMessage(UiText.Verbatim(reading.label), pluralText(R.plurals.time_reminder_week_remaining, 25)),
            loggedReminderMessage(reading, ReminderDays(today, today), minutesToGo = 25),
        )

        val supplements = supplementsState().checklists.single()
        val care = careState().checklists.single()
        assertTrue(speaksWithMo(supplements, supplements.checkpoints.single()))
        assertTrue(care.checkpoints.all { speaksWithMo(care, it) })
        // Every checkpoint uses the checklist's configured motif for its artwork.
        assertTrue(speaksWithMo(care, Checkpoint("cpt_noon", CheckpointTime.AfterWake(240), remind = true)))

        val waist = testMadeMeasurement()
        assertFalse(waist.speaksWithMo)
        assertEquals(MoReminderMessage(UiText.Verbatim("Waist"), uiText(R.string.measurement_reminder_open)), loggedReminderMessage(waist, ReminderDays(today, today)))
    }

    /** Weight's notification on show is said again, quietly, once its configuration changes, as to another unit. */
    @Test fun weightsNotificationIsSaidAgainQuietlyAfterItsUnitChanges() {
        val fixture = Fixture(AppState().withWeight(weight))
        fixture.ring(weight)
        fixture.state = AppState().withWeight(weight.copy(unit = "lb"))
        fixture.coordinator.refreshPosted()
        assertEquals(listOf(false, true), fixture.quietly)
        assertEquals("lb", (fixture.saidOf.last() as MeasurementConfiguration).unit)
    }

    private fun AppState.withSupplements(remind: Boolean) = copy(checklists = checklists + supplementsState(remind = remind).checklists)

    private data class Said(val key: String, val days: ReminderDays, val minutesToGo: Int?)

    private inner class Fixture(
        var state: AppState,
        visibility: FeatureVisibilitySettings = FeatureVisibilitySettings(),
        awayDays: HolidaySchedule = HolidaySchedule(),
    ) {
        val runtime = MemoryReminderRuntime()
        val gateway = RecordingReminderGateway(runtime)
        val said = mutableListOf<Said>()
        val saidOf = mutableListOf<LoggedView>()
        val quietly = mutableListOf<Boolean>()
        val checklistsSaid = mutableListOf<Pair<String, List<ChecklistItem>>>()
        val coordinator = ChecklistReminderCoordinator(
            trackerRepository = object : TrackerRepository {
                override fun load() = state
                override fun save(state: AppState) = error("Every write is an update.")
                override fun update(transform: (AppState) -> AppState): TrackerUpdateResult =
                    TrackerUpdateResult.Updated(transform(state).also { state = it })
            },
            featureVisibility = FeatureVisibilitySource { visibility },
            gateway = gateway,
            runtime = runtime,
            notifier = { _, _, open, key, _, _ ->
                checklistsSaid += key to open
                true
            },
            loggedNotifier = object : LoggedReminderNotifier {
                override fun show(view: LoggedView, key: String, days: ReminderDays, quietly: Boolean) = error("A logged view's reminder is handed the minutes it names.")
                override fun show(view: LoggedView, minutesToGo: Int?, key: String, days: ReminderDays, quietly: Boolean): Boolean {
                    said += Said(key, days, minutesToGo)
                    saidOf += view
                    this@Fixture.quietly += quietly
                    return true
                }
            },
            reminderSettings = { ReminderSettings() },
            todayKey = { today.toString() },
            awayDays = { awayDays },
            timestampMillis = { 9L },
        )

        fun ring(view: LoggedView) = coordinator.onReminderDue(view.kind.reminderKind, view.id, view.checkpoint.id)

        fun ringChecklist(checklistId: String, checkpointId: String) = coordinator.onReminderDue(ReminderViewKind.Checklist, checklistId, checkpointId)
    }
}

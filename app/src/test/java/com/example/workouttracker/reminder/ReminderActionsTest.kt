package com.example.workouttracker.reminder

import com.example.workouttracker.R
import com.example.workouttracker.core.text.uiText
import com.example.workouttracker.domain.AppState
import com.example.workouttracker.domain.LoggedView
import com.example.workouttracker.domain.checklist.ChecklistConfiguration
import com.example.workouttracker.domain.checklist.ChecklistDay
import com.example.workouttracker.domain.checklist.ChecklistItem
import com.example.workouttracker.domain.checklist.Checkpoint
import com.example.workouttracker.domain.checklist.CheckpointTime
import com.example.workouttracker.domain.checklist.Dated
import com.example.workouttracker.domain.checklist.ViewHue
import com.example.workouttracker.domain.checklist.ViewIcon
import com.example.workouttracker.domain.checklist.recordOn
import com.example.workouttracker.domain.measurement.MeasurementConfiguration
import com.example.workouttracker.domain.measurement.MeasurementLog
import com.example.workouttracker.domain.measurement.testMadeMeasurement
import com.example.workouttracker.domain.reminder.ReminderSettings
import com.example.workouttracker.domain.settings.FeatureVisibilitySettings
import com.example.workouttracker.persistence.TrackerRepository
import com.example.workouttracker.persistence.TrackerUpdateResult
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What a reminder offers is answered by the coordinator that posted it: a measurement's reply
 * stores the number for the day it asked about, "Mark all done" ticks what is due at its
 * checkpoint, and either takes its notification down, or says it again with what went wrong.
 */
class ReminderActionsTest {
    private val today = LocalDate.parse("2026-10-14")
    private val yesterday = ReminderDays(today, today.minusDays(1))
    private val waist = testMadeMeasurement().let { it.copy(checkpoint = it.checkpoint.copy(remind = true)) }
    private val waistKey = slotKey(ReminderViewKind.Measurement, waist.id, "day")
    private val medication = ChecklistConfiguration(
        id = "chk_med",
        label = "Medication",
        icon = ViewIcon.Medication,
        hue = ViewHue.Sky,
        checkpoints = listOf(Checkpoint("morning", CheckpointTime.Fixed(LocalTime.of(8, 0)), remind = true)),
        items = listOf(
            ChecklistItem("itm_levo", "Levothyroxine"),
            ChecklistItem("itm_mag", "Magnesium"),
            ChecklistItem("itm_skip", "Ibuprofen", exceptionsOnly = Dated.always(true)),
        ),
    )
    private val medicationKey = slotKey(medication.id, "morning")

    /** The reply of a measurement a person made is read by its words, a number alone, and stored for the day its notification asked about. */
    @Test fun aReplyStoresTheNumberForTheDayAskedAboutAndTakesTheNotificationDown() {
        val fixture = Fixture(AppState(measurements = listOf(waist)))
        fixture.runtime.posted(waistKey, yesterday)

        assertTrue(fixture.coordinator.onReply(waist.id, "81,5", yesterday))
        assertEquals(listOf(MeasurementLog(waist.id, yesterday.recordDay.toString(), 5L, 81.5)), fixture.state.measurementLogs)
        assertEquals(listOf(waistKey), fixture.gateway.dismissed)
        assertEquals(emptyMap<String, ReminderDays>(), fixture.runtime.posted())
    }

    /** No number stores nothing and says so with the reply still on it; a store that failed says to send it again. */
    @Test fun aReplyThatIsNoNumberOrCannotBeSavedIsSaidAgainWithWhy() {
        val fixture = Fixture(AppState(measurements = listOf(waist)))
        fixture.runtime.posted(waistKey, yesterday)

        assertFalse(fixture.coordinator.onReply(waist.id, "82 kg", yesterday))
        fixture.writes = false
        assertFalse(fixture.coordinator.onReply(waist.id, "82", yesterday))

        assertEquals(emptyList<MeasurementLog>(), fixture.state.measurementLogs)
        assertEquals(listOf(Unsaved(waist.id, waistKey, yesterday, unreadable = true), Unsaved(waist.id, waistKey, yesterday, unreadable = false)), fixture.unsaved)
        assertEquals(emptyList<String>(), fixture.gateway.dismissed)
    }

    /** A measurement hidden or deleted since its reminder came stores nothing and its notification goes. */
    @Test fun aReplyForAMeasurementHiddenOrGoneTakesItsNotificationDown() {
        val hidden = Fixture(AppState(measurements = listOf(waist)), FeatureVisibilitySettings(hiddenMeasurements = setOf(waist.id)))
        assertFalse(hidden.coordinator.onReply(waist.id, "81", yesterday))
        assertEquals(emptyList<MeasurementLog>(), hidden.state.measurementLogs)
        assertEquals(listOf(waistKey), hidden.gateway.dismissed)

        val gone = Fixture(AppState())
        assertFalse(gone.coordinator.onReply(waist.id, "abc", yesterday))
        assertEquals(listOf(waistKey), gone.gateway.dismissed)
        assertEquals(emptyList<Unsaved>(), gone.unsaved)
    }

    /**
     * "Mark all done" ticks every item due at the checkpoint in the record asked about, keeping
     * what was ticked, and leaves an item that logs only misses as it is; the notification goes.
     */
    @Test fun markAllDoneTicksWhatIsDueAndLeavesAnItemThatLogsOnlyMisses() {
        val ticked = ChecklistDay(medication.id, yesterday.recordDay.toString(), 1L, setOf("morning/itm_levo"))
        val fixture = Fixture(AppState(checklists = listOf(medication), checklistDays = listOf(ticked)))
        fixture.runtime.posted(medicationKey, yesterday)

        assertTrue(fixture.coordinator.onMarkAllDone(medication.id, "morning", yesterday))
        val record = fixture.state.recordOn(medication.id, yesterday.recordDay.toString())
        assertEquals(setOf("morning/itm_levo", "morning/itm_mag"), record.ticks)
        assertEquals(emptySet<String>(), record.misses)
        assertTrue(medicationKey in fixture.gateway.dismissed)
        assertEquals(emptyMap<String, ReminderDays>(), fixture.runtime.posted())
    }

    @Test fun markAllDoneThatCannotBeSavedIsSaidAgain() {
        val fixture = Fixture(AppState(checklists = listOf(medication)))
        fixture.writes = false

        assertFalse(fixture.coordinator.onMarkAllDone(medication.id, "morning", yesterday))
        assertEquals(listOf(Unsaved(medication.id, medicationKey, yesterday, unreadable = false)), fixture.unsaved)
        assertEquals(emptyList<String>(), fixture.gateway.dismissed)
    }

    /**
     * Records that cannot be read say nothing of the view, so a reply or "Mark all done" that
     * meets them stores nothing and takes nothing down: its notification stays, saying it was not
     * saved, with its action on it to press again. A read that throws is no different, and never
     * escapes the receiver.
     */
    @Test fun anAnswerThatMeetsUnreadableRecordsKeepsItsNotificationAndSaysItWasNotSaved() {
        listOf(Reading.Unreadable, Reading.Throws).forEach { reading ->
            val fixture = Fixture(AppState(measurements = listOf(waist), checklists = listOf(medication)))
            fixture.runtime.posted(waistKey, yesterday)
            fixture.runtime.posted(medicationKey, yesterday)
            fixture.reading = reading

            assertFalse(fixture.coordinator.onReply(waist.id, "81", yesterday))
            assertFalse(fixture.coordinator.onMarkAllDone(medication.id, "morning", yesterday))

            assertEquals(emptyList<String>(), fixture.gateway.dismissed)
            assertEquals(
                listOf(
                    waistKey to uiText(R.string.measurement_error_save_failed),
                    medicationKey to uiText(R.string.reminder_mark_all_done_failed, uiText(R.string.checklist_mark_all_done)),
                ),
                fixture.gateway.keptUnsaved,
            )
            assertEquals(setOf(waistKey, medicationKey), fixture.runtime.posted().keys)
        }
    }

    /**
     * A notification kept after an answer met unreadable records stays through what follows while
     * the records still cannot be read: a reconcile and a refresh read nothing of its view, so they
     * neither take it down nor cancel an alarm, and they answer that they could not do their work.
     */
    @Test fun aKeptNotificationOutlastsAReconcileWhileTheRecordsCannotBeRead() {
        listOf(Reading.Unreadable, Reading.Throws).forEach { reading ->
            val fixture = Fixture(AppState(measurements = listOf(waist), checklists = listOf(medication)))
            fixture.runtime.posted(waistKey, yesterday)
            fixture.runtime.posted(medicationKey, yesterday)
            fixture.runtime.record(setOf(waistKey, medicationKey))
            fixture.reading = reading
            assertFalse(fixture.coordinator.onReply(waist.id, "81", yesterday))

            assertFalse(fixture.coordinator.reconcile())
            fixture.coordinator.refreshPosted()

            assertEquals(emptyList<String>(), fixture.gateway.dismissed)
            assertEquals(emptyList<String>(), fixture.gateway.cancelled)
            assertEquals(setOf(waistKey, medicationKey), fixture.runtime.posted().keys)
            assertEquals(setOf(waistKey, medicationKey), fixture.runtime.scheduled())
        }
    }

    private data class Unsaved(val viewId: String, val key: String, val days: ReminderDays, val unreadable: Boolean)

    /** How the records read: as they are, not at all, or with a read that throws. */
    private enum class Reading { Readable, Unreadable, Throws }

    private inner class Fixture(var state: AppState, visibility: FeatureVisibilitySettings = FeatureVisibilitySettings()) {
        var writes = true
        var reading = Reading.Readable
        val runtime = MemoryReminderRuntime()
        val gateway = RecordingReminderGateway(runtime)
        val unsaved = mutableListOf<Unsaved>()
        val coordinator = ChecklistReminderCoordinator(
            trackerRepository = object : TrackerRepository {
                override fun load() = loadIfReadable() ?: AppState()
                override fun loadIfReadable(): AppState? = when (reading) {
                    Reading.Readable -> state
                    Reading.Unreadable -> null
                    Reading.Throws -> error("The store could not be opened.")
                }
                override fun save(state: AppState) = error("Every write is an update.")
                override fun update(transform: (AppState) -> AppState): TrackerUpdateResult {
                    if (reading != Reading.Readable) return TrackerUpdateResult.UnreadableState
                    if (!writes) return TrackerUpdateResult.SaveFailed
                    val updated = runCatching { transform(state) }.getOrElse { return TrackerUpdateResult.SaveFailed }
                    state = updated
                    return TrackerUpdateResult.Updated(updated)
                }
            },
            featureVisibility = FeatureVisibilitySource { visibility },
            gateway = gateway,
            runtime = runtime,
            notifier = object : ChecklistReminderNotifier {
                override fun show(checklist: ChecklistConfiguration, checkpoint: Checkpoint, open: List<ChecklistItem>, key: String, days: ReminderDays, quietly: Boolean) = true
                override fun unsaved(checklist: ChecklistConfiguration, checkpoint: Checkpoint, open: List<ChecklistItem>, key: String, days: ReminderDays): Boolean {
                    unsaved += Unsaved(checklist.id, key, days, unreadable = false)
                    return true
                }
            },
            loggedNotifier = object : LoggedReminderNotifier {
                override fun show(view: LoggedView, key: String, days: ReminderDays, quietly: Boolean) = true
                override fun unsaved(measurement: MeasurementConfiguration, key: String, days: ReminderDays, unreadable: Boolean): Boolean {
                    unsaved += Unsaved(measurement.id, key, days, unreadable)
                    return true
                }
            },
            reminderSettings = { ReminderSettings() },
            todayKey = { today.toString() },
            timestampMillis = { 5L },
        )
    }
}

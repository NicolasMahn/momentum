package de.nicolasmahn.momentum.reminder

import de.nicolasmahn.momentum.domain.measurement.recordsOf

import de.nicolasmahn.momentum.domain.compatibility.WeightMeasurementId
import de.nicolasmahn.momentum.domain.AppState
import de.nicolasmahn.momentum.domain.activity.ActivityEntry
import de.nicolasmahn.momentum.domain.measurement.MeasurementLog
import de.nicolasmahn.momentum.domain.measurement.asMeasurementLogs
import de.nicolasmahn.momentum.domain.measurement.testMadeMeasurement
import de.nicolasmahn.momentum.domain.measurement.testWeightMeasurement
import de.nicolasmahn.momentum.domain.measurement.withWeight
import de.nicolasmahn.momentum.domain.measurement.MeasurementRecord
import de.nicolasmahn.momentum.persistence.TrackerRepository
import de.nicolasmahn.momentum.persistence.TrackerUpdateResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class MeasurementReplyTest {
    @Test
    fun validInputSavesTodaysWeightWithoutChangingOtherHistory() {
        val oldWeight = MeasurementRecord("2026-09-15", 10L, 87.4)
        val activity = ActivityEntry(
            id = "activity-1",
            activityId = "bouldering",
            name = "Bouldering",
            dateKey = "2026-09-15",
            timestampMillis = 9L,
        )
        val initial = AppState(
            measurementLogs = listOf(oldWeight).asMeasurementLogs("weight"),
            activityEntries = listOf(activity),
        ).withWeight()
        val repository = FakeTrackerRepository(initial)
        val coordinator = coordinator(repository)

        assertEquals(
            MeasurementReplyResult.Saved(87.65),
            coordinator.submit("87,65 kg"),
        )
        assertEquals(
            listOf(
                MeasurementRecord("2026-09-16", 123L, 87.65),
                oldWeight,
            ),
            repository.savedState?.recordsOf("weight"),
        )
        assertSame(initial.activityEntries, repository.savedState?.activityEntries)
    }

    @Test
    fun validInputReplacesOnlyTodaysExistingWeight() {
        val previous = MeasurementRecord("2026-09-15", 10L, 87.4)
        val firstToday = MeasurementRecord("2026-09-16", 11L, 87.5)
        val duplicateToday = MeasurementRecord("2026-09-16", 12L, 87.6)
        val repository = FakeTrackerRepository(
            AppState(measurementLogs = listOf(firstToday, duplicateToday, previous).asMeasurementLogs("weight")).withWeight(),
        )

        assertEquals(
            MeasurementReplyResult.Saved(87.7),
            coordinator(repository).submit("87.7"),
        )
        assertEquals(
            listOf(MeasurementRecord("2026-09-16", 123L, 87.7), previous),
            repository.savedState?.recordsOf("weight"),
        )
    }

    /**
     * A reply touches Weight's day alone: its records of that day become the one sent, and another
     * measurement's records of the same day, two alike among them, stay exactly as they were.
     */
    @Test
    fun aReplyLeavesAnotherMeasurementsRecordsOfTheDayAsTheyWere() {
        val other = MeasurementLog("msr_k2p9", "2026-09-16", 30L, 61.0)
        val otherEarlier = MeasurementLog("msr_k2p9", "2026-09-14", 1L, 60.0)
        val previous = MeasurementLog("weight", "2026-09-15", 10L, 87.4)
        val initial = AppState(
            measurementLogs = listOf(
                other,
                MeasurementLog("weight", "2026-09-16", 11L, 87.5),
                other,
                MeasurementLog("weight", "2026-09-16", 12L, 87.6),
                previous,
                otherEarlier,
            ),
        ).withWeight()
        val repository = FakeTrackerRepository(initial)

        assertEquals(MeasurementReplyResult.Saved(87.7), coordinator(repository).submit("87.7"))

        val sent = MeasurementLog("weight", "2026-09-16", 123L, 87.7)
        assertEquals(listOf(other, other, sent, previous, otherEarlier), repository.savedState?.measurementLogs)
        assertEquals(listOf(MeasurementRecord("2026-09-16", 123L, 87.7), MeasurementRecord("2026-09-15", 10L, 87.4)), repository.savedState?.recordsOf("weight"))

        // A reply for a day that so far only the other measurement has a record on.
        assertEquals(MeasurementReplyResult.Saved(87.2), coordinator(repository).submit("87.2", targetDateKey = "2026-09-14"))
        assertEquals(
            listOf(other, other, sent, previous, otherEarlier, MeasurementLog("weight", "2026-09-14", 123L, 87.2)),
            repository.savedState?.measurementLogs,
        )
    }

    /**
     * The reply is read as Weight's field reads what is typed, by the unit Weight is stored with:
     * a trailing "kg" is part of the number only while that unit is `kg`. In pounds "82 kg" is no
     * number and nothing is written; a number alone is stored as it was typed, converted to nothing.
     */
    @Test
    fun aReplyWithKgIsANumberOnlyWhileWeightsUnitIsKilograms() {
        val inPounds = AppState().withWeight(testWeightMeasurement().copy(unit = "lb"))
        listOf("82 kg", "82kg", "82,5 KG").forEach { reply ->
            val repository = FakeTrackerRepository(inPounds)
            assertEquals(reply, MeasurementReplyResult.Invalid, coordinator(repository).submit(reply))
            assertEquals(reply, 0, repository.saveCalls)
        }
        val repository = FakeTrackerRepository(inPounds)
        assertEquals(MeasurementReplyResult.Saved(181.5), coordinator(repository).submit("181,5"))
        val withUnit = FakeTrackerRepository(inPounds)
        assertEquals(MeasurementReplyResult.Saved(181.5), coordinator(withUnit).submit("181.5 lb"))
        assertEquals(1, withUnit.saveCalls)
        assertEquals(listOf(MeasurementRecord("2026-09-16", 123L, 181.5)), repository.load().recordsOf("weight"))
        assertEquals(inPounds.measurements, repository.load().measurements)

        // In kilograms the same reply is the weight it always was.
        assertEquals(MeasurementReplyResult.Saved(82.0), coordinator(FakeTrackerRepository(AppState().withWeight())).submit("82 kg"))
    }

    /**
     * Every measurement takes a reply, read by its own words: a number alone for one a person
     * made, under its own ID, beside Weight's records of the same day.
     */
    @Test
    fun aMadeMeasurementsReplyIsReadByItsWordsAndStoredUnderItsID() {
        val waist = testMadeMeasurement()
        val weighed = MeasurementLog("weight", "2026-09-16", 1L, 82.0)
        val repository = FakeTrackerRepository(AppState(measurementLogs = listOf(weighed)).withWeight().let { it.copy(measurements = it.measurements + waist) })
        val reply = MeasurementReplyCoordinator(waist.id, repository, todayKey = { "2026-09-16" }, timestampMillis = { 123L })

        assertEquals(MeasurementReplyResult.Invalid, reply.submit("81 kg"))
        assertEquals(MeasurementReplyResult.Saved(81.5), reply.submit("81,5"))
        assertEquals(listOf(MeasurementLog(waist.id, "2026-09-16", 123L, 81.5), weighed).toSet(), repository.load().measurementLogs.toSet())
    }

    @Test
    fun genericReplySavesSignedAndZeroValuesAndRejectsNonfiniteInput() {
        val measurement = testMadeMeasurement(label = "Temperature", unit = "°C")
        val weight = MeasurementLog("weight", "2026-09-16", 1L, 82.0)
        val repository = FakeTrackerRepository(AppState(measurements = listOf(testWeightMeasurement(), measurement), measurementLogs = listOf(weight)))
        val reply = MeasurementReplyCoordinator(measurement.id, repository, todayKey = { "2026-09-16" }, timestampMillis = { 123L })
        listOf("-2,5 °C" to -2.5, "0" to 0.0, "−3.5°C" to -3.5).forEach { (typed, value) ->
            assertEquals(MeasurementReplyResult.Saved(value), reply.submit(typed))
            assertEquals(listOf(MeasurementRecord("2026-09-16", 123L, value)), repository.load().recordsOf(measurement.id))
            assertEquals(listOf(weight), repository.load().measurementLogs.filter { it.measurementId == "weight" })
        }
        val saves = repository.saveCalls
        listOf("NaN", "Infinity", "-Infinity", "1e309", "abc", "-2 kg").forEach { typed ->
            assertEquals(MeasurementReplyResult.Invalid, reply.submit(typed))
        }
        assertEquals(saves, repository.saveCalls)
    }

    /** Weight deleted since the reminder was sent has no measurement to save the number for: the reply is refused, and nothing is written. */
    @Test
    fun aReplyForWeightDeletedMeanwhileIsRefusedAndWritesNothing() {
        val previous = MeasurementRecord("2026-09-15", 10L, 87.4)
        val repository = FakeTrackerRepository(AppState(measurementLogs = listOf(previous).asMeasurementLogs("weight")))

        assertEquals(MeasurementReplyResult.Deleted, coordinator(repository).submit("87.6"))

        assertEquals(0, repository.saveCalls)
        assertEquals(listOf(previous), repository.load().recordsOf("weight"))
    }

    /**
     * A reply that is no number, to Weight deleted meanwhile, answers that Weight is gone, so the
     * notification is not posted again with a reply nothing can take.
     */
    @Test
    fun aReplyThatIsNoNumberForWeightDeletedMeanwhileAnswersDeleted() {
        listOf("abc", "", "0").forEach { reply ->
            val repository = FakeTrackerRepository(AppState())
            assertEquals(reply, MeasurementReplyResult.Deleted, coordinator(repository).submit(reply))
            assertEquals(reply, 0, repository.saveCalls)
        }
    }

    @Test
    fun notificationTargetDateIsUsedEvenAfterTheLocalDayChanges() {
        val repository = FakeTrackerRepository(AppState().withWeight())

        assertEquals(
            MeasurementReplyResult.Saved(87.7),
            coordinator(repository).submit("87.7", targetDateKey = "2026-09-15"),
        )

        assertEquals("2026-09-15", repository.load().recordsOf("weight").single().dateKey)
    }

    /**
     * A store whose records cannot be read ends a reply in a failed save, which can be sent again,
     * whichever way it fails: by throwing where the reply's words are read, as a damaged
     * preference does, or by saying the records cannot be read while answering an empty state to
     * a plain load, as the store does. Nothing is saved, and no failure of the store escapes to
     * the receiver.
     */
    @Test
    fun unreadablePersistedStateMapsToRecoverableSaveFailure() {
        fun unreadable(load: () -> AppState?) = object : TrackerRepository {
            override fun load(): AppState = load() ?: AppState()

            override fun loadIfReadable(): AppState? = load()

            override fun save(state: AppState): Boolean = error("save should not be called")

            override fun update(transform: (AppState) -> AppState): TrackerUpdateResult =
                TrackerUpdateResult.UnreadableState
        }
        val throwing = unreadable { error("The stored state cannot be read") }
        val empty = unreadable { null }

        listOf("87.6", "87,6 kg", "181.5", "not a weight").forEach { reply ->
            assertEquals(reply, MeasurementReplyResult.SaveFailed, coordinator(throwing).submit(reply))
        }
        assertEquals(MeasurementReplyResult.SaveFailed, coordinator(empty).submit("87.6"))
        // No reply at all is no number, and the store is not asked.
        assertEquals(MeasurementReplyResult.Invalid, coordinator(throwing).submit(null))
    }

    @Test
    fun invalidInputDoesNotAttemptAWrite() {
        listOf<CharSequence?>(null, "", "not a weight", "0", "-1", "NaN", "Infinity")
            .forEach { input ->
                val repository = FakeTrackerRepository(AppState().withWeight())

                assertEquals(
                    MeasurementReplyResult.Invalid,
                    coordinator(repository).submit(input),
                )
                assertEquals(0, repository.saveCalls)
            }
    }

    @Test
    fun failedWriteReportsFailureAndLeavesRepositoryStateUntouched() {
        val initial = AppState(measurementLogs = listOf(MeasurementRecord("2026-09-15", 10L, 87.4)).asMeasurementLogs("weight")).withWeight()
        val repository = FakeTrackerRepository(initial, saveSucceeds = false)

        assertEquals(
            MeasurementReplyResult.SaveFailed,
            coordinator(repository).submit("87.6"),
        )
        assertEquals(initial, repository.load())
        assertEquals(1, repository.saveCalls)
    }

    @Test
    fun refreshedUiSaveAfterDirectReplyPreservesNotificationWeight() {
        val repository = FakeTrackerRepository(AppState().withWeight())
        assertEquals(
            MeasurementReplyResult.Saved(87.6),
            coordinator(repository).submit("87.6"),
        )

        val refreshedUiState = repository.load()
        val activity = ActivityEntry(
            id = "activity-1",
            activityId = "bouldering",
            name = "Bouldering",
            dateKey = "2026-09-16",
            timestampMillis = 124L,
        )
        repository.save(refreshedUiState.copy(activityEntries = listOf(activity)))

        assertEquals(87.6, repository.load().recordsOf("weight").single().value, 0.0)
        assertEquals(listOf(activity), repository.load().activityEntries)
    }

    private fun coordinator(repository: TrackerRepository) = MeasurementReplyCoordinator(
        measurementId = WeightMeasurementId,
        trackerRepository = repository,
        todayKey = { "2026-09-16" },
        timestampMillis = { 123L },
    )

    private class FakeTrackerRepository(
        initialState: AppState,
        private val saveSucceeds: Boolean = true,
    ) : TrackerRepository {
        private var state = initialState
        var savedState: AppState? = null
        var saveCalls = 0

        override fun load(): AppState = state

        override fun save(state: AppState): Boolean {
            saveCalls += 1
            savedState = state
            if (saveSucceeds) this.state = state
            return saveSucceeds
        }

        // A change that throws is refused without a write, as the store refuses it.
        override fun update(transform: (AppState) -> AppState): TrackerUpdateResult {
            val updated = runCatching { transform(state) }.getOrElse { return TrackerUpdateResult.SaveFailed }
            return if (save(updated)) {
                TrackerUpdateResult.Updated(updated)
            } else {
                TrackerUpdateResult.SaveFailed
            }
        }
    }
}

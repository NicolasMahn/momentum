package de.nicolasmahn.momentum.persistence

import de.nicolasmahn.momentum.domain.AppState
import de.nicolasmahn.momentum.domain.activity.testActivityConfiguration
import de.nicolasmahn.momentum.domain.holiday.HolidaySchedule
import de.nicolasmahn.momentum.domain.measurement.MeasurementLog
import de.nicolasmahn.momentum.persistence.json.AppStateJsonCodec
import java.time.LocalDate
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import de.nicolasmahn.momentum.persistence.rows.MemoryRowStore

class PreferencesTrackerRepositoryTest {
    @Test fun aNewInstallationSeedsOnlyActivityOnceInTheChosenLanguage() {
        val store = CurrentTrackerStore()
        var started = 0
        val repository = PreferencesTrackerRepository(store, defaults = GermanDefaults, onNewInstallation = { started++ }, rows = store.rows, keepBeforeRows = { true })
        val expected = AppState(activity = GermanDefaults.activity())
        assertEquals(expected, repository.loadIfReadable())
        assertEquals(expected, repository.loadIfReadable())
        assertEquals(1, started)
        assertEquals(1, store.commits)
        // The seed is rows; the preferences hold no document.
        assertEquals(emptySet<String>(), store.keys())
        assertTrue(store.rows.rows().isNotEmpty())
    }

    @Test fun currentSiblingSettingsCanPrecedeTrackerInitialization() {
        val siblings = mapOf(
            PreferencesHabitRuleStartRepository.SettingsKey to "2026-10-25",
            PreferencesHolidayRepository.SettingsKey to HolidayScheduleCodec.encode(HolidaySchedule()),
            PreferencesPassChoiceRepository.SettingsKey to PassChoicesCodec.encode(PassChoices(LocalDate.parse("2026-10-25"))),
        )
        val store = CurrentTrackerStore(siblings)
        assertEquals(testActivityConfiguration(), trackerRepository(store).loadIfReadable()!!.activity)
        assertEquals(siblings, store.values.filterKeys { it in siblings })
        assertTrue(trackerRepository(store).save(AppState()))
        assertEquals(AppState(), trackerRepository(store).loadIfReadable())
    }

    @Test fun failedSeedWritesAreRetriedAndDoNotAnnounceInitialization() {
        val store = CurrentTrackerStore().apply { writable = false }
        var started = 0
        val repository = trackerRepository(store, onNewInstallation = { started++ })
        assertNotNull(repository.loadIfReadable())
        assertTrue(store.values.isEmpty())
        assertEquals(0, started)
        store.writable = true
        repository.loadIfReadable()
        assertEquals(1, started)
    }

    @Test fun obsoleteAndUnknownEntriesRefuseAccessWithoutChangingBytes() {
        val current = currentValues()
        listOf(
            mapOf("state_v7" to "old phone history"),
            mapOf("state_v30" to "{}"),
            current + ("state_v29" to "old snapshot"),
            current + ("unreadable_state_v32" to "preserved damage"),
            mapOf("future_records" to "history"),
            mapOf(PreferencesHabitRuleStartRepository.SettingsKey to "yesterday"),
            mapOf(PreferencesHolidayRepository.SettingsKey to "{version:1,ranges:[]}"),
            mapOf(PreferencesPassChoiceRepository.SettingsKey to "{}"),
        ).forEach { initial -> assertRefused(initial) }
    }

    @Test fun damageInCurrentStateOrItsLedgerRefusesReadsAndAllWrites() {
        val state = AppState(measurementLogs = listOf(MeasurementLog("sensor", "2026-10-25", 1L, -5.0)))
        val valid = currentValues(state)
        val raw = valid.getValue(PreferencesTrackerRepository.STATE_V32_KEY)
        listOf(
            valid + (PreferencesTrackerRepository.STATE_V32_KEY to "damaged"),
            valid + (PreferencesTrackerRepository.STATE_V32_KEY to JSONObject(raw).put("schemaVersion", 30).toString()),
            valid + (PreferencesTrackerRepository.STATE_V32_KEY to JSONObject(raw).put("measurementLogs", JSONArray().put(JSONObject().put("measurement", "sensor"))).toString()),
            valid + (PreferencesTrackerRepository.RECORDED_TIME_SESSIONS_KEY to "[1,1]"),
            valid - PreferencesTrackerRepository.RECORDED_TIME_SESSIONS_KEY,
            valid - PreferencesTrackerRepository.STATE_V32_KEY,
        ).forEach { initial -> assertRefused(initial) }
    }

    @Test fun savesAndUpdatesKeepFactsAndSessionBookkeepingInOneCommit() {
        val store = CurrentTrackerStore()
        var notifications = 0
        val repository = trackerRepository(store, onWritten = { notifications++ })
        val state = AppState(
            measurementLogs = listOf(MeasurementLog("absent-config", "2026-10-25", 1L, 0.0)),
            recordedTimeSessions = (0L..80L).toSet(),
        )
        assertTrue(repository.save(state))
        assertEquals(state, trackerRepository(store).loadIfReadable())
        val updated = state.copy(measurementLogs = state.measurementLogs + MeasurementLog("absent-config", "2026-10-24", 2L, -5.0))
        assertEquals(TrackerUpdateResult.Updated(updated), repository.update { updated })
        assertEquals(updated, repository.loadIfReadable())
        assertEquals(2, store.commits)
        assertEquals(2, notifications)
        val before = store.values.toMap()
        store.writable = false
        assertFalse(repository.save(state))
        assertEquals(TrackerUpdateResult.SaveFailed, repository.update { state })
        assertEquals(before, store.values)
    }

    @Test fun aWriteCannotUseAnEarlierSuccessfulReadToOverwriteNewDamage() {
        val store = CurrentTrackerStore(currentValues())
        val repository = trackerRepository(store)
        assertNotNull(repository.loadIfReadable())
        store.values[PreferencesTrackerRepository.STATE_V32_KEY] = "new damaged history"
        assertFalse(repository.save(AppState()))
        assertEquals("new damaged history", store.values[PreferencesTrackerRepository.STATE_V32_KEY])
    }

    @Test fun storageAccessFailureRefusesTheOperation() {
        val store = object : TrackerStringStore {
            override fun keys(): Set<String> = error("Recovery failed")
            override fun read(key: String): String? = error("Recovery failed")
            override fun write(key: String, value: String): Boolean = error("Recovery failed")
            override fun writeAll(values: Map<String, String>): Boolean = error("Recovery failed")
        }
        val repository = trackerRepository(store)
        assertNull(repository.loadIfReadable())
        assertFalse(repository.save(AppState()))
        assertEquals(TrackerUpdateResult.SaveFailed, repository.update { it })
    }

    private fun currentValues(state: AppState = AppState()): Map<String, String> = mapOf(
        PreferencesTrackerRepository.STATE_V32_KEY to AppStateJsonCodec.encode(state),
        PreferencesTrackerRepository.RECORDED_TIME_SESSIONS_KEY to RecordedTimeSessionsCodec.encode(state.recordedTimeSessions),
    )

    private fun assertRefused(initial: Map<String, String>) {
        val store = CurrentTrackerStore(initial)
        val repository = trackerRepository(store)
        assertNull(initial.keys.toString(), repository.loadIfReadable())
        assertEquals(AppState(), repository.load())
        assertFalse(repository.save(AppState()))
        var transformed = false
        assertEquals(TrackerUpdateResult.UnreadableState, repository.update { transformed = true; it })
        assertFalse(transformed)
        assertEquals(initial, store.values)
        assertEquals(0, store.commits)
    }
}

package de.nicolasmahn.momentum.persistence.rows

import de.nicolasmahn.momentum.domain.AppState
import de.nicolasmahn.momentum.domain.measurement.MeasurementLog
import de.nicolasmahn.momentum.persistence.CurrentTrackerStore
import de.nicolasmahn.momentum.persistence.PreferencesTrackerRepository
import de.nicolasmahn.momentum.persistence.RecordedTimeSessionsCodec
import de.nicolasmahn.momentum.persistence.backup.CurrentBackupFixture
import de.nicolasmahn.momentum.persistence.backup.validateRestoreCompatibility
import de.nicolasmahn.momentum.persistence.fingerprint
import de.nicolasmahn.momentum.persistence.json.AppStateJsonCodec
import de.nicolasmahn.momentum.persistence.trackerRepository
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackerRowsTest {
    private val state = CurrentBackupFixture.state

    private fun stored(state: AppState, into: MemoryRowStore = MemoryRowStore()): MemoryRowStore =
        into.also { it.commit(TrackerRows.changes(it.rows(), state)) }

    private fun read(rows: MemoryRowStore): AppState {
        val (document, sessions) = TrackerRows.assemble(rows.rows())
        return AppStateJsonCodec.decodeVersion32(document).copy(recordedTimeSessions = sessions)
    }

    @Test
    fun everyShapeTheDocumentHoldsComesBackEqualFromItsRows() {
        assertEquals(state, read(stored(state)))
        assertEquals(AppState(), read(stored(AppState())))
        val rows = stored(state).rows()
        assertTrue(rows.any { it.kind == "activity" && it.id == TrackerRows.Singleton })
        assertTrue(rows.any { it.kind == "substances" && it.id == TrackerRows.Singleton })
        assertEquals(state.recordedTimeSessions.map { it.toString() }.toSet(), rows.filter { it.kind == TrackerRows.TimerSessionsKind }.map { it.id }.toSet())
    }

    @Test
    fun aSaveWritesOnlyWhatChanged() {
        val rows = stored(state)
        val edited = state.copy(measurementLogs = state.measurementLogs.map { if (it.measurementId == "msr_custom") it.copy(value = -3.0) else it })
        val changes = TrackerRows.changes(rows.rows(), edited)
        // A weigh-in is the day's of its measurement: corrected, it keeps its row.
        assertEquals(listOf(RowKey("measurementLogs", "msr_custom/2026-10-14")), changes.written.map { RowKey(it.kind, it.id) })
        assertEquals(emptyList<RowKey>(), changes.deleted)

        val renamed = state.copy(measurements = state.measurements.map { it.copy(label = "Fever") })
        val rename = TrackerRows.changes(rows.rows(), renamed)
        // A view keeps its row: the same ID, written again.
        assertEquals(listOf(RowKey("measurements", "msr_custom")), rename.written.map { RowKey(it.kind, it.id) })
        assertEquals(emptyList<RowKey>(), rename.deleted)

        assertTrue(TrackerRows.changes(rows.rows(), state).isEmpty)
    }

    @Test
    fun identicalRecordsAndRecordsSharingAnIdStaySeparateRows() {
        // The fixture holds two identical weigh-ins of a deleted measurement.
        val twins = state.measurementLogs.filter { it.measurementId == "deleted_measurement" }
        assertEquals(2, twins.size)
        val session = state.sessions.single()
        val shared = state.copy(sessions = listOf(session, session.copy(durationMinutes = 20)))
        val rows = stored(shared)
        assertEquals(2, rows.rows().count { it.kind == "measurementLogs" && it.body.contains("deleted_measurement") })
        assertEquals(2, rows.rows().count { it.kind == "sessions" })
        assertEquals(shared, read(rows))
        // Dropping one of the twins deletes one row, never both.
        val one = shared.copy(measurementLogs = shared.measurementLogs - twins.first())
        assertEquals(1, TrackerRows.changes(rows.rows(), one).deleted.size)
        // An edited one of two sessions sharing an ID keeps its row.
        val sessionRows = rows.rows().filter { it.kind == "sessions" }.map { it.id }
        val edited = TrackerRows.changes(rows.rows(), shared.copy(sessions = listOf(session, session.copy(durationMinutes = 25))))
        assertEquals(emptyList<RowKey>(), edited.deleted)
        assertEquals(listOf(sessionRows[1]), edited.written.map { it.id })
    }

    @Test
    fun aDayThatHappensOnceKeepsItsRowWhenItChanges() {
        val rows = stored(state)
        val day = state.checklistDays.single()
        val ticked = state.copy(checklistDays = listOf(day.copy(ticks = day.ticks + "day/later")))
        val changes = TrackerRows.changes(rows.rows(), ticked)
        assertEquals(listOf(RowKey("checklistDays", "${day.checklistId}/${day.dateKey}")), changes.written.map { RowKey(it.kind, it.id) })
        assertEquals(emptyList<RowKey>(), changes.deleted)
    }

    @Test
    fun rowsThatContradictThemselvesAreRefused() {
        val rows = stored(state).rows()
        val view = rows.first { it.kind == "measurements" }
        // A row's ID is only its name: a view under another one reads as the same view.
        assertEquals(state, read(MemoryRowStore(rows - view + view.copy(id = "elsewhere"))))
        val cases = listOf(
            rows + rows.first { it.kind == "measurementLogs" }.copy(id = "twin"),
            rows + StoredRow("unknownKind", "x", "{}", 0),
            rows + StoredRow("substances", "second", rows.first { it.kind == "substances" }.body, 0),
            rows.filterNot { it.kind == "substances" },
        )
        cases.forEachIndexed { index, broken ->
            assertTrue("case $index", runCatching { AppStateJsonCodec.decodeVersion32(TrackerRows.assemble(broken).first) }.isFailure)
        }
    }

    @Test
    fun theUpgradeKeepsTheDocumentFirstAndRemovesItOnlyOnceTheRowsHoldIt() {
        val document = mapOf(
            PreferencesTrackerRepository.STATE_V32_KEY to AppStateJsonCodec.encode(state),
            PreferencesTrackerRepository.RECORDED_TIME_SESSIONS_KEY to RecordedTimeSessionsCodec.encode(state.recordedTimeSessions),
        )
        val store = CurrentTrackerStore(document)
        val kept = mutableListOf<Map<String, String>>()
        val repository = trackerRepository(store, rows = store.rows, keepBeforeRows = { kept += it; true })

        assertEquals(state, repository.loadIfReadable())
        assertEquals(listOf(document), kept)
        assertEquals(emptySet<String>(), store.keys())
        // The marker lives only until its document is removed.
        assertNull(store.rows.meta(PreferencesTrackerRepository.ImportedKey))
        assertEquals(state, repository.loadIfReadable())
        assertEquals(1, kept.size)
    }

    @Test
    fun anUpgradeThatCannotFinishLeavesTheDocumentAsItWas() {
        val document = mapOf(
            PreferencesTrackerRepository.STATE_V32_KEY to AppStateJsonCodec.encode(state),
            PreferencesTrackerRepository.RECORDED_TIME_SESSIONS_KEY to RecordedTimeSessionsCodec.encode(state.recordedTimeSessions),
        )
        // No copy kept: no rows, and the document stays and reads.
        val noCopy = CurrentTrackerStore(document)
        assertEquals(state, trackerRepository(noCopy, rows = noCopy.rows, keepBeforeRows = { false }).loadIfReadable())
        assertEquals(document, noCopy.values)
        assertTrue(noCopy.rows.rows().isEmpty())

        // The rows committed, the document not yet removed: the next read removes it as spent.
        val stopped = CurrentTrackerStore(document)
        stopped.rows.commit(TrackerRows.changes(emptyList(), state).copy(meta = mapOf(
            PreferencesTrackerRepository.FormatKey to PreferencesTrackerRepository.RowsFormat,
            PreferencesTrackerRepository.ImportedKey to fingerprint(document),
        )))
        assertEquals(state, trackerRepository(stopped, rows = stopped.rows).loadIfReadable())
        assertEquals(emptySet<String>(), stopped.keys())
        assertNull(stopped.rows.meta(PreferencesTrackerRepository.ImportedKey))
    }

    /** While the document could not become rows, it is read but never written over: a save beside it would leave two copies. */
    @Test
    fun anUpgradeThatCannotStartRefusesEveryWrite() {
        val document = document(state)
        val store = CurrentTrackerStore(document)
        val repository = trackerRepository(store, rows = store.rows, keepBeforeRows = { false })
        assertEquals(state, repository.loadIfReadable())
        assertTrue(!repository.save(AppState()))
        assertEquals(de.nicolasmahn.momentum.persistence.TrackerUpdateResult.SaveFailed, repository.update { it.copy(measurementLogs = emptyList()) })
        assertEquals(document, store.values)
        assertTrue(store.rows.rows().isEmpty())
    }

    /** The import that made the rows is finished; the same document restored later by an older build is not taken as its leftover. */
    @Test
    fun theOriginalDocumentRestoredAfterTheImportIsNeverRemoved() {
        val document = document(state)
        val store = CurrentTrackerStore(document)
        val repository = trackerRepository(store, rows = store.rows)
        assertEquals(state, repository.loadIfReadable())
        assertTrue(repository.save(state.copy(measurementLogs = emptyList())))
        store.values += document
        val before = store.everything
        assertNull(repository.loadIfReadable())
        assertEquals(before, store.everything)
    }

    /**
     * While the document's removal has not reached the disk, the marker stays and nothing is
     * written: a save now would leave rows the marker's document could later be taken as spent beside.
     */
    @Test
    fun anImportWhoseCleanupFailsRefusesWritesUntilItIsFinished() {
        val document = document(state)
        val store = CurrentTrackerStore(document).apply { removable = false }
        val repository = trackerRepository(store, rows = store.rows)
        assertEquals(state, repository.loadIfReadable())
        assertTrue(!repository.save(AppState()))
        // A second read sees no keys in memory, and still does not retire the marker.
        assertEquals(state, repository.loadIfReadable())
        assertTrue(!repository.save(AppState()))
        assertEquals(fingerprint(document), store.rows.meta(PreferencesTrackerRepository.ImportedKey))

        store.removable = true
        assertEquals(state, repository.loadIfReadable())
        assertNull(store.rows.meta(PreferencesTrackerRepository.ImportedKey))
        assertTrue(repository.save(AppState()))
    }

    @Test
    fun emptyRowsThatOnceHeldTheStateAreNotANewInstallation() {
        listOf(
            mapOf(PreferencesTrackerRepository.FormatKey to PreferencesTrackerRepository.RowsFormat),
            mapOf(PreferencesTrackerRepository.FormatKey to "rows-2"),
            mapOf(PreferencesTrackerRepository.ImportedKey to "a document"),
        ).forEach { meta ->
            val store = CurrentTrackerStore()
            store.rows.commit(RowChanges(meta = meta))
            val repository = trackerRepository(store, rows = store.rows)
            assertNull(meta.toString(), repository.loadIfReadable())
            assertTrue(store.rows.rows().isEmpty())
        }
    }

    @Test
    fun aDamagedTimerLedgerIsRefusedBeforeAnythingIsWritten() {
        val store = CurrentTrackerStore()
        val repository = trackerRepository(store, rows = store.rows)
        assertTrue(repository.save(state))
        assertTrue(!repository.save(state.copy(recordedTimeSessions = setOf(-1L))))
        assertEquals(state, repository.loadIfReadable())
    }

    @Test
    fun aTimerDayKeepsItsRowWhenMinutesAreAdded() {
        val rows = stored(state)
        val log = state.timeLogs.first { it.viewId == "tim_custom" }
        val more = state.copy(timeLogs = state.timeLogs.map { if (it == log) it.copy(minutes = 60) else it })
        val changes = TrackerRows.changes(rows.rows(), more)
        assertEquals(listOf(RowKey("timeLogs", "tim_custom/${log.dateKey}")), changes.written.map { RowKey(it.kind, it.id) })
        assertEquals(emptyList<RowKey>(), changes.deleted)
    }

    @Test
    fun aRowWrittenOtherwiseThanABackupWritesItIsRefused() {
        val values = CurrentBackupFixture.rowsSnapshot().stores.getValue(RowsBackupStore.StoreName)
        val (key, raw) = values.entries.first { it.key != RowsBackupStore.FormatEntry }
        val pretty = values + (key to JSONObject(raw).toString(2))
        assertTrue(runCatching { RowsBackupStore.rows(pretty) }.isFailure)
        RowsBackupStore.rows(values)
    }

    private fun document(state: AppState) = mapOf(
        PreferencesTrackerRepository.STATE_V32_KEY to AppStateJsonCodec.encode(state),
        PreferencesTrackerRepository.RECORDED_TIME_SESSIONS_KEY to RecordedTimeSessionsCodec.encode(state.recordedTimeSessions),
    )

    /** An older build restoring a backup leaves its document beside rows it never knew; which one is the person's is not guessed. */
    @Test
    fun aDocumentTheRowsWereNotMadeFromIsNeverRemoved() {
        val other = state.copy(measurementLogs = state.measurementLogs + MeasurementLog("msr_custom", "2026-10-20", 200, 1.0))
        val document = mapOf(
            PreferencesTrackerRepository.STATE_V32_KEY to AppStateJsonCodec.encode(other),
            PreferencesTrackerRepository.RECORDED_TIME_SESSIONS_KEY to RecordedTimeSessionsCodec.encode(other.recordedTimeSessions),
        )
        val store = CurrentTrackerStore(document)
        store.rows.commit(TrackerRows.changes(emptyList(), state).copy(meta = mapOf(
            PreferencesTrackerRepository.FormatKey to PreferencesTrackerRepository.RowsFormat,
            PreferencesTrackerRepository.ImportedKey to fingerprint(mapOf(PreferencesTrackerRepository.STATE_V32_KEY to "an earlier document")),
        )))
        val before = store.everything
        val repository = trackerRepository(store, rows = store.rows)
        assertNull(repository.loadIfReadable())
        assertTrue(!repository.save(state))
        assertEquals(before, store.everything)
    }

    @Test
    fun aCurrentBackupHoldsRowsAndOneHoldingBothIsRefused() {
        validateRestoreCompatibility(CurrentBackupFixture.rowsSnapshot())
        validateRestoreCompatibility(CurrentBackupFixture.snapshot())
        val both = CurrentBackupFixture.rowsSnapshot().let { rows ->
            val document = CurrentBackupFixture.snapshot().stores.getValue(PreferencesTrackerRepository.PREFERENCES_NAME)
            rows.copy(stores = rows.stores + (PreferencesTrackerRepository.PREFERENCES_NAME to document))
        }
        assertTrue(runCatching { validateRestoreCompatibility(both) }.isFailure)
        val moved = CurrentBackupFixture.rowsSnapshot().let { snapshot ->
            val values = snapshot.stores.getValue(RowsBackupStore.StoreName)
            val (key, raw) = values.entries.first { it.key.startsWith("measurements/") }
            snapshot.copy(stores = snapshot.stores + (RowsBackupStore.StoreName to (values - key + ("measurements/elsewhere" to raw))))
        }
        validateRestoreCompatibility(moved)
        val position = JSONObject(CurrentBackupFixture.rowsSnapshot().stores.getValue(RowsBackupStore.StoreName).values.first { it.contains("position") }).getLong("position")
        assertTrue(position >= 0)
    }
}

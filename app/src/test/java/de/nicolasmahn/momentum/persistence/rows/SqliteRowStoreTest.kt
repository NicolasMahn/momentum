package de.nicolasmahn.momentum.persistence.rows

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import de.nicolasmahn.momentum.persistence.CurrentTrackerStore
import de.nicolasmahn.momentum.persistence.backup.CurrentBackupFixture
import de.nicolasmahn.momentum.persistence.trackerRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SqliteRowStoreTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private var now = 1_000L
    private val store = SqliteRowStore(context, name = "rows-test.db", clock = { now })

    @Test
    fun aCommitWritesMovesAndDeletesTogether() {
        val a = StoredRow("measurementLogs", "a", """{"value":1}""", 0)
        val b = StoredRow("measurementLogs", "b", """{"value":2}""", 1)
        assertTrue(store.commit(RowChanges(written = listOf(a, b), meta = mapOf("trackerFormat" to "rows-1"))))
        assertTrue(store.commit(RowChanges(moved = listOf(b.copy(position = 0), a.copy(position = 1)))))
        assertEquals(listOf(b.copy(position = 0), a.copy(position = 1)), store.rows())

        assertTrue(store.commit(RowChanges(deleted = listOf(RowKey("measurementLogs", "a")))))
        assertEquals(listOf(b.copy(position = 0)), store.rows())
        assertEquals("rows-1", store.meta("trackerFormat"))
        // The tombstone stays for a server, its body emptied.
        assertEquals(listOf("" to 1), rawRows("a"))
    }

    @Test
    fun aRestoreReplacesEveryRowAndItsLayoutExactly() {
        store.commit(RowChanges(written = listOf(StoredRow("timeLogs", "old", "{}", 0)), meta = mapOf("trackerFormat" to "rows-1")))
        val restored = listOf(StoredRow("measurements", "m", """{"id":"m"}""", 0))
        store.replaceAll(restored, mapOf("trackerFormat" to "rows-1"))
        assertEquals(restored to mapOf("trackerFormat" to "rows-1"), store.snapshot(setOf("trackerFormat")))
        // Restoring a backup from before the rows empties them and forgets the layout.
        store.replaceAll(emptyList(), mapOf("trackerFormat" to null))
        assertEquals(emptyList<StoredRow>() to emptyMap<String, String>(), store.snapshot(setOf("trackerFormat")))
    }

    @Test
    fun theClockNeverGoesBackEvenWhenThePhonesDoes() {
        store.commit(RowChanges(written = listOf(StoredRow("timeLogs", "a", "{}", 0))))
        now = 500L
        store.commit(RowChanges(written = listOf(StoredRow("timeLogs", "b", "{}", 1))))
        val (first, second) = listOf("a", "b").map { id -> changed(id) }
        assertTrue("$first < $second", first < second)
    }

    @Test
    fun aFullCounterMovesTheClockOn() {
        assertEquals("000000000001001-00000-a", Hlc.next("000000000001000-99999-a", 1_000, "a"))
    }

    /**
     * A database that came from another phone, by Android's transfer, takes a device ID of its own
     * and forgets where it was in syncing, so two phones never stamp alike; its rows stay as they are.
     */
    @Test
    fun aDatabaseFromAnotherPhoneGetsItsOwnDevice() {
        store.commit(RowChanges(written = listOf(StoredRow("timeLogs", "a", "{}", 0)), meta = mapOf(SyncMarks.Cursor to "7", SyncMarks.Held to "restored")))
        val first = store.device()
        assertEquals(first, SqliteRowStore(context, name = "rows-test.db").device())

        java.io.File(context.noBackupFilesDir, "rows-test.db.device").delete()
        val moved = SqliteRowStore(context, name = "rows-test.db")
        assertTrue(moved.device() != first)
        assertEquals(null, moved.meta(SyncMarks.Cursor))
        // A restore's hold is not where it was in syncing: it stays until someone decides.
        assertEquals("restored", moved.meta(SyncMarks.Held))
        assertEquals(listOf(StoredRow("timeLogs", "a", "{}", 0)), moved.rows())
    }

    @Test
    fun theWholeStateRoundTripsThroughTheDatabase() {
        val preferences = CurrentTrackerStore()
        val repository = trackerRepository(preferences, rows = store)
        assertTrue(repository.save(CurrentBackupFixture.state))
        assertEquals(CurrentBackupFixture.state, repository.loadIfReadable())
        assertEquals(CurrentBackupFixture.state, trackerRepository(preferences, rows = SqliteRowStore(context, name = "rows-test.db")).loadIfReadable())
    }

    private fun database(): SQLiteDatabase = SQLiteDatabase.openDatabase(context.getDatabasePath("rows-test.db").path, null, SQLiteDatabase.OPEN_READONLY)

    private fun rawRows(id: String): List<Pair<String, Int>> = database().use { db ->
        db.rawQuery("SELECT body, deleted FROM entity WHERE id = ?", arrayOf(id)).use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.getString(0) to cursor.getInt(1)) }
        }
    }

    private fun changed(id: String): String = database().use { db ->
        db.rawQuery("SELECT changed FROM entity WHERE id = ?", arrayOf(id)).use { it.moveToFirst(); it.getString(0) }
    }
}

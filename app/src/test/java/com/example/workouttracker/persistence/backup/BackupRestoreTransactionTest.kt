package com.example.workouttracker.persistence.backup

import org.junit.Assert.*
import org.junit.Test

class BackupRestoreTransactionTest {
    private val target = CurrentBackupFixture.snapshot()

    @Test fun restoreReplacesEveryStoreAndRemovesObsoleteKeys() {
        val storage = Storage()
        BackupRestoreTransaction(storage).restore(target)
        assertEquals(target, storage.readSnapshot())
        assertNull(storage.journal)
        assertEquals(listOf("journal") + BackupArchive.StoreNames + listOf("verify", "clear", "verify"), storage.operations)
    }

    @Test fun interruptedStoreWritesRollForwardOnRestart() {
        BackupArchive.StoreNames.forEach { interruptedStore ->
            val storage = Storage().apply { failStore = interruptedStore }
            assertTrue(runCatching { BackupRestoreTransaction(storage).restore(target) }.isFailure)
            assertEquals(target, storage.journal)
            storage.failStore = null
            assertTrue(BackupRestoreTransaction(storage).recover())
            assertEquals(target, storage.readSnapshot())
            assertNull(storage.journal)
            assertFalse(BackupRestoreTransaction(storage).recover())
        }
    }

    @Test fun journalWriteFailureNeverStartsReplacingStores() {
        val storage = Storage().apply { failJournal = true }
        val before = storage.readSnapshot()
        assertTrue(runCatching { BackupRestoreTransaction(storage).restore(target) }.isFailure)
        assertEquals(before, storage.readSnapshot())
        assertNull(storage.journal)
        assertFalse(storage.operations.any { it in BackupArchive.StoreNames })
    }

    @Test fun interruptedJournalClearCanBeReplayedSafely() {
        val storage = Storage().apply { failClear = true }
        assertTrue(runCatching { BackupRestoreTransaction(storage).restore(target) }.isFailure)
        assertEquals(target, storage.readSnapshot())
        assertEquals(target, storage.journal)
        storage.failClear = false
        assertTrue(BackupRestoreTransaction(storage).recover())
        assertEquals(target, storage.readSnapshot())
        assertNull(storage.journal)
    }

    @Test fun mismatchedReadbackRetainsJournalUntilSuccessfulRecovery() {
        val storage = Storage().apply { discardStore = BackupArchive.StoreNames.first() }
        assertTrue(runCatching { BackupRestoreTransaction(storage).restore(target) }.isFailure)
        assertEquals(target, storage.journal)
        assertFalse(storage.operations.contains("clear"))
        storage.discardStore = null
        assertTrue(BackupRestoreTransaction(storage).recover())
        assertEquals(target, storage.readSnapshot())
    }

    @Test fun invalidSnapshotNeverWritesAnything() {
        val invalid = listOf(
            BackupSnapshot(emptyMap()),
            BackupSnapshot(target.stores - BackupArchive.StoreNames.first()),
            BackupSnapshot(target.stores + ("unknown" to emptyMap())),
            BackupSnapshot(target.stores + ("workout_tracker" to mapOf("state_v30" to "{}"))),
            BackupSnapshot(target.stores + ("workout_tracker" to mapOf("state_v32" to "{}"))),
            BackupSnapshot(target.stores + ("workout_tracker" to mapOf("state_v32" to "{broken", "reading_sessions_recorded_v1" to "[]"))),
        )
        invalid.forEach { snapshot ->
            val storage = Storage()
            assertTrue(runCatching { BackupRestoreTransaction(storage).restore(snapshot) }.isFailure)
            assertTrue(storage.operations.isEmpty())
            storage.journal = snapshot
            assertTrue(runCatching { BackupRestoreTransaction(storage).recover() }.isFailure)
            assertTrue(storage.operations.isEmpty())
            assertEquals(snapshot, storage.journal)
        }
    }

    @Test fun absentJournalDoesNotTouchStores() {
        val storage = Storage()
        assertFalse(BackupRestoreTransaction(storage).recover())
        assertTrue(storage.operations.isEmpty())
    }

    private class Storage : BackupRestoreStorage {
        var journal: BackupSnapshot? = null
        var failJournal = false
        var failClear = false
        var failStore: String? = null
        var discardStore: String? = null
        val operations = mutableListOf<String>()
        private val stores = BackupArchive.StoreNames.associateWith {
            mapOf("obsolete" to "old value")
        }.toMutableMap()

        override fun readJournal() = journal
        override fun writeJournal(snapshot: BackupSnapshot) {
            operations += "journal"
            if (failJournal) error("Interrupted journal write")
            journal = snapshot
        }
        override fun clearJournal() {
            operations += "clear"
            if (failClear) error("Interrupted journal removal")
            journal = null
        }
        override fun replaceStore(name: String, values: Map<String, String>) {
            operations += name
            if (name == failStore) {
                stores[name] = emptyMap()
                error("Interrupted store replacement")
            }
            if (name != discardStore) stores[name] = values.toMap()
        }
        override fun readSnapshot(): BackupSnapshot {
            operations += "verify"
            return BackupSnapshot(stores.toMap())
        }
    }
}

package de.nicolasmahn.momentum.persistence.backup

/** Operations must complete durably before returning. The caller holds the persistence lock. */
interface BackupRestoreStorage {
    fun readJournal(): BackupSnapshot?
    fun writeJournal(snapshot: BackupSnapshot)
    fun clearJournal()
    fun replaceStore(name: String, values: Map<String, String>)
    fun readSnapshot(): BackupSnapshot
}

/** A durable journal lets startup finish a restore interrupted between independent stores. */
class BackupRestoreTransaction(private val storage: BackupRestoreStorage) {
    fun recover(): Boolean {
        val pending = storage.readJournal() ?: return false
        replay(validatedCopy(pending))
        return true
    }

    fun restore(snapshot: BackupSnapshot) {
        val target = validatedCopy(snapshot)
        storage.writeJournal(target)
        replay(target)
    }

    private fun replay(snapshot: BackupSnapshot) {
        snapshot.stores.forEach { (name, values) -> storage.replaceStore(name, values) }
        check(storage.readSnapshot() == snapshot) { "Restored data did not match the backup" }
        storage.clearJournal()
    }

    private fun validatedCopy(snapshot: BackupSnapshot): BackupSnapshot {
        // A store added after schema 32 that the snapshot lacks is restored empty.
        return BackupArchive.withAddedStores(snapshot.stores.mapValues { (_, values) -> values.toMap() }).also(::validateRestoreCompatibility)
    }
}

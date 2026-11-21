package com.example.workouttracker.persistence.backup

/** A file in the backup folder, as the folder names it. Never a folder inside it. */
data class BackupFile(val id: String, val name: String)

/**
 * The backup folder, outside the application's uninstallable private storage.
 *
 * Each write must create a new generation, close and flush its output before returning an opaque
 * identifier, and leave every previous generation unchanged even when writing fails; it never
 * overwrites one. Reads must reopen the persisted destination, not return a cached input buffer,
 * and report missing, inaccessible, or incomplete data as failures. Successful readback proves
 * current readability; remote durability depends on the chosen provider.
 *
 * [delete] returns only when the folder confirms the removal and throws otherwise, also for a
 * generation that was gone already. [list] names the files directly in the folder and no folder
 * inside it, since removing a folder would remove everything in it. Which generations may be
 * deleted is not decided here: see [BackupGenerations].
 */
interface BackupDestination {
    fun writeNew(name: String, bytes: ByteArray): String
    fun read(id: String): ByteArray
    fun delete(id: String)
    fun list(): List<BackupFile>
}

class VerifiedBackupWriter(private val destination: BackupDestination) {
    /** Returns a generation identifier only after persisted readback and application-level restore validation. */
    fun write(snapshot: BackupSnapshot, name: String): String {
        validateRestoreCompatibility(snapshot)
        val encoded = BackupArchive.encode(snapshot)
        val id = destination.writeNew(name, encoded)
        check(BackupArchive.decode(destination.read(id)) == BackupArchive.withAddedStores(snapshot.stores)) {
            "Backup readback does not match the saved records"
        }
        return id
    }

    /**
     * Keeps an exact, read-back copy of data that may not be restorable, such as damaged records
     * about to be replaced by a restore. It is never counted as a verified backup.
     */
    fun preserve(snapshot: BackupSnapshot, name: String): String {
        val id = destination.writeNew(name, BackupArchive.encode(snapshot))
        check(BackupArchive.decode(destination.read(id)) == BackupArchive.withAddedStores(snapshot.stores)) {
            "Backup readback does not match the saved records"
        }
        return id
    }
}

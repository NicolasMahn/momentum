package com.example.workouttracker.persistence.backup

import java.io.IOException

/** A folder as Android's file provider behaves: an id is the file's place, and a removal that did not happen throws. */
internal class FakeBackupFolder : BackupDestination {
    data class File(val name: String, val bytes: ByteArray)
    val files = linkedMapOf<String, File>()
    var interruptWrite = false
    var refuseDelete = false
    var refuseList = false
    /** A listing that does not show the newest file yet. */
    var listLagsBehind = false
    /** Runs as a file is read, the moment a save can slip in during a backup. */
    var whileReading: () -> Unit = {}

    fun put(name: String, bytes: ByteArray = ByteArray(1)): String {
        check(name !in files) { "The folder holds $name already" }
        files[name] = File(name, bytes)
        return name
    }
    fun decoded(id: String) = BackupArchive.decode(files.getValue(id).bytes)

    override fun writeNew(name: String, bytes: ByteArray): String {
        put(name, if (interruptWrite) bytes.copyOf(bytes.size / 2) else bytes.copyOf())
        if (interruptWrite) throw IOException("Interrupted write")
        return name
    }
    override fun read(id: String): ByteArray {
        whileReading()
        return files[id]?.bytes?.copyOf() ?: throw IOException("Gone")
    }
    override fun delete(id: String) {
        if (refuseDelete || files.remove(id) == null) throw IOException("Not removed")
    }
    override fun list(): List<BackupFile> {
        if (refuseList) throw IOException("Cannot list")
        return files.map { (id, file) -> BackupFile(id, file.name) }.let { if (listLagsBehind) it.dropLast(1) else it }
    }
}

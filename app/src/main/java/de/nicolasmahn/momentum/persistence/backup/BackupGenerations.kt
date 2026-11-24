package de.nicolasmahn.momentum.persistence.backup

import java.time.Instant
import java.util.UUID

/** One verified backup this installation wrote and still keeps. */
internal data class Generation(val id: String, val writtenAt: Instant)

/**
 * Where an installation remembers the generations it wrote, in the order it wrote them. A write
 * is durable before it returns. It is the whole list of files the installation removes by itself.
 */
internal interface GenerationIndex {
    fun read(): List<Generation>
    fun write(generations: List<Generation>)
}

/**
 * What tidying the backups of earlier versions does in one folder, decided when it was read:
 * exactly [remove] goes, and only while every one of [keep] can still be read.
 */
internal data class OldBackupTidy(val found: Int, val remove: Set<String>, val keep: Set<String>)

/**
 * The backups of one installation in its folder: writes one when the records changed, and keeps
 * only those [backupsToKeep] names.
 *
 * It removes by its own index and never by a file's name. Another installation's backups, a file
 * somebody put there, what a failed write left behind and the copy made before a restore are not
 * in the index, so nothing here removes them. Only [tidy], which a person confirms, goes by name,
 * and only by the name versions before the keep rule gave.
 */
internal class BackupGenerations(
    private val destination: BackupDestination,
    private val index: GenerationIndex,
    private val now: () -> Instant = Instant::now,
    private val newSuffix: () -> String = { UUID.randomUUID().toString().take(8) },
) {
    private val writer = VerifiedBackupWriter(destination)

    /**
     * Returns once a verified generation holds [snapshot]. Unchanged records get no new file: the
     * generation written last is read back instead, which proves the same thing.
     */
    fun hold(snapshot: BackupSnapshot) {
        validateRestoreCompatibility(snapshot)
        val last = index.read().lastOrNull()
        if (last != null && holds(last, snapshot)) thinOut(verified = last) else write(snapshot)
    }

    /** Writes a new verified generation whatever the folder holds, which also proves the folder can be written. */
    fun write(snapshot: BackupSnapshot) {
        val writtenAt = now()
        // The random part keeps a name from ever being given twice, so an id in the index can
        // only mean the file this installation wrote.
        val generation = Generation(writer.write(snapshot, BackupFileNames.backup(writtenAt, newSuffix())), writtenAt)
        index.write(index.read() + generation)
        thinOut(verified = generation)
    }

    /** Keeps an exact copy of records a restore is about to replace. It is no generation: nothing counts on it or removes it. */
    fun preserve(snapshot: BackupSnapshot) {
        writer.preserve(snapshot, BackupFileNames.beforeRestore(now()))
    }

    /**
     * What tidying would do to the backups earlier versions left in the folder. The rule goes by
     * their names, so a file it would keep is read first: one that does not [count][counts] keeps
     * nothing else from being kept and is itself left where it is.
     */
    fun oldBackupTidy(): OldBackupTidy {
        val old = destination.list()
            .mapNotNull { file -> BackupFileNames.writtenBeforeKeepRule(file.name)?.let { file.id to it } }
            .toMap()
        var candidates = old.keys
        val counted = mutableMapOf<String, Boolean>()
        while (true) {
            val keep = backupsToKeep(candidates) { old.getValue(it) }
            // Nothing to remove needs nothing read, which is every time after the pile is gone.
            if (keep.size == candidates.size) return OldBackupTidy(old.size, remove = emptySet(), keep)
            val setAside = keep.filterNot { counted.getOrPut(it) { counts(it) } }
            if (setAside.isEmpty()) return OldBackupTidy(old.size, remove = candidates - keep, keep)
            candidates = candidates - setAside.toSet()
        }
    }

    /** Removes exactly what [tidy] names. Throws, before or after removing some, if the folder no longer is as [tidy] found it. */
    fun tidy(tidy: OldBackupTidy) {
        check(tidy.keep.all(::counts)) { "A backup that was to stay can no longer be read" }
        tidy.remove.mapNotNull { runCatching { destination.delete(it) }.exceptionOrNull() }.firstOrNull()?.let { throw it }
    }

    /**
     * Whether an old backup may stand for its day or month: one this app can restore, or a
     * readable one from before schema 32. A file that merely reads does not count. The copy made
     * before a restore holds the damaged records the restore replaced, and standing for its day
     * it would cost the last good backup written before it.
     */
    private fun counts(id: String): Boolean = runCatching {
        val snapshot = BackupArchive.decode(destination.read(id))
        isFromBeforeSchema32(snapshot) || runCatching { validateRestoreCompatibility(snapshot) }.isSuccess
    }.getOrDefault(false)

    private fun holds(generation: Generation, snapshot: BackupSnapshot): Boolean = runCatching {
        BackupArchive.decode(destination.read(generation.id)) == BackupArchive.withAddedStores(snapshot.stores)
    }.getOrDefault(false)

    /**
     * Removes what the rule no longer keeps. Trouble here is no failed backup: [verified] already
     * holds the records, and whatever could not be removed is tried again after the next one.
     */
    private fun thinOut(verified: Generation) {
        runCatching { removeWhatTheRuleNoLongerKeeps(verified) }
    }

    private fun removeWhatTheRuleNoLongerKeeps(verified: Generation) {
        // A folder that cannot be listed says nothing about what is gone, so nothing is removed.
        val present = destination.list().mapTo(mutableSetOf()) { it.id }
        val indexed = index.read()
        // A generation somebody removed by hand must not stand for its day or month any longer.
        val existing = indexed.filter { it.id in present || it == verified }
        // Days and months go by the time in the name. The last writes are kept by the order they
        // were written in, since a phone clock set back makes the newest look old.
        val kept = backupsToKeep(existing.asReversed(), Generation::writtenAt) + existing.takeLast(KeptNewest)
        val remaining = existing.filter { it in kept || runCatching { destination.delete(it.id) }.isFailure }
        if (remaining != indexed) index.write(remaining)
    }
}

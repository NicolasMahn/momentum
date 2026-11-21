package com.example.workouttracker.persistence.backup

import java.time.Instant

/**
 * How backup files are named. A name starts with the moment it was written, so a file manager
 * sorted by name shows the newest last, and it ends in `.momentum.json`, which Restore asks for.
 */
internal object BackupFileNames {
    /** [suffix] is eight random hex digits, so no two backups ever share a name, whoever wrote them. */
    fun backup(writtenAt: Instant, suffix: String): String = "momentum-${stamp(writtenAt)}-$suffix$Extension"

    /** The copy of the records a restore is about to replace. Its name says so, and nothing ever removes it. */
    fun beforeRestore(writtenAt: Instant): String = "momentum-before-restore-${stamp(writtenAt)}$Extension"

    /**
     * When a backup named by a version before the keep rule was written, or null for any other
     * file. Those versions ended every name in a random UUID and never removed a file, so the
     * shape tells their pile apart from the backups this version looks after itself.
     */
    fun writtenBeforeKeepRule(name: String): Instant? {
        val (date, hour, minute, second) = BeforeKeepRule.matchEntire(name)?.destructured ?: return null
        return runCatching { Instant.parse("${date}T$hour:$minute:${second}Z") }.getOrNull()
    }

    // A colon is not allowed in a file name on every storage.
    private fun stamp(writtenAt: Instant) = writtenAt.toString().replace(':', '-')

    private const val Extension = ".momentum.json"
    private val BeforeKeepRule = Regex(
        """momentum-(\d{4}-\d{2}-\d{2})T(\d{2})-(\d{2})-(\d{2}(?:\.\d+)?)Z-[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}\.momentum\.json""",
    )
}

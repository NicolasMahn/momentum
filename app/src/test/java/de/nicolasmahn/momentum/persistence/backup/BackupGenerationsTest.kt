package de.nicolasmahn.momentum.persistence.backup

import de.nicolasmahn.momentum.persistence.PreferencesHabitRuleStartRepository
import de.nicolasmahn.momentum.persistence.PreferencesTrackerRepository
import java.io.IOException
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupGenerationsTest {
    private val folder = FakeBackupFolder()
    private val index = Index()
    private var now = Instant.parse("2026-11-18T08:00:00Z")
    private var written = 0
    private val generations = BackupGenerations(folder, index, now = { now }, newSuffix = { "%08x".format(++written) })

    private val records = CurrentBackupFixture.snapshot()
    private var changes = 0L

    /** The records after one more save. Each call differs from every one before it. */
    private fun changedRecords(): BackupSnapshot {
        val tracker = records.stores.getValue(PreferencesTrackerRepository.PREFERENCES_NAME)
        val ruleStart = LocalDate.parse("2000-01-01").plusDays(++changes).toString()
        return BackupSnapshot(records.stores + (PreferencesTrackerRepository.PREFERENCES_NAME to
            tracker + (PreferencesHabitRuleStartRepository.SettingsKey to ruleStart)))
    }

    /** Saving every hour, each save changing the records. */
    private fun useFor(days: Int) = repeat(days * 24) { saveAfter(Duration.ofHours(1)) }

    private fun saveAfter(pause: Duration): BackupSnapshot {
        now = now.plus(pause)
        return changedRecords().also(generations::hold)
    }

    private fun indexedFiles() = index.read().map { it.id }.toSet()

    @Test fun unchangedRecordsGetNoNewFile() {
        generations.hold(records)
        now = now.plusSeconds(60)

        generations.hold(records)

        assertEquals(1, folder.files.size)
        assertEquals(1, index.read().size)
    }

    @Test fun changedRecordsGetANewFileThatHoldsThem() {
        generations.hold(records)

        val later = saveAfter(Duration.ofMinutes(1))

        assertEquals(2, folder.files.size)
        assertEquals(later, folder.decoded(index.read().last().id))
    }

    @Test fun aLastBackupThatIsGoneIsReplaced() {
        generations.hold(records)
        folder.files.clear()
        now = now.plusSeconds(60)

        generations.hold(records)

        assertEquals(records, folder.decoded(index.read().single().id))
    }

    @Test fun aDamagedLastBackupIsNotTakenForTheRecords() {
        generations.hold(records)
        val id = index.read().single().id
        folder.files[id] = folder.files.getValue(id).let { it.copy(bytes = it.bytes.copyOf(it.bytes.size / 2)) }
        now = now.plusSeconds(60)

        generations.hold(records)

        assertEquals(records, folder.decoded(index.read().last().id))
    }

    @Test fun monthsOfUseLeaveOnlyWhatTheRuleKeeps() {
        useFor(days = 100)

        val kept = index.read()
        assertEquals(backupsToKeep(kept, Generation::writtenAt), kept.toSet())
        assertTrue(kept.size <= KeptNewest + KeptDays + KeptMonths)
        assertEquals(indexedFiles(), folder.files.keys)
    }

    @Test fun filesTheInstallationDidNotWriteAreNeverRemoved() {
        val foreign = listOf(
            // Another installation's backup, a backup from an earlier version, the copy made
            // before a restore, and something else a person keeps in the folder.
            BackupFileNames.backup(Instant.parse("2026-11-01T08:00:00Z"), "0badf00d"),
            oldName(Instant.parse("2026-09-17T21:49:15.063Z")),
            BackupFileNames.beforeRestore(Instant.parse("2026-10-25T22:05:00Z")),
            "notes.txt",
        ).map { folder.put(it) }

        useFor(days = 100)

        assertTrue(folder.files.keys.containsAll(foreign))
    }

    @Test fun aBackupThatCannotBeRemovedIsNoFailedBackupAndIsRemovedLater() {
        generations.hold(records)
        folder.refuseDelete = true
        useFor(days = 10)
        val piled = folder.files.size
        assertEquals(piled, index.read().size)

        folder.refuseDelete = false
        saveAfter(Duration.ofMinutes(1))

        assertTrue(folder.files.size < piled)
        assertEquals(indexedFiles(), folder.files.keys)
    }

    @Test fun aFolderThatCannotBeListedLosesNothing() {
        useFor(days = 3)
        folder.refuseList = true
        val before = folder.files.keys.toSet()

        useFor(days = 3)

        assertTrue(folder.files.keys.containsAll(before))
        assertTrue(indexedFiles().containsAll(before))
    }

    @Test fun aListingThatDoesNotShowTheNewBackupYetDoesNotMakeItForgotten() {
        folder.listLagsBehind = true
        generations.hold(records)

        generations.hold(records)

        assertEquals(1, folder.files.size)
        assertEquals(folder.files.keys, indexedFiles())
    }

    @Test fun aFailedWriteRemovesNothingAndIsNotCounted() {
        useFor(days = 3)
        val before = index.read()
        val filesBefore = folder.files.toMap()
        folder.interruptWrite = true
        now = now.plusSeconds(60)

        assertTrue(runCatching { generations.hold(changedRecords()) }.isFailure)

        assertEquals(before, index.read())
        assertEquals(filesBefore, folder.files.filterKeys { it in filesBefore })
        // What the broken write left behind is in the folder, and is nobody's generation.
        assertEquals(1, (folder.files.keys - filesBefore.keys).size)
    }

    @Test fun aBackupTheIndexCouldNotRecordFailsAndCostsNoEarlierOne() {
        useFor(days = 3)
        val before = folder.files.keys.toSet()
        index.refuseWrite = true
        now = now.plusSeconds(60)

        assertTrue(runCatching { generations.hold(changedRecords()) }.isFailure)

        assertTrue(folder.files.keys.containsAll(before))
    }

    /** The app stopped after removing a file and before the index knew. */
    @Test fun aGenerationThatIsAlreadyGoneLeavesTheIndexAndTakesNoOtherFileWithIt() {
        useFor(days = 3)
        val gone = index.read().first().id
        folder.files.remove(gone)
        val others = folder.files.keys.toSet()

        saveAfter(Duration.ofMinutes(1))

        assertFalse(gone in indexedFiles())
        assertEquals(indexedFiles(), folder.files.keys)
        assertTrue(backupsToKeep(index.read(), Generation::writtenAt).map { it.id }.containsAll(others intersect folder.files.keys))
    }

    /** Without this, a month whose backup somebody deleted would still count as one of the three. */
    @Test fun aMonthWhoseBackupWasRemovedByHandDoesNotCostAnEarlierMonth() {
        now = Instant.parse("2026-02-10T08:00:00Z")
        generations.hold(changedRecords())
        val february = index.read().single().id
        now = Instant.parse("2026-03-10T08:00:00Z")
        generations.hold(changedRecords())
        val march = index.read().last().id
        now = Instant.parse("2026-04-01T08:00:00Z")
        repeat(8) { saveAfter(Duration.ofDays(1)) }
        folder.files.remove(march)

        now = Instant.parse("2026-05-01T08:00:00Z")
        generations.hold(changedRecords())

        assertTrue("February is one of the three latest months that still have a backup", february in folder.files)
    }

    @Test fun aPhoneClockSetBackCostsNeitherOfTheLastTwoBackups() {
        useFor(days = 3)
        now = Instant.parse("2020-01-01T00:00:00Z")
        val first = saveAfter(Duration.ofMinutes(1))
        val second = saveAfter(Duration.ofMinutes(1))

        val files = folder.files.size
        generations.hold(second)

        assertEquals("the last write is recognized whatever its time says", files, folder.files.size)
        assertEquals(listOf(first, second), index.read().takeLast(2).map { folder.decoded(it.id) })
    }

    @Test fun theCopyMadeBeforeARestoreIsNoGeneration() {
        generations.preserve(records)

        assertEquals(1, folder.files.size)
        assertTrue(index.read().isEmpty())
        assertEquals(null, BackupFileNames.writtenBeforeKeepRule(folder.files.values.single().name))
    }

    @Test fun aNewBackupsNameIsNotTakenForAnEarlierVersions() {
        generations.hold(records)

        assertEquals(null, BackupFileNames.writtenBeforeKeepRule(folder.files.values.single().name))
        assertEquals(
            Instant.parse("2026-09-17T21:49:15.063Z"),
            BackupFileNames.writtenBeforeKeepRule("momentum-2026-09-17T21-49-15.063Z-6fa6caf4-63f9-4e33-ad70-b19f47f12b53.momentum.json"),
        )
    }

    private val oldStart = Instant.parse("2026-09-17T08:00:00Z")
    private fun oldName(writtenAt: Instant) =
        "momentum-${writtenAt.toString().replace(':', '-')}-6fa6caf4-63f9-4e33-ad70-b19f47f12b53.momentum.json"

    /** [count] backups as an earlier version left them, three hours apart, oldest first. */
    private fun oldPile(count: Int): Map<String, Instant> = (0 until count).associate { step ->
        val writtenAt = oldStart.plus(Duration.ofHours(step * 3L))
        folder.put(oldName(writtenAt), BackupArchive.encode(records)) to writtenAt
    }

    @Test fun tidyingAppliesTheRuleToEarlierVersionsBackupsAndToNothingElse() {
        val old = oldPile(400)
        val untouched = listOf(folder.put("notes.txt"), folder.put(BackupFileNames.beforeRestore(oldStart)))
        useFor(days = 2)
        val own = indexedFiles()
        val expected = backupsToKeep(old.keys) { old.getValue(it) }

        val tidy = generations.oldBackupTidy()
        assertEquals(OldBackupTidy(found = 400, remove = old.keys - expected, keep = expected), tidy)
        generations.tidy(tidy)

        assertEquals(expected + untouched + own, folder.files.keys)
        assertTrue(generations.oldBackupTidy().remove.isEmpty())
    }

    /** Going by names alone would keep two broken files as the newest and remove the last good backup. */
    @Test fun tidyingNeverKeepsAnUnreadableBackupInPlaceOfAReadableOne() {
        val old = oldPile(100).keys.toList()
        val broken = old.takeLast(2).onEach { folder.files[it] = folder.files.getValue(it).copy(bytes = ByteArray(10)) }
        val newestReadable = old.dropLast(2).takeLast(2)

        generations.tidy(generations.oldBackupTidy())

        assertTrue("the newest backups that can be read stay", folder.files.keys.containsAll(newestReadable))
        assertTrue("what cannot be read is left where it is", folder.files.keys.containsAll(broken))
        assertTrue(folder.files.size <= KeptNewest + KeptDays + KeptMonths + broken.size)
    }

    /**
     * The copy made before a restore reads, and holds the damaged records the restore replaced.
     * As the last file of its day it would stand for that day, and the last good backup before
     * the damage would go, with whatever the restored older state no longer holds.
     */
    @Test fun tidyingNeverLetsTheCopyMadeBeforeARestoreStandForItsDay() {
        val damaged = BackupSnapshot(records.stores + (PreferencesTrackerRepository.PREFERENCES_NAME to
            records.stores.getValue(PreferencesTrackerRepository.PREFERENCES_NAME) + (PreferencesTrackerRepository.STATE_V32_KEY to "{malformed")))
        fun old(writtenAt: String, snapshot: BackupSnapshot) = folder.put(oldName(Instant.parse(writtenAt)), BackupArchive.encode(snapshot))
        val superseded = old("2026-11-15T09:00:00Z", records)
        val dayBefore = old("2026-11-15T10:00:00Z", records)
        val lastGood = old("2026-11-16T09:00:00Z", records)
        val copy = old("2026-11-16T10:00:00Z", damaged)
        val afterRestore = old("2026-11-17T09:00:00Z", changedRecords())

        generations.tidy(generations.oldBackupTidy())

        assertEquals(setOf(dayBefore, lastGood, copy, afterRestore), folder.files.keys)
        assertFalse(superseded in folder.files)
    }

    /** The same before schema 32, where the damaged state sits above a readable copy from before a migration. */
    @Test fun tidyingNeverLetsACopyWithADamagedEarlierStateStandForItsDay() {
        fun earlierVersion(state: String) = BackupSnapshot(mapOf(
            "workout_tracker" to mapOf("state_v7" to state, "state_v1" to """{"weightLogs":[],"sessions":[]}"""),
            "momentum_reminders" to emptyMap(),
            "momentum_feature_visibility" to emptyMap(),
        ))
        fun old(writtenAt: String, state: String) = folder.put(oldName(Instant.parse(writtenAt)), BackupArchive.encode(earlierVersion(state)))
        val superseded = old("2026-09-16T09:00:00Z", """{"weightLogs":[]}""")
        val dayBefore = old("2026-09-16T10:00:00Z", """{"weightLogs":[]}""")
        val lastGood = old("2026-09-17T09:00:00Z", """{"weightLogs":[{"date":"2026-09-17"}]}""")
        val copy = old("2026-09-17T10:00:00Z", "{malformed")
        val afterRestore = old("2026-09-18T09:00:00Z", """{"weightLogs":[]}""")

        generations.tidy(generations.oldBackupTidy())

        assertEquals(setOf(dayBefore, lastGood, copy, afterRestore), folder.files.keys)
        assertFalse(superseded in folder.files)
    }

    /** A backup from before schema 32 cannot be restored by this app, and is still the record of its day. */
    @Test fun tidyingCountsBackupsFromBeforeSchema32AndThinsThemLikeAnyOther() {
        val earlierVersion = BackupArchive.encode(BackupSnapshot(mapOf(
            "workout_tracker" to mapOf("state_v7" to """{"weightLogs":[],"sessions":[]}"""),
            "momentum_reminders" to emptyMap(),
            "momentum_feature_visibility" to emptyMap(),
        )))
        val old = (0 until 200).associate { step ->
            val writtenAt = oldStart.plus(Duration.ofHours(step * 3L))
            folder.put(oldName(writtenAt), earlierVersion) to writtenAt
        }

        generations.tidy(generations.oldBackupTidy())

        assertEquals(backupsToKeep(old.keys) { old.getValue(it) }, folder.files.keys)
    }

    @Test fun tidyingStopsWhenABackupThatWasToStayCanNoLongerBeRead() {
        oldPile(100)
        val tidy = generations.oldBackupTidy()
        val files = folder.files.keys.toSet()
        folder.files.remove(tidy.keep.first())

        assertTrue(runCatching { generations.tidy(tidy) }.isFailure)

        assertEquals(files - tidy.keep.first(), folder.files.keys)
    }

    @Test fun tidyingRemovesOnlyWhatWasCountedWhenItWasShown() {
        oldPile(100)
        val tidy = generations.oldBackupTidy()
        val arrivedLater = folder.put(oldName(oldStart.minus(Duration.ofDays(30))), BackupArchive.encode(records))

        generations.tidy(tidy)

        assertTrue(arrivedLater in folder.files)
        assertEquals(tidy.keep + arrivedLater, folder.files.keys)
    }

    @Test fun tidyingReportsABackupItCouldNotRemove() {
        oldPile(100)
        folder.refuseDelete = true

        assertTrue(runCatching { generations.tidy(generations.oldBackupTidy()) }.isFailure)

        assertEquals(100, folder.files.size)
    }

    private class Index : GenerationIndex {
        private var stored = emptyList<Generation>()
        var refuseWrite = false
        override fun read() = stored
        override fun write(generations: List<Generation>) {
            if (refuseWrite) throw IOException("Cannot record")
            stored = generations
        }
    }
}

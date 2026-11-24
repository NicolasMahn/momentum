package de.nicolasmahn.momentum.persistence.backup

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import de.nicolasmahn.momentum.persistence.PreferencesHabitRuleStartRepository
import de.nicolasmahn.momentum.persistence.PreferencesTrackerRepository
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * What the backup status and the folders hold together, with the real preferences and stores and
 * folders in memory: what "backed up" may be said of, and whose files a change of folder or a
 * tidying may touch.
 */
@RunWith(RobolectricTestRunner::class)
class BackupFolderTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val folders = mutableMapOf<Uri, FakeBackupFolder>()
    private val manager = DurableBackupManager(context) { tree -> folders.getOrPut(tree, ::FakeBackupFolder) }

    private fun tree(name: String) = Uri.parse("content://com.android.externalstorage.documents/tree/primary%3A$name")
    private val documents = tree("Documents")
    private val download = tree("Download")
    private fun folder(tree: Uri) = folders.getOrPut(tree, ::FakeBackupFolder)

    private var saves = 0L

    /** One more save, as a repository makes it: the change is marked before it is stored. */
    private fun save() {
        manager.beforeChange()
        check(context.getSharedPreferences(PreferencesTrackerRepository.PREFERENCES_NAME, Context.MODE_PRIVATE).edit()
            .putString(PreferencesHabitRuleStartRepository.SettingsKey, LocalDate.parse("2000-01-01").plusDays(++saves).toString())
            .commit())
    }

    private fun oldBackupsIn(tree: Uri, count: Int) = repeat(count) { hour ->
        val stamp = "2026-09-${17 + hour / 24}T${"%02d".format(hour % 24)}-00-00Z"
        folder(tree).put("momentum-$stamp-6fa6caf4-63f9-4e33-ad70-b19f47f12b53.momentum.json", BackupArchive.encode(personalSnapshot(context)))
    }

    @Test fun backingUpAgainWithoutAChangeWritesNoFileAndStaysBackedUp() {
        manager.configure(documents)

        manager.backupNow()
        manager.backupNow()

        assertEquals(1, folder(documents).files.size)
        assertFalse(manager.status.value.pending)
        assertNull(manager.status.value.error)
    }

    @Test fun aSaveGetsItsOwnFile() {
        manager.configure(documents)

        save()
        manager.backupNow()

        assertEquals(2, folder(documents).files.size)
        assertFalse(manager.status.value.pending)
    }

    /** The file read back holds the records from before the save, so it must not pass for a backup of it. */
    @Test fun aSaveThatSlipsInWhileTheLastBackupIsReadBackStaysPending() {
        manager.configure(documents)
        folder(documents).whileReading = { folder(documents).whileReading = {}; save() }

        manager.backupNow()

        assertTrue(manager.status.value.pending)
        manager.backupNow()
        assertFalse(manager.status.value.pending)
        assertEquals(2, folder(documents).files.size)
    }

    @Test fun aLastBackupThatWasDeletedIsWrittenAgain() {
        manager.configure(documents)
        folder(documents).files.clear()

        manager.backupNow()

        assertEquals(1, folder(documents).files.size)
        assertFalse(manager.status.value.pending)
    }

    @Test fun aFolderThatCannotBeWrittenLeavesTheWorkingOneAsItWas() {
        manager.configure(documents)
        folder(download).interruptWrite = true

        assertTrue(runCatching { manager.configure(download) }.isFailure)

        // The working folder still counts its own backup: nothing new is needed there.
        manager.backupNow()
        assertEquals(1, folder(documents).files.size)
        assertFalse(manager.status.value.pending)
    }

    @Test fun backupsInAFolderThatIsNoLongerChosenAreLeftAlone() {
        manager.configure(documents)
        repeat(5) { save(); manager.backupNow() }
        val left = folder(documents).files.keys.toSet()

        manager.configure(download)
        repeat(5) { save(); manager.backupNow() }

        assertEquals(left, folder(documents).files.keys)
        // Everything was written on one day: the day's last backup and the one before it stay.
        assertEquals(KeptNewest, folder(download).files.size)
    }

    @Test fun choosingTheSameFolderAgainKeepsLookingAfterItsBackups() {
        manager.configure(documents)
        repeat(3) { save(); manager.backupNow() }

        manager.configure(documents)
        repeat(3) { save(); manager.backupNow() }

        assertEquals(KeptNewest, folder(documents).files.size)
    }

    @Test fun tidyingBacksUpTheRecordsBeforeItRemovesAnOldBackup() {
        manager.configure(documents)
        oldBackupsIn(documents, count = 60)
        val old = requireNotNull(manager.oldBackups())
        assertEquals(60, old.found)
        save()

        manager.tidy(old)

        assertFalse(manager.status.value.pending)
        assertEquals(60 - old.removable + KeptNewest, folder(documents).files.size)
    }

    @Test fun tidyingRemovesNothingWhenTheRecordsCannotBeBackedUpFirst() {
        manager.configure(documents)
        oldBackupsIn(documents, count = 60)
        val old = requireNotNull(manager.oldBackups())
        save()
        folder(documents).interruptWrite = true

        assertTrue(runCatching { manager.tidy(old) }.isFailure)

        assertEquals(60, folder(documents).files.values.count { BackupFileNames.writtenBeforeKeepRule(it.name) != null })
    }

    @Test fun whatWasCountedInOneFolderIsNotRemovedAfterAnotherIsChosen() {
        manager.configure(documents)
        oldBackupsIn(documents, count = 60)
        val old = requireNotNull(manager.oldBackups())
        manager.configure(download)

        assertTrue(runCatching { manager.tidy(old) }.isFailure)

        assertEquals(61, folder(documents).files.size)
    }
}

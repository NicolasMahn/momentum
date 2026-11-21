package com.example.workouttracker.persistence.backup

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
import com.example.workouttracker.domain.time.TimeLog
import com.example.workouttracker.persistence.PreferencesFeatureVisibilityRepository
import com.example.workouttracker.persistence.PreferencesHabitRuleStartRepository
import com.example.workouttracker.persistence.PreferencesHolidayRepository
import com.example.workouttracker.persistence.PreferencesPassChoiceRepository
import com.example.workouttracker.persistence.PreferencesReminderSettingsRepository
import com.example.workouttracker.persistence.PreferencesTrackerRepository
import com.example.workouttracker.persistence.rows.RowsBackupStore
import java.io.File
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Run each phase separately, with an actual disposable-package uninstall/reinstall between them:
 * `-Pandroid.testInstrumentationRunnerArguments.recoveryDrillPhase=seed|verify|revoked`.
 *
 * Every phase needs a person first (choosing a folder, reinstalling, copying the archive), so an
 * unattended suite run skips the drill instead of reporting a failure that says nothing. A phase
 * that was asked for still fails hard when its preparation is missing.
 */
class BackupRecoveryDeviceTest {
    private fun isolatedContext(phase: String): Context {
        assumeTrue(
            "Recovery drill phase '$phase' runs only when requested with recoveryDrillPhase",
            InstrumentationRegistry.getArguments().getString("recoveryDrillPhase") == phase,
        )
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName == "com.example.workouttracker.test") { "Only the disposable package may run this drill" }
        check(Build.MODEL.startsWith("sdk_gphone") || Build.FINGERPRINT.startsWith("generic")) {
            "Recovery drills require an Android emulator"
        }
        return context
    }

    @Test fun seedAndBackup() {
        val context = isolatedContext("seed")
        val manager = DurableBackupManager.get(context)
        assertTrue("Choose an emulator backup folder before the seed phase", manager.status.value.configured)
        assertTrue(PreferencesTrackerRepository(context).save(representativeState()))
        assertTrue(PreferencesReminderSettingsRepository(context).save(reminderSettings()))
        assertTrue(PreferencesFeatureVisibilityRepository(context).save(visibilitySettings()))
        assertTrue(PreferencesHolidayRepository(context).save(CurrentBackupFixture.holidays))
        assertTrue(PreferencesPassChoiceRepository(context).save(CurrentBackupFixture.passChoices))
        assertEquals(CurrentBackupFixture.ruleStart, PreferencesHabitRuleStartRepository(context).loadOrStart(CurrentBackupFixture.ruleStart))
        val saved = readSnapshot(context)
        validateRestoreCompatibility(saved)
        // The records are rows, and the backup carries them.
        assertTrue(saved.stores.getValue(RowsBackupStore.StoreName).isNotEmpty())
        manager.backupNow()
        assertFalse("Latest records must be verified outside app storage", manager.status.value.pending)
        assertNull(manager.status.value.error)
        assertNotNull(manager.status.value.lastVerifiedAt)
    }

    @Test fun verifyAfterReinstall() {
        val context = isolatedContext("verify")
        // These checks precede manager construction: neither a retained install nor restored config qualifies.
        assertTrue(context.getSharedPreferences("momentum_backup", Context.MODE_PRIVATE).all.isEmpty())
        assertTrue(readSnapshot(context).stores.values.all { it.isEmpty() })
        val manager = DurableBackupManager.get(context)
        assertFalse(manager.status.value.configured)
        val archive = File(context.filesDir, "recovery-drill.momentum.json")
        assertTrue("Copy the surviving external archive into the new installation before verification", archive.isFile)
        val snapshot = manager.readForRestore(Uri.fromFile(archive))
        assertTrue("The records must survive the uninstall as rows", snapshot.stores.getValue(RowsBackupStore.StoreName).isNotEmpty())
        manager.restore(snapshot)
        assertEquals("Every raw persisted value must come back exactly", snapshot, readSnapshot(context))
        assertEquals(representativeState(), PreferencesTrackerRepository(context).load())
        assertEquals(reminderSettings(), PreferencesReminderSettingsRepository(context).load())
        assertEquals(visibilitySettings(), PreferencesFeatureVisibilityRepository(context).load())
        assertEquals(CurrentBackupFixture.holidays, PreferencesHolidayRepository(context).load())
        assertEquals(CurrentBackupFixture.passChoices, PreferencesPassChoiceRepository(context).load())
        assertEquals(CurrentBackupFixture.ruleStart, PreferencesHabitRuleStartRepository(context).loadOrStart(CurrentBackupFixture.ruleStart.plusDays(1)))
        assertFalse("Restore must not invent a new backup configuration", manager.status.value.configured)
    }

    @Test fun revokedFolderAccessNeverReportsProtected() {
        val context = isolatedContext("revoked")
        val manager = DurableBackupManager.get(context)
        assertTrue("Choose an emulator backup folder before this phase", manager.status.value.configured)
        manager.backupNow()
        val preferences = context.getSharedPreferences("momentum_backup", Context.MODE_PRIVATE)
        val tree = Uri.parse(checkNotNull(preferences.getString("tree", null)))
        context.contentResolver.releasePersistableUriPermission(
            tree, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )
        assertFalse(context.contentResolver.persistedUriPermissions.any { it.uri == tree })
        // Serialize with any startup backup that was already running when access was revoked.
        assertTrue("The revoked destination must fail", runCatching { manager.backupNow() }.isFailure)
        val lastVerified = manager.status.value.lastVerifiedAt
        assertNotNull(lastVerified)
        val changed = representativeState().copy(timeLogs = listOf(TimeLog("tim_custom", "2026-10-15", 206, 30)))
        val repository = PreferencesTrackerRepository(context)
        assertTrue("A backup failure must preserve the local save", repository.save(changed))
        assertEquals(changed, repository.load())
        assertTrue("The explicit retry must also fail", runCatching { manager.backupNow() }.isFailure)
        val status = manager.status.value
        assertTrue(status.pending)
        assertNotNull(status.error)
        assertEquals(lastVerified, status.lastVerifiedAt)
        assertTrue("Failure must survive process restart", preferences.getBoolean("pending", false))
        assertNotNull(preferences.getString("error", null))
        assertEquals(lastVerified, preferences.getString("verified", null))
    }

    /** As a backup captures it, the records database included. */
    private fun readSnapshot(context: Context) = personalSnapshot(context)
    private fun visibilitySettings() = CurrentBackupFixture.visibility
    private fun reminderSettings() = CurrentBackupFixture.reminders
    private fun representativeState() = CurrentBackupFixture.state
}

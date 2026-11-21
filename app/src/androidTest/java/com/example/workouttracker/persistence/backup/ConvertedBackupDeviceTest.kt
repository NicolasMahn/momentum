package com.example.workouttracker.persistence.backup

import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
import com.example.workouttracker.persistence.PreferencesTrackerRepository
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/** The host supplies a converted copy only for an explicitly isolated emulator run. */
class ConvertedBackupDeviceTest {
    @Test fun convertedBackupRestoresEveryStoredValueAndReloadsAfterSaving() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        assumeTrue(InstrumentationRegistry.getArguments().getString("convertedBackup") == "true")
        val context = instrumentation.targetContext
        check(context.packageName == "com.example.workouttracker.test")
        check(Build.MODEL.startsWith("sdk_gphone") || Build.FINGERPRINT.startsWith("generic"))
        val file = File(context.filesDir, "converted-backup.json")
        assertTrue("Supply the external conversion before starting this test", file.isFile)
        val manager = DurableBackupManager.get(context)
        val snapshot = manager.readForRestore(Uri.fromFile(file))
        manager.restore(snapshot)
        assertEquals(snapshot, snapshot(context))

        val repository = PreferencesTrackerRepository(context)
        val before = repository.loadIfReadable()
        assertNotNull(before)
        assertTrue(repository.save(requireNotNull(before)))
        assertEquals(before, PreferencesTrackerRepository(context).loadIfReadable())
        val saved = snapshot(context)
        validateRestoreCompatibility(saved)
        // After the first read the records are rows, so the backup must carry them.
        assertTrue(saved.stores.getValue(com.example.workouttracker.persistence.rows.RowsBackupStore.StoreName).isNotEmpty())
        assertEquals(saved, BackupArchive.decode(BackupArchive.encode(saved)))
    }

    /** As a backup captures it, the records database included. */
    private fun snapshot(context: Context) = personalSnapshot(context)
}

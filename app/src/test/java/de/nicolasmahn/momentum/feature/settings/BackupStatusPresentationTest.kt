package de.nicolasmahn.momentum.feature.settings

import de.nicolasmahn.momentum.persistence.backup.BackupError
import de.nicolasmahn.momentum.persistence.backup.BackupStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class BackupStatusPresentationTest {
    private val verified = BackupStatus(true, "2026-09-17T12:00:00Z", false, null)

    @Test fun currentVerifiedCopyHasNoWarningEvenAfterDelay() {
        assertEquals(BackupPresentation.Verified, backupPresentation(verified, delayed = false))
        assertEquals(BackupPresentation.Verified, backupPresentation(verified, delayed = true))
    }

    @Test fun previousVerifiedCopyDoesNotHideNewPendingChanges() {
        val pending = verified.copy(pending = true, pendingSinceMillis = 1_000)
        assertEquals(BackupPresentation.BackingUp, backupPresentation(pending, delayed = false))
        assertEquals(BackupPresentation.Delayed, backupPresentation(pending, delayed = true))
    }

    @Test fun noVerifiedCopyCannotAppearProtected() {
        val missingVerification = verified.copy(lastVerifiedAt = null)
        assertEquals(BackupPresentation.BackingUp, backupPresentation(missingVerification, false))
        assertEquals(BackupPresentation.Delayed, backupPresentation(missingVerification, true))
    }

    @Test fun setupIsVisibleWithoutWaiting() {
        val unconfigured = verified.copy(configured = false, lastVerifiedAt = null, pending = true)
        assertEquals(BackupPresentation.NeedsSetup, backupPresentation(unconfigured, false))
        assertEquals(BackupPresentation.NeedsSetup, backupPresentation(unconfigured, true))
    }

    @Test fun failureTakesPrecedenceOverPendingSetupAndPreviousVerification() {
        for (configured in listOf(false, true)) {
            for (pending in listOf(false, true)) {
                val failed = verified.copy(configured = configured, pending = pending, error = BackupError.WriteFailed)
                assertEquals(BackupPresentation.Failed, backupPresentation(failed, false))
                assertEquals(BackupPresentation.Failed, backupPresentation(failed, true))
            }
        }
    }

    @Test fun aStoredLegacySentenceStillReadsAsTheWriteFailure() {
        val legacy = "Backup failed. Latest changes are only on this installation. Check folder access and retry."
        assertEquals(BackupError.WriteFailed, BackupError.fromStored(legacy))
        assertEquals(BackupError.WriteFailed, BackupError.fromStored(BackupError.WriteFailed.code))
    }

    @Test fun warningDelayUsesOriginalPendingTimeAndThirtySecondBoundary() {
        val pending = verified.copy(pending = true, pendingSinceMillis = 1_000)
        assertEquals(30_000L, backupWarningDelayMillis(pending, 1_000))
        assertEquals(1L, backupWarningDelayMillis(pending, 30_999))
        assertEquals(0L, backupWarningDelayMillis(pending, 31_000))
        assertEquals(0L, backupWarningDelayMillis(pending, 91_000))
        assertEquals(10_000L, backupWarningDelayMillis(pending.copy(lastVerifiedAt = "2026-09-17T12:01:00Z"), 21_000))
    }

    @Test fun missingOrFuturePendingTimeCannotCreateNegativeOrUnboundedDelay() {
        assertEquals(30_000L, backupWarningDelayMillis(verified.copy(pending = true), 50_000))
        assertEquals(30_000L, backupWarningDelayMillis(verified.copy(pending = true, pendingSinceMillis = 90_000), 50_000))
    }
}

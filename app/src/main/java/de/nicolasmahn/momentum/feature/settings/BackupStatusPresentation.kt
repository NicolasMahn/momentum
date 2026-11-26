package de.nicolasmahn.momentum.feature.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.nicolasmahn.momentum.R
import de.nicolasmahn.momentum.persistence.backup.BackupError
import de.nicolasmahn.momentum.persistence.backup.BackupSetupException
import de.nicolasmahn.momentum.persistence.backup.BackupStatus
import kotlinx.coroutines.delay

internal const val BackupDelayWarningMillis = 30_000L

internal enum class BackupPresentation { NeedsSetup, BackingUp, Delayed, Failed, Verified }

internal fun backupPresentation(status: BackupStatus, delayed: Boolean): BackupPresentation = when {
    status.error != null -> BackupPresentation.Failed
    !status.configured -> BackupPresentation.NeedsSetup
    status.pending || status.lastVerifiedAt == null ->
        if (delayed) BackupPresentation.Delayed else BackupPresentation.BackingUp
    else -> BackupPresentation.Verified
}

@get:StringRes
internal val BackupError.messageRes: Int
    get() = when (this) {
        BackupError.WriteFailed -> R.string.settings_backup_write_failed
    }

@get:StringRes
internal val BackupSetupException.Problem.messageRes: Int
    get() = when (this) {
        BackupSetupException.Problem.NotLocalFolder -> R.string.settings_backup_not_local_folder
        BackupSetupException.Problem.NoFolderChosen -> R.string.settings_backup_no_folder
    }

internal fun backupWarningDelayMillis(status: BackupStatus, nowMillis: Long): Long =
    (BackupDelayWarningMillis - (nowMillis - (status.pendingSinceMillis ?: nowMillis)))
        .coerceIn(0, BackupDelayWarningMillis)

@Composable
internal fun rememberBackupPresentation(status: BackupStatus): BackupPresentation {
    val startedAt = status.pendingSinceMillis
    val needsBackup = status.pending || status.lastVerifiedAt == null
    var delayElapsed by remember(startedAt, needsBackup) { mutableStateOf(false) }
    val remaining = backupWarningDelayMillis(status, System.currentTimeMillis())
    LaunchedEffect(startedAt, needsBackup, status.configured) {
        if (needsBackup && status.configured) {
            delay(backupWarningDelayMillis(status, System.currentTimeMillis()))
            delayElapsed = true
        }
    }
    return backupPresentation(status, delayed = delayElapsed || remaining == 0L)
}

/** Normal exports have no banner, placeholder, or animation that could move the current screen. */
@Composable
internal fun BackupWarning(status: BackupStatus, onOpenSettings: () -> Unit) {
    val presentation = rememberBackupPresentation(status)
    val message = when (presentation) {
        BackupPresentation.NeedsSetup -> R.string.settings_backup_warning_setup
        BackupPresentation.Delayed -> R.string.settings_backup_warning_delayed
        BackupPresentation.Failed -> R.string.settings_backup_warning_failed
        BackupPresentation.BackingUp, BackupPresentation.Verified -> return
    }
    val failure = presentation == BackupPresentation.Failed
    Surface(
        onClick = onOpenSettings,
        modifier = Modifier.fillMaxWidth().testTag("backup_warning"),
        color = if (failure) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = if (failure) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface,
    ) {
        Text(stringResource(message), modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.bodySmall)
    }
}

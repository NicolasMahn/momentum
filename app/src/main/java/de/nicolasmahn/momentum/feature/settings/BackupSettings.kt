package de.nicolasmahn.momentum.feature.settings

import android.text.format.DateFormat
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.nicolasmahn.momentum.R
import de.nicolasmahn.momentum.core.format.formatMoment
import de.nicolasmahn.momentum.core.locale.currentLocale
import de.nicolasmahn.momentum.core.text.UiText
import de.nicolasmahn.momentum.core.text.resolve
import de.nicolasmahn.momentum.core.text.uiText
import de.nicolasmahn.momentum.core.ui.MomentumButton
import de.nicolasmahn.momentum.core.ui.MomentumButtonVariant
import de.nicolasmahn.momentum.core.ui.MomentumCard
import de.nicolasmahn.momentum.core.ui.MomentumBlockingDialog
import de.nicolasmahn.momentum.core.ui.MomentumDialog
import de.nicolasmahn.momentum.core.ui.MomentumDialogAction
import de.nicolasmahn.momentum.core.ui.ReservedMessage
import de.nicolasmahn.momentum.persistence.backup.BackupSetupException
import de.nicolasmahn.momentum.persistence.backup.BackupSnapshot
import de.nicolasmahn.momentum.persistence.backup.DurableBackupManager
import de.nicolasmahn.momentum.persistence.backup.OldBackups
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun BackupSettings() {
    val context = LocalContext.current
    val manager = remember(context) { DurableBackupManager.get(context) }
    val status by manager.status.collectAsState()
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var restoring by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<UiText?>(null) }
    var restoreSnapshot by remember { mutableStateOf<BackupSnapshot?>(null) }
    var oldBackups by remember { mutableStateOf<OldBackups?>(null) }
    var tidying by remember { mutableStateOf<OldBackups?>(null) }

    // A folder that cannot be listed shows no pile; backing up reports that trouble itself.
    suspend fun findOldBackups() {
        oldBackups = withContext(Dispatchers.IO) { runCatching { manager.oldBackups() }.getOrNull() }
    }
    // The verification time also changes when another folder is chosen.
    LaunchedEffect(status.configured, status.lastVerifiedAt) { findOldBackups() }

    /** [failure] names what went wrong in the user's words; the technical cause goes to the log. */
    fun perform(@StringRes failure: Int, action: suspend () -> Unit) {
        busy = true
        message = null
        scope.launch {
            try {
                action()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (setup: BackupSetupException) {
                message = uiText(setup.problem.messageRes)
            } catch (cause: Exception) {
                // The checks that fail here (a checksum, a schema, folder access) are written for
                // whoever debugs them, not for the screen, so the screen says what it means.
                Log.w(BackupLogTag, "Backup action failed", cause)
                message = uiText(failure)
            } finally {
                busy = false
            }
        }
    }

    val chooseFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            perform(R.string.settings_backup_folder_failed) { withContext(Dispatchers.IO) { manager.configure(uri) } }
        }
    }
    val chooseRestore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            perform(R.string.settings_backup_unreadable) {
                restoreSnapshot = withContext(Dispatchers.IO) { manager.readForRestore(uri) }
            }
        }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .testTag("settings_group_backups"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val presentation = rememberBackupPresentation(status)
        // Unprotected records are the one thing on this screen that cannot wait, so in that state
        // choosing a folder is the screen's primary action rather than one option among many.
        val needsAttention = presentation == BackupPresentation.NeedsSetup ||
            presentation == BackupPresentation.Failed

        Text(stringResource(R.string.settings_backups_title), style = MaterialTheme.typography.titleMedium)
        // Whether records are actually protected is a single claim, so it keeps a ground of its own.
        MomentumCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                // Stored as a UTC instant, shown as the phone's clock reads it; a value this version
                // cannot read is shown as written, since a verified copy exists either way.
                val locale = currentLocale()
                Text(
                    status.lastVerifiedAt?.let { stored ->
                        val shown = runCatching { Instant.parse(stored) }.getOrNull()
                            ?.let { formatMoment(it, ZoneId.systemDefault(), locale, DateFormat.is24HourFormat(context)) }
                            ?: stored
                        stringResource(R.string.settings_backup_last_verified, shown)
                    } ?: stringResource(R.string.settings_backup_none_verified),
                )
                when (presentation) {
                    BackupPresentation.NeedsSetup -> Text(stringResource(R.string.settings_backup_needs_setup), color = MaterialTheme.colorScheme.error)
                    BackupPresentation.Failed -> Text(
                        stringResource(
                            R.string.settings_backup_needs_attention,
                            stringResource(status.error?.messageRes ?: R.string.settings_backup_write_failed),
                        ),
                        color = MaterialTheme.colorScheme.error,
                    )
                    BackupPresentation.BackingUp -> Text(stringResource(R.string.settings_backup_backing_up))
                    BackupPresentation.Delayed -> Text(stringResource(R.string.settings_backup_delayed))
                    BackupPresentation.Verified -> Text(stringResource(R.string.settings_backup_verified))
                }
            }
        }
        ReservedMessage(
            text = message?.resolve() ?: stringResource(R.string.settings_backup_working).takeIf { busy },
            testTag = "backup_message",
            color = if (message != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        MomentumButton(
            text = stringResource(
                if (status.configured) R.string.settings_backup_change_folder else R.string.settings_backup_choose_folder,
            ),
            onClick = { chooseFolder.launch(null) },
            variant = if (needsAttention) MomentumButtonVariant.Primary else MomentumButtonVariant.Secondary,
            enabled = !busy,
            testTag = "backup_choose_folder",
        )
        if (status.configured) {
            MomentumButton(
                text = stringResource(R.string.settings_backup_now),
                onClick = {
                    perform(R.string.settings_backup_write_failed) { withContext(Dispatchers.IO) { manager.backupNow() } }
                },
                variant = MomentumButtonVariant.Secondary,
                enabled = !busy,
            )
        }
        MomentumButton(
            text = stringResource(R.string.settings_backup_restore),
            onClick = { chooseRestore.launch(arrayOf("application/json")) },
            variant = MomentumButtonVariant.Quiet,
            enabled = !busy,
            testTag = "backup_restore",
        )
        // The caveats matter, but only after the reader knows whether they are covered right now.
        Text(
            stringResource(R.string.settings_backup_explanation),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            stringResource(R.string.settings_backup_kept),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // Last on the page, so that appearing once the folder has been read moves nothing.
        oldBackups?.takeIf { it.removable > 0 }?.let { old ->
            Text(pluralStringResource(R.plurals.settings_backup_old_found, old.found, old.found))
            MomentumButton(
                text = stringResource(R.string.settings_backup_tidy),
                onClick = { tidying = old },
                variant = MomentumButtonVariant.Secondary,
                enabled = !busy,
                testTag = "backup_tidy",
            )
        }
    }

    tidying?.let { old ->
        val staying = old.found - old.removable
        MomentumDialog(
            onDismissRequest = { tidying = null },
            title = pluralStringResource(R.plurals.settings_backup_tidy_title, old.removable, old.removable),
            message = pluralStringResource(R.plurals.settings_backup_tidy_message, staying, staying),
            confirm = MomentumDialogAction(
                text = stringResource(R.string.settings_backup_tidy_confirm),
                onClick = {
                    tidying = null
                    perform(R.string.settings_backup_tidy_failed) {
                        try {
                            withContext(Dispatchers.IO) { manager.tidy(old) }
                        } finally {
                            findOldBackups()
                        }
                    }
                },
                destructive = true,
                testTag = "backup_confirm_tidy",
            ),
            dismiss = MomentumDialogAction(
                text = stringResource(R.string.settings_cancel),
                onClick = { tidying = null },
            ),
        )
    }

    restoreSnapshot?.let { snapshot ->
        MomentumDialog(
            onDismissRequest = { restoreSnapshot = null },
            title = stringResource(R.string.settings_backup_restore_title),
            message = stringResource(R.string.settings_backup_restore_message),
            confirm = MomentumDialogAction(
                text = stringResource(R.string.settings_backup_restore_confirm),
                onClick = {
                    restoreSnapshot = null
                    restoring = true
                    perform(R.string.settings_backup_restore_failed) {
                        // Keep editing blocked until startup reloads stores or recovers the journal.
                        withContext(NonCancellable) {
                            try {
                                withContext(Dispatchers.IO) { manager.restore(snapshot) }
                            } finally {
                                context.hostActivity()?.recreate()
                            }
                        }
                    }
                },
                // Restoring discards every current record, so it reads as destructive.
                destructive = true,
                testTag = "backup_confirm_restore",
            ),
            dismiss = MomentumDialogAction(
                text = stringResource(R.string.settings_cancel),
                onClick = { restoreSnapshot = null },
            ),
        )
    }

    if (restoring) {
        MomentumBlockingDialog(message = stringResource(R.string.settings_backup_restoring))
    }
}

private const val BackupLogTag = "MomentumBackup"

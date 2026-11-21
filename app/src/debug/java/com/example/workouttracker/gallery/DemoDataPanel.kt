package com.example.workouttracker.gallery

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.workouttracker.core.time.currentDateKey
import com.example.workouttracker.core.ui.MomentumButton
import com.example.workouttracker.core.ui.MomentumButtonDefaults
import com.example.workouttracker.core.ui.MomentumButtonVariant
import com.example.workouttracker.core.ui.MomentumDialog
import com.example.workouttracker.core.ui.MomentumDialogAction
import com.example.workouttracker.core.ui.ReservedMessage
import com.example.workouttracker.domain.AppState
import com.example.workouttracker.persistence.AndroidConfigurationDefaults
import com.example.workouttracker.persistence.PreferencesTrackerRepository
import com.example.workouttracker.persistence.rows.SqliteRowStore
import com.example.workouttracker.persistence.SharedPreferencesTrackerStringStore
import com.example.workouttracker.persistence.TrackerRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Writes the real store of the *debug* app so the real screens, not only specimens, can be judged
 * against a full history. This file lives in `src/debug`, which the `personal` and `release` build
 * types do not include, so it cannot reach a real installation: that is the whole reason an
 * action that overwrites everything is allowed to exist at all.
 */
@Composable
internal fun DemoDataPanel(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val repository = remember(context) { demoRepository(context) }
    val scope = rememberCoroutineScope()
    var pendingAction by remember { mutableStateOf<DemoDataAction?>(null) }
    var outcome by remember { mutableStateOf<DemoDataOutcome?>(null) }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HorizontalDivider()
        Text(
            text = "Demo data",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        MomentumButton(
            text = "Fill app with demo data",
            onClick = { pendingAction = DemoDataAction.Fill },
            modifier = Modifier.fillMaxWidth(),
            variant = MomentumButtonVariant.Secondary,
        )
        MomentumButton(
            text = "Add or remove the second checklist",
            onClick = { scope.launch { outcome = withContext(Dispatchers.IO) { toggleSecondChecklist(repository) } } },
            modifier = Modifier.fillMaxWidth(),
            variant = MomentumButtonVariant.Secondary,
        )
        MomentumButton(
            text = "Clear app data",
            onClick = { pendingAction = DemoDataAction.Clear },
            modifier = Modifier.fillMaxWidth(),
            variant = MomentumButtonVariant.Secondary,
            colors = MomentumButtonDefaults.destructiveColors(MomentumButtonVariant.Secondary),
        )
        ReservedMessage(
            text = outcome?.message,
            color = if (outcome?.succeeded == false) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }

    pendingAction?.let { action ->
        DemoDataConfirmDialog(
            action = action,
            onDismiss = { pendingAction = null },
            onConfirm = {
                pendingAction = null
                // Building and writing a full history is disk and CPU work; the gallery stays live.
                scope.launch { outcome = withContext(Dispatchers.IO) { action.run(repository) } }
            },
        )
    }
}

private enum class DemoDataAction {
    Fill,
    Clear,
    ;

    fun run(repository: TrackerRepository): DemoDataOutcome {
        // The configurations stay: only records are invented or cleared.
        val current = repository.load()
        val state = when (this) {
            Fill -> demoHistory(
                currentDateKey(),
                supplements = current.supplementsChecklist ?: DemoSupplementsChecklist,
                care = current.careChecklist ?: DemoCareChecklist,
                weight = current.weightMeasurement ?: DemoWeightMeasurement,
                eating = current.eatingQuestions ?: DemoEatingQuestions,
                reading = current.readingTime ?: DemoReadingTime,
            ).let { demo ->
                demo.copy(
                    activity = current.activity,
                    checklists = demo.checklists + DemoSecondChecklist,
                    checklistDays = demo.checklistDays + demoSecondChecklistDays(currentDateKey(), DemoHistoryDayCount),
                )
            }
            Clear -> AppState(activity = current.activity, checklists = current.checklists, measurements = current.measurements, questions = current.questions, time = current.time)
        }
        val saved = runCatching { repository.save(state) }.getOrDefault(false)
        return DemoDataOutcome(
            succeeded = saved,
            message = when {
                !saved -> "Could not write the debug app's records."
                this == Fill -> "Filled with $DemoHistoryDayCount days of demo data. Reopen Momentum to see it."
                else -> "Cleared the debug app's records. Reopen Momentum to see it."
            },
        )
    }
}

private data class DemoDataOutcome(val succeeded: Boolean, val message: String)

/**
 * Adds the second checklist, or removes its configuration again. Its records stay either way, as
 * records of an unknown checklist do, so removing it can be seen to leave everything else as it was.
 */
private fun toggleSecondChecklist(repository: TrackerRepository): DemoDataOutcome {
    var added = false
    val result = repository.update { state ->
        added = state.checklists.none { it.id == DemoSecondChecklist.id }
        state.copy(checklists = if (added) state.checklists + DemoSecondChecklist else state.checklists.filterNot { it.id == DemoSecondChecklist.id })
    }
    val saved = result is com.example.workouttracker.persistence.TrackerUpdateResult.Updated
    return DemoDataOutcome(
        succeeded = saved,
        message = when {
            !saved -> "Could not write the debug app's records."
            added -> "Added the second checklist. Reopen Momentum to see it."
            else -> "Removed the second checklist; its records stay. Reopen Momentum to see it."
        },
    )
}

@Composable
private fun DemoDataConfirmDialog(
    action: DemoDataAction,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    MomentumDialog(
        onDismissRequest = onDismiss,
        message = when (action) {
            DemoDataAction.Fill ->
                "This replaces everything the debug app has recorded with $DemoHistoryDayCount days of invented history."

            DemoDataAction.Clear ->
                "This deletes everything the debug app has recorded."
        },
        confirm = MomentumDialogAction(
            text = when (action) {
                DemoDataAction.Fill -> "Replace"
                DemoDataAction.Clear -> "Delete"
            },
            onClick = onConfirm,
            destructive = true,
        ),
        dismiss = MomentumDialogAction(text = "Cancel", onClick = onDismiss),
    )
}

/**
 * The repository over the plain preference store and rows, without the DurableBackupManager the app wires
 * in: invented data must never be copied into a backup folder the debug app happens to point at,
 * and it has no revision worth protecting. Everything above this — the codec, the schema version,
 * the validation — is the app's own, so the state written here is state the app could have written.
 */
private fun demoRepository(context: Context): TrackerRepository = PreferencesTrackerRepository(
    SharedPreferencesTrackerStringStore(
        context.getSharedPreferences(
            PreferencesTrackerRepository.PREFERENCES_NAME,
            Context.MODE_PRIVATE,
        ),
    ),
    defaults = AndroidConfigurationDefaults(context),
    rows = SqliteRowStore(context.applicationContext),
    // Invented data needs no copy before the rows replace a document.
    keepBeforeRows = { true },
)

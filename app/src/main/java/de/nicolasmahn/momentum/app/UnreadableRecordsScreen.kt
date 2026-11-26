package de.nicolasmahn.momentum.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.nicolasmahn.momentum.R
import de.nicolasmahn.momentum.core.ui.MomentumButton
import de.nicolasmahn.momentum.feature.settings.BackupSettings

/**
 * Shown instead of the app when records exist but cannot be read. An empty Home would read as a
 * lost history and invite new records over it, so the only ways on are to retry or to restore a
 * backup; the stored payload is left exactly as it is.
 */
@Composable
internal fun UnreadableRecordsScreen(onRetry: () -> Unit) {
    // Outside the app's scaffold nothing else provides a content color for the text.
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        UnreadableRecordsContent(onRetry)
    }
}

@Composable
private fun UnreadableRecordsContent(onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
            .testTag("unreadable_records"),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.app_unreadable_records_message))
        MomentumButton(
            text = stringResource(R.string.app_unreadable_records_retry),
            onClick = onRetry,
            testTag = "unreadable_records_retry",
        )
        BackupSettings()
    }
}

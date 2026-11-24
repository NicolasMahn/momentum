package de.nicolasmahn.momentum.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.nicolasmahn.momentum.R
import de.nicolasmahn.momentum.core.ui.MomentumChoiceTile
import de.nicolasmahn.momentum.core.ui.MomentumChoiceTileRow
import de.nicolasmahn.momentum.domain.reminder.ReminderSettings
import de.nicolasmahn.momentum.domain.reminder.ReminderTime
import de.nicolasmahn.momentum.domain.reminder.usualNight

/**
 * What "after waking" and "before sleep" mean: the wake time on a day the phone has no alarm, and
 * the bedtime, eight hours before waking or a fixed time. Both times open Settings' time picker
 * through [onEditWake] and [onEditBedtime]; the bedtime can be edited only while it is fixed.
 */
@Composable
internal fun SleepSettings(
    settings: ReminderSettings,
    onEditWake: () -> Unit,
    onFixedBedtimeChange: (ReminderTime?) -> Unit,
    onEditBedtime: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val fixed = settings.fixedBedtime != null
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("settings_group_sleep"),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text(
            text = stringResource(R.string.settings_sleep_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            SleepTimeRow(
                label = stringResource(R.string.settings_sleep_wake),
                time = settings.fallbackAlarmTime,
                enabled = true,
                testTag = "settings_fallback_alarm_time",
                onEdit = onEditWake,
            )
            NoteText(text = stringResource(R.string.settings_sleep_wake_note), testTag = "settings_fallback_alarm_note")
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // The time stays in place while it follows waking, so choosing a fixed one moves nothing.
            SleepTimeRow(
                label = stringResource(R.string.settings_sleep_bedtime),
                time = settings.usualBedtime,
                enabled = fixed,
                testTag = "settings_bedtime_time",
                onEdit = onEditBedtime,
            )
            MomentumChoiceTileRow {
                MomentumChoiceTile(
                    label = stringResource(R.string.settings_sleep_bedtime_automatic),
                    selected = !fixed,
                    onClick = { if (fixed) onFixedBedtimeChange(null) },
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    testTag = "settings_bedtime_automatic",
                )
                // A fixed bedtime starts where the automatic one is, and the time button moves it.
                MomentumChoiceTile(
                    label = stringResource(R.string.settings_sleep_bedtime_fixed),
                    selected = fixed,
                    onClick = { if (!fixed) onFixedBedtimeChange(settings.usualBedtime) },
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    testTag = "settings_bedtime_fixed",
                )
            }
        }
    }
}

@Composable
private fun SleepTimeRow(
    label: String,
    time: ReminderTime,
    enabled: Boolean,
    testTag: String,
    onEdit: () -> Unit,
) {
    val shown = formatTime(time)
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text = label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        TimeButton(
            time = shown,
            description = stringResource(R.string.settings_time_at, label, shown),
            enabled = enabled,
            testTag = testTag,
            onClick = onEdit,
        )
    }
}

/** When sleep starts on a day without a phone alarm: the fixed bedtime, or eight hours before waking. */
internal val ReminderSettings.usualBedtime: ReminderTime
    get() = usualNight.asleepBy.let { ReminderTime(it.hour, it.minute) }

/** The wake and sleep times of a day without a phone alarm, as the Settings list shows them. */
@Composable
internal fun sleepSummary(settings: ReminderSettings): String =
    stringResource(R.string.settings_sleep_summary, formatTime(settings.fallbackAlarmTime), formatTime(settings.usualBedtime))

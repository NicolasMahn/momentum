package com.example.workouttracker.gallery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.workouttracker.core.ui.ViewTheme
import com.example.workouttracker.core.ui.theme.WorkoutTrackerTheme
import com.example.workouttracker.domain.reminder.ReminderSettings
import com.example.workouttracker.domain.reminder.ReminderTime
import com.example.workouttracker.feature.settings.SleepSettings
import com.example.workouttracker.feature.settings.settingsPresentation

internal val sleepSettingsSpecimens = listOf(
    GallerySpecimen("Sleep, 8 hours before waking") { SleepSettingsSpecimen(ReminderSettings(fallbackAlarmTime = ReminderTime(7, 0))) },
    GallerySpecimen("Sleep, fixed after midnight") {
        SleepSettingsSpecimen(ReminderSettings(fallbackAlarmTime = ReminderTime(7, 0), fixedBedtime = ReminderTime(0, 30)))
    },
)

/**
 * The real page on settings held here. Choosing a bedtime applies at once; the times themselves are
 * picked in Settings' time picker, which the gallery leaves out.
 */
@Composable
internal fun SleepSettingsSpecimen(initial: ReminderSettings) {
    var settings by remember { mutableStateOf(initial) }
    ViewTheme(settingsPresentation().colors) {
        SleepSettings(
            settings = settings,
            onEditWake = {},
            onFixedBedtimeChange = { settings = settings.copy(fixedBedtime = it) },
            onEditBedtime = {},
        )
    }
}

@Preview
@Composable
private fun SleepSettingsPreview() {
    WorkoutTrackerTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(32.dp)) {
                SleepSettingsSpecimen(ReminderSettings(fallbackAlarmTime = ReminderTime(7, 0)))
                SleepSettingsSpecimen(ReminderSettings(fallbackAlarmTime = ReminderTime(7, 0), fixedBedtime = ReminderTime(0, 30)))
            }
        }
    }
}

package com.example.workouttracker.gallery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.workouttracker.core.time.currentHabitDate
import com.example.workouttracker.core.ui.MomentumButton
import com.example.workouttracker.core.ui.MomentumButtonVariant
import com.example.workouttracker.core.ui.ReservedMessage
import com.example.workouttracker.domain.AppState
import com.example.workouttracker.domain.checklist.DayRecord
import com.example.workouttracker.domain.checklist.progressOn
import com.example.workouttracker.domain.holiday.HolidaySchedule
import com.example.workouttracker.reminder.TestReminder
import com.example.workouttracker.reminder.checklistTestReminder
import com.example.workouttracker.reminder.loggedTestReminder
import com.example.workouttracker.reminder.sendTestReminder
import java.time.LocalDate

internal val reminderNotificationSpecimens = listOf(
    GallerySpecimen("Reminder notifications, posted with their actions") { ReminderNotificationsSpecimen() },
)

/** The three reminders that carry an action, in the order the specimen offers them, each as "Send a test" would send it [today]. */
internal fun reminderNotificationTests(state: AppState, today: LocalDate): List<Pair<String, TestReminder?>> = listOf(
    "Weight, with Add weight" to state.weightMeasurement?.let { loggedTestReminder(it, today, HolidaySchedule()) },
    "Reading, with Start reading" to state.readingTime?.let { loggedTestReminder(it, today, HolidaySchedule()) },
    "Supplements, with Mark all done" to state.supplementsChecklist?.let { checklist ->
        val progress = checklist.progressOn(today, { DayRecord() }, HolidaySchedule())
        checklist.checkpoints.firstNotNullOfOrNull { checklistTestReminder(checklist, it, progress, today) }
    },
)

/**
 * A notification lives in the phone's shade, not in a screen, so this specimen posts the real one:
 * each button sends the fixture's view through the notifier its reminder uses, as "Send a test"
 * does, with nothing scheduled and a tap that opens the app where it was. Weight's carries the
 * "Add weight" reply, Reading's "Start reading" and Supplements' "Mark all done"; each answers
 * this debug installation's own records. The line under the buttons says when the phone refused,
 * as without the permission to notify.
 */
@Composable
private fun ReminderNotificationsSpecimen() {
    val context = LocalContext.current
    val tests = remember { reminderNotificationTests(fixtureAppState, currentHabitDate()) }
    var refused by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        tests.forEachIndexed { index, (label, test) ->
            MomentumButton(
                text = label,
                onClick = { refused = test?.let(context::sendTestReminder) != true },
                variant = MomentumButtonVariant.Quiet,
                enabled = test != null,
                testTag = "gallery_reminder_$index",
            )
        }
        ReservedMessage(
            text = "The phone did not post it. Allow notifications for this app.".takeIf { refused },
            testTag = "gallery_reminder_refused",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

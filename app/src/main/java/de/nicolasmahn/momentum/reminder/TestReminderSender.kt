package de.nicolasmahn.momentum.reminder

import android.content.Context
import android.text.format.DateFormat
import de.nicolasmahn.momentum.domain.checklist.DayCheckpointId
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/**
 * Posts [test] through the notifier the real reminder uses, and does nothing else: no alarm is set,
 * moved or cancelled, and none of the coordinators' bookkeeping is written, so the real reminder
 * still comes at its time exactly as it would have. It replaces a notification of the same kind on
 * show, as the next real one would. A checkpoint's test opens its view on a tap only where Settings
 * said Home can open it ([TestReminder.AtCheckpoint.opens]); otherwise the tap opens the app where
 * it was.
 *
 * A checklist's test and a logged view's are not remembered as on show either: each may come from
 * a draft nothing ever saves, and an entry for that would never be cleared. So a save in the app
 * does not take it away; it stays until swiped or until the next real reminder of its slot
 * replaces it. Where it replaced a real one that was on show, that one's entry is still there, so
 * the save that answers it takes the test down in its place. Activity's and Substances'
 * notifications are cancelled by their fixed IDs when a save answers them, so a test of theirs
 * goes as the real one does. Mo's check-in is taken down whenever the app resumes or saves with
 * nothing really overdue, so his test posts under its own ID and stays.
 *
 * False when the notification could not be posted, such as without permission.
 */
internal fun Context.sendTestReminder(test: TestReminder): Boolean = when (test) {
    is TestReminder.Checklist -> ChecklistReminderNotification(applicationContext).showTest(
        checklist = test.checklist,
        checkpoint = test.checkpoint,
        open = test.open,
        key = slotKey(test.checklist.id, test.checkpoint.id),
        dueDay = test.dueDay,
        opens = test.opens,
    )
    is TestReminder.Logged -> ChecklistReminderNotification(applicationContext).showTest(
        view = test.view,
        key = slotKey(test.view.kind.reminderKind, test.view.id, DayCheckpointId),
        days = test.days,
        opens = test.opens,
        minutesToGo = test.minutesToGo,
    )
    is TestReminder.Activity -> WorkoutReminderNotification(applicationContext).show(test.subject)
    is TestReminder.Substances -> SubstanceCheckInNotification(applicationContext).showTest(test.targetDateKey, test.opens)
    is TestReminder.MoCheckIn -> moNudgeMessage(
        mood = test.mood,
        overdue = test.overdue,
        // He fumes about the clock as it stands, as a nudge does about the moment it was set for.
        nudge = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES),
        is24Hour = DateFormat.is24HourFormat(applicationContext),
    )?.let { message -> MoCheckInNotification(applicationContext).showTest(message, test.mood) } ?: false
}

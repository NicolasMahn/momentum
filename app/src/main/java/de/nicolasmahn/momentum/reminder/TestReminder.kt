package de.nicolasmahn.momentum.reminder

import de.nicolasmahn.momentum.domain.checklist.ChecklistConfiguration
import de.nicolasmahn.momentum.domain.checklist.ChecklistItem
import de.nicolasmahn.momentum.domain.checklist.ChecklistProgress
import de.nicolasmahn.momentum.domain.checklist.Checkpoint
import de.nicolasmahn.momentum.domain.LoggedView
import de.nicolasmahn.momentum.domain.holiday.HolidaySchedule
import de.nicolasmahn.momentum.domain.isExcusedOn
import de.nicolasmahn.momentum.domain.mo.MoMood
import de.nicolasmahn.momentum.domain.TrackerRef
import de.nicolasmahn.momentum.domain.trackerRef
import de.nicolasmahn.momentum.domain.time.TimeConfiguration
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/**
 * A reminder sent on request from Settings, now, as it would arrive at its time: the same words,
 * face, tap and actions, through the notifier the real one uses. It holds only what that notifier
 * needs, so sending one sets no alarm and writes none of the bookkeeping that decides when the
 * real one comes; see [sendTestReminder].
 */
internal sealed interface TestReminder {
    /**
     * A checkpoint's reminder, whose words the Reminders page previews. Its tap [opens] the view
     * only where Settings says Home can open it today: stored, shown, with an item in use. A view
     * the draft alone makes usable, or the setup's, has no screen yet, so the tap opens the app
     * where it was; until Settings says otherwise through [opening], a test opens nothing.
     */
    sealed interface AtCheckpoint : TestReminder {
        val opens: Boolean

        /** What it says, in the tone [mood] gives Mo's voice. */
        fun words(mood: MoMood?): MoReminderMessage

        fun opening(opens: Boolean): AtCheckpoint
    }

    /**
     * A checklist's reminder on [dueDay], naming [open]: everything due at [checkpoint] in the
     * record that day asks about, as if all of it were still open. Preview and notification
     * share the same configured labels and kind wording.
     */
    data class Checklist(
        val checklist: ChecklistConfiguration,
        val checkpoint: Checkpoint,
        val open: List<ChecklistItem>,
        val dueDay: LocalDate,
        override val opens: Boolean = false,
    ) : AtCheckpoint {
        override fun words(mood: MoMood?): MoReminderMessage = checklistReminderMessage(checklist, checkpoint, open, checklist.reminderDaysOn(dueDay), mood)

        override fun opening(opens: Boolean): Checklist = copy(opens = opens)
    }

    /**
     * The reminder of a logged view, rung on the due day of [days], as if the day it asks about
     * were not logged yet; in Mo's voice while the view is a task of his. A time view's names
     * [minutesToGo], all its week asks, as if nothing were logged in it yet; the other kinds name
     * no number.
     */
    data class Logged(
        val view: LoggedView,
        val days: ReminderDays,
        override val opens: Boolean = false,
        val minutesToGo: Int? = null,
    ) : AtCheckpoint {
        override fun words(mood: MoMood?): MoReminderMessage = loggedReminderMessage(view, days, minutesToGo, mood)

        override fun opening(opens: Boolean): Logged = copy(opens = opens)
    }

    /** Activity's reminder as it would come today, naming the gym day next in turn while the gym is on. */
    data class Activity(val subject: MoReminderSubject.Activity) : TestReminder

    /**
     * Substances' check-in about [targetDateKey], yesterday, whose "None yesterday" records "none" for
     * it; its tap [opens] Substances on that day where Home shows it, and the app otherwise.
     */
    data class Substances(val targetDateKey: String, val opens: Boolean = true) : TestReminder

    /** Mo's check-in, fuming about [overdue]: every task he asks about today, as if all were still open. */
    data class MoCheckIn(val mood: MoMood, val overdue: List<TrackerRef>) : TestReminder
}

/**
 * What [checkpoint]'s reminder sends [today] if everything due there were still open, as the
 * coordinator hands it to its notifier. Null when nothing is due at this checkpoint: then the
 * reminder stays quiet and there is nothing to send. [progress] is that of the record today asks
 * about, today's own or yesterday's on a checklist about yesterday, read from the week's records,
 * so a week item is named only on a day skipping it would cost.
 */
internal fun checklistTestReminder(
    checklist: ChecklistConfiguration,
    checkpoint: Checkpoint,
    progress: ChecklistProgress,
    today: LocalDate,
): TestReminder.Checklist? {
    // An item that logs only misses is never owed, so no reminder names it; a checkpoint where
    // every due item does has nothing to send.
    val due = progress.checkpoints.firstOrNull { it.checkpoint.id == checkpoint.id }?.let { it.due - it.exceptionsOnly.toSet() }?.takeIf { it.isNotEmpty() } ?: return null
    return TestReminder.Checklist(checklist, checkpoint, due, today)
}

/**
 * What the reminder of a logged view sends [today] if nothing were logged yet: the
 * day today asks about for a measurement and a questions view, the week today is in for a time
 * view, with all that week asks still to go. Null where today asks about no day, as on the day a
 * view about yesterday was made, where the day asked about is an away day the view is not due on,
 * by its rule of that day, and where a time view's week asks nothing: then the reminder stays
 * quiet and there is nothing to send.
 */
internal fun loggedTestReminder(view: LoggedView, today: LocalDate, daysOff: HolidaySchedule): TestReminder.Logged? {
    val days = view.reminderDaysOn(today)?.takeUnless { view.isExcusedOn(it.recordDay, daysOff) } ?: return null
    if (view !is TimeConfiguration) return TestReminder.Logged(view, days)
    val weekAsks = view.weekAsks(days.recordDay.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)), daysOff)
    return TestReminder.Logged(view, days, minutesToGo = weekAsks).takeIf { weekAsks > 0 }
}

/**
 * Mo's check-in as if every task he asks about today were still open: the ones missing and the
 * ones already kept, in his ledger's order. A task a free day would cover is not asked for, so he
 * does not fume about it, as the real nudge does not. Null while he asks about nothing, or has no
 * mood to speak from; the real one is silent then too.
 */
internal fun moCheckInTestReminder(mood: MoMood?): TestReminder.MoCheckIn? {
    val asked = mood?.let { it.missingTrackersToday + it.keptTrackersToday }?.takeIf { it.isNotEmpty() } ?: return null
    return TestReminder.MoCheckIn(mood, asked.distinct().sortedWith(compareBy<TrackerRef> { it.type.value }.thenBy { it.id }))
}

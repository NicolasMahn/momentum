package de.nicolasmahn.momentum.reminder

import de.nicolasmahn.momentum.R
import de.nicolasmahn.momentum.core.text.UiText
import de.nicolasmahn.momentum.core.text.joinedWithAnd
import de.nicolasmahn.momentum.core.text.pluralText
import de.nicolasmahn.momentum.core.text.uiText
import de.nicolasmahn.momentum.core.time.currentDateKey
import de.nicolasmahn.momentum.domain.AppState
import de.nicolasmahn.momentum.domain.LoggedView
import de.nicolasmahn.momentum.domain.LoggedViewKind
import de.nicolasmahn.momentum.domain.LoggedViewRef
import de.nicolasmahn.momentum.domain.checklist.ChecklistConfiguration
import de.nicolasmahn.momentum.domain.checklist.ChecklistItem
import de.nicolasmahn.momentum.domain.checklist.Checkpoint
import de.nicolasmahn.momentum.domain.checklist.CheckpointTime
import de.nicolasmahn.momentum.domain.checklist.checklist
import de.nicolasmahn.momentum.domain.checklist.isInUse
import de.nicolasmahn.momentum.domain.checklist.obligations
import de.nicolasmahn.momentum.domain.checklist.progressOf
import de.nicolasmahn.momentum.domain.checklist.recordDayFor
import de.nicolasmahn.momentum.domain.checklist.tickAllDueAt
import de.nicolasmahn.momentum.domain.checklist.DayCheckpointId
import de.nicolasmahn.momentum.domain.reminder.ReminderSettings
import de.nicolasmahn.momentum.domain.reminder.ReminderTime
import de.nicolasmahn.momentum.domain.reminder.timing
import de.nicolasmahn.momentum.domain.settings.FeatureVisibilitySettings
import de.nicolasmahn.momentum.domain.settings.ViewRef
import de.nicolasmahn.momentum.domain.mo.MoMood
import de.nicolasmahn.momentum.domain.shows
import de.nicolasmahn.momentum.domain.holiday.HolidaySchedule
import de.nicolasmahn.momentum.domain.isExcusedOn
import de.nicolasmahn.momentum.domain.isLoggedOn
import de.nicolasmahn.momentum.domain.loggedViews
import de.nicolasmahn.momentum.domain.loggedView
import de.nicolasmahn.momentum.domain.measurement.MeasurementConfiguration
import de.nicolasmahn.momentum.feature.measurement.words
import de.nicolasmahn.momentum.persistence.TrackerUpdateResult
import de.nicolasmahn.momentum.core.time.nowMillis
import de.nicolasmahn.momentum.domain.ref
import de.nicolasmahn.momentum.domain.trackerRef
import de.nicolasmahn.momentum.domain.time.TimeConfiguration
import de.nicolasmahn.momentum.domain.time.minutesOn
import de.nicolasmahn.momentum.domain.time.minutesToGo
import de.nicolasmahn.momentum.persistence.TrackerRepository
import java.time.LocalDate

/**
 * The tracker kinds whose checkpoints remind here. A checklist's, a measurement's, a
 * questions view's and a time view's IDs are each unique only among their own kind, so a reminder
 * always says which kind its view is. A measurement, a questions view and a time view each remind
 * once a day, by the same code ([LoggedView]).
 */
internal enum class ReminderViewKind {
    Checklist,
    Measurement,
    Questions,
    Time,
    ;

    /** The kind of logged view this is, or null for a checklist. */
    val loggedViewKind: LoggedViewKind?
        get() = when (this) {
            Checklist -> null
            Measurement -> LoggedViewKind.Measurement
            Questions -> LoggedViewKind.Questions
            Time -> LoggedViewKind.Time
        }

    /**
     * What this kind's keys start with, in a namespace of its own, or null for a checklist, whose
     * key has had no namespace since before any other kind could remind.
     */
    val namespace: String?
        get() = when (this) {
            Checklist -> null
            Measurement -> "measurement"
            Questions -> "questions"
            Time -> "time"
        }
}

internal val LoggedViewKind.reminderKind: ReminderViewKind
    get() = when (this) {
        LoggedViewKind.Measurement -> ReminderViewKind.Measurement
        LoggedViewKind.Questions -> ReminderViewKind.Questions
        LoggedViewKind.Time -> ReminderViewKind.Time
    }

/**
 * One checkpoint that reminds: the kind of view it belongs to, the view, the checkpoint and its
 * time. Its alarm and its notification are told apart from every other one by [key], so any number
 * of views can remind without two sharing one.
 */
internal data class ReminderSlot(val kind: ReminderViewKind, val viewId: String, val checkpointId: String, val at: CheckpointTime) {
    val key: String get() = slotKey(kind, viewId, checkpointId)
}

/**
 * A checklist's key, in the form it has had since before a measurement could remind, so an alarm
 * set and a notification posted then are still that checklist's. IDs never contain a slash, so the
 * key names exactly one checkpoint of one checklist.
 */
internal fun slotKey(checklistId: String, checkpointId: String) = "$checklistId$SlotSeparator$checkpointId"

/**
 * The key of a slot of [kind]. A measurement's, a questions view's and a time view's are each in a
 * namespace of their own, `measurement/<id>/<checkpoint>`, `questions/<id>/<checkpoint>` and
 * `time/<id>/<checkpoint>`: such a key holds two slashes where a checklist's holds one, and names
 * its kind first, so no two kinds can ever have the same key, whatever their IDs are.
 */
internal fun slotKey(kind: ReminderViewKind, viewId: String, checkpointId: String): String =
    kind.namespace?.let { namespace -> "$namespace$SlotSeparator$viewId$SlotSeparator$checkpointId" } ?: slotKey(viewId, checkpointId)

/** What a key names. */
private data class SlotName(val kind: ReminderViewKind, val viewId: String, val checkpointId: String) {
    /** The logged view it names, or null for a checklist's. */
    val loggedView: LoggedViewRef? get() = kind.loggedViewKind?.let { LoggedViewRef(it, viewId) }
}

/** What [key] names, or null for a key that is no slot's. */
private fun slotNamed(key: String): SlotName? {
    val parts = key.split(SlotSeparator)
    return when (parts.size) {
        2 -> SlotName(ReminderViewKind.Checklist, parts[0], parts[1])
        3 -> ReminderViewKind.entries.firstOrNull { it.namespace == parts[0] }?.let { kind -> SlotName(kind, parts[1], parts[2]) }
        else -> null
    }
}

private const val SlotSeparator = '/'

/** Whether the view [slot] names is here and shown, with the checkpoint it names, whether that reminds or not. */
private fun AppState.holds(slot: SlotName, visibility: FeatureVisibilitySettings): Boolean = when (val view = slot.loggedView) {
    null -> checklist(slot.viewId)?.takeIf { visibility.shows(it) }?.checkpoints?.any { it.id == slot.checkpointId } == true
    else -> visibility.shows(view) && loggedView(view)?.checkpoint?.id == slot.checkpointId
}

/**
 * The checkpoints that remind now, of every checklist shown with an item in use on [today],
 * under keys that include its configuration and checkpoint IDs.
 */
internal fun checklistReminderSlots(state: AppState, visibility: FeatureVisibilitySettings, today: LocalDate): List<ReminderSlot> =
    state.checklists
        .filter { visibility.shows(it) && it.isInUse(today) }
        .flatMap { checklist ->
            checklist.checkpoints.filter { it.remind }
                .map { checkpoint -> ReminderSlot(ReminderViewKind.Checklist, checklist.id, checkpoint.id, checkpoint.at) }
        }

/** Reminders for every visible configured measurement, questions tracker and timer. */
internal fun loggedReminderSlots(state: AppState, visibility: FeatureVisibilitySettings): List<ReminderSlot> =
    state.loggedViews
        .filter { visibility.shows(it.ref) }
        .mapNotNull { view ->
            view.checkpoint.takeIf { it.remind }?.let { ReminderSlot(view.kind.reminderKind, view.id, it.id, it.at) }
        }

/** Every slot that reminds now, of every kind. */
internal fun reminderSlots(state: AppState, visibility: FeatureVisibilitySettings, today: LocalDate): List<ReminderSlot> =
    checklistReminderSlots(state, visibility, today) + loggedReminderSlots(state, visibility)

internal interface ChecklistReminderGateway {
    fun canScheduleExact(): Boolean

    /** The keys of the slots whose notification is on show now, not swiped away or tapped. */
    fun shown(): Set<String>

    /**
     * Sets [slot]'s alarm for its next time on or after the habit day [earliestDate], replacing any
     * earlier one. A time relative to waking follows the phone's alarm, or [fallbackAlarmTime]; a
     * time before sleep counts back from [fixedBedtime] when one is set.
     */
    fun schedule(slot: ReminderSlot, earliestDate: LocalDate?, fallbackAlarmTime: ReminderTime, fixedBedtime: ReminderTime?): Boolean

    /** Cancels the alarm and any notification of the slot with this key. */
    fun cancel(key: String)

    /** Takes the notification of the slot with this key away, leaving its alarm. */
    fun dismiss(key: String)

    /**
     * Says [why] on the notification of the slot with this key as it is on show, quietly, keeping
     * its title, its tap and its actions, so what was asked of it can be asked again. For an
     * answer that met records it could not read, which say nothing of the view to say it with.
     * False where none is on show.
     */
    fun keepUnsaved(key: String, why: UiText): Boolean
}

/**
 * The habit day a reminder rang on, and the day it asked about: the same day, or the one before on
 * a checklist or a questions view about yesterday. A time view's asks about the week the day it
 * rang on is in. A notification keeps both as they were when it rang, so said again after a
 * settings change it still asks about, and opens, the same record.
 */
internal data class ReminderDays(val dueDay: LocalDate, val recordDay: LocalDate) {
    /** Whether it asks about the day before the one it rang on. */
    val aboutYesterday: Boolean get() = recordDay < dueDay
}

/** The days a reminder of this checklist ringing on [dueDay] is about. */
internal fun ChecklistConfiguration.reminderDaysOn(dueDay: LocalDate): ReminderDays = ReminderDays(dueDay, recordDayFor(dueDay))

/**
 * The days a reminder of this view ringing on [dueDay] is about, or null where that day asks about
 * none: for a questions view about yesterday, the day it was made.
 */
internal fun LoggedView.reminderDaysOn(dueDay: LocalDate): ReminderDays? = recordDayFor(dueDay)?.let { ReminderDays(dueDay, it) }

/**
 * Runtime bookkeeping, never a record: which slots have an alarm set, so a slot whose view is gone
 * can still be cancelled, which notifications were posted with the days each asked about, so a
 * save answers the one that is about the day it saved, and the earliest habit day each slot may
 * ring on again. It holds the keys of every kind of view.
 */
internal interface ChecklistReminderRuntime {
    fun scheduled(): Set<String>

    fun record(keys: Set<String>)

    /** Each slot key with a notification posted and not yet answered, to the days it asked about. */
    fun posted(): Map<String, ReminderDays>

    fun posted(key: String, days: ReminderDays)

    fun dismissed(key: String)

    /**
     * Each slot key that rang, to the first habit day it may ring on again: the day after it rang.
     * Every reschedule respects it, so an evening with two candidate times, the phone's alarm and
     * the fallback, is never reminded twice.
     */
    fun earliest(): Map<String, LocalDate>

    fun earliest(key: String, day: LocalDate)
}

internal fun interface ChecklistReminderNotifier {
    /**
     * [key] is the slot's own, so its notification replaces only its own earlier one. [days] are
     * the habit day the reminder rang on and the day whose record it asks about, which a tap opens.
     * A notification said again [quietly] changes its words without sounding a second time.
     */
    fun show(checklist: ChecklistConfiguration, checkpoint: Checkpoint, open: List<ChecklistItem>, key: String, days: ReminderDays, quietly: Boolean): Boolean

    /** The notification said again quietly, saying that its "Mark all done" could not be saved, so it can be pressed again. */
    fun unsaved(checklist: ChecklistConfiguration, checkpoint: Checkpoint, open: List<ChecklistItem>, key: String, days: ReminderDays): Boolean =
        show(checklist, checkpoint, open, key, days, quietly = true)
}

internal fun interface LoggedReminderNotifier {
    /**
     * As [ChecklistReminderNotifier.show], for a logged view a person made. A measurement's and a
     * time view's are always about the day they rang on; a tap opens the view.
     */
    fun show(view: LoggedView, key: String, days: ReminderDays, quietly: Boolean): Boolean

    /**
     * The same, with what a time view's words name beside its name: [minutesToGo], the minutes
     * its week still asks for by the logs its reminder was decided on. It is null for the kinds
     * whose words name no number, and a notifier that says no words needs nothing of it.
     */
    fun show(view: LoggedView, minutesToGo: Int?, key: String, days: ReminderDays, quietly: Boolean): Boolean = show(view, key, days, quietly)

    /**
     * A measurement's notification said again quietly, its reply still on it, saying why the
     * reply was not stored: it was no number ([unreadable]), or it could not be saved.
     */
    fun unsaved(measurement: MeasurementConfiguration, key: String, days: ReminderDays, unreadable: Boolean): Boolean =
        show(measurement, key, days, quietly = true)
}

/** Notifications and Settings previews use current configuration labels and factual open items. */
internal fun checklistReminderMessage(checklist: ChecklistConfiguration, checkpoint: Checkpoint, open: List<ChecklistItem>, days: ReminderDays, mood: MoMood? = null): MoReminderMessage =
    MoReminderMessage(
        title = if (checklist.checkpoints.size > 1 && checkpoint.label != null) {
            uiText(R.string.checklist_reminder_title_checkpoint, checklist.label, checkpoint.label)
        } else {
            UiText.Verbatim(checklist.label)
        },
        text = uiText(
            if (days.aboutYesterday) R.string.checklist_reminder_open_yesterday else R.string.checklist_reminder_open,
            open.map { UiText.Verbatim(it.label) }.joinedWithAnd(),
        ),
    )

/** A motif chooses the optional notification illustration, never a tracker kind or its facts. */
internal val LoggedView.speaksWithMo: Boolean
    get() = this is TimeConfiguration && motif != null

internal fun speaksWithMo(checklist: ChecklistConfiguration, checkpoint: Checkpoint): Boolean = checklist.motif != null

/** Generic controls and notifications use the same kind vocabulary for every configured tracker. */
internal fun loggedReminderMessage(view: LoggedView, days: ReminderDays, minutesToGo: Int? = null, mood: MoMood? = null): MoReminderMessage =
    MoReminderMessage(
        title = UiText.Verbatim(view.label),
        text = when (view.kind) {
            LoggedViewKind.Measurement -> uiText(R.string.measurement_reminder_open)
            LoggedViewKind.Questions -> uiText(if (days.aboutYesterday) R.string.questions_reminder_open_yesterday else R.string.questions_reminder_open)
            LoggedViewKind.Time -> pluralText(R.plurals.time_reminder_week_remaining, requireNotNull(minutesToGo) { "A timer reminder requires the remaining weekly minutes." })
        },
    )

/**
 * What a measurement's reminder says of a reply it did not store: its title as it was, and why,
 * in the measurement's words: [unreadable], what was typed is no number; otherwise it could not
 * be saved and can be sent again.
 */
internal fun unsavedReplyMessage(measurement: MeasurementConfiguration, days: ReminderDays, unreadable: Boolean, mood: MoMood? = null): MoReminderMessage =
    loggedReminderMessage(measurement, days, mood = mood).copy(text = measurement.words().let { if (unreadable) it.replyInvalid else it.replyFailed })

/** What a checklist's reminder says when its "Mark all done" could not be saved: its title as it was, and to press it again. */
internal fun unsavedMarkAllDoneMessage(checklist: ChecklistConfiguration, checkpoint: Checkpoint, open: List<ChecklistItem>, days: ReminderDays, mood: MoMood? = null): MoReminderMessage =
    checklistReminderMessage(checklist, checkpoint, open, days, mood).copy(text = markAllDoneFailed)

/** That "Mark all done" was not saved, and to press it again, which names the button by its label. */
private val markAllDoneFailed: UiText = uiText(R.string.reminder_mark_all_done_failed, uiText(R.string.checklist_mark_all_done))

/**
 * Whether a reminder of [view] about [day] has something to ask. A measurement and a questions
 * view ask while the day holds no record of them. A time view asks while the week of that day is
 * short of what it asks and the day holds no record yet, as its Home row is done once it does;
 * on the Sunday the streak judges its week ([TimeConfiguration.streakJudgesWeekOn]) it asks while
 * the week is short whatever the day holds, as its Home row stays open then. None asks on an away
 * day it is not due on by the rule of that day; a sick day still asks, also one that is away too.
 */
internal fun AppState.owes(view: LoggedView, day: LocalDate, daysOff: HolidaySchedule): Boolean {
    if (view.isExcusedOn(day, daysOff)) return false
    val logged = isLoggedOn(view.ref, day.toString())
    if (view !is TimeConfiguration) return !logged
    return minutesToGo(view, day, daysOff) > 0 && (!logged || view.streakJudgesWeekOn(day, daysOff))
}

/**
 * The minutes the words of [view]'s reminder about [day] name: what a time view's week still asks
 * for. Null for a measurement and a questions view, whose words name no number.
 */
internal fun AppState.minutesToName(view: LoggedView, day: LocalDate, daysOff: HolidaySchedule): Int? =
    (view as? TimeConfiguration)?.let { minutesToGo(it, day, daysOff) }

/**
 * Whether what was stored for [dateKey] of [view], the records going from [before] to this state,
 * answers its reminder about that day while it may still ask: minutes added to a time view's day
 * do, whatever its week still asks for. A measurement's
 * and a questions view's is answered only by owing nothing.
 */
private fun AppState.answersWithMinutes(before: AppState, view: LoggedViewRef, dateKey: String): Boolean =
    view.kind == LoggedViewKind.Time && minutesOn(timeLogs, view.id, dateKey) > minutesOn(before.timeLogs, view.id, dateKey)

/**
 * Sets, rings, answers and cancels the reminders of every checkpoint that reminds: a checklist's,
 * and the one of every logged view. It is the one owner of their alarms, their notifications and
 * the bookkeeping of all of them, so nothing of one kind is ever cancelled or taken down as
 * unknown by another.
 */
internal class ChecklistReminderCoordinator(
    private val trackerRepository: TrackerRepository,
    private val featureVisibility: FeatureVisibilitySource,
    private val gateway: ChecklistReminderGateway,
    private val runtime: ChecklistReminderRuntime,
    private val notifier: ChecklistReminderNotifier,
    private val loggedNotifier: LoggedReminderNotifier,
    private val reminderSettings: () -> ReminderSettings,
    private val todayKey: () -> String = ::currentDateKey,
    /**
     * The days off, which the progress reads: an item excused while away is not due, and a week
     * item's week leaves sick and excused days out. A logged view asking about an away day it is
     * not due on is silent, and a time view's week asks less for each day off.
     */
    private val awayDays: () -> HolidaySchedule = { HolidaySchedule() },
    private val timestampMillis: () -> Long = ::nowMillis,
    /** Whether a choice stopped today's asking for a view or one of its checkpoints: its alarm then says nothing. */
    private val quietedByChoice: QuietedByChoice = QuietedByChoice { _, _ -> false },
) {
    /**
     * Sets every reminding checkpoint's next alarm, no earlier than the day its slot may ring on
     * again, and cancels the alarms nothing asks for anymore. Every slot is attempted, so one that
     * cannot be set leaves the others set; the answer is whether all of them were.
     *
     * A notification on show of a view hidden or deleted since, or of a checkpoint its view no
     * longer has, goes too: a test is one, which neither an alarm nor the bookkeeping knows of.
     *
     * Records that cannot be read say nothing of any view, so nothing is set, cancelled or taken
     * down until they can be read again, and the answer is false: a notification kept after an
     * answer that met them stays, saying it was not saved.
     */
    fun reconcile(): Boolean {
        val state = readable() ?: return false
        val visibility = featureVisibility.load()
        val wanted = reminderSlots(state, visibility, LocalDate.parse(todayKey()))
        val wantedKeys = wanted.mapTo(HashSet()) { it.key }
        (runtime.scheduled() - wantedKeys).forEach { key ->
            gateway.cancel(key)
            runtime.dismissed(key)
        }
        gateway.shown().filterNot { key -> slotNamed(key)?.let { state.holds(it, visibility) } == true }.forEach(::takeDown)
        val settings = reminderSettings()
        val earliest = runtime.earliest()
        val scheduled = wanted.isEmpty() || gateway.canScheduleExact() &&
            wanted.map { gateway.schedule(it, earliest[it.key], settings.fallbackAlarmTime, settings.fixedBedtime) }.all { it }
        runtime.record(wantedKeys)
        return scheduled
    }

    /**
     * The alarm of one checkpoint rang: it may ring again from tomorrow, its next alarm is set so,
     * and it notifies only while its view still has something to say about the record today asks
     * about.
     */
    fun onReminderDue(kind: ReminderViewKind, viewId: String, checkpointId: String): Boolean {
        val today = LocalDate.parse(todayKey())
        val due = slotKey(kind, viewId, checkpointId)
        runtime.earliest(due, today.plusDays(1))
        val rescheduled = reconcile()
        val state = readable() ?: return false
        val shown = when (val logged = kind.loggedViewKind) {
            null -> remindOfChecklist(state, viewId, checkpointId, due, today)
            else -> remindOfLogged(state, LoggedViewRef(logged, viewId), checkpointId, due, today)
        } ?: return rescheduled
        return shown && rescheduled
    }

    /**
     * Notifies while something due at the checkpoint is still open and no token is set aside for
     * it. Answers whether the notification could be shown, or null where the checklist has
     * nothing to say.
     */
    private fun remindOfChecklist(state: AppState, checklistId: String, checkpointId: String, key: String, today: LocalDate): Boolean? {
        val checklist = state.checklist(checklistId)?.takeIf { featureVisibility.load().shows(it) } ?: return null
        val checkpoint = checklist.checkpoints.firstOrNull { it.id == checkpointId && it.remind } ?: return null
        val settings = reminderSettings()
        // Only what is due is owed, in the record the day asks about: today's, or yesterday's on a
        // checklist about yesterday. An item excused while away is not due, and a week item only
        // on a day skipping it would cost; on its free days it is offered without a nudge.
        val days = checklist.reminderDaysOn(today)
        val owed = state.progressOf(checklist, days.recordDay.toString(), awayDays())
            .obligations(settings.timing)
            .firstOrNull { it.checkpoint.id == checkpointId }
            ?: return null
        if (quietedByChoice.isQuiet(ViewRef.Checklist(checklistId).trackerRef, checkpointId)) return null
        val shown = notifier.show(checklist, checkpoint, owed.open, key, days, quietly = false)
        if (shown) runtime.posted(key, days)
        return shown
    }

    /**
     * Notifies while the view still asks ([owes]): while the day today asks about holds no record
     * of a measurement or a questions view, today itself or yesterday for a questions view about
     * yesterday, and while a time view's week is short of what it asks and, but on the Sunday the
     * streak judges it, the day holds nothing of it. It stays silent once that
     * is settled or tokens are set aside for it, while the view is hidden, gone or no longer
     * reminds, where today asks about no day, and where the day asked about is an away day it is
     * not due on. Answers whether the
     * notification could be shown, or null where it stays silent.
     */
    private fun remindOfLogged(state: AppState, ref: LoggedViewRef, checkpointId: String, key: String, today: LocalDate): Boolean? {
        val view = state.loggedView(ref)?.takeIf { featureVisibility.load().shows(ref) } ?: return null
        if (view.checkpoint.takeIf { it.id == checkpointId }?.remind != true) return null
        val days = view.reminderDaysOn(today)?.takeIf { state.owes(view, it.recordDay, awayDays()) } ?: return null
        if (quietedByChoice.isQuiet(ref.trackerRef, null)) return null
        val shown = say(state, view, key, days, quietly = false)
        if (shown) runtime.posted(key, days)
        return shown
    }

    /** Says [view]'s reminder about [days], in the words the records in [state] give it. */
    private fun say(state: AppState, view: LoggedView, key: String, days: ReminderDays, quietly: Boolean): Boolean =
        loggedNotifier.show(view, state.minutesToName(view, days.recordDay, awayDays()), key, days, quietly)

    /**
     * A save changed [checklistId]'s records from [before] to [after]: a notification on show about
     * one of its checkpoints is refreshed to name what is still owed there in the record it asked
     * about, and taken away once nothing is. That day may have turned since it posted, so a save
     * for today never answers a notification about yesterday, and a late tick for yesterday still does.
     */
    fun onRecordsSaved(before: AppState, after: AppState, checklistId: String) {
        onShow().forEach { (key, days) ->
            val slot = slotNamed(key)?.takeIf { it.kind == ReminderViewKind.Checklist && it.viewId == checklistId } ?: return@forEach
            val owedBefore = before.checklist(checklistId)?.let { before.progressOf(it, days.recordDay.toString(), awayDays()).openAt(slot.checkpointId) }.orEmpty()
            refreshChecklist(after, slot, key, days, shown = owedBefore)
        }
    }

    /**
     * A day of [view], a measurement's number, a questions view's answers or a time view's
     * minutes, was stored for [dateKey], the records going from [before] to [after]: its
     * notification about that day is taken down once the day is logged. For a time view that is
     * once minutes were added to the day, although its week may still ask for more. One about
     * another day, another view's and every checklist's stay as they are. Removing the record
     * brings nothing back: a notification taken down is forgotten.
     */
    fun onDayLogged(before: AppState, after: AppState, view: LoggedViewRef, dateKey: String) {
        onShow().forEach { (key, days) ->
            val slot = slotNamed(key)?.takeIf { it.loggedView == view } ?: return@forEach
            if (days.recordDay.toString() != dateKey) return@forEach
            if (after.answersWithMinutes(before, view, dateKey)) return@forEach takeDown(key)
            val askedBefore = before.loggedView(view)?.let { before.owes(it, days.recordDay, awayDays()) } ?: false
            refreshLogged(after, slot, key, days, said = askedBefore)
        }
    }

    /**
     * A measurement's notification about [days] was answered with [reply], a number for the day it
     * asked about, which is stored as a save on the measurement's screen would store it
     * ([MeasurementReplyCoordinator]); then the notification goes. One that is no number, or
     * could not be stored, is said again quietly with what went wrong and its reply still on it,
     * also where the records cannot be read at all. One whose measurement is gone or hidden since
     * is taken down and stores nothing. Answers whether the number was stored.
     */
    fun onReply(measurementId: String, reply: CharSequence?, days: ReminderDays): Boolean {
        val ref = LoggedViewRef(LoggedViewKind.Measurement, measurementId)
        val key = slotKey(ReminderViewKind.Measurement, measurementId, DayCheckpointId)
        // Records that cannot be read say nothing of the measurement, gone or not, so the notification on show says it.
        val state = readable() ?: return false.also { gateway.keepUnsaved(key, uiText(R.string.measurement_error_save_failed)) }
        val measurement = state.loggedView(ref) as? MeasurementConfiguration
        if (measurement == null || !featureVisibility.load().shows(ref)) return false.also { takeDown(key) }
        val result = MeasurementReplyCoordinator(measurementId, trackerRepository, timestampMillis = timestampMillis).submit(reply, days.recordDay.toString())
        when (result) {
            is MeasurementReplyResult.Saved, MeasurementReplyResult.Deleted -> takeDown(key)
            MeasurementReplyResult.Invalid, MeasurementReplyResult.SaveFailed -> loggedNotifier.unsaved(measurement, key, days, unreadable = result == MeasurementReplyResult.Invalid)
        }
        return result is MeasurementReplyResult.Saved
    }

    /**
     * "Mark all done" was pressed on a checklist's notification about [days]: every item due at
     * [checkpointId] in the record it asked about is ticked, keeping what was recorded, and an item
     * that logs only misses stays as it is ([tickAllDueAt]). The save answers the notification as
     * a save on the checklist's screen does ([onRecordsSaved]). One that could not be saved, the
     * records unreadable included, is said again quietly so it can be pressed again; one whose
     * checklist is gone or hidden since is taken down. Answers whether the ticks were stored.
     */
    fun onMarkAllDone(checklistId: String, checkpointId: String, days: ReminderDays): Boolean {
        val key = slotKey(checklistId, checkpointId)
        val state = readable() ?: return false.also { gateway.keepUnsaved(key, markAllDoneFailed) }
        val checklist = state.checklist(checklistId)
        if (checklist == null || !featureVisibility.load().shows(checklist)) return false.also { takeDown(key) }
        var before: AppState? = null
        val result = trackerRepository.update { current ->
            before = current
            current.checklist(checklistId)?.let { tickAllDueAt(current, it, days.recordDay.toString(), checkpointId, timestampMillis(), awayDays()) } ?: current
        }
        if (result is TrackerUpdateResult.Updated) {
            onRecordsSaved(requireNotNull(before), result.state, checklistId)
            takeDown(key)
            return true
        }
        val checkpoint = checklist.checkpoints.firstOrNull { it.id == checkpointId }
        val open = state.progressOf(checklist, days.recordDay.toString(), awayDays()).openAt(checkpointId)
        if (checkpoint == null || !notifier.unsaved(checklist, checkpoint, open, key, days)) takeDown(key)
        return false
    }

    /** The records, or null where they cannot be read, a read that throws included: an answer from a notification must never escape its receiver. */
    private fun readable(): AppState? = runCatching { trackerRepository.loadIfReadable() }.getOrNull()

    /**
     * Brings every notification on show in line with the records and configurations as they are
     * now: each names only what is still owed at its checkpoint in the record it asked about, and
     * one that owes nothing is taken away. A settings change settles an item without a save, as
     * switching it to only misses does, and so does a token set aside, so this runs after either;
     * the words change without alerting again.
     * Records that cannot be read leave every notification as it is, as [reconcile] does.
     */
    fun refreshPosted() {
        val state = readable() ?: return
        onShow().forEach { (key, days) ->
            when (val slot = slotNamed(key)) {
                null -> takeDown(key)
                else -> when (slot.kind) {
                    ReminderViewKind.Checklist -> refreshChecklist(state, slot, key, days, shown = null)
                    ReminderViewKind.Measurement, ReminderViewKind.Questions, ReminderViewKind.Time -> refreshLogged(state, slot, key, days, said = false)
                }
            }
        }
    }

    /**
     * The notifications posted that are still on show, with the days each asked about. One swiped
     * away or tapped is forgotten here too, so nothing ever brings it back.
     */
    private fun onShow(): Map<String, ReminderDays> {
        val posted = runtime.posted()
        val shown = gateway.shown()
        (posted.keys - shown).forEach(runtime::dismissed)
        return posted.filterKeys { it in shown }
    }

    /** [shown] is what the notification names now, where known; it is left alone while that is still what is owed. */
    private fun refreshChecklist(state: AppState, slot: SlotName, key: String, days: ReminderDays, shown: List<ChecklistItem>?) {
        val checklist = state.checklist(slot.viewId)
        val checkpoint = checklist?.checkpoints?.firstOrNull { it.id == slot.checkpointId }
        val owed = if (checklist == null) emptyList() else state.progressOf(checklist, days.recordDay.toString(), awayDays()).openAt(slot.checkpointId)
        // A token set aside in the app answers the notification as a tick does: it has nothing left to ask.
        if (checklist == null || checkpoint == null || owed.isEmpty() || quietedByChoice.isQuiet(ViewRef.Checklist(slot.viewId).trackerRef, slot.checkpointId)) {
            takeDown(key)
        } else if (owed != shown) {
            notifier.show(checklist, checkpoint, owed, key, days, quietly = true)
        }
    }

    /**
     * The notification of a logged view goes once it asks nothing any more ([owes]), the view is
     * gone, a choice in the app settled it, or the day it asked about is no longer due. While it still asks it is said again,
     * quietly, unless it [said] so already: its words may have changed, as after a rename.
     */
    private fun refreshLogged(state: AppState, slot: SlotName, key: String, days: ReminderDays, said: Boolean) {
        val view = slot.loggedView?.let(state::loggedView)?.takeIf { it.checkpoint.id == slot.checkpointId }
        if (view == null || !state.owes(view, days.recordDay, awayDays()) || quietedByChoice.isQuiet(view.ref.trackerRef, null)) {
            takeDown(key)
        } else if (!said) {
            say(state, view, key, days, quietly = true)
        }
    }

    private fun takeDown(key: String) {
        gateway.dismiss(key)
        runtime.dismissed(key)
    }
}

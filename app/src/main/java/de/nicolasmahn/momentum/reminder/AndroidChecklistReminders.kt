package de.nicolasmahn.momentum.reminder

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.annotation.DrawableRes
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat
import android.content.res.Resources
import de.nicolasmahn.momentum.R
import de.nicolasmahn.momentum.app.appPendingIntent
import de.nicolasmahn.momentum.app.checklistDestinationPendingIntent
import de.nicolasmahn.momentum.app.loggedViewDestinationPendingIntent
import de.nicolasmahn.momentum.app.startTimePendingIntent
import de.nicolasmahn.momentum.core.time.parseDateKey
import de.nicolasmahn.momentum.domain.measurement.MeasurementConfiguration
import de.nicolasmahn.momentum.domain.time.TimeConfiguration
import de.nicolasmahn.momentum.feature.time.words
import de.nicolasmahn.momentum.feature.measurement.words
import de.nicolasmahn.momentum.core.locale.withAppLanguage
import de.nicolasmahn.momentum.core.text.UiText
import de.nicolasmahn.momentum.core.text.resolve
import de.nicolasmahn.momentum.domain.checklist.ChecklistConfiguration
import de.nicolasmahn.momentum.domain.checklist.ChecklistItem
import de.nicolasmahn.momentum.domain.checklist.Checkpoint
import de.nicolasmahn.momentum.domain.LoggedView
import de.nicolasmahn.momentum.domain.LoggedViewKind
import de.nicolasmahn.momentum.domain.ref
import de.nicolasmahn.momentum.domain.mo.MoMood
import de.nicolasmahn.momentum.domain.reminder.ReminderTime
import de.nicolasmahn.momentum.persistence.PreferencesHolidayRepository
import de.nicolasmahn.momentum.persistence.PreferencesReminderSettingsRepository
import de.nicolasmahn.momentum.persistence.PreferencesTrackerRepository
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import org.json.JSONObject

/**
 * Receives the alarm of every slot, a checklist's, a measurement's, a questions view's and a time
 * view's. It keeps the name, the action and the extras it had while fewer kinds reminded: an alarm
 * is matched by those, so one set then is still the alarm its slot sets and cancels now.
 */
class ChecklistReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val checkpointId = intent.getStringExtra(ExtraCheckpointId) ?: return
        // Each kind names its view in an extra of its own, so an alarm never rings another kind's view.
        val (kind, viewId) = ReminderViewKind.entries.firstNotNullOfOrNull { kind -> intent.getStringExtra(kind.viewIdExtra)?.let { kind to it } } ?: return
        context.checklistReminderCoordinator().onReminderDue(kind, viewId, checkpointId)
    }

    internal companion object {
        const val ExtraChecklistId = "de.nicolasmahn.momentum.extra.REMINDER_CHECKLIST_ID"
        const val ExtraMeasurementId = "de.nicolasmahn.momentum.extra.REMINDER_MEASUREMENT_ID"
        const val ExtraQuestionsId = "de.nicolasmahn.momentum.extra.REMINDER_QUESTIONS_ID"
        const val ExtraTimeId = "de.nicolasmahn.momentum.extra.REMINDER_TIME_ID"
        const val ExtraCheckpointId = "de.nicolasmahn.momentum.extra.REMINDER_CHECKPOINT_ID"
    }
}

/** The extra of an alarm that names the view of this kind. */
private val ReminderViewKind.viewIdExtra: String
    get() = when (this) {
        ReminderViewKind.Checklist -> ChecklistReminderReceiver.ExtraChecklistId
        ReminderViewKind.Measurement -> ChecklistReminderReceiver.ExtraMeasurementId
        ReminderViewKind.Questions -> ChecklistReminderReceiver.ExtraQuestionsId
        ReminderViewKind.Time -> ChecklistReminderReceiver.ExtraTimeId
    }

internal fun Context.checklistReminderCoordinator(): ChecklistReminderCoordinator {
    val notification = ChecklistReminderNotification(applicationContext)
    return ChecklistReminderCoordinator(
        trackerRepository = PreferencesTrackerRepository(applicationContext),
        featureVisibility = featureVisibilitySource(),
        gateway = AndroidChecklistReminderGateway(applicationContext),
        runtime = PreferencesChecklistReminderRuntime(applicationContext),
        notifier = notification,
        loggedNotifier = notification,
        reminderSettings = PreferencesReminderSettingsRepository(applicationContext)::load,
        awayDays = PreferencesHolidayRepository(applicationContext)::load,
        quietedByChoice = quietedByChoice(applicationContext),
    )
}

internal class AndroidChecklistReminderGateway(
    context: Context,
    private val now: () -> ZonedDateTime = { ZonedDateTime.now(ZoneId.systemDefault()) },
) : ChecklistReminderGateway {
    private val applicationContext = context.applicationContext
    private val alarmManager = applicationContext.getSystemService(AlarmManager::class.java)

    override fun canScheduleExact(): Boolean = canScheduleExactReminders(applicationContext)

    override fun shown(): Set<String> = runCatching {
        applicationContext.getSystemService(NotificationManager::class.java).activeNotifications
            .filter { it.id == ChecklistReminderRequestCode }
            .mapNotNullTo(HashSet()) { it.tag }
    }.getOrDefault(emptySet())

    override fun schedule(
        slot: ReminderSlot,
        earliestDate: LocalDate?,
        fallbackAlarmTime: ReminderTime,
        fixedBedtime: ReminderTime?,
    ): Boolean = runCatching {
        val current = now()
        val nextSystemAlarm = alarmManager.nextAlarmClock?.triggerTime
        val at = nextReminderOnOrAfterHabitDate(current, earliestDate) { searchFrom ->
            nextCheckpointReminderInstant(searchFrom, slot.at, fallbackAlarmTime, fixedBedtime, nextSystemAlarm, earliestDate ?: current.toLocalDate())
        } ?: return false
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.toEpochMilli(), alarm(slot.key, slot))
    }.isSuccess

    override fun cancel(key: String) {
        alarmManager.cancel(alarm(key, slot = null))
        dismissChecklistReminder(applicationContext, key)
    }

    override fun dismiss(key: String) = dismissChecklistReminder(applicationContext, key)

    override fun keepUnsaved(key: String, why: UiText): Boolean = runCatching {
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        val onShow = manager.activeNotifications.firstOrNull { it.id == ChecklistReminderRequestCode && it.tag == key } ?: return false
        val text = why.resolve(applicationContext.withAppLanguage().resources)
        val said = Notification.Builder.recoverBuilder(applicationContext, onShow.notification)
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setOnlyAlertOnce(true)
            .build()
        manager.notify(key, ChecklistReminderRequestCode, said)
        true
    }.getOrDefault(false)

    /**
     * An alarm is matched by its action and data, never its extras, so the slot key in the data keeps
     * every slot's alarm its own, and cancelling needs only the key. The extras say which view rings.
     */
    private fun alarm(key: String, slot: ReminderSlot?): PendingIntent = PendingIntent.getBroadcast(
        applicationContext,
        ChecklistReminderRequestCode,
        Intent(applicationContext, ChecklistReminderReceiver::class.java).apply {
            action = ActionChecklistReminder
            data = Uri.Builder().scheme(ChecklistUriScheme).authority("reminder").appendPath(key).build()
            slot?.let {
                putExtra(it.kind.viewIdExtra, it.viewId)
                putExtra(ChecklistReminderReceiver.ExtraCheckpointId, it.checkpointId)
            }
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private companion object {
        const val ActionChecklistReminder = "de.nicolasmahn.momentum.action.CHECKLIST_REMINDER"
    }
}

/** Every slot's alarm and notification shares one code, whichever kind its view is; the slot key tells them apart. */
internal const val ChecklistReminderRequestCode = 20_000
internal const val ChecklistUriScheme = "momentum"

/**
 * Receives a measurement's reply typed into its reminder, of any measurement: one key for the
 * reply and one action, whose data names the slot and the days it asked about so two
 * notifications never share one. The number is stored for the day asked about, and the
 * notification goes, or is said again with what went wrong ([ChecklistReminderCoordinator.onReply]).
 */
class MeasurementReplyReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ActionReplyMeasurement) return
        val measurementId = intent.getStringExtra(ExtraActionViewId) ?: return
        val days = intent.reminderDays() ?: return
        val reply = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(ReplyResultKey)
        if (context.checklistReminderCoordinator().onReply(measurementId, reply, days)) context.announceSavedRecord()
    }
}

/**
 * Receives "Mark all done" pressed on a checklist's reminder, of any checklist, which ticks every
 * item due at its checkpoint in the record it asked about ([ChecklistReminderCoordinator.onMarkAllDone]).
 */
class MarkAllDoneReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ActionMarkAllDone) return
        val checklistId = intent.getStringExtra(ExtraActionViewId) ?: return
        val checkpointId = intent.getStringExtra(ExtraActionCheckpointId) ?: return
        val days = intent.reminderDays() ?: return
        if (context.checklistReminderCoordinator().onMarkAllDone(checklistId, checkpointId, days)) context.announceSavedRecord()
    }
}

/** The days an action's notification asked about, as it carries them; null for an intent that names none. */
private fun Intent.reminderDays(): ReminderDays? {
    val due = getStringExtra(ExtraActionDueDay)?.let(::parseDateKey) ?: return null
    val record = getStringExtra(ExtraActionRecordDay)?.let(::parseDateKey) ?: return null
    return ReminderDays(due, record)
}

internal const val ActionReplyMeasurement = "de.nicolasmahn.momentum.action.REPLY_MEASUREMENT"
internal const val ActionMarkAllDone = "de.nicolasmahn.momentum.action.MARK_ALL_DONE"
internal const val ReplyResultKey = "measurement_reply"
private const val ExtraActionViewId = "de.nicolasmahn.momentum.extra.ACTION_VIEW_ID"
private const val ExtraActionCheckpointId = "de.nicolasmahn.momentum.extra.ACTION_CHECKPOINT_ID"
private const val ExtraActionDueDay = "de.nicolasmahn.momentum.extra.ACTION_DUE_DAY"
private const val ExtraActionRecordDay = "de.nicolasmahn.momentum.extra.ACTION_RECORD_DAY"

/** Takes a slot's notification away, as when its view is completed in the app. */
internal fun dismissChecklistReminder(context: Context, key: String) {
    NotificationManagerCompat.from(context.applicationContext).cancel(key, ChecklistReminderRequestCode)
}

/**
 * Runtime bookkeeping, never a record: which alarms are set, which notifications are on show, and
 * when each slot may ring again. Losing it costs at most one repeated reminder, so it stays out of
 * the backup.
 */
internal class PreferencesChecklistReminderRuntime(context: Context) : ChecklistReminderRuntime {
    private val preferences = context.applicationContext.getSharedPreferences("checklist_reminder_runtime", Context.MODE_PRIVATE)

    override fun scheduled(): Set<String> = preferences.getStringSet(ScheduledKey, null).orEmpty().toSet()

    override fun record(keys: Set<String>) {
        preferences.edit().putStringSet(ScheduledKey, keys).apply()
    }

    /** An entry written before a notification kept its due day reads as asking about the day it rang on. */
    override fun posted(): Map<String, ReminderDays> {
        val json = preferences.getString(PostedKey, null)?.let { raw -> runCatching { JSONObject(raw) }.getOrNull() } ?: return emptyMap()
        return json.keys().asSequence().mapNotNull { key ->
            runCatching {
                when (val days = json.get(key)) {
                    is JSONObject -> ReminderDays(LocalDate.parse(days.getString(DueKey)), LocalDate.parse(days.getString(RecordKey)))
                    else -> LocalDate.parse(days.toString()).let { ReminderDays(it, it) }
                }
            }.getOrNull()?.let { key to it }
        }.toMap()
    }

    override fun posted(key: String, days: ReminderDays) = write(posted() + (key to days))

    override fun dismissed(key: String) = write(posted() - key)

    private fun write(posted: Map<String, ReminderDays>) {
        val json = JSONObject()
        posted.forEach { (key, days) -> json.put(key, JSONObject().put(DueKey, days.dueDay.toString()).put(RecordKey, days.recordDay.toString())) }
        preferences.edit().putString(PostedKey, json.toString()).apply()
    }

    override fun earliest(): Map<String, LocalDate> {
        val json = preferences.getString(EarliestKey, null)?.let { raw -> runCatching { JSONObject(raw) }.getOrNull() } ?: return emptyMap()
        return json.keys().asSequence().mapNotNull { key -> runCatching { key to LocalDate.parse(json.getString(key)) }.getOrNull() }.toMap()
    }

    override fun earliest(key: String, day: LocalDate) {
        val json = JSONObject()
        (earliest() + (key to day)).forEach { (slot, date) -> json.put(slot, date.toString()) }
        preferences.edit().putString(EarliestKey, json.toString()).apply()
    }

    private companion object {
        const val ScheduledKey = "scheduled"
        const val PostedKey = "posted"
        const val EarliestKey = "earliest"
        const val DueKey = "due"
        const val RecordKey = "record"
    }
}

/**
 * The reminder of a checklist, naming what is still open, or of a logged view, in the words
 * [checklistReminderMessage] and [loggedReminderMessage] decide. One of a task of Mo's speaks in
 * his voice, in the tone of his mood, and wears his face; every other one is plain.
 */
internal class ChecklistReminderNotification(context: Context) : ChecklistReminderNotifier, LoggedReminderNotifier {
    private val applicationContext = context.applicationContext

    override fun show(checklist: ChecklistConfiguration, checkpoint: Checkpoint, open: List<ChecklistItem>, key: String, days: ReminderDays, quietly: Boolean): Boolean =
        notify(checklist, checkpoint, open, key, days, opensChecklist = true, quietly = quietly)

    /**
     * The same reminder sent as a test. Where the checklist [opens], a tap opens it on the day it
     * asks about, as ever; where Home could not open it, as in the setup, a tap opens the app
     * where it was.
     */
    fun showTest(checklist: ChecklistConfiguration, checkpoint: Checkpoint, open: List<ChecklistItem>, key: String, dueDay: LocalDate, opens: Boolean): Boolean =
        notify(checklist, checkpoint, open, key, checklist.reminderDaysOn(dueDay), opensChecklist = opens, quietly = false)

    override fun show(view: LoggedView, key: String, days: ReminderDays, quietly: Boolean): Boolean = show(view, minutesToGo = null, key, days, quietly)

    override fun show(view: LoggedView, minutesToGo: Int?, key: String, days: ReminderDays, quietly: Boolean): Boolean =
        notify(view, key, days, minutesToGo, opensView = true, quietly = quietly)

    /**
     * The reminder of a logged view sent as a test, under its slot's own [key] as the real one is,
     * a time view's naming [minutesToGo]. Where the view [opens], a tap opens it, a measurement
     * and a questions view on the day [days] ask about; where Home could not open it, as in the
     * setup, a tap opens the app where it was.
     */
    fun showTest(view: LoggedView, key: String, days: ReminderDays, opens: Boolean, minutesToGo: Int? = null): Boolean =
        notify(view, key, days, minutesToGo, opensView = opens, quietly = false)

    override fun unsaved(checklist: ChecklistConfiguration, checkpoint: Checkpoint, open: List<ChecklistItem>, key: String, days: ReminderDays): Boolean =
        notify(checklist, checkpoint, key, days, opensChecklist = true, quietly = true) { mood -> unsavedMarkAllDoneMessage(checklist, checkpoint, open, days, mood) }

    override fun unsaved(measurement: MeasurementConfiguration, key: String, days: ReminderDays, unreadable: Boolean): Boolean =
        notify(measurement, key, days, opensView = true, quietly = true) { mood -> unsavedReplyMessage(measurement, days, unreadable, mood) }

    private fun notify(checklist: ChecklistConfiguration, checkpoint: Checkpoint, open: List<ChecklistItem>, key: String, days: ReminderDays, opensChecklist: Boolean, quietly: Boolean): Boolean =
        notify(checklist, checkpoint, key, days, opensChecklist, quietly) { mood -> checklistReminderMessage(checklist, checkpoint, open, days, mood) }

    private fun notify(
        checklist: ChecklistConfiguration,
        checkpoint: Checkpoint,
        key: String,
        days: ReminderDays,
        opensChecklist: Boolean,
        quietly: Boolean,
        message: (MoMood?) -> MoReminderMessage,
    ): Boolean = post(
        message = message,
        withMo = speaksWithMo(checklist, checkpoint),
        smallIcon = R.drawable.ic_notification_checklist,
        key = key,
        // Tapped, it opens the day it asked about, even after that day has become yesterday.
        tap = {
            if (opensChecklist) {
                checklistDestinationPendingIntent(applicationContext, checklist.id, days.recordDay.toString())
            } else {
                appPendingIntent(applicationContext)
            }
        },
        quietly = quietly,
        actions = { resources -> listOf(markAllDoneAction(checklist.id, checkpoint.id, key, days, resources)) },
    )

    private fun notify(view: LoggedView, key: String, days: ReminderDays, minutesToGo: Int?, opensView: Boolean, quietly: Boolean): Boolean =
        notify(view, key, days, opensView, quietly) { mood -> loggedReminderMessage(view, days, minutesToGo, mood) }

    private fun notify(view: LoggedView, key: String, days: ReminderDays, opensView: Boolean, quietly: Boolean, message: (MoMood?) -> MoReminderMessage): Boolean = post(
        message = message,
        withMo = view.speaksWithMo,
        smallIcon = when (view.kind) {
            LoggedViewKind.Measurement -> R.drawable.ic_notification_measurement
            LoggedViewKind.Questions -> R.drawable.ic_notification_questions
            LoggedViewKind.Time -> R.drawable.ic_notification_time
        },
        key = key,
        // Tapped, it opens the view with the day it asked about, even after that day has become yesterday.
        tap = {
            if (opensView) {
                loggedViewDestinationPendingIntent(applicationContext, view.ref, days.recordDay.toString())
            } else {
                appPendingIntent(applicationContext)
            }
        },
        quietly = quietly,
        actions = { resources -> listOfNotNull(offerOf(view, key, days, resources)) },
    )

    /**
     * What a logged view's reminder offers beside its tap, by its kind: a measurement a reply that
     * logs its number, a time view a start of its session, a questions view nothing, since its
     * answers need its screen.
     */
    private fun offerOf(view: LoggedView, key: String, days: ReminderDays, resources: Resources): NotificationCompat.Action? = when (view) {
        is MeasurementConfiguration -> replyAction(view, key, days, resources)
        is TimeConfiguration -> NotificationCompat.Action.Builder(
            R.drawable.ic_notification_time,
            view.words().start.resolve(resources),
            startTimePendingIntent(applicationContext, view.id),
        ).build()
        else -> null
    }

    /** The configured unit and shared input rules apply to replies just as they do on screen. */
    private fun replyAction(measurement: MeasurementConfiguration, key: String, days: ReminderDays, resources: Resources): NotificationCompat.Action {
        val input = RemoteInput.Builder(ReplyResultKey)
            .setLabel(measurement.words().replyPrompt.resolve(resources))
            .build()
        return NotificationCompat.Action.Builder(
            R.drawable.ic_notification_measurement,
            resources.getString(R.string.reminder_action_log),
            // A reply is read from the intent it comes back on, so it must stay mutable.
            actionIntent(MeasurementReplyReceiver::class.java, ActionReplyMeasurement, measurement.id, checkpointId = null, key, days, PendingIntent.FLAG_MUTABLE),
        )
            .addRemoteInput(input)
            .setAllowGeneratedReplies(false)
            .build()
    }

    private fun markAllDoneAction(checklistId: String, checkpointId: String, key: String, days: ReminderDays, resources: Resources): NotificationCompat.Action =
        NotificationCompat.Action.Builder(
            R.drawable.ic_notification_checklist,
            resources.getString(R.string.checklist_mark_all_done),
            actionIntent(MarkAllDoneReceiver::class.java, ActionMarkAllDone, checklistId, checkpointId, key, days, PendingIntent.FLAG_IMMUTABLE),
        ).build()

    /** One per slot and days: the key and the days are in the data, so a newer notification never rewrites what an older one answers. */
    private fun actionIntent(receiver: Class<out BroadcastReceiver>, action: String, viewId: String, checkpointId: String?, key: String, days: ReminderDays, mutability: Int): PendingIntent =
        PendingIntent.getBroadcast(
            applicationContext,
            ChecklistReminderRequestCode,
            Intent(applicationContext, receiver).apply {
                this.action = action
                data = Uri.Builder().scheme(ChecklistUriScheme).authority("answer").appendPath(key).appendPath(days.dueDay.toString()).appendPath(days.recordDay.toString()).build()
                putExtra(ExtraActionViewId, viewId)
                checkpointId?.let { putExtra(ExtraActionCheckpointId, it) }
                putExtra(ExtraActionDueDay, days.dueDay.toString())
                putExtra(ExtraActionRecordDay, days.recordDay.toString())
            },
            PendingIntent.FLAG_UPDATE_CURRENT or mutability,
        )

    /**
     * Every slot's notification is the same one, told apart by its [key] as its tag. One [withMo]
     * is said in the tone of his mood as it stands, read when it posts, and wears his face.
     */
    private fun post(
        message: (MoMood?) -> MoReminderMessage,
        withMo: Boolean,
        @DrawableRes smallIcon: Int,
        key: String,
        tap: () -> PendingIntent,
        quietly: Boolean,
        actions: (Resources) -> List<NotificationCompat.Action>,
    ): Boolean {
        if (!notificationsAllowed()) return false
        val face = if (withMo) moReminderFace(applicationContext) else null
        val said = message(face?.mood)
        val resources = applicationContext.withAppLanguage().resources
        val text = said.text.resolve(resources)
        val builder = NotificationCompat.Builder(applicationContext, ReminderNotificationChannel.Id)
            .setSmallIcon(smallIcon)
            .setLargeIcon(face?.largeIcon)
            .setContentTitle(said.title.resolve(resources))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setOnlyAlertOnce(quietly)
            .setContentIntent(tap())
            .setAutoCancel(true)
        actions(resources).forEach(builder::addAction)
        return runCatching { NotificationManagerCompat.from(applicationContext).notify(key, ChecklistReminderRequestCode, builder.build()); true }.getOrDefault(false)
    }

    private fun notificationsAllowed(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
}

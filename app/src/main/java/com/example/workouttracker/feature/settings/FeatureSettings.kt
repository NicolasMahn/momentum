package com.example.workouttracker.feature.settings

import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.example.workouttracker.R
import com.example.workouttracker.core.text.titleRes
import com.example.workouttracker.core.ui.MomentumBarAction
import com.example.workouttracker.core.ui.MomentumButton
import com.example.workouttracker.core.ui.ViewColors
import com.example.workouttracker.domain.reminder.ReminderSettings
import com.example.workouttracker.domain.reminder.ReminderTime
import com.example.workouttracker.domain.settings.DashboardFeature

/** Where a plain page's Save stands after a press, or after the phone answered what it asked. */
internal enum class FeatureSaveStep {
    /** The wanted values were stored. */
    Stored,

    /** Storing reported a failure. Whether the values were stored all the same is read from the settings. */
    Failed,

    /** The Save waits, and the phone has to be asked for exact alarms. */
    AskExactAlarm,

    /** The Save waits, and the phone has to be asked for notifications. */
    AskNotifications,

    /** The phone did not give what was asked, so nothing was stored and the Save is over. */
    Refused,
}

/**
 * Activity's reminder on its page as a draft: what the person wants of the workout reminder's
 * switch and time. A field not touched is null and follows what is stored. Nothing else of
 * [ReminderSettings] is kept, so a Save never stores a copy that went stale: the wanted values are
 * put on the settings as they are stored at that moment ([appliedTo]).
 *
 * Whether there is something to save is read, never remembered ([hasUnsavedWork]): the wanted
 * values differ from what is stored now. Once they are stored, by whatever route, the page is
 * clean, and while they are not, whatever went wrong, it has unsaved work.
 *
 * Switching a reminder on may need a permission, exact alarms before notifications. Then [save]
 * stores nothing and the Save waits ([waitingFor]); the phone's answer finishes it over the
 * settings stored by then ([exactAlarmAnswered], [notificationsAnswered]). The wanted values and
 * a waiting Save are kept across process death, as the permission screen may outlive the process.
 */
internal class FeatureSettingsDraft(
    enabled: Boolean? = null,
    time: ReminderTime? = null,
    waitingFor: ReminderAccessRequirement? = null,
) {
    /** The reminder's switch as wanted, or null while untouched. */
    var enabled: Boolean? by mutableStateOf(enabled)
        private set

    /** The reminder's time as wanted, or null while untouched. */
    var time: ReminderTime? by mutableStateOf(time)
        private set

    /** The permission a pressed Save waits for, or null while none waits. */
    var waitingFor: ReminderAccessRequirement? by mutableStateOf(waitingFor)
        private set

    fun remind(on: Boolean) {
        enabled = on
    }

    fun at(time: ReminderTime) {
        this.time = time
    }

    /** The switch as the page shows it over [stored]. */
    fun enabledIn(stored: ReminderSettings): Boolean = enabled ?: stored.workout.enabled

    /** The time as the page shows it over [stored]. */
    fun timeIn(stored: ReminderSettings): ReminderTime = time ?: stored.workout.fixedTime

    /** [stored] with the wanted values in place of its own; every other field is [stored]'s. */
    fun appliedTo(stored: ReminderSettings): ReminderSettings =
        stored.copy(workout = stored.workout.copy(enabled = enabledIn(stored), fixedTime = timeIn(stored)))

    fun hasUnsavedWork(stored: ReminderSettings): Boolean = appliedTo(stored) != stored

    /**
     * Save was pressed. The wanted values go over [stored], the settings as they are now, through
     * [store], unless switching a reminder on needs a permission the phone has not given: then
     * nothing is stored yet and the answer says which one to ask for. With nothing to save,
     * nothing is stored or asked.
     */
    fun save(
        stored: ReminderSettings,
        exactAlarmAllowed: Boolean,
        notificationsAllowed: Boolean,
        store: (ReminderSettings) -> Boolean,
    ): FeatureSaveStep {
        if (!hasUnsavedWork(stored)) return FeatureSaveStep.Stored
        val needsAccess = settingsChangeNeedsPlatformAccess(stored, appliedTo(stored))
        return when (reminderAccessRequirement(needsAccess, exactAlarmAllowed, notificationsAllowed)) {
            ReminderAccessRequirement.ExactAlarm -> waitFor(ReminderAccessRequirement.ExactAlarm)
            ReminderAccessRequirement.Notifications -> waitFor(ReminderAccessRequirement.Notifications)
            ReminderAccessRequirement.Ready, ReminderAccessRequirement.Stop -> finish(stored, store)
        }
    }

    /**
     * The phone's exact-alarm screen was left. If exact alarms were [granted], the waiting Save
     * goes on: it stores the wanted values over [stored], the settings as they are stored at this
     * moment, or waits on for notifications. Otherwise it is over with nothing stored. Null where
     * no Save waited for this.
     */
    fun exactAlarmAnswered(
        granted: Boolean,
        notificationsAllowed: Boolean,
        stored: ReminderSettings,
        store: (ReminderSettings) -> Boolean,
    ): FeatureSaveStep? {
        if (waitingFor != ReminderAccessRequirement.ExactAlarm) return null
        return when (reminderAccessAfterExactAlarm(granted, notificationsAllowed)) {
            ReminderAccessRequirement.Ready -> finish(stored, store)
            ReminderAccessRequirement.Notifications -> waitFor(ReminderAccessRequirement.Notifications)
            else -> refused()
        }
    }

    /** The phone answered the notification request, as [exactAlarmAnswered] reads its own. */
    fun notificationsAnswered(granted: Boolean, stored: ReminderSettings, store: (ReminderSettings) -> Boolean): FeatureSaveStep? {
        if (waitingFor != ReminderAccessRequirement.Notifications) return null
        return if (reminderAccessAfterNotifications(granted) == ReminderAccessRequirement.Ready) finish(stored, store) else refused()
    }

    /** The phone could not be asked at all, so the Save is over with nothing stored. */
    fun notAsked() {
        waitingFor = null
    }

    private fun waitFor(access: ReminderAccessRequirement): FeatureSaveStep {
        waitingFor = access
        return if (access == ReminderAccessRequirement.ExactAlarm) FeatureSaveStep.AskExactAlarm else FeatureSaveStep.AskNotifications
    }

    private fun finish(stored: ReminderSettings, store: (ReminderSettings) -> Boolean): FeatureSaveStep {
        waitingFor = null
        return if (store(appliedTo(stored))) FeatureSaveStep.Stored else FeatureSaveStep.Failed
    }

    private fun refused(): FeatureSaveStep {
        waitingFor = null
        return FeatureSaveStep.Refused
    }

    companion object {
        private const val Untouched = -1

        val Saver: Saver<FeatureSettingsDraft, Any> = listSaver(
            save = {
                listOf(
                    it.enabled?.toString().orEmpty(),
                    it.time?.hour ?: Untouched,
                    it.time?.minute ?: Untouched,
                    it.waitingFor?.name.orEmpty(),
                )
            },
            restore = {
                FeatureSettingsDraft(
                    enabled = (it[0] as String).toBooleanStrictOrNull(),
                    time = (it[1] as Int).takeIf { hour -> hour != Untouched }?.let { hour -> ReminderTime(hour, it[2] as Int) },
                    waitingFor = (it[3] as String).ifEmpty { null }?.let(ReminderAccessRequirement::valueOf),
                )
            },
        )
    }
}

/**
 * Activity's settings where the host hands no configuration of it, on the page every view has
 * ([ViewSettingsFrame]) with the one part left: Reminders. Its switch and time change [draft] and
 * are stored by [onSave]; until then the page has unsaved work, read from [settings] as they are
 * stored, and asks before it is left. [reminder] is the reminder row over the draft, which belongs
 * to Settings with the permissions it may need; it is handed the way to pick the time, which
 * opens on the page's time, changes the page on OK and nothing on Cancel. Activity is built in, so
 * its Delete is disabled and the bar says to switch it off instead; what went wrong in Settings
 * ([message]) takes the bar's line before that.
 */
@Composable
internal fun FeatureSettingsPage(
    draft: FeatureSettingsDraft,
    settings: ReminderSettings,
    colors: ViewColors,
    onSave: () -> Unit,
    reminder: @Composable (onEditTime: () -> Unit) -> Unit,
    leaveRequested: Boolean,
    onLeaveAnswered: (discard: Boolean) -> Unit,
    onUnsavedWorkChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    message: String? = null,
) {
    val unsavedWork = draft.hasUnsavedWork(settings)
    var pickingTime by rememberSaveable { mutableStateOf(false) }

    ViewSettingsFrame(
        testTag = "feature_settings",
        pageKey = SettingsPage.Feature(DashboardFeature.Activity).key,
        colors = colors,
        parts = listOf(ChecklistPage.Reminders),
        selected = ChecklistPage.Reminders,
        onSelect = {},
        partTestTag = { "feature_settings_tab_${it.tagName}" },
        unsavedWork = unsavedWork,
        discard = DiscardQuestion(
            title = stringResource(R.string.checklist_editor_discard_title),
            message = stringResource(R.string.checklist_editor_discard_message, stringResource(DashboardFeature.Activity.titleRes)),
        ),
        leaveRequested = leaveRequested,
        onLeaveAnswered = onLeaveAnswered,
        onUnsavedWorkChange = onUnsavedWorkChange,
        error = message?.let { BarLine(it, "settings_reminder_error") },
        delete = ViewDelete.BuiltIn,
        buttons = {
            MomentumBarAction(
                text = stringResource(R.string.settings_feature_save),
                onClick = onSave,
                ready = unsavedWork,
                testTag = "feature_settings_save",
            )
        },
        modifier = modifier,
        dialogs = {
            if (pickingTime) {
                SettingsTimePickerDialog(
                    initialTime = draft.timeIn(settings),
                    onDismiss = { pickingTime = false },
                    onConfirm = { time ->
                        pickingTime = false
                        draft.at(time)
                    },
                )
            }
        },
    ) {
        reminder { pickingTime = true }
    }
}

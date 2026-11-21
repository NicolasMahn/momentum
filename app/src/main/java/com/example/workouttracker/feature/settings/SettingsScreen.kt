package com.example.workouttracker.feature.settings

import com.example.workouttracker.domain.substance.substanceAskedDay
import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import com.example.workouttracker.persistence.backup.DurableBackupManager
import com.example.workouttracker.core.ui.ViewIdentity
import com.example.workouttracker.core.ui.MomentumCard
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.runtime.collectAsState
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Luggage
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Sick
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerColors
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.TimePickerDialog
import androidx.compose.material3.TimePickerDialogDefaults
import androidx.compose.material3.TimePickerDisplayMode
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.LaunchedEffect
import com.example.workouttracker.feature.tracker.TrackerModules
import com.example.workouttracker.feature.tracker.TrackerView
import com.example.workouttracker.feature.tracker.TrackerSettingsHost
import com.example.workouttracker.feature.tracker.TrackerReminderFlow
import com.example.workouttracker.feature.tracker.TrackerReminderEffect
import com.example.workouttracker.feature.tracker.TrackerVisibilityFlow
import com.example.workouttracker.feature.tracker.TrackerVisibilityEffect
import com.example.workouttracker.feature.tracker.trackerRef
import com.example.workouttracker.feature.about.AboutPage
import com.example.workouttracker.feature.about.LegalText
import com.example.workouttracker.feature.about.LegalTextPage
import com.example.workouttracker.feature.about.LicensePage
import com.example.workouttracker.feature.tracker.trackerCreationType
import com.example.workouttracker.feature.tracker.trackerCreationVariant
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.example.workouttracker.R
import com.example.workouttracker.core.locale.currentLocale
import com.example.workouttracker.core.time.currentDateKey
import com.example.workouttracker.core.text.UiText
import com.example.workouttracker.core.text.resolve
import com.example.workouttracker.core.text.titleRes
import com.example.workouttracker.core.text.uiText
import com.example.workouttracker.core.ui.MomentumButton
import com.example.workouttracker.core.ui.MomentumButtonLabel
import com.example.workouttracker.core.ui.MomentumButtonVariant
import com.example.workouttracker.core.ui.ReservedMessage
import com.example.workouttracker.core.ui.ViewTheme
import com.example.workouttracker.domain.reminder.Bedtime
import com.example.workouttracker.domain.mo.MoMood
import com.example.workouttracker.domain.reminder.MoCheckInSettings
import com.example.workouttracker.domain.AppState
import com.example.workouttracker.feature.checklist.tagName
import com.example.workouttracker.domain.checklist.CheckpointTime
import com.example.workouttracker.domain.checklist.isInUse
import com.example.workouttracker.domain.reminder.ReminderSettings
import com.example.workouttracker.domain.reminder.usualNight
import com.example.workouttracker.feature.checklist.presentation
import com.example.workouttracker.domain.reminder.ReminderTime
import com.example.workouttracker.domain.reminder.WorkoutReminderSettings
import com.example.workouttracker.domain.settings.DashboardFeature
import com.example.workouttracker.persistence.FeatureVisibilitySettingsCodec
import com.example.workouttracker.domain.settings.FeatureVisibilitySettings
import com.example.workouttracker.domain.substance.SubstanceCheckIn
import com.example.workouttracker.domain.substance.SubstanceEdit
import com.example.workouttracker.domain.substance.SubstanceSettings
import com.example.workouttracker.domain.holiday.DayOff
import com.example.workouttracker.domain.holiday.HolidaySchedule
import com.example.workouttracker.domain.activity.ActivityConfiguration
import com.example.workouttracker.domain.activity.SeededActivityRules
import com.example.workouttracker.domain.activity.ActivityEdit
import com.example.workouttracker.domain.activity.NamedActivityIds
import com.example.workouttracker.feature.logactivity.logActivityPresentation
import com.example.workouttracker.feature.substances.viewIdentity
import com.example.workouttracker.feature.substances.viewLabel
import com.example.workouttracker.reminder.MoReminderSubject
import com.example.workouttracker.reminder.TestReminder
import com.example.workouttracker.reminder.canScheduleExactReminders
import com.example.workouttracker.reminder.exactAlarmAccessIntent
import com.example.workouttracker.reminder.moCheckInTestReminder
import com.example.workouttracker.reminder.sendTestReminder
import com.example.workouttracker.reminder.moNudgeTimes
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

@Composable
internal fun SettingsScreen(
    settings: ReminderSettings,
    onSettingsChange: (ReminderSettings) -> Boolean,
    todayKey: String,
    trackerModules: TrackerModules,
    onActivitySave: (edits: List<ActivityEdit>) -> ViewWrite,
    featureVisibility: FeatureVisibilitySettings = FeatureVisibilitySettings(),
    onFeatureVisibilityChange: (FeatureVisibilitySettings) -> Boolean = { true },
    visibilityInUse: FeatureVisibilitySettings = featureVisibility,
    page: SettingsPage? = null,
    onOpenPage: (SettingsPage) -> Unit = {},
    onClosePage: () -> Unit = {},
    activityConfiguration: ActivityConfiguration? = null,
    activityReminder: () -> MoReminderSubject.Activity,
    activityNamedIds: NamedActivityIds = NamedActivityIds(),
    rewardsDay: java.time.LocalDate? = null,
    holidaySchedule: HolidaySchedule = HolidaySchedule(),
    onHolidayScheduleChange: (HolidaySchedule) -> Boolean = { true },
    substanceSettings: SubstanceSettings = SubstanceSettings(),
    recordedSubstances: Set<String> = emptySet(),
    onSubstancesSave: (List<SubstanceEdit>) -> Boolean = { true },
    leaveRequested: Boolean = false,
    onLeaveAnswered: (discard: Boolean) -> Unit = {},
    onUnsavedWorkChange: (Boolean) -> Unit = {},
    bedtime: Bedtime = settings.usualNight,
    mood: MoMood? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val presentation = settingsPresentation()
    val today = rewardsDay ?: java.time.LocalDate.parse(todayKey)
    var errorRes by remember { mutableStateOf<Int?>(null) }
    var timePickerTarget by rememberSaveable { mutableStateOf<TimePickerTarget?>(null) }
    var pendingNotificationSettings by rememberSaveable(stateSaver = ReminderSettingsSaver) {
        mutableStateOf<ReminderSettings?>(null)
    }
    var pendingExactAlarmSettings by rememberSaveable(stateSaver = ReminderSettingsSaver) {
        mutableStateOf<ReminderSettings?>(null)
    }
    var pendingNotificationVisibility by rememberSaveable(stateSaver = FeatureVisibilitySettingsSaver) {
        mutableStateOf<FeatureVisibilitySettings?>(null)
    }
    var pendingExactAlarmVisibility by rememberSaveable(stateSaver = FeatureVisibilitySettingsSaver) {
        mutableStateOf<FeatureVisibilitySettings?>(null)
    }
    val trackerReminderFlow = rememberSaveable(saver = TrackerReminderFlow.Saver) { TrackerReminderFlow() }
    val trackerVisibilityFlow = rememberSaveable(saver = TrackerVisibilityFlow.Saver) { TrackerVisibilityFlow() }
    var trackerAccessRequest by remember { mutableStateOf<ReminderAccessRequirement?>(null) }
    var notificationsAllowed by remember { mutableStateOf(notificationsEnabled(context)) }
    var exactAlarmAllowed by remember { mutableStateOf(canScheduleExactReminders(context)) }
    // A test waiting for notifications to be allowed, with the habit day it was pressed on. Unlike a
    // setting, it is not kept across process death: one lost on the way back from the permission
    // screen costs one more press.
    var pendingTest by remember { mutableStateOf<PendingTest?>(null) }

    // Activity's page keeps what the person wants of its reminder until Save. It is kept here,
    // not on the page, because its Save may wait for a permission, and the phone's answer comes
    // back to Settings.
    val featureDraft = (page as? SettingsPage.Feature)?.takeIf { it.feature == DashboardFeature.Activity }?.let { open ->
        rememberSaveable(open.key, saver = FeatureSettingsDraft.Saver) { FeatureSettingsDraft() }
    }
    // Configuration and reminder drafts share one save continuation while platform access is requested.
    val settingUp = LocalSettingUp.current

    val activityPageSave: ActivityPageSave? = featureDraft?.let { reminderDraft ->
        activityConfiguration?.let { stored -> rememberBuiltInViewSave(stored, today, reminderDraft, ActivitySettingsDraft.Saver) { activity, day -> ActivitySettingsDraft(activity, day) } }
    }

    fun refreshAccess() {
        notificationsAllowed = notificationsEnabled(context)
        exactAlarmAllowed = canScheduleExactReminders(context)
    }

    // Both permissions can be changed in system settings while this screen is only stopped, so the
    // rows are re-read on the way back in rather than left stale until the screen is rebuilt.
    LifecycleResumeEffect(Unit) {
        refreshAccess()
        onPauseOrDispose {}
    }

    fun persist(updated: ReminderSettings): Boolean {
        val stored = onSettingsChange(updated)
        errorRes = if (stored) null else R.string.settings_error_reminders
        return stored
    }

    fun persistVisibility(updated: FeatureVisibilitySettings) {
        errorRes = if (onFeatureVisibilityChange(updated)) {
            null
        } else {
            R.string.settings_error_features
        }
    }

    /**
     * A built-in view's page saved and left. Setting a view up in the first start means wanting it,
     * so saving its page there switches it on, as creating a view does; elsewhere a page leaves the
     * Home switch as it was. Its reminder was saved with the access it needs, so nothing is asked.
     */
    fun closeSaved(feature: DashboardFeature) {
        if (settingUp && !featureVisibility.isVisible(feature)) persistVisibility(featureVisibility.withVisibility(feature, true, today))
        onClosePage()
    }

    fun deliverTest(send: () -> Boolean) {
        errorRes = if (send()) null else R.string.settings_error_test
    }

    /**
     * The waiting test, taken once. A test is built for the day it was pressed on, so one that
     * waited past 04:00 would answer for yesterday, as its Taken or reply would record; it is
     * dropped instead, and the next press builds today's.
     */
    fun takePendingTest(): (() -> Boolean)? {
        val waiting = pendingTest
        pendingTest = null
        return waiting?.takeIf { it.dayKey == currentDateKey() }?.send
    }

    fun applyTrackerEffect(effect: TrackerReminderEffect) {
        when (effect) {
            is TrackerReminderEffect.RequestAccess -> trackerAccessRequest = effect.requirement
            is TrackerReminderEffect.Finished -> when (effect.write) {
                ViewWrite.Stored -> if (page?.trackerRef == effect.target.view) onClosePage()
                ViewWrite.NotStored -> errorRes = R.string.settings_error_view
                ViewWrite.StoredWithoutAlarm, ViewWrite.ReminderWaits -> errorRes = R.string.measurement_settings_alarm_not_set
            }
            is TrackerReminderEffect.Refused -> errorRes = R.string.measurement_settings_alarm_not_set
            is TrackerReminderEffect.Unavailable -> errorRes = R.string.settings_error_view
            TrackerReminderEffect.None -> Unit
        }
    }

    fun applyTrackerVisibilityEffect(effect: TrackerVisibilityEffect) {
        when (effect) {
            is TrackerVisibilityEffect.RequestAccess -> trackerAccessRequest = effect.requirement
            is TrackerVisibilityEffect.Changed -> errorRes = if (effect.stored) null else R.string.settings_error_features
            is TrackerVisibilityEffect.Refused -> errorRes = if (canScheduleExactReminders(context)) R.string.settings_error_notifications_off else R.string.settings_error_precise_timing_off
            is TrackerVisibilityEffect.Unavailable -> errorRes = R.string.settings_error_features
            TrackerVisibilityEffect.None -> Unit
        }
    }

    val notificationSettingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        refreshAccess()
        // A test that led to the system screen is sent once notifications are on, and dropped otherwise.
        val test = takePendingTest()
        if (test != null && notificationsEnabled(context)) deliverTest(test)
    }

    fun openNotificationSettings() {
        val opened = runCatching {
            notificationSettingsLauncher.launch(appNotificationSettingsIntent(context))
        }.isSuccess
        if (!opened) errorRes = R.string.settings_error_open_notifications
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        refreshAccess()
        if (trackerReminderFlow.pending?.waitingFor == ReminderAccessRequirement.Notifications) {
            applyTrackerEffect(trackerReminderFlow.answer(canScheduleExactReminders(context), granted, trackerModules))
            return@rememberLauncherForActivityResult
        }
        if (trackerVisibilityFlow.pending?.waitingFor == ReminderAccessRequirement.Notifications) {
            applyTrackerVisibilityEffect(trackerVisibilityFlow.answer(canScheduleExactReminders(context), granted, trackerModules))
            return@rememberLauncherForActivityResult
        }
        val pendingSettings = pendingNotificationSettings
        val pendingVisibility = pendingNotificationVisibility
        pendingNotificationSettings = null
        pendingNotificationVisibility = null
        // A page's waiting Save holds no settings: what it wants goes on the ones stored now. A
        // refusal ends it like any waiting change, below. Stored, the page returns to the list.
        val closes = activityPageSave?.let { it.notificationsAnswered(granted, settings, ::persist) == BuiltInViewSaveStep.Close }
            ?: (featureDraft?.notificationsAnswered(granted, settings, ::persist) == FeatureSaveStep.Stored)
        if (closes) closeSaved(DashboardFeature.Activity)
        when (reminderAccessAfterNotifications(granted)) {
            ReminderAccessRequirement.Ready -> {
                pendingSettings?.let(::persist)
                pendingVisibility?.let(::persistVisibility)
                takePendingTest()?.let(::deliverTest)
            }
            // A denial the system never actually asked about must not dead-end the button, so it
            // hands the user the system screen where the switch really lives; a waiting test
            // follows it there and is sent on the way back.
            else -> when (
                notificationAccessFollowUp(granted, canAskForNotificationsAgain(context))
            ) {
                NotificationAccessAction.OpenSystemSettings -> openNotificationSettings()
                else -> {
                    pendingTest = null
                    errorRes = R.string.settings_error_notifications_off
                }
            }
        }
    }

    val exactAlarmAccessLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        refreshAccess()
        if (trackerReminderFlow.pending?.waitingFor == ReminderAccessRequirement.ExactAlarm) {
            applyTrackerEffect(trackerReminderFlow.answer(canScheduleExactReminders(context), notificationsEnabled(context), trackerModules))
            return@rememberLauncherForActivityResult
        }
        if (trackerVisibilityFlow.pending?.waitingFor == ReminderAccessRequirement.ExactAlarm) {
            applyTrackerVisibilityEffect(trackerVisibilityFlow.answer(canScheduleExactReminders(context), notificationsEnabled(context), trackerModules))
            return@rememberLauncherForActivityResult
        }
        val pendingSettings = pendingExactAlarmSettings
        val pendingVisibility = pendingExactAlarmVisibility
        pendingExactAlarmSettings = null
        pendingExactAlarmVisibility = null
        if (pendingSettings != null || pendingVisibility != null) {
            when (
                reminderAccessAfterExactAlarm(
                    granted = canScheduleExactReminders(context),
                    notificationsAllowed = notificationsEnabled(context),
                )
            ) {
                ReminderAccessRequirement.Ready -> {
                    pendingSettings?.let(::persist)
                    pendingVisibility?.let(::persistVisibility)
                }
                ReminderAccessRequirement.Notifications -> {
                    pendingNotificationSettings = pendingSettings
                    pendingNotificationVisibility = pendingVisibility
                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                ReminderAccessRequirement.Stop -> errorRes = R.string.settings_error_precise_timing_off
                else -> error("Unexpected exact alarm permission step")
            }
        }
        if (activityPageSave != null) {
            when (activityPageSave.exactAlarmAnswered(canScheduleExactReminders(context), notificationsEnabled(context), settings, ::persist)) {
                BuiltInViewSaveStep.AskNotifications -> permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                BuiltInViewSaveStep.ReminderRefused -> errorRes = R.string.settings_error_precise_timing_off
                BuiltInViewSaveStep.Close -> closeSaved(DashboardFeature.Activity)
                else -> Unit
            }
            return@rememberLauncherForActivityResult
        }
        when (
            featureDraft?.exactAlarmAnswered(
                granted = canScheduleExactReminders(context),
                notificationsAllowed = notificationsEnabled(context),
                stored = settings,
                store = ::persist,
            )
        ) {
            FeatureSaveStep.AskNotifications -> permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            FeatureSaveStep.Refused -> errorRes = R.string.settings_error_precise_timing_off
            FeatureSaveStep.Stored -> closeSaved(DashboardFeature.Activity)
            // Not stored, storing said so itself; and no Save may have waited at all.
            else -> Unit
        }
    }

    LaunchedEffect(trackerAccessRequest) {
        val access = trackerAccessRequest ?: return@LaunchedEffect
        trackerAccessRequest = null
        val opened = runCatching {
            if (access == ReminderAccessRequirement.Notifications) permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            else exactAlarmAccessLauncher.launch(exactAlarmAccessIntent(context))
        }.isSuccess
        if (!opened) {
            trackerReminderFlow.cancel()
            trackerVisibilityFlow.cancel()
            errorRes = if (access == ReminderAccessRequirement.Notifications) R.string.settings_error_open_notifications else R.string.settings_error_open_precise_timing
        }
    }

    fun requestNotificationAccess() {
        when (notificationAccessAction(Build.VERSION.SDK_INT, notificationsEnabled(context))) {
            NotificationAccessAction.RequestPermission -> {
                // Carrying the current settings makes the grant reschedule whatever is already on.
                // Without precise timing nothing can be scheduled yet, and its own row says so:
                // allowing that reschedules instead.
                pendingNotificationSettings = settings.takeIf { canScheduleExactReminders(context) }
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            NotificationAccessAction.OpenSystemSettings -> openNotificationSettings()
            NotificationAccessAction.None -> refreshAccess()
        }
    }

    /**
     * Sends a reminder now, as it would arrive at its time. Without notifications it asks for them
     * the way Allow does and sends once they are allowed. It needs no exact alarm: nothing is
     * scheduled.
     */
    fun sendTestCommand(send: () -> Boolean) {
        when (notificationAccessAction(Build.VERSION.SDK_INT, notificationsEnabled(context))) {
            NotificationAccessAction.RequestPermission -> {
                pendingTest = PendingTest(send, currentDateKey())
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            NotificationAccessAction.OpenSystemSettings -> {
                pendingTest = PendingTest(send, currentDateKey())
                openNotificationSettings()
            }
            NotificationAccessAction.None -> deliverTest(send)
        }
    }

    fun sendTest(test: TestReminder) = sendTestCommand { context.sendTestReminder(test) }

    fun requestExactAlarmAccess() {
        pendingExactAlarmSettings = settings
        val launched = runCatching {
            exactAlarmAccessLauncher.launch(exactAlarmAccessIntent(context))
        }.isSuccess
        if (!launched) {
            pendingExactAlarmSettings = null
            errorRes = R.string.settings_error_open_precise_timing
        }
    }

    fun requestAccessFor(dependency: ReminderDependency) {
        when (dependency) {
            ReminderDependency.PreciseTiming -> requestExactAlarmAccess()
            ReminderDependency.Notifications -> requestNotificationAccess()
            ReminderDependency.Hidden, ReminderDependency.Ready -> Unit
        }
    }

    fun applySettings(updated: ReminderSettings) {
        when (
            reminderAccessRequirement(
                enabled = settingsChangeNeedsPlatformAccess(settings, updated) ||
                    ((settings.fallbackAlarmTime != updated.fallbackAlarmTime || settings.fixedBedtime != updated.fixedBedtime) &&
                        trackerModules.views.any { it.visible && it.reminders.any { reminder -> reminder.enabled } }),
                exactAlarmAllowed = canScheduleExactReminders(context),
                notificationsAllowed = notificationsEnabled(context),
            )
        ) {
            ReminderAccessRequirement.Ready -> persist(updated)
            ReminderAccessRequirement.Notifications -> {
                pendingNotificationSettings = updated
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            ReminderAccessRequirement.ExactAlarm -> {
                pendingExactAlarmSettings = updated
                val launched = runCatching {
                    exactAlarmAccessLauncher.launch(exactAlarmAccessIntent(context))
                }.isSuccess
                if (!launched) {
                    pendingExactAlarmSettings = null
                    errorRes = R.string.settings_error_open_precise_timing
                }
            }
            ReminderAccessRequirement.Stop -> Unit
        }
    }

    /**
     * Save on a plain view's page: what the page wants is stored over the settings as they are
     * now, through the flow a switch uses, or the phone is asked first for what switching a
     * reminder on needs. The answer finishes the Save where the other waiting changes finish.
     * Stored, the page has done its work and returns to the list, as every view's page does.
     */
    fun saveFeaturePage(draft: FeatureSettingsDraft) {
        when (draft.save(settings, canScheduleExactReminders(context), notificationsEnabled(context), ::persist)) {
            FeatureSaveStep.AskNotifications -> permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            FeatureSaveStep.AskExactAlarm -> {
                val launched = runCatching {
                    exactAlarmAccessLauncher.launch(exactAlarmAccessIntent(context))
                }.isSuccess
                if (!launched) {
                    draft.notAsked()
                    errorRes = R.string.settings_error_open_precise_timing
                }
            }
            FeatureSaveStep.Stored -> closeSaved(DashboardFeature.Activity)
            FeatureSaveStep.Failed, FeatureSaveStep.Refused -> Unit
        }
    }

    /**
     * Save a page whose configuration and reminder use separate stores ([BuiltInViewSave]): the
     * configuration first, through [store], then the reminder through the flow a plain page's
     * Save uses, asking the phone where switching it on needs to. With nothing left in either
     * draft and nothing failed, the page returns to the list.
     */
    fun <E> saveActivityPage(save: BuiltInViewSave<*, E, *>, store: (edits: List<E>) -> ViewWrite) {
        val step = save.save(
            settings = settings,
            exactAlarmAllowed = canScheduleExactReminders(context),
            notificationsAllowed = notificationsEnabled(context),
            // Alarm access belongs to the separate reminder write, not the configuration write.
            storeConfiguration = { edits ->
                (store(edits) != ViewWrite.NotStored).also { stored -> errorRes = if (stored) null else R.string.settings_error_view }
            },
            storeReminder = ::persist,
        )
        when (step) {
            BuiltInViewSaveStep.AskNotifications -> permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            BuiltInViewSaveStep.AskExactAlarm -> {
                val launched = runCatching {
                    exactAlarmAccessLauncher.launch(exactAlarmAccessIntent(context))
                }.isSuccess
                if (!launched) {
                    save.notAsked()
                    errorRes = R.string.settings_error_open_precise_timing
                }
            }
            BuiltInViewSaveStep.Close -> closeSaved(DashboardFeature.Activity)
            // What went wrong is on the bar already, said by whichever store failed.
            BuiltInViewSaveStep.ConfigurationFailed, BuiltInViewSaveStep.ReminderFailed, BuiltInViewSaveStep.ReminderRefused, BuiltInViewSaveStep.Stay -> Unit
        }
    }

    fun applyVisibility(updated: FeatureVisibilitySettings, resumesReminder: Boolean) {
        when (
            reminderAccessRequirement(
                enabled = resumesReminder,
                exactAlarmAllowed = canScheduleExactReminders(context),
                notificationsAllowed = notificationsEnabled(context),
            )
        ) {
            ReminderAccessRequirement.Ready -> persistVisibility(updated)
            ReminderAccessRequirement.Notifications -> {
                pendingNotificationVisibility = updated
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            ReminderAccessRequirement.ExactAlarm -> {
                pendingExactAlarmVisibility = updated
                val launched = runCatching {
                    exactAlarmAccessLauncher.launch(exactAlarmAccessIntent(context))
                }.isSuccess
                if (!launched) {
                    pendingExactAlarmVisibility = null
                    errorRes = R.string.settings_error_open_precise_timing
                }
            }
            ReminderAccessRequirement.Stop -> Unit
        }
    }

    fun applyFeatureVisibility(feature: DashboardFeature, visible: Boolean) =
        applyVisibility(featureVisibility.withVisibility(feature, visible, today), resumesReminder = visible && featureHasEnabledReminder(feature, settings, substanceSettings.checkIn))

    fun setMoCheckInEnabled(enabled: Boolean) {
        applySettings(settings.copy(moCheckIn = settings.moCheckIn.copy(enabled = enabled)))
    }

    // In the first start a view's page is where it is set up, and saving it switches it on, so
    // its reminder can be chosen there before the view is on.
    fun dependencyOf(feature: DashboardFeature): ReminderDependency = reminderDependency(
        featureVisible = featureVisibility.isVisible(feature) || settingUp,
        exactAlarmAllowed = exactAlarmAllowed,
        notificationsAllowed = notificationsAllowed,
    )

    val activity = activityConfiguration?.presentation() ?: logActivityPresentation()
    val substances = substanceSettings.viewIdentity()

    /** The row of Activity or Substances: its Home switch, and its page. */
    @Composable
    fun FeatureRow(feature: DashboardFeature, label: String?, identity: ViewIdentity, note: String? = null, onOpen: (() -> Unit)?) = ViewRow(
        note = note,
        label = label ?: stringResource(feature.titleRes),
        tagName = feature.name.lowercase(Locale.ROOT),
        identity = identity,
        visible = featureVisibility.isVisible(feature),
        onVisibleChange = { applyFeatureVisibility(feature, it) },
        onOpen = onOpen,
    )

    @Composable
    fun TrackerRow(view: TrackerView, tagName: String = view.tagName) = ViewRow(
        label = view.label,
        tagName = tagName,
        identity = view.identity(),
        visible = view.visible && view.usable,
        onVisibleChange = { visible ->
            trackerReminderFlow.cancel()
            applyTrackerVisibilityEffect(trackerVisibilityFlow.change(view, visible, canScheduleExactReminders(context), notificationsEnabled(context)))
        },
        onOpen = { onOpenPage(SettingsPage.Tracker(view.ref)) },
        enabled = view.usable,
        note = view.unavailableNote?.resolve(),
    )

    ViewTheme(presentation.colors) {
        timePickerTarget?.let { target ->
            val initialTime = when (target) {
                TimePickerTarget.FallbackAlarm -> settings.fallbackAlarmTime
                TimePickerTarget.Bedtime -> settings.usualBedtime
            }
            SettingsTimePickerDialog(
                initialTime = initialTime,
                onDismiss = { timePickerTarget = null },
                onConfirm = { selectedTime ->
                    timePickerTarget = null
                    val updated = when (target) {
                        TimePickerTarget.FallbackAlarm -> settings.copy(
                            fallbackAlarmTime = selectedTime,
                        )
                        TimePickerTarget.Bedtime -> settings.copy(fixedBedtime = selectedTime)
                    }
                    applySettings(updated)
                },
            )
        }

        val trackerHost = TrackerSettingsHost(
            today = today,
            bedtime = bedtime,
            daysOff = holidaySchedule,
            leaveRequested = leaveRequested,
            onLeaveAnswered = onLeaveAnswered,
            onUnsavedWorkChange = onUnsavedWorkChange,
            onClose = onClosePage,
            message = errorRes?.let { stringResource(it) },
            modifier = modifier.fillMaxSize(),
            reminderRow = { row, onChange ->
                FeatureReminder(
                    label = stringResource(R.string.checklist_settings_reminder),
                    checked = row.checkpoint.remind,
                    onCheckedChange = onChange,
                    dependency = reminderDependency(
                        featureVisible = trackerModules.view(row.target.view)?.visible ?: true,
                        exactAlarmAllowed = exactAlarmAllowed,
                        notificationsAllowed = notificationsAllowed,
                    ),
                    featureLabel = row.label,
                    timing = checkpointTimingText(row.checkpoint.at, settings).resolve(),
                    onRequestAccess = ::requestAccessFor,
                    testTag = row.testTag,
                    timingTestTag = row.timingTestTag,
                )
            },
            sendTest = ::sendTestCommand,
            saveWithAccess = { target, saveOrdinary, saveAll ->
                trackerVisibilityFlow.cancel()
                val result = trackerReminderFlow.start(target, canScheduleExactReminders(context), notificationsEnabled(context), saveOrdinary, saveAll)
                (result.effect as? TrackerReminderEffect.RequestAccess)?.let(::applyTrackerEffect)
                result.write
            },
        )

        // The workout reminder's row over its page's draft, the same on the page with and without a
        // stored configuration: the switch with its time, the days it comes on, which are the edited
        // week's as the page shows it, what it still needs and a test. The row's time opens the
        // page's picker through what it is handed.
        val workoutReminder: @Composable (FeatureSettingsDraft, ActivityConfiguration?, onEditTime: () -> Unit) -> Unit = { draft, edited, onEditTime ->
            val week = (edited ?: activityConfiguration ?: SeededActivityRules).week.on(today).chosen
            FeatureReminder(
                label = stringResource(R.string.settings_reminder_workout),
                checked = draft.enabledIn(settings),
                onCheckedChange = draft::remind,
                dependency = dependencyOf(DashboardFeature.Activity),
                featureLabel = activityConfiguration?.label ?: stringResource(DashboardFeature.Activity.titleRes),
                timing = workoutTimingText(week, draft.timeIn(settings), currentLocale()).resolve(),
                onRequestAccess = ::requestAccessFor,
                // There is always a test to send; what it says is read as the records stand at the tap.
                test = TestReminder.Activity(activityReminder()),
                onSendTest = { sendTest(TestReminder.Activity(activityReminder())) },
                testTag = "settings_workout_reminder",
                timingTestTag = "settings_workout_timing",
                timeLabel = formatTime(draft.timeIn(settings)),
                timeTestTag = "settings_workout_time",
                onEditTime = onEditTime,
            )
        }

        val moduleView = page?.trackerRef?.let(trackerModules::view)
        val moduleCreation = page?.trackerCreationType?.let { trackerModules.creation(it, page.trackerCreationVariant) }
        when {
            moduleView != null -> key(moduleView.ref) { moduleView.Settings(trackerHost) }
            moduleCreation != null -> key(moduleCreation.typeId, moduleCreation.variant) { moduleCreation.Setup(trackerHost) }
            else -> when (page) {
            // The check-in's switch is a feature reminder's row, with the permissions Settings owns;
            // its answer goes to the page's draft, saved with it. A test's tap opens Substances
            // only where Home can open it today.
            SettingsPage.Substances -> SubstanceSettingsPage(
                substances = substanceSettings,
                recorded = recordedSubstances,
                today = today,
                bedtime = bedtime,
                onSave = onSubstancesSave,
                reminderRow = { checkIn: SubstanceCheckIn, onRemindChange: (Boolean) -> Unit ->
                    FeatureReminder(
                        label = stringResource(R.string.substance_settings_check_in),
                        checked = checkIn.remind,
                        onCheckedChange = onRemindChange,
                        dependency = dependencyOf(DashboardFeature.Substances),
                        featureLabel = substanceSettings.viewLabel().resolve(),
                        timing = checkpointTimingText(checkIn.at, settings).resolve(),
                        onRequestAccess = ::requestAccessFor,
                        test = TestReminder.Substances(substanceAskedDay(today).toString(), opens = visibilityInUse.isVisible(DashboardFeature.Substances)),
                        onSendTest = ::sendTest,
                        testTag = "substance_settings_reminder",
                        timingTestTag = "substance_settings_reminder_timing",
                    )
                },
                onClose = { closeSaved(DashboardFeature.Substances) },
                switchesOn = settingUp && !featureVisibility.isVisible(DashboardFeature.Substances),
                // With nothing tracked the page is opened to choose, from the screen's own button
                // too, so it opens on the substances; a first start walks every part from Look.
                opensOn = if (substanceSettings.tracked.isEmpty() && !settingUp) ChecklistPage.Items else ChecklistPage.Look,
                leaveRequested = leaveRequested,
                onLeaveAnswered = onLeaveAnswered,
                onUnsavedWorkChange = onUnsavedWorkChange,
                message = errorRes?.let { stringResource(it) },
                modifier = modifier.fillMaxSize(),
            )

            // Activity's reminder row is Settings', with the permissions it may need; its
            // switch and time answer to the page's draft, stored with Save. The schedule under
            // the switch follows the page too, so the row never says a time the page does not show.
            is SettingsPage.Feature -> featureDraft?.let { draft ->
                if (activityPageSave != null) {
                    ActivitySettingsPage(
                        save = activityPageSave,
                        settings = settings,
                        named = activityNamedIds,
                        onSave = { saveActivityPage(activityPageSave, onActivitySave) },
                        reminder = { edited, onEditTime -> workoutReminder(draft, edited, onEditTime) },
                        leaveRequested = leaveRequested,
                        onLeaveAnswered = onLeaveAnswered,
                        onUnsavedWorkChange = onUnsavedWorkChange,
                        message = errorRes?.let { stringResource(it) },
                        modifier = modifier.fillMaxSize(),
                        switchesOn = settingUp && !featureVisibility.isVisible(DashboardFeature.Activity),
                    )
                } else {
                    FeatureSettingsPage(
                        draft = draft,
                        settings = settings,
                        colors = activity.colors,
                        onSave = { saveFeaturePage(draft) },
                        reminder = { onEditTime -> workoutReminder(draft, null, onEditTime) },
                        leaveRequested = leaveRequested,
                        onLeaveAnswered = onLeaveAnswered,
                        onUnsavedWorkChange = onUnsavedWorkChange,
                        message = errorRes?.let { stringResource(it) },
                        modifier = modifier.fillMaxSize(),
                    )
                }
            }

            else -> {
            // Every view, each with its switch and page, and the one way in for whatever is
            // added, however many kinds there are: the page behind it asks which.
            val viewList: @Composable (firstStart: Boolean) -> Unit = { firstStart ->
                val viewRows = trackerModules.views.map { view -> SettingsListEntry(view.settingsOrder) { TrackerRow(view) } }
                val places = listOfNotNull(
                    SettingsListEntry(0) {
                        FeatureRow(DashboardFeature.Activity, activityConfiguration?.label, activity, note = if (firstStart) stringResource(R.string.settings_activity_description) else null) {
                            onOpenPage(SettingsPage.Feature(DashboardFeature.Activity))
                        }
                    },
                    SettingsListEntry(40) {
                        FeatureRow(DashboardFeature.Substances, substanceSettings.label, substances, note = if (firstStart) stringResource(R.string.settings_substances_description) else null) {
                            onOpenPage(SettingsPage.Substances)
                        }
                    },
                )
                (viewRows + places).sortedBy { it.order }.forEach { it.content() }
                if (!firstStart) {
                    MomentumButton(
                        text = stringResource(R.string.settings_new),
                        onClick = { onOpenPage(SettingsPage.NewView) },
                        modifier = Modifier.fillMaxWidth(),
                        variant = MomentumButtonVariant.Secondary,
                        icon = Icons.Rounded.Add,
                        testTag = "settings_new",
                    )
                }
            }
            // A page opened from another, as a license is from About, starts at its own top, and
            // Back finds the first where it was left: each page keeps its own scroll position.
            rememberSaveableStateHolder().SaveableStateProvider(page?.key.orEmpty()) {
            Column(
                modifier = modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                // Settings is a list of unrelated parts, so spacing alone separates them.
                verticalArrangement = Arrangement.spacedBy(if (page == null || page == SettingsPage.Views) 12.dp else 28.dp),
            ) {
                when (page) {
                    null -> {
                        BackupLinkRow(onClick = { onOpenPage(SettingsPage.Backups) })
                        SettingsLinkRow(
                            icon = Icons.Rounded.Luggage,
                            title = stringResource(R.string.settings_holidays_title),
                            summary = thisYearsDays(DayOff.Away, holidaySchedule, todayKey),
                            onClick = { onOpenPage(SettingsPage.Holidays) },
                            testTag = "settings_open_holidays",
                        )
                        SettingsLinkRow(
                            icon = Icons.Rounded.Sick,
                            title = stringResource(R.string.settings_sick_title),
                            summary = thisYearsDays(DayOff.Sick, holidaySchedule, todayKey),
                            onClick = { onOpenPage(SettingsPage.SickDays) },
                            testTag = "settings_open_sick_days",
                        )
                        SettingsLinkRow(
                            icon = Icons.Rounded.Bedtime,
                            title = stringResource(R.string.settings_sleep_title),
                            summary = sleepSummary(settings),
                            onClick = { onOpenPage(SettingsPage.Sleep) },
                            testTag = "settings_open_sleep",
                        )
                        SettingsLinkRow(
                            icon = Icons.Rounded.Notifications,
                            title = stringResource(R.string.settings_reminders_title),
                            summary = when {
                                !notificationsAllowed -> stringResource(R.string.settings_needs_notifications)
                                !exactAlarmAllowed -> stringResource(R.string.settings_needs_precise_timing)
                                else -> remindersOnSummary(
                                    remindersOn(settings, featureVisibility, substanceSettings.checkIn.remind) + trackerModules.views.filter { it.visible && it.usable }.sumOf { view -> view.reminders.count { it.enabled } },
                                )
                            },
                            summaryIsWarning = !notificationsAllowed || !exactAlarmAllowed,
                            onClick = { onOpenPage(SettingsPage.Reminders) },
                            testTag = "settings_open_reminders",
                        )

                        Spacer(Modifier.height(8.dp))
                        viewList(false)

                        Spacer(Modifier.height(8.dp))
                        LanguageSettings()

                        // Rarely needed, so it is the list's last and quietest entry.
                        MomentumButton(
                            text = stringResource(R.string.about_title),
                            onClick = { onOpenPage(SettingsPage.About) },
                            modifier = Modifier.fillMaxWidth(),
                            variant = MomentumButtonVariant.Quiet,
                            testTag = "settings_open_about",
                        )
                    }

                    // Every kind to make is in sight, not behind "Track something new", and
                    // Statistics is no view a person tracks, so it is left out.
                    SettingsPage.Views -> {
                        NewViewPage(onChoose = onOpenPage, creations = trackerModules.creations)
                        Spacer(Modifier.height(8.dp))
                        viewList(true)
                    }

                    // Losing every record to an uninstall is the worst thing that can happen here, so
                    // the backup row leads the list whatever state it is in, and its page is all backups.
                    SettingsPage.Backups -> BackupSettings()

                    SettingsPage.Holidays -> DaysOffSettings(
                        kind = DayOff.Away,
                        schedule = holidaySchedule,
                        todayKey = todayKey,
                        onChange = onHolidayScheduleChange,
                    )

                    SettingsPage.SickDays -> DaysOffSettings(
                        kind = DayOff.Sick,
                        schedule = holidaySchedule,
                        todayKey = todayKey,
                        onChange = onHolidayScheduleChange,
                    )

                    SettingsPage.Sleep -> SleepSettings(
                        settings = settings,
                        onEditWake = { timePickerTarget = TimePickerTarget.FallbackAlarm },
                        onFixedBedtimeChange = { applySettings(settings.copy(fixedBedtime = it)) },
                        onEditBedtime = { timePickerTarget = TimePickerTarget.Bedtime },
                    )

                    SettingsPage.Reminders -> NotificationsGroup(
                        notificationsAllowed = notificationsAllowed,
                        exactAlarmAllowed = exactAlarmAllowed,
                        onRequestNotifications = ::requestNotificationAccess,
                        onRequestExactAlarm = ::requestExactAlarmAccess,
                    ) {
                        FeatureReminder(
                            label = stringResource(R.string.settings_reminder_mo),
                            checked = settings.moCheckIn.enabled,
                            onCheckedChange = ::setMoCheckInEnabled,
                            // Mo belongs to no feature, so hiding one can never take his check-in away.
                            dependency = reminderDependency(
                                featureVisible = true,
                                exactAlarmAllowed = exactAlarmAllowed,
                                notificationsAllowed = notificationsAllowed,
                            ),
                            featureLabel = stringResource(R.string.settings_reminder_mo_feature),
                            timing = stringResource(
                                R.string.settings_timing_mo,
                                moNudgeTimes().joinToString { time ->
                                    formatTime(ReminderTime(time.hour, time.minute))
                                },
                            ),
                            onRequestAccess = ::requestAccessFor,
                            // The rows above this one ask for what it needs, so it does not ask again.
                            accessAskedAbove = true,
                            test = moCheckInTestReminder(mood),
                            onSendTest = ::sendTest,
                            testTag = "settings_mo_check_in",
                            timingTestTag = "settings_mo_check_in_timing",
                        )
                    }

                    SettingsPage.NewView -> NewViewPage(onChoose = onOpenPage, creations = trackerModules.creations)

                    SettingsPage.About -> AboutPage(
                        onOpenPrivacy = { onOpenPage(SettingsPage.Privacy) },
                        onOpenLegalNotice = { onOpenPage(SettingsPage.LegalNotice) },
                        onOpenComponent = { onOpenPage(SettingsPage.License(it.name)) },
                    )

                    SettingsPage.Privacy -> LegalTextPage(LegalText.PrivacyPolicy)

                    SettingsPage.LegalNotice -> LegalTextPage(LegalText.LegalNotice)

                    is SettingsPage.License -> LicensePage(page.component)

                    is SettingsPage.Tracker, is SettingsPage.NewTracker,
                    is SettingsPage.Feature, is SettingsPage.Checklist, is SettingsPage.Measurement, is SettingsPage.Questions, is SettingsPage.Time,
                    SettingsPage.NewChecklist, SettingsPage.NewMeasurement, SettingsPage.NewQuestions, SettingsPage.NewTime, SettingsPage.Substances,
                    -> Unit
                }

                ReservedMessage(text = errorRes?.let { stringResource(it) }, testTag = "settings_reminder_error")
            }
            }
            }
            }
        }
    }
}

/** A test waiting for notifications, and the habit day ([dayKey]) it was built for. */
private class PendingTest(val send: () -> Boolean, val dayKey: String)

/**
 * A row that opens a page: what it is, and in a line how it stands or what it is for. A row of a
 * list of things a person made may wear the thing's own color on its icon ([iconTint]), keep its
 * line as tall as the tallest it may say ([reserving]), so the row keeps its height whichever it
 * says, and carry one small [action] of its own before the chevron, such as moving it up.
 *
 * [fillsHeight] is for a parent that gives its rows one height, as [EqualHeightColumn] does: the
 * row's content then sits in the middle of that height. Leave it off anywhere else, where a row is
 * as tall as what it holds.
 */
@Composable
internal fun SettingsLinkRow(
    icon: ImageVector,
    title: String,
    summary: String,
    onClick: () -> Unit,
    testTag: String,
    summaryIsWarning: Boolean = false,
    fillsHeight: Boolean = false,
    iconTint: Color? = null,
    reserving: List<String> = emptyList(),
    action: (@Composable () -> Unit)? = null,
) {
    MomentumCard(onClick = onClick, modifier = Modifier.fillMaxWidth(), testTag = testTag) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (fillsHeight) Modifier.weight(1f) else Modifier)
                .padding(start = 16.dp, end = if (action != null) 8.dp else 16.dp, top = 14.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(24.dp), tint = iconTint ?: LocalContentColor.current)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(text = title, style = MaterialTheme.typography.titleMedium)
                val color = if (summaryIsWarning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                if (reserving.isEmpty()) {
                    Text(text = summary, style = MaterialTheme.typography.bodySmall, color = color)
                } else {
                    ReservedMessage(text = summary, color = color, reserving = reserving, announced = false)
                }
            }
            if (action != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    action()
                    Icon(imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null)
                }
            } else {
                Icon(imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null)
            }
        }
    }
}

/**
 * One view of the list: its name in its color, and whether Home shows it. Pressing the row opens
 * the view's page where it has one; a view with nothing more to set is a plain row, because only
 * what can be pressed is raised. A [note] under the name says why the switch is not [enabled],
 * as for a checklist with nothing on it yet.
 */
@Composable
private fun ViewRow(
    label: String,
    tagName: String,
    identity: ViewIdentity,
    visible: Boolean,
    onVisibleChange: (Boolean) -> Unit,
    onOpen: (() -> Unit)?,
    enabled: Boolean = true,
    note: String? = null,
) {
    val showOnHome = stringResource(R.string.settings_show_feature_on_home, label)
    ViewTheme(identity.colors) {
        val row = @Composable {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Icon(imageVector = identity.icon, contentDescription = null, modifier = Modifier.size(24.dp), tint = identity.colors.accent)
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(text = label, style = MaterialTheme.typography.titleMedium, color = identity.colors.label)
                    if (note != null) NoteText(text = note, testTag = "settings_feature_${tagName}_note")
                }
                Switch(
                    checked = visible,
                    onCheckedChange = onVisibleChange,
                    enabled = enabled,
                    modifier = Modifier
                        .testTag("settings_feature_$tagName")
                        .semantics { contentDescription = showOnHome },
                )
            }
        }
        if (onOpen != null) {
            MomentumCard(onClick = onOpen, modifier = Modifier.fillMaxWidth(), testTag = "settings_group_$tagName") { row() }
        } else {
            Box(modifier = Modifier.fillMaxWidth().testTag("settings_group_$tagName")) { row() }
        }
    }
}

/** Where backups stand, in a word; it is a warning while they protect nothing. */
@Composable
private fun BackupLinkRow(onClick: () -> Unit) {
    val presentation = if (LocalInspectionMode.current) {
        BackupPresentation.NeedsSetup
    } else {
        val context = LocalContext.current
        val manager = remember(context) { DurableBackupManager.get(context) }
        val status by manager.status.collectAsState()
        rememberBackupPresentation(status)
    }
    SettingsLinkRow(
        icon = Icons.Rounded.CloudUpload,
        title = stringResource(R.string.settings_backups_title),
        summary = stringResource(
            when (presentation) {
                BackupPresentation.NeedsSetup -> R.string.settings_backups_summary_setup
                BackupPresentation.BackingUp -> R.string.settings_backup_backing_up
                BackupPresentation.Delayed -> R.string.settings_backups_summary_delayed
                BackupPresentation.Failed -> R.string.settings_backups_summary_failed
                BackupPresentation.Verified -> R.string.settings_backups_summary_verified
            },
        ),
        summaryIsWarning = presentation == BackupPresentation.NeedsSetup || presentation == BackupPresentation.Failed,
        onClick = onClick,
        testTag = "settings_open_backups",
    )
}

/** This year's holiday or sick days, as their page counts them. */
@Composable
private fun thisYearsDays(kind: DayOff, schedule: HolidaySchedule, todayKey: String): String {
    val year = java.time.LocalDate.parse(todayKey).year
    return daysInYear(schedule.daysIn(kind, year), year)
}

/** How many reminders are on, as the Settings list shows it. */
@Composable
private fun remindersOnSummary(count: Int): String =
    if (count == 0) {
        stringResource(R.string.settings_reminders_summary_none)
    } else {
        pluralStringResource(R.plurals.settings_reminders_summary_on, count, count)
    }

/** The two times of the Sleep page, which are stored as they are picked. */
private enum class TimePickerTarget {
    FallbackAlarm,
    Bedtime,
}

/**
 * Everything the app as a whole needs before any reminder can arrive. It sits in one place so no
 * feature group has to explain a permission that is not its own.
 *
 * A permission that is already granted leaves the screen entirely: from then on it belongs to the
 * phone's own settings, and a row saying "allowed" is only noise between the reader and the things
 * that still need them. What stays are the reminders that belong to no feature.
 */
@Composable
private fun NotificationsGroup(
    notificationsAllowed: Boolean,
    exactAlarmAllowed: Boolean,
    onRequestNotifications: () -> Unit,
    onRequestExactAlarm: () -> Unit,
    appWideReminders: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("settings_group_notifications"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (!notificationsAllowed) {
            AppWideRow(
                label = stringResource(R.string.settings_allow_notifications),
                note = stringResource(R.string.settings_allow_notifications_note),
                noteTestTag = "settings_notifications_status",
                actionLabel = stringResource(R.string.settings_allow),
                onAction = onRequestNotifications,
                actionTestTag = "settings_allow_notifications",
            )
        }

        if (!exactAlarmAllowed) {
            AppWideRow(
                label = stringResource(R.string.settings_precise_timing),
                note = stringResource(R.string.settings_precise_timing_note),
                noteTestTag = "settings_precise_timing_status",
                actionLabel = stringResource(R.string.settings_allow),
                onAction = onRequestExactAlarm,
                actionTestTag = "settings_allow_exact_alarm",
            )
        }

        appWideReminders()
    }
}

@Composable
private fun AppWideRow(
    label: String,
    note: String,
    noteTestTag: String,
    actionLabel: String,
    onAction: () -> Unit,
    actionTestTag: String,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = label,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge,
            )
            MomentumButton(
                text = actionLabel,
                onClick = onAction,
                variant = MomentumButtonVariant.Secondary,
                testTag = actionTestTag,
            )
        }
        // A row is here only while its permission is missing, so the sentence says so in error.
        NoteText(text = note, testTag = noteTestTag, color = MaterialTheme.colorScheme.error)
    }
}

/**
 * One reminder's switch, with when it arrives and what still stands in its way. [onSendTest] sends
 * [test], the reminder as it can be sent now, or nothing while none would come, as on a day
 * a timer's weekly goal is already met; the button keeps its place, so switching the reminder on or
 * off moves nothing. A row without a sender shows no button: a checkpoint's test sits under its
 * preview instead.
 */
@Composable
private fun FeatureReminder(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    dependency: ReminderDependency,
    featureLabel: String,
    timing: String,
    onRequestAccess: (ReminderDependency) -> Unit,
    testTag: String,
    timingTestTag: String,
    test: TestReminder? = null,
    onSendTest: ((TestReminder) -> Unit)? = null,
    timeLabel: String? = null,
    timeTestTag: String? = null,
    onEditTime: (() -> Unit)? = null,
    /** The page already asks for notifications and precise timing above this row, so the row says only that it is off. */
    accessAskedAbove: Boolean = false,
) {
    val usable = dependency != ReminderDependency.Hidden
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = label,
                modifier = Modifier.weight(1f),
                // Beside the time and the switch a long German word such as "Trainingserinnerung"
                // breaks onto a second line, which it does where the language breaks it.
                style = MaterialTheme.typography.bodyLarge.copy(hyphens = Hyphens.Auto),
            )
            if (timeLabel != null && onEditTime != null) {
                TimeButton(
                    time = timeLabel,
                    description = stringResource(R.string.settings_time_at, label, timeLabel),
                    enabled = usable,
                    testTag = timeTestTag,
                    onClick = onEditTime,
                )
            }
            Switch(
                checked = checked,
                // Switched on, it asks at once for notifications, the question the phone asks in
                // place; precise timing opens the phone's settings, which is left to Save and the
                // Reminders step, as is whatever was refused here.
                onCheckedChange = { on ->
                    onCheckedChange(on)
                    if (on && !accessAskedAbove && dependency != ReminderDependency.Ready) onRequestAccess(ReminderDependency.Notifications)
                },
                enabled = usable,
                modifier = Modifier
                    .testTag(testTag)
                    .semantics { contentDescription = label },
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // The schedule is stated whatever stands in its way, so a blocked reminder still says
            // when it would arrive once the way is clear.
            NoteText(text = timing, testTag = timingTestTag, modifier = Modifier.weight(1f))
            if (onSendTest != null) {
                // A hidden feature's reminder leads to a screen that cannot open, so it is not sent either.
                MomentumButton(
                    text = stringResource(R.string.settings_send_test),
                    onClick = { test?.let(onSendTest) },
                    variant = MomentumButtonVariant.Quiet,
                    enabled = usable && test != null,
                    testTag = "${testTag}_test",
                )
            }
        }
        if (dependency != ReminderDependency.Ready && !(accessAskedAbove && dependency != ReminderDependency.Hidden)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                NoteText(
                    text = when (dependency) {
                        ReminderDependency.Hidden -> stringResource(R.string.settings_turn_feature_on, featureLabel)
                        ReminderDependency.PreciseTiming -> stringResource(R.string.settings_needs_precise_timing)
                        ReminderDependency.Notifications -> stringResource(R.string.settings_needs_notifications)
                        ReminderDependency.Ready -> ""
                    },
                    testTag = "${testTag}_dependency",
                    modifier = Modifier.weight(1f),
                    color = if (dependency == ReminderDependency.Hidden) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                )
                if (dependency != ReminderDependency.Hidden) {
                    MomentumButton(
                        text = stringResource(R.string.settings_allow),
                        onClick = { onRequestAccess(dependency) },
                        variant = MomentumButtonVariant.Quiet,
                        testTag = "${testTag}_allow",
                    )
                }
            }
        }
    }
}

@Composable
internal fun GroupTitle(text: String) {
    Text(text = text, style = MaterialTheme.typography.titleMedium)
}

@Composable
internal fun NoteText(
    text: String,
    testTag: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Text(
        text = text,
        modifier = modifier.testTag(testTag),
        style = MaterialTheme.typography.bodySmall,
        color = color,
    )
}

@Composable
internal fun TimeButton(
    time: String,
    description: String,
    enabled: Boolean,
    testTag: String?,
    onClick: () -> Unit,
) {
    MomentumButton(
        onClick = onClick,
        variant = MomentumButtonVariant.Secondary,
        enabled = enabled,
        testTag = testTag,
        faceModifier = Modifier.semantics { contentDescription = description },
    ) {
        MomentumButtonLabel(time)
    }
}

/** Whether [feature]'s reminder is on: Activity's workout reminder or Substances' check-in. */
private fun featureHasEnabledReminder(
    feature: DashboardFeature,
    settings: ReminderSettings,
    checkIn: SubstanceCheckIn,
): Boolean = when (feature) {
    DashboardFeature.Activity -> settings.workout.enabled
    DashboardFeature.Substances -> checkIn.remind
    // Tracker modules read reminder switches from their configured checkpoints.
    else -> false
}

/** Whether storing [updated] over [current] schedules a reminder, which needs what the phone has to allow. */
internal fun settingsChangeNeedsPlatformAccess(current: ReminderSettings, updated: ReminderSettings): Boolean =
    reminderUpdateNeedsPlatformAccess(current, updated)

/** Material's time picker in Settings' neutral colors; a checklist's page picks a checkpoint's time with it too. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsTimePickerDialog(
    initialTime: ReminderTime,
    onDismiss: () -> Unit,
    onConfirm: (ReminderTime) -> Unit,
) {
    val state = rememberTimePickerState(
        initialHour = initialTime.hour,
        initialMinute = initialTime.minute,
        is24Hour = true,
    )
    TimePickerDialog(
        onDismissRequest = onDismiss,
        title = {
            TimePickerDialogDefaults.Title(displayMode = TimePickerDisplayMode.Picker)
        },
        confirmButton = {
            MomentumButton(
                text = stringResource(R.string.settings_ok),
                onClick = { onConfirm(ReminderTime(state.hour, state.minute)) },
                variant = MomentumButtonVariant.Quiet,
            )
        },
        dismissButton = {
            MomentumButton(
                text = stringResource(R.string.settings_cancel),
                onClick = onDismiss,
                variant = MomentumButtonVariant.Quiet,
            )
        },
        modeToggleButton = {},
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        TimePicker(
            state = state,
            colors = timePickerColors(),
        )
    }
}

/** The picker stays as hueless as the rest of Settings: the selection is the neutral control grey. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun timePickerColors(): TimePickerColors {
    val scheme = MaterialTheme.colorScheme
    return TimePickerDefaults.colors(
        clockDialColor = scheme.surfaceContainerHighest,
        clockDialSelectedContentColor = scheme.surfaceContainerLowest,
        clockDialUnselectedContentColor = scheme.onSurface,
        selectorColor = scheme.onSurfaceVariant,
        containerColor = scheme.surfaceContainerHigh,
        timeSelectorSelectedContainerColor = scheme.onSurfaceVariant,
        timeSelectorUnselectedContainerColor = scheme.surfaceContainerHighest,
        timeSelectorSelectedContentColor = scheme.surfaceContainerLowest,
        timeSelectorUnselectedContentColor = scheme.onSurfaceVariant,
    )
}

internal fun formatTime(time: ReminderTime): String =
    String.format(Locale.ROOT, "%02d:%02d", time.hour, time.minute)

internal fun everyDayTimingText(time: ReminderTime): UiText =
    uiText(R.string.settings_timing_every_day, formatTime(time))

/** When the workout reminder comes: every day at its time, or its days by their short names, in the week's order. */
internal fun workoutTimingText(days: Set<DayOfWeek>, time: ReminderTime, locale: Locale): UiText =
    if (days.size == DayOfWeek.entries.size) {
        everyDayTimingText(time)
    } else {
        uiText(R.string.settings_timing_on_days, days.sorted().joinToString { it.getDisplayName(TextStyle.SHORT, locale) }, formatTime(time))
    }

/** When a checkpoint reminds, in the words each form of time already has elsewhere in Settings. */
internal fun checkpointTimingText(at: CheckpointTime, settings: ReminderSettings): UiText = when (at) {
    is CheckpointTime.Fixed -> everyDayTimingText(ReminderTime(at.time.hour, at.time.minute))
    is CheckpointTime.AfterWake -> reminderTimingText(at.minutes.toLong(), settings.fallbackAlarmTime)
    is CheckpointTime.BeforeSleep -> {
        val fallback = settings.usualNight.beforeSleep(java.time.Duration.ofMinutes(at.minutes.toLong()))
        val latest = Bedtime.LatestEveningReminder
        uiText(
            R.string.settings_timing_before_sleep,
            at.minutes,
            formatTime(ReminderTime(latest.hour, latest.minute)),
            formatTime(ReminderTime(fallback.hour, fallback.minute)),
        )
    }
}

internal fun reminderTimingText(
    minutesAfterAlarm: Long,
    fallbackAlarmTime: ReminderTime,
): UiText {
    val fallbackReminderTime = java.time.LocalTime
        .of(fallbackAlarmTime.hour, fallbackAlarmTime.minute)
        .plusMinutes(minutesAfterAlarm)
    val formattedFallback = formatTime(
        ReminderTime(fallbackReminderTime.hour, fallbackReminderTime.minute),
    )
    return uiText(R.string.settings_timing_after_alarm, minutesAfterAlarm, formattedFallback)
}

private val ReminderSettingsSaver = listSaver<ReminderSettings?, Any>(
    save = { settings ->
        settings?.let(::reminderSettingsSaveValues).orEmpty()
    },
    restore = ::reminderSettingsFromSaveValues,
)

/**
 * A visibility change waiting for the phone's answer, kept across the permission screen and the
 * process dying behind it, in the shape the settings are stored in, dated entries and all.
 */
internal val FeatureVisibilitySettingsSaver = listSaver<FeatureVisibilitySettings?, String>(
    save = { visibility -> visibility?.let { listOf(FeatureVisibilitySettingsCodec.encode(it)) }.orEmpty() },
    restore = { values -> values.firstOrNull()?.let { runCatching { FeatureVisibilitySettingsCodec.decode(it) }.getOrNull() } },
)

internal fun reminderSettingsSaveValues(settings: ReminderSettings): List<Any> = listOf(
    settings.workout.enabled,
    settings.workout.fixedTime.hour,
    settings.workout.fixedTime.minute,
    false, // Kept for the positional saver written before checkpoint settings moved to trackers.
    settings.fallbackAlarmTime.hour,
    settings.fallbackAlarmTime.minute,
    false,
    false,
    settings.moCheckIn.enabled,
    settings.moCheckIn.fixedTime.hour,
    settings.moCheckIn.fixedTime.minute,
    settings.fixedBedtime != null,
    settings.fixedBedtime?.hour ?: 0,
    settings.fixedBedtime?.minute ?: 0,
)

/** The settings [reminderSettingsSaveValues] kept, or null where none were kept. */
internal fun reminderSettingsFromSaveValues(values: List<Any>): ReminderSettings? =
    if (values.isEmpty()) {
        null
    } else {
        ReminderSettings(
            workout = WorkoutReminderSettings(enabled = values[0] as Boolean, fixedTime = ReminderTime(values[1] as Int, values[2] as Int)),
            fallbackAlarmTime = ReminderTime(values[4] as Int, values[5] as Int),
            moCheckIn = MoCheckInSettings(enabled = values[8] as Boolean, fixedTime = ReminderTime(values[9] as Int, values[10] as Int)),
            fixedBedtime = if (values[11] as Boolean) ReminderTime(values[12] as Int, values[13] as Int) else null,
        )
    }

private class SettingsListEntry(val order: Int, val content: @Composable () -> Unit)

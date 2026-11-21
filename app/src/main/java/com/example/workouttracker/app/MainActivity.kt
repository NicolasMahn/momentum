package com.example.workouttracker.app

import com.example.workouttracker.reminder.ReminderCompletionNotification
import com.example.workouttracker.reminder.quietedByChoice
import com.example.workouttracker.domain.SubstancesTrackerRef
import com.example.workouttracker.domain.ActivityTrackerRef
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.core.os.ConfigurationCompat
import androidx.core.os.LocaleListCompat
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.referentialEqualityPolicy
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.example.workouttracker.R
import com.example.workouttracker.core.ui.MomentumButton
import com.example.workouttracker.core.ui.theme.WorkoutTrackerTheme
import com.example.workouttracker.core.ui.theme.enableDarkEdgeToEdge
import com.example.workouttracker.domain.AppState
import com.example.workouttracker.domain.LoggedViewKind
import com.example.workouttracker.domain.LoggedViewRef
import com.example.workouttracker.persistence.FirstStartLocal
import com.example.workouttracker.persistence.PreferencesHolidayRepository
import com.example.workouttracker.persistence.PreferencesHabitRuleStartRepository
import com.example.workouttracker.core.time.currentHabitDate
import com.example.workouttracker.persistence.PreferencesPassChoiceRepository
import com.example.workouttracker.persistence.PassChoices
import com.example.workouttracker.persistence.PreferencesFeatureVisibilityRepository
import com.example.workouttracker.persistence.PreferencesReminderSettingsRepository
import com.example.workouttracker.persistence.PreferencesTrackerRepository
import com.example.workouttracker.persistence.TrackerUpdateResult
import com.example.workouttracker.domain.checklist.deleteChecklist
import com.example.workouttracker.domain.settings.FeatureVisibilitySettings
import com.example.workouttracker.domain.visibilityInUse
import com.example.workouttracker.domain.withoutLeftoverEntries
import com.example.workouttracker.reminder.canScheduleExactReminders
import com.example.workouttracker.reminder.checklistReminderCoordinator
import com.example.workouttracker.reminder.AndroidMoCheckInScheduler
import com.example.workouttracker.reminder.AndroidWorkoutReminderScheduler
import com.example.workouttracker.reminder.activityDays
import com.example.workouttracker.reminder.FeatureNotificationCanceller
import com.example.workouttracker.reminder.FeatureVisibilitySource
import com.example.workouttracker.reminder.FeatureVisibilityUpdateCoordinator
import com.example.workouttracker.reminder.ReminderNotificationChannel
import com.example.workouttracker.reminder.ReminderRuntimeReconciler
import com.example.workouttracker.reminder.ReminderSettingsUpdateCoordinator
import com.example.workouttracker.reminder.moCheckInCoordinator
import com.example.workouttracker.reminder.gatedBy
import com.example.workouttracker.reminder.retireOldFeatureReminders
import com.example.workouttracker.reminder.substanceCheckInCoordinator
import com.example.workouttracker.widget.MoWidgetProvider

/**
 * An AppCompat activity because AppCompat is what applies the chosen app language below API 33.
 * The manifest lets it handle a language change in place, so the screen recomposes in the new
 * language without losing what is typed.
 */
class MainActivity : AppCompatActivity() {
    private var requestedNavigation by mutableStateOf<AppNavigationRequest?>(null)
    private lateinit var trackerRepository: PreferencesTrackerRepository
    /** Each read's own object, even when equal to the last: a save names the very read it showed. */
    private var trackerState by mutableStateOf(AppState(), referentialEqualityPolicy())
    private val moCheckIn by lazy { moCheckInCoordinator() }
    private var shownLocales: LocaleListCompat? = null
    private val refreshTrackerState: () -> Unit = {
        if (::trackerRepository.isInitialized) {
            // Records that became unreadable while open must not turn into an empty history.
            val loaded = trackerRepository.loadIfReadable()
            if (loaded == null) {
                recreate()
            } else {
                trackerState = loaded
                moCheckIn.retireIfSettled(loaded)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        shownLocales = ConfigurationCompat.getLocales(resources.configuration)
        enableDarkEdgeToEdge()
        try {
            initializeApp()
        } catch (_: Exception) {
            // A pending restore must recover before any screen can load defaults or write new data.
            setContent {
                WorkoutTrackerTheme {
                    androidx.compose.foundation.layout.Column {
                        androidx.compose.material3.Text(stringResource(R.string.app_open_failed_message))
                        MomentumButton(
                            text = stringResource(R.string.app_open_failed_retry),
                            onClick = { recreate() },
                        )
                    }
                }
            }
        }
    }

    private fun initializeApp() {
        requestedNavigation = intent.requestedAppNavigation()

        val repository = PreferencesTrackerRepository(applicationContext)
        val initialState = repository.loadIfReadable()
        if (initialState == null) {
            setContent { WorkoutTrackerTheme { UnreadableRecordsScreen(onRetry = ::recreate) } }
            return
        }
        trackerRepository = repository
        trackerState = initialState
        val passChoiceRepository = PreferencesPassChoiceRepository(applicationContext)
        val initialPassChoices = passChoiceRepository.load()
        val rewardStart = PreferencesHabitRuleStartRepository(applicationContext).loadOrStart(currentHabitDate())
        val holidayRepository = PreferencesHolidayRepository(applicationContext)
        val initialHolidaySchedule = holidayRepository.load()
        val reminderRepository = PreferencesReminderSettingsRepository(applicationContext)
        val workoutReminderScheduler = AndroidWorkoutReminderScheduler(applicationContext)
        val moCheckInScheduler = AndroidMoCheckInScheduler(applicationContext)
        val featureVisibilityRepository =
            PreferencesFeatureVisibilityRepository(applicationContext)
        // A feature whose checklist has nothing on it is hidden to every alarm, as it is on Home.
        fun visibilityInUse(visibility: FeatureVisibilitySettings = featureVisibilityRepository.load()) =
            repository.load().visibilityInUse(visibility, currentHabitDate())
        val featureVisibilitySource = FeatureVisibilitySource { visibilityInUse() }
        val reminderSettingsUpdater = ReminderSettingsUpdateCoordinator(
            repository = reminderRepository,
            workoutScheduler = workoutReminderScheduler,
            moCheckInScheduler = moCheckInScheduler,
            featureVisibility = featureVisibilitySource,
            activityDays = { repository.load().activityDays() },
        )
        val initialReminderSettings = reminderRepository.load()
        val initialFeatureVisibility = featureVisibilityRepository.load()
        val notificationCanceller = FeatureNotificationCanceller(applicationContext)
        val featureVisibilityUpdater = FeatureVisibilityUpdateCoordinator(
            visibilityRepository = featureVisibilityRepository,
            reminderRepository = reminderRepository,
            workoutScheduler = workoutReminderScheduler,
            notificationCanceller = notificationCanceller,
            activityDays = { repository.load().activityDays() },
        )
        ReminderNotificationChannel.create(applicationContext)
        val runtimeReminders = ReminderRuntimeReconciler(
            workoutScheduler = workoutReminderScheduler,
            notificationCanceller = notificationCanceller,
            moCheckInScheduler = moCheckInScheduler,
        )
        runtimeReminders.reconcile(initialReminderSettings, initialState.visibilityInUse(initialFeatureVisibility, currentHabitDate()), initialState.activityDays())
        // What the build before set for the five's own reminders goes before their slots are set.
        retireOldFeatureReminders()
        val checklistReminders = checklistReminderCoordinator()
        checklistReminders.reconcile()
        val substanceCheckIn = substanceCheckInCoordinator()
        substanceCheckIn.reconcile()
        val firstStart = FirstStartLocal(applicationContext)
        val firstStartWaits = firstStart.waitsFor(initialState, initialFeatureVisibility)

        setContent {
            WorkoutTrackerTheme {
                var firstStartOpen by remember { mutableStateOf(firstStartWaits) }
                var reminderSettings by remember { mutableStateOf(initialReminderSettings) }
                var holidaySchedule by remember { mutableStateOf(initialHolidaySchedule) }
                var passChoices by remember { mutableStateOf(initialPassChoices) }
                var featureVisibility by remember {
                    mutableStateOf(initialFeatureVisibility)
                }

                /**
                 * A configuration stored with the records changed, a checklist's, a logged view's
                 * or Substances': its checkpoints may remind at other times or not at all, and the
                 * check-in's switch and time are Substances'. Whether every alarm could be set is
                 * the answer.
                 */
                fun configurationsChanged(state: AppState): Boolean {
                    trackerState = state
                    reminderSettings = reminderRepository.load()
                    moCheckIn.retireIfSettled(state)
                    // All run, whatever the earlier ones say.
                    val features = runtimeReminders.reconcile(reminderSettings, state.visibilityInUse(featureVisibility, currentHabitDate()), state.activityDays())
                    val checklists = checklistReminders.reconcile()
                    val substances = substanceCheckIn.reconcile()
                    // A notification on show names only what is still owed: an item switched to
                    // only misses is settled, so it leaves the notification or takes it down.
                    checklistReminders.refreshPosted()
                    return features && checklists && substances
                }

                val checklistCommands = remember {
                    ChecklistCommands(
                        repository = trackerRepository,
                        reconcile = ::configurationsChanged,
                        exactAlarmsAllowed = { canScheduleExactReminders(applicationContext) },
                        refresh = refreshTrackerState,
                        hidden = { featureVisibilityRepository.load().namedChecklistIds },
                    )
                }
                /** A day's change was decided against the store: the screens are shown the records as they are now. */
                fun dayStored(updated: AppState) {
                    trackerState = updated
                    moCheckIn.retireIfSettled(updated)
                }

                /** Drops the hidden entry a deleted view left, in a write of its own; where it fails, the next save of the visibility settings drops it. */
                fun forgetHidden(view: LoggedViewRef) {
                    val shown = featureVisibility.forgetting(view)
                    if (shown != featureVisibility && featureVisibilityUpdater.apply(featureVisibility, shown)) {
                        featureVisibility = shown
                    }
                }

                val measurementCommands = remember {
                    MeasurementCommands(
                        repository = trackerRepository,
                        reconcile = ::configurationsChanged,
                        exactAlarmsAllowed = { canScheduleExactReminders(applicationContext) },
                        refresh = refreshTrackerState,
                        stored = ::dayStored,
                        hidden = { featureVisibilityRepository.load().namedIds(LoggedViewKind.Measurement) },
                        forgetHidden = { measurementId -> forgetHidden(LoggedViewRef(LoggedViewKind.Measurement, measurementId)) },
                    )
                }
                val questionsCommands = remember {
                    QuestionsCommands(
                        repository = trackerRepository,
                        reconcile = ::configurationsChanged,
                        exactAlarmsAllowed = { canScheduleExactReminders(applicationContext) },
                        refresh = refreshTrackerState,
                        stored = ::dayStored,
                        hidden = { featureVisibilityRepository.load().namedIds(LoggedViewKind.Questions) },
                        forgetHidden = { viewId -> forgetHidden(LoggedViewRef(LoggedViewKind.Questions, viewId)) },
                    )
                }
                val timeCommands = remember {
                    TimeCommands(
                        repository = trackerRepository,
                        reconcile = ::configurationsChanged,
                        exactAlarmsAllowed = { canScheduleExactReminders(applicationContext) },
                        refresh = refreshTrackerState,
                        stored = ::dayStored,
                        hidden = { featureVisibilityRepository.load().namedIds(LoggedViewKind.Time) },
                        forgetHidden = { viewId -> forgetHidden(LoggedViewRef(LoggedViewKind.Time, viewId)) },
                    )
                }
                val substanceCommands = remember {
                    SubstanceCommands(
                        repository = trackerRepository,
                        reconcile = ::configurationsChanged,
                        exactAlarmsAllowed = { canScheduleExactReminders(applicationContext) },
                        refresh = refreshTrackerState,
                    )
                }
                val activityCommands = remember {
                    ActivityCommands(
                        repository = trackerRepository,
                        reconcile = ::configurationsChanged,
                        exactAlarmsAllowed = { canScheduleExactReminders(applicationContext) },
                        refresh = refreshTrackerState,
                        reminderOn = { reminderRepository.load().gatedBy(featureVisibility).workout.enabled },
                    )
                }

                // The state this composition shows, which a screen's change was made from, even
                // if a refresh replaced the activity's state before the change arrives.
                val shown = trackerState
                MomentumApp(
                    state = shown,
                    rewardStart = rewardStart,
                    onStateChange = { updated ->
                        trackerRepository.save(updated, base = shown).also {
                            // What is stored can hold more than the screen had: a change that came
                            // while it was open. A refused save means one stands in its way.
                            refreshTrackerState()
                        }
                    },
                    featureVisibility = featureVisibility,
                    holidaySchedule = holidaySchedule,
                    passChoices = passChoices,
                    onPassChoicesChange = { updated: PassChoices ->
                        passChoiceRepository.save(updated).also { saved ->
                            if (saved) {
                                passChoices = updated
                                moCheckIn.retireIfSettled(trackerState)
                                // A choice answers a reminder as a record does: one already on
                                // show for what was just settled would ask for nothing.
                                checklistReminders.refreshPosted()
                                val quiet = quietedByChoice(applicationContext)
                                if (quiet.isQuiet(ActivityTrackerRef, null)) notificationCanceller.cancel(ReminderCompletionNotification.Activity)
                                if (quiet.isQuiet(SubstancesTrackerRef, null)) notificationCanceller.cancel(ReminderCompletionNotification.Substances)
                            }
                        }
                    },
                    onHolidayScheduleChange = { updated ->
                        holidayRepository.save(updated).also { saved ->
                            if (saved) {
                                holidaySchedule = updated
                                moCheckIn.retireIfSettled(trackerState)
                            }
                        }
                    },
                    onFeatureVisibilityChange = { changed ->
                        // The entry of a logged view deleted since, left behind by a write that failed, goes with this save.
                        val updated = trackerState.withoutLeftoverEntries(changed)
                        val saved = featureVisibilityUpdater.apply(
                            current = featureVisibility,
                            updated = updated,
                        )
                        if (saved) {
                            featureVisibility = updated
                            // A hidden checklist or measurement stops reminding; one shown again starts. A
                            // feature's checklist with nothing on it stays quiet whatever its switch says.
                            runtimeReminders.reconcile(reminderSettings, trackerState.visibilityInUse(updated, currentHabitDate()), trackerState.activityDays())
                            checklistReminders.reconcile()
                            substanceCheckIn.reconcile()
                            moCheckIn.retireIfSettled(trackerState)
                        }
                        saved
                    },
                    reminderSettings = reminderSettings,
                    onReminderSettingsChange = { updated ->
                        val current = reminderSettings
                        var saved = reminderSettingsUpdater.apply(current = current, updated = updated)
                        // Refresh the configuration snapshot after the reminder write so
                        // a subsequent record save cannot reuse stale settings.
                        if (saved) refreshTrackerState()
                        // A checkpoint after waking or before sleep follows the wake time and bedtime too, as
                        // the check-in does. An alarm that cannot follow takes the change back, as the other
                        // reminders' alarms do; missing access is shown on the reminder's own row instead.
                        if (saved && !listOf(checklistReminders.reconcile(), substanceCheckIn.reconcile()).all { it } && canScheduleExactReminders(applicationContext)) {
                            val restored = reminderSettingsUpdater.apply(current = updated, updated = current)
                            refreshTrackerState()
                            checklistReminders.reconcile()
                            substanceCheckIn.reconcile()
                            // A change that cannot be taken back stays saved, and the screen shows it.
                            if (!restored) reminderSettings = reminderRepository.load()
                            saved = false
                        }
                        if (saved) {
                            reminderSettings = updated
                            moCheckIn.retireIfSettled(trackerState)
                        }
                        saved
                    },
                    onChecklistSave = checklistCommands::save,
                    onChecklistCreate = checklistCommands::create,
                    onChecklistDelete = { checklistId ->
                        val result = trackerRepository.update { state -> state.deleteChecklist(checklistId) }
                        val deleted = (result as? TrackerUpdateResult.Updated)?.state
                        if (deleted != null) {
                            // Reconciling cancels its alarms and takes its notifications down. Its
                            // hidden entry would otherwise outlive it; losing that is no loss.
                            configurationsChanged(deleted)
                            val shown = featureVisibility.forgettingChecklist(checklistId)
                            if (shown != featureVisibility && featureVisibilityUpdater.apply(featureVisibility, shown)) {
                                featureVisibility = shown
                            }
                        }
                        deleted != null
                    },
                    onChecklistSaved = { checklistId, before, after -> checklistReminders.onRecordsSaved(before, after, checklistId) },
                    onSubstancesSave = substanceCommands::save,
                    onMeasurementSave = measurementCommands::save,
                    onMeasurementCreate = measurementCommands::create,
                    onMeasurementDelete = measurementCommands::delete,
                    onMeasurementDayChange = measurementCommands::changeDay,
                    onDayLogged = { view, dateKey, before, after -> checklistReminders.onDayLogged(before, after, view, dateKey) },
                    onQuestionsSave = questionsCommands::save,
                    onQuestionsCreate = questionsCommands::create,
                    onQuestionsDelete = questionsCommands::delete,
                    onQuestionsDayChange = questionsCommands::changeDay,
                    onTimeSave = timeCommands::save,
                    onActivitySave = activityCommands::save,
                    onTimeCreate = timeCommands::create,
                    onTimeDelete = timeCommands::delete,
                    onTimeDayChange = timeCommands::changeDay,
                    onTimeSessionSave = timeCommands::saveSession,
                    requestedNavigation = requestedNavigation,
                    onDestinationConsumed = ::consumeDestinationRequest,
                    onReminderCompletion = notificationCanceller::cancel,
                    firstStartOpen = firstStartOpen,
                    onFirstStartAnswered = {
                        firstStart.answer()
                        firstStartOpen = false
                    },
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        TrackerStateRefreshSignal.addListener(refreshTrackerState)
    }

    override fun onResume() {
        super.onResume()
        refreshTrackerState()
    }

    override fun onStop() {
        TrackerStateRefreshSignal.removeListener(refreshTrackerState)
        // The home screen is what shows next, so Mo there must be the one just left. This also
        // re-arms his quarter-hour tick, which a force-stop clears.
        MoWidgetProvider.refresh(this)
        super.onStop()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val locales = ConfigurationCompat.getLocales(newConfig)
        if (locales == shownLocales) return
        shownLocales = locales
        // The channel names in the phone's notification settings and Mo's widget speak the app
        // language too, and while the app is open nothing else would rewrite them.
        ReminderNotificationChannel.create(applicationContext)
        MoWidgetProvider.refresh(this)
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        requestedNavigation = intent.requestedAppNavigation()
    }

    private fun consumeDestinationRequest() {
        requestedNavigation = null
        setIntent(
            android.content.Intent(intent).apply {
                action = null
                removeExtra(ExtraChecklistId)
                removeExtra(ExtraChecklistDateKey)
                removeExtra(ExtraSubstanceDateKey)
                removeExtra(ExtraMeasurementId)
                removeExtra(ExtraMeasurementDateKey)
                removeExtra(ExtraQuestionsId)
                removeExtra(ExtraQuestionsDateKey)
                removeExtra(ExtraTimeId)
                removeExtra(ExtraTimeDateKey)
            },
        )
    }
}

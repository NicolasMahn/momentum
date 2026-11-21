package com.example.workouttracker.app

import com.example.workouttracker.core.locale.currentLocale
import com.example.workouttracker.core.format.formatDate
import com.example.workouttracker.core.format.DateStyle
import com.example.workouttracker.domain.mo.MoEvent
import com.example.workouttracker.domain.mo.MoStreakLoss
import com.example.workouttracker.feature.mo.renderStreakShareImage
import com.example.workouttracker.feature.mo.MoLook
import com.example.workouttracker.feature.mo.MoBackfill
import com.example.workouttracker.feature.mo.momentKey
import com.example.workouttracker.feature.mo.MoMomentsSeen
import com.example.workouttracker.feature.mo.MoEventLedger
import com.example.workouttracker.core.locale.withAppLanguage
import com.example.workouttracker.feature.statistics.statisticsExport
import com.example.workouttracker.feature.statistics.StatisticsExportInput
import com.example.workouttracker.domain.mo.trackerDueMinutes
import com.example.workouttracker.domain.mo.habitDayMinuteOf
import com.example.workouttracker.domain.substance.substanceAskedDay
import com.example.workouttracker.domain.substance.substanceRecordJudgedOn
import com.example.workouttracker.domain.TrackerStatisticsContribution

import com.example.workouttracker.domain.TrackerStatisticsIndex

import com.example.workouttracker.core.text.UiText

import com.example.workouttracker.domain.measurement.applyingMeasurementEdits
import com.example.workouttracker.domain.measurement.MeasurementEdit
import com.example.workouttracker.domain.measurement.MeasurementConfiguration
import com.example.workouttracker.domain.measurement.addMeasurement
import com.example.workouttracker.domain.measurement.deleteMeasurement
import com.example.workouttracker.domain.measurement.recordedDaysOf
import com.example.workouttracker.domain.measurement.starterMeasurement
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import com.example.workouttracker.domain.TrackerRef
import com.example.workouttracker.domain.TrackerTypeId
import com.example.workouttracker.domain.LoggedViewRef
import com.example.workouttracker.feature.tracker.TrackerModule
import com.example.workouttracker.feature.tracker.TrackerModules
import com.example.workouttracker.feature.tracker.TrackerScreenHost
import com.example.workouttracker.feature.tracker.ChecklistTrackerModule
import com.example.workouttracker.feature.tracker.TrackerHomeStatus
import com.example.workouttracker.feature.tracker.MeasurementTrackerModule
import com.example.workouttracker.feature.tracker.QuestionsTrackerModule
import com.example.workouttracker.feature.tracker.TimeTrackerModule
import com.example.workouttracker.feature.tracker.LoggedTrackerActions
import com.example.workouttracker.domain.trackerRef
import com.example.workouttracker.feature.tracker.trackerRef
import com.example.workouttracker.feature.tracker.trackerCreationType
import com.example.workouttracker.feature.tracker.trackerCreationVariant
import com.example.workouttracker.feature.tracker.loggedViewRefOrNull
import com.example.workouttracker.core.text.resolve
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.workouttracker.R
import com.example.workouttracker.core.time.currentDateKey
import com.example.workouttracker.core.time.currentHabitDate
import com.example.workouttracker.core.time.localDateKeys
import com.example.workouttracker.core.time.nowMillis
import com.example.workouttracker.core.time.parseDateKey
import com.example.workouttracker.core.ui.LocalDaysOff
import com.example.workouttracker.core.ui.LocalTrackerIdentity
import com.example.workouttracker.core.ui.LocalDayCovers
import com.example.workouttracker.core.ui.ViewIdentity
import com.example.workouttracker.core.ui.LeaveGuard
import com.example.workouttracker.core.ui.ViewTheme
import com.example.workouttracker.core.ui.platformAnimationsEnabled
import com.example.workouttracker.core.ui.theme.WorkoutTrackerTheme
import com.example.workouttracker.domain.AppState
import com.example.workouttracker.domain.holiday.HolidaySchedule
import com.example.workouttracker.domain.loggedView
import com.example.workouttracker.domain.LoggedViewKind
import com.example.workouttracker.domain.questions.QuestionsConfiguration
import com.example.workouttracker.domain.questions.QuestionsEdit
import com.example.workouttracker.domain.questions.addQuestions
import com.example.workouttracker.domain.questions.answeredDaysOf
import com.example.workouttracker.domain.questions.applyingQuestionsEdits
import com.example.workouttracker.domain.questions.deleteQuestions
import com.example.workouttracker.domain.questions.namedAnswerIds
import com.example.workouttracker.domain.questions.namedQuestionIds
import com.example.workouttracker.domain.questions.starterQuestions
import com.example.workouttracker.feature.settings.NamedQuestionIds
import com.example.workouttracker.domain.ref
import com.example.workouttracker.domain.mo.DefaultRewardStart
import java.time.LocalDate
import com.example.workouttracker.persistence.PassChoices
import com.example.workouttracker.domain.activity.GymActivityId
import com.example.workouttracker.domain.activity.SeededGym
import com.example.workouttracker.domain.activity.routine.ExerciseLog
import com.example.workouttracker.domain.activity.routine.SeededGymDayIds
import com.example.workouttracker.domain.activity.routine.WorkoutSession
import com.example.workouttracker.domain.mo.moDaySlot
import com.example.workouttracker.domain.mo.moEvents
import com.example.workouttracker.domain.mo.MoEvaluation
import com.example.workouttracker.domain.time.TimeConfiguration
import com.example.workouttracker.domain.time.TimeEdit
import com.example.workouttracker.domain.activity.ActivityEdit
import com.example.workouttracker.domain.activity.namedActivityIds
import com.example.workouttracker.domain.time.TimeSession
import com.example.workouttracker.domain.time.addTime
import com.example.workouttracker.domain.time.applyingTimeEdits
import com.example.workouttracker.domain.time.deleteTime
import com.example.workouttracker.domain.time.loggedDaysOf
import com.example.workouttracker.domain.time.starterTime
import com.example.workouttracker.domain.time.finishTimeSession
import com.example.workouttracker.domain.reminder.ReminderSettings
import com.example.workouttracker.domain.settings.DashboardFeature
import com.example.workouttracker.domain.settings.FeatureVisibilitySettings
import com.example.workouttracker.domain.substance.SubstanceEdit
import com.example.workouttracker.domain.substance.applyingSubstanceEdits
import com.example.workouttracker.feature.questions.QuestionsDayChange
import com.example.workouttracker.feature.logactivity.ActivityLogScreen
import com.example.workouttracker.feature.mo.moPicture
import com.example.workouttracker.feature.mo.MoNewsSeen
import com.example.workouttracker.feature.mo.TrackerBankPresentation
import com.example.workouttracker.feature.mo.bankReservation
import com.example.workouttracker.domain.ActivityTrackerRef
import com.example.workouttracker.domain.SubstancesTrackerRef
import com.example.workouttracker.feature.substances.viewIdentity
import com.example.workouttracker.feature.substances.viewLabel
import com.example.workouttracker.feature.mo.label
import com.example.workouttracker.feature.mo.MoStreakScreen
import com.example.workouttracker.feature.mo.MoTaskStanding
import com.example.workouttracker.feature.mo.canSetAside
import com.example.workouttracker.feature.mo.canChooseRest
import com.example.workouttracker.domain.mo.canQuiet
import com.example.workouttracker.feature.tracker.CheckpointTokens
import com.example.workouttracker.domain.mo.MoChoices
import com.example.workouttracker.domain.mo.checkpointsToSetAside
import com.example.workouttracker.feature.mo.tokensLeft
import com.example.workouttracker.feature.mo.tokensOnCheckpoints
import com.example.workouttracker.feature.mo.plannedRestDays
import com.example.workouttracker.feature.mo.moDayCovers
import com.example.workouttracker.feature.mo.saidTodayCovers
import com.example.workouttracker.feature.mo.isNews
import com.example.workouttracker.reminder.currentBedtime
import com.example.workouttracker.feature.mo.moFreeDayKeys
import com.example.workouttracker.feature.time.sessionAppearance
import com.example.workouttracker.feature.tracker.missingTrackerIdentity
import com.example.workouttracker.feature.time.TimeDayChange
import com.example.workouttracker.feature.time.TimeFocusQuestion
import com.example.workouttracker.feature.time.TimedMinutes
import com.example.workouttracker.feature.time.TimeSessionHost
import com.example.workouttracker.feature.time.TimeSessions
import com.example.workouttracker.feature.settings.BackupWarning
import com.example.workouttracker.feature.settings.ViewWrite
import com.example.workouttracker.core.text.titleRes
import com.example.workouttracker.feature.settings.FirstStartFrame
import com.example.workouttracker.feature.settings.FirstStartSteps
import com.example.workouttracker.feature.settings.FirstStartWelcome
import com.example.workouttracker.feature.settings.LocalRecordsExist
import com.example.workouttracker.feature.settings.LocalSettingUp
import com.example.workouttracker.feature.settings.SettingsPage
import com.example.workouttracker.feature.settings.parentPage
import com.example.workouttracker.persistence.hasRecords
import com.example.workouttracker.feature.settings.SettingsScreen
import com.example.workouttracker.feature.statistics.StatisticsScreen
import com.example.workouttracker.feature.substances.SubstanceScreen
import com.example.workouttracker.domain.checklist.progressOf
import com.example.workouttracker.domain.checklist.recordDayFor
import com.example.workouttracker.domain.checklist.checklist
import com.example.workouttracker.domain.checklist.checklistRecordedDates
import com.example.workouttracker.domain.checklist.starterChecklist
import com.example.workouttracker.domain.checklist.ChecklistEdit
import com.example.workouttracker.domain.visibilityInUse
import com.example.workouttracker.feature.checklist.presentation
import com.example.workouttracker.feature.checklist.vector
import com.example.workouttracker.feature.checklist.ChecklistScreen
import com.example.workouttracker.domain.checklist.ChecklistConfiguration
import com.example.workouttracker.feature.measurement.MeasurementDayChange
import com.example.workouttracker.domain.DayChangeResult
import com.example.workouttracker.domain.changingDay
import com.example.workouttracker.persistence.backup.DurableBackupManager
import com.example.workouttracker.reminder.ReminderCompletionNotification
import com.example.workouttracker.reminder.activityReminderSubject
import com.example.workouttracker.reminder.activityWasLoggedForDate
import com.example.workouttracker.reminder.substancesWereLoggedForDate
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

@Composable
internal fun MomentumApp(
    state: AppState,
    rewardStart: LocalDate = DefaultRewardStart,
    onStateChange: (AppState) -> Boolean,
    /**
     * Stores Activity's page as saved and moves its reminder to the week as stored. It has no
     * default: storing the edits alone would leave the alarm waiting for a day the week dropped.
     */
    onActivitySave: (edits: List<ActivityEdit>) -> ViewWrite,
    featureVisibility: FeatureVisibilitySettings = FeatureVisibilitySettings(),
    holidaySchedule: HolidaySchedule = HolidaySchedule(),
    passChoices: PassChoices? = null,
    onPassChoicesChange: (PassChoices) -> Boolean = { true },
    onHolidayScheduleChange: (HolidaySchedule) -> Boolean = { true },
    onFeatureVisibilityChange: (FeatureVisibilitySettings) -> Boolean = { true },
    reminderSettings: ReminderSettings = ReminderSettings(),
    onReminderSettingsChange: (ReminderSettings) -> Boolean = { true },
    /** Stores a checklist's page as saved: the edits made on it, applied on the day they are saved. */
    onChecklistSave: (checklistId: String, edits: List<ChecklistEdit>) -> Boolean = { _, _ -> true },
    /** Stores a new checklist: its starter, and the edits the setup made to it. */
    onChecklistCreate: (starter: ChecklistConfiguration, edits: List<ChecklistEdit>) -> Boolean = { _, _ -> true },
    /** Deletes a checklist a person made, with its records, its reminders and its hidden entry. */
    onChecklistDelete: (checklistId: String) -> Boolean = { true },
    /** A save changed a checklist's records, so a reminder on show may have been answered. */
    onChecklistSaved: (checklistId: String, before: AppState, after: AppState) -> Unit = { _, _, _ -> },
    /** Stores the Substances page as saved: the edits made on it, applied on the day they are saved, with its check-in's alarm set or cancelled. */
    onSubstancesSave: (List<SubstanceEdit>) -> Boolean = { edits -> onStateChange(state.applyingSubstanceEdits(edits, currentHabitDate())) },
    /** Stores a measurement's page as saved: the edits made, applied on the habit day they are saved. */
    onMeasurementSave: (measurementId: String, edits: List<MeasurementEdit>) -> ViewWrite = { measurementId, edits ->
        viewWrite(onStateChange(state.applyingMeasurementEdits(measurementId, edits, currentHabitDate())))
    },
    /** Stores a new measurement: its starter, and the edits the setup made to it. Refused where anything names its ID by then. */
    onMeasurementCreate: (starter: MeasurementConfiguration, edits: List<MeasurementEdit>) -> ViewWrite = { starter, edits ->
        viewWrite(
            runCatching { state.addMeasurement(starter, featureVisibility.namedIds(LoggedViewKind.Measurement)).applyingMeasurementEdits(starter.id, edits, currentHabitDate()) }
                .getOrNull()?.let(onStateChange) ?: false,
        )
    },
    /** Deletes a measurement a person made, with every record of it, its reminder and its hidden entry. */
    onMeasurementDelete: (measurementId: String) -> Boolean = { measurementId ->
        runCatching { state.deleteMeasurement(measurementId) }.getOrNull()?.let(onStateChange) ?: false
    },
    /** Saves or removes one day's number of a measurement, decided against the records as they are stored at that moment. */
    onMeasurementDayChange: (MeasurementDayChange) -> DayChangeResult = { change -> state.changingDay(change, nowMillis(), onStateChange) },
    /** A stored tracker record may have answered its reminder for that day. */
    onDayLogged: (view: LoggedViewRef, dateKey: String, before: AppState, after: AppState) -> Unit = { _, _, _, _ -> },
    /** Stores a questions view's page as saved: the edits made, applied on the habit day they are saved. */
    onQuestionsSave: (viewId: String, edits: List<QuestionsEdit>) -> ViewWrite = { viewId, edits ->
        viewWrite(onStateChange(state.applyingQuestionsEdits(viewId, edits, currentHabitDate())))
    },
    /** Stores a new questions view: its starter, and the edits the setup made to it. Refused where anything names its ID by then. */
    onQuestionsCreate: (starter: QuestionsConfiguration, edits: List<QuestionsEdit>) -> ViewWrite = { starter, edits ->
        viewWrite(
            runCatching {
                state.addQuestions(starter.copy(madeOn = currentHabitDate()), featureVisibility.namedIds(LoggedViewKind.Questions)).applyingQuestionsEdits(starter.id, edits, currentHabitDate())
            }.getOrNull()?.let(onStateChange) ?: false,
        )
    },
    /** Deletes a questions view a person made, with every entry of it, its reminder and its hidden entry. */
    onQuestionsDelete: (viewId: String) -> Boolean = { viewId ->
        runCatching { state.deleteQuestions(viewId) }.getOrNull()?.let(onStateChange) ?: false
    },
    /** Saves or removes one day's answers of a questions view, decided against the entries as they are stored at that moment. */
    onQuestionsDayChange: (QuestionsDayChange) -> DayChangeResult = { change -> state.changingDay(change, nowMillis(), onStateChange) },
    /** Stores a time view's page as saved: the edits made, applied on the habit day they are saved. */
    onTimeSave: (viewId: String, edits: List<TimeEdit>) -> ViewWrite = { viewId, edits ->
        viewWrite(onStateChange(state.applyingTimeEdits(viewId, edits, currentHabitDate())))
    },
    /** Stores a new time view: its starter, and the edits the setup made to it. Refused where anything names its ID by then. */
    onTimeCreate: (starter: TimeConfiguration, edits: List<TimeEdit>) -> ViewWrite = { starter, edits ->
        viewWrite(
            runCatching { state.addTime(starter, featureVisibility.namedIds(LoggedViewKind.Time)).applyingTimeEdits(starter.id, edits, currentHabitDate()) }
                .getOrNull()?.let(onStateChange) ?: false,
        )
    },
    /** Deletes a time view a person made, with every log of it, its reminder and its hidden entry. */
    onTimeDelete: (viewId: String) -> Boolean = { viewId ->
        runCatching { state.deleteTime(viewId) }.getOrNull()?.let(onStateChange) ?: false
    },
    /** Adds minutes to one day of a time view or removes the day, decided against the logs as they are stored at that moment. */
    onTimeDayChange: (TimeDayChange) -> DayChangeResult = { change -> state.changingDay(change, nowMillis(), onStateChange) },
    /** Stores what a timed session timed as it ends, decided against the logs as they are stored at that moment. */
    onTimeSessionSave: (session: TimeSession, endMillis: Long) -> DayChangeResult = { session, endMillis ->
        state.changingDay(TimedMinutes(session, endMillis), nowMillis(), onStateChange)
    },
    additionalTrackerModules: List<TrackerModule> = emptyList(),
    requestedNavigation: AppNavigationRequest? = null,
    onDestinationConsumed: () -> Unit = {},
    onReminderCompletion: (ReminderCompletionNotification) -> Unit = {},
    /** The one timed session of the app, whichever time view it is of. */
    timeSessions: TimeSessions = TimeSessions.of(LocalContext.current.applicationContext),
    /**
     * A new installation that has not answered the first start: a welcome, then Settings' own
     * parts as steps ([FirstStartSteps]), before Home.
     */
    firstStartOpen: Boolean = false,
    /** The first start was finished, and never comes again. */
    onFirstStartAnswered: () -> Unit = {},
) {
    var welcomed by rememberSaveable { mutableStateOf(false) }
    var selectedScreenName by rememberSaveable { mutableStateOf(if (firstStartOpen) AppDestination.SettingsPage.name else AppDestination.Home.name) }
    var selectedChecklistId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedLoggedViewId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedTrackerType by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedTrackerId by rememberSaveable { mutableStateOf<String?>(null) }
    // Effects must read the saved reference after navigation, including before recomposition.
    fun selectedTracker(): TrackerRef? = runCatching {
        selectedTrackerType?.let { type -> selectedTrackerId?.let { id -> TrackerRef(TrackerTypeId(type), id) } }
    }.getOrNull()
    var selectedSettingsPage by rememberSaveable { mutableStateOf(if (firstStartOpen) FirstStartSteps.first().key else null) }
    var requestedActivityId by rememberSaveable { mutableStateOf<String?>(null) }
    var requestedChecklistDateKey by rememberSaveable { mutableStateOf<String?>(null) }
    var requestedSubstanceDateKey by rememberSaveable { mutableStateOf<String?>(null) }
    var requestedLoggedDateKey by rememberSaveable { mutableStateOf<String?>(null) }
    var correctionDateKey by rememberSaveable { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val resources = LocalResources.current
    val motionEnabled = platformAnimationsEnabled()
    // The one place the live day is collected. Every screen receives it as a plain string, so a
    // screen left open for days follows the day without owning a clock of its own.
    val systemDateKeys = remember { localDateKeys() }
    val todayKey by systemDateKeys.collectAsState(initial = currentDateKey())
    // A checklist with nothing on it is nowhere in use: Home, the streak and the doors read the
    // switches with such a checklist hidden; Settings reads them as they are.
    val shownVisibility = remember(state, featureVisibility, todayKey) {
        state.visibilityInUse(featureVisibility, requireNotNull(parseDateKey(todayKey)))
    }
    // Overdue depends on the clock, so Home looks at it again every minute while it is open.
    val minuteNow by produceState(LocalTime.now()) {
        while (true) {
            delay(MillisPerMinute - System.currentTimeMillis() % MillisPerMinute)
            value = LocalTime.now()
        }
    }
    // One walk of the history serves the mood and the calendars' free days, keyed on every input it
    // reads. The stored choices are applied on top, those that still stand, so choosing one does
    // not walk the history again.
    val moduleRewards = additionalTrackerModules.flatMap { it.rewards }
    val moduleObservations = additionalTrackerModules.flatMap { it.observations }
    val moEvaluation = remember(state, featureVisibility, todayKey, holidaySchedule, rewardStart, moduleRewards, moduleObservations) {
        MoEvaluation(state, featureVisibility, requireNotNull(parseDateKey(todayKey)), holidaySchedule, rewardStart, moduleRewards, moduleObservations)
    }
    val storedChoices = passChoices?.on(moEvaluation.today) ?: MoChoices()
    val choices = storedChoices.copy(tasks = storedChoices.tasks + additionalTrackerModules.flatMap { it.chosenPasses })
    val mood = remember(moEvaluation, choices) { moEvaluation.moodWith(choices) }
    val observationDay = moEvaluation.today
    val observedRecordTimes = moEvaluation.observations.associate { it.ref to it.recordedAt[observationDay] }
    val observedActions = moEvaluation.observations.associate { it.ref to it.recordedActionKeysOn(observationDay) }
    val owedActions = moEvaluation.observations.mapNotNull { observation ->
        observation.owedActionKeysOn?.let { observation.ref to it(observationDay) }
    }.toMap()
    val observedKept = mood.keptTrackersToday.toSet()
    var observedCompletions by rememberSaveable {
        mutableStateOf(ObservedTrackerCompletions(todayKey, observedKept, observedRecordTimes, observedActions, owedActions))
    }
    val newlyLogged = observedCompletions.celebrating.takeIf { observedCompletions.dayKey == todayKey }
    SideEffect {
        val seen = observedCompletions.seeing(todayKey, observedKept, observedRecordTimes, observedActions, owedActions)
        if (seen != observedCompletions) observedCompletions = seen
    }
    val appContext = LocalContext.current.applicationContext
    val moEvents = remember(mood) { moEvents(mood) }
    val moNewsSeen = remember(appContext) { MoNewsSeen(appContext) }
    val moMomentsSeen = remember(appContext) { MoMomentsSeen(appContext) }
    // Events already true when the app opened have had their moment, except news that has not been
    // handed over today, and a lost streak or a level, which wait until their own screen was seen.
    var moLedger by remember {
        mutableStateOf(
            MoEventLedger(
                celebrated = moEvents.filter { it.momentKey == null && !(it.isNews && !moNewsSeen.isSeen(todayKey)) }.toSet(),
                momentsSeen = moMomentsSeen.all(),
            ),
        )
    }
    val moEvent = moLedger.next(moEvents)
    val bedtime = remember(reminderSettings.fallbackAlarmTime, reminderSettings.fixedBedtime, minuteNow) {
        appContext.currentBedtime(reminderSettings)
    }
    val livePicture = moPicture(mood, reminderSettings, LocalDateTime.of(requireNotNull(parseDateKey(todayKey)), minuteNow), bedtime)
    // What he is up to follows the day and the part of the day, so it is steady while Home is open.
    // How late things are is part of the key, but only in steps, so he does not change every minute.
    val steadyPicture = remember(mood, livePicture.asked.overdueBy.mapValues { it.value / 60 }, todayKey, moDaySlot(minuteNow.hour), livePicture.feeling) {
        livePicture
    }
    val timeSession = timeSessions.session
    // A session can end from a lifecycle callback long after this composition, so it saves into
    // whatever the records are by then.
    val latestState by rememberUpdatedState(state)

    /**
     * Typed or timed minutes answer the reminder for [viewId] on [dateKey], even when they do not
     * reach the configured weekly goal.
     */
    fun timeMinutesStored(viewId: String, dateKey: String, stored: DayChangeResult.Stored) =
        onDayLogged(LoggedViewRef(LoggedViewKind.Time, viewId), dateKey, stored.before, stored.after)

    fun changeTimeDay(change: TimeDayChange): DayChangeResult = onTimeDayChange(change).also { result ->
        if (result is DayChangeResult.Stored && change is TimeDayChange.Add) timeMinutesStored(change.viewId, change.dateKey, result)
    }
    val saveTimeSession: (TimeSession, Long) -> Int? =
        { session, endMillis ->
            // Under a minute, or on a day that is full, a session has nothing to store.
            if (finishTimeSession(latestState, session, endMillis).savedMinutes == 0) {
                0
            } else {
                when (val result = onTimeSessionSave(session, endMillis)) {
                    is DayChangeResult.Stored -> {
                        timeMinutesStored(session.viewId, session.dateKey, result)
                        finishTimeSession(result.before, session, endMillis).savedMinutes
                    }
                    // Its view was deleted while it waited: there is nothing to credit, and nothing to try again.
                    is DayChangeResult.Refused -> 0
                    DayChangeResult.Failed -> null
                }
            }
        }
    val homeStatuses = mood.homeStatuses(started = moEvaluation.observations.filter { it.firstDay != null }.mapTo(mutableSetOf()) { it.ref })
    val statisticsIndex = remember(state) { TrackerStatisticsIndex(state) }
    val modules = TrackerModules(listOf(
        ChecklistTrackerModule(
            state = state,
            visibility = featureVisibility,
            today = requireNotNull(parseDateKey(todayKey)),
            daysOff = holidaySchedule,
            mood = mood,
            onVisibilityChange = { id, visible -> onFeatureVisibilityChange(featureVisibility.withChecklistVisibility(id, visible, requireNotNull(parseDateKey(todayKey)))) },
            onStateChange = { id, updated -> onStateChange(updated).also { saved -> if (saved) onChecklistSaved(id, state, updated) } },
            onSave = onChecklistSave,
            onCreate = onChecklistCreate,
            onDelete = onChecklistDelete,
            starter = { label -> state.starterChecklist(label, currentHabitDate(), hidden = featureVisibility.namedChecklistIds) },
            progress = { checklist -> state.progressOf(checklist, checklist.recordDayFor(requireNotNull(parseDateKey(todayKey))).toString(), holidaySchedule) },
            recordedDays = { checklist -> state.checklistDays.count { it.checklistId == checklist.id } },
            recordedDates = { checklist -> state.checklistRecordedDates(checklist.id) },
            homeStatuses = homeStatuses,
            statisticsIndex = statisticsIndex,
        ),
        MeasurementTrackerModule(
            state, featureVisibility,
            onVisibilityChange = { ref, visible -> onFeatureVisibilityChange(featureVisibility.withVisibility(ref, visible, requireNotNull(parseDateKey(todayKey)))) },
            actions = LoggedTrackerActions(onMeasurementSave, onMeasurementCreate, onMeasurementDelete),
            newMeasurement = { label -> state.starterMeasurement(label, hidden = featureVisibility.namedIds(LoggedViewKind.Measurement)) },
            onDayChange = { change -> onMeasurementDayChange(change).also { result ->
                if (result is DayChangeResult.Stored) onDayLogged(LoggedViewRef(LoggedViewKind.Measurement, change.measurementId), change.dateKey, result.before, result.after)
            } },
            recordedDays = { state.recordedDaysOf(it.id) },
            homeStatuses = homeStatuses,
            statisticsIndex = statisticsIndex,
        ),
        QuestionsTrackerModule(
            state, featureVisibility,
            onVisibilityChange = { ref, visible -> onFeatureVisibilityChange(featureVisibility.withVisibility(ref, visible, requireNotNull(parseDateKey(todayKey)))) },
            actions = LoggedTrackerActions(onQuestionsSave, onQuestionsCreate, onQuestionsDelete),
            newQuestions = { label, question -> state.starterQuestions(label, question, requireNotNull(parseDateKey(todayKey)), hidden = featureVisibility.namedIds(LoggedViewKind.Questions)) },
            onDayChange = { change -> onQuestionsDayChange(change).also { result ->
                if (result is DayChangeResult.Stored) onDayLogged(LoggedViewRef(LoggedViewKind.Questions, change.viewId), change.dateKey, result.before, result.after)
            } },
            answeredDays = { state.answeredDaysOf(it.id) },
            namedIds = { view -> NamedQuestionIds(state.namedQuestionIds(view.id)) { questionId -> state.namedAnswerIds(view.id, questionId) } },
            homeStatuses = homeStatuses,
            statisticsIndex = statisticsIndex,
        ),
        TimeTrackerModule(
            state, featureVisibility,
            onVisibilityChange = { ref, visible -> onFeatureVisibilityChange(featureVisibility.withVisibility(ref, visible, requireNotNull(parseDateKey(todayKey)))) },
            actions = LoggedTrackerActions(onTimeSave, onTimeCreate, onTimeDelete),
            newTime = { label -> state.starterTime(label, hidden = featureVisibility.namedIds(LoggedViewKind.Time)) },
            onDayChange = ::changeTimeDay,
            loggedDays = { state.loggedDaysOf(it.id) },
            daysOff = holidaySchedule,
            sessions = { timeSessions },
            homeStatuses = homeStatuses,
            statisticsIndex = statisticsIndex,
        ),
    ) + additionalTrackerModules)
    fun recordDateForJudgmentDay(ref: TrackerRef, day: LocalDate): LocalDate? {
        if (ref == SubstancesTrackerRef) return substanceRecordJudgedOn(day)
        val view = modules.view(ref)
        return if (view != null) view.recordDateForJudgmentDay(day) else day
    }
    val additionalViews = additionalTrackerModules.flatMap { it.views }
    val freeDays = remember(moEvaluation, additionalViews) {
        moFreeDayKeys(moEvaluation.trackerFreeDays(), ::recordDateForJudgmentDay)
    }
    val settingsPage = selectedSettingsPage?.let(SettingsPage::fromKey)
    fun SettingsPage?.isOpenable(): Boolean = this != null && (trackerRef?.let { modules.view(it) != null }
        ?: trackerCreationType?.let { modules.creation(it, trackerCreationVariant) != null } ?: true)
    fun canOpen(target: NavigationTarget): Boolean = target.normalized().let { current ->
        shownVisibility.canOpen(current.screen) && when (current.screen) {
            AppDestination.Tracker -> current.tracker?.let(modules::view)?.let { it.visible && it.usable } == true
            AppDestination.SettingsPage -> current.pageKey?.let(SettingsPage::fromKey).isOpenable()
            else -> true
        }
    }
    // These slots decode a destination saved before tracker routes shared one reference.
    val storedTarget = NavigationTarget(
        AppDestination.Home,
        checklistId = selectedChecklistId, loggedViewId = selectedLoggedViewId,
        pageKey = selectedSettingsPage, tracker = selectedTracker(),
    ).atSavedScreen(selectedScreenName)
    val selectedScreen = storedTarget.screen.takeIf { canOpen(storedTarget) } ?: AppDestination.Home
    val openTracker = storedTarget.tracker?.let(modules::view)

    // A settings page or a day screen says whether it holds work that leaving would lose; while it
    // does, any way away from it waits in the guard until the person has answered, and the page asks.
    val guard = rememberSaveable(saver = NavigationGuard.Saver) { NavigationGuard() }
    // The ways below are read when they are taken, and Back can still hold the ones composed
    // during the first start after it ended; read through this, they see that it ended.
    val settingUp by rememberUpdatedState(firstStartOpen)

    /** Where the time view [viewId] is looked at. */
    fun timeTarget(viewId: String) = NavigationTarget(AppDestination.Tracker, tracker = TrackerRef(TrackerTypeId("time"), viewId))

    /** Where a step of the first start is shown; null outside the first start. */
    fun firstStartTarget(step: SettingsPage): NavigationTarget? =
        NavigationTarget(AppDestination.SettingsPage, pageKey = step.key).takeIf { settingUp }

    fun go(requested: NavigationTarget) {
        val target = requested.normalized()
        // The note about the last session is for the next look at its view, not for later ones.
        if (timeSessions.note?.let { target.isAt(timeTarget(it.viewId)) } != true) timeSessions.dismissNote()
        selectedChecklistId = target.checklistId
        selectedLoggedViewId = target.loggedViewId
        selectedSettingsPage = target.pageKey
        selectedTrackerType = target.tracker?.type?.value
        selectedTrackerId = target.tracker?.id
        val screen = if (canOpen(target)) target.screen else AppDestination.Home
        selectedScreenName = screen.name
        // What a reminder asked for happens on arrival, where the screen reached consumes it.
        if (target.startSession && screen == AppDestination.Tracker) target.tracker?.let(modules::view)?.startSession?.invoke()
        if (target.activityTarget is ActivityLaunchTarget.Activity && screen == AppDestination.Activity) requestedActivityId = target.activityTarget.activityId
        if (target.substanceDateKey != null && screen == AppDestination.Substances) requestedSubstanceDateKey = target.substanceDateKey
        val opensOnReminderDate = screen == AppDestination.Tracker && target.tracker?.let(modules::view)?.opensOnReminderDate == true
        requestedLoggedDateKey = target.loggedDateKey?.takeIf { opensOnReminderDate }
        // Only the screen it was asked for shows it; any other way there leaves it behind.
        correctionDateKey = target.correctionDateKey?.takeIf { screen == AppDestination.Activity || screen == AppDestination.Tracker }
        requestedChecklistDateKey = null
    }

    fun openTarget(target: NavigationTarget) {
        val normalized = target.normalized()
        // Where the app is now, read when the way is asked for: the system back handler can hold a
        // callback from an earlier composition, whose place would let a page be left unasked.
        val here = NavigationTarget(
            AppDestination.Home,
            checklistId = selectedChecklistId, loggedViewId = selectedLoggedViewId, pageKey = selectedSettingsPage, tracker = selectedTracker(),
        ).atSavedScreen(selectedScreenName)
        if (!guard.holds(normalized, here)) go(normalized)
    }

    fun open(screen: AppDestination, checklistId: String? = null, loggedViewId: String? = null, page: SettingsPage? = null) =
        openTarget(NavigationTarget(screen, checklistId = checklistId, loggedViewId = loggedViewId, pageKey = page?.key))

    /** The open page closed itself, after saving, creating or deleting, so nothing on it is left to guard. */
    fun closePage() {
        guard.pageClosed()
        go(firstStartTarget(FirstStartSteps.first()) ?: NavigationTarget(AppDestination.Settings))
    }

    // Once nothing guards the page any more, as when it was discarded or disposed, the way waits no longer.
    LaunchedEffect(guard.unsavedWork, guard.pending) {
        guard.released()?.let(::go)
    }
    val leaveGuard = LeaveGuard(
        leaveRequested = guard.pending != null,
        onLeaveAnswered = { discard -> guard.answered(discard)?.let(::go) },
        onUnsavedWorkChange = { guard.unsavedWork = it },
    )

    // A page of Settings belongs to Settings, so going back from it returns there. The stored
    // name is read when back is pressed: the system back handler keeps an earlier composition's
    // callback while its enabled state stays the same.
    fun backTarget(): NavigationTarget {
        // In the first start, back goes a step back, and a page opened from a step returns to the first.
        if (settingUp) {
            val step = FirstStartSteps.indexOfFirst { it.key == selectedSettingsPage }
            return requireNotNull(firstStartTarget(FirstStartSteps.getOrNull(step - 1) ?: FirstStartSteps.first()))
        }
        if (selectedScreenName != AppDestination.SettingsPage.name) return NavigationTarget(AppDestination.Home)
        val parent = selectedSettingsPage?.let(SettingsPage::fromKey)?.parentPage
        return parent?.let { NavigationTarget(AppDestination.SettingsPage, pageKey = it.key) } ?: NavigationTarget(AppDestination.Settings)
    }

    /** The whole history as Markdown, offered to the share sheet for an agent to reason with. */
    fun exportStatistics(context: android.content.Context): Boolean {
        val today = LocalDate.parse(todayKey)
        val views = buildList {
            if (shownVisibility.isVisible(DashboardFeature.Activity) && state.activity != null) add(ActivityTrackerRef)
            addAll(modules.views.filter { it.visible && it.usable }.sortedBy { it.homeOrder }.map { it.ref })
            if (shownVisibility.isVisible(DashboardFeature.Substances)) add(SubstancesTrackerRef)
        }
        val resources = context.withAppLanguage().resources
        val markdown = statisticsExport(
            StatisticsExportInput(
                state = state,
                today = today,
                exportedAt = LocalDateTime.now(),
                daysOff = holidaySchedule,
                reminders = reminderSettings,
                observations = moEvaluation.observations,
                history = mood.streak.history,
                currentStreak = mood.streak.days,
                views = views,
            ),
            text = { it.resolve(resources) },
            locale = resources.configuration.locales[0],
        )
        return context.shareStatisticsExport(markdown, today)
    }

    /** The streak as a picture, with Mo as [look] shows him, offered to the share sheet. */
    fun shareStreak(context: android.content.Context, look: MoLook) {
        context.shareStreakImage(renderStreakShareImage(context, look, mood.streak.days), mood.streak.days)
    }

    /**
     * The views whose miss on [loss]'s day broke the streak, in judging order, each opened on the
     * day its record is about. A covered view did not break it, and a view hidden today is not offered.
     */
    fun lossBackfills(loss: MoStreakLoss, locale: java.util.Locale): List<MoBackfill> {
        // The walk's own account of the day: only views that took part in it, in the order it judged them.
        return mood.streak.history.firstOrNull { it.date == loss.lostOn }?.missedTrackers.orEmpty()
            .mapNotNull { ref ->
                val day = recordDateForJudgmentDay(ref, loss.lostOn) ?: return@mapNotNull null
                val dateText = formatDate(day, DateStyle.WeekdayDayMonth, locale)
                when (ref) {
                    ActivityTrackerRef -> state.activity?.takeIf { shownVisibility.isVisible(DashboardFeature.Activity) }?.let { activity ->
                        MoBackfill(activity.label, dateText) {
                            openTarget(NavigationTarget(AppDestination.Activity, correctionDateKey = day.toString()))
                        }
                    }
                    SubstancesTrackerRef -> if (shownVisibility.isVisible(DashboardFeature.Substances)) {
                        MoBackfill(state.substances.viewLabel().resolve(appContext.withAppLanguage().resources), dateText) {
                            openTarget(NavigationTarget(AppDestination.Substances, substanceDateKey = day.toString()))
                        }
                    } else null
                    else -> modules.view(ref)?.takeIf { it.visible && it.usable }?.let { view ->
                        MoBackfill(view.label, dateText) {
                            openTarget(NavigationTarget(AppDestination.Tracker, tracker = ref, loggedDateKey = day.toString(), correctionDateKey = day.toString().takeUnless { view.opensOnReminderDate }))
                        }
                    }
                }
            }
    }
    fun goBack() {
        // Back from the first step goes back to the welcome.
        if (settingUp && selectedSettingsPage == FirstStartSteps.first().key && selectedScreenName == AppDestination.SettingsPage.name) {
            welcomed = false
            return
        }
        openTarget(backTarget())
    }

    /** The stored choices as they hold today: a day's choices end with their day, a week's with its week. */
    fun choicesToday(): PassChoices = requireNotNull(parseDateKey(todayKey)).let { day -> passChoices?.carriedTo(day) ?: PassChoices(day) }

    /**
     * Leaves a task as it is for today, or takes that back: the close deals it its rest day or
     * spends its tokens, and a week's amount before its Sunday is only quieted for the day. Said
     * of a whole checklist, it replaces what was said of its checkpoints. A week's choice an
     * earlier version stored is taken back with it.
     */
    fun setAside(ref: TrackerRef, setAside: Boolean) {
        val stored = choicesToday()
        onPassChoicesChange(
            stored.copy(
                trackers = if (setAside) stored.trackers + ref else stored.trackers - ref,
                checkpoints = stored.checkpoints - ref,
                weeks = stored.weeks - ref,
            ),
        )
    }

    /**
     * Sets a token aside for one checkpoint of a checklist today, or takes it back, starting from
     * the checkpoints that hold one now: a choice of the whole checklist becomes its checkpoints'.
     */
    fun setCheckpointAside(ref: TrackerRef, checkpointId: String, setAside: Boolean) {
        val stored = choicesToday()
        val holding = mood.checkpointsSetAside[ref].orEmpty().let { if (setAside) it + checkpointId else it - checkpointId }
        onPassChoicesChange(
            stored.copy(
                trackers = stored.trackers - ref,
                checkpoints = if (holding.isEmpty()) stored.checkpoints - ref else stored.checkpoints + (ref to holding),
            ),
        )
    }

    /** The tokens [ref]'s screen shows on its checkpoints, or null where its header carries them or it has none. */
    fun checkpointTokens(ref: TrackerRef): CheckpointTokens? = if (!mood.tokensOnCheckpoints(ref)) null else CheckpointTokens(
        left = mood.tokensLeft(ref),
        offered = mood.checkpointsToSetAside(ref),
        setAside = mood.checkpointsSetAside[ref].orEmpty(),
    ) { checkpointId, chosen -> setCheckpointAside(ref, checkpointId, chosen) }

    fun reservationCommand(ref: TrackerRef): ((Boolean) -> Unit)? {
        // An extension module owns its choices and write command, including opting out entirely.
        additionalTrackerModules.firstOrNull { it.typeId == ref.type }?.let { module ->
            return module.views.firstOrNull { it.ref == ref }?.onSetAside
        }
        // Every view with a bank on show, Mo's task or not. For Substances, judged on yesterday's
        // record, a token set aside covers today's judgment of it.
        return if (ref in mood.streak.standings) ({ chosen -> setAside(ref, chosen) }) else null
    }

    LaunchedEffect(requestedNavigation, shownVisibility) {
        requestedNavigation?.let {
            // Whatever the request asks for on arrival travels with the way, which may wait for a
            // page's answer and be forgotten with it.
            openTarget(
                NavigationTarget(
                    it.destination,
                    checklistId = it.checklistId,
                    startSession = it.startSession,
                    activityTarget = it.activityTarget,
                    checklistDateKey = it.checklistDateKey,
                    substanceDateKey = it.substanceDateKey,
                    loggedViewId = it.loggedViewId,
                    loggedDateKey = it.loggedDateKey,
                    tracker = it.tracker,
                ),
            )
            onDestinationConsumed()
        }
    }

    // Reads the stored name when it runs, not the screen this composition resolved: on a cold
    // start a reminder's destination is stored before this effect's first run, and a comparison
    // against the composed screen would send that launch straight back to Home.
    LaunchedEffect(shownVisibility, selectedScreenName, selectedSettingsPage, modules.views.map { it.ref to it.visible }) {
        val current = NavigationTarget(
            AppDestination.Home,
            checklistId = selectedChecklistId, loggedViewId = selectedLoggedViewId, pageKey = selectedSettingsPage, tracker = selectedTracker(),
        ).atSavedScreen(selectedScreenName)
        if (!canOpen(current)) go(backTarget())
    }

    // A session always belongs to its view's screen, including one resumed after the process
    // died, so it is where he lands when it ends.
    LaunchedEffect(timeSession?.viewId) {
        timeSession?.let { openTarget(timeTarget(it.viewId)) }
    }

    // The time view a session or the question before it is of, while it is shown.
    fun timeView(viewId: String): TimeConfiguration? = state.time.firstOrNull { it.id == viewId && modules.view(TrackerRef(TrackerTypeId("time"), it.id))?.visible == true }
    val sessionView = timeSession?.let { timeView(it.viewId) }
    // A session runs on its view's screen. One whose view was hidden meanwhile ends here and keeps
    // what it timed; one whose view was deleted ends with nothing to credit. Either way the one
    // session of the app is free again, and a view's Start is never dead for a session nothing shows.
    LaunchedEffect(timeSession, sessionView == null) {
        if (timeSession != null && sessionView == null) timeSessions.end(leftApp = false, save = saveTimeSession)
    }

    // Behind the welcome nothing is open yet, so back leaves the app.
    BackHandler(enabled = selectedScreen != AppDestination.Home && sessionView == null && !(firstStartOpen && !welcomed), onBack = ::goBack)

    // The first start is Settings' pages; a way out of them, such as a notification's, answers it.
    LaunchedEffect(selectedScreen, firstStartOpen) {
        if (firstStartOpen && selectedScreen != AppDestination.SettingsPage) onFirstStartAnswered()
    }

    if (!LocalInspectionMode.current) TimeFocusQuestion(timeSessions) { viewId -> timeView(viewId)?.sessionAppearance() }

    if (timeSession != null && sessionView != null) {
        TimeSessionHost(
            sessions = timeSessions,
            session = timeSession,
            appearance = sessionView.sessionAppearance(),
            onSave = saveTimeSession,
        )
        return
    }

    if (firstStartOpen && !welcomed) {
        // Its own ground: it stands before the Scaffold that gives every other screen one.
        Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
            FirstStartWelcome(onStart = { welcomed = true }, modifier = Modifier.safeDrawingPadding())
        }
        return
    }
    val firstStartStep = settingsPage?.takeIf { firstStartOpen && selectedScreen == AppDestination.SettingsPage && it in FirstStartSteps }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        CompositionLocalProvider(
            LocalDayCovers provides remember(mood.streak, freeDays, additionalViews, todayKey, mood.chosenTrackersToday, mood.restChosenTrackers) {
                moDayCovers(mood.streak, freeDays, ::recordDateForJudgmentDay, parseDateKey(todayKey), mood.saidTodayCovers())
            },
            LocalRecordsExist provides state.hasRecords,
            LocalSettingUp provides firstStartOpen,
            LocalDaysOff provides remember(holidaySchedule) { { key -> parseDateKey(key)?.let(holidaySchedule::dayOff) } },
            // Each task of Mo's wears its view's stored look, wherever Mo or a calendar shows it.
            LocalTrackerIdentity provides { ref -> modules.view(ref)?.identity() ?: when (ref) {
                ActivityTrackerRef -> state.activity?.presentation() ?: AppDestination.Activity.viewIdentity()
                SubstancesTrackerRef -> state.substances.viewIdentity()
                else -> missingTrackerIdentity()
            } },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                if (selectedScreen != AppDestination.Home && firstStartStep == null) {
                    val shownTracker = if (selectedScreen == AppDestination.Tracker) openTracker
                        else settingsPage?.trackerRef?.takeIf { selectedScreen == AppDestination.SettingsPage }?.let(modules::view)
                    val standingRef = when (selectedScreen) {
                        AppDestination.Activity -> ActivityTrackerRef
                        AppDestination.Tracker -> shownTracker?.ref
                        AppDestination.Substances -> SubstancesTrackerRef
                        else -> null
                    }
                    // A checklist with several checkpoints shows its tokens on them, not here.
                    val bank = standingRef?.takeUnless(mood::tokensOnCheckpoints)?.let { mood.streak.standings[it] }
                    val restDays = (mood.streak.freeDaysLeftByTracker[ActivityTrackerRef] ?: 0).takeIf { standingRef == ActivityTrackerRef && mood.plannedRestDays() > 0 }
                    val pageFeature = when (val page = settingsPage.takeIf { selectedScreen == AppDestination.SettingsPage }) {
                        is SettingsPage.Feature -> page.feature
                        SettingsPage.Substances -> DashboardFeature.Substances
                        else -> null
                    }
                    val shownActivity = state.activity.takeIf { selectedScreen == AppDestination.Activity || pageFeature == DashboardFeature.Activity }
                    val shownSubstances = state.substances.takeIf { selectedScreen == AppDestination.Substances || pageFeature == DashboardFeature.Substances }
                    val trackerCreation = settingsPage?.trackerCreationType?.takeIf { selectedScreen == AppDestination.SettingsPage }?.let { modules.creation(it, settingsPage.trackerCreationVariant) }
                    val pageTitle = when (settingsPage.takeIf { selectedScreen == AppDestination.SettingsPage }) {
                        SettingsPage.Backups -> R.string.settings_backups_title
                        SettingsPage.Holidays -> R.string.settings_holidays_title
                        SettingsPage.SickDays -> R.string.settings_sick_title
                        SettingsPage.Sleep -> R.string.settings_sleep_title
                        SettingsPage.Reminders -> R.string.settings_reminders_title
                        SettingsPage.NewView -> R.string.settings_new
                        SettingsPage.About -> R.string.about_title
                        SettingsPage.Privacy -> R.string.about_privacy_title
                        SettingsPage.LegalNotice -> R.string.about_legal_notice_title
                        is SettingsPage.License -> R.string.about_license_title
                        else -> null
                    }
                    // The arrow is the system's back, so a page that guards unsaved work meets
                    // both the same way; without a dispatcher, as in a preview, it goes back itself.
                    val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
                    TopNavigation(
                        screenLabel = shownTracker?.label ?: trackerCreation?.pageTitle?.resolve() ?: shownActivity?.label ?: shownSubstances?.viewLabel()?.resolve() ?: (pageFeature?.titleRes ?: pageTitle ?: selectedScreen.label).let { stringResource(it) },
                        identity = shownTracker?.identity()
                            ?: trackerCreation?.let { selectedScreen.viewIdentity().copy(icon = it.icon) }
                            ?: shownActivity?.presentation()
                            ?: shownSubstances?.viewIdentity()
                            ?: pageFeature?.destination?.viewIdentity()
                            ?: selectedScreen.viewIdentity(),
                        standing = standingRef?.takeIf { bank != null || restDays != null }?.let { ref ->
                            {
                                val command = reservationCommand(ref)
                                MoTaskStanding(
                                    ref = ref,
                                    label = shownTracker?.label?.let(UiText::Verbatim)
                                        ?: shownActivity?.label?.let(UiText::Verbatim)
                                        ?: shownSubstances?.viewLabel()
                                        ?: mood.label(ref),
                                    tokens = bank?.let { mood.tokensLeft(ref) },
                                    restDays = restDays,
                                    reserved = ref in mood.chosenTrackersToday,
                                    onReserve = command?.takeIf { mood.canSetAside(ref) },
                                    thisWeek = ref in mood.weekJudgedTrackers,
                                    resting = ref in mood.restChosenTrackers,
                                    onRest = command?.takeIf { mood.canChooseRest(ref) },
                                    quiet = ref in mood.quietTrackersToday,
                                    onQuiet = command?.takeIf { mood.canQuiet(ref) },
                                )
                            }
                        },
                        onBack = { backDispatcher?.onBackPressed() ?: goBack() },
                    )
                }

                // A new installation has nothing to lose yet, and the first start has a step for backups.
                if (!LocalInspectionMode.current && !firstStartOpen) {
                    val context = LocalContext.current
                    val backupManager = remember(context) { DurableBackupManager.get(context) }
                    val backupStatus by backupManager.status.collectAsState()
                    BackupWarning(backupStatus) { open(AppDestination.SettingsPage, page = SettingsPage.Backups) }
                }

                // A day to fill in that the screen cannot open on itself is named above it.
                correctionDateKey?.takeIf { selectedScreen == AppDestination.Activity || selectedScreen == AppDestination.Tracker }?.let { key ->
                    Text(
                        text = stringResource(R.string.mo_lost_backfill_hint, formatDate(LocalDate.parse(key), DateStyle.WeekdayDayMonth, currentLocale())),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).testTag("correction_hint"),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }

                AnimatedContent(
                    targetState = selectedScreen,
                    transitionSpec = { destinationTransform(motionEnabled) },
                    label = "destination",
                ) { screen ->
                    when (screen) {
                        AppDestination.Home -> {
                            val context = LocalContext.current
                            HomeScreen(
                            mood = mood,
                            moIsAngry = steadyPicture.angry,
                            moScene = steadyPicture.scene,
                            moEvent = moEvent,
                            onMoEventDone = {
                                moEvent?.let { shown ->
                                    val key = shown.momentKey
                                    // Every ordinary event waiting now counts as handed over, so news
                                    // that a milestone played in front of must not replay after a restart.
                                    if (key == null && moEvents.any { it.isNews }) moNewsSeen.markSeen(todayKey)
                                    if (key != null) moMomentsSeen.markSeen(key)
                                    moLedger = moLedger.handedOver(shown, moEvents)
                                }
                            },
                            onOpenStreak = { open(AppDestination.Streak) },
                            celebrateMo = newlyLogged,
                            moSaveTurn = steadyPicture.saveTurn,
                            onMoCelebrated = {
                                observedCompletions = observedCompletions.copy(pending = emptyList())
                            },
                            featureVisibility = shownVisibility,
                            onLogActivity = { open(AppDestination.Activity) },
                            activity = state.activity,
                            substances = state.substances,
                            trackers = modules.views,
                            onOpenTracker = { ref -> openTarget(NavigationTarget(AppDestination.Tracker, tracker = ref)) },
                            activityStatus = homeStatuses[ActivityTrackerRef] ?: TrackerHomeStatus.None,
                            substanceStatus = (homeStatuses[SubstancesTrackerRef] ?: TrackerHomeStatus.None).let { if (it == TrackerHomeStatus.Done) TrackerHomeStatus.Kept else it },
                            onOpenSubstances = { open(AppDestination.Substances) },
                            pictures = remember(state, todayKey, holidaySchedule) {
                                homeDayPictures(state, LocalDate.parse(todayKey), holidaySchedule)
                            },
                            dueMinutes = { ref -> mood.trackerDueMinutes(ref, reminderSettings) },
                            nowMinute = habitDayMinuteOf(minuteNow),
                            onOpenStatistics = { open(AppDestination.Statistics) },
                            onOpenSettings = { open(AppDestination.Settings) },
                            onShareStreak = { look -> shareStreak(context, look) },
                            onTalkToAgent = { exportStatistics(context) },
                            lossBackfills = (moEvent as? MoEvent.StreakLost)?.let { lossBackfills(it.loss, currentLocale()) }.orEmpty(),
                        )
                        }

                        AppDestination.Activity -> ActivityLogScreen(
                            state = state,
                            leave = leaveGuard,
                            onStateChange = { updated ->
                                val today = currentDateKey()
                                onStateChange(updated).also { stored ->
                                    // Its reminder is answered by anything logged on the day it asked about, as it was stored.
                                    if (stored && activityWasLoggedForDate(state, updated, today)) onReminderCompletion(ReminderCompletionNotification.Activity)
                                }
                            },
                            todayKey = todayKey,
                            week = mood.trackerWeekProgress[ActivityTrackerRef],
                            requestedActivityId = requestedActivityId,
                            onActivityRequestConsumed = { requestedActivityId = null },
                            onWorkoutFinished = { savedSetCount ->
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar(
                                        resources.getString(
                                            if (savedSetCount > 0) R.string.app_workout_saved else R.string.app_workout_finished,
                                        ),
                                    )
                                }
                            },
                        )

                        AppDestination.Tracker -> openTracker?.let { view ->
                            key(view.ref) {
                                ViewTheme(view.identity().colors) {
                                    view.Screen(TrackerScreenHost(todayKey, requestedLoggedDateKey ?: requestedChecklistDateKey, checkpointTokens(view.ref), leaveGuard) {
                                        requestedLoggedDateKey = null
                                        requestedChecklistDateKey = null
                                    })
                                }
                            }
                        }
                        // Compatibility entry points normalize before they reach rendering.
                        AppDestination.Checklist, AppDestination.Measurement, AppDestination.Questions, AppDestination.Time -> Unit

                        AppDestination.Substances -> SubstanceScreen(
                            state = state,
                            leave = leaveGuard,
                            onStateChange = { updated ->
                                val asked = substanceAskedDay(LocalDate.parse(currentDateKey())).toString()
                                onStateChange(updated).also { saved ->
                                    // The check-in asks only whether its day is logged, so any record of that day answers it.
                                    if (saved && substancesWereLoggedForDate(state, updated, asked)) {
                                        onReminderCompletion(ReminderCompletionNotification.Substances)
                                    }
                                }
                            },
                            todayKey = todayKey,
                            onOpenSettings = { open(AppDestination.SettingsPage, page = SettingsPage.Substances) },
                            initialDateKey = requestedSubstanceDateKey,
                            onInitialDateConsumed = { requestedSubstanceDateKey = null },
                        )

                        AppDestination.Streak -> {
                            fun reservation(ref: TrackerRef) = mood.bankReservation(ref, reservationCommand(ref))
                            val banks = modules.views.filter { it.visible && it.usable }.sortedBy { it.bankOrder }.map { view ->
                                view.bankOrder to TrackerBankPresentation(view.ref, view.label, view.bankTagName, view.tokenProgressInMinutes, reservation(view.ref))
                            } + listOf(
                                0 to TrackerBankPresentation(ActivityTrackerRef, state.activity?.label ?: stringResource(R.string.feature_activity), "streak_token_activity", reservation = reservation(ActivityTrackerRef), restDays = (mood.streak.freeDaysLeftByTracker[ActivityTrackerRef] ?: 0).takeIf { mood.plannedRestDays() > 0 }),
                                200 to TrackerBankPresentation(SubstancesTrackerRef, state.substances.viewLabel().resolve(), "streak_substances", reservation = reservation(SubstancesTrackerRef)),
                            )
                            val context = LocalContext.current
                            MoStreakScreen(
                                mood = mood,
                                banks = banks.sortedBy { it.first }.map { it.second },
                                onShare = { shareStreak(context, MoLook.of(mood)) },
                            )
                        }

                        AppDestination.Statistics -> {
                            val context = LocalContext.current
                            StatisticsScreen(
                                state = state,
                                todayKey = todayKey,
                                featureVisibility = shownVisibility,
                                streak = mood.streak,
                                statisticsContributions = modules.views.filter { it.visible && it.usable }.mapNotNull { view ->
                                    view.statistics?.let { TrackerStatisticsContribution(view.ref, view.label, it) }
                                },
                                observations = moEvaluation.observations,
                                daysOff = holidaySchedule,
                                onExport = { exportStatistics(context) },
                            )
                        }

                        // A checklist's settings are a page of Settings, which owns the reminder
                        // permissions and time picker they share.
                        //
                        // A page that was closed is still drawn until its motion ends, with no page
                        // chosen any more. It shows nothing then, not the list: the screen coming
                        // in is that list, and building it twice made every return slower.
                        AppDestination.Settings, AppDestination.SettingsPage -> if (screen == AppDestination.Settings || settingsPage != null) {
                            val settingsScreen: @Composable () -> Unit = {
                            SettingsScreen(
                                trackerModules = modules,
                                todayKey = todayKey,
                                holidaySchedule = holidaySchedule,
                                onHolidayScheduleChange = onHolidayScheduleChange,
                                settings = reminderSettings,
                                onSettingsChange = onReminderSettingsChange,
                                featureVisibility = featureVisibility,
                                onFeatureVisibilityChange = onFeatureVisibilityChange,
                                visibilityInUse = shownVisibility,
                                activityConfiguration = state.activity,
                                // The test is sent for the day of the clock it is tapped on, the day the real one lands on.
                                activityReminder = { activityReminderSubject(state, LocalDate.now(ZoneId.systemDefault()).dayOfWeek) },
                                activityNamedIds = remember(state.activity, state.activityEntries, state.sessions) { state.namedActivityIds },
                                onActivitySave = onActivitySave,
                                rewardsDay = parseDateKey(todayKey),
                                page = settingsPage.takeIf { screen == AppDestination.SettingsPage },
                                onOpenPage = { open(AppDestination.SettingsPage, page = it) },
                                onClosePage = ::closePage,
                                substanceSettings = state.substances,
                                recordedSubstances = state.substanceLogs.mapTo(HashSet()) { it.substanceId },
                                onSubstancesSave = onSubstancesSave,
                                leaveRequested = guard.pending != null,
                                onLeaveAnswered = { discard -> guard.answered(discard)?.let(::go) },
                                onUnsavedWorkChange = { guard.unsavedWork = it },
                                // The Reminders page previews what today's reminder asks about: today's record, or yesterday's.
                                bedtime = bedtime,
                                mood = mood,
                            )
                            }
                            val step = firstStartStep.takeIf { screen == AppDestination.SettingsPage }
                            if (step != null) {
                                FirstStartFrame(
                                    step = step,
                                    onStep = { firstStartTarget(it)?.let(::openTarget) },
                                    onDone = {
                                        onFirstStartAnswered()
                                        go(NavigationTarget(AppDestination.Home))
                                    },
                                    // Track waits until something is on Home: without it the app has nothing to do.
                                    canContinue = step != FirstStartSteps.first() ||
                                        featureVisibility.isVisible(DashboardFeature.Activity) || featureVisibility.isVisible(DashboardFeature.Substances) ||
                                        modules.views.any { it.visible && it.usable },
                                    content = settingsScreen,
                                )
                            } else {
                                settingsScreen()
                            }
                        }
                    }
                }
            }
        }
    }
}

/** What a host without a store or alarms makes of a write, a preview, the gallery or a test: stored or not. */
private fun viewWrite(stored: Boolean): ViewWrite = if (stored) ViewWrite.Stored else ViewWrite.NotStored

/**
 * Opening a view pushes it in from just behind the launchpad; going back pulls it away again. The
 * new screen is composed at once, so the motion only covers the work, never waits for it. The size
 * snaps because the transform must never re-lay out the screen under a finger.
 */
private fun AnimatedContentTransitionScope<AppDestination>.destinationTransform(
    motionEnabled: Boolean,
): ContentTransform {
    val keepSize = SizeTransform(clip = false) { _, _ -> snap() }
    if (!motionEnabled) {
        return (EnterTransition.None togetherWith ExitTransition.None) using keepSize
    }
    val opening = targetState != AppDestination.Home
    val entering = if (opening) EnteringScale else LeavingScale
    val leaving = if (opening) LeavingScale else EnteringScale
    val enter = fadeIn(tween(NavigationDurationMillis)) +
        scaleIn(tween(NavigationDurationMillis), initialScale = entering)
    val exit = fadeOut(tween(NavigationFadeOutMillis)) +
        scaleOut(tween(NavigationDurationMillis), targetScale = leaving)
    return (enter togetherWith exit) using keepSize
}

private const val NavigationDurationMillis = 200
private const val NavigationFadeOutMillis = 110
private const val EnteringScale = 0.92f
private const val LeavingScale = 1.06f

private fun FeatureVisibilitySettings.canOpen(destination: AppDestination): Boolean =
    destination.dashboardFeature?.let(::isVisible) ?: true

@Composable
private fun TopNavigation(
    screenLabel: String,
    identity: ViewIdentity,
    standing: (@Composable () -> Unit)?,
    onBack: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier.testTag("top_back"),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = stringResource(R.string.app_back),
            )
        }
        Icon(
            imageVector = identity.icon,
            contentDescription = null,
            modifier = Modifier.size(28.dp),
            tint = identity.colors.accent,
        )
        // Beside the standing badges a long name or a large font shrinks the name before it is cut.
        val labelStyle = MaterialTheme.typography.titleLarge
        Text(
            text = screenLabel,
            modifier = Modifier.weight(1f),
            style = labelStyle,
            color = identity.colors.label,
            autoSize = TextAutoSize.StepBased(minFontSize = 16.sp, maxFontSize = labelStyle.fontSize, stepSize = 0.5.sp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (standing != null) {
            Box(Modifier.padding(end = 8.dp)) { ViewTheme(identity.colors, standing) }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun TrackerPreview() {
    WorkoutTrackerTheme {
        MomentumApp(
            state = AppState(
                sessions = listOf(
                    WorkoutSession(
                        id = "preview",
                        activityId = GymActivityId,
                        day = SeededGymDayIds[0],
                        dateKey = currentDateKey(),
                        timestampMillis = nowMillis(),
                        exerciseLogs = SeededGym.day(SeededGymDayIds[0])!!.exercises.map { exercise ->
                            ExerciseLog(
                                exerciseId = exercise.id,
                                exerciseName = exercise.label,
                                sets = listOf(exercise.start),
                            )
                        },
                    ),
                ),
            ),
            onStateChange = { true },
            onActivitySave = { ViewWrite.NotStored },
        )
    }
}


private const val MillisPerMinute = 60_000L

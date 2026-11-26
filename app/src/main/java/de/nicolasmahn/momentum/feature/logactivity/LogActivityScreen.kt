package de.nicolasmahn.momentum.feature.logactivity

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import de.nicolasmahn.momentum.R
import de.nicolasmahn.momentum.core.format.DateStyle
import de.nicolasmahn.momentum.core.format.formatDate
import de.nicolasmahn.momentum.core.locale.currentLocale
import de.nicolasmahn.momentum.core.time.habitDate
import de.nicolasmahn.momentum.core.time.parseDateKey
import de.nicolasmahn.momentum.core.ui.DiscardChangesDialog
import de.nicolasmahn.momentum.core.ui.FollowTodayEffect
import de.nicolasmahn.momentum.core.ui.GuardUnsavedWork
import de.nicolasmahn.momentum.core.ui.LeaveGuard
import de.nicolasmahn.momentum.core.ui.MomentaryCompletionButton
import de.nicolasmahn.momentum.core.ui.MomentumButton
import de.nicolasmahn.momentum.core.ui.MomentumButtonVariant
import de.nicolasmahn.momentum.core.ui.MomentumTextField
import de.nicolasmahn.momentum.core.ui.ReservedMessage
import de.nicolasmahn.momentum.core.ui.SelectedDayLine
import de.nicolasmahn.momentum.core.ui.ViewTheme
import de.nicolasmahn.momentum.domain.AppState
import de.nicolasmahn.momentum.domain.activity.ActivityEntry
import de.nicolasmahn.momentum.domain.activity.ListedActivity
import de.nicolasmahn.momentum.domain.activity.activityRules
import de.nicolasmahn.momentum.domain.activity.nextRoutineDay
import de.nicolasmahn.momentum.domain.activity.routine.RoutineDay
import de.nicolasmahn.momentum.domain.mo.MoWeekProgress
import de.nicolasmahn.momentum.feature.checklist.presentation
import de.nicolasmahn.momentum.feature.logactivity.gym.GymPlanPicker
import de.nicolasmahn.momentum.feature.logactivity.gym.GymWorkoutEditor
import de.nicolasmahn.momentum.feature.logactivity.gym.sessionFor
import de.nicolasmahn.momentum.feature.logactivity.gym.unfoldingFrom
import de.nicolasmahn.momentum.feature.logactivity.route.RoutePanel
import de.nicolasmahn.momentum.feature.logactivity.route.RouteRecorder
import de.nicolasmahn.momentum.feature.logactivity.route.RouteRecordingService
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import kotlinx.coroutines.launch

@Composable
internal fun ActivityLogScreen(
    state: AppState,
    onStateChange: (AppState) -> Boolean,
    onWorkoutFinished: (Int) -> Unit,
    todayKey: String,
    /** The activity a reminder's tap asked for, chosen on arrival while a retained activity has the ID, and consumed either way. */
    requestedActivityId: String? = null,
    onActivityRequestConsumed: () -> Unit = {},
    /** The week as the streak counts it: active days kept so far, and the days it asks for. */
    week: MoWeekProgress? = null,
    leave: LeaveGuard = LeaveGuard.None,
    modifier: Modifier = Modifier,
) {
    val rules = state.activityRules
    val tiles = rules.tiles()
    // The first tile, Gym at the start, is what this screen is opened for most days, so it costs
    // no tap. The choice is kept as text, so a tile that left the configuration since falls back
    // to the first.
    var selectedChoice by rememberSaveable { mutableStateOf(tiles.first().choice) }
    var selectedRoutineDayId by rememberSaveable { mutableStateOf<String?>(null) }
    var nameText by rememberSaveable { mutableStateOf("") }
    // The activity picked under Other, by ID, so an entry moves to the one that was tapped and
    // not to whichever activity its name finds; a name typed by hand is a choice by name again.
    var chosenOtherId by rememberSaveable { mutableStateOf<String?>(null) }
    // The day the form works on, picked in the calendar below. It survives the activity being
    // recreated, as a correction must stay on its day.
    var selectedDateKey by rememberSaveable { mutableStateOf(todayKey) }
    var durationText by rememberSaveable { mutableStateOf("") }
    var message by remember { mutableStateOf<ActivityFormMessage?>(null) }
    var editingEntryId by rememberSaveable { mutableStateOf<String?>(null) }
    var expandedTimelineItemId by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedMark = tiles.firstOrNull { it.choice == selectedChoice } ?: tiles.first()
    // The activity chosen, by its tile or under Other, while Activity still offers it; a name
    // typed by hand is no choice of one.
    val selectedActivity = ((selectedMark as? ActivityMark.Listed)?.activityId ?: chosenOtherId?.takeIf { selectedMark == ActivityMark.Other })
        ?.let(rules::activity)
        ?.takeUnless { it.removed }
    val selectedActivityId = selectedActivity?.id
    val isEditingEntry = editingEntryId != null
    // The chosen activity's routine takes the place of the plain entry wherever the activity was
    // chosen; a record opened as an entry stays one, whether or not its activity has a routine since.
    val hasRoutine = !isEditingEntry && selectedActivity?.hasRoutine == true
    // The day open in the editor, resolved through the whole configuration with removed ones kept:
    // the chosen activity's retained day, for a new or continued session, or any day a session on
    // the date was recorded on, so an old session opens whatever its routine or the tiles offer now.
    // A day the routine no longer offers with nothing recorded on the date is no selection, nor
    // is another activity's day the screen was saved with.
    val openDay = selectedRoutineDayId
        ?.let { dayId -> rules.activityOfDay(dayId)?.let { activity -> OpenRoutineDay(activity, requireNotNull(activity.day(dayId))) } }
        ?.takeIf { (activity, day) -> (hasRoutine && activity.id == selectedActivityId && !day.removed) || state.sessionFor(activity, day, selectedDateKey) != null }
    val logsDuration = rules.logsDuration
    val editedEntry = editingEntryId?.let { id -> state.activityEntries.firstOrNull { it.id == id } }
    // A recorded route's minutes are its moving time, which the route measures: no one types them.
    val showsDuration = logsDuration && editedEntry?.route == null
    // Without the field nothing typed is read: text the form still holds from before the setting
    // was switched off, or from the record it opened, neither blocks a save nor reaches one.
    val typedDuration = if (showsDuration) durationText else ""
    val parsedDuration = typedDuration
        .trim()
        .takeIf { it.isNotEmpty() }
        ?.toIntOrNull()
    val durationIsValid = typedDuration.isBlank() || (parsedDuration != null && parsedDuration > 0)

    /**
     * The duration a save stores: what the field holds, or, with no field, what the record being
     * saved over has [recorded]. A recorded duration is a fact, so switching the field off never
     * takes one away; a new record then gets none.
     */
    fun durationToStore(recorded: Int?): Int? = if (showsDuration) parsedDuration else recorded

    /** The tile an entry opens on: its activity's own, or Other, which then holds its name. */
    fun openingMark(entry: ActivityEntry): ActivityMark = tiles.firstOrNull { it == entry.mark } ?: ActivityMark.Other
    fun openingName(entry: ActivityEntry): String =
        if (openingMark(entry) == ActivityMark.Other) rules.activityLabel(entry.activityId, entry.name) else ""

    // What the form holds and has not saved: something typed for a new entry, anything changed on
    // one opened in Edit, or an open routine day's minutes, which only Finish stores; its sets store
    // themselves as they go. An activity picked from Other's list is a choice, not typing.
    val unsaved = when {
        openDay != null -> logsDuration &&
            durationText.trim() != state.sessionFor(openDay.activity, openDay.day, selectedDateKey)?.durationMinutes?.toString().orEmpty()
        editedEntry == null -> typedDuration.isNotBlank() || (selectedMark == ActivityMark.Other && chosenOtherId == null && nameText.isNotBlank())
        else -> selectedMark != openingMark(editedEntry) ||
            nameText != openingName(editedEntry) ||
            (showsDuration && durationText.trim() != editedEntry.durationMinutes?.toString().orEmpty())
    }
    val focusManager = LocalFocusManager.current
    val screenScrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()
    // A day chosen on the picker unfolds out of its card. Opened any other way (a recorded
    // session, a restore) it is simply there, which a negative start says.
    var unfoldFrom by remember { mutableFloatStateOf(-1f) }
    var pickerReturns by remember { mutableStateOf(false) }
    val timeline = remember(state.activityEntries, state.sessions, rules, selectedDateKey) {
        activityTimelineForDate(state, selectedDateKey)
    }
    val history = remember(state.activityEntries, state.sessions, rules, todayKey) {
        activityHistoryGrid(state = state, todayKey = todayKey)
    }
    val presentation = rules.presentation()
    val locale = currentLocale()
    // The screen keeps one identity — Activity's own — so the eye has a single lead. What the
    // chosen activity contributes is its icon on the Save button, which names what is being saved.
    val formIcon = rules.lookOf(openDay?.let { ActivityMark.RoutineDay(it.activity.id, it.day.id) } ?: selectedMark).icon

    // A form that was started belongs to the day it was started on; only an untouched one moves.
    FollowTodayEffect(
        todayKey = todayKey,
        canFollow = { previousTodayKey ->
            selectedDateKey == previousTodayKey &&
                openDay == null &&
                editingEntryId == null &&
                nameText.isBlank() &&
                typedDuration.isBlank()
        },
        onFollow = { selectedDateKey = it },
    )

    /**
     * Closes the routine day open in the editor. Its minutes stay with it: left in the field, they
     * would be saved with the next day or entry as if they had been typed for it.
     */
    fun closeDay() {
        selectedRoutineDayId = null
        durationText = ""
    }

    // Back and Change plan close the day, so they ask first while its minutes are not saved.
    var closeDayAsked by remember { mutableStateOf(false) }

    BackHandler(enabled = openDay != null) {
        if (unsaved) closeDayAsked = true else closeDay()
    }

    /**
     * Lets go of the entry open in Edit with what it brought: its duration, and under Other its
     * name, which left in the form would read as typed for a new entry.
     */
    fun letGoOfEntry() {
        editingEntryId = null
        durationText = ""
        if (selectedMark == ActivityMark.Other) {
            nameText = ""
            chosenOtherId = null
        }
    }

    fun selectActivity(mark: ActivityMark) {
        selectedChoice = mark.choice
        if (openDay != null) closeDay() else selectedRoutineDayId = null
        nameText = ""
        chosenOtherId = null
        message = null
        focusManager.clearFocus()
    }

    /** Chooses [activity] as a tap on it would: its tile, or Other with it chosen where it has no tile. */
    fun chooseActivity(activity: ListedActivity) {
        val tile = tiles.firstOrNull { it is ActivityMark.Listed && it.activityId == activity.id }
        selectActivity(tile ?: ActivityMark.Other)
        if (tile == null) {
            nameText = activity.label
            chosenOtherId = activity.id
        }
    }

    LaunchedEffect(requestedActivityId) {
        val requested = requestedActivityId ?: return@LaunchedEffect
        // An ID no retained activity has, removed since the notification was posted, leaves the
        // screen as it is; a session already open on the requested activity is not disturbed.
        val activity = rules.activity(requested)?.takeUnless { it.removed }
        if (activity != null && (selectedActivityId != activity.id || openDay == null)) {
            chooseActivity(activity)
            // The reminder asks for today's log of the activity it named, never for a record
            // opened from the timeline to move there: that edit is let go, with its duration.
            editingEntryId = null
            durationText = ""
            selectedDateKey = todayKey
            coroutineScope.launch { screenScrollState.animateScrollTo(0) }
        }
        onActivityRequestConsumed()
    }

    /** A calendar tap chooses the day the form works on; nothing opens on top of the screen. */
    fun selectDay(day: ActivityHistoryDay) {
        // The tap lets go of an entry or session being edited, with the duration it brought: left
        // in the form, it would be saved with the next record as if it had been typed for it.
        if (editingEntryId != null) letGoOfEntry() else if (openDay != null) durationText = ""
        selectedDateKey = day.dateKey
        selectedRoutineDayId = null
        expandedTimelineItemId = null
        message = null
    }

    fun editTimelineItem(item: ActivityTimelineItem) {
        when (val source = item.source) {
            is ActivityTimelineSource.Logged -> {
                val entry = source.entry
                selectedChoice = openingMark(entry).choice
                selectedRoutineDayId = null
                nameText = openingName(entry)
                chosenOtherId = null
                selectedDateKey = entry.dateKey
                durationText = entry.durationMinutes?.toString().orEmpty()
                editingEntryId = entry.id
            }

            is ActivityTimelineSource.Workout -> {
                val session = source.session
                // The session opens on its own activity's day, removed or not; the tiles show
                // its activity where they still offer it, and Other as it is otherwise.
                rules.activity(session.activityId)?.takeUnless { it.removed }?.let(::chooseActivity) ?: selectActivity(ActivityMark.Other)
                unfoldFrom = -1f
                selectedRoutineDayId = session.day
                selectedDateKey = session.dateKey
                durationText = session.durationMinutes?.toString().orEmpty()
                editingEntryId = null
            }
        }
        expandedTimelineItemId = null
        message = null
        focusManager.clearFocus()
        coroutineScope.launch { screenScrollState.animateScrollTo(0) }
    }

    fun removeTimelineItem(item: ActivityTimelineItem): Boolean {
        val result = when (val source = item.source) {
            is ActivityTimelineSource.Logged -> deleteActivity(state, source.entry.id)
            is ActivityTimelineSource.Workout -> deleteWorkoutSession(state, source.session.id)
        }
        if (result !is DeleteActivityResult.Success || !onStateChange(result.state)) return false

        val removedEntryId = (item.source as? ActivityTimelineSource.Logged)?.entry?.id
        if (removedEntryId != null && removedEntryId == editingEntryId) letGoOfEntry()
        expandedTimelineItemId = null
        return true
    }

    var saveCelebrationSignal by remember { mutableIntStateOf(0) }

    // The one route recording of the app. While there is one, it takes the form's place, whichever
    // activity is selected: a run goes on when the app is left, and coming back finds it here.
    val context = LocalContext.current
    val recorder = remember(context) { RouteRecorder.of(context) }
    val recording by recorder.recording.collectAsState()
    val lastFixMillis by recorder.lastFixMillis.collectAsState()
    val unsavedOnPhone by recorder.unsavedOnPhone.collectAsState()
    var routeMessage by remember { mutableStateOf<Int?>(null) }

    /** Whether the phone can measure a route now, location allowed and switched on; the line under the button says what is missing. */
    fun canMeasure(): Boolean {
        routeMessage = when {
            !RouteRecordingService.hasLocationPermission(context) -> R.string.activity_route_location_needed
            !RouteRecordingService.locationIsOn(context) -> R.string.activity_route_location_off
            else -> null
        }
        return routeMessage == null
    }

    // What to do once the person answered the question about their location: start, or resume.
    var afterPermission by remember { mutableStateOf<(() -> Unit)?>(null) }
    val askForLocation = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        afterPermission?.invoke()
        afterPermission = null
    }

    /** Runs [action] where location may be used, and asks first where it may not yet. */
    fun withLocation(action: () -> Unit) {
        if (RouteRecordingService.hasLocationPermission(context)) return action()
        afterPermission = action
        // The notification that shows the recording is asked for with it, since Android 13 asks for each.
        val asked = listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION) +
            listOfNotNull(Manifest.permission.POST_NOTIFICATIONS.takeIf { Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU })
        askForLocation.launch(asked.toTypedArray())
    }

    fun startRecording(activity: ListedActivity) = withLocation {
        if (canMeasure() && recorder.start(activity.id, System.currentTimeMillis())) RouteRecordingService.start(context)
    }

    fun resumeRecording() = withLocation {
        if (canMeasure()) {
            recorder.resume()
            RouteRecordingService.start(context)
        }
    }

    fun finishRecording() {
        val finished = recording ?: return
        recorder.pause()
        val dateKey = habitDate(Instant.ofEpochMilli(finished.startedAtMillis).atZone(ZoneId.systemDefault())).toString()
        val result = recordRoute(state, finished, dateKey)
        if (result is RecordActivityResult.Success && onStateChange(result.state)) {
            recorder.clear()
            routeMessage = null
            // The run's day is shown, unless a record opened for editing before the run still waits
            // under the panel: its day is the one it is saved on.
            if (editingEntryId == null && openDay == null) selectedDateKey = dateKey
        } else {
            // Kept, paused, so nothing of the run is lost: Finish can be tried again, or the recording discarded.
            routeMessage = R.string.activity_save_failed
        }
    }

    fun saveActivity() {
        if (!durationIsValid) {
            message = ActivityFormMessage(
                text = R.string.activity_duration_invalid,
                errorField = ActivityFormField.Duration,
            )
            return
        }

        val entryId = editingEntryId
        val duration = durationToStore(recorded = state.activityEntries.firstOrNull { it.id == entryId }?.durationMinutes)
        val result = when {
            entryId == null && selectedActivityId != null -> recordActivityOf(state, selectedActivityId, selectedDateKey, duration, notes = null)
            entryId == null -> recordActivity(state, rawName = nameText, dateKey = selectedDateKey, durationMinutes = duration, notes = null)
            selectedActivityId != null -> updateActivityOf(state, entryId, selectedActivityId, selectedDateKey, duration)
            else -> updateActivity(state, entryId, rawName = nameText, dateKey = selectedDateKey, durationMinutes = duration)
        }

        when (result) {
            is RecordActivityResult.Success -> {
                if (onStateChange(result.state)) {
                    // The button celebrates the save; a second confirmation only pushes the page down.
                    saveCelebrationSignal += 1
                    editingEntryId = null
                    if (selectedMark == ActivityMark.Other) {
                        nameText = ""
                        chosenOtherId = null
                    }
                    durationText = ""
                    message = null
                    focusManager.clearFocus()
                } else {
                    message = ActivityFormMessage(
                        text = R.string.activity_save_failed,
                        errorField = ActivityFormField.General,
                    )
                }
            }

            is RecordActivityResult.Invalid -> {
                val field = when (result.problem) {
                    ActivityProblem.InvalidDuration -> ActivityFormField.Duration
                    ActivityProblem.MissingName, ActivityProblem.NotFound -> ActivityFormField.Name
                    ActivityProblem.InvalidDate, ActivityProblem.BlankEntryId -> ActivityFormField.General
                }
                message = ActivityFormMessage(result.problem.message, errorField = field)
            }
        }
    }

    val canSaveActivity = !hasRoutine &&
        (selectedMark != ActivityMark.Other || nameText.isNotBlank()) &&
        durationIsValid

    ViewTheme(presentation.colors) {
        GuardUnsavedWork(unsaved, leave, testTag = "activity_discard")
        if (closeDayAsked) {
            DiscardChangesDialog(
                testTag = "activity_discard",
                onDiscard = {
                    closeDayAsked = false
                    closeDay()
                },
                onKeep = { closeDayAsked = false },
            )
        }
        Column(
            modifier = modifier
                .fillMaxSize()
                .imePadding()
                .verticalScroll(screenScrollState)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SelectedDayLine(
                selectedDateKey = selectedDateKey,
                openingDateKey = todayKey,
                testTag = "activity_selected_day",
            )

            // The header already counts an ordinary week; only holidays or sick days lower the
            // target, and that is the one thing worth a line here.
            val usualWeeklyTarget = state.activityRules.daysAWeekOf(LocalDate.parse(todayKey).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)))
            if (week != null && week.expected < usualWeeklyTarget) {
                Text(
                    text = pluralStringResource(R.plurals.activity_lighter_week, week.expected, week.done, week.expected),
                    style = MaterialTheme.typography.titleMedium,
                    color = presentation.colors.label,
                    modifier = Modifier.testTag("activity_week_days"),
                )
            }
            val current = recording
            if (current != null) RoutePanel(
                recording = current,
                activityLabel = rules.activity(current.activityId)?.label.orEmpty(),
                lastFixMillis = lastFixMillis,
                // What the person must do comes first; a recording kept only in memory is said otherwise.
                message = (routeMessage ?: R.string.activity_route_unsaved.takeIf { unsavedOnPhone })?.let { stringResource(it) },
                reservedMessages = RouteMessages.map { stringResource(it) },
                onPause = recorder::pause,
                onResume = ::resumeRecording,
                onFinish = ::finishRecording,
                onDiscard = {
                    recorder.clear()
                    routeMessage = null
                },
            ) else ActivityTileRow(
                tiles = tiles,
                configuration = rules,
                selected = selectedMark,
                onSelect = ::selectActivity,
            )

            if (current == null) Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (openDay == null && (selectedMark == ActivityMark.Other || !hasRoutine)) {
                    // Other owns the overflow picker, including activities with a routine.
                    val other = selectedMark == ActivityMark.Other
                    val underOther = rules.activitiesUnderOther()
                    if (other && underOther.isNotEmpty()) {
                        OtherActivityChoices(
                            activities = underOther,
                            configuration = rules,
                            chosenName = if (other) nameText else "",
                            onChoose = ::chooseActivity,
                        )
                    }
                    // Only Other is named here: a chosen tile already says what the activity is.
                    if (other) ActivityNameField(
                        value = nameText,
                        onValueChange = {
                            nameText = it
                            chosenOtherId = null
                            if (message?.errorField == ActivityFormField.Name) message = null
                        },
                        isError = message?.errorField == ActivityFormField.Name,
                    )
                }

                // Only where Settings asks for it. The duration belongs to the entry the button
                // below saves, so it stands above it.
                if (showsDuration) MomentumTextField(
                    value = durationText,
                    onValueChange = {
                        durationText = it
                        if (message?.errorField == ActivityFormField.Duration) message = null
                    },
                    label = stringResource(R.string.activity_duration_label),
                    error = (
                        message?.takeIf { it.errorField == ActivityFormField.Duration }?.text
                            ?: R.string.activity_duration_invalid.takeIf { !durationIsValid }
                        )?.let { stringResource(it) },
                    modifier = Modifier.fillMaxWidth(),
                    testTag = "activity_duration",
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Next,
                    ),
                )

                if (openDay != null) {
                    Column(
                        modifier = Modifier.unfoldingFrom(key = openDay.day.id, fromY = unfoldFrom, animate = unfoldFrom >= 0f),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        MomentumButton(
                            text = stringResource(R.string.activity_change_plan),
                            onClick = {
                                pickerReturns = true
                                if (unsaved) closeDayAsked = true else closeDay()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            variant = MomentumButtonVariant.Secondary,
                            testTag = "gym_change_plan",
                        )
                        GymWorkoutEditor(
                            configuration = rules,
                            activity = openDay.activity,
                            selectedDay = openDay.day.id,
                            dateKey = selectedDateKey,
                            durationMinutes = durationToStore(recorded = state.sessionFor(openDay.activity, openDay.day, selectedDateKey)?.durationMinutes),
                            durationIsValid = durationIsValid,
                            state = state,
                            onStateChange = onStateChange,
                            onFinished = { savedSetCount ->
                                selectedRoutineDayId = null
                                durationText = ""
                                onWorkoutFinished(savedSetCount)
                            },
                        )
                    }
                } else if (hasRoutine) {
                    val activity = requireNotNull(selectedActivity)
                    GymPlanPicker(
                        configuration = rules,
                        activity = activity,
                        nextDayId = activity.nextRoutineDay(state.sessions)?.id,
                        modifier = Modifier.unfoldingFrom(key = activity.id, fromY = 0f, animate = pickerReturns),
                        onPlanSelected = { day, cardTop ->
                            when {
                                !durationIsValid -> message = ActivityFormMessage(
                                    text = R.string.activity_duration_invalid,
                                    errorField = ActivityFormField.Duration,
                                )

                                else -> {
                                    // A recorded session opens on its own minutes, as it does from
                                    // the day's records; a new one takes what was typed for it.
                                    activity.day(day)?.let { state.sessionFor(activity, it, selectedDateKey) }?.durationMinutes
                                        ?.let { durationText = it.toString() }
                                    message = null
                                    unfoldFrom = cardTop
                                    pickerReturns = false
                                    selectedRoutineDayId = day
                                }
                            }
                        },
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        // A recording measures what happens now, so it is offered on today alone;
                        // the button under it still logs the activity without one.
                        val recordable = selectedActivity?.takeIf {
                            it.offersRecording && !isEditingEntry && selectedDateKey == todayKey && RouteRecordingService.phoneHasGps(context)
                        }
                        if (recordable != null) {
                            MomentumButton(
                                text = stringResource(R.string.activity_route_start),
                                onClick = { startRecording(recordable) },
                                icon = Icons.Rounded.LocationOn,
                                modifier = Modifier.fillMaxWidth(),
                                // The save under it stays the one green button the screen leads to.
                                variant = MomentumButtonVariant.Secondary,
                                testTag = "activity_route_start",
                            )
                            ReservedMessage(
                                text = routeMessage?.let { stringResource(it) },
                                testTag = "activity_route_start_message",
                                reserving = RouteMessages.map { stringResource(it) },
                            )
                        }
                        // Momentary because the same activity can honestly be logged
                        // twice in a day.
                        MomentaryCompletionButton(
                            actionLabel = stringResource(
                                if (isEditingEntry) R.string.activity_save_changes else R.string.activity_save,
                            ),
                            completedLabel = stringResource(R.string.activity_saved),
                            onClick = ::saveActivity,
                            icon = formIcon,
                            testTag = "activity_save",
                            celebrationSignal = saveCelebrationSignal,
                            enabled = canSaveActivity,
                        )
                        // Under the button, where the space is already reserved: a failed
                        // save moves neither the choices above nor the day's records below.
                        ReservedMessage(
                            text = message
                                ?.takeIf {
                                    it.errorField == ActivityFormField.General ||
                                        it.errorField == ActivityFormField.Name
                                }
                                ?.text
                                ?.let { stringResource(it) },
                            testTag = "activity_form_message",
                        )
                        if (isEditingEntry) {
                            MomentumButton(
                                text = stringResource(R.string.activity_cancel),
                                onClick = {
                                    letGoOfEntry()
                                    message = null
                                    focusManager.clearFocus()
                                },
                                modifier = Modifier.fillMaxWidth(),
                                variant = MomentumButtonVariant.Quiet,
                                testTag = "activity_edit_cancel",
                            )
                        }
                    }
                }
            }

            ActivityHistory(
                grid = history,
                configuration = rules,
                selectedDateKey = selectedDateKey,
                todayKey = todayKey,
                onDayClick = ::selectDay,
            )

            ActivityTimeline(
                items = timeline,
                configuration = rules,
                dayLabel = parseDateKey(selectedDateKey)
                    ?.let { formatDate(it, DateStyle.WeekdayDayMonth, locale) }
                    .orEmpty(),
                expandedItemId = expandedTimelineItemId,
                onExpandedItemChange = { expandedTimelineItemId = it },
                // Under a recording the form is not shown, so nothing can be opened into it.
                onEdit = if (recording == null) ::editTimelineItem else null,
                onRemove = ::removeTimelineItem,
            )
        }
    }
}

/** Every line the route's reserved message may show, so its space fits the longest at any font. */
private val RouteMessages = listOf(R.string.activity_route_location_needed, R.string.activity_route_location_off, R.string.activity_save_failed, R.string.activity_route_unsaved)

/** The routine day the editor is open on, with the activity whose routine it belongs to. */
private data class OpenRoutineDay(val activity: ListedActivity, val day: RoutineDay)

/** A tile's choice as the saved screen state keeps it: text, so a tile gone since, or the `gym` an older screen kept, reads as the first. */
private val ActivityMark.choice: String
    get() = when (this) {
        ActivityMark.Other -> "custom"
        is ActivityMark.Listed -> "activity:$activityId"
        is ActivityMark.RoutineDay -> "routine:$dayId"
    }

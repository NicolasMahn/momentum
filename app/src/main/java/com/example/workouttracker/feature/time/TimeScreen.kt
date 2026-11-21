package com.example.workouttracker.feature.time

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MoreTime
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.example.workouttracker.core.format.dateText
import com.example.workouttracker.core.text.resolve
import com.example.workouttracker.core.ui.DayCover
import com.example.workouttracker.core.ui.FollowTodayEffect
import com.example.workouttracker.core.ui.GuardUnsavedWork
import com.example.workouttracker.core.ui.LeaveGuard
import com.example.workouttracker.core.ui.LocalViewColors
import com.example.workouttracker.core.ui.MomentaryCompletionButton
import com.example.workouttracker.core.ui.MomentumButton
import com.example.workouttracker.core.ui.MomentumButtonDefaults
import com.example.workouttracker.core.ui.MomentumButtonVariant
import com.example.workouttracker.core.ui.MomentumProgressBar
import com.example.workouttracker.core.ui.MomentumRemoveButton
import com.example.workouttracker.core.ui.MomentumTextField
import com.example.workouttracker.core.ui.ReservedMessage
import com.example.workouttracker.core.ui.SelectedDayLine
import com.example.workouttracker.core.ui.ViewTheme
import com.example.workouttracker.domain.AppState
import com.example.workouttracker.domain.DayChangeResult
import com.example.workouttracker.domain.holiday.HolidaySchedule
import com.example.workouttracker.domain.time.TimeConfiguration
import com.example.workouttracker.domain.time.minutesInWeek
import com.example.workouttracker.feature.checklist.presentation
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import kotlinx.coroutines.delay

/**
 * The screen of one time view: the selected day's week in minutes against what the week asks,
 * Start or typed minutes with Add, the two-step removal of a day, one message line and the recent
 * days as a calendar, which is where a day is picked. The selected day is named above the week
 * only while it is not today. Each configured timer reads and writes the logs of [view]'s ID
 * alone, says what [words] gives it and wears the view's look.
 *
 * The week's bar shows what the week asks ([TimeConfiguration.weekAsks]): its Sunday's goal, less
 * for the days in [daysOff] that are sick or away and not due, as the streak judges it.
 *
 * Minutes to add and a removal are handed to [onDayChange] as what is asked for one day, with the
 * log the screen showed for it. Whoever stores it decides against the logs as they are at that
 * moment. What is typed belongs to the view and the day it was typed for, never to today: only an
 * untouched screen moves on when the day changes, and the selected day and what is typed survive
 * process death.
 *
 * [onStartSession] starts the timer for today. [sessionNote] is what the last session of this view
 * left to explain, shown in the message line. [testTagPrefix] identifies the configured timer in UI tests. [covers] are the days Mo's streak covered without a log, for a view that is one of
 * his tasks.
 */
@Composable
internal fun TimeScreen(
    state: AppState,
    view: TimeConfiguration,
    words: TimeWords,
    onDayChange: (TimeDayChange) -> DayChangeResult,
    todayKey: String,
    daysOff: HolidaySchedule = HolidaySchedule(),
    onStartSession: () -> Unit = {},
    sessionNote: TimeSessionNote? = null,
    testTagPrefix: String = view.testTagPrefix,
    covers: Map<String, DayCover> = emptyMap(),
    leave: LeaveGuard = LeaveGuard.None,
) {
    key(view.id) {
        TimeDays(state, view, words, onDayChange, todayKey, daysOff, onStartSession, sessionNote, testTagPrefix, covers, leave)
    }
}

@Composable
private fun TimeDays(
    state: AppState,
    view: TimeConfiguration,
    words: TimeWords,
    onDayChange: (TimeDayChange) -> DayChangeResult,
    todayKey: String,
    daysOff: HolidaySchedule,
    onStartSession: () -> Unit,
    sessionNote: TimeSessionNote?,
    testTagPrefix: String,
    covers: Map<String, DayCover>,
    leave: LeaveGuard,
) {
    val logs = remember(state.timeLogs, view.id) { state.timeLogs.filter { it.viewId == view.id } }
    // Kept across process death with what is typed. It sits inside the view's own key and is never
    // keyed on today, so it is neither another view's nor dropped when the day changes.
    var selectedDateKey by rememberSaveable { mutableStateOf(todayKey) }
    var minutesProblem by remember { mutableStateOf<MinutesError?>(null) }
    // Never a preset: a number already in the field would be minutes nobody recorded. Typed minutes
    // belong to the date they were typed for, so another date starts empty.
    var minutesText by rememberSaveable(selectedDateKey) { mutableStateOf("") }
    // Typing minutes for today is a detour from the timer, taken on purpose and left after saving.
    var typingMinutes by rememberSaveable(selectedDateKey) { mutableStateOf(false) }
    // Only a screen still resting on the old today with nothing typed moves on at the habit-day
    // boundary. Minutes typed and not added belong to the day they were typed for, and stay with it.
    FollowTodayEffect(
        todayKey = todayKey,
        canFollow = { previousTodayKey -> selectedDateKey == previousTodayKey && minutesText.isEmpty() },
        onFollow = { selectedDateKey = it },
    )
    val savedLog = logs.firstOrNull { it.dateKey == selectedDateKey }
    val historyWindow = remember(logs, todayKey) {
        timeHistoryWindow(logs = logs, todayKey = todayKey)
    }
    val presentation = view.presentation()
    var saveError by remember(logs, selectedDateKey) { mutableStateOf(false) }
    var deleteError by remember(logs, selectedDateKey) { mutableStateOf(false) }
    var saveInFlight by remember { mutableStateOf(false) }
    var pendingSuccessfulDates by remember { mutableStateOf(emptySet<String>()) }
    var completionSignal by remember { mutableIntStateOf(0) }
    val selectedDateIsPending = selectedDateKey in pendingSuccessfulDates
    val isEffectivelyCompleted = selectedDateIsPending
    val selectedWeekMonday = LocalDate.parse(selectedDateKey).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val weekMinutes = minutesInWeek(logs, view.id, selectedWeekMonday)
    val weekAsks = view.weekAsks(selectedWeekMonday, daysOff)
    // Only today can be timed; any day, today included, can have minutes typed in.
    val isToday = selectedDateKey == todayKey

    LaunchedEffect(completionSignal) {
        if (completionSignal == 0 || !typingMinutes) return@LaunchedEffect
        // Long enough to see "Added", then back to the timer, which is how today is recorded.
        delay(ReturnToTimerMillis)
        typingMinutes = false
    }
    LaunchedEffect(logs) {
        pendingSuccessfulDates = pendingSuccessfulDates - logs.map { it.dateKey }.toSet()
    }

    ViewTheme(presentation.colors) {
        GuardUnsavedWork(minutesText.isNotEmpty(), leave, testTag = "${testTagPrefix}_discard")
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // The week, the action and its message line are one group, closer to each other than
            // to the calendar, so the empty message slot opens no band of dead space above it.
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SelectedDayLine(
                    selectedDateKey = selectedDateKey,
                    openingDateKey = todayKey,
                    testTag = "${testTagPrefix}_selected_day",
                )

                WeekMinutes(minutes = weekMinutes, asks = weekAsks, words = words, testTag = "${testTagPrefix}_week_minutes")

                // Today is timed as it happens. Minutes added without the timer are the exception, so
                // they wait behind a small button that turns this same row into a minutes row; a day
                // already gone can only be typed in, so it shows that row at once. The row keeps its
                // height either way, so nothing under it moves.
                val minutesField = @Composable { modifier: Modifier ->
                    MomentumTextField(
                        value = minutesText,
                        onValueChange = {
                            minutesText = it.filter(Char::isDigit).take(4)
                            minutesProblem = null
                            saveError = false
                        },
                        label = words.minutesLabel.resolve(),
                        error = minutesProblem?.let { words.minutesError(it).resolve() },
                        modifier = modifier,
                        testTag = "${testTagPrefix}_minutes_input",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                }
                val addLabel = words.add.resolve()
                val addedLabel = words.added.resolve()
                val labelStyle = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                val measurer = rememberTextMeasurer()
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    // On a narrow phone or at a large font the add button's label does not fit beside
                    // the field; then the field stands on its own line above the buttons. That line is
                    // kept on the timer's row too, so opening the minutes row moves nothing under it.
                    val labelRoom = maxWidth - MinutesFieldWidth - MomentumButtonDefaults.MinHeight - 8.dp * 2 - AddButtonChrome
                    val widestLabel = listOf(addLabel, addedLabel).maxOf { measurer.measure(it, labelStyle, maxLines = 1).size.width }
                    val fieldAbove = widestLabel > with(LocalDensity.current) { labelRoom.roundToPx() }
                    val typing = !isToday || typingMinutes
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = if (fieldAbove) FieldLineHeight + ActionRowMinHeight else 0.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.Bottom),
                    ) {
                        if (typing && fieldAbove) minutesField(Modifier.fillMaxWidth())
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = ActionRowMinHeight),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.Bottom,
                        ) {
                            if (!typing) {
                                MomentumButton(
                                    text = words.start.resolve(),
                                    onClick = onStartSession,
                                    modifier = Modifier.weight(1f),
                                    icon = presentation.icon,
                                    testTag = "${testTagPrefix}_start",
                                )
                                SquareIconButton(
                                    icon = Icons.Rounded.MoreTime,
                                    description = words.typeMinutes.resolve(),
                                    onClick = { typingMinutes = true },
                                    testTag = "${testTagPrefix}_type_minutes",
                                )
                            } else {
                                if (!fieldAbove) {
                                    minutesField(Modifier.width(MinutesFieldWidth).padding(bottom = MomentumButtonDefaults.LedgeHeight))
                                }

                                key(selectedDateKey) {
                                    MomentaryCompletionButton(
                                        actionLabel = addLabel,
                                        completedLabel = addedLabel,
                                        onClick = {
                                            if (saveInFlight || isEffectivelyCompleted) return@MomentaryCompletionButton
                                            val minutes = minutesText.toIntOrNull()
                                            minutesProblem = minutesError(
                                                minutes = minutes,
                                                alreadyRecorded = savedLog?.minutes ?: 0,
                                            )
                                            if (minutesProblem == null && minutes != null) {
                                                saveInFlight = true
                                                val saved = onDayChange(TimeDayChange.Add(view.id, selectedDateKey, savedLog, minutes)) is DayChangeResult.Stored
                                                saveError = !saved
                                                if (saved) {
                                                    pendingSuccessfulDates =
                                                        pendingSuccessfulDates + selectedDateKey
                                                    completionSignal += 1
                                                    minutesText = ""
                                                }
                                                saveInFlight = false
                                            }
                                        },
                                        modifier = Modifier.weight(1f),
                                        icon = presentation.icon,
                                        testTag = "${testTagPrefix}_log_today",
                                        enabled = !saveInFlight && !selectedDateIsPending,
                                        celebrationSignal = completionSignal,
                                    )
                                }
                            }

                            // The remove button keeps its slot on days with nothing to remove, so moving
                            // between days never changes where the completion button ends. While today's
                            // minutes are being typed, the way back to the timer takes that slot; removing
                            // is one step away, on the timer's row.
                            if (isToday && typingMinutes) {
                                SquareIconButton(
                                    icon = Icons.Rounded.Close,
                                    description = words.backToTimer.resolve(),
                                    onClick = {
                                        typingMinutes = false
                                        minutesText = ""
                                        minutesProblem = null
                                    },
                                    testTag = "${testTagPrefix}_back_to_timer",
                                )
                            } else {
                                MomentumRemoveButton(
                                    description = words.remove(savedLog?.minutes, dateText(selectedDateKey)).resolve(),
                                    resetKey = selectedDateKey,
                                    enabled = savedLog != null,
                                    testTag = "${testTagPrefix}_remove_day",
                                    onRemove = {
                                        val log = savedLog ?: return@MomentumRemoveButton
                                        val removed = onDayChange(TimeDayChange.Remove(view.id, log.dateKey, log)) is DayChangeResult.Stored
                                        deleteError = !removed
                                        if (removed) {
                                            pendingSuccessfulDates = pendingSuccessfulDates - log.dateKey
                                        }
                                    },
                                )
                            }
                        }
                    }
                }

                val failed = deleteError || saveError
                ReservedMessage(
                    text = when {
                        deleteError -> words.removalFailed.resolve()
                        saveError -> words.saveFailed.resolve()
                        else -> sessionNote?.let(words::sessionNote)?.resolve()
                    },
                    testTag = when {
                        deleteError -> "${testTagPrefix}_delete_error"
                        saveError -> "${testTagPrefix}_save_error"
                        else -> "${testTagPrefix}_session_note"
                    },
                    color = if (failed) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }

            TimeHistory(
                window = historyWindow,
                words = words,
                selectedDateKey = selectedDateKey,
                todayKey = todayKey,
                testTagPrefix = testTagPrefix,
                onDayClick = { day ->
                    selectedDateKey = day.dateKey
                    saveError = false
                },
                covers = covers,
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

/** The selected day's week against what the week asks: a bar to glance at and the count to read. */
@Composable
private fun WeekMinutes(minutes: Int, asks: Int, words: TimeWords, testTag: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MomentumProgressBar(
            // A week off throughout asks nothing, and so reads as full.
            progress = if (asks == 0) 1f else minutes / asks.toFloat(),
            description = words.weekMinutesSpoken(minutes, asks).resolve(),
            modifier = Modifier.weight(1f),
        )
        Text(
            text = words.weekMinutes(minutes, asks).resolve(),
            modifier = Modifier.testTag(testTag),
            style = MaterialTheme.typography.titleMedium,
            color = LocalViewColors.current.label,
        )
    }
}

private const val ReturnToTimerMillis = 1_400L
private val MinutesFieldWidth = 116.dp

/** An outlined field with a floating label: its minimum height, the label's gap above it, and the ledge padding under it. */
private val ActionRowMinHeight = OutlinedTextFieldDefaults.MinHeight + 8.dp + MomentumButtonDefaults.LedgeHeight

/** The minutes field on its own line above the buttons: the field, its label's gap, and the gap to the buttons. */
private val FieldLineHeight = OutlinedTextFieldDefaults.MinHeight + 8.dp + 8.dp

/** What the add button holds beside its label: its side padding, its icon and the gap after it. */
private val AddButtonChrome = 24.dp * 2 + 32.dp + 12.dp

/** A square secondary button carrying only an icon, the size of the remove button beside it. */
@Composable
private fun SquareIconButton(icon: ImageVector, description: String, onClick: () -> Unit, testTag: String) {
    MomentumButton(
        onClick = onClick,
        modifier = Modifier.width(MomentumButtonDefaults.MinHeight),
        variant = MomentumButtonVariant.Secondary,
        contentPadding = PaddingValues(0.dp),
        testTag = testTag,
        faceModifier = Modifier.semantics { contentDescription = description },
    ) {
        Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(24.dp))
    }
}

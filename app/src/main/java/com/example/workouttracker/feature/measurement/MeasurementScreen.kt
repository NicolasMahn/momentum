package com.example.workouttracker.feature.measurement

import com.example.workouttracker.R
import com.example.workouttracker.core.time.isValidDateKey
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.workouttracker.core.format.formatMetricNumber
import com.example.workouttracker.core.format.dateText
import com.example.workouttracker.core.format.relativeDateText
import com.example.workouttracker.core.locale.currentLocale
import com.example.workouttracker.core.text.resolve
import com.example.workouttracker.core.ui.CompletionButton
import com.example.workouttracker.core.ui.FollowTodayEffect
import com.example.workouttracker.core.ui.MomentumButton
import com.example.workouttracker.core.ui.MomentumButtonVariant
import com.example.workouttracker.core.ui.DiscardChangesDialog
import com.example.workouttracker.core.ui.GuardUnsavedWork
import com.example.workouttracker.core.ui.LeaveGuard
import com.example.workouttracker.core.ui.MomentumRemoveButton
import com.example.workouttracker.core.ui.ReservedMessage
import com.example.workouttracker.core.ui.SelectedDayLine
import com.example.workouttracker.core.ui.ViewTheme
import com.example.workouttracker.domain.AppState
import com.example.workouttracker.feature.checklist.presentation
import com.example.workouttracker.domain.DayChangeRefusal
import com.example.workouttracker.domain.DayChangeResult
import com.example.workouttracker.core.format.typedDecimal
import com.example.workouttracker.core.ui.MomentumPopOutGroup
import com.example.workouttracker.core.ui.MomentumStepPopOut
import com.example.workouttracker.core.ui.StepEditButton
import com.example.workouttracker.core.ui.StepRefusal
import com.example.workouttracker.domain.measurement.MaximumMeasurementStep
import com.example.workouttracker.domain.measurement.MeasurementStep
import com.example.workouttracker.domain.measurement.MeasurementStepDecimals
import com.example.workouttracker.domain.measurement.MeasurementConfiguration
import com.example.workouttracker.domain.measurement.MeasurementValuePolicy
import com.example.workouttracker.domain.measurement.recordsOf
import com.example.workouttracker.domain.measurement.MeasurementRecord

/**
 * The screen of one measurement: today's number, or another day's, with the record before it, and
 * the history as a chart with its 7-day average. A day is picked in the chart: a tap selects the
 * day under it, measured or not, and a drag steps through the measured days. The selected day is
 * named above the number only while it is not today. Records are scoped to [measurement]'s ID;
 * [words] uses its configured label and unit, and the screen wears its configured identity.
 *
 * What is typed and not saved belongs to the measurement's ID and the selected day. Another
 * measurement starts from its own records; a new name, unit, step or look, and a new day while
 * something is typed, leave the draft as it is.
 *
 * The step of − and + is changed here too, in a pop-out under the field, and handed to
 * [onStepChange], which stores it as Settings does and says whether it did.
 *
 * A save and a removal are handed to [onDayChange] as what is asked for one day, with the record
 * the draft opened on. Whoever stores it decides against the records as they are at that moment,
 * so this screen never writes the state it was shown over one that changed meanwhile.
 *
 * [initialDateKey] is a day a reminder asked about, opened once on arrival, which
 * [onInitialDateConsumed] acknowledges. It moves a screen nobody touched and never replaces a
 * number being typed.
 */
@Composable
internal fun MeasurementScreen(
    state: AppState,
    measurement: MeasurementConfiguration,
    onDayChange: (MeasurementDayChange) -> DayChangeResult,
    onStepChange: (MeasurementStep) -> Boolean,
    todayKey: String,
    initialDateKey: String? = null,
    onInitialDateConsumed: () -> Unit = {},
    leave: LeaveGuard = LeaveGuard.None,
) {
    key(measurement.id) {
        MeasurementDays(state, measurement, onDayChange, onStepChange, todayKey, initialDateKey, onInitialDateConsumed, leave)
    }
}

@Composable
private fun MeasurementDays(
    state: AppState,
    measurement: MeasurementConfiguration,
    onDayChange: (MeasurementDayChange) -> DayChangeResult,
    onStepChange: (MeasurementStep) -> Boolean,
    todayKey: String,
    initialDateKey: String?,
    onInitialDateConsumed: () -> Unit,
    leave: LeaveGuard,
) {
    val locale = currentLocale()
    val words = remember(measurement.id, measurement.label, measurement.unit, measurement.valuePolicy) { measurement.words() }
    val records = remember(state.measurementLogs, measurement.id) { state.recordsOf(measurement.id) }
    var draft by rememberSaveable(stateSaver = MeasurementDraftSaver) {
        mutableStateOf(measurementDraftForDate(records, todayKey, locale))
    }
    var pendingDateKey by rememberSaveable { mutableStateOf<String?>(null) }
    var historyRange by rememberSaveable { mutableStateOf(MeasurementHistoryRange.FourWeeks) }
    // What went wrong, not a sentence, so a language change rewords a message that is already showing.
    var error by remember { mutableStateOf<MeasurementScreenError?>(null) }
    var saveSignal by remember { mutableIntStateOf(0) }
    var stepOpen by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    // The screen wears the look the measurement's configuration names, as its tile and its header do.
    val presentation = measurement.presentation()
    val selectedLog = records
        .filter { it.dateKey == draft.dateKey }
        .maxByOrNull { it.timestampMillis }
    val previousLog = records
        .filter { it.dateKey < draft.dateKey && isValidDateKey(it.dateKey) }
        .maxWithOrNull(compareBy<MeasurementRecord> { it.dateKey }.thenBy { it.timestampMillis })

    fun loadDate(dateKey: String) {
        draft = measurementDraftForDate(records, dateKey, locale)
        pendingDateKey = null
        error = null
        focusManager.clearFocus()
    }

    fun requestDate(dateKey: String) {
        if (dateKey == draft.dateKey) return
        if (draft.isDirty) pendingDateKey = dateKey else loadDate(dateKey)
    }

    fun save() {
        val value = words.parse(draft.valueText)
        if (value == null || !measurement.acceptsValue(value)) {
            error = MeasurementScreenError.Invalid
            return
        }
        when (val result = onDayChange(MeasurementDayChange.Save(measurement.id, draft.dateKey, draft.originalLog, value))) {
            is DayChangeResult.Stored -> {
                draft = measurementDraftForDate(result.after.recordsOf(measurement.id), draft.dateKey, locale)
                error = null
                saveSignal += 1
                focusManager.clearFocus()
            }

            is DayChangeResult.Refused -> error = when (result.why) {
                DayChangeRefusal.ChangedElsewhere -> MeasurementScreenError.ChangedBeforeSave
                DayChangeRefusal.Deleted -> MeasurementScreenError.Deleted
            }

            DayChangeResult.Failed -> error = MeasurementScreenError.SaveFailed
        }
    }

    fun removeSavedMeasurement() {
        val dateKey = draft.dateKey
        if (selectedLog == null) return
        when (val result = onDayChange(MeasurementDayChange.Remove(measurement.id, dateKey, draft.originalLog))) {
            is DayChangeResult.Stored -> {
                draft = measurementDraftForDate(result.after.recordsOf(measurement.id), dateKey, locale)
                error = null
                focusManager.clearFocus()
            }

            is DayChangeResult.Refused -> error = when (result.why) {
                DayChangeRefusal.ChangedElsewhere -> MeasurementScreenError.ChangedBeforeRemoval
                DayChangeRefusal.Deleted -> MeasurementScreenError.Deleted
            }

            DayChangeResult.Failed -> error = MeasurementScreenError.RemovalFailed
        }
    }

    fun adjust(delta: Double) {
        val adjusted = draft.adjusted(delta, locale, words::parse, measurement.valuePolicy)
        if (adjusted == null) {
            error = MeasurementScreenError.Invalid
        } else {
            draft = adjusted
            error = null
        }
    }

    FollowTodayEffect(
        todayKey = todayKey,
        canFollow = { previousTodayKey -> !draft.isDirty && draft.dateKey == previousTodayKey },
        onFollow = { draft = measurementDraftForDate(records, it, locale) },
    )

    LaunchedEffect(initialDateKey) {
        if (initialDateKey != null) {
            if (isValidDateKey(initialDateKey) && !draft.isDirty) loadDate(initialDateKey)
            onInitialDateConsumed()
        }
    }

    LaunchedEffect(records, draft.dateKey) {
        if (!draft.isDirty) draft = measurementDraftForDate(records, draft.dateKey, locale)
    }

    // The day counts as logged only while the saved entry still matches the draft on screen.
    val isSaved = draft.originalLog != null && !draft.isDirty
    val historyWindow = remember(records, todayKey, historyRange, measurement.trendDays, measurement.valuePolicy) {
        measurementHistoryWindow(records, todayKey, historyRange, measurement.trendDays, measurement.valuePolicy)
    }

    ViewTheme(presentation.colors) {
        GuardUnsavedWork(draft.isDirty, leave, testTag = "measurement_discard")
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SelectedDayLine(
                    selectedDateKey = draft.dateKey,
                    openingDateKey = todayKey,
                    testTag = "measurement_selected_day",
                )
                OutlinedTextField(
                    value = draft.valueText,
                    onValueChange = {
                        draft = draft.copy(valueText = it)
                        error = null
                    },
                    modifier = Modifier.fillMaxWidth().testTag("measurement_value"),
                    label = { Text(words.fieldLabel.resolve()) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        // Decimal keyboards may omit the minus key that signed trackers need.
                        keyboardType = if (measurement.valuePolicy == MeasurementValuePolicy.Positive) KeyboardType.Decimal else KeyboardType.Text,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    isError = error != null,
                )
                // The step decides what the two buttons add and how the chart is spaced, nothing else.
                MomentumPopOutGroup {
                    MomentumStepPopOut(
                        open = stepOpen,
                        onClose = { stepOpen = false },
                        step = formatMetricNumber(measurement.step.value, locale),
                        unit = words.unitBesideTheNumber,
                        label = stringResource(R.string.measurement_settings_step),
                        invalidMessage = stringResource(R.string.measurement_settings_step_invalid, MaximumMeasurementStep, MeasurementStepDecimals),
                        onSave = { typed ->
                            val step = MeasurementStep.parse(typedDecimal(typed))
                            when {
                                step == null -> StepRefusal.Invalid
                                step == measurement.step || onStepChange(step) -> null
                                else -> StepRefusal.NotStored
                            }
                        },
                        testTag = "measurement_step",
                    ) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(-measurement.step.value, measurement.step.value).forEach { delta ->
                                MomentumButton(
                                    text = words.stepButton(delta, locale).resolve(),
                                    onClick = { adjust(delta) },
                                    modifier = Modifier.weight(1f),
                                    variant = MomentumButtonVariant.Secondary,
                                    testTag = if (delta > 0) "measurement_increment" else "measurement_decrement",
                                )
                            }
                            StepEditButton(onClick = { stepOpen = true }, testTag = "measurement_step")
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    key(draft.dateKey) {
                        CompletionButton(
                            actionLabel = words.save.resolve(),
                            completedLabel = words.saved.resolve(),
                            completed = isSaved,
                            onClick = ::save,
                            icon = presentation.icon,
                            testTag = "measurement_save",
                            modifier = Modifier.weight(1f),
                            celebrationSignal = saveSignal,
                        )
                    }
                    MomentumRemoveButton(
                        description = words.remove(dateText(draft.dateKey)).resolve(),
                        resetKey = draft.dateKey,
                        onRemove = ::removeSavedMeasurement,
                        testTag = "measurement_delete",
                        enabled = selectedLog != null,
                    )
                }
                // One line under the buttons carries either the previous value or an error, and it
                // always keeps its space, so neither can push the buttons or the chart.
                val previousText = previousLog?.let { previous ->
                    val day = relativeDateText(previous.dateKey, draft.dateKey)
                    if (selectedLog == null) {
                        words.previous(previous.value, day)
                    } else {
                        words.previousWithChange(previous.value, day, change = selectedLog.value - previous.value)
                    }
                }
                ReservedMessage(
                    text = (error?.let(words::error) ?: previousText)?.resolve(),
                    color = if (error != null) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            MeasurementHistory(
                window = historyWindow,
                selectedDateKey = draft.dateKey,
                onDateSelected = ::requestDate,
                words = words,
                chartSpacing = measurement.chartSpacing,
                selectedValue = selectedLog?.value,
                range = historyRange,
                onRangeChange = { historyRange = it },
                colors = presentation.colors,
            )
            Spacer(Modifier.height(8.dp))
        }

        pendingDateKey?.let { dateKey ->
            DiscardChangesDialog(testTag = "measurement_discard", onDiscard = { loadDate(dateKey) }, onKeep = { pendingDateKey = null })
        }
    }
}

private val MeasurementDraftSaver = listSaver<MeasurementDraft, Any>(
    save = {
        listOf(
            it.dateKey,
            it.valueText,
            it.initialValueText,
            it.originalLog != null,
            it.originalLog?.timestampMillis ?: 0L,
            it.originalLog?.value ?: 0.0,
        )
    },
    restore = {
        MeasurementDraft(
            dateKey = it[0] as String,
            valueText = it[1] as String,
            initialValueText = it[2] as String,
            originalLog = if (it[3] as Boolean) {
                MeasurementRecord(it[0] as String, it[4] as Long, it[5] as Double)
            } else null,
        )
    },
)

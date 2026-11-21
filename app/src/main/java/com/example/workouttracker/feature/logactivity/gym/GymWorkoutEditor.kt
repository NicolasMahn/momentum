package com.example.workouttracker.feature.logactivity.gym

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.workouttracker.R
import com.example.workouttracker.core.text.resolve
import com.example.workouttracker.core.format.formatMetricNumber
import com.example.workouttracker.core.locale.currentLocale
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import com.example.workouttracker.core.ui.MomentumButton
import com.example.workouttracker.core.ui.MomentumButtonDefaults
import com.example.workouttracker.core.ui.MomentumButtonVariant
import com.example.workouttracker.core.ui.MomentumPopOutGroup
import com.example.workouttracker.core.ui.MomentumStepPopOut
import com.example.workouttracker.core.ui.StepRefusal
import com.example.workouttracker.core.time.currentHabitDate
import com.example.workouttracker.domain.activity.ActivityEdit
import com.example.workouttracker.domain.activity.applyingActivityEdits
import com.example.workouttracker.feature.settings.ActivitySettingsDraft
import com.example.workouttracker.core.ui.ReservedMessage
import com.example.workouttracker.domain.AppState
import com.example.workouttracker.domain.activity.ActivityConfiguration
import com.example.workouttracker.domain.activity.ListedActivity
import com.example.workouttracker.feature.logactivity.ActivityMark
import com.example.workouttracker.feature.logactivity.lookOf

/** The editor of [activity]'s session of [selectedDay], a day of its routine, on [dateKey]. */
@Composable
internal fun GymWorkoutEditor(
    configuration: ActivityConfiguration,
    activity: ListedActivity,
    selectedDay: String,
    dateKey: String,
    durationMinutes: Int?,
    durationIsValid: Boolean,
    state: AppState,
    onStateChange: (AppState) -> Boolean,
    onFinished: (Int) -> Unit,
) {
    val day = activity.day(selectedDay) ?: return
    val locale = currentLocale()
    val gymLook = configuration.lookOf(ActivityMark.RoutineDay(activity.id, selectedDay))
    // The exercises as the session had them when the editor opened, with the draft: a set saved or
    // a step changed while editing changes neither the rows, their order nor what is typed.
    val exercises = remember(activity.id, day.id, dateKey) { state.sessionExercises(activity, day, dateKey) }
    // What the last workout before the open day was: neither the one being edited nor a later one
    // when an earlier day is filled in. It only changes with the history, so typing a set does not
    // sort the history again.
    val lastSummaries = remember(state.sessions, exercises, dateKey) {
        val earlier = state.copy(sessions = state.sessions.filter { it.dateKey < dateKey })
        latestWorkoutSummaries(earlier, exercises)
    }
    var draft by remember(activity.id, day.id, dateKey) { mutableStateOf(createDraft(activity, day, exercises, state, dateKey, locale)) }
    var finishError by remember(selectedDay, dateKey) { mutableStateOf<Int?>(null) }
    var isFinishing by remember(selectedDay, dateKey) { mutableStateOf(false) }

    fun persistCompletedExerciseDraft(exercise: SessionExercise, candidate: WorkoutDraft): Boolean {
        val exerciseDrafts = candidate.setsByExercise[exercise.id].orEmpty()
        if (exerciseDrafts.any { it.repsText.isNotBlank() && it.toLoggedSetOrNull(exercise.weighted) == null }) {
            return false
        }
        val completedSets = exerciseDrafts.mapNotNull { it.toLoggedSetOrNull(exercise.weighted) }
        val updatedState = state.withReplacedWorkoutSets(
            activity = activity,
            day = day,
            exercise = exercise,
            sets = completedSets,
            todayKey = dateKey,
        )
        return onStateChange(updatedState)
    }

    fun finishWorkout() {
        isFinishing = true
        if (!durationIsValid) {
            finishError = R.string.activity_gym_finish_duration_invalid
            isFinishing = false
            return
        }
        fun rows(exercise: SessionExercise) = draft.setsByExercise[exercise.id].orEmpty()
        fun savedSets(exercise: SessionExercise) = state.sessionFor(activity, day, dateKey)?.exerciseLogs?.firstOrNull { it.exerciseId == exercise.id }?.sets.orEmpty()
        val attemptedInvalidSet = exercises.any { exercise ->
            rows(exercise).any { setDraft -> setDraft.repsText.isNotBlank() && setDraft.toLoggedSetOrNull(exercise.weighted) == null }
        }
        if (attemptedInvalidSet) {
            finishError = R.string.activity_gym_finish_invalid_set
            isFinishing = false
            return
        }
        val completedSetCount = exercises.sumOf { exercise -> rows(exercise).count { it.toLoggedSetOrNull(exercise.weighted) != null } }
        if (completedSetCount == 0) {
            finishError = R.string.activity_gym_finish_no_sets
            isFinishing = false
            return
        }
        // A recorded session finished untouched writes nothing: its rows still hold the sets they
        // were recorded as.
        val updatedState = exercises.fold(state) { candidateState, exercise ->
            val completedSets = rows(exercise).mapNotNull { it.toLoggedSetOrNull(exercise.weighted) }
            if (completedSets == savedSets(exercise)) {
                candidateState
            } else {
                candidateState.withReplacedWorkoutSets(
                    activity = activity,
                    day = day,
                    exercise = exercise,
                    sets = completedSets,
                    todayKey = dateKey,
                )
            }
        }.withWorkoutDuration(activity, day, dateKey, durationMinutes)
        if (updatedState == state || onStateChange(updatedState)) {
            finishError = null
            val savedSetCount = updatedState.sessions
                .firstOrNull { it.dateKey == dateKey && it.activityId == activity.id && it.day == selectedDay }
                ?.totalLoggedSets
                ?: 0
            onFinished(savedSetCount)
        } else {
            finishError = R.string.activity_gym_finish_failed
            isFinishing = false
        }
    }

    // One exercise's step is open at a time, and Back closes it before it leaves the editor.
    MomentumPopOutGroup {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            exercises.forEachIndexed { index, exercise ->
                // Exercises are sections, as a schedule's times are: a hairline where the next begins.
                if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                val lastWorkoutSummary = lastSummaries[exercise.id]
                ExerciseEntryCard(
                    exercise = exercise,
                    lastWorkoutSummary = lastWorkoutSummary,
                    drafts = draft.setsByExercise[exercise.id].orEmpty(),
                    onSetChange = { index, updated ->
                        draft = draft.updateSet(exercise.id, index, updated)
                    },
                    onFillLast = {
                        val candidate = draft.fillExerciseWith(
                            exercise,
                            requireNotNull(lastWorkoutSummary).asLoggedSet(),
                            locale,
                        )
                        if (persistCompletedExerciseDraft(exercise, candidate)) {
                            draft = candidate
                            true
                        } else {
                            false
                        }
                    },
                    onCommitSet = { index ->
                        if (draft.setsByExercise[exercise.id]?.getOrNull(index) == null) {
                            false
                        } else {
                            persistCompletedExerciseDraft(exercise, draft)
                        }
                    },
                    onAdjustWeight = { delta ->
                        val candidate = draft.adjustExerciseWeight(exercise.id, delta, locale)
                        if (persistCompletedExerciseDraft(exercise, candidate)) {
                            draft = candidate
                            true
                        } else {
                            false
                        }
                    },
                    // The step as stored now, not as the editor opened with it.
                    stepKg = state.activity?.routineExercise(exercise.id)?.weight?.stepKg,
                    onSaveStep = { typed -> state.savingStep(exercise.id, typed, onStateChange) },
                    shouldCommitOnBlur = { !isFinishing },
                    onFieldFocused = { isFinishing = false },
                    progressContainerColor = gymLook.containerColor,
                    progressContentColor = gymLook.contentColor,
                )
            }
            ReservedMessage(
                text = finishError?.let { stringResource(it) },
                testTag = "finish_workout_error",
            )
            MomentumButton(
                text = stringResource(R.string.activity_gym_finish),
                onClick = ::finishWorkout,
                modifier = Modifier.fillMaxWidth(),
                testTag = "finish_workout",
            )
        }
    }
}

/**
 * One exercise's sets with its presets. What the card offers follows what the exercise has: the
 * routine's weight gives the kg field, "−" and "+" by its step with the pencil that changes it,
 * and "Increase weight" by its rep range, while an exercise done without one shows reps alone; an
 * earlier session or a starting set gives "Last"; an exercise the routine never knew keeps its
 * rows and says so. [stepKg] is the routine's step as stored now, and [onSaveStep] stores a typed one.
 */
@Composable
private fun ExerciseEntryCard(
    exercise: SessionExercise,
    lastWorkoutSummary: ExerciseWorkoutSummary?,
    drafts: List<SetDraft>,
    onSetChange: (Int, SetDraft) -> Unit,
    onFillLast: () -> Boolean,
    onCommitSet: (Int) -> Boolean,
    onAdjustWeight: (Double) -> Boolean,
    stepKg: Double?,
    onSaveStep: (String) -> StepRefusal?,
    shouldCommitOnBlur: () -> Boolean,
    onFieldFocused: () -> Unit,
    progressContainerColor: Color,
    progressContentColor: Color,
) {
    val filledSets = drafts.count { it.toLoggedSetOrNull(exercise.weighted) != null }
    val focusManager = LocalFocusManager.current
    val coroutineScope = rememberCoroutineScope()
    val repFocusRequesters = remember(drafts.size) { List(drafts.size) { FocusRequester() } }
    var saveError by remember { mutableStateOf<Int?>(null) }
    val locale = currentLocale()
    val configured = exercise.configured
    val step = configured?.weight?.let { stepKg ?: it.stepKg }
    val quickStep = step?.let { formatMetricNumber(it, locale) }
    var stepOpen by remember { mutableStateOf(false) }
    val suppressRepsBlurCommit = remember { mutableSetOf<Int>() }

    fun commit(index: Int, advance: Boolean) {
        if (onCommitSet(index)) {
            saveError = null
            if (advance) {
                suppressRepsBlurCommit += index
                repFocusRequesters.getOrNull(index + 1)?.requestFocus() ?: focusManager.clearFocus()
            }
        } else {
            saveError = R.string.activity_gym_save_set_failed
        }
    }

    fun commitAfterFocusChange(index: Int) {
        coroutineScope.launch {
            yield()
            if (shouldCommitOnBlur()) commit(index, advance = false)
        }
    }

    fun runPreset(action: () -> Boolean, @StringRes failure: Int) {
        saveError = if (action()) null else failure
    }

    // On the page itself, as every section is; the hairline before the next exercise keeps its set rows apart.
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        // Tight: the presets' row and every field's floating label bring their own room.
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = exercise.label,
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    text = if (configured != null) {
                        pluralStringResource(
                            R.plurals.activity_gym_exercise_target,
                            configured.sets,
                            configured.sets,
                            configured.reps.first,
                            configured.reps.last,
                        )
                    } else {
                        stringResource(R.string.activity_gym_exercise_not_in_plan)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            GymInfoPill(
                text = stringResource(R.string.activity_gym_progress, filledSets, exercise.rows),
                containerColor = progressContainerColor,
                contentColor = progressContentColor,
            )
        }

        // The last workout's average, or the routine's starting set until there is one; "Increase
        // weight" only after an earlier session reached the top of the rep range with a weight.
        if (lastWorkoutSummary != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(
                        if (lastWorkoutSummary.fromHistory) R.string.activity_gym_last_average else R.string.activity_gym_start_set,
                        formatAverageWorkoutSummary(lastWorkoutSummary).resolve(),
                    ),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (shouldIncreaseWeight(lastWorkoutSummary, exercise)) {
                    GymInfoPill(
                        text = stringResource(R.string.activity_gym_increase_weight),
                        containerColor = IncreaseWeightAccent.containerColor,
                        contentColor = IncreaseWeightAccent.contentColor,
                    )
                }
            }
        }

        val presets = listOfNotNull(
            lastWorkoutSummary?.let {
                Preset(
                    label = stringResource(R.string.activity_gym_preset_last),
                    icon = Icons.Rounded.History,
                    // The button shows a short label, so the full action lives here.
                    iconDescription = stringResource(R.string.activity_gym_preset_last_description),
                    testTag = "workout_${exercise.id}_use_last",
                ) { runPreset(onFillLast, R.string.activity_gym_save_sets_failed) }
            },
        ) + if (step != null && quickStep != null) {
            listOf(
                Preset(
                    label = stringResource(R.string.unit_kg, quickStep),
                    icon = Icons.Rounded.Remove,
                    iconDescription = stringResource(R.string.activity_gym_weight_down, quickStep),
                    testTag = "workout_${exercise.id}_weight_down",
                ) { runPreset({ onAdjustWeight(-step) }, R.string.activity_gym_weight_change_failed) },
                Preset(
                    label = stringResource(R.string.unit_kg, quickStep),
                    icon = Icons.Rounded.Add,
                    iconDescription = stringResource(R.string.activity_gym_weight_up, quickStep),
                    testTag = "workout_${exercise.id}_weight_up",
                ) { runPreset({ onAdjustWeight(step) }, R.string.activity_gym_weight_change_failed) },
                Preset(
                    label = null,
                    icon = Icons.Rounded.Edit,
                    iconDescription = stringResource(R.string.core_step_edit),
                    testTag = "workout_${exercise.id}_step",
                ) { stepOpen = true },
            )
        } else {
            emptyList()
        }

        if (step != null && quickStep != null) {
            MomentumStepPopOut(
                open = stepOpen,
                onClose = { stepOpen = false },
                step = quickStep,
                unit = stringResource(R.string.activity_gym_weight_field),
                label = stringResource(R.string.measurement_settings_step),
                invalidMessage = stringResource(R.string.activity_settings_step_invalid),
                onSave = onSaveStep,
                testTag = "workout_${exercise.id}_step",
            ) { PresetRows(presets) }
        } else if (presets.isNotEmpty()) {
            PresetRows(presets)
        }

        drafts.forEachIndexed { index, draft ->
            var repsHadFocus by remember { mutableStateOf(false) }
            var weightHadFocus by remember { mutableStateOf(false) }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "${index + 1}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.width(22.dp),
                )
                // A recorded weight on an exercise done without one now stays in the row
                // unseen, and is written back as it was when the reps are retyped.
                if (exercise.weighted) {
                    OutlinedTextField(
                        value = draft.weightText,
                        onValueChange = { onSetChange(index, draft.copy(weightText = it)) },
                        label = { Text(stringResource(R.string.activity_gym_weight_field)) },
                        singleLine = true,
                        textStyle = NumberEntryStyle(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Decimal,
                            imeAction = ImeAction.Next,
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("workout_${exercise.id}_weight_$index")
                            .onFocusChanged { focusState ->
                                if (weightHadFocus && !focusState.isFocused) {
                                    commitAfterFocusChange(index)
                                }
                                weightHadFocus = focusState.isFocused
                                if (focusState.isFocused) onFieldFocused()
                            },
                    )
                }
                OutlinedTextField(
                    value = draft.repsText,
                    onValueChange = { onSetChange(index, draft.copy(repsText = it)) },
                    label = { Text(stringResource(R.string.activity_gym_reps_field)) },
                    singleLine = true,
                    textStyle = NumberEntryStyle(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = if (index == drafts.lastIndex) ImeAction.Done else ImeAction.Next,
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { commit(index, advance = true) },
                        onDone = { commit(index, advance = true) },
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("workout_${exercise.id}_reps_$index")
                        .focusRequester(repFocusRequesters[index])
                        .onFocusChanged { focusState ->
                            if (repsHadFocus && !focusState.isFocused) {
                                if (index in suppressRepsBlurCommit) {
                                    suppressRepsBlurCommit -= index
                                } else if (shouldCommitOnBlur()) {
                                    commit(index, advance = false)
                                }
                            }
                            repsHadFocus = focusState.isFocused
                            if (focusState.isFocused) onFieldFocused()
                        },
                )
            }
        }
        // A preset that failed to save says so under the sets it rewrote. One line per exercise,
        // so its tag is unique, and kept free at the section's end, where it is the space before
        // the next exercise rather than a gap inside this one.
        ReservedMessage(
            text = saveError?.let { stringResource(it) },
            testTag = "workout_save_error_${exercise.id}",
        )
    }
}

/** What the user typed is the point of a set row, so entered numbers carry the heaviest weight. */
@Composable
private fun NumberEntryStyle() =
    LocalTextStyle.current.copy(fontWeight = FontWeight.ExtraBold)

/**
 * One quick action on an exercise, as its button says it: [label], after [icon] where it has one,
 * which TalkBack hears as [iconDescription]. Without a label it is the icon alone, in a square.
 */
private class Preset(
    val label: String?,
    val testTag: String,
    val icon: ImageVector? = null,
    val iconDescription: String? = null,
    val onClick: () -> Unit,
)

/**
 * An exercise's presets as one strip while every label fits its column whole: the labelled ones in
 * equal columns, an icon alone in a square. Where a label would be cut, as with German labels on
 * a 320 dp phone, two rows of two. Decided from the widest preset, measured with its padding and
 * its icon, so no column is ever narrower than what it says.
 */
@Composable
private fun PresetRows(presets: List<Preset>) {
    val measurer = rememberTextMeasurer()
    val style = MaterialTheme.typography.labelMedium
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val labelled = presets.filter { it.label != null }
        val widest = labelled.maxOfOrNull { preset ->
            val text = with(density) { measurer.measure(preset.label.orEmpty(), style).size.width.toDp() }
            text + (if (preset.icon != null) PresetIconSize + PresetIconGap else 0.dp) + PresetPadding * 2
        } ?: 0.dp
        val squares = presets.size - labelled.size
        val fitsOneRow = widest * labelled.size + PresetSquare * squares + PresetGap * (presets.size - 1) <= maxWidth
        val perRow = if (fitsOneRow) presets.size else PresetsAtTheNarrowest
        Column(verticalArrangement = Arrangement.spacedBy(PresetGap)) {
            presets.chunked(perRow).forEach { row ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(PresetGap)) {
                    row.forEach { QuickPreset(it) }
                    repeat(perRow - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

private const val PresetsAtTheNarrowest = 2
private val PresetGap = 6.dp
private val PresetIconSize = 16.dp
private val PresetIconGap = 3.dp
private val PresetSquareIconSize = 20.dp

/** The side of a preset that is an icon alone: as wide as a raised button's face is tall. */
private val PresetSquare = MomentumButtonDefaults.MinHeight

/** What a button keeps clear on either side of its content. */
private val PresetPadding = 10.dp

/**
 * One quick action on an exercise's sets, raised as every button is, so the row reads as the
 * things on the page that can be pressed.
 */
@Composable
private fun RowScope.QuickPreset(preset: Preset) {
    MomentumButton(
        onClick = preset.onClick,
        modifier = if (preset.label == null) Modifier.width(PresetSquare) else Modifier.weight(1f),
        variant = MomentumButtonVariant.Secondary,
        testTag = preset.testTag,
        contentPadding = PaddingValues(horizontal = if (preset.label == null) 0.dp else PresetPadding),
    ) {
        if (preset.icon != null) {
            Icon(
                imageVector = preset.icon,
                contentDescription = preset.iconDescription,
                modifier = Modifier.size(if (preset.label == null) PresetSquareIconSize else PresetIconSize),
            )
        }
        if (preset.label != null) {
            if (preset.icon != null) Spacer(Modifier.width(PresetIconGap))
            Text(
                text = preset.label,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

/**
 * [typed] stored as the step of the routine's exercise [exerciseId] through [store], or why not.
 * The step is a rule of the routine, the same number Settings shows, so it counts for every later
 * workout; it judges no day, so the habit day it is applied on changes nothing.
 */
private fun AppState.savingStep(exerciseId: String, typed: String, store: (AppState) -> Boolean): StepRefusal? {
    val stepKg = ActivitySettingsDraft.typedStepKg(typed) ?: return StepRefusal.Invalid
    val exercise = activity?.routineExercise(exerciseId) ?: return StepRefusal.NotStored
    if (exercise.removed) return StepRefusal.NotStored
    val weight = exercise.weight ?: return StepRefusal.NotStored
    val edit = ActivityEdit.SetExerciseNumbers(exerciseId, exercise.sets, exercise.reps, weight.copy(stepKg = stepKg), exercise.startReps)
    val updated = applyingActivityEdits(listOf(edit), currentHabitDate())
    return if (updated == this || store(updated)) null else StepRefusal.NotStored
}

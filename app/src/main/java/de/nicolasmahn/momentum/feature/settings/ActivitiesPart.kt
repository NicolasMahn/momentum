package de.nicolasmahn.momentum.feature.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import de.nicolasmahn.momentum.core.ui.MomentumTextField
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import de.nicolasmahn.momentum.R
import de.nicolasmahn.momentum.core.format.formatMetricNumber
import de.nicolasmahn.momentum.core.locale.currentLocale
import de.nicolasmahn.momentum.core.ui.MomentumButton
import de.nicolasmahn.momentum.core.ui.MomentumButtonLabel
import de.nicolasmahn.momentum.core.ui.MomentumButtonVariant
import de.nicolasmahn.momentum.core.ui.MomentumCheckboxRow
import de.nicolasmahn.momentum.core.ui.MomentumDisclosure
import de.nicolasmahn.momentum.core.ui.MomentumPopOut
import de.nicolasmahn.momentum.core.ui.MomentumRemoveButton
import de.nicolasmahn.momentum.domain.activity.ActivityConfiguration
import de.nicolasmahn.momentum.domain.activity.ListedActivity
import de.nicolasmahn.momentum.domain.activity.NamedActivityIds
import de.nicolasmahn.momentum.domain.activity.routine.ExercisesADay
import de.nicolasmahn.momentum.domain.activity.routine.RoutineDay
import de.nicolasmahn.momentum.domain.activity.routine.RoutineDays
import de.nicolasmahn.momentum.domain.activity.routine.RoutineExercise
import de.nicolasmahn.momentum.domain.activity.routine.SetsAnExercise
import de.nicolasmahn.momentum.feature.logactivity.route.RouteRecordingService
import de.nicolasmahn.momentum.feature.settings.ActivitySettingsDraft.Companion.activityKey
import de.nicolasmahn.momentum.feature.settings.ActivitySettingsDraft.Companion.dayKey
import de.nicolasmahn.momentum.feature.settings.ActivitySettingsDraft.Companion.exerciseKey
import de.nicolasmahn.momentum.feature.settings.ActivitySettingsDraft.Companion.numberKey
import de.nicolasmahn.momentum.feature.logactivity.ActivityMark
import de.nicolasmahn.momentum.feature.logactivity.lookOf
import java.util.Locale

/**
 * Where Activities stands: its list; a new activity being made, first its name and icon, then
 * what it comes with, a routine for now; one activity's page; or one routine day's page under its
 * activity. It is kept as text, its [path], `new`, `new/<id>`, `activity/<id>` or
 * `activity/<id>/day/<dayId>`, a day reached while making its activity `new/<id>/day/<dayId>`,
 * the list's being empty, so it survives the process with the page.
 */
internal sealed interface ActivitiesLevel {
    val path: String

    /** Where Back goes from here: the list from an activity, the activity from its day. */
    val up: ActivitiesLevel

    data object List : ActivitiesLevel {
        override val path: String get() = ""
        override val up: ActivitiesLevel get() = this
    }

    data object New : ActivitiesLevel {
        override val path: String get() = "new"
        override val up: ActivitiesLevel get() = List
    }

    /** The second step of making [activityId]: what it comes with. Done, or the way up, ends it on the list. */
    data class Extras(val activityId: String) : ActivitiesLevel {
        override val path: String get() = "new/$activityId"
        override val up: ActivitiesLevel get() = List
    }

    data class Activity(val activityId: String) : ActivitiesLevel {
        override val path: String get() = "activity/$activityId"
        override val up: ActivitiesLevel get() = List
    }

    /** A routine day's page; one opened while its activity is being made ([settingUp]) goes up to that step. */
    data class Day(val activityId: String, val dayId: String, val settingUp: Boolean = false) : ActivitiesLevel {
        override val path: String get() = if (settingUp) "new/$activityId/day/$dayId" else "activity/$activityId/day/$dayId"
        override val up: ActivitiesLevel get() = if (settingUp) Extras(activityId) else Activity(activityId)
    }

    /** Whether this level is a step of making an activity, where the bar holds that step's button. */
    val makesAnActivity: Boolean get() = this == New || this is Extras || (this is Day && settingUp)

    companion object {
        /** The level [path] names, or the list for a path that names none. */
        fun of(path: String): ActivitiesLevel {
            val parts = path.split('/')
            return when {
                path == New.path -> New
                parts.size == 2 && parts[0] == "new" -> Extras(parts[1])
                parts.size == 2 && parts[0] == "activity" -> Activity(parts[1])
                parts.size == 4 && parts[0] in setOf("activity", "new") && parts[2] == "day" -> Day(parts[1], parts[3], settingUp = parts[0] == "new")
                else -> List
            }
        }
    }
}

/** This level where [view] still offers what it names; the list where it does not. */
private fun ActivitiesLevel.shownIn(view: ActivityConfiguration): ActivitiesLevel {
    fun offered(activityId: String) = view.activity(activityId)?.takeUnless { it.removed }
    val stands = when (this) {
        ActivitiesLevel.List, ActivitiesLevel.New -> true
        is ActivitiesLevel.Activity -> offered(activityId) != null
        is ActivitiesLevel.Extras -> offered(activityId) != null
        is ActivitiesLevel.Day -> offered(activityId)?.routine?.retainedDays?.any { it.id == dayId } == true
    }
    return if (stands) this else ActivitiesLevel.List
}

/**
 * What Activity is made of, over [view] as [draft] is changing it, at [level]: the activities a
 * person lists ([ActivityList]), one activity's page ([ActivityPage]) or one routine day's
 * ([RoutineDayPage]), each moving to another through [onLevel]. A level naming what the draft no
 * longer offers shows the list. While a page is open, Back goes up one level: composed inside the
 * screen, this handler is registered after the app's and so answers first, and the header's arrow
 * goes through it as the system's Back does. A page also opens with the way up, named by where it
 * leads ([UpButton]), so a day's page says whose day it is. Removing an activity goes to the list,
 * removing a day to its activity, and a day added opens on its page, where its exercise waits for
 * a name.
 */
@Composable
internal fun ActivitiesPart(
    view: ActivityConfiguration,
    draft: ActivitySettingsDraft,
    named: NamedActivityIds,
    level: ActivitiesLevel,
    onLevel: (ActivitiesLevel) -> Unit,
    testTag: String,
) {
    val shown = level.shownIn(view)
    BackHandler(enabled = shown != ActivitiesLevel.List) { onLevel(shown.up) }
    when (shown) {
        ActivitiesLevel.List -> ActivityList(view = view, draft = draft, testTag = testTag, onNew = { onLevel(ActivitiesLevel.New) }) { onLevel(ActivitiesLevel.Activity(it)) }
        ActivitiesLevel.New -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            UpButton(to = stringResource(R.string.activity_settings_activities_part), testTag = "${testTag}_up") { onLevel(shown.up) }
            NewActivityPage(draft = draft, testTag = testTag)
        }
        is ActivitiesLevel.Extras -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            UpButton(to = stringResource(R.string.activity_settings_activities_part), testTag = "${testTag}_up") { onLevel(shown.up) }
            val activity = requireNotNull(view.activity(shown.activityId))
            Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Text(
                    text = stringResource(R.string.activity_settings_added, activity.label),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                RoutineRows(
                    view = view,
                    activity = activity,
                    draft = draft,
                    named = named,
                    rowTag = "${testTag}_activity_${activity.id}",
                    testTag = testTag,
                    onOpenDay = { onLevel(ActivitiesLevel.Day(activity.id, it, settingUp = true)) },
                )
            }
        }
        is ActivitiesLevel.Activity -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            UpButton(to = stringResource(R.string.activity_settings_activities_part), testTag = "${testTag}_up") { onLevel(shown.up) }
            ActivityPage(
                view = view,
                activity = requireNotNull(view.activity(shown.activityId)),
                draft = draft,
                named = named,
                testTag = testTag,
                onOpenDay = { onLevel(ActivitiesLevel.Day(shown.activityId, it)) },
                onRemoved = { onLevel(ActivitiesLevel.List) },
            )
        }
        is ActivitiesLevel.Day -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val activity = requireNotNull(view.activity(shown.activityId))
            UpButton(to = activity.label, testTag = "${testTag}_up") { onLevel(shown.up) }
            RoutineDayPage(
                activity = activity,
                day = requireNotNull(activity.day(shown.dayId)),
                draft = draft,
                named = named,
                testTag = testTag,
                onRemoved = { onLevel(shown.up) },
            )
        }
    }
}

/**
 * The activities a person lists, one pressable row each in the list's order ([SettingsLinkRow]):
 * the activity's icon in its color, its name and a line under it, how many days its routine has
 * or that it has none, which says instead that a field on its pages has something to fix while
 * one does; a quiet button that moves it up, which the first cannot press; and the row opens its
 * page through [onOpen]. The line keeps the height of the longest it may say, so no row grows
 * when it changes and every row stands alike. Under the rows "New activity" opens the page that
 * makes one ([onNew]), and last stands what logging any of them asks for ([DurationSwitch]). A row
 * is tagged `<testTag>_activity_<id>`, its button `…_up`, and "New activity"
 * `<testTag>_new_activity`.
 */
@Composable
private fun ActivityList(view: ActivityConfiguration, draft: ActivitySettingsDraft, testTag: String, onNew: () -> Unit, onOpen: (String) -> Unit) {
    val toFixLine = stringResource(R.string.activity_settings_to_fix)
    val noRoutine = stringResource(R.string.activity_settings_no_routine)
    val lines = listOf(routineSummary(RoutineDays.last), noRoutine, toFixLine)
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            view.retainedActivities.forEachIndexed { index, activity ->
                key(activity.id) {
                    val tag = "${testTag}_activity_${activity.id}"
                    val look = view.lookOf(ActivityMark.Listed(activity.id))
                    val toFix = draft.holdsAnError(activityKey(activity.id))
                    SettingsLinkRow(
                        icon = look.icon,
                        iconTint = look.containerColor,
                        title = activity.label,
                        summary = if (toFix) toFixLine else activity.routine?.takeIf { it.isOffered }?.let { routineSummary(it.retainedDays.size) } ?: noRoutine,
                        summaryIsWarning = toFix,
                        reserving = lines,
                        onClick = { onOpen(activity.id) },
                        testTag = tag,
                        action = { MoveUpButton(name = activity.label, enabled = index > 0, testTag = "${tag}_up") { draft.moveActivityUp(activity.id) } },
                    )
                }
            }
        }
        MomentumButton(
            text = stringResource(R.string.activity_settings_new_activity),
            onClick = onNew,
            modifier = Modifier.fillMaxWidth(),
            variant = MomentumButtonVariant.Secondary,
            icon = Icons.Rounded.Add,
            testTag = "${testTag}_new_activity",
        )
        DurationSwitch(logsDuration = view.logsDuration, testTag = testTag) { draft.logDuration(it) }
    }
}

/**
 * Whether logging an activity asks how long it took, with the line that says what that adds. It is
 * about every activity alike, so it stands under their list and on no activity's page. Tagged
 * `<testTag>_logs_duration`, the line `…_note`.
 */
@Composable
private fun DurationSwitch(logsDuration: Boolean, testTag: String, onChange: (Boolean) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        EditorSwitch(
            label = stringResource(R.string.activity_settings_logs_duration),
            checked = logsDuration,
            testTag = "${testTag}_logs_duration",
            onCheckedChange = onChange,
        )
        EditorNote(stringResource(R.string.activity_settings_logs_duration_note), testTag = "${testTag}_logs_duration_note")
    }
}

/**
 * Making an activity: its name, refused where another activity has it, and its icon, which also
 * gives it its color. Nothing is chosen for the person. Both are held by the draft, so leaving the
 * page keeps them and Save waits on the typed name as on any typed field; Next, which adds it and
 * goes on to what it comes with, is the page's own, in the bar. Tags: the field `<testTag>_add_activity` and an icon `<testTag>_new_icon_<icon>`.
 */
@Composable
private fun NewActivityPage(draft: ActivitySettingsDraft, testTag: String) {
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        MomentumTextField(
            value = draft.newActivityText,
            onValueChange = { draft.typing(ActivitySettingsDraft.NewActivityField, it) },
            label = stringResource(R.string.activity_name_label),
            error = stringResource(R.string.activity_settings_name_taken).takeIf { draft.newActivityTaken },
            modifier = Modifier.fillMaxWidth(),
            testTag = "${testTag}_add_activity",
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
        )
        EditorGroup(stringResource(R.string.checklist_editor_icon)) {
            IconGrid(selected = draft.newActivityIcon, testTag = "${testTag}_new", icons = ActivityIcons, onSelect = draft::chooseNewActivityIcon)
        }
    }
}

/** What a routine of [days] days is, in a line: "Routine · 3 days". */
@Composable
private fun routineSummary(days: Int): String =
    stringResource(R.string.activity_settings_routine) + " · " + pluralStringResource(R.plurals.activity_settings_routine_days, days, days)

/**
 * One activity's page: its name, refused in the field where another activity has it; its icon
 * behind a disclosure whose summary names the icon it wears; whether it is recorded with GPS,
 * while it has no routine; its routine ([RoutineRows]); and last the two-step remove of the
 * activity, which goes back to the list ([onRemoved]). A removal belongs with the thing it
 * removes, so it is here and not on the list's row. Tags follow from the activity's,
 * `<testTag>_activity_<id>`: its field `…_name`, the disclosure `…_icon`, an icon
 * `…_icon_<icon>`, the GPS switch `…_records_route` and the remove `…_remove`.
 */
@Composable
private fun ActivityPage(
    view: ActivityConfiguration,
    activity: ListedActivity,
    draft: ActivitySettingsDraft,
    named: NamedActivityIds,
    testTag: String,
    onOpenDay: (String) -> Unit,
    onRemoved: () -> Unit,
) {
    val tag = "${testTag}_activity_${activity.id}"
    val key = activityKey(activity.id)
    var showingIcon by rememberSaveable(activity.id) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            NameField(
                name = draft.textOf(key) ?: activity.label,
                key = key,
                label = stringResource(R.string.activity_name_label),
                testTag = "${tag}_name",
                onName = { draft.nameActivity(activity.id, it) },
                // The draft keeps what was typed, so the field only ever mirrors it.
                settled = true,
                error = stringResource(R.string.activity_settings_name_taken).takeIf { draft.nameTaken(activity.id) },
                onText = { draft.keepText(key, it) },
            )
            MomentumDisclosure(
                label = stringResource(R.string.checklist_editor_icon),
                expanded = showingIcon,
                onExpandedChange = { showingIcon = it },
                testTag = "${tag}_icon",
                summary = stringResource(activity.icon.labelRes),
            ) {
                Box(Modifier.padding(top = 8.dp)) {
                    IconGrid(selected = activity.icon, testTag = tag, icons = ActivityIcons) { draft.setActivityIcon(activity.id, it) }
                }
            }
        }
        // A routine is what its log is, so an activity with one is not recorded with GPS; nor is
        // anything on a phone without GPS.
        if (!activity.hasRoutine && RouteRecordingService.phoneHasGps(LocalContext.current)) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            EditorSwitch(
                label = stringResource(R.string.activity_settings_records_route),
                checked = activity.recordsRoute,
                testTag = "${tag}_records_route",
                onCheckedChange = { draft.recordRoute(activity.id, it) },
            )
            EditorNote(stringResource(R.string.activity_settings_records_route_note), testTag = "${tag}_records_route_note")
        }
        RoutineRows(view = view, activity = activity, draft = draft, named = named, rowTag = tag, testTag = testTag, onOpenDay = onOpenDay)
        RemoveRow(
            text = stringResource(R.string.checklist_editor_remove, activity.label),
            testTag = "${tag}_remove",
        ) { if (draft.removeActivity(activity.id)) onRemoved() }
    }
}

/**
 * An activity's routine on its page. While it has none, "Add a routine", which adds its first
 * day; with one, under its title, one pressable row per retained day: its name and what it asks,
 * "5 exercises · 14 sets", which says instead that a field on its page has something to fix while
 * one does, keeping the height of the longest it may say, with a quiet button that moves it up;
 * the row opens the day's page ([onOpenDay]). Under them "Add a day", which
 * opens the day it adds, and the two-step remove of the whole routine, which keeps the days for
 * the sessions that name them. Tags follow from [rowTag], the activity's: the button
 * `<rowTag>_add_routine`, a day's row `<rowTag>_day_<dayId>` with `…_up`, and `<rowTag>_add_day`
 * and `<rowTag>_remove_routine`.
 */
@Composable
private fun RoutineRows(
    view: ActivityConfiguration,
    activity: ListedActivity,
    draft: ActivitySettingsDraft,
    named: NamedActivityIds,
    rowTag: String,
    testTag: String,
    onOpenDay: (String) -> Unit,
) {
    val exerciseWord = stringResource(R.string.activity_settings_exercise_name)
    val routine = activity.routine
    // A day's letter follows every day the routine ever had, so no two days are lettered alike.
    val newDayLabel = stringResource(R.string.activity_settings_day_lettered, dayLetter(routine?.days?.size ?: 0))
    fun addDay() {
        if (draft.addRoutineDay(activity.id, newDayLabel, exerciseWord, named)) {
            draft.edited.activity(activity.id)?.routine?.retainedDays?.lastOrNull()?.let { onOpenDay(it.id) }
        }
    }
    if (routine == null || !routine.isOffered) {
        MomentumButton(
            text = stringResource(R.string.activity_settings_add_routine),
            onClick = ::addDay,
            modifier = Modifier.fillMaxWidth(),
            variant = MomentumButtonVariant.Secondary,
            icon = Icons.Rounded.Add,
            testTag = "${rowTag}_add_routine",
        )
        return
    }
    val days = routine.retainedDays
    val toFixLine = stringResource(R.string.activity_settings_to_fix)
    val lines = listOf(daySummary(ExercisesADay.last, ExercisesADay.last * SetsAnExercise.last), toFixLine)
    EditorGroup(stringResource(R.string.activity_settings_routine)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            days.forEachIndexed { index, day ->
                key(day.id) {
                    val tag = "${rowTag}_day_${day.id}"
                    val look = view.lookOf(ActivityMark.RoutineDay(activity.id, day.id))
                    val toFix = draft.holdsAnError(dayKey(activity.id, day.id)) || day.exercises.any { draft.holdsAnError(exerciseKey(activity.id, it.id)) }
                    SettingsLinkRow(
                        icon = look.icon,
                        iconTint = look.containerColor,
                        title = day.label,
                        summary = if (toFix) toFixLine else daySummary(day.retainedExercises.size, day.totalSets),
                        summaryIsWarning = toFix,
                        reserving = lines,
                        onClick = { onOpenDay(day.id) },
                        testTag = tag,
                        action = { MoveUpButton(name = day.label, enabled = index > 0, testTag = "${tag}_up") { draft.moveDayUp(day.id) } },
                    )
                }
            }
        }
        MomentumButton(
            text = stringResource(R.string.activity_settings_add_day),
            onClick = ::addDay,
            modifier = Modifier.fillMaxWidth(),
            variant = MomentumButtonVariant.Secondary,
            icon = Icons.Rounded.Add,
            enabled = days.size < RoutineDays.last,
            testTag = "${rowTag}_add_day",
        )
        RemoveRow(text = stringResource(R.string.activity_settings_remove_routine), testTag = "${rowTag}_remove_routine") { draft.removeRoutine(activity.id) }
    }
}

/** What a day of [exercises] and [sets] asks, in a line: "5 exercises · 14 sets". */
@Composable
private fun daySummary(exercises: Int, sets: Int): String = listOf(
    pluralStringResource(R.plurals.activity_gym_plan_exercises, exercises, exercises),
    pluralStringResource(R.plurals.activity_sets, sets, sets),
).joinToString(" · ")

/** A day's letter by its place among every day the routine ever had: A, B, C, …, and a number once the alphabet is spent. */
private fun dayLetter(index: Int): String = if (index < Alphabet) ('A' + index).toString() else (index + 1).toString()

private const val Alphabet = 26

/**
 * One routine day's page: its name; its exercises as Block 34 built them ([ExerciseEditor]), why
 * a day's last exercise stays and "Add an exercise"; and last the two-step remove of the day,
 * which goes back to its activity ([onRemoved]), disabled with why while the day is its routine's
 * last. Tags follow from [testTag]: the field is `<testTag>_day_<id>_name`, the remove `…_remove`
 * with its line `…_keep_day`, the last exercise's line `…_keep` and the button `…_add_exercise`.
 */
@Composable
private fun RoutineDayPage(activity: ListedActivity, day: RoutineDay, draft: ActivitySettingsDraft, named: NamedActivityIds, testTag: String, onRemoved: () -> Unit) {
    val tag = "${testTag}_day_${day.id}"
    val key = dayKey(activity.id, day.id)
    val exercises = day.retainedExercises
    val last = activity.routine?.retainedDays?.size == 1
    val exerciseWord = stringResource(R.string.activity_settings_exercise_name)
    var typedIn by rememberSaveable(day.id) { mutableStateOf(draft.textOf(key) != "") }
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        NameField(
            name = draft.textOf(key) ?: day.label,
            key = key,
            label = stringResource(R.string.activity_settings_day_name),
            testTag = "${tag}_name",
            onName = { draft.nameDay(day.id, it) },
            settled = true,
            missingShown = typedIn,
            onText = {
                typedIn = true
                draft.keepText(key, it)
            },
        )
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            exercises.forEachIndexed { index, exercise ->
                key(exercise.id) { ExerciseEditor(activity = activity, exercise = exercise, first = index == 0, removable = exercises.size > 1, draft = draft, testTag = testTag) }
            }
            StayNote(stringResource(R.string.activity_settings_last_exercise), shown = exercises.size == 1, testTag = "${tag}_keep")
            MomentumButton(
                text = stringResource(R.string.activity_settings_add_exercise),
                onClick = { draft.addExercise(day.id, exerciseWord, named) },
                modifier = Modifier.fillMaxWidth(),
                variant = MomentumButtonVariant.Secondary,
                icon = Icons.Rounded.Add,
                enabled = exercises.size < ExercisesADay.last,
                testTag = "${tag}_add_exercise",
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            RemoveRow(
                text = stringResource(R.string.checklist_editor_remove, day.label),
                testTag = "${tag}_remove",
                enabled = !last,
            ) { if (draft.removeRoutineDay(day.id)) onRemoved() }
            StayNote(stringResource(R.string.activity_settings_last_day), shown = last, testTag = "${tag}_keep_day")
        }
    }
}

/**
 * One exercise: its name beside the button that moves it up and the two-step remove, and under
 * them its numbers behind a disclosure that reads like "3 sets · 8–10 reps · ±2.5 kg": the sets
 * as a stepper, the rep range, "With weight", and the starting set and, done with a weight, its
 * starting weight and the step of the − and + buttons as typed fields, each saying its rule in its
 * label's place while it holds no number the exercise may have. It stays open while a field holds
 * an error. Tags follow from [testTag]: the exercise is `<testTag>_exercise_<id>`, its field
 * `…_name`, its buttons `…_up` and `…_remove`, the disclosure `…_plan`, the stepper `…_sets`, the
 * checkbox `…_weight` and each number by its own name.
 */
@Composable
private fun ExerciseEditor(activity: ListedActivity, exercise: RoutineExercise, first: Boolean, removable: Boolean, draft: ActivitySettingsDraft, testTag: String) {
    val tag = "${testTag}_exercise_${exercise.id}"
    val key = exerciseKey(activity.id, exercise.id)
    val locale = currentLocale()
    var open by rememberSaveable(exercise.id) { mutableStateOf(false) }
    // One just added waits for its name without calling it missing until something was typed.
    var typedIn by rememberSaveable(exercise.id) { mutableStateOf(draft.textOf(key) != "") }
    val kg = stringResource(R.string.activity_gym_weight_field)
    val refused = ExerciseNumber.entries.any { draft.numberRefused(exercise.id, it) }
    MomentumPopOut(open = open || refused, onClose = { if (!refused) open = false }) {
        Column(modifier = Modifier.testTag(tag), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NameField(
                    name = draft.textOf(key) ?: exercise.label,
                    key = key,
                    label = stringResource(R.string.activity_settings_exercise_name),
                    testTag = "${tag}_name",
                    onName = { draft.nameExercise(exercise.id, it) },
                    modifier = Modifier.weight(1f),
                    settled = true,
                    missingShown = typedIn,
                    onText = {
                        typedIn = true
                        draft.keepText(key, it)
                    },
                )
                MoveUpButton(name = exercise.label, enabled = !first, testTag = "${tag}_up") { draft.moveExerciseUp(exercise.id) }
                MomentumRemoveButton(
                    description = stringResource(R.string.checklist_editor_remove, exercise.label),
                    onRemove = { draft.removeExercise(exercise.id) },
                    testTag = "${tag}_remove",
                    variant = MomentumButtonVariant.Quiet,
                    enabled = removable,
                )
            }
            // An error opens the fold, and the fold stays open once it is fixed: a field shown again
            // with its error must not fold away under the first digit that settles it.
            LaunchedEffect(refused) { if (refused) open = true }
            MomentumDisclosure(
                label = stringResource(R.string.activity_settings_target),
                summary = exerciseSummary(exercise, locale),
                expanded = open || refused,
                onExpandedChange = { if (!refused) open = it },
                testTag = "${tag}_plan",
            ) {
                Column(modifier = Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    NumberStepper(
                        label = stringResource(R.string.activity_settings_sets),
                        value = exercise.sets,
                        range = SetsAnExercise,
                        valueText = exercise.sets.toString(),
                        testTag = "${tag}_sets",
                        onChange = { draft.setSets(exercise.id, it) },
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ExerciseNumberField(activity, exercise, ExerciseNumber.RepsFrom, exercise.reps.first.toString(), stringResource(R.string.activity_settings_reps_from), draft, tag, Modifier.weight(1f))
                        ExerciseNumberField(activity, exercise, ExerciseNumber.RepsTo, exercise.reps.last.toString(), stringResource(R.string.activity_settings_reps_to), draft, tag, Modifier.weight(1f))
                    }
                    val weight = exercise.weight
                    MomentumCheckboxRow(
                        checked = weight != null,
                        onCheckedChange = { draft.setWeighted(exercise.id, it) },
                        testTag = "${tag}_weight",
                    ) {
                        Text(text = stringResource(R.string.activity_settings_with_weight), style = MaterialTheme.typography.bodyLarge)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (weight != null) {
                            ExerciseNumberField(activity, exercise, ExerciseNumber.StartWeight, formatMetricNumber(weight.startKg, locale), stringResource(R.string.activity_settings_start_weight), draft, tag, Modifier.weight(1f), suffix = kg)
                        }
                        ExerciseNumberField(activity, exercise, ExerciseNumber.StartReps, exercise.startReps.toString(), stringResource(R.string.activity_settings_start_reps), draft, tag, Modifier.weight(1f))
                    }
                    if (weight != null) {
                        ExerciseNumberField(activity, exercise, ExerciseNumber.Step, formatMetricNumber(weight.stepKg, locale), stringResource(R.string.measurement_settings_step), draft, tag, Modifier, suffix = kg)
                    }
                }
            }
        }
    }
}

/** What an exercise asks, in one line: its sets, its rep range and, done with a weight, the step of its − and + buttons. */
@Composable
private fun exerciseSummary(exercise: RoutineExercise, locale: Locale): String = listOfNotNull(
    pluralStringResource(R.plurals.activity_sets, exercise.sets, exercise.sets),
    stringResource(R.string.activity_settings_reps_range, exercise.reps.first, exercise.reps.last),
    exercise.weight?.let { stringResource(R.string.activity_settings_step_summary, formatMetricNumber(it.stepKg, locale)) },
).joinToString(" · ")

/**
 * One typed number of an exercise, kept by the draft as typed ([ActivitySettingsDraft.typeNumber]):
 * [text] is the number as the exercise has it, shown while nothing typed waits. Refused text says
 * the rule in the label's place: a count of reps, the other end of the range where that is what
 * refuses it, or the kilograms a weight or a step may be.
 */
@Composable
private fun ExerciseNumberField(
    activity: ListedActivity,
    exercise: RoutineExercise,
    number: ExerciseNumber,
    text: String,
    label: String,
    draft: ActivitySettingsDraft,
    testTag: String,
    modifier: Modifier,
    suffix: String? = null,
) {
    val refused = draft.numberRefused(exercise.id, number)
    val bound = draft.repsBoundRefusing(exercise.id, number)
    TypedPartField(
        text = draft.numberText(exercise.id, number) ?: text,
        key = numberKey(activity.id, exercise.id, number),
        label = label,
        error = when {
            !refused -> null
            bound != null -> stringResource(if (number == ExerciseNumber.RepsFrom) R.string.activity_settings_reps_at_most else R.string.activity_settings_reps_at_least, bound)
            number == ExerciseNumber.StartWeight -> stringResource(R.string.activity_settings_weight_invalid)
            number == ExerciseNumber.Step -> stringResource(R.string.activity_settings_step_invalid)
            else -> stringResource(R.string.activity_settings_reps_invalid)
        },
        // The draft keeps what was typed, so the field only ever mirrors it.
        settled = true,
        testTag = "${testTag}_${number.tag}",
        onType = { draft.typeNumber(exercise.id, number, it) },
        keyboardType = if (suffix == null) KeyboardType.Number else KeyboardType.Decimal,
        modifier = modifier,
        suffix = suffix,
    )
}

/** The way up from a page, named by where it leads, as Back goes: the list from an activity, the activity from its day. */
@Composable
private fun UpButton(to: String, testTag: String, onClick: () -> Unit) {
    val spoken = stringResource(R.string.activity_settings_up, to)
    MomentumButton(
        onClick = onClick,
        variant = MomentumButtonVariant.Quiet,
        testTag = testTag,
        faceModifier = Modifier.semantics { contentDescription = spoken },
    ) {
        Icon(imageVector = Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        // TalkBack hears where it leads in a sentence, so the bare name is not said again.
        Box(Modifier.clearAndSetSemantics {}) { MomentumButtonLabel(to) }
    }
}

/** What a remove does, in words, beside its two-step button: a routine, an activity or a day, each on its own page. */
@Composable
private fun RemoveRow(text: String, testTag: String, enabled: Boolean = true, onRemove: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = text,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        MomentumRemoveButton(description = text, onRemove = onRemove, testTag = testTag, variant = MomentumButtonVariant.Quiet, enabled = enabled)
    }
}

/** The quiet button that moves an activity, a day or an exercise one place up; the first cannot move. */
@Composable
private fun MoveUpButton(name: String, enabled: Boolean, testTag: String, onClick: () -> Unit) {
    SmallIconButton(
        description = stringResource(R.string.activity_settings_move_up, name),
        icon = Icons.Rounded.ArrowUpward,
        enabled = enabled,
        testTag = testTag,
        onClick = onClick,
    )
}

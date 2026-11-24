package de.nicolasmahn.momentum.gallery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import de.nicolasmahn.momentum.core.text.resolve
import de.nicolasmahn.momentum.core.time.parseDateKey
import de.nicolasmahn.momentum.domain.AppState
import de.nicolasmahn.momentum.domain.activity.ActivityConfiguration
import de.nicolasmahn.momentum.domain.activity.ActivityEdit
import de.nicolasmahn.momentum.domain.activity.BoulderingActivityId
import de.nicolasmahn.momentum.domain.activity.CyclingActivityId
import de.nicolasmahn.momentum.domain.activity.GymActivityId
import de.nicolasmahn.momentum.domain.activity.activityRules
import de.nicolasmahn.momentum.domain.activity.applyEdit
import de.nicolasmahn.momentum.domain.activity.nextRoutineDay
import de.nicolasmahn.momentum.domain.activity.routine.ExerciseLog
import de.nicolasmahn.momentum.domain.activity.routine.ExerciseWeight
import de.nicolasmahn.momentum.domain.activity.routine.LoggedSet
import de.nicolasmahn.momentum.domain.activity.routine.RoutineDay
import de.nicolasmahn.momentum.domain.activity.routine.RoutineExercise
import de.nicolasmahn.momentum.domain.activity.routine.WorkoutSession
import de.nicolasmahn.momentum.domain.mo.moMood
import de.nicolasmahn.momentum.domain.settings.FeatureVisibilitySettings
import de.nicolasmahn.momentum.feature.logactivity.ActivityLogScreen
import de.nicolasmahn.momentum.feature.logactivity.ActivityMark
import de.nicolasmahn.momentum.feature.logactivity.ActivityNameField
import de.nicolasmahn.momentum.feature.logactivity.ActivityTileRow
import de.nicolasmahn.momentum.feature.logactivity.OtherActivityChoices
import de.nicolasmahn.momentum.feature.logactivity.activitiesUnderOther
import de.nicolasmahn.momentum.feature.logactivity.gym.GymPlanPicker
import de.nicolasmahn.momentum.feature.logactivity.gym.GymWorkoutEditor
import de.nicolasmahn.momentum.feature.logactivity.tiles
import de.nicolasmahn.momentum.reminder.MoReminderVoice
import de.nicolasmahn.momentum.reminder.activityReminderSubject
import java.time.DayOfWeek
import java.time.LocalDate

/*
 * The Activity screen's parts, each the real component over a configuration the fixture does not
 * have: the gym removed, a long German name, fewer activities, Other open, the day next in turn,
 * a recorded session the routine has moved on from, a routine on an activity other than the gym
 * under its tile and under Other, the duration field Settings can switch on, above Save and above
 * a routine's days, an exercise done without a weight, and the reminder's lines.
 */

internal val activityScreenSpecimens: List<GallerySpecimen> = listOf(
    GallerySpecimen("ActivityTileRow, Gym first") { ActivityTileRowSpecimen(fixtureAppState.activityRules) },
    GallerySpecimen("ActivityTileRow, Gym removed") { ActivityTileRowSpecimen(fixtureAppState.activityRules.withoutGym()) },
    GallerySpecimen("ActivityTileRow, a long German name") { ActivityTileRowSpecimen(fixtureAppState.activityRules.withLongGermanName()) },
    GallerySpecimen("ActivityTileRow, fewer tiles") { ActivityTileRowSpecimen(fixtureAppState.activityRules.withoutGym().withOneActivity()) },
    GallerySpecimen("Other open") { OtherOpenSpecimen(fixtureAppState.activityRules.withLongGermanName()) },
    GallerySpecimen("GymPlanPicker, with the day next in turn") {
        val gym = requireNotNull(fixtureAppState.activityRules.activity(GymActivityId))
        GymPlanPicker(configuration = fixtureAppState.activityRules, activity = gym, onPlanSelected = { _, _ -> }, nextDayId = gym.nextRoutineDay(fixtureAppState.sessions)?.id)
    },
    GallerySpecimen("GymWorkoutEditor, a recorded session the routine moved on from") { RecordedSessionEditorSpecimen() },
    GallerySpecimen("ActivityLogScreen, a routine under Bouldering's tile") { RoutineOnScreenSpecimen(boulderingRoutineState(), BoulderingActivityId) },
    GallerySpecimen("ActivityLogScreen, a routine under Other") {
        val state = boulderingRoutineState().withYogaRoutine()
        RoutineOnScreenSpecimen(state, requireNotNull(state.activityRules.activityNamed(DemoYogaName)).id)
    },
    GallerySpecimen("ActivityLogScreen, asking how long it took, above Save") { RoutineOnScreenSpecimen(fixtureAppState.loggingDurations(), CyclingActivityId) },
    GallerySpecimen("ActivityLogScreen, asking how long it took, above a routine's days") { RoutineOnScreenSpecimen(fixtureAppState.loggingDurations(), GymActivityId) },
    GallerySpecimen("GymWorkoutEditor, an exercise done without a weight beside one done with, after a session") { WeightlessEditorSpecimen() },
    GallerySpecimen("WorkoutReminder, its lines: a planned activity with a routine, one without, any activity") { WorkoutReminderLinesSpecimen() },
)

private const val DemoYogaName = "Yoga"

/**
 * Bouldering holding a routine of one day, a campus board done without a weight beside a
 * hangboard done with one, after a session of it three days ago, so the editor shows a recorded
 * history for each.
 */
private fun boulderingRoutineState(): AppState {
    val session = RoutineDay(
        id = "day_boulder",
        label = "Session",
        exercises = listOf(
            RoutineExercise("ex_campus", "Campus board", sets = 3, reps = 5..8, weight = null, startReps = 6),
            RoutineExercise("ex_hang", "Hangboard", sets = 3, reps = 5..8, weight = ExerciseWeight(startKg = 20.0, stepKg = 2.5), startReps = 6),
        ),
    )
    val earlier = WorkoutSession(
        id = "demo-boulder", activityId = BoulderingActivityId, day = session.id, dateKey = fixtureDateKey(3), timestampMillis = 1L,
        exerciseLogs = listOf(
            ExerciseLog("ex_campus", "Campus board", List(3) { LoggedSet(weightKg = null, reps = 6.0) }),
            ExerciseLog("ex_hang", "Hangboard", List(3) { LoggedSet(20.0, 8.0) }),
        ),
    )
    return fixtureAppState.copy(
        activity = fixtureAppState.activityRules.applyEdit(ActivityEdit.AddRoutineDay(BoulderingActivityId, session), fixtureToday),
        sessions = fixtureAppState.sessions + earlier,
    )
}

/** The demo history's Yoga, listed past the tiles and so under Other, given a routine of one day done without weights. */
private fun AppState.withYogaRoutine(): AppState {
    val yoga = requireNotNull(activityRules.activityNamed(DemoYogaName))
    val flow = RoutineDay("day_flow", "Flow", listOf(RoutineExercise("ex_sun", "Sun salutation", sets = 2, reps = 5..10, weight = null, startReps = 5)))
    return copy(activity = activityRules.applyEdit(ActivityEdit.AddRoutineDay(yoga.id, flow), fixtureToday))
}

/** This state with "Log how long it took" switched on, which the fixture leaves off as a stored configuration does. */
private fun AppState.loggingDurations(): AppState = copy(activity = activityRules.copy(logsDuration = true))

private val fixtureToday: LocalDate get() = requireNotNull(parseDateKey(FixtureTodayKey))

/** The real screen over [initial], opened on [activityId] as a reminder's tap opens it: its tile, or Other with it chosen. */
@Composable
private fun RoutineOnScreenSpecimen(initial: AppState, activityId: String) {
    var state by remember { mutableStateOf(initial) }
    var requested by remember { mutableStateOf<String?>(activityId) }
    ScreenFrame(height = 720.dp) {
        ActivityLogScreen(
            state = state,
            onStateChange = { state = it; true },
            onWorkoutFinished = {},
            todayKey = FixtureTodayKey,
            requestedActivityId = requested,
            onActivityRequestConsumed = { requested = null },
        )
    }
}

/** Bouldering's day today, nothing recorded yet: the campus board's rows read reps alone under its last average, the hangboard's keep their kg field. */
@Composable
private fun WeightlessEditorSpecimen() {
    var state by remember { mutableStateOf(boulderingRoutineState()) }
    GymWorkoutEditor(
        configuration = state.activityRules,
        activity = requireNotNull(state.activityRules.activity(BoulderingActivityId)),
        selectedDay = "day_boulder",
        dateKey = FixtureTodayKey,
        durationMinutes = null,
        durationIsValid = true,
        state = state,
        onStateChange = { state = it; true },
        onFinished = {},
    )
}

/**
 * The workout reminder's words on the fixture's day, in the tone its mood gives Mo: Monday plans
 * the gym, whose routine names the day next in turn; Tuesday plans Bouldering, which has no
 * routine; Thursday plans any activity.
 */
@Composable
private fun WorkoutReminderLinesSpecimen() {
    val state = fixtureAppState
    val mood = remember { moMood(state, FeatureVisibilitySettings(disabledFeatures = emptySet()), fixtureToday) }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        listOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.THURSDAY).forEach { day ->
            val message = MoReminderVoice.speak(activityReminderSubject(state, day), mood, fixtureToday)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(text = message.title.resolve(), style = MaterialTheme.typography.titleMedium)
                Text(text = message.text.resolve(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun ActivityConfiguration.withoutGym() = copy(activities = activities.map { if (it.id == GymActivityId) it.copy(removed = true) else it })

/** A tile with a long German name, which the row must wrap or cut inside it. */
private fun ActivityConfiguration.withLongGermanName() = copy(
    activities = activities.map { if (it.id == "cycling") it.copy(label = "Schwimmtraining am Morgen") else it },
)

private fun ActivityConfiguration.withOneActivity() = copy(activities = activities.filter { it.id == GymActivityId || it.id == activities.first { activity -> activity.id != GymActivityId }.id })

@Composable
private fun ActivityTileRowSpecimen(configuration: ActivityConfiguration) {
    val tiles = configuration.tiles()
    var selected by remember(configuration) { mutableStateOf(tiles.first()) }
    ActivityTileRow(tiles = tiles, configuration = configuration, selected = selected, onSelect = { selected = it })
}

/** Other chosen: the row, every further activity to pick, and the field a pick fills. */
@Composable
private fun OtherOpenSpecimen(configuration: ActivityConfiguration) {
    var selected by remember(configuration) { mutableStateOf<ActivityMark>(ActivityMark.Other) }
    var name by remember(configuration) { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ActivityTileRow(tiles = configuration.tiles(), configuration = configuration, selected = selected, onSelect = { selected = it })
        OtherActivityChoices(
            activities = configuration.activitiesUnderOther(),
            configuration = configuration,
            chosenName = name,
            onChoose = { name = it.label },
        )
        ActivityNameField(value = name, onValueChange = { name = it }, isError = false)
    }
}

/**
 * Day A's session today, recorded with five sets of a three-set exercise, two sets of an exercise
 * the routine has removed since and two of one it never knew, after an earlier session that
 * reached the top of the bench press's rep range.
 */
@Composable
private fun RecordedSessionEditorSpecimen() {
    var state by remember { mutableStateOf(recordedSessionState()) }
    GymWorkoutEditor(
        configuration = state.activityRules,
        activity = requireNotNull(state.activityRules.activity(GymActivityId)),
        selectedDay = "A",
        dateKey = FixtureTodayKey,
        durationMinutes = null,
        durationIsValid = true,
        state = state,
        onStateChange = { state = it; true },
        onFinished = {},
    )
}

private fun recordedSessionState(): AppState {
    val rules = fixtureAppState.activityRules
    val gym = requireNotNull(rules.activity(GymActivityId))
    val routine = requireNotNull(gym.routine)
    val dayA = requireNotNull(gym.day("A"))
    val movedOn = rules.copy(
        activities = rules.activities.map { activity ->
            if (activity.id != GymActivityId) activity
            else activity.copy(routine = routine.copy(days = routine.days.map { day -> if (day.id != "A") day else day.copy(exercises = day.exercises.map { if (it.id == "row") it.copy(removed = true) else it }) }))
        },
    )
    val earlier = WorkoutSession(
        id = "demo-earlier-A", activityId = GymActivityId, day = "A", dateKey = fixtureDateKey(3), timestampMillis = 1L,
        exerciseLogs = listOf(ExerciseLog("bench_press", "Bench Press", List(3) { LoggedSet(22.5, 10.0) })),
    )
    val today = WorkoutSession(
        id = "$FixtureTodayKey-A", activityId = GymActivityId, day = "A", dateKey = FixtureTodayKey, timestampMillis = 2L,
        exerciseLogs = dayA.exercises.map { exercise ->
            ExerciseLog(
                exercise.id, exercise.label,
                when (exercise.id) {
                    "bench_press" -> List(5) { LoggedSet(25.0, 8.0) }
                    "row" -> listOf(LoggedSet(40.0, 10.0), LoggedSet(40.0, 9.0))
                    else -> emptyList()
                },
            )
        } + ExerciseLog("ex_leg_press", "Leg press", listOf(LoggedSet(80.0, 12.0), LoggedSet(85.0, 10.0))),
    )
    return AppState(activity = movedOn, sessions = listOf(today, earlier))
}

package de.nicolasmahn.momentum.gallery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.nicolasmahn.momentum.core.time.parseDateKey
import de.nicolasmahn.momentum.core.ui.ViewTheme
import de.nicolasmahn.momentum.domain.activity.ActivityConfiguration
import de.nicolasmahn.momentum.domain.activity.ActivityEdit
import de.nicolasmahn.momentum.domain.activity.ActivityTokenStep
import de.nicolasmahn.momentum.domain.activity.ActivityTokenSteps
import de.nicolasmahn.momentum.domain.activity.BoulderingActivityId
import de.nicolasmahn.momentum.domain.activity.CyclingActivityId
import de.nicolasmahn.momentum.domain.activity.GymActivityId
import de.nicolasmahn.momentum.domain.activity.JoggingActivityId
import de.nicolasmahn.momentum.domain.activity.ListedActivity
import de.nicolasmahn.momentum.domain.activity.PlannedDay
import de.nicolasmahn.momentum.domain.activity.WeekPlan
import de.nicolasmahn.momentum.domain.activity.activityRules
import de.nicolasmahn.momentum.domain.activity.applyEdit
import de.nicolasmahn.momentum.domain.activity.applyingEdits
import de.nicolasmahn.momentum.domain.activity.routine.ExerciseWeight
import de.nicolasmahn.momentum.domain.activity.routine.Routine
import de.nicolasmahn.momentum.domain.activity.routine.RoutineDay
import de.nicolasmahn.momentum.domain.activity.routine.RoutineExercise
import de.nicolasmahn.momentum.domain.activity.namedActivityIds
import de.nicolasmahn.momentum.domain.checklist.ViewIcon
import de.nicolasmahn.momentum.domain.settings.DashboardFeature
import de.nicolasmahn.momentum.feature.checklist.presentation
import de.nicolasmahn.momentum.feature.checklist.vector
import de.nicolasmahn.momentum.feature.logactivity.ActivityMark
import de.nicolasmahn.momentum.feature.logactivity.lookOf
import de.nicolasmahn.momentum.feature.settings.ActivitiesLevel
import de.nicolasmahn.momentum.feature.settings.ActivitiesPart
import de.nicolasmahn.momentum.feature.settings.ActivitySettingsDraft
import de.nicolasmahn.momentum.feature.settings.ActivitySettingsPage
import de.nicolasmahn.momentum.feature.settings.ActivityStreak
import de.nicolasmahn.momentum.feature.settings.ActivityWeek
import de.nicolasmahn.momentum.feature.settings.ChecklistPage
import de.nicolasmahn.momentum.feature.settings.ExerciseNumber
import de.nicolasmahn.momentum.feature.settings.IconGrid
import de.nicolasmahn.momentum.feature.settings.SettingsLinkRow
import de.nicolasmahn.momentum.feature.settings.SmallIconButton
import java.time.DayOfWeek

/**
 * Activity in Settings: its page on each of its four parts and, on Activities, at each of its
 * levels; Activities on its own with a long list, the duration switched on, an activity without a routine, a name in use on
 * the list and on its page, a refused rep range, a long routine and an exercise done without a
 * weight; Week with something planned on every day; Streak with fewer days offered, at one day a
 * week and with steps; the icon grid's longest labels; and the settings row the list is made of.
 */
internal val activitySettingsSpecimens = listOf(
    GallerySpecimen("ActivitySettingsPage, Look") { ActivitySettingsPageSpecimen(ChecklistPage.Look) },
    GallerySpecimen("ActivitySettingsPage, Activities") { ActivitySettingsPageSpecimen(ChecklistPage.Items) },
    GallerySpecimen("ActivitySettingsPage, Activities, the gym's page") { ActivitySettingsPageSpecimen(ChecklistPage.Items, ActivitiesLevel.Activity(GymActivityId)) },
    GallerySpecimen("ActivitySettingsPage, Activities, the gym's Day A") { ActivitySettingsPageSpecimen(ChecklistPage.Items, ActivitiesLevel.Day(GymActivityId, "A")) },
    GallerySpecimen("ActivitySettingsPage, Week") { ActivitySettingsPageSpecimen(ChecklistPage.Reminders) },
    GallerySpecimen("ActivitySettingsPage, Streak") { ActivitySettingsPageSpecimen(ChecklistPage.Streak) },
    GallerySpecimen("ActivitiesPart, a long list") { ActivitiesPartSpecimen(fixtureAppState.activityRules.withLongList()) },
    GallerySpecimen("ActivitiesPart, logging how long it took switched on") { ActivitiesPartSpecimen(fixtureAppState.activityRules.copy(logsDuration = true)) },
    GallerySpecimen("ActivitiesPart, an activity without a routine") { ActivitiesPartSpecimen(fixtureAppState.activityRules, ActivitiesLevel.Activity(BoulderingActivityId)) },
    GallerySpecimen("ActivitiesPart, a name in use, the list") { ActivitiesPartSpecimen(fixtureAppState.activityRules, refused = true) },
    GallerySpecimen("ActivitiesPart, a name in use, Cycling's page") { ActivitiesPartSpecimen(fixtureAppState.activityRules, ActivitiesLevel.Activity(CyclingActivityId), refused = true) },
    GallerySpecimen("ActivitiesPart, a refused rep range on Day A") { ActivitiesPartSpecimen(fixtureAppState.activityRules, ActivitiesLevel.Day(GymActivityId, "A"), refused = true) },
    GallerySpecimen("ActivitiesPart, a long routine's day") { ActivitiesPartSpecimen(fixtureAppState.activityRules.withLongRoutine(), ActivitiesLevel.Day(GymActivityId, "day_long")) },
    GallerySpecimen("ActivitiesPart, an exercise done without a weight") { ActivitiesPartSpecimen(fixtureAppState.activityRules.withWeightlessExercise(), ActivitiesLevel.Day(GymActivityId, "A")) },
    GallerySpecimen("ActivityWeek, something planned on every day") { ActivityWeekSpecimen(fixtureAppState.activityRules.plannedEveryDay()) },
    GallerySpecimen("ActivityStreak, fewer days offered") { ActivityStreakSpecimen(fixtureAppState.activityRules) },
    GallerySpecimen("ActivityStreak, one day a week") { ActivityStreakSpecimen(fixtureAppState.activityRules, daysAWeek = 1) },
    GallerySpecimen("ActivityStreak, out of the streak since last week") { ActivityStreakSpecimen(fixtureAppState.activityRules, left = true) },
    GallerySpecimen("ActivityStreak, steps at the days and two days more") { ActivityStreakSpecimen(fixtureAppState.activityRules, steps = ActivityTokenSteps(listOf(ActivityTokenStep(0, 1), ActivityTokenStep(1, 2), ActivityTokenStep(2, 1)))) },
    GallerySpecimen("IconGrid, the longest labels on one line in English and German, at full width and at 320 dp") { OneLineIconGridSpecimen() },
    GallerySpecimen("SettingsLinkRow, an icon in a color, a line kept at its tallest, and an action") { SettingsLinkRowSpecimen() },
)

/**
 * Activity's real page, opened on one of its four parts and, on Activities, at [level], over a
 * configuration and reminder settings held here ([BuiltInViewPageSpecimen]): Look; Activities,
 * the fixture's activities, the gym's page or its Day A; Week, its days, what is planned on each
 * and the workout reminder's switch over its draft; and Streak, its steps, its bank's size and its
 * holiday rule with the count fewer days asks. Delete is disabled with its reason, and the one
 * Save stores both.
 */
@Composable
private fun ActivitySettingsPageSpecimen(part: ChecklistPage, level: ActivitiesLevel = ActivitiesLevel.List) {
    val named = remember { fixtureAppState.namedActivityIds }
    BuiltInViewPageSpecimen(
        feature = DashboardFeature.Activity,
        demo = fixtureAppState.activityRules,
        draft = { activity, today -> ActivitySettingsDraft(activity, today) },
        applying = { edits, today -> applyingEdits(edits, today) },
    ) { save, reminders, onSave ->
        ActivitySettingsPage(
            save = save,
            settings = reminders,
            named = named,
            onSave = onSave,
            reminder = { _, _ -> SpecimenReminderRow(save.reminder.enabledIn(reminders), save.reminder::remind) },
            // The specimen is its own page: nothing asks it to leave.
            leaveRequested = false,
            onLeaveAnswered = {},
            onUnsavedWorkChange = {},
            opensOn = part,
            opensAt = level,
        )
    }
}

/**
 * The Activities part on its own over [configuration]'s draft, opened at [opensAt] and moving
 * between its levels as the page's does. [refused]: Cycling's name field holds Jogging's name,
 * which its row on the list says has something to fix, and the bench press's "Reps from" holds 12
 * above its "to" of 10, which keeps the exercise's numbers open on Day A.
 */
@Composable
private fun ActivitiesPartSpecimen(configuration: ActivityConfiguration, opensAt: ActivitiesLevel = ActivitiesLevel.List, refused: Boolean = false) {
    val today = requireNotNull(parseDateKey(FixtureTodayKey))
    val named = remember { fixtureAppState.namedActivityIds }
    var level by remember { mutableStateOf(opensAt) }
    val draft = remember {
        ActivitySettingsDraft(configuration, today).also { draft ->
            if (refused) {
                draft.typeNumber("bench_press", ExerciseNumber.RepsFrom, "12")
                draft.keepText(ActivitySettingsDraft.activityKey(CyclingActivityId), "Jogging")
                draft.nameActivity(CyclingActivityId, "Jogging")
            }
        }
    }
    ViewTheme(draft.edited.presentation().colors) {
        ActivitiesPart(view = draft.edited, draft = draft, named = named, level = level, onLevel = { level = it }, testTag = "gallery_activity")
    }
}

/** The Week part on its own over [configuration]'s draft, with a switch standing in for Settings' reminder row. */
@Composable
private fun ActivityWeekSpecimen(configuration: ActivityConfiguration) {
    val today = requireNotNull(parseDateKey(FixtureTodayKey))
    val draft = remember { ActivitySettingsDraft(configuration, today) }
    var remind by remember { mutableStateOf(true) }
    ViewTheme(draft.edited.presentation().colors) {
        ActivityWeek(view = draft.edited, draft = draft, testTag = "gallery_activity") { SpecimenReminderRow(remind) { remind = it } }
    }
}

/**
 * The Streak part on its own: the switch, the steps a week earns tokens by in days, one at the
 * week's days unless [steps] lists more, the bank's size and the holiday rule, fewer days with its
 * count while the week asks more than one day. [left]: Activity left the streak a week ago, so the
 * switch is off and only the holiday rule stays.
 */
@Composable
private fun ActivityStreakSpecimen(configuration: ActivityConfiguration, daysAWeek: Int? = null, steps: ActivityTokenSteps? = null, left: Boolean = false) {
    val today = requireNotNull(parseDateKey(FixtureTodayKey))
    val draft = remember {
        val edits = listOfNotNull(
            daysAWeek?.let { ActivityEdit.SetWeek(WeekPlan.of(*DayOfWeek.entries.take(it).toTypedArray())) },
            steps?.let { ActivityEdit.SetTokenSteps(it) },
        )
        val edited = configuration.applyingEdits(edits, today.minusWeeks(1))
        ActivitySettingsDraft(if (left) edited.applyEdit(ActivityEdit.LeaveRewards, today.minusWeeks(1), firstRecordedDay = today.minusWeeks(8)) else edited, today)
    }
    ViewTheme(draft.edited.presentation().colors) {
        ActivityStreak(view = draft.edited, draft = draft, testTag = "gallery_activity")
    }
}

/**
 * The icon grid's tiles with the longest labels either language has, each on one line, shrunk
 * where the tile is too narrow: in English and in German, at the gallery's width and in the width
 * a 320 dp phone leaves a page. Every tile is one size, in every row.
 */
@Composable
private fun OneLineIconGridSpecimen() {
    val longest = listOf(
        ViewIcon.MartialArts, ViewIcon.Skateboarding, ViewIcon.Basketball, ViewIcon.Bouldering,
        ViewIcon.Swimming, ViewIcon.Skiing, ViewIcon.Language, ViewIcon.Mood,
    )
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        listOf("en", "de").forEach { language ->
            InLanguage(language) {
                IconGrid(selected = ViewIcon.Bouldering, testTag = "gallery_icons_$language", icons = longest) {}
                Box(Modifier.width(288.dp)) {
                    IconGrid(selected = ViewIcon.Bouldering, testTag = "gallery_icons_narrow_$language", icons = longest) {}
                }
            }
        }
    }
}

/** Eight activities and a gym routine of five days, the first with twelve exercises under long names, as a person who lists everything would have it. */
private fun ActivityConfiguration.withLongRoutine(): ActivityConfiguration {
    fun exercise(index: Int) = RoutineExercise("ex_long_$index", "Bulgarian split squat with dumbbells, rear foot raised $index", sets = 4, reps = 8..12, weight = ExerciseWeight(12.5, 1.0), startReps = 10)
    val longDay = RoutineDay("day_long", "Legs, glutes and core", (1..12).map(::exercise))
    val more = (1..4).map { ListedActivity("act_more_$it", "Activity number $it with a long name", ViewIcon.Bolt) }
    val longer = activities.map { activity ->
        if (activity.id != GymActivityId) activity
        else activity.copy(routine = Routine(listOf(longDay) + activity.routine?.days.orEmpty() + RoutineDay("day_e", "Day E", listOf(exercise(13)))))
    }
    return copy(activities = longer + more)
}

/** Twelve activities, the first four the fixture's, under names as long as a person may give them, two with a routine. */
private fun ActivityConfiguration.withLongList(): ActivityConfiguration {
    val icons = listOf(ViewIcon.Running, ViewIcon.Swimming, ViewIcon.Tennis, ViewIcon.MartialArts, ViewIcon.Skiing, ViewIcon.Kayaking, ViewIcon.Rowing, ViewIcon.Golf)
    val names = listOf("Trail running in the hills", "Lane swimming", "Tennis with Anna", "Brazilian jiu-jitsu", "Cross-country skiing", "Kayak", "Rowing machine", "Golf driving range")
    val more = icons.zip(names).mapIndexed { index, (icon, name) -> ListedActivity("act_more_$index", name, icon) }
    val climbing = activities.map { activity ->
        if (activity.id != BoulderingActivityId) activity
        else activity.copy(routine = Routine(listOf(RoutineDay("day_wall", "Wall session", listOf(RoutineExercise("ex_hang", "Hangboard", 3, 5..8, weight = null, startReps = 6))))))
    }
    return copy(activities = climbing + more)
}

/** The gym's Day A with its bench press done without a weight, its fold reading the sets and reps alone. */
private fun ActivityConfiguration.withWeightlessExercise(): ActivityConfiguration =
    applyingEdits(listOf(ActivityEdit.SetExerciseNumbers("bench_press", sets = 3, reps = 8..10, weight = null, startReps = 10)), requireNotNull(parseDateKey(FixtureTodayKey)).minusWeeks(1))

/** A week of seven days, each with an activity planned. */
private fun ActivityConfiguration.plannedEveryDay(): ActivityConfiguration {
    val planned = listOf(GymActivityId, BoulderingActivityId, GymActivityId, CyclingActivityId, GymActivityId, JoggingActivityId, BoulderingActivityId)
    val week = WeekPlan(DayOfWeek.entries.zip(planned) { day, activity -> PlannedDay(day, activity) })
    return applyingEdits(listOf(ActivityEdit.SetWeek(week)), requireNotNull(parseDateKey(FixtureTodayKey)).minusWeeks(1))
}

/**
 * The settings row a list of things a person made is built of: rows wearing their icon's color,
 * each with a quiet action before the chevron and a line kept as tall as the longest it may say,
 * one of them with a name long enough to wrap and the warning in its line's place; and a plain
 * one as Settings' own list has it.
 */
@Composable
private fun SettingsLinkRowSpecimen() {
    val rules = fixtureAppState.activityRules
    val lines = listOf("Routine · 7 days", "No routine", "Something to fix")
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val gym = rules.lookOf(ActivityMark.Listed(GymActivityId))
        SettingsLinkRow(icon = gym.icon, iconTint = gym.containerColor, title = "Gym", summary = "Routine · 3 days", reserving = lines, onClick = {}, testTag = "gallery_link_summary", action = { SpecimenMoveUp() })
        val cycling = rules.lookOf(ActivityMark.Listed(CyclingActivityId))
        SettingsLinkRow(icon = cycling.icon, iconTint = cycling.containerColor, title = "Cycling", summary = "No routine", reserving = lines, onClick = {}, testTag = "gallery_link_plain", action = { SpecimenMoveUp() })
        SettingsLinkRow(icon = ViewIcon.MartialArts.vector, iconTint = cycling.containerColor, title = "Brazilian jiu-jitsu with the club on Thursdays", summary = "Something to fix", summaryIsWarning = true, reserving = lines, onClick = {}, testTag = "gallery_link_warning", action = { SpecimenMoveUp() })
        SettingsLinkRow(icon = ViewIcon.Bolt.vector, title = "Backups", summary = "Latest saved changes are backed up.", onClick = {}, testTag = "gallery_link_settings")
    }
}

@Composable
private fun SpecimenMoveUp() {
    SmallIconButton(description = "Move up", icon = Icons.Rounded.ArrowUpward, enabled = true, testTag = "gallery_link_up", onClick = {})
}

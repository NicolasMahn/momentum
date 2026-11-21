package com.example.workouttracker.gallery

import com.example.workouttracker.feature.mo.plannedRestDays

import com.example.workouttracker.feature.mo.bankReservation
import com.example.workouttracker.feature.tracker.CheckpointTokens


import com.example.workouttracker.feature.checklist.tagName

import com.example.workouttracker.domain.checklist.isInUse

import com.example.workouttracker.domain.trackerRef



import com.example.workouttracker.domain.loggedViews
import com.example.workouttracker.domain.measurement.applyingEdits
import com.example.workouttracker.feature.settings.BuiltInViewSave
import com.example.workouttracker.feature.settings.ConfigurationDraft
import com.example.workouttracker.feature.settings.MeasurementDraft
import com.example.workouttracker.feature.settings.MeasurementSetup
import com.example.workouttracker.feature.settings.MeasurementSetupState
import com.example.workouttracker.feature.settings.MeasurementUnitPart
import com.example.workouttracker.feature.settings.ChecklistPage
import com.example.workouttracker.domain.checklist.CheckpointTime
import com.example.workouttracker.reminder.TestReminder
import com.example.workouttracker.feature.settings.ViewWrite
import com.example.workouttracker.feature.settings.NewViewPage
import com.example.workouttracker.feature.settings.MadeMeasurementSettingsPage
import com.example.workouttracker.feature.settings.DailyViewStreak
import com.example.workouttracker.feature.settings.MeasurementStreak
import com.example.workouttracker.domain.measurement.MeasurementConfiguration
import com.example.workouttracker.domain.measurement.MeasurementEdit
import com.example.workouttracker.domain.measurement.MeasurementLog
import com.example.workouttracker.domain.measurement.MeasurementStep
import com.example.workouttracker.domain.measurement.applyingMeasurementEdits
import com.example.workouttracker.domain.measurement.starterMeasurement
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.runtime.Composable
import com.example.workouttracker.core.ui.LocalDaysOff
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.workouttracker.app.AppDestination
import com.example.workouttracker.app.HomeScreen
import com.example.workouttracker.domain.LoggedView
import com.example.workouttracker.domain.ViewLook
import com.example.workouttracker.core.format.dateText
import com.example.workouttracker.core.text.resolve
import com.example.workouttracker.core.ui.CompletionButton
import com.example.workouttracker.core.ui.CompletionButtonColors
import com.example.workouttracker.core.ui.CompletionCelebrationIntensity
import com.example.workouttracker.core.ui.MomentaryCompletionButton
import com.example.workouttracker.core.ui.MomentumButton
import com.example.workouttracker.core.ui.MomentumButtonVariant
import com.example.workouttracker.core.ui.ViewTheme
import com.example.workouttracker.core.ui.completionButtonColors
import com.example.workouttracker.domain.AppState
import com.example.workouttracker.domain.time.TimeLog
import com.example.workouttracker.domain.settings.DashboardFeature
import com.example.workouttracker.domain.settings.FeatureVisibilitySettings
import com.example.workouttracker.domain.ref
import com.example.workouttracker.domain.settings.NewInstallationVisibility
import com.example.workouttracker.domain.activity.SeededActivityRules
import com.example.workouttracker.feature.questions.QuestionsWords
import com.example.workouttracker.feature.questions.QuestionHelpDialog
import com.example.workouttracker.feature.questions.presentedQuestions
import com.example.workouttracker.feature.questions.QuestionsHistory
import com.example.workouttracker.feature.questions.answerStep
import com.example.workouttracker.domain.activity.activityRules
import com.example.workouttracker.feature.logactivity.ActivityHistory
import com.example.workouttracker.feature.logactivity.ActivityMark
import com.example.workouttracker.feature.logactivity.ActivityTimeline
import com.example.workouttracker.feature.logactivity.labelOf
import com.example.workouttracker.feature.logactivity.logActivityPresentation
import com.example.workouttracker.feature.logactivity.lookOf
import com.example.workouttracker.feature.time.words
import com.example.workouttracker.feature.time.TimeHistory
import com.example.workouttracker.feature.settings.BackupWarning
import com.example.workouttracker.feature.settings.ChecklistEditor
import com.example.workouttracker.feature.settings.ChecklistSetup
import com.example.workouttracker.domain.checklist.applyingEdits
import com.example.workouttracker.domain.checklist.startedOn
import com.example.workouttracker.domain.checklist.starterChecklist
import com.example.workouttracker.R
import com.example.workouttracker.domain.reminder.Bedtime
import com.example.workouttracker.domain.reminder.ReminderSettings
import com.example.workouttracker.feature.settings.DaysOffSettings
import com.example.workouttracker.feature.settings.FeatureSettingsDraft
import com.example.workouttracker.feature.settings.FeatureSettingsPage
import com.example.workouttracker.feature.settings.SubstanceSettingsPage
import com.example.workouttracker.feature.settings.settingsPresentation
import com.example.workouttracker.domain.holiday.DayRange
import com.example.workouttracker.domain.holiday.DayOff
import com.example.workouttracker.domain.holiday.HolidaySchedule
import com.example.workouttracker.domain.checklist.progressOn
import com.example.workouttracker.domain.checklist.DayRecord
import com.example.workouttracker.feature.statistics.StatisticsRange
import com.example.workouttracker.feature.statistics.StatisticsScreen
import com.example.workouttracker.domain.checklist.Dated
import com.example.workouttracker.domain.substance.OwnSubstance
import com.example.workouttracker.domain.substance.SubstanceAmountUnit
import com.example.workouttracker.domain.substance.SubstanceKind
import com.example.workouttracker.domain.substance.SubstanceEdit
import com.example.workouttracker.domain.substance.SubstanceGoal
import com.example.workouttracker.domain.substance.SubstanceSettings
import com.example.workouttracker.domain.substance.applyEdit
import com.example.workouttracker.domain.substance.applyingEdits
import com.example.workouttracker.feature.substances.SubstanceHistory
import com.example.workouttracker.feature.checklist.ChecklistDayState
import com.example.workouttracker.domain.ActivityTrackerRef
import com.example.workouttracker.domain.SubstancesTrackerRef
import com.example.workouttracker.core.ui.LocalDayCovers
import com.example.workouttracker.core.ui.DayCover
import com.example.workouttracker.feature.substances.SubstanceSummary
import com.example.workouttracker.feature.substances.substanceFeaturePresentation
import com.example.workouttracker.feature.checklist.ChecklistHistory
import com.example.workouttracker.feature.checklist.ChecklistScreen
import com.example.workouttracker.feature.checklist.presentation
import com.example.workouttracker.domain.checklist.ChecklistConfiguration
import com.example.workouttracker.feature.measurement.MeasurementHistory
import com.example.workouttracker.feature.measurement.chartSpacing
import com.example.workouttracker.feature.measurement.words
import com.example.workouttracker.feature.measurement.MeasurementHistoryWindow
import com.example.workouttracker.persistence.backup.BackupError
import com.example.workouttracker.persistence.backup.BackupStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.example.workouttracker.domain.mo.moMood
import com.example.workouttracker.feature.mo.MoStreakScreen
import com.example.workouttracker.domain.checklist.WhenAway
import com.example.workouttracker.domain.checklist.joinRewards
import com.example.workouttracker.core.time.parseDateKey
import com.example.workouttracker.domain.checklist.ChecklistDay
import com.example.workouttracker.domain.checklist.tickOf
import java.time.LocalDate
import java.time.LocalTime

/**
 * Every view opens with its identity, then hosts whatever of its own composables can run on fake
 * data alone. A view whose pieces are private inside its screen file shows only the identity.
 */
internal fun viewSpecimens(destination: AppDestination): List<GallerySpecimen> =
    listOf(identitySpecimen(destination)) + ownSpecimens(destination).map { specimen ->
        GallerySpecimen(specimen.name) { ViewThemed(destination) { specimen.content() } }
    }

private fun identitySpecimen(destination: AppDestination) = GallerySpecimen("Identity") {
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        viewIdentities(destination).forEach { IdentityShowcase(it) }
    }
}

/** A view's specimens must look the way they look on its screen, which means inside its theme. */
@Composable
private fun ViewThemed(destination: AppDestination, content: @Composable () -> Unit) {
    val identity = viewIdentities(destination).first()
    if (identity.isOwned) ViewTheme(identity.colors, content) else content()
}

/** The fixture with the demo medication list, which joined rewards three weeks before [today]. */
private fun fixtureWithDemoChecklistJoined(today: LocalDate): AppState {
    val withSecond = fixtureAppState.copy(
        checklists = fixtureAppState.checklists + DemoSecondChecklist,
        checklistDays = fixtureAppState.checklistDays + demoSecondChecklistDays(FixtureTodayKey, 28),
    )
    return joinRewards(withSecond, DemoSecondChecklist.id, today.minusDays(21))
}

/**
 * The fixture with two demo measurements a person made: the waist joined the streak three weeks
 * before [today], was logged on each day since but the fourth last and is open today; the mood is
 * outside the streak.
 */
private fun fixtureWithDemoMeasurementJoined(today: LocalDate): AppState {
    val logged = (1L..21L).filterNot { it == 4L }.map { MeasurementLog(DemoWaist.id, today.minusDays(it).toString(), it, 92.0 - it / 10.0) }
    return fixtureAppState
        .copy(measurements = fixtureAppState.measurements + DemoWaist + DemoMood, measurementLogs = fixtureAppState.measurementLogs + logged)
        .applyingMeasurementEdits(DemoWaist.id, listOf(MeasurementEdit.JoinRewards), today.minusDays(21))
}

private fun ownSpecimens(destination: AppDestination): List<GallerySpecimen> = when (destination) {
    AppDestination.Home -> listOf(
        GallerySpecimen("HomeScreen") {
            ScreenFrame(height = 560.dp) {
                // The demo medication list is in the streak, so its tile wears a badge like a built-in task's.
                val today = requireNotNull(parseDateKey(FixtureTodayKey))
                val state = remember { fixtureWithDemoChecklistJoined(today) }
                val mood = remember(state) { moMood(state, FeatureVisibilitySettings(disabledFeatures = emptySet()), today) }
                HomeScreen(
                    mood = mood,
                    moIsAngry = false,
                    moScene = null,
                    moEvent = null,
                    onMoEventDone = {},
                    celebrateMo = null,
                    onMoCelebrated = {},
                    featureVisibility = FeatureVisibilitySettings(disabledFeatures = emptySet()),
                    onLogActivity = {},
                    activity = state.activity,
                    trackers = homeFixtureViews(state, mood),
                    activityStatus = fixtureActivityStatus(mood),
                    onOpenSubstances = {},
                    onOpenStatistics = {},
                    onOpenSettings = {},
                )
            }
        },
        GallerySpecimen("HomeScreen, a questions view that joined") {
            ScreenFrame(height = 560.dp) {
                // The sleep view is in the streak and open today, so its tile wears the socket after the measurements' tiles.
                val today = requireNotNull(parseDateKey(FixtureTodayKey))
                val state = remember { fixtureWithDemoQuestionsJoined(today) }
                val mood = remember(state) { moMood(state, FeatureVisibilitySettings(disabledFeatures = emptySet()), today) }
                HomeScreen(
                    mood = mood,
                    moIsAngry = false,
                    moScene = null,
                    moEvent = null,
                    onMoEventDone = {},
                    celebrateMo = null,
                    onMoCelebrated = {},
                    featureVisibility = FeatureVisibilitySettings(disabledFeatures = emptySet()),
                    onLogActivity = {},
                    activity = state.activity,
                    trackers = homeFixtureViews(state, mood),
                    activityStatus = fixtureActivityStatus(mood),
                    onOpenSubstances = {},
                    onOpenStatistics = {},
                    onOpenSettings = {},
                )
            }
        },
        GallerySpecimen("HomeScreen, a time view that joined") {
            ScreenFrame(height = 560.dp) {
                // The piano is in the streak: its tile wears the coin once today holds minutes, the socket on a Sunday whose week is short, and nothing otherwise.
                val today = requireNotNull(parseDateKey(FixtureTodayKey))
                val state = remember { fixtureWithDemoTimeJoined(today) }
                val mood = remember(state) { moMood(state, FeatureVisibilitySettings(disabledFeatures = emptySet()), today) }
                HomeScreen(
                    mood = mood,
                    moIsAngry = false,
                    moScene = null,
                    moEvent = null,
                    onMoEventDone = {},
                    celebrateMo = null,
                    onMoCelebrated = {},
                    featureVisibility = FeatureVisibilitySettings(disabledFeatures = emptySet()),
                    onLogActivity = {},
                    activity = state.activity,
                    trackers = homeFixtureViews(state, mood),
                    activityStatus = fixtureActivityStatus(mood),
                    onOpenSubstances = {},
                    onOpenStatistics = {},
                    onOpenSettings = {},
                )
            }
        },
        GallerySpecimen("HomeScreen, a measurement that joined") {
            ScreenFrame(height = 560.dp) {
                // The waist is in the streak and open today, so its tile wears the socket; the mood is outside it and wears nothing.
                val today = requireNotNull(parseDateKey(FixtureTodayKey))
                val state = remember { fixtureWithDemoMeasurementJoined(today) }
                val mood = remember(state) { moMood(state, FeatureVisibilitySettings(disabledFeatures = emptySet()), today) }
                HomeScreen(
                    mood = mood,
                    moIsAngry = false,
                    moScene = null,
                    moEvent = null,
                    onMoEventDone = {},
                    celebrateMo = null,
                    onMoCelebrated = {},
                    featureVisibility = FeatureVisibilitySettings(disabledFeatures = emptySet()),
                    onLogActivity = {},
                    activity = state.activity,
                    trackers = homeFixtureViews(state, mood),
                    activityStatus = fixtureActivityStatus(mood),
                    onOpenSubstances = {},
                    onOpenStatistics = {},
                    onOpenSettings = {},
                )
            }
        },
        GallerySpecimen("HomeScreen, Weight hidden from today") {
            ScreenFrame(height = 560.dp) {
                // Hidden from today, Weight has no tile and Mo does not wait for it; the days before keep what they were.
                val today = requireNotNull(parseDateKey(FixtureTodayKey))
                val visibility = remember { FeatureVisibilitySettings(disabledFeatures = emptySet()).withVisibility(DemoTask.Weight.view, visible = false, today = today) }
                val mood = remember { moMood(fixtureAppState, visibility, today) }
                HomeScreen(
                    mood = mood,
                    moIsAngry = false,
                    moScene = null,
                    moEvent = null,
                    onMoEventDone = {},
                    celebrateMo = null,
                    onMoCelebrated = {},
                    featureVisibility = visibility,
                    onLogActivity = {},
                    activity = fixtureAppState.activity,
                    trackers = homeFixtureViews(fixtureAppState, mood, visibility),
                    activityStatus = fixtureActivityStatus(mood),
                    onOpenSubstances = {},
                    onOpenStatistics = {},
                    onOpenSettings = {},
                )
            }
        },
        GallerySpecimen("HomeScreen, a new installation") {
            ScreenFrame(height = 560.dp) {
                // Activity alone, and Settings to make the rest; Substances and Statistics wait in Settings' list.
                val today = requireNotNull(parseDateKey(FixtureTodayKey))
                val state = remember { AppState(activity = SeededActivityRules) }
                val mood = remember { moMood(state, NewInstallationVisibility, today) }
                HomeScreen(
                    mood = mood,
                    moIsAngry = false,
                    moScene = null,
                    moEvent = null,
                    onMoEventDone = {},
                    celebrateMo = null,
                    onMoCelebrated = {},
                    featureVisibility = NewInstallationVisibility,
                    onLogActivity = {},
                    activity = state.activity,
                    trackers = homeFixtureViews(state, mood),
                    activityStatus = fixtureActivityStatus(mood),
                    onOpenSubstances = {},
                    onOpenStatistics = {},
                    onOpenSettings = {},
                )
            }
        },
    ) + moSpecimens + moMomentSpecimens + moActSpecimens

    AppDestination.Streak -> listOf(
        GallerySpecimen("MoStreakScreen") {
            ScreenFrame(height = 760.dp) {
                // Pressing a bank sets its task aside, so the fixture keeps the choices it is given.
                var chosen by remember { mutableStateOf(emptySet<com.example.workouttracker.domain.TrackerRef>()) }
                val mood = remember(chosen) {
                    moMood(
                        fixtureAppState,
                        FeatureVisibilitySettings(disabledFeatures = emptySet()),
                        requireNotNull(parseDateKey(FixtureTodayKey)),
                        chosenTrackers = chosen,
                    )
                }
                GalleryStreakScreen(fixtureAppState, mood, onSetAside = { need, setAside -> chosen = if (setAside) chosen + need else chosen - need })
            }
        },
        GallerySpecimen("MoStreakScreen, a checklist that joined") {
            ScreenFrame(height = 760.dp) {
                // The demo medication list joined rewards three weeks ago, so its bank sits under Mo's.
                val today = requireNotNull(parseDateKey(FixtureTodayKey))
                val state = remember { fixtureWithDemoChecklistJoined(today) }
                // It is still open today, so setting every task aside leaves today open.
                var chosen by remember { mutableStateOf(emptySet<com.example.workouttracker.domain.TrackerRef>()) }
                val mood = remember(chosen) {
                    moMood(state, FeatureVisibilitySettings(disabledFeatures = emptySet()), today, chosenTrackers = chosen)
                }
                GalleryStreakScreen(
                    state, mood,
                    onSetAside = { need, setAside -> chosen = if (setAside) chosen + need else chosen - need },
                )
            }
        },
        GallerySpecimen("MoStreakScreen, a questions view that joined") {
            ScreenFrame(height = 760.dp) {
                // The sleep view joined three weeks ago, so its bank sits under Mo's in its own look.
                val today = requireNotNull(parseDateKey(FixtureTodayKey))
                val state = remember { fixtureWithDemoQuestionsJoined(today) }
                val mood = remember { moMood(state, FeatureVisibilitySettings(disabledFeatures = emptySet()), today) }
                GalleryStreakScreen(state, mood)
            }
        },
        GallerySpecimen("MoStreakScreen, a time view that joined") {
            ScreenFrame(height = 760.dp) {
                // The piano joined three weeks ago, so its bank sits under Mo's in its own look, with this week's minutes toward its next token as Reading's has.
                val today = requireNotNull(parseDateKey(FixtureTodayKey))
                val state = remember { fixtureWithDemoTimeJoined(today) }
                val mood = remember { moMood(state, FeatureVisibilitySettings(disabledFeatures = emptySet()), today) }
                GalleryStreakScreen(state, mood)
            }
        },
        GallerySpecimen("MoStreakScreen, a measurement that joined") {
            ScreenFrame(height = 760.dp) {
                // The waist joined three weeks ago, so its bank sits under Mo's in its own look; the mood never joined and has no row.
                val today = requireNotNull(parseDateKey(FixtureTodayKey))
                val state = remember { fixtureWithDemoMeasurementJoined(today) }
                val mood = remember { moMood(state, FeatureVisibilitySettings(disabledFeatures = emptySet()), today) }
                GalleryStreakScreen(state, mood)
            }
        },
        // Sick yesterday and today: the week and each bank's squares say so, and nothing is open.
        // The second checklist never joined, so it has no row here, like any view outside the streak.
        GallerySpecimen("MoStreakScreen, sick, a checklist outside the streak") {
            ScreenFrame(height = 760.dp) {
                val today = requireNotNull(parseDateKey(FixtureTodayKey))
                val state = remember {
                    fixtureAppState.copy(
                        checklists = fixtureAppState.checklists + DemoSecondChecklist,
                        checklistDays = fixtureAppState.checklistDays + demoSecondChecklistDays(FixtureTodayKey, 28),
                    )
                }
                val sick = remember { HolidaySchedule().withSickDays(DayRange(today.minusDays(1), today)) }
                val mood = remember { moMood(state, FeatureVisibilitySettings(disabledFeatures = emptySet()), today, sick) }
                GalleryStreakScreen(state, mood)
            }
        },
    )

    AppDestination.Activity -> listOf(
        GallerySpecimen("MomentaryCompletionButton, Strong") {
            val presentation = logActivityPresentation()
            MomentarySavingSpecimen(
                colors = presentation.colors.completionButtonColors(),
                icon = presentation.icon,
                actionLabel = "Log activity",
                completedLabel = "Activity logged",
                testTag = "gallery_activity_momentary",
                intensity = CompletionCelebrationIntensity.Strong,
            )
        },
        GallerySpecimen("Activity palettes") { ActivityPalettesSpecimen() },
    ) + activityScreenSpecimens + activitySettingsSpecimens + listOf(
        GallerySpecimen("ActivityHistory") { ActivityHistorySpecimen() },
        GallerySpecimen("ActivityTimeline") { ActivityTimelineSpecimen() },
    )

    AppDestination.Measurement -> madeMeasurementScreenSpecimens + ofTask(DemoTask.Weight, weightSpecimens)

    AppDestination.Questions -> madeQuestionsScreenSpecimens + ofTask(DemoTask.Eating, eatingSpecimens)

    AppDestination.Checklist -> listOf(
        GallerySpecimen("ChecklistScreen, after waking") {
            ChecklistScreenSpecimen(
                fixtureAppState.copy(
                    checklists = fixtureAppState.checklists + DemoSecondChecklist,
                    checklistDays = fixtureAppState.checklistDays + demoSecondChecklistDays(FixtureTodayKey, 28),
                ),
                DemoSecondChecklist,
            )
        },
        // The three days before yesterday were away and the day before them sick: every calendar
        // marks them, whatever was recorded.
        GallerySpecimen("ChecklistScreen, days away and sick") {
            val today = requireNotNull(parseDateKey(FixtureTodayKey))
            val daysOff = remember {
                HolidaySchedule(
                    holidays = listOf(DayRange(today.minusDays(4), today.minusDays(2))),
                    sickDays = listOf(DayRange(today.minusDays(5), today.minusDays(5))),
                )
            }
            CompositionLocalProvider(LocalDaysOff provides { key -> parseDateKey(key)?.let(daysOff::dayOff) }) {
                ChecklistScreenSpecimen(fixtureAppState, DemoSupplementsChecklist, daysOff)
            }
        },
        // Flossing once a week, on any day: offered every day, with where the week stands under it.
        GallerySpecimen("ChecklistScreen, once a week") {
            ChecklistScreenSpecimen(
                fixtureAppState.copy(checklists = fixtureAppState.checklists.map { if (it.id == DemoWeeklyCareChecklist.id) DemoWeeklyCareChecklist else it }),
                DemoWeeklyCareChecklist,
            )
        },
        // Brushing and hair foam log only misses: today's boxes are assumed done, quieter than a
        // tick, except this morning's brushing, which was marked missed and wears the cross, as
        // yesterday morning's does.
        GallerySpecimen("ChecklistScreen, only log misses") {
            val today = requireNotNull(parseDateKey(FixtureTodayKey))
            val missedDays = listOf(today, today.minusDays(1)).map { it.toString() }
            val brushing = tickOf(DemoCareSession.MORNING.storageId, DemoCareAction.TOOTHBRUSHING.storageId)
            ChecklistScreenSpecimen(
                fixtureAppState.copy(
                    checklists = fixtureAppState.checklists.map { if (it.id == DemoOnlyMissesCareChecklist.id) DemoOnlyMissesCareChecklist else it },
                    checklistDays = fixtureAppState.checklistDays.filterNot { it.checklistId == DemoOnlyMissesCareChecklist.id && it.dateKey in missedDays } +
                        missedDays.map { ChecklistDay(DemoOnlyMissesCareChecklist.id, it, 0L, ticks = emptySet(), misses = setOf(brushing)) },
                ),
                DemoOnlyMissesCareChecklist,
            )
        },
    ) + ofTask(DemoTask.Supplements, supplementsSpecimens) + ofTask(DemoTask.Care, careSpecimens)

    AppDestination.Time -> madeTimeScreenSpecimens + ofTask(DemoTask.Reading, timerPresetSpecimens)

    AppDestination.Tracker -> trackerModuleSpecimens

    AppDestination.Substances -> listOf(
        GallerySpecimen("MomentaryCompletionButton, Restrained") {
            val presentation = substanceFeaturePresentation()
            MomentarySavingSpecimen(
                colors = presentation.colors.completionButtonColors(),
                icon = presentation.icon,
                actionLabel = "Log use",
                completedLabel = "Use logged",
                testTag = "gallery_substance_momentary",
            )
        },
        GallerySpecimen("SubstanceSummary") { SubstanceSummary(fixtureSubstanceWindow) },
        GallerySpecimen("SubstanceHistory") {
            var selectedDateKey by remember { mutableStateOf(FixtureTodayKey) }
            // One covered day without a use and one with, so the seal is seen alone and under dots.
            val past = fixtureSubstanceWindow.days.filter { it.dateKey < FixtureTodayKey }
            val covers = listOfNotNull(
                past.firstOrNull { it.logs.isEmpty() && !it.none },
                past.firstOrNull { it.logs.isNotEmpty() },
            ).associate { it.dateKey to DayCover.Token }
            CompositionLocalProvider(LocalDayCovers provides mapOf(SubstancesTrackerRef to covers)) {
                SubstanceHistory(
                    window = fixtureSubstanceWindow,
                    selectedDateKey = selectedDateKey,
                    todayKey = FixtureTodayKey,
                    onDayClick = { selectedDateKey = it.dateKey },
                )
            }
        },
    )

    AppDestination.Statistics -> listOf(
        GallerySpecimen("StatisticsScreen") {
            ScreenFrame(height = 860.dp) {
                StatisticsScreen(fixtureAppState, FixtureTodayKey, streak = fixtureStreak)
            }
        },
        GallerySpecimen("One year") {
            ScreenFrame(height = 860.dp) {
                StatisticsScreen(
                    state = fixtureAppState,
                    todayKey = FixtureTodayKey,
                    streak = fixtureStreak,
                    initialRange = StatisticsRange.Year,
                )
            }
        },
        // Kept, frozen, a token, several tokens, a restart and rest days, all in one month.
        GallerySpecimen("Every kind of streak day") {
            ScreenFrame(height = 440.dp) {
                StatisticsScreen(
                    state = AppState(),
                    todayKey = FixtureMidWeekTodayKey,
                    featureVisibility = FeatureVisibilitySettings(
                        disabledFeatures = DashboardFeature.entries.toSet() - DashboardFeature.Statistics,
                    ),
                    streak = fixtureEveryStreakDay,
                    initialRange = StatisticsRange.FourWeeks,
                )
            }
        },
        // The first-record rule at work: ten days of history must not read as 3% of three months.
        GallerySpecimen("Taken up ten days ago") {
            ScreenFrame(height = 860.dp) {
                StatisticsScreen(fixtureRecentStartState, FixtureTodayKey)
            }
        },
        // Piano asks 90 minutes a week: one week reached it, one fell short with a mark and no
        // minutes, a sick Sunday left one unjudged (a tick), and this week is half full: 1 of 2, 50%.
        GallerySpecimen("A timer by its weekly goal") {
            ScreenFrame(height = 300.dp) {
                val visibility = FeatureVisibilitySettings(disabledFeatures = DashboardFeature.entries.toSet() - DashboardFeature.Statistics)
                val sunday = LocalDate.parse("2026-09-13")
                StatisticsScreen(
                    state = TimerWeeksState,
                    todayKey = FixtureMidWeekTodayKey,
                    featureVisibility = visibility,
                    initialRange = StatisticsRange.FourWeeks,
                    daysOff = HolidaySchedule().withSickDays(DayRange(sunday, sunday)),
                )
            }
        },
        // Hidden a week ago, Weight has no row and no block; the tokens it earned before stay in the key.
        GallerySpecimen("Weight hidden from a week ago") {
            ScreenFrame(height = 860.dp) {
                val today = requireNotNull(parseDateKey(FixtureTodayKey))
                StatisticsScreen(
                    state = fixtureAppState,
                    todayKey = FixtureTodayKey,
                    featureVisibility = FeatureVisibilitySettings(disabledFeatures = emptySet()).withVisibility(DemoTask.Weight.view, visible = false, today = today.minusWeeks(1)),
                    streak = fixtureStreak,
                )
            }
        },
        GallerySpecimen("A new installation, nothing logged") {
            ScreenFrame(height = 620.dp) {
                StatisticsScreen(
                    state = AppState(activity = SeededActivityRules),
                    todayKey = FixtureTodayKey,
                    featureVisibility = FeatureVisibilitySettings(disabledFeatures = emptySet()),
                )
            }
        },
    )

    AppDestination.Settings -> listOf(
        GallerySpecimen("BackupWarning") { BackupWarningsSpecimen() },
        GallerySpecimen("DaysOffSettings, holidays") { DaysOffSettingsSpecimen(DayOff.Away) },
        GallerySpecimen("DaysOffSettings, sick days") { DaysOffSettingsSpecimen(DayOff.Sick) },
        // What "Track something new" opens: a card for each kind, which here leads nowhere.
        GallerySpecimen("NewViewPage, registered counter kind") { NewViewPage(onChoose = {}, creations = listOf(remember { counterTrackerCreation() })) },
    ) + sleepSettingsSpecimens + reminderNotificationSpecimens

    // Every page here is built on ViewSettingsFrame, and between them they show its states:
    // Delete working (Care, Weight, Eating, Reading, and views of their kinds a person made),
    // Delete disabled with its reason (Substances, Activity), an error on the bar, the steps of a
    // setup, four for a checklist, a measurement, a questions view and a time view on the one
    // stepping, a page with a measurement's four parts (Weight, a measurement of its own), one
    // with a questions view's four (Eating, questions of its own), one with a time view's four
    // (Reading, a time view of its own), one with three (Substances) and a page with one tab
    // (Activity).
    AppDestination.SettingsPage -> listOf(
        GallerySpecimen("ChecklistEditor, a checklist of its own") { ChecklistEditorSpecimen(DemoSecondChecklist) },
        GallerySpecimen("ChecklistEditor, Care") { ChecklistEditorSpecimen(requireNotNull(fixtureAppState.careChecklist)) },
        // What Settings reports takes the bar's line.
        GallerySpecimen("ChecklistEditor, an error on the bar") {
            ChecklistEditorSpecimen(requireNotNull(fixtureAppState.careChecklist), message = stringResource(R.string.settings_error_notifications_off))
        },
        // The between-teeth cleaning once a week, with the holiday count the Streak tab can ask for.
        GallerySpecimen("ChecklistEditor, once a week") { ChecklistEditorSpecimen(DemoWeeklyCareChecklist) },
        // Brushing and hair foam log only misses, so the Streak page shows their boxes checked and
        // says what Fewer times would change.
        GallerySpecimen("ChecklistEditor, only log misses") { ChecklistEditorSpecimen(DemoOnlyMissesCareChecklist) },
        // Stored before the last item was guarded, so it has none: its page opens on Items.
        GallerySpecimen("ChecklistEditor, no items yet") { ChecklistEditorSpecimen(DemoSecondChecklist.copy(items = emptyList())) },
        GallerySpecimen("ChecklistSetup") { ChecklistSetupSpecimen() },
        GallerySpecimen("SubstanceSettingsPage") { SubstanceSettingsPageSpecimen() },
        GallerySpecimen("MadeMeasurementSettingsPage, Weight, Look") { WeightSettingsPageSpecimen() },
        GallerySpecimen("MadeMeasurementSettingsPage, Weight, Unit") { WeightSettingsPageSpecimen(ChecklistPage.Items) },
        GallerySpecimen("MadeMeasurementSettingsPage, Weight, Reminders") { WeightSettingsPageSpecimen(ChecklistPage.Reminders) },
        GallerySpecimen("MadeMeasurementSettingsPage, Weight, Streak") { WeightSettingsPageSpecimen(ChecklistPage.Streak) },
        // The setup at each of its steps: as it opens, then with a name on Unit, with its unit on
        // Reminders, then on Streak with the reminder on and counting toward the streak, where
        // Create waits.
        GallerySpecimen("MeasurementSetup, step 1 Look") { MeasurementSetupSpecimen(ChecklistPage.Look) },
        GallerySpecimen("MeasurementSetup, step 2 Unit") { MeasurementSetupSpecimen(ChecklistPage.Items) },
        GallerySpecimen("MeasurementSetup, step 3 Reminders") { MeasurementSetupSpecimen(ChecklistPage.Reminders) },
        GallerySpecimen("MeasurementSetup, step 4 Streak") { MeasurementSetupSpecimen(ChecklistPage.Streak) },
        // The page on each of its parts: Look and Unit as it was made, Reminders with the reminder
        // on after waking, and Streak a week after it joined, not due on holiday.
        GallerySpecimen("MadeMeasurementSettingsPage, Look") { MadeMeasurementSettingsPageSpecimen(ChecklistPage.Look) },
        GallerySpecimen("MadeMeasurementSettingsPage, Unit") { MadeMeasurementSettingsPageSpecimen(ChecklistPage.Items) },
        GallerySpecimen("MadeMeasurementSettingsPage, Reminders") { MadeMeasurementSettingsPageSpecimen(ChecklistPage.Reminders) },
        GallerySpecimen("MadeMeasurementSettingsPage, Reminders, hidden") {
            MadeMeasurementSettingsPageSpecimen(ChecklistPage.Reminders, hidden = true)
        },
        GallerySpecimen("MadeMeasurementSettingsPage, Streak") { MadeMeasurementSettingsPageSpecimen(ChecklistPage.Streak) },
        GallerySpecimen("MeasurementUnitPart, a measurement's Unit part") { MeasurementUnitPartSpecimen() },
        GallerySpecimen("DailyViewStreak, a made measurement's or questions view's Streak part") { DailyViewStreakSpecimen() },
        GallerySpecimen("MeasurementStreak, the Streak part of a measurement still earning by the week") { WeekMeasurementStreakSpecimen() },
    ) + questionsSettingsSpecimens + timeSettingsSpecimens + listOf(
        GallerySpecimen("FeatureSettingsPage, Activity") { FeatureSettingsPageSpecimen() },
    )
}

/**
 * The real page of a view that holds its reminder alone, Activity's, over reminder settings held
 * here: one tab, a disabled Delete with its reason, and a Save that stores what the switch wants
 * as the app does, with every permission given.
 */
@Composable
private fun FeatureSettingsPageSpecimen() {
    var stored by remember { mutableStateOf(ReminderSettings()) }
    val draft = remember { FeatureSettingsDraft() }
    ScreenFrame(height = 480.dp) {
        FeatureSettingsPage(
            draft = draft,
            settings = stored,
            colors = logActivityPresentation().colors,
            onSave = { draft.save(stored, exactAlarmAllowed = true, notificationsAllowed = true) { updated -> stored = updated; true } },
            reminder = { SpecimenReminderRow(draft.enabledIn(stored), draft::remind) },
            // The specimen is its own page: nothing asks it to leave.
            leaveRequested = false,
            onLeaveAnswered = {},
            onUnsavedWorkChange = {},
        )
    }
}

/**
 * Weight's real page, the page a measurement a person made has, over a configuration held here,
 * opened on [part] ([LoggedPageSpecimen]): Look; Unit, with its kilograms and its step of 0.1 to
 * change; Reminders, five minutes after waking with its switch; and Streak, whether it counts,
 * its week bank's two numbers and its holiday rule. Save stores the edits from the fixture's
 * today, and Delete asks first, naming Weight and its 412 recorded days. Renamed or recolored,
 * the page wears the change as it is made.
 */
@Composable
private fun WeightSettingsPageSpecimen(part: ChecklistPage = ChecklistPage.Look) {
    LoggedPageSpecimen(DemoWeightMeasurement, applying = { edits, today -> applyingEdits(edits, today) }) { stored, today, onSave, reminderRow, bedtime ->
        MadeMeasurementSettingsPage(
            measurement = stored,
            today = today,
            recordedDays = 412,
            bedtime = bedtime,
            daysOff = HolidaySchedule(),
            onSave = onSave,
            reminderRow = reminderRow,
            onSendTest = {},
            // The specimen holds one view, so deleting it only closes the dialog.
            onDelete = { true },
            // The specimen is its own page: it has no list to return to, and nothing asks it to leave.
            onClose = {},
            leaveRequested = false,
            onLeaveAnswered = {},
            onUnsavedWorkChange = {},
            opensOn = part,
        )
    }
}

/**
 * The real setup of a new measurement at one of its four steps, from the starter the app would
 * hand it beside Weight alone: the ruler, steps of 0.1, no unit, the first starter color. On Look
 * it is as it opens, with nothing typed, so Next waits and the three steps ahead are held back.
 * On Unit it has its name; on Reminders its unit and a step of 0.5 too; on Streak the reminder is
 * on after waking as well and it counts toward the streak, so Create is ready. Every step can be
 * walked on from there, and Create keeps the result on show.
 */
@Composable
private fun MeasurementSetupSpecimen(step: ChecklistPage) {
    val today = requireNotNull(parseDateKey(FixtureTodayKey))
    val starter = remember { AppState(measurements = listOf(DemoWeightMeasurement)).starterMeasurement("Measurement") }
    val state = remember {
        val named = listOf(MeasurementEdit.Rename("Waist"))
        val measured = named + listOf(MeasurementEdit.SetUnit("cm"), MeasurementEdit.SetStep(MeasurementStep("0.5")))
        val reminding = listOf(MeasurementEdit.SetRemind(true), MeasurementEdit.SetReminderTime(CheckpointTime.AfterWake(15)), MeasurementEdit.JoinRewards)
        when (step) {
            ChecklistPage.Look -> MeasurementSetupState(starter, today)
            ChecklistPage.Items -> MeasurementSetupState(MeasurementDraft(starter, today, named), name = "Waist", nameTouched = true, step = step)
            ChecklistPage.Reminders -> MeasurementSetupState(MeasurementDraft(starter, today, measured), name = "Waist", nameTouched = true, step = step)
            ChecklistPage.Streak -> MeasurementSetupState(MeasurementDraft(starter, today, measured + reminding), name = "Waist", nameTouched = true, step = step)
        }
    }
    var created by remember { mutableStateOf<MeasurementConfiguration?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ScreenFrame(height = 860.dp) {
            MeasurementSetup(
                starter = { starter },
                today = today,
                bedtime = Bedtime(LocalTime.of(7, 0), fixedBedtime = null),
                daysOff = HolidaySchedule(),
                onCreate = { made, edits ->
                    created = made.applyingEdits(edits, today)
                    ViewWrite.Stored
                },
                onClose = {},
                reminderRow = { edited, _, onRemindChange -> SpecimenReminderRow(edited.checkpoint?.remind == true, onRemindChange) },
                // The gallery posts nothing: Settings owns the sending and its permission.
                onSendTest = {},
                // The specimen is its own page: nothing asks it to leave.
                leaveRequested = false,
                onLeaveAnswered = {},
                onUnsavedWorkChange = {},
                state = state,
            )
        }
        created?.let { measurement ->
            Text(
                text = "Created ${measurement.label}, unit \"${measurement.unit}\", steps of ${measurement.step}, " +
                    "reminder ${if (measurement.checkpoint?.remind == true) "on" else "off"}, ${if (measurement.standardRewards != null) "in" else "outside"} the streak",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * The real page of a measurement a person made, opened on one of its four parts, over a
 * configuration held here: Look; Unit, with the unit, the step and the line that says nothing is
 * converted; Reminders, with the reminder on after waking, its preview and its test; and Streak, a week after it joined and not due on
 * holiday, so the bank's steppers show. Save stores the edits from the fixture's today, and
 * Delete asks first, naming the measurement and twelve recorded days. [hidden], no reminder of it
 * can come, so there is no test to send; the row that says so is Settings' own.
 */
@Composable
private fun MadeMeasurementSettingsPageSpecimen(part: ChecklistPage, hidden: Boolean = false) {
    val today = requireNotNull(parseDateKey(FixtureTodayKey))
    var stored by remember {
        mutableStateOf(
            when (part) {
                ChecklistPage.Reminders -> DemoWaist.applyingEdits(listOf(MeasurementEdit.SetRemind(true), MeasurementEdit.SetReminderTime(CheckpointTime.AfterWake(15))), today)
                ChecklistPage.Streak -> demoWaistInTheStreak(today)
                else -> DemoWaist
            },
        )
    }
    ScreenFrame(height = 860.dp) {
        MadeMeasurementSettingsPage(
            measurement = stored,
            today = today,
            recordedDays = 12,
            bedtime = Bedtime(LocalTime.of(7, 0), fixedBedtime = null),
            daysOff = HolidaySchedule(),
            onSave = { edits ->
                stored = stored.applyingEdits(edits, today)
                ViewWrite.Stored
            },
            reminderRow = { edited, _, onRemindChange -> SpecimenReminderRow(edited.checkpoint?.remind == true, onRemindChange) },
            // The gallery posts nothing: Settings owns the sending and its permission.
            onSendTest = ({ _: TestReminder.AtCheckpoint -> }).takeUnless { hidden },
            // The specimen holds one measurement, so deleting it only closes the dialog.
            onDelete = { true },
            // The specimen is its own page: it has no list to return to, and nothing asks it to leave.
            onClose = {},
            leaveRequested = false,
            onLeaveAnswered = {},
            onUnsavedWorkChange = {},
            opensOn = part,
        )
    }
}

/** The demo waist after it joined the streak a week before [today] and said a holiday is not due. */
private fun demoWaistInTheStreak(today: LocalDate): MeasurementConfiguration =
    DemoWaist.applyingEdits(listOf(MeasurementEdit.JoinRewards, MeasurementEdit.SetWhenAway(WhenAway.Excused)), today.minusWeeks(1))

/**
 * The Unit part a made measurement's page, its setup and Weight's page share, on its own, over a
 * waist's draft: the unit and the step as typed fields and the line under them. Text that cannot
 * be a unit or a step stays in its field, which says why in its label's place.
 */
@Composable
private fun MeasurementUnitPartSpecimen() {
    val today = requireNotNull(parseDateKey(FixtureTodayKey))
    val draft = remember { MeasurementDraft(DemoWaist, today) }
    val shown = draft.edited
    ViewTheme(shown.presentation().colors) {
        MeasurementUnitPart(measurement = shown, draft = draft, testTag = "gallery_measurement")
    }
}

/**
 * The Streak part a made measurement's page and a made questions view's share, on its own, over a
 * measurement's draft that counts toward the streak: the switch, the bank's two steppers and the
 * holiday choice, each edit applied from the fixture's today.
 */
@Composable
private fun DailyViewStreakSpecimen() {
    val today = requireNotNull(parseDateKey(FixtureTodayKey))
    val draft = remember { MeasurementDraft(demoWaistInTheStreak(today), today) }
    val shown = draft.edited
    ViewTheme(shown.presentation().colors) {
        DailyViewStreak(view = shown, edits = draft, today = today, testTag = "gallery_measurement_streak")
    }
}

/**
 * The Streak part of a measurement whose bank still earns by the week, as Weight's was stored,
 * over its draft: the same two steppers with the line under them that says its days count within
 * a week. Changing a number makes it earn by kept days, and the line goes.
 */
@Composable
private fun WeekMeasurementStreakSpecimen() {
    val today = requireNotNull(parseDateKey(FixtureTodayKey))
    val draft = remember { MeasurementDraft(DemoWeightMeasurement, today) }
    val shown = draft.edited
    ViewTheme(shown.presentation().colors) {
        MeasurementStreak(measurement = shown, draft = draft, today = today, testTag = "gallery_week_measurement_streak")
    }
}

/**
 * The real Substances page over a choice held here, opening on Look as it does in Settings: its
 * name, the app's own word until one is typed, its icon, its color and Mo's flavor. Behind the
 * other tabs are the three everyone had, stayed away from, and one of the person's own that
 * already has records, so its remove stays disabled and it is just tracked. Staying away has
 * counted toward the streak for a week, so the Streak part shows its tiles, "Only log uses", the
 * bank's steppers and the holiday rule, and the flavors say nothing about Mo not watching. Save applies the edits made to the
 * choice shown, from the fixture's today, as the store does on the day a save runs.
 */
@Composable
private fun SubstanceSettingsPageSpecimen() {
    val own = OwnSubstance("sub_demo", DemoOwnSubstanceLabel, SubstanceAmountUnit.GRAMS)
    val today = requireNotNull(parseDateKey(FixtureTodayKey))
    var stored by remember {
        mutableStateOf(
            SubstanceSettings(tracked = SubstanceSettings.Legacy.tracked + own.id, own = listOf(own), stayingAway = Dated.always(SubstanceSettings.Legacy.tracked))
                .applyEdit(SubstanceEdit.SetGoal(SubstanceGoal.StayingAway), today.minusWeeks(1)),
        )
    }
    ScreenFrame(height = 720.dp) {
        SubstanceSettingsPage(
            substances = stored,
            recorded = setOf(SubstanceKind.ALCOHOL.id, own.id),
            today = today,
            bedtime = Bedtime(LocalTime.of(7, 0), fixedBedtime = null),
            onSave = { edits -> stored = stored.applyingEdits(edits, today); true },
            reminderRow = { checkIn, onRemindChange -> SpecimenReminderRow(checkIn.remind, onRemindChange) },
            // The specimen is its own page: it has no list to return to, and nothing asks it to leave.
            onClose = {},
            leaveRequested = false,
            onLeaveAnswered = {},
            onUnsavedWorkChange = {},
        )
    }
}

private const val DemoOwnSubstanceLabel = "Kratom"

/**
 * The real editor over a checklist held here: every edit changes its draft as it does in Settings,
 * from the fixture's today, and Save stores the draft as the checklist shown. [message] is what
 * Settings would have to say on the bar.
 */
@Composable
private fun ChecklistEditorSpecimen(checklist: ChecklistConfiguration, message: String? = null) {
    val today = requireNotNull(parseDateKey(FixtureTodayKey))
    var stored by remember { mutableStateOf(checklist) }
    // The pages scroll above the pinned Save and Delete, so the editor needs a screen's height.
    ScreenFrame(height = 720.dp) {
        ChecklistEditor(
            checklist = stored,
            today = today,
            bedtime = Bedtime(LocalTime.of(7, 0), fixedBedtime = null),
            mood = null,
            progress = { it.progressOn(today, { DayRecord() }, HolidaySchedule()) },
            recordedDays = 12,
            recordedDates = (1L..12L).mapTo(linkedSetOf()) { today.minusDays(it) },
            onSave = { edits -> stored = stored.applyingEdits(edits, today, (1L..12L).mapTo(linkedSetOf()) { today.minusDays(it) }); true },
            reminderRow = { _, checkpoint, onRemindChange -> SpecimenReminderRow(checkpoint.remind, onRemindChange) },
            // The gallery posts nothing: Settings owns the sending and its permission.
            onSendTest = {},
            // The specimen holds one checklist, so deleting it only closes the dialog.
            onDelete = { true },
            // The specimen is its own page: it has no list to return to, and nothing asks it to leave.
            onClose = {},
            leaveRequested = false,
            onLeaveAnswered = {},
            onUnsavedWorkChange = {},
            message = message,
        )
    }
}

/** The setup from the starter the app would hand it, over an empty state; Create keeps the result on show. */
@Composable
private fun ChecklistSetupSpecimen() {
    val today = requireNotNull(parseDateKey(FixtureTodayKey))
    var created by remember { mutableStateOf<ChecklistConfiguration?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ScreenFrame(height = 720.dp) {
            ChecklistSetup(
                starter = { AppState().starterChecklist("Checklist", today) },
                today = today,
                bedtime = Bedtime(LocalTime.of(7, 0), fixedBedtime = null),
                mood = null,
                progress = { it.progressOn(today, { DayRecord() }, HolidaySchedule()) },
                onCreate = { starter, edits -> created = starter.startedOn(today).applyingEdits(edits, today); true },
                onClose = {},
                reminderRow = { _, checkpoint, onRemindChange -> SpecimenReminderRow(checkpoint.remind, onRemindChange) },
                onSendTest = {},
                leaveRequested = false,
                onLeaveAnswered = {},
                onUnsavedWorkChange = {},
            )
        }
        created?.let { checklist ->
            Text(
                text = "Created ${checklist.label} with ${checklist.items.size} item(s)",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Settings' reminder row without its permissions: a switch that answers to the page's draft. */
@Composable
internal fun SpecimenReminderRow(remind: Boolean, onRemindChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Reminder", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(checked = remind, onCheckedChange = onRemindChange)
    }
}

/**
 * The host of Weight's, Eating's or Reading's real page: [demo] held here, and a Save that applies
 * the page's edits from the fixture's today ([applying]) as the store does, so the page wears what
 * it saved. [page] is the view's own page, handed the configuration as it is stored, what Save
 * does, the stand-in switch of its reminder's row and the night its times are read against. The
 * gallery posts nothing, so a test sends nothing.
 */
@Composable
internal fun <C : LoggedView, E> LoggedPageSpecimen(
    demo: C,
    applying: C.(edits: List<E>, today: LocalDate) -> C,
    page: @Composable (stored: C, today: LocalDate, onSave: (List<E>) -> ViewWrite, reminderRow: @Composable (LoggedView, String, (Boolean) -> Unit) -> Unit, bedtime: Bedtime) -> Unit,
) {
    val today = requireNotNull(parseDateKey(FixtureTodayKey))
    var stored by remember { mutableStateOf(demo) }
    ScreenFrame(height = 860.dp) {
        page(
            stored,
            today,
            { edits -> stored = stored.applying(edits, today); ViewWrite.Stored },
            { edited, _, onRemindChange -> SpecimenReminderRow(edited.checkpoint.remind, onRemindChange) },
            Bedtime(LocalTime.of(7, 0), fixedBedtime = null),
        )
    }
}

/**
 * The host of Activity's real page: [demo] and reminder settings held here, the page's two drafts
 * over them, and the one Save over both, which stores the configuration's edits from the
 * fixture's today ([applying]) and then the reminder, with every permission given, as the app
 * does. [page] is the view's own page, handed that Save, the reminder settings as they are stored
 * and what pressing Save does.
 */
@Composable
internal fun <C : ViewLook, E, D : ConfigurationDraft<C, E>> BuiltInViewPageSpecimen(
    feature: DashboardFeature,
    demo: C,
    draft: (C, LocalDate) -> D,
    applying: C.(edits: List<E>, today: LocalDate) -> C,
    page: @Composable (save: BuiltInViewSave<C, E, D>, reminders: ReminderSettings, onSave: () -> Unit) -> Unit,
) {
    val today = requireNotNull(parseDateKey(FixtureTodayKey))
    var configuration by remember { mutableStateOf(demo) }
    var reminders by remember { mutableStateOf(ReminderSettings()) }
    val save = remember { BuiltInViewSave(draft(demo, today), FeatureSettingsDraft()) }
    save.configuration.follow(configuration, today)
    ScreenFrame(height = 860.dp) {
        page(save, reminders) {
            save.save(
                settings = reminders,
                exactAlarmAllowed = true,
                notificationsAllowed = true,
                storeConfiguration = { edits -> configuration = configuration.applying(edits, today); true },
                storeReminder = { updated -> reminders = updated; true },
            )
        }
    }
}

/** Every look the fixture configuration gives: each listed activity with the days of its routine, and Other. */
@Composable
private fun ActivityPalettesSpecimen() {
    val configuration = fixtureAppState.activityRules
    val marks = configuration.activities.flatMap { activity ->
        listOf(ActivityMark.Listed(activity.id)) + activity.routine?.days.orEmpty().map { ActivityMark.RoutineDay(activity.id, it.id) }
    } + ActivityMark.Other
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        marks.chunked(4).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { mark ->
                    val look = configuration.lookOf(mark)
                    IconTile(
                        icon = look.icon,
                        name = configuration.labelOf(mark).resolve(),
                        containerColor = look.containerColor,
                        contentColor = look.contentColor,
                        modifier = Modifier.weight(1f),
                    )
                }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun WeightHistorySpecimen(window: MeasurementHistoryWindow) {
    var selectedDateKey by remember(window) { mutableStateOf(FixtureTodayKey) }
    MeasurementHistory(
        window = window,
        selectedDateKey = selectedDateKey,
        onDateSelected = { selectedDateKey = it },
        words = DemoWeightMeasurement.words(),
        chartSpacing = DemoWeightMeasurement.chartSpacing,
        colors = DemoWeightMeasurement.presentation().colors,
    )
}

@Composable
private fun ActivityHistorySpecimen() {
    var selectedDateKey by remember { mutableStateOf<String?>(FixtureTodayKey) }
    // A token, a rest day and a freeze on the first empty days, so each seal is seen beside filled days.
    val covers = fixtureActivityGrid.days
        .filter { it.activities.isEmpty() && it.dateKey < FixtureTodayKey }
        .zip(listOf(DayCover.Token, DayCover.Rest, DayCover.Freeze))
        .associate { (day, cover) -> day.dateKey to cover }
    CompositionLocalProvider(LocalDayCovers provides mapOf(ActivityTrackerRef to covers)) {
        ActivityHistory(
            grid = fixtureActivityGrid,
            configuration = fixtureAppState.activityRules,
            selectedDateKey = selectedDateKey,
            todayKey = FixtureTodayKey,
            onDayClick = { selectedDateKey = it.dateKey },
        )
    }
}

@Composable
private fun ActivityTimelineSpecimen() {
    var expandedItemId by remember { mutableStateOf<String?>(null) }
    ActivityTimeline(
        items = fixtureActivityTimeline,
        configuration = fixtureAppState.activityRules,
        dayLabel = dateText(FixtureTimelineDateKey).resolve(),
        expandedItemId = expandedItemId,
        onExpandedItemChange = { expandedItemId = it },
        onEdit = {},
        onRemove = { true },
    )
}

/**
 * Imitates a save so the celebration can be judged the way a real successful write triggers it.
 * The completed state sticks, as it does for a once-a-day record, so Reset returns to the action.
 */
@Composable
private fun SavingCompletionSpecimen(
    colors: CompletionButtonColors,
    icon: ImageVector,
    actionLabel: String,
    completedLabel: String,
    testTag: String,
    intensity: CompletionCelebrationIntensity = CompletionCelebrationIntensity.Restrained,
) {
    val scope = rememberCoroutineScope()
    var completed by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf(false) }
    var celebrationSignal by remember { mutableIntStateOf(0) }

    Column {
        CompletionButton(
            actionLabel = actionLabel,
            completedLabel = completedLabel,
            completed = completed,
            onClick = {
                scope.launch {
                    pending = true
                    delay(FakeSaveMillis)
                    pending = false
                    completed = true
                    celebrationSignal++
                }
            },
            icon = icon,
            colors = colors,
            testTag = testTag,
            pending = pending,
            celebrationSignal = celebrationSignal,
            intensity = intensity,
        )
        MomentumButton(
            text = "Reset",
            onClick = {
                pending = false
                completed = false
            },
            variant = MomentumButtonVariant.Quiet,
        )
    }
}

@Composable
private fun TimerCompletionSpecimen(
    intensity: CompletionCelebrationIntensity,
    testTag: String,
) {
    val presentation = DemoReadingTime.presentation()
    SavingCompletionSpecimen(
        colors = presentation.colors.completionButtonColors(),
        icon = presentation.icon,
        actionLabel = "Record minutes",
        completedLabel = "Minutes recorded",
        testTag = testTag,
        intensity = intensity,
    )
}

/** A repeatable save: the signal rises when the fake write lands, and the button returns on its own. */
@Composable
internal fun MomentarySavingSpecimen(
    colors: CompletionButtonColors,
    icon: ImageVector,
    actionLabel: String,
    completedLabel: String,
    testTag: String,
    intensity: CompletionCelebrationIntensity = CompletionCelebrationIntensity.Restrained,
) {
    val scope = rememberCoroutineScope()
    var celebrationSignal by remember { mutableIntStateOf(0) }

    MomentaryCompletionButton(
        actionLabel = actionLabel,
        completedLabel = completedLabel,
        onClick = { scope.launch { delay(FakeSaveMillis); celebrationSignal++ } },
        icon = icon,
        colors = colors,
        testTag = testTag,
        celebrationSignal = celebrationSignal,
        intensity = intensity,
    )
}

private const val FakeSaveMillis = 450L

@Composable
private fun EatingQualityScaleSpecimen() {
    val answers = DemoEatingQuestions.presentedQuestions().first().answers
    SwatchGrid(
        swatches = answers.mapIndexed { index, answer ->
            GalleryColorRole(
                name = answer.label.resolve(),
                color = answerStep(DemoEatingQuestions.hue, index, answers.size).fill,
            )
        },
        columns = 5,
    )
}

@Composable
private fun QuestionHelpSpecimen() {
    var visible by remember { mutableStateOf(false) }
    MomentumButton(
        text = "Open help",
        onClick = { visible = true },
        variant = MomentumButtonVariant.Secondary,
    )
    if (visible) {
        QuestionHelpDialog(text = "Consider the whole day when choosing an answer. Your own guide can explain what each choice means.", testTag = "question_help", onDismiss = { visible = false })
    }
}

/**
 * The real page on fake state: a holiday ahead and two sick days behind. Its calendar offers days
 * ahead for a holiday and days behind for being sick.
 */
@Composable
private fun DaysOffSettingsSpecimen(kind: DayOff) {
    val today = requireNotNull(parseDateKey(FixtureTodayKey))
    var schedule by remember {
        mutableStateOf(
            HolidaySchedule()
                .withHoliday(DayRange(today.plusDays(9), today.plusDays(15)))
                .withSickDays(DayRange(today.minusDays(6), today.minusDays(5))),
        )
    }
    ViewTheme(settingsPresentation().colors) {
        DaysOffSettings(
            kind = kind,
            schedule = schedule,
            todayKey = FixtureTodayKey,
            onChange = { schedule = it; true },
        )
    }
}

@Composable
private fun BackupWarningsSpecimen() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BackupWarningStates.forEach { (name, status) ->
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                BackupWarning(status = status, onOpenSettings = {})
            }
        }
    }
}

private val BackupWarningStates: List<Pair<String, BackupStatus>> = listOf(
    "Needs setup" to BackupStatus(
        configured = false,
        lastVerifiedAt = null,
        pending = false,
        error = null,
    ),
    "Failed" to BackupStatus(
        configured = true,
        lastVerifiedAt = null,
        pending = false,
        error = BackupError.WriteFailed,
    ),
    "Verified, no banner" to BackupStatus(
        configured = true,
        lastVerifiedAt = "2026-09-18T08:00:00Z",
        pending = false,
        error = null,
    ),
)

/**
 * The real screen over fake records, saving into memory so ticking and saving can be tried. It
 * scrolls itself, so it needs a frame of fixed height inside the scrolling gallery.
 */
@Composable
private fun ChecklistScreenSpecimen(
    initial: AppState,
    checklist: ChecklistConfiguration,
    daysOff: HolidaySchedule = HolidaySchedule(),
    /** The tokens the checklist's bank holds, shown on each checkpoint; null is a checklist outside the streak, which shows none. */
    bank: Int? = null,
    /** The checkpoints that hold a token as the specimen opens. */
    setAsideAtFirst: Set<String> = emptySet(),
) {
    var state by remember { mutableStateOf(initial) }
    var setAside by remember { mutableStateOf(setAsideAtFirst) }
    ScreenFrame(height = 760.dp) {
        ChecklistScreen(
            state = state,
            checklist = checklist,
            onStateChange = { updated -> state = updated; true },
            todayKey = FixtureTodayKey,
            daysOff = daysOff,
            testTagPrefix = "gallery_checklist_${checklist.id}",
            // Every open checkpoint can take a token while the bank has one left; one that holds a token can give it back.
            checkpointTokens = bank?.let {
                val left = bank - setAside.size
                CheckpointTokens(
                    left = left,
                    offered = if (left > 0) checklist.checkpoints.mapTo(LinkedHashSet()) { it.id } else setAside,
                    setAside = setAside,
                ) { id, chosen -> setAside = if (chosen) setAside + id else setAside - id }
            },
        )
    }
}

/**
 * [specimens] of Mo's task [need], each in the colors its view was built with: they share the
 * section of their view's kind, whose own colors are a made view's.
 */
private fun ofTask(need: DemoTask, specimens: List<GallerySpecimen>): List<GallerySpecimen> = specimens.map { specimen ->
    GallerySpecimen(specimen.name) { ViewTheme(demoTrackerIdentity(need).colors) { specimen.content() } }
}

private val weightSpecimens: List<GallerySpecimen>
    get() = listOf(
        GallerySpecimen("CompletionButton, Restrained") {
            val presentation = DemoWeightMeasurement.presentation()
            SavingCompletionSpecimen(
                colors = presentation.colors.completionButtonColors(),
                icon = presentation.icon,
                actionLabel = "Save weight",
                completedLabel = "Saved today",
                testTag = "gallery_weight_completion",
            )
        },
        GallerySpecimen("MeasurementHistory") { WeightHistorySpecimen(fixtureWeightWindow) },
    ) + weightTrendChartSpecimens + fixtureWeightEdgeCases.map { (name, window) ->
        GallerySpecimen("MeasurementHistory, $name") { WeightHistorySpecimen(window) }
    } + weightScreenSpecimens

private val supplementsSpecimens: List<GallerySpecimen>
    get() = listOf(
        GallerySpecimen("CompletionButton, Restrained, Supplements") {
            val presentation = DemoSupplementsChecklist.presentation()
            SavingCompletionSpecimen(
                colors = presentation.colors.completionButtonColors(),
                icon = presentation.icon,
                actionLabel = "Take supplements",
                completedLabel = "Taken today",
                testTag = "gallery_supplement_completion",
            )
        },
        GallerySpecimen("ChecklistHistory") {
            var selectedDateKey by remember { mutableStateOf<String?>(FixtureTodayKey) }
            // Every past day not complete holds a token, as a running streak needs.
            val covers = fixtureSupplementHistory
                .filter { it.dateKey < FixtureTodayKey && it.state != ChecklistDayState.COMPLETE }
                .associate { it.dateKey to DayCover.Token }
            ChecklistHistory(
                days = fixtureSupplementHistory,
                identity = DemoSupplementsChecklist.presentation(),
                selectedDateKey = selectedDateKey,
                todayKey = FixtureTodayKey,
                testTagPrefix = "gallery_supplement",
                onDayClick = { selectedDateKey = it.dateKey },
                covers = covers,
            )
        },
        GallerySpecimen("ChecklistScreen, fixed time") { ChecklistScreenSpecimen(fixtureAppState, DemoSupplementsChecklist) },
    )

private val timerPresetSpecimens: List<GallerySpecimen>
    get() = listOf(
        GallerySpecimen("CompletionButton, Strong") {
            TimerCompletionSpecimen(CompletionCelebrationIntensity.Strong, "gallery_strong")
        },
        GallerySpecimen("CompletionButton, Restrained") {
            TimerCompletionSpecimen(CompletionCelebrationIntensity.Restrained, "gallery_restrained")
        },
        GallerySpecimen("CompletionButton, already recorded") {
            val presentation = DemoReadingTime.presentation()
            CompletionButton(
                actionLabel = "Record minutes",
                completedLabel = "Minutes recorded",
                completed = true,
                onClick = {},
                icon = presentation.icon,
                colors = presentation.colors.completionButtonColors(),
                testTag = "gallery_completed",
            )
        },
        GallerySpecimen("TimeHistory, Reading") {
            var selectedDateKey by remember { mutableStateOf<String?>(FixtureTodayKey) }
            ViewTheme(DemoReadingTime.presentation().colors) {
                TimeHistory(
                    window = fixtureReadingWindow,
                    words = DemoReadingTime.words(),
                    selectedDateKey = selectedDateKey,
                    todayKey = FixtureTodayKey,
                    testTagPrefix = "reading",
                    onDayClick = { selectedDateKey = it.dateKey },
                )
            }
        },
    ) + readingScreenSpecimens

private val careSpecimens: List<GallerySpecimen>
    get() = listOf(
        GallerySpecimen("CompletionButton, Restrained, Care") {
            val presentation = DemoCareChecklist.presentation()
            SavingCompletionSpecimen(
                colors = presentation.colors.completionButtonColors(),
                icon = presentation.icon,
                actionLabel = "Log care",
                completedLabel = "Done today",
                testTag = "gallery_care_completion",
            )
        },
        // Care runs on the shared checklist screen: two checkpoints, each saved on its own.
        GallerySpecimen("ChecklistScreen, two checkpoints") { ChecklistScreenSpecimen(fixtureAppState, DemoCareChecklist) },
        // In the streak, each checkpoint carries the token and the number left: dimmed with none, a switch with one, pressed in while it holds one.
        GallerySpecimen("ChecklistScreen, two checkpoints, no tokens") { ChecklistScreenSpecimen(fixtureAppState, DemoCareChecklist, bank = 0) },
        GallerySpecimen("ChecklistScreen, two checkpoints, a token to set aside for one") { ChecklistScreenSpecimen(fixtureAppState, DemoCareChecklist, bank = 1) },
        GallerySpecimen("ChecklistScreen, two checkpoints, a token set aside for the first") {
            ChecklistScreenSpecimen(fixtureAppState, DemoCareChecklist, bank = 1, setAsideAtFirst = setOf(DemoCareChecklist.checkpoints.first().id))
        },
    )

private val eatingSpecimens: List<GallerySpecimen>
    get() = listOf(
        GallerySpecimen("MomentaryCompletionButton, Restrained") {
            val presentation = DemoEatingQuestions.presentation()
            MomentarySavingSpecimen(
                colors = presentation.colors.completionButtonColors(),
                icon = presentation.icon,
                actionLabel = "Log meal",
                completedLabel = "Meal logged",
                testTag = "gallery_eating_momentary",
            )
        },
        GallerySpecimen("Quality scale") { EatingQualityScaleSpecimen() },
        GallerySpecimen("QuestionsHistory, Eating") {
            var selectedDateKey by remember { mutableStateOf<String?>(FixtureTodayKey) }
            QuestionsHistory(
                window = fixtureEatingWindow,
                asked = DemoEatingQuestions.scales,
                hue = DemoEatingQuestions.hue,
                words = QuestionsWords(DemoEatingQuestions),
                selectedDateKey = selectedDateKey,
                todayKey = FixtureTodayKey,
                testTagPrefix = "questions_${DemoEatingQuestions.id}",
                onDayClick = { selectedDateKey = it.dateKey },
            )
        },
        GallerySpecimen("QuestionHelpDialog") { QuestionHelpSpecimen() },
    ) + eatingScreenSpecimens

/** Fake view content keeps Home specimens on the real registry-driven launchpad. */
private fun homeFixtureViews(
    state: AppState,
    mood: com.example.workouttracker.domain.mo.MoMood,
    visibility: FeatureVisibilitySettings = FeatureVisibilitySettings(disabledFeatures = emptySet()),
): List<com.example.workouttracker.feature.tracker.TrackerView> {
    val day = requireNotNull(parseDateKey(FixtureTodayKey))
    fun fixture(
        ref: com.example.workouttracker.domain.TrackerRef,
        label: String,
        tag: String,
        order: Int,
        visible: Boolean,
        status: com.example.workouttracker.feature.tracker.TrackerHomeStatus,
        identityOf: @Composable () -> com.example.workouttracker.core.ui.ViewIdentity,
    ) = object : com.example.workouttracker.feature.tracker.TrackerView {
        override val ref = ref
        override val label = label
        override val tagName = tag
        override val visible = visible
        override val homeOrder = order
        override val bankOrder = order + 100
        override val bankTagName = "streak_$tag"
        override val tokenProgressInMinutes = ref.type.value == "time"
        override val homeStatus = status
        override val reminders = emptyList<com.example.workouttracker.feature.tracker.TrackerReminderCommand>()
        override val opensOnReminderDate = true
        override fun setVisible(visible: Boolean) = false
        @Composable override fun identity() = identityOf()
        @Composable override fun Screen(host: com.example.workouttracker.feature.tracker.TrackerScreenHost) = Unit
        @Composable override fun Settings(host: com.example.workouttracker.feature.tracker.TrackerSettingsHost) = Unit
    }
    val checklists = state.checklists.map { view ->
        fixture(
            com.example.workouttracker.domain.TrackerRef(com.example.workouttracker.domain.TrackerTypeId("checklist"), view.id), view.label,
            "checklist_${view.id}",
            100,
            visibility.isChecklistVisible(view.id) && view.isInUse(day),
            com.example.workouttracker.app.trackerHomeStatus(mood.trackerProgress[com.example.workouttracker.domain.TrackerRef(com.example.workouttracker.domain.TrackerTypeId("checklist"), view.id)]),
        ) { view.presentation() }
    }
    return checklists + state.loggedViews.map { view ->
        fixture(
            view.ref.trackerRef, view.label,
            "${view.kind.tagName}_${view.id}",
            200,
            visibility.shows(view.ref),
            com.example.workouttracker.app.trackerHomeStatus(mood.trackerProgress[view.ref.trackerRef]),
        ) { view.presentation() }
    }
}

private fun fixtureActivityStatus(mood: com.example.workouttracker.domain.mo.MoMood) =
    com.example.workouttracker.app.trackerHomeStatus(mood.trackerProgress[com.example.workouttracker.domain.ActivityTrackerRef])

@Composable
private fun GalleryStreakScreen(
    state: AppState,
    mood: com.example.workouttracker.domain.mo.MoMood,
    onSetAside: (com.example.workouttracker.domain.TrackerRef, Boolean) -> Unit = { _, _ -> },
) {
    val views = homeFixtureViews(state, mood)
    fun reservation(ref: com.example.workouttracker.domain.TrackerRef) = mood.bankReservation(ref) { onSetAside(ref, it) }
    val activityRef = com.example.workouttracker.domain.ActivityTrackerRef
    val substanceRef = com.example.workouttracker.domain.SubstancesTrackerRef
    val banks = views.sortedBy { it.bankOrder }.map { view ->
        view.bankOrder to com.example.workouttracker.feature.mo.TrackerBankPresentation(
            view.ref, view.label, view.bankTagName, view.tokenProgressInMinutes, reservation(view.ref),
        )
    } + listOf(
        0 to com.example.workouttracker.feature.mo.TrackerBankPresentation(activityRef, state.activity?.label ?: stringResource(R.string.feature_activity), "streak_token_activity", reservation = reservation(activityRef), restDays = (mood.streak.freeDaysLeftByTracker[activityRef] ?: 0).takeIf { mood.plannedRestDays() > 0 }),
        200 to com.example.workouttracker.feature.mo.TrackerBankPresentation(substanceRef, stringResource(R.string.feature_substances), "streak_substances", reservation = reservation(substanceRef)),
    )
    CompositionLocalProvider(com.example.workouttracker.core.ui.LocalTrackerIdentity provides { ref ->
        views.firstOrNull { it.ref == ref }?.identity() ?: demoTrackerIdentity(ref)
    }) {
        MoStreakScreen(mood, banks = banks.sortedBy { it.first }.map { it.second })
    }
}

private val TimerWeeksState = AppState(
    time = listOf(DemoPiano),
    timeLogs = listOf(
        TimeLog(DemoPiano.id, "2026-08-25", 1, minutes = 50),
        TimeLog(DemoPiano.id, "2026-08-28", 1, minutes = 50),
        TimeLog(DemoPiano.id, "2026-09-01", 1, minutes = 30),
        TimeLog(DemoPiano.id, "2026-09-03", 1, minutes = null),
        TimeLog(DemoPiano.id, "2026-09-08", 1, minutes = 20),
        TimeLog(DemoPiano.id, "2026-09-15", 1, minutes = 45),
    ),
)

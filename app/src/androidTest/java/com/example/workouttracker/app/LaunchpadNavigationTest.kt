package com.example.workouttracker.app

import androidx.compose.ui.test.assert

import com.example.workouttracker.domain.measurement.recordsOf

import com.example.workouttracker.testfixtures.ReadingTimeId
import com.example.workouttracker.domain.time.TimeLog
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.workouttracker.R
import com.example.workouttracker.domain.measurement.asMeasurementLogs
import com.example.workouttracker.testing.appString
import com.example.workouttracker.testing.assertInputEquals
import com.example.workouttracker.domain.AppState
import com.example.workouttracker.domain.activity.ActivityEntry
import com.example.workouttracker.domain.activity.ListedActivity
import com.example.workouttracker.domain.activity.BoulderingActivityId
import com.example.workouttracker.domain.activity.GymActivityId
import com.example.workouttracker.domain.activity.SeededActivityRules
import com.example.workouttracker.domain.activity.SeededGym
import com.example.workouttracker.domain.activity.activityRules
import com.example.workouttracker.domain.checklist.ViewIcon
import com.example.workouttracker.domain.activity.routine.ExerciseLog
import com.example.workouttracker.domain.activity.routine.LoggedSet
import com.example.workouttracker.domain.activity.routine.SeededGymDayIds
import com.example.workouttracker.domain.activity.routine.WorkoutSession
import com.example.workouttracker.careState
import com.example.workouttracker.careDone
import com.example.workouttracker.testfixtures.LegacyCareAction
import com.example.workouttracker.testfixtures.LegacyCareSession
import com.example.workouttracker.domain.measurement.MeasurementRecord
import com.example.workouttracker.domain.reminder.ReminderSettings
import com.example.workouttracker.domain.reminder.ReminderTime
import com.example.workouttracker.domain.reminder.WorkoutReminderSettings
import com.example.workouttracker.domain.settings.DashboardFeature
import com.example.workouttracker.domain.settings.FeatureVisibilitySettings
import com.example.workouttracker.core.time.currentHabitDate
import com.example.workouttracker.testfixtures.StandardSupplementIds
import com.example.workouttracker.AllSupplementIds
import com.example.workouttracker.YearRoundSupplementIds
import com.example.workouttracker.supplementDay
import com.example.workouttracker.supplementTicks
import com.example.workouttracker.supplementsAppState
import com.example.workouttracker.TestSecondChecklist
import com.example.workouttracker.TestSupplementsChecklist
import com.example.workouttracker.TestCareChecklist
import com.example.workouttracker.TestEatingQuestions
import com.example.workouttracker.TestReadingTime
import com.example.workouttracker.TestWeightMeasurement
import com.example.workouttracker.domain.checklist.rulesOn
import com.example.workouttracker.domain.substance.SubstanceAmountUnit
import com.example.workouttracker.domain.substance.SubstanceKind
import com.example.workouttracker.domain.substance.SubstanceLog
import com.example.workouttracker.domain.substance.SubstanceSettings
import com.example.workouttracker.core.time.currentDateKey
import com.example.workouttracker.core.format.dateText
import com.example.workouttracker.core.text.resolve
import com.example.workouttracker.core.ui.theme.WorkoutTrackerTheme
import com.example.workouttracker.feature.logactivity.gym.withReplacedWorkoutSets
import com.example.workouttracker.feature.logactivity.gym.planned
import com.example.workouttracker.feature.logactivity.gym.withSavedWorkoutSet
import com.example.workouttracker.feature.logactivity.activityHistoryGrid
import com.example.workouttracker.feature.time.TimeScreenFixture
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import com.example.workouttracker.testfixtures.defaultReadingTime
import com.example.workouttracker.testfixtures.defaultEatingQuestions

class LaunchpadNavigationTest {
    @get:Rule
    val composeRule = createComposeRule()

    /** Weight, Eating, Reading, Supplements and Care, each of which Home and Settings show only while it exists. */
    private val everyView = AppState(
        checklists = listOf(TestSupplementsChecklist, TestCareChecklist),
        measurements = listOf(TestWeightMeasurement),
        questions = listOf(TestEatingQuestions),
        time = listOf(TestReadingTime),
    )

    @Test
    fun homeHidesDisabledFeaturesAndKeepsSettings() {
        composeRule.setContent {
            WorkoutTrackerTheme {
                MomentumApp(
                    state = everyView,
                    onStateChange = { true },
                    onActivitySave = ActivitySaveNotExpected,
                    featureVisibility = FeatureVisibilitySettings(hiddenMeasurements = setOf("weight"), hiddenTime = setOf("reading")),
                )
            }
        }

        composeRule.onNodeWithTag("home_measurement_weight").assertDoesNotExist()
        composeRule.onNodeWithTag("home_time_reading").assertDoesNotExist()
        composeRule.onNodeWithTag("home_checklist_care").assertIsDisplayed()
        composeRule.onNodeWithTag("home_statistics").assertIsDisplayed()
        composeRule.onNodeWithTag("home_settings").assertIsDisplayed()
    }

    /** Statistics has no switch: a stored "off" from before is read by nothing, and it opens. */
    @Test
    fun statisticsOpensFromHomeEvenWhereItsOldSwitchIsOff() {
        composeRule.setContent {
            WorkoutTrackerTheme {
                MomentumApp(
                    state = AppState(),
                    onStateChange = { true },
                    onActivitySave = ActivitySaveNotExpected,
                    featureVisibility = FeatureVisibilitySettings(),
                )
            }
        }

        composeRule.onNodeWithTag("home_statistics").performClick()
        composeRule.onNodeWithTag("statistics_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("top_back").performClick()
        composeRule.onNodeWithTag("home_settings").performClick()
        composeRule.onNodeWithTag("settings_feature_statistics").assertDoesNotExist()
    }

    @Test
    fun careSessionSavesAreIndependentAndPreserveTheOtherPendingDraft() {
        val today = currentDateKey()
        var persistedState = careState()
        var saveCalls = 0
        composeRule.setContent {
            var currentState by remember { mutableStateOf(careState()) }
            WorkoutTrackerTheme {
                MomentumApp(
                    state = currentState,
                    onStateChange = { updated ->
                        saveCalls += 1
                        persistedState = updated
                        currentState = updated
                        true
                    },
                    onActivitySave = ActivitySaveNotExpected,
                )
            }
        }

        composeRule.onNodeWithTag("home_checklist_care").performClick()
        composeRule.onNodeWithText("Care").assertIsDisplayed()
        composeRule.onNode(
            hasTestTag("checklist_care_morning_card") and
                hasAnyDescendant(hasTestTag("checklist_care_morning_save")),
            useUnmergedTree = true,
        ).assertExists()
        composeRule.onNode(
            hasTestTag("checklist_care_evening_card") and
                hasAnyDescendant(hasTestTag("checklist_care_evening_save")),
            useUnmergedTree = true,
        ).assertExists()
        composeRule.onNodeWithTag("checklist_care_morning_toothbrushing").performClick()
        composeRule.onNodeWithTag("checklist_care_evening_hair_foam").performClick()

        composeRule.runOnIdle {
            assertEquals(0, saveCalls)
            assertTrue(persistedState.careDone(today).isEmpty())
        }
        composeRule.onNodeWithTag("checklist_care_morning_save").performClick()

        composeRule.runOnIdle {
            assertEquals(1, saveCalls)
            assertEquals(
                setOf(LegacyCareSession.MORNING to LegacyCareAction.TOOTHBRUSHING),
                persistedState.careDone(today),
            )
        }
        composeRule.onNodeWithTag("checklist_care_evening_hair_foam").assertIsOn()
        composeRule.onNodeWithTag("checklist_care_evening_save").performClick()
        composeRule.runOnIdle {
            assertEquals(2, saveCalls)
            assertEquals(
                setOf(
                    LegacyCareSession.MORNING to LegacyCareAction.TOOTHBRUSHING,
                    LegacyCareSession.EVENING to LegacyCareAction.HAIR_FOAM,
                ),
                persistedState.careDone(today),
            )
        }
    }

    @Test
    fun failedCareSaveKeepsDraftRetryableAndBackDiscardsIt() {
        var saveCalls = 0
        composeRule.setContent {
            WorkoutTrackerTheme {
                MomentumApp(
                    state = careState(),
                    onStateChange = {
                        saveCalls += 1
                        false
                    },
                    onActivitySave = ActivitySaveNotExpected,
                )
            }
        }

        composeRule.onNodeWithTag("home_checklist_care").performClick()
        composeRule.onNodeWithTag("checklist_care_morning_toothbrushing").performClick()
        composeRule.onNodeWithTag("checklist_care_evening_hair_foam").performClick()
        composeRule.onNodeWithTag("checklist_care_morning_save").performClick()

        composeRule.onNodeWithTag("checklist_care_morning_save_error").assertIsDisplayed()
        composeRule.onNodeWithTag("checklist_care_evening_save_error").assertDoesNotExist()
        composeRule.onNodeWithTag("checklist_care_morning_toothbrushing").assertIsOn()
        composeRule.onNodeWithTag("checklist_care_evening_hair_foam").assertIsOn()
        composeRule.onNodeWithTag("checklist_care_morning_save").performClick()
        composeRule.runOnIdle { assertEquals(2, saveCalls) }

        composeRule.onNodeWithTag("top_back").performClick()
        composeRule.onNodeWithTag("home_checklist_care").performClick()
        composeRule.onNodeWithTag("checklist_care_morning_toothbrushing").assertIsOff()
        composeRule.onNodeWithTag("checklist_care_evening_hair_foam").assertIsOff()
        composeRule.onNodeWithTag("checklist_care_morning_save_error").assertDoesNotExist()
    }

    @Test
    fun careHistoryCorrectionSavesTheExactDayOnlyOnConfirm() {
        val today = currentDateKey()
        val past = java.time.LocalDate.parse(today).minusDays(1).toString()
        val pastDone = LegacyCareSession.MORNING to LegacyCareAction.TOOTHBRUSHING
        val todayDone = LegacyCareSession.EVENING to LegacyCareAction.HAIR_FOAM
        val initial = careState(past to pastDone, today to todayDone)
        var persistedState = initial
        var saveCalls = 0
        composeRule.setContent {
            var currentState by remember { mutableStateOf(persistedState) }
            WorkoutTrackerTheme {
                MomentumApp(
                    state = currentState,
                    onStateChange = { updated ->
                        saveCalls += 1
                        persistedState = updated
                        currentState = updated
                        true
                    },
                    onActivitySave = ActivitySaveNotExpected,
                )
            }
        }

        composeRule.onNodeWithTag("home_checklist_care").performClick()
        composeRule.onNodeWithTag("checklist_care_history_$past").performScrollTo().performClick()
        composeRule.onNodeWithTag("checklist_care_morning_toothbrushing")
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithTag("checklist_care_evening_hair_foam")
            .performScrollTo()
            .performClick()
        composeRule.runOnIdle {
            assertEquals(0, saveCalls)
            assertEquals(initial, persistedState)
        }
        composeRule.onNodeWithTag("checklist_care_evening_save").performScrollTo().performClick()

        composeRule.runOnIdle {
            assertEquals(1, saveCalls)
            assertEquals(setOf(todayDone), persistedState.careDone(today))
            assertEquals(
                setOf(
                    LegacyCareSession.MORNING to LegacyCareAction.TOOTHBRUSHING,
                    LegacyCareSession.EVENING to LegacyCareAction.HAIR_FOAM,
                ),
                persistedState.careDone(past),
            )
        }
        composeRule.onNodeWithTag("checklist_care_morning_toothbrushing").assertIsOff()
        composeRule.onNodeWithTag("checklist_care_morning_save").performScrollTo().performClick()

        composeRule.runOnIdle {
            assertEquals(2, saveCalls)
            assertEquals(setOf(todayDone), persistedState.careDone(today))
            assertEquals(setOf(LegacyCareSession.EVENING to LegacyCareAction.HAIR_FOAM), persistedState.careDone(past))
        }
    }

    @Test
    fun hiddenReminderDeepLinkIsConsumedAtHome() {
        var request by mutableStateOf<AppNavigationRequest?>(
            AppNavigationRequest(destination = AppDestination.Time, loggedViewId = "reading"),
        )
        var consumed = 0
        composeRule.setContent {
            WorkoutTrackerTheme {
                MomentumApp(
                    state = everyView,
                    onStateChange = { true },
                    onActivitySave = ActivitySaveNotExpected,
                    featureVisibility = FeatureVisibilitySettings(disabledFeatures = emptySet(), hiddenTime = setOf("reading")),
                    requestedNavigation = request,
                    onDestinationConsumed = {
                        consumed += 1
                        request = null
                    },
                )
            }
        }

        composeRule.onNodeWithTag("home_settings").assertIsDisplayed()
        composeRule.onNodeWithText("Reading").assertDoesNotExist()
        composeRule.runOnIdle { assertEquals(1, consumed) }
    }

    @Test
    fun featureSwitchHidesTheTileWithoutChangingReminderPreference() {
        val readingReminds = everyView.copy(time = listOf(defaultReadingTime(label = "Reading", remind = true)))
        composeRule.setContent {
            var visibility by remember { mutableStateOf(FeatureVisibilitySettings()) }
            WorkoutTrackerTheme {
                MomentumApp(
                    state = readingReminds,
                    onStateChange = { true },
                    onActivitySave = ActivitySaveNotExpected,
                    featureVisibility = visibility,
                    onFeatureVisibilityChange = { updated ->
                        visibility = updated
                        true
                    },
                )
            }
        }

        composeRule.onNodeWithTag("home_settings").performClick()
        composeRule.onNodeWithTag("settings_feature_time_reading")
            .performScrollTo()
            .assertIsOn()
            .performClick()
        composeRule.onNodeWithTag("settings_group_time_reading").performClick()
        composeRule.onNodeWithTag("time_settings_tab_reminders").performScrollTo().performClick()
        composeRule.onNodeWithTag("time_settings_reminder")
            .performScrollTo()
            .assertIsOn()
            .assertIsNotEnabled()
        composeRule.onNodeWithTag("top_back").performClick()
        composeRule.onNodeWithTag("top_back").performClick()
        composeRule.onNodeWithTag("home_time_reading").assertDoesNotExist()
    }

    /** Supplements and Care are checklists: without a configuration they are deleted, with no tile and no row. */
    @Test
    fun supplementsAndCareWithoutAConfigurationAreNowhere() {
        composeRule.setContent {
            WorkoutTrackerTheme {
                MomentumApp(state = AppState(), onStateChange = { true }, onActivitySave = ActivitySaveNotExpected)
            }
        }

        listOf("checklist_supplements", "checklist_care").forEach { name -> composeRule.onNodeWithTag("home_$name").assertDoesNotExist() }
        composeRule.onNodeWithTag("home_settings").performClick()
        listOf("checklist_supplements", "checklist_care").forEach { name -> composeRule.onNodeWithTag("settings_group_$name").assertDoesNotExist() }
    }

    /** Each view's reminders are on its own page, Care's in its checkpoints, each with when it speaks. */
    @Test
    fun eachViewKeepsItsRemindersOnItsOwnPage() {
        composeRule.setContent {
            WorkoutTrackerTheme {
                MomentumApp(
                    state = careState().let { state ->
                        state.copy(questions = listOf(defaultEatingQuestions(label = "Eating", remind = true)), checklists = state.checklists.map { care -> care.copy(checkpoints = care.checkpoints.map { if (it.id == "morning") it.copy(remind = true) else it }) })
                    },
                    onStateChange = { true },
                    onActivitySave = ActivitySaveNotExpected,
                )
            }
        }

        composeRule.onNodeWithTag("home_settings").performClick()
        composeRule.onNodeWithTag("settings_workout_reminder").assertDoesNotExist()
        composeRule.onNodeWithTag("settings_group_activity").performClick()
        composeRule.onNodeWithTag("feature_settings_tab_reminders").performScrollTo().performClick()
        composeRule.onNodeWithTag("settings_workout_reminder").assertExists()
        composeRule.onNodeWithTag("top_back").performClick()

        composeRule.onNodeWithTag("settings_group_checklist_care").performScrollTo().performClick()
        composeRule.onNodeWithTag("checklist_editor_tab_reminders").performClick()
        composeRule.onNodeWithTag("checklist_editor_checkpoint_morning_reminder").performScrollTo().assertIsOn()
        composeRule.onNodeWithTag("checklist_editor_checkpoint_morning_timing")
            .assertTextEquals("5 min after the next alarm · 08:05 fallback")
        composeRule.onNodeWithTag("checklist_editor_checkpoint_evening_reminder").performScrollTo().assertIsOff()
        composeRule.onNodeWithTag("checklist_editor_checkpoint_evening_timing")
            .assertTextContains("15 min before sleep", substring = true)
        composeRule.onNodeWithTag("top_back").performClick()

        composeRule.onNodeWithTag("settings_group_questions_eating").performScrollTo().performClick()
        composeRule.onNodeWithTag("questions_settings_tab_reminders").performScrollTo().performClick()
        composeRule.onNodeWithTag("questions_settings_reminder").performScrollTo().assertIsOn()
        composeRule.onNodeWithTag("questions_settings_reminder_timing")
            .assertTextEquals("15 min after the next alarm · 08:15 fallback")
    }

    @Test
    fun readingTodayStartsATimerAndAnUntimedMomentSavesNothing() {
        var saveCalls = 0
        composeRule.setContent {
            var currentState by remember { mutableStateOf(AppState(time = listOf(TestReadingTime))) }
            WorkoutTrackerTheme {
                MomentumApp(
                    state = currentState,
                    onStateChange = { updated ->
                        saveCalls += 1
                        currentState = updated
                        true
                    },
                    onActivitySave = ActivitySaveNotExpected,
                )
            }
        }

        composeRule.onNodeWithTag("home_time_reading").performClick()
        composeRule.onNodeWithTag("time_reading_history").assertExists().performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("time_reading_start").performClick()
        // The one-time question about Do Not Disturb only appears on a fresh install.
        if (composeRule.onAllNodesWithTag("time_reading_focus_without").fetchSemanticsNodes().isNotEmpty()) {
            composeRule.onNodeWithTag("time_reading_focus_without").performClick()
        }
        composeRule.onNodeWithTag("time_reading_session").assertIsDisplayed()
        composeRule.onNodeWithTag("time_reading_session_done").performClick()
        composeRule.onNodeWithTag("time_reading_session_note").assertTextContains("nothing saved", substring = true)
        composeRule.onNodeWithTag("time_reading_week_minutes").assertTextContains("0 / 60 min")
        composeRule.runOnIdle { assertEquals(0, saveCalls) }
        composeRule.onNodeWithTag("top_back").performClick()
        composeRule.onNodeWithTag("home_time_reading").assertIsDisplayed()
    }

    @Test
    fun failedReadingSaveDoesNotClaimTodayWasRecorded() {
        composeRule.setContent {
            WorkoutTrackerTheme {
                MomentumApp(state = AppState(time = listOf(TestReadingTime)), onStateChange = { false }, onActivitySave = ActivitySaveNotExpected)
            }
        }
        composeRule.onNodeWithTag("home_time_reading").performClick()
        // A past day takes typed minutes at once.
        val yesterday = java.time.LocalDate.parse(currentDateKey()).minusDays(1)
        composeRule.onNodeWithTag("time_reading_history_$yesterday").performScrollTo().performClick()
        composeRule.onNodeWithTag("time_reading_minutes_input").performTextReplacement("15")
        composeRule.onNodeWithTag("time_reading_log_today").performClick()
        composeRule.onNodeWithTag("time_reading_save_error").assertIsDisplayed()
        composeRule.onNodeWithTag("time_reading_week_minutes").assertTextContains("0 / 60 min")
    }

    @Test
    fun readingReminderDeepLinkOpensTheSameReadingScreenOnce() {
        var request by mutableStateOf<AppNavigationRequest?>(
            AppNavigationRequest(destination = AppDestination.Time, loggedViewId = "reading"),
        )
        var consumed = 0
        composeRule.setContent {
            WorkoutTrackerTheme {
                MomentumApp(
                    state = AppState(time = listOf(TestReadingTime)),
                    onStateChange = { true },
                    onActivitySave = ActivitySaveNotExpected,
                    requestedNavigation = request,
                    onDestinationConsumed = {
                        consumed += 1
                        request = null
                    },
                )
            }
        }
        composeRule.runOnIdle {
            assertEquals(1, consumed)
            assertEquals(null, request)
        }
        composeRule.onNodeWithText("Reading").assertIsDisplayed()
        composeRule.onNodeWithTag("time_reading_history").assertExists().performScrollTo().assertIsDisplayed()
        composeRule.runOnIdle { assertEquals(1, consumed) }
        composeRule.onNodeWithTag("top_back").performClick()
        composeRule.onNodeWithTag("home_time_reading").assertIsDisplayed()
        composeRule.runOnIdle { assertEquals(1, consumed) }
    }

    @Test
    fun eatingReminderDeepLinkKeepsItsIntendedReflectionDateAfterConsumption() {
        var request by mutableStateOf<AppNavigationRequest?>(
            AppNavigationRequest(
                destination = AppDestination.Questions,
                loggedViewId = "eating",
                loggedDateKey = "2026-09-15",
            ),
        )
        var consumed = 0
        composeRule.setContent {
            WorkoutTrackerTheme {
                MomentumApp(
                    state = AppState(questions = listOf(TestEatingQuestions)),
                    onStateChange = { true },
                    onActivitySave = ActivitySaveNotExpected,
                    requestedNavigation = request,
                    onDestinationConsumed = {
                        consumed += 1
                        request = null
                    },
                )
            }
        }

        composeRule.runOnIdle {
            assertEquals(1, consumed)
            assertEquals(null, request)
        }
        composeRule.onNodeWithTag("questions_eating_selected_day").assertTextEquals("Tue, Sep 15")
        composeRule.runOnIdle { assertEquals(1, consumed) }
        composeRule.onNodeWithTag("questions_eating_selected_day").assertTextEquals("Tue, Sep 15")
        composeRule.onNodeWithTag("top_back").performClick()
        composeRule.onNodeWithTag("home_questions_eating").assertIsDisplayed()
        composeRule.onNodeWithTag("questions_eating_save").assertDoesNotExist()
        composeRule.runOnIdle { assertEquals(1, consumed) }
    }

    @Test
    fun storedQuestionHelpIsAccessibleAndOnlyShownForQuestionsThatHaveHelp() {
        val configured = TestEatingQuestions.copy(
            label = "Daily reflection",
            questions = TestEatingQuestions.questions.mapIndexed { index, question ->
                question.copy(help = if (index == 0) "Use your own whole-day perspective when choosing an answer." else null)
            },
        )
        composeRule.setContent {
            WorkoutTrackerTheme {
                MomentumApp(
                    state = AppState(questions = listOf(configured)),
                    onStateChange = { true },
                    onActivitySave = ActivitySaveNotExpected,
                )
            }
        }
        composeRule.onNodeWithTag("home_questions_eating").performClick()
        composeRule.onNodeWithTag("questions_eating_quality_help").performScrollTo().assertIsDisplayed()
            .assert(hasContentDescription(appString(R.string.questions_help_description))).performClick()
        composeRule.onNodeWithTag("questions_eating_quality_help_dialog").assertIsDisplayed()
        composeRule.onNodeWithText(configured.questions.first().help!!).assertIsDisplayed()
        composeRule.onNodeWithTag("questions_eating_quality_help_close").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("questions_eating_quality_help_dialog").assertDoesNotExist()
        composeRule.onNodeWithTag("questions_eating_amount_help").assertDoesNotExist()
    }

    @Test
    fun settingsShowsReadingAsAnIndependentDisabledAlarmAwareReminder() {
        composeRule.setContent {
            WorkoutTrackerTheme {
                MomentumApp(
                    state = everyView,
                    onStateChange = { true },
                    onActivitySave = ActivitySaveNotExpected,
                )
            }
        }
        composeRule.onNodeWithTag("home_settings").performClick()
        composeRule.onNodeWithTag("settings_open_sleep").performClick()
        composeRule.onNodeWithTag("settings_fallback_alarm_time")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Wake time at 08:00")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithTag("top_back").performClick()
        composeRule.onNodeWithTag("settings_group_measurement_weight").performScrollTo().performClick()
        composeRule.onNodeWithTag("measurement_settings_tab_reminders").performScrollTo().performClick()
        composeRule.onNodeWithTag("measurement_settings_reminder_timing")
            .assertTextContains("5 min after the next alarm · 08:05 fallback", substring = true)
        composeRule.onNodeWithTag("top_back").performClick()
        composeRule.onNodeWithTag("settings_group_checklist_supplements").performScrollTo().performClick()
        composeRule.onNodeWithTag("checklist_editor_tab_reminders").performClick()
        composeRule.onNodeWithTag("checklist_editor_checkpoint_midday_timing")
            .performScrollTo()
            .assertTextContains("Every day at 12:30", substring = true)
        composeRule.onNodeWithTag("top_back").performClick()
        composeRule.onNodeWithTag("settings_group_time_reading").performScrollTo().performClick()
        composeRule.onNodeWithTag("time_settings_tab_reminders").performScrollTo().performClick()
        composeRule.onNodeWithTag("time_settings_reminder").assertIsOff()
        composeRule.onNodeWithTag("time_settings_reminder_timing")
            .assertTextContains("Every day at 18:00", substring = true)
    }

    @Test
    fun readingScreenMovesToTheNewDayWithoutARecordMutation() {
        var todayKey by mutableStateOf("2026-09-16")
        val unchangedRecords = listOf(TimeLog(ReadingTimeId, "2026-09-16", 100L))
        composeRule.setContent {
            WorkoutTrackerTheme {
                TimeScreenFixture(
                    records = unchangedRecords,
                    onAddMinutes = { _, _ -> true },
                    onRemoveDate = { true },
                    todayKey = todayKey,
                )
            }
        }
        composeRule.onNodeWithTag("time_reading_history_2026-09-16").assertIsSelected()
        composeRule.runOnIdle { todayKey = "2026-09-17" }
        composeRule.onNodeWithTag("time_reading_history_2026-09-17").assertIsSelected()
        composeRule.onNodeWithTag("time_reading_selected_day").assertDoesNotExist()
        composeRule.onNodeWithTag("time_reading_history_2026-09-17").assertIsDisplayed()
    }

    @Test
    fun substancesTileLogsThreeKindsWithoutReplacingSameDayRecords() {
        var persistedState = AppState(substances = SubstanceSettings.Legacy)
        composeRule.setContent {
            var currentState by remember { mutableStateOf(AppState(substances = SubstanceSettings.Legacy)) }
            WorkoutTrackerTheme {
                MomentumApp(
                    state = currentState,
                    onStateChange = { updated ->
                        persistedState = updated
                        currentState = updated
                        true
                    },
                    onActivitySave = ActivitySaveNotExpected,
                )
            }
        }
        composeRule.onNodeWithTag("home_substances").performClick()
        composeRule.onNodeWithText("Substances").assertIsDisplayed()
        composeRule.onNodeWithTag("substance_history").assertIsDisplayed()
        composeRule.onNodeWithTag("substance_choice_cannabis").performClick()
        composeRule.onNodeWithTag("substance_optional_details").performClick()
        composeRule.onNodeWithTag("substance_amount_new-cannabis-1")
            .performTextReplacement("0.5")
        composeRule.onNodeWithTag("substance_choice_alcohol").performClick()
        composeRule.onNodeWithTag("substance_amount_new-alcohol-1")
            .performTextReplacement("2")
        composeRule.onNodeWithTag("substance_choice_tobacco").performClick()
        composeRule.onNodeWithTag("substance_save").performScrollTo().performClick()
        composeRule.runOnIdle {
            assertEquals(3, persistedState.substanceLogs.size)
            assertEquals(
                setOf(SubstanceKind.CANNABIS.id, SubstanceKind.ALCOHOL.id, SubstanceKind.TOBACCO.id),
                persistedState.substanceLogs.mapTo(mutableSetOf()) { it.substanceId },
            )
            assertEquals(
                SubstanceAmountUnit.GRAMS,
                persistedState.substanceLogs.first { it.substanceId == SubstanceKind.CANNABIS.id }.amountUnit,
            )
            assertEquals(
                SubstanceAmountUnit.DRINKS,
                persistedState.substanceLogs.first { it.substanceId == SubstanceKind.ALCOHOL.id }.amountUnit,
            )
            assertEquals(null, persistedState.substanceLogs.first { it.substanceId == SubstanceKind.TOBACCO.id }.amountUnit)
        }
    }

    @Test
    fun consecutiveSubstanceLogsKeepTheChosenHistoricalDate() {
        // Two days back: yesterday is the day the screen opens on, so it would prove nothing.
        val historicalDate = java.time.LocalDate.parse(currentDateKey()).minusDays(2).toString()
        var persistedState = AppState(substances = SubstanceSettings.Legacy)
        composeRule.setContent {
            var currentState by remember { mutableStateOf(AppState(substances = SubstanceSettings.Legacy)) }
            WorkoutTrackerTheme {
                MomentumApp(
                    state = currentState,
                    onStateChange = { updated ->
                        persistedState = updated
                        currentState = updated
                        true
                    },
                    onActivitySave = ActivitySaveNotExpected,
                )
            }
        }
        composeRule.onNodeWithTag("home_substances").performClick()
        composeRule.onNodeWithTag("substance_history_$historicalDate").performScrollTo().performClick()
        composeRule.onNodeWithTag("substance_choice_cannabis").performScrollTo().performClick()
        composeRule.onNodeWithTag("substance_editor_$historicalDate").assertExists()
        composeRule.onNodeWithTag("substance_selected_day").assertExists()
        composeRule.onNodeWithTag("substance_choice_alcohol").performClick()
        composeRule.onNodeWithTag("substance_save").performScrollTo().performClick()

        composeRule.runOnIdle {
            assertEquals(2, persistedState.substanceLogs.size)
            assertEquals(
                setOf(SubstanceKind.CANNABIS.id, SubstanceKind.ALCOHOL.id),
                persistedState.substanceLogs.mapTo(mutableSetOf(), SubstanceLog::substanceId),
            )
            assertEquals(
                setOf(historicalDate),
                persistedState.substanceLogs.mapTo(mutableSetOf(), SubstanceLog::dateKey),
            )
        }
    }

    @Test
    fun deletingOneOfMultipleSubstanceRecordsPreservesTheOther() {
        val first = SubstanceLog("first", SubstanceKind.CANNABIS.id, currentDateKey(), 100L)
        val second = SubstanceLog("second", SubstanceKind.CANNABIS.id, currentDateKey(), 200L)
        var persistedState = AppState(substances = SubstanceSettings.Legacy, substanceLogs = listOf(second, first))
        composeRule.setContent {
            var currentState by remember { mutableStateOf(persistedState) }
            WorkoutTrackerTheme {
                MomentumApp(
                    state = currentState,
                    onStateChange = { updated ->
                        persistedState = updated
                        currentState = updated
                        true
                    },
                    onActivitySave = ActivitySaveNotExpected,
                )
            }
        }
        composeRule.onNodeWithTag("home_substances").performClick()
        composeRule.onNodeWithTag("substance_record_delete_second").performClick()
        composeRule.onNodeWithTag("substance_save").performScrollTo().performClick()
        composeRule.runOnIdle { assertEquals(listOf(first), persistedState.substanceLogs) }
    }

    @Test
    fun failedSubstanceDeleteKeepsExactRecordAndCorrectionRetryable() {
        val first = SubstanceLog("first", SubstanceKind.CANNABIS.id, currentDateKey(), 100L)
        val second = SubstanceLog("second", SubstanceKind.ALCOHOL.id, currentDateKey(), 200L)
        val persistedState = AppState(substances = SubstanceSettings.Legacy, substanceLogs = listOf(second, first))
        composeRule.setContent {
            WorkoutTrackerTheme {
                MomentumApp(
                    state = persistedState,
                    onStateChange = { false },
                    onActivitySave = ActivitySaveNotExpected,
                )
            }
        }

        composeRule.onNodeWithTag("home_substances").performClick()
        composeRule.onNodeWithTag("substance_choice_alcohol").performClick()
        composeRule.onNodeWithTag("substance_save").performScrollTo().performClick()

        composeRule.onNodeWithTag("substance_save_error").assertIsDisplayed()
        composeRule.onNodeWithTag("substance_choice_alcohol").assertIsNotSelected()
        composeRule.onNodeWithTag("substance_save").performScrollTo().performClick()
        composeRule.runOnIdle { assertEquals(listOf(second, first), persistedState.substanceLogs) }
    }

    @Test
    fun substanceCalendarOffersExplicitEditForSingleRecord() {
        val historicalDate = java.time.LocalDate.parse(currentDateKey()).minusDays(1).toString()
        val log = SubstanceLog(
            id = "alcohol",
            substanceId = SubstanceKind.ALCOHOL.id,
            dateKey = historicalDate,
            timestampMillis = 100L,
            amount = 2.0,
            amountUnit = SubstanceAmountUnit.DRINKS,
        )
        var persistedState = AppState(substances = SubstanceSettings.Legacy, substanceLogs = listOf(log))
        composeRule.setContent {
            var currentState by remember { mutableStateOf(persistedState) }
            WorkoutTrackerTheme {
                MomentumApp(
                    state = currentState,
                    onStateChange = { updated ->
                        persistedState = updated
                        currentState = updated
                        true
                    },
                    onActivitySave = ActivitySaveNotExpected,
                )
            }
        }

        composeRule.onNodeWithTag("home_substances").performClick()
        // The calendar sits below the editor, which may run past the fold; a tap must reach the cell.
        composeRule.onNodeWithTag("substance_history_$historicalDate").performScrollTo().performClick()

        composeRule.onNodeWithTag("substance_choice_alcohol").assertIsSelected()
        composeRule.onNodeWithTag("substance_choice_alcohol").performClick()
        composeRule.onNodeWithTag("substance_save").performScrollTo().performClick()
        composeRule.runOnIdle { assertEquals(emptyList<SubstanceLog>(), persistedState.substanceLogs) }
    }

    @Test
    fun populatedReadingDayCanBeRemovedWithTwoTaps() {
        val log = TimeLog(ReadingTimeId, "2026-09-16", 100L)
        val todayKey = "2026-09-16"
        composeRule.setContent {
            var records by remember { mutableStateOf(listOf(log)) }
            WorkoutTrackerTheme {
                TimeScreenFixture(
                    records = records,
                    onAddMinutes = { _, _ -> true },
                    onRemoveDate = { readingLogId ->
                        records = records.filterNot { it.dateKey == readingLogId }
                        true
                    },
                    todayKey = todayKey,
                )
            }
        }

        composeRule.onNodeWithTag("time_reading_history_2026-09-16").performScrollTo().performClick()
        composeRule.onNodeWithTag("time_reading_remove_day").performScrollTo().performClick()
        composeRule.onNodeWithTag("time_reading_remove_day").performClick()

        composeRule.onNodeWithTag("time_reading_remove_day").assertIsNotEnabled()

        composeRule.onNodeWithContentDescription(
            "Today, Wed, Sep 16: no entry. Select date.",
        )
            .assertIsDisplayed()
    }

    @Test
    fun failedReadingRemovalKeepsTheRecordAndStaysRetryable() {
        val log = TimeLog(ReadingTimeId, "2026-09-16", 100L)
        val todayKey = "2026-09-16"
        var requestedReadingId: String? = null
        composeRule.setContent {
            WorkoutTrackerTheme {
                TimeScreenFixture(
                    records = listOf(log),
                    onAddMinutes = { _, _ -> true },
                    onRemoveDate = { readingLogId ->
                        requestedReadingId = readingLogId
                        false
                    },
                    todayKey = todayKey,
                )
            }
        }

        composeRule.onNodeWithTag("time_reading_history_2026-09-16").performScrollTo().performClick()
        composeRule.onNodeWithTag("time_reading_remove_day").performScrollTo().performClick()
        composeRule.runOnIdle { assertEquals(null, requestedReadingId) }
        composeRule.onNodeWithTag("time_reading_remove_day").performClick()

        composeRule.onNodeWithTag("time_reading_delete_error").assertIsDisplayed()
        composeRule.runOnIdle { assertEquals(log.dateKey, requestedReadingId) }
        composeRule.onNodeWithContentDescription(
            "Today, Wed, Sep 16: minutes recorded. Select date.",
        )
            .performScrollTo()
            .assertIsDisplayed()

        // The failed attempt disarmed the button, so a retry asks again before removing.
        requestedReadingId = null
        composeRule.onNodeWithTag("time_reading_remove_day").performScrollTo().performClick()
        composeRule.runOnIdle { assertEquals(null, requestedReadingId) }
        composeRule.onNodeWithTag("time_reading_remove_day").performClick()
        composeRule.runOnIdle { assertEquals(log.dateKey, requestedReadingId) }
    }

    @Test
    fun supplementsDefaultBothAndCanSaveOnlyOne() {
        var persistedState = supplementsAppState()
        composeRule.setContent {
            var currentState by remember { mutableStateOf(supplementsAppState()) }
            WorkoutTrackerTheme {
                MomentumApp(
                    state = currentState,
                    onStateChange = { updated ->
                        persistedState = updated
                        currentState = updated
                        true
                    },
                    onActivitySave = ActivitySaveNotExpected,
                )
            }
        }

        composeRule.onNodeWithTag("home_checklist_supplements").performClick()
        composeRule.onNodeWithText("Supplements").assertIsDisplayed()
        // Nothing starts ticked: every tick is one a person pressed themselves.
        composeRule.onNodeWithTag("checklist_supplements_status_multivitamin").assertIsOff()
        composeRule.onNodeWithTag("checklist_supplements_status_creatine").assertIsOff()
        composeRule.onNodeWithTag("checklist_supplements_history").assertExists().performScrollTo().assertIsDisplayed()

        composeRule.onNodeWithTag("checklist_supplements_status_multivitamin").performClick()
        composeRule.onNodeWithTag("checklist_supplements_status_multivitamin").assertIsOn()
        composeRule.onNodeWithTag("checklist_supplements_save").performClick()

        composeRule.onNodeWithTag("checklist_supplements_status_multivitamin").assertIsOn()
        composeRule.onNodeWithTag("checklist_supplements_status_creatine").assertIsOff()
        composeRule.onNodeWithTag("checklist_supplements_save").assertIsNotEnabled()
        composeRule.runOnIdle {
            val today = persistedState.checklistDays.single { it.dateKey == currentDateKey() }
            assertEquals(supplementTicks(setOf(StandardSupplementIds.MULTIVITAMIN)), today.ticks)
        }

        composeRule.onNodeWithTag("checklist_supplements_status_creatine").performClick()
        composeRule.onNodeWithTag("checklist_supplements_save").performClick()
        composeRule.runOnIdle {
            val today = persistedState.checklistDays.single { it.dateKey == currentDateKey() }
            // Exactly what was pressed, whatever else the season offers today.
            assertEquals(supplementTicks(setOf(StandardSupplementIds.MULTIVITAMIN, StandardSupplementIds.CREATINE)), today.ticks)
        }

        composeRule.onNodeWithTag("top_back").performClick()
        composeRule.onNodeWithTag("home_checklist_supplements").assertIsDisplayed()
    }

    @Test
    fun aSecondChecklistOpensFromItsOwnTileAndSavesOnlyItsOwnRecords() {
        val today = currentDateKey()
        val supplementsDay = supplementDay(today, 1L, setOf(StandardSupplementIds.CREATINE))
        var persistedState = supplementsAppState(supplementsDay).let { it.copy(checklists = it.checklists + TestSecondChecklist) }
        composeRule.setContent {
            var currentState by remember { mutableStateOf(persistedState) }
            WorkoutTrackerTheme {
                MomentumApp(
                    state = currentState,
                    onStateChange = { updated ->
                        persistedState = updated
                        currentState = updated
                        true
                    },
                    onActivitySave = ActivitySaveNotExpected,
                )
            }
        }

        composeRule.onNodeWithTag("home_checklist_chk_second").performClick()
        composeRule.onNodeWithText("Medication").assertIsDisplayed()
        composeRule.onNodeWithTag("checklist_chk_second_status_itm_levo").performClick()
        composeRule.onNodeWithTag("checklist_chk_second_status_itm_ibu").performClick()
        composeRule.onNodeWithTag("checklist_chk_second_save").performClick()

        composeRule.runOnIdle {
            assertEquals(setOf("morning/itm_levo", "morning/itm_ibu"), persistedState.checklistDays.single { it.checklistId == "chk_second" }.ticks)
            assertEquals(supplementsDay, persistedState.checklistDays.single { it.checklistId == "supplements" })
        }
        composeRule.onNodeWithTag("top_back").performClick()
        composeRule.onNodeWithTag("home_checklist_supplements").performClick()
        composeRule.onNodeWithTag("checklist_supplements_status_creatine").assertIsOn()
        composeRule.onNodeWithTag("checklist_supplements_status_multivitamin").assertIsOff()
    }

    @Test
    fun supplementsEditCompleteDayAndRemoveItsRecord() {
        val today = currentDateKey()
        // MomentumApp reads the clock itself, so a complete day is whatever today asks for: Vitamin D too, in its season.
        val due = TestSupplementsChecklist.rulesOn(java.time.LocalDate.parse(today)).checkpoints.single().items.map { it.id }.toSet()
        val withoutMultivitamin = due - StandardSupplementIds.MULTIVITAMIN
        var persistedState = supplementsAppState(supplementDay(today, 1L, due))
        composeRule.setContent {
            var currentState by remember { mutableStateOf(persistedState) }
            WorkoutTrackerTheme {
                MomentumApp(
                    state = currentState,
                    onStateChange = { updated ->
                        persistedState = updated
                        currentState = updated
                        true
                    },
                    onActivitySave = ActivitySaveNotExpected,
                )
            }
        }

        composeRule.onNodeWithTag("home_checklist_supplements").performClick()
        composeRule.onNodeWithTag("checklist_supplements_status_multivitamin").performClick()
        composeRule.onNodeWithTag("checklist_supplements_save").performClick()
        composeRule.runOnIdle {
            assertEquals(
                supplementTicks(withoutMultivitamin),
                persistedState.checklistDays.single().ticks,
            )
        }

        withoutMultivitamin.forEach { id -> composeRule.onNodeWithTag("checklist_supplements_status_$id").performClick() }
        composeRule.onNodeWithTag("checklist_supplements_save").assertTextContains("Remove all")
        composeRule.onNodeWithTag("checklist_supplements_save").performClick()
        composeRule.runOnIdle { assertTrue(persistedState.checklistDays.isEmpty()) }
    }

    @Test
    fun populatedSupplementCalendarDayCanBeRemovedWithTwoTaps() {
        val today = currentDateKey()
        var persistedState = supplementsAppState(supplementDay(today, 1L, AllSupplementIds))
        composeRule.setContent {
            var currentState by remember { mutableStateOf(persistedState) }
            WorkoutTrackerTheme {
                MomentumApp(
                    state = currentState,
                    onStateChange = { updated ->
                        persistedState = updated
                        currentState = updated
                        true
                    },
                    onActivitySave = ActivitySaveNotExpected,
                )
            }
        }

        composeRule.onNodeWithTag("home_checklist_supplements").performClick()
        composeRule.onNodeWithTag("checklist_supplements_history_$today").performScrollTo().performClick()
        composeRule.onNodeWithTag("checklist_supplements_remove_day").performScrollTo().performClick()
        composeRule.runOnIdle { assertEquals(1, persistedState.checklistDays.size) }
        composeRule.onNodeWithTag("checklist_supplements_remove_day").performClick()

        composeRule.runOnIdle { assertTrue(persistedState.checklistDays.isEmpty()) }
        composeRule.onNodeWithTag("checklist_supplements_remove_day").assertIsNotEnabled()
    }

    @Test
    fun failedSupplementCalendarRemovalKeepsRecordAndStaysRetryable() {
        val today = currentDateKey()
        val persisted = supplementsAppState(supplementDay(today, 1L, AllSupplementIds))
        var saveAttempts = 0
        composeRule.setContent {
            WorkoutTrackerTheme {
                MomentumApp(
                    state = persisted,
                    onStateChange = {
                        saveAttempts += 1
                        false
                    },
                    onActivitySave = ActivitySaveNotExpected,
                )
            }
        }

        composeRule.onNodeWithTag("home_checklist_supplements").performClick()
        composeRule.onNodeWithTag("checklist_supplements_history_$today").performScrollTo().performClick()
        composeRule.onNodeWithTag("checklist_supplements_remove_day").performScrollTo().performClick()
        composeRule.onNodeWithTag("checklist_supplements_remove_day").performClick()

        composeRule.onNodeWithTag("checklist_supplements_delete_error").assertIsDisplayed()
        composeRule.runOnIdle { assertEquals(1, saveAttempts) }

        composeRule.onNodeWithTag("checklist_supplements_remove_day").performScrollTo().performClick()
        composeRule.runOnIdle { assertEquals(1, saveAttempts) }
        composeRule.onNodeWithTag("checklist_supplements_remove_day").performClick()
        composeRule.runOnIdle { assertEquals(2, saveAttempts) }
        composeRule.onNodeWithTag("checklist_supplements_delete_error").assertIsDisplayed()
    }

    @Test
    fun failedSupplementSaveKeepsTheSelectionRetryable() {
        composeRule.setContent {
            WorkoutTrackerTheme {
                MomentumApp(
                    state = supplementsAppState(),
                    onStateChange = { false },
                    onActivitySave = ActivitySaveNotExpected,
                )
            }
        }

        composeRule.onNodeWithTag("home_checklist_supplements").performClick()
        composeRule.onNodeWithTag("checklist_supplements_status_creatine").performClick()
        composeRule.onNodeWithTag("checklist_supplements_save").performClick()

        composeRule.onNodeWithTag("checklist_supplements_save_error").assertIsDisplayed()
        composeRule.onNodeWithTag("checklist_supplements_status_multivitamin").assertIsOff()
        composeRule.onNodeWithTag("checklist_supplements_status_creatine").assertIsOn()
        composeRule.onNodeWithTag("checklist_supplements_save").performClick()
        composeRule.onNodeWithTag("checklist_supplements_save_error").assertIsDisplayed()
    }

    @Test
    fun failedSupplementRemovalKeepsTheRemovalRetryable() {
        val today = currentDateKey()
        // The day holds only the two cleared below, so the save is a removal in any season.
        val persisted = supplementsAppState(supplementDay(today, 1L, YearRoundSupplementIds))
        composeRule.setContent {
            WorkoutTrackerTheme {
                MomentumApp(
                    state = persisted,
                    onStateChange = { false },
                    onActivitySave = ActivitySaveNotExpected,
                )
            }
        }

        composeRule.onNodeWithTag("home_checklist_supplements").performClick()
        composeRule.onNodeWithTag("checklist_supplements_status_multivitamin").performClick()
        composeRule.onNodeWithTag("checklist_supplements_status_creatine").performClick()
        composeRule.onNodeWithTag("checklist_supplements_save").assertTextContains("Remove all")
        composeRule.onNodeWithTag("checklist_supplements_save").performClick()

        composeRule.onNodeWithTag("checklist_supplements_save_error").assertIsDisplayed()
        composeRule.onNodeWithTag("checklist_supplements_save")
            .assertTextContains("Remove all")
            .assertIsEnabled()
            .performClick()
        composeRule.onNodeWithTag("checklist_supplements_save_error").assertIsDisplayed()
    }

    @Test
    fun supplementReminderDeepLinkOpensTheSameSupplementsScreenOnce() {
        var request by mutableStateOf<AppNavigationRequest?>(
            AppNavigationRequest(destination = AppDestination.Checklist, checklistId = "supplements"),
        )
        var consumed = 0
        composeRule.setContent {
            WorkoutTrackerTheme {
                MomentumApp(
                    state = supplementsAppState(),
                    onStateChange = { true },
                    onActivitySave = ActivitySaveNotExpected,
                    requestedNavigation = request,
                    onDestinationConsumed = {
                        consumed += 1
                        request = null
                    },
                )
            }
        }

        composeRule.runOnIdle {
            assertEquals(1, consumed)
            assertEquals(null, request)
        }
        composeRule.onNodeWithText("Supplements").assertIsDisplayed()
        composeRule.onNodeWithTag("checklist_supplements_history").assertExists().performScrollTo().assertIsDisplayed()
        composeRule.runOnIdle { assertEquals(1, consumed) }

        composeRule.onNodeWithTag("top_back").performClick()
        composeRule.onNodeWithTag("home_checklist_supplements").assertIsDisplayed()
        composeRule.runOnIdle { assertEquals(1, consumed) }
    }

    @Test
    fun homeWeightAndStatisticsRoutesReadSeededStateWithoutWriting() {
        val activityType = ListedActivity("type-1", "Bowling", ViewIcon.Bolt)
        val initial = AppState(
            measurements = listOf(TestWeightMeasurement),
            measurementLogs = listOf(MeasurementRecord(currentDateKey(), 20L, 87.65)).asMeasurementLogs("weight"),
            sessions = listOf(
                WorkoutSession(
                    id = "workout-1",
                    activityId = GymActivityId,
                    day = "B",
                    dateKey = currentDateKey(),
                    timestampMillis = 30L,
                    exerciseLogs = listOf(
                        ExerciseLog("pull_ups", "Pull Ups", listOf(LoggedSet(-15.0, 8.0))),
                    ),
                ),
            ),
            activity = SeededActivityRules.let { it.copy(activities = it.activities + activityType) },
            activityEntries = listOf(
                ActivityEntry("activity-1", activityType.id, activityType.label, currentDateKey(), 40L),
            ),
        )
        var saveCalls = 0
        composeRule.setContent {
            WorkoutTrackerTheme {
                MomentumApp(
                    state = initial,
                    onStateChange = {
                        saveCalls += 1
                        true
                    },
                    onActivitySave = ActivitySaveNotExpected,
                    // Statistics is hidden until it is turned on in Settings.
                    featureVisibility = FeatureVisibilitySettings(disabledFeatures = emptySet()),
                )
            }
        }

        composeRule.onNodeWithTag("home_measurement_weight").performClick()
        composeRule.onNodeWithTag("measurement_value").assertIsDisplayed()
        composeRule.onNodeWithTag("top_back").performClick()
        composeRule.onNodeWithTag("home_statistics").performClick()

        composeRule.onNodeWithTag("statistics_range_threemonths").assertIsDisplayed()
        composeRule.onNodeWithTag("statistics_consistency_measurement_weight").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("statistics_measurement_weight").performScrollTo().assertIsDisplayed()
        composeRule.runOnIdle { assertEquals(0, saveCalls) }
    }

    @Test
    fun homeMarksOnlyTheViewsAlreadyLoggedToday() {
        val initial = AppState(
            measurements = listOf(TestWeightMeasurement),
            time = listOf(TestReadingTime),
            measurementLogs = listOf(MeasurementRecord(currentDateKey(), 20L, 87.65)).asMeasurementLogs("weight"),
            timeLogs = listOf(TimeLog(ReadingTimeId, "2000-01-01", 10L)),
        )
        composeRule.setContent {
            WorkoutTrackerTheme {
                MomentumApp(
                    state = initial,
                    onStateChange = { true },
                    onActivitySave = ActivitySaveNotExpected,
                )
            }
        }

        // The badge's own words, so the two absences below stay real if it is ever reworded.
        val done = appString(R.string.app_home_task_done)
        composeRule.onNode(
            hasTestTag("home_measurement_weight") and hasAnyDescendant(hasContentDescription(done)),
            useUnmergedTree = true,
        ).assertExists()
        composeRule.onNode(
            hasTestTag("home_time_reading") and hasAnyDescendant(hasContentDescription(done)),
            useUnmergedTree = true,
        ).assertDoesNotExist()
        composeRule.onNode(
            hasTestTag("home_settings") and hasAnyDescendant(hasContentDescription(done)),
            useUnmergedTree = true,
        ).assertDoesNotExist()
    }

    @Test
    fun gymChoiceKeepsSharedActivityContextAndOpensEachPlanInline() {
        composeRule.setContent {
            WorkoutTrackerTheme {
                MomentumApp(
                    state = AppState(),
                    onStateChange = { true },
                    onActivitySave = ActivitySaveNotExpected,
                )
            }
        }

        composeRule.onNodeWithTag("home_log_activity").performClick()
        composeRule.onNodeWithTag("activity_choice_gym").performClick()
        composeRule.onNodeWithTag("activity_choice_gym").assertIsSelected()

        composeRule.onNodeWithText("Activity").assertIsDisplayed()
        composeRule.onNodeWithTag("activity_history").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("activity_name").assertDoesNotExist()
        composeRule.onNodeWithTag("activity_save").assertDoesNotExist()

        composeRule.onNodeWithTag("gym_plan_a").performScrollTo().performClick()
        composeRule.onNodeWithText("Bench Press").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("activity_history").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("gym_change_plan").performScrollTo().performClick()
        composeRule.onNodeWithTag("gym_plan_b").performScrollTo().performClick()
        composeRule.onNodeWithText("Pull Ups").assertIsDisplayed()
        composeRule.onNodeWithTag("gym_change_plan").performScrollTo().performClick()
        composeRule.onNodeWithTag("gym_plan_c").performScrollTo().performClick()
        composeRule.onNodeWithText("Overhead Press").assertIsDisplayed()
    }

    @Test
    fun workoutReminderDeepLinkOpensGymPlanPickerInsideActivity() {
        var request by mutableStateOf<AppNavigationRequest?>(
            AppNavigationRequest(
                destination = AppDestination.Activity,
                activityTarget = ActivityLaunchTarget.Activity(GymActivityId),
            ),
        )
        var consumed = 0
        composeRule.setContent {
            WorkoutTrackerTheme {
                MomentumApp(
                    state = AppState(),
                    onStateChange = { true },
                    onActivitySave = ActivitySaveNotExpected,
                    requestedNavigation = request,
                    onDestinationConsumed = {
                        consumed += 1
                        request = null
                    },
                )
            }
        }

        composeRule.onNodeWithText("Activity").assertIsDisplayed()
        composeRule.onNodeWithTag("activity_choice_gym").assertIsSelected()
        composeRule.onNodeWithTag("activity_history").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("gym_plan_a").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("activity_save").assertDoesNotExist()
        composeRule.runOnIdle { assertEquals(1, consumed) }

        // The choices scroll sideways on their own, so the row itself is what has to come back
        // into view before one of them can be tapped.
        composeRule.onNodeWithTag("activity_standard_choices").performScrollTo()
        composeRule.onNodeWithTag("activity_choice_bouldering").performClick()
        composeRule.onNodeWithTag("activity_choice_bouldering").assertIsSelected()
        composeRule.runOnIdle { assertEquals(1, consumed) }
    }

    @Test
    fun workoutReminderDeepLinkReplacesAPickedPastDayWithToday() {
        val pastDate = activityHistoryGrid(AppState(), currentDateKey()).days.first().dateKey
        var request by mutableStateOf<AppNavigationRequest?>(null)
        composeRule.setContent {
            WorkoutTrackerTheme {
                MomentumApp(
                    state = AppState(),
                    onStateChange = { true },
                    onActivitySave = ActivitySaveNotExpected,
                    requestedNavigation = request,
                    onDestinationConsumed = { request = null },
                )
            }
        }

        composeRule.onNodeWithTag("home_log_activity").performClick()
        composeRule.onNodeWithTag("activity_history_$pastDate").performScrollTo().performClick()
        composeRule.onNodeWithTag("activity_selected_day").performScrollTo().assertTextEquals(dayName(pastDate))
        composeRule.runOnIdle {
            request = AppNavigationRequest(
                destination = AppDestination.Activity,
                activityTarget = ActivityLaunchTarget.Activity(GymActivityId),
            )
        }

        composeRule.onNodeWithTag("activity_choice_gym").assertIsSelected()
        composeRule.onNodeWithTag("activity_selected_day").assertDoesNotExist()
        composeRule.onNodeWithTag("activity_history_${currentDateKey()}").assertIsSelected()
        composeRule.onNodeWithTag("gym_plan_a").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun settingsShowsWorkoutAsAnIndependentFixedTimeReminder() {
        composeRule.setContent {
            WorkoutTrackerTheme {
                MomentumApp(
                    state = AppState(measurements = listOf(TestWeightMeasurement)),
                    onStateChange = { true },
                    onActivitySave = ActivitySaveNotExpected,
                    reminderSettings = ReminderSettings(
                        workout = WorkoutReminderSettings(
                            enabled = true,
                            fixedTime = ReminderTime(19, 30),
                        ),
                    ),
                )
            }
        }

        composeRule.onNodeWithTag("home_settings").performClick()
        composeRule.onNodeWithTag("settings_group_measurement_weight").performScrollTo().performClick()
        composeRule.onNodeWithTag("measurement_settings_tab_reminders").performScrollTo().performClick()
        composeRule.onNodeWithTag("measurement_settings_reminder").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("top_back").performClick()
        composeRule.onNodeWithTag("settings_group_activity").performScrollTo().performClick()
        composeRule.onNodeWithTag("feature_settings_tab_reminders").performScrollTo().performClick()
        composeRule.onNodeWithTag("settings_workout_reminder").assertIsOn()
        // Monday to Friday, the seeded week's days: the reminder comes on the week's days and has none of its own.
        composeRule.onNodeWithTag("settings_workout_timing")
            .performScrollTo()
            .assertTextEquals("Mon, Tue, Wed, Thu, Fri at 19:30")
        composeRule.onNodeWithTag("settings_workout_time").assertTextEquals("19:30")
        composeRule.onNodeWithTag("settings_workout_day_1").assertDoesNotExist()
    }

    @Test
    fun failedGymSaveStaysOnTheSetAndShowsAnError() {
        composeRule.setContent {
            WorkoutTrackerTheme {
                MomentumApp(
                    state = AppState(),
                    onStateChange = { false },
                    onActivitySave = ActivitySaveNotExpected,
                )
            }
        }

        composeRule.onNodeWithTag("home_log_activity").performClick()
        composeRule.onNodeWithTag("activity_choice_gym").performClick()
        composeRule.onNodeWithTag("gym_plan_a").performScrollTo().performClick()
        val repsField = composeRule.onNodeWithTag("workout_bench_press_reps_0")
        repsField.performScrollTo()
        repsField.performTextReplacement("8")
        repsField.performImeAction()

        composeRule.onNodeWithTag("workout_save_error_bench_press").assertIsDisplayed()
        composeRule.onNodeWithText("Bench Press").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun finishWorkoutDurablySavesThenReturnsToActivity() {
        var persistedState = AppState()
        var saveCalls = 0
        composeRule.setContent {
            var currentState by remember { mutableStateOf(AppState()) }
            WorkoutTrackerTheme {
                MomentumApp(
                    state = currentState,
                    onStateChange = { updated ->
                        saveCalls += 1
                        persistedState = updated
                        currentState = updated
                        true
                    },
                    onActivitySave = ActivitySaveNotExpected,
                )
            }
        }

        composeRule.onNodeWithTag("home_log_activity").performClick()
        composeRule.onNodeWithTag("activity_choice_gym").performClick()
        composeRule.onNodeWithTag("gym_plan_a").performScrollTo().performClick()
        val reps = composeRule.onNodeWithTag("workout_bench_press_reps_0")
        reps.performScrollTo()
        reps.performTextReplacement("8")
        reps.performImeAction()
        // Committing the set is the durable write; it does not wait for Finish.
        composeRule.runOnIdle {
            assertEquals(1, saveCalls)
            assertEquals(1, persistedState.sessions.single().totalLoggedSets)
        }
        composeRule.onNodeWithTag("finish_workout").performScrollTo().performClick()

        composeRule.onNodeWithTag("gym_plan_a").assertExists().performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("activity_history").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(appString(R.string.app_workout_saved)).assertIsDisplayed()
        composeRule.runOnIdle {
            assertEquals(1, persistedState.sessions.single().totalLoggedSets)
            // Finishing had nothing new to store, so it must not rewrite the same state.
            assertEquals(1, saveCalls)
        }
    }

    @Test
    fun finishWhileFieldIsFocusedMakesOneFinalAuthoritativeWrite() {
        val plannedDay = SeededGym.day("A")!!
        val bench = plannedDay.exercises.first { it.id == "bench_press" }
        val squat = plannedDay.exercises.first { it.id == "back_squat" }
        val type = SeededActivityRules.activity(BoulderingActivityId)!!
        val activity = ActivityEntry(
            "activity-entry",
            type.id,
            type.label,
            currentDateKey(),
            60L,
        )
        val initial = AppState(
            // The duration is part of the one write, so Settings asks for it here.
            activity = SeededActivityRules.copy(logsDuration = true),
            activityEntries = listOf(activity),
        ).withSavedWorkoutSet(
            activity = SeededGym,
            day = plannedDay,
            exercise = bench.planned,
            setIndex = 0,
            set = LoggedSet(20.0, 8.0),
            todayKey = currentDateKey(),
            timestampMillis = 100L,
        ).withSavedWorkoutSet(
            activity = SeededGym,
            day = plannedDay,
            exercise = squat.planned,
            setIndex = 0,
            set = LoggedSet(60.0, 9.0),
            todayKey = currentDateKey(),
            timestampMillis = 110L,
        )
        val writes = mutableListOf<AppState>()
        composeRule.setContent {
            WorkoutTrackerTheme {
                MomentumApp(
                    state = initial,
                    onStateChange = { updated ->
                        writes += updated
                        true
                    },
                    onActivitySave = ActivitySaveNotExpected,
                )
            }
        }

        composeRule.onNodeWithTag("home_log_activity").performClick()
        composeRule.onNodeWithTag("activity_choice_gym").performClick()
        composeRule.onNodeWithTag("activity_duration").performScrollTo().performTextReplacement("75")
        composeRule.onNodeWithTag("gym_plan_a").performScrollTo().performClick()
        composeRule.onNodeWithTag("workout_bench_press_reps_0")
            .performScrollTo()
            .performTextReplacement("11")
        composeRule.onNodeWithTag("finish_workout").performScrollTo().performClick()

        composeRule.runOnIdle {
            assertEquals(1, writes.size)
            val finalState = writes.single()
            assertEquals(listOf(activity), finalState.activityEntries)
            val session = finalState.sessions.single()
            assertEquals(75, session.durationMinutes)
            assertEquals(11.0, session.exerciseLogs.first { it.exerciseId == bench.id }.sets.single().reps, 0.0)
            assertEquals(9.0, session.exerciseLogs.first { it.exerciseId == squat.id }.sets.single().reps, 0.0)
        }
        composeRule.onNodeWithTag("gym_plan_a").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(appString(R.string.app_workout_saved)).assertIsDisplayed()
    }

    @Test
    fun finishUnchangedSavedWorkoutIsIdempotent() {
        val plannedDay = SeededGym.day("A")!!
        val exercise = plannedDay.exercises.first()
        val initial = AppState().withSavedWorkoutSet(
            activity = SeededGym,
            day = plannedDay,
            exercise = exercise.planned,
            setIndex = 0,
            set = LoggedSet(20.0, 8.0),
            todayKey = currentDateKey(),
            timestampMillis = 100L,
        )
        var saveCalls = 0
        composeRule.setContent {
            WorkoutTrackerTheme {
                MomentumApp(
                    state = initial,
                    onStateChange = {
                        saveCalls += 1
                        true
                    },
                    onActivitySave = ActivitySaveNotExpected,
                )
            }
        }

        composeRule.onNodeWithTag("home_log_activity").performClick()
        composeRule.onNodeWithTag("activity_choice_gym").performClick()
        composeRule.onNodeWithTag("gym_plan_a").performScrollTo().performClick()
        composeRule.onNodeWithTag("finish_workout").performScrollTo().performClick()

        composeRule.runOnIdle { assertEquals(0, saveCalls) }
        composeRule.onNodeWithTag("gym_plan_a").assertIsDisplayed()
        composeRule.onNodeWithText(appString(R.string.app_workout_saved)).assertIsDisplayed()
    }

    @Test
    fun failedFinishKeepsTheWorkoutEditorAndDraftVisible() {
        composeRule.setContent {
            WorkoutTrackerTheme {
                MomentumApp(
                    state = AppState(),
                    onStateChange = { false },
                    onActivitySave = ActivitySaveNotExpected,
                )
            }
        }

        composeRule.onNodeWithTag("home_log_activity").performClick()
        composeRule.onNodeWithTag("activity_choice_gym").performClick()
        composeRule.onNodeWithTag("gym_plan_a").performScrollTo().performClick()
        composeRule.onNodeWithTag("workout_bench_press_reps_0")
            .performScrollTo()
            .performTextReplacement("8")
        composeRule.onNodeWithTag("finish_workout").performScrollTo().performClick()

        // The slot is always laid out, so only its text proves the failure was reported.
        composeRule.onNodeWithTag("finish_workout_error")
            .assertTextEquals("Couldn't finish the workout. Your draft is still here.")
        composeRule.onNodeWithText("Bench Press").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun finishWorkoutKeepsAnInvalidAttemptBesideSavedSets() {
        composeRule.setContent {
            WorkoutTrackerTheme {
                MomentumApp(
                    state = AppState(),
                    onStateChange = { true },
                    onActivitySave = ActivitySaveNotExpected,
                )
            }
        }

        composeRule.onNodeWithTag("home_log_activity").performClick()
        composeRule.onNodeWithTag("activity_choice_gym").performClick()
        composeRule.onNodeWithTag("gym_plan_a").performScrollTo().performClick()
        composeRule.onNodeWithTag("workout_bench_press_reps_0")
            .performScrollTo()
            .performTextReplacement("8")
        composeRule.onNodeWithTag("workout_bench_press_weight_1")
            .performScrollTo()
            .performTextReplacement("")
        composeRule.onNodeWithTag("workout_bench_press_reps_1")
            .performScrollTo()
            .performTextReplacement("9")
        composeRule.onNodeWithTag("finish_workout").performScrollTo().performClick()

        composeRule.onNodeWithText("Check the started sets before finishing.").assertIsDisplayed()
        composeRule.onNodeWithTag("workout_bench_press_reps_1").assertInputEquals("9")
        composeRule.onNodeWithText("Bench Press").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun boulderingChoiceUsesItsFixedIdentityWithoutANameField() {
        composeRule.setContent {
            WorkoutTrackerTheme {
                MomentumApp(
                    state = AppState(),
                    onStateChange = { true },
                    onActivitySave = ActivitySaveNotExpected,
                )
            }
        }

        composeRule.onNodeWithTag("home_log_activity").performClick()
        composeRule.onNodeWithTag("activity_choice_bouldering").performClick()

        // The name field keeps its place under every tile but Gym; under a listed tile it only shows the tile's name.
        composeRule.onNodeWithTag("activity_name").assertIsNotEnabled().assertTextContains("Bouldering")
        composeRule.onNodeWithTag("activity_save").assertIsDisplayed()
        composeRule.onNodeWithText("Notes", substring = true, ignoreCase = true).assertDoesNotExist()
    }

    @Test
    fun fixedChoicesAdaptTheFormWhileCustomChoiceOwnsTheNameField() {
        composeRule.setContent {
            WorkoutTrackerTheme {
                MomentumApp(
                    state = AppState(),
                    onStateChange = { true },
                    onActivitySave = ActivitySaveNotExpected,
                )
            }
        }

        composeRule.onNodeWithTag("home_log_activity").performClick()
        composeRule.onNodeWithTag("activity_choice_cycling").performScrollTo().performClick()
        composeRule.onNodeWithTag("activity_name").assertIsNotEnabled().assertTextContains("Cycling")
        composeRule.onNodeWithTag("activity_other_jogging").assertDoesNotExist()
        composeRule.onNodeWithTag("activity_choice_bouldering").performScrollTo().performClick()
        composeRule.onNodeWithTag("activity_other_jogging").assertDoesNotExist()
        // Jogging is offered only after opening Other.
        composeRule.onNodeWithTag("activity_choice_jogging").assertDoesNotExist()
        composeRule.onNodeWithTag("activity_choice_custom").performScrollTo().performClick()
        composeRule.onNodeWithTag("activity_other_jogging").performScrollTo().assertIsNotSelected().performClick()
        composeRule.onNodeWithTag("activity_other_jogging").assertIsSelected()
        composeRule.onNodeWithTag("activity_choice_custom").assertIsSelected()
        composeRule.onNodeWithTag("activity_name").assertIsEnabled().assertTextContains("Jogging")
        // Chosen by its tile, Other starts with nothing named.
        composeRule.onNodeWithTag("activity_choice_custom").performScrollTo().performClick()
        composeRule.onNodeWithTag("activity_other_jogging").assertIsNotSelected()
        composeRule.onNodeWithTag("activity_name").assertIsEnabled().assertInputEquals("")
    }

    @Test
    fun customActivitiesAreNotPromotedIntoQuickChoices() {
        val type = ListedActivity("type-1", "Bowling", ViewIcon.Bolt)
        val state = AppState(
            activity = SeededActivityRules.let { it.copy(activities = it.activities + type) },
            activityEntries = listOf(
                ActivityEntry("entry-1", type.id, type.label, "2020-01-01", 100L),
            ),
        )
        composeRule.setContent {
            WorkoutTrackerTheme {
                MomentumApp(
                    state = state,
                    onStateChange = { true },
                    onActivitySave = ActivitySaveNotExpected,
                )
            }
        }

        composeRule.onNodeWithTag("home_log_activity").performClick()

        composeRule.onNodeWithText("Bowling").assertDoesNotExist()
        composeRule.onNodeWithTag("activity_choice_custom").performClick()
        composeRule.onNodeWithTag("activity_name").assertIsDisplayed()
        // Under Other it is one tap away, beside the third seeded activity.
        composeRule.onNodeWithTag("activity_other_jogging").assertIsDisplayed()
        composeRule.onNodeWithTag("activity_other_type-1").assertIsDisplayed()
    }

    @Test
    fun topBackArrowReturnsToHome() {
        composeRule.setContent {
            WorkoutTrackerTheme {
                MomentumApp(
                    state = AppState(),
                    onStateChange = { true },
                    onActivitySave = ActivitySaveNotExpected,
                )
            }
        }

        composeRule.onNodeWithTag("home_log_activity").performClick()
        composeRule.onNodeWithContentDescription(appString(R.string.app_back)).performClick()

        composeRule.onNodeWithTag("home_log_activity").assertIsDisplayed()
    }

    @Test
    fun emptyCalendarDateBecomesTheDayTheActivityIsLoggedFor() {
        val dateKey = activityHistoryGrid(AppState(), currentDateKey()).days.first().dateKey
        var savedState = AppState()
        composeRule.setContent {
            WorkoutTrackerTheme {
                MomentumApp(
                    state = AppState(),
                    onStateChange = { updated ->
                        savedState = updated
                        true
                    },
                    onActivitySave = ActivitySaveNotExpected,
                )
            }
        }

        composeRule.onNodeWithTag("home_log_activity").performClick()
        composeRule.onNodeWithTag("activity_selected_day").assertDoesNotExist()
        composeRule.onNodeWithTag("activity_history_$dateKey").performScrollTo().performClick()

        // The screen has to name the chosen day, or a past day could be saved unnoticed.
        composeRule.onNodeWithTag("activity_history_$dateKey").assertIsSelected()
        composeRule.onNodeWithTag("activity_selected_day").performScrollTo().assertTextEquals(dayName(dateKey))
        composeRule.onNodeWithTag("activity_date").assertDoesNotExist()

        composeRule.onNodeWithTag("activity_choice_cycling").performScrollTo().performClick()
        composeRule.onNodeWithTag("activity_save").performScrollTo().performClick()
        composeRule.runOnIdle {
            assertEquals(listOf(dateKey), savedState.activityEntries.map { it.dateKey })
        }
    }

    @Test
    fun populatedCalendarDateEditsTheExactActivity() {
        val dateKey = activityHistoryGrid(AppState(), currentDateKey()).days.first().dateKey
        val type = ListedActivity("type-1", "Bowling", ViewIcon.Bolt)
        val entry = ActivityEntry("entry-1", type.id, type.label, dateKey, 100L, 30)
        val initial = AppState(activity = SeededActivityRules.let { it.copy(activities = it.activities + type, logsDuration = true) }, activityEntries = listOf(entry))
        var savedState = initial
        composeRule.setContent {
            WorkoutTrackerTheme {
                MomentumApp(
                    state = initial,
                    onStateChange = { updated ->
                        savedState = updated
                        true
                    },
                    onActivitySave = ActivitySaveNotExpected,
                )
            }
        }

        composeRule.onNodeWithTag("home_log_activity").performClick()
        composeRule.onNodeWithTag("activity_history_$dateKey").performScrollTo().performClick()
        composeRule.onNodeWithTag("activity_entry_edit_activity:entry-1")
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithTag("activity_name").performScrollTo().performTextReplacement("Cycling")
        composeRule.onNodeWithTag("activity_duration").performScrollTo().performTextReplacement("45")
        composeRule.onNodeWithTag("activity_save").performScrollTo().performClick()

        composeRule.runOnIdle {
            assertEquals("Cycling", savedState.activityEntries.single().name)
            assertEquals(45, savedState.activityEntries.single().durationMinutes)
            assertEquals(dateKey, savedState.activityEntries.single().dateKey)
        }
    }

    @Test
    fun historicalGymCalendarShowsReviewBeforeOpeningExactWorkout() {
        val pastDate = activityHistoryGrid(AppState(), currentDateKey()).days.first().dateKey
        val plannedDay = SeededGym.day("B")!!
        val exercise = plannedDay.exercises.first()
        val savedWorkout = AppState().withSavedWorkoutSet(
            activity = SeededGym,
            day = plannedDay,
            exercise = exercise.planned,
            setIndex = 0,
            set = LoggedSet(20.0, 8.0),
            todayKey = pastDate,
            timestampMillis = 100L,
        )
        val initial = savedWorkout.copy(
            // The session's duration is shown in its field once Settings asks for one.
            activity = SeededActivityRules.copy(logsDuration = true),
            sessions = savedWorkout.sessions.map { it.copy(durationMinutes = 55) },
        )
        composeRule.setContent {
            WorkoutTrackerTheme {
                MomentumApp(
                    state = initial,
                    onStateChange = { true },
                    onActivitySave = ActivitySaveNotExpected,
                )
            }
        }

        composeRule.onNodeWithTag("home_log_activity").performClick()
        composeRule.onNodeWithTag("activity_history_$pastDate").performScrollTo().performClick()

        val timelineItem = "workout:$pastDate-${"B"}"
        // The legend names the day too, so the row's own title is found through its container.
        composeRule.onNode(
            hasText("Gym · Day B") and hasAnyAncestor(hasTestTag("activity_timeline_item_$timelineItem")),
            useUnmergedTree = true,
        ).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("activity_entry_expand_$timelineItem")
            .performScrollTo()
            .performClick()

        composeRule.onNodeWithText("Pull Ups").performScrollTo().assertIsDisplayed()
        val pullUpsReview = hasAnyAncestor(hasTestTag("workout_review_exercise_pull_ups"))
        composeRule.onNode(hasText("20 kg × 8") and pullUpsReview, useUnmergedTree = true)
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithTag("workout_pull_ups_reps_0").assertDoesNotExist()

        composeRule.onNodeWithTag("activity_entry_edit_$timelineItem")
            .performScrollTo()
            .performClick()

        composeRule.onNodeWithText("Activity").assertIsDisplayed()
        composeRule.onNodeWithTag("activity_selected_day").performScrollTo().assertTextEquals(dayName(pastDate))
        composeRule.onNodeWithTag("activity_duration").performScrollTo().assertInputEquals("55")
        composeRule.onNodeWithTag("activity_history").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("workout_pull_ups_weight_0").assertInputEquals("20")
        composeRule.onNodeWithTag("workout_pull_ups_reps_0").assertInputEquals("8")
    }

    @Test
    fun confirmedHistoricalWorkoutDeletePreservesOtherRecords() {
        val historyDays = activityHistoryGrid(AppState(), currentDateKey()).days
        val targetDate = historyDays.first().dateKey
        val otherDate = historyDays[1].dateKey
        val activityType = ListedActivity("type-1", "Bowling", ViewIcon.Bolt)
        val activity = ActivityEntry(
            id = "activity-1",
            activityId = activityType.id,
            name = activityType.label,
            dateKey = currentDateKey(),
            timestampMillis = 20L,
            durationMinutes = 45,
        )
        val weight = MeasurementRecord(currentDateKey(), 30L, 88.45)
        val targetDay = SeededGym.day("B")!!
        val targetExercise = targetDay.exercises.first()
        val otherDay = SeededGym.day("A")!!
        val otherExercise = otherDay.exercises.first()
        val initial = AppState(
            measurementLogs = listOf(weight).asMeasurementLogs("weight"),
            activity = SeededActivityRules.let { it.copy(activities = it.activities + activityType) },
            activityEntries = listOf(activity),
        ).withSavedWorkoutSet(
            activity = SeededGym,
            day = targetDay,
            exercise = targetExercise.planned,
            setIndex = 0,
            set = LoggedSet(20.0, 8.0),
            todayKey = targetDate,
            timestampMillis = 100L,
        ).withSavedWorkoutSet(
            activity = SeededGym,
            day = otherDay,
            exercise = otherExercise.planned,
            setIndex = 0,
            set = LoggedSet(30.0, 10.0),
            todayKey = otherDate,
            timestampMillis = 200L,
        )
        val targetSessionId = "$targetDate-${"B"}"
        var savedState = initial
        var saveCalls = 0
        composeRule.setContent {
            var currentState by remember { mutableStateOf(initial) }
            WorkoutTrackerTheme {
                MomentumApp(
                    state = currentState,
                    onStateChange = { updated ->
                        saveCalls += 1
                        savedState = updated
                        currentState = updated
                        true
                    },
                    onActivitySave = ActivitySaveNotExpected,
                )
            }
        }

        composeRule.onNodeWithTag("home_log_activity").performClick()
        composeRule.onNodeWithTag("activity_history_$targetDate").performScrollTo().performClick()
        val timelineItem = "workout:$targetSessionId"
        composeRule.onNodeWithTag("activity_entry_remove_$timelineItem")
            .performScrollTo()
            .performClick()

        // The first tap only arms the button.
        composeRule.runOnIdle {
            assertEquals(0, saveCalls)
            assertEquals(initial, savedState)
        }

        composeRule.onNodeWithTag("activity_entry_remove_$timelineItem").performClick()

        composeRule.onNodeWithTag("activity_timeline_item_$timelineItem").assertDoesNotExist()
        composeRule.runOnIdle {
            assertEquals(1, saveCalls)
            assertEquals(listOf("$otherDate-${"A"}"), savedState.sessions.map { it.id })
            assertEquals(listOf(weight), savedState.recordsOf("weight"))
            assertEquals(initial.activityRules.activities, savedState.activityRules.activities)
            assertEquals(listOf(activity), savedState.activityEntries)
            assertEquals(false, savedState.sessions.any { it.id == targetSessionId })
        }
    }

    @Test
    fun emptyHistoricalDateCreatesGymWorkoutOnThatDate() {
        val pastDate = activityHistoryGrid(AppState(), currentDateKey()).days.first().dateKey
        var savedState = AppState()
        composeRule.setContent {
            WorkoutTrackerTheme {
                MomentumApp(
                    state = AppState(),
                    onStateChange = { updated ->
                        savedState = updated
                        true
                    },
                    onActivitySave = ActivitySaveNotExpected,
                )
            }
        }

        composeRule.onNodeWithTag("home_log_activity").performClick()
        composeRule.onNodeWithTag("activity_history_$pastDate").performScrollTo().performClick()
        composeRule.onNodeWithTag("activity_choice_gym").performScrollTo().performClick()
        composeRule.onNodeWithTag("activity_selected_day").performScrollTo().assertTextEquals(dayName(pastDate))
        composeRule.onNodeWithTag("gym_plan_a").performScrollTo().performClick()
        val reps = composeRule.onNodeWithTag("workout_bench_press_reps_0")
        reps.performScrollTo()
        reps.performTextReplacement("8")
        reps.performImeAction()

        composeRule.runOnIdle {
            assertEquals(listOf(pastDate), savedState.sessions.map { it.dateKey }.distinct())
        }
    }

    @Test
    fun duplicateActivitiesShowDistinctTimelineEntries() {
        val dateKey = currentDateKey()
        val type = SeededActivityRules.activity(BoulderingActivityId)!!
        val initial = AppState(
            activityEntries = listOf(
                ActivityEntry("entry-1", type.id, type.label, dateKey, 100L, 30),
                ActivityEntry("entry-2", type.id, type.label, dateKey, 200L, 30),
            ),
        )
        composeRule.setContent {
            WorkoutTrackerTheme {
                MomentumApp(
                    state = initial,
                    onStateChange = { true },
                    onActivitySave = ActivitySaveNotExpected,
                )
            }
        }

        composeRule.onNodeWithTag("home_log_activity").performClick()
        composeRule.onNodeWithTag("activity_history_$dateKey").performScrollTo().performClick()

        composeRule.onNodeWithTag("activity_timeline_item_activity:entry-1")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithTag("activity_timeline_item_activity:entry-2")
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun useLastWorkoutPersistsTheCompletedExerciseImmediately() {
        var savedState: AppState? = null
        composeRule.setContent {
            WorkoutTrackerTheme {
                MomentumApp(
                    state = AppState(),
                    onStateChange = { updated ->
                        savedState = updated
                        true
                    },
                    onActivitySave = ActivitySaveNotExpected,
                )
            }
        }

        composeRule.onNodeWithTag("home_log_activity").performClick()
        composeRule.onNodeWithTag("activity_choice_gym").performClick()
        composeRule.onNodeWithTag("gym_plan_a").performScrollTo().performClick()
        composeRule.onNodeWithTag("workout_bench_press_use_last").performScrollTo().performClick()

        composeRule.runOnIdle {
            val benchSets = savedState
                ?.sessions
                ?.single()
                ?.exerciseLogs
                ?.single { it.exerciseId == "bench_press" }
                ?.sets
            assertEquals(3, benchSets?.size)
        }
    }

    @Test
    fun clearingFirstSetThenEditingSecondReplacesTheExerciseWithoutDuplicates() {
        val plannedDay = SeededGym.day("A")!!
        val exercise = plannedDay.exercises.first()
        val initial = AppState().withReplacedWorkoutSets(
            activity = SeededGym,
            day = plannedDay,
            exercise = exercise.planned,
            sets = listOf(
                LoggedSet(20.0, 8.0),
                LoggedSet(21.0, 9.0),
                LoggedSet(22.0, 10.0),
            ),
            todayKey = currentDateKey(),
            timestampMillis = 100L,
        )
        var savedState = initial
        composeRule.setContent {
            WorkoutTrackerTheme {
                MomentumApp(
                    state = initial,
                    onStateChange = { updated ->
                        savedState = updated
                        true
                    },
                    onActivitySave = ActivitySaveNotExpected,
                )
            }
        }

        composeRule.onNodeWithTag("home_log_activity").performClick()
        composeRule.onNodeWithTag("activity_choice_gym").performClick()
        composeRule.onNodeWithTag("gym_plan_a").performScrollTo().performClick()
        composeRule.onNodeWithTag("workout_bench_press_reps_0")
            .performScrollTo()
            .performTextReplacement("")
        val secondReps = composeRule.onNodeWithTag("workout_bench_press_reps_1")
        secondReps.performTextReplacement("11")
        secondReps.performImeAction()

        composeRule.runOnIdle {
            val benchSets = savedState.sessions
                .single()
                .exerciseLogs
                .single { it.exerciseId == "bench_press" }
                .sets
            assertEquals(
                listOf(LoggedSet(21.0, 11.0), LoggedSet(22.0, 10.0)),
                benchSets,
            )
        }
    }
}

/** A day as a screen's selected-day line names it. */
private fun dayName(dateKey: String): String =
    dateText(dateKey).resolve(InstrumentationRegistry.getInstrumentation().targetContext.resources)

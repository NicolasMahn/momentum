package com.example.workouttracker.app

import android.Manifest
import android.app.Application
import android.content.Context
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import com.example.workouttracker.core.ui.theme.WorkoutTrackerTheme
import com.example.workouttracker.domain.AppState
import com.example.workouttracker.domain.TrackerRef
import com.example.workouttracker.domain.TrackerTypeId
import com.example.workouttracker.feature.tracker.TestTrackerModule
import com.example.workouttracker.feature.tracker.TestTrackerView
import com.example.workouttracker.feature.tracker.TrackerSettingsHost
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/** The shell renders a kind that has no production record/configuration type or enum entry. */
@RunWith(RobolectricTestRunner::class)
class TrackerModuleFlowTest {
    @get:Rule
    val compose = createComposeRule()

    private var settingsHost: TrackerSettingsHost? = null
    private val view = TestTrackerView(TrackerRef(TrackerTypeId("counter"), "water")) { host ->
        SideEffect { settingsHost = host }
    }
    private val module = TestTrackerModule(views = listOf(view))
    private var request by mutableStateOf<AppNavigationRequest?>(null)

    @Composable
    private fun App(module: TestTrackerModule = this.module, showStatistics: Boolean = false) {
        WorkoutTrackerTheme {
            MomentumApp(
                state = AppState(),
                onStateChange = { error("The fake kind owns its state; a shell action must not write AppState.") },
                onActivitySave = ActivitySaveNotExpected,
                featureVisibility = if (showStatistics) com.example.workouttracker.domain.settings.FeatureVisibilitySettings(disabledFeatures = emptySet()) else com.example.workouttracker.domain.settings.FeatureVisibilitySettings(),
                additionalTrackerModules = listOf(module),
                requestedNavigation = request,
                onDestinationConsumed = { request = null },
            )
        }
    }

    private fun click(tag: String) {
        compose.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
    }

    @Test
    fun aModuleOwnsItsReservationWriteAndSuppliesTheCurrentChoice() {
        val today = com.example.workouttracker.core.time.currentHabitDate()
        var chosen by mutableStateOf(emptySet<TrackerRef>())
        val reservable = TestTrackerView(ref = view.ref, onSetAside = { selected ->
            chosen = if (selected) chosen + view.ref else chosen - view.ref
        })
        val rules = com.example.workouttracker.domain.reward.RewardRules(2, com.example.workouttracker.domain.reward.Earn.EveryKeptDays(1))
        val reward = com.example.workouttracker.domain.reward.dailyTrackerReward(
            ref = view.ref, today = today, rulesOn = { rules }, began = today.minusDays(7),
            visibility = com.example.workouttracker.domain.checklist.Dated.always(true),
            openOn = { if (it < today) 0 else 1 }, takesPartOn = { true }, bankShownOn = { true },
        )
        val target = com.example.workouttracker.domain.mo.ObservationTarget.Daily({ it < today })
        val observation = com.example.workouttracker.domain.mo.TrackerObservation(
            ref = view.ref, firstDay = today.minusDays(7),
            recordedAt = (1L..7L).associate { today.minusDays(it) to it },
            target = target,
            visibleOn = { true }, countsOn = { true }, participatesInMood = true,
            content = com.example.workouttracker.domain.mo.MoContentKey(com.example.workouttracker.domain.mo.MoViewKind.Checklist),
        )
        compose.setContent {
            App(TestTrackerModule(views = listOf(reservable), rewards = listOf(reward), observations = listOf(observation), chosenPasses = chosen))
        }
        compose.runOnIdle { request = AppNavigationRequest(AppDestination.Streak) }
        compose.waitForIdle()
        click("streak_counter_water")
        compose.runOnIdle { assertEquals(setOf(view.ref), chosen) }
        click("streak_counter_water")
        compose.runOnIdle { assertTrue(chosen.isEmpty()) }
        assertFalse(target.keptOn(today))
    }

    @Test
    fun aModuleRewardShowsItsHomeBadgeAndBankThroughTheSameRegistry() {
        val today = com.example.workouttracker.core.time.currentHabitDate()
        val rules = com.example.workouttracker.domain.reward.RewardRules(2, com.example.workouttracker.domain.reward.Earn.EveryKeptDays(1))
        val reward = com.example.workouttracker.domain.reward.dailyTrackerReward(
            ref = view.ref, today = today, rulesOn = { rules }, began = today.minusDays(1),
            visibility = com.example.workouttracker.domain.checklist.Dated.always(true),
            openOn = { 0 }, takesPartOn = { true }, bankShownOn = { true },
        )
        compose.setContent { App(TestTrackerModule(views = listOf(view), rewards = listOf(reward))) }
        compose.onNodeWithContentDescription("Kept today", useUnmergedTree = true).assertExists()
        compose.runOnIdle { request = AppNavigationRequest(AppDestination.Streak) }
        compose.waitForIdle()
        compose.onNodeWithTag("streak_counter_water").assertExists()
    }

    @Test
    fun anAdditionalKindContributesItsRecordedFactsToStatistics() {
        val counted = TestTrackerView(
            ref = view.ref,
            statistics = com.example.workouttracker.domain.TrackerStatistics(setOf(com.example.workouttracker.core.time.currentDateKey())),
        )
        compose.setContent { App(TestTrackerModule(views = listOf(counted)), showStatistics = true) }
        click("home_statistics")
        compose.onNodeWithTag("statistics_consistency_counter_water").assertExists()
    }

    @Test
    fun anAdditionalKindOpensFromHomeAndItsSelectedRouteSurvivesRestoration() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent { App() }

        click("home_counter_water")
        compose.onNodeWithTag("counter_water_screen").assertExists()

        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("counter_water_screen").assertExists()

        click("top_back")
        compose.onNodeWithTag("home_counter_water").assertExists()
        compose.onNodeWithTag("counter_water_screen").assertDoesNotExist()
    }

    @Test
    fun anAdditionalKindOwnsItsSettingsAndItsCreationPage() {
        compose.setContent { App() }
        click("home_settings")
        click("settings_group_counter_water")
        compose.onNodeWithTag("counter_water_settings").assertExists()

        click("top_back")
        compose.onNodeWithTag("settings_group_counter_water").assertExists()
        click("settings_new")
        click("counter_new")
        compose.onNodeWithTag("counter_setup").assertExists()

        click("top_back")
        compose.onNodeWithTag("settings_group_counter_water").assertExists()
        compose.onNodeWithTag("counter_setup").assertDoesNotExist()
    }

    @Test
    fun theModuleCanHoldBackNavigationAndAnswerTheUnsavedWorkQuestion() {
        compose.setContent { App() }
        click("home_settings")
        click("settings_group_counter_water")
        compose.runOnIdle { requireNotNull(settingsHost).onUnsavedWorkChange(true) }

        click("top_back")

        compose.onNodeWithTag("counter_water_settings").assertExists()
        compose.runOnIdle { assertTrue(requireNotNull(settingsHost).leaveRequested) }
        compose.runOnIdle { requireNotNull(settingsHost).onLeaveAnswered(false) }
        compose.waitForIdle()
        compose.onNodeWithTag("counter_water_settings").assertExists()
        compose.runOnIdle { assertFalse(requireNotNull(settingsHost).leaveRequested) }

        click("top_back")
        compose.runOnIdle { assertTrue(requireNotNull(settingsHost).leaveRequested) }
        compose.runOnIdle { requireNotNull(settingsHost).onLeaveAnswered(true) }
        compose.waitForIdle()

        compose.onNodeWithTag("counter_water_settings").assertDoesNotExist()
        compose.onNodeWithTag("settings_group_counter_water").assertExists()
    }

    @Test
    fun switchingDirectlyBetweenTwoViewsDoesNotReuseThePreviousScreensDraftOrArrivalDate() {
        fun draftView(id: String) = TestTrackerView(
            TrackerRef(TrackerTypeId("counter"), id),
            screenContent = { host ->
                var draft by rememberSaveable { mutableStateOf("") }
                BasicTextField(draft, { draft = it }, Modifier.testTag("${id}_draft"))
                Text(host.initialDateKey ?: "none", Modifier.testTag("${id}_arrival"))
            },
        )
        val first = draftView("water")
        val second = draftView("steps")
        val twoCounters = TestTrackerModule(views = listOf(first, second))
        compose.setContent { App(twoCounters) }
        compose.runOnIdle {
            request = AppNavigationRequest(AppDestination.Tracker, tracker = first.ref, loggedDateKey = "2026-10-17")
        }
        compose.onNodeWithTag("water_arrival").assertTextEquals("2026-10-17")
        compose.onNodeWithTag("water_draft").performTextReplacement("unsaved water count")

        compose.runOnIdle { request = AppNavigationRequest(AppDestination.Tracker, tracker = second.ref) }

        compose.onNodeWithTag("steps_draft").assertTextEquals("")
        compose.onNodeWithTag("steps_arrival").assertTextEquals("none")
        compose.onNodeWithTag("water_draft").assertDoesNotExist()
    }

    @Test
    fun anUnknownKindCanSendItsOwnReminderTestWithoutACentralReminderVariant() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        shadowOf(context as Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        var sent = 0
        compose.setContent { App() }
        click("home_settings")
        click("settings_group_counter_water")

        compose.runOnIdle { requireNotNull(settingsHost).sendTest { sent += 1; true } }

        compose.runOnIdle { assertEquals(1, sent) }
        compose.onNodeWithTag("settings_reminder_error").assertDoesNotExist()
    }
}

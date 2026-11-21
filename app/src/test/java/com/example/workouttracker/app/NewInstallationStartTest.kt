package com.example.workouttracker.app

import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.example.workouttracker.domain.settings.NewInstallationVisibility
import com.example.workouttracker.persistence.FeatureVisibilitySettingsCodec
import com.example.workouttracker.persistence.PreferencesFeatureVisibilityRepository
import com.example.workouttracker.reminder.ReminderRescheduleReceiver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The first start of a new installation, the real activity on empty storage: nothing is on Home,
 * written to the visibility settings, and the app opens on Settings until the first start is done. It does
 * whoever reads the empty records first, the app or a broadcast that comes before it, such as a
 * change of the clock rescheduling every reminder.
 */
@RunWith(RobolectricTestRunner::class)
class NewInstallationStartTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun aNewInstallationStartsWithNothingOnHome() = assertNewInstallationOnFirstStart()

    @Test
    fun aBroadcastBeforeTheFirstStartLeavesEverythingOffAllTheSame() {
        ReminderRescheduleReceiver().onReceive(context, Intent(Intent.ACTION_TIME_CHANGED))
        assertNewInstallationOnFirstStart()
    }

    private fun assertNewInstallationOnFirstStart() {
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.waitForIdle()
            val stored = context.getSharedPreferences(PreferencesFeatureVisibilityRepository.PREFERENCES_NAME, Context.MODE_PRIVATE)
                .getString(PreferencesFeatureVisibilityRepository.SETTINGS_KEY, null)

            assertEquals(NewInstallationVisibility, stored?.let(FeatureVisibilitySettingsCodec::decode))
            click("first_start_begin")
            compose.onNodeWithTag("settings_feature_substances").performScrollTo().performClick()
            compose.waitForIdle()
            listOf("first_start_next", "first_start_next", "first_start_next", "first_start_done").forEach(::click)
            compose.onNodeWithTag("home_settings").assertExists()
            compose.onNodeWithTag("home_log_activity").assertDoesNotExist()
            compose.onNodeWithTag("home_substances").assertExists()
            // Statistics is a place of the app, always there, as Settings is, and it opens though a
            // new installation stores its old switch as off.
            click("home_statistics")
            compose.onNodeWithTag("statistics_screen").assertExists()
        }
        // Answered once, it never comes again.
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.waitForIdle()
            compose.onNodeWithTag("first_start_begin").assertDoesNotExist()
            compose.onNodeWithTag("home_settings").assertExists()
        }
    }

    @Test
    fun theFirstStartWalksSettingsOwnPartsThenHome() {
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.waitForIdle()
            click("first_start_begin")
            // What is tracked comes first, without Statistics, and a kind is chosen from Settings' own page.
            compose.onNodeWithTag("settings_feature_statistics", useUnmergedTree = true).assertDoesNotExist()
            click("settings_checklist_new")
            // A setup goes on only once the open step is complete.
            compose.onNodeWithTag("checklist_setup_next").assertIsNotEnabled()
            compose.onNodeWithTag("checklist_setup_name").performTextInput("Morning")
            compose.waitForIdle()
            click("checklist_setup_next")
            click("top_back")
            click("top_back")
            click("checklist_setup_discard_dialog_confirm")
            // Nothing to track yet, so there is nothing for the next steps to serve.
            compose.onNodeWithTag("first_start_next").assertIsNotEnabled()
            compose.onNodeWithTag("settings_feature_activity").performScrollTo().performClick()
            compose.waitForIdle()
            // A view set up for the first time is walked part by part, and saved under the last.
            compose.onNodeWithTag("settings_group_activity").performScrollTo().performClick()
            compose.waitForIdle()
            repeat(3) { click("feature_settings_next") }
            compose.onNodeWithTag("feature_settings_next").assertDoesNotExist()
            compose.onNodeWithTag("feature_settings_save").assertExists()
            click("top_back")
            click("first_start_next")
            compose.onNodeWithTag("first_start_step_sleep").assertExists()
            click("first_start_next")
            click("first_start_next")
            click("first_start_done")
            compose.onNodeWithTag("home_settings").assertExists()
        }
    }

    @Test
    fun aBuiltInViewOpenedInTheFirstStartIsWalkedToItsLastPartBeforeSave() {
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.waitForIdle()
            click("first_start_begin")
            compose.onNodeWithTag("settings_feature_substances").performScrollTo().performClick()
            compose.onNodeWithTag("settings_group_substances").performScrollTo().performClick()
            compose.waitForIdle()
            // Look, then Substances, then Reminders, then Streak: Save waits under the last.
            repeat(3) {
                compose.onNodeWithTag("substance_settings_save").assertDoesNotExist()
                click("substance_settings_next")
            }
            compose.onNodeWithTag("substance_settings_tab_streak").assertIsSelected()
            compose.onNodeWithTag("substance_settings_next").assertDoesNotExist()
            compose.onNodeWithTag("substance_settings_save").assertExists()
        }
    }

    /**
     * In the first start a view's page is where it is set up: its reminder can be chosen before the
     * view is on, and saving the page switches it on, even with every default kept.
     */
    @Test
    fun savingActivitysPageInTheFirstStartSwitchesItOn() {
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.waitForIdle()
            click("first_start_begin")
            compose.onNodeWithTag("settings_feature_activity", useUnmergedTree = true).performScrollTo().assertIsOff()
            compose.onNodeWithTag("settings_group_activity").performScrollTo().performClick()
            compose.waitForIdle()
            repeat(2) { click("feature_settings_next") }
            compose.onNodeWithTag("settings_workout_reminder").performScrollTo().assertIsEnabled()
            click("feature_settings_next")
            click("feature_settings_save")
            compose.onNodeWithTag("settings_feature_activity", useUnmergedTree = true).performScrollTo().assertIsOn()
            compose.onNodeWithTag("first_start_next").assertIsEnabled()
        }
    }

    @Test
    fun whatIsSetUpInTheFirstStartNeverEndsItBeforeDone() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            compose.waitForIdle()
            click("first_start_begin")
            compose.onNodeWithTag("settings_feature_substances").performScrollTo().performClick()
            compose.waitForIdle()
            click("first_start_next")
            scenario.recreate()
            compose.waitForIdle()
            compose.onNodeWithTag("first_start_next").assertExists()
        }
        // A restart finds it as it was left too.
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.waitForIdle()
            compose.onNodeWithTag("first_start_begin").assertExists()
        }
    }

    /** Back keeps what it was composed with during the first start, so it must not read that as still open. */
    @Test
    fun backAfterTheFirstStartReturnsToSettingsNotToItsSteps() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            compose.waitForIdle()
            click("first_start_begin")
            compose.onNodeWithTag("settings_feature_activity").performScrollTo().performClick()
            compose.waitForIdle()
            listOf("first_start_next", "first_start_next", "first_start_next", "first_start_done", "home_settings").forEach(::click)
            compose.onNodeWithTag("settings_group_activity").performScrollTo().performClick()
            compose.waitForIdle()
            scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            compose.waitForIdle()
            compose.onNodeWithTag("settings_open_sleep").assertExists()
        }
    }

    @Test
    fun backOnTheWelcomeLeavesTheApp() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            compose.waitForIdle()
            compose.onNodeWithTag("first_start_begin").assertExists()
            scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            scenario.onActivity { assertTrue(it.isFinishing) }
        }
    }

    private fun click(tag: String) {
        compose.onNodeWithTag(tag).performClick()
        compose.waitForIdle()
    }
}

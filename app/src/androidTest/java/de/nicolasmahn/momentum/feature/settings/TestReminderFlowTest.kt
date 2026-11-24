package de.nicolasmahn.momentum.feature.settings

import de.nicolasmahn.momentum.app.ActivitySaveNotExpected
import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.os.Build
import android.service.notification.StatusBarNotification
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.height
import androidx.test.platform.app.InstrumentationRegistry
import de.nicolasmahn.momentum.R
import de.nicolasmahn.momentum.app.MomentumApp
import de.nicolasmahn.momentum.core.ui.theme.WorkoutTrackerTheme
import de.nicolasmahn.momentum.domain.AppState
import de.nicolasmahn.momentum.domain.checklist.ChecklistConfiguration
import de.nicolasmahn.momentum.domain.checklist.ChecklistItem
import de.nicolasmahn.momentum.domain.checklist.Checkpoint
import de.nicolasmahn.momentum.domain.checklist.CheckpointTime
import de.nicolasmahn.momentum.domain.checklist.ViewHue
import de.nicolasmahn.momentum.domain.checklist.ViewIcon
import de.nicolasmahn.momentum.domain.settings.FeatureVisibilitySettings
import de.nicolasmahn.momentum.core.time.currentHabitDate
import de.nicolasmahn.momentum.reminder.ChecklistReminderRequestCode
import de.nicolasmahn.momentum.reminder.ReminderNotificationChannel
import de.nicolasmahn.momentum.reminder.slotKey
import de.nicolasmahn.momentum.testing.appString
import java.time.LocalTime
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * "Send a test" on a checklist's Reminders page posts the checkpoint's reminder, switch off or
 * not, and the notification says what the page previews.
 */
class TestReminderFlowTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val notificationManager = context.getSystemService(NotificationManager::class.java)
    private val plants = ChecklistConfiguration(
        id = "chk_plants",
        label = "Plants",
        icon = ViewIcon.Checklist,
        hue = ViewHue.Sky,
        checkpoints = listOf(Checkpoint("cpt_morning", CheckpointTime.Fixed(LocalTime.of(8, 0)), remind = false)),
        items = listOf(ChecklistItem("itm_water", "Water the plants")),
    )

    @Before
    fun allowNotifications() {
        // The suite grants no permission on its own; a test needs the one the real reminder needs.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        }
        // The app creates the channel on start; the test host is not the app's activity.
        ReminderNotificationChannel.create(context)
        notificationManager.cancelAll()
    }

    @After
    fun clearNotifications() {
        notificationManager.cancelAll()
    }

    @Test
    fun sendATestPostsTheCheckpointsReminderWhileItsSwitchIsOff() {
        composeRule.setContent {
            WorkoutTrackerTheme {
                MomentumApp(state = AppState(checklists = listOf(plants)), onStateChange = { true }, onActivitySave = ActivitySaveNotExpected)
            }
        }
        composeRule.onNodeWithTag("home_settings").performClick()
        composeRule.onNodeWithTag("settings_group_checklist_chk_plants").performScrollTo().performClick()
        composeRule.onNodeWithTag("checklist_editor_tab_reminders").performScrollTo().performClick()

        composeRule.onNodeWithTag("checklist_editor_checkpoint_cpt_morning_test").performScrollTo().performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) { postedTest() != null }
        val posted = requireNotNull(postedTest()).notification
        assertEquals("Plants", posted.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString())
        assertEquals(appString(R.string.checklist_reminder_open, "Water the plants"), posted.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString())
    }

    /**
     * The note's slot holds the whole height the "reminder off" sentence wraps to, and the button
     * sits on its own line under it, so the note coming and going with the switch moves nothing
     * below it. At twice the font the sentence takes more than one line, which is the case to prove.
     */
    @Test
    fun theTestButtonKeepsItsPlaceWhenTheReminderIsSwitchedAtALargeFont() {
        val twoCheckpoints = plants.copy(
            checkpoints = plants.checkpoints + Checkpoint("cpt_evening", CheckpointTime.Fixed(LocalTime.of(21, 0)), remind = false),
        )
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
                WorkoutTrackerTheme {
                    MomentumApp(state = AppState(checklists = listOf(twoCheckpoints)), onStateChange = { true }, onActivitySave = ActivitySaveNotExpected)
                }
            }
        }
        composeRule.onNodeWithTag("home_settings").performClick()
        composeRule.onNodeWithTag("settings_group_checklist_chk_plants").performScrollTo().performClick()
        composeRule.onNodeWithTag("checklist_editor_tab_reminders").performScrollTo().performClick()
        fun gap() = composeRule.onNodeWithTag("checklist_editor_checkpoint_cpt_evening_test").getBoundsInRoot().top -
            composeRule.onNodeWithTag("checklist_editor_checkpoint_cpt_morning_test").getBoundsInRoot().top

        val note = composeRule.onNodeWithTag("checklist_editor_checkpoint_cpt_morning_preview_note").performScrollTo().assertIsDisplayed()
        // The timing line is one line of the same type; a taller note has wrapped.
        val oneLine = composeRule.onNodeWithTag("checklist_editor_checkpoint_cpt_morning_timing").getBoundsInRoot().height
        assertTrue("the note should wrap at this font size", note.getBoundsInRoot().height > oneLine)
        val before = gap()
        composeRule.onNodeWithTag("checklist_editor_checkpoint_cpt_morning_reminder").performScrollTo().performClick()
        composeRule.onNodeWithTag("checklist_editor_checkpoint_cpt_morning_preview_note").assertDoesNotExist()
        assertEquals(before, gap())
    }

    /** A hidden checklist's reminders cannot come, so its checkpoints have no test to send. */
    @Test
    fun aHiddenChecklistsTestCannotBeSent() {
        composeRule.setContent {
            WorkoutTrackerTheme {
                MomentumApp(
                    state = AppState(checklists = listOf(plants)),
                    onStateChange = { true },
                    onActivitySave = ActivitySaveNotExpected,
                    featureVisibility = FeatureVisibilitySettings().withChecklistVisibility("chk_plants", visible = false, today = currentHabitDate()),
                )
            }
        }
        composeRule.onNodeWithTag("home_settings").performClick()
        composeRule.onNodeWithTag("settings_group_checklist_chk_plants").performScrollTo().performClick()
        composeRule.onNodeWithTag("checklist_editor_tab_reminders").performScrollTo().performClick()

        composeRule.onNodeWithTag("checklist_editor_checkpoint_cpt_morning_test").performScrollTo().assertIsNotEnabled()
    }

    private fun postedTest(): StatusBarNotification? = notificationManager.activeNotifications
        .firstOrNull { it.tag == slotKey("chk_plants", "cpt_morning") && it.id == ChecklistReminderRequestCode }
}

package de.nicolasmahn.momentum.app

import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.test.platform.app.InstrumentationRegistry
import de.nicolasmahn.momentum.core.time.currentDateKey
import de.nicolasmahn.momentum.core.ui.theme.WorkoutTrackerTheme
import de.nicolasmahn.momentum.domain.mo.TrackerMoPolicy
import de.nicolasmahn.momentum.domain.AppState
import de.nicolasmahn.momentum.careDone
import de.nicolasmahn.momentum.careState
import de.nicolasmahn.momentum.supplementsAppState
import de.nicolasmahn.momentum.testfixtures.LegacyCareAction
import de.nicolasmahn.momentum.domain.checklist.ChecklistDay
import de.nicolasmahn.momentum.domain.checklist.ChecklistConfiguration
import de.nicolasmahn.momentum.domain.checklist.ChecklistItem
import de.nicolasmahn.momentum.domain.checklist.ChecklistRewards
import de.nicolasmahn.momentum.domain.checklist.Checkpoint
import de.nicolasmahn.momentum.domain.checklist.CheckpointTime
import de.nicolasmahn.momentum.domain.checklist.RewardPeriod
import de.nicolasmahn.momentum.domain.checklist.ViewHue
import de.nicolasmahn.momentum.domain.checklist.ViewIcon
import de.nicolasmahn.momentum.testfixtures.LegacyCareSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

/** Exercises real app navigation with disposable records, and saves visual evidence on the emulator. */
class HabitFlowEvidenceTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun careRequiresBothSessionsBeforeCompletingTheDay() {
        val today = currentDateKey()
        var persistedState = careState()
        composeRule.setContent {
            var state by remember { mutableStateOf(careState()) }
            WorkoutTrackerTheme {
                MomentumApp(
                    state = state,
                    onStateChange = { updated ->
                        persistedState = updated
                        state = updated
                        true
                    },
                    onActivitySave = ActivitySaveNotExpected,
                )
            }
        }

        composeRule.onNodeWithTag("home_checklist_care").performClick()
        composeRule.onNodeWithTag("checklist_care_morning_toothbrushing").performClick()
        composeRule.onNodeWithTag("checklist_care_morning_hair_foam").performClick()
        composeRule.onNodeWithTag("checklist_care_morning_save").performClick()
        composeRule.onNodeWithTag("checklist_care_morning_toothbrushing").assertIsOn()
        composeRule.onNodeWithTag("checklist_care_morning_hair_foam").assertIsOn()
        composeRule.runOnIdle {
            assertEquals(
                setOf(LegacyCareSession.MORNING to LegacyCareAction.TOOTHBRUSHING, LegacyCareSession.MORNING to LegacyCareAction.HAIR_FOAM),
                persistedState.careDone(today),
            )
            assertTrue(persistedState.checklistDays.all { it.dateKey == today })
        }
        capture("01-care-morning-saved")

        composeRule.onNodeWithTag("top_back").performClick()
        // One of Care's two sessions is done.
        assertTileStatus("home_checklist_care", "1 of 2 done today")
        composeRule.onNodeWithContentDescription("0 day streak").assertIsDisplayed()
        capture("02-care-partial-home")

        composeRule.onNodeWithTag("home_checklist_care").performClick()
        composeRule.onNodeWithTag("checklist_care_evening_toothbrushing").performScrollTo().performClick()
        composeRule.onNodeWithTag("checklist_care_evening_hair_foam").performScrollTo().performClick()
        // On a Saturday the evening asks for the between-teeth cleaning as well.
        val saturday = LocalDate.parse(today).dayOfWeek == DayOfWeek.SATURDAY
        if (saturday) composeRule.onNodeWithTag("checklist_care_evening_clean_between_teeth").performScrollTo().performClick()
        composeRule.onNodeWithTag("checklist_care_evening_save").performScrollTo().performClick()
        composeRule.onNodeWithTag("checklist_care_evening_toothbrushing").assertIsOn()
        composeRule.onNodeWithTag("checklist_care_evening_hair_foam").assertIsOn()
        composeRule.runOnIdle {
            assertEquals(
                LegacyCareSession.entries.flatMap { session -> listOf(session to LegacyCareAction.TOOTHBRUSHING, session to LegacyCareAction.HAIR_FOAM) }.toSet() +
                    listOfNotNull((LegacyCareSession.EVENING to LegacyCareAction.CLEAN_BETWEEN_TEETH).takeIf { saturday }),
                persistedState.careDone(today),
            )
            assertTrue(persistedState.checklistDays.all { it.dateKey == today })
        }
        capture("03-care-evening-saved")

        composeRule.onNodeWithTag("top_back").performClick()
        assertTileStatus("home_checklist_care", "Done today")
        composeRule.onNodeWithContentDescription("1 day streak").assertIsDisplayed()
        capture("04-care-complete-home")
    }

    /** An already recorded checklist shows today's factual progress checkpoint by checkpoint. */
    @Test
    fun aMadeChecklistInTheStreakWearsItsBadgeByCheckpoint() {
        val today = LocalDate.parse(currentDateKey())
        val yesterday = today.minusDays(1)
        val medication = ChecklistConfiguration(
            id = "chk_med",
            label = "Medication",
            icon = ViewIcon.Checklist,
            hue = ViewHue.Sky,
            checkpoints = listOf(
                Checkpoint("morning", CheckpointTime.Fixed(LocalTime.of(8, 0)), remind = false),
                Checkpoint("evening", CheckpointTime.Fixed(LocalTime.of(21, 0)), remind = false),
            ),
            items = listOf(ChecklistItem("itm_levo", "Levothyroxine", from = yesterday)),
            mo = TrackerMoPolicy(enabled = true),
            rewards = ChecklistRewards(maxPasses = 3, everyKeptDays = 7, periods = listOf(RewardPeriod(today))),
        )
        // A configured start date is not a recorded start. These are real earlier checkpoint facts;
        // they must survive both of today's saves without creating any ticks for today.
        val previous = ChecklistDay(
            medication.id, yesterday.toString(), 1L,
            setOf("morning/itm_levo", "evening/itm_levo"),
        )
        var persistedState = AppState(checklists = listOf(medication), checklistDays = listOf(previous))
        composeRule.setContent {
            var state by remember { mutableStateOf(persistedState) }
            WorkoutTrackerTheme {
                MomentumApp(
                    state = state,
                    onStateChange = { updated ->
                        persistedState = updated
                        state = updated
                        true
                    },
                    onActivitySave = ActivitySaveNotExpected,
                )
            }
        }

        composeRule.runOnIdle { assertEquals(listOf(previous), persistedState.checklistDays) }
        assertTileStatus("home_checklist_chk_med", "0 of 2 done today")
        composeRule.onNodeWithTag("home_checklist_chk_med").performClick()
        composeRule.onNodeWithTag("checklist_chk_med_morning_itm_levo").performClick()
        composeRule.onNodeWithTag("checklist_chk_med_morning_save").performClick()
        composeRule.onNodeWithTag("top_back").performClick()
        composeRule.runOnIdle {
            assertEquals(previous, persistedState.checklistDays.single { it.dateKey == yesterday.toString() })
            assertEquals(setOf("morning/itm_levo"), persistedState.checklistDays.single { it.dateKey == today.toString() }.ticks)
        }
        assertTileStatus("home_checklist_chk_med", "1 of 2 done today")
        capture("06-checklist-partial-home")

        composeRule.onNodeWithTag("home_checklist_chk_med").performClick()
        composeRule.onNodeWithTag("checklist_chk_med_evening_itm_levo").performScrollTo().performClick()
        composeRule.onNodeWithTag("checklist_chk_med_evening_save").performScrollTo().performClick()
        composeRule.onNodeWithTag("top_back").performClick()
        composeRule.runOnIdle {
            assertEquals(previous, persistedState.checklistDays.single { it.dateKey == yesterday.toString() })
            assertEquals(setOf("morning/itm_levo", "evening/itm_levo"), persistedState.checklistDays.single { it.dateKey == today.toString() }.ticks)
            assertTrue(persistedState.checklistDays.all { it.misses.isEmpty() })
        }
        assertTileStatus("home_checklist_chk_med", "Done today")
        capture("07-checklist-complete-home")
    }

    @Test
    fun supplementsSettingsShowLunchtimeReminder() {
        composeRule.setContent {
            WorkoutTrackerTheme {
                MomentumApp(
                    state = supplementsAppState().let { state ->
                        state.copy(checklists = state.checklists.map { it.copy(checkpoints = it.checkpoints.map { checkpoint -> checkpoint.copy(remind = true) }) })
                    },
                    onStateChange = { true },
                    onActivitySave = ActivitySaveNotExpected,
                )
            }
        }
        composeRule.onNodeWithTag("home_settings").performClick()
        composeRule.onNodeWithTag("settings_group_checklist_supplements").performScrollTo().performClick()
        composeRule.onNodeWithTag("checklist_editor_tab_reminders").performClick()
        composeRule.onNodeWithTag("checklist_editor_checkpoint_midday_timing")
            .performScrollTo()
        // Bring the whole supplements group away from the viewport edge for the recording.
        composeRule.onRoot().performTouchInput {
            swipe(
                start = Offset(center.x, height * 0.75f),
                end = Offset(center.x, height * 0.45f),
                durationMillis = 600,
            )
        }
        composeRule.onNodeWithTag("checklist_editor_checkpoint_midday_timing")
            .assertIsDisplayed()
            .assertTextContains("Every day at 12:30", substring = true)
        capture("05-supplements-1230")
    }

    /** What the badge on the Home tile [tag] says to TalkBack. */
    private fun assertTileStatus(tag: String, description: String) {
        composeRule.onNode(
            hasContentDescription(description) and hasAnyAncestor(hasTestTag(tag)),
            useUnmergedTree = true,
        ).assertExists()
    }

    private fun capture(name: String) {
        composeRule.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        if (InstrumentationRegistry.getArguments().getString("captureDemo") == "true") {
            Thread.sleep(2_000)
        }
        val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "habit-evidence")
        check(directory.isDirectory || directory.mkdirs())
        val screenshot = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        try {
            File(directory, "$name.png").outputStream().use { stream ->
                check(screenshot.compress(Bitmap.CompressFormat.PNG, 100, stream))
            }
        } finally {
            screenshot.recycle()
        }
    }
}

package com.example.workouttracker.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextReplacement
import androidx.test.espresso.Espresso
import com.example.workouttracker.R
import com.example.workouttracker.TestReadingTime
import com.example.workouttracker.TestWeightMeasurement
import com.example.workouttracker.core.time.currentDateKey
import com.example.workouttracker.core.ui.theme.WorkoutTrackerTheme
import com.example.workouttracker.domain.AppState
import com.example.workouttracker.domain.checklist.AboutDay
import com.example.workouttracker.domain.checklist.ChecklistConfiguration
import com.example.workouttracker.domain.checklist.ChecklistDay
import com.example.workouttracker.domain.checklist.ChecklistItem
import com.example.workouttracker.domain.checklist.ChecklistTarget
import com.example.workouttracker.domain.checklist.Checkpoint
import com.example.workouttracker.domain.checklist.CheckpointTime
import com.example.workouttracker.domain.checklist.Dated
import com.example.workouttracker.domain.checklist.ViewHue
import com.example.workouttracker.domain.checklist.ViewIcon
import com.example.workouttracker.domain.checklist.WhenAway
import com.example.workouttracker.domain.checklist.recordDayFor
import com.example.workouttracker.testing.appString
import com.example.workouttracker.testing.assertInputEquals
import com.example.workouttracker.domain.checklist.addChecklist
import com.example.workouttracker.domain.checklist.deleteChecklist
import com.example.workouttracker.domain.checklist.applyingEdits
import com.example.workouttracker.domain.checklist.startedOn
import com.example.workouttracker.testfixtures.configuredChecklists
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * A checklist set up step by step, changed and saved on purpose, and deleted in Settings. The
 * stored state is the proof: it changes only when Create, Save or Delete say so.
 */
class ChecklistSettingsFlowTest {
    @get:Rule
    val composeRule = createComposeRule()

    private var state by mutableStateOf(AppState())
    private var request by mutableStateOf<AppNavigationRequest?>(null)
    private var creates = 0
    private var saves = 0
    private val today = LocalDate.parse(currentDateKey())

    /** Whether the store refuses a Save, as a write that failed does. */
    private var storeFails = false

    private fun setApp() {
        composeRule.setContent {
            WorkoutTrackerTheme {
                MomentumApp(
                    state = state,
                    onStateChange = { state = it; true },
                    onActivitySave = ActivitySaveNotExpected,
                    onChecklistCreate = { starter, edits ->
                        creates++
                        state = state.addChecklist(starter.startedOn(today)).applyingEdits(starter.id, edits, today)
                        true
                    },
                    onChecklistSave = { id, edits ->
                        saves++
                        if (storeFails) {
                            false
                        } else {
                            state = state.applyingEdits(id, edits, today)
                            true
                        }
                    },
                    onChecklistDelete = { id -> runCatching { state.deleteChecklist(id) }.onSuccess { state = it }.isSuccess },
                    requestedNavigation = request,
                    onDestinationConsumed = { request = null },
                )
            }
        }
    }

    /** From the Settings list: the one button asks what kind, and the checklist's card opens its setup. */
    private fun openChecklistSetup() {
        composeRule.onNodeWithTag("settings_new").performScrollTo().performClick()
        composeRule.onNodeWithTag("settings_checklist_new").performScrollTo().performClick()
    }

    /** From Settings through the four steps to Create, with Next refused until each step is complete. */
    private fun createChecklist(name: String = "Plants", item: String = "Water the plants") {
        composeRule.onNodeWithTag("home_settings").performClick()
        openChecklistSetup()
        composeRule.onNodeWithTag("checklist_setup").assertIsDisplayed()

        // The name field is not told it is missing before anyone typed; the bar says what Items needs.
        composeRule.onNodeWithTag("checklist_setup_next").assertIsNotEnabled()
        composeRule.onNodeWithTag("checklist_setup_name").performScrollTo().assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Error))
        composeRule.onNodeWithTag("checklist_setup_name").performTextReplacement(name)
        advanceSetup("items")

        composeRule.onNodeWithTag("checklist_setup_next").assertIsNotEnabled()
        // A step that is not ready says why only when its button is pressed.
        composeRule.onNodeWithTag("checklist_setup_next").performClick()
        composeRule.onNodeWithTag("checklist_setup_hint").assertIsDisplayed()
        composeRule.onNodeWithTag("checklist_editor_add_item").performScrollTo().performTextReplacement(item)
        composeRule.onNodeWithTag("checklist_editor_add_item_button").performScrollTo().performClick()
        composeRule.onNodeWithTag("checklist_setup_hint").assertDoesNotExist()
        advanceSetup("reminders")

        advanceSetup("streak")
        composeRule.onNodeWithTag("checklist_setup_create").assertIsDisplayed()
        composeRule.runOnIdle { assertTrue(state.configuredChecklists.isEmpty()) }
        composeRule.onNodeWithTag("checklist_setup_create").performClick()
    }

    private fun advanceSetup(to: String) {
        Espresso.closeSoftKeyboard()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("checklist_setup_next").assertIsEnabled().performClick()
        composeRule.onNodeWithTag("checklist_setup_step_$to").assertIsSelected()
    }

    private fun openChecklistPage() {
        val checklist = state.configuredChecklists.single()
        composeRule.onNodeWithTag("settings_group_checklist_${checklist.id}").performScrollTo().performClick()
        composeRule.onNodeWithTag("checklist_editor").assertIsDisplayed()
    }

    /**
     * How far [below] sits under [above], in pixels, wherever the page is scrolled to. The Streak
     * tab can be taller than the screen, and a control scrolled out of sight has no clipped bounds
     * to measure, so the two are measured from one another.
     */
    private fun gap(above: String, below: String): Float =
        composeRule.onNodeWithTag(below).fetchSemanticsNode().positionInRoot.y - composeRule.onNodeWithTag(above).fetchSemanticsNode().positionInRoot.y

    @Test
    fun theSetupWalksFourStepsAndCreatesOnceOnTheLast() {
        setApp()
        createChecklist()

        composeRule.runOnIdle {
            assertEquals(1, creates)
            val checklist = state.configuredChecklists.single()
            assertEquals("Plants", checklist.label)
            assertEquals(listOf("Water the plants"), checklist.items.map { it.label })
            assertEquals(1, checklist.checkpoints.size)
        }
        // The setup is gone and the list shows the new row.
        val checklist = state.configuredChecklists.single()
        composeRule.onNodeWithTag("checklist_setup").assertDoesNotExist()
        composeRule.onNodeWithTag("settings_group_checklist_${checklist.id}").performScrollTo().assertIsDisplayed()
    }

    /**
     * The setup's Streak step lists the item just added under "Only log misses", once that is
     * unfolded, and Create stores what was chosen there. The step waits for nothing: Create is ready before anything is chosen.
     */
    @Test
    fun theSetupOffersOnlyLogMissesOnItsStreakStepAndCreateStoresIt() {
        // The item's ID is made up by the setup, so its checkbox is found by what every one ends in.
        val theItemsBox = SemanticsMatcher("is an item's checkbox under \"Only log misses\"") {
            it.config.getOrNull(SemanticsProperties.TestTag)?.endsWith("_exceptions_only") == true
        }
        setApp()
        composeRule.onNodeWithTag("home_settings").performClick()
        openChecklistSetup()
        composeRule.onNodeWithTag("checklist_setup_name").performScrollTo().performTextReplacement("Teeth")
        advanceSetup("items")
        composeRule.onNodeWithTag("checklist_editor_add_item").performScrollTo().performTextReplacement("Brush")
        composeRule.onNodeWithTag("checklist_editor_add_item_button").performScrollTo().performClick()
        advanceSetup("reminders")
        advanceSetup("streak")

        composeRule.onNodeWithTag("checklist_setup_create").assertIsEnabled()
        // The choice is folded away on a setup's step as on a page: opening it is the person's own act.
        composeRule.onNode(theItemsBox).assertDoesNotExist()
        composeRule.onNodeWithTag("checklist_editor_advanced").performScrollTo().performClick()
        composeRule.onNode(theItemsBox).performScrollTo().assertIsOff().performClick()
        composeRule.onNode(theItemsBox).assertIsOn()
        composeRule.runOnIdle { assertTrue(state.configuredChecklists.isEmpty()) }
        composeRule.onNodeWithTag("checklist_setup_create").assertIsEnabled().performClick()
        composeRule.runOnIdle {
            assertEquals(1, creates)
            assertTrue(state.configuredChecklists.single().items.single().exceptionsOnly.on(today))
        }
    }

    /** A step ahead cannot be chosen; one already passed can, and Back goes there too. */
    @Test
    fun stepsAheadAreNotPressableAndStepsPassedAre() {
        setApp()
        composeRule.onNodeWithTag("home_settings").performClick()
        openChecklistSetup()

        composeRule.onNodeWithTag("checklist_setup_step_items").assertIsNotEnabled()
        composeRule.onNodeWithTag("checklist_setup_name").performScrollTo().performTextReplacement("Plants")
        composeRule.onNodeWithTag("checklist_setup_next").performClick()
        composeRule.onNodeWithTag("checklist_setup_step_items").assertIsEnabled()
        composeRule.onNodeWithTag("checklist_setup_step_reminders").assertIsNotEnabled()

        composeRule.onNodeWithTag("checklist_setup_step_look").performClick()
        composeRule.onNodeWithTag("checklist_setup_name").performScrollTo().assertTextContains("Plants")
    }

    /** A name typed and cleared again is missing; one never typed is not said to be. */
    @Test
    fun theNameIsMissingOnlyOnceItWasTypedAndCleared() {
        setApp()
        composeRule.onNodeWithTag("home_settings").performClick()
        openChecklistSetup()

        composeRule.onNodeWithTag("checklist_setup_name").performScrollTo().assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Error))
        composeRule.onNodeWithTag("checklist_setup_name").performTextReplacement("Plants")
        composeRule.onNodeWithTag("checklist_setup_name").performTextClearance()
        composeRule.onNodeWithTag("checklist_setup_name").assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Error))
        composeRule.onNodeWithTag("checklist_setup_next").assertIsNotEnabled()
    }

    @Test
    fun leavingTheSetupAsksBeforeDiscardingAndStoresNothing() {
        setApp()
        composeRule.onNodeWithTag("home_settings").performClick()
        openChecklistSetup()
        composeRule.onNodeWithTag("checklist_setup_name").performScrollTo().performTextReplacement("Plants")

        Espresso.closeSoftKeyboard()
        Espresso.pressBack()
        composeRule.onNodeWithTag("checklist_setup_discard_dialog").assertIsDisplayed()
        composeRule.onNodeWithTag("checklist_setup_discard_dialog_stay").performClick()
        composeRule.onNodeWithTag("checklist_setup").assertIsDisplayed()

        // The arrow is the same way out as the system's back.
        composeRule.onNodeWithTag("top_back").performClick()
        composeRule.onNodeWithTag("checklist_setup_discard_dialog_confirm").performClick()
        composeRule.onNodeWithTag("checklist_setup").assertDoesNotExist()
        composeRule.onNodeWithTag("settings_new").performScrollTo().assertIsDisplayed()
        composeRule.runOnIdle {
            assertEquals(0, creates)
            assertTrue(state.configuredChecklists.isEmpty())
        }
    }

    @Test
    fun changesStayOnThePageUntilSaveWhichReturnsToTheList() {
        setApp()
        createChecklist()
        openChecklistPage()
        val checklist = state.configuredChecklists.single()
        val item = checklist.items.single()

        composeRule.onNodeWithTag("checklist_editor_save").assertIsNotEnabled()
        composeRule.onNodeWithTag("checklist_editor_tab_items").performScrollTo().performClick()
        composeRule.onNodeWithTag("checklist_editor_item_${item.id}_name").performScrollTo().performTextReplacement("Water them")
        composeRule.runOnIdle {
            assertEquals(0, saves)
            assertEquals("Water the plants", state.configuredChecklists.single().items.single().label)
        }

        composeRule.onNodeWithTag("checklist_editor_save").assertIsEnabled().performClick()
        composeRule.runOnIdle {
            assertEquals(1, saves)
            assertEquals("Water them", state.configuredChecklists.single().items.single().label)
        }
        // Saved, the page has done its work and the list is back, as after Create.
        composeRule.onNodeWithTag("checklist_editor").assertDoesNotExist()
        composeRule.onNodeWithTag("settings_group_checklist_${checklist.id}").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun leavingWithUnsavedChangesAsksAndDiscardingLeavesStorageUntouched() {
        setApp()
        createChecklist()
        openChecklistPage()
        val stored = state.configuredChecklists.single()

        composeRule.onNodeWithTag("checklist_editor_name").performScrollTo().performTextReplacement("Garden")
        composeRule.onNodeWithTag("top_back").performClick()
        composeRule.onNodeWithTag("checklist_editor_discard_dialog").assertIsDisplayed()
        composeRule.onNodeWithTag("checklist_editor_discard_dialog_stay").performClick()
        composeRule.onNodeWithTag("checklist_editor").assertIsDisplayed()

        Espresso.pressBack()
        composeRule.onNodeWithTag("checklist_editor_discard_dialog_confirm").performClick()
        composeRule.onNodeWithTag("checklist_editor").assertDoesNotExist()
        composeRule.onNodeWithTag("settings_group_checklist_${stored.id}").performScrollTo().assertIsDisplayed()
        composeRule.runOnIdle {
            assertEquals(0, saves)
            assertEquals(stored, state.configuredChecklists.single())
        }

        // Without a change, back leaves at once.
        openChecklistPage()
        Espresso.pressBack()
        composeRule.onNodeWithTag("checklist_editor_discard_dialog").assertDoesNotExist()
        composeRule.onNodeWithTag("checklist_editor").assertDoesNotExist()
    }

    /** A reminder's destination arriving while a page holds unsaved work waits for the answer, then goes where it was going. */
    @Test
    fun aDestinationRequestedWhileEditingAsksFirstAndGoesThereOnDiscard() {
        state = AppState(measurements = listOf(TestWeightMeasurement))
        setApp()
        createChecklist()
        openChecklistPage()
        composeRule.onNodeWithTag("checklist_editor_name").performScrollTo().performTextReplacement("Garden")

        composeRule.runOnIdle { request = AppNavigationRequest(AppDestination.Measurement, loggedViewId = "weight") }

        composeRule.onNodeWithTag("checklist_editor_discard_dialog").assertIsDisplayed()
        composeRule.onNodeWithTag("checklist_editor").assertIsDisplayed()
        composeRule.onNodeWithTag("checklist_editor_discard_dialog_confirm").performClick()
        composeRule.onNodeWithTag("checklist_editor").assertDoesNotExist()
        composeRule.onNodeWithTag("measurement_value").assertIsDisplayed()
        composeRule.runOnIdle {
            assertEquals(0, saves)
            assertEquals("Plants", state.configuredChecklists.single().label)
        }
    }

    /** A reminder's "Start reading" arriving while a page holds unsaved work starts no session until the way is taken. */
    @Test
    fun aReadingStartRequestedWhileEditingWaitsForTheAnswer() {
        state = AppState(time = listOf(TestReadingTime))
        setApp()
        createChecklist()
        openChecklistPage()
        composeRule.onNodeWithTag("checklist_editor_name").performScrollTo().performTextReplacement("Garden")

        composeRule.runOnIdle { request = AppNavigationRequest(AppDestination.Time, loggedViewId = "reading", startSession = true) }

        composeRule.onNodeWithTag("checklist_editor_discard_dialog").assertIsDisplayed()
        composeRule.onNodeWithTag("checklist_editor").assertIsDisplayed()
        composeRule.onNodeWithTag("time_reading_session").assertDoesNotExist()
        composeRule.onNodeWithTag("checklist_editor_discard_dialog_confirm").performClick()
        composeRule.onNodeWithTag("checklist_editor").assertDoesNotExist()
        // The session starts on arrival; on a fresh install its one-time focus question comes first.
        if (composeRule.onAllNodesWithTag("time_reading_focus_without").fetchSemanticsNodes().isNotEmpty()) {
            composeRule.onNodeWithTag("time_reading_focus_without").performClick()
        }
        composeRule.onNodeWithTag("time_reading_session").assertIsDisplayed()

        // Finish so the disposable package keeps no session running into the next test.
        composeRule.onNodeWithTag("time_reading_session_done").performClick()
    }

    /** Text typed into "New item" but not added holds Next and Create back, and the bar says so. */
    @Test
    fun theSetupWaitsForTextNotYetAdded() {
        setApp()
        composeRule.onNodeWithTag("home_settings").performClick()
        openChecklistSetup()
        composeRule.onNodeWithTag("checklist_setup_name").performScrollTo().performTextReplacement("Plants")
        composeRule.onNodeWithTag("checklist_setup_next").performClick()
        composeRule.onNodeWithTag("checklist_editor_add_item").performScrollTo().performTextReplacement("Water the plants")
        composeRule.onNodeWithTag("checklist_editor_add_item_button").performScrollTo().performClick()
        composeRule.onNodeWithTag("checklist_setup_next").assertIsEnabled()

        composeRule.onNodeWithTag("checklist_editor_add_item").performScrollTo().performTextReplacement("Prune")
        composeRule.onNodeWithTag("checklist_setup_next").assertIsNotEnabled()
        // A step that is not ready says why only when its button is pressed.
        composeRule.onNodeWithTag("checklist_setup_next").performClick()
        composeRule.onNodeWithTag("checklist_setup_hint").assertIsDisplayed()
        composeRule.onNodeWithTag("checklist_editor_add_item").performTextClearance()
        composeRule.onNodeWithTag("checklist_setup_next").assertIsEnabled()
    }

    /** A cleared name keeps Save back, and text typed into "New item" is work the guard asks about. */
    @Test
    fun aClearedNameHoldsSaveBackAndTypedTextIsGuarded() {
        setApp()
        createChecklist()
        openChecklistPage()

        composeRule.onNodeWithTag("checklist_editor_name").performScrollTo().performTextReplacement("G")
        composeRule.onNodeWithTag("checklist_editor_save").assertIsEnabled()
        composeRule.onNodeWithTag("checklist_editor_name").performTextClearance()
        composeRule.onNodeWithTag("checklist_editor_name").assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Error))
        composeRule.onNodeWithTag("checklist_editor_save").assertIsNotEnabled()
        composeRule.onNodeWithTag("checklist_editor_name").performTextReplacement("Garden")
        composeRule.onNodeWithTag("checklist_editor_save").assertIsEnabled().performClick()
        composeRule.runOnIdle { assertEquals("Garden", state.configuredChecklists.single().label) }

        // Saved, the page returned to the list, so the text is typed on the page opened again.
        openChecklistPage()
        composeRule.onNodeWithTag("checklist_editor_tab_items").performScrollTo().performClick()
        composeRule.onNodeWithTag("checklist_editor_add_item").performScrollTo().performTextReplacement("Prune")
        composeRule.onNodeWithTag("checklist_editor_save").assertIsNotEnabled()
        Espresso.closeSoftKeyboard()
        Espresso.pressBack()
        composeRule.onNodeWithTag("checklist_editor_discard_dialog").assertIsDisplayed()
        composeRule.onNodeWithTag("checklist_editor_discard_dialog_confirm").performClick()
        composeRule.onNodeWithTag("checklist_editor").assertDoesNotExist()
    }

    /**
     * Text typed into "New item" and not added keeps a saved page open, as leaving would lose it,
     * and the bar says what the page waits for whenever that text is all it still holds. Cleared,
     * nothing holds the page, and the next Save returns to the list.
     */
    @Test
    fun textTypedAndNotAddedHoldsTheSavedPageUntilItIsCleared() {
        setApp()
        createChecklist()
        openChecklistPage()
        val checklist = state.configuredChecklists.single()
        val itemName = "checklist_editor_item_${checklist.items.single().id}_name"
        val waitsForText = appString(R.string.settings_typed_text_pending)

        // The line is read from the draft, so it is there before anything was saved, and it
        // gives way to a change, which Save has to store first.
        composeRule.onNodeWithTag("checklist_editor_tab_items").performScrollTo().performClick()
        composeRule.onNodeWithTag("checklist_editor_add_item").performScrollTo().performTextReplacement("Prune")
        // A step that is not ready says why only when its button is pressed.
        composeRule.onNodeWithTag("checklist_editor_save").performClick()
        composeRule.onNodeWithTag("checklist_editor_hint").assertIsDisplayed().assertTextEquals(waitsForText)
        composeRule.onNodeWithTag(itemName).performScrollTo().performTextReplacement("Water them")
        composeRule.onNodeWithTag("checklist_editor_hint").assertDoesNotExist()

        // Save stores the change and the page stays, with the text still in its field.
        composeRule.onNodeWithTag("checklist_editor_save").assertIsEnabled().performClick()
        composeRule.runOnIdle {
            assertEquals(1, saves)
            assertEquals(listOf("Water them"), state.configuredChecklists.single().items.map { it.label })
        }
        composeRule.onNodeWithTag("checklist_editor").assertIsDisplayed()
        // A step that is not ready says why only when its button is pressed.
        composeRule.onNodeWithTag("checklist_editor_save").performClick()
        composeRule.onNodeWithTag("checklist_editor_hint").assertIsDisplayed().assertTextEquals(waitsForText)
        composeRule.onNodeWithTag("checklist_editor_save").assertIsNotEnabled()
        composeRule.onNodeWithTag("checklist_editor_add_item").assertInputEquals("Prune")

        // Cleared, the page waits for nothing, and the next change saved returns to the list.
        composeRule.onNodeWithTag("checklist_editor_add_item").performScrollTo().performTextClearance()
        composeRule.onNodeWithTag("checklist_editor_hint").assertDoesNotExist()
        composeRule.onNodeWithTag(itemName).performScrollTo().performTextReplacement("Water the garden")
        composeRule.onNodeWithTag("checklist_editor_save").assertIsEnabled().performClick()
        composeRule.runOnIdle {
            assertEquals(2, saves)
            assertEquals(listOf("Water the garden"), state.configuredChecklists.single().items.map { it.label })
        }
        composeRule.onNodeWithTag("checklist_editor").assertDoesNotExist()
        composeRule.onNodeWithTag("settings_group_checklist_${checklist.id}").performScrollTo().assertIsDisplayed()
    }

    /** A Save the store refuses stays on the page with its error and the change still there to save; stored, the page returns to the list. */
    @Test
    fun aSaveThatFailsStaysOnThePageWithItsError() {
        setApp()
        createChecklist()
        openChecklistPage()
        val stored = state.configuredChecklists.single()

        composeRule.onNodeWithTag("checklist_editor_name").performScrollTo().performTextReplacement("Garden")
        storeFails = true
        composeRule.onNodeWithTag("checklist_editor_save").performClick()
        composeRule.onNodeWithTag("checklist_editor_error").assertIsDisplayed().assertTextEquals(appString(R.string.checklist_error_save_failed))
        composeRule.onNodeWithTag("checklist_editor").assertIsDisplayed()
        composeRule.onNodeWithTag("checklist_editor_name").assertInputEquals("Garden")
        composeRule.onNodeWithTag("checklist_editor_save").assertIsEnabled()
        composeRule.runOnIdle { assertEquals(stored, state.configuredChecklists.single()) }

        storeFails = false
        composeRule.onNodeWithTag("checklist_editor_save").performClick()
        composeRule.runOnIdle { assertEquals("Garden", state.configuredChecklists.single().label) }
        composeRule.onNodeWithTag("checklist_editor").assertDoesNotExist()
        composeRule.onNodeWithTag("settings_group_checklist_${stored.id}").performScrollTo().assertIsDisplayed()
    }

    /** The Items step offers which day the items are about. Created today, a checklist about yesterday has nothing to tick yesterday, so its screen opens on today. */
    @Test
    fun aChecklistAboutYesterdayIsChosenOnTheItemsStep() {
        setApp()
        composeRule.onNodeWithTag("home_settings").performClick()
        openChecklistSetup()
        composeRule.onNodeWithTag("checklist_setup_name").performScrollTo().performTextReplacement("Yesterday")
        composeRule.onNodeWithTag("checklist_setup_next").performClick()

        composeRule.onNodeWithTag("checklist_editor_about_today").performScrollTo().assertIsSelected()
        composeRule.onNodeWithTag("checklist_editor_about_yesterday").performClick()
        composeRule.onNodeWithTag("checklist_editor_about_yesterday").assertIsSelected()
        composeRule.onNodeWithTag("checklist_editor_add_item").performScrollTo().performTextReplacement("Practiced violin")
        composeRule.onNodeWithTag("checklist_editor_add_item_button").performScrollTo().performClick()
        composeRule.onNodeWithTag("checklist_setup_next").performClick()
        composeRule.onNodeWithTag("checklist_setup_next").performClick()
        composeRule.onNodeWithTag("checklist_setup_create").performClick()
        val checklist = composeRule.runOnIdle { state.configuredChecklists.single() }
        assertEquals(today.minusDays(1), checklist.recordDayFor(today))

        Espresso.pressBack()
        composeRule.onNodeWithTag("home_checklist_${checklist.id}").performClick()
        composeRule.onNodeWithTag("checklist_${checklist.id}_history_$today").assertIsSelected()
        composeRule.onNodeWithTag("checklist_${checklist.id}_selected_day").assertDoesNotExist()
    }

    /** A checklist about yesterday opens on yesterday, and today's cell stays selectable. */
    @Test
    fun aChecklistAboutYesterdayOpensOnYesterday() {
        val lastNight = ChecklistConfiguration(
            id = "chk_night",
            label = "Yesterday",
            icon = ViewIcon.Journal,
            hue = ViewHue.Plum,
            checkpoints = listOf(Checkpoint("cpt_morning", CheckpointTime.Fixed(LocalTime.of(8, 0)), remind = false)),
            items = listOf(ChecklistItem("itm_violin", "Practiced violin")),
            about = Dated.always(AboutDay.Yesterday),
        )
        state = AppState(checklists = listOf(lastNight))
        setApp()

        composeRule.onNodeWithTag("home_checklist_chk_night").performClick()
        composeRule.onNodeWithTag("checklist_chk_night_history_${today.minusDays(1)}").assertIsSelected()
        composeRule.onNodeWithTag("checklist_chk_night_selected_day").assertDoesNotExist()
        composeRule.onNodeWithTag("checklist_chk_night_history_$today").performScrollTo().performClick()
        composeRule.onNodeWithTag("checklist_chk_night_history_$today").assertIsSelected()
        // Today is not the day this checklist asks about, so the screen names it.
        composeRule.onNodeWithTag("checklist_chk_night_selected_day").assertExists()
    }

    /** Each item on the Streak page says what a holiday means for it; the choice reaches storage only with Save. */
    @Test
    fun anItemsHolidayChoiceIsMadeOnTheStreakPageAndStoredOnSave() {
        setApp()
        createChecklist()
        openChecklistPage()
        val item = state.configuredChecklists.single().items.single()
        val tag = "checklist_editor_item_${item.id}_away"

        composeRule.onNodeWithTag("checklist_editor_tab_streak").performScrollTo().performClick()
        composeRule.onNodeWithTag("${tag}_due").performScrollTo().assertIsSelected()
        composeRule.onNodeWithTag("${tag}_excused").performScrollTo().assertIsNotSelected().performClick()
        composeRule.onNodeWithTag("${tag}_excused").assertIsSelected()
        composeRule.runOnIdle {
            assertEquals(0, saves)
            assertEquals(WhenAway.Due, state.configuredChecklists.single().items.single().whenAway.current)
        }

        composeRule.onNodeWithTag("checklist_editor_save").assertIsEnabled().performClick()
        composeRule.runOnIdle {
            assertEquals(1, saves)
            assertEquals(WhenAway.Excused, state.configuredChecklists.single().items.single().whenAway.current)
        }
    }

    /** The count a week item gets under Fewer times opens under the tile just pressed and takes no room before, so a page of items has no holes. */
    @Test
    fun theHolidayCountOpensUnderItsTilesAndTakesNoRoomBefore() {
        setApp()
        createChecklist()
        openChecklistPage()
        val first = state.configuredChecklists.single().items.single()

        // The first item three times a week, a second item under it, both stored.
        composeRule.onNodeWithTag("checklist_editor_tab_items").performScrollTo().performClick()
        composeRule.onNodeWithTag("checklist_editor_item_${first.id}_due").performScrollTo().performClick()
        composeRule.onNodeWithTag("checklist_editor_item_${first.id}_times_a_week").performScrollTo().performClick()
        composeRule.onNodeWithTag("checklist_editor_item_${first.id}_times_3").performScrollTo().performClick()
        composeRule.onNodeWithTag("checklist_editor_add_item").performScrollTo().performTextReplacement("Feed the plants")
        composeRule.onNodeWithTag("checklist_editor_add_item_button").performScrollTo().performClick()
        composeRule.onNodeWithTag("checklist_editor_save").performClick()
        val second = composeRule.runOnIdle { state.configuredChecklists.single().items.single { it.id != first.id } }

        // Saved, the page returned to the list; the holiday choices are read on the page opened again.
        openChecklistPage()
        composeRule.onNodeWithTag("checklist_editor_tab_streak").performScrollTo().performClick()
        val firstTile = "checklist_editor_item_${first.id}_away_due"
        val secondTile = "checklist_editor_item_${second.id}_away_due"
        fun gap() = gap(above = firstTile, below = secondTile)
        composeRule.onNodeWithTag(firstTile).performScrollTo()
        val before = gap()
        composeRule.onNodeWithTag("checklist_editor_item_${first.id}_away_times_1").assertDoesNotExist()
        composeRule.onNodeWithTag("checklist_editor_item_${first.id}_away_lowerTarget").performClick()
        composeRule.onNodeWithTag("checklist_editor_item_${first.id}_away_times_1").assertExists()
        assertTrue(gap() > before)
    }

    /** Fewer times appears once the item is due a number of times a week, with how many a week on holiday asks. */
    @Test
    fun aWeekItemCanAskFewerTimesOnHoliday() {
        setApp()
        createChecklist()
        openChecklistPage()
        val item = state.configuredChecklists.single().items.single()
        val tag = "checklist_editor_item_${item.id}"

        composeRule.onNodeWithTag("checklist_editor_tab_items").performScrollTo().performClick()
        composeRule.onNodeWithTag("${tag}_due").performScrollTo().performClick()
        composeRule.onNodeWithTag("${tag}_times_a_week").performScrollTo().performClick()
        composeRule.onNodeWithTag("${tag}_times_3").performScrollTo().performClick()

        composeRule.onNodeWithTag("checklist_editor_tab_streak").performScrollTo().performClick()
        // The count waits until fewer times is chosen, then starts at one of three, with neither none nor three on offer.
        composeRule.onNodeWithTag("${tag}_away_times_1").assertDoesNotExist()
        composeRule.onNodeWithTag("${tag}_away_lowerTarget").performScrollTo().assertIsNotSelected().performClick()
        composeRule.onNodeWithTag("${tag}_away_times_1").performScrollTo().assertIsSelected()
        composeRule.onNodeWithTag("${tag}_away_times_0").assertDoesNotExist()
        composeRule.onNodeWithTag("${tag}_away_times_3").assertDoesNotExist()
        composeRule.onNodeWithTag("${tag}_away_times_2").performClick()

        composeRule.onNodeWithTag("checklist_editor_save").performClick()
        composeRule.runOnIdle {
            val stored = state.configuredChecklists.single().items.single { it.id == item.id }
            assertEquals(WhenAway.LowerTarget, stored.whenAway.current)
            assertEquals(ChecklistTarget.PerWeek(times = 3), stored.target)
            assertEquals(2, stored.awayTimes)
        }
    }

    /** A week item lowered to nothing before Block 17 shows its 0 tile while that is its count, and keeps the slot once another is picked. */
    @Test
    fun aCountStoredBeforeBlock17KeepsItsTileAndItsSlot() {
        val violin = ChecklistItem("itm_v", "Violin", target = ChecklistTarget.PerWeek(times = 5), awayTimes = 0, whenAway = Dated.always(WhenAway.LowerTarget))
        val practice = ChecklistConfiguration(
            id = "chk_practice",
            label = "Practice",
            icon = ViewIcon.Music,
            hue = ViewHue.Indigo,
            checkpoints = listOf(Checkpoint("cpt_e", CheckpointTime.Fixed(LocalTime.of(21, 0)), remind = false)),
            items = listOf(violin),
        )
        state = AppState(checklists = listOf(practice))
        setApp()
        composeRule.onNodeWithTag("home_settings").performClick()
        composeRule.onNodeWithTag("settings_group_checklist_chk_practice").performScrollTo().performClick()
        val tag = "checklist_editor_item_itm_v_away"

        composeRule.onNodeWithTag("checklist_editor_tab_streak").performScrollTo().performClick()
        composeRule.onNodeWithTag("${tag}_lowerTarget").performScrollTo().assertIsSelected()
        composeRule.onNodeWithTag("${tag}_times_0").performScrollTo().assertIsSelected()
        composeRule.onNodeWithTag("${tag}_times_5").assertDoesNotExist()
        val before = composeRule.onNodeWithTag("${tag}_times_1").getBoundsInRoot()
        composeRule.onNodeWithTag("${tag}_times_2").performClick()
        composeRule.onNodeWithTag("${tag}_times_2").assertIsSelected()
        composeRule.onNodeWithTag("${tag}_times_0").assertDoesNotExist()
        assertEquals(before, composeRule.onNodeWithTag("${tag}_times_1").getBoundsInRoot())
    }

    /** An item due every day offers fewer times too: three of seven first, one tile per count between not due and still due. */
    @Test
    fun aDailyItemCanAskFewerTimesOnHoliday() {
        setApp()
        createChecklist()
        openChecklistPage()
        val item = state.configuredChecklists.single().items.single()
        val tag = "checklist_editor_item_${item.id}_away"

        composeRule.onNodeWithTag("checklist_editor_tab_streak").performScrollTo().performClick()
        composeRule.onNodeWithTag("${tag}_times_3").assertDoesNotExist()
        composeRule.onNodeWithTag("${tag}_lowerTarget").performScrollTo().assertIsNotSelected().performClick()
        composeRule.onNodeWithTag("${tag}_times_3").performScrollTo().assertIsSelected()
        composeRule.onNodeWithTag("${tag}_times_0").assertDoesNotExist()
        composeRule.onNodeWithTag("${tag}_times_7").assertDoesNotExist()
        composeRule.onNodeWithTag("${tag}_times_2").performClick()
        composeRule.runOnIdle { assertEquals(0, saves) }

        composeRule.onNodeWithTag("checklist_editor_save").performClick()
        composeRule.runOnIdle {
            val stored = state.configuredChecklists.single().items.single()
            assertEquals(WhenAway.LowerTarget, stored.whenAway.current)
            assertEquals(2, stored.awayTimes)
            assertEquals(null, stored.target)
        }
    }

    /** A once-a-week item lowered before Block 17 keeps Fewer times after going back to due, across pages too, so the choice can be undone, and back to fewer times it asks what it asked. */
    @Test
    fun aLegacyOnceAWeekItemKeepsFewerTimesAndItsMeaningAcrossStillDue() {
        val floss = ChecklistItem("itm_f", "Floss", target = ChecklistTarget.PerWeek(times = 1), whenAway = Dated.always(WhenAway.LowerTarget))
        val teeth = ChecklistConfiguration(
            id = "chk_teeth",
            label = "Teeth",
            icon = ViewIcon.Toothbrush,
            hue = ViewHue.Teal,
            checkpoints = listOf(Checkpoint("cpt_e", CheckpointTime.Fixed(LocalTime.of(21, 0)), remind = false)),
            items = listOf(floss, ChecklistItem("itm_b", "Brush")),
        )
        state = AppState(checklists = listOf(teeth))
        setApp()
        composeRule.onNodeWithTag("home_settings").performClick()
        composeRule.onNodeWithTag("settings_group_checklist_chk_teeth").performScrollTo().performClick()
        val tag = "checklist_editor_item_itm_f_away"
        val below = "checklist_editor_item_itm_b_away_due"
        fun gap() = gap(above = "${tag}_due", below = below)

        composeRule.onNodeWithTag("checklist_editor_tab_streak").performScrollTo().performClick()
        composeRule.onNodeWithTag("${tag}_lowerTarget").performScrollTo().assertIsSelected()
        composeRule.onNodeWithTag("${tag}_times_1").performScrollTo().assertIsSelected()
        composeRule.onNodeWithTag("${tag}_due").performScrollTo()
        val before = gap()
        composeRule.onNodeWithTag("${tag}_due").performClick()
        composeRule.onNodeWithTag("${tag}_due").assertIsSelected()
        composeRule.onNodeWithTag("${tag}_times_1").assertDoesNotExist()
        // The count closes with the choice, and the item below moves up into its room.
        assertTrue(gap() < before)
        // Another page and back, unsaved: the choice is still there to undo.
        composeRule.onNodeWithTag("checklist_editor_tab_items").performScrollTo().performClick()
        composeRule.onNodeWithTag("checklist_editor_tab_streak").performScrollTo().performClick()
        composeRule.onNodeWithTag("${tag}_due").performScrollTo().assertIsSelected()
        composeRule.onNodeWithTag("${tag}_lowerTarget").assertIsNotSelected().performClick()
        composeRule.onNodeWithTag("${tag}_times_1").assertIsSelected()
        assertEquals(before, gap())
    }

    /** The only item stays, and the button says why instead of failing. */
    @Test
    fun theLastItemCannotBeRemoved() {
        setApp()
        createChecklist()
        openChecklistPage()
        val item = state.configuredChecklists.single().items.single()

        composeRule.onNodeWithTag("checklist_editor_tab_items").performScrollTo().performClick()
        composeRule.onNodeWithTag("checklist_editor_item_${item.id}_remove").performScrollTo().assertIsNotEnabled()
        composeRule.onNodeWithTag("checklist_editor_item_${item.id}_keep").assertIsDisplayed()
    }

    /** Deleting goes through the one dialog, takes the records along and lands back on the list. */
    @Test
    fun aDeletedChecklistLeavesSettingsWithItsRecords() {
        setApp()
        createChecklist()
        openChecklistPage()
        val checklist = state.configuredChecklists.single()
        composeRule.runOnIdle {
            state = state.copy(checklistDays = listOf(ChecklistDay(checklist.id, currentDateKey(), 1L, setOf("${checklist.checkpoints.single().id}/itm_x"))))
        }

        composeRule.onNodeWithTag("checklist_editor_delete").performClick()
        composeRule.onNodeWithTag("checklist_editor_delete_dialog").assertIsDisplayed()
        composeRule.onNodeWithTag("checklist_editor_delete_cancel").performClick()
        composeRule.runOnIdle { assertEquals(listOf(checklist), state.configuredChecklists) }

        composeRule.onNodeWithTag("checklist_editor_delete").performClick()
        composeRule.onNodeWithTag("checklist_editor_delete_confirm").performClick()
        composeRule.runOnIdle {
            assertTrue(state.configuredChecklists.isEmpty())
            assertTrue(state.checklistDays.isEmpty())
        }
        composeRule.onNodeWithTag("checklist_editor").assertDoesNotExist()
        composeRule.onNodeWithTag("settings_group_checklist_${checklist.id}").assertDoesNotExist()
        composeRule.onNodeWithTag("settings_new").performScrollTo().assertIsDisplayed()
    }
}

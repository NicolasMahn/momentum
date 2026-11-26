package de.nicolasmahn.momentum.app

import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import de.nicolasmahn.momentum.R
import de.nicolasmahn.momentum.core.time.currentDateKey
import de.nicolasmahn.momentum.core.ui.theme.WorkoutTrackerTheme
import de.nicolasmahn.momentum.domain.AppState
import de.nicolasmahn.momentum.domain.checklist.ChecklistConfiguration
import de.nicolasmahn.momentum.domain.checklist.ChecklistDay
import de.nicolasmahn.momentum.domain.checklist.ChecklistItem
import de.nicolasmahn.momentum.domain.checklist.Checkpoint
import de.nicolasmahn.momentum.domain.checklist.CheckpointTime
import de.nicolasmahn.momentum.domain.checklist.Dated
import de.nicolasmahn.momentum.domain.checklist.DayRecord
import de.nicolasmahn.momentum.domain.checklist.ViewHue
import de.nicolasmahn.momentum.domain.checklist.ViewIcon
import de.nicolasmahn.momentum.domain.checklist.WhenAway
import de.nicolasmahn.momentum.domain.checklist.applyingEdits
import de.nicolasmahn.momentum.domain.checklist.recordOn
import de.nicolasmahn.momentum.testfixtures.StandardSupplementIds
import de.nicolasmahn.momentum.supplementsAppState
import de.nicolasmahn.momentum.testing.appString
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * "Only log misses" where it is looked for: chosen per item on a checklist's Streak tab, against
 * fewer times on holiday on the same tab, then marked missed on the screen and taken back. The
 * stored state is the proof.
 */
class OnlyLogMissesFlowTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val teeth = ChecklistConfiguration(
        id = "chk_teeth",
        label = "Teeth",
        icon = ViewIcon.Toothbrush,
        hue = ViewHue.Teal,
        checkpoints = listOf(Checkpoint("cpt_e", CheckpointTime.Fixed(LocalTime.of(21, 0)), remind = false)),
        items = listOf(ChecklistItem("itm_b", "Brush"), ChecklistItem("itm_v", "Vitamin")),
    )
    private var state by mutableStateOf(AppState(checklists = listOf(teeth)))
    private var request by mutableStateOf<AppNavigationRequest?>(null)
    private val today = LocalDate.parse(currentDateKey())

    // The brushing's two choices on the Streak tab, and the line under each that warns about the other.
    private val box = "checklist_editor_item_itm_b_exceptions_only"
    private val boxWarning = "${box}_note"
    private val stillDue = "checklist_editor_item_itm_b_away_due"
    private val fewerTimes = "checklist_editor_item_itm_b_away_lowerTarget"
    private val holidayWarning = "checklist_editor_item_itm_b_away_exceptions_only_note"

    private val fold = "checklist_editor_advanced"

    /** Every item's checkbox under "Only log misses", whatever ID an item added on the page was given. */
    private val anItemsBox = SemanticsMatcher("is an item's checkbox under \"Only log misses\"") {
        it.config.getOrNull(SemanticsProperties.TestTag)?.endsWith("_exceptions_only") == true
    }

    private fun setApp() {
        composeRule.setContent {
            WorkoutTrackerTheme {
                MomentumApp(
                    state = state,
                    onStateChange = { state = it; true },
                    onActivitySave = ActivitySaveNotExpected,
                    onChecklistSave = { id, edits -> state = state.applyingEdits(id, edits, today); true },
                    requestedNavigation = request,
                    onDestinationConsumed = { request = null },
                )
            }
        }
    }

    private fun openStreakTab(settingsRow: String = "settings_group_checklist_chk_teeth") {
        composeRule.onNodeWithTag("home_settings").performClick()
        composeRule.onNodeWithTag(settingsRow).performScrollTo().performClick()
        showTab("streak")
    }

    /** Shows a tab; on Streak with "Advanced" unfolded, since what it holds is what these tests are about. */
    private fun showTab(name: String) {
        composeRule.onNodeWithTag("checklist_editor_tab_$name").performScrollTo().performClick()
        if (name == "streak" && composeRule.onAllNodes(anItemsBox).fetchSemanticsNodes().isEmpty()) {
            composeRule.onNodeWithTag(fold).performScrollTo().performClick()
        }
    }

    private fun saysOfItsState(@StringRes words: Int) = SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, appString(words))

    /** A box that only says checked or not, as an item not ticked yet and a recorded tick do. */
    private val saysNothingMore = SemanticsMatcher.keyNotDefined(SemanticsProperties.StateDescription)

    /**
     * The choice is folded away under "Advanced" and closed until it is opened, so nobody setting
     * up a checklist meets it unasked or checks a box by a stray tap. Closed, it says what is chosen.
     */
    @Test
    fun theChoiceIsFoldedUnderAdvancedAndSaysWhatIsChosenWhileClosed() {
        setApp()
        composeRule.onNodeWithTag("home_settings").performClick()
        composeRule.onNodeWithTag("settings_group_checklist_chk_teeth").performScrollTo().performClick()
        composeRule.onNodeWithTag("checklist_editor_tab_streak").performScrollTo().performClick()

        composeRule.onAllNodes(anItemsBox).assertCountEquals(0)
        composeRule.onNodeWithTag(fold).performScrollTo().assertTextContains(appString(R.string.checklist_editor_advanced))

        composeRule.onNodeWithTag(fold).performClick()
        composeRule.onNodeWithTag(box).performScrollTo().assertIsOff().performClick()
        composeRule.onNodeWithTag(fold).performScrollTo().performClick()
        composeRule.onAllNodes(anItemsBox).assertCountEquals(0)
        composeRule.onNodeWithTag(fold).assertTextContains(appString(R.string.checklist_editor_advanced_only_misses, "Brush"))
    }

    @Test
    fun anItemThatLogsOnlyMissesIsMarkedMissedAndTakenBack() {
        setApp()
        openStreakTab()

        // Teeth does not count toward the streak, and the choice is there all the same. Only
        // misses, then fewer times, which turns it off, then only misses again, which sends fewer
        // times back to still due. Each line shows under the choice it warns about.
        composeRule.onNodeWithTag(box).performScrollTo().assertIsOff().performClick()
        composeRule.onNodeWithTag(box).assertIsOn()
        composeRule.onNodeWithTag(boxWarning).assertDoesNotExist()
        composeRule.onNodeWithTag(holidayWarning).performScrollTo().assertIsDisplayed()

        composeRule.onNodeWithTag(fewerTimes).performScrollTo().performClick()
        composeRule.onNodeWithTag(fewerTimes).assertIsSelected()
        composeRule.onNodeWithTag(holidayWarning).assertDoesNotExist()
        composeRule.onNodeWithTag(box).performScrollTo().assertIsOff()
        composeRule.onNodeWithTag(boxWarning).performScrollTo().assertIsDisplayed()

        composeRule.onNodeWithTag(box).performScrollTo().performClick()
        composeRule.onNodeWithTag(box).assertIsOn()
        composeRule.onNodeWithTag(boxWarning).assertDoesNotExist()
        composeRule.onNodeWithTag(stillDue).performScrollTo().assertIsSelected()
        composeRule.onNodeWithTag(holidayWarning).performScrollTo().assertIsDisplayed()

        composeRule.onNodeWithTag("checklist_editor_save").performClick()
        composeRule.runOnIdle {
            // Fewer times chose a count, which starts a new entry today, so the live one is read.
            val brush = state.checklists.single().items.single { it.id == "itm_b" && it.until == null }
            assertTrue(brush.exceptionsOnly.on(today))
            assertEquals(WhenAway.Due, brush.whenAway.on(today))
        }

        // On the screen the brushing is assumed done. A tap marks it missed, which its box says,
        // unlike the empty box of the vitamin not ticked yet, and saving records the miss alone.
        request = AppNavigationRequest(AppDestination.Checklist, checklistId = teeth.id)
        val brush = "checklist_chk_teeth_status_itm_b"
        composeRule.onNodeWithTag(brush).performScrollTo().assertIsOn().assert(saysOfItsState(R.string.checklist_item_assumed_done)).performClick()
        composeRule.onNodeWithTag(brush).assertIsOff().assert(saysOfItsState(R.string.checklist_item_missed))
        composeRule.onNodeWithTag("checklist_chk_teeth_status_itm_v").performScrollTo().assertIsOff().assert(saysNothingMore)
        composeRule.onNodeWithTag("checklist_chk_teeth_save").performScrollTo().performClick()
        composeRule.runOnIdle { assertEquals(DayRecord(misses = setOf("cpt_e/itm_b")), state.recordOn(teeth.id, today.toString())) }

        // Taking the miss back leaves nothing recorded.
        composeRule.onNodeWithTag(brush).performScrollTo().performClick()
        composeRule.onNodeWithTag(brush).assertIsOn()
        composeRule.onNodeWithTag("checklist_chk_teeth_save").performScrollTo().performClick()
        composeRule.runOnIdle { assertEquals(DayRecord(), state.recordOn(teeth.id, today.toString())) }
    }

    /**
     * The same exclusion on a page that opens with the box checked. An item not due on holiday
     * stays not due when its box is checked, so its line never speaks.
     */
    @Test
    fun onAPageOpenedCheckedEachChoiceEndsTheOther() {
        state = AppState(checklists = listOf(teeth.copy(items = listOf(ChecklistItem("itm_b", "Brush", exceptionsOnly = Dated.always(true)), ChecklistItem("itm_v", "Vitamin")))))
        setApp()
        openStreakTab()

        composeRule.onNodeWithTag(box).performScrollTo().assertIsOn()
        composeRule.onNodeWithTag(boxWarning).assertDoesNotExist()
        composeRule.onNodeWithTag(holidayWarning).performScrollTo().assertIsDisplayed()

        composeRule.onNodeWithTag(fewerTimes).performScrollTo().performClick()
        composeRule.onNodeWithTag(fewerTimes).assertIsSelected()
        composeRule.onNodeWithTag(holidayWarning).assertDoesNotExist()
        composeRule.onNodeWithTag(box).performScrollTo().assertIsOff()
        composeRule.onNodeWithTag(boxWarning).performScrollTo().assertIsDisplayed()

        composeRule.onNodeWithTag(box).performScrollTo().performClick()
        composeRule.onNodeWithTag(box).assertIsOn()
        composeRule.onNodeWithTag(boxWarning).assertDoesNotExist()
        composeRule.onNodeWithTag(stillDue).performScrollTo().assertIsSelected()
        composeRule.onNodeWithTag(holidayWarning).performScrollTo().assertIsDisplayed()

        val vitaminBox = "checklist_editor_item_itm_v_exceptions_only"
        val vitaminNotDue = "checklist_editor_item_itm_v_away_excused"
        composeRule.onNodeWithTag(vitaminNotDue).performScrollTo().performClick()
        composeRule.onNodeWithTag(vitaminBox).performScrollTo().assertIsOff()
        composeRule.onNodeWithTag("${vitaminBox}_note").assertDoesNotExist()
        composeRule.onNodeWithTag(vitaminBox).performClick()
        composeRule.onNodeWithTag(vitaminBox).assertIsOn()
        composeRule.onNodeWithTag(vitaminNotDue).performScrollTo().assertIsSelected()
    }

    /**
     * The list is the page's draft: an item added and not saved is listed, one checked and then
     * made due a number of times a week on the Items tab is gone, and made daily again it is
     * listed unchecked. The Items tab no longer holds the choice, and its folded item still says
     * where it is chosen.
     */
    @Test
    fun theListFollowsWhatTheItemsTabAddsAndChanges() {
        setApp()
        openStreakTab()
        composeRule.onAllNodes(anItemsBox).assertCountEquals(2)
        composeRule.onNodeWithTag("checklist_editor_exceptions_only_week_items").assertDoesNotExist()

        showTab("items")
        composeRule.onNodeWithTag("checklist_editor_add_item").performScrollTo().performTextReplacement("Rinse")
        composeRule.onNodeWithTag("checklist_editor_add_item_button").performScrollTo().performClick()
        showTab("streak")
        composeRule.onAllNodes(anItemsBox).assertCountEquals(3)

        composeRule.onNodeWithTag(box).performScrollTo().performClick()
        composeRule.onNodeWithTag(box).assertIsOn()
        showTab("items")
        composeRule.onNodeWithTag("checklist_editor_item_itm_b_due").performScrollTo()
            .assertTextContains(appString(R.string.checklist_editor_exceptions_only_summary), substring = true)
        unfoldWhenItIsDue("itm_b")
        composeRule.onNodeWithTag(box).assertDoesNotExist()
        composeRule.onNodeWithTag("checklist_editor_item_itm_b_times_a_week").performScrollTo().performClick()
        showTab("streak")
        composeRule.onNodeWithTag(box).assertDoesNotExist()
        composeRule.onAllNodes(anItemsBox).assertCountEquals(2)
        composeRule.onNodeWithTag("checklist_editor_exceptions_only_week_items").performScrollTo().assertIsDisplayed()

        showTab("items")
        unfoldWhenItIsDue("itm_b")
        composeRule.onNodeWithTag("checklist_editor_item_itm_b_every_day").performScrollTo().performClick()
        showTab("streak")
        composeRule.onNodeWithTag(box).performScrollTo().assertIsOff()
        composeRule.onAllNodes(anItemsBox).assertCountEquals(3)
        composeRule.onNodeWithTag("checklist_editor_exceptions_only_week_items").assertDoesNotExist()
        composeRule.runOnIdle { assertEquals(listOf(teeth), state.checklists) }
    }

    /** Unfolds an item's "When it's due" unless it is open already, as it stays while another tab is looked at. */
    private fun unfoldWhenItIsDue(itemId: String) {
        if (composeRule.onAllNodesWithTag("checklist_editor_item_${itemId}_every_day").fetchSemanticsNodes().isEmpty()) {
            composeRule.onNodeWithTag("checklist_editor_item_${itemId}_due").performScrollTo().performClick()
        }
    }

    /** Supplements has the choice like any checklist: an item checked and saved is assumed done on its screen that day. */
    @Test
    fun supplementsListsTheChoiceAndACheckedItemIsAssumedDoneOnItsScreen() {
        state = supplementsAppState()
        setApp()
        openStreakTab("settings_group_checklist_supplements")

        composeRule.onNodeWithTag("checklist_editor_item_${StandardSupplementIds.CREATINE}_exceptions_only").performScrollTo().assertIsOff().performClick()
        composeRule.onNodeWithTag("checklist_editor_save").performClick()
        composeRule.runOnIdle {
            assertTrue(state.checklists.single().items.single { it.id == StandardSupplementIds.CREATINE }.exceptionsOnly.on(today))
        }

        request = AppNavigationRequest(AppDestination.Checklist, checklistId = "supplements")
        composeRule.onNodeWithTag("checklist_supplements_status_${StandardSupplementIds.CREATINE}").performScrollTo().assertIsOn()
            .assert(saysOfItsState(R.string.checklist_item_assumed_done))
        composeRule.onNodeWithTag("checklist_supplements_status_${StandardSupplementIds.MULTIVITAMIN}").performScrollTo().assertIsOff().assert(saysNothingMore)
    }

    /**
     * A saved miss is a miss when the screen opens, where an item not ticked yet is not. The miss
     * and the assumed check swing, and a recorded tick marked missed comes back as the recorded
     * tick it was, not as an assumed one.
     */
    @Test
    fun aSavedMissOpensAsMissedAndARecordedTickMarkedMissedComesBack() {
        val items = listOf(
            ChecklistItem("itm_b", "Brush", exceptionsOnly = Dated.always(true)),
            ChecklistItem("itm_v", "Vitamin"),
            ChecklistItem("itm_r", "Rinse", exceptionsOnly = Dated.always(true)),
        )
        val saved = DayRecord(ticks = setOf("cpt_e/itm_r"), misses = setOf("cpt_e/itm_b"))
        state = AppState(
            checklists = listOf(teeth.copy(items = items)),
            checklistDays = listOf(ChecklistDay(teeth.id, today.toString(), 1L, saved.ticks, saved.misses)),
        )
        setApp()
        request = AppNavigationRequest(AppDestination.Checklist, checklistId = teeth.id)
        val brush = "checklist_chk_teeth_status_itm_b"
        val rinse = "checklist_chk_teeth_status_itm_r"

        composeRule.onNodeWithTag(brush).performScrollTo().assertIsOff().assert(saysOfItsState(R.string.checklist_item_missed))
        composeRule.onNodeWithTag("checklist_chk_teeth_status_itm_v").performScrollTo().assertIsOff().assert(saysNothingMore)

        composeRule.onNodeWithTag(brush).performScrollTo().performClick()
        composeRule.onNodeWithTag(brush).assertIsOn().assert(saysOfItsState(R.string.checklist_item_assumed_done))
        composeRule.onNodeWithTag(brush).performClick()
        composeRule.onNodeWithTag(brush).assertIsOff().assert(saysOfItsState(R.string.checklist_item_missed))

        composeRule.onNodeWithTag(rinse).performScrollTo().assertIsOn().assert(saysNothingMore).performClick()
        composeRule.onNodeWithTag(rinse).assertIsOff().assert(saysOfItsState(R.string.checklist_item_missed))
        composeRule.onNodeWithTag(rinse).performClick()
        composeRule.onNodeWithTag(rinse).assertIsOn().assert(saysNothingMore)

        // Nothing was saved, so the day's record is the one the screen opened on.
        composeRule.runOnIdle { assertEquals(saved, state.recordOn(teeth.id, today.toString())) }
    }
}

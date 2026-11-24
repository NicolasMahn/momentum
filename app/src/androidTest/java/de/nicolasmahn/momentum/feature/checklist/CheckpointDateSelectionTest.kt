package de.nicolasmahn.momentum.feature.checklist

import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import de.nicolasmahn.momentum.TestCareChecklist
import de.nicolasmahn.momentum.careDone
import de.nicolasmahn.momentum.careState
import de.nicolasmahn.momentum.domain.AppState
import de.nicolasmahn.momentum.testfixtures.LegacyCareAction
import de.nicolasmahn.momentum.testfixtures.LegacyCareSession
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import de.nicolasmahn.momentum.domain.holiday.HolidaySchedule

/** A checklist with several checkpoints, as Care is: a calendar day becomes what one checkpoint saves to. */
class CheckpointDateSelectionTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun emptyCalendarDayBecomesTheSaveTarget() {
        val saves = mutableListOf<AppState>()

        composeRule.setContent {
            MaterialTheme {
                ChecklistScreen(
                    state = careState(),
                    checklist = TestCareChecklist,
                    onStateChange = { saves += it; true },
                    todayKey = "2026-09-17",
                    daysOff = HolidaySchedule(),
                )
            }
        }

        composeRule.onNodeWithTag("checklist_care_history_2026-09-17").assertIsSelected()
        composeRule.onNodeWithTag("checklist_care_selected_day").assertDoesNotExist()
        composeRule.onNodeWithTag("checklist_care_history_2026-09-15")
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithTag("checklist_care_history_2026-09-15").assertIsSelected()
        composeRule.onNodeWithTag("checklist_care_selected_day").assertTextEquals("Tue, Sep 15")
        composeRule.onNodeWithTag("checklist_care_morning_toothbrushing")
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithTag("checklist_care_morning_save").performClick()

        composeRule.runOnIdle {
            assertEquals(1, saves.size)
            assertEquals(setOf(LegacyCareSession.MORNING to LegacyCareAction.TOOTHBRUSHING), saves.single().careDone("2026-09-15"))
            assertEquals(emptySet<Pair<LegacyCareSession, LegacyCareAction>>(), saves.single().careDone("2026-09-17"))
        }
    }

    /** A correction stays on its day when Android recreates the screen, as it did on Care's own screen. */
    @Test
    fun theSelectedDaySurvivesRecreation() {
        val restoration = StateRestorationTester(composeRule)
        restoration.setContent {
            MaterialTheme {
                ChecklistScreen(
                    state = careState(),
                    checklist = TestCareChecklist,
                    onStateChange = { true },
                    todayKey = "2026-09-17",
                    daysOff = HolidaySchedule(),
                )
            }
        }

        composeRule.onNodeWithTag("checklist_care_history_2026-09-15").performScrollTo().performClick()
        restoration.emulateSavedInstanceStateRestore()

        composeRule.onNodeWithTag("checklist_care_history_2026-09-15").assertIsSelected()
        composeRule.onNodeWithTag("checklist_care_selected_day").assertTextEquals("Tue, Sep 15")
    }
}

package com.example.workouttracker.core.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.example.workouttracker.core.ui.theme.WorkoutTrackerTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class MomentumDropdownTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun theClosedFieldSaysItsLabelAndValueAndOpensAListMarkingTheChoice() {
        val selected = mutableStateOf("English")
        composeRule.setContent {
            WorkoutTrackerTheme {
                MomentumDropdown(
                    label = "Language",
                    options = listOf("System language", "English", "Deutsch"),
                    selected = selected.value,
                    onSelect = { selected.value = it },
                    optionLabel = { it },
                    testTag = "field",
                    optionTestTag = { "option_$it" },
                )
            }
        }

        composeRule.onNodeWithTag("field")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.DropdownList))
            .assertTextEquals("Language", "English")
            .performClick()
        composeRule.onNodeWithTag("option_English").assertIsSelected()
        composeRule.onNodeWithTag("option_Deutsch").assertIsNotSelected().performClick()

        composeRule.runOnIdle { assertEquals("Deutsch", selected.value) }
        composeRule.onNodeWithTag("option_Deutsch").assertDoesNotExist()
        composeRule.onNodeWithTag("field").assertTextEquals("Language", "Deutsch")
    }
}

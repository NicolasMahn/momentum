package com.example.workouttracker.core.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextReplacement
import com.example.workouttracker.R
import com.example.workouttracker.core.ui.theme.WorkoutTrackerTheme
import com.example.workouttracker.testing.appString
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class MomentumDateFieldTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun errorReplacesTheLabelWithoutMovingAnything() {
        val label = appString(R.string.core_date_field_label)
        val invalidMessage = appString(R.string.core_date_field_invalid)
        val invalid = mutableStateOf(false)
        val value = mutableStateOf("2026-09-19")
        composeRule.setContent {
            WorkoutTrackerTheme {
                Column {
                    MomentumDateField(
                        value = value.value,
                        onValueChange = { value.value = it },
                        isError = invalid.value,
                        testTag = "date",
                    )
                    Text("below", modifier = Modifier.testTag("below"))
                }
            }
        }
        val fieldBefore = composeRule.onNodeWithTag("date").getBoundsInRoot()
        val belowBefore = composeRule.onNodeWithTag("below").getBoundsInRoot()
        composeRule.onNodeWithText(label, useUnmergedTree = true).assertIsDisplayed()

        invalid.value = true

        composeRule.onNodeWithText(invalidMessage, useUnmergedTree = true)
            .assertIsDisplayed()
        composeRule.onNodeWithText(label, useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithTag("date").assert(
            SemanticsMatcher.expectValue(SemanticsProperties.Error, invalidMessage),
        )
        assertEquals(fieldBefore, composeRule.onNodeWithTag("date").getBoundsInRoot())
        assertEquals(belowBefore, composeRule.onNodeWithTag("below").getBoundsInRoot())

        composeRule.onNodeWithTag("date").performTextReplacement("2026-09-18")
        composeRule.runOnIdle { assertEquals("2026-09-18", value.value) }
    }
}

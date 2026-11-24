package de.nicolasmahn.momentum.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.espresso.Espresso
import de.nicolasmahn.momentum.R
import de.nicolasmahn.momentum.core.ui.theme.WorkoutTrackerTheme
import de.nicolasmahn.momentum.domain.AppState
import de.nicolasmahn.momentum.domain.substance.SubstanceAmountUnit
import de.nicolasmahn.momentum.domain.substance.SubstanceKind
import de.nicolasmahn.momentum.domain.substance.SubstanceSettings
import de.nicolasmahn.momentum.testing.appString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * A substance chosen in Settings and logged on its screen. The stored state is the proof: the
 * choice changes only when Save says so, and a record names the substance that was chosen.
 */
class SubstanceSettingsFlowTest {
    @get:Rule
    val composeRule = createComposeRule()

    private var state by mutableStateOf(AppState())

    /** Whether the store refuses a write, as one that failed does. */
    private var storeFails = false

    private fun setApp() {
        composeRule.setContent {
            WorkoutTrackerTheme {
                MomentumApp(
                    state = state,
                    onStateChange = { updated ->
                        if (storeFails) {
                            false
                        } else {
                            state = updated
                            true
                        }
                    },
                    onActivitySave = ActivitySaveNotExpected,
                )
            }
        }
    }

    /** From the Settings list to the Substances page, on the part that lists the substances. */
    private fun openSubstancesPage() {
        composeRule.onNodeWithTag("settings_group_substances").performScrollTo().performClick()
        composeRule.onNodeWithTag("substance_settings_tab_substances").performScrollTo().performClick()
    }

    @Test
    fun aSubstanceChosenInSettingsIsOfferedOnTheScreenAndLogged() {
        setApp()

        // Nothing is tracked yet, so the screen says so and leads to the page.
        composeRule.onNodeWithTag("home_substances").performClick()
        composeRule.onNodeWithTag("substance_nothing_tracked").assertIsDisplayed()
        composeRule.onNodeWithTag("substance_choice_coffee").assertDoesNotExist()
        composeRule.onNodeWithTag("substance_choose").performScrollTo().performClick()
        composeRule.onNodeWithTag("substance_settings").assertIsDisplayed()

        // The choice stays on the page until Save.
        composeRule.onNodeWithTag("substance_settings_save").assertIsNotEnabled()
        composeRule.onNodeWithTag("substance_settings_tab_substances").performScrollTo().assertIsSelected().performClick()
        composeRule.onNodeWithTag("substance_settings_track_coffee").performScrollTo().performClick()
        composeRule.onNodeWithTag("substance_settings_track_coffee").assertIsSelected()
        composeRule.runOnIdle { assertEquals(SubstanceSettings(), state.substances) }
        composeRule.onNodeWithTag("substance_settings_save").performClick()
        composeRule.runOnIdle { assertEquals(setOf(SubstanceKind.COFFEE.id), state.substances.tracked) }

        // Saved, the page returns to the Settings list, wherever it was opened from. Home is one
        // step back, and the screen now offers coffee.
        composeRule.onNodeWithTag("substance_settings").assertDoesNotExist()
        composeRule.onNodeWithTag("settings_group_substances").performScrollTo().assertIsDisplayed()
        Espresso.pressBack()
        composeRule.onNodeWithTag("home_substances").performClick()
        composeRule.onNodeWithTag("substance_nothing_tracked").assertDoesNotExist()
        composeRule.onNodeWithTag("substance_choice_coffee").performClick()
        composeRule.onNodeWithTag("substance_optional_details").performClick()
        composeRule.onNodeWithTag("substance_amount_new-coffee-1").performTextReplacement("2")
        composeRule.onNodeWithTag("substance_save").performScrollTo().performClick()

        composeRule.runOnIdle {
            val log = state.substanceLogs.single()
            assertEquals(SubstanceKind.COFFEE.id, log.substanceId)
            assertEquals(2.0, log.amount)
            assertEquals(SubstanceAmountUnit.CUPS, log.amountUnit)
        }
    }

    @Test
    fun anOwnSubstanceIsNamedTrackedAndLoggedAndStaysWhileItHasRecords() {
        setApp()
        composeRule.onNodeWithTag("home_settings").performClick()
        openSubstancesPage()

        composeRule.onNodeWithTag("substance_settings_new_name").performScrollTo().performTextReplacement("Kratom")
        composeRule.onNodeWithTag("substance_settings_new_unit").performScrollTo().performClick()
        composeRule.onNodeWithTag("substance_settings_new_unit_grams").performClick()
        composeRule.onNodeWithTag("substance_settings_add").performScrollTo().performClick()
        composeRule.onNodeWithTag("substance_settings_save").performClick()

        val own = composeRule.runOnIdle { state.substances.own.single() }
        assertEquals("Kratom", own.label)
        assertEquals(SubstanceAmountUnit.GRAMS, own.unit)
        assertTrue(state.substances.isTracked(own.id))
        // Saved, the page returned to the list. Opened again, the substance can still go:
        // nothing was recorded of it.
        openSubstancesPage()
        composeRule.onNodeWithTag("substance_settings_remove_${own.id}").performScrollTo().assertIsDisplayed()

        Espresso.closeSoftKeyboard()
        Espresso.pressBack()
        Espresso.pressBack()
        composeRule.onNodeWithTag("home_substances").performClick()
        composeRule.onNodeWithTag("substance_choice_${own.id}").performClick()
        composeRule.onNodeWithTag("substance_save").performScrollTo().performClick()
        composeRule.runOnIdle { assertEquals(own.id, state.substanceLogs.single().substanceId) }

        // With a record, the substance can no longer be removed; it can still be untracked.
        composeRule.onNodeWithTag("substance_choose").assertDoesNotExist()
        Espresso.pressBack()
        composeRule.onNodeWithTag("home_settings").performClick()
        openSubstancesPage()
        composeRule.onNodeWithTag("substance_settings_remove_${own.id}").performScrollTo().assertIsNotEnabled()
        composeRule.onNodeWithTag("substance_settings_keep_${own.id}").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("substance_settings_track_${own.id}").performScrollTo().performClick()
        composeRule.onNodeWithTag("substance_settings_save").performClick()
        composeRule.runOnIdle {
            assertEquals(emptySet<String>(), state.substances.tracked)
            assertEquals(1, state.substanceLogs.size)
        }
    }

    /**
     * A name typed for a new substance and not added keeps the saved page open, as leaving would
     * lose it, and the bar says what the page waits for. Added, the name is a change like any
     * other, and saving it returns to the list.
     */
    @Test
    fun aNameTypedAndNotAddedHoldsTheSavedPageUntilItIsAdded() {
        setApp()
        composeRule.onNodeWithTag("home_settings").performClick()
        openSubstancesPage()

        // While a change waits for Save, the line has nothing to say yet.
        composeRule.onNodeWithTag("substance_settings_track_coffee").performScrollTo().performClick()
        composeRule.onNodeWithTag("substance_settings_new_name").performScrollTo().performTextReplacement("Kratom")
        composeRule.onNodeWithTag("substance_settings_hint").assertDoesNotExist()

        // Save stores the change and the page stays for the name.
        composeRule.onNodeWithTag("substance_settings_save").performClick()
        composeRule.runOnIdle {
            assertEquals(setOf(SubstanceKind.COFFEE.id), state.substances.tracked)
            assertTrue(state.substances.own.isEmpty())
        }
        composeRule.onNodeWithTag("substance_settings").assertIsDisplayed()
        // A step that is not ready says why only when its button is pressed.
        composeRule.onNodeWithTag("substance_settings_save").performClick()
        composeRule.onNodeWithTag("substance_settings_hint").assertIsDisplayed().assertTextEquals(appString(R.string.settings_typed_text_pending))
        composeRule.onNodeWithTag("substance_settings_save").assertIsNotEnabled()

        composeRule.onNodeWithTag("substance_settings_add").performScrollTo().performClick()
        composeRule.onNodeWithTag("substance_settings_hint").assertDoesNotExist()
        composeRule.onNodeWithTag("substance_settings_save").performClick()
        composeRule.runOnIdle { assertEquals(listOf("Kratom"), state.substances.own.map { it.label }) }
        composeRule.onNodeWithTag("substance_settings").assertDoesNotExist()
        composeRule.onNodeWithTag("settings_group_substances").performScrollTo().assertIsDisplayed()
    }

    /** A Save the store refuses stays on the page with its error and the choice still there to save; stored, the page returns to the list. */
    @Test
    fun aSaveThatFailsStaysOnThePageWithItsError() {
        setApp()
        composeRule.onNodeWithTag("home_settings").performClick()
        openSubstancesPage()

        composeRule.onNodeWithTag("substance_settings_track_coffee").performScrollTo().performClick()
        storeFails = true
        composeRule.onNodeWithTag("substance_settings_save").performClick()
        composeRule.onNodeWithTag("substance_settings_error").assertIsDisplayed().assertTextEquals(appString(R.string.substance_settings_error_save_failed))
        composeRule.onNodeWithTag("substance_settings").assertIsDisplayed()
        composeRule.onNodeWithTag("substance_settings_track_coffee").performScrollTo().assertIsSelected()
        composeRule.onNodeWithTag("substance_settings_save").assertIsEnabled()
        composeRule.runOnIdle { assertEquals(SubstanceSettings(), state.substances) }

        storeFails = false
        composeRule.onNodeWithTag("substance_settings_save").performClick()
        composeRule.runOnIdle { assertEquals(setOf(SubstanceKind.COFFEE.id), state.substances.tracked) }
        composeRule.onNodeWithTag("substance_settings").assertDoesNotExist()
        composeRule.onNodeWithTag("settings_group_substances").performScrollTo().assertIsDisplayed()
    }
}

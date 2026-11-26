package de.nicolasmahn.momentum.app

import androidx.compose.runtime.Composable
import de.nicolasmahn.momentum.feature.settings.EqualHeightColumn
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import de.nicolasmahn.momentum.core.ui.theme.WorkoutTrackerTheme
import de.nicolasmahn.momentum.domain.AppState
import de.nicolasmahn.momentum.domain.measurement.withWeight
import de.nicolasmahn.momentum.feature.settings.SettingsPage
import de.nicolasmahn.momentum.feature.weight.transcript
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Settings has one way in for whatever a person adds to track. Its button opens a page that asks
 * what kind, a card for each, and a card opens that kind's setup; the page itself holds nothing.
 */
@RunWith(RobolectricTestRunner::class)
class NewViewPageTest {
    @get:Rule
    val compose = createComposeRule()

    private val kindTags = listOf("settings_checklist_new", "settings_measurement_new", "settings_questions_new", "settings_time_new")
    private val records = AppState().withWeight()
    private var state by mutableStateOf(records)

    @Composable
    private fun App() {
        WorkoutTrackerTheme { MomentumApp(state = state, onStateChange = { state = it; true }, onActivitySave = ActivitySaveNotExpected) }
    }

    private fun click(tag: String) {
        compose.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
    }

    private fun said(text: String): Boolean = "text=\"$text\"" in compose.transcript()

    @Test
    fun oneButtonAsksWhatKindAndEachKindOpensItsSetup() {
        compose.setContent { App() }
        click("home_settings")

        // The list has the one button, and no button of a kind's own.
        compose.onNodeWithTag("settings_new").assertExists()
        assertTrue(compose.transcript().toString(), said("Track something new"))
        kindTags.forEach { tag -> compose.onNodeWithTag(tag).assertDoesNotExist() }

        // The page is titled like the button, with a card for each kind: its name and what it is.
        click("settings_new")
        val page = compose.transcript()
        assertTrue(page.toString(), said("Track something new"))
        assertTrue(page.toString(), said("Checklist") && said("Such as medication and brushing your teeth, or cleaning once a week."))
        assertTrue(page.toString(), said("Measurement") && said("Such as your weight."))
        assertTrue(page.toString(), said("Questions") && said("Multiple-choice questions to answer each day."))
        assertTrue(page.toString(), said("Timer") && said("Such as reading, meditating or practising an instrument."))
        // A plain page: nothing to save, nothing to delete, no parts, and no word of "view".
        assertFalse(page.toString(), page.any { "_save]" in it || "_delete]" in it || "_tab_" in it || "view" in it.lowercase() })
        compose.onNodeWithTag("settings_new").assertDoesNotExist()

        click("settings_checklist_new")
        compose.onNodeWithTag("checklist_setup").assertExists()
        assertTrue(said("New checklist"))
        // Leaving a setup returns to the Settings list, not to the page that asked.
        click("top_back")
        compose.onNodeWithTag("checklist_setup").assertDoesNotExist()
        compose.onNodeWithTag("settings_new").assertExists()
        compose.onNodeWithTag("settings_checklist_new").assertDoesNotExist()

        click("settings_new")
        click("settings_measurement_new")
        compose.onNodeWithTag("measurement_setup").assertExists()
        assertTrue(said("New measurement"))
        click("top_back")
        compose.onNodeWithTag("measurement_setup").assertDoesNotExist()

        click("settings_new")
        click("settings_questions_new")
        compose.onNodeWithTag("questions_setup").assertExists()
        assertTrue(said("New questions"))
        click("top_back")
        compose.onNodeWithTag("questions_setup").assertDoesNotExist()

        click("settings_new")
        click("settings_time_new")
        compose.onNodeWithTag("time_setup").assertExists()
        assertTrue(said("New timer"))
        click("top_back")
        compose.onNodeWithTag("time_setup").assertDoesNotExist()
        compose.onNodeWithTag("settings_new").assertExists()
        assertEquals(records, state)
    }

    /** The page holds nothing: it is still there after the activity was recreated, and leaving it asks nothing. */
    @Test
    fun thePageSurvivesTheActivityBeingRecreatedAndLeavingItAsksNothing() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent { App() }
        click("home_settings")
        click("settings_new")

        restoration.emulateSavedInstanceStateRestore()
        compose.waitForIdle()

        kindTags.forEach { tag -> compose.onNodeWithTag(tag).assertExists() }
        click("top_back")
        kindTags.forEach { tag -> compose.onNodeWithTag(tag).assertDoesNotExist() }
        compose.onNodeWithTag("settings_new").assertExists()
        assertFalse(compose.transcript().toString(), compose.transcript().any { "discard" in it })
        assertEquals(records, state)
    }

    /** A kind is one entry: its card opens its own setup, under a tag of its own. */
    /** The cards are one height on a narrow phone too, where lines of different length wrap differently. */
    @Test
    @Config(qualifiers = "w320dp-h640dp")
    fun theCardsAreOneHeightWhereOnlySomeLinesWrap() {
        compose.setContent { App() }
        click("home_settings")
        click("settings_new")

        val cards = kindTags.map { tag -> compose.onNodeWithTag(tag).fetchSemanticsNode().size.height }
        assertEquals(cards.toString(), 1, cards.distinct().size)
    }

    /** The column behind it gives every child the height of the tallest. */
    @Test
    fun aColumnOfOneHeightMakesEveryChildAsTallAsTheTallest() {
        compose.setContent {
            EqualHeightColumn(spacing = 12.dp) {
                Box(Modifier.testTag("short").fillMaxWidth().height(40.dp))
                Box(Modifier.testTag("tall").fillMaxWidth().height(80.dp))
            }
        }
        val tall = compose.onNodeWithTag("tall").fetchSemanticsNode()
        val short = compose.onNodeWithTag("short").fetchSemanticsNode()
        assertEquals(tall.size.height, short.size.height)
        assertTrue(tall.positionInRoot.y > short.positionInRoot.y + short.size.height)
    }

    @Test
    @Config(qualifiers = "de")
    fun thePageSpeaksGerman() {
        compose.setContent { App() }
        click("home_settings")
        assertTrue(compose.transcript().toString(), said("Etwas Neues erfassen"))

        click("settings_new")
        val page = compose.transcript()
        assertTrue(page.toString(), said("Etwas Neues erfassen"))
        assertTrue(page.toString(), said("Checkliste") && said("Etwa Medikamente und Zähneputzen oder einmal pro Woche putzen."))
        assertTrue(page.toString(), said("Messwert") && said("Etwa dein Gewicht."))
        assertTrue(page.toString(), said("Fragen") && said("Multiple-Choice-Fragen, die du jeden Tag beantwortest."))
    }
}

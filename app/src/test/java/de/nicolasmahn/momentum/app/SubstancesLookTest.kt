package de.nicolasmahn.momentum.app

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Science
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import de.nicolasmahn.momentum.R
import de.nicolasmahn.momentum.core.text.UiText
import de.nicolasmahn.momentum.core.text.resolve
import de.nicolasmahn.momentum.core.ui.ViewIdentity
import de.nicolasmahn.momentum.core.ui.theme.WorkoutTrackerTheme
import de.nicolasmahn.momentum.core.ui.viewColors
import de.nicolasmahn.momentum.domain.AppState
import de.nicolasmahn.momentum.domain.SubstancesTrackerRef
import de.nicolasmahn.momentum.domain.checklist.MoMotif
import de.nicolasmahn.momentum.domain.checklist.ViewHue
import de.nicolasmahn.momentum.domain.checklist.ViewIcon
import de.nicolasmahn.momentum.domain.mo.moMood
import de.nicolasmahn.momentum.domain.reminder.Bedtime
import de.nicolasmahn.momentum.domain.settings.FeatureVisibilitySettings
import de.nicolasmahn.momentum.domain.substance.SubstanceEdit
import de.nicolasmahn.momentum.domain.substance.SubstanceGoal
import de.nicolasmahn.momentum.domain.substance.SubstanceMotifs
import de.nicolasmahn.momentum.domain.substance.SubstanceSettings
import de.nicolasmahn.momentum.domain.substance.applyEdit
import de.nicolasmahn.momentum.domain.substance.applyingEdits
import de.nicolasmahn.momentum.feature.checklist.momentumHue
import de.nicolasmahn.momentum.feature.checklist.vector
import de.nicolasmahn.momentum.feature.mo.label
import de.nicolasmahn.momentum.feature.settings.ChecklistPage
import de.nicolasmahn.momentum.feature.settings.SubstanceSettingsPage
import de.nicolasmahn.momentum.feature.substances.viewIdentity
import de.nicolasmahn.momentum.feature.substances.viewLabel
import de.nicolasmahn.momentum.feature.weight.transcript
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Substances wears the name and the look its settings hold, wherever it shows, and they are chosen
 * on the Look part every view's page has. Settings stored before it had a look hold none, and wear
 * what Substances always wore: the flask in pink, under the app's own word.
 */
@RunWith(RobolectricTestRunner::class)
class SubstancesLookTest {
    @get:Rule
    val compose = createComposeRule()

    private val today = LocalDate.parse("2026-11-14")
    private val evenings = SubstanceSettings.Legacy.copy(label = "Evenings", icon = ViewIcon.Drink, hue = ViewHue.Berry)
    private val resources get() = ApplicationProvider.getApplicationContext<Context>().resources

    private fun click(tag: String) {
        compose.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
    }

    private fun said(text: String): Boolean = "text=\"$text\"" in compose.transcript().toString()

    private fun names(tag: String, name: String) =
        compose.onNode(hasTestTag(tag) and hasAnyDescendant(hasText(name)), useUnmergedTree = true).assertExists()

    @Test
    fun settingsWithoutALookWearWhatSubstancesAlwaysWore() {
        val stored = SubstanceSettings.Legacy
        var actual: ViewIdentity? = null
        compose.setContent { WorkoutTrackerTheme { actual = stored.viewIdentity() } }

        assertNull(stored.label)
        assertEquals("Substances", stored.viewLabel().resolve(resources))
        compose.waitForIdle()
        assertEquals(Icons.Rounded.Science, requireNotNull(actual).icon)
        assertEquals(ViewHue.Pink.momentumHue.viewColors(), requireNotNull(actual).colors)
    }

    /** Home, the header of its screen and of its page, its row in Settings and its bank on the streak view call it what it is called. */
    @Test
    fun aRenamedSubstancesIsCalledByItsNameWhereverItShows() {
        val joined = evenings.applyEdit(SubstanceEdit.SetGoal(SubstanceGoal.Logging), LocalDate.parse("2026-10-01"))
        compose.setContent {
            WorkoutTrackerTheme {
                MomentumApp(state = AppState(substances = joined), onStateChange = { true }, onActivitySave = ActivitySaveNotExpected, featureVisibility = FeatureVisibilitySettings(disabledFeatures = emptySet()))
            }
        }

        names("home_substances", "Evenings")
        click("home_substances")
        assertTrue(compose.transcript().toString(), said("Evenings") && !said("Substances"))
        click("top_back")

        click("home_streak")
        compose.onNodeWithTag("streak_substances").assertExists()
        assertTrue(compose.transcript().toString(), "Evenings" in compose.transcript().toString() && !said("Substances"))
        click("top_back")

        click("home_settings")
        names("settings_group_substances", "Evenings")
        compose.onNodeWithTag("settings_feature_substances").assert(hasContentDescription("Show Evenings on Home"))
        click("settings_group_substances")
        // The header says its name; the part that lists the substances keeps the app's word for them.
        assertTrue(compose.transcript().toString(), said("Evenings") && said("Substances"))
    }

    /** Mo's lines name it as typed, and by the app's own word while it has no name. */
    @Test
    fun moNamesItByItsStoredNameOrTheAppsWord() {
        fun label(settings: SubstanceSettings): UiText {
            val joined = settings.applyEdit(SubstanceEdit.SetGoal(SubstanceGoal.Logging), today.minusDays(3))
            return moMood(AppState(substances = joined), FeatureVisibilitySettings(), today).label(SubstancesTrackerRef)
        }

        assertEquals(UiText.Verbatim("Evenings"), label(evenings))
        assertEquals(UiText.Resource(R.string.feature_substances), label(SubstanceSettings.Legacy))
    }

    private fun showPage(stored: () -> SubstanceSettings, onSave: (List<SubstanceEdit>) -> Unit, opensOn: ChecklistPage = ChecklistPage.Look) {
        compose.setContent {
            WorkoutTrackerTheme {
                SubstanceSettingsPage(
                    substances = stored(),
                    recorded = emptySet(),
                    today = today,
                    bedtime = Bedtime(LocalTime.of(7, 0), fixedBedtime = null),
                    onSave = { edits -> onSave(edits); true },
                    reminderRow = { _, _ -> },
                    onClose = {},
                    leaveRequested = false,
                    onLeaveAnswered = {},
                    onUnsavedWorkChange = {},
                    opensOn = opensOn,
                )
            }
        }
    }

    /** The page opens on Look, with the four controls every view's Look has, and Save stores what was chosen. */
    @Test
    fun theLookPartSetsTheNameTheIconTheColorAndMosFlavor() {
        var stored by mutableStateOf(SubstanceSettings.Legacy)
        showPage({ stored }, onSave = { stored = stored.applyingEdits(it, today) })

        compose.onNodeWithTag("substance_settings_tab_look").assertIsSelected()
        // The name field shows the app's own word while the view has no name of its own.
        assertTrue(compose.transcript().toString(), "field=\"Substances\"" in compose.transcript().toString())
        compose.onNodeWithTag("substance_settings_icon_${ViewIcon.Science.storageId}").assertIsSelected()
        compose.onNodeWithTag("substance_settings_hue_${ViewHue.Pink.storageId}").assertIsSelected()
        compose.onNodeWithTag("substance_settings_motif_none").assertIsSelected()
        // Every flavor is offered but the pills.
        SubstanceMotifs.forEach { compose.onNodeWithTag("substance_settings_motif_${it.storageId}").assertExists() }
        compose.onNodeWithTag("substance_settings_motif_${MoMotif.Pills.storageId}").assertDoesNotExist()

        compose.onNodeWithTag("substance_settings_name").performTextReplacement("Evenings")
        click("substance_settings_icon_${ViewIcon.Drink.storageId}")
        click("substance_settings_hue_${ViewHue.Berry.storageId}")
        click("substance_settings_motif_${MoMotif.Food.storageId}")
        click("substance_settings_save")

        assertEquals("Evenings", stored.label)
        assertEquals(ViewIcon.Drink, stored.icon)
        assertEquals(ViewHue.Berry, stored.hue)
        assertEquals(MoMotif.Food, stored.mo.motif)
        // The flag no screen shows is left as it was.
        assertFalse(stored.mo.enabled)
    }

    /** The app's own word typed back is no name of one's own: the view follows the app's language again. */
    @Test
    fun typingTheAppsWordBackReturnsToIt() {
        var stored by mutableStateOf(evenings)
        showPage({ stored }, onSave = { stored = stored.applyingEdits(it, today) })

        compose.onNodeWithTag("substance_settings_name").performTextReplacement("Substances")
        click("substance_settings_save")

        assertNull(stored.label)
    }

    /** A blank name is none: the field says so and Save waits, as on every view's Look. */
    @Test
    fun aBlankNameHoldsSaveBack() {
        var saves = 0
        showPage({ evenings }, onSave = { saves++ })

        compose.onNodeWithTag("substance_settings_name").performTextReplacement("Late")
        compose.onNodeWithTag("substance_settings_name").performTextReplacement(" ")
        click("substance_settings_save")

        assertEquals(0, saves)
    }

    /** Mo's flavor does nothing on a view he does not watch, and the Look part says what would change that. */
    @Test
    fun theFlavorSaysWhileMoDoesNotWatchTheView() {
        var stored by mutableStateOf(SubstanceSettings.Legacy)
        showPage({ stored }, onSave = {})

        compose.onNodeWithTag("substance_settings_motif_unwatched").assertExists()
        compose.runOnIdle { stored = stored.applyEdit(SubstanceEdit.SetGoal(SubstanceGoal.Logging), today) }
        compose.waitForIdle()
        compose.onNodeWithTag("substance_settings_motif_unwatched").assertDoesNotExist()
        // The rule itself stands under "Counts toward the streak".
        click("substance_settings_tab_streak")
        compose.onNodeWithTag("substance_settings_mo_watches").assertExists()
    }
}

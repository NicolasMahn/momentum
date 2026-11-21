package com.example.workouttracker.app

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.core.app.ApplicationProvider
import com.example.workouttracker.core.ui.theme.WorkoutTrackerTheme
import com.example.workouttracker.domain.AppState
import com.example.workouttracker.feature.about.licenseParagraphs
import com.example.workouttracker.feature.about.licenseText
import com.example.workouttracker.feature.about.openSourceComponents
import com.example.workouttracker.feature.about.LegalText
import com.example.workouttracker.feature.about.legalBlocks
import com.example.workouttracker.feature.about.legalText
import java.util.Locale
import com.example.workouttracker.feature.weight.transcript
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Settings' last entry opens a page with the app's version and every open-source component the app
 * ships; a component opens the full text of its license, and Back leads to the list of components.
 */
@RunWith(RobolectricTestRunner::class)
class AboutPageTest {
    @get:Rule
    val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val components = context.openSourceComponents()
    private var state by mutableStateOf(AppState())

    @Composable
    private fun App() {
        WorkoutTrackerTheme { MomentumApp(state = state, onStateChange = { state = it; true }, onActivitySave = ActivitySaveNotExpected) }
    }

    private fun click(tag: String) {
        compose.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
    }

    private fun said(text: String): Boolean = compose.transcript().any { "text=\"$text\"" in it }

    private fun openAbout() {
        compose.setContent { App() }
        click("home_settings")
        click("settings_open_about")
    }

    @Test
    fun thePageSaysTheVersionAndListsEveryComponentWithItsLicense() {
        openAbout()

        val page = compose.transcript().toString()
        assertTrue(page, said("About Momentum"))
        val version = context.packageManager.getPackageInfo(context.packageName, 0).versionName
        assertTrue(page, version != null && said("Version $version"))
        assertTrue(page, said("Open-source licenses"))
        components.forEachIndexed { index, component ->
            assertTrue("$component in $page", said(component.name) && said(component.license.name))
            compose.onNodeWithTag("about_component_$index").assertExists()
        }
        compose.onNodeWithTag("about_component_${components.size}").assertDoesNotExist()
    }

    @Test
    fun aComponentOpensTheFullTextOfItsLicenseAndBackReturnsToTheList() {
        openAbout()
        val index = components.indexOfFirst { it.notice != null && it.license.id == "apache-2.0" }
        val component = components[index]

        click("about_component_$index")

        val page = compose.transcript()
        assertTrue(page.toString(), said("License") && said(component.name) && said(component.license.name))
        assertTrue(page.toString(), said(component.notice!!))
        compose.onNodeWithTag("license_source").assertExists()
        // The whole license is on the page, first paragraph to last.
        val paragraphs = licenseParagraphs(context.licenseText(component.license))
        assertEquals(paragraphs, paragraphs.indices.map { compose.transcript().single { line -> line.startsWith("[license_paragraph_$it]") }.substringAfter("text=\"").removeSuffix("\"") })

        // Back from a license is the list of components, and Back from there is Settings.
        click("top_back")
        compose.onNodeWithTag("about_component_$index").assertExists()
        compose.onNodeWithTag("license_source").assertDoesNotExist()
        click("top_back")
        compose.onNodeWithTag("settings_open_about").assertExists()
    }

    /** Google Play asks for the policy inside the app: the whole of it is one press from About, and Back leads there again. */
    @Test
    fun thePrivacyPolicyOpensInFullAndBackReturnsToAbout() {
        openAbout()

        click("about_privacy")

        val blocks = legalBlocks(context.legalText(LegalText.PrivacyPolicy, Locale.ENGLISH))
        val shown = compose.transcript().filter { it.startsWith("[legal_") }.map { it.substringAfter("text=\"").removeSuffix("\"") }
        assertTrue(compose.transcript().toString(), said("Privacy policy"))
        assertEquals(blocks.map { it.text.text }, shown)

        click("top_back")
        compose.onNodeWithTag("about_privacy").assertExists()
    }

    @Test
    @Config(qualifiers = "de")
    fun inGermanThePrivacyPolicyIsTheGermanOne() {
        openAbout()

        click("about_privacy")

        val german = legalBlocks(context.legalText(LegalText.PrivacyPolicy, Locale.GERMAN))
        assertTrue(compose.transcript().toString(), said("Datenschutzerklärung") && said(german.first().text.text))
    }

    /** German law asks who offers the app to be easy to find: it is one press from About, under the word Germans look for. */
    @Test
    @Config(qualifiers = "de")
    fun theLegalNoticeOpensInFullAndBackReturnsToAbout() {
        openAbout()

        click("about_legal_notice")

        val blocks = legalBlocks(context.legalText(LegalText.LegalNotice, Locale.GERMAN))
        val shown = compose.transcript().filter { it.startsWith("[legal_") }.map { it.substringAfter("text=\"").removeSuffix("\"") }
        assertTrue(compose.transcript().toString(), said("Impressum"))
        assertEquals(blocks.map { it.text.text }, shown)

        click("top_back")
        compose.onNodeWithTag("about_legal_notice").assertExists()
    }

    /** The list is longer than a screen: a license opened from its end starts at its own top, and Back finds the list where it was. */
    @Test
    fun aLicenseOpensAtItsTopAndBackFindsTheListWhereItWasLeft() {
        openAbout()
        val last = "about_component_${components.lastIndex}"
        compose.onNodeWithTag(last).performScrollTo()
        compose.onNodeWithTag("about_version").assertIsNotDisplayed()

        click(last)
        compose.onNodeWithTag("license_component").assertIsDisplayed()

        click("top_back")
        compose.onNodeWithTag(last).assertIsDisplayed()
        compose.onNodeWithTag("about_version").assertIsNotDisplayed()
    }

    /** Names and licenses are proper nouns and legal text: German changes the page's own words only. */
    @Test
    @Config(qualifiers = "de")
    fun inGermanThePageSpeaksGermanAndTheComponentsKeepTheirNames() {
        openAbout()

        val page = compose.transcript().toString()
        assertTrue(page, said("Über Momentum") && said("Open-Source-Lizenzen"))
        components.forEach { component -> assertTrue("$component in $page", said(component.name) && said(component.license.name)) }

        click("about_component_0")
        assertTrue(compose.transcript().toString(), said("Lizenz") && said("Projektseite") && said(components.first().name))
    }
}

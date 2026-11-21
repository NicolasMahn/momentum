package com.example.workouttracker.app

import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.platform.app.InstrumentationRegistry
import com.example.workouttracker.R
import com.example.workouttracker.TestReadingTime
import com.example.workouttracker.TestWeightMeasurement
import com.example.workouttracker.core.locale.AppLanguage
import com.example.workouttracker.core.locale.applyAppLanguage
import com.example.workouttracker.core.locale.currentAppLanguage
import com.example.workouttracker.testfixtures.weightMeasurement
import com.example.workouttracker.testfixtures.readingTime
import com.example.workouttracker.persistence.FirstStartLocal
import com.example.workouttracker.persistence.PreferencesTrackerRepository
import com.example.workouttracker.testing.assertInputEquals
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain

/**
 * A language change rewords the open screen in place. It must never recreate the activity, which
 * would drop a typed draft or a running reading session along with the old words.
 */
class LanguageSwitchTest {
    private val composeRule = createAndroidComposeRule<MainActivity>()

    /**
     * A new installation tracks nothing, so the debug package's records are given Weight and
     * Reading, where they lack them, before the app starts, and its first start is answered: the
     * test begins on Home whatever the package was left in.
     */
    private val weightAndReading = object : ExternalResource() {
        override fun before() {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            FirstStartLocal(context).answer()
            PreferencesTrackerRepository(context).update { state ->
                state.copy(
                    measurements = if (state.weightMeasurement == null) state.measurements + TestWeightMeasurement else state.measurements,
                    time = if (state.readingTime == null) state.time + TestReadingTime else state.time,
                )
            }
        }
    }

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(weightAndReading).around(composeRule)

    @Before
    fun followThePhone() = setLanguage(AppLanguage.System)

    @After
    fun leaveThePhoneLanguage() = setLanguage(AppLanguage.System)

    @Test
    fun switchingLanguageRewordsTheScreenAndKeepsTheDraft() {
        val activity = composeRule.activity
        composeRule.onNodeWithTag("home_measurement_weight").performClick()
        composeRule.onNodeWithTag("measurement_value").performTextReplacement("81,7")

        setLanguage(AppLanguage.German)
        composeRule.onNodeWithText("Weight in ⁨kg⁩").assertExists()
        composeRule.onNodeWithTag("measurement_save").assertTextEquals(composeRule.activity.getString(R.string.measurement_save))
        composeRule.onNodeWithTag("measurement_value").assertInputEquals("81,7")

        setLanguage(AppLanguage.English)
        composeRule.onNodeWithText("Weight in ⁨kg⁩").assertExists()
        composeRule.onNodeWithTag("measurement_save").assertTextEquals(composeRule.activity.getString(R.string.measurement_save))
        composeRule.onNodeWithTag("measurement_value").assertInputEquals("81,7")
        assertSame(activity, composeRule.activity)
    }

    @Test
    fun aRunningReadingSessionSurvivesALanguageChange() {
        composeRule.onNodeWithTag("home_time_reading").performClick()
        composeRule.onNodeWithTag("time_reading_start").performClick()
        // The first start asks about Do Not Disturb; reading without it starts the session.
        if (composeRule.onAllNodes(hasTestTag("time_reading_focus_without")).fetchSemanticsNodes().isNotEmpty()) {
            composeRule.onNodeWithTag("time_reading_focus_without").performClick()
        }
        val activity = composeRule.activity
        composeRule.onNodeWithTag("time_reading_session").assertExists()

        setLanguage(AppLanguage.German)
        composeRule.onNodeWithTag("time_reading_session").assertExists()
        assertSame(activity, composeRule.activity)

        // Finish so the disposable package keeps no session running into the next test.
        composeRule.onNodeWithTag("time_reading_session_done").performClick()
    }

    @Test
    fun theSettingFollowsTheStoredChoiceAndFollowSystemClearsIt() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        composeRule.onNodeWithTag("home_settings").performClick()

        composeRule.onNodeWithTag("settings_language").performScrollTo().performClick()
        composeRule.onNodeWithTag("settings_language_german").performClick()
        composeRule.waitForIdle()
        assertEquals(AppLanguage.German, currentAppLanguage(context))
        composeRule.onNodeWithText("Sprache").assertExists()

        composeRule.onNodeWithTag("settings_language").performScrollTo().performClick()
        composeRule.onNodeWithTag("settings_language_system").performClick()
        composeRule.waitForIdle()
        assertEquals(AppLanguage.System, currentAppLanguage(context))
    }

    private fun setLanguage(language: AppLanguage) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync { applyAppLanguage(language) }
        composeRule.waitForIdle()
    }
}

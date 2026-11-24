package de.nicolasmahn.momentum.app

import de.nicolasmahn.momentum.domain.settings.hidingTasks
import android.view.View
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import de.nicolasmahn.momentum.core.time.currentHabitDate
import de.nicolasmahn.momentum.domain.compatibility.MoNeed
import de.nicolasmahn.momentum.domain.mo.only
import de.nicolasmahn.momentum.domain.mo.reflectionAbout
import org.robolectric.annotation.GraphicsMode
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import de.nicolasmahn.momentum.core.ui.theme.WorkoutTrackerTheme
import de.nicolasmahn.momentum.core.ui.viewColors
import de.nicolasmahn.momentum.domain.AppState
import de.nicolasmahn.momentum.domain.checklist.ViewHue
import de.nicolasmahn.momentum.domain.checklist.ViewIcon
import de.nicolasmahn.momentum.domain.measurement.withWeight
import de.nicolasmahn.momentum.domain.questions.testEatingQuestions
import de.nicolasmahn.momentum.domain.questions.withEating
import de.nicolasmahn.momentum.domain.settings.FeatureVisibilitySettings
import de.nicolasmahn.momentum.feature.checklist.momentumHue
import de.nicolasmahn.momentum.feature.checklist.vector
import de.nicolasmahn.momentum.feature.checklist.presentation
import de.nicolasmahn.momentum.feature.weight.transcript
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import de.nicolasmahn.momentum.domain.time.withReading
import de.nicolasmahn.momentum.domain.activity.withActivity

/**
 * Eating wears the name and the look its configuration holds, wherever it shows. The configuration
 * it is seeded with holds what it always wore, so nothing differs until a person changes it.
 */
@RunWith(RobolectricTestRunner::class)
class EatingLookTest {
    @get:Rule
    val compose = createComposeRule()

    private fun show(state: AppState, hidden: Set<MoNeed> = emptySet()) {
        compose.setContent {
            WorkoutTrackerTheme { MomentumApp(state = state, onStateChange = { true }, onActivitySave = ActivitySaveNotExpected, featureVisibility = FeatureVisibilitySettings(disabledFeatures = emptySet()).hidingTasks(*hidden.toTypedArray())) }
        }
    }

    private fun click(tag: String) {
        compose.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
    }

    private fun said(text: String): Boolean = "text=\"$text\"" in compose.transcript()

    private fun names(tag: String, name: String) =
        compose.onNode(hasTestTag(tag) and hasAnyDescendant(hasText(name)), useUnmergedTree = true).assertExists()

    @Test
    fun theSeededConfigurationWearsWhatEatingAlwaysWore() {
        val seeded = testEatingQuestions()
        assertEquals(Icons.Rounded.Restaurant, seeded.icon.vector)
        var actual: de.nicolasmahn.momentum.core.ui.ViewIdentity? = null
        compose.setContent {
            WorkoutTrackerTheme { actual = seeded.presentation() }
        }
        compose.waitForIdle()
        assertEquals(seeded.icon.vector, requireNotNull(actual).icon)
        assertEquals(seeded.hue.momentumHue.viewColors(), requireNotNull(actual).colors)
    }

    /** Home, the header of its screen and of its page, its row in Settings and its row in Statistics call it what it is called. */
    @Test
    fun aRenamedEatingIsCalledByItsNameWhereverItShows() {
        val food = testEatingQuestions().copy(label = "Food", icon = ViewIcon.Heart, hue = ViewHue.Plum)
        show(AppState().withWeight().withEating(food).withReading().withActivity())

        names("home_questions_eating", "Food")
        click("home_questions_eating")
        // The header says its name; the screen keeps asking Eating's own questions.
        assertTrue(compose.transcript().toString(), said("Food") && said("How was the day?"))
        click("top_back")

        click("home_statistics")
        compose.onNodeWithTag("statistics_consistency_questions_eating").assert(hasContentDescription("Food", substring = true))
        click("top_back")

        click("home_settings")
        names("settings_group_questions_eating", "Food")
        compose.onNodeWithTag("settings_feature_questions_eating").assert(hasContentDescription("Show Food on Home"))
        click("settings_group_questions_eating")
        assertTrue(compose.transcript().toString(), said("Food"))
    }

    /**
     * Its color is the configuration's on Home and on the token page: recolored, its tile is plum
     * where it was orange, and so are the squares of the days its bank has counted. Read from the
     * pixels each draws inside its own bounds, since no color is said to TalkBack.
     */
    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun aRecoloredEatingWearsItsColorOnHomeAndOnTheTokenPage() {
        val today = currentHabitDate()
        // Three days kept, so its bank shows three squares in its color.
        val kept = AppState(questionEntries = (1..3).map { reflectionAbout(today.minusDays(it.toLong())) }).withWeight()
        var state by mutableStateOf(kept.withEating())
        lateinit var screen: View
        compose.setContent {
            screen = LocalView.current
            WorkoutTrackerTheme {
                MomentumApp(state = state, rewardStart = today.minusDays(30), onStateChange = { true }, onActivitySave = ActivitySaveNotExpected, featureVisibility = only(MoNeed.Eating))
            }
        }
        fun colorsIn(tag: String): Set<Int> = compose.colorsDrawnIn(screen, tag)
        val orange = ViewHue.Orange.momentumHue.viewColors()
        val plum = ViewHue.Plum.momentumHue.viewColors()

        // Its accent is on its row whether the day is open (the icon) or kept (the whole face).
        assertTrue(orange.accent.toArgb() in colorsIn("home_questions_eating"))
        click("home_streak")
        assertTrue(orange.accent.toArgb() in colorsIn("streak_questions_eating"))
        click("top_back")

        state = kept.withEating(testEatingQuestions().copy(hue = ViewHue.Plum))
        val tile = colorsIn("home_questions_eating")
        assertTrue(plum.accent.toArgb() in tile && orange.accent.toArgb() !in tile)
        click("home_streak")
        val bank = colorsIn("streak_questions_eating")
        assertTrue(plum.accent.toArgb() in bank && orange.accent.toArgb() !in bank)
    }

    /** Hidden, its reminder's row says which view to turn on, by the name it has. */
    @Test
    fun itsRemindersRowNamesItToo() {
        val food = testEatingQuestions().copy(label = "Food")
        show(AppState().withWeight().withEating(food).withReading().withActivity(), hidden = setOf(MoNeed.Eating))

        click("home_settings")
        click("settings_group_questions_eating")
        click("questions_settings_tab_reminders")
        compose.onNodeWithTag("questions_settings_reminder_dependency").assert(hasText("Turn Food on to use this reminder"))
    }
}

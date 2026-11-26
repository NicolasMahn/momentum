package de.nicolasmahn.momentum.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.geometry.Offset
import de.nicolasmahn.momentum.core.time.currentDateKey
import de.nicolasmahn.momentum.core.ui.theme.WorkoutTrackerTheme
import de.nicolasmahn.momentum.domain.AppState
import de.nicolasmahn.momentum.domain.activity.ActivityEntry
import de.nicolasmahn.momentum.domain.activity.ListedActivity
import de.nicolasmahn.momentum.domain.activity.activityRules
import de.nicolasmahn.momentum.domain.activity.testActivityConfiguration
import de.nicolasmahn.momentum.domain.checklist.ViewIcon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Other on the Activity screen: what a tap on one of its activities chooses, and what the name
 * typed by hand chooses. Walked through the app on the real habit day, as the screen reads it.
 */
@RunWith(RobolectricTestRunner::class)
class ActivityOtherTest {
    @get:Rule
    val compose = createComposeRule()

    private val todayKey = currentDateKey()
    private var state by mutableStateOf(AppState())

    private fun show(initial: AppState) {
        state = initial
        compose.setContent { WorkoutTrackerTheme { MomentumApp(state = state, onStateChange = { state = it; true }, onActivitySave = ActivitySaveNotExpected) } }
    }

    private fun click(tag: String) {
        compose.onNodeWithTag(tag).performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
    }

    private val entry get() = state.activityEntries.single()

    /** A tap on something already on show, with the page left where it is, so positions can be compared across it. */
    private fun tap(tag: String) {
        compose.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
    }

    private fun at(tag: String): Offset = compose.onNodeWithTag(tag).fetchSemanticsNode().positionInRoot

    private fun assertOtherChoicesAbsent() {
        compose.onNodeWithTag("activity_other_choices").assertDoesNotExist()
        compose.onNodeWithTag("activity_other_jogging").assertDoesNotExist()
        compose.onNodeWithTag("activity_other_act_yoga").assertDoesNotExist()
    }

    private fun overflowChoicesOnlyAppearUnderOther() {
        show(AppState(activity = testActivityConfiguration().let { it.copy(activities = it.activities + ListedActivity("act_yoga", "Yoga", ViewIcon.Bolt)) }))
        tap("home_log_activity")
        compose.onNodeWithTag("activity_choice_gym").assertIsSelected()
        assertOtherChoicesAbsent()

        tap("activity_choice_bouldering")
        assertOtherChoicesAbsent()
        // A listed tile names the activity itself, so there is no name field to fill.
        compose.onNodeWithTag("activity_name").assertDoesNotExist()
        val below = listOf("activity_save", "activity_history")
        val withBouldering = below.map(::at)

        tap("activity_choice_cycling")
        assertOtherChoicesAbsent()
        compose.onNodeWithTag("activity_name").assertDoesNotExist()

        tap("activity_choice_custom")
        compose.onNodeWithTag("activity_other_choices").assertIsDisplayed()
        compose.onNodeWithTag("activity_other_jogging").assertIsDisplayed().assertIsNotSelected()
        compose.onNodeWithTag("activity_other_act_yoga").assertIsDisplayed()
        compose.onNodeWithTag("activity_name").assertIsEnabled()
        val withinOther = listOf("activity_other_choices", "activity_other_jogging", "activity_other_act_yoga", "activity_name") + below
        val withOther = withinOther.map(::at)

        tap("activity_other_jogging")
        compose.onNodeWithTag("activity_choice_custom").assertIsSelected()
        compose.onNodeWithTag("activity_other_choices").assertIsDisplayed()
        compose.onNodeWithTag("activity_other_jogging").assertIsDisplayed().assertIsSelected()
        compose.onNodeWithTag("activity_other_act_yoga").assertIsDisplayed()
        compose.onNodeWithTag("activity_name").assertIsEnabled()
        assertEquals(withOther, withinOther.map(::at))

        tap("activity_choice_bouldering")
        compose.onNodeWithTag("activity_choice_bouldering").assertIsSelected()
        assertOtherChoicesAbsent()
        compose.onNodeWithTag("activity_name").assertDoesNotExist()
        assertEquals(withBouldering, below.map(::at))
    }

    @Test
    @Config(qualifiers = "w411dp-h1100dp-xhdpi")
    fun overflowChoicesOnlyAppearUnderOtherAt411dp() = overflowChoicesOnlyAppearUnderOther()

    @Test
    @Config(qualifiers = "de-rDE-w320dp-h1100dp-xhdpi")
    fun overflowChoicesOnlyAppearUnderOtherAt320dpInGerman() = overflowChoicesOnlyAppearUnderOther()

    /**
     * An entry logged under an activity the configuration never knew shows its name, which a
     * listed activity happens to share. Saved untouched, it keeps its own activity: the name it
     * shows is no choice. Tapped under Other, the listed activity is the choice, by its ID, and the
     * entry moves to it. A name typed by hand is a choice by name again.
     */
    @Test
    fun anActivityTappedUnderOtherIsTheOneAnEditedEntryMovesTo() {
        val yoga = ListedActivity("act_yoga", "Yoga", ViewIcon.Bolt)
        val configuration = testActivityConfiguration().let { it.copy(activities = it.activities + yoga) }
        show(AppState(activity = configuration, activityEntries = listOf(ActivityEntry("entry-1", "act_gone", "Yoga", todayKey, 100L))))
        tap("home_log_activity")

        click("activity_entry_edit_activity:entry-1")
        click("activity_save")
        assertEquals("act_gone", entry.activityId)
        assertEquals("Yoga", entry.name)

        click("activity_entry_edit_activity:entry-1")
        click("activity_other_act_yoga")
        click("activity_save")
        assertEquals("act_yoga", entry.activityId)
        assertEquals("Yoga", entry.name)
        assertEquals(configuration, state.activityRules)

        click("activity_entry_edit_activity:entry-1")
        click("activity_other_act_yoga")
        compose.onNodeWithTag("activity_name").performScrollTo().performTextReplacement("Pilates")
        compose.waitForIdle()
        click("activity_save")
        val pilates = state.activityRules.activityNamed("Pilates")
        assertNotNull(pilates)
        assertEquals(pilates?.id, entry.activityId)
        assertEquals("Pilates", entry.name)
    }
}

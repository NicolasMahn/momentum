package de.nicolasmahn.momentum.app

import de.nicolasmahn.momentum.domain.settings.hidingTasks
import android.view.View
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import de.nicolasmahn.momentum.core.time.currentHabitDate
import de.nicolasmahn.momentum.core.ui.theme.MomentumHues
import de.nicolasmahn.momentum.core.ui.theme.WorkoutTrackerTheme
import de.nicolasmahn.momentum.core.ui.viewColors
import de.nicolasmahn.momentum.domain.AppState
import de.nicolasmahn.momentum.domain.checklist.ViewHue
import de.nicolasmahn.momentum.domain.checklist.ViewIcon
import de.nicolasmahn.momentum.domain.measurement.withWeight
import de.nicolasmahn.momentum.domain.compatibility.MoNeed
import de.nicolasmahn.momentum.domain.mo.only
import de.nicolasmahn.momentum.domain.questions.withEating
import de.nicolasmahn.momentum.domain.settings.FeatureVisibilitySettings
import de.nicolasmahn.momentum.domain.compatibility.ReadingTimeId
import de.nicolasmahn.momentum.domain.time.TimeLog
import de.nicolasmahn.momentum.domain.time.testReadingTime
import de.nicolasmahn.momentum.domain.time.withReading
import de.nicolasmahn.momentum.domain.activity.withActivity
import de.nicolasmahn.momentum.feature.checklist.momentumHue
import de.nicolasmahn.momentum.feature.checklist.vector
import de.nicolasmahn.momentum.feature.weight.transcript
import java.time.DayOfWeek
import java.time.temporal.TemporalAdjusters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/** A seeded timer uses its configuration for labels, icons and colors throughout the app. */
@RunWith(RobolectricTestRunner::class)
class TimerPresetLookTest {
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
    fun theSeededTimerKeepsItsConfiguredIdentity() {
        val seeded = testReadingTime()
        assertEquals(Icons.AutoMirrored.Rounded.MenuBook, seeded.icon.vector)
        assertEquals(MomentumHues.Yellow.viewColors(), seeded.hue.momentumHue.viewColors())
    }

    /** Home, the header of its screen and of its page, its row in Settings and its row in Statistics call it what it is called. */
    @Test
    fun aRenamedTimerIsCalledByItsNameWhereverItShows() {
        val books = testReadingTime().copy(label = "Books", icon = ViewIcon.Heart, hue = ViewHue.Plum)
        show(AppState().withWeight().withEating().withReading(books))

        names("home_time_reading", "Books")
        click("home_time_reading")
        // The header uses its current name; its controls use the shared timer wording.
        assertTrue(compose.transcript().toString(), said("Books") && said("Start the timer"))
        click("top_back")

        click("home_statistics")
        compose.onNodeWithTag("statistics_consistency_time_reading").assert(hasContentDescription("Books", substring = true))
        click("top_back")

        click("home_settings")
        names("settings_group_time_reading", "Books")
        compose.onNodeWithTag("settings_feature_time_reading").assert(hasContentDescription("Show Books on Home"))
        click("settings_group_time_reading")
        assertTrue(compose.transcript().toString(), said("Books"))
    }

    /**
     * Its color is the configuration's on Home and on the token page: recolored, its tile is plum
     * where it was yellow, and so is the token its bank holds. Read from the pixels each draws
     * inside its own bounds, since no color is said to TalkBack.
     */
    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun aRecoloredTimerWearsItsColorOnHomeAndOnTheTokenPage() {
        val today = currentHabitDate()
        // An hour read in the week before this one, so its bank holds the token that week earned.
        val lastMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks(1)
        val read = AppState(timeLogs = listOf(TimeLog(ReadingTimeId, lastMonday.toString(), timestampMillis = 0, minutes = 60))).withWeight().withEating()
        var state by mutableStateOf(read.withReading().withActivity())
        lateinit var screen: View
        compose.setContent {
            screen = LocalView.current
            WorkoutTrackerTheme {
                MomentumApp(state = state, rewardStart = today.minusDays(30), onStateChange = { true }, onActivitySave = ActivitySaveNotExpected, featureVisibility = only(MoNeed.Reading))
            }
        }
        fun colorsIn(tag: String): Set<Int> = compose.colorsDrawnIn(screen, tag)
        val yellow = ViewHue.Yellow.momentumHue.viewColors()
        val plum = ViewHue.Plum.momentumHue.viewColors()

        assertTrue(yellow.container.toArgb() in colorsIn("home_time_reading"))
        click("home_streak")
        assertTrue(yellow.accent.toArgb() in colorsIn("streak_time_reading"))
        click("top_back")

        state = read.withReading(testReadingTime().copy(hue = ViewHue.Plum))
        val tile = colorsIn("home_time_reading")
        assertTrue(plum.container.toArgb() in tile && yellow.container.toArgb() !in tile)
        click("home_streak")
        val bank = colorsIn("streak_time_reading")
        assertTrue(plum.accent.toArgb() in bank && yellow.accent.toArgb() !in bank)
    }

    /** Hidden, its reminder's row says which view to turn on, by the name it has. */
    @Test
    fun itsRemindersRowNamesItToo() {
        val books = testReadingTime().copy(label = "Books")
        show(AppState().withWeight().withEating().withReading(books), hidden = setOf(MoNeed.Reading))

        click("home_settings")
        click("settings_group_time_reading")
        click("time_settings_tab_reminders")
        compose.onNodeWithTag("time_settings_reminder_dependency").assert(hasText("Turn Books on to use this reminder"))
    }
}

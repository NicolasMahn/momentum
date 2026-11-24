package de.nicolasmahn.momentum.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import de.nicolasmahn.momentum.core.time.currentHabitDate
import de.nicolasmahn.momentum.core.ui.theme.WorkoutTrackerTheme
import de.nicolasmahn.momentum.domain.ActivityTrackerRef
import de.nicolasmahn.momentum.domain.AppState
import de.nicolasmahn.momentum.domain.SubstancesTrackerRef
import de.nicolasmahn.momentum.domain.activity.ActivityEdit
import de.nicolasmahn.momentum.domain.activity.WeekPlan
import de.nicolasmahn.momentum.domain.activity.applyingActivityEdits
import de.nicolasmahn.momentum.domain.substance.joiningStreak
import de.nicolasmahn.momentum.persistence.CurrentTrackerStore
import de.nicolasmahn.momentum.persistence.PassChoices
import de.nicolasmahn.momentum.persistence.PreferencesPassChoiceRepository
import java.time.DayOfWeek
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The switches as the app wires them, on everyday views ([EverydayViews]) kept until the day
 * before yesterday: each one is offered where its rule says, stores the choice it stands for, and
 * never writes a record. The rules themselves are tested in [SaidAheadTest].
 */
@RunWith(RobolectricTestRunner::class)
class SaidAheadInTheAppTest {
    @get:Rule val compose = createComposeRule()

    // The app reads the clock, so the views' history is kept up to the day before yesterday.
    private val today = currentHabitDate()
    private val repository = PreferencesPassChoiceRepository(CurrentTrackerStore())
    private val pressable = SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick)
    private val notPressable = SemanticsMatcher.keyNotDefined(SemanticsActions.OnClick)
    private val selected = SemanticsMatcher.expectValue(SemanticsProperties.Selected, true)
    private val notSelected = SemanticsMatcher.expectValue(SemanticsProperties.Selected, false)
    private fun spoken(words: String) = SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf(words))

    private fun show(state: AppState) {
        var choices by mutableStateOf<PassChoices?>(null)
        compose.setContent {
            WorkoutTrackerTheme {
                MomentumApp(
                    state = state,
                    rewardStart = today.minusDays(30),
                    onStateChange = { error("A choice cannot change recorded facts") },
                    onActivitySave = ActivitySaveNotExpected,
                    passChoices = choices,
                    onPassChoicesChange = { updated -> repository.save(updated).also { if (it) choices = updated } },
                )
            }
        }
    }

    private fun press(tag: String) {
        compose.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
    }

    private fun stored(): PassChoices = compose.runOnIdle { requireNotNull(repository.load()) }

    @Test fun careShowsItsOneTokenOnBothCheckpointsAndOneOfThemTakesIt() {
        show(EverydayViews.state(today = today))
        press("home_checklist_care")
        // Care holds one token and owes two checkpoints: the header shows none, each checkpoint shows the one and can take it.
        compose.onNodeWithTag("top_tokens").assertDoesNotExist()
        compose.onNodeWithTag("checklist_care_morning_token").assert(spoken("1 Care token. Use a token for Morning")).assert(notSelected).assertIsEnabled()
        compose.onNodeWithTag("checklist_care_evening_token").assert(spoken("1 Care token. Use a token for Evening")).assert(notSelected).assertIsEnabled()

        press("checklist_care_morning_token")
        assertEquals(PassChoices(today, checkpoints = mapOf(EverydayViews.care to setOf("morning"))), stored())
        // The token is taken: both count none left, the morning holds it and the evening has none to take.
        compose.onNodeWithTag("checklist_care_morning_token").assert(spoken("0 Care tokens. Use a token for Morning")).assert(selected).assertIsEnabled()
        compose.onNodeWithTag("checklist_care_evening_token").assert(spoken("0 Care tokens")).assert(notSelected).assertIsNotEnabled()
        compose.onNodeWithTag("top_tokens").assertDoesNotExist()

        press("checklist_care_morning_token")
        assertEquals(PassChoices(today), stored())
        compose.onNodeWithTag("checklist_care_evening_token").assert(spoken("1 Care token. Use a token for Evening")).assertIsEnabled()
    }

    @Test fun aChecklistWithOneCheckpointKeepsItsTokensInTheHeader() {
        show(EverydayViews.state(today = today))
        press("home_checklist_supplements")
        compose.onNodeWithTag("top_tokens").assertExists()
        compose.onNodeWithTag("checklist_supplements_midday_token").assertDoesNotExist()
    }

    @Test fun aWeekOfReadingIsQuietedBeforeItsSundayAndCoveredOnIt() {
        // Fifty minutes today leave less than a quarter of the goal missing, which one token covers.
        show(EverydayViews.state("timeLogs" to """{"time":"reading","dateKey":"$today","timestampMillis":2,"minutes":50}""", today = today))
        press("home_time_reading")
        // On its Sunday the token is the switch; before it, Mo's "Zz" is, and the tokens are not.
        val switch = if (today.dayOfWeek == DayOfWeek.SUNDAY) "top_tokens" else "top_quiet"
        compose.onNodeWithTag(switch).assert(pressable)
        if (switch == "top_quiet") compose.onNodeWithTag("top_tokens").assert(!pressable)
        press(switch)
        assertEquals(PassChoices(today, setOf(EverydayViews.reading)), stored())
        compose.onNodeWithTag(switch).assert(selected)
        press(switch)
        assertEquals(PassChoices(today), stored())
    }

    @Test fun substancesWithoutMoIsSetAsideFromItsHeader() {
        // Joined to the streak today with Mo off, and yesterday's record, which today judges, is missing.
        val state = EverydayViews.state(today = today).let { it.copy(substances = it.substances.joiningStreak(today)) }
        show(state)
        press("home_substances")
        compose.onNodeWithTag("top_tokens").assert(pressable)
        press("top_tokens")
        assertEquals(PassChoices(today, setOf(SubstancesTrackerRef)), stored())
        compose.onNodeWithTag("top_tokens").assert(selected)
    }

    @Test fun activityRestsFromItsHeaderOnAPlannedDayARestDayCovers() {
        // A Sunday cannot be both: the week's one rest day went earlier, or the week is done.
        assumeTrue(today.dayOfWeek != DayOfWeek.SUNDAY)
        // Six days a week with today among them, and active until yesterday: the week is not done, and its rest day is unused.
        val planned = DayOfWeek.entries.filter { it != today.plusDays(1).dayOfWeek }.toTypedArray()
        val state = EverydayViews.state(
            "activityEntries" to """{"id":"yesterday","activity":"gym","name":"Gym","dateKey":"${today.minusDays(1)}","timestampMillis":1}""",
            today = today,
        ).applyingActivityEdits(listOf(ActivityEdit.SetWeek(WeekPlan.of(*planned))), today.minusDays(30))
        show(state)
        press("home_log_activity")
        compose.onNodeWithTag("top_rest_days").assert(pressable)
        compose.onNodeWithTag("top_tokens").assert(notPressable)
        press("top_rest_days")
        assertEquals(PassChoices(today, setOf(ActivityTrackerRef)), stored())
        compose.onNodeWithTag("top_rest_days").assert(selected)
        // No token is held for a rest day.
        compose.onNodeWithTag("top_tokens").assert(notPressable)
        press("top_rest_days")
        assertEquals(PassChoices(today), stored())
    }
}

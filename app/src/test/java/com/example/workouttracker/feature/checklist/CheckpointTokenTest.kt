package com.example.workouttracker.feature.checklist

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import com.example.workouttracker.core.ui.theme.WorkoutTrackerTheme
import com.example.workouttracker.domain.AppState
import com.example.workouttracker.domain.checklist.ChecklistConfiguration
import com.example.workouttracker.domain.checklist.ChecklistItem
import com.example.workouttracker.domain.checklist.Checkpoint
import com.example.workouttracker.domain.checklist.CheckpointTime
import com.example.workouttracker.domain.checklist.ViewHue
import com.example.workouttracker.domain.checklist.ViewIcon
import com.example.workouttracker.domain.holiday.HolidaySchedule
import com.example.workouttracker.feature.tracker.CheckpointTokens
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A checklist with several checkpoints shows its token and the number left beside each of them,
 * on the day the screen opens on: always there, a switch where a token can be set aside, pressed
 * in where one is, and not pressable where none can be. The rules that decide which are in
 * `SaidAheadTest`; here the screen is handed what they would say.
 */
@RunWith(RobolectricTestRunner::class)
class CheckpointTokenTest {
    @get:Rule
    val compose = createComposeRule()

    private val care = ChecklistConfiguration(
        id = "chk_care",
        label = "Care",
        icon = ViewIcon.Journal,
        hue = ViewHue.Plum,
        checkpoints = listOf(
            Checkpoint("cpt_morning", CheckpointTime.Fixed(LocalTime.of(8, 0)), remind = true, label = "Morning"),
            Checkpoint("cpt_evening", CheckpointTime.Fixed(LocalTime.of(21, 0)), remind = true, label = "Evening"),
        ),
        items = listOf(ChecklistItem("itm_teeth", "Brush teeth")),
    )
    private val tag = "checklist_chk_care"
    private val morning = "${tag}_cpt_morning_token"
    private val evening = "${tag}_cpt_evening_token"
    private var setAside by mutableStateOf(emptySet<String>())

    /** The screen over a bank of [bank] tokens, each open checkpoint offered one while one is left, as the rules offer them. */
    private fun show(bank: Int?) {
        compose.setContent {
            WorkoutTrackerTheme {
                ChecklistScreen(
                    state = AppState(checklists = listOf(care)),
                    checklist = care,
                    onStateChange = { true },
                    todayKey = "2026-10-13",
                    daysOff = HolidaySchedule(),
                    checkpointTokens = bank?.let {
                        val left = bank - setAside.size
                        CheckpointTokens(
                            left = left,
                            offered = if (left > 0) care.checkpoints.mapTo(LinkedHashSet()) { it.id } else setAside,
                            setAside = setAside,
                        ) { id, chosen -> setAside = if (chosen) setAside + id else setAside - id }
                    },
                )
            }
        }
        compose.waitForIdle()
    }

    private fun click(tag: String) {
        compose.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
    }

    /**
     * What the pressed node says of itself with what it holds merged in, as TalkBack hears it:
     * its words, its state, whether it is pressed in and whether it can be pressed. Null where there is no token.
     */
    private fun token(tag: String): String? = compose.onAllNodesWithTag(tag).fetchSemanticsNodes().singleOrNull()?.config?.let { said ->
        listOfNotNull(
            said.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" | "),
            said.getOrNull(SemanticsProperties.StateDescription),
            said.getOrNull(SemanticsProperties.Selected)?.let { if (it) "selected" else "not selected" },
            said.getOrNull(SemanticsProperties.Disabled)?.let { "disabled" },
        ).joinToString(" / ")
    }

    @Test
    fun withNoTokensBothCheckpointsShowAnEmptyTokenThatCannotBePressed() {
        show(bank = 0)
        assertEquals("0 Care tokens / not selected / disabled", token(morning))
        assertEquals("0 Care tokens / not selected / disabled", token(evening))
    }

    @Test
    fun aTokenIsOfferedOnBothAndTheCountsMoveTogetherWhenOneTakesIt() {
        show(bank = 1)
        assertEquals("1 Care token. Use a token for Morning / Can be reserved for today / not selected", token(morning))
        assertEquals("1 Care token. Use a token for Evening / Can be reserved for today / not selected", token(evening))
        val eveningTop = compose.onNodeWithTag(evening).getUnclippedBoundsInRoot().top

        click(morning)
        assertEquals(setOf("cpt_morning"), setAside)
        assertEquals("0 Care tokens. Use a token for Morning / Reserved for today / selected", token(morning))
        assertEquals("0 Care tokens / not selected / disabled", token(evening))
        // The evening's token lost its switch and kept its place: nothing moved under the thumb.
        assertEquals(eveningTop, compose.onNodeWithTag(evening).getUnclippedBoundsInRoot().top)

        click(morning)
        assertEquals(emptySet<String>(), setAside)
        assertEquals("1 Care token. Use a token for Evening / Can be reserved for today / not selected", token(evening))
    }

    @Test
    @Config(qualifiers = "de")
    fun theTokenSpeaksGerman() {
        show(bank = 2)
        assertEquals("2 Tokens für Care. Token für Morning einsetzen / Kann für heute reserviert werden / not selected", token(morning))
    }

    /** A token is said ahead for today alone, so another day shows none; a checklist outside the streak shows none on any day. */
    @Test
    fun anotherDayAndAChecklistWithoutABankShowNoToken() {
        show(bank = 1)
        click("${tag}_history_2026-10-12")
        assertEquals(null, token(morning))
        assertEquals(null, token(evening))
        click("${tag}_history_2026-10-13")
        assertEquals("1 Care token. Use a token for Evening / Can be reserved for today / not selected", token(evening))
    }

    @Test
    fun aChecklistOutsideTheStreakShowsNoToken() {
        show(bank = null)
        assertEquals(null, token(morning))
        assertEquals(null, token(evening))
    }
}

package de.nicolasmahn.momentum.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import de.nicolasmahn.momentum.core.ui.theme.WorkoutTrackerTheme
import de.nicolasmahn.momentum.domain.checklist.Dated
import de.nicolasmahn.momentum.domain.checklist.MoMotif
import de.nicolasmahn.momentum.domain.checklist.RewardPeriod
import de.nicolasmahn.momentum.domain.checklist.WhenAway
import de.nicolasmahn.momentum.domain.holiday.HolidaySchedule
import de.nicolasmahn.momentum.domain.reminder.Bedtime
import de.nicolasmahn.momentum.domain.time.TimeConfiguration
import de.nicolasmahn.momentum.domain.time.DefaultTokenSteps
import de.nicolasmahn.momentum.domain.time.TimeEdit
import de.nicolasmahn.momentum.domain.time.StepTokens
import de.nicolasmahn.momentum.domain.time.TimeRewards
import de.nicolasmahn.momentum.domain.time.TokenStep
import de.nicolasmahn.momentum.domain.time.TokenSteps
import de.nicolasmahn.momentum.domain.time.applyingEdits
import de.nicolasmahn.momentum.domain.time.testMadeTime
import de.nicolasmahn.momentum.domain.time.testReadingTime
import de.nicolasmahn.momentum.feature.weight.transcript
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The two parts a time view's page has of its own take a configuration and its draft, a made
 * view's and Reading's alike, so Reading's page can host them as they are. Goal is the same on
 * both. Streak has the switch for a view a person made and none for Reading, which always counts,
 * and on neither a number of kept days.
 */
@RunWith(RobolectricTestRunner::class)
class TimePartsTest {
    @get:Rule
    val compose = createComposeRule()

    private val today = LocalDate.parse("2026-10-14")
    private var shown by mutableStateOf<(@Composable () -> Unit)?>(null)

    private fun show(draft: TimeSettingsDraft, part: @Composable (TimeConfiguration) -> Unit) {
        shown = { part(draft.edited) }
        compose.waitForIdle()
    }

    private fun click(tag: String) {
        compose.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
    }

    private fun line(tag: String): String = compose.transcript().single { it.startsWith("[$tag]") }

    /** The part in a column that scrolls, as its page holds it, so a part longer than the window is laid out whole. */
    private fun showScrolling() = compose.setContent { WorkoutTrackerTheme { Column(Modifier.verticalScroll(rememberScrollState())) { shown?.invoke() } } }

    /** How far down the part a node is laid out, and where it ends. */
    private fun top(tag: String): Float = compose.onNodeWithTag(tag).fetchSemanticsNode().positionInRoot.y

    private fun bottom(tag: String): Float = compose.onNodeWithTag(tag).fetchSemanticsNode().let { it.positionInRoot.y + it.size.height }

    private fun bounds(tag: String): Rect = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot

    /** How a text node was laid out: its lines, its width and whether anything was cut. */
    private fun layoutOf(node: SemanticsNodeInteraction): TextLayoutResult {
        val layouts = mutableListOf<TextLayoutResult>()
        node.fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action!!.invoke(layouts)
        return layouts.single()
    }

    /** How many lines a text node is laid out on. */
    private fun lineCount(tag: String): Int = layoutOf(compose.onNodeWithTag(tag)).lineCount

    /**
     * A one-line text was read whole: no second line discarded, nothing cut to an ellipsis, and
     * its last glyph inside the node's width. (A text narrower than the width it was laid out in
     * still reports a width overflow, so that flag says nothing here.)
     */
    private fun assertReadWhole(layout: TextLayoutResult) {
        assertEquals(1, layout.lineCount)
        assertFalse(layout.didOverflowHeight)
        assertFalse(layout.isLineEllipsized(0))
        assertTrue("${layout.layoutInput.text} ends at ${layout.getLineRight(0)} px in ${layout.size.width} px", layout.getLineRight(0) <= layout.size.width)
    }

    /** The label line of the field [tag] while it shows [text], a label or an error. */
    private fun labelOf(tag: String, text: String): SemanticsNodeInteraction =
        compose.onNode(hasText(text) and hasAnyAncestor(hasTestTag(tag)) and !SemanticsMatcher.keyIsDefined(SemanticsProperties.EditableText), useUnmergedTree = true)

    private var smallType: TextUnit = TextUnit.Unspecified

    /** What a percent field leaves its typed number: its width less its padding, its sign and the sign's own padding. */
    private var roomForTheNumber: (field: Rect) -> Float = { it.width }

    private var pageView by mutableStateOf<TimeConfiguration?>(null)

    /** A made view's real page, opened on Streak, with the padding and the scroll a person gets; another view, under its own ID, replaces the first. */
    private fun showPage(view: TimeConfiguration) {
        val first = pageView == null
        pageView = view
        if (!first) {
            compose.waitForIdle()
            return
        }
        compose.setContent {
            WorkoutTrackerTheme {
                smallType = MaterialTheme.typography.bodySmall.fontSize
                val measurer = rememberTextMeasurer()
                val sign = measurer.measure(" %", MaterialTheme.typography.bodyLarge).size.width
                val padding = with(LocalDensity.current) { (16.dp * 2 + 2.dp).toPx() }
                roomForTheNumber = { field -> field.width - padding - sign }
                val shown = pageView ?: return@WorkoutTrackerTheme
                MadeTimeSettingsPage(
                    view = shown,
                    today = today,
                    loggedDays = 0,
                    bedtime = Bedtime(LocalTime.of(7, 0), fixedBedtime = null),
                    daysOff = HolidaySchedule(),
                    onSave = { ViewWrite.Stored },
                    reminderRow = { _, _, _ -> },
                    onSendTest = {},
                    onDelete = { true },
                    onClose = {},
                    leaveRequested = false,
                    onLeaveAnswered = {},
                    onUnsavedWorkChange = {},
                    opensOn = ChecklistPage.Streak,
                )
            }
        }
    }

    /** A made view in the streak since a week ago, asking [goal] minutes and earning by [steps]. */
    private fun joinedPiano(steps: TokenSteps, goal: Int = 60, id: String = "tim_k2p9"): TimeConfiguration =
        testMadeTime(id = id, goal = goal).applyingEdits(listOf(TimeEdit.JoinRewards), today.minusWeeks(1)).applyingEdits(listOf(TimeEdit.SetTokenSteps(steps)), today)

    private fun type(tag: String, text: String) {
        compose.onNodeWithTag(tag).performTextReplacement(text)
        compose.waitForIdle()
    }

    @Test
    fun readingsGoalAndStreakAreTheMadeViewsParts() {
        compose.setContent { WorkoutTrackerTheme { shown?.invoke() } }
        val reading = TimeSettingsDraft(testReadingTime(), today)

        show(reading) { view -> TimeGoalPart(view, reading, today, testTag = "time_settings") }
        assertEquals("[time_settings_goal] field=\"60\"", line("time_settings_goal"))
        assertEquals("[time_settings_goal_note] text=\"60 minutes in a week earn a token. More minutes earn up to 2 tokens a week.\"", line("time_settings_goal_note"))
        compose.onNodeWithTag("time_settings_goal").performTextReplacement("1")
        compose.waitForIdle()
        assertEquals("[time_settings_goal_note] text=\"1 minute in a week earns a token. More minutes earn up to 2 tokens a week.\"", line("time_settings_goal_note"))
        compose.onNodeWithTag("time_settings_goal").performTextReplacement("45")
        compose.waitForIdle()
        // Reading's weeks were always judged, so its goal changes from today on.
        assertEquals(Dated(listOf(Dated.Revision(60), Dated.Revision(45, today))), reading.edited.goal)

        show(reading) { view -> TimeStreak(view, reading, today, testTag = "time_settings") }
        // It counts toward the streak as it always has, and can leave it as a made view can.
        compose.onNodeWithTag("time_settings_rewards").assertIsOn()
        compose.onNodeWithTag("time_settings_every_kept_days_value").assertDoesNotExist()
        compose.onNodeWithTag("time_settings_max_passes_value").assertTextEquals("3")
        compose.onNodeWithTag("time_settings_away_due").assertIsSelected()
        click("time_settings_max_passes_more")
        click("time_settings_away_excused")
        compose.onNodeWithTag("time_settings_max_passes_value").assertTextEquals("4")
        compose.onNodeWithTag("time_settings_away_excused").assertIsSelected()
        assertEquals(TimeRewards(maxPasses = Dated(listOf(Dated.Revision(3), Dated.Revision(4, today)))), reading.edited.rewards)
        assertEquals(WhenAway.Excused, reading.edited.whenAway.on(today))
        assertEquals(WhenAway.Due, reading.edited.whenAway.on(today.minusDays(1)))

        click("time_settings_rewards")
        compose.onNodeWithTag("time_settings_rewards").assertIsOff()
        compose.onNodeWithTag("time_settings_max_passes_value").assertDoesNotExist()
        assertFalse(requireNotNull(reading.edited.rewards).takesPartOn(today))
    }

    /**
     * Look ends with "Mo's flavor", the control a checklist's page has, under the color: Plain and
     * the one motif Mo has something for while a timer runs. A made view stands on Plain and
     * Reading on Reading; either takes the other's, and nothing else of the view changes.
     */
    @Test
    fun lookEndsWithMosFlavorOfferingPlainAndWhatMoHasForATimer() {
        showScrolling()
        val piano = TimeSettingsDraft(testMadeTime(), today)

        show(piano) { view -> TimePart(part = ChecklistPage.Look, view = view, draft = piano, today = today, testTag = "time_settings", nameField = {}) {} }
        compose.onNodeWithText("Mo's flavor").assertExists()
        compose.onNodeWithTag("time_settings_motif_none").assertTextEquals("Plain").assertIsSelected()
        compose.onNodeWithTag("time_settings_motif_reading").assertTextEquals("Reading").assertIsNotSelected()
        (MoMotif.entries - MoMotif.Book).forEach { motif -> compose.onNodeWithTag("time_settings_motif_${motif.storageId}").assertDoesNotExist() }
        assertTrue(top("time_settings_hue_navy") < top("time_settings_motif_none"))
        assertTrue(top("time_settings_motif_none") < top("time_settings_motif_reading"))

        click("time_settings_motif_reading")
        compose.onNodeWithTag("time_settings_motif_reading").assertIsSelected()
        assertEquals(testMadeTime().copy(mo = testMadeTime().mo.copy(motif = MoMotif.Book)), piano.edited)
        assertTrue(piano.canSave)
        // Back on what is stored, nothing is left to save.
        click("time_settings_motif_none")
        assertEquals(testMadeTime(), piano.edited)
        assertFalse(piano.canSave)

        val reading = TimeSettingsDraft(testReadingTime(), today)
        show(reading) { view -> TimePart(part = ChecklistPage.Look, view = view, draft = reading, today = today, testTag = "time_settings", nameField = {}) {} }
        compose.onNodeWithTag("time_settings_motif_reading").assertIsSelected()
        compose.onNodeWithTag("time_settings_motif_none").assertIsNotSelected()
        click("time_settings_motif_none")
        compose.onNodeWithTag("time_settings_motif_none").assertIsSelected()
        assertEquals(testReadingTime().copy(mo = testReadingTime().mo.copy(motif = null)), reading.edited)
    }

    private fun steps(vararg steps: Pair<Int, Int>) = TokenSteps(steps.map { (percent, tokens) -> TokenStep(percent, tokens) })

    private fun field(tag: String): String = compose.onNodeWithTag(tag).fetchSemanticsNode().config[SemanticsProperties.EditableText].text

    /**
     * The steps a week earns by stand on Streak between the bank's size and the holiday rule, one
     * row each, read the same way: the goal, or a percent typed in a field that says it is of the
     * goal and holds its sign; the minutes beside it; what reaching it adds, "+1", with its
     * stepper named for TalkBack; and the remove. Under the rows one line adds them up, following
     * every change, then "Add a step", disabled while no step can be added. A typed percent the
     * step may not have stays in its field and says why, and the Goal part's line reads what the
     * goal earns and the most a week can earn, every step added up. On a phone's width the
     * minutes stand beside the step.
     */
    @Test
    @Config(qualifiers = "w411dp-h1000dp-xhdpi")
    fun theStepsAreListedAfterTheBankOnStreakAndAddUpUnderTheRows() {
        showScrolling()
        val reading = TimeSettingsDraft(testReadingTime(), today)

        show(reading) { view -> TimeStreak(view, reading, today, testTag = "feature_settings") }
        compose.onNodeWithText("Tokens a week").assertExists()
        compose.onNodeWithText("At the goal").assertExists()
        assertEquals("[feature_settings_steps_note] text=\"A week earns the tokens of every step it reaches.\"", line("feature_settings_steps_note"))
        assertEquals("[feature_settings_steps_total] text=\"A week of 120 minutes or more earns 2 tokens.\"", line("feature_settings_steps_total"))
        assertTrue(top("feature_settings_max_passes_value") < top("feature_settings_steps_note"))
        assertTrue(top("feature_settings_steps_note") < top("feature_settings_step_1_minutes"))
        assertTrue(bottom("feature_settings_step_1") <= top("feature_settings_step_2"))
        assertTrue(bottom("feature_settings_step_2") <= top("feature_settings_steps_total"))
        assertTrue(bottom("feature_settings_steps_total") <= top("feature_settings_add_step"))
        assertTrue(bottom("feature_settings_add_step") <= top("feature_settings_away_due"))
        // The field says what its number is a percent of and carries the sign; the steppers are named for TalkBack.
        compose.onNodeWithTag("feature_settings_step_2_percent").assert(hasText("Of the goal")).assert(hasText("%", substring = true))
        compose.onNodeWithTag("feature_settings_step_1_tokens_less").assertContentDescriptionEquals("Tokens at the goal: less")
        compose.onNodeWithTag("feature_settings_step_2_tokens_more").assertContentDescriptionEquals("Tokens at step 2: more")
        // One row each: a step's minutes, its tokens and its remove all start within its field's height.
        listOf("minutes", "tokens_value", "remove").forEach { column ->
            assertTrue(column, top("feature_settings_step_2_$column") >= top("feature_settings_step_2_percent"))
            assertTrue(column, top("feature_settings_step_2_$column") < bottom("feature_settings_step_2_percent"))
        }
        compose.onNodeWithTag("feature_settings_step_1_minutes").assertTextEquals("60 min")
        compose.onNodeWithTag("feature_settings_step_1_percent").assertDoesNotExist()
        compose.onNodeWithTag("feature_settings_step_1_remove").assertDoesNotExist()
        compose.onNodeWithTag("feature_settings_step_1_tokens_value").assertTextEquals("+1")
        assertEquals("200", field("feature_settings_step_2_percent"))
        compose.onNodeWithTag("feature_settings_step_2_minutes").assertTextEquals("120 min")
        compose.onNodeWithTag("feature_settings_step_2_tokens_value").assertTextEquals("+1")
        compose.onNodeWithTag("feature_settings_step_3_minutes").assertDoesNotExist()
        // Each step's tokens are its own, one to five: at one, only more is offered.
        compose.onNodeWithTag("feature_settings_step_1_tokens_less").assertIsNotEnabled()
        compose.onNodeWithTag("feature_settings_step_1_tokens_more").assertIsEnabled()
        compose.onNodeWithTag("feature_settings_step_2_tokens_less").assertIsNotEnabled()
        click("feature_settings_step_2_tokens_more")
        compose.onNodeWithTag("feature_settings_step_2_tokens_value").assertTextEquals("+2")
        compose.onNodeWithTag("feature_settings_step_2_tokens_less").assertIsEnabled()
        assertEquals("[feature_settings_steps_total] text=\"A week of 120 minutes or more earns 3 tokens.\"", line("feature_settings_steps_total"))

        // A third step starts a hundred points above the last, adding one token; typed below its neighbor it is refused and says why, and holds Save and the button back.
        click("feature_settings_add_step")
        assertEquals("300", field("feature_settings_step_3_percent"))
        compose.onNodeWithTag("feature_settings_step_3_minutes").assertTextEquals("180 min")
        compose.onNodeWithTag("feature_settings_step_3_tokens_value").assertTextEquals("+1")
        assertEquals("[feature_settings_steps_total] text=\"A week of 180 minutes or more earns 4 tokens.\"", line("feature_settings_steps_total"))
        compose.onNodeWithTag("feature_settings_step_3_percent").performTextReplacement("150")
        compose.waitForIdle()
        assertEquals("[feature_settings_step_3_percent] field=\"150\" error=\"201–1000\"", line("feature_settings_step_3_percent"))
        assertTrue(reading.heldBack)
        assertFalse(reading.canSave)
        compose.onNodeWithTag("feature_settings_add_step").assertIsNotEnabled()
        // The line under the rows keeps adding up the last steps taken.
        assertEquals("[feature_settings_steps_total] text=\"A week of 180 minutes or more earns 4 tokens.\"", line("feature_settings_steps_total"))
        compose.onNodeWithTag("feature_settings_step_3_percent").performTextReplacement("250")
        compose.waitForIdle()
        assertEquals("[feature_settings_step_3_percent] field=\"250\"", line("feature_settings_step_3_percent"))
        compose.onNodeWithTag("feature_settings_step_3_minutes").assertTextEquals("150 min")
        assertEquals("[feature_settings_steps_total] text=\"A week of 150 minutes or more earns 4 tokens.\"", line("feature_settings_steps_total"))
        compose.onNodeWithTag("feature_settings_add_step").assertIsEnabled()
        assertTrue(reading.canSave)
        // Reading's weeks were always judged, so its steps change from today on.
        assertEquals(
            TimeRewards(maxPasses = Dated.always(3), tokenSteps = Dated(listOf(Dated.Revision(DefaultTokenSteps), Dated.Revision(steps(100 to 1, 200 to 2, 250 to 1), today)))),
            reading.edited.rewards,
        )
        show(reading) { view -> TimeGoalPart(view, reading, today, testTag = "feature_settings") }
        assertEquals("[feature_settings_goal_note] text=\"60 minutes in a week earn a token. More minutes earn up to 4 tokens a week.\"", line("feature_settings_goal_note"))
        // The rows' minutes follow the goal as the draft has it.
        compose.onNodeWithTag("feature_settings_goal").performTextReplacement("90")
        compose.waitForIdle()
        show(reading) { view -> TimeStreak(view, reading, today, testTag = "feature_settings") }
        compose.onNodeWithTag("feature_settings_step_1_minutes").assertTextEquals("90 min")
        compose.onNodeWithTag("feature_settings_step_3_minutes").assertTextEquals("225 min")
        assertEquals("[feature_settings_steps_total] text=\"A week of 225 minutes or more earns 4 tokens.\"", line("feature_settings_steps_total"))

        // The second step is removed with two taps; the third moves up with its text. The goal alone earns its tokens, in a line of its own.
        click("feature_settings_step_2_remove")
        click("feature_settings_step_2_remove")
        assertEquals("250", field("feature_settings_step_2_percent"))
        compose.onNodeWithTag("feature_settings_step_3_percent").assertDoesNotExist()
        click("feature_settings_step_2_remove")
        click("feature_settings_step_2_remove")
        compose.onNodeWithTag("feature_settings_step_2_percent").assertDoesNotExist()
        repeat(2) { click("feature_settings_step_1_tokens_more") }
        compose.onNodeWithTag("feature_settings_step_1_tokens_value").assertTextEquals("+3")
        assertEquals(steps(100 to 3), reading.steps)
        assertEquals("[feature_settings_steps_total] text=\"A week of 90 minutes or more earns 3 tokens.\"", line("feature_settings_steps_total"))
        show(reading) { view -> TimeGoalPart(view, reading, today, testTag = "feature_settings") }
        assertEquals("[feature_settings_goal_note] text=\"90 minutes in a week earn 3 tokens.\"", line("feature_settings_goal_note"))

        // A view a person made has the rows while it counts, and its Goal line says the two every bank starts with until it has a bank of its own.
        val piano = TimeSettingsDraft(testMadeTime(), today)
        show(piano) { view -> TimeGoalPart(view, piano, today, testTag = "time_settings") }
        assertEquals("[time_settings_goal_note] text=\"60 minutes in a week earn a token. More minutes earn up to 2 tokens a week.\"", line("time_settings_goal_note"))
        show(piano) { view -> TimeStreak(view, piano, today, testTag = "time_settings") }
        compose.onNodeWithTag("time_settings_step_1_minutes").assertDoesNotExist()
        compose.onNodeWithTag("time_settings_add_step").assertDoesNotExist()
        click("time_settings_rewards")
        compose.onNodeWithTag("time_settings_step_2_tokens_value").assertTextEquals("+1")
        assertTrue(top("time_settings_max_passes_value") < top("time_settings_step_1_minutes"))
        assertTrue(bottom("time_settings_add_step") <= top("time_settings_away_due"))
        click("time_settings_add_step")
        assertEquals(TimeRewards(maxPasses = 3, periods = listOf(RewardPeriod(today)), tokenSteps = steps(100 to 1, 200 to 1, 300 to 1)), piano.edited.rewards)
        show(piano) { view -> TimeGoalPart(view, piano, today, testTag = "time_settings") }
        assertEquals("[time_settings_goal_note] text=\"60 minutes in a week earn a token. More minutes earn up to 3 tokens a week.\"", line("time_settings_goal_note"))

        // Switched off, the rows are out of sight with a refused text, so nothing hidden holds Save back; on again, they show the steps as kept.
        show(piano) { view -> TimeStreak(view, piano, today, testTag = "time_settings") }
        compose.onNodeWithTag("time_settings_step_3_percent").performTextReplacement("none")
        compose.waitForIdle()
        assertFalse(piano.canSave)
        click("time_settings_rewards")
        compose.onNodeWithTag("time_settings_step_1_minutes").assertDoesNotExist()
        assertEquals(TimeRewards(maxPasses = 3, periods = emptyList(), tokenSteps = steps(100 to 1, 200 to 1, 300 to 1)), piano.edited.rewards)
        assertTrue(piano.canSave)
        click("time_settings_rewards")
        assertEquals("300", field("time_settings_step_3_percent"))
    }

    /**
     * On a narrow phone a row has no room for the minutes beside the step: they stand under it,
     * in its column, on one line, inside the row, which only grows taller; the stepper and the
     * remove keep their columns. The widest minutes decide for every row alike. Real glyph widths
     * are needed for that, which Robolectric's legacy graphics do not give.
     */
    @Test
    @Config(qualifiers = "w320dp-h640dp")
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun onANarrowPhoneTheMinutesStandUnderTheStepOnOneLine() {
        showScrolling()
        val reading = TimeSettingsDraft(testReadingTime().copy(goal = Dated.always(1440)), today)
        show(reading) { view -> TimeStreak(view, reading, today, testTag = "feature_settings") }

        compose.onNodeWithTag("feature_settings_step_1_minutes").assertTextEquals("1440 min")
        compose.onNodeWithTag("feature_settings_step_2_minutes").assertTextEquals("2880 min")
        val goal = compose.onNodeWithText("At the goal").fetchSemanticsNode().boundsInRoot
        listOf(1 to goal, 2 to bounds("feature_settings_step_2_percent")).forEach { (number, lead) ->
            val row = bounds("feature_settings_step_$number")
            val minutes = bounds("feature_settings_step_${number}_minutes")
            assertEquals("step $number", 1, lineCount("feature_settings_step_${number}_minutes"))
            assertTrue("step $number lead=$lead minutes=$minutes", minutes.top >= lead.bottom)
            assertEquals("step $number", lead.left, minutes.left)
            assertTrue("step $number", minutes.top >= row.top && minutes.bottom <= row.bottom && minutes.right <= row.right)
            listOf("tokens_value", "tokens_more").forEach { column -> assertTrue(column, bounds("feature_settings_step_${number}_$column").left > minutes.right) }
        }
        assertTrue(bounds("feature_settings_step_2_remove").left > bounds("feature_settings_step_2_tokens_more").right)
        assertEquals(bounds("feature_settings_step_1_tokens_value").left, bounds("feature_settings_step_2_tokens_value").left)
    }

    /**
     * Where the minutes stand is decided by the widest minutes the goal allows, ten times it, not
     * by what the rows hold: a row just wide enough for "2 min" beside the step and not for
     * "10 min" stacks the minutes from the start, and typing the second step from 200 to 1000 %,
     * to text it refuses and down to 500 % moves no row, nothing under the rows, and leaves the
     * field focused; "Add a step" only greys out.
     */
    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Config(qualifiers = "w411dp-h1000dp")
    fun typingAPercentNeverMovesTheRowsOrTheFocus() {
        compose.setContent {
            WorkoutTrackerTheme {
                val measurer = rememberTextMeasurer()
                val style = MaterialTheme.typography.bodyLarge
                val (fits, widest) = with(LocalDensity.current) { listOf("2 min", "10 min").map { measurer.measure(it, style).size.width.toDp() } }
                val rowWidth = stepLeadWidth() + StepColumnGap + StepLeadGap + (fits + widest) / 2 + StepColumnGap + stepControlsWidth(StepTokens.last)
                Column(Modifier.width(rowWidth).verticalScroll(rememberScrollState())) { shown?.invoke() }
            }
        }
        val reading = TimeSettingsDraft(testReadingTime().copy(goal = Dated.always(1)), today)
        show(reading) { view -> TimeStreak(view, reading, today, testTag = "feature_settings") }
        compose.onNodeWithTag("feature_settings_step_2_minutes").assertTextEquals("2 min")
        assertTrue(bounds("feature_settings_step_2_minutes").top >= bounds("feature_settings_step_2_percent").bottom)
        val rows = listOf(
            "feature_settings_step_1", "feature_settings_step_1_tokens_value", "feature_settings_step_2", "feature_settings_step_2_percent", "feature_settings_step_2_tokens_value", "feature_settings_step_2_remove",
            "feature_settings_steps_total", "feature_settings_add_step", "feature_settings_away_due",
        )
        // Where each stands and how tall it is; the total line's text may well grow wider.
        fun place(tag: String) = bounds(tag).let { Triple(it.left, it.top, it.height) }
        val before = rows.associateWith(::place)

        compose.onNodeWithTag("feature_settings_step_2_percent").performTextReplacement("1000")
        compose.waitForIdle()
        compose.onNodeWithTag("feature_settings_step_2_percent").assertIsFocused()
        compose.onNodeWithTag("feature_settings_step_2_minutes").assertTextEquals("10 min")
        assertEquals(1, lineCount("feature_settings_step_2_minutes"))
        assertEquals(before, rows.associateWith(::place))
        // At ten times the goal no step can follow, so the button under the rows greys out where it stands.
        compose.onNodeWithTag("feature_settings_add_step").assertIsNotEnabled()
        // Refused text says why in the label's place and holds the button back; nothing moves.
        compose.onNodeWithTag("feature_settings_step_2_percent").performTextReplacement("half")
        compose.waitForIdle()
        compose.onNodeWithTag("feature_settings_step_2_percent").assertIsFocused()
        assertEquals("[feature_settings_step_2_percent] field=\"half\" error=\"101–1000\"", line("feature_settings_step_2_percent"))
        compose.onNodeWithTag("feature_settings_add_step").assertIsNotEnabled()
        assertEquals(before, rows.associateWith(::place))
        compose.onNodeWithTag("feature_settings_step_2_percent").performTextReplacement("500")
        compose.waitForIdle()
        compose.onNodeWithTag("feature_settings_step_2_percent").assertIsFocused()
        compose.onNodeWithTag("feature_settings_step_2_minutes").assertTextEquals("5 min")
        assertEquals(before, rows.associateWith(::place))
        compose.onNodeWithTag("feature_settings_add_step").assertIsEnabled()
    }


    /**
     * Every error a step's field can show fits its label line on the real page, in the language
     * on show: the label itself; the one percent a step between 999 % and nothing may have, said
     * alone; and the range of a field emptied and left, which keeps the label's small size instead
     * of resting large inside the empty field, where it would be cut.
     */
    @Test
    @Config(qualifiers = "w411dp-h1400dp-xhdpi")
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun everyErrorAStepCanShowFitsItsFieldOnThePage() = everyErrorFitsItsField(label = "Of the goal")

    @Test
    @Config(qualifiers = "de-rDE-w411dp-h1400dp-xhdpi")
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun everyErrorAStepCanShowFitsItsFieldOnThePageInGerman() = everyErrorFitsItsField(label = "Vom Ziel")

    private fun everyErrorFitsItsField(label: String) {
        showPage(joinedPiano(steps(100 to 1, 999 to 1, 1000 to 1)))
        assertReadWhole(layoutOf(labelOf("time_settings_step_2_percent", label)))

        type("time_settings_step_3_percent", "50")
        assertReadWhole(layoutOf(labelOf("time_settings_step_3_percent", "1000")))

        type("time_settings_step_2_percent", "")
        compose.onNodeWithTag("time_settings_step_3_percent").requestFocus()
        compose.waitForIdle()
        compose.onNodeWithTag("time_settings_step_2_percent").assertIsNotFocused()
        val emptied = layoutOf(labelOf("time_settings_step_2_percent", "101–999"))
        assertReadWhole(emptied)
        assertEquals(smallType, emptied.layoutInput.style.fontSize)
    }

    /**
     * On a narrow phone at twice the font size the lead column and the controls cannot share a
     * line: the field keeps the width its content asks, the minutes stand under it and the
     * stepper and the remove take a line of their own, from the row's left edge to its right.
     * "1000 %" lies whole inside the field, nothing scrolls, and a refused percent's range is
     * read whole. Real glyph widths are needed, which Robolectric's legacy graphics do not give.
     */
    @Test
    @Config(qualifiers = "w320dp-h2000dp-xhdpi", fontScale = 2f)
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun atALargeFontScaleOnANarrowPhoneTheControlsStandOnTheirOwnLine() = controlsStandOnTheirOwnLine(label = "Of the goal", goalWord = "At the goal")

    @Test
    @Config(qualifiers = "de-rDE-w320dp-h2000dp-xhdpi", fontScale = 2f)
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun atALargeFontScaleOnANarrowPhoneTheControlsStandOnTheirOwnLineInGerman() = controlsStandOnTheirOwnLine(label = "Vom Ziel", goalWord = "Beim Ziel")

    private fun controlsStandOnTheirOwnLine(label: String, goalWord: String) {
        showPage(joinedPiano(DefaultTokenSteps))
        type("time_settings_step_2_percent", "1000")

        val field = bounds("time_settings_step_2_percent")
        val input = layoutOf(compose.onNodeWithTag("time_settings_step_2_percent"))
        assertTrue("the typed number scrolls: ${input.size.width} px in ${roomForTheNumber(field)} px", input.size.width <= roomForTheNumber(field))
        assertReadWhole(layoutOf(labelOf("time_settings_step_2_percent", label)))
        listOf(1 to compose.onNodeWithText(goalWord).fetchSemanticsNode().boundsInRoot, 2 to field).forEach { (number, lead) ->
            val row = bounds("time_settings_step_$number")
            val minutes = bounds("time_settings_step_${number}_minutes")
            val less = bounds("time_settings_step_${number}_tokens_less")
            assertTrue("step $number", lead.right <= row.right)
            assertTrue("step $number", minutes.top >= lead.bottom && minutes.left == lead.left)
            assertEquals("step $number", 1, lineCount("time_settings_step_${number}_minutes"))
            assertTrue("step $number", less.top >= minutes.bottom && less.left == row.left)
            assertTrue("step $number", bounds("time_settings_step_${number}_tokens_more").right <= row.right)
        }
        assertEquals(bounds("time_settings_step_2").right, bounds("time_settings_step_2_remove").right)

        type("time_settings_step_2_percent", "50")
        assertReadWhole(layoutOf(labelOf("time_settings_step_2_percent", "101–1000")))
    }

    /**
     * The widest minutes the goal allows decide the narrow layout as the lead does: a row with
     * room beside the controls for the lead but not for "100800 min" stacks the controls under
     * the minutes, one with room for both stands them beside, and one without room for the lead
     * stacks whatever the minutes need. With the words as they are the lead is the wider of the
     * two in both languages at every font size, so the rule is held with a lead made narrower.
     */
    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun theWidestMinutesDecideTheNarrowLayoutAsTheLeadDoes() {
        val decided = mutableMapOf<String, StepRowLayout>()
        compose.setContent {
            WorkoutTrackerTheme {
                val controls = stepControlsWidth(StepTokens.last)
                val minutes = with(LocalDensity.current) { rememberTextMeasurer().measure("100800 min", MaterialTheme.typography.bodyLarge).size.width.toDp() }
                val narrowLead = minutes - 20.dp
                decided["minutes too wide"] = stepRowLayout(rowWidth = controls + StepColumnGap + minutes - 1.dp, leadWidth = narrowLead, widestMiddle = "100800 min", controlsWidth = controls)
                decided["both fit"] = stepRowLayout(rowWidth = controls + StepColumnGap + minutes, leadWidth = narrowLead, widestMiddle = "100800 min", controlsWidth = controls)
                decided["lead too wide"] = stepRowLayout(rowWidth = controls + StepColumnGap + minutes, leadWidth = minutes + 1.dp, widestMiddle = "100800 min", controlsWidth = controls)
                decided["all on one line"] = stepRowLayout(rowWidth = narrowLead + StepColumnGap + StepLeadGap + minutes + StepColumnGap + controls, leadWidth = narrowLead, widestMiddle = "100800 min", controlsWidth = controls)
            }
        }
        compose.waitForIdle()
        assertEquals(
            mapOf("minutes too wide" to StepRowLayout.Stacked, "both fit" to StepRowLayout.MiddleUnder, "lead too wide" to StepRowLayout.Stacked, "all on one line" to StepRowLayout.OneLine),
            decided,
        )
    }

    /**
     * The widest minutes a goal can reach are read whole where they stand: on a 396 dp page at
     * twice the font, "100800 min" stands under the lead beside the controls, no unit lost to a
     * discarded second line, and the field holds "1000 %" whole beside them.
     */
    @Test
    @Config(qualifiers = "w396dp-h2200dp-xhdpi", fontScale = 2f)
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun theWidestMinutesAreReadWholeOnANarrowPageAtALargeFont() {
        showPage(joinedPiano(steps(100 to 1, 1000 to 1), goal = 10080))
        compose.onNodeWithTag("time_settings_step_2_minutes").assertTextEquals("100800 min")
        assertReadWhole(layoutOf(compose.onNodeWithTag("time_settings_step_2_minutes")))
        val row = bounds("time_settings_step_2")
        val minutes = bounds("time_settings_step_2_minutes")
        val field = bounds("time_settings_step_2_percent")
        val less = bounds("time_settings_step_2_tokens_less")
        assertTrue("minutes $minutes in row $row", minutes.right <= row.right)
        assertTrue("minutes $minutes under field $field", minutes.top >= field.bottom && minutes.left == field.left)
        assertTrue("stepper $less beside minutes $minutes", less.left >= minutes.right && less.top < minutes.bottom)
        val input = layoutOf(compose.onNodeWithTag("time_settings_step_2_percent"))
        assertTrue("the typed number scrolls: ${input.size.width} px in ${roomForTheNumber(field)} px", input.size.width <= roomForTheNumber(field))
    }

    /**
     * A row keeps its place when the step above it is removed, and the step below moves into it.
     * A remove button armed on the step that moved must not stay armed for it: the shifted row
     * still needs two taps, and the field shows the step that moved in.
     */
    @Test
    fun aRemoveArmedOnOneStepDoesNotPassToTheStepThatMovesIntoItsRow() {
        showScrolling()
        val reading = TimeSettingsDraft(testReadingTime().copy(rewards = TimeRewards(maxPasses = 3, tokenSteps = steps(100 to 1, 200 to 2, 300 to 3, 400 to 4))), today)
        show(reading) { view -> TimeStreak(view, reading, today, testTag = "feature_settings") }

        click("feature_settings_step_3_remove")
        compose.onNodeWithTag("feature_settings_step_3_remove").assertContentDescriptionEquals("Confirm: Remove step 3")
        click("feature_settings_step_2_remove")
        click("feature_settings_step_2_remove")
        assertEquals(steps(100 to 1, 300 to 3, 400 to 4), reading.steps)
        assertEquals("300", field("feature_settings_step_2_percent"))
        assertEquals("400", field("feature_settings_step_3_percent"))

        // The 400 % step sits where the armed button was: one tap only arms it.
        compose.onNodeWithTag("feature_settings_step_3_remove").assertContentDescriptionEquals("Remove step 3")
        click("feature_settings_step_3_remove")
        assertEquals(steps(100 to 1, 300 to 3, 400 to 4), reading.steps)
        compose.onNodeWithTag("feature_settings_step_3_remove").assertContentDescriptionEquals("Confirm: Remove step 3")
        click("feature_settings_step_3_remove")
        assertEquals(steps(100 to 1, 300 to 3), reading.steps)
    }

    @Test
    fun aMadeViewsStreakHasTheSwitchAndItsBanksSizeWhileItCounts() {
        compose.setContent { WorkoutTrackerTheme { shown?.invoke() } }
        val piano = TimeSettingsDraft(testMadeTime(), today)

        show(piano) { view -> TimeStreak(view, piano, today, testTag = "time_settings") }
        compose.onNodeWithTag("time_settings_rewards").assertIsOff()
        compose.onNodeWithTag("time_settings_max_passes_value").assertDoesNotExist()
        click("time_settings_rewards")
        click("time_settings_max_passes_more")
        compose.onNodeWithTag("time_settings_max_passes_value").assertTextEquals("4")
        compose.onNodeWithTag("time_settings_every_kept_days_value").assertDoesNotExist()
        assertEquals(TimeRewards(maxPasses = 4, periods = listOf(RewardPeriod(today))), piano.edited.rewards)

        // Switched off again, the bank's size is out of sight and kept for when it counts again.
        click("time_settings_rewards")
        compose.onNodeWithTag("time_settings_max_passes_value").assertDoesNotExist()
        assertEquals(TimeRewards(maxPasses = 4, periods = emptyList()), piano.edited.rewards)
    }
}

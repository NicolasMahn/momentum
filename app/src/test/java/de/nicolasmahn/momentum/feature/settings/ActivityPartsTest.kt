package de.nicolasmahn.momentum.feature.settings

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.text.TextLayoutResult
import de.nicolasmahn.momentum.core.ui.theme.WorkoutTrackerTheme
import de.nicolasmahn.momentum.domain.activity.ActivityConfiguration
import de.nicolasmahn.momentum.domain.activity.ActivityEdit
import de.nicolasmahn.momentum.domain.activity.ActivityTokenStep
import de.nicolasmahn.momentum.domain.activity.ActivityTokenSteps
import de.nicolasmahn.momentum.domain.activity.WeekPlan
import de.nicolasmahn.momentum.domain.activity.ListedActivity
import de.nicolasmahn.momentum.domain.activity.NamedActivityIds
import de.nicolasmahn.momentum.domain.activity.applyingEdits
import de.nicolasmahn.momentum.domain.activity.routine.Routine
import de.nicolasmahn.momentum.domain.activity.testActivityConfiguration
import de.nicolasmahn.momentum.domain.checklist.ViewIcon
import de.nicolasmahn.momentum.domain.checklist.WhenAway
import de.nicolasmahn.momentum.domain.reminder.ReminderSettings
import de.nicolasmahn.momentum.domain.settings.DashboardFeature
import java.time.DayOfWeek
import java.time.LocalDate
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
 * Activity's parts of its own over its draft: the Week says what its days mean and plans each
 * chosen one; Activities is a list whose rows open an activity's page and a day's page, which
 * Back leaves one level at a time; every number says what it means, a fold stays open while a
 * field in it holds an error, nothing below a field moves when its error appears, a refused name
 * says so where it was typed and on the row that leads to it, and what a field holds survives
 * going up a level, a look at another part and process death. Laid out as the page lays them out,
 * in a column that scrolls.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h2400dp-xhdpi")
class ActivityPartsTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val today = LocalDate.parse("2026-10-14")
    private val tag = "feature_settings"
    private var shown by mutableStateOf<(@Composable () -> Unit)?>(null)
    private var level by mutableStateOf<ActivitiesLevel>(ActivitiesLevel.List)

    private fun show(draft: ActivitySettingsDraft, part: @Composable (ActivityConfiguration) -> Unit) {
        shown = { part(draft.edited) }
        compose.waitForIdle()
    }

    private fun showScrolling() = compose.setContent { WorkoutTrackerTheme { Column(Modifier.verticalScroll(rememberScrollState())) { shown?.invoke() } } }

    private fun click(tag: String) {
        compose.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
    }

    private fun type(tag: String, text: String) {
        compose.onNodeWithTag(tag).performTextReplacement(text)
        compose.waitForIdle()
    }

    /** The system's Back, as a person presses it. */
    private fun back() {
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }

    private fun top(tag: String): Float = compose.onNodeWithTag(tag).fetchSemanticsNode().positionInRoot.y

    private fun assertError(tag: String, error: String) = compose.onNodeWithTag(tag).assert(SemanticsMatcher.expectValue(SemanticsProperties.Error, error))

    private fun assertNoError(tag: String) = compose.onNodeWithTag(tag).assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Error))

    private fun input(tag: String): String = compose.onNodeWithTag(tag).fetchSemanticsNode().config[SemanticsProperties.EditableText].text

    private fun activities(draft: ActivitySettingsDraft, at: ActivitiesLevel = ActivitiesLevel.List) {
        level = at
        show(draft) { view -> ActivitiesPart(view = view, draft = draft, named = NamedActivityIds(), level = level, onLevel = { level = it }, testTag = tag) }
    }

    /**
     * Week's toggles choose the days, the line under them says what they earn and what rests, and
     * one row per chosen day plans what is done on it, "Any activity" or an activity by name;
     * choosing a day moves nothing above the rows.
     */
    @Test
    fun theWeekSaysWhatItsDaysMeanAndPlansEachChosenDay() {
        showScrolling()
        val draft = ActivitySettingsDraft(testActivityConfiguration(), today)
        show(draft) { view -> ActivityWeek(view = view, draft = draft, testTag = tag) {} }

        compose.onNodeWithTag("${tag}_week_day_5").assertIsSelected()
        compose.onNodeWithTag("${tag}_week_day_6").assertIsNotSelected()
        compose.onNodeWithTag("${tag}_week_note").assertTextEquals("5 active days in a week earn a token. The other 2 days are rest days.")
        compose.onNodeWithTag("${tag}_week_plan_1").assertTextContains("Monday").assertTextContains("Gym")
        compose.onNodeWithTag("${tag}_week_plan_2").assertTextContains("Bouldering")
        compose.onNodeWithTag("${tag}_week_plan_4").assertTextContains("Any activity")
        compose.onNodeWithTag("${tag}_week_plan_6").assertDoesNotExist()
        val plans = top("${tag}_week_plan_1")

        click("${tag}_week_day_6")
        click("${tag}_week_day_7")
        compose.onNodeWithTag("${tag}_week_note").assertTextEquals("7 active days in a week earn a token. There are no rest days.")
        compose.onNodeWithTag("${tag}_week_plan_6").assertTextContains("Saturday").assertTextContains("Any activity")
        assertEquals(plans, top("${tag}_week_plan_1"), 0f)

        click("${tag}_week_plan_2")
        click("${tag}_week_plan_2_cycling")
        compose.onNodeWithTag("${tag}_week_plan_2").assertTextContains("Cycling")
        click("${tag}_week_plan_1")
        click("${tag}_week_plan_1_any")
        assertEquals(listOf(null, "cycling", "gym", null, "gym", null, null), draft.edited.week.on(today).days.map { it.activityId })

        (2..7).forEach { click("${tag}_week_day_$it") }
        compose.onNodeWithTag("${tag}_week_note").assertTextEquals("1 active day in a week earns a token. The other 6 days are rest days.")
        compose.onNodeWithTag("${tag}_week_plan_2").assertDoesNotExist()
        assertEquals(plans, top("${tag}_week_plan_1"), 0f)
        // The last day stays chosen.
        click("${tag}_week_day_1")
        compose.onNodeWithTag("${tag}_week_day_1").assertIsSelected()
        assertEquals(1, draft.edited.daysAWeekOn(today))
    }

    /**
     * The list's rows open an activity's page, whose day rows open a day's page; the system's Back
     * goes up one level at a time. Every number says what it means, on the rows and in the folds.
     */
    @Test
    fun theListOpensAnActivityAndADayAndBackGoesUpALevel() {
        showScrolling()
        val draft = ActivitySettingsDraft(testActivityConfiguration(), today)
        activities(draft)

        compose.onNodeWithTag("${tag}_activity_gym").assertTextContains("Gym").assertTextContains("Routine · 3 days")
        compose.onNodeWithTag("${tag}_activity_bouldering").assertTextEquals("Bouldering", "No routine")
        compose.onNodeWithTag("${tag}_activity_gym_up").assertIsNotEnabled()
        compose.onNodeWithTag("${tag}_activity_bouldering_up").assertIsEnabled()
        compose.onNodeWithTag("${tag}_activity_gym_name").assertDoesNotExist()

        click("${tag}_activity_gym")
        assertEquals("Gym", input("${tag}_activity_gym_name"))
        compose.onNodeWithTag("${tag}_activity_gym_icon").assertTextEquals("Icon", "Dumbbell")
        compose.onNodeWithTag("${tag}_activity_gym_day_A").assertTextContains("Day A").assertTextContains("5 exercises · 14 sets")
        compose.onNodeWithTag("${tag}_activity_gym_day_A_up").assertIsNotEnabled()
        compose.onNodeWithTag("${tag}_activity_bouldering").assertDoesNotExist()

        click("${tag}_activity_gym_day_A")
        assertEquals("Day A", input("${tag}_day_A_name"))
        compose.onNodeWithTag("${tag}_exercise_bench_press_plan").assertTextContains("3 sets · 8–10 reps · ±2.5 kg")
        compose.onNodeWithTag("${tag}_exercise_bench_press_sets_value").assertDoesNotExist()
        click("${tag}_exercise_bench_press_plan")
        compose.onNodeWithTag("${tag}_exercise_bench_press_sets_value").assertTextEquals("3")
        compose.onNodeWithTag("${tag}_exercise_bench_press_reps_from").assertTextContains("8")
        compose.onNodeWithTag("${tag}_exercise_bench_press_start_weight").assertTextContains("22.5")
        click("${tag}_exercise_bench_press_sets_more")
        compose.onNodeWithTag("${tag}_exercise_bench_press_plan").assertTextContains("4 sets · 8–10 reps · ±2.5 kg")

        back()
        compose.onNodeWithTag("${tag}_activity_gym_day_A").assertTextContains("5 exercises · 15 sets")
        back()
        compose.onNodeWithTag("${tag}_activity_gym").assertTextContains("Routine · 3 days")
        assertEquals(ActivitiesLevel.List, level)

        // Each page opens with the way up, named by where it leads.
        click("${tag}_activity_gym")
        click("${tag}_activity_gym_day_B")
        compose.onNodeWithTag("${tag}_up").assertContentDescriptionEquals("Back to Gym")
        click("${tag}_up")
        assertEquals(ActivitiesLevel.Activity("gym"), level)
        compose.onNodeWithTag("${tag}_up").assertContentDescriptionEquals("Back to Activities")
        click("${tag}_up")
        assertEquals(ActivitiesLevel.List, level)
        compose.onNodeWithTag("${tag}_up").assertDoesNotExist()
    }

    /**
     * Every activity's row has a line under its name, "No routine" where it has none, and "Something
     * to fix" takes that line's place while its page holds an error: a name that wraps keeps its
     * row's height either way, so nothing under it moves.
     */
    @Test
    @Config(qualifiers = "w320dp-h2400dp-xhdpi")
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun aWrappedNameKeepsItsRowsHeightWhenTheWarningTakesItsLine() {
        showScrolling()
        val long = "Brazilian jiu-jitsu with the club"
        val draft = ActivitySettingsDraft(testActivityConfiguration().let { it.copy(activities = it.activities + ListedActivity("act_bjj", long, ViewIcon.MartialArts)) }, today)
        activities(draft)
        val row = "${tag}_activity_act_bjj"
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNode(hasText(long) and hasAnyAncestor(hasTestTag(row)), useUnmergedTree = true).fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action!!.invoke(layouts)
        assertTrue("the name wraps", layouts.single().lineCount > 1)
        compose.onNodeWithTag(row).assertTextContains("No routine")
        compose.onNodeWithTag("${tag}_activity_gym").assertTextContains("Routine · 3 days")
        val height = compose.onNodeWithTag(row).fetchSemanticsNode().boundsInRoot.height
        val add = top("${tag}_new_activity")

        draft.keepText(ActivitySettingsDraft.activityKey("act_bjj"), "Gym")
        draft.nameActivity("act_bjj", "Gym")
        compose.waitForIdle()
        compose.onNodeWithTag(row).assertTextContains("Something to fix")
        assertEquals(height, compose.onNodeWithTag(row).fetchSemanticsNode().boundsInRoot.height, 0f)
        assertEquals(add, top("${tag}_new_activity"), 0f)
    }

    /** A refused number says its rule where it was typed, keeps its fold open, and moves nothing under it; the other end of the range lets it in. */
    @Test
    fun aRefusedNumberKeepsItsFoldOpenAndMovesNothingBelow() {
        showScrolling()
        val draft = ActivitySettingsDraft(testActivityConfiguration(), today)
        activities(draft, ActivitiesLevel.Day("gym", "A"))
        click("${tag}_exercise_bench_press_plan")
        val squat = top("${tag}_exercise_back_squat")
        val add = top("${tag}_day_A_add_exercise")

        type("${tag}_exercise_bench_press_reps_from", "12")
        assertError("${tag}_exercise_bench_press_reps_from", "At most 10")
        assertEquals(8..10, draft.edited.routineExercise("bench_press")!!.reps)
        assertEquals(squat, top("${tag}_exercise_back_squat"), 0f)
        assertEquals(add, top("${tag}_day_A_add_exercise"), 0f)
        // A tap to fold it while the error is on show counts for nothing, so the fixed field is still there to see.
        click("${tag}_exercise_bench_press_plan")
        assertEquals("12", input("${tag}_exercise_bench_press_reps_from"))
        type("${tag}_exercise_bench_press_reps_to", "15")
        assertNoError("${tag}_exercise_bench_press_reps_from")
        assertEquals("12", input("${tag}_exercise_bench_press_reps_from"))
        assertEquals(12..15, draft.edited.routineExercise("bench_press")!!.reps)
        type("${tag}_exercise_bench_press_start_weight", "22,5")
        assertNoError("${tag}_exercise_bench_press_start_weight")
        type("${tag}_exercise_bench_press_step", "0.2")
        assertError("${tag}_exercise_bench_press_step", "0.25 to 20 kg")
        type("${tag}_exercise_bench_press_start_weight", "1000")
        assertError("${tag}_exercise_bench_press_start_weight", "−200 to 500 kg")

        // Up a level, the day's row says something on its page waits to be fixed.
        back()
        compose.onNodeWithTag("${tag}_activity_gym_day_A").assertTextContains("Something to fix")
        compose.onNodeWithTag("${tag}_activity_gym_day_B").assertTextContains("4 exercises · 12 sets")
    }

    /**
     * A refused number shown again after going up a level opens its fold, and the fold stays open
     * while it is corrected: the first digit that settles it folds nothing away.
     */
    @Test
    fun aRefusedNumberShownAgainStaysOpenWhileItIsCorrected() {
        showScrolling()
        val draft = ActivitySettingsDraft(testActivityConfiguration(), today)
        activities(draft, ActivitiesLevel.Day("gym", "A"))
        click("${tag}_exercise_bench_press_plan")
        type("${tag}_exercise_bench_press_start_weight", "1000")
        assertError("${tag}_exercise_bench_press_start_weight", "−200 to 500 kg")
        back()
        click("${tag}_activity_gym_day_A")
        assertEquals("1000", input("${tag}_exercise_bench_press_start_weight"))

        type("${tag}_exercise_bench_press_start_weight", "2")
        assertNoError("${tag}_exercise_bench_press_start_weight")
        type("${tag}_exercise_bench_press_start_weight", "20")
        assertEquals("20", input("${tag}_exercise_bench_press_start_weight"))
        assertEquals(20.0, draft.edited.routineExercise("bench_press")!!.weight!!.startKg, 0.0)
    }

    /**
     * Turned off, "With weight" takes the start weight and the step out of the fold, and what
     * their fields held with them, so nothing hidden holds Save back; turned on again, the weight
     * stored for the exercise is back.
     */
    @Test
    fun withWeightOffTheWeightFieldsLeaveAndTakeWhatTheyHeld() {
        showScrolling()
        val draft = ActivitySettingsDraft(testActivityConfiguration(), today)
        activities(draft, ActivitiesLevel.Day("gym", "A"))
        click("${tag}_exercise_bench_press_plan")
        compose.onNodeWithTag("${tag}_exercise_bench_press_weight").assertIsOn()
        type("${tag}_exercise_bench_press_step", "no")
        assertTrue(draft.heldBack)

        click("${tag}_exercise_bench_press_weight")
        compose.onNodeWithTag("${tag}_exercise_bench_press_weight").assertIsOff()
        compose.onNodeWithTag("${tag}_exercise_bench_press_start_weight").assertDoesNotExist()
        compose.onNodeWithTag("${tag}_exercise_bench_press_step").assertDoesNotExist()
        compose.onNodeWithTag("${tag}_exercise_bench_press_start_reps").assertExists()
        compose.onNodeWithTag("${tag}_exercise_bench_press_plan").assertTextContains("3 sets · 8–10 reps").assert(SemanticsMatcher("no step") { node -> node.config.getOrNull(SemanticsProperties.Text)?.none { "kg" in it.text } != false })
        assertEquals(null, draft.edited.routineExercise("bench_press")!!.weight)
        assertFalse(draft.heldBack)

        click("${tag}_exercise_bench_press_weight")
        assertEquals("22.5", input("${tag}_exercise_bench_press_start_weight"))
        assertEquals("2.5", input("${tag}_exercise_bench_press_step"))
        assertEquals(testActivityConfiguration().routineExercise("bench_press"), draft.edited.routineExercise("bench_press"))
    }

    /** A name another activity has is refused in its own field and in "New activity", and the row leading to it says so; a new name is added at the end with Other's icon. */
    @Test
    fun aTakenNameIsRefusedWhereItWasTypedAndANewOneIsAdded() {
        showScrolling()
        val draft = ActivitySettingsDraft(testActivityConfiguration(), today)
        activities(draft, ActivitiesLevel.Activity("cycling"))
        type("${tag}_activity_cycling_name", "jogging")
        assertError("${tag}_activity_cycling_name", "Already in use")
        assertEquals("jogging", input("${tag}_activity_cycling_name"))
        assertEquals("Cycling", draft.edited.activity("cycling")!!.label)
        assertTrue(draft.heldBack)
        back()
        compose.onNodeWithTag("${tag}_activity_cycling").assertTextContains("Cycling").assertTextContains("Something to fix")

        click("${tag}_new_activity")
        type("${tag}_add_activity", "Jogging")
        assertError("${tag}_add_activity", "Already in use")
        assertFalse(draft.canAddNewActivity)
        assertTrue(draft.newActivityTaken && draft.heldBack)
        type("${tag}_add_activity", "Swimming")
        assertNoError("${tag}_add_activity")
        assertFalse(draft.newActivityTaken)
        // Nothing is chosen for a new activity: Add, the page's in the bar, waits for its icon, which gives it its color too.
        assertFalse(draft.canAddNewActivity)
        click("${tag}_new_icon_swimming")
        compose.onNodeWithTag("${tag}_new_icon_swimming").assertIsSelected()
        compose.runOnUiThread { draft.addNewActivity(NamedActivityIds())?.let { level = ActivitiesLevel.Extras(it) } }
        compose.waitForIdle()
        val swimming = draft.edited.activities.last()
        assertEquals("Swimming", swimming.label)
        assertEquals(ViewIcon.Swimming, swimming.icon)
        // Added, the next step offers what it may come with; up from there is the list.
        compose.onNodeWithText("Swimming is added. Give it a routine now, or later on its page.").assertExists()
        compose.onNodeWithTag("${tag}_activity_${swimming.id}_add_routine").assertExists()
        back()
        compose.onNodeWithTag("${tag}_activity_${swimming.id}").assertTextEquals("Swimming", "No routine")
        click("${tag}_new_activity")
        assertEquals("", input("${tag}_add_activity"))
        back()
        click("${tag}_activity_cycling")
        type("${tag}_activity_cycling_name", "Bike")
        assertNoError("${tag}_activity_cycling_name")
        assertFalse(draft.heldBack)
    }

    /** A routine given while an activity is made stays a step of making it: up from its day is that step, across process death too. */
    @Test
    fun aRoutineDayOpenedWhileMakingAnActivityGoesBackUpToThatStep() {
        showScrolling()
        val draft = ActivitySettingsDraft(testActivityConfiguration(), today)
        activities(draft, ActivitiesLevel.Extras("cycling"))
        click("${tag}_activity_cycling_add_routine")
        val day = level as ActivitiesLevel.Day
        assertTrue(day.settingUp)
        assertEquals(day, ActivitiesLevel.of(day.path))
        back()
        assertEquals(ActivitiesLevel.Extras("cycling"), level)
        back()
        assertEquals(ActivitiesLevel.List, level)
    }

    /**
     * What a field holds belongs to the draft, not to the field: a name in use typed into
     * Bouldering's, a blank name and text not yet added each survive going up a level, a look at
     * another part and process death, each with what it says, and hold Save back while they are
     * there to be seen; Bouldering removed with the name in use, its error goes with it.
     */
    @Test
    fun whatAFieldHoldsSurvivesGoingUpATabAndRestoration() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent { WorkoutTrackerTheme { Page(testActivityConfiguration()) } }
        click("${tag}_new_activity")
        type("${tag}_add_activity", "Swim")
        back()
        click("${tag}_activity_bouldering")
        type("${tag}_activity_bouldering_name", "Gym")
        assertError("${tag}_activity_bouldering_name", "Already in use")
        back()
        click("${tag}_activity_jogging")
        type("${tag}_activity_jogging_name", "")
        back()
        click("${tag}_new_activity")
        assertEquals("Swim", input("${tag}_add_activity"))
        back()
        compose.onNodeWithTag("${tag}_activity_bouldering").assertTextContains("Something to fix")
        compose.onNodeWithTag("${tag}_activity_jogging").assertTextContains("Something to fix")
        compose.onNodeWithTag("${tag}_save").assertIsNotEnabled()

        click("${tag}_activity_bouldering")
        click("${tag}_tab_streak")
        click("${tag}_tab_activities")
        assertEquals("Gym", input("${tag}_activity_bouldering_name"))
        assertError("${tag}_activity_bouldering_name", "Already in use")

        restoration.emulateSavedInstanceStateRestore()
        assertEquals("Gym", input("${tag}_activity_bouldering_name"))
        assertError("${tag}_activity_bouldering_name", "Already in use")
        compose.onNodeWithTag("${tag}_save").assertIsNotEnabled()
        back()
        click("${tag}_new_activity")
        assertEquals("Swim", input("${tag}_add_activity"))
        back()
        click("${tag}_activity_jogging")
        assertEquals("", input("${tag}_activity_jogging_name"))
        assertError("${tag}_activity_jogging_name", "Enter a name")
        type("${tag}_activity_jogging_name", "Running")
        back()
        compose.onNodeWithTag("${tag}_save").assertIsNotEnabled()

        click("${tag}_activity_bouldering")
        click("${tag}_activity_bouldering_remove")
        click("${tag}_activity_bouldering_remove")
        compose.onNodeWithTag("${tag}_activity_bouldering").assertDoesNotExist()
        compose.onNodeWithTag("${tag}_save").assertIsEnabled()
        click("${tag}_save")
        assertEquals("Running", stored.activity("jogging")!!.label)
        assertTrue(stored.activity("bouldering")!!.removed)
        click("${tag}_new_activity")
        assertEquals("Swim", input("${tag}_add_activity"))
    }

    /**
     * A name another activity has under "New activity" holds Save back, as anything a field
     * refuses does; a new name there is only text to add, so other changes save, and it stays
     * afterwards, with the line that says so, across process death too.
     */
    @Test
    fun aDuplicateUnderNewActivityHoldsSaveWhilePlainTextLetsOtherChangesSave() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent { WorkoutTrackerTheme { Page(stored) } }
        click("${tag}_activity_bouldering_up")
        compose.onNodeWithTag("${tag}_save").assertIsEnabled()
        click("${tag}_new_activity")
        // While a new activity is being made, the bar holds its Add in place of Save.
        compose.onNodeWithTag("${tag}_new_activity_next").assertIsNotEnabled()
        compose.onNodeWithTag("${tag}_save").assertDoesNotExist()
        type("${tag}_add_activity", "Jogging")
        assertError("${tag}_add_activity", "Already in use")
        back()
        compose.onNodeWithTag("${tag}_save").assertIsNotEnabled()
        click("${tag}_tab_streak")
        click("${tag}_tab_activities")
        compose.onNodeWithTag("${tag}_save").assertIsNotEnabled()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("${tag}_save").assertIsNotEnabled()
        click("${tag}_new_activity")
        assertEquals("Jogging", input("${tag}_add_activity"))

        type("${tag}_add_activity", "Swim")
        back()
        compose.onNodeWithTag("${tag}_save").assertIsEnabled()
        click("${tag}_save")
        assertEquals(listOf("bouldering", "gym", "cycling", "jogging"), stored.activities.map { it.id })
        // A step that is not ready says why only when its button is pressed.
        compose.onNodeWithTag("${tag}_save").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("${tag}_hint").assertTextEquals("Add or clear what you typed.")
        compose.onNodeWithTag("${tag}_save").assertIsNotEnabled()
        restoration.emulateSavedInstanceStateRestore()
        // A step that is not ready says why only when its button is pressed.
        compose.onNodeWithTag("${tag}_save").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("${tag}_hint").assertTextEquals("Add or clear what you typed.")
        click("${tag}_new_activity")
        assertEquals("Swim", input("${tag}_add_activity"))
        // Beside Add the bar has nothing to say about what Save waits for.
        compose.onNodeWithTag("${tag}_hint").assertDoesNotExist()
    }

    /** A page opened from low on a long list starts at the top, under the tab row, which stays where it was on every level. */
    @Test
    @Config(qualifiers = "w411dp-h700dp-xhdpi")
    fun openingALevelScrollsThePageToItsTop() {
        // The last one has a routine as long as the gym's, so its page is long enough to keep a scroll of its own.
        val many = testActivityConfiguration().let { seeded ->
            seeded.copy(activities = seeded.activities + (1..8).map { ListedActivity("act_more_$it", "Activity $it", ViewIcon.Bolt, routine = seeded.activity("gym")!!.routine.takeIf { _ -> it == 8 }?.let { routine -> Routine(routine.days.map { day -> day.copy(id = "${day.id}8", exercises = day.exercises.map { exercise -> exercise.copy(id = "${exercise.id}_8") }) }) }) })
        }
        compose.setContent { WorkoutTrackerTheme { Page(many) } }
        compose.onNodeWithTag("${tag}_activity_act_more_8").performScrollTo()
        compose.waitForIdle()
        val tabs = top("${tag}_tab_activities")
        assertTrue("the list was scrolled past its tab row", tabs < 0)
        click("${tag}_activity_act_more_8")
        assertTrue(top("${tag}_tab_activities") > tabs)
        assertTrue(top("${tag}_tab_activities") >= 0f)
        assertTrue(top("${tag}_up") > top("${tag}_tab_activities"))
    }

    private var stored by mutableStateOf(testActivityConfiguration())

    /** Activity's real page over a draft kept as the app keeps it, so what it holds survives a restoration; Save stores into [stored]. */
    @Composable
    private fun Page(view: ActivityConfiguration) {
        val reminder = rememberSaveable(saver = FeatureSettingsDraft.Saver) { FeatureSettingsDraft() }
        val save = rememberBuiltInViewSave(view, today, reminder, ActivitySettingsDraft.Saver) { activity, day -> ActivitySettingsDraft(activity, day) }
        ActivitySettingsPage(
            save = save,
            settings = ReminderSettings(),
            named = NamedActivityIds(),
            onSave = {
                save.save(
                    settings = ReminderSettings(),
                    exactAlarmAllowed = true,
                    notificationsAllowed = true,
                    storeConfiguration = { edits -> stored = stored.applyingEdits(edits, today); true },
                    storeReminder = { true },
                )
            },
            reminder = { _, _ -> },
            leaveRequested = false,
            onLeaveAnswered = {},
            onUnsavedWorkChange = {},
            opensOn = ChecklistPage.Items,
        )
    }

    /** The last day and a day's last exercise cannot be removed, and the line where the remove would act says why; with more, nothing says anything. */
    @Test
    fun theLastDayAndADaysLastExerciseSayWhyTheyStay() {
        showScrolling()
        val onlyBench = testActivityConfiguration().let { seeded ->
            seeded.applyingEdits(
                listOf(ActivityEdit.RemoveRoutineDay("B"), ActivityEdit.RemoveRoutineDay("C")) +
                    seeded.routineDay("A")!!.exercises.drop(1).map { ActivityEdit.RemoveExercise(it.id) },
                today,
            )
        }
        val whole = ActivitySettingsDraft(testActivityConfiguration(), today)
        activities(whole, ActivitiesLevel.Day("gym", "A"))
        compose.onNodeWithTag("${tag}_day_A_keep_day").assertDoesNotExist()
        compose.onNodeWithTag("${tag}_day_A_keep").assertDoesNotExist()
        compose.onNodeWithTag("${tag}_day_A_remove").assertIsEnabled()
        compose.onNodeWithTag("${tag}_exercise_bench_press_remove").assertIsEnabled()

        val draft = ActivitySettingsDraft(onlyBench, today)
        activities(draft, ActivitiesLevel.Day("gym", "A"))
        compose.onNodeWithTag("${tag}_day_A_keep_day").assertTextEquals("A routine keeps at least one day.")
        compose.onNodeWithTag("${tag}_day_A_remove").assertIsNotEnabled()
        compose.onNodeWithTag("${tag}_exercise_bench_press_remove").assertIsNotEnabled()
        compose.onNodeWithTag("${tag}_day_A_keep").assertTextEquals("A day keeps at least one exercise.")
    }

    /**
     * A day added opens on its page, where its exercise waits for a name; removing a day goes back
     * to its activity, and removing an activity to the list. A level naming what is gone shows the
     * list.
     */
    @Test
    fun aDayAddedOpensOnItsPageAndARemovalGoesUpALevel() {
        showScrolling()
        val draft = ActivitySettingsDraft(testActivityConfiguration(), today)
        activities(draft, ActivitiesLevel.Activity("gym"))
        click("${tag}_activity_gym_add_day")
        val added = draft.edited.activity("gym")!!.routine!!.retainedDays.last()
        assertEquals(ActivitiesLevel.Day("gym", added.id), level)
        assertEquals("Day D", input("${tag}_day_${added.id}_name"))
        // A new exercise is not scolded for a name nobody has typed yet; Save still waits for one.
        assertNoError(nameFieldOf(added.exercises.single().id))
        assertTrue(draft.heldBack)

        click("${tag}_day_${added.id}_remove")
        click("${tag}_day_${added.id}_remove")
        assertEquals(ActivitiesLevel.Activity("gym"), level)
        compose.onNodeWithTag("${tag}_activity_gym_day_${added.id}").assertDoesNotExist()
        assertFalse(draft.heldBack)

        click("${tag}_activity_gym_remove")
        click("${tag}_activity_gym_remove")
        assertEquals(ActivitiesLevel.List, level)
        compose.onNodeWithTag("${tag}_activity_gym").assertDoesNotExist()
        level = ActivitiesLevel.Day("gym", "A")
        compose.waitForIdle()
        compose.onNodeWithTag("${tag}_activity_bouldering").assertExists()
    }

    private fun nameFieldOf(exerciseId: String) = "${tag}_exercise_${exerciseId}_name"

    /** Removed, the routine's days leave the page and what their fields held goes with them, so Save is no longer held back; added again, the activity gets a first day, on its page. */
    @Test
    fun removingTheRoutineHidesItsDaysAndDropsWhatTheirFieldsHeld() {
        showScrolling()
        val draft = ActivitySettingsDraft(testActivityConfiguration(), today)
        activities(draft, ActivitiesLevel.Day("gym", "A"))
        click("${tag}_exercise_bench_press_plan")
        type("${tag}_exercise_bench_press_step", "no")
        assertTrue(draft.heldBack)
        back()
        click("${tag}_activity_gym_remove_routine")
        click("${tag}_activity_gym_remove_routine")
        compose.onNodeWithTag("${tag}_activity_gym_day_A").assertDoesNotExist()
        assertFalse(draft.edited.activity("gym")!!.hasRoutine)
        assertTrue(draft.edited.activity("gym")!!.routine!!.days.all { it.removed })
        assertFalse(draft.heldBack)
        click("${tag}_activity_gym_add_routine")
        val added = draft.edited.activity("gym")!!.routine!!.retainedDays.single()
        assertEquals(ActivitiesLevel.Day("gym", added.id), level)
        assertEquals(listOf("A", "B", "C"), draft.edited.activity("gym")!!.routine!!.days.filter { it.removed }.map { it.id })
        assertEquals("Day D", added.label)
        // Its exercise waits for a name, which the day's row says until it has one.
        back()
        compose.onNodeWithTag("${tag}_activity_gym_day_${added.id}").assertTextContains("Something to fix")
        click("${tag}_activity_gym_day_${added.id}")
        type(nameFieldOf(added.exercises.single().id), "Squat")
        back()
        compose.onNodeWithTag("${tag}_activity_gym_day_${added.id}").assertTextContains("1 exercise · 3 sets")
        back()
        compose.onNodeWithTag("${tag}_activity_gym").assertTextContains("Routine · 1 day")
    }

    /**
     * Under the list, "Log how long it took" is off until it is switched on and says what it adds;
     * it shows what the stored configuration holds, and no activity's page has it, since it is
     * about every activity alike.
     */
    @Test
    fun loggingHowLongItTookIsOffUntilSwitchedOnAndShowsWhatIsStored() {
        showScrolling()
        val draft = ActivitySettingsDraft(testActivityConfiguration(), today)
        activities(draft)
        compose.onNodeWithTag("${tag}_logs_duration").assertIsOff().assertContentDescriptionEquals("Log how long it took")
        compose.onNodeWithTag("${tag}_logs_duration_note").assertTextEquals("Adds a field for the minutes when you log an activity.")
        assertTrue(top("${tag}_new_activity") < top("${tag}_logs_duration"))
        assertFalse(draft.canSave)

        click("${tag}_logs_duration")
        compose.onNodeWithTag("${tag}_logs_duration").assertIsOn()
        assertEquals(listOf(ActivityEdit.SetLogsDuration(true)), draft.edits)
        assertTrue(draft.edited.logsDuration)
        assertTrue(draft.canSave)

        // Stored, a page opened afresh shows it on, and switching it off is a change of its own.
        val stored = ActivitySettingsDraft(draft.edited, today)
        activities(stored)
        compose.onNodeWithTag("${tag}_logs_duration").assertIsOn()
        assertFalse(stored.canSave)
        click("${tag}_logs_duration")
        compose.onNodeWithTag("${tag}_logs_duration").assertIsOff()
        assertFalse(stored.edited.logsDuration)
        assertTrue(stored.canSave)

        activities(stored, ActivitiesLevel.Activity("bouldering"))
        compose.onNodeWithTag("${tag}_logs_duration").assertDoesNotExist()
    }

    /**
     * "Counts toward the streak" stands first on the Streak part, above the steps. Switched off,
     * Activity leaves the streak from today: its steps and its bank are out of sight and the
     * holiday rule stays, since its reminder reads it too; switched on again, it joins.
     */
    @Test
    fun theStreakPartStartsWithTheSwitchAndShowsTheBankOnlyWhileActivityCounts() {
        showScrolling()
        val draft = ActivitySettingsDraft(testActivityConfiguration(), today)
        show(draft) { view -> ActivityStreak(view = view, draft = draft, testTag = tag) }
        compose.onNodeWithTag("${tag}_rewards").assertIsOn()
        assertTrue(top("${tag}_rewards") < top("${tag}_step_1_days"))

        // Out of the streak from today: the steps and the bank are out of sight, the holiday rule stays.
        click("${tag}_rewards")
        compose.onNodeWithTag("${tag}_rewards").assertIsOff()
        compose.onNodeWithTag("${tag}_step_1_days").assertDoesNotExist()
        compose.onNodeWithTag("${tag}_max_passes_value").assertDoesNotExist()
        compose.onNodeWithTag("${tag}_away_due").assertExists()
        assertEquals(listOf(ActivityEdit.LeaveRewards), draft.edits)

        click("${tag}_rewards")
        compose.onNodeWithTag("${tag}_rewards").assertIsOn()
        compose.onNodeWithTag("${tag}_max_passes_value").assertExists()
        assertEquals(listOf(ActivityEdit.LeaveRewards, ActivityEdit.JoinRewards), draft.edits)
    }

    /**
     * The Streak part lists what a week earns in days, above the bank and the holiday rule: a step
     * at the week's days, each further one a day more with tokens of its own, as many as the days
     * leave room for; the total under them, and the line under the week's days, follow every
     * change, and removing a step moves the ones after it down a day.
     */
    @Test
    fun theStreakPartListsTheStepsInDaysAboveTheBankAndTheHolidayRule() {
        showScrolling()
        val draft = ActivitySettingsDraft(testActivityConfiguration(), today)
        show(draft) { view -> ActivityStreak(view = view, draft = draft, testTag = tag) }
        compose.onNodeWithText("At your days").assertExists()
        compose.onNodeWithTag("${tag}_step_1_days").assertTextEquals("5 days")
        compose.onNodeWithTag("${tag}_step_1_tokens_value").assertTextEquals("+1")
        compose.onNodeWithTag("${tag}_step_1_tokens_less").assertContentDescriptionEquals("Tokens at your days: less")
        compose.onNodeWithTag("${tag}_step_1_remove").assertDoesNotExist()
        compose.onNodeWithTag("${tag}_steps_total").assertTextEquals("A week with 5 active days or more earns a token.")
        assertTrue(top("${tag}_add_step") < top("${tag}_max_passes_value") && top("${tag}_max_passes_value") < top("${tag}_away_due"))

        click("${tag}_add_step")
        click("${tag}_step_2_tokens_more")
        click("${tag}_add_step")
        compose.onNodeWithTag("${tag}_add_step").assertIsNotEnabled()
        compose.onNodeWithText("1 day more").assertExists()
        compose.onNodeWithTag("${tag}_step_3_days").assertTextEquals("7 days")
        compose.onNodeWithTag("${tag}_steps_total").assertTextEquals("A week with 7 active days or more earns 4 tokens.")
        assertEquals(ActivityTokenSteps(listOf(ActivityTokenStep(0, 1), ActivityTokenStep(1, 2), ActivityTokenStep(2, 1))), draft.edited.rewards.tokenSteps.on(today))

        click("${tag}_step_2_remove")
        click("${tag}_step_2_remove")
        compose.onNodeWithTag("${tag}_step_3").assertDoesNotExist()
        compose.onNodeWithTag("${tag}_step_2_days").assertTextEquals("6 days")
        compose.onNodeWithTag("${tag}_step_2_tokens_value").assertTextEquals("+1")
        compose.onNodeWithTag("${tag}_steps_total").assertTextEquals("A week with 6 active days or more earns 2 tokens.")

        show(draft) { view -> ActivityWeek(view = view, draft = draft, testTag = tag) {} }
        compose.onNodeWithTag("${tag}_week_note").assertTextEquals("5 active days in a week earn a token. The other 2 days are rest days. More days earn up to 2 tokens a week.")
    }

    /** Fewer days stands beside still due and not due while the week asks more than one day, with the count's slot kept while another answer is chosen. */
    @Test
    fun theStreakPartOffersFewerDaysWithItsCountAboveOneDayAWeek() {
        showScrolling()
        val draft = ActivitySettingsDraft(testActivityConfiguration(), today)
        show(draft) { view -> ActivityStreak(view = view, draft = draft, testTag = tag) }
        compose.onNodeWithTag("${tag}_max_passes_value").assertTextEquals("3")
        compose.onNodeWithTag("${tag}_away_lowerTarget").assertIsSelected().assertContentDescriptionEquals("Fewer days")
        compose.onNodeWithTag("${tag}_away_days_2").assertIsSelected()
        compose.onNodeWithTag("${tag}_away_days_5").assertDoesNotExist()
        click("${tag}_away_days_3")
        assertEquals(3, draft.edited.awayDays.on(today))
        click("${tag}_away_due")
        compose.onNodeWithTag("${tag}_away_days_3").assertDoesNotExist()
        assertEquals(WhenAway.Due, draft.edited.whenAway.on(today))
        click("${tag}_away_lowerTarget")
        compose.onNodeWithTag("${tag}_away_days_3").assertIsSelected()

        val oneDay = ActivitySettingsDraft(testActivityConfiguration().applyingEdits(listOf(ActivityEdit.SetWeek(WeekPlan.of(DayOfWeek.MONDAY))), today.minusWeeks(1)), today)
        show(oneDay) { view -> ActivityStreak(view = view, draft = oneDay, testTag = tag) }
        compose.onNodeWithTag("${tag}_away_due").assertIsSelected()
        compose.onNodeWithTag("${tag}_away_lowerTarget").assertDoesNotExist()
    }
}

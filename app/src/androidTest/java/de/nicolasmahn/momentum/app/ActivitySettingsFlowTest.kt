package de.nicolasmahn.momentum.app

import de.nicolasmahn.momentum.domain.activity.ActivityEntry
import de.nicolasmahn.momentum.domain.checklist.RewardPeriod
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.espresso.Espresso
import de.nicolasmahn.momentum.R
import de.nicolasmahn.momentum.core.time.currentDateKey
import de.nicolasmahn.momentum.domain.activity.applyingActivityEdits
import de.nicolasmahn.momentum.feature.settings.ViewWrite
import de.nicolasmahn.momentum.core.ui.theme.WorkoutTrackerTheme
import de.nicolasmahn.momentum.domain.AppState
import de.nicolasmahn.momentum.domain.activity.ActivityTokenStep
import de.nicolasmahn.momentum.domain.activity.ActivityTokenSteps
import de.nicolasmahn.momentum.domain.activity.OtherActivityIcon
import de.nicolasmahn.momentum.domain.activity.SeededActivityRules
import de.nicolasmahn.momentum.domain.activity.activityRules
import de.nicolasmahn.momentum.domain.activity.routine.LoggedSet
import de.nicolasmahn.momentum.domain.checklist.ViewIcon
import de.nicolasmahn.momentum.domain.checklist.WhenAway
import de.nicolasmahn.momentum.domain.reminder.ReminderSettings
import de.nicolasmahn.momentum.domain.reminder.ReminderTime
import de.nicolasmahn.momentum.domain.reminder.WorkoutReminderSettings
import de.nicolasmahn.momentum.testing.appString
import de.nicolasmahn.momentum.testing.assertInputEquals
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Activity's settings on the page every view has, over its stored configuration: Look,
 * Activities with a page for each activity and each routine day, Week and Streak, saved by one
 * press over two stores. The stored configuration and the stored reminder settings are the proof,
 * and the Activity screen shows what a change reaches: its tiles, Other, a routine and a workout
 * on the edited plan.
 */
class ActivitySettingsFlowTest {
    @get:Rule
    val composeRule = createComposeRule()

    private var state by mutableStateOf(AppState(activity = SeededActivityRules))
    private var reminders by mutableStateOf(ReminderSettings(workout = WorkoutReminderSettings(enabled = true, fixedTime = ReminderTime(19, 30))))

    private val activity get() = state.activityRules
    private val today: LocalDate get() = LocalDate.parse(currentDateKey())

    private fun setApp() {
        composeRule.setContent { App() }
    }

    @Composable
    private fun App() {
        WorkoutTrackerTheme {
            MomentumApp(
                state = state,
                onStateChange = { state = it; true },
                onActivitySave = { edits -> state = state.applyingActivityEdits(edits, today); ViewWrite.Stored },
                reminderSettings = reminders,
                onReminderSettingsChange = { reminders = it; true },
            )
        }
    }

    /** From the Settings list, which every Save returns to. */
    private fun openActivitysPage() {
        composeRule.onNodeWithTag("settings_group_activity").performScrollTo().performClick()
        composeRule.onNodeWithTag("feature_settings").assertIsDisplayed()
    }

    private fun openPart(part: String) {
        composeRule.onNodeWithTag("feature_settings_tab_$part").performScrollTo().performClick()
        composeRule.onNodeWithTag("feature_settings_tab_$part").assertIsSelected()
    }

    private fun tap(tag: String) {
        composeRule.onNodeWithTag(tag).performScrollTo().performClick()
    }

    private fun type(tag: String, text: String) {
        composeRule.onNodeWithTag(tag).performScrollTo().performTextReplacement(text)
        Espresso.closeSoftKeyboard()
    }

    /** The two-step remove: armed by the first tap, done by the second. */
    private fun remove(tag: String) {
        tap(tag)
        tap(tag)
    }

    private fun assertError(tag: String, error: String) = composeRule.onNodeWithTag(tag).assert(SemanticsMatcher.expectValue(SemanticsProperties.Error, error))

    /** The tag of the name field whose row the page gave an ID the test cannot know, found by what the field holds. */
    private fun nameFieldTag(prefix: String, holding: String): String {
        val tag = composeRule.onNode(
            SemanticsMatcher("a $prefix name field holding '$holding'") { node ->
                val tag = node.config.getOrNull(SemanticsProperties.TestTag)
                tag != null && tag.startsWith(prefix) && tag.endsWith("_name") && node.config.getOrNull(SemanticsProperties.EditableText)?.text == holding
            },
        ).fetchSemanticsNode().config[SemanticsProperties.TestTag]
        return tag
    }

    /**
     * The page has Look as it is stored, without Mo's flavor; Activities, a list of the four
     * activities with the gym's routine named on its row, whose rows open an activity's page and
     * a day's; Week with the week's days, the line saying what they earn, what is planned on each,
     * and the workout reminder's switch, its time and the week's days in its timing line; and
     * Streak with what a week earns in days, the bank's size and the holiday rule, fewer days
     * with its count, without a switch for the streak. Its Delete cannot be pressed.
     */
    @Test
    fun activitysPageHasItsFourPartsAndADisabledDelete() {
        setApp()
        composeRule.onNodeWithTag("home_settings").performClick()
        openActivitysPage()

        composeRule.onNodeWithTag("feature_settings_tab_look").assertIsSelected()
        composeRule.onNodeWithTag("feature_settings_tab_items").assertDoesNotExist()
        composeRule.onNodeWithTag("feature_settings_tab_reminders").assertDoesNotExist()
        composeRule.onNodeWithTag("feature_settings_name").performScrollTo().assertInputEquals("Activity")
        composeRule.onNodeWithTag("feature_settings_icon_hiking").performScrollTo().assertIsSelected()
        composeRule.onNodeWithTag("feature_settings_hue_green").performScrollTo().assertIsSelected()
        composeRule.onNodeWithTag("feature_settings_motif_none").assertDoesNotExist()

        openPart("activities")
        listOf("gym" to "Gym", "bouldering" to "Bouldering", "cycling" to "Cycling", "jogging" to "Jogging").forEach { (id, label) ->
            composeRule.onNodeWithTag("feature_settings_activity_$id").performScrollTo().assertTextContains(label)
        }
        composeRule.onNodeWithTag("feature_settings_activity_gym").assertTextContains("Routine · 3 days")
        composeRule.onNodeWithTag("feature_settings_activity_gym_up").assertIsNotEnabled()
        composeRule.onNodeWithTag("feature_settings_new_activity").performScrollTo().assertIsDisplayed()
        tap("feature_settings_activity_gym")
        composeRule.onNodeWithTag("feature_settings_activity_gym_name").performScrollTo().assertInputEquals("Gym")
        composeRule.onNodeWithTag("feature_settings_activity_gym_day_A").performScrollTo().assertTextContains("5 exercises · 14 sets")
        composeRule.onNodeWithTag("feature_settings_activity_gym_add_day").performScrollTo().assertIsEnabled()
        tap("feature_settings_activity_gym_day_A")
        composeRule.onNodeWithTag("feature_settings_day_A_name").performScrollTo().assertInputEquals("Day A")
        composeRule.onNodeWithTag("feature_settings_up").assertContentDescriptionEquals("Back to Gym")

        openPart("week")
        composeRule.onNodeWithTag("feature_settings_week_day_5").performScrollTo().assertIsSelected()
        composeRule.onNodeWithTag("feature_settings_week_day_6").assertIsNotSelected()
        composeRule.onNodeWithTag("feature_settings_week_note").assertTextEquals("5 active days in a week earn a token. The other 2 days are rest days.")
        composeRule.onNodeWithTag("feature_settings_week_plan_1").performScrollTo().assertTextContains("Monday").assertTextContains("Gym")
        composeRule.onNodeWithTag("feature_settings_week_plan_4").performScrollTo().assertTextContains("Any activity")
        composeRule.onNodeWithTag("settings_workout_reminder").performScrollTo().assertIsOn()
        composeRule.onNodeWithTag("settings_workout_timing").assertTextEquals("Mon, Tue, Wed, Thu, Fri at 19:30")
        composeRule.onNodeWithTag("settings_workout_time").assertTextEquals("19:30")
        composeRule.onNodeWithTag("settings_workout_day_1").assertDoesNotExist()

        openPart("streak")
        composeRule.onNodeWithTag("feature_settings_rewards").performScrollTo().assertIsOn()
        composeRule.onNodeWithTag("feature_settings_step_1_days").performScrollTo().assertTextEquals("5 days")
        composeRule.onNodeWithTag("feature_settings_steps_total").assertTextEquals("A week with 5 active days or more earns a token.")
        composeRule.onNodeWithTag("feature_settings_max_passes_value").performScrollTo().assertTextEquals("3")
        composeRule.onNodeWithTag("feature_settings_away_lowerTarget").performScrollTo().assertIsSelected().assertContentDescriptionEquals("Fewer days")
        composeRule.onNodeWithTag("feature_settings_away_days_2").performScrollTo().assertIsSelected()
        composeRule.onNodeWithTag("feature_settings_away_days_5").assertDoesNotExist()

        // Activity is built in, so its page offers no Delete; it is switched off in the Settings list.
        composeRule.onNodeWithTag("feature_settings_delete").assertDoesNotExist()
        composeRule.onNodeWithTag("feature_settings_save").assertIsNotEnabled()
        composeRule.onNodeWithTag("top_back").performClick()
        composeRule.onNodeWithTag("feature_settings_discard_dialog").assertDoesNotExist()
        composeRule.onNodeWithTag("feature_settings").assertDoesNotExist()
        composeRule.runOnIdle { assertEquals(SeededActivityRules, activity) }
    }

    /**
     * Saturday in and Monday out of the week, Thursday planned for Bouldering and Saturday for
     * Cycling: the timing line follows before anything is stored, and one Save stores the week
     * dated from today. The reminder is off and has no days of its own, so the Save asks the phone
     * for nothing and the stored settings stay as they were.
     */
    @Test
    fun theWeeksDaysAndPlansAreStoredOnSaveAndTheReminderFollows() {
        reminders = ReminderSettings(workout = WorkoutReminderSettings(enabled = false, fixedTime = ReminderTime(19, 30)))
        setApp()
        composeRule.onNodeWithTag("home_settings").performClick()
        openActivitysPage()
        openPart("week")
        tap("feature_settings_week_day_6")
        tap("feature_settings_week_day_1")
        composeRule.onNodeWithTag("feature_settings_week_plan_1").assertDoesNotExist()
        tap("feature_settings_week_plan_4")
        composeRule.onNodeWithTag("feature_settings_week_plan_4_bouldering").performClick()
        tap("feature_settings_week_plan_6")
        composeRule.onNodeWithTag("feature_settings_week_plan_6_cycling").performClick()
        composeRule.onNodeWithTag("feature_settings_week_plan_6").assertTextContains("Cycling")
        composeRule.onNodeWithTag("settings_workout_timing").performScrollTo().assertTextEquals("Tue, Wed, Thu, Fri, Sat at 19:30")
        composeRule.runOnIdle { assertEquals(SeededActivityRules, activity) }

        composeRule.onNodeWithTag("feature_settings_save").performClick()
        composeRule.runOnIdle {
            assertEquals(today, activity.week.revisions.last().from)
            assertEquals(
                listOf(DayOfWeek.TUESDAY to "bouldering", DayOfWeek.WEDNESDAY to "gym", DayOfWeek.THURSDAY to "bouldering", DayOfWeek.FRIDAY to "gym", DayOfWeek.SATURDAY to "cycling"),
                activity.week.current.days.map { it.day to it.activityId },
            )
            assertEquals(WorkoutReminderSettings(enabled = false, fixedTime = ReminderTime(19, 30)), reminders.workout)
        }
        composeRule.onNodeWithTag("feature_settings").assertDoesNotExist()
        composeRule.onNodeWithTag("settings_group_activity").performScrollTo().assertIsDisplayed()
    }

    /**
     * An activity added under the list, one renamed on its page, the new one moved up on the list
     * and one removed on its page are stored on Save: by ID, with the removed one kept and marked.
     * The screen's tiles follow the order, and the renamed one stands under Other.
     */
    @Test
    fun theActivitiesAreStoredOnSaveAndTheTilesFollow() {
        setApp()
        composeRule.onNodeWithTag("home_settings").performClick()
        openActivitysPage()
        openPart("activities")

        tap("feature_settings_new_activity")
        type("feature_settings_add_activity", "Yoga")
        tap("feature_settings_new_icon_meditation")
        // Add is the page's own while it makes an activity, in the bar where Save stands.
        composeRule.onNodeWithTag("feature_settings_new_activity_next").performClick()
        // Added, Yoga opens on its own page; the list is one level up.
        Espresso.pressBack()
        val yogaRow = composeRule.onNode(SemanticsMatcher("Yoga's row") { node ->
            node.config.getOrNull(SemanticsProperties.TestTag)?.let { it.startsWith("feature_settings_activity_") && !it.endsWith("_up") } == true &&
                node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == "Yoga" } == true
        }).fetchSemanticsNode().config[SemanticsProperties.TestTag]
        tap("feature_settings_activity_jogging")
        type("feature_settings_activity_jogging_name", "Running")
        Espresso.pressBack()
        tap("${yogaRow}_up")
        tap("feature_settings_activity_cycling")
        remove("feature_settings_activity_cycling_remove")
        composeRule.onNodeWithTag("feature_settings_activity_cycling").assertDoesNotExist()
        composeRule.runOnIdle { assertEquals(SeededActivityRules, activity) }

        composeRule.onNodeWithTag("feature_settings_save").performClick()
        composeRule.runOnIdle {
            val yoga = requireNotNull(activity.activityNamed("Yoga"))
            assertEquals(listOf("gym", "bouldering", "cycling", yoga.id, "jogging"), activity.activities.map { it.id })
            assertEquals(listOf(false, false, true, false, false), activity.activities.map { it.removed })
            assertEquals(ViewIcon.Meditation, yoga.icon)
            assertEquals("Running", activity.activity("jogging")?.label)
        }
        composeRule.onNodeWithTag("feature_settings").assertDoesNotExist()

        composeRule.onNodeWithTag("top_back").performClick()
        composeRule.onNodeWithTag("home_log_activity").performClick()
        val yoga = requireNotNull(activity.activityNamed("Yoga")).id
        composeRule.onNodeWithTag("activity_choice_gym").assertIsDisplayed()
        composeRule.onNodeWithTag("activity_choice_bouldering").assertIsDisplayed()
        composeRule.onNodeWithTag("activity_choice_$yoga").assertIsDisplayed()
        composeRule.onNodeWithTag("activity_choice_custom").assertIsDisplayed()
        composeRule.onNodeWithTag("activity_choice_jogging").assertDoesNotExist()
        composeRule.onNodeWithTag("activity_choice_cycling").assertDoesNotExist()
        // The overflow picker belongs to Custom; choosing a listed activity keeps its own name.
        composeRule.onNodeWithTag("activity_choice_bouldering").performClick()
        composeRule.onNodeWithTag("activity_other_jogging").assertDoesNotExist()
        composeRule.onNodeWithTag("activity_choice_custom").performClick()
        composeRule.onNodeWithTag("activity_other_jogging").performScrollTo()
            .assertTextEquals("Running").performClick()
        composeRule.onNodeWithTag("activity_name").performScrollTo().assertInputEquals("Running")
        composeRule.onNodeWithTag("activity_other_cycling").assertDoesNotExist()
    }

    /**
     * Day A renamed on its page, the bench press given a fourth set and a range up to twelve, an
     * exercise added to it, and a day added on the gym's page, which opens on the new day, with
     * its one exercise, each named before Save lets go. On the screen the picker names the renamed
     * day and the new one, and a session on the renamed day records the new exercise from its
     * starting set.
     */
    @Test
    fun aRoutineDayAndAnExerciseAreAddedAndChangedAndAWorkoutRunsOnTheEditedPlan() {
        setApp()
        composeRule.onNodeWithTag("home_settings").performClick()
        openActivitysPage()
        openPart("activities")

        tap("feature_settings_activity_gym")
        tap("feature_settings_activity_gym_day_A")
        type("feature_settings_day_A_name", "Push")
        tap("feature_settings_exercise_bench_press_plan")
        tap("feature_settings_exercise_bench_press_sets_more")
        type("feature_settings_exercise_bench_press_reps_to", "12")
        composeRule.onNodeWithTag("feature_settings_exercise_bench_press_plan").performScrollTo().assertTextContains("4 sets · 8–12 reps · ±2.5 kg")
        tap("feature_settings_day_A_add_exercise")
        // The new exercise waits for its name, and so does Save.
        composeRule.onNodeWithTag("feature_settings_save").assertIsNotEnabled()
        type(nameFieldTag("feature_settings_exercise_", holding = ""), "Squat")
        composeRule.onNodeWithTag("feature_settings_save").assertIsEnabled()
        Espresso.pressBack()
        composeRule.onNodeWithTag("feature_settings_activity_gym_day_A").performScrollTo().assertTextContains("Push").assertTextContains("6 exercises · 18 sets")
        tap("feature_settings_activity_gym_add_day")
        composeRule.onNodeWithTag("feature_settings_up").assertContentDescriptionEquals("Back to Gym")
        composeRule.onNodeWithTag("feature_settings_save").assertIsNotEnabled()
        type(nameFieldTag("feature_settings_exercise_", holding = ""), "Deadlift")
        composeRule.onNodeWithTag("feature_settings_save").assertIsEnabled()
        composeRule.runOnIdle { assertEquals(SeededActivityRules, activity) }

        composeRule.onNodeWithTag("feature_settings_save").performClick()
        composeRule.runOnIdle {
            val push = requireNotNull(activity.routineDay("A"))
            assertEquals("Push", push.label)
            val bench = push.exercises.first()
            assertEquals("bench_press" to 4, bench.id to bench.sets)
            assertEquals(8..12, bench.reps)
            val squat = push.retainedExercises.last()
            assertEquals("Squat", squat.label)
            assertEquals(Triple(3, 8..10, 20.0), Triple(squat.sets, squat.reps, squat.weight?.startKg))
            val days = activity.activity("gym")!!.routine!!.days
            val added = days.last()
            assertEquals(listOf("A", "B", "C", added.id), days.map { it.id })
            assertEquals("Day D", added.label)
            assertEquals("Deadlift", added.exercises.single().label)
        }
        composeRule.onNodeWithTag("feature_settings").assertDoesNotExist()

        val squatId = activity.routineDay("A")!!.retainedExercises.last().id
        val addedDayId = activity.activity("gym")!!.routine!!.days.last().id
        composeRule.onNodeWithTag("top_back").performClick()
        composeRule.onNodeWithTag("home_log_activity").performClick()
        composeRule.onNodeWithTag("activity_choice_gym").performClick()
        composeRule.onNode(hasTestTag("gym_plan_a") and hasAnyDescendant(hasText("Push")), useUnmergedTree = true).performScrollTo().assertExists()
        composeRule.onNodeWithTag("gym_plan_${addedDayId.lowercase(Locale.ROOT)}").performScrollTo().assertIsDisplayed()
        tap("gym_plan_a")
        composeRule.onNodeWithText("Squat").performScrollTo().assertIsDisplayed()
        val reps = composeRule.onNodeWithTag("workout_${squatId}_reps_0")
        reps.performScrollTo()
        reps.performTextReplacement("10")
        reps.performImeAction()
        composeRule.runOnIdle {
            val session = state.sessions.single()
            assertEquals("gym" to "A", session.activityId to session.day)
            val log = session.exerciseLogs.first { it.exerciseId == squatId }
            assertEquals("Squat", log.exerciseName)
            assertEquals(listOf(LoggedSet(weightKg = 20.0, reps = 10.0)), log.sets)
        }
        tap("finish_workout")
        composeRule.onNodeWithTag("gym_plan_a").assertExists()
        composeRule.runOnIdle { assertEquals(1, state.sessions.single().totalLoggedSets) }
    }

    /**
     * What a field holds is the page's, not the field's: "Swim" typed under "New activity", "Gym"
     * typed into Bouldering's name, which another activity has, and "Push" typed into Day A's
     * name each come back as they were left after the system's Back went up two levels, and
     * after the activity was recreated on the day's page.
     */
    @Test
    fun whatTheFieldsHoldSurvivesGoingUpTwiceAndRecreation() {
        val restoration = StateRestorationTester(composeRule)
        restoration.setContent { App() }
        composeRule.onNodeWithTag("home_settings").performClick()
        openActivitysPage()
        openPart("activities")
        tap("feature_settings_new_activity")
        type("feature_settings_add_activity", "Swim")
        Espresso.pressBack()
        tap("feature_settings_activity_bouldering")
        type("feature_settings_activity_bouldering_name", "Gym")
        assertError("feature_settings_activity_bouldering_name", "Already in use")
        Espresso.pressBack()
        tap("feature_settings_activity_gym")
        tap("feature_settings_activity_gym_day_A")
        type("feature_settings_day_A_name", "Push")

        Espresso.pressBack()
        Espresso.pressBack()
        composeRule.onNodeWithTag("feature_settings").assertIsDisplayed()
        tap("feature_settings_new_activity")
        composeRule.onNodeWithTag("feature_settings_add_activity").performScrollTo().assertInputEquals("Swim")
        Espresso.pressBack()
        composeRule.onNodeWithTag("feature_settings_activity_bouldering").performScrollTo().assertTextContains("Something to fix")
        composeRule.onNodeWithTag("feature_settings_save").assertIsNotEnabled()

        tap("feature_settings_activity_gym")
        tap("feature_settings_activity_gym_day_A")
        restoration.emulateSavedInstanceStateRestore()
        composeRule.onNodeWithTag("feature_settings_day_A_name").performScrollTo().assertInputEquals("Push")
        Espresso.pressBack()
        composeRule.onNodeWithTag("feature_settings_activity_gym_day_A").performScrollTo().assertTextContains("Push")
        Espresso.pressBack()
        tap("feature_settings_new_activity")
        composeRule.onNodeWithTag("feature_settings_add_activity").performScrollTo().assertInputEquals("Swim")
        Espresso.pressBack()
        tap("feature_settings_activity_bouldering")
        composeRule.onNodeWithTag("feature_settings_activity_bouldering_name").performScrollTo().assertInputEquals("Gym")
        assertError("feature_settings_activity_bouldering_name", "Already in use")
        composeRule.runOnIdle { assertEquals(SeededActivityRules, activity) }
    }

    /**
     * Jogging, under Other on the screen, gets a routine on its page: the day it adds opens, its
     * exercise is named and done without a weight, and Save stores it. Under Other, Jogging picked
     * shows its routine, and the exercise asks for reps alone. Back on its page the routine is
     * removed and Save stores that, keeping the day for its sessions.
     */
    @Test
    fun aRoutineIsAddedToAnActivityUnderOtherAndRemoved() {
        setApp()
        composeRule.onNodeWithTag("home_settings").performClick()
        openActivitysPage()
        openPart("activities")
        tap("feature_settings_activity_jogging")
        tap("feature_settings_activity_jogging_add_routine")
        val exercise = nameFieldTag("feature_settings_exercise_", holding = "")
        type(exercise, "Strides")
        val plan = exercise.removeSuffix("_name")
        tap("${plan}_plan")
        composeRule.onNodeWithTag("${plan}_weight").performScrollTo().assertIsOn()
        tap("${plan}_weight")
        composeRule.onNodeWithTag("${plan}_weight").assertIsOff()
        composeRule.onNodeWithTag("${plan}_start_weight").assertDoesNotExist()
        composeRule.onNodeWithTag("${plan}_plan").performScrollTo().assertTextContains("3 sets · 8–10 reps")
        composeRule.onNodeWithTag("feature_settings_save").performClick()
        val day = composeRule.runOnIdle {
            val routine = requireNotNull(activity.activity("jogging")?.routine)
            val day = routine.retainedDays.single()
            assertEquals("Day A", day.label)
            assertNull(day.exercises.single().weight)
            day
        }

        composeRule.onNodeWithTag("top_back").performClick()
        composeRule.onNodeWithTag("home_log_activity").performClick()
        tap("activity_choice_custom")
        tap("activity_other_jogging")
        tap("gym_plan_${day.id.lowercase(Locale.ROOT)}")
        val strides = day.exercises.single().id
        composeRule.onNodeWithTag("workout_${strides}_reps_0").performScrollTo().assertExists()
        composeRule.onNodeWithTag("workout_${strides}_weight_0").assertDoesNotExist()

        // The arrow closes the open day first, as the system's Back does, then leaves the screen.
        composeRule.onNodeWithTag("top_back").performClick()
        composeRule.onNodeWithTag("top_back").performClick()
        composeRule.onNodeWithTag("home_settings").performClick()
        openActivitysPage()
        openPart("activities")
        tap("feature_settings_activity_jogging")
        remove("feature_settings_activity_jogging_remove_routine")
        composeRule.onNodeWithTag("feature_settings_activity_jogging_add_routine").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("feature_settings_save").performClick()
        composeRule.runOnIdle {
            val jogging = requireNotNull(activity.activity("jogging"))
            assertFalse(jogging.hasRoutine)
            assertTrue(jogging.routine!!.days.single().removed)
        }
    }

    /**
     * The Streak part's steps in days, the holiday rule and the bank's size are stored by one
     * Save: a step a day above the week's five with two tokens of its own, not due on holiday,
     * and a smaller bank, each dated from today.
     */
    @Test
    fun theStepsTheHolidayRuleAndTheBankAreStoredOnSave() {
        setApp()
        composeRule.onNodeWithTag("home_settings").performClick()
        openActivitysPage()
        openPart("streak")
        tap("feature_settings_add_step")
        composeRule.onNodeWithTag("feature_settings_step_2_days").performScrollTo().assertTextEquals("6 days")
        tap("feature_settings_step_2_tokens_more")
        composeRule.onNodeWithTag("feature_settings_steps_total").assertTextEquals("A week with 6 active days or more earns 3 tokens.")
        tap("feature_settings_away_excused")
        composeRule.onNodeWithTag("feature_settings_away_excused").assertIsSelected()
        // The count's slot stays, so nothing moved; the count itself is neither seen nor pressed.
        composeRule.onNodeWithTag("feature_settings_away_days_2").assertDoesNotExist()
        tap("feature_settings_max_passes_less")
        composeRule.onNodeWithTag("feature_settings_max_passes_value").assertTextEquals("2")
        composeRule.runOnIdle { assertEquals(SeededActivityRules, activity) }

        composeRule.onNodeWithTag("feature_settings_save").performClick()
        composeRule.runOnIdle {
            assertEquals(ActivityTokenSteps(listOf(ActivityTokenStep(0, 1), ActivityTokenStep(1, 2))), activity.rewards.tokenSteps.current)
            assertEquals(listOf(WhenAway.LowerTarget, WhenAway.Excused), activity.whenAway.revisions.map { it.value })
            assertEquals(listOf(3, 2), activity.rewards.maxPasses.revisions.map { it.value })
            assertEquals(today, activity.rewards.tokenSteps.revisions.last().from)
            assertEquals(today, activity.whenAway.revisions.last().from)
            assertEquals(today, activity.rewards.maxPasses.revisions.last().from)
        }
        composeRule.onNodeWithTag("feature_settings").assertDoesNotExist()
    }

    /**
     * Other on the screen: a listed activity, Jogging, is one tap away under the tiles and saves
     * under its own ID; a new name saves under an activity added at the end of the list, with
     * Other's icon, which the page then lists.
     */
    @Test
    fun otherOffersAListedActivityAndTakesANewOne() {
        setApp()
        composeRule.onNodeWithTag("home_log_activity").performClick()
        // Only Other offers the activities without a primary tile.
        composeRule.onNodeWithTag("activity_other_jogging").assertDoesNotExist()
        tap("activity_choice_custom")
        tap("activity_other_jogging")
        composeRule.onNodeWithTag("activity_choice_custom").assertIsSelected()
        composeRule.onNodeWithTag("activity_name").assertInputEquals("Jogging")
        tap("activity_save")
        composeRule.runOnIdle {
            assertEquals("jogging", state.activityEntries.single().activityId)
            assertEquals(SeededActivityRules, activity)
        }

        // The screen opened anew, as for the next entry: Save has stopped celebrating the last one.
        composeRule.onNodeWithTag("top_back").performClick()
        composeRule.onNodeWithTag("home_log_activity").performClick()
        tap("activity_choice_custom")
        type("activity_name", "Yoga")
        tap("activity_save")
        val yoga = composeRule.runOnIdle {
            val yoga = requireNotNull(activity.activityNamed("Yoga"))
            assertEquals(OtherActivityIcon, yoga.icon)
            assertEquals(activity.activities.last(), yoga)
            assertEquals(setOf("jogging", yoga.id), state.activityEntries.map { it.activityId }.toSet())
            yoga
        }

        composeRule.onNodeWithTag("top_back").performClick()
        composeRule.onNodeWithTag("home_settings").performClick()
        openActivitysPage()
        openPart("activities")
        composeRule.onNodeWithTag("feature_settings_activity_${yoga.id}").performScrollTo().assertTextContains("Yoga")
        tap("feature_settings_activity_${yoga.id}")
        composeRule.onNodeWithTag("feature_settings_activity_${yoga.id}_icon").performScrollTo().assertTextEquals("Icon", "Bolt")
    }

    /**
     * Activity leaves the streak from its Streak part: its token steps and bank go out of sight,
     * what a holiday means stays, and saved, it took part until yesterday from its first entry.
     * Switched on again the same day, it takes part on exactly the days it did.
     */
    @Test
    fun activityLeavesTheStreakAndJoinsAgain() {
        val firstEntry = today.minusDays(9)
        state = state.copy(activityEntries = listOf(ActivityEntry("a1", "bouldering", "Bouldering", firstEntry.toString(), 1L)))
        setApp()
        composeRule.onNodeWithTag("home_settings").performClick()
        openActivitysPage()
        openPart("streak")

        tap("feature_settings_rewards")
        composeRule.onNodeWithTag("feature_settings_rewards").assertIsOff()
        composeRule.onNodeWithTag("feature_settings_max_passes_value").assertDoesNotExist()
        composeRule.onNodeWithTag("feature_settings_step_1_days").assertDoesNotExist()
        composeRule.onNodeWithTag("feature_settings_away_lowerTarget").performScrollTo().assertIsSelected()
        composeRule.onNodeWithTag("feature_settings_save").performClick()
        composeRule.runOnIdle { assertEquals(listOf(RewardPeriod(firstEntry, today.minusDays(1))), activity.rewards.periods) }

        openActivitysPage()
        openPart("streak")
        tap("feature_settings_rewards")
        composeRule.onNodeWithTag("feature_settings_max_passes_value").performScrollTo().assertTextEquals("3")
        composeRule.onNodeWithTag("feature_settings_save").performClick()
        composeRule.runOnIdle { assertEquals(listOf(RewardPeriod(firstEntry)), activity.rewards.periods) }
    }
}

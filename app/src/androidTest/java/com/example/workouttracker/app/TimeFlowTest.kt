package com.example.workouttracker.app

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.app.NotificationManager.INTERRUPTION_FILTER_ALL
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.performTextReplacement
import androidx.test.espresso.Espresso
import androidx.test.platform.app.InstrumentationRegistry
import com.example.workouttracker.R
import com.example.workouttracker.TestEatingQuestions
import com.example.workouttracker.TestReadingTime
import com.example.workouttracker.TestWeightMeasurement
import com.example.workouttracker.core.time.currentDateKey
import com.example.workouttracker.core.ui.theme.WorkoutTrackerTheme
import com.example.workouttracker.domain.AppState
import com.example.workouttracker.domain.LoggedViewKind
import com.example.workouttracker.domain.LoggedViewRef
import com.example.workouttracker.domain.checklist.Dated
import com.example.workouttracker.domain.checklist.MoMotif
import com.example.workouttracker.domain.checklist.RewardPeriod
import com.example.workouttracker.domain.checklist.StarterDayCheckpoint
import com.example.workouttracker.domain.checklist.ViewHue
import com.example.workouttracker.domain.checklist.ViewIcon
import com.example.workouttracker.domain.checklist.WhenAway
import com.example.workouttracker.domain.settings.FeatureVisibilitySettings
import com.example.workouttracker.domain.ref
import com.example.workouttracker.domain.time.DefaultTokenSteps
import com.example.workouttracker.domain.time.MadeTimeWhenAway
import com.example.workouttracker.domain.time.TimeConfiguration
import com.example.workouttracker.domain.time.TimeEdit
import com.example.workouttracker.domain.time.TimeLog
import com.example.workouttracker.domain.time.TimeRewards
import com.example.workouttracker.domain.time.TimeSession
import com.example.workouttracker.domain.time.TokenStep
import com.example.workouttracker.domain.time.TokenSteps
import com.example.workouttracker.domain.time.applyingTimeEdits
import com.example.workouttracker.testfixtures.madeTime
import com.example.workouttracker.feature.time.DoNotDisturb
import com.example.workouttracker.feature.time.FocusChange
import com.example.workouttracker.feature.time.TimeSessionNote
import com.example.workouttracker.feature.time.TimeSessionStore
import com.example.workouttracker.feature.time.TimeSessions
import com.example.workouttracker.reminder.ChecklistReminderRequestCode
import com.example.workouttracker.reminder.ReminderNotificationChannel
import com.example.workouttracker.reminder.ReminderViewKind
import com.example.workouttracker.reminder.slotKey
import com.example.workouttracker.testing.appString
import com.example.workouttracker.testing.assertInputEquals
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * A time view a person makes, through the app as a person meets it: made in Settings in four
 * steps, its minutes typed and timed on its own screen beside Reading, changed on its page, hidden
 * and shown again, in the streak with its badge and its row, reminded of, and deleted with its
 * logs. The stored state is the proof, and Reading's logs are read before and after.
 */
class TimeFlowTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val today = currentDateKey()
    private val yesterday = LocalDate.parse(today).minusDays(1).toString()
    private val piano = madeTime("tim_p1an", "Piano")
    private val guitar = madeTime("tim_gu1t", "Guitar", goal = 90)
    private val readings = TimeLog("reading", yesterday, 1L, 25)

    private var state by mutableStateOf(AppState(measurements = listOf(TestWeightMeasurement), questions = listOf(TestEatingQuestions), time = listOf(TestReadingTime), timeLogs = listOf(readings)))
    private var visibility by mutableStateOf(FeatureVisibilitySettings())

    /** What a tap on a notification asks for, as the activity hands it to the app. */
    private var request by mutableStateOf<AppNavigationRequest?>(null)

    /** The clock the timed session reads, moved by the test where minutes have to pass. */
    private var clock = System.currentTimeMillis()

    /** The session owner over memory, on a phone that gave Do Not Disturb access, so no question comes before a start. */
    private val sessions = TimeSessions(
        store = object : TimeSessionStore {
            override var session: TimeSession? = null
                private set

            override fun writeSession(session: TimeSession?): Boolean {
                this.session = session
                return true
            }

            override var focusChange: FocusChange? = null
            override var focusAsked = false
            override var note: TimeSessionNote? = null
        },
        doNotDisturb = object : DoNotDisturb {
            override val hasAccess = true
            override var filter = INTERRUPTION_FILTER_ALL
        },
        now = { clock },
    )

    private fun madeTime(id: String, label: String, goal: Int = 60) = TimeConfiguration(
        id = id,
        label = label,
        icon = ViewIcon.Music,
        hue = ViewHue.Navy,
            goal = Dated.always(goal),
        rewards = null,
        whenAway = MadeTimeWhenAway,
        checkpoint = StarterDayCheckpoint,
    )

    /** The app over [state], which every save, creation and deletion is decided against, with the switches of the Settings list kept in [visibility]. */
    private fun setApp() {
        composeRule.setContent { WorkoutTrackerTheme { App() } }
    }

    @Composable
    private fun App() {
        MomentumApp(
            state = state,
            onStateChange = { state = it; true },
            onActivitySave = ActivitySaveNotExpected,
            featureVisibility = visibility,
            onFeatureVisibilityChange = { visibility = it; true },
            requestedNavigation = request,
            onDestinationConsumed = { request = null },
            timeSessions = sessions,
        )
    }

    /** A tile of Home, brought into sight first where Home had to scroll to fit its tiles. */
    private fun tile(tag: String): SemanticsNodeInteraction {
        val tile = composeRule.onNodeWithTag(tag)
        if (!tile.isDisplayed()) tile.performScrollTo()
        return tile
    }

    private fun tileNamed(tag: String, name: String) = composeRule.onNode(hasTestTag(tag) and hasAnyDescendant(hasText(name)), useUnmergedTree = true)

    /** From the Settings list, which Create, Save and Delete return to. */
    private fun openPage(view: TimeConfiguration) {
        composeRule.onNodeWithTag("settings_group_time_${view.id}").performScrollTo().performClick()
        composeRule.onNodeWithTag("time_settings").assertIsDisplayed()
    }

    /** A part of a page or a step of a setup, which sits in the page's scroll; the bar's buttons are pinned and pressed directly. */
    private fun openPart(tag: String) {
        composeRule.onNodeWithTag(tag).performScrollTo().performClick()
        composeRule.onNodeWithTag(tag).assertIsSelected()
    }

    private fun press(tag: String) = composeRule.onNodeWithTag(tag).performScrollTo().performClick()

    private val refused = SemanticsMatcher.keyIsDefined(SemanticsProperties.Error)

    private fun assertTileStatus(tag: String, description: String) {
        composeRule.onNode(hasContentDescription(description) and hasAnyAncestor(hasTestTag(tag)), useUnmergedTree = true).assertExists()
    }

    /** Today's typed minutes are a detour from the timer: once added, the row shows "Added" for a moment and goes back to it. */
    private fun awaitTheTimerRow(screen: String) {
        composeRule.waitUntil(timeoutMillis = 5_000) { composeRule.onAllNodesWithTag("${screen}_type_minutes").fetchSemanticsNodes().isNotEmpty() }
    }

    private fun minutesOf(viewId: String) = state.timeLogs.filter { it.viewId == viewId }.map { it.dateKey to it.minutes }

    private fun plural(id: Int, quantity: Int, vararg args: Any): String =
        InstrumentationRegistry.getInstrumentation().targetContext.resources.getQuantityString(id, quantity, *args)

    private fun weekMinutes(minutes: Int, asks: Int) = plural(R.plurals.time_week_minutes, asks, minutes, asks)

    /** The Goal part's line: what [goal] minutes earn, and with more than one step the most a week can earn, [most]. */
    private fun goalNote(goal: Int, most: Int? = 2): String = listOfNotNull(
        plural(R.plurals.time_settings_goal_note, goal, goal, plural(R.plurals.settings_tokens, 1, 1)),
        most?.let { plural(R.plurals.time_settings_goal_note_more, it, it) },
    ).joinToString(" ")

    private fun stepMinutes(minutes: Int) = appString(R.string.time_settings_step_minutes, minutes)

    /** The line under the rows: what a week of [minutes] or more earns in all, [tokens]. */
    private fun stepsTotal(minutes: Int, tokens: Int) = plural(R.plurals.time_settings_steps_total, minutes, minutes, plural(R.plurals.settings_tokens, tokens, tokens))

    /**
     * "Track something new" has a fourth card, Time, whose setup walks Look, Goal, Reminders and
     * Streak. Look waits for a name; Goal for minutes a week can ask, typed as a whole number;
     * Streak has no kept days to earn by; Create, on the last step, stores the view and nothing
     * else. Its row is in the Settings list with its switch on, and its tile on Home opens its
     * screen.
     */
    @Test
    fun aTimeViewIsMadeInFourStepsAndHasItsRowAndItsTile() {
        setApp()
        tile("home_settings").performClick()
        press("settings_new")
        composeRule.onNodeWithTag("settings_time_new").performScrollTo()
        tileNamed("settings_time_new", appString(R.string.time_destination)).assertIsDisplayed()
        tileNamed("settings_time_new", appString(R.string.settings_new_time_description)).assertIsDisplayed()
        composeRule.onNodeWithTag("settings_time_new").performClick()
        composeRule.onNodeWithTag("time_setup").assertIsDisplayed()

        fun step(number: Int, part: Int) = appString(R.string.checklist_setup_step, number, appString(part))
        composeRule.onNodeWithTag("time_setup_tab_look").performScrollTo().assertIsSelected().assertTextEquals(step(1, R.string.checklist_editor_tab_look))
        composeRule.onNodeWithTag("time_setup_tab_goal").assertIsNotEnabled().assertTextEquals(step(2, R.string.time_settings_goal_part))
        composeRule.onNodeWithTag("time_setup_tab_reminders").assertIsNotEnabled().assertTextEquals(step(3, R.string.checklist_editor_tab_reminders))
        composeRule.onNodeWithTag("time_setup_tab_streak").assertIsNotEnabled().assertTextEquals(step(4, R.string.checklist_editor_rewards))
        composeRule.onNodeWithTag("time_setup_delete").assertDoesNotExist()
        composeRule.onNodeWithTag("time_setup_next").assertIsNotEnabled()
        composeRule.onNodeWithTag("time_setup_name").performScrollTo().assert(!refused).performTextReplacement("Piano")
        // Pressed by its action, not by a touch at its middle: with more icons above it the tile can come to rest under the bar that holds Next.
        composeRule.onNodeWithTag("time_setup_hue_teal").performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
        // Look ends with Mo's flavor, on Plain, beside the one motif Mo has something for while a timer runs.
        composeRule.onNodeWithTag("time_setup_motif_none").performScrollTo().assertIsSelected()
        composeRule.onNodeWithTag("time_setup_motif_reading").performScrollTo().assertIsNotSelected()
        composeRule.onNodeWithTag("time_setup_motif_pills").assertDoesNotExist()
        Espresso.closeSoftKeyboard()
        composeRule.onNodeWithTag("time_setup_next").assertIsEnabled().performClick()

        // Step 2: an hour a week and what a token takes. Text that is no such number stays, says why, and holds Next.
        composeRule.onNodeWithTag("time_setup_tab_goal").performScrollTo().assertIsSelected()
        composeRule.onNodeWithTag("time_setup_goal").performScrollTo().assertInputEquals("60").assert(!refused)
        composeRule.onNodeWithTag("time_setup_goal_note").assertTextEquals(goalNote(60))
        composeRule.onNodeWithTag("time_setup_goal").performTextReplacement("1.5")
        composeRule.onNodeWithTag("time_setup_goal").assertInputEquals("1.5").assert(refused)
        composeRule.onNodeWithTag("time_setup_next").assertIsNotEnabled()
        composeRule.onNodeWithTag("time_setup_goal").performTextReplacement("90")
        composeRule.onNodeWithTag("time_setup_goal").assert(!refused)
        composeRule.onNodeWithTag("time_setup_goal_note").assertTextEquals(goalNote(90))
        Espresso.closeSoftKeyboard()
        composeRule.runOnIdle { assertTrue(state.madeTime.isEmpty()) }
        composeRule.onNodeWithTag("time_setup_next").assertIsEnabled().performClick()

        // Step 3 is the reminder every made view has, previewed under the name just typed with all the week asks.
        composeRule.onNodeWithTag("time_setup_tab_reminders").performScrollTo().assertIsSelected()
        composeRule.onNodeWithTag("time_setup_reminder").performScrollTo().assertIsOff()
        composeRule.onNodeWithTag("time_setup_reminder_preview_title").performScrollTo().assertTextEquals("Piano")
        composeRule.onNodeWithTag("time_setup_reminder_preview_text").assertTextEquals(plural(R.plurals.time_reminder_week_remaining, 90, 90))
        composeRule.onNodeWithTag("time_setup_next").performClick()

        // Step 4: the switch and, once it counts, how many tokens the bank holds and, last, the steps a week earns tokens by. No kept days earn here.
        composeRule.onNodeWithTag("time_setup_tab_streak").performScrollTo().assertIsSelected()
        composeRule.onNodeWithTag("time_setup_step_1_minutes").assertDoesNotExist()
        composeRule.onNodeWithTag("time_setup_rewards").performScrollTo().assertIsOff().performClick()
        composeRule.onNodeWithTag("time_setup_max_passes_value").performScrollTo().assertTextEquals("3")
        composeRule.onNodeWithTag("time_setup_every_kept_days_value").assertDoesNotExist()
        composeRule.onNodeWithTag("time_setup_away_due").performScrollTo().assertIsSelected()
        composeRule.onNodeWithTag("time_setup_step_1_minutes").performScrollTo().assertTextEquals(stepMinutes(90))
        composeRule.onNodeWithTag("time_setup_step_1_tokens_value").performScrollTo().assertTextEquals("+1")
        composeRule.onNodeWithTag("time_setup_step_2_percent").performScrollTo().assertInputEquals("200")
        composeRule.onNodeWithTag("time_setup_step_2_minutes").assertTextEquals(stepMinutes(180))
        composeRule.onNodeWithTag("time_setup_step_2_tokens_value").performScrollTo().assertTextEquals("+1")
        composeRule.onNodeWithTag("time_setup_create").assertIsEnabled().performClick()

        val made = composeRule.runOnIdle { state.madeTime.single() }
        composeRule.runOnIdle {
            assertTrue(made.id, made.id.matches(Regex("tim_[a-z2-9]{4}")))
            assertEquals("Piano" to ViewHue.Teal, made.label to made.hue)
            assertNull(made.motif)
            assertEquals(Dated.always(90), made.goal)
            assertEquals(TimeRewards(maxPasses = 3, periods = listOf(RewardPeriod(LocalDate.parse(today)))), made.rewards)
            assertEquals(listOf(readings), state.timeLogs)
        }

        // Created, the setup returned to the list; its tile opens its screen on today, against its own goal.
        composeRule.onNodeWithTag("time_setup").assertDoesNotExist()
        tileNamed("settings_group_time_${made.id}", "Piano").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("settings_feature_time_${made.id}").performScrollTo().assertIsOn()
        composeRule.onNodeWithTag("top_back").performClick()
        tileNamed("home_time_${made.id}", "Piano").assertExists()
        tile("home_time_${made.id}").performClick()
        composeRule.onNodeWithTag("time_${made.id}_selected_day").assertDoesNotExist()
        composeRule.onNodeWithTag("time_${made.id}_week_minutes").assertTextEquals(weekMinutes(0, 90))
        composeRule.onNodeWithTag("time_${made.id}_start").assertIsDisplayed()
    }

    /**
     * Minutes are typed for today behind the small button and for an earlier day at once, added to
     * again, and a day is removed with two taps, each stored under the view's ID for its day.
     * Reading's logs are as they were, and Reading's screen shows its own week.
     */
    @Test
    fun minutesAreTypedAddedToAndADayIsRemovedAndReadingSeesNothingOfIt() {
        state = state.copy(time = state.time + piano + guitar)
        setApp()
        tile("home_time_${piano.id}").performClick()
        val screen = "time_${piano.id}"

        composeRule.onNodeWithTag("${screen}_week_minutes").assertTextEquals(weekMinutes(0, 60))
        press("${screen}_type_minutes")
        composeRule.onNodeWithTag("${screen}_minutes_input").performTextReplacement("20")
        Espresso.closeSoftKeyboard()
        press("${screen}_log_today")
        composeRule.runOnIdle { assertEquals(listOf(today to 20), minutesOf(piano.id)) }

        // Typed again for the same day, the minutes add up: a day is never lowered.
        awaitTheTimerRow(screen)
        press("${screen}_type_minutes")
        composeRule.onNodeWithTag("${screen}_minutes_input").performTextReplacement("15")
        Espresso.closeSoftKeyboard()
        press("${screen}_log_today")
        composeRule.runOnIdle { assertEquals(listOf(today to 35), minutesOf(piano.id)) }

        // An earlier day is typed in at once, and removed with two taps.
        composeRule.onNodeWithTag("${screen}_history_$yesterday").performScrollTo().performClick()
        composeRule.onNodeWithTag("${screen}_minutes_input").performScrollTo().performTextReplacement("10")
        Espresso.closeSoftKeyboard()
        press("${screen}_log_today")
        composeRule.runOnIdle { assertEquals(setOf(today to 35, yesterday to 10), minutesOf(piano.id).toSet()) }
        press("${screen}_remove_day")
        composeRule.runOnIdle { assertEquals(2, minutesOf(piano.id).size) }
        press("${screen}_remove_day")
        composeRule.runOnIdle {
            assertEquals(listOf(today to 35), minutesOf(piano.id))
            assertEquals(listOf(yesterday to 25), minutesOf("reading"))
            assertTrue(minutesOf(guitar.id).isEmpty())
        }

        // The other view counts its own, against its own goal, and Reading's screen shows Reading's minutes.
        composeRule.onNodeWithTag("top_back").performClick()
        tile("home_time_${guitar.id}").performClick()
        composeRule.onNodeWithTag("time_${guitar.id}_week_minutes").assertTextEquals(weekMinutes(0, 90))
        composeRule.onNodeWithTag("top_back").performClick()
        tile("home_time_reading").performClick()
        composeRule.onNodeWithTag("time_reading_start").assertIsDisplayed()
        composeRule.onNodeWithTag("time_${piano.id}_start").assertDoesNotExist()
    }

    /**
     * A session is started on the view's screen, runs full screen under the view's name while
     * nothing else of the app shows, and Done adds its whole minutes to today. One under a minute
     * saves nothing, and its screen says so.
     */
    @Test
    fun aSessionIsTimedSavedAndOneUnderAMinuteSavesNothing() {
        state = state.copy(time = state.time + piano)
        setApp()
        tile("home_time_${piano.id}").performClick()
        val screen = "time_${piano.id}"

        press("${screen}_start")
        composeRule.onNodeWithTag("${screen}_session").assertIsDisplayed()
        composeRule.onNodeWithTag("${screen}_start").assertDoesNotExist()
        composeRule.onNodeWithTag("home_time_reading").assertDoesNotExist()
        composeRule.runOnIdle {
            assertEquals(piano.id, sessions.session?.viewId)
            // One session runs in the whole app: Reading's start is not taken while this one runs.
            sessions.requestStart("reading")
            assertEquals(piano.id, sessions.session?.viewId)
            clock += 25 * 60_000L + 30_000L
        }
        composeRule.onNodeWithTag("${screen}_session_done").performClick()

        composeRule.onNodeWithTag("${screen}_session").assertDoesNotExist()
        composeRule.runOnIdle {
            assertNull(sessions.session)
            assertEquals(listOf(today to 25), minutesOf(piano.id))
            assertEquals(listOf(yesterday to 25), minutesOf("reading"))
        }
        composeRule.onNodeWithTag("${screen}_week_minutes").performScrollTo().assertTextEquals(weekMinutes(25, 60))

        // Started again and ended at once: under a minute, nothing is saved, and the screen says so.
        press("${screen}_start")
        composeRule.runOnIdle { clock += 20_000L }
        composeRule.onNodeWithTag("${screen}_session_done").performClick()
        composeRule.onNodeWithTag("${screen}_session_note").performScrollTo().assertTextEquals(appString(R.string.time_note_nothing_saved))
        composeRule.runOnIdle { assertEquals(listOf(today to 25), minutesOf(piano.id)) }
    }

    /**
     * The page changes the view over one Save: its name, its goal, and counting toward the streak
     * with a smaller bank and holidays not due. A goal that is no goal holds Save back.
     */
    @Test
    fun thePageChangesItsNameItsGoalAndItsStreakOverOneSave() {
        state = state.copy(time = state.time + piano)
        setApp()
        tile("home_settings").performClick()
        openPage(piano)

        composeRule.onNodeWithTag("time_settings_save").assertIsNotEnabled()
        composeRule.onNodeWithTag("time_settings_name").performScrollTo().performTextReplacement("Keys")
        openPart("time_settings_tab_goal")
        composeRule.onNodeWithTag("time_settings_goal").performScrollTo().assertInputEquals("60")
        composeRule.onNodeWithTag("time_settings_goal").performTextReplacement("0")
        composeRule.onNodeWithTag("time_settings_goal").assert(refused)
        composeRule.onNodeWithTag("time_settings_save").assertIsNotEnabled()
        composeRule.onNodeWithTag("time_settings_goal").performTextReplacement("45")
        Espresso.closeSoftKeyboard()

        openPart("time_settings_tab_streak")
        composeRule.onNodeWithTag("time_settings_rewards").performScrollTo().assertIsOff().performClick()
        press("time_settings_max_passes_less")
        composeRule.onNodeWithTag("time_settings_max_passes_value").assertTextEquals("2")
        composeRule.onNodeWithTag("time_settings_every_kept_days_value").assertDoesNotExist()
        press("time_settings_away_excused")
        composeRule.runOnIdle { assertEquals(piano, state.madeTime.single()) }

        composeRule.onNodeWithTag("time_settings_save").assertIsEnabled().performClick()
        composeRule.runOnIdle {
            val saved = state.madeTime.single()
            assertEquals("Keys", saved.label)
            assertEquals(Dated.always(45), saved.goal)
            assertEquals(TimeRewards(maxPasses = 2, periods = listOf(RewardPeriod(LocalDate.parse(today)))), saved.rewards)
            assertEquals(WhenAway.Excused, saved.whenAway.on(LocalDate.parse(today)))
        }
        composeRule.onNodeWithTag("time_settings").assertDoesNotExist()
        tileNamed("settings_group_time_${piano.id}", "Keys").performScrollTo().assertIsDisplayed()
    }

    /**
     * The steps a week earns tokens by stand last on the Streak part of a view that counts, one
     * row each. A third is added and starts at 300 % adding one token, its percent typed to 250
     * and its tokens stepped up, the 200 % step is removed with two taps, and Save dates the list
     * from today.
     * The Goal part's line says the most a week can earn, and the token page, which counted this
     * week's seventy-five minutes toward the 200 % step, counts toward the 250 % step after.
     */
    @Test
    fun theTokenStepsAreListedOnItsStreakPartAndTheTokenPageCountsTowardThem() {
        val joined = LocalDate.parse(today).minusDays(14)
        state = state.copy(time = state.time + piano, timeLogs = listOf(TimeLog(piano.id, today, 2L, 75), readings))
            .applyingTimeEdits(piano.id, listOf(TimeEdit.JoinRewards), joined)
        setApp()
        fun minutesToAToken(done: Int, goal: Int) = hasContentDescription(plural(R.plurals.mo_progress_minutes_this_week, goal, done, goal), substring = true)

        composeRule.onNodeWithTag("home_streak").performClick()
        composeRule.onNodeWithTag("streak_time_${piano.id}").performScrollTo().assert(minutesToAToken(75, 120))
        composeRule.onNodeWithTag("top_back").performClick()

        tile("home_settings").performClick()
        openPage(piano)
        openPart("time_settings_tab_goal")
        composeRule.onNodeWithTag("time_settings_goal_note").performScrollTo().assertTextEquals(goalNote(60))
        openPart("time_settings_tab_streak")
        composeRule.onNodeWithTag("time_settings_step_1_minutes").performScrollTo().assertTextEquals(stepMinutes(60))
        composeRule.onNodeWithTag("time_settings_step_2_percent").performScrollTo().assertInputEquals("200")
        composeRule.onNodeWithTag("time_settings_step_2_minutes").assertTextEquals(stepMinutes(120))
        composeRule.onNodeWithTag("time_settings_step_2_tokens_value").performScrollTo().assertTextEquals("+1")
        composeRule.onNodeWithTag("time_settings_steps_total").performScrollTo().assertTextEquals(stepsTotal(120, 2))
        press("time_settings_add_step")
        composeRule.onNodeWithTag("time_settings_step_3_percent").performScrollTo().assertInputEquals("300")
        composeRule.onNodeWithTag("time_settings_step_3_tokens_value").performScrollTo().assertTextEquals("+1")
        // Typed below the step before it, the percent stays, says why, and holds Save and the button back.
        composeRule.onNodeWithTag("time_settings_step_3_percent").performScrollTo().performTextReplacement("150")
        composeRule.onNodeWithTag("time_settings_step_3_percent").assertInputEquals("150").assert(refused)
        composeRule.onNodeWithTag("time_settings_save").assertIsNotEnabled()
        composeRule.onNodeWithTag("time_settings_add_step").assertIsNotEnabled()
        composeRule.onNodeWithTag("time_settings_step_3_percent").performTextReplacement("250")
        composeRule.onNodeWithTag("time_settings_step_3_percent").assert(!refused)
        composeRule.onNodeWithTag("time_settings_step_3_minutes").assertTextEquals(stepMinutes(150))
        Espresso.closeSoftKeyboard()
        press("time_settings_step_3_tokens_more")
        composeRule.onNodeWithTag("time_settings_step_3_tokens_value").assertTextEquals("+2")
        composeRule.onNodeWithTag("time_settings_steps_total").performScrollTo().assertTextEquals(stepsTotal(150, 4))
        press("time_settings_step_2_remove")
        press("time_settings_step_2_remove")
        composeRule.onNodeWithTag("time_settings_step_2_percent").performScrollTo().assertInputEquals("250")
        composeRule.onNodeWithTag("time_settings_step_3_percent").assertDoesNotExist()
        openPart("time_settings_tab_goal")
        composeRule.onNodeWithTag("time_settings_goal_note").performScrollTo().assertTextEquals(goalNote(60, most = 3))
        composeRule.runOnIdle { assertEquals(Dated.always(DefaultTokenSteps), state.madeTime.single().rewards?.tokenSteps) }

        composeRule.onNodeWithTag("time_settings_save").assertIsEnabled().performClick()
        composeRule.runOnIdle {
            // It took part before today, so the steps are dated and earlier weeks keep the two they had.
            assertEquals(
                Dated(listOf(Dated.Revision(DefaultTokenSteps), Dated.Revision(TokenSteps(listOf(TokenStep(100, 1), TokenStep(250, 2))), LocalDate.parse(today)))),
                state.madeTime.single().rewards?.tokenSteps,
            )
        }
        composeRule.onNodeWithTag("time_settings").assertDoesNotExist()

        composeRule.onNodeWithTag("top_back").performClick()
        composeRule.onNodeWithTag("home_streak").performClick()
        composeRule.onNodeWithTag("streak_time_${piano.id}").performScrollTo().assert(minutesToAToken(75, 150))
    }

    /**
     * Mo's flavor is picked on the Look part, Plain or Reading, and stored on Save. The view's
     * session then starts and saves as before, under the view's own name and tags and not
     * Reading's. What Mo does in it is not said to TalkBack: the gallery shows it, and
     * `TimeSessionScreenTest` holds which Mo each flavor gives.
     */
    @Test
    fun aFlavorIsPickedOnItsLookPartAndItsSessionRunsUnderItsOwnName() {
        state = state.copy(time = state.time + piano)
        setApp()
        tile("home_settings").performClick()
        openPage(piano)

        composeRule.onNodeWithTag("time_settings_motif_none").performScrollTo().assertIsSelected()
        composeRule.onNodeWithTag("time_settings_motif_reading").performScrollTo().assertIsNotSelected()
        composeRule.onNodeWithTag("time_settings_motif_pills").assertDoesNotExist()
        composeRule.onNodeWithTag("time_settings_save").assertIsNotEnabled()
        press("time_settings_motif_reading")
        composeRule.onNodeWithTag("time_settings_motif_reading").assertIsSelected()
        composeRule.runOnIdle { assertNull(state.madeTime.single().motif) }
        composeRule.onNodeWithTag("time_settings_save").assertIsEnabled().performClick()
        composeRule.runOnIdle { assertEquals(piano.copy(mo = piano.mo.copy(motif = MoMotif.Book)), state.madeTime.single()) }
        composeRule.onNodeWithTag("time_settings").assertDoesNotExist()

        composeRule.onNodeWithTag("top_back").performClick()
        tile("home_time_${piano.id}").performClick()
        val screen = "time_${piano.id}"
        press("${screen}_start")
        composeRule.onNodeWithTag("${screen}_session").assertIsDisplayed()
        composeRule.onNodeWithTag("time_reading_session").assertDoesNotExist()
        composeRule.onNodeWithTag("${screen}_session_minutes").assertTextContains("Piano", substring = true)
        composeRule.runOnIdle {
            assertEquals(piano.id, sessions.session?.viewId)
            clock += 3 * 60_000L + 10_000L
        }
        composeRule.onNodeWithTag("${screen}_session_done").performClick()

        composeRule.onNodeWithTag("${screen}_session").assertDoesNotExist()
        composeRule.runOnIdle {
            assertNull(sessions.session)
            assertEquals(listOf(today to 3), minutesOf(piano.id))
            assertEquals(listOf(yesterday to 25), minutesOf("reading"))
        }
    }

    /**
     * Counting toward the streak, its tile is done once today holds minutes, and the token page
     * has its row with this week's minutes toward the next token.
     */
    @Test
    fun inTheStreakItsTileIsDoneByTodaysMinutesAndTheTokenPageHasItsRow() {
        state = state.copy(time = state.time + piano).applyingTimeEdits(piano.id, listOf(TimeEdit.JoinRewards), LocalDate.parse(today).minusDays(14))
        setApp()

        composeRule.onNodeWithTag("home_streak").performClick()
        // The row speaks its bank and, in minutes, how far this week is from the next token.
        composeRule.onNodeWithTag("streak_time_${piano.id}").performScrollTo().assertIsDisplayed()
            .assert(hasContentDescription(plural(R.plurals.mo_progress_minutes_this_week, 60, 0, 60), substring = true))
        composeRule.onNodeWithTag("top_back").performClick()

        tile("home_time_${piano.id}").performClick()
        press("time_${piano.id}_type_minutes")
        composeRule.onNodeWithTag("time_${piano.id}_minutes_input").performTextReplacement("20")
        Espresso.closeSoftKeyboard()
        press("time_${piano.id}_log_today")
        composeRule.onNodeWithTag("top_back").performClick()
        assertTileStatus("home_time_${piano.id}", appString(R.string.app_home_task_done))

        composeRule.onNodeWithTag("home_streak").performClick()
        composeRule.onNodeWithTag("streak_time_${piano.id}").performScrollTo()
            .assert(hasContentDescription(plural(R.plurals.mo_progress_minutes_this_week, 60, 20, 60), substring = true))
    }

    /**
     * "Send a test" on its Reminders part posts its reminder, with the switch off, under the view's
     * name, with all its week asks. The notification's tap is the one that opens this view, and
     * arriving, that request opens its screen on today.
     */
    @Test
    fun sendATestShowsItsReminderAndATapOpensItsScreen() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        // The suite grants no permission on its own, and the app creates the channel on start; the test host is not the app's activity.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        }
        ReminderNotificationChannel.create(context)
        notificationManager.cancelAll()
        state = state.copy(time = state.time + piano)
        fun postedTest() = notificationManager.activeNotifications
            .firstOrNull { it.tag == slotKey(ReminderViewKind.Time, piano.id, "day") && it.id == ChecklistReminderRequestCode }

        try {
            setApp()
            tile("home_settings").performClick()
            openPage(piano)
            openPart("time_settings_tab_reminders")
            val words = plural(R.plurals.time_reminder_week_remaining, 60, 60)
            composeRule.onNodeWithTag("time_settings_reminder").performScrollTo().assertIsOff()
            composeRule.onNodeWithTag("time_settings_reminder_preview_text").performScrollTo().assertTextEquals(words)
            composeRule.onNodeWithTag("time_settings_reminder_test").performScrollTo().assertIsEnabled().performClick()

            composeRule.waitUntil(timeoutMillis = 5_000) { postedTest() != null }
            val posted = requireNotNull(postedTest()).notification
            assertEquals("Piano", posted.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString())
            assertEquals(words, posted.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString())
            assertEquals(loggedViewDestinationPendingIntent(context, LoggedViewRef(LoggedViewKind.Time, piano.id), today), posted.contentIntent)

            // The tap's request arrives: the page held nothing unsaved, so the view opens, on today.
            composeRule.runOnIdle { request = AppNavigationRequest(AppDestination.Time, loggedViewId = piano.id, loggedDateKey = today) }
            composeRule.onNodeWithTag("time_${piano.id}_selected_day").assertDoesNotExist()
            composeRule.onNodeWithTag("time_${piano.id}_start").assertIsDisplayed()
            composeRule.runOnIdle { assertEquals(null, request) }
        } finally {
            notificationManager.cancelAll()
        }
    }

    /**
     * Hidden with the switch on its Settings row, its tile is gone and a reminder's tap finds no
     * screen to open, while its settings stay open. Shown again, its tile is back. Deleted, it
     * goes with every day's minutes, and Reading's stay.
     */
    @Test
    fun itIsHiddenShownAgainAndDeletedWithItsLogs() {
        state = state.copy(time = state.time + piano + guitar, timeLogs = listOf(TimeLog(piano.id, today, 2L, 30), TimeLog(piano.id, yesterday, 1L, 20), readings))
        setApp()
        tile("home_time_${piano.id}").assertIsDisplayed()
        tile("home_settings").performClick()

        composeRule.onNodeWithTag("settings_feature_time_${piano.id}").performScrollTo().assertIsOn().performClick()
        composeRule.onNodeWithTag("settings_feature_time_${piano.id}").assertIsOff()
        composeRule.onNodeWithTag("settings_feature_time_${guitar.id}").performScrollTo().assertIsOn()
        composeRule.runOnIdle { assertEquals(false, visibility.shows(piano.ref)) }

        composeRule.onNodeWithTag("top_back").performClick()
        composeRule.onNodeWithTag("home_time_${piano.id}").assertDoesNotExist()
        tile("home_time_${guitar.id}").assertIsDisplayed()
        composeRule.runOnIdle { request = AppNavigationRequest(AppDestination.Time, loggedViewId = piano.id, loggedDateKey = today) }
        composeRule.onNodeWithTag("time_${piano.id}_start").assertDoesNotExist()
        tile("home_settings").assertIsDisplayed()

        tile("home_settings").performClick()
        openPage(piano)
        openPart("time_settings_tab_reminders")
        composeRule.onNodeWithTag("time_settings_reminder_dependency").performScrollTo().assertTextEquals(appString(R.string.settings_turn_feature_on, "Piano"))
        composeRule.onNodeWithTag("time_settings_reminder_test").performScrollTo().assertIsNotEnabled()
        composeRule.onNodeWithTag("top_back").performClick()

        composeRule.onNodeWithTag("settings_feature_time_${piano.id}").performScrollTo().performClick()
        composeRule.runOnIdle { assertEquals(FeatureVisibilitySettings(), visibility) }
        composeRule.onNodeWithTag("top_back").performClick()
        tile("home_time_${piano.id}").assertIsDisplayed()

        // Delete names the view and its two recorded days, and takes them with it on the second answer.
        tile("home_settings").performClick()
        openPage(piano)
        composeRule.onNodeWithTag("time_settings_delete").performClick()
        composeRule.onNodeWithTag("time_settings_delete_dialog").assertIsDisplayed()
        composeRule.onNode(hasText(plural(R.plurals.measurement_settings_delete_message, 2, 2))).assertIsDisplayed()
        composeRule.onNodeWithTag("time_settings_delete_cancel").performClick()
        composeRule.runOnIdle { assertEquals(listOf(piano, guitar), state.madeTime) }
        composeRule.onNodeWithTag("time_settings_delete").performClick()
        composeRule.onNodeWithTag("time_settings_delete_confirm").performClick()

        composeRule.runOnIdle {
            assertEquals(listOf(guitar), state.madeTime)
            assertEquals(listOf(readings), state.timeLogs)
        }
        composeRule.onNodeWithTag("time_settings").assertDoesNotExist()
        composeRule.onNodeWithTag("settings_group_time_${piano.id}").assertDoesNotExist()
        composeRule.onNodeWithTag("top_back").performClick()
        composeRule.onNodeWithTag("home_time_${piano.id}").assertDoesNotExist()
        tile("home_time_${guitar.id}").assertIsDisplayed()
    }
}

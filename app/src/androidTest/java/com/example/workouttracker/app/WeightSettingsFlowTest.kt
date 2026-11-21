package com.example.workouttracker.app

import com.example.workouttracker.domain.checklist.Dated
import com.example.workouttracker.domain.measurement.EarlierWeeks
import com.example.workouttracker.domain.measurement.MeasurementBank
import com.example.workouttracker.domain.measurement.recordsOf

import java.time.LocalTime
import com.example.workouttracker.domain.checklist.CheckpointTime
import com.example.workouttracker.domain.checklist.Checkpoint
import com.example.workouttracker.domain.checklist.RewardPeriod
import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.espresso.Espresso
import androidx.test.platform.app.InstrumentationRegistry
import com.example.workouttracker.R
import com.example.workouttracker.core.format.formatMetricNumber
import com.example.workouttracker.core.locale.locale
import com.example.workouttracker.core.text.isolated
import com.example.workouttracker.core.time.currentDateKey
import com.example.workouttracker.core.time.currentHabitDate
import com.example.workouttracker.core.ui.theme.WorkoutTrackerTheme
import com.example.workouttracker.domain.AppState
import com.example.workouttracker.domain.checklist.ViewHue
import com.example.workouttracker.domain.checklist.ViewIcon
import com.example.workouttracker.domain.checklist.WhenAway
import com.example.workouttracker.domain.measurement.MeasurementEdit
import com.example.workouttracker.domain.measurement.MeasurementLog
import com.example.workouttracker.domain.measurement.MeasurementStep
import com.example.workouttracker.testfixtures.WeightMeasurementId
import com.example.workouttracker.domain.measurement.applyingMeasurementEdits
import com.example.workouttracker.testfixtures.defaultWeightMeasurement
import com.example.workouttracker.testfixtures.weightMeasurement
import com.example.workouttracker.domain.reminder.ReminderSettings
import com.example.workouttracker.domain.settings.DashboardFeature
import com.example.workouttracker.domain.settings.FeatureVisibilitySettings
import com.example.workouttracker.feature.settings.ViewWrite
import com.example.workouttracker.persistence.TrackerRepository
import com.example.workouttracker.persistence.TrackerUpdateResult
import com.example.workouttracker.reminder.ReminderNotificationChannel
import com.example.workouttracker.reminder.AndroidChecklistReminderGateway
import com.example.workouttracker.reminder.ChecklistReminderCoordinator
import com.example.workouttracker.reminder.ChecklistReminderNotification
import com.example.workouttracker.reminder.FeatureVisibilitySource
import com.example.workouttracker.reminder.PreferencesChecklistReminderRuntime
import com.example.workouttracker.reminder.ReminderDays
import com.example.workouttracker.reminder.ReminderViewKind
import com.example.workouttracker.reminder.slotKey
import com.example.workouttracker.testing.appString
import com.example.workouttracker.testing.assertInputEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Weight's settings on the page a measurement a person made has, over its stored configuration:
 * Look, Unit, Reminders and Streak, saved by one press, its reminder among them, and Delete. The
 * stored configuration is the proof, and the places that wear the name show what a rename reaches.
 */
class WeightSettingsFlowTest {
    @get:Rule
    val composeRule = createComposeRule()

    /** Weight as it is seeded, its reminder on. */
    private val seeded = defaultWeightMeasurement(label = "Weight", remind = true)

    private var state by mutableStateOf(AppState(measurements = listOf(seeded)))

    /** Whether the store refuses a save, as a write that failed does. */
    private var storeFails = false

    private val weight get() = requireNotNull(state.weightMeasurement)

    private val resources get() = InstrumentationRegistry.getInstrumentation().targetContext.resources

    /** A number as the device's language writes it. */
    private fun number(value: Double): String = formatMetricNumber(value, resources.locale)

    private val refused = SemanticsMatcher.keyIsDefined(SemanticsProperties.Error)

    /** [onMeasurementSave] is what Save on Weight's page stores through: the app's own default, or the commands a flow brings. */
    private fun setApp(onMeasurementSave: ((String, List<MeasurementEdit>) -> ViewWrite)? = null) {
        composeRule.setContent {
            WorkoutTrackerTheme {
                MomentumApp(
                    state = state,
                    onStateChange = { state = it; true },
                    onActivitySave = ActivitySaveNotExpected,
                    onMeasurementSave = onMeasurementSave ?: { measurementId, edits ->
                        if (storeFails) {
                            ViewWrite.NotStored
                        } else {
                            state = state.applyingMeasurementEdits(measurementId, edits, currentHabitDate())
                            ViewWrite.Stored
                        }
                    },
                    // Statistics starts hidden, and one flow follows Weight's name into it.
                    featureVisibility = FeatureVisibilitySettings().withVisibility(DashboardFeature.Statistics, true, currentHabitDate()),
                )
            }
        }
    }

    /** From the Settings list, which every Save returns to. */
    private fun openWeightsPage() {
        composeRule.onNodeWithTag("settings_group_measurement_weight").performScrollTo().performClick()
        composeRule.onNodeWithTag("measurement_settings").assertIsDisplayed()
    }

    private fun openPart(part: String) {
        composeRule.onNodeWithTag("measurement_settings_tab_$part").performScrollTo().performClick()
        composeRule.onNodeWithTag("measurement_settings_tab_$part").assertIsSelected()
    }

    private fun daysThisWeek(done: Int, goal: Int): String =
        InstrumentationRegistry.getInstrumentation().targetContext.resources.getQuantityString(R.plurals.mo_progress_days_this_week, goal, done, goal)

    private fun daysAcrossWeeks(done: Int, goal: Int): String =
        InstrumentationRegistry.getInstrumentation().targetContext.resources.getQuantityString(R.plurals.mo_progress_days, goal, done, goal)

    private fun keptDays(days: Int): String =
        InstrumentationRegistry.getInstrumentation().targetContext.resources.getQuantityString(R.plurals.checklist_editor_days, days, days)

    /** The page is a measurement's, under its kind's tags, showing what is stored, with its week bank and a Delete that asks first. */
    @Test
    fun weightsPageIsAMeasurementsPageWithItsWeekBankAndDelete() {
        setApp()
        composeRule.onNodeWithTag("home_settings").performClick()
        openWeightsPage()

        composeRule.onNodeWithTag("measurement_settings_tab_look").assertIsSelected()
        composeRule.onNodeWithTag("measurement_settings_tab_items").assertDoesNotExist()
        composeRule.onNodeWithTag("measurement_settings_name").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("measurement_settings_icon_scale").performScrollTo().assertIsSelected()
        composeRule.onNodeWithTag("measurement_settings_hue_sky").performScrollTo().assertIsSelected()

        // Unit is the second part, named by its own word: the kilograms and the step of 0.1 Weight starts with, and what changing the unit does not do.
        composeRule.onNodeWithTag("measurement_settings_tab_unit").assertTextEquals(appString(R.string.measurement_settings_unit_part))
        openPart("unit")
        composeRule.onNodeWithTag("measurement_settings_unit").performScrollTo().assertInputEquals("kg")
        composeRule.onNodeWithTag("measurement_settings_step").performScrollTo().assertInputEquals(formatMetricNumber(0.1, resources.locale))
        composeRule.onNodeWithText(appString(R.string.measurement_settings_unit_note)).performScrollTo().assertIsDisplayed()

        openPart("reminders")
        composeRule.onNodeWithTag("measurement_settings_reminder").performScrollTo().assertIsOn()
        composeRule.onNodeWithTag("measurement_settings_reminder_time_after_wake").performScrollTo().assertIsSelected()

        // It counts toward the streak, with the week bank it was stored with: its two numbers stand in the steppers
        // every daily bank has, over the line that says the days still count within a week.
        openPart("streak")
        composeRule.onNodeWithTag("measurement_settings_rewards").performScrollTo().assertIsOn()
        composeRule.onNodeWithTag("measurement_settings_max_passes_value").performScrollTo().assertTextEquals("7")
        composeRule.onNodeWithTag("measurement_settings_every_kept_days_value").performScrollTo().assertTextEquals(keptDays(6))
        composeRule.onNodeWithTag("measurement_settings_earn_note").performScrollTo().assertTextEquals(appString(R.string.measurement_settings_still_by_week))
        composeRule.onNodeWithTag("measurement_settings_away_excused").performScrollTo().assertIsSelected()
        composeRule.onNodeWithTag("measurement_settings_away_due").assertIsNotSelected()

        // Delete asks first, naming it and its days; cancelled, Weight stays as it is.
        composeRule.onNodeWithTag("measurement_settings_delete_reason").assertDoesNotExist()
        composeRule.onNodeWithTag("measurement_settings_delete").assertIsDisplayed().assertIsEnabled().performClick()
        composeRule.onNodeWithTag("measurement_settings_delete_dialog").assertIsDisplayed()
        composeRule.onNodeWithText(appString(R.string.measurement_settings_delete_title, "Weight")).assertIsDisplayed()
        composeRule.onNodeWithText(appString(R.string.measurement_settings_delete_message_none)).assertIsDisplayed()
        composeRule.onNodeWithTag("measurement_settings_delete_cancel").performClick()
        composeRule.onNodeWithTag("measurement_settings_delete_dialog").assertDoesNotExist()
        composeRule.runOnIdle { assertEquals(seeded, weight) }
        // Untouched, there is nothing to save, and back leaves at once.
        composeRule.onNodeWithTag("measurement_settings_save").assertIsNotEnabled()
        composeRule.onNodeWithTag("top_back").performClick()
        composeRule.onNodeWithTag("measurement_settings_discard_dialog").assertDoesNotExist()
        composeRule.onNodeWithTag("measurement_settings").assertDoesNotExist()
    }

    /**
     * Renamed and recolored, Weight is stored so on Save and wears the new name in the Settings
     * list, on Home, in its header and in Statistics. A cleared name holds Save back.
     */
    @Test
    fun aRenamedAndRecoloredWeightShowsItsNameEverywhere() {
        val name = "Morning weight"
        setApp()
        composeRule.onNodeWithTag("home_settings").performClick()
        openWeightsPage()

        composeRule.onNodeWithTag("measurement_settings_hue_plum").performScrollTo().performClick()
        composeRule.onNodeWithTag("measurement_settings_hue_plum").assertIsSelected()
        composeRule.onNodeWithTag("measurement_settings_icon_heart").performScrollTo().performClick()
        composeRule.onNodeWithTag("measurement_settings_save").assertIsEnabled()
        composeRule.onNodeWithTag("measurement_settings_name").performScrollTo().performTextReplacement("")
        composeRule.onNodeWithTag("measurement_settings_save").assertIsNotEnabled()
        composeRule.onNodeWithTag("measurement_settings_name").performTextReplacement(name)
        composeRule.onNodeWithTag("measurement_settings_save").assertIsEnabled()
        composeRule.runOnIdle { assertEquals(seeded, weight) }

        Espresso.closeSoftKeyboard()
        composeRule.onNodeWithTag("measurement_settings_save").performClick()
        composeRule.runOnIdle {
            assertEquals(name, weight.label)
            assertEquals(ViewIcon.Heart, weight.icon)
            assertEquals(ViewHue.Plum, weight.hue)
            // The look judges no day, so nothing about the streak moved.
            assertEquals(seeded.rewards, weight.rewards)
            assertEquals(seeded.whenAway, weight.whenAway)
        }

        // Saved, the page returned to the list, whose row wears the name.
        composeRule.onNodeWithTag("measurement_settings").assertDoesNotExist()
        composeRule.onNode(hasTestTag("settings_group_measurement_weight") and hasAnyDescendant(hasText(name)), useUnmergedTree = true).performScrollTo().assertIsDisplayed()

        composeRule.onNodeWithTag("top_back").performClick()
        composeRule.onNode(hasTestTag("home_measurement_weight") and hasAnyDescendant(hasText(name)), useUnmergedTree = true).assertIsDisplayed()

        composeRule.onNodeWithTag("home_measurement_weight").performClick()
        composeRule.onNodeWithText(name).assertIsDisplayed()
        composeRule.onNodeWithTag("top_back").performClick()

        composeRule.onNodeWithTag("home_statistics").performClick()
        composeRule.onNodeWithTag("statistics_consistency_measurement_weight").performScrollTo().assert(hasContentDescription(name, substring = true))
        composeRule.onNodeWithTag("top_back").performClick()

        // Opened again, the page shows what was stored and has nothing left to save.
        composeRule.onNodeWithTag("home_settings").performClick()
        openWeightsPage()
        composeRule.onNodeWithTag("measurement_settings_icon_heart").performScrollTo().assertIsSelected()
        composeRule.onNodeWithTag("measurement_settings_hue_plum").performScrollTo().assertIsSelected()
        composeRule.onNodeWithTag("measurement_settings_save").assertIsNotEnabled()
    }

    /**
     * The bank's size, the kept days a token takes and holidays still due are stored on Save,
     * dated from today. With them the week bank earns by kept days from today, and the token page
     * shows the new bank and the days toward the next token without a week.
     */
    @Test
    fun aChangedBankAndNumberOfDaysAreOnTheTokenPageAfterSave() {
        setApp()
        composeRule.onNodeWithTag("home_streak").performClick()
        composeRule.onNodeWithTag("streak_measurement_weight").performScrollTo().assert(hasContentDescription(daysThisWeek(0, 6), substring = true))
        composeRule.onNodeWithTag("top_back").performClick()

        composeRule.onNodeWithTag("home_settings").performClick()
        openWeightsPage()
        openPart("streak")
        repeat(6) { composeRule.onNodeWithTag("measurement_settings_max_passes_less").performScrollTo().performClick() }
        composeRule.onNodeWithTag("measurement_settings_max_passes_value").assertTextEquals("1")
        composeRule.onNodeWithTag("measurement_settings_max_passes_less").assertIsNotEnabled()
        repeat(2) { composeRule.onNodeWithTag("measurement_settings_every_kept_days_less").performScrollTo().performClick() }
        composeRule.onNodeWithTag("measurement_settings_every_kept_days_value").assertTextEquals(keptDays(4))
        composeRule.onNodeWithTag("measurement_settings_earn_note").assertDoesNotExist()
        composeRule.onNodeWithTag("measurement_settings_away_due").performScrollTo().performClick()
        composeRule.onNodeWithTag("measurement_settings_away_due").assertIsSelected()
        composeRule.runOnIdle { assertEquals(seeded, weight) }

        composeRule.onNodeWithTag("measurement_settings_save").performClick()
        composeRule.runOnIdle {
            assertEquals(listOf(7, 1), weight.standardRewards!!.maxPasses.revisions.map { it.value })
            assertEquals(Dated.always(4), weight.standardRewards!!.everyKeptDays)
            assertEquals(EarlierWeeks(Dated.always(6), until = currentHabitDate().minusDays(1)), (weight.rewards as MeasurementBank.Standard).earlierWeeks)
            assertEquals(listOf(WhenAway.Excused, WhenAway.Due), weight.whenAway.revisions.map { it.value })
        }
        composeRule.onNodeWithTag("measurement_settings").assertDoesNotExist()

        composeRule.onNodeWithTag("top_back").performClick()
        composeRule.onNodeWithTag("home_streak").performClick()
        composeRule.onNodeWithTag("streak_measurement_weight").performScrollTo()
            .assert(hasContentDescription(daysAcrossWeeks(0, 4), substring = true))
            // The one token Weight starts with fills a bank of one.
            .assert(hasContentDescription(appString(R.string.mo_full), substring = true))
    }

    /**
     * Weight leaves the streak on its Streak part: its week bank goes out of sight and, saved, the
     * period it took part in since its first weight closes yesterday. Joined again the same day,
     * the period reopens, so it takes part on exactly the days it did.
     */
    @Test
    fun weightLeavesTheStreakAndJoinsAgain() {
        val firstWeight = currentHabitDate().minusDays(10)
        state = state.copy(measurementLogs = listOf(MeasurementLog(WeightMeasurementId, firstWeight.toString(), 1L, 81.0)))
        setApp()
        composeRule.onNodeWithTag("home_settings").performClick()
        openWeightsPage()
        openPart("streak")

        composeRule.onNodeWithTag("measurement_settings_rewards").performScrollTo().assertIsOn().performClick()
        composeRule.onNodeWithTag("measurement_settings_rewards").assertIsOff()
        composeRule.onNodeWithTag("measurement_settings_max_passes_value").assertDoesNotExist()
        composeRule.onNodeWithTag("measurement_settings_every_kept_days_value").assertDoesNotExist()
        composeRule.onNodeWithTag("measurement_settings_away_excused").performScrollTo().assertIsSelected()
        composeRule.onNodeWithTag("measurement_settings_save").performClick()
        composeRule.runOnIdle {
            assertEquals(listOf(RewardPeriod(firstWeight, currentHabitDate().minusDays(1))), weight.weekRewards!!.periods)
            assertEquals(seeded.weekRewards!!.maxPasses, weight.weekRewards!!.maxPasses)
        }

        openWeightsPage()
        openPart("streak")
        composeRule.onNodeWithTag("measurement_settings_rewards").performScrollTo().assertIsOff().performClick()
        composeRule.onNodeWithTag("measurement_settings_max_passes_value").performScrollTo().assertTextEquals("7")
        composeRule.onNodeWithTag("measurement_settings_save").performClick()
        composeRule.runOnIdle { assertEquals(listOf(RewardPeriod(firstWeight)), weight.weekRewards!!.periods) }
    }

    /** The reminder's checkpoint takes its time in the three forms a checkpoint has, and Save stores it. */
    @Test
    fun theReminderComesAtTheTimeChosen() {
        setApp()
        composeRule.onNodeWithTag("home_settings").performClick()
        openWeightsPage()
        openPart("reminders")
        composeRule.onNodeWithTag("measurement_settings_reminder_time_after_wake").performScrollTo().assertIsSelected()
        composeRule.onNodeWithTag("measurement_settings_reminder_offset_more").performScrollTo().performClick()
        composeRule.onNodeWithTag("measurement_settings_reminder_time_fixed").performScrollTo().performClick()
        composeRule.onNodeWithTag("measurement_settings_reminder_time").performScrollTo().assertTextEquals("12:00")
        composeRule.runOnIdle { assertEquals(seeded, weight) }

        composeRule.onNodeWithTag("measurement_settings_save").performClick()
        composeRule.runOnIdle { assertEquals(Checkpoint("day", CheckpointTime.Fixed(LocalTime.NOON), remind = true), weight.checkpoint) }
        composeRule.onNodeWithTag("measurement_settings").assertDoesNotExist()
    }

    /** One Save stores a Look change and the reminder switched off, both in the configuration, and returns to the list. */
    @Test
    fun oneSaveStoresALookChangeAndAReminderChange() {
        setApp()
        composeRule.onNodeWithTag("home_settings").performClick()
        openWeightsPage()
        composeRule.onNodeWithTag("measurement_settings_hue_plum").performScrollTo().performClick()
        openPart("reminders")
        composeRule.onNodeWithTag("measurement_settings_reminder").performScrollTo().assertIsOn().performClick()
        composeRule.onNodeWithTag("measurement_settings_reminder").assertIsOff()
        composeRule.runOnIdle { assertEquals(seeded, weight) }

        composeRule.onNodeWithTag("measurement_settings_save").performClick()
        composeRule.runOnIdle {
            assertEquals(ViewHue.Plum, weight.hue)
            assertFalse(weight.checkpoint.remind)
        }
        composeRule.onNodeWithTag("measurement_settings").assertDoesNotExist()
        composeRule.onNodeWithTag("settings_group_measurement_weight").performScrollTo().assertIsDisplayed()
    }

    /**
     * The store refuses the save. Nothing is stored, the Look change and the reminder alike; the
     * page stays with what went wrong and both still there to save, and leaving asks about them.
     * Once the store works, Save stores both and returns to the list.
     */
    @Test
    fun aRefusedSaveStoresNothingAndKeepsThePageOpenWithItsError() {
        setApp()
        composeRule.onNodeWithTag("home_settings").performClick()
        openWeightsPage()
        composeRule.onNodeWithTag("measurement_settings_hue_navy").performScrollTo().performClick()
        openPart("reminders")
        composeRule.onNodeWithTag("measurement_settings_reminder").performScrollTo().assertIsOn().performClick()

        storeFails = true
        composeRule.onNodeWithTag("measurement_settings_save").performClick()
        composeRule.onNodeWithTag("measurement_settings_error").assertIsDisplayed().assertTextEquals(appString(R.string.settings_error_view))
        composeRule.onNodeWithTag("measurement_settings").assertIsDisplayed()
        composeRule.runOnIdle { assertEquals(seeded, weight) }
        composeRule.onNodeWithTag("measurement_settings_reminder").assertIsOff()
        composeRule.onNodeWithTag("measurement_settings_save").assertIsEnabled()

        // Leaving asks, as the changes would be lost; staying keeps them.
        composeRule.onNodeWithTag("top_back").performClick()
        composeRule.onNodeWithTag("measurement_settings_discard_dialog").assertIsDisplayed()
        composeRule.onNodeWithTag("measurement_settings_discard_dialog_stay").performClick()
        composeRule.onNodeWithTag("measurement_settings_reminder").assertIsOff()

        storeFails = false
        composeRule.onNodeWithTag("measurement_settings_save").performClick()
        composeRule.runOnIdle {
            assertEquals(ViewHue.Navy, weight.hue)
            assertFalse(weight.checkpoint.remind)
        }
        composeRule.onNodeWithTag("measurement_settings").assertDoesNotExist()
        composeRule.onNodeWithTag("settings_group_measurement_weight").performScrollTo().assertIsDisplayed()
    }

    /**
     * Weight's unit and step are changed on its Unit part and stored by the page's one Save. Its
     * records stay the numbers they were. From then on its screen and Statistics speak as a
     * measurement a person made does, with its name and the new unit, and say kilograms nowhere;
     * its buttons take the new step, and its field takes no number typed with "kg".
     */
    @Test
    fun weightInPoundsSaysItsUnitOnItsScreenAndInStatisticsAndConvertsNothing() {
        val records = listOf(MeasurementLog(WeightMeasurementId, currentDateKey(), 2L, 181.5), MeasurementLog(WeightMeasurementId, "2026-01-05", 1L, 183.5))
        state = state.copy(measurementLogs = records)
        setApp()
        composeRule.onNodeWithTag("home_settings").performClick()
        openWeightsPage()
        openPart("unit")

        // Text that is no step stays in its field, says so and holds Save back; nothing is stored in its place.
        composeRule.onNodeWithTag("measurement_settings_unit").performScrollTo().performTextReplacement("lb")
        composeRule.onNodeWithTag("measurement_settings_step").performScrollTo().performTextReplacement("0.125")
        composeRule.onNodeWithTag("measurement_settings_step").assertInputEquals("0.125").assert(refused)
        composeRule.onNodeWithTag("measurement_settings_save").assertIsNotEnabled()
        composeRule.onNodeWithTag("measurement_settings_step").performTextReplacement("0.5")
        composeRule.onNodeWithTag("measurement_settings_step").assert(!refused)
        composeRule.runOnIdle { assertEquals(seeded, weight) }

        Espresso.closeSoftKeyboard()
        composeRule.onNodeWithTag("measurement_settings_save").assertIsEnabled().performClick()
        composeRule.runOnIdle {
            assertEquals(seeded.copy(unit = "lb", step = MeasurementStep("0.5")), weight)
            // The unit is a label: no stored number was converted.
            assertEquals(records, state.measurementLogs)
        }
        composeRule.onNodeWithTag("measurement_settings").assertDoesNotExist()

        // Its screen: its name and the unit, steps of 0.5, and nothing that says kilograms.
        composeRule.onNodeWithTag("top_back").performClick()
        composeRule.onNodeWithTag("home_measurement_weight").performClick()
        composeRule.onNodeWithText(appString(R.string.measurement_field_label_with_unit, "Weight", isolated("lb"))).assertExists()
        composeRule.onAllNodes(hasText("kg", substring = true, ignoreCase = true)).assertCountEquals(0)
        composeRule.onAllNodes(hasContentDescription("kilogram", substring = true, ignoreCase = true)).assertCountEquals(0)
        composeRule.onNodeWithTag("measurement_value").performScrollTo().assertInputEquals(number(181.5))
        composeRule.onNodeWithTag("measurement_increment").performScrollTo().performClick()
        composeRule.onNodeWithTag("measurement_value").assertInputEquals(number(182.0))

        // "82 kg" is no number in pounds: it is refused in the words of Weight's kind, and nothing is stored.
        composeRule.onNodeWithTag("measurement_value").performTextReplacement("82 kg")
        Espresso.closeSoftKeyboard()
        composeRule.onNodeWithTag("measurement_save").performScrollTo().performClick()
        composeRule.onNodeWithText(appString(R.string.measurement_error_invalid)).assertIsDisplayed()
        composeRule.runOnIdle { assertEquals(records, state.measurementLogs) }
        composeRule.onNodeWithTag("measurement_value").performTextReplacement("180")
        composeRule.onNodeWithTag("measurement_save").performScrollTo().performClick()
        composeRule.runOnIdle { assertEquals(180.0, state.recordsOf("weight").first { it.dateKey == currentDateKey() }.value, 0.0) }

        // Statistics: the caption says the unit, and nothing there says kilograms either.
        composeRule.onNodeWithTag("top_back").performClick()
        composeRule.onNodeWithTag("home_statistics").performClick()
        composeRule.onNodeWithTag("statistics_measurement_weight").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(appString(R.string.measurement_statistics_caption, "Weight", appString(R.string.measurement_value_with_unit, number(180.0), isolated("lb")))).assertExists()
        composeRule.onAllNodes(hasText("kg", substring = true, ignoreCase = true)).assertCountEquals(0)
        composeRule.onAllNodes(hasContentDescription("kilogram", substring = true, ignoreCase = true)).assertCountEquals(0)
    }

    /**
     * Weight's reminder asks for a number in Weight's unit. One on show when another unit is saved
     * is said again, quietly, in the new one by the reconcile that follows the save, and says of a
     * reply it cannot read what a measurement of its kind says. The save goes through the commands
     * the app saves with, and the reminder through the coordinator every view reminds through,
     * wired as the app wires them.
     */
    @Test
    fun aReminderOnShowIsSaidAgainInTheUnitSaved() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        // The suite grants no permission on its own, and the app creates the channel on start; the test host is not the app's activity.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        }
        ReminderNotificationChannel.create(context)
        notificationManager.cancelAll()
        val key = slotKey(ReminderViewKind.Measurement, WeightMeasurementId, seeded.checkpoint.id)
        fun onShow() = notificationManager.activeNotifications.firstOrNull { it.tag == key }?.notification
        fun prompt() = requireNotNull(onShow()).actions.single().remoteInputs.single().label.toString()
        val records = object : TrackerRepository {
            override fun load(): AppState = state
            override fun save(state: AppState): Boolean = true.also { this@WeightSettingsFlowTest.state = state }
            override fun update(transform: (AppState) -> AppState): TrackerUpdateResult = TrackerUpdateResult.Updated(transform(state).also { state = it })
        }
        val notification = ChecklistReminderNotification(context)
        val reminders = ChecklistReminderCoordinator(
            trackerRepository = records,
            featureVisibility = FeatureVisibilitySource { FeatureVisibilitySettings() },
            gateway = AndroidChecklistReminderGateway(context),
            runtime = PreferencesChecklistReminderRuntime(context),
            notifier = notification,
            loggedNotifier = notification,
            reminderSettings = { ReminderSettings() },
        )
        val commands = MeasurementCommands(
            repository = records,
            reconcile = { reminders.reconcile().also { reminders.refreshPosted() } },
            exactAlarmsAllowed = { true },
            refresh = {},
            stored = {},
            hidden = { emptySet() },
            forgetHidden = {},
        )
        val today = ReminderDays(currentHabitDate(), currentHabitDate())

        try {
            reminders.onReminderDue(ReminderViewKind.Measurement, WeightMeasurementId, seeded.checkpoint.id)
            composeRule.waitUntil(timeoutMillis = 5_000) { onShow() != null }
            assertEquals(appString(R.string.reminder_action_log), requireNotNull(onShow()).actions.single().title.toString())
            assertEquals(appString(R.string.measurement_field_label_with_unit, "Weight", isolated("kg")), prompt())

            setApp(onMeasurementSave = commands::save)
            composeRule.onNodeWithTag("home_settings").performClick()
            openWeightsPage()

            // A save that leaves the unit as it is leaves the reminder asking as it did.
            composeRule.onNodeWithTag("measurement_settings_hue_plum").performScrollTo().performClick()
            composeRule.onNodeWithTag("measurement_settings_save").performClick()
            composeRule.onNodeWithTag("measurement_settings").assertDoesNotExist()
            assertEquals(appString(R.string.measurement_field_label_with_unit, "Weight", isolated("kg")), prompt())

            openWeightsPage()
            openPart("unit")
            composeRule.onNodeWithTag("measurement_settings_unit").performScrollTo().performTextReplacement("lb")
            Espresso.closeSoftKeyboard()
            composeRule.onNodeWithTag("measurement_settings_save").assertIsEnabled().performClick()
            composeRule.runOnIdle { assertEquals("lb", weight.unit) }

            // Said again, it asks in the new unit, offers "Log it" as any measurement in its own unit does, and refuses a reply in the words of Weight's kind.
            val inPounds = appString(R.string.measurement_field_label_with_unit, "Weight", isolated("lb"))
            composeRule.waitUntil(timeoutMillis = 5_000) { onShow()?.actions?.singleOrNull()?.remoteInputs?.singleOrNull()?.label?.toString() == inPounds }
            assertEquals(appString(R.string.reminder_action_log), requireNotNull(onShow()).actions.single().title.toString())
            assertFalse(reminders.onReply(WeightMeasurementId, "82 kg", today))
            composeRule.waitUntil(timeoutMillis = 5_000) {
                onShow()?.extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString() == appString(R.string.measurement_error_invalid)
            }
            assertEquals(inPounds, prompt())
            composeRule.runOnIdle { assertTrue(state.measurementLogs.isEmpty()) }

            // A number is stored as Weight's for the day asked about, and the reminder goes.
            assertTrue(reminders.onReply(WeightMeasurementId, "82", today))
            composeRule.waitUntil(timeoutMillis = 5_000) { onShow() == null }
            composeRule.runOnIdle { assertEquals(listOf(82.0), state.measurementLogs.map { it.value }) }
        } finally {
            reminders.reconcile()
            AndroidChecklistReminderGateway(context).cancel(key)
            notificationManager.cancelAll()
        }
    }
}

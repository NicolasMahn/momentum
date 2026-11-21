package com.example.workouttracker.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.workouttracker.core.text.resolve
import com.example.workouttracker.core.time.currentHabitDate
import com.example.workouttracker.reminder.ReminderDays
import com.example.workouttracker.reminder.loggedReminderMessage
import com.example.workouttracker.core.ui.theme.WorkoutTrackerTheme
import com.example.workouttracker.domain.AppState
import com.example.workouttracker.domain.trackerRef
import com.example.workouttracker.domain.checklist.Dated
import com.example.workouttracker.domain.checklist.ViewHue
import com.example.workouttracker.domain.checklist.ViewIcon
import com.example.workouttracker.domain.checklist.WhenAway
import com.example.workouttracker.domain.holiday.DayRange
import com.example.workouttracker.domain.holiday.HolidaySchedule
import com.example.workouttracker.domain.measurement.withWeight
import com.example.workouttracker.domain.mo.MoLedger
import com.example.workouttracker.domain.compatibility.MoNeed
import com.example.workouttracker.domain.mo.MoDayMark
import com.example.workouttracker.domain.mo.MoDayRecord
import com.example.workouttracker.domain.mo.habitStreak
import com.example.workouttracker.domain.mo.only
import com.example.workouttracker.domain.mo.reflectionAbout
import com.example.workouttracker.domain.compatibility.eatingQuestions
import com.example.workouttracker.domain.questions.testEatingQuestions
import com.example.workouttracker.domain.questions.withEating
import com.example.workouttracker.domain.settings.FeatureVisibilitySettings
import com.example.workouttracker.feature.weight.transcript
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import com.example.workouttracker.domain.time.withReading
import com.example.workouttracker.domain.activity.withActivity

/**
 * Eating's settings through the app as a person meets them: the page a made questions view has,
 * with its questions fixed, its reminder a time of the day, and the switch for the streak; Delete,
 * which asks first naming its answered days; and one Save over its configuration, the reminder
 * among it. Nothing is stored until Save, and a changed number or holiday rule judges no earlier
 * day again.
 */
@RunWith(RobolectricTestRunner::class)
class EatingSettingsPageTest {
    @get:Rule
    val compose = createComposeRule()

    private val today = currentHabitDate()
    private val seeded = testEatingQuestions().let { it.copy(checkpoint = it.checkpoint.copy(remind = true)) }
    private var state by mutableStateOf(AppState().withWeight().withEating(seeded).withReading().withActivity())
    private var holidays = HolidaySchedule()
    private var visibility = FeatureVisibilitySettings(disabledFeatures = emptySet())

    /** The token rules have applied for as long as the histories here go back. */
    private val rewardStart = today.minusDays(60)

    private val eating get() = requireNotNull(state.eatingQuestions)

    @Composable
    private fun App() {
        WorkoutTrackerTheme {
            MomentumApp(
                state = state,
                rewardStart = rewardStart,
                onStateChange = { state = it; true },
                onActivitySave = ActivitySaveNotExpected,
                featureVisibility = visibility,
                holidaySchedule = holidays,
            )
        }
    }

    private fun click(tag: String) {
        compose.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
    }

    private fun type(tag: String, text: String) {
        compose.onNodeWithTag(tag).performTextReplacement(text)
        compose.waitForIdle()
    }

    private fun line(tag: String): String = compose.transcript().single { it.startsWith("[$tag]") }

    private fun names(tag: String, name: String) =
        compose.onNode(hasTestTag(tag) and hasAnyDescendant(hasText(name)), useUnmergedTree = true).assertExists()

    private fun openPage() {
        click("home_settings")
        click("settings_group_questions_eating")
        compose.onNodeWithTag("questions_settings").assertExists()
    }

    @Test
    fun thePageHasTheFourPartsOfAQuestionsViewWithWhatEatingKeepsOfItsOwn() {
        compose.setContent { App() }
        openPage()

        compose.onNodeWithTag("questions_settings_tab_look").assertTextEquals("Look").assertIsSelected()
        compose.onNodeWithTag("questions_settings_tab_questions").assertTextEquals("Questions")
        compose.onNodeWithTag("questions_settings_tab_reminders").assertTextEquals("Reminders")
        compose.onNodeWithTag("questions_settings_tab_streak").assertTextEquals("Streak")
        assertEquals("[questions_settings_name] field=\"Eating\"", line("questions_settings_name"))
        compose.onNodeWithTag("questions_settings_icon_food").assertIsSelected()
        compose.onNodeWithTag("questions_settings_hue_orange").assertIsSelected()

        // Stored prompts and answers are editable user data, including this preset's questions.
        click("questions_settings_tab_questions")
        compose.onNodeWithTag("questions_settings_about_yesterday").assertIsSelected().assertIsEnabled()
        compose.onNodeWithTag("questions_settings_about_today").assertIsEnabled()
        compose.onNodeWithTag("questions_settings_built_in").assertDoesNotExist()
        compose.onNodeWithTag("questions_settings_question_quality_text").assertIsEnabled()
        assertTrue(line("questions_settings_question_quality_text"), "field=\"How was the day?\"" in line("questions_settings_question_quality_text"))
        assertEquals(
            (1..5).map { "Level $it" },
            compose.transcript().filter { it.startsWith("[questions_settings_question_quality_answer_") && "field=\"" in it }.map { it.substringAfter("field=\"").substringBefore("\"") },
        )
        compose.onNodeWithTag("questions_settings_question_amount_answer_much_more").assertIsEnabled()
        assertEquals(5, compose.transcript().count { it.startsWith("[questions_settings_question_amount_answer_") && "field=\"" in it })
        listOf("questions_settings_question_quality_remove", "questions_settings_question_quality_add_answer", "questions_settings_question_quality_more_options", "questions_settings_add_question")
            .forEach { compose.onNodeWithTag(it).assertExists() }

        // Its reminder at its checkpoint, fifteen minutes after waking, about yesterday, which can be moved as a made view's can.
        click("questions_settings_tab_reminders")
        compose.onNodeWithTag("questions_settings_reminder").assertIsOn()
        compose.onNodeWithTag("questions_settings_reminder_time_after_wake").assertIsSelected()
        compose.onNodeWithTag("settings_eating_reminder").assertDoesNotExist()
        val said = loggedReminderMessage(eating, ReminderDays(today, today.minusDays(1)))
        val resources = ApplicationProvider.getApplicationContext<Context>().resources
        assertEquals("[questions_settings_reminder_preview_title] text=\"${said.title.resolve(resources)}\"", line("questions_settings_reminder_preview_title"))
        assertEquals("[questions_settings_reminder_preview_text] text=\"${said.text.resolve(resources)}\"", line("questions_settings_reminder_preview_text"))

        // It counts toward the streak, as it always has, with its bank's two numbers, and its holiday rule.
        click("questions_settings_tab_streak")
        compose.onNodeWithTag("questions_settings_rewards").assertIsOn()
        compose.onNodeWithTag("questions_settings_max_passes_value").assertTextEquals("3")
        compose.onNodeWithTag("questions_settings_every_kept_days_value").assertTextEquals("7 kept days")
        compose.onNodeWithTag("questions_settings_away_due").assertIsSelected()

        // Delete asks first, naming it and that nothing is answered yet; cancelled, it stays.
        compose.onNodeWithTag("questions_settings_delete_reason").assertDoesNotExist()
        click("questions_settings_delete")
        compose.onNodeWithTag("questions_settings_delete_dialog").assertExists()
        assertTrue(compose.transcript().any { "Delete Eating?" in it })
        click("questions_settings_delete_cancel")
        compose.onNodeWithTag("questions_settings_delete_dialog").assertDoesNotExist()
        // Untouched, there is nothing to save, and back leaves at once.
        compose.onNodeWithTag("questions_settings_save").assertIsNotEnabled()
        click("top_back")
        compose.onNodeWithTag("questions_settings_discard_dialog").assertDoesNotExist()
        compose.onNodeWithTag("questions_settings").assertDoesNotExist()
        assertEquals(seeded, eating)
    }

    /** Renamed and recolored, Eating is stored so on Save, and nothing that judges a day moved. A cleared name holds Save back. */
    @Test
    fun aRenamedAndRecoloredEatingIsStoredOnSaveAndShownByItsName() {
        compose.setContent { App() }
        openPage()

        click("questions_settings_icon_heart")
        click("questions_settings_hue_plum")
        compose.onNodeWithTag("questions_settings_save").assertIsEnabled()
        type("questions_settings_name", "")
        compose.onNodeWithTag("questions_settings_save").assertIsNotEnabled()
        type("questions_settings_name", "Food")
        assertEquals(seeded, eating)

        click("questions_settings_save")
        assertEquals(seeded.copy(label = "Food", icon = ViewIcon.Heart, hue = ViewHue.Plum), eating)

        // Saved, the page returned to the list, whose row wears the name, as its tile and its screen's header do.
        compose.onNodeWithTag("questions_settings").assertDoesNotExist()
        names("settings_group_questions_eating", "Food")
        click("top_back")
        names("home_questions_eating", "Food")
        click("home_questions_eating")
        assertTrue(compose.transcript().toString(), "text=\"Food\"" in compose.transcript())
        click("top_back")

        // Opened again, the page shows what was stored and has nothing left to save.
        openPage()
        compose.onNodeWithTag("questions_settings_icon_heart").assertIsSelected()
        compose.onNodeWithTag("questions_settings_hue_plum").assertIsSelected()
        compose.onNodeWithTag("questions_settings_save").assertIsNotEnabled()
    }

    /**
     * Four weeks of Eating with one day missed and one away day without a reflection. A bank of
     * one, a token for every three kept days and "Not due" on holiday, saved on the page today,
     * are dated from today: every earlier day is judged as it was, by the walk as each of those
     * days saw it and by Mo's ledger, and today the new numbers apply.
     */
    @Test
    fun aChangedBankAndHolidayRuleAreDatedFromTodayAndJudgeNoEarlierDayAgain() {
        fun day(ago: Int) = today.minusDays(ago.toLong())
        val eatingOnly = only(MoNeed.Eating)
        visibility = eatingOnly
        holidays = HolidaySchedule(listOf(DayRange(day(12), day(12))))
        // A day is kept by the reflection about the day before it.
        val before = AppState(questionEntries = (1..28).filter { it != 5 && it != 12 }.map { reflectionAbout(day(it + 1)) }).withWeight().withEating().withReading().withActivity()
        state = before
        compose.setContent { App() }
        openPage()

        click("questions_settings_tab_streak")
        repeat(2) { click("questions_settings_max_passes_less") }
        compose.onNodeWithTag("questions_settings_max_passes_value").assertTextEquals("1")
        compose.onNodeWithTag("questions_settings_max_passes_less").assertIsNotEnabled()
        repeat(4) { click("questions_settings_every_kept_days_less") }
        compose.onNodeWithTag("questions_settings_every_kept_days_value").assertTextEquals("3 kept days")
        click("questions_settings_away_excused")
        compose.onNodeWithTag("questions_settings_away_excused").assertIsSelected()
        assertEquals(before, state)

        click("questions_settings_save")
        compose.onNodeWithTag("questions_settings").assertDoesNotExist()
        assertEquals(Dated(listOf(Dated.Revision(3), Dated.Revision(1, today))), eating.rewards?.maxPasses)
        assertEquals(Dated(listOf(Dated.Revision(7), Dated.Revision(3, today))), eating.rewards?.everyKeptDays)
        assertEquals(Dated(listOf(Dated.Revision(WhenAway.Due), Dated.Revision(WhenAway.Excused, today))), eating.whenAway)
        // Only the configuration changed: every record is as it was.
        assertEquals(before.copy(questions = state.questions), state)

        (1..28).forEach { ago ->
            assertEquals("$ago days ago", habitStreak(before, eatingOnly, day(ago), holidays, rewardStart), habitStreak(state, eatingOnly, day(ago), holidays, rewardStart))
        }
        val was = habitStreak(before, eatingOnly, today, holidays, rewardStart)
        val now = habitStreak(state, eatingOnly, today, holidays, rewardStart)
        assertEquals(was.history.filter { it.date < today }, now.history.filter { it.date < today })
        assertEquals(
            MoLedger.of(before, eatingOnly, today, holidays, rewardStart).history(MoNeed.Eating.view.trackerRef).filterKeys { it < today },
            MoLedger.of(state, eatingOnly, today, holidays, rewardStart).history(MoNeed.Eating.view.trackerRef).filterKeys { it < today },
        )
        // The away day without a reflection stays the gap a token covered while Eating was still due; excused, it would have been no gap.
        assertEquals(MoDayRecord(day(12), MoDayMark.Protected, coveredTrackers = setOf(MoNeed.Eating.view.trackerRef)), now.history.single { it.date == day(12) })
        // Today the bank holds no more than one and counts to three.
        assertEquals(2, was.participantBanks[MoNeed.Eating.view.trackerRef])
        assertEquals(3, was.participantCapacities[MoNeed.Eating.view.trackerRef])
        assertEquals(1, now.participantBanks[MoNeed.Eating.view.trackerRef])
        assertEquals(1, now.participantCapacities[MoNeed.Eating.view.trackerRef])
        assertEquals(7, was.participantNextToken.getValue(MoNeed.Eating.view.trackerRef).goal)
        val toNext = now.participantNextToken.getValue(MoNeed.Eating.view.trackerRef)
        assertEquals(3, toNext.goal)

        // The token page shows the new bank and the new goal.
        click("top_back")
        click("home_streak")
        compose.onNode(hasContentDescription("Eating: 1 token. full. next token: ${toNext.done}/3 days")).assertExists()
    }

    /** One Save stores a Look change and the reminder switched off, both in the configuration, and returns to the list. */
    @Test
    fun oneSaveStoresTheLookAndTheReminder() {
        compose.setContent { App() }
        openPage()
        click("questions_settings_hue_plum")
        click("questions_settings_tab_reminders")
        click("questions_settings_reminder")
        compose.onNodeWithTag("questions_settings_reminder").assertIsOff()
        assertEquals(seeded, eating)

        click("questions_settings_save")
        assertEquals(ViewHue.Plum, eating.hue)
        assertFalse(eating.checkpoint.remind)
        compose.onNodeWithTag("questions_settings").assertDoesNotExist()
        compose.onNodeWithTag("settings_group_questions_eating").assertExists()
    }

    /** The draft survives process death with the part on show, and Save then stores what it holds. */
    @Test
    fun thePageSurvivesProcessDeath() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent { App() }
        openPage()
        type("questions_settings_name", "Food")
        click("questions_settings_tab_reminders")
        click("questions_settings_reminder")
        click("questions_settings_tab_streak")
        click("questions_settings_max_passes_more")

        restoration.emulateSavedInstanceStateRestore()
        compose.waitForIdle()

        compose.onNodeWithTag("questions_settings_tab_streak").assertIsSelected()
        compose.onNodeWithTag("questions_settings_max_passes_value").assertTextEquals("4")
        click("questions_settings_tab_reminders")
        compose.onNodeWithTag("questions_settings_reminder").assertIsOff()
        click("questions_settings_tab_look")
        assertEquals("[questions_settings_name] field=\"Food\"", line("questions_settings_name"))
        assertEquals(seeded, eating)

        click("questions_settings_save")
        assertEquals("Food", eating.label)
        assertEquals(4, eating.rewards?.maxPasses?.on(today))
        assertFalse(eating.checkpoint.remind)
    }

    @Test
    @Config(qualifiers = "de")
    fun thePageSpeaksGerman() {
        compose.setContent { App() }
        openPage()

        compose.onNodeWithTag("questions_settings_tab_look").assertTextEquals("Aussehen")
        compose.onNodeWithTag("questions_settings_tab_questions").assertTextEquals("Fragen")
        click("questions_settings_tab_questions")
        compose.onNodeWithTag("questions_settings_built_in").assertDoesNotExist()
        assertTrue(line("questions_settings_question_quality_text"), "field=\"How was the day?\"" in line("questions_settings_question_quality_text"))
        compose.onNodeWithTag("questions_settings_about_yesterday").assertTextEquals("Gestern").assertIsSelected()
        click("questions_settings_tab_streak")
        compose.onNodeWithTag("questions_settings_every_kept_days_value").assertTextEquals("7 erledigte Tage")
        compose.onNodeWithTag("questions_settings_rewards").assertIsOn()
        compose.onNodeWithTag("questions_settings_away_due").assertTextEquals("Weiter fällig").assertIsSelected()
        compose.onNodeWithTag("questions_settings_delete_reason").assertDoesNotExist()
    }
}

package de.nicolasmahn.momentum.app

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
import de.nicolasmahn.momentum.core.time.currentHabitDate
import de.nicolasmahn.momentum.core.ui.theme.WorkoutTrackerTheme
import de.nicolasmahn.momentum.domain.AppState
import de.nicolasmahn.momentum.domain.trackerRef
import de.nicolasmahn.momentum.domain.checklist.Dated
import de.nicolasmahn.momentum.domain.checklist.ViewHue
import de.nicolasmahn.momentum.domain.checklist.ViewIcon
import de.nicolasmahn.momentum.domain.checklist.WhenAway
import de.nicolasmahn.momentum.domain.holiday.DayRange
import de.nicolasmahn.momentum.domain.holiday.HolidaySchedule
import de.nicolasmahn.momentum.domain.measurement.withWeight
import de.nicolasmahn.momentum.domain.mo.MoDayMark
import de.nicolasmahn.momentum.domain.mo.MoDayRecord
import de.nicolasmahn.momentum.domain.mo.MoLedger
import de.nicolasmahn.momentum.domain.compatibility.MoNeed
import de.nicolasmahn.momentum.domain.mo.MoTokenProgress
import de.nicolasmahn.momentum.domain.mo.habitStreak
import de.nicolasmahn.momentum.domain.mo.only
import de.nicolasmahn.momentum.domain.questions.withEating
import de.nicolasmahn.momentum.domain.settings.FeatureVisibilitySettings
import de.nicolasmahn.momentum.domain.time.DefaultTokenSteps
import de.nicolasmahn.momentum.domain.compatibility.ReadingTimeId
import de.nicolasmahn.momentum.domain.time.TimeLog
import de.nicolasmahn.momentum.domain.time.TimeRewards
import de.nicolasmahn.momentum.domain.time.TokenStep
import de.nicolasmahn.momentum.domain.time.TokenSteps
import de.nicolasmahn.momentum.domain.compatibility.readingTime
import de.nicolasmahn.momentum.domain.time.testReadingTime
import de.nicolasmahn.momentum.domain.time.withReading
import de.nicolasmahn.momentum.domain.activity.withActivity
import de.nicolasmahn.momentum.feature.weight.transcript
import java.time.DayOfWeek
import java.time.temporal.TemporalAdjusters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A seeded timer uses the shared editor and preserves dated goal, reward and holiday rules. */
@RunWith(RobolectricTestRunner::class)
class TimerPresetSettingsPageTest {
    @get:Rule
    val compose = createComposeRule()

    private val today = currentHabitDate()
    private val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    private val seeded = testReadingTime().let { it.copy(checkpoint = it.checkpoint.copy(remind = true)) }
    private var state by mutableStateOf(AppState().withWeight().withEating().withReading(seeded).withActivity())
    private var holidays = HolidaySchedule()
    private var visibility = FeatureVisibilitySettings(disabledFeatures = emptySet())

    /** The token rules have applied for as long as the histories here go back. */
    private val rewardStart = today.minusDays(60)

    private val reading get() = requireNotNull(state.readingTime)

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
        click("settings_group_time_reading")
        compose.onNodeWithTag("time_settings").assertExists()
    }

    @Test
    fun theSeededTimerUsesTheSameSettingsPartsAndWordingAsOtherTimers() {
        compose.setContent { App() }
        openPage()

        compose.onNodeWithTag("time_settings_tab_look").assertTextEquals("Look").assertIsSelected()
        compose.onNodeWithTag("time_settings_tab_goal").assertTextEquals("Goal")
        compose.onNodeWithTag("time_settings_tab_reminders").assertTextEquals("Reminders")
        compose.onNodeWithTag("time_settings_tab_streak").assertTextEquals("Streak")
        assertEquals("[time_settings_name] field=\"Reading\"", line("time_settings_name"))
        compose.onNodeWithTag("time_settings_icon_book").assertIsSelected()
        compose.onNodeWithTag("time_settings_hue_yellow").assertIsSelected()

        // Its minutes a week, and what a token takes.
        click("time_settings_tab_goal")
        assertEquals("[time_settings_goal] field=\"60\"", line("time_settings_goal"))
        compose.onNodeWithTag("time_settings_goal_note").assertTextEquals("60 minutes in a week earn a token. More minutes earn up to 2 tokens a week.")

        // Its reminder once a day at its checkpoint's time, which can be chosen as a made view's can.
        click("time_settings_tab_reminders")
        compose.onNodeWithTag("time_settings_reminder").assertIsOn()
        compose.onNodeWithTag("time_settings_reminder_time").assertTextEquals("18:00")
        compose.onNodeWithTag("settings_reading_reminder").assertDoesNotExist()
        // The reminder uses its saved name and the shared timer wording.
        assertEquals("[time_settings_reminder_preview_title] text=\"Reading\"", line("time_settings_reminder_preview_title"))
        assertEquals("[time_settings_reminder_preview_text] text=\"60 minutes to go this week\"", line("time_settings_reminder_preview_text"))

        // It counts toward the streak, as it always has, with its bank's size and its holiday rule; it earns by its goal.
        click("time_settings_tab_streak")
        compose.onNodeWithTag("time_settings_rewards").assertIsOn()
        compose.onNodeWithTag("time_settings_every_kept_days_value").assertDoesNotExist()
        compose.onNodeWithTag("time_settings_max_passes_value").assertTextEquals("3")
        compose.onNodeWithTag("time_settings_away_due").assertIsSelected()

        // Delete asks first, naming it; cancelled, it stays.
        compose.onNodeWithTag("time_settings_delete_reason").assertDoesNotExist()
        click("time_settings_delete")
        assertTrue(compose.transcript().any { "Delete Reading?" in it })
        click("time_settings_delete_cancel")
        compose.onNodeWithTag("time_settings_delete_dialog").assertDoesNotExist()
        // Untouched, there is nothing to save, and back leaves at once.
        compose.onNodeWithTag("time_settings_save").assertIsNotEnabled()
        click("top_back")
        compose.onNodeWithTag("time_settings_discard_dialog").assertDoesNotExist()
        compose.onNodeWithTag("time_settings").assertDoesNotExist()
        assertEquals(seeded, reading)
    }

    /** Renamed, recolored and set to Plain, Reading is stored so on Save, and nothing that judges a week moved. A cleared name holds Save back. */
    @Test
    fun aRenamedAndRecoloredTimerIsStoredOnSaveAndShownByItsName() {
        compose.setContent { App() }
        openPage()

        click("time_settings_icons_more")
        click("time_settings_icon_heart")
        click("time_settings_hue_plum")
        // Its flavor stands on Reading, Mo with his book, and Plain is the other choice.
        compose.onNodeWithTag("time_settings_motif_reading").assertIsSelected()
        compose.onNodeWithTag("time_settings_motif_pills").assertDoesNotExist()
        click("time_settings_motif_none")
        compose.onNodeWithTag("time_settings_save").assertIsEnabled()
        type("time_settings_name", "")
        compose.onNodeWithTag("time_settings_save").assertIsNotEnabled()
        type("time_settings_name", "Books")
        assertEquals(seeded, reading)

        click("time_settings_save")
        assertEquals(seeded.copy(label = "Books", icon = ViewIcon.Heart, hue = ViewHue.Plum, mo = seeded.mo.copy(motif = null)), reading)

        // Saved, the page returned to the list, whose row wears the name, as its tile and its screen's header do.
        compose.onNodeWithTag("time_settings").assertDoesNotExist()
        names("settings_group_time_reading", "Books")
        click("top_back")
        names("home_time_reading", "Books")
        click("home_time_reading")
        assertTrue(compose.transcript().toString(), "text=\"Books\"" in compose.transcript())
        click("top_back")

        // Opened again, the page shows what was stored and has nothing left to save.
        openPage()
        // An icon chosen from behind More opens the whole catalogue again, so it is in sight.
        compose.onNodeWithTag("time_settings_icon_heart").assertIsSelected()
        compose.onNodeWithTag("time_settings_hue_plum").assertIsSelected()
        compose.onNodeWithTag("time_settings_motif_none").assertIsSelected()
        compose.onNodeWithTag("time_settings_save").assertIsNotEnabled()
    }

    /** Text that is no number of minutes a week can ask stays in the field and holds Save back, from every part, until it is one. */
    @Test
    fun aGoalThatIsNoGoalHoldsSaveBackAndTheReminderFollowsTheGoalAsTyped() {
        compose.setContent { App() }
        openPage()
        click("time_settings_hue_plum")
        click("time_settings_tab_goal")

        type("time_settings_goal", "an hour")
        assertEquals("[time_settings_goal] field=\"an hour\" error=\"A whole number from 1 to 10080\"", line("time_settings_goal"))
        compose.onNodeWithTag("time_settings_save").assertIsNotEnabled()
        click("time_settings_tab_streak")
        compose.onNodeWithTag("time_settings_save").assertIsNotEnabled()

        click("time_settings_tab_goal")
        type("time_settings_goal", "90")
        compose.onNodeWithTag("time_settings_goal_note").assertTextEquals("90 minutes in a week earn a token. More minutes earn up to 2 tokens a week.")
        compose.onNodeWithTag("time_settings_save").assertIsEnabled()
        // What the reminder would say names the goal the page holds, before it is saved.
        click("time_settings_tab_reminders")
        assertEquals("[time_settings_reminder_preview_text] text=\"90 minutes to go this week\"", line("time_settings_reminder_preview_text"))
        assertEquals(seeded, reading)
    }

    /**
     * Four weeks of Reading before this one: two hours, two hours, an hour, and fifty-five minutes
     * in a week with an away day, which was short by a gap a token covered while Reading was
     * still due on holiday. Ninety minutes a week, a bank of one, the goal as the one step and
     * "Not due" on holiday, saved on the page today, are dated from today: every earlier week is
     * judged and credited as it was, the two that earned two tokens each among them, by the walk
     * as each of its days saw it and by Mo's ledger, and from this week on the new numbers apply.
     */
    @Test
    fun aChangedGoalBankAndHolidayRuleAreDatedFromTodayAndJudgeNoEarlierWeekAgain() {
        fun week(ago: Int) = monday.minusWeeks(ago.toLong())
        val readingOnly = only(MoNeed.Reading)
        visibility = readingOnly
        val away = week(1).plusDays(2)
        holidays = HolidaySchedule(listOf(DayRange(away, away)))
        val minutes = listOf(week(4) to 120, week(3) to 120, week(2) to 60, week(1) to 55, monday to 40)
        val before = AppState(timeLogs = minutes.mapIndexed { index, (day, read) -> TimeLog(ReadingTimeId, day.toString(), index.toLong(), read) })
            .withWeight().withEating().withReading().withActivity()
        state = before
        compose.setContent { App() }
        openPage()

        click("time_settings_tab_goal")
        type("time_settings_goal", "90")
        click("time_settings_tab_streak")
        repeat(2) { click("time_settings_max_passes_less") }
        compose.onNodeWithTag("time_settings_max_passes_value").assertTextEquals("1")
        compose.onNodeWithTag("time_settings_max_passes_less").assertIsNotEnabled()
        click("time_settings_away_excused")
        compose.onNodeWithTag("time_settings_away_excused").assertIsSelected()
        click("time_settings_step_2_remove")
        click("time_settings_step_2_remove")
        compose.onNodeWithTag("time_settings_step_2_percent").assertDoesNotExist()
        assertEquals(before, state)

        click("time_settings_save")
        compose.onNodeWithTag("time_settings").assertDoesNotExist()
        assertEquals(Dated(listOf(Dated.Revision(60), Dated.Revision(90, today))), reading.goal)
        assertEquals(
            TimeRewards(maxPasses = Dated(listOf(Dated.Revision(3), Dated.Revision(1, today))), tokenSteps = Dated(listOf(Dated.Revision(DefaultTokenSteps), Dated.Revision(TokenSteps(listOf(TokenStep(100, 1))), today)))),
            reading.rewards,
        )
        assertEquals(Dated(listOf(Dated.Revision(WhenAway.Due), Dated.Revision(WhenAway.Excused, today))), reading.whenAway)
        // Only the configuration changed: every record is as it was.
        assertEquals(before.copy(time = state.time), state)

        generateSequence(week(4)) { it.plusDays(1) }.takeWhile { it < monday }.forEach { day ->
            assertEquals("$day", habitStreak(before, readingOnly, day, holidays, rewardStart), habitStreak(state, readingOnly, day, holidays, rewardStart))
        }
        val was = habitStreak(before, readingOnly, today, holidays, rewardStart)
        val now = habitStreak(state, readingOnly, today, holidays, rewardStart)
        assertEquals(was.history.filter { it.date < monday }, now.history.filter { it.date < monday })
        assertEquals(
            MoLedger.of(before, readingOnly, today, holidays, rewardStart).history(MoNeed.Reading.view.trackerRef).filterKeys { it < monday },
            MoLedger.of(state, readingOnly, today, holidays, rewardStart).history(MoNeed.Reading.view.trackerRef).filterKeys { it < monday },
        )
        // The week with the away day stays five minutes short of sixty, the gap a token covered; not due, it would have asked for less than was read.
        assertEquals(MoDayRecord(week(1).plusDays(6), MoDayMark.Protected, coveredTrackers = setOf(MoNeed.Reading.view.trackerRef)), now.history.single { it.date == week(1).plusDays(6) })
        // Today the bank holds no more than one, and this week asks for ninety.
        assertEquals(2, was.participantBanks[MoNeed.Reading.view.trackerRef])
        assertEquals(3, was.participantCapacities[MoNeed.Reading.view.trackerRef])
        assertEquals(1, now.participantBanks[MoNeed.Reading.view.trackerRef])
        assertEquals(1, now.participantCapacities[MoNeed.Reading.view.trackerRef])
        assertEquals(MoTokenProgress(40, 60), was.participantNextToken.getValue(MoNeed.Reading.view.trackerRef))
        assertEquals(MoTokenProgress(40, 90), now.participantNextToken.getValue(MoNeed.Reading.view.trackerRef))

        // The token page shows the new bank and the new goal, and so does its screen's week.
        click("top_back")
        click("home_streak")
        compose.onNode(hasContentDescription("Reading: 1 token. full. next token: 40/90 min this week")).assertExists()
        click("top_back")
        click("home_time_reading")
        compose.onNodeWithTag("time_reading_week_minutes").assertTextEquals("40 / 90 min")
    }

    /** One Save stores a Look change and the reminder switched off, both in the configuration, and returns to the list. */
    @Test
    fun oneSaveStoresTheLookAndTheReminder() {
        compose.setContent { App() }
        openPage()
        click("time_settings_hue_plum")
        click("time_settings_tab_reminders")
        click("time_settings_reminder")
        compose.onNodeWithTag("time_settings_reminder").assertIsOff()
        assertEquals(seeded, reading)

        click("time_settings_save")
        assertEquals(ViewHue.Plum, reading.hue)
        assertFalse(reading.checkpoint.remind)
        compose.onNodeWithTag("time_settings").assertDoesNotExist()
        compose.onNodeWithTag("settings_group_time_reading").assertExists()
    }

    /** The draft survives process death with the part on show, a typed goal among it, and Save then stores what it holds. */
    @Test
    fun thePageSurvivesProcessDeath() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent { App() }
        openPage()
        type("time_settings_name", "Books")
        click("time_settings_tab_goal")
        type("time_settings_goal", "45")
        click("time_settings_tab_reminders")
        click("time_settings_reminder")
        click("time_settings_tab_streak")
        click("time_settings_max_passes_more")

        restoration.emulateSavedInstanceStateRestore()
        compose.waitForIdle()

        compose.onNodeWithTag("time_settings_tab_streak").assertIsSelected()
        compose.onNodeWithTag("time_settings_max_passes_value").assertTextEquals("4")
        click("time_settings_tab_reminders")
        compose.onNodeWithTag("time_settings_reminder").assertIsOff()
        click("time_settings_tab_goal")
        assertEquals("[time_settings_goal] field=\"45\"", line("time_settings_goal"))
        click("time_settings_tab_look")
        assertEquals("[time_settings_name] field=\"Books\"", line("time_settings_name"))
        assertEquals(seeded, reading)

        click("time_settings_save")
        assertEquals("Books", reading.label)
        assertEquals(45, reading.goal.on(today))
        assertEquals(4, reading.rewards?.maxPasses?.on(today))
        assertFalse(reading.checkpoint.remind)
    }

    @Test
    @Config(qualifiers = "de")
    fun thePageSpeaksGerman() {
        compose.setContent { App() }
        openPage()

        compose.onNodeWithTag("time_settings_tab_look").assertTextEquals("Aussehen")
        compose.onNodeWithTag("time_settings_tab_goal").assertTextEquals("Ziel")
        click("time_settings_tab_goal")
        assertTrue(compose.transcript().toString(), "text=\"Minuten pro Woche\"" in compose.transcript())
        compose.onNodeWithTag("time_settings_goal_note").assertTextEquals("Für 60 Minuten in einer Woche gibt es ein Token. Für mehr Minuten gibt es bis zu 2 Tokens pro Woche.")
        click("time_settings_tab_reminders")
        assertEquals("[time_settings_reminder_preview_text] text=\"Noch 60 Minuten diese Woche\"", line("time_settings_reminder_preview_text"))
        click("time_settings_tab_streak")
        compose.onNodeWithTag("time_settings_rewards").assertIsOn()
        compose.onNodeWithTag("time_settings_away_due").assertTextEquals("Weiter fällig").assertIsSelected()
        compose.onNodeWithTag("time_settings_delete_reason").assertDoesNotExist()
    }
}

package de.nicolasmahn.momentum.feature.mo

import de.nicolasmahn.momentum.domain.compatibility.withWeightWeekRule
import de.nicolasmahn.momentum.domain.mo.MoChoices
import de.nicolasmahn.momentum.domain.time.TimeLog
import de.nicolasmahn.momentum.app.ActivitySaveNotExpected
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import de.nicolasmahn.momentum.app.MomentumApp
import de.nicolasmahn.momentum.core.time.currentHabitDate
import de.nicolasmahn.momentum.core.ui.theme.WorkoutTrackerTheme
import de.nicolasmahn.momentum.domain.AppState
import de.nicolasmahn.momentum.domain.TrackerRef
import de.nicolasmahn.momentum.domain.compatibility.CareChecklistId
import de.nicolasmahn.momentum.domain.compatibility.LegacyCareSession
import de.nicolasmahn.momentum.domain.care.dueCareActions
import de.nicolasmahn.momentum.domain.care.withCare
import de.nicolasmahn.momentum.domain.checklist.ChecklistEdit
import de.nicolasmahn.momentum.domain.checklist.applyEdit
import de.nicolasmahn.momentum.domain.compatibility.WeightMeasurementId
import de.nicolasmahn.momentum.domain.measurement.withWeight
import de.nicolasmahn.momentum.domain.mo.MoDayMark
import de.nicolasmahn.momentum.domain.mo.MoDayRecord
import de.nicolasmahn.momentum.domain.mo.MoMood
import de.nicolasmahn.momentum.domain.compatibility.MoNeed
import de.nicolasmahn.momentum.domain.mo.habitStreak
import de.nicolasmahn.momentum.domain.mo.keeping
import de.nicolasmahn.momentum.domain.mo.keepingWeeks
import de.nicolasmahn.momentum.domain.mo.moMood
import de.nicolasmahn.momentum.domain.mo.moMoodWithStoredChoices
import de.nicolasmahn.momentum.domain.mo.only
import de.nicolasmahn.momentum.domain.trackerRef
import de.nicolasmahn.momentum.persistence.PassChoices
import de.nicolasmahn.momentum.domain.compatibility.EatingQuestionsId
import de.nicolasmahn.momentum.domain.questions.QuestionsEdit
import de.nicolasmahn.momentum.domain.questions.applyingQuestionsEdits
import de.nicolasmahn.momentum.domain.questions.withEating
import de.nicolasmahn.momentum.domain.settings.FeatureVisibilitySettings
import de.nicolasmahn.momentum.domain.compatibility.ReadingTimeId
import de.nicolasmahn.momentum.domain.time.TimeEdit
import de.nicolasmahn.momentum.domain.time.applyingTimeEdits
import de.nicolasmahn.momentum.domain.time.withReading
import de.nicolasmahn.momentum.domain.activity.withActivity
import java.time.LocalDate
import java.util.TimeZone
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * A reservation is stored for the day, and what it was made over can change before the day
 * closes: a bank's size and Reading's goal are settings, and a record can be removed. A stored
 * choice counts only while a new one would be allowed, whichever task it is of. In each case here
 * what the app shows before 04:00 is what the walk does as the day closes: shown as set aside,
 * the day is covered by tokens, and otherwise it is not.
 *
 * Reading's Sunday is the second of two weeks: two hours in the first fill the bank to three, and
 * the second holds forty of its sixty minutes, two gaps of fifteen.
 */
@RunWith(RobolectricTestRunner::class)
class StoredReservationTest {
    @get:Rule
    val compose = createComposeRule()

    private val monday = LocalDate.parse("2026-10-05")
    private fun on(day: Int) = monday.plusDays(day.toLong())
    private val sunday = on(13)
    private val onlyReading = only(MoNeed.Reading)
    private val fortyOfSixty = AppState(timeLogs = listOf(TimeLog(ReadingTimeId, on(1).toString(), 1L, 120), TimeLog(ReadingTimeId, on(8).toString(), 8L, 40))).withReading().withActivity()
    private lateinit var zone: TimeZone
    private var shown by mutableStateOf<MoMood?>(null)

    // Eating's cutoff is judged in the default zone, and its reflections here are written in UTC.
    @Before fun pinZone() {
        zone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    @After fun restoreZone() = TimeZone.setDefault(zone)

    /** Reading's settings as its page saves them on the Sunday, after the reservation was stored. */
    private fun AppState.savedOnSunday(edit: TimeEdit) = applyingTimeEdits(ReadingTimeId, listOf(edit), sunday)

    /** [day] as the app shows it while a reservation of [need] is stored for it. */
    private fun stored(state: AppState, need: MoNeed, day: LocalDate) =
        moMoodWithStoredChoices(state, only(need), day, storedChoices = MoChoices(setOf(need.view.trackerRef)))

    /** The Sunday as the app shows it while Reading's reservation is stored. */
    private fun reserved(state: AppState) = stored(state, MoNeed.Reading, sunday)

    /** How the walk closed [day], read on the day after, with the tokens [need]'s bank holds then. */
    private fun closed(state: AppState, visibility: FeatureVisibilitySettings, day: LocalDate, need: MoNeed): Pair<MoDayRecord, Int?> =
        habitStreak(state, visibility, day.plusDays(1)).let { streak ->
            streak.history.single { it.date == day } to streak.standings[need.view.trackerRef]?.passes
        }

    /** Keep the historical task assertion while supplying the canonical identities the walk stores. */
    private fun closedRecord(day: LocalDate, mark: MoDayMark, vararg needs: MoNeed) = MoDayRecord(
        date = day,
        mark = mark,
        coveredTrackers = needs.mapTo(linkedSetOf()) { it.view.trackerRef },
    )

    /** What the token page says of Reading's bank for TalkBack: the tokens it has free, those reserved, and the week so far. */
    private fun tokenRow(mood: MoMood): String {
        shown = mood
        compose.waitForIdle()
        return compose.onNode(hasContentDescription("Reading: ", substring = true)).fetchSemanticsNode().config[SemanticsProperties.ContentDescription].single()
    }

    private fun showTokenPage() = compose.setContent {
        WorkoutTrackerTheme {
            shown?.let { mood ->
                MoStreakScreen(mood = mood, banks = testTrackerBankPresentations(mood))
            }
        }
    }

    @Test
    fun untouchedTheReservationStandsAndTheWalkSpendsItsTwoTokens() {
        showTokenPage()
        val mood = reserved(fortyOfSixty)

        assertEquals(setOf(MoNeed.Reading.view.trackerRef), mood.selectedTrackerChoices)
        assertEquals(emptyList<TrackerRef>(), mood.missingTrackersToday)
        assertTrue(mood.todayIsSetAside)
        assertEquals(2, mood.tokensSetAside(MoNeed.Reading.view.trackerRef))
        assertEquals("Reading: 1 token. 2 reserved for today. next token: 40/60 min this week", tokenRow(mood))
        assertEquals(closedRecord(sunday, MoDayMark.Protected, MoNeed.Reading) to 1, closed(fortyOfSixty, onlyReading, sunday, MoNeed.Reading))
    }

    /**
     * A bank of one, saved after two tokens were set aside, holds one token for two gaps. The
     * reservation no longer counts: Reading is missing again, nothing is reserved and nothing can
     * be, and the walk covers the Sunday with a freeze and leaves the token where it is.
     */
    @Test
    fun aSmallerBankSavedAfterReservingLeavesReadingMissingAsTheWalkWillFindIt() {
        showTokenPage()
        val smallerBank = fortyOfSixty.savedOnSunday(TimeEdit.SetBankSize(1))
        val mood = reserved(smallerBank)

        assertEquals(1, mood.streak.standings.getValue(MoNeed.Reading.view.trackerRef).passes)
        assertEquals(listOf(MoNeed.Reading.view.trackerRef), mood.missingTrackersToday)
        assertEquals(emptySet<TrackerRef>(), mood.selectedTrackerChoices)
        assertFalse(mood.todayIsSetAside)
        assertEquals(0, mood.tokensSetAside(MoNeed.Reading.view.trackerRef))
        assertFalse(mood.canSetAside(MoNeed.Reading.view.trackerRef))
        assertEquals("Reading: 1 token. full. next token: 40/60 min this week", tokenRow(mood))
        // It is the mood the Sunday has without any choice.
        assertEquals(moMood(smallerBank, onlyReading, sunday), mood)
        assertEquals(closedRecord(sunday, MoDayMark.Frozen, MoNeed.Reading) to 1, closed(smallerBank, onlyReading, sunday, MoNeed.Reading))

        // Five minutes more leave one gap, which the one token covers: the stored choice counts again, and the walk spends the token.
        val oneGap = smallerBank.copy(timeLogs = smallerBank.timeLogs + listOf(TimeLog(ReadingTimeId, sunday.toString(), 13L, 5)))
        assertEquals(setOf(MoNeed.Reading.view.trackerRef), reserved(oneGap).selectedTrackerChoices)
        assertEquals(1, reserved(oneGap).tokensSetAside(MoNeed.Reading.view.trackerRef))
        assertTrue(reserved(oneGap).todayIsSetAside)
        assertEquals(closedRecord(sunday, MoDayMark.Protected, MoNeed.Reading) to 0, closed(oneGap, onlyReading, sunday, MoNeed.Reading))
    }

    /**
     * Four hours a week, saved after two tokens were set aside, leave 200 minutes missing: four
     * gaps of sixty, more than the bank of three holds. The reservation no
     * longer counts. Eighty minutes a week leave forty missing, two gaps of twenty, so there it
     * stands, by the new numbers.
     */
    @Test
    fun aRaisedGoalSavedAfterReservingCountsOnlyWhileItsGapsAreStillCovered() {
        showTokenPage()
        val fourHours = fortyOfSixty.savedOnSunday(TimeEdit.SetGoal(240))
        val mood = reserved(fourHours)

        assertEquals(3, mood.streak.standings.getValue(MoNeed.Reading.view.trackerRef).passes)
        assertEquals(200, mood.trackerWeeklyRemainders[MoNeed.Reading.view.trackerRef])
        assertEquals(listOf(MoNeed.Reading.view.trackerRef), mood.missingTrackersToday)
        assertEquals(emptySet<TrackerRef>(), mood.selectedTrackerChoices)
        assertFalse(mood.todayIsSetAside)
        assertEquals(0, mood.tokensSetAside(MoNeed.Reading.view.trackerRef))
        assertFalse(mood.canSetAside(MoNeed.Reading.view.trackerRef))
        assertEquals("Reading: 3 tokens. full. next token: 40/240 min this week", tokenRow(mood))
        assertEquals(closedRecord(sunday, MoDayMark.Frozen, MoNeed.Reading) to 3, closed(fourHours, onlyReading, sunday, MoNeed.Reading))

        val eightyMinutes = fortyOfSixty.savedOnSunday(TimeEdit.SetGoal(80))
        val standing = reserved(eightyMinutes)
        assertEquals(setOf(MoNeed.Reading.view.trackerRef), standing.selectedTrackerChoices)
        assertTrue(standing.todayIsSetAside)
        assertEquals(2, standing.tokensSetAside(MoNeed.Reading.view.trackerRef))
        assertEquals("Reading: 1 token. 2 reserved for today. next token: 40/80 min this week", tokenRow(standing))
        assertEquals(closedRecord(sunday, MoDayMark.Protected, MoNeed.Reading) to 1, closed(eightyMinutes, onlyReading, sunday, MoNeed.Reading))
    }

    /**
     * Care's day has two sessions, each taking a token. With a bank of three both are set aside
     * while it holds three tokens; a bank of one saved after that holds one token for two gaps,
     * so the reservation no longer counts and the walk covers the day with a freeze.
     *
     * A removed record does the same without any setting: one session set aside with the one
     * token Care's own bank holds, then the other session's ticks taken back, leaves two gaps
     * for one token.
     */
    @Test
    fun caresTwoSessionsAreSetAsideOnlyWhileItsBankHoldsATokenForEach() {
        val onlyCare = only(MoNeed.Care)
        val today = on(14)
        val threeTokens = AppState().keepingWeeks(MoNeed.Care, monday, "xxxxxxx", "xxxxxxx")
            .applyEdit(CareChecklistId, ChecklistEdit.SetRewardRules(maxPasses = 3, everyKeptDays = 7), monday)
        val bothSetAside = stored(threeTokens, MoNeed.Care, today)
        assertEquals(3, bothSetAside.streak.standings.getValue(MoNeed.Care.view.trackerRef).passes)
        assertEquals(setOf(MoNeed.Care.view.trackerRef), bothSetAside.selectedTrackerChoices)
        assertEquals(2, bothSetAside.tokensSetAside(MoNeed.Care.view.trackerRef))
        assertTrue(bothSetAside.todayIsSetAside)
        assertEquals(closedRecord(today, MoDayMark.Protected, MoNeed.Care) to 1, closed(threeTokens, onlyCare, today, MoNeed.Care))

        val smallerBank = threeTokens.applyEdit(CareChecklistId, ChecklistEdit.SetRewardRules(maxPasses = 1, everyKeptDays = 7), today)
        val mood = stored(smallerBank, MoNeed.Care, today)
        assertEquals(1, mood.streak.standings.getValue(MoNeed.Care.view.trackerRef).passes)
        assertEquals(listOf(MoNeed.Care.view.trackerRef), mood.missingTrackersToday)
        assertEquals(emptySet<de.nicolasmahn.momentum.domain.TrackerRef>(), mood.selectedTrackerChoices)
        assertEquals(0, mood.tokensSetAside(MoNeed.Care.view.trackerRef))
        assertFalse(mood.canSetAside(MoNeed.Care.view.trackerRef))
        assertFalse(mood.todayIsSetAside)
        assertEquals(moMood(smallerBank, onlyCare, today), mood)
        assertEquals(closedRecord(today, MoDayMark.Frozen, MoNeed.Care) to 1, closed(smallerBank, onlyCare, today, MoNeed.Care))

        // Care's own bank holds one token, which covers the evening while the morning is done.
        val tuesday = on(1)
        val keptMonday = AppState().keeping(MoNeed.Care, monday)
        val morningDone = keptMonday.withCare(*dueCareActions(tuesday.toString(), LegacyCareSession.MORNING))
        val eveningSetAside = stored(morningDone, MoNeed.Care, tuesday)
        assertEquals(1, eveningSetAside.streak.standings.getValue(MoNeed.Care.view.trackerRef).passes)
        assertEquals(setOf(MoNeed.Care.view.trackerRef), eveningSetAside.selectedTrackerChoices)
        assertEquals(1, eveningSetAside.tokensSetAside(MoNeed.Care.view.trackerRef))
        assertTrue(eveningSetAside.todayIsSetAside)
        assertEquals(closedRecord(tuesday, MoDayMark.Protected, MoNeed.Care) to 0, closed(morningDone, onlyCare, tuesday, MoNeed.Care))
        // The morning's ticks taken back, both sessions are open: two gaps for one token.
        val bothOpen = stored(keptMonday, MoNeed.Care, tuesday)
        assertEquals(listOf(MoNeed.Care.view.trackerRef), bothOpen.missingTrackersToday)
        assertEquals(emptySet<TrackerRef>(), bothOpen.selectedTrackerChoices)
        assertFalse(bothOpen.todayIsSetAside)
        assertEquals(closedRecord(tuesday, MoDayMark.Frozen, MoNeed.Care) to 1, closed(keptMonday, onlyCare, tuesday, MoNeed.Care))
    }

    /**
     * Home asks through the same check. Care is set aside for today with the one token its bank
     * holds while its morning is done; with the morning's ticks taken back the stored choice is
     * still there, and the token page reserves nothing.
     */
    @Test
    fun homeShowsAStoredChoiceOnlyWhileItStands() {
        val today = currentHabitDate()
        val keptYesterday = AppState().keeping(MoNeed.Care, today.minusDays(1))
        var state by mutableStateOf(keptYesterday.withCare(*dueCareActions(today.toString(), LegacyCareSession.MORNING)))
        compose.setContent {
            WorkoutTrackerTheme {
                MomentumApp(
                    state = state,
                    rewardStart = today.minusDays(30),
                    onStateChange = { true },
                    onActivitySave = ActivitySaveNotExpected,
                    featureVisibility = only(MoNeed.Care),
                    passChoices = PassChoices(today, setOf(MoNeed.Care.view.trackerRef)),
                )
            }
        }
        fun caresRow(): String {
            compose.waitForIdle()
            return compose.onNode(hasContentDescription("Care: ", substring = true)).fetchSemanticsNode().config[SemanticsProperties.ContentDescription].single()
        }
        compose.onNodeWithTag("home_streak").performSemanticsAction(SemanticsActions.OnClick)

        assertTrue(caresRow(), caresRow().startsWith("Care: 0 tokens. 1 reserved for today"))
        state = keptYesterday
        assertTrue(caresRow(), caresRow().startsWith("Care: 1 token. full"))
    }

    /**
     * Weight's and Eating's pages cannot do the same. Their day has one part, which one token
     * covers, and a bank holds at least one: a size of one saved after reserving, where the bank
     * held three, leaves the token the reservation needs, and the walk spends it.
     */
    @Test
    fun aSmallerBankLeavesWeightsAndEatingsReservationsStanding() {
        val today = on(14)
        val bothKept = AppState().withWeight().withEating()
            .keepingWeeks(MoNeed.Weight, monday, "xxxxxxx", "xxxxxxx")
            .keepingWeeks(MoNeed.Eating, monday, "xxxxxxx", "xxxxxxx")
        val smallerBanks = bothKept
            .withWeightWeekRule(today, maxPasses = 1, keptDaysInWeek = 6)
            .applyingQuestionsEdits(EatingQuestionsId, listOf(QuestionsEdit.SetRewardRules(maxPasses = 1, everyKeptDays = 7)), today)

        listOf(MoNeed.Weight, MoNeed.Eating).forEach { need ->
            val before = stored(bothKept, need, today)
            val ref = need.view.trackerRef
            assertEquals("$need", 3, before.streak.standings.getValue(ref).passes)
            assertTrue("$need", before.todayIsSetAside)

            val after = stored(smallerBanks, need, today)
            assertEquals("$need", 1, after.streak.standings.getValue(ref).passes)
            assertEquals("$need", setOf(ref), after.selectedTrackerChoices)
            assertTrue("$need", after.todayIsSetAside)
            assertEquals("$need", 1, after.tokensSetAside(ref))
            assertEquals("$need", closedRecord(today, MoDayMark.Protected, need) to 0, closed(smallerBanks, only(need), today, need))
        }
    }
}

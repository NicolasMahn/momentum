package com.example.workouttracker.domain.reward

import com.example.workouttracker.domain.settings.showingOnly
import com.example.workouttracker.domain.AppState
import com.example.workouttracker.domain.trackerRef
import com.example.workouttracker.domain.checklist.Dated
import com.example.workouttracker.domain.checklist.MoMotif
import com.example.workouttracker.domain.checklist.WhenAway
import com.example.workouttracker.domain.holiday.DayRange
import com.example.workouttracker.domain.holiday.HolidaySchedule
import com.example.workouttracker.domain.measurement.MeasurementLog
import com.example.workouttracker.domain.compatibility.WeightMeasurementId
import com.example.workouttracker.domain.measurement.testMadeMeasurement
import com.example.workouttracker.domain.measurement.testWeightMeasurement
import com.example.workouttracker.domain.mo.MoContentKey
import com.example.workouttracker.domain.mo.MoDayMark
import com.example.workouttracker.domain.compatibility.MoNeed
import com.example.workouttracker.domain.mo.MoStreak
import com.example.workouttracker.domain.mo.MoStreakStanding
import com.example.workouttracker.domain.mo.MoTokenProgress
import com.example.workouttracker.domain.mo.MoViewKind
import com.example.workouttracker.domain.mo.habitStreak
import com.example.workouttracker.domain.mo.moMood
import com.example.workouttracker.domain.mo.compatibility.InheritedMoEvaluation
import com.example.workouttracker.domain.settings.FeatureVisibilitySettings
import com.example.workouttracker.domain.ref
import com.example.workouttracker.domain.time.DefaultTokenSteps
import com.example.workouttracker.domain.time.TimeConfiguration
import com.example.workouttracker.domain.time.TimeEdit
import com.example.workouttracker.domain.time.TimeLog
import com.example.workouttracker.domain.time.TimeLogOrder
import com.example.workouttracker.domain.time.TokenStep
import com.example.workouttracker.domain.time.TokenSteps
import com.example.workouttracker.domain.time.applyingTimeEdits
import com.example.workouttracker.domain.time.testMadeTime
import com.example.workouttracker.domain.time.tokenStepsOfTokensPerWeek
import com.example.workouttracker.domain.time.withReading
import com.example.workouttracker.domain.activity.withActivity
import com.example.workouttracker.feature.mo.todayIsSetAside
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A time view a person made that joins the streak is in the walk what Reading is, with its own
 * numbers: its week is judged on its Sunday against its goal, short by gaps of a quarter of it,
 * and earns the tokens of every one of its bank's steps it reaches, a token at the goal and one
 * more at twice it at first. What it has of its own is that a week counts only whole: judged, and
 * able to earn, only when every day of it is in one of its periods.
 */
class TimeRewardsTest {
    private val monday = LocalDate.parse("2026-10-05")
    private val piano = testMadeTime()
    private val pianoRef = piano.ref.trackerRef
    private val weightRef = MoNeed.Weight.view.trackerRef
    private val readingRef = MoNeed.Reading.view.trackerRef
    private val onlyWeight = showingOnly(MoNeed.Weight)
    private val weightAndReading = showingOnly(MoNeed.Weight, MoNeed.Reading)

    private fun on(day: Long): LocalDate = monday.plusDays(day)

    /** Weight logged every day of four weeks, and [view]'s minutes on the days given. */
    private fun history(vararg minutes: Pair<Long, Int>, view: TimeConfiguration = piano): AppState = AppState(
        measurements = listOf(testWeightMeasurement()),
        measurementLogs = (0L until 28L).map { MeasurementLog(WeightMeasurementId, on(it).toString(), it, 80.0) },
        timeLogs = minutes.map { (day, logged) -> TimeLog(view.id, on(day).toString(), day, logged) }.sortedWith(TimeLogOrder),
    ).withReading().withActivity().let { it.copy(time = it.time + view) }

    private fun AppState.edited(vararg edits: TimeEdit, day: Long): AppState = applyingTimeEdits(piano.id, edits.toList(), on(day))

    private fun AppState.joined(day: Long = 0): AppState = edited(TimeEdit.JoinRewards, day = day)

    private fun AppState.withoutTheView(): AppState = copy(time = time.filterNot { it.id == piano.id }, timeLogs = timeLogs.filterNot { it.viewId == piano.id })

    private fun AppState.withReadingMinutes(vararg minutes: Pair<Long, Int>): AppState =
        copy(timeLogs = (timeLogs + minutes.map { (day, read) -> TimeLog("reading", on(day).toString(), day, read) }).sortedWith(TimeLogOrder))

    private fun streak(state: AppState, day: Long, visibility: FeatureVisibilitySettings = onlyWeight, daysOff: HolidaySchedule = HolidaySchedule()) =
        habitStreak(state, visibility, on(day), daysOff)

    private fun MoStreak.markOn(day: Long) = history.single { it.date == on(day) }.mark

    private fun MoStreak.coveredOn(day: Long) = history.single { it.date == on(day) }.coveredTrackers

    private fun MoStreak.piano(): MoStreakStanding? = standings[pianoRef]

    @Test fun aViewOutsideTheStreakChangesNothing() {
        val without = streak(history(2L to 30).withoutTheView(), 27)
        assertEquals(without, streak(history(2L to 30), 27))
        assertNull(streak(history(2L to 30), 27).piano())
    }

    /**
     * A whole week is judged on its Sunday. One that reached its goal is kept, and each full goal
     * earns a token, two a week at most, into a bank of three. A week short by no more gaps than
     * its own bank holds is covered from it; one short by more is not.
     */
    @Test fun aWholeWeekIsJudgedOnItsSundayAndEachFullGoalEarnsAToken() {
        val joined = history(2L to 60, 8L to 100, 10L to 50, 15L to 30).joined()

        val result = streak(joined, 28)

        assertEquals(MoDayMark.Perfect, result.markOn(6))
        assertEquals(MoDayMark.Perfect, result.markOn(13))
        // Thirty of sixty are two gaps of fifteen, which two of its tokens cover.
        assertEquals(MoDayMark.Protected, result.markOn(20))
        assertEquals(setOf(pianoRef), result.coveredOn(20))
        // Nothing of sixty is four gaps, more than the one token left: a freeze pays.
        assertEquals(MoDayMark.Frozen, result.markOn(27))
        assertEquals(setOf(pianoRef), result.coveredOn(27))
        // No other day of a week is judged for it.
        (0L until 28L).filterNot { it % 7 == 6L }.forEach { assertEquals(MoDayMark.Perfect, result.markOn(it)) }

        // One to start and one for the first week's goal; two more for 150 minutes, held at three; two spent.
        assertEquals(2, streak(joined, 7).piano()?.passes)
        assertEquals(3, streak(joined, 14).piano()?.passes)
        assertEquals(1, streak(joined, 21).piano()?.passes)
        assertEquals(1, result.piano()?.passes)
        assertEquals(3, result.piano()?.passCapacity)
        // Weight's bank is its own: it spent nothing on a week only the view was short of.
        assertEquals(streak(history().withoutTheView(), 21).participantBanks[weightRef], streak(joined, 21).participantBanks[weightRef])
    }

    /** A full bank covers a week as far as it reaches: three tokens, all of them, for three gaps. */
    @Test fun aFullBankCoversAWeekShortByAsManyGapsAsItHolds() {
        // Two weeks fill the bank to three; the third reads 16 of 60, three gaps of fifteen.
        val joined = history(2L to 60, 9L to 60, 16L to 16).joined()

        val result = streak(joined, 21)

        assertEquals(3, streak(joined, 14).piano()?.passes)
        assertEquals(MoDayMark.Protected, result.markOn(20))
        assertEquals(0, result.piano()?.passes)
    }

    private fun steps(vararg steps: Pair<Int, Int>) = TokenSteps(steps.map { (percent, tokens) -> TokenStep(percent, tokens) })

    /**
     * A week earns the tokens of every step it reaches, added up, judged against each step's
     * minutes on its Sunday: nothing below the goal, the first step's at the goal, the same
     * between two steps, the next step's more at the next, and all of them past the last. The
     * token page counts toward the next step's minutes, and at the last step toward its own.
     */
    @Test fun aWeekEarnsTheTokensOfEveryStepItReaches() {
        // Sixty, ninety, 120 and 240 minutes adding one, one, two and one, into a bank that could hold every token beside the one it starts with.
        val four = steps(100 to 1, 150 to 1, 200 to 2, 400 to 1)
        fun week(minutes: Int) = history(2L to minutes).joined().edited(TimeEdit.SetBankSize(9), TimeEdit.SetTokenSteps(four), day = 0)
        fun passesAfter(minutes: Int) = streak(week(minutes), 7).piano()?.passes
        fun nextToken(minutes: Int) = streak(week(minutes), 3).piano()?.nextToken

        // Short of the goal by one gap, the week spends the token it started with and earns none.
        assertEquals(MoDayMark.Protected, streak(week(59), 7).markOn(6))
        assertEquals(0, passesAfter(59))
        assertEquals(MoTokenProgress(59, 60), nextToken(59))
        assertEquals(2, passesAfter(60))
        assertEquals(MoTokenProgress(60, 90), nextToken(60))
        assertEquals(2, passesAfter(89))
        assertEquals(MoTokenProgress(89, 90), nextToken(89))
        assertEquals(3, passesAfter(90))
        assertEquals(MoTokenProgress(90, 120), nextToken(90))
        assertEquals(3, passesAfter(119))
        assertEquals(5, passesAfter(120))
        assertEquals(MoTokenProgress(120, 240), nextToken(120))
        assertEquals(5, passesAfter(239))
        assertEquals(6, passesAfter(240))
        assertEquals(MoTokenProgress(240, 240), nextToken(240))
        assertEquals(6, passesAfter(500))
        assertEquals(MoTokenProgress(240, 240), nextToken(500))

        // One step alone: the goal earns its tokens, twice the goal earns nothing more, and the token page has no further token to count toward.
        val once = history(2L to 120).joined().edited(TimeEdit.SetTokenSteps(steps(100 to 1)), day = 0)
        assertEquals(2, streak(once, 7).piano()?.passes)
        assertEquals(MoTokenProgress(60, 60), streak(once, 3).piano()?.nextToken)
        assertEquals(MoTokenProgress(45, 60), streak(history(2L to 45).joined().edited(TimeEdit.SetTokenSteps(steps(100 to 1)), day = 0), 3).piano()?.nextToken)
        assertEquals(4, streak(history(2L to 60).joined().edited(TimeEdit.SetBankSize(9), TimeEdit.SetTokenSteps(steps(100 to 3)), day = 0), 7).piano()?.passes)
        // A bank that holds them covers every gap: nothing of sixty is four gaps, four of its six tokens.
        val full = history(2L to 300).joined().edited(TimeEdit.SetBankSize(9), TimeEdit.SetTokenSteps(steps(100 to 5)), day = 0)
        assertEquals(MoDayMark.Protected, streak(full, 14).markOn(13))
        assertEquals(2, streak(full, 14).piano()?.passes)
    }

    /** A step's minutes are its share of the goal rounded up: 150 % of 45 is 68, so 67 reaches only the goal. */
    @Test fun aStepWhoseMinutesRoundUpIsReachedOnlyByTheWholeMinute() {
        val fortyFive = testMadeTime(goal = 45)
        fun week(minutes: Int) = history(2L to minutes, view = fortyFive).joined().edited(TimeEdit.SetTokenSteps(steps(100 to 1, 150 to 2)), day = 0)
        assertEquals(2, streak(week(67), 7).piano()?.passes)
        assertEquals(3, streak(week(68), 7).piano()?.passes)
        assertEquals(MoTokenProgress(67, 68), streak(week(67), 3).piano()?.nextToken)
        assertEquals(MoTokenProgress(68, 68), streak(week(68), 3).piano()?.nextToken)
    }

    /**
     * The steps are dated as the goal is: a week earns by the steps its Sunday has. A list saved in
     * the middle of a week is that week's, one saved on the next Monday is not, and every earlier
     * week is judged and credited as it was, for a made view and for Reading.
     */
    @Test fun aChangedListOfStepsCreditsEarlierWeeksAsBefore() {
        val one = steps(100 to 1)
        val twoGoalsAWeek = history(2L to 120, 9L to 120, 16L to 120, 23L to 120).joined().edited(TimeEdit.SetBankSize(9), day = 0)
        // Lowered on the Wednesday of the third week.
        val lowered = twoGoalsAWeek.edited(TimeEdit.SetTokenSteps(one), day = 16)

        // One to start and two for each of the first two weeks, either way; up to the second Sunday nothing differs at all.
        assertEquals(5, streak(twoGoalsAWeek, 14).piano()?.passes)
        assertEquals(5, streak(lowered, 14).piano()?.passes)
        (0L..13L).forEach { day -> assertEquals("day $day", streak(twoGoalsAWeek, day), streak(lowered, day)) }
        // The week it was saved in earns by the new steps, and so does the next.
        assertEquals(7, streak(twoGoalsAWeek, 21).piano()?.passes)
        assertEquals(6, streak(lowered, 21).piano()?.passes)
        assertEquals(7, streak(lowered, 28).piano()?.passes)
        // On the Thursday after, the token page counts toward the week's last step as its Sunday will have it.
        assertEquals(MoTokenProgress(120, 120), streak(twoGoalsAWeek, 17).piano()?.nextToken)
        assertEquals(MoTokenProgress(60, 60), streak(lowered, 17).piano()?.nextToken)
        // Seen from the end, every day is judged as it was: only the bank differs.
        assertEquals(streak(twoGoalsAWeek, 28).history, streak(lowered, 28).history)
        // Saved on a Monday, the week before still earns by the steps its Sunday had.
        val loweredOnMonday = twoGoalsAWeek.edited(TimeEdit.SetTokenSteps(one), day = 14)
        assertEquals(5, streak(loweredOnMonday, 14).piano()?.passes)
        assertEquals(6, streak(loweredOnMonday, 21).piano()?.passes)

        // Reading's are dated from the day they are saved, too: three goals a week, a third step added in the third week.
        val read = history().withReadingMinutes(2L to 180, 9L to 180, 16L to 180)
            .applyingTimeEdits("reading", listOf(TimeEdit.SetBankSize(9)), on(0))
        val raised = read.applyingTimeEdits("reading", listOf(TimeEdit.SetTokenSteps(steps(100 to 1, 200 to 1, 300 to 1))), on(20))
        fun readingPasses(state: AppState, day: Long) = streak(state, day, weightAndReading).participantBanks[readingRef]
        (0L..13L).forEach { day -> assertEquals("day $day", streak(read, day, weightAndReading), streak(raised, day, weightAndReading)) }
        assertEquals(readingPasses(read, 14), readingPasses(raised, 14))
        assertEquals(readingPasses(read, 14)!! + 2, readingPasses(read, 21))
        assertEquals(readingPasses(read, 14)!! + 3, readingPasses(raised, 21))
        assertEquals(streak(read, 21, weightAndReading).history, streak(raised, 21, weightAndReading).history)
    }

    /**
     * A step's tokens are its own: steps whose tokens are equal, or fall, earn their sum, and the
     * sum is what the week earns in all, held to the bank's size. Two steps whose minutes round to
     * the same minute both count once that minute is reached. The default steps and every number
     * a bank once stored as its tokens a week earn exactly what they earned, at every boundary.
     */
    @Test fun stepsWhoseTokensAreEqualOrFallEarnTheirSumAndCoincidingStepsBothCount() {
        fun week(steps: TokenSteps, minutes: Int, bank: Int, view: TimeConfiguration) =
            history(2L to minutes, view = view).joined().edited(TimeEdit.SetBankSize(bank), TimeEdit.SetTokenSteps(steps), day = 0)
        fun passes(steps: TokenSteps, minutes: Int, bank: Int = 30, view: TimeConfiguration = piano) = streak(week(steps, minutes, bank, view), 7).piano()?.passes
        fun nextToken(steps: TokenSteps, minutes: Int, view: TimeConfiguration = piano) = streak(week(steps, minutes, 30, view), 3).piano()?.nextToken
        // Five at the goal, one at 200 %, five at 300 %: a week short of the goal spends the one it started with, then 6, 7 and 12.
        val falling = steps(100 to 5, 200 to 1, 300 to 5)
        assertEquals(listOf(0, 6, 6, 7, 7, 12, 12), listOf(59, 60, 119, 120, 179, 180, 1000).map { passes(falling, it) })
        assertEquals(MoTokenProgress(150, 180), nextToken(falling, 150))
        assertEquals(MoTokenProgress(180, 180), nextToken(falling, 180))
        // Twenty-five is the most a week can earn, and a bank holds no more than its size.
        val most = steps(100 to 5, 200 to 5, 300 to 5, 400 to 5, 500 to 5)
        assertEquals(26, passes(most, 300))
        assertEquals(3, passes(most, 300, bank = 3))
        // With a goal of one minute, 101 % and 102 % are both two minutes: two minutes reach both steps.
        val oneMinute = testMadeTime(goal = 1)
        val close = steps(100 to 1, 101 to 2, 102 to 3)
        assertEquals(1 + 1, passes(close, 1, view = oneMinute))
        assertEquals(1 + 1 + 2 + 3, passes(close, 2, view = oneMinute))
        assertEquals(MoTokenProgress(1, 2), nextToken(close, 1, view = oneMinute))
        assertEquals(MoTokenProgress(2, 2), nextToken(close, 2, view = oneMinute))
        // The default steps, and each number of tokens a week a bank once stored, earn what they earned.
        assertEquals(listOf(0, 2, 2, 3, 3), listOf(59, 60, 119, 120, 300).map { passes(DefaultTokenSteps, it) })
        (1..5).forEach { number ->
            val once = tokenStepsOfTokensPerWeek(number)
            (1..6).forEach { goals -> assertEquals("$number a week, $goals goals", 1 + minOf(goals, number), passes(once, goals * 60)) }
            assertEquals("$number a week, just short of three", 1 + minOf(2, number), passes(once, 3 * 60 - 1))
        }
    }

    /**
     * Joined in the middle of a week, the view starts with the next Monday: the week it joined in
     * is neither judged nor able to earn, whatever its minutes, so a Friday never asks for a
     * week's minutes by Sunday. Its bank is on show from the day it joined, without a next token.
     */
    @Test fun aViewThatJoinsInTheMiddleOfAWeekStartsWithTheNextMonday() {
        val empty = streak(history().joined(day = 4), 14)
        assertEquals(MoDayMark.Perfect, empty.markOn(6))
        assertFalse(pianoRef in empty.coveredOn(6))
        // The first whole week is judged.
        assertEquals(MoDayMark.Frozen, empty.markOn(13))

        val busy = history(5L to 120, 9L to 60).joined(day = 4)
        assertEquals(1, streak(busy, 7).piano()?.passes)
        assertEquals(2, streak(busy, 14).piano()?.passes)
        assertEquals(MoDayMark.Perfect, streak(busy, 14).markOn(13))

        // In the week it joined in its bank is there and says nothing of a next token, also on that week's Sunday.
        assertEquals(MoStreakStanding(passes = 1, passCapacity = 3, nextToken = null, openToday = false, keptToday = false), streak(history().joined(day = 4), 4).piano())
        assertEquals(MoStreakStanding(passes = 1, passCapacity = 3, nextToken = null, openToday = false, keptToday = false), streak(history().joined(day = 4), 6).piano())
        assertEquals(MoTokenProgress(0, 60), streak(history().joined(day = 4), 7).piano()?.nextToken)
        // Joined on a Monday, the week is whole from its first day.
        assertEquals(MoTokenProgress(0, 60), streak(history().joined(day = 7), 7).piano()?.nextToken)
    }

    /**
     * Left in the middle of a week, the view ends with the Sunday before: the week it left in is
     * neither judged nor able to earn, and the weeks before keep their judgments. Joined again, it
     * starts with the next Monday once more, with the bank it had.
     */
    @Test fun aViewThatLeavesInTheMiddleOfAWeekEndsWithTheSundayBefore() {
        val left = history(2L to 30, 8L to 120).joined().edited(TimeEdit.LeaveRewards, day = 9)

        val result = streak(left, 21)

        // The first week was short by two gaps, which its one token could not cover.
        assertEquals(MoDayMark.Frozen, result.markOn(6))
        assertEquals(setOf(pianoRef), result.coveredOn(6))
        assertEquals(MoDayMark.Perfect, result.markOn(13))
        assertEquals(MoDayMark.Perfect, result.markOn(20))
        assertNull(result.piano())

        // Back on a Friday: that week is not its own yet, the one after is, and the 120 minutes of the week it left in earned nothing.
        val back = left.edited(TimeEdit.JoinRewards, day = 18)
        assertEquals(MoDayMark.Perfect, streak(back, 28).markOn(20))
        // Judged again, and short by four gaps with the one freeze gone on the first week.
        assertEquals(MoDayMark.Missed, streak(back, 28).markOn(27))
        assertEquals(1, streak(back, 21).piano()?.passes)
        assertNull(streak(back, 20).piano()?.nextToken)
    }

    /**
     * The walk decides what a week's minutes earn apart from whether its Sunday is judged. So
     * Reading earns on a sick Sunday, as it always has, and so does a made view whose week is
     * whole; a made view whose week is not whole earns nothing, and Reading beside it earns as
     * ever.
     */
    @Test fun aWeeksMinutesEarnOnASundayThatIsNotJudgedButOnlyInAWholeWeek() {
        val sickSunday = HolidaySchedule().withSickDays(DayRange(on(6), on(6)))
        val minutes = history(5L to 60).withReadingMinutes(1L to 60)

        val whole = streak(minutes.joined(), 7, weightAndReading, sickSunday)
        assertEquals(MoDayMark.Sick, whole.markOn(6))
        assertEquals(2, whole.participantBanks[readingRef])
        assertEquals(2, whole.piano()?.passes)

        val joinedOnFriday = streak(minutes.joined(day = 4), 7, weightAndReading, sickSunday)
        assertEquals(2, joinedOnFriday.participantBanks[readingRef])
        assertEquals(1, joinedOnFriday.piano()?.passes)
        // On an ordinary Sunday too: Reading earns in a week the made view cannot.
        assertEquals(2, streak(minutes.joined(day = 4), 7, weightAndReading).participantBanks[readingRef])
        assertEquals(1, streak(minutes.joined(day = 4), 7, weightAndReading).piano()?.passes)

        // Everything of Reading's and of the streak is what it is without the made view.
        val without = streak(minutes.withoutTheView(), 7, weightAndReading, sickSunday)
        assertEquals(InheritedMoEvaluation(minutes.withoutTheView(), weightAndReading, on(7), sickSunday).streak,
            InheritedMoEvaluation(minutes.joined(), weightAndReading, on(7), sickSunday).streak.copy(time = emptyMap()))
        assertEquals(InheritedMoEvaluation(minutes.withoutTheView(), weightAndReading, on(7), sickSunday).streak,
            InheritedMoEvaluation(minutes.joined(day = 4), weightAndReading, on(7), sickSunday).streak.copy(time = emptyMap()))
    }

    /**
     * A sick Sunday, or an away one the view is not due on, leaves its week unjudged: nothing is
     * missed and nothing spent, however short the week. Away and still due, the Sunday judges it.
     */
    @Test fun aSickSundayOrAnAwayOneItIsNotDueOnLeavesTheWeekUnjudged() {
        val joined = history().joined()
        val notDue = joined.edited(TimeEdit.SetWhenAway(WhenAway.Excused), day = 0)
        val sickSunday = HolidaySchedule().withSickDays(DayRange(on(6), on(6)))
        val awaySunday = HolidaySchedule(listOf(DayRange(on(6), on(6))))

        assertEquals(MoDayMark.Sick, streak(joined, 7, daysOff = sickSunday).markOn(6))
        assertFalse(pianoRef in streak(joined, 7, daysOff = sickSunday).coveredOn(6))
        assertFalse(pianoRef in streak(notDue, 7, daysOff = awaySunday).coveredOn(6))
        assertEquals(1, streak(notDue, 7, daysOff = awaySunday).piano()?.passes)
        assertEquals(setOf(pianoRef), streak(joined, 7, daysOff = awaySunday).coveredOn(6))
        assertEquals(MoDayMark.Frozen, streak(joined, 7, daysOff = awaySunday).markOn(6))
    }

    /** An away day the view is not due on, and a sick day, each lower what the week asks by a seventh, rounded up once. */
    @Test fun aSickDayAndAnAwayDayItIsNotDueOnEachLowerWhatTheWeekAsksByASeventh() {
        val seventy = testMadeTime(goal = 70).copy(whenAway = Dated.always(WhenAway.Excused))
        val daysOff = HolidaySchedule(listOf(DayRange(on(1), on(1)))).withSickDays(DayRange(on(2), on(2)))
        fun week(logged: Int) = streak(history(4L to logged, view = seventy).joined(), 7, daysOff = daysOff)

        // Seventy less two sevenths is fifty.
        assertEquals(MoDayMark.Perfect, week(50).markOn(6))
        assertFalse(pianoRef in week(50).coveredOn(6))
        // One minute short is one gap, which its token covers.
        assertEquals(MoDayMark.Protected, week(49).markOn(6))
        assertEquals(0, week(49).piano()?.passes)
        // Still due while away, only the sick day lowers it: fifty of sixty is one gap.
        val due = history(4L to 50, view = seventy.copy(whenAway = Dated.always(WhenAway.Due))).joined()
        assertEquals(MoDayMark.Protected, streak(due, 7, daysOff = daysOff).markOn(6))
        assertEquals(MoDayMark.Perfect, streak(history(4L to 60, view = seventy.copy(whenAway = Dated.always(WhenAway.Due))).joined(), 7, daysOff = daysOff).markOn(6))
    }

    /** Hidden, the view is not judged and its bank is out of sight; shown again, its weeks are judged as they were logged. */
    @Test fun aHiddenViewIsNotJudgedAndHasNoStanding() {
        val joined = history().joined()
        val hidden = onlyWeight.hiding(piano.ref)

        val result = streak(joined, 14, hidden)

        assertEquals(MoDayMark.Perfect, result.markOn(6))
        assertEquals(MoDayMark.Perfect, result.markOn(13))
        assertNull(result.piano())
        assertEquals(streak(history().withoutTheView(), 14), result)
        assertEquals(MoDayMark.Frozen, streak(joined, 14).markOn(6))
    }

    /**
     * Its standing is its bank, this week's minutes toward the next token, and today: open on a
     * Sunday whose week is judged and still short, and kept once today holds a log, whatever the
     * week still asks for, as Reading's tile is done by any minutes read today.
     */
    @Test fun itsStandingSaysItsBankTheMinutesTowardTheNextTokenAndToday() {
        val joined = history(2L to 60, 9L to 45).joined()

        assertEquals(MoStreakStanding(passes = 2, passCapacity = 3, nextToken = MoTokenProgress(45, 60), openToday = true, keptToday = false), streak(joined, 13).piano())
        // A Wednesday is never open, and is kept by its own log alone.
        assertEquals(MoStreakStanding(passes = 2, passCapacity = 3, nextToken = MoTokenProgress(45, 60), openToday = false, keptToday = true), streak(joined, 9).piano())
        assertEquals(MoStreakStanding(passes = 2, passCapacity = 3, nextToken = MoTokenProgress(45, 60), openToday = false, keptToday = false), streak(joined, 10).piano())

        // Ten minutes on the Sunday keep the day, and the week is still short.
        val someToday = history(2L to 60, 9L to 45, 13L to 10).joined()
        assertEquals(MoStreakStanding(passes = 2, passCapacity = 3, nextToken = MoTokenProgress(55, 60), openToday = true, keptToday = true), streak(someToday, 13).piano())
        // The goal reached, nothing is open and the next token is the week's second.
        val reached = history(2L to 60, 9L to 45, 13L to 15).joined()
        assertEquals(MoStreakStanding(passes = 2, passCapacity = 3, nextToken = MoTokenProgress(60, 120), openToday = false, keptToday = true), streak(reached, 13).piano())
        // A measurement of the same ID is another view with another bank.
        val waist = testMadeMeasurement(id = piano.id)
        assertEquals(streak(joined, 13).piano(), streak(joined.copy(measurements = joined.measurements + waist), 13).piano())
    }

    /**
     * Flavor changes presentation metadata, while every historical reward and mood outcome
     * remains the same. The configured plain timer now presents itself as plain.
     */
    @Test fun mosFlavorChangesNothingTheWalkOrTheMoodSees() {
        val state = history(2L to 60, 9L to 30).joined().withReadingMinutes(1L to 45, 8L to 60)
        val flavored = state
            .applyingTimeEdits("reading", listOf(TimeEdit.SetMotif(null)), on(3))
            .applyingTimeEdits(piano.id, listOf(TimeEdit.SetMotif(MoMotif.Book)), on(3))
        assertNull(flavored.time.single { it.id == "reading" }.motif)
        assertEquals(MoMotif.Book, flavored.time.single { it.id == piano.id }.motif)

        (0L..14L).forEach { day ->
            assertEquals("day $day", streak(state, day, weightAndReading), streak(flavored, day, weightAndReading))
            val mood = moMood(flavored, weightAndReading, on(day))
            assertEquals("day $day", InheritedMoEvaluation(state, weightAndReading, on(day)).mood,
                InheritedMoEvaluation(flavored, weightAndReading, on(day)).mood)
            assertEquals(MoContentKey(MoViewKind.Time), mood.trackerContent[readingRef])
        }
    }

    /** Only Mo's tasks can be set aside, so a joined time view left open on its Sunday keeps today from looking covered. */
    @Test fun anOpenJoinedViewKeepsTodayFromLookingSetAside() {
        fun AppState.withoutTodaysWeight() = copy(measurementLogs = measurementLogs.filterNot { it.dateKey == on(13).toString() })
        fun setAside(state: AppState) = moMood(state.withoutTodaysWeight(), onlyWeight, on(13), chosenTrackers = setOf(weightRef)).todayIsSetAside

        assertFalse(setAside(history(9L to 45).joined()))
        // With its week's goal reached, or outside the streak, it holds nothing open.
        assertTrue(setAside(history(9L to 60).joined()))
        assertTrue(setAside(history(9L to 45)))
    }
}

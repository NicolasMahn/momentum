package de.nicolasmahn.momentum.domain.time

import de.nicolasmahn.momentum.domain.checklist.CheckpointTime
import de.nicolasmahn.momentum.domain.checklist.Dated
import de.nicolasmahn.momentum.domain.checklist.MoMotif
import de.nicolasmahn.momentum.domain.checklist.RewardPeriod
import de.nicolasmahn.momentum.domain.checklist.ViewHue
import de.nicolasmahn.momentum.domain.checklist.ViewIcon
import de.nicolasmahn.momentum.domain.checklist.WhenAway
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import de.nicolasmahn.momentum.domain.AppState
import de.nicolasmahn.momentum.domain.time.TimeLog
import de.nicolasmahn.momentum.domain.time.withReading
import de.nicolasmahn.momentum.domain.time.applyingTimeEdits
import de.nicolasmahn.momentum.domain.time.firstLoggedDay
import de.nicolasmahn.momentum.domain.compatibility.readingTime

class TimeEditsTest {
    private val today = LocalDate.parse("2026-10-13")
    private val reading = testReadingTime()
    private val piano = testMadeTime()

    private fun TimeConfiguration.refuses(edit: TimeEdit) {
        assertThrows(edit.toString(), IllegalArgumentException::class.java) { applyEdit(edit, today) }
    }

    /** The name and the look judge no week, so they change everywhere at once, Reading's as a made view's. */
    @Test fun theNameAndTheLookChangePlainly() {
        listOf(reading, piano).forEach { view ->
            val edited = view
                .applyEdit(TimeEdit.Rename("Evening"), today)
                .applyEdit(TimeEdit.SetIcon(ViewIcon.Heart), today)
                .applyEdit(TimeEdit.SetHue(ViewHue.Plum), today)
            assertEquals(view.copy(label = "Evening", icon = ViewIcon.Heart, hue = ViewHue.Plum), edited)
            view.refuses(TimeEdit.Rename(" "))
        }
    }

    /**
     * Mo's flavor is a look: it changes plainly, Reading's to Plain and a made view's to Reading,
     * and changes nothing else. A motif Mo has nothing for while a timer runs is refused.
     */
    @Test fun mosFlavorChangesPlainlyAndOnlyToWhatMoHasForATimer() {
        assertEquals(reading.copy(mo = reading.mo.copy(motif = null)), reading.applyEdit(TimeEdit.SetMotif(null), today))
        assertEquals(piano.copy(mo = piano.mo.copy(motif = MoMotif.Book)), piano.applyEdit(TimeEdit.SetMotif(MoMotif.Book), today))
        assertEquals(reading, reading.applyingEdits(listOf(TimeEdit.SetMotif(null), TimeEdit.SetMotif(MoMotif.Book)), today))
        // In the streak for days, it is still not dated: no week was ever judged by it.
        val joined = piano.applyEdit(TimeEdit.JoinRewards, today.minusDays(9))
        assertEquals(joined.copy(mo = joined.mo.copy(motif = MoMotif.Book)), joined.applyEdit(TimeEdit.SetMotif(MoMotif.Book), today))
        (MoMotif.entries - TimerMotifs.toSet()).forEach { motif ->
            reading.refuses(TimeEdit.SetMotif(motif))
            piano.refuses(TimeEdit.SetMotif(motif))
        }
        // Refused, it is left out and the other edits are applied.
        assertEquals(piano.copy(label = "Keys"), piano.applyingEdits(listOf(TimeEdit.SetMotif(MoMotif.Pills), TimeEdit.Rename("Keys")), today))
    }

    /** Reading's goal, its bank's size and its holiday rule have always judged weeks: each changes from the day it is saved, and every earlier day keeps what it had. */
    @Test fun readingsGoalBankAndHolidayRuleChangeFromToday() {
        val edited = reading
            .applyEdit(TimeEdit.SetGoal(90), today)
            .applyEdit(TimeEdit.SetBankSize(5), today)
            .applyEdit(TimeEdit.SetWhenAway(WhenAway.Excused), today)
        assertEquals(Dated(listOf(Dated.Revision(60), Dated.Revision(90, today))), edited.goal)
        assertEquals(Dated(listOf(Dated.Revision(3), Dated.Revision(5, today))), edited.rewards!!.maxPasses)
        assertEquals(Dated(listOf(Dated.Revision(WhenAway.Due), Dated.Revision(WhenAway.Excused, today))), edited.whenAway)
        assertEquals(60, edited.goal.on(today.minusDays(1)))
        assertEquals(3, edited.rewards!!.maxPasses.on(today.minusDays(1)))
        assertEquals(WhenAway.Due, edited.whenAway.on(today.minusDays(1)))
        assertNull("It stays in the streak over its whole history", edited.rewards!!.periods)
    }

    /** A revision that starts today judged no week yet: a second change that day replaces it, and one taken back leaves nothing. */
    @Test fun aSecondChangeOnTheSameDayReplacesTheFirstAndATakenBackOneLeavesNothing() {
        val twice = reading.applyingEdits(listOf(TimeEdit.SetGoal(90), TimeEdit.SetGoal(120), TimeEdit.SetBankSize(5), TimeEdit.SetBankSize(2), TimeEdit.SetWhenAway(WhenAway.Excused)), today)
        assertEquals(Dated(listOf(Dated.Revision(60), Dated.Revision(120, today))), twice.goal)
        assertEquals(Dated(listOf(Dated.Revision(3), Dated.Revision(2, today))), twice.rewards!!.maxPasses)

        val takenBack = twice.applyingEdits(listOf(TimeEdit.SetGoal(60), TimeEdit.SetBankSize(3), TimeEdit.SetWhenAway(WhenAway.Due)), today)
        assertEquals(reading, takenBack)
    }

    /** A later change keeps every revision before it. */
    @Test fun aLaterChangeKeepsTheEarlierRevisions() {
        val later = reading.applyEdit(TimeEdit.SetGoal(90), today).applyEdit(TimeEdit.SetGoal(45), today.plusDays(10))
        assertEquals(Dated(listOf(Dated.Revision(60), Dated.Revision(90, today), Dated.Revision(45, today.plusDays(10)))), later.goal)
    }

    /** What no time view takes is refused of Reading too: a lowered count, a goal no week can ask, an empty bank. */
    @Test fun readingRefusesWhatIsFixedAboutIt() {
        listOf(
            TimeEdit.SetWhenAway(WhenAway.LowerTarget),
            TimeEdit.SetGoal(0),
            TimeEdit.SetGoal(10081),
            TimeEdit.SetBankSize(0),
        ).forEach { reading.refuses(it) }
        // An edit that is refused is left out, and the others are applied.
        assertEquals(reading.copy(label = "Books"), reading.applyingEdits(listOf(TimeEdit.SetWhenAway(WhenAway.LowerTarget), TimeEdit.Rename("Books"), TimeEdit.SetGoal(0)), today))
    }

    /** Reading's reminder is its checkpoint, switched and timed as a made view's is, undated. */
    @Test fun readingsReminderIsItsCheckpoint() {
        val at = CheckpointTime.Fixed(LocalTime.of(20, 0))
        assertEquals(reading.checkpoint.copy(remind = true, at = at), reading.applyingEdits(listOf(TimeEdit.SetRemind(true), TimeEdit.SetReminderTime(at)), today).checkpoint)
    }

    /**
     * Reading leaves and joins the streak as a made view does, dated: leaving closes the period it
     * has been in since its first log, which the state tells, and keeps the bank's size and steps;
     * joining the next day reopens it, and joining while it takes part changes nothing.
     */
    @Test fun readingLeavesAndJoinsTheStreakFromItsFirstLog() {
        val first = LocalDate.parse("2026-09-01")
        val left = reading.applyEdit(TimeEdit.LeaveRewards, today, firstRecordedDay = first)
        assertEquals(listOf(RewardPeriod(first.with(java.time.DayOfWeek.MONDAY), today.minusDays(1))), left.rewards?.periods)
        assertEquals(reading.rewards?.copy(periods = left.rewards?.periods), left.rewards)
        assertEquals(emptyList<RewardPeriod>(), reading.applyEdit(TimeEdit.LeaveRewards, today).rewards?.periods)
        assertEquals(listOf(RewardPeriod(first.with(java.time.DayOfWeek.MONDAY))), left.applyEdit(TimeEdit.JoinRewards, today).rewards?.periods)
        assertEquals(reading, reading.applyEdit(TimeEdit.JoinRewards, today))

        val state = AppState(timeLogs = listOf(TimeLog("reading", today.toString(), 2L, 20), TimeLog("reading", first.toString(), 1L))).withReading()
        assertEquals(first, state.firstLoggedDay("reading"))
        assertNull(AppState().withReading().firstLoggedDay("reading"))
        assertEquals(left, state.applyingTimeEdits("reading", listOf(TimeEdit.LeaveRewards), today).readingTime)
    }

    /** Before a made view ever counted, no day was judged by its goal: it changes plainly, and it has no bank whose size could be set. */
    @Test fun aMadeViewsGoalChangesPlainlyWhileNoDayWasJudged() {
        assertEquals(Dated.always(90), piano.applyEdit(TimeEdit.SetGoal(90), today).goal)
        piano.refuses(TimeEdit.SetBankSize(5))
        piano.refuses(TimeEdit.SetGoal(0))
        piano.refuses(TimeEdit.SetWhenAway(WhenAway.LowerTarget))
    }

    /**
     * A timer outside the streak still shows each week's minutes against its goal, so once a day
     * before today holds minutes, a raised goal starts today and the finished week keeps its goal.
     * Minutes recorded only today leave no earlier week to keep.
     */
    @Test fun aGoalRaisedOutsideTheStreakKeepsTheGoalOfTheWeeksWithMinutes() {
        val monday = LocalDate.parse("2026-10-12")
        val made = testMadeTime(goal = 30)
        fun logged(vararg days: Pair<String, Int>) = AppState(
            time = listOf(made),
            timeLogs = days.mapIndexed { index, (day, minutes) -> TimeLog(made.id, day, index.toLong(), minutes) },
        )
        val raised = logged("2026-10-08" to 13, "2026-10-10" to 10)
            .applyingTimeEdits(made.id, listOf(TimeEdit.SetGoal(45)), monday).time.single()
        assertEquals(30, raised.goalOfWeek(LocalDate.parse("2026-10-05")))
        assertEquals(45, raised.goalOfWeek(monday))
        assertNull(raised.rewards)

        val onlyToday = logged("2026-10-12" to 20).applyingTimeEdits(made.id, listOf(TimeEdit.SetGoal(45)), monday).time.single()
        assertEquals(Dated.always(45), onlyToday.goal)

        // The settings page replays its draft without the records: changed and changed back, it is the goal stored.
        assertEquals(raised, raised.applyingEdits(listOf(TimeEdit.SetGoal(60), TimeEdit.SetGoal(45)), monday))
    }

    /** It joins with the bank every time view starts with, from today; joined today, its goal and its bank still change plainly. */
    @Test fun aMadeViewJoinsFromTodayWithTheStartingBank() {
        val joined = piano.applyEdit(TimeEdit.JoinRewards, today)
        assertEquals(TimeRewards(maxPasses = 3, periods = listOf(RewardPeriod(today))), joined.rewards)
        assertEquals(joined, joined.applyEdit(TimeEdit.JoinRewards, today))

        val sameDay = joined.applyingEdits(listOf(TimeEdit.SetGoal(90), TimeEdit.SetBankSize(5)), today)
        assertEquals(Dated.always(90), sameDay.goal)
        assertEquals(Dated.always(5), sameDay.rewards!!.maxPasses)
    }

    /** Once a day before today took part, its goal and its bank's size are dated, as Reading's are. */
    @Test fun aMadeViewsGoalAndBankAreDatedOnceADayWasJudged() {
        val joined = piano.applyEdit(TimeEdit.JoinRewards, today.minusDays(3))
        val edited = joined.applyingEdits(listOf(TimeEdit.SetGoal(90), TimeEdit.SetBankSize(5)), today)
        assertEquals(Dated(listOf(Dated.Revision(60), Dated.Revision(90, today))), edited.goal)
        assertEquals(Dated(listOf(Dated.Revision(3), Dated.Revision(5, today))), edited.rewards!!.maxPasses)

        // Out again, the weeks it took part in were still judged, so a change stays dated.
        val left = joined.applyEdit(TimeEdit.LeaveRewards, today)
        assertEquals(Dated(listOf(Dated.Revision(60), Dated.Revision(90, today))), left.applyEdit(TimeEdit.SetGoal(90), today).goal)
    }

    /** It leaves after yesterday and keeps its closed periods and its bank's size; a period that only began today leaves nothing. */
    @Test fun aMadeViewLeavesAfterYesterdayAndJoinsAgain() {
        val joined = piano.applyEdit(TimeEdit.JoinRewards, today.minusDays(3)).applyEdit(TimeEdit.SetBankSize(5), today.minusDays(3))
        val left = joined.applyEdit(TimeEdit.LeaveRewards, today)
        assertEquals(TimeRewards(maxPasses = 5, periods = listOf(RewardPeriod(today.minusDays(3), today.minusDays(1)))), left.rewards)
        assertEquals(left, left.applyEdit(TimeEdit.LeaveRewards, today))

        // Joining again on the day it left reopens the period: the leave is taken back.
        assertEquals(joined, left.applyEdit(TimeEdit.JoinRewards, today))
        // A later day starts a second period.
        assertEquals(
            listOf(RewardPeriod(today.minusDays(3), today.minusDays(1)), RewardPeriod(today.plusDays(2))),
            left.applyEdit(TimeEdit.JoinRewards, today.plusDays(2)).rewards!!.periods,
        )

        val joinedToday = piano.applyEdit(TimeEdit.JoinRewards, today)
        assertEquals(emptyList<RewardPeriod>(), joinedToday.applyEdit(TimeEdit.LeaveRewards, today).rewards!!.periods)
        // A view that never joined has nothing to leave.
        assertNull(piano.applyEdit(TimeEdit.LeaveRewards, today).rewards)
    }

    /**
     * The steps a week earns by are the bank's, and change as its size does: Reading's from the
     * day they are saved, a made view's plainly until a day before today was judged. A view
     * without a bank has none to set, and a list that cannot be one is never built.
     */
    @Test fun theStepsChangeAsTheBanksSizeDoes() {
        val one = TokenSteps(listOf(TokenStep(100, 1)))
        val three = TokenSteps(listOf(TokenStep(100, 1), TokenStep(150, 2), TokenStep(300, 5)))
        val lowered = reading.applyEdit(TimeEdit.SetTokenSteps(one), today)
        assertEquals(Dated(listOf(Dated.Revision(DefaultTokenSteps), Dated.Revision(one, today))), lowered.rewards!!.tokenSteps)
        assertEquals(reading.rewards!!.copy(tokenSteps = lowered.rewards!!.tokenSteps), lowered.rewards)
        assertEquals(DefaultTokenSteps, lowered.rewards!!.tokenSteps.on(today.minusDays(1)))
        // A second change that day replaces the first, and one taken back leaves nothing.
        assertEquals(Dated(listOf(Dated.Revision(DefaultTokenSteps), Dated.Revision(three, today))), lowered.applyEdit(TimeEdit.SetTokenSteps(three), today).rewards!!.tokenSteps)
        assertEquals(reading, lowered.applyEdit(TimeEdit.SetTokenSteps(DefaultTokenSteps), today))

        piano.refuses(TimeEdit.SetTokenSteps(three))
        assertThrows(IllegalArgumentException::class.java) { TokenSteps(listOf(TokenStep(100, 1), TokenStep(100, 2))) }

        // Joined today, no day was judged yet: it changes plainly, and the bank's size stays.
        val joinedToday = piano.applyEdit(TimeEdit.JoinRewards, today)
        assertEquals(Dated.always(DefaultTokenSteps), joinedToday.rewards!!.tokenSteps)
        assertEquals(TimeRewards(maxPasses = 3, periods = listOf(RewardPeriod(today)), tokenSteps = three), joinedToday.applyEdit(TimeEdit.SetTokenSteps(three), today).rewards)
        // Once a day before today took part it is dated, and a view that leaves keeps it for when it joins again.
        val raised = piano.applyEdit(TimeEdit.JoinRewards, today.minusDays(3)).applyEdit(TimeEdit.SetTokenSteps(three), today)
        assertEquals(Dated(listOf(Dated.Revision(DefaultTokenSteps), Dated.Revision(three, today))), raised.rewards!!.tokenSteps)
        assertEquals(raised.rewards!!.tokenSteps, raised.applyEdit(TimeEdit.LeaveRewards, today).rewards!!.tokenSteps)
    }

    /** What an away day means is dated on its own, in or out of the streak: its reminder reads the rule of the day it asks about. */
    @Test fun aMadeViewsHolidayRuleIsAlwaysDated() {
        val edited = piano.applyEdit(TimeEdit.SetWhenAway(WhenAway.Excused), today)
        assertEquals(Dated(listOf(Dated.Revision(WhenAway.Due), Dated.Revision(WhenAway.Excused, today))), edited.whenAway)
        assertEquals(piano, edited.applyEdit(TimeEdit.SetWhenAway(WhenAway.Due), today))
    }

    /** Its reminder is switched and moved plainly, as a checkpoint's is. */
    @Test fun aMadeViewsReminderIsSwitchedAndMoved() {
        val edited = piano.applyingEdits(listOf(TimeEdit.SetRemind(true), TimeEdit.SetReminderTime(CheckpointTime.BeforeSleep(30))), today)
        assertEquals(piano.checkpoint!!.copy(remind = true, at = CheckpointTime.BeforeSleep(30)), edited.checkpoint)
    }
}

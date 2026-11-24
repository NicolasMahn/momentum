package de.nicolasmahn.momentum.domain.mo

import de.nicolasmahn.momentum.domain.compatibility.MoNeed
import de.nicolasmahn.momentum.domain.mo.compatibility.InheritedMoEvaluation
import de.nicolasmahn.momentum.domain.compatibility.ReadingTimeId
import de.nicolasmahn.momentum.domain.time.TimeLog
import de.nicolasmahn.momentum.domain.AppState
import de.nicolasmahn.momentum.domain.ActivityTrackerRef
import de.nicolasmahn.momentum.domain.trackerRef
import de.nicolasmahn.momentum.domain.activity.ActivityEdit
import de.nicolasmahn.momentum.domain.activity.activityRules
import de.nicolasmahn.momentum.domain.activity.applyingActivityEdits
import de.nicolasmahn.momentum.domain.activity.withActivity
import de.nicolasmahn.momentum.domain.checklist.ChecklistEdit
import de.nicolasmahn.momentum.domain.checklist.RewardPeriod
import de.nicolasmahn.momentum.domain.checklist.applyEdit
import de.nicolasmahn.momentum.domain.measurement.MeasurementEdit
import de.nicolasmahn.momentum.domain.measurement.applyingMeasurementEdits
import de.nicolasmahn.momentum.domain.compatibility.weightRewards
import de.nicolasmahn.momentum.domain.measurement.withWeight
import de.nicolasmahn.momentum.domain.questions.QuestionsEdit
import de.nicolasmahn.momentum.domain.questions.applyingQuestionsEdits
import de.nicolasmahn.momentum.domain.compatibility.eatingRewards
import de.nicolasmahn.momentum.domain.questions.withEating
import de.nicolasmahn.momentum.domain.compatibility.SupplementsChecklistId
import de.nicolasmahn.momentum.domain.compatibility.supplementsChecklist
import de.nicolasmahn.momentum.domain.time.TimeEdit
import de.nicolasmahn.momentum.domain.time.applyingTimeEdits
import de.nicolasmahn.momentum.domain.compatibility.readingRules
import de.nicolasmahn.momentum.domain.time.withReading
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every task of Mo's leaves and joins the streak, and is hidden and shown, by the day, as a made
 * view is. Weeks are written as seven characters, Monday first, `x` for a day with a record. The
 * first Monday is after the reward start, so the token rules judge throughout.
 */
class OrdinaryViewsRewardsTest {
    private val monday = LocalDate.parse("2026-10-05")
    private fun on(day: Int) = monday.plusDays(day.toLong())
    private fun MoStreak.markOn(day: Int) = history.single { it.date == on(day) }.mark
    private fun MoStreak.marks(days: IntRange) = days.map { markOn(it) }
    private fun MoStreak.restedTrackersOn(days: IntRange) = days.map { day -> history.single { it.date == on(day) }.restedTrackers }
    private fun log(day: Int, minutes: Int) = TimeLog(ReadingTimeId, on(day).toString(), day.toLong(), minutes)

    /**
     * Weight leaves the streak on a Wednesday: its bank's one period closes on Tuesday, every
     * mark up to Tuesday stays what it was, from Wednesday it is not due, Mo does not wait for it
     * and its bank is out of sight. Joining again on Wednesday takes the leave back and reopens
     * the period; joining on Thursday starts a second one, and Thursday is due again.
     */
    @Test fun weightLeavesTheStreakOnAWednesdayAndJoinsAgain() {
        val kept = AppState().withWeight().keepingWeeks(MoNeed.Weight, monday, "xxxxxxx", "xx.....")
        val left = kept.applyingMeasurementEdits("weight", listOf(MeasurementEdit.LeaveRewards), on(9))
        val saturday = on(12)

        assertEquals(listOf(RewardPeriod(on(0), on(8))), left.weightRewards.periods)
        val before = habitStreak(kept, only(MoNeed.Weight), saturday)
        val after = habitStreak(left, only(MoNeed.Weight), saturday)
        assertEquals(before.marks(0..8), after.marks(0..8))
        assertEquals(listOf(MoDayMark.Protected, MoDayMark.Protected, MoDayMark.Frozen), before.marks(9..11))
        assertEquals(List(3) { MoDayMark.NothingDue }, after.marks(9..11))
        assertEquals(MoDayMark.NothingDue, after.week[5])
        assertFalse(MoNeed.Weight.view.trackerRef in after.standings)
        assertEquals(listOf(MoNeed.Weight.view.trackerRef), moMood(kept, only(MoNeed.Weight), saturday).missingTrackersToday)
        assertEquals(moMood(kept, only(MoNeed.Weight), saturday).missingTrackersToday, moMood(left, only(MoNeed.Weight), saturday).missingTrackersToday)

        val takenBack = left.applyingMeasurementEdits("weight", listOf(MeasurementEdit.JoinRewards), on(9))
        assertEquals(listOf(RewardPeriod(on(0))), takenBack.weightRewards.periods)
        val thursday = left.applyingMeasurementEdits("weight", listOf(MeasurementEdit.JoinRewards), on(10))
        assertEquals(listOf(RewardPeriod(on(0), on(8)), RewardPeriod(on(10))), thursday.weightRewards.periods)
        val back = habitStreak(thursday, only(MoNeed.Weight), saturday)
        assertEquals(listOf(MoDayMark.NothingDue, MoDayMark.Protected, MoDayMark.Protected), back.marks(9..11))
        assertEquals(listOf(MoNeed.Weight.view.trackerRef), moMood(thursday, only(MoNeed.Weight), saturday).missingTrackersToday)
        assertEquals(0, back.standings.getValue(MoNeed.Weight.view.trackerRef).passes)
        assertTrue(back.standings.getValue(MoNeed.Weight.view.trackerRef).nextToken != null)
    }

    /** Eating leaves on a Wednesday from its first reflection's due day, and its days up to Tuesday stay what they were. */
    @Test fun eatingLeavesTheStreakOnAWednesdayFromItsFirstReflectionsDueDay() {
        val kept = AppState().withEating().keepingWeeks(MoNeed.Eating, monday, "xxxxxxx", "xx.....")
        val left = kept.applyingQuestionsEdits("eating", listOf(QuestionsEdit.LeaveRewards), on(9))
        val saturday = on(12)

        assertEquals(listOf(RewardPeriod(on(0), on(8))), left.eatingRewards.periods)
        val before = habitStreak(kept, only(MoNeed.Eating), saturday)
        val after = habitStreak(left, only(MoNeed.Eating), saturday)
        assertEquals(before.marks(0..8), after.marks(0..8))
        assertEquals(List(3) { MoDayMark.NothingDue }, after.marks(9..11))
        assertFalse(MoNeed.Eating.view.trackerRef in after.standings)
        assertEquals(moMood(kept, only(MoNeed.Eating), saturday).missingTrackersToday, moMood(left, only(MoNeed.Eating), saturday).missingTrackersToday)
        assertEquals(listOf(RewardPeriod(on(0))), left.applyingQuestionsEdits("eating", listOf(QuestionsEdit.JoinRewards), on(9)).eatingRewards.periods)
        assertEquals(listOf(RewardPeriod(on(0), on(8)), RewardPeriod(on(10))), left.applyingQuestionsEdits("eating", listOf(QuestionsEdit.JoinRewards), on(10)).eatingRewards.periods)
    }

    /**
     * Reading's weeks are whole from its first log, a Wednesday, so its first week is judged and
     * earns on its Sunday, and still is after leaving weeks later closes the inherited period. The
     * week it left in is neither judged nor earning, and a rejoin on the Thursday of that week
     * counts from the next Monday.
     */
    @Test fun readingsWeeksAreWholeFromItsFirstLogAndAWeekItLeftOrRejoinedInIsNeitherJudgedNorEarning() {
        val logs = listOf(log(2, 60), log(9, 60), log(16, 60), log(17, 60), log(23, 60))
        val kept = AppState(timeLogs = logs).withReading().applyingTimeEdits("reading", listOf(TimeEdit.SetBankSize(5)), on(0))
        val left = kept.applyingTimeEdits("reading", listOf(TimeEdit.LeaveRewards), on(16))
        val rejoined = left.applyingTimeEdits("reading", listOf(TimeEdit.JoinRewards), on(17))
        val fourthMonday = on(28)

        assertEquals(listOf(RewardPeriod(on(0), on(15))), left.readingRules.rewards?.periods)
        assertEquals(listOf(RewardPeriod(on(0), on(15)), RewardPeriod(on(17))), rejoined.readingRules.rewards?.periods)
        val before = habitStreak(kept, only(MoNeed.Reading), fourthMonday)
        val after = habitStreak(rejoined, only(MoNeed.Reading), fourthMonday)
        assertEquals(before.marks(0..13), after.marks(0..13))
        assertEquals(listOf(MoDayMark.Perfect, MoDayMark.Perfect), listOf(after.markOn(6), after.markOn(13)))
        assertEquals(MoDayMark.Perfect, before.markOn(20))
        assertEquals(MoDayMark.NothingDue, after.markOn(20))
        assertEquals(MoDayMark.Perfect, after.markOn(27))
        assertEquals(5, before.standings.getValue(MoNeed.Reading.view.trackerRef).passes)
        assertEquals(4, after.standings.getValue(MoNeed.Reading.view.trackerRef).passes)
        assertEquals(listOf(MoNeed.Reading.view.trackerRef), habitStreak(kept, only(MoNeed.Reading), on(21)).earnedTrackersYesterday)
        assertEquals(emptyList<de.nicolasmahn.momentum.domain.TrackerRef>(), habitStreak(rejoined, only(MoNeed.Reading), on(21)).earnedTrackersYesterday)
        assertEquals(setOf(on(6), on(13), on(20), on(27)), MoLedger.of(kept, only(MoNeed.Reading), fourthMonday).history(MoNeed.Reading.view.trackerRef).keys)
        assertEquals(setOf(on(6), on(13), on(20), on(27)), MoLedger.of(rejoined, only(MoNeed.Reading), fourthMonday).history(MoNeed.Reading.view.trackerRef).keys)
    }

    /**
     * Activity is judged day by day: leaving on a Wednesday leaves Monday's and Tuesday's
     * judgments and free days as they were, deals no free day and no rest token while it is out,
     * and a rejoin on Thursday makes Thursday due with the free days its span has left.
     */
    @Test fun activityLeavesAndRejoinsDayByDay() {
        val kept = AppState().withActivity().keepingWeeks(MoNeed.Activity, monday, "xxxxxxx", "xx.....")
        val left = kept.applyingActivityEdits(listOf(ActivityEdit.LeaveRewards), on(9))
        val rejoined = left.applyingActivityEdits(listOf(ActivityEdit.JoinRewards), on(10))
        val saturday = on(12)

        assertEquals(listOf(RewardPeriod(on(0), on(8))), left.activityRules.rewards.periods)
        val before = habitStreak(kept, only(MoNeed.Activity), saturday)
        val out = habitStreak(left, only(MoNeed.Activity), saturday)
        val back = habitStreak(rejoined, only(MoNeed.Activity), saturday)
        assertEquals(before.marks(0..8), out.marks(0..8))
        assertEquals(before.marks(0..8), back.marks(0..8))
        assertEquals(listOf(MoDayMark.Perfect, MoDayMark.Perfect, MoDayMark.Protected), before.marks(9..11))
        assertEquals(listOf(setOf(ActivityTrackerRef), setOf(ActivityTrackerRef), emptySet()), before.restedTrackersOn(9..11))
        assertEquals(List(3) { MoDayMark.NothingDue }, out.marks(9..11))
        assertEquals(List(3) { emptySet<de.nicolasmahn.momentum.domain.TrackerRef>() }, out.restedTrackersOn(9..11))
        assertEquals(0, out.freeDaysLeftByTracker[ActivityTrackerRef] ?: 0)
        assertFalse(ActivityTrackerRef in out.standings)
        assertEquals(moMood(kept, only(MoNeed.Activity), saturday).missingTrackersToday, moMood(left, only(MoNeed.Activity), saturday).missingTrackersToday)
        assertEquals(listOf(MoDayMark.NothingDue, MoDayMark.Perfect, MoDayMark.Perfect), back.marks(9..11))
        assertEquals(listOf(emptySet(), setOf(ActivityTrackerRef), setOf(ActivityTrackerRef)), back.restedTrackersOn(9..11))
        assertEquals(0, back.freeDaysLeftByTracker[ActivityTrackerRef] ?: 0)
        assertEquals(listOf(ActivityTrackerRef), moMood(rejoined, only(MoNeed.Activity), saturday).missingTrackersToday)
        assertEquals(MoLedger.of(kept, only(MoNeed.Activity), saturday).history(ActivityTrackerRef), MoLedger.of(rejoined, only(MoNeed.Activity), saturday).history(ActivityTrackerRef))
        assertEquals(MoLedger.of(kept, only(MoNeed.Activity), saturday).history(ActivityTrackerRef), MoLedger.of(left, only(MoNeed.Activity), saturday).history(ActivityTrackerRef))
    }

    /** Supplements out of the streak since a Wednesday is no longer waited for by Mo from that day; the days before are as they were. */
    @Test fun leavingTheSupplementBankDoesNotUnenrollItsExplicitMoPolicy() {
        val kept = AppState().keepingWeeks(MoNeed.Supplements, monday, "xxxxxxx", "xx.....")
        val left = kept.applyEdit(SupplementsChecklistId, ChecklistEdit.LeaveRewards, on(9))
        val saturday = on(12)

        assertEquals(listOf(RewardPeriod(on(0), on(8))), left.supplementsChecklist?.rewards?.periods)
        val before = MoLedger.of(kept, only(MoNeed.Supplements), saturday)
        val after = MoLedger.of(left, only(MoNeed.Supplements), saturday)
        assertEquals(before.history(MoNeed.Supplements.view.trackerRef), after.history(MoNeed.Supplements.view.trackerRef))
        assertEquals(listOf(MoNeed.Supplements.view.trackerRef), moMood(kept, only(MoNeed.Supplements), saturday).missingTrackersToday)
        assertEquals(moMood(kept, only(MoNeed.Supplements), saturday).missingTrackersToday, moMood(left, only(MoNeed.Supplements), saturday).missingTrackersToday)
    }

    /**
     * Weight hidden on a Wednesday and shown again on Friday: Tuesday and before are unchanged in
     * the walk and the ledger, Wednesday and Thursday are not due and stay unjudged, Friday is due,
     * its periods are untouched, and its week bank earns nothing for a week with a day hidden by a
     * dated change.
     */
    @Test fun weightHiddenOnAWednesdayAndShownOnFridayKeepsTuesdayAndEarnsNothingThatWeek() {
        val kept = AppState().withWeight().keepingWeeks(MoNeed.Weight, monday, "xxxxxxx", "xxxxxxx", "x......")
        val shown = only(MoNeed.Weight)
        val hidden = shown.withVisibility(MoNeed.Weight.view, visible = false, today = on(9)).withVisibility(MoNeed.Weight.view, visible = true, today = on(11))
        val nextMonday = on(14)

        assertNull(kept.weightRewards.periods)
        val before = habitStreak(kept, shown, nextMonday)
        val after = habitStreak(kept, hidden, nextMonday)
        assertEquals(before.marks(0..8), after.marks(0..8))
        assertEquals(List(5) { MoDayMark.Perfect }, before.marks(9..13))
        assertEquals(listOf(MoDayMark.NothingDue, MoDayMark.NothingDue, MoDayMark.Perfect, MoDayMark.Perfect, MoDayMark.Perfect), after.marks(9..13))
        assertEquals(3, before.standings.getValue(MoNeed.Weight.view.trackerRef).passes)
        assertEquals(2, after.standings.getValue(MoNeed.Weight.view.trackerRef).passes)
        assertEquals(listOf(MoNeed.Weight.view.trackerRef), before.earnedTrackersYesterday)
        assertEquals(emptyList<de.nicolasmahn.momentum.domain.TrackerRef>(), after.earnedTrackersYesterday)

        val ledgerShown = MoLedger.of(kept, shown, nextMonday)
        val ledgerHidden = MoLedger.of(kept, hidden, nextMonday)
        assertEquals(ledgerShown.on(on(8)).keptTrackers, ledgerHidden.on(on(8)).keptTrackers)
        assertEquals(ledgerShown.history(MoNeed.Weight.view.trackerRef) - on(9) - on(10), ledgerHidden.history(MoNeed.Weight.view.trackerRef))
        assertEquals(setOf(MoNeed.Weight.view.trackerRef), ledgerHidden.trackers)
        assertEquals(emptySet<de.nicolasmahn.momentum.domain.TrackerRef>(), MoLedger.of(kept, hidden, on(10)).trackers)
        assertEquals(listOf(MoNeed.Weight.view.trackerRef), moMood(kept, hidden, on(8)).keptTrackersToday)
        assertEquals(emptyList<de.nicolasmahn.momentum.domain.TrackerRef>(), moMood(kept, hidden, on(10)).missingTrackersToday)
    }

    /**
     * Eating's seventh-day token earned on a Monday stays after a hide on Wednesday, Tuesday is
     * protected as before, and shown again on Friday its count goes on from where it stood.
     */
    @Test fun eatingsTokenEarnedBeforeAHideStaysAndItsCountGoesOnFromTheShow() {
        // Kept from Tuesday to the next Monday, the seventh kept day.
        val kept = AppState().withEating().keepingWeeks(MoNeed.Eating, monday, ".xxxxxx", "x...x..")
        val shown = only(MoNeed.Eating)
        val hidden = shown.withVisibility(MoNeed.Eating.view, visible = false, today = on(9)).withVisibility(MoNeed.Eating.view, visible = true, today = on(11))
        val saturday = on(12)

        assertEquals(listOf(MoNeed.Eating.view.trackerRef), habitStreak(kept, hidden, on(8)).earnedTrackersYesterday)
        val before = habitStreak(kept, shown, saturday)
        val after = habitStreak(kept, hidden, saturday)
        assertEquals(before.marks(0..8), after.marks(0..8))
        assertEquals(MoDayMark.Protected, after.markOn(8))
        assertEquals(listOf(MoDayMark.NothingDue, MoDayMark.NothingDue, MoDayMark.Perfect), after.marks(9..11))
        assertEquals(1, after.standings.getValue(MoNeed.Eating.view.trackerRef).passes)
        assertEquals(1, after.standings.getValue(MoNeed.Eating.view.trackerRef).nextToken!!.done)
    }

    /**
     * Reading hidden by a dated change on a Wednesday earns nothing for that week whatever its
     * minutes; hidden from the beginning, beside a task that runs the walk, it earns by its minutes
     * as it always has.
     */
    @Test fun readingHiddenByADatedChangeEarnsNothingThatWeek() {
        val kept = AppState(timeLogs = listOf(log(0, 60), log(7, 120))).withReading().applyingTimeEdits("reading", listOf(TimeEdit.SetBankSize(5)), on(0))
        val shown = only(MoNeed.Reading)
        val hidden = shown.withVisibility(MoNeed.Reading.view, visible = false, today = on(9))
        val nextMonday = on(14)

        assertEquals(4, habitStreak(kept, shown, nextMonday).standings.getValue(MoNeed.Reading.view.trackerRef).passes)
        val hiddenReport = InheritedMoEvaluation(kept, hidden, nextMonday).streak
        assertEquals(2, hiddenReport.passes[MoNeed.Reading])
        assertEquals(4, InheritedMoEvaluation(kept.withWeight().keeping(MoNeed.Weight, on(0)), only(MoNeed.Weight), nextMonday).streak.passes[MoNeed.Reading])
        assertTrue(MoLedger.of(kept, hidden, nextMonday).history(MoNeed.Reading.view.trackerRef).keys.none { it > on(8) })
    }

    /**
     * A week that earns nothing says nothing of a next token: Reading read for its whole goal on
     * Monday, hidden on Wednesday and shown on Friday, shows no progress on Sunday, since Monday
     * brings it nothing. Weight's week bank, which grants weekly too, is the same.
     */
    @Test fun aWeekPausedByADatedHideShowsNoNextToken() {
        val read = AppState(timeLogs = listOf(log(0, 120))).withReading()
        val weighed = AppState().withWeight().keepingWeeks(MoNeed.Weight, monday, "xxxxxxx")
        fun pausedAndShown(need: MoNeed) =
            only(need).withVisibility(need.view, visible = false, today = on(2)).withVisibility(need.view, visible = true, today = on(4))
        val sunday = on(6)

        assertEquals(120, habitStreak(read, only(MoNeed.Reading), sunday).standings.getValue(MoNeed.Reading.view.trackerRef).nextToken!!.done)
        assertEquals(null, habitStreak(read, pausedAndShown(MoNeed.Reading), sunday).standings[MoNeed.Reading.view.trackerRef]?.nextToken)
        assertEquals(1, habitStreak(read, pausedAndShown(MoNeed.Reading), on(7)).standings.getValue(MoNeed.Reading.view.trackerRef).passes)
        assertEquals(null, habitStreak(weighed, pausedAndShown(MoNeed.Weight), sunday).standings[MoNeed.Weight.view.trackerRef]?.nextToken)
    }
}

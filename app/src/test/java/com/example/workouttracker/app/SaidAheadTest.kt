package com.example.workouttracker.app

import com.example.workouttracker.domain.ActivityTrackerRef
import com.example.workouttracker.domain.AppState
import com.example.workouttracker.domain.SubstancesTrackerRef
import com.example.workouttracker.domain.TrackerRef
import com.example.workouttracker.domain.mo.MoChoices
import com.example.workouttracker.domain.mo.MoMood
import com.example.workouttracker.domain.mo.canRest
import com.example.workouttracker.domain.mo.canQuiet
import com.example.workouttracker.domain.mo.checkpointsToSetAside
import com.example.workouttracker.domain.mo.habitDayMinuteOf
import com.example.workouttracker.domain.mo.moOverdueTrackers
import com.example.workouttracker.domain.mo.restingTrackersToday
import com.example.workouttracker.domain.mo.settledByChoice
import com.example.workouttracker.domain.mo.tokensCover
import com.example.workouttracker.domain.mo.trackerDueMinutes
import com.example.workouttracker.domain.substance.SubstanceNoneDay
import com.example.workouttracker.domain.substance.joiningStreak
import com.example.workouttracker.feature.mo.bankReservation
import com.example.workouttracker.feature.mo.canChooseRest
import com.example.workouttracker.feature.mo.canSetAside
import com.example.workouttracker.feature.mo.saidTodayCovers
import com.example.workouttracker.core.ui.DayCover
import com.example.workouttracker.feature.mo.tokensOnCheckpoints
import com.example.workouttracker.feature.mo.todayIsSetAside
import com.example.workouttracker.feature.mo.tokensLeft
import com.example.workouttracker.feature.mo.tokensSetAside
import com.example.workouttracker.feature.tracker.TrackerHomeStatus
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Wherever a view shows its tokens or rest days, a person can say now what the close would do
 * anyway: rest on a planned day a rest day covers, cover one checkpoint of several, cover a week's
 * amount before its Sunday, and cover a view that counts toward the streak without Mo. Saying it
 * stops the asking and settles the Home row. It never touches a record, and it leaves the streak
 * exactly as the reward walk made it: the walk does not read choices at all, so what it decides
 * at the close is the same whatever was said.
 */
class SaidAheadTest {
    private val wednesday = LocalDate.parse("2026-11-11")
    private val thursday = EverydayViews.thursday
    private val saturday = LocalDate.parse("2026-11-14")
    private val sunday = LocalDate.parse("2026-11-15")
    private val reminders = EverydayViews.reminders
    private val care = EverydayViews.care
    private val reading = EverydayViews.reading

    private fun mood(state: AppState, day: LocalDate, choices: MoChoices = MoChoices()): MoMood = EverydayViews.evaluation(state, day).moodWith(choices)

    private fun home(state: AppState, day: LocalDate, choices: MoChoices = MoChoices()): Map<TrackerRef, TrackerHomeStatus> {
        val evaluation = EverydayViews.evaluation(state, day)
        return evaluation.moodWith(choices).homeStatuses(started = evaluation.observations.filter { it.firstDay != null }.mapTo(mutableSetOf()) { it.ref })
    }

    private fun tasks(vararg refs: TrackerRef) = MoChoices(tasks = refs.toSet())

    private fun checkpoints(ref: TrackerRef, vararg ids: String) = MoChoices(checkpoints = mapOf(ref to ids.toSet()))

    private fun careTicks(day: LocalDate, vararg ticks: String) =
        "checklistDays" to """{"checklistId":"care","dateKey":"$day","timestampMillis":1,"ticks":[${ticks.joinToString(",") { "\"$it/toothbrushing\"" }}]}"""

    private fun read(day: LocalDate, minutes: Int) = "timeLogs" to """{"time":"reading","dateKey":"$day","timestampMillis":2,"minutes":$minutes}"""

    private fun active(vararg days: LocalDate) = days.map { "activityEntries" to """{"id":"b$it","activity":"gym","name":"Gym","dateKey":"$it","timestampMillis":1}""" }.toTypedArray()

    /**
     * What a choice may never move: the streak with its marks, banks and rest days as the walk
     * left them, and every fact read from the records.
     */
    private fun assertOnlyTheAskingChanged(unchosen: MoMood, chosen: MoMood) {
        assertEquals(unchosen.streak, chosen.streak)
        assertEquals(unchosen.keptTrackersToday, chosen.keptTrackersToday)
        assertEquals(unchosen.freeTrackersToday, chosen.freeTrackersToday)
        assertEquals(unchosen.dueToday, chosen.dueToday)
        assertEquals(unchosen.state, chosen.state)
        assertEquals(unchosen.trackerWeekProgress, chosen.trackerWeekProgress)
        assertEquals(unchosen.trackerWeeklyRemainders, chosen.trackerWeeklyRemainders)
        assertEquals(unchosen.trackerTokenRequirements, chosen.trackerTokenRequirements)
        assertEquals(unchosen.trackerOpenParts, chosen.trackerOpenParts)
    }

    // 1. Activity: rest today.

    // Wednesday is planned and the week is not done, so the rest day that covers it still asks.
    @Test fun aPlannedDayARestDayCoversCanBeSaidToRestAndThenNothingAsksForIt() {
        val state = EverydayViews.state()
        val asked = mood(state, wednesday)
        assertTrue(asked.canRest(ActivityTrackerRef))
        assertFalse(asked.canSetAside(ActivityTrackerRef))
        assertEquals(TrackerHomeStatus.Open(0, 1, optional = true), home(state, wednesday)[ActivityTrackerRef])
        assertEquals(listOf(ActivityTrackerRef), moOverdueTrackers(asked, reminders, LocalTime.of(19, 1)).filter { it == ActivityTrackerRef })

        val resting = mood(state, wednesday, tasks(ActivityTrackerRef))
        assertEquals(setOf(ActivityTrackerRef), resting.restChosenTrackers)
        assertTrue(ActivityTrackerRef in resting.restingTrackersToday)
        assertTrue(ActivityTrackerRef in resting.settledByChoice)
        assertEquals(TrackerHomeStatus.Rest, home(state, wednesday, tasks(ActivityTrackerRef))[ActivityTrackerRef])
        // A rest day leaves the calendar plain, today as on any day the close dealt one.
        assertEquals(emptyMap<TrackerRef, DayCover>(), resting.saidTodayCovers())
        assertEquals(emptyMap<TrackerRef, DayCover>(), asked.saidTodayCovers())
        assertEquals(emptyList<Int>(), resting.trackerDueMinutes(ActivityTrackerRef, reminders))
        assertFalse(ActivityTrackerRef in moOverdueTrackers(resting, reminders, LocalTime.of(23, 0)))
        // It can be taken back, and no token is held for it.
        assertTrue(resting.canChooseRest(ActivityTrackerRef))
        assertEquals(0, resting.tokensSetAside(ActivityTrackerRef))
        assertEquals(asked.tokensLeft(ActivityTrackerRef), resting.tokensLeft(ActivityTrackerRef))
        assertTrue(resting.chosenTrackersToday.isEmpty())
        assertOnlyTheAskingChanged(asked, resting)
    }

    @Test fun aDayThatAlreadyRestsOrHasNoRestDayLeftIsNotOfferedARestDay() {
        // The week is done: Saturday rests without a word, and saying so changes nothing.
        val weekDone = EverydayViews.state(*active(wednesday, thursday, thursday.plusDays(1)))
        assertFalse(mood(weekDone, saturday).canChooseRest(ActivityTrackerRef))
        assertEquals(mood(weekDone, saturday), mood(weekDone, saturday, tasks(ActivityTrackerRef)))

        // Both rest days went on Wednesday and Thursday: Saturday is owed, and only a token covers it.
        val state = EverydayViews.state()
        val owed = mood(state, saturday)
        assertFalse(owed.canChooseRest(ActivityTrackerRef))
        assertTrue(owed.canSetAside(ActivityTrackerRef))
        val covered = mood(state, saturday, tasks(ActivityTrackerRef))
        assertEquals(setOf(ActivityTrackerRef), covered.chosenTrackersToday)
        assertTrue(covered.restChosenTrackers.isEmpty())
        assertEquals(mapOf(ActivityTrackerRef to DayCover.Token), covered.saidTodayCovers())
        assertEquals(TrackerHomeStatus.Covered, home(state, saturday, tasks(ActivityTrackerRef))[ActivityTrackerRef])
    }

    // Friday, planned, was rested on, so the week's one rest day left is Saturday's or Sunday's:
    // Saturday asks for the workout, and saying it rests leaves Sunday to train.
    @Test fun aRestDayTheWeekNamesAsksOnceAPlannedDayTookItsRest() {
        val state = EverydayViews.state(*active(wednesday, thursday))
        val asked = mood(state, saturday)
        assertTrue(asked.canRest(ActivityTrackerRef))
        assertFalse(ActivityTrackerRef in asked.restingTrackersToday)
        assertEquals(TrackerHomeStatus.Open(0, 1, optional = true), home(state, saturday)[ActivityTrackerRef])

        val resting = mood(state, saturday, tasks(ActivityTrackerRef))
        assertTrue(ActivityTrackerRef in resting.restingTrackersToday)
        assertEquals(TrackerHomeStatus.Rest, home(state, saturday, tasks(ActivityTrackerRef))[ActivityTrackerRef])
        assertOnlyTheAskingChanged(asked, resting)

        // With Friday trained the week is done, and Saturday rests without a word.
        val done = EverydayViews.state(*active(wednesday, thursday, thursday.plusDays(1)))
        assertTrue(ActivityTrackerRef in mood(done, saturday).restingTrackersToday)
    }

    @Test fun theStreakViewsRowOfActivityIsPressedToRestWhereARestDayCovers() {
        val state = EverydayViews.state()
        val offered = requireNotNull(mood(state, wednesday).bankReservation(ActivityTrackerRef) {})
        assertTrue(offered.allowed && offered.rest && !offered.selected)
        val resting = requireNotNull(mood(state, wednesday, tasks(ActivityTrackerRef)).bankReservation(ActivityTrackerRef) {})
        assertTrue(resting.allowed && resting.rest && resting.selected)
        assertEquals(0, resting.count)
        // Where only a token covers the day, the same row sets one aside.
        val token = requireNotNull(mood(state, saturday).bankReservation(ActivityTrackerRef) {})
        assertTrue(token.allowed && !token.rest)
    }

    // 2. A checklist with several checkpoints: one token per checkpoint.

    // Care holds one token, and both of its checkpoints are open on Thursday.
    @Test fun aTokenIsSetAsideForOneCheckpointWhileTheBankCannotCoverTheDay() {
        val state = EverydayViews.state()
        val open = mood(state, thursday)
        assertEquals(1, open.tokensLeft(care))
        assertFalse(open.tokensCover(care))
        assertTrue(open.tokensOnCheckpoints(care))
        assertEquals(setOf("morning", "evening"), open.checkpointsToSetAside(care))

        val morning = mood(state, thursday, checkpoints(care, "morning"))
        assertEquals(mapOf(care to setOf("morning")), morning.checkpointsSetAside)
        assertEquals(1, morning.tokensSetAside(care))
        assertEquals(0, morning.tokensLeft(care))
        // The one token is taken: the evening has none to offer, and the morning's can be taken back.
        assertEquals(setOf("morning"), morning.checkpointsToSetAside(care))
        // The evening is still owed, so the checklist stays open, at the evening's time alone.
        assertTrue(care in morning.missingTrackersToday)
        assertEquals(TrackerHomeStatus.Open(0, 2), home(state, thursday, checkpoints(care, "morning"))[care])
        assertEquals(2, open.trackerDueMinutes(care, reminders).size)
        assertEquals(listOf(open.trackerDueMinutes(care, reminders).last()), morning.trackerDueMinutes(care, reminders))
        // Mid-morning the morning is late for Mo; set aside, it is not.
        assertTrue(care in moOverdueTrackers(open, reminders, LocalTime.of(11, 0)))
        assertFalse(care in moOverdueTrackers(morning, reminders, LocalTime.of(11, 0)))
        assertOnlyTheAskingChanged(open, morning)
    }

    @Test fun aChecklistIsCoveredOnceEveryCheckpointItOwesHasAToken() {
        val eveningDone = EverydayViews.state(careTicks(thursday, "evening"))
        val covered = mood(eveningDone, thursday, checkpoints(care, "morning"))
        assertFalse(care in covered.missingTrackersToday)
        assertEquals(setOf(care), covered.chosenTrackersToday)
        assertEquals(TrackerHomeStatus.Covered, home(eveningDone, thursday, checkpoints(care, "morning"))[care])
        assertEquals(emptyList<Int>(), covered.trackerDueMinutes(care, reminders))
        assertEquals(1, covered.tokensSetAside(care))
        assertOnlyTheAskingChanged(mood(eveningDone, thursday), covered)
    }

    @Test fun aCheckpointsTokenIsDroppedOnceItIsTickedOrTheBankNoLongerHoldsIt() {
        // Ticked since: nothing is owed there, so nothing is set aside for it.
        val morningDone = EverydayViews.state(careTicks(thursday, "morning"))
        assertTrue(mood(morningDone, thursday, checkpoints(care, "morning")).checkpointsSetAside.isEmpty())
        // One token cannot hold two checkpoints: the first keeps it.
        val both = mood(EverydayViews.state(), thursday, checkpoints(care, "morning", "evening"))
        assertEquals(mapOf(care to setOf("morning")), both.checkpointsSetAside)
        assertTrue(care in both.missingTrackersToday)
        // A choice of the whole checklist stands for all it owes, or for nothing.
        assertEquals(mood(EverydayViews.state(), thursday), mood(EverydayViews.state(), thursday, tasks(care)))
        val whole = mood(morningDone, thursday, tasks(care))
        assertEquals(mapOf(care to setOf("evening")), whole.checkpointsSetAside)
        assertEquals(setOf(care), whole.chosenTrackersToday)
    }

    @Test fun aChecklistWithOneCheckpointKeepsItsOneSwitch() {
        val supplements = EverydayViews.supplements
        val open = mood(EverydayViews.state(), thursday)
        assertTrue(open.canSetAside(supplements))
        assertFalse(open.tokensOnCheckpoints(supplements))
        assertTrue(open.checkpointsToSetAside(supplements).isEmpty())
    }

    // 3. A week's amount: quieted for a day before its Sunday, covered on it.

    // Reading asks 60 minutes a week and holds one token, which covers a quarter of the goal.
    @Test fun beforeItsSundayAWeeksAmountIsQuietedForTheDayAndNoTokenIsSetAside() {
        val short = EverydayViews.state(read(wednesday, 45))
        val open = mood(short, thursday)
        assertEquals(13, open.trackerWeeklyRemainders[reading])
        // The week can still be made up, so no token can cover it yet.
        assertFalse(open.canSetAside(reading))
        assertTrue(open.canQuiet(reading))
        assertEquals(listOf(habitDayMinuteOf(LocalTime.of(18, 0))), open.trackerDueMinutes(reading, reminders))

        val quiet = mood(short, thursday, tasks(reading))
        assertEquals(setOf(reading), quiet.quietTrackersToday)
        assertTrue(reading in quiet.settledByChoice)
        assertEquals(emptyList<Int>(), quiet.trackerDueMinutes(reading, reminders))
        // It holds no token, covers nothing, and the calendar shows nothing for it.
        assertTrue(quiet.chosenTrackersToday.isEmpty())
        assertEquals(open.tokensLeft(reading), quiet.tokensLeft(reading))
        assertEquals(TrackerHomeStatus.Open(0, 1), home(short, thursday, tasks(reading))[reading])
        assertEquals(emptyMap<TrackerRef, DayCover>(), quiet.saidTodayCovers())
        assertTrue(quiet.canQuiet(reading))
        assertOnlyTheAskingChanged(open, quiet)

        // From Friday he nudges for a week that is short, and not on a day it is quieted.
        val friday = thursday.plusDays(1)
        assertEquals(13, mood(short, friday).trackerWeeklyNudges[reading])
        assertNull(mood(short, friday, tasks(reading)).trackerWeeklyNudges[reading])
    }

    @Test fun onItsSundayAWeeksAmountIsCoveredWhileTheTokensCoverWhatItLacks() {
        val short = EverydayViews.state(read(wednesday, 45))
        val open = mood(short, sunday)
        assertTrue(reading in open.missingTrackersToday)
        assertTrue(open.canSetAside(reading))
        assertFalse(open.canQuiet(reading))

        val covered = mood(short, sunday, tasks(reading))
        assertFalse(reading in covered.missingTrackersToday)
        assertEquals(setOf(reading), covered.chosenTrackersToday)
        assertTrue(covered.quietTrackersToday.isEmpty())
        assertEquals(TrackerHomeStatus.Covered, home(short, sunday, tasks(reading))[reading])
        assertEquals(emptyList<Int>(), covered.trackerDueMinutes(reading, reminders))
        assertEquals(1, covered.tokensSetAside(reading))
        assertEquals(0, covered.tokensLeft(reading))
        assertEquals(DayCover.Token, covered.saidTodayCovers()[reading])
        assertOnlyTheAskingChanged(open, covered)
    }

    @Test fun aWeeksChoiceIsDroppedOnceTheGoalIsReachedOrTheTokensFallShortOnItsSunday() {
        // 58 minutes missing are four gaps, and the bank holds one token: Sunday cannot cover the
        // week, though a day before it can still be quieted.
        val farShort = EverydayViews.state()
        assertFalse(mood(farShort, sunday).canSetAside(reading))
        assertEquals(mood(farShort, sunday), mood(farShort, sunday, tasks(reading)))
        assertTrue(mood(farShort, thursday).canQuiet(reading))
        // Reached: there is nothing left to quiet or cover, and the row reads as kept.
        val reached = EverydayViews.state(read(wednesday, 60))
        assertFalse(mood(reached, thursday).canQuiet(reading))
        assertEquals(mood(reached, thursday), mood(reached, thursday, tasks(reading)))
        assertEquals(TrackerHomeStatus.Kept, home(reached, thursday, tasks(reading))[reading])
    }

    // 4. A view the stored flag never enrolled, watched because it counts toward the streak.

    /** Substances with the flag off, joined to the streak on [thursday], judged on yesterday's record. */
    private fun substancesJoined(vararg records: Pair<String, String>) =
        EverydayViews.state(*records).let { it.copy(substances = it.substances.joiningStreak(thursday)) }

    @Test fun substancesInTheStreakIsSetAsideWhileItsDayIsOpenAndItsTokensCover() {
        val state = substancesJoined()
        val open = mood(state, thursday)
        assertTrue(SubstancesTrackerRef in open.missingTrackersToday)
        assertTrue(open.streak.standings.getValue(SubstancesTrackerRef).openToday)
        assertTrue(open.canSetAside(SubstancesTrackerRef))
        assertTrue(requireNotNull(open.bankReservation(SubstancesTrackerRef) {}).allowed)

        val covered = mood(state, thursday, tasks(SubstancesTrackerRef))
        assertEquals(setOf(SubstancesTrackerRef), covered.chosenTrackersToday)
        assertFalse(SubstancesTrackerRef in covered.missingTrackersToday)
        assertEquals(TrackerHomeStatus.Covered, home(state, thursday, tasks(SubstancesTrackerRef))[SubstancesTrackerRef])
        assertEquals(1, covered.tokensSetAside(SubstancesTrackerRef))
        assertEquals(0, covered.tokensLeft(SubstancesTrackerRef))
        assertTrue(requireNotNull(covered.bankReservation(SubstancesTrackerRef) {}).selected)
        // The other tasks are still open, so the day is not covered yet.
        assertFalse(covered.todayIsSetAside)
        assertOnlyTheAskingChanged(open, covered)
    }

    @Test fun aChoiceForSubstancesIsDroppedOnceItsDayIsKept() {
        // Yesterday logged as a day without any: today's judgment is kept, and nothing is left to cover.
        val logged = substancesJoined().let { it.copy(substanceNoneDays = it.substanceNoneDays + SubstanceNoneDay(wednesday.toString(), 1L)) }
        val mood = mood(logged, thursday, tasks(SubstancesTrackerRef))
        assertTrue(mood.streak.standings.getValue(SubstancesTrackerRef).keptToday)
        assertFalse(mood.canSetAside(SubstancesTrackerRef))
        assertTrue(mood.chosenTrackersToday.isEmpty())
    }

    /** While it counts toward the streak, a checklist is the same task with the stored flag on or off, token for token. */
    @Test fun aChecklistInTheStreakIsTheSameTaskWithTheFlagOnOrOff() {
        fun withoutMo(state: AppState) = state.copy(checklists = state.checklists.map { if (it.id == "care") it.copy(mo = it.mo.copy(enabled = false)) else it })
        listOf(EverydayViews.state(), EverydayViews.state(careTicks(thursday, "morning"))).forEach { state ->
            assertEquals(mood(state, thursday), mood(withoutMo(state), thursday))
            assertEquals(home(state, thursday, tasks(care)), home(withoutMo(state), thursday, tasks(care)))
            assertEquals(mood(state, thursday, tasks(care)), mood(withoutMo(state), thursday, tasks(care)))
        }
        // Two checkpoints open are two gaps, and Care holds one token.
        val twoOpen = mood(withoutMo(EverydayViews.state()), thursday)
        assertEquals(2, twoOpen.trackerTokenRequirements[care])
        assertFalse(twoOpen.canSetAside(care))
    }

    @Test fun aRestDayLeftCoversAnOpenDayWithoutAToken() {
        fun withoutMo(state: AppState) = state.copy(activity = state.activity?.let { it.copy(mo = it.mo.copy(enabled = false)) })
        // Wednesday has a rest day left: the close deals it, so no token is offered.
        assertFalse(mood(withoutMo(EverydayViews.state()), wednesday).canSetAside(ActivityTrackerRef))
        // Saturday has none left, so a token covers it.
        assertTrue(mood(withoutMo(EverydayViews.state()), saturday).canSetAside(ActivityTrackerRef))
    }
}

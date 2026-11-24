package com.example.workouttracker.app

import com.example.workouttracker.domain.ActivityTrackerRef
import com.example.workouttracker.domain.AppState
import com.example.workouttracker.domain.SubstancesTrackerRef
import com.example.workouttracker.domain.TrackerRef
import com.example.workouttracker.domain.mo.MoMood
import com.example.workouttracker.domain.mo.habitDayMinuteOf
import com.example.workouttracker.domain.mo.moOverdueTrackers
import com.example.workouttracker.domain.mo.trackerDueMinutes
import com.example.workouttracker.domain.substance.SubstanceEdit
import com.example.workouttracker.domain.substance.SubstanceGoal
import com.example.workouttracker.domain.substance.applyEdit
import com.example.workouttracker.domain.time.TimeEdit
import com.example.workouttracker.domain.time.applyingTimeEdits
import com.example.workouttracker.feature.tracker.TrackerHomeStatus
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Home puts what is due first, in the order of the day: due within the hour or past due, then what has no time, then later, then what is done. */
class HomeOrderTest {
    private val open = TrackerHomeStatus.Open(0, 1)
    private val now = 600 // 14:00, ten hours after the day began at 04:00

    @Test fun whatIsNextComesFirstAndWhatIsDoneLast() {
        val rows = mapOf(
            "evening" to homeRowRank(open, listOf(now + 300), now),
            "kept" to homeRowRank(TrackerHomeStatus.Kept, emptyList(), now),
            "untimed" to homeRowRank(open, emptyList(), now),
            "inHalfAnHour" to homeRowRank(open, listOf(now + 30), now),
            "rest" to homeRowRank(TrackerHomeStatus.None, emptyList(), now),
            "pastDue" to homeRowRank(open, listOf(now - 120, now + 400), now),
        )
        val sorted = rows.entries.sortedWith(compareBy({ it.value.first }, { it.value.second })).map { it.key }
        assertEquals(listOf("pastDue", "inHalfAnHour", "untimed", "evening", "rest", "kept"), sorted)
    }

    @Test fun aTaskAFreeDayWouldCoverStandsByItsTimeLikeAnyOther() {
        val offered = open.copy(optional = true)
        for (due in listOf(listOf(now - 10), listOf(now + 30), listOf(now + 300))) {
            assertEquals(homeRowRank(open, due, now), homeRowRank(offered, due, now))
        }
    }

    private val thursday = EverydayViews.thursday
    private val reminders = EverydayViews.reminders
    private val care = EverydayViews.care
    private val supplements = EverydayViews.supplements
    private val weight = EverydayViews.weight
    private val eating = EverydayViews.eating
    private val reading = EverydayViews.reading
    private fun state(vararg records: Pair<String, String>): AppState = EverydayViews.state(*records)

    /** Each row's standing on [day], as Home shows it. */
    private fun statuses(state: AppState, day: LocalDate, setAside: Set<TrackerRef> = emptySet()): Pair<MoMood, Map<TrackerRef, TrackerHomeStatus>> {
        val evaluation = EverydayViews.evaluation(state, day)
        val mood = evaluation.moodWithTrackerChoices(setAside)
        return mood to mood.homeStatuses(started = evaluation.observations.filter { it.firstDay != null }.mapTo(mutableSetOf()) { it.ref })
    }

    /** Home's rows top to bottom at [time] on [day], as HomeScreen sorts them. */
    private fun order(state: AppState, time: LocalTime, day: LocalDate = thursday): List<TrackerRef> {
        val (mood, statuses) = statuses(state, day)
        return listOf(ActivityTrackerRef, supplements, care, weight, eating, reading, SubstancesTrackerRef).sortedBy { ref ->
            homeRowRank(statuses[ref] ?: TrackerHomeStatus.None, mood.trackerDueMinutes(ref, reminders), habitDayMinuteOf(time))
                .let { (group, minute) -> group.ordinal * 24 * 60 + minute }
        }
    }

    // 2026-11-14 at 12:37: Supplements, due at 12:30, stood above Care and Weight, open since waking.
    @Test fun whatHasWaitedSinceTheMorningStaysAboveWhatJustCameDue() {
        assertEquals(listOf(care, weight, eating, supplements), order(state(), LocalTime.of(12, 40)).take(4))
    }

    @Test fun beforeWakingTheMorningIsNextAndWhatAsksNothingIsLast() {
        val rows = order(state(), LocalTime.of(7, 30))
        assertEquals(listOf(care, weight, eating), rows.take(3))
        // Substances asks nothing of the streak.
        assertEquals(SubstancesTrackerRef, rows.last())
    }

    private val monday = LocalDate.parse("2026-11-16")

    /** A Monday afternoon: the morning kept, the evening, the week's reading and the planned workout open. */
    private fun mondayAfternoon() = EverydayViews.state(
        "checklistDays" to """{"checklistId":"supplements","dateKey":"2026-11-16","timestampMillis":1,"ticks":["midday/creatine"]}""",
        "checklistDays" to """{"checklistId":"care","dateKey":"2026-11-16","timestampMillis":1,"ticks":["morning/toothbrushing"]}""",
        "measurementLogs" to """{"measurement":"weight","dateKey":"2026-11-16","timestampMillis":1,"value":80.0}""",
        "questionEntries" to """{"id":"q","questions":"eating","dateKey":"2026-11-15","timestampMillis":1,"answers":{"quality":"good"}}""",
        today = monday,
    )

    // 2026-11-16 at 17:49: the workout planned for Monday, reminded at 18:00, stood below Care's
    // evening, because the week's free days could still cover it.
    @Test fun aPlannedWorkoutAFreeDayWouldCoverIsDueAtItsReminder() {
        val state = mondayAfternoon()
        val (mood, statuses) = statuses(state, monday)
        assertEquals(TrackerHomeStatus.Open(0, 1, optional = true), statuses[ActivityTrackerRef])

        // Activity and Reading are both due at 18:00, and Activity is first in Home's own order.
        assertEquals(listOf(ActivityTrackerRef, reading, care), order(state, LocalTime.of(17, 49), monday).take(3))
        // In the morning it is due later, in the order of the day: before Care's evening.
        assertEquals(listOf(ActivityTrackerRef, reading, care), order(state, LocalTime.of(9, 0), monday).take(3))
        // An hour late, Mo is cross about it whether a free day would cover it or not, and Home agrees.
        val evening = LocalTime.of(19, 30)
        assertEquals(listOf(ActivityTrackerRef), moOverdueTrackers(mood, reminders, evening))
        assertEquals(ActivityTrackerRef, order(state, evening, monday).first())
    }

    private fun active(vararg days: String) = days.map { "activityEntries" to """{"id":"a$it","activity":"gym","name":"Gym","dateKey":"$it","timestampMillis":1}""" }.toTypedArray()

    // The week plans Monday to Friday, and the history is active on Monday and Tuesday.
    @Test fun aRestDayAFreeDayCoversRestsAndSinksToTheKeptRows() {
        val saturday = LocalDate.parse("2026-11-14")
        val weekDone = state(*active("2026-11-11", "2026-11-12", "2026-11-13"))
        assertEquals(TrackerHomeStatus.Rest, statuses(weekDone, saturday).second[ActivityTrackerRef])
        assertEquals(ActivityTrackerRef, order(weekDone, LocalTime.of(21, 30), saturday).last { it != SubstancesTrackerRef })

        // The week is one day short and no planned day is left: the last free day covers Saturday
        // or Sunday, so Saturday is offered as a planned day is, not rested on unasked.
        val oneShort = state(*active("2026-11-11", "2026-11-12"))
        assertEquals(TrackerHomeStatus.Open(0, 1, optional = true), statuses(oneShort, saturday).second[ActivityTrackerRef])
    }

    @Test fun aPlannedDayAFreeDayCoversIsOfferedAndADayWithNoneLeftIsOpen() {
        // Wednesday is planned, so the free day that covers it does not make it a rest day.
        assertEquals(TrackerHomeStatus.Open(0, 1, optional = true), statuses(state(), LocalDate.parse("2026-11-11")).second[ActivityTrackerRef])
        // Both free days went on Wednesday and Thursday: Friday and Saturday are owed.
        assertEquals(TrackerHomeStatus.Open(0, 1), statuses(state(), LocalDate.parse("2026-11-14")).second[ActivityTrackerRef])
    }

    @Test fun aTokenSetAsideCoversTheRowAndATokenThatCannotCoverLeavesItOpen() {
        // Supplements earned a token in nine kept days; Care, with two times open, needs two and holds one at most.
        val (mood, statuses) = statuses(state(), thursday, setAside = setOf(supplements, care))
        assertEquals(TrackerHomeStatus.Covered, statuses[supplements])
        assertEquals(TrackerHomeStatus.Open(0, 2), statuses[care])
        assertEquals(HomeRowGroup.Kept, homeRowRank(TrackerHomeStatus.Covered, mood.trackerDueMinutes(supplements, reminders), 600).first)
    }

    @Test fun aWeeklyTimerIsOpenAtItsTimeUntilTheWeeksGoalIsReachedThenKept() {
        val below = order(state("timeLogs" to """{"time":"reading","dateKey":"2026-11-11","timestampMillis":1,"minutes":20}"""), LocalTime.of(17, 30))
        // Due within the hour, so it stands with what is past due, after what has waited since the
        // morning, and below Thursday's workout, due at the same 18:00 and first in Home's own order.
        assertEquals(listOf(care, weight, eating, supplements, ActivityTrackerRef, reading), below.take(6))
        val reached = state("timeLogs" to """{"time":"reading","dateKey":"2026-11-11","timestampMillis":1,"minutes":60}""")
        assertEquals(reading, order(reached, LocalTime.of(17, 30)).last())
    }

    // 2026-11-22: on a Sunday, minutes that left the week short wore the check, though the close
    // still took a token for the week.
    @Test fun onItsSundayAWeeklyTimerIsDoneOnlyOnceTheWeeksGoalIsReached() {
        val sunday = LocalDate.parse("2026-11-22")
        fun readingOn(vararg minutes: Pair<String, Int>, edits: List<Pair<String, TimeEdit>> = emptyList()) = statuses(
            EverydayViews.state(*minutes.map { (day, logged) -> "timeLogs" to """{"time":"reading","dateKey":"$day","timestampMillis":2,"minutes":$logged}""" }.toTypedArray(), today = sunday)
                .let { state -> edits.fold(state) { edited, (day, edit) -> edited.applyingTimeEdits("reading", listOf(edit), LocalDate.parse(day)) } },
            sunday,
        ).second[reading]
        // The kept history gave the week 5 of its 60 minutes before today.
        assertEquals(open, readingOn("2026-11-22" to 15))
        assertEquals(TrackerHomeStatus.Done, readingOn("2026-11-22" to 55))
        // Out of the streak, or back in it since Friday, the week is not judged, so today's minutes settle the day.
        assertEquals(TrackerHomeStatus.Done, readingOn("2026-11-22" to 15, edits = listOf("2026-11-18" to TimeEdit.LeaveRewards)))
        val rejoined = listOf("2026-11-12" to TimeEdit.LeaveRewards, "2026-11-20" to TimeEdit.JoinRewards)
        assertEquals(TrackerHomeStatus.Done, readingOn("2026-11-22" to 15, edits = rejoined))
        // A weekday's minutes still settle that day: the week is not judged on it.
        val thursdayMinutes = statuses(state("timeLogs" to """{"time":"reading","dateKey":"$thursday","timestampMillis":2,"minutes":5}"""), thursday).second[reading]
        assertEquals(TrackerHomeStatus.Done, thursdayMinutes)
    }

    /** Substances counting toward the streak from Monday, with its check-in reminding at 18:00. */
    private fun substancesInTheStreak(state: AppState) = state.copy(
        substances = state.substances
            .applyEdit(SubstanceEdit.Remind(true), thursday)
            .applyEdit(SubstanceEdit.SetGoal(SubstanceGoal.Logging), thursday.minusDays(3)),
    )

    // 2026-11-14: Substances reminded at its check-in, yet its open row stood with the rows that have no time.
    @Test fun substancesInTheStreakIsDueAtItsCheckIn() {
        val joined = substancesInTheStreak(state())
        val (mood, statuses) = statuses(joined, thursday)

        assertEquals(TrackerHomeStatus.Open(0, 1), statuses[SubstancesTrackerRef])
        assertEquals(listOf(habitDayMinuteOf(LocalTime.of(18, 0))), mood.trackerDueMinutes(SubstancesTrackerRef, reminders))
        // In the morning it is due later, below what waits now; within the hour of its check-in it joins them.
        assertEquals(HomeRowGroup.Later, homeRowRank(open, mood.trackerDueMinutes(SubstancesTrackerRef, reminders), habitDayMinuteOf(LocalTime.of(9, 0))).first)
        assertEquals(HomeRowGroup.Due, homeRowRank(open, mood.trackerDueMinutes(SubstancesTrackerRef, reminders), habitDayMinuteOf(LocalTime.of(17, 30))).first)
        assertTrue(order(joined, LocalTime.of(9, 0)).let { it.indexOf(SubstancesTrackerRef) > it.indexOf(care) })
        // Outside the streak it asks nothing, and has no time.
        assertEquals(emptyList<Int>(), statuses(state(), thursday).first.trackerDueMinutes(SubstancesTrackerRef, reminders))
        assertEquals(null, statuses(state(), thursday).second[SubstancesTrackerRef])
    }

    @Test fun aCheckpointDoneNoLongerHoldsItsRowUp() {
        val morningDone = state("checklistDays" to """{"checklistId":"care","dateKey":"2026-11-12","timestampMillis":1,"ticks":["morning/toothbrushing"]}""")
        val rows = order(morningDone, LocalTime.of(9, 0))
        assertTrue(rows.indexOf(care) > rows.indexOf(supplements))
    }
}

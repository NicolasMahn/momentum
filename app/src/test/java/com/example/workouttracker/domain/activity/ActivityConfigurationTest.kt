package com.example.workouttracker.domain.activity

import com.example.workouttracker.domain.activity.routine.ExerciseLog
import com.example.workouttracker.domain.activity.routine.ExerciseWeight
import com.example.workouttracker.domain.activity.routine.LoggedSet
import com.example.workouttracker.domain.activity.routine.Routine
import com.example.workouttracker.domain.activity.routine.RoutineDay
import com.example.workouttracker.domain.activity.routine.RoutineExercise
import com.example.workouttracker.domain.activity.routine.SeededGymDayIds
import com.example.workouttracker.domain.activity.routine.WorkoutSession
import com.example.workouttracker.domain.checklist.Dated
import com.example.workouttracker.domain.checklist.ViewIcon
import com.example.workouttracker.domain.checklist.WhenAway
import com.example.workouttracker.domain.holiday.DayRange
import com.example.workouttracker.domain.holiday.HolidaySchedule
import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Activity's configuration holds what the code held: five days a week, two of them on a week
 * away, which is the holiday table, three tokens, the gym with its routine and the three
 * activities beside it. The rules that judge weeks are dated, and a week takes a change as a
 * checklist's week item does. Days are counted from a Monday.
 */
class ActivityConfigurationTest {
    private val monday = LocalDate.parse("2026-10-05")
    private fun on(day: Int): LocalDate = monday.plusDays(day.toLong())
    private val seeded = testActivityConfiguration()
    private fun away(vararg days: Int) = HolidaySchedule(days.map { DayRange(on(it), on(it)) })
    private fun HolidaySchedule.sick(vararg days: Int) = days.fold(this) { schedule, day -> schedule.withSickDays(DayRange(on(day), on(day))) }
    private fun ActivityConfiguration.saved(day: Int, vararg edits: ActivityEdit) = edits.fold(this) { draft, edit -> draft.applyEdit(edit, on(day)) }

    /** The seeded week with its first [days] weekdays, what is planned on them kept. */
    private fun week(days: Int) = DefaultActivityWeek.withDays(DayOfWeek.entries.take(days).toSet())
    private fun steps(vararg tokens: Int) = ActivityTokenSteps(tokens.mapIndexed { index, each -> ActivityTokenStep(index, each) })

    @Test fun theSeededRulesAreFiveDaysAWeekTwoOnAWeekAwayAndThreeTokens() {
        assertEquals(Dated.always(DefaultActivityWeek), seeded.week)
        assertEquals(5, seeded.daysAWeekOn(monday))
        assertEquals(setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY), DefaultActivityWeek.chosen)
        assertEquals(listOf("gym", "bouldering", "gym", null, "gym"), DefaultActivityWeek.days.map { it.activityId })
        assertNull(DefaultActivityWeek.plannedOn(DayOfWeek.SATURDAY))
        assertEquals(Dated.always(DefaultActivityTokenSteps), seeded.rewards.tokenSteps)
        assertEquals(listOf(ActivityTokenStep(0, 1)), DefaultActivityTokenSteps.steps)
        assertEquals(Dated.always(WhenAway.LowerTarget), seeded.whenAway)
        assertEquals(Dated.always(2), seeded.awayDays)
        assertEquals(Dated.always(3), seeded.rewards.maxPasses)
        assertEquals(listOf("gym", "bouldering", "cycling", "jogging"), seeded.activities.map { it.id })
        assertEquals(listOf("Gym", "Bouldering", "Cycling", "Jogging"), seeded.activities.map { it.label })
        assertEquals(listOf(ViewIcon.Dumbbell, ViewIcon.Bouldering, ViewIcon.Bicycle, ViewIcon.Running), seeded.activities.map { it.icon })
        assertEquals(listOf(true, false, false, false), seeded.activities.map { it.hasRoutine })
        assertEquals("Activity" to ViewIcon.Hiking, seeded.label to seeded.icon)
    }

    /** The week asks the table Activity always had, and a day less for each sick day; the free days are the rest. */
    @Test fun theSeededWeekAsksTheHolidayTableLessADayPerSickDay() {
        val table = listOf(5, 5, 5, 4, 4, 3, 3, 2)
        table.forEachIndexed { holidayDays, asked ->
            val holidays = HolidaySchedule(if (holidayDays == 0) emptyList() else listOf(DayRange(monday, on(holidayDays - 1))))
            assertEquals("$holidayDays away", asked, seeded.weekAsks(monday, holidays))
            assertEquals("$holidayDays away", 7 - asked, seeded.freeDaysOn(on(3), holidays))
        }
        assertEquals(4, seeded.weekAsks(monday, HolidaySchedule().sick(2)))
        assertEquals(3, seeded.weekAsks(monday, away(0, 1, 2).sick(5)))
        // A holiday day that is also sick counts among the holiday days, as it always did.
        assertEquals(3, seeded.weekAsks(monday, away(0, 1, 2).sick(2)))
        assertEquals(0, seeded.weekAsks(monday, HolidaySchedule().sick(0, 1, 2, 3, 4, 5, 6)))
        // Sick days lower nothing here: the walk and the ledger skip them themselves.
        assertEquals(2, seeded.freeDaysOn(on(3), HolidaySchedule().sick(2)))
        assertEquals(5, seeded.daysAWeekOf(monday))
    }

    @Test fun theSeededRoutineIsThePlanTheAppAlwaysHad() {
        fun exercise(id: String, label: String, sets: Int, reps: IntRange, startKg: Double, startReps: Int, stepKg: Double) =
            RoutineExercise(id, label, sets, reps, ExerciseWeight(startKg, stepKg), startReps)
        val gym = seeded.activity("gym")!!
        val plan = gym.routine!!
        assertEquals("Gym" to ViewIcon.Dumbbell, gym.label to gym.icon)
        assertTrue(plan.isOffered)
        assertEquals(SeededGymDayIds, plan.days.map { it.id })
        assertEquals(listOf("Day A", "Day B", "Day C"), plan.days.map { it.label })
        assertEquals(
            listOf(
                exercise("bench_press", "Bench Press", 3, 8..10, 22.5, 10, 2.5),
                exercise("back_squat", "Back Squat", 3, 8..10, 60.0, 9, 1.0),
                exercise("row", "Row", 3, 8..12, 40.0, 8, 1.0),
                exercise("lateral_raises", "Lateral Raises", 3, 12..15, 4.0, 12, 1.0),
                exercise("triceps_pushdowns", "Triceps Pushdowns", 2, 12..15, 12.5, 15, 2.5),
            ),
            plan.days[0].exercises,
        )
        assertEquals(
            listOf(
                exercise("pull_ups", "Pull Ups", 3, 6..10, -19.0, 8, 4.0),
                exercise("dips", "Dips", 3, 8..10, 0.0, 8, 1.0),
                exercise("bulgarian_split_squat", "Bulgarian Split Squat", 3, 8..10, 15.0, 10, 2.5),
                exercise("preacher_hammer_curls", "Preacher Hammer Curls", 3, 10..12, 10.0, 10, 2.5),
            ),
            plan.days[1].exercises,
        )
        assertEquals(
            listOf(
                exercise("barbell_deadlift", "Barbell Deadlift", 3, 6..8, 70.0, 8, 1.0),
                exercise("overhead_press", "Overhead Press", 3, 6..10, 10.0, 8, 1.0),
                exercise("face_pulls", "Face Pulls", 3, 15..20, 17.5, 15, 2.5),
                exercise("hamstrings", "Hamstrings", 3, 10..12, 50.0, 10, 2.5),
            ),
            plan.days[2].exercises,
        )
        assertEquals(listOf(14, 12, 12), plan.days.map { it.totalSets })
        assertEquals(LoggedSet(22.5, 10.0), plan.days[0].exercises[0].start)
        assertEquals(LoggedSet(null, 5.0), RoutineExercise("ex", "Plank", 3, 1..1, null, 5).start)
        assertEquals(SeededGym, gym)
    }

    /** Still due asks the days a week less sick days; not due takes away days out of the week as well, each once, never below zero. */
    @Test fun stillDueAndNotDueAskTheirOwnWeeks() {
        val due = seeded.saved(0, ActivityEdit.SetWhenAway(WhenAway.Due))
        assertEquals(5, due.weekAsks(monday, away(0, 1, 2, 3)))
        assertEquals(4, due.weekAsks(monday, away(0, 1, 2, 3).sick(1)))
        assertEquals(2, due.freeDaysOn(on(2), away(0, 1, 2, 3)))

        val excused = seeded.saved(0, ActivityEdit.SetWhenAway(WhenAway.Excused))
        assertEquals(2, excused.weekAsks(monday, away(0, 1, 2)))
        assertEquals(2, excused.weekAsks(monday, away(0, 1, 2).sick(2)))
        assertEquals(1, excused.weekAsks(monday, away(0, 1, 2).sick(3)))
        assertEquals(0, excused.weekAsks(monday, away(0, 1, 2, 3, 4, 5)))
        assertEquals(2, excused.freeDaysOn(on(2), away(0, 1, 2)))
        assertTrue(excused.isNotDueAwayOn(on(1), away(1)))
        assertTrue(!seeded.isNotDueAwayOn(on(1), away(1)))
    }

    /** Each day's free days follow the rule of that day; a changed count or number of days starts a new span, a changed answer keeps it. */
    @Test fun aChangeOfTheDaysOrTheCountStartsASpanAndAChangeOfTheAnswerKeepsIt() {
        val fewer = seeded.saved(9, ActivityEdit.SetWeek(week(3)))
        assertEquals(2, fewer.freeDaysOn(on(8), HolidaySchedule()))
        assertEquals(4, fewer.freeDaysOn(on(9), HolidaySchedule()))
        assertEquals(on(7), fewer.spanStartOn(on(8)))
        assertEquals(on(9), fewer.spanStartOn(on(9)))
        assertEquals(on(9), fewer.spanStartOn(on(13)))
        assertEquals(on(14), fewer.spanStartOn(on(14)))
        assertEquals(3, fewer.weekAsks(on(7), HolidaySchedule()))
        assertEquals(5, fewer.weekAsks(monday, HolidaySchedule()))
        assertEquals(3, fewer.daysAWeekOf(on(7)))

        // Five days away ask three with the seeded count and leave four free; a count of four asks all five.
        val count = seeded.saved(10, ActivityEdit.SetAwayDays(4))
        assertEquals(on(10), count.spanStartOn(on(12)))
        assertEquals(4, count.freeDaysOn(on(9), away(7, 8, 9, 10, 11)))
        assertEquals(2, count.freeDaysOn(on(10), away(7, 8, 9, 10, 11)))

        val answer = seeded.saved(10, ActivityEdit.SetWhenAway(WhenAway.Due))
        assertEquals(on(7), answer.spanStartOn(on(12)))
        assertEquals(4, answer.freeDaysOn(on(9), away(7, 8, 9, 10, 11)))
        assertEquals(2, answer.freeDaysOn(on(10), away(7, 8, 9, 10, 11)))

        val bank = seeded.saved(10, ActivityEdit.SetBankSize(5))
        assertEquals(on(7), bank.spanStartOn(on(12)))
        assertEquals(Dated(listOf(Dated.Revision(3), Dated.Revision(5, on(10)))), bank.rewards.maxPasses)

        // Which days are chosen, or what is planned on one, keeps the span: the free days dealt are by number.
        val otherDays = seeded.saved(9, ActivityEdit.SetWeek(DefaultActivityWeek.withDays(setOf(DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY))))
        assertEquals(on(7), otherDays.spanStartOn(on(12)))
        assertEquals(5, otherDays.daysAWeekOn(on(12)))
        assertEquals(Dated.always(5), otherDays.week.map { it.size })
        // A day that stays keeps what is planned on it; Saturday, new to the week, plans any activity.
        assertEquals(listOf("bouldering", "gym", null, "gym", null), otherDays.week.on(on(9)).days.map { it.activityId })
        val planned = seeded.saved(9, ActivityEdit.SetWeek(DefaultActivityWeek.planning(DayOfWeek.THURSDAY, "cycling")))
        assertEquals(on(7), planned.spanStartOn(on(12)))
        assertEquals("cycling", planned.week.on(on(9)).plannedOn(DayOfWeek.THURSDAY))
        assertNull(planned.week.on(on(8)).plannedOn(DayOfWeek.THURSDAY))
        val steps = seeded.saved(10, ActivityEdit.SetTokenSteps(steps(1, 2)))
        assertEquals(on(7), steps.spanStartOn(on(12)))
    }

    /**
     * More days leave less room for steps, and raising the days drops the steps beyond it from
     * today in the same edit: five days with steps at the days, one more and two more, then a
     * sixth day chosen keep the first two, then a seventh the first alone, with the earlier
     * revisions as they were. A week earns by the steps its Sunday has.
     */
    @Test fun raisingTheDaysDropsTheStepsBeyondTheRoomAndKeepsTheEarlierRevisions() {
        val three = seeded.saved(1, ActivityEdit.SetTokenSteps(steps(1, 2, 3)))
        assertEquals(steps(1, 2, 3), three.rewards.tokenSteps.on(on(1)))
        assertThrows(IllegalArgumentException::class.java) { three.applyEdit(ActivityEdit.SetTokenSteps(steps(1, 1, 1, 1)), on(1)) }

        val six = three.saved(2, ActivityEdit.SetWeek(week(6)))
        assertEquals(steps(1, 2), six.rewards.tokenSteps.on(on(2)))
        assertEquals(steps(1, 2, 3), six.rewards.tokenSteps.on(on(1)))
        val seven = six.saved(3, ActivityEdit.SetWeek(week(7)))
        assertEquals(steps(1), seven.rewards.tokenSteps.on(on(3)))
        assertEquals(
            listOf(DefaultActivityTokenSteps to null, steps(1, 2, 3) to on(1), steps(1, 2) to on(2), steps(1) to on(3)),
            seven.rewards.tokenSteps.revisions.map { it.value to it.from },
        )
        assertEquals(listOf(5, 6, 7), seven.week.revisions.map { it.value.size })
        // Fewer days leave more room, and take nothing away.
        val back = seven.saved(4, ActivityEdit.SetWeek(week(5)))
        assertEquals(steps(1), back.rewards.tokenSteps.on(on(4)))
        assertEquals(3, ActivityTokenSteps.roomFor(5))
        assertEquals(7, ActivityTokenSteps.roomFor(1))
        assertEquals(1, ActivityTokenSteps.roomFor(7))
        assertEquals(steps(1, 3), steps(1, 2, 3).without(1))
        assertEquals(listOf(1, 3, 6), steps(1, 2, 3).runningTotals)
        assertEquals(6, steps(1, 2, 3).total)
        assertEquals(steps(1, 2, 1), steps(1, 2).grown())
        assertTrue(steps(1, 2).canGrow(5) && !steps(1, 2, 3).canGrow(5))
    }

    /** A week has one to seven days, each once, in the week's order, planning only what Activity lists; the steps start at the days, go up a day each and stay within the room. */
    @Test fun theWeekAndTheStepsRefuseWhatTheyCannotBe() {
        assertThrows(IllegalArgumentException::class.java) { WeekPlan(emptyList()) }
        assertThrows(IllegalArgumentException::class.java) { WeekPlan(listOf(PlannedDay(DayOfWeek.MONDAY), PlannedDay(DayOfWeek.MONDAY))) }
        assertThrows(IllegalArgumentException::class.java) { WeekPlan(listOf(PlannedDay(DayOfWeek.TUESDAY), PlannedDay(DayOfWeek.MONDAY))) }
        assertEquals(listOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY), WeekPlan.of(DayOfWeek.TUESDAY, DayOfWeek.MONDAY).days.map { it.day })
        assertThrows(IllegalArgumentException::class.java) { seeded.copy(week = Dated.always(DefaultActivityWeek.planning(DayOfWeek.THURSDAY, "act_none"))) }
        assertThrows(IllegalArgumentException::class.java) { seeded.applyEdit(ActivityEdit.SetWeek(DefaultActivityWeek.planning(DayOfWeek.THURSDAY, "act_none")), monday) }
        assertThrows(IllegalArgumentException::class.java) { ActivityTokenSteps(emptyList()) }
        assertThrows(IllegalArgumentException::class.java) { ActivityTokenSteps(listOf(ActivityTokenStep(1, 1))) }
        assertThrows(IllegalArgumentException::class.java) { ActivityTokenSteps(listOf(ActivityTokenStep(0, 1), ActivityTokenStep(2, 1))) }
        assertThrows(IllegalArgumentException::class.java) { steps(0) }
        assertThrows(IllegalArgumentException::class.java) { steps(6) }
        assertThrows(IllegalArgumentException::class.java) { steps(1, 1, 1, 1, 1, 1, 1, 1) }
        assertThrows(IllegalArgumentException::class.java) { seeded.copy(rewards = ActivityRewards(3, steps(1, 1, 1, 1))) }
        assertThrows(IllegalArgumentException::class.java) { steps(1, 2).without(0) }
        assertThrows(IllegalArgumentException::class.java) { steps(1, 2).without(2) }
        assertEquals(steps(2, 2), steps(1, 2).withTokens(0, 2))
        assertThrows(IllegalArgumentException::class.java) { steps(1).grown().grown().grown().grown().grown().grown().grown() }
    }

    /** A removed activity's planned days plan any activity from today; the earlier revision, history, still names it. */
    @Test fun removingAnActivityPlansAnyActivityWhereItWas() {
        val removed = seeded.saved(3, ActivityEdit.RemoveActivity("bouldering"))
        assertNull(removed.week.on(on(3)).plannedOn(DayOfWeek.TUESDAY))
        assertEquals("bouldering", removed.week.on(on(2)).plannedOn(DayOfWeek.TUESDAY))
        assertEquals("gym", removed.week.on(on(3)).plannedOn(DayOfWeek.MONDAY))
        assertEquals(listOf(null, on(3)), removed.week.revisions.map { it.from })
        // A planned activity that was never Tuesday's leaves the week as it was.
        assertEquals(Dated.always(DefaultActivityWeek), seeded.saved(3, ActivityEdit.RemoveActivity("jogging")).week)
    }

    /** Fewer days is for more than one day a week, and its count stays below the days a week. */
    @Test fun oneDayAWeekTurnsFewerDaysIntoStillDueAndACountTooHighIsClamped() {
        val one = seeded.saved(3, ActivityEdit.SetWeek(week(1)))
        assertEquals(WhenAway.Due, one.whenAway.on(on(3)))
        assertEquals(WhenAway.LowerTarget, one.whenAway.on(on(2)))
        assertEquals(2, one.awayDays.current)
        assertThrows(IllegalArgumentException::class.java) { one.applyEdit(ActivityEdit.SetWhenAway(WhenAway.LowerTarget), on(3)) }

        val two = seeded.saved(3, ActivityEdit.SetWeek(week(2)))
        assertEquals(WhenAway.LowerTarget, two.whenAway.current)
        assertEquals(1, two.awayDays.on(on(3)))
        assertEquals(2, two.awayDays.on(on(2)))
        assertThrows(IllegalArgumentException::class.java) { two.applyEdit(ActivityEdit.SetAwayDays(2), on(3)) }
        assertEquals(1, two.saved(3, ActivityEdit.SetAwayDays(1)).awayDays.current)

        // Not due keeps its count, which lowering finds again once the days allow it.
        val excused = seeded.saved(3, ActivityEdit.SetWhenAway(WhenAway.Excused), ActivityEdit.SetWeek(week(1)))
        assertEquals(WhenAway.Excused, excused.whenAway.current)
        assertEquals(2, excused.saved(4, ActivityEdit.SetWeek(week(5)), ActivityEdit.SetWhenAway(WhenAway.LowerTarget)).awayDays.current)

        assertThrows(IllegalArgumentException::class.java) { seeded.applyEdit(ActivityEdit.SetBankSize(10), monday) }
        assertThrows(IllegalArgumentException::class.java) { seeded.copy(whenAway = Dated.always(WhenAway.LowerTarget), awayDays = Dated.always(5)) }
    }

    /** A name is one, trimmed and ignoring case, among all activities, removed ones included. */
    @Test fun activityNamesAreUniqueTrimmedAndIgnoringCaseRemovedOnesIncluded() {
        val yoga = ListedActivity("act_yoga", "Yoga", ViewIcon.Bolt)
        val added = seeded.saved(0, ActivityEdit.AddActivity(yoga))
        assertEquals(yoga, added.activities.last())
        assertEquals(yoga, added.activityNamed(" yOGA "))
        assertEquals(seeded.activity("cycling"), added.activityNamed("cycling"))
        assertNull(added.activityNamed("Running"))
        assertThrows(IllegalArgumentException::class.java) { added.applyEdit(ActivityEdit.RenameActivity("act_yoga", "Cycling"), monday) }
        assertThrows(IllegalArgumentException::class.java) { added.applyEdit(ActivityEdit.RenameActivity("act_yoga", " cycling"), monday) }
        assertThrows(IllegalArgumentException::class.java) { added.applyEdit(ActivityEdit.AddActivity(ListedActivity("act_more", "yoga", ViewIcon.Bolt)), monday) }
        assertThrows(IllegalArgumentException::class.java) { added.applyEdit(ActivityEdit.AddActivity(ListedActivity("act_yoga", "Pilates", ViewIcon.Bolt)), monday) }

        val removed = added.saved(0, ActivityEdit.RemoveActivity("act_yoga"))
        assertTrue(removed.activity("act_yoga")!!.removed)
        assertEquals(seeded.activities, removed.retainedActivities)
        assertEquals("Yoga", removed.activityLabel("act_yoga", "whatever"))
        assertEquals("Bowling", removed.activityLabel("act_gone", "Bowling"))
        assertThrows(IllegalArgumentException::class.java) { removed.applyEdit(ActivityEdit.AddActivity(ListedActivity("act_more", "yoga", ViewIcon.Bolt)), monday) }
        assertThrows(IllegalArgumentException::class.java) { removed.applyEdit(ActivityEdit.RemoveActivity("act_yoga"), monday) }
        // The removed one keeps its place; moving up walks past it.
        val moved = removed.saved(0, ActivityEdit.SetActivityIcon("jogging", ViewIcon.Swimming), ActivityEdit.MoveActivityUp("jogging"))
        assertEquals(listOf("gym", "bouldering", "jogging", "cycling", "act_yoga"), moved.activities.map { it.id })
        assertEquals(ViewIcon.Swimming, moved.activity("jogging")!!.icon)
        assertThrows(IllegalArgumentException::class.java) { moved.applyEdit(ActivityEdit.MoveActivityUp("gym"), monday) }
        val renamed = moved.saved(0, ActivityEdit.RenameActivity("act_yoga", "Hot yoga"))
        assertEquals("Hot yoga", renamed.activity("act_yoga")!!.label)
        assertTrue(renamed.activity("act_yoga")!!.removed)
    }

    @Test fun aRoutineKeepsItsLastDayAndADaysLastExerciseAndEveryIdOnce() {
        val ex = { id: String -> RoutineExercise(id, id, 3, 8..10, ExerciseWeight(20.0, 2.5), 8) }
        val day = RoutineDay("day_d", "Day D", listOf(ex("ex_1")))
        val grown = seeded.saved(0, ActivityEdit.AddRoutineDay("gym", day), ActivityEdit.AddExercise("day_d", ex("ex_2")), ActivityEdit.RenameRoutineDay("day_d", "Legs"))
        val gym = { configuration: ActivityConfiguration -> configuration.activity("gym")!!.routine!! }
        assertEquals(listOf("A", "B", "C", "day_d"), gym(grown).days.map { it.id })
        assertEquals("Legs", gym(grown).dayLabel("day_d"))
        assertEquals("day_x", gym(grown).dayLabel("day_x"))
        assertEquals(listOf("ex_1", "ex_2"), gym(grown).day("day_d")!!.exercises.map { it.id })
        assertEquals("ex_2", gym(grown).exerciseLabel("ex_2", "Old name"))
        assertEquals("Old name", gym(grown).exerciseLabel("ex_gone", "Old name"))
        assertEquals("gym", grown.activityOfDay("day_d")!!.id)
        assertEquals("gym", grown.activityOfExercise("ex_2")!!.id)
        assertThrows(IllegalArgumentException::class.java) { grown.applyEdit(ActivityEdit.AddExercise("A", ex("ex_1")), monday) }
        assertThrows(IllegalArgumentException::class.java) { grown.applyEdit(ActivityEdit.AddRoutineDay("gym", RoutineDay("A", "Again", listOf(ex("ex_9")))), monday) }
        // Day and exercise IDs are unique across the whole configuration, so another activity's routine cannot take them either.
        assertThrows(IllegalArgumentException::class.java) { grown.applyEdit(ActivityEdit.AddRoutineDay("bouldering", RoutineDay("A", "Wall", listOf(ex("ex_9")))), monday) }
        assertThrows(IllegalArgumentException::class.java) { grown.applyEdit(ActivityEdit.AddRoutineDay("bouldering", RoutineDay("day_w", "Wall", listOf(ex("ex_1")))), monday) }

        val removed = grown.saved(0, ActivityEdit.RemoveExercise("ex_1"), ActivityEdit.RemoveRoutineDay("day_d"))
        assertTrue(gym(removed).day("day_d")!!.removed)
        assertEquals(listOf("A", "B", "C"), gym(removed).retainedDays.map { it.id })
        assertEquals(listOf("ex_2"), gym(removed).day("day_d")!!.retainedExercises.map { it.id })
        assertThrows(IllegalArgumentException::class.java) { removed.applyEdit(ActivityEdit.RemoveExercise("ex_2"), monday) }
        assertThrows(IllegalArgumentException::class.java) { removed.applyEdit(ActivityEdit.RenameExercise("ex_1", "Gone"), monday) }
        val lastDay = removed.saved(0, ActivityEdit.RemoveRoutineDay("A"), ActivityEdit.RemoveRoutineDay("B"))
        assertThrows(IllegalArgumentException::class.java) { lastDay.applyEdit(ActivityEdit.RemoveRoutineDay("C"), monday) }

        val numbers = seeded.saved(0, ActivityEdit.SetExerciseNumbers("row", 4, 10..12, ExerciseWeight(42.5, 0.25), 9))
        assertEquals(RoutineExercise("row", "Row", 4, 10..12, ExerciseWeight(42.5, 0.25), 9), numbers.routineExercise("row"))
        val weightless = seeded.saved(0, ActivityEdit.SetExerciseNumbers("row", 4, 10..12, null, 9))
        assertEquals(RoutineExercise("row", "Row", 4, 10..12, null, 9), weightless.routineExercise("row"))
        listOf<() -> Unit>(
            { seeded.applyEdit(ActivityEdit.SetExerciseNumbers("row", 0, 8..10, ExerciseWeight(40.0, 1.0), 8), monday) },
            { seeded.applyEdit(ActivityEdit.SetExerciseNumbers("row", 11, 8..10, ExerciseWeight(40.0, 1.0), 8), monday) },
            { seeded.applyEdit(ActivityEdit.SetExerciseNumbers("row", 3, 10..8, ExerciseWeight(40.0, 1.0), 8), monday) },
            { seeded.applyEdit(ActivityEdit.SetExerciseNumbers("row", 3, 0..8, ExerciseWeight(40.0, 1.0), 8), monday) },
            { seeded.applyEdit(ActivityEdit.SetExerciseNumbers("row", 3, 8..101, ExerciseWeight(40.0, 1.0), 8), monday) },
            { seeded.applyEdit(ActivityEdit.SetExerciseNumbers("row", 3, 8..10, ExerciseWeight(40.0, 1.0), 0), monday) },
            // A weight that no exercise may have is refused before it can be an edit at all.
            { ExerciseWeight(-200.5, 1.0) },
            { ExerciseWeight(500.5, 1.0) },
            { ExerciseWeight(40.0, 0.2) },
            { ExerciseWeight(40.0, 20.5) },
        ).forEachIndexed { index, refused -> assertThrows("refusal $index", IllegalArgumentException::class.java) { refused() } }
        assertThrows(IllegalArgumentException::class.java) { Routine(emptyList()) }
        assertThrows(IllegalArgumentException::class.java) { Routine(List(8) { RoutineDay("day_$it", "Day", listOf(ex("ex_$it"))) }) }
        assertThrows(IllegalArgumentException::class.java) { RoutineDay("day_x", "Day", List(13) { ex("ex_$it") }) }

        // The order among the days and the exercises still offered is the person's, walking past removed ones.
        val reordered = removed.saved(0, ActivityEdit.MoveRoutineDayUp("C"), ActivityEdit.MoveExerciseUp("back_squat"))
        assertEquals(listOf("A", "C", "B", "day_d"), gym(reordered).days.map { it.id })
        assertEquals(listOf("back_squat", "bench_press", "row", "lateral_raises", "triceps_pushdowns"), gym(reordered).day("A")!!.exercises.map { it.id })
        assertThrows(IllegalArgumentException::class.java) { reordered.applyEdit(ActivityEdit.MoveRoutineDayUp("A"), monday) }
    }

    /**
     * Any activity can get a routine and lose it: its first day makes one, removing the routine
     * marks every day removed and keeps them, so an old session still finds its day's name, and
     * a day added after that is a routine of one day again.
     */
    @Test fun anyActivityCanGetARoutineAndLoseIt() {
        val ex = { id: String -> RoutineExercise(id, id, 3, 8..10, null, 8) }
        assertNull(seeded.activity("bouldering")!!.routine)
        assertThrows(IllegalArgumentException::class.java) { seeded.applyEdit(ActivityEdit.RemoveRoutine("bouldering"), monday) }
        val withWall = seeded.saved(0, ActivityEdit.AddRoutineDay("bouldering", RoutineDay("day_w", "Wall", listOf(ex("ex_w")))))
        assertTrue(withWall.activity("bouldering")!!.hasRoutine)
        assertEquals("Wall", withWall.routineDay("day_w")!!.label)
        assertEquals("bouldering", withWall.activityOfDay("day_w")!!.id)

        val lost = withWall.saved(0, ActivityEdit.RemoveRoutine("bouldering"))
        val bouldering = lost.activity("bouldering")!!
        assertFalse(bouldering.hasRoutine)
        assertEquals(listOf("day_w"), bouldering.routine!!.days.filter { it.removed }.map { it.id })
        assertEquals("Wall", bouldering.routine!!.dayLabel("day_w"))
        assertThrows(IllegalArgumentException::class.java) { lost.applyEdit(ActivityEdit.RemoveRoutine("bouldering"), monday) }
        assertThrows(IllegalArgumentException::class.java) { lost.applyEdit(ActivityEdit.AddRoutineDay("bouldering", RoutineDay("day_w", "Wall", listOf(ex("ex_x")))), monday) }

        val again = lost.saved(0, ActivityEdit.AddRoutineDay("bouldering", RoutineDay("day_v", "Volume", listOf(ex("ex_v")))))
        assertEquals(listOf("day_w" to true, "day_v" to false), again.activity("bouldering")!!.routine!!.days.map { it.id to it.removed })
        assertTrue(again.activity("bouldering")!!.hasRoutine)
        // The gym's routine can go the same way; the Gym activity stays listed.
        val gymless = seeded.saved(0, ActivityEdit.RemoveRoutine("gym"))
        assertFalse(gymless.activity("gym")!!.hasRoutine)
        assertEquals(listOf("A", "B", "C"), gymless.activity("gym")!!.routine!!.days.map { it.id })
    }

    /** The next day follows the newest of the activity's own sessions in the routine's order, walking past removed days, and is the first without one. */
    @Test fun theNextDayFollowsTheNewestSessionInTheRoutinesOrder() {
        fun session(activity: String, day: String, at: Long) = WorkoutSession("s$at", activity, day, "2026-10-05", at, listOf(ExerciseLog("bench_press", "Bench Press", listOf(LoggedSet(20.0, 8.0)))))
        val gym = seeded.activity("gym")!!
        val plan = gym.routine!!
        assertEquals("A", plan.nextDay(emptyList())!!.id)
        assertEquals("B", plan.nextDay(listOf(session("gym", "A", 1)))!!.id)
        assertEquals("A", plan.nextDay(listOf(session("gym", "C", 1)))!!.id)
        assertEquals("C", plan.nextDay(listOf(session("gym", "A", 1), session("gym", "B", 2), session("gym", "C", 0)))!!.id)
        // Another activity's newer session says nothing about the gym's next day.
        assertEquals("B", gym.nextRoutineDay(listOf(session("gym", "A", 1), session("act_yoga", "day_flow", 2)))!!.id)
        val withoutB = seeded.saved(0, ActivityEdit.RemoveRoutineDay("B")).activity("gym")!!.routine!!
        assertEquals("C", withoutB.nextDay(listOf(session("gym", "A", 1)))!!.id)
        assertEquals("C", withoutB.nextDay(listOf(session("gym", "B", 1)))!!.id)
        assertEquals(listOf(0, 1), listOf("A", "C").map { withoutB.placeOf(it) })
        assertEquals(1, withoutB.placeOf("B"))
        assertNull(withoutB.placeOf("day_x"))
        assertNull(seeded.saved(0, ActivityEdit.RemoveRoutine("gym")).activity("gym")!!.nextRoutineDay(emptyList()))
        assertNull(seeded.activity("bouldering")!!.nextRoutineDay(emptyList()))
    }

    /**
     * The newest session is the one of the latest habit date, not the one saved last: a session's
     * timestamp moves with every edit. Of two sessions on one date, the one saved last is newest.
     */
    @Test fun theNextDayFollowsTheLatestDateNotTheLatestSave() {
        fun mobility(removed: String? = null) = ListedActivity("act_mobility", "Mobility", ViewIcon.Bolt, routine = Routine(('A'..'G').map(Char::toString).map {
            RoutineDay("day_$it", it, listOf(RoutineExercise("ex_$it", "Stretch $it", 2, 5..10, null, 5)), removed = it == removed)
        }))
        fun session(day: String, date: Int, at: Long, activity: String = "act_mobility") =
            WorkoutSession("$date-$day", activity, "day_$day", on(date).toString(), at, listOf(ExerciseLog("ex_$day", "Stretch $day", listOf(LoggedSet(null, 8.0)))))
        fun ListedActivity.next(vararg sessions: WorkoutSession) = nextRoutineDay(sessions.toList())!!.label
        val wednesday = session("B", 2, at = 1_000)
        val saturday = session("G", 5, at = 2_000)
        // Wednesday corrected on Sunday, or already stored with such a time, is still before Saturday.
        val correctedOnSunday = wednesday.copy(timestampMillis = 3_000)

        assertEquals("A", mobility().next(wednesday, saturday))
        assertEquals("A", mobility().next(correctedOnSunday, saturday))
        assertEquals("A", mobility().next(saturday, correctedOnSunday))
        assertEquals("D", mobility().next(session("B", 5, at = 1_000), session("C", 5, at = 2_000)))
        assertEquals("C", mobility().next(session("B", 5, at = 2_000), session("C", 5, at = 1_000)))
        // Past a removed day, after the newest date.
        assertEquals("B", mobility(removed = "A").next(correctedOnSunday, saturday))
        // A later session of another activity says nothing about this one's turn.
        assertEquals("C", mobility().next(wednesday, session("flow", 6, at = 4_000, activity = "act_yoga")))
    }

    /** The edits a page keeps are applied in order, and one the configuration does not take is left out. */
    @Test fun editsApplyInOrderAndARefusedOneIsLeftOut() {
        val edits = listOf(
            ActivityEdit.Rename("Sport"),
            ActivityEdit.SetWeek(week(4)),
            ActivityEdit.SetAwayDays(9),
            ActivityEdit.SetAwayDays(3),
            ActivityEdit.RemoveActivity("act_none"),
            ActivityEdit.SetHue(com.example.workouttracker.domain.checklist.ViewHue.Teal),
        )
        val applied = seeded.applyingEdits(edits, on(2))
        assertEquals("Sport", applied.label)
        assertEquals(4, applied.week.current.size)
        assertEquals(3, applied.awayDays.current)
        assertEquals(com.example.workouttracker.domain.checklist.ViewHue.Teal, applied.hue)
        assertEquals(seeded.activities, applied.activities)
        assertEquals(seeded, seeded.applyingEdits(listOf(ActivityEdit.SetWeek(week(3)), ActivityEdit.SetWeek(DefaultActivityWeek)), on(2)))
    }
}

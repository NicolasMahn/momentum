package com.example.workouttracker.domain.mo

import com.example.workouttracker.domain.trackerRef
import com.example.workouttracker.domain.compatibility.missed

import com.example.workouttracker.domain.compatibility.free

import com.example.workouttracker.domain.compatibility.kept

import com.example.workouttracker.domain.compatibility.latestOn

import com.example.workouttracker.domain.compatibility.history

import com.example.workouttracker.domain.compatibility.needs

import com.example.workouttracker.domain.compatibility.moFreeDays

import com.example.workouttracker.domain.compatibility.MoNeed

import com.example.workouttracker.domain.compatibility.ReadingTimeId
import com.example.workouttracker.domain.AppState
import com.example.workouttracker.domain.activity.routine.SeededGymDayIds
import com.example.workouttracker.domain.compatibility.eatingDateKeyFor
import com.example.workouttracker.domain.settings.FeatureVisibilitySettings
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test
import com.example.workouttracker.domain.care.everyCareAction
import com.example.workouttracker.domain.care.careState
import com.example.workouttracker.domain.measurement.withWeight
import com.example.workouttracker.domain.mo.compatibility.withInheritedTrackerConfigurations

/**
 * Weeks are written as seven characters, Monday first. Records: `x` for a day with one and `.` for a
 * day without. Statuses: `K` kept, `F` free, `M` missed and `-` not due.
 */
class MoLedgerTest {
    private val monday = LocalDate.parse("2026-09-14")
    private val nextMonday = monday.plusWeeks(1)

    @Test
    fun partialCareIsLatestActivityButCannotKeepTheDayOrGrowTheStreak() {
        val complete = AppState().keeping(MoNeed.Care, monday)
        val partial = careState(*everyCareAction(monday.toString()).take(1).toTypedArray())
        val ledger = MoLedger.of(partial, only(MoNeed.Care), monday)
        assertEquals(MoNeed.Care, ledger.latestOn(monday))
        assertEquals(listOf(MoNeed.Care), ledger.on(monday).missed)
        assertEquals(0, ledger.streak().days)
        assertEquals(1, MoLedger.of(complete, only(MoNeed.Care), monday).streak().days)
        assertEquals(
            listOf(com.example.workouttracker.domain.CheckpointPart("morning"),
                com.example.workouttracker.domain.CheckpointPart("evening")),
            moMood(partial, only(MoNeed.Care), monday).trackerOpenParts[MoNeed.Care.view.trackerRef].orEmpty(),
        )
    }

    @Test
    fun aWeeksFreeDaysGoToItsEarliestDaysWithoutARecord() {
        val expected = listOf(
            MoNeed.Activity to "KFFMKMK",
            MoNeed.Reading to "KFMMKMK",
            MoNeed.Eating to "KFMMKMK",
            MoNeed.Weight to "KFMMKMK",
            MoNeed.Care to "KMMMKMK",
            MoNeed.Supplements to "KMMMKMK",
        )

        expected.forEach { (need, statuses) ->
            assertEquals("$need", listOf(statuses), weeks(need, "x...x.x").statuses(need))
        }
    }

    @Test
    fun theAllowanceStartsAgainEveryMondayAndUnusedFreeDaysAreNotCarriedOver() {
        assertEquals(
            listOf("KKKKKKF", "FKKKKKK"),
            weeks(MoNeed.Reading, "xxxxxx.", ".xxxxxx").statuses(MoNeed.Reading, weekCount = 2),
        )
        assertEquals(
            listOf("KKKKKKK", "FMKKKKK"),
            weeks(MoNeed.Reading, "xxxxxxx", "..xxxxx").statuses(MoNeed.Reading, weekCount = 2),
        )
        assertEquals(
            listOf("KKKKFFM", "FFMKKKK"),
            weeks(MoNeed.Activity, "xxxx...", "...xxxx").statuses(MoNeed.Activity, weekCount = 2),
        )
    }

    @Test
    fun aTaskTakenUpInTheMiddleOfAWeekStillGetsTheWholeAllowance() {
        assertEquals(listOf("---KFFM"), weeks(MoNeed.Activity, "...x...").statuses(MoNeed.Activity))
        assertEquals(listOf("-----KF"), weeks(MoNeed.Reading, ".....x.").statuses(MoNeed.Reading))
    }

    @Test
    fun aRecordAddedLaterHandsItsFreeDayToTheNextDayWithoutOne() {
        val forgotten = weeks(MoNeed.Reading, "x..xxxx")
        val tuesday = monday.plusDays(1)
        val backfilled = forgotten.keeping(MoNeed.Reading, tuesday)

        assertEquals(listOf("KFMKKKK"), forgotten.statuses(MoNeed.Reading))
        assertEquals(listOf("KKFKKKK"), backfilled.statuses(MoNeed.Reading))
        assertEquals(setOf(tuesday), moFreeDays(forgotten, only(MoNeed.Reading), nextMonday)[MoNeed.Reading])
        assertEquals(setOf(tuesday.plusDays(1)), moFreeDays(backfilled, only(MoNeed.Reading), nextMonday)[MoNeed.Reading])
    }

    @Test
    fun theOrderTheRecordsWereSavedInDoesNotMatter() {
        val inOrder = weeks(MoNeed.Reading, "x.x.x.x", ".x.x.x.")
        val shuffled = inOrder.copy(timeLogs = inOrder.timeLogs.filter { it.viewId == ReadingTimeId }.reversed())
        val visibility = only(MoNeed.Reading)

        assertEquals(inOrder.statuses(MoNeed.Reading, weekCount = 2), shuffled.statuses(MoNeed.Reading, weekCount = 2))
        assertEquals(moMood(inOrder, visibility, nextMonday.plusWeeks(1)), moMood(shuffled, visibility, nextMonday.plusWeeks(1)))
    }

    @Test
    fun activityIsKeptByAnyWorkoutWithASetOrAnyTrainingOfThePlan() {
        val sunday = monday.plusDays(6)
        val keeps = listOf("Bouldering", "Jogging", "Running", "Cycling", "cycling", "RUNNING")
            .map { AppState(activityEntries = listOf(activity(it, sunday))) } +
            SeededGymDayIds.map { AppState(sessions = listOf(workout(it, sunday))) }
        val keepsNothing = listOf("Yoga", "Walking", "Run")
            .map { AppState(activityEntries = listOf(activity(it, sunday))) } +
            AppState(sessions = listOf(workout("A", sunday, sets = 0)))

        keeps.forEach { assertEquals("$it", listOf("------K"), it.statuses(MoNeed.Activity)) }
        // Such a record still takes the task up, so the day is due and spends a free day.
        keepsNothing.forEach { assertEquals("$it", listOf("------F"), it.statuses(MoNeed.Activity)) }
    }

    @Test
    fun anyFiveActiveDaysKeepAWeekOfActivityWhicheverDaysTheyAre() {
        val restOnTuesdayAndThursday = AppState(
            sessions = listOf(workout("A", monday), workout("A", monday.plusDays(4))),
            activityEntries = listOf(
                activity("Running", monday.plusDays(2)),
                activity("Bouldering", monday.plusDays(5)),
                activity("Cycling", monday.plusDays(6)),
            ),
        )

        assertEquals(listOf("KFKFKKK"), restOnTuesdayAndThursday.statuses(MoNeed.Activity))
        assertEquals(listOf("KKKKKFF"), weeks(MoNeed.Activity, "xxxxx..").statuses(MoNeed.Activity))
        assertEquals(listOf("KFFMKKK"), weeks(MoNeed.Activity, "x...xxx").statuses(MoNeed.Activity))
        assertEquals(7, moMood(restOnTuesdayAndThursday.withInheritedTrackerConfigurations(), only(MoNeed.Activity), nextMonday).streak.days)
    }

    @Test
    fun anEatingReflectionKeepsTheDayAfterTheOneItIsAbout() {
        val aboutDays = listOf(-1L, 0L, 3L, 4L, 5L).map(monday::plusDays)
        val reflections = AppState(questionEntries = aboutDays.map { reflectionAbout(it) })
        val wednesday = monday.plusDays(2)

        assertEquals(listOf("KKFMKKK"), reflections.statuses(MoNeed.Eating))
        assertEquals(mapOf(MoNeed.Eating to setOf(wednesday)), moFreeDays(reflections.withInheritedTrackerConfigurations(), only(MoNeed.Eating), nextMonday))
        // The Eating calendar files a reflection under the day it is about, which is the day before.
        assertEquals(monday.plusDays(1).toString(), eatingDateKeyFor(wednesday.toString()))
    }

    @Test
    fun aReflectionAboutSundayKeepsTheMondayOfTheNextWeek() {
        val sundayBefore = monday.minusDays(1)
        val sundayWritten = AppState(
            questionEntries = ((0L..4L).map { monday.minusWeeks(1).plusDays(it) } + sundayBefore).map { reflectionAbout(it) },
        )
        val sundaySkipped = AppState(
            questionEntries = ((0L..5L).map { monday.minusWeeks(1).plusDays(it) } + monday.plusDays(1)).map { reflectionAbout(it) },
        )

        assertEquals(
            listOf("-KKKKKF", "KFMMMMM"),
            sundayWritten.statuses(MoNeed.Eating, firstMonday = monday.minusWeeks(1), weekCount = 2),
        )
        assertEquals(
            listOf("-KKKKKK", "FMKMMMM"),
            sundaySkipped.statuses(MoNeed.Eating, firstMonday = monday.minusWeeks(1), weekCount = 2),
        )
    }

    @Test
    fun aTaskIsDueFromItsFirstRecordAndOnlyWhileItsFeatureIsVisible() {
        val readingFromThursday = weeks(MoNeed.Care, "xxxxxxx") + weeks(MoNeed.Reading, "...x...")

        assertEquals(listOf("---KFMM"), readingFromThursday.statuses(MoNeed.Reading, FeatureVisibilitySettings()))
        assertEquals(listOf("-------"), readingFromThursday.statuses(MoNeed.Reading, only(MoNeed.Care)))
        assertEquals(listOf("-------"), AppState().statuses(MoNeed.Activity, FeatureVisibilitySettings()))
    }

    @Test
    fun freeDaysAreTheFinishedFreeDaysOfEveryVisibleTask() {
        val twoWeeks = weeks(MoNeed.Activity, "x..x.xx", "xx.....") +
            weeks(MoNeed.Reading, "xxxxxxx", "x.xxxxx") +
            weeks(MoNeed.Care, "x......", ".......")
        val thursday = nextMonday.plusDays(3)

        assertEquals(
            mapOf(
                MoNeed.Activity to setOf(monday.plusDays(1), monday.plusDays(2), nextMonday.plusDays(2)),
                MoNeed.Reading to setOf(nextMonday.plusDays(1)),
                MoNeed.Care to emptySet(),
            ),
            moFreeDays(twoWeeks, only(MoNeed.Activity, MoNeed.Reading, MoNeed.Care), thursday),
        )
        assertEquals(
            mapOf(MoNeed.Care to emptySet<LocalDate>()),
            moFreeDays(twoWeeks, only(MoNeed.Care), thursday),
        )
    }

    @Test
    fun todayIsNotAFreeDayBeforeItIsOver() {
        val restedOnTuesday = weeks(MoNeed.Activity, "x......")
        val wednesday = monday.plusDays(2)

        assertEquals(
            setOf(monday.plusDays(1)),
            moFreeDays(restedOnTuesday, only(MoNeed.Activity), wednesday)[MoNeed.Activity],
        )
        assertEquals(
            setOf(monday.plusDays(1), wednesday),
            moFreeDays(restedOnTuesday, only(MoNeed.Activity), wednesday.plusDays(1))[MoNeed.Activity],
        )
    }

    /** Records that keep [need] on every `x` of [weeks], the first of which starts on [monday]. */
    /**
     * A hide on Wednesday leaves Tuesday's status where it was and Wednesday and Thursday unjudged;
     * shown again on Friday, Friday is judged on. The task stays in the ledger while hidden, with
     * its history, and out of what Mo exposes on a day it is hidden.
     */
    @Test
    fun aHideOnWednesdayKeepsTuesdayAndAShowOnFridayJudgesFridayOn() {
        val state = AppState().withWeight().keeping(MoNeed.Weight, nextMonday)
        val shown = only(MoNeed.Weight)
        val hidden = shown
            .withVisibility(MoNeed.Weight.view, visible = false, today = nextMonday.plusDays(2))
            .withVisibility(MoNeed.Weight.view, visible = true, today = nextMonday.plusDays(4))
        val saturday = nextMonday.plusDays(5)
        fun statuses(visibility: FeatureVisibilitySettings) = (0L..5L).joinToString(separator = "") { day ->
            when (MoLedger.of(state, visibility, saturday, rewardStart = monday).history(MoNeed.Weight)[nextMonday.plusDays(day)]) {
                MoStatus.Kept -> "K"
                MoStatus.Free -> "F"
                MoStatus.Missed -> "M"
                null -> "-"
            }
        }

        assertEquals("KMMMMM", statuses(shown))
        assertEquals("KM--MM", statuses(hidden))
        assertEquals(setOf(MoNeed.Weight), MoLedger.of(state, hidden, saturday, rewardStart = monday).needs)
        val thursday = MoLedger.of(state, hidden, nextMonday.plusDays(3), rewardStart = monday)
        assertEquals(emptySet<MoNeed>(), thursday.needs)
        assertEquals(nextMonday, thursday.firstDay)
        assertEquals(listOf(MoNeed.Weight), thursday.on(nextMonday.plusDays(1)).missed)
        assertEquals(0, thursday.on(nextMonday.plusDays(3)).dueCount)
    }

    private fun weeks(need: MoNeed, vararg weeks: String): AppState = AppState().keepingWeeks(need, monday, *weeks)

    private operator fun AppState.plus(other: AppState) = copy(
        sessions = sessions + other.sessions,
        activityEntries = activityEntries + other.activityEntries,
        timeLogs = timeLogs + other.timeLogs,
        checklists = (checklists + other.checklists).distinctBy { it.id },
        checklistDays = checklistDays + other.checklistDays,
    )

    /**
     * How [need] went on each day of [weekCount] weeks under the old rules, read once all of them are
     * over. The old rules only apply before an install's rule start, so it is put after the reading.
     */
    private fun AppState.statuses(
        need: MoNeed,
        visibility: FeatureVisibilitySettings = only(need),
        firstMonday: LocalDate = monday,
        weekCount: Int = 1,
    ): List<String> {
        val today = firstMonday.plusWeeks(weekCount.toLong())
        val history = MoLedger.of(this.withInheritedTrackerConfigurations(), visibility, today, rewardStart = today.plusDays(1)).history(need)
        return List(weekCount) { week ->
            (0L until 7L).joinToString(separator = "") { day ->
                when (history[firstMonday.plusWeeks(week.toLong()).plusDays(day)]) {
                    MoStatus.Kept -> "K"
                    MoStatus.Free -> "F"
                    MoStatus.Missed -> "M"
                    null -> "-"
                }
            }
        }
    }
}

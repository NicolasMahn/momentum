package com.example.workouttracker.domain.mo

import com.example.workouttracker.domain.compatibility.missed

import com.example.workouttracker.domain.compatibility.free

import com.example.workouttracker.domain.compatibility.kept

import com.example.workouttracker.domain.compatibility.thriving

import com.example.workouttracker.domain.compatibility.neglect

import com.example.workouttracker.domain.compatibility.weekProgress

import com.example.workouttracker.domain.compatibility.history

import com.example.workouttracker.domain.compatibility.needs

import com.example.workouttracker.domain.compatibility.mood

import com.example.workouttracker.domain.compatibility.MoNeed

import com.example.workouttracker.domain.compatibility.EatingQuestionsId
import com.example.workouttracker.domain.questions.eatingEntry
import com.example.workouttracker.domain.AppState
import com.example.workouttracker.domain.TrackerRef
import com.example.workouttracker.domain.activity.routine.SeededGymDayIds
import com.example.workouttracker.domain.settings.FeatureVisibilitySettings
import com.example.workouttracker.domain.mo.compatibility.InheritedMoEvaluation
import com.example.workouttracker.domain.mo.compatibility.InheritedStreakReport
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import com.example.workouttracker.domain.care.withCare
import com.example.workouttracker.domain.care.everyCareAction
import com.example.workouttracker.domain.supplement.withSupplements
import com.example.workouttracker.domain.measurement.withWeight
import com.example.workouttracker.domain.questions.withEating
import com.example.workouttracker.domain.time.withReading
import com.example.workouttracker.domain.activity.withActivity
import org.junit.Assert.assertTrue

class MoMoodTest {
    // A Sunday, so counting back from it walks one whole week first: 1 is its Saturday, 6 its Monday.
    private val today = LocalDate.parse("2026-09-20")

    // Care and Supplements have no free days, so with them every day without a record is a miss.
    private val onlyCare = only(MoNeed.Care)
    private val careAndSupplements = only(MoNeed.Care, MoNeed.Supplements)
    private val readingAndWeight = only(MoNeed.Reading, MoNeed.Weight)

    private fun inheritedMood(state: AppState, visibility: FeatureVisibilitySettings, date: LocalDate) =
        InheritedMoEvaluation(state, visibility, date).mood

    private fun InheritedStreakReport.tally() = Tally(days, freezes, freezeCapacity)

    @Test
    fun withoutAnyRecordsMoIsSteadyAndAsksForNothing() {
        val mood = InheritedMoEvaluation(AppState(), FeatureVisibilitySettings(), today).current

        assertEquals(MoState.Steady, mood.state)
        assertEquals(emptyList<TrackerRef>(), mood.missingTrackersToday)
        assertEquals(emptyList<TrackerRef>(), mood.freeTrackersToday)
        assertEquals(0, mood.dueToday)
        assertEquals(Tally(days = 0, freezes = 0, freezeCapacity = 3), mood.streak.tally())
        assertEquals(emptyMap<TrackerRef, MoNeglect>(), mood.trackerNeglect)
        assertEquals(emptyList<TrackerRef>(), mood.streak.frozenTrackersYesterday)
        assertEquals(emptyList<TrackerRef>(), mood.keptTrackersToday)
        assertNull(mood.latestTrackerToday)
        assertEquals(emptySet<TrackerRef>(), mood.thrivingTrackers)
    }

    /**
     * Weight hidden on a Wednesday and shown again on Friday: Tuesday keeps its miss, the two hidden
     * days are not held against Mo, so he is less neglected and no colder than with Weight shown
     * throughout, and the week's progress counts Monday's record either way.
     */
    @Test
    fun aHideOnWednesdayAndAShowOnFridayKeepTuesdayAndSkipTheHiddenDays() {
        val sunday = LocalDate.parse("2026-10-18")
        val monday = sunday.minusDays(6)
        val state = AppState().withWeight().keeping(MoNeed.Weight, monday)
        val shown = only(MoNeed.Weight)
        val hidden = shown
            .withVisibility(MoNeed.Weight.view, visible = false, today = monday.plusDays(2))
            .withVisibility(MoNeed.Weight.view, visible = true, today = monday.plusDays(4))

        val throughout = inheritedMood(state, shown, sunday)
        val paused = inheritedMood(state, hidden, sunday)

        assertEquals(MoWeekProgress(1, 7), paused.weekProgress[MoNeed.Weight])
        assertEquals(throughout.weekProgress, paused.weekProgress)
        assertEquals(MoNeglect.Severe, throughout.neglect[MoNeed.Weight])
        assertEquals(MoNeglect.Mild, paused.neglect[MoNeed.Weight])
        assertTrue(paused.state >= throughout.state)
        assertEquals(listOf(MoNeed.Weight), paused.missingToday)
        assertEquals(listOf(MoNeed.Weight), MoLedger.of(state, hidden, sunday).on(monday.plusDays(1)).missed)
        assertEquals(0, MoLedger.of(state, hidden, sunday).on(monday.plusDays(2)).dueCount)
    }

    @Test
    fun keptDaysBuildUpToBlazing() {
        val mood = inheritedMood(caredOn(daysAgo = 1..10), onlyCare, today)

        assertEquals(MoState.Blazing, mood.state)
        assertEquals(0, mood.daysCold)
    }

    @Test
    fun fromFullHeatHeCoolsOneStatePerMissedDayAfterOneDayOfGrace() {
        val expected = listOf(
            1 to MoState.Blazing,
            2 to MoState.Steady,
            3 to MoState.Dimming,
            4 to MoState.Cold,
            5 to MoState.Husk,
        )

        expected.forEach { (missedDays, state) ->
            val mood = inheritedMood(caredOn(daysAgo = (missedDays + 1)..(missedDays + 10)), onlyCare, today)
            assertEquals("after $missedDays missed days", state, mood.state)
        }
    }

    @Test
    fun aFreeDayDoesNotCoolHim() {
        val twoDaysWithoutARecord = 3..12

        assertEquals(MoState.Steady, inheritedMood(caredOn(twoDaysWithoutARecord), onlyCare, today).state)
        assertEquals(
            MoState.Blazing,
            inheritedMood(history(MoNeed.Activity to twoDaysWithoutARecord), only(MoNeed.Activity), today).state,
        )
    }

    @Test
    fun aFreeDayHoldsItsShareOfADayLikeARecord() {
        // Two days without any record. Reading's free day covers the first of them, nothing the second.
        val withAFreeDay = history(MoNeed.Care to 3..6, MoNeed.Reading to 3..6)
        val withoutOne = history(MoNeed.Care to 3..6, MoNeed.Supplements to 3..6)

        assertEquals(MoState.Blazing, inheritedMood(withAFreeDay, only(MoNeed.Care, MoNeed.Reading), today).state)
        assertEquals(MoState.Steady, inheritedMood(withoutOne, careAndSupplements, today).state)
    }

    @Test
    fun aFreeDayWarmsNothingBeforeMidnight() {
        val monday = today.plusDays(1)
        val gaveUpAWeekAgo = history(MoNeed.Care to 7..16, MoNeed.Reading to 7..16)
        val careAndReading = only(MoNeed.Care, MoNeed.Reading)

        val untouched = inheritedMood(gaveUpAWeekAgo, careAndReading, monday)
        val tended = inheritedMood(gaveUpAWeekAgo.keeping(MoNeed.Care, monday), careAndReading, monday)

        assertEquals(listOf(MoNeed.Reading), untouched.freeToday)
        assertEquals(MoState.Husk, untouched.state)
        assertEquals(MoState.Cold, tended.state)
    }

    @Test
    fun daysColdCountsTheCurrentColdSpellOnly() {
        val mood = inheritedMood(caredOn(daysAgo = 7..16), onlyCare, today)

        assertEquals(MoState.Husk, mood.state)
        assertEquals(3, mood.daysCold)
    }

    @Test
    fun theFirstRecordTodayLiftsHimOneStateAtOnce() {
        val rekindled = caredOn(daysAgo = (7..16) + 0)

        val mood = inheritedMood(rekindled, onlyCare, today)

        assertEquals(MoState.Cold, mood.state)
        assertEquals(emptyList<MoNeed>(), mood.missingToday)
    }

    @Test
    fun oneCareActionBringsReliefWithoutCompletingTheDayOrGrowingTheStreak() {
        val history = caredOn(daysAgo = 7..16)
        val oneMorningAction = everyCareAction(today.toString()).first()
        val mood = inheritedMood(history.withCare(oneMorningAction), onlyCare, today)

        assertEquals(MoState.Husk, inheritedMood(history, onlyCare, today).state)
        assertEquals(MoState.Cold, mood.state)
        assertEquals(listOf(MoNeed.Care), mood.missingToday)
        assertEquals(emptyList<MoNeed>(), mood.keptToday)
        assertEquals(MoNeed.Care, mood.latestToday)
        assertEquals(0, mood.streak.days)

        val hiddenCare = inheritedMood(
            history.withCare(oneMorningAction),
            only(MoNeed.Reading),
            today,
        )
        assertEquals(MoState.Steady, hiddenCare.state)
    }

    @Test
    fun yogaAloneDoesNotGetTheCareReliefOrCompletionHeat() {
        val history = caredOn(daysAgo = 7..16)
        val mood = inheritedMood(
            history.copy(activityEntries = listOf(activity("Yoga", today))),
            only(MoNeed.Care, MoNeed.Activity),
            today,
        )
        assertEquals(MoState.Husk, mood.state)
        assertEquals(MoNeed.Activity, mood.latestToday)
    }

    @Test
    fun aBurningMoEarnsBlazingOnlyWithAFullDay() {
        // Supplements were taken up and then dropped, which leaves him exactly where he started.
        val halfOfToday = history(MoNeed.Supplements to listOf(2), MoNeed.Care to listOf(0))

        assertEquals(MoState.Steady, inheritedMood(halfOfToday, careAndSupplements, today).state)
        assertEquals(MoState.Blazing, inheritedMood(halfOfToday, onlyCare, today).state)
    }

    @Test
    fun aBlazingMoStaysBlazingWhenTodayIsKeptToo() {
        val mood = inheritedMood(caredOn(daysAgo = 0..10), onlyCare, today)

        assertEquals(MoState.Blazing, mood.state)
    }

    @Test
    fun anUnfinishedTodayNeverCoolsHim() {
        val mood = inheritedMood(caredOn(daysAgo = 1..10), onlyCare, today)

        assertEquals(MoState.Blazing, mood.state)
        assertEquals(listOf(MoNeed.Care), mood.missingToday)
    }

    @Test
    fun heReactsToTheActOfLoggingNeverToTheLoggedValue() {
        val onlyEating = only(MoNeed.Eating)
        fun eating(quality: String) = AppState(
            questionEntries = (1..10L).map { daysAgo ->
                val dateKey = today.minusDays(daysAgo).toString()
                eatingEntry(id = dateKey, dateKey = dateKey, timestampMillis = 0, quality = quality)
            },
        )

        assertEquals(
            inheritedMood(eating("good"), onlyEating, today),
            inheritedMood(eating("poor"), onlyEating, today),
        )
    }

    @Test
    fun hiddenFeaturesAreNotCommitments() {
        val longAgo = AppState(timeLogs = listOf(reading(today.minusDays(20))))

        val mood = inheritedMood(longAgo, only(), today)

        assertEquals(MoState.Steady, mood.state)
        assertEquals(emptyList<MoNeed>(), mood.missingToday)
        assertEquals(emptyList<MoNeed>(), mood.freeToday)
        assertEquals(emptyMap<MoNeed, MoWeekProgress>(), mood.weekProgress)
        assertEquals(0, mood.streak.days)
        assertEquals(emptyMap<MoNeed, MoNeglect>(), mood.neglect)
    }

    @Test
    fun aHiddenFeatureCannotBreakTheStreakOfTheOthers() {
        val supplementsDropped = history(MoNeed.Care to 1..5, MoNeed.Supplements to listOf(5))

        assertEquals(0, inheritedMood(supplementsDropped, careAndSupplements, today).streak.days)
        assertEquals(5, inheritedMood(supplementsDropped, onlyCare, today).streak.days)
    }

    @Test
    fun historyBeforeTheFirstRecordIsNotHeldAgainstHim() {
        val startedYesterday = caredOn(daysAgo = 1..1)

        assertEquals(MoState.Blazing, inheritedMood(startedYesterday, onlyCare, today).state)
    }

    @Test
    fun aTaskIsDueOnlyFromItsOwnFirstRecord() {
        val supplementsTakenUpLater = history(MoNeed.Care to 1..5, MoNeed.Supplements to 1..2)

        val mood = inheritedMood(supplementsTakenUpLater, FeatureVisibilitySettings(), today)

        assertEquals(5, mood.streak.days)
        assertEquals(listOf(MoNeed.Care, MoNeed.Supplements), mood.missingToday)
    }

    @Test
    fun anyActivityRecordStartsTheActivityTaskButOnlyATrainingKeepsIt() {
        val thursday = today.minusDays(3)
        val yoga = AppState(activityEntries = listOf(activity("Yoga", thursday)))
        val running = AppState(activityEntries = listOf(activity("Running", thursday)))

        // Friday and Saturday went without a training. After yoga so did Thursday, one day more than is free.
        assertEquals(0, inheritedMood(yoga, only(MoNeed.Activity), today).streak.days)
        assertEquals(3, inheritedMood(running, only(MoNeed.Activity), today).streak.days)
    }

    @Test
    fun restDaysKeepTheStreakGrowing() {
        val friday = today.minusDays(2)
        val tuesday = friday.plusDays(4)
        val gymAroundTheWeekend = AppState(
            sessions = listOf(workout("C", friday), workout("A", friday.plusDays(3))),
        )

        val mood = inheritedMood(gymAroundTheWeekend, only(MoNeed.Activity), tuesday)

        assertEquals(4, mood.streak.days)
        assertEquals(listOf(MoNeed.Activity), mood.freeToday)
        assertEquals(emptyList<MoNeed>(), mood.missingToday)
    }

    @Test
    fun anEatingReflectionAnswersTheDayAfterTheOneItIsAbout() {
        val reflectedOnYesterday = AppState(questionEntries = listOf(reflectionAbout(today.minusDays(1))))

        val mood = inheritedMood(reflectedOnYesterday, only(MoNeed.Eating), today)

        assertEquals(listOf(MoNeed.Eating), mood.keptToday)
        assertEquals(emptyList<MoNeed>(), mood.missingToday)
        assertEquals(1, mood.streak.days)
    }

    @Test
    fun eatingAsksTodayWhetherYesterdaysReflectionIsWritten() {
        val wednesday = today.minusDays(4)
        val onlyEating = only(MoNeed.Eating)
        val upToMonday = AppState(questionEntries = (2..3L).map { reflectionAbout(wednesday.minusDays(it)) })
        val aboutTodayOnly = upToMonday.copy(questionEntries = (upToMonday.questionEntries.filter { it.viewId == EatingQuestionsId } + reflectionAbout(wednesday)))
        val aboutYesterday = upToMonday.copy(
            questionEntries = (upToMonday.questionEntries.filter { it.viewId == EatingQuestionsId } + reflectionAbout(wednesday.minusDays(1))),
        )
        // Without Monday's reflection Tuesday took the week's free day, so Wednesday has none left.
        val mondaySkipped = AppState(questionEntries = listOf(reflectionAbout(wednesday.minusDays(3))))

        listOf(upToMonday, aboutTodayOnly).forEach { state ->
            val mood = inheritedMood(state, onlyEating, wednesday)
            assertEquals(listOf(MoNeed.Eating), mood.freeToday)
            assertEquals(emptyList<MoNeed>(), mood.keptToday)
            assertNull(mood.latestToday)
        }
        assertEquals(listOf(MoNeed.Eating), inheritedMood(aboutYesterday, onlyEating, wednesday).keptToday)
        assertEquals(MoNeed.Eating, inheritedMood(aboutYesterday, onlyEating, wednesday).latestToday)
        assertEquals(listOf(MoNeed.Eating), inheritedMood(mondaySkipped, onlyEating, wednesday).missingToday)
        assertEquals(
            mapOf(MoNeed.Eating to MoWeekProgress(done = 1, expected = 6)),
            inheritedMood(mondaySkipped, onlyEating, wednesday).weekProgress,
        )
    }

    @Test
    fun aBadDayCostsOneFreezeHoweverManyTasksItMissedAndKeepsTheStreakWithoutGrowingIt() {
        // Two whole weeks came before the bad day, so it found two freezes in the bank.
        val oneMissed = history(MoNeed.Care to (3..23) + 1, MoNeed.Supplements to 1..23)
        // Weight rested on Monday already, so two days ago it had no free day left.
        val threeMissed = history(
            MoNeed.Care to (3..23) + 1,
            MoNeed.Supplements to (3..23) + 1,
            MoNeed.Weight to ((3..23) - 6) + 1,
        )

        assertEquals(
            Tally(days = 22, freezes = 1, freezeCapacity = 3),
            inheritedMood(oneMissed, careAndSupplements, today).streak.tally(),
        )
        assertEquals(
            Tally(days = 22, freezes = 1, freezeCapacity = 3),
            inheritedMood(threeMissed, only(MoNeed.Care, MoNeed.Supplements, MoNeed.Weight), today).streak.tally(),
        )
    }

    @Test
    fun aBadDayThatFindsTheBankEmptyResetsTheStreak() {
        // Care was taken up last week: on its Tuesday, so that week earned nothing, or on its Monday.
        val withoutAFreeze = inheritedMood(caredOn(daysAgo = (3..12) + 1), onlyCare, today).streak
        val withOneFreeze = inheritedMood(caredOn(daysAgo = (3..13) + 1), onlyCare, today).streak

        assertEquals(Tally(days = 1, freezes = 0, freezeCapacity = 3), withoutAFreeze.tally())
        assertEquals(Tally(days = 12, freezes = 0, freezeCapacity = 3), withOneFreeze.tally())
    }

    @Test
    fun aFullyLostDayCostsOneFreezeAndNamesTheTasksItPaidFor() {
        val fiveTasks = listOf(MoNeed.Reading, MoNeed.Weight, MoNeed.Care, MoNeed.Supplements, MoNeed.Eating)
        val nothingLoggedYesterday = history(*fiveTasks.map { it to 2..36 }.toTypedArray())

        val streak = inheritedMood(nothingLoggedYesterday, only(*fiveTasks.toTypedArray()), today).streak

        assertEquals(Tally(days = 35, freezes = 3, freezeCapacity = 5), streak.tally())
        // The other three tasks spent their free day on it, and a free day needs no freeze.
        assertEquals(listOf(MoNeed.Care, MoNeed.Supplements), streak.frozenYesterday)
    }

    @Test
    fun aCompleteTodayAlreadyCountsAndAnOpenOneCostsNothing() {
        val open = history(MoNeed.Care to 1..6, MoNeed.Supplements to 0..6)
        val complete = history(MoNeed.Care to 0..6, MoNeed.Supplements to 0..6)

        assertEquals(
            Tally(days = 6, freezes = 0, freezeCapacity = 3),
            inheritedMood(open, careAndSupplements, today).streak.tally(),
        )
        // Today is the Sunday of a perfect week, and still its freeze only comes with Monday.
        assertEquals(
            Tally(days = 7, freezes = 0, freezeCapacity = 3),
            inheritedMood(complete, careAndSupplements, today).streak.tally(),
        )
    }

    @Test
    fun aFreeDayCompletesTodayOnlyAtMidnight() {
        val readingLeftForItsFreeDay = history(MoNeed.Care to 0..6, MoNeed.Reading to 1..6)
        val careAndReading = only(MoNeed.Care, MoNeed.Reading)

        val beforeMidnight = inheritedMood(readingLeftForItsFreeDay, careAndReading, today)
        val afterMidnight = inheritedMood(readingLeftForItsFreeDay, careAndReading, today.plusDays(1))

        assertEquals(listOf(MoNeed.Reading), beforeMidnight.freeToday)
        assertEquals(Tally(days = 6, freezes = 0, freezeCapacity = 3), beforeMidnight.streak.tally())
        assertEquals(Tally(days = 7, freezes = 1, freezeCapacity = 3), afterMidnight.streak.tally())
    }

    @Test
    fun neglectGrowsWithTheMissesOfTheLastSevenFinishedDays() {
        val expected = listOf(
            1 to null,
            2 to MoNeglect.Mild,
            3 to MoNeglect.Mild,
            4 to MoNeglect.Severe,
            5 to MoNeglect.Severe,
            6 to MoNeglect.Dire,
            7 to MoNeglect.Dire,
        )

        expected.forEach { (misses, neglect) ->
            val mood = inheritedMood(caredOn(daysAgo = (misses + 1)..10), onlyCare, today)
            assertEquals("after $misses misses", neglect, mood.neglect[MoNeed.Care])
        }
    }

    @Test
    fun aFreeDayIsNotNeglect() {
        val droppedSixDaysAgo = history(MoNeed.Care to 7..10, MoNeed.Reading to 7..10)
        val restedOnMondays = history(MoNeed.Reading to (1..14) - 6 - 13)

        assertEquals(
            mapOf(MoNeed.Care to MoNeglect.Dire, MoNeed.Reading to MoNeglect.Severe),
            inheritedMood(droppedSixDaysAgo, only(MoNeed.Care, MoNeed.Reading), today).neglect,
        )
        assertEquals(emptyMap<MoNeed, MoNeglect>(), inheritedMood(restedOnMondays, only(MoNeed.Reading), today).neglect)
    }

    @Test
    fun neglectForgetsMissesOlderThanAWeek() {
        val missedLongAgo = caredOn(daysAgo = (1..7) + 12)

        assertEquals(emptyMap<MoNeed, MoNeglect>(), inheritedMood(missedLongAgo, onlyCare, today).neglect)
    }

    @Test
    fun neglectCountsOnlyDaysTheTaskWasDue() {
        val takenUpThreeDaysAgo = caredOn(daysAgo = 3..3)

        assertEquals(MoNeglect.Mild, inheritedMood(takenUpThreeDaysAgo, onlyCare, today).neglect[MoNeed.Care])
    }

    @Test
    fun aRecordTodayTakesOneMissBack() {
        assertEquals(
            mapOf(MoNeed.Care to MoNeglect.Mild),
            inheritedMood(caredOn(daysAgo = (5..10) + 0), onlyCare, today).neglect,
        )
        assertEquals(
            emptyMap<MoNeed, MoNeglect>(),
            inheritedMood(caredOn(daysAgo = (3..10) + 0), onlyCare, today).neglect,
        )
    }

    @Test
    fun aFreezeSavesTheStreakButTheMissStillCountsAsNeglect() {
        // Two whole weeks left two freezes, and the last two days spent them.
        val mood = inheritedMood(caredOn(daysAgo = 3..23), onlyCare, today)

        assertEquals(Tally(days = 21, freezes = 0, freezeCapacity = 3), mood.streak.tally())
        assertEquals(mapOf(MoNeed.Care to MoNeglect.Mild), mood.neglect)
    }

    @Test
    fun aRecordTodayLowersDireNeglectToo() {
        val mood = inheritedMood(caredOn(daysAgo = (7..10) + 0), onlyCare, today)

        assertEquals(mapOf(MoNeed.Care to MoNeglect.Severe), mood.neglect)
    }

    @Test
    fun activityHasTwoFreeDaysAWeekSoItsNeglectStopsAtSevere() {
        val droppedLongAgo = history(MoNeed.Activity to listOf(17))

        // Every seven days in a row hold two free days, wherever the week boundary falls in them.
        (0L..6L).forEach { later ->
            val mood = inheritedMood(droppedLongAgo, only(MoNeed.Activity), today.plusDays(later))
            assertEquals("$later days later", mapOf(MoNeed.Activity to MoNeglect.Severe), mood.neglect)
        }
    }

    @Test
    fun yesterdayIsFrozenOnlyWhenAFreezePaidForItsMisses() {
        // Thirteen days ago was a Monday, so last week was a whole perfect one and left a freeze.
        val perfect = inheritedMood(caredOn(daysAgo = 1..13), onlyCare, today).streak
        val frozen = inheritedMood(caredOn(daysAgo = 2..13), onlyCare, today).streak
        val broken = inheritedMood(caredOn(daysAgo = 2..12), onlyCare, today).streak
        val frozenTheDayBefore = inheritedMood(caredOn(daysAgo = (3..13) + 1), onlyCare, today).streak

        assertEquals(emptyList<MoNeed>(), perfect.frozenYesterday)
        assertEquals(listOf(MoNeed.Care), frozen.frozenYesterday)
        assertEquals(Tally(days = 12, freezes = 0, freezeCapacity = 3), frozen.tally())
        assertEquals(emptyList<MoNeed>(), broken.frozenYesterday)
        assertEquals(0, broken.days)
        assertEquals(emptyList<MoNeed>(), frozenTheDayBefore.frozenYesterday)
    }

    @Test
    fun aRecordAddedLaterForAFrozenDayBringsTheFreezeBack() {
        val backfilled = inheritedMood(caredOn(daysAgo = 1..13), onlyCare, today).streak

        assertEquals(Tally(days = 13, freezes = 1, freezeCapacity = 3), backfilled.tally())
        assertEquals(emptyList<MoNeed>(), backfilled.frozenYesterday)
    }

    @Test
    fun aRecordAddedLaterTurnsTheMissItsFreeDayMovesOntoIntoAFreeOne() {
        // Reading rested on Tuesday and Wednesday, so Wednesday was missed and broke the streak.
        val forgotten = history(MoNeed.Reading to (1..3) + (6..9))
        val backfilled = history(MoNeed.Reading to (1..3) + (5..9))

        assertEquals(3, inheritedMood(forgotten, only(MoNeed.Reading), today).streak.days)
        assertEquals(9, inheritedMood(backfilled, only(MoNeed.Reading), today).streak.days)
    }

    @Test
    fun keptTodayListsTheDueTasksThatHaveTheirRecord() {
        val careDone = history(MoNeed.Care to 0..3, MoNeed.Supplements to 1..3)

        val mood = inheritedMood(careDone, careAndSupplements, today)

        assertEquals(listOf(MoNeed.Care), mood.keptToday)
        assertEquals(listOf(MoNeed.Supplements), mood.missingToday)
        assertEquals(2, mood.dueToday)
    }

    @Test
    fun anOpenTaskIsFreeTodayWhileItsWeekHasAFreeDayLeftAndMissingOnceItHasNone() {
        val monday = today.minusDays(6)
        val wednesday = monday.plusDays(2)
        val visible = only(MoNeed.Activity, MoNeed.Reading, MoNeed.Care)
        // Activity and Reading both rested on Tuesday, which leaves Activity one free day and Reading none.
        val restedOnTuesday = AppState()
            .keeping(MoNeed.Activity, monday)
            .keeping(MoNeed.Reading, monday)
            .keeping(MoNeed.Care, monday)
            .keeping(MoNeed.Care, monday.plusDays(1))

        val untouched = inheritedMood(restedOnTuesday, visible, wednesday)
        val readToday = inheritedMood(restedOnTuesday.keeping(MoNeed.Reading, wednesday), visible, wednesday)
        val nextDay = inheritedMood(restedOnTuesday, visible, wednesday.plusDays(1))
        val nextWeek = inheritedMood(restedOnTuesday, visible, monday.plusWeeks(1))

        assertEquals(listOf(MoNeed.Activity), untouched.freeToday)
        assertEquals(listOf(MoNeed.Reading, MoNeed.Care), untouched.missingToday)
        assertEquals(emptyList<MoNeed>(), untouched.keptToday)
        assertEquals(3, untouched.dueToday)
        assertEquals(week(activity = 1, reading = 1, care = 2), untouched.weekProgress)

        assertEquals(listOf(MoNeed.Reading), readToday.keptToday)
        assertEquals(listOf(MoNeed.Care), readToday.missingToday)
        // A record today shows at once, because the week's count includes today.
        assertEquals(week(activity = 1, reading = 2, care = 2), readToday.weekProgress)

        // Midnight spent Activity's last free day on the untouched Wednesday.
        assertEquals(emptyList<MoNeed>(), nextDay.freeToday)
        assertEquals(listOf(MoNeed.Activity, MoNeed.Reading, MoNeed.Care), nextDay.missingToday)
        assertEquals(untouched.weekProgress, nextDay.weekProgress)

        assertEquals(listOf(MoNeed.Activity, MoNeed.Reading), nextWeek.freeToday)
        assertEquals(listOf(MoNeed.Care), nextWeek.missingToday)
        assertEquals(week(activity = 0, reading = 0, care = 0), nextWeek.weekProgress)
    }

    @Test
    fun theWeekExpectsSevenDaysMinusEachTasksFreeDaysForEveryVisibleTask() {
        val expected = mapOf(
            MoNeed.Activity to 5, MoNeed.Reading to 6, MoNeed.Care to 7,
            MoNeed.Supplements to 7, MoNeed.Eating to 6, MoNeed.Weight to 6,
        )

        // Activity's days a week are its configuration's; every other task's are fixed.
        assertEquals(null, MoNeed.Activity.expectedDaysPerWeek)
        assertEquals(expected - MoNeed.Activity, MoNeed.entries.filter { it != MoNeed.Activity }.associateWith { it.expectedDaysPerWeek })
        // A task exists by its configuration or its records; the six configured and nothing logged is every task at nothing.
        val configured = AppState().withSupplements().withCare().withWeight().withEating().withReading().withActivity()
        assertEquals(
            expected.mapValues { (_, days) -> MoWeekProgress(done = 0, expected = days) },
            inheritedMood(configured, FeatureVisibilitySettings(disabledFeatures = emptySet()), today).weekProgress,
        )
        assertEquals(
            setOf(MoNeed.Weight, MoNeed.Care),
            inheritedMood(configured, only(MoNeed.Weight, MoNeed.Care), today).weekProgress.keys,
        )
        assertEquals(
            emptyMap<TrackerRef, MoWeekProgress>(),
            moMood(AppState(), FeatureVisibilitySettings(disabledFeatures = emptySet()), today).weekProgressByTracker,
        )
    }

    private fun week(activity: Int, reading: Int, care: Int) = mapOf(
        MoNeed.Activity to MoWeekProgress(activity, expected = 5),
        MoNeed.Reading to MoWeekProgress(reading, expected = 6),
        MoNeed.Care to MoWeekProgress(care, expected = 7),
    )

    @Test
    fun latestTodayIsTheTaskWithTheNewestRecord() {
        val readLast = AppState().keeping(MoNeed.Weight, today, at = 100).keeping(MoNeed.Reading, today, at = 200)
        val weighedLast = AppState().keeping(MoNeed.Weight, today, at = 300).keeping(MoNeed.Reading, today, at = 200)

        assertEquals(MoNeed.Reading, inheritedMood(readLast, readingAndWeight, today).latestToday)
        assertEquals(MoNeed.Weight, inheritedMood(weighedLast, readingAndWeight, today).latestToday)
        assertEquals(listOf(MoNeed.Reading, MoNeed.Weight), inheritedMood(weighedLast, readingAndWeight, today).keptToday)
    }

    @Test
    fun latestTodayIgnoresOtherDaysAndHiddenFeatures() {
        val weighedLast = AppState()
            .keeping(MoNeed.Reading, today.minusDays(1), at = 900)
            .keeping(MoNeed.Reading, today, at = 200)
            .keeping(MoNeed.Weight, today, at = 300)

        assertEquals(MoNeed.Reading, inheritedMood(weighedLast, only(MoNeed.Reading), today).latestToday)
        assertEquals(listOf(MoNeed.Reading), inheritedMood(weighedLast, only(MoNeed.Reading), today).keptToday)
        assertNull(inheritedMood(weighedLast, onlyCare, today).latestToday)
    }

    @Test
    fun latestTodayFollowsTheEatingReflectionThatKeepsToday() {
        val aboutYesterday = AppState().keeping(MoNeed.Reading, today, at = 100).keeping(MoNeed.Eating, today, at = 200)
        val aboutToday = AppState().keeping(MoNeed.Reading, today, at = 100).keeping(MoNeed.Eating, today.plusDays(1), at = 200)
        val readingAndEating = only(MoNeed.Reading, MoNeed.Eating)

        assertEquals(MoNeed.Eating, inheritedMood(aboutYesterday, readingAndEating, today).latestToday)
        assertEquals(MoNeed.Reading, inheritedMood(aboutToday, readingAndEating, today).latestToday)
    }

    @Test
    fun aWeekendWorkoutKeepsItsDayAndIsTheLatestThingDone() {
        val saturday = today.minusDays(1)
        val trainedLast = AppState(sessions = listOf(workout("B", saturday, at = 200)))
            .keeping(MoNeed.Reading, saturday, at = 100)

        val mood = inheritedMood(trainedLast, only(MoNeed.Activity, MoNeed.Reading), saturday)

        assertEquals(MoNeed.Activity, mood.latestToday)
        assertEquals(listOf(MoNeed.Activity, MoNeed.Reading), mood.keptToday)
        assertEquals(2, mood.dueToday)
    }

    @Test
    fun anActivityOutsideThePlanIsStillTheLatestThingDoneButKeepsNothing() {
        val thursday = today.minusDays(3)
        val yoga = AppState(activityEntries = listOf(activity("Yoga", thursday, at = 100)))

        val mood = inheritedMood(yoga, only(MoNeed.Activity), thursday)

        assertEquals(MoNeed.Activity, mood.latestToday)
        assertEquals(emptyList<MoNeed>(), mood.keptToday)
        assertEquals(listOf(MoNeed.Activity), mood.freeToday)
    }

    @Test
    fun aTaskThrivesWhenTheLastWeekHasNoMissAndAtLeastThreeKeptDays() {
        val expected = listOf(
            (1..2) to false,
            (1..3) to true,
            (1..7) to true,
            (1..30) to true,
            (2..8) to false,
            ((1..6) + (8..10)) to false,
            // Today is not a finished day, so it neither helps nor hurts.
            ((0..0) + (2..8)) to false,
        )

        expected.forEach { (daysAgo, thrives) ->
            val mood = inheritedMood(caredOn(daysAgo), onlyCare, today)
            assertEquals("cared on $daysAgo", thrives, MoNeed.Care in mood.thriving)
        }
    }

    @Test
    fun aFreeDayNeitherHelpsNorHurtsThriving() {
        val expected = listOf(
            // Saturday was free.
            (MoNeed.Reading to 2..8) to true,
            // Friday was free, Saturday was missed.
            (MoNeed.Reading to 3..8) to false,
            // Rest days on Tuesday and Thursday, and then on Friday too.
            (MoNeed.Activity to listOf(1, 2, 4) + (6..13)) to true,
            (MoNeed.Activity to listOf(1, 4) + (6..13)) to false,
            // Taken up on Wednesday: two free days do not make up for a third kept one.
            (MoNeed.Activity to 3..4) to false,
            (MoNeed.Activity to 2..4) to true,
        )

        expected.forEach { (kept, thrives) ->
            val (need, daysAgo) = kept
            val mood = inheritedMood(history(kept), only(need), today)
            assertEquals("$need kept on $daysAgo", thrives, need in mood.thriving)
        }
    }

    @Test
    fun aHiddenFeatureDoesNotThrive() {
        val bothKept = history(MoNeed.Reading to 1..7, MoNeed.Weight to 1..7)

        assertEquals(setOf(MoNeed.Reading), inheritedMood(bothKept, only(MoNeed.Reading), today).thriving)
    }

    @Test
    fun noTaskIsEverBothThrivingAndNeglected() {
        // Every way to keep or skip the last week, started before it, with and without a record today.
        listOf(MoNeed.Care, MoNeed.Reading, MoNeed.Activity).forEach { need ->
            (0 until (1 shl 8)).forEach { pattern ->
                val keptDaysAgo = (0..7).filter { pattern and (1 shl it) != 0 } + 8
                val mood = inheritedMood(history(need to keptDaysAgo), only(need), today)

                assertEquals("$need kept on $keptDaysAgo", emptySet<MoNeed>(), mood.thriving intersect mood.neglect.keys)
            }
        }
    }

    private fun caredOn(daysAgo: Iterable<Int>) = history(MoNeed.Care to daysAgo)

    /** Records that keep each task on the given days, counted back from [today]. */
    private fun history(vararg kept: Pair<MoNeed, Iterable<Int>>): AppState =
        kept.fold(AppState()) { state, (need, daysAgo) ->
            daysAgo.fold(state) { soFar, ago -> soFar.keeping(need, today.minusDays(ago.toLong())) }
        }
}

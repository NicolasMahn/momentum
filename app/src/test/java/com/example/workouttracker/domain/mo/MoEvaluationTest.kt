package com.example.workouttracker.domain.mo

import com.example.workouttracker.domain.compatibility.history

import com.example.workouttracker.domain.compatibility.moodWithStoredChoices

import com.example.workouttracker.domain.compatibility.mood

import com.example.workouttracker.domain.compatibility.freeDays

import com.example.workouttracker.domain.compatibility.moFreeDays

import com.example.workouttracker.domain.compatibility.MoNeed

import com.example.workouttracker.domain.trackerRef

import com.example.workouttracker.domain.measurement.recordsOf

import com.example.workouttracker.domain.compatibility.eatingDateKeyFor
import com.example.workouttracker.domain.questions.areQuestionsLoggedOnTimeFor
import com.example.workouttracker.domain.compatibility.EatingQuestionsId
import com.example.workouttracker.domain.compatibility.LegacyCareSession
import com.example.workouttracker.domain.holiday.DayRange
import com.example.workouttracker.domain.holiday.HolidaySchedule
import com.example.workouttracker.domain.time.minutesInWeek
import com.example.workouttracker.domain.settings.FeatureVisibilitySettings
import com.example.workouttracker.domain.checklist.isCompleteFor
import com.example.workouttracker.domain.supplement.testSupplementsChecklist
import com.example.workouttracker.domain.compatibility.ReadingTimeId
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.TimeZone
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import com.example.workouttracker.domain.compatibility.isLoggedFor
import com.example.workouttracker.domain.compatibility.isCareSessionComplete

class MoEvaluationTest {
    private val first = LocalDate.parse("2026-06-03")
    private val last = LocalDate.parse("2026-10-25")
    // A second timer beside Reading, so a view's weeks are proven never to take another's minutes.
    private val state = generatedHistory(seed = 11, first, last, adherence = 0.7).let { history ->
        history.copy(timeLogs = history.timeLogs + history.timeLogs.map { it.copy(viewId = Piano, minutes = (it.minutes ?: 0) + 1) })
    }
    private val holidays = HolidaySchedule(listOf(DayRange(LocalDate.parse("2026-09-17"), LocalDate.parse("2026-09-23"))))
    private lateinit var zone: TimeZone

    // Eating cutoffs are judged in the default zone, and the history is written in UTC.
    @Before fun pinZone() {
        zone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    @After fun restoreZone() = TimeZone.setDefault(zone)

    @Test fun theIndexAnswersEveryDayExactlyAsTheFeaturePredicatesOverAllRecords() {
        val records = MoRecordIndex(state, HolidaySchedule())
        generateSequence(first.minusDays(2)) { it.plusDays(1) }.takeWhile { it <= last.plusDays(2) }.forEach { day ->
            val key = day.toString()
            assertEquals(key, state.recordsOf("weight").any { it.dateKey == key }, records.isWeightLogged(day))
            LegacyCareSession.entries.forEach { session ->
                assertEquals(key, state.isCareSessionComplete(key, session, HolidaySchedule()), records.isCareSessionComplete(day, session))
            }
            assertEquals(key, state.isLoggedFor(MoNeed.Care, key, HolidaySchedule()), records.isCareComplete(day))
            assertEquals(key, testSupplementsChecklist().isCompleteFor(state.checklistDays, key, HolidaySchedule()), records.isSupplementsComplete(day))
            assertEquals(key, areQuestionsLoggedOnTimeFor(state.questionEntries, EatingQuestionsId, eatingDateKeyFor(key), key), records.isEatingLoggedOnTime(day))
            val active = state.activityEntries.any { it.dateKey == key } ||
                state.sessions.any { it.dateKey == key && it.totalLoggedSets > 0 }
            assertEquals(key, active, records.isActive(day))
            if (day.dayOfWeek == DayOfWeek.MONDAY) {
                assertEquals(key, minutesInWeek(state.timeLogs, ReadingTimeId, day), records.minutesInWeek(ReadingTimeId, day))
                listOf(ReadingTimeId, Piano).forEach { view ->
                    assertEquals("$key $view", minutesInWeek(state.timeLogs, view, day), records.minutesInWeek(view, day))
                }
            }
        }
    }

    @Test fun choosingOnlyQuietsWhatIsAskedAndNeverMovesTheStreakOrTheRecords() {
        var coveredSomewhere = false
        var restingSomewhere = false
        evaluatedDays().forEach { today ->
            val evaluation = MoEvaluation(state, FeatureVisibilitySettings(), today, holidays)
            val unchosen = evaluation.mood(emptySet())
            val chosen = evaluation.mood(MoNeed.entries.toSet())
            val refs = MoNeed.entries.map { it.view.trackerRef }
            // A token covers a task only while its bank covers all of it, and a task rests only where a free day covers it.
            val covered = refs.filterTo(linkedSetOf()) { unchosen.tokensCover(it) }
            val resting = refs.filterTo(linkedSetOf()) { unchosen.canRest(it) }
            // A week's amount before its Sunday is only quieted for the day.
            val quiet = refs.filterTo(linkedSetOf()) { unchosen.canQuiet(it) }
            assertEquals("$today", unchosen.missingTrackersToday - covered, chosen.missingTrackersToday)
            assertEquals("$today", covered, chosen.chosenTrackersToday)
            assertEquals("$today", resting, chosen.restChosenTrackers)
            // Everything else is as it was: the streak, the banks and every fact read from the records.
            assertEquals("$today", unchosen.copy(
                missingTrackersToday = chosen.missingTrackersToday,
                selectedTrackerChoices = covered,
                restChosenTrackers = resting,
                quietTrackersToday = quiet,
                checkpointsSetAside = chosen.checkpointsSetAside,
                trackerMissedParts = chosen.trackerMissedParts,
                trackerWeeklyNudges = unchosen.trackerWeeklyNudges - covered - quiet,
                trackerProgress = unchosen.trackerProgress.filterNot { (ref, progress) -> ref in covered && !progress.isStarted },
            ), chosen)
            coveredSomewhere = coveredSomewhere || covered.isNotEmpty()
            restingSomewhere = restingSomewhere || resting.isNotEmpty()
        }
        assertTrue(coveredSomewhere && restingSomewhere)
    }

    /**
     * The app hands stored choices in through a check that stands before the mood. With none
     * stored, or with choices that all stand, it is the plain mood of those choices; a choice
     * that does not stand counts for nothing, as if it had not been made.
     */
    @Test fun storedChoicesThatStandGiveThePlainMoodOfThoseChoicesAndTheOthersCountForNothing() {
        val visibility = FeatureVisibilitySettings()
        var standingSomewhere = false
        var fallingSomewhere = false
        evaluatedDays().forEach { today ->
            val unchosen = moMood(state, visibility, today, holidays)
            assertEquals("$today", unchosen, moMoodWithStoredChoices(state, visibility, today, holidays))

            val standing = MoNeed.entries.filterTo(mutableSetOf()) { unchosen.tokensCover(it.view.trackerRef) || unchosen.canRest(it.view.trackerRef) || unchosen.canQuiet(it.view.trackerRef) }
            val plain = moMood(state, visibility, today, holidays, standing.mapTo(linkedSetOf()) { it.view.trackerRef })
            assertEquals("$today", plain, moMoodWithStoredChoices(state, visibility, today, holidays, MoChoices(standing.mapTo(linkedSetOf()) { it.view.trackerRef })))
            assertEquals("$today", standing.mapTo(linkedSetOf()) { it.view.trackerRef }, plain.settledByChoice)
            // Every task chosen, the ones that stand count and the rest are as if not chosen.
            assertEquals("$today", plain, moMoodWithStoredChoices(state, visibility, today, holidays, MoChoices(MoNeed.entries.mapTo(linkedSetOf()) { it.view.trackerRef })))
            assertEquals("$today", plain, MoEvaluation(state, visibility, today, holidays).moodWithStoredChoices(MoNeed.entries.toSet()))

            standingSomewhere = standingSomewhere || standing.isNotEmpty()
            fallingSomewhere = fallingSomewhere || unchosen.missingTrackersToday.any { missing -> standing.none { it.view.trackerRef == missing } }
        }
        // The history has days of both kinds: choices that stand, and a missing task whose tokens do not cover it.
        assertTrue(standingSomewhere && fallingSomewhere)
    }

    @Test fun homesSharedEvaluationGivesTheSameFreeDaysAsTheStandaloneAnswer() {
        listOf(DefaultRewardStart, LocalDate.parse("2026-12-07")).forEach { rewardStart ->
            evaluatedDays().forEach { today ->
                val visibility = only(MoNeed.Weight, MoNeed.Eating, MoNeed.Activity)
                assertEquals(
                    "$today $rewardStart",
                    moFreeDays(state, visibility, today, holidays, rewardStart),
                    MoEvaluation(state, visibility, today, holidays, rewardStart).freeDays(),
                )
            }
        }
    }

    /** Under the token rules a token or a freeze covers a task that was really not done, and never more. */
    @Test fun aCoveredTaskIsNeverARecordedOneWhateverTheRewardRules() {
        val records = MoRecordIndex(state, HolidaySchedule())
        var covered = 0
        listOf(DefaultRewardStart, LocalDate.parse("2026-07-01")).forEach { rewardStart ->
            val streak = habitStreak(state, FeatureVisibilitySettings(), last, holidays, rewardStart)
            streak.history.filter { it.date < last }.forEach { record ->
                record.coveredTrackers.forEach { ref ->
                    val need = com.example.workouttracker.domain.compatibility.InheritedNeedsByTracker.getValue(ref)
                    covered++
                    val day = record.date
                    val done = when (need) {
                        MoNeed.Weight -> records.isWeightLogged(day)
                        MoNeed.Care -> records.isCareComplete(day)
                        MoNeed.Supplements -> records.isSupplementsComplete(day)
                        MoNeed.Eating -> records.isEatingLoggedOnTime(day)
                        MoNeed.Activity -> records.isActive(day)
                        MoNeed.Reading -> records.minutesInWeek(ReadingTimeId, day.minusDays(6)) >= 60
                    }
                    assertFalse("$need on $day under rules from $rewardStart", done)
                }
            }
        }
        assertTrue("the history should exercise covered days", covered > 20)
    }

    private fun evaluatedDays() = generateSequence(LocalDate.parse("2026-09-21")) { it.plusDays(1) }
        .takeWhile { it <= LocalDate.parse("2026-10-06") }.toList()
}

private const val Piano = "piano"

package com.example.workouttracker.domain.reward

import com.example.workouttracker.domain.compatibility.EatingQuestionsId
import com.example.workouttracker.domain.settings.showingOnly
import com.example.workouttracker.domain.AppState
import com.example.workouttracker.domain.TrackerRef
import com.example.workouttracker.domain.LoggedViewKind
import com.example.workouttracker.domain.LoggedViewRef
import com.example.workouttracker.domain.checklist.AboutDay
import com.example.workouttracker.domain.checklist.WhenAway
import com.example.workouttracker.domain.holiday.DayRange
import com.example.workouttracker.domain.holiday.HolidaySchedule
import com.example.workouttracker.domain.measurement.MeasurementEdit
import com.example.workouttracker.domain.measurement.MeasurementLog
import com.example.workouttracker.domain.compatibility.WeightMeasurementId
import com.example.workouttracker.domain.measurement.applyingMeasurementEdits
import com.example.workouttracker.domain.measurement.testMadeMeasurement
import com.example.workouttracker.domain.measurement.testWeightMeasurement
import com.example.workouttracker.domain.mo.MoDayMark
import com.example.workouttracker.domain.compatibility.MoNeed
import com.example.workouttracker.domain.mo.MoStreak
import com.example.workouttracker.domain.mo.MoStreakStanding
import com.example.workouttracker.domain.mo.MoTokenProgress
import com.example.workouttracker.domain.mo.habitStreak
import com.example.workouttracker.domain.mo.moMood
import com.example.workouttracker.domain.mo.compatibility.InheritedMoEvaluation
import com.example.workouttracker.domain.questions.QuestionEntry
import com.example.workouttracker.domain.questions.QuestionEntryOrder
import com.example.workouttracker.domain.questions.QuestionsConfiguration
import com.example.workouttracker.domain.questions.QuestionsEdit
import com.example.workouttracker.domain.questions.applyingQuestionsEdits
import com.example.workouttracker.domain.questions.testMadeQuestions
import com.example.workouttracker.domain.questions.withEating
import com.example.workouttracker.domain.ref
import com.example.workouttracker.domain.trackerRef
import com.example.workouttracker.domain.settings.FeatureVisibilitySettings
import com.example.workouttracker.feature.mo.todayIsSetAside
import com.example.workouttracker.feature.statistics.StatisticsRange
import com.example.workouttracker.feature.statistics.statisticsSummary
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A questions view a person made that joins the streak is in the walk what a made measurement is,
 * by the same participant: judged from the day it joined by any entry of the day asked about,
 * covered by its own bank, never reaching back. What it has of its own is which day a due day asks
 * about: that day or the one before, and none before the view was made.
 */
class QuestionsRewardsTest {
    private val monday = LocalDate.parse("2026-10-05")
    private val sleep = testMadeQuestions(madeOn = monday)
    private val lookBack = testMadeQuestions(about = AboutDay.Yesterday, madeOn = monday)
    private val sleepRef = sleep.ref.trackerRef
    private val eatingRef = MoNeed.Eating.view.trackerRef
    private val onlyWeight = showingOnly(MoNeed.Weight)

    private fun on(day: Long): LocalDate = monday.plusDays(day)

    /** Weight logged every day of three weeks, and [view] answered for every one of them but [missed]. */
    private fun history(vararg missed: Long, view: QuestionsConfiguration = sleep): AppState = AppState(
        measurements = listOf(testWeightMeasurement()),
        measurementLogs = (0L until 21L).map { MeasurementLog(WeightMeasurementId, on(it).toString(), it, 80.0) },
        questionEntries = (0L until 21L).filterNot { it in missed }.map { QuestionEntry("e$it", view.id, on(it).toString(), it, mapOf("qn_rest" to "a3")) }.sortedWith(QuestionEntryOrder),
    ).withEating().let { it.copy(questions = it.questions + view) }

    private fun AppState.edited(vararg edits: QuestionsEdit, day: Long): AppState = applyingQuestionsEdits(sleep.id, edits.toList(), on(day))

    private fun AppState.joined(day: Long = 0): AppState = edited(QuestionsEdit.JoinRewards, day = day)

    private fun AppState.withoutTheView(): AppState = copy(questions = questions.filterNot { it.id == sleep.id }, questionEntries = questionEntries.filterNot { it.viewId == sleep.id })

    private fun streak(state: AppState, day: Long, visibility: FeatureVisibilitySettings = onlyWeight, daysOff: HolidaySchedule = HolidaySchedule()) =
        habitStreak(state, visibility, on(day), daysOff)

    private fun MoStreak.markOn(day: Long) = history.single { it.date == on(day) }.mark

    private fun MoStreak.coveredOn(day: Long): Set<TrackerRef> = history.single { it.date == on(day) }.coveredTrackers

    private fun MoStreak.sleep(): MoStreakStanding? = standings[sleepRef]

    @Test fun aViewOutsideTheStreakChangesNothingAndJoiningNeverReachesBack() {
        val without = streak(history(3, 4).withoutTheView(), 20)
        assertEquals(without, streak(history(3, 4), 20))

        val after = streak(history(3, 4).joined(day = 10), 20)
        assertEquals(without.history, after.history)
        assertEquals(3, after.sleep()?.passCapacity)
        assertEquals(InheritedMoEvaluation(history(3, 4).withoutTheView(), onlyWeight, on(20)).streak,
            InheritedMoEvaluation(history(3, 4).joined(day = 10), onlyWeight, on(20)).streak.copy(questions = emptyMap()))
    }

    /** A day is kept by its entry whatever it says: one with no answer and no note keeps it as any other does. */
    @Test fun aDayIsKeptByItsEntryWhateverItSays() {
        val empty = history(5).let { it.copy(questionEntries = (it.questionEntries + QuestionEntry("empty", sleep.id, on(5).toString(), 1)).sortedWith(QuestionEntryOrder)) }
        assertEquals(streak(history().joined(), 20), streak(empty.joined(), 20))
        // An entry of another view on the day, Eating's among them, keeps nothing for this one.
        val othersEntry = history(6).let { it.copy(questionEntries = (it.questionEntries + QuestionEntry("other", "eating", on(6).toString(), 1)).sortedWith(QuestionEntryOrder)) }
        assertEquals(setOf(sleepRef), streak(othersEntry.joined(), 20, onlyWeight).coveredOn(6))
    }

    @Test fun aGapIsCoveredFromItsOwnBankAndEverySevenKeptDaysEarnAToken() {
        val joined = history(12).joined()
        val day12 = streak(joined, 20).history.single { it.date == on(12) }
        assertEquals(MoDayMark.Protected, day12.mark)
        assertEquals(setOf(sleepRef), day12.coveredTrackers)
        // One to start, one for the 7th kept day, one spent on day 12, one for the 14th kept day.
        assertEquals(2, streak(joined, 20).sleep()?.passes)
        assertEquals(1, streak(joined, 13).sleep()?.passes)
        // Weight's bank is its own: it spent nothing on a day only the view missed.
        assertEquals(streak(history(12).withoutTheView(), 20).participantBanks, streak(joined, 20).participantBanks - sleepRef)
    }

    /**
     * About today, the walk is a measurement's: with the same days recorded, joined, left and
     * joined again, hidden, sick, and away while due or not, every day's mark and the standing are
     * what a measurement logged on those days gets.
     */
    @Test fun aboutTodayItIsJudgedDayForDayAsAMeasurementIs() {
        val waist = testMadeMeasurement()
        fun asMeasurement(missed: Set<Long>, vararg edits: Pair<Long, MeasurementEdit>): AppState = AppState(
            measurements = listOf(testWeightMeasurement(), waist),
            measurementLogs = (0L until 21L).map { MeasurementLog(WeightMeasurementId, on(it).toString(), it, 80.0) } +
                (0L until 21L).filterNot { it in missed }.map { MeasurementLog(waist.id, on(it).toString(), it, 90.0) },
        ).let { state -> edits.fold(state) { edited, (day, edit) -> edited.applyingMeasurementEdits(waist.id, listOf(edit), on(day)) } }
        fun asQuestions(missed: Set<Long>, vararg edits: Pair<Long, QuestionsEdit>): AppState =
            edits.fold(history(*missed.toLongArray())) { edited, (day, edit) -> edited.edited(edit, day = day) }

        val away = HolidaySchedule(listOf(DayRange(on(12), on(13))))
        val sick = HolidaySchedule().withSickDays(DayRange(on(12), on(12)))
        val sickAndAway = HolidaySchedule(listOf(DayRange(on(12), on(12)))).withSickDays(DayRange(on(12), on(12)))
        val missed = setOf(3L, 12L, 13L, 20L)
        listOf(HolidaySchedule(), away, sick, sickAndAway).forEach { daysOff ->
            listOf(
                asMeasurement(missed, 0L to MeasurementEdit.JoinRewards) to asQuestions(missed, 0L to QuestionsEdit.JoinRewards),
                asMeasurement(missed, 2L to MeasurementEdit.JoinRewards, 2L to MeasurementEdit.SetWhenAway(WhenAway.Excused)) to
                    asQuestions(missed, 2L to QuestionsEdit.JoinRewards, 2L to QuestionsEdit.SetWhenAway(WhenAway.Excused)),
                asMeasurement(missed, 0L to MeasurementEdit.JoinRewards, 10L to MeasurementEdit.LeaveRewards, 14L to MeasurementEdit.JoinRewards, 14L to MeasurementEdit.SetBankRules(2, 3)) to
                    asQuestions(missed, 0L to QuestionsEdit.JoinRewards, 10L to QuestionsEdit.LeaveRewards, 14L to QuestionsEdit.JoinRewards, 14L to QuestionsEdit.SetRewardRules(2, 3)),
            ).forEach { (measured, asked) ->
                (0L..20L).forEach { today ->
                    val measurement = streak(measured, today, daysOff = daysOff)
                    val questions = streak(asked, today, daysOff = daysOff)
                    assertEquals("day $today", measurement.standings[waist.ref.trackerRef], questions.sleep())
                    assertEquals("day $today", measurement.history.map { it.mark }, questions.history.map { it.mark })
                    assertEquals("day $today", measurement.history.map { waist.ref.trackerRef in it.coveredTrackers }, questions.history.map { sleepRef in it.coveredTrackers })
                    assertEquals("day $today", InheritedMoEvaluation(measured, onlyWeight, on(today), daysOff).streak.copy(measurements = emptyMap(), history = emptyList()),
                        InheritedMoEvaluation(asked, onlyWeight, on(today), daysOff).streak.copy(questions = emptyMap(), history = emptyList()))
                }
            }
        }
    }

    /** Hiding is not dated: a hidden view is out of the whole history, and shown again it is back in all of it. */
    @Test fun aHiddenViewIsOutOfTheWholeHistoryAndBackWhenShown() {
        val joined = history(12).joined()
        val hidden = onlyWeight.hiding(sleep.ref)

        val out = streak(joined, 20, hidden)
        assertEquals(MoDayMark.Perfect, out.markOn(12))
        assertNull(out.sleep())
        assertEquals(streak(history(12).withoutTheView(), 20), out)
        // A hidden measurement of its ID hides nothing of it.
        assertEquals(streak(joined, 20), streak(joined, 20, onlyWeight.hiding(LoggedViewRef(LoggedViewKind.Measurement, sleep.id))))
        assertEquals(streak(joined, 20), streak(joined, 20, hidden.forgetting(sleep.ref)))
    }

    /**
     * About yesterday, a due day is judged by the entry of the day before: the gap of a day without
     * one falls on the day after, and today is open until yesterday is answered. Periods count due
     * days, so joining asks about yesterday at once, since the view existed then.
     */
    @Test fun aboutYesterdayADueDayIsJudgedByTheEntryOfTheDayBefore() {
        val joined = history(11, view = lookBack).joined(day = 5)

        val result = streak(joined, 20)
        assertEquals(MoDayMark.Perfect, result.markOn(11))
        assertEquals(MoDayMark.Protected, result.markOn(12))
        assertEquals(setOf(lookBack.ref.trackerRef), result.coveredOn(12))
        // Today asks about yesterday, which is answered; today's own entry is asked for tomorrow.
        assertEquals(true, result.sleep()?.keptToday)
        assertEquals(true, streak(history(11, 20, view = lookBack).joined(day = 5), 20).sleep()?.keptToday)
        assertEquals(true, streak(history(19, view = lookBack).joined(day = 5), 20).sleep()?.openToday)

        // Joined on day 12, that day is due at once and asks about day 11.
        val joinedOnTheGap = streak(history(11, view = lookBack).joined(day = 12), 20)
        assertEquals(MoDayMark.Protected, joinedOnTheGap.markOn(12))
        assertEquals(setOf(lookBack.ref.trackerRef), joinedOnTheGap.coveredOn(12))
    }

    /**
     * A day before the view was made is never asked about. Made today about yesterday and joined
     * at once, it asks nothing today: its bank is on show, neither open nor kept, and no token is
     * spent. Tomorrow asks about today.
     */
    @Test fun aViewMadeTodayAboutYesterdayAsksNothingToday() {
        val made = testMadeQuestions(about = AboutDay.Yesterday, madeOn = on(10))
        val state = AppState(
            measurements = listOf(testWeightMeasurement()),
            measurementLogs = (0L until 21L).map { MeasurementLog(WeightMeasurementId, on(it).toString(), it, 80.0) },
        ).withEating().let { it.copy(questions = it.questions + made) }.joined(day = 10)

        val firstDay = streak(state, 10)
        assertEquals(MoStreakStanding(passes = 1, passCapacity = 3, nextToken = MoTokenProgress(done = 0, goal = 7), openToday = false, keptToday = false), firstDay.sleep())
        assertEquals(InheritedMoEvaluation(state.withoutTheView(), onlyWeight, on(10)).streak,
            InheritedMoEvaluation(state, onlyWeight, on(10)).streak.copy(questions = emptyMap()))

        val nextDay = streak(state, 11)
        assertEquals(true, nextDay.sleep()?.openToday)
        assertEquals(MoDayMark.Perfect, nextDay.markOn(10))
        // Day 11 without an entry for day 10 costs the token it started with; day 10 itself cost nothing.
        val later = streak(state, 13)
        val madeRef = made.ref.trackerRef
        assertEquals(emptySet<TrackerRef>(), later.coveredOn(10))
        assertEquals(setOf(madeRef), later.coveredOn(11))
    }

    /**
     * The day a view switches to being about yesterday would ask for the entry the day before
     * already asked for, so it is not judged. From the next day on each due day asks about the day
     * before it.
     */
    @Test fun theDayItSwitchesToYesterdayIsNotJudged() {
        // Day 10 has no entry: about today throughout, that is day 10's gap.
        assertEquals(setOf(sleepRef), streak(history(10).joined(), 20).coveredOn(10))

        val switched = history(10).joined().edited(QuestionsEdit.SetAbout(AboutDay.Yesterday), day = 10)
        val result = streak(switched, 20)
        assertEquals(emptySet<TrackerRef>(), result.coveredOn(10))
        assertEquals(MoDayMark.Perfect, result.markOn(10))
        assertEquals(setOf(sleepRef), result.coveredOn(11))
        // Days before the switch keep the rule they had: day 9 asked about itself.
        assertEquals(setOf(sleepRef), streak(history(9).joined().edited(QuestionsEdit.SetAbout(AboutDay.Yesterday), day = 10), 20).coveredOn(9))
    }

    /**
     * About yesterday, sick and away days are read by the day asked about, as a look-back
     * checklist reads them: a day that asks about a sick day, or about an away day the view is not
     * due on, is not judged. A due day that is itself away asks as ever.
     */
    @Test fun aboutYesterdaySickAndAwayDaysAreReadByTheDayAskedAbout() {
        val joined = history(11, view = lookBack).joined(day = 5)
        val notDue = joined.edited(QuestionsEdit.SetWhenAway(WhenAway.Excused), day = 5)
        val sickOn11 = HolidaySchedule().withSickDays(DayRange(on(11), on(11)))
        val awayOn11 = HolidaySchedule(listOf(DayRange(on(11), on(11))))
        val awayOn12 = HolidaySchedule(listOf(DayRange(on(12), on(12))))

        // Day 12 asks about day 11, which was sick: nothing is asked and no token is spent.
        streak(joined, 20, daysOff = sickOn11).let { result ->
            assertEquals(MoDayMark.Sick, result.markOn(11))
            assertEquals(MoDayMark.Perfect, result.markOn(12))
            assertEquals(emptySet<TrackerRef>(), result.coveredOn(12))
        }
        // Day 11 away and not due: day 12 asks nothing. Still due, it asks as on any day.
        assertEquals(emptySet<TrackerRef>(), streak(notDue, 20, daysOff = awayOn11).coveredOn(12))
        assertEquals(setOf(lookBack.ref.trackerRef), streak(joined, 20, daysOff = awayOn11).coveredOn(12))
        // Away on the due day itself changes nothing: the day asked about was an ordinary one.
        assertEquals(setOf(lookBack.ref.trackerRef), streak(notDue, 20, daysOff = awayOn12).coveredOn(12))
    }

    /** Only Mo's tasks can be set aside, so a joined questions view left open keeps today from looking covered. */
    @Test fun anOpenJoinedViewKeepsTodayFromLookingSetAside() {
        fun AppState.withoutTodaysWeight() = copy(measurementLogs = measurementLogs.filterNot { it.dateKey == on(20).toString() })
        fun setAside(state: AppState) = moMood(state.withoutTodaysWeight(), onlyWeight, on(20), chosenTrackers = setOf(MoNeed.Weight.view.trackerRef)).todayIsSetAside

        assertEquals(true, streak(history(20).joined(), 20).sleep()?.openToday)
        assertFalse(setAside(history(20).joined()))
        // Answered, or outside the streak, it holds nothing open.
        assertTrue(setAside(history().joined()))
        assertTrue(setAside(history(20)))
    }

    /**
     * Eating beside a made view: its entries, its bank, its days and everything Mo and Statistics
     * make of it are what they are without the view, whether the view is outside the streak or in
     * it and answered. Outside the streak the view changes nothing at all; in it, the streak gains
     * its standing and Mo one more task, and what he makes of Eating and Weight stays.
     */
    @Test fun eatingIsWhatItWasBesideAMadeView() {
        val eatingToo = showingOnly(MoNeed.Weight, MoNeed.Eating)
        // Eating reflected on for most days, written the morning after; days 6 and 13 are missing.
        val reflections = (0L until 20L).filterNot { it == 6L || it == 13L }.map { day ->
            QuestionEntry("eat$day", "eating", on(day).toString(), on(day + 1).atTime(8, 0).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli(), mapOf("quality" to "good"))
        }
        fun AppState.withEatingDays() = copy(questionEntries = (questionEntries + reflections).sortedWith(QuestionEntryOrder))
        val alone = history().withoutTheView().withEatingDays()
        val outside = history(3, 9).withEatingDays()
        val joined = history().joined(day = 2).withEatingDays()

        (3L..20L).forEach { today ->
            val without = moMood(alone, eatingToo, on(today))
            assertEquals("day $today", InheritedMoEvaluation(alone, eatingToo, on(today)).mood,
                InheritedMoEvaluation(outside, eatingToo, on(today)).mood)
            val with = moMood(joined, eatingToo, on(today))
            assertEquals("day $today", setOf(sleepRef), with.streak.standings.keys.filter { it.type.value == "questions" && it != eatingRef }.toSet())
            assertTrue("day $today", sleepRef in with.trackerOrder)
            listOf(eatingRef, MoNeed.Weight.view.trackerRef).forEach { ref ->
                assertEquals("day $today", without.streak.standings[ref], with.streak.standings[ref])
                assertEquals("day $today", without.trackerWeekProgress[ref], with.trackerWeekProgress[ref])
                assertEquals("day $today", without.trackerNeglect[ref], with.trackerNeglect[ref])
                assertEquals("day $today", ref in without.keptTrackersToday, ref in with.keptTrackersToday)
                assertEquals("day $today", ref in without.missingTrackersToday, ref in with.missingTrackersToday)
                assertEquals("day $today", ref in without.freeTrackersToday, ref in with.freeTrackersToday)
            }
            assertEquals("day $today", without.streak.days, with.streak.days)
            assertEquals("day $today", without.streak.freezes, with.streak.freezes)
        }
        assertEquals(alone.questionEntries.filter { it.viewId == EatingQuestionsId }, joined.questionEntries.filter { it.viewId == EatingQuestionsId })
        StatisticsRange.entries.forEach { range ->
            val inherited = statisticsSummary(alone, on(20).toString(), range, eatingToo, streak(alone, 20, eatingToo).history)
            val allTrackers = statisticsSummary(joined, on(20).toString(), range, eatingToo, streak(alone, 20, eatingToo).history)
            assertEquals(inherited, allTrackers.copy(consistency = allTrackers.consistency.filterNot { it.ref == sleep.ref.trackerRef }))
        }
    }

    /** A measurement's standings come before a questions view's, each kind in the order it was made, and one ID under both kinds is two banks. */
    @Test fun measurementsAndQuestionsViewsEachHaveTheirOwnBankUnderOneId() {
        val twin = testMadeMeasurement(id = sleep.id, label = "Twin")
        val mood = testMadeQuestions(id = "qst_m00d", label = "Mood", madeOn = monday)
        val state = history(12).joined().let { it.copy(measurements = it.measurements + twin, questions = listOf(it.questions.first(), mood) + it.questions.drop(1)) }
            .applyingMeasurementEdits(twin.id, listOf(MeasurementEdit.JoinRewards), on(15))
            .applyingQuestionsEdits(mood.id, listOf(QuestionsEdit.JoinRewards), on(18))

        val result = streak(state, 20)

        val twinRef = twin.ref.trackerRef
        val moodRef = mood.ref.trackerRef
        assertEquals(listOf(twinRef, moodRef, sleepRef), result.standings.keys.filter { it == twinRef || it == moodRef || it == sleepRef })
        // The view's entries keep nothing for the measurement of its ID, which was never logged.
        assertEquals(true, result.standings[twinRef]?.openToday)
        assertEquals(true, result.sleep()?.keptToday)
        assertEquals(setOf(sleepRef), result.coveredOn(12))
    }
}

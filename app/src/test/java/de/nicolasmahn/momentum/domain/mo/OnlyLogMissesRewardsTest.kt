package de.nicolasmahn.momentum.domain.mo

import de.nicolasmahn.momentum.domain.compatibility.missed

import de.nicolasmahn.momentum.domain.compatibility.kept

import de.nicolasmahn.momentum.domain.compatibility.history

import de.nicolasmahn.momentum.domain.compatibility.mood

import de.nicolasmahn.momentum.domain.compatibility.MoNeed

import de.nicolasmahn.momentum.domain.trackerRef
import de.nicolasmahn.momentum.domain.TrackerRef

import de.nicolasmahn.momentum.domain.settings.showingOnly
import de.nicolasmahn.momentum.app.featureProgressOn
import de.nicolasmahn.momentum.app.featuresLoggedOn
import de.nicolasmahn.momentum.app.MoSeen
import de.nicolasmahn.momentum.app.featureTicksOn
import de.nicolasmahn.momentum.domain.AppState
import de.nicolasmahn.momentum.domain.DayProgress
import de.nicolasmahn.momentum.domain.compatibility.LegacyCareAction
import de.nicolasmahn.momentum.domain.compatibility.CareChecklistId
import de.nicolasmahn.momentum.domain.compatibility.LegacyCareSession
import de.nicolasmahn.momentum.domain.care.careState
import de.nicolasmahn.momentum.domain.care.dueCareActions
import de.nicolasmahn.momentum.domain.checklist.ChecklistDay
import de.nicolasmahn.momentum.domain.checklist.ChecklistEdit
import de.nicolasmahn.momentum.domain.checklist.applyEdit
import de.nicolasmahn.momentum.domain.checklist.tickOf
import de.nicolasmahn.momentum.domain.holiday.HolidaySchedule
import de.nicolasmahn.momentum.domain.measurement.asMeasurementLogs
import de.nicolasmahn.momentum.domain.reminder.ReminderSettings
import de.nicolasmahn.momentum.domain.compatibility.StandardSupplementIds
import de.nicolasmahn.momentum.domain.compatibility.SupplementsChecklistId
import de.nicolasmahn.momentum.domain.compatibility.SupplementsCheckpointId
import de.nicolasmahn.momentum.domain.supplement.supplementsState
import de.nicolasmahn.momentum.domain.checklist.ChecklistTarget
import de.nicolasmahn.momentum.domain.checklist.checklist
import de.nicolasmahn.momentum.domain.measurement.MeasurementRecord
import de.nicolasmahn.momentum.feature.mo.canSetAside
import de.nicolasmahn.momentum.feature.mo.tokensSetAside
import de.nicolasmahn.momentum.feature.statistics.StatisticsRange
import de.nicolasmahn.momentum.feature.statistics.statisticsSummary
import de.nicolasmahn.momentum.domain.care.openCareItems
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Care with brushing and hair foam switched to only log misses on a Monday: the walk, Mo's ledger
 * and mood, Home and Statistics read the same days, and an assumed day is kept without ever being
 * counted or celebrated as recorded.
 */
class OnlyLogMissesRewardsTest {
    private val monday = LocalDate.parse("2026-10-12")
    private val sunday = monday.minusDays(1)
    private val onlyCare = showingOnly(MoNeed.Care)
    private val onlySupplements = showingOnly(MoNeed.Supplements)
    private val careRef = MoNeed.Care.view.trackerRef
    private val supplementsRef = MoNeed.Supplements.view.trackerRef
    private fun day(offset: Int): LocalDate = monday.plusDays(offset.toLong())

    private val dailyCare = listOf(LegacyCareAction.TOOTHBRUSHING, LegacyCareAction.HAIR_FOAM)

    /** Care done in full on the Sunday before, so the task has begun, and its daily actions logging only misses from [switchedOn]. */
    private fun care(switchedOn: LocalDate = monday): AppState =
        dailyCare.fold(careState(*(dueCareActions(sunday.toString(), LegacyCareSession.MORNING) + dueCareActions(sunday.toString(), LegacyCareSession.EVENING)))) { state, action ->
            state.applyEdit(CareChecklistId, ChecklistEdit.SetItemExceptionsOnly(action.storageId, true), switchedOn)
        }

    private fun AppState.withCareDay(date: LocalDate, ticks: Set<String> = emptySet(), misses: Set<String> = emptySet()) =
        copy(checklistDays = checklistDays + ChecklistDay(CareChecklistId, date.toString(), 9L, ticks, misses))

    @Test fun anUntouchedDayIsKeptInTheWalkOnHomeAndInMosHistory() {
        val state = care()
        val friday = day(4)
        val ledger = MoLedger.of(state, onlyCare, friday).history(careRef)
        val walk = habitStreak(state, onlyCare, friday).history.associateBy { it.date }
        (0..3).forEach { offset ->
            assertEquals("day $offset", MoStatus.Kept, ledger[day(offset)])
            assertEquals("day $offset", MoDayMark.Perfect, walk.getValue(day(offset)).mark)
        }
        assertEquals(listOf(careRef), moMood(state, onlyCare, friday).keptTrackersToday)
        // Home says the day is kept, never done.
        val today = friday.toString()
        assertEquals(DayProgress(done = 2, parts = 2, assumed = true), featureProgressOn(state, today, HolidaySchedule())[MoNeed.Care])
        assertTrue(MoNeed.Care in featuresLoggedOn(state, today, HolidaySchedule()))
    }

    /** Two misses at one session are one gap: the day is not kept, yet nothing is owed and nobody waits for it. */
    @Test fun aMissIsAGapThatNothingWaitsFor() {
        val tuesday = day(1)
        val missed = care().withCareDay(tuesday, misses = dailyCare.map { tickOf(LegacyCareSession.MORNING.storageId, it.storageId) }.toSet())
        assertEquals(1, MoRecordIndex(missed, HolidaySchedule()).progress(missed.checklist(CareChecklistId)!!, tuesday).gaps)
        val walk = habitStreak(missed, onlyCare, day(2)).history.associateBy { it.date }
        assertNotEquals(MoDayMark.Perfect, walk.getValue(tuesday).mark)
        assertEquals(MoStatus.Missed, MoLedger.of(missed, onlyCare, day(2)).history(careRef)[tuesday])

        val mood = moMood(missed, onlyCare, tuesday)
        assertEquals(listOf(careRef), mood.missingTrackersToday)
        // The morning is still open, for a token to cover, and owes nothing.
        assertEquals(listOf(de.nicolasmahn.momentum.domain.CheckpointPart("morning")), mood.trackerStillOpen(careRef))
        assertEquals(emptyList<Any>(), mood.trackerStillOwed(careRef))
        assertEquals(emptyList<MoObligation>(), mood.trackerObligations(ReminderSettings())[careRef].orEmpty())
        assertEquals(emptyList<TrackerRef>(), moOverdueTrackers(mood, ReminderSettings(), java.time.LocalTime.of(23, 0)))
        assertEquals(emptyList<Any>(), missed.openCareItems(tuesday.toString(), LegacyCareSession.MORNING, HolidaySchedule()))
    }

    /** A token covers each session a miss leaves open, as the walk spends one for each gap, though nothing is owed there. */
    @Test fun aTokenCoversEachSessionAMissLeavesOpen() {
        val tuesday = day(1)
        val bothMissed = care().withCareDay(tuesday, misses = LegacyCareSession.entries.map { tickOf(it.storageId, LegacyCareAction.TOOTHBRUSHING.storageId) }.toSet())
        assertEquals(2, MoRecordIndex(bothMissed, HolidaySchedule()).progress(bothMissed.checklist(CareChecklistId)!!, tuesday).gaps)
        val mood = moMood(bothMissed, onlyCare, tuesday)
        assertEquals(LegacyCareSession.entries.map { de.nicolasmahn.momentum.domain.CheckpointPart(it.storageId) }, mood.trackerStillOpen(careRef))
        assertEquals(emptyList<Any>(), mood.trackerStillOwed(careRef))
        fun withTokens(count: Int): MoMood {
            val standing = requireNotNull(mood.streak.standings[careRef])
            return mood.copy(streak = mood.streak.copy(standings = mood.streak.standings + (careRef to standing.copy(passes = count))))
        }
        assertFalse("one token cannot cover two gaps", withTokens(1).canSetAside(careRef))
        assertTrue(withTokens(2).canSetAside(careRef))
        assertEquals(2, withTokens(2).copy(selectedTrackerChoices = setOf(careRef)).tokensSetAside(careRef))
    }

    /**
     * Once an item that logs only misses is due, Supplements' one part is done as the walk keeps
     * the day, so a miss is never outweighed by a tick; without one, any tick still counts, as ever.
     */
    @Test fun aMissIsNeverOutweighedByATickOnHome() {
        val today = monday.toString()
        val vitaminAssumed = supplementsState().applyEdit(SupplementsChecklistId, ChecklistEdit.SetItemExceptionsOnly(StandardSupplementIds.VITAMIN_D, true), monday)
        val taken = setOf(StandardSupplementIds.MULTIVITAMIN, StandardSupplementIds.CREATINE).map { tickOf(SupplementsCheckpointId, it) }.toSet()
        val missedVitamin = vitaminAssumed.copy(checklistDays = listOf(ChecklistDay(SupplementsChecklistId, today, 1L, taken, setOf(tickOf(SupplementsCheckpointId, StandardSupplementIds.VITAMIN_D)))))
        assertEquals(1, MoRecordIndex(missedVitamin, HolidaySchedule()).progress(missedVitamin.checklist(SupplementsChecklistId)!!, monday).gaps)
        assertEquals(DayProgress(done = 0, parts = 1), featureProgressOn(missedVitamin, today, HolidaySchedule())[MoNeed.Supplements])
        assertFalse(MoNeed.Supplements in featuresLoggedOn(missedVitamin, today, HolidaySchedule()))
        val assumedVitamin = vitaminAssumed.copy(checklistDays = listOf(ChecklistDay(SupplementsChecklistId, today, 1L, taken)))
        assertEquals(DayProgress(done = 1, parts = 1), featureProgressOn(assumedVitamin, today, HolidaySchedule())[MoNeed.Supplements])

        val creatineOnly = supplementsState().copy(checklistDays = listOf(ChecklistDay(SupplementsChecklistId, today, 1L, setOf(tickOf(SupplementsCheckpointId, StandardSupplementIds.CREATINE)))))
        assertTrue("without only misses any tick counts, as it always has", MoNeed.Supplements in featuresLoggedOn(creatineOnly, today, HolidaySchedule()))
    }

    /**
     * Mo acts out a change that keeps the day by adding a tick, never a day nobody touched, a miss
     * taken back or a settings change; a view without ticks is acted out when newly logged, as ever.
     */
    @Test fun onlyATickThatKeepsTheDayIsActedOut() {
        val today = monday.toString()
        fun MoSeen.after(state: AppState, dayKey: String = today) = seeing(dayKey, featuresLoggedOn(state, dayKey, HolidaySchedule()), featureTicksOn(state, dayKey, HolidaySchedule()))
        fun seen(state: AppState, dayKey: String = today) = MoSeen(dayKey, featuresLoggedOn(state, dayKey, HolidaySchedule()), featureTicksOn(state, dayKey, HolidaySchedule()))

        // An untouched day kept by assumption, a miss and the miss taken back: nothing to act out.
        val ids = listOf(StandardSupplementIds.MULTIVITAMIN, StandardSupplementIds.CREATINE, StandardSupplementIds.VITAMIN_D)
        val assumed = ids.fold(supplementsState()) { state, id -> state.applyEdit(SupplementsChecklistId, ChecklistEdit.SetItemExceptionsOnly(id, true), monday) }
        val missed = assumed.copy(checklistDays = listOf(ChecklistDay(SupplementsChecklistId, today, 1L, emptySet(), setOf(tickOf(SupplementsCheckpointId, StandardSupplementIds.CREATINE)))))
        assertEquals(null, seen(assumed).after(missed).after(assumed).celebrating)

        // Creatine still ticks: its tick keeps the day, and that is acted out until Mo has done so.
        val creatineTicks = ids.filter { it != StandardSupplementIds.CREATINE }
            .fold(supplementsState()) { state, id -> state.applyEdit(SupplementsChecklistId, ChecklistEdit.SetItemExceptionsOnly(id, true), monday) }
        val ticked = creatineTicks.copy(checklistDays = listOf(ChecklistDay(SupplementsChecklistId, today, 1L, setOf(tickOf(SupplementsCheckpointId, StandardSupplementIds.CREATINE)))))
        val earned = seen(creatineTicks).after(ticked)
        assertEquals(MoNeed.Supplements, earned.celebrating)
        assertEquals(MoNeed.Supplements, earned.after(ticked).celebrating)
        assertEquals(null, earned.copy(pending = emptyList()).after(ticked).celebrating)
        // A tick taken away takes the act away with it.
        assertEquals(null, earned.after(creatineTicks).celebrating)

        // Care on Wednesday: the brushing ticked before the switch, the evening's foam missed, then the miss taken back.
        val wednesday = day(2).toString()
        val brushed = setOf(tickOf(LegacyCareSession.MORNING.storageId, LegacyCareAction.TOOTHBRUSHING.storageId))
        val foamMissed = care().withCareDay(day(2), brushed, setOf(tickOf(LegacyCareSession.EVENING.storageId, LegacyCareAction.HAIR_FOAM.storageId)))
        val missTakenBack = care().withCareDay(day(2), brushed)
        assertTrue(MoNeed.Care in featuresLoggedOn(missTakenBack, wednesday, HolidaySchedule()))
        assertEquals(null, seen(foamMissed, wednesday).after(missTakenBack, wednesday).celebrating)

        // On Tuesday one save takes back the evening's brushing miss and ticks the cleaning, due only
        // on Saturdays: the day is kept by the miss taken back, and the tick answered nothing owed.
        val tuesday = day(1).toString()
        val brushingMissed = care().withCareDay(day(1), misses = setOf(tickOf(LegacyCareSession.EVENING.storageId, LegacyCareAction.TOOTHBRUSHING.storageId)))
        val offScheduleTick = care().withCareDay(day(1), ticks = setOf(tickOf(LegacyCareSession.EVENING.storageId, LegacyCareAction.CLEAN_BETWEEN_TEETH.storageId)))
        assertTrue(MoNeed.Care in featuresLoggedOn(offScheduleTick, tuesday, HolidaySchedule()))
        assertEquals(null, seen(brushingMissed, tuesday).after(offScheduleTick, tuesday).celebrating)

        // Saturday also asks for the between-teeth cleaning, ticked as ever.
        val saturday = day(5).toString()
        val cleaning = tickOf(LegacyCareSession.EVENING.storageId, LegacyCareAction.CLEAN_BETWEEN_TEETH.storageId)
        val owed = care().withCareDay(day(5), brushed)
        // Switched to only log misses, the cleaning keeps the day by a settings change, which is never acted out.
        val switched = owed.applyEdit(CareChecklistId, ChecklistEdit.SetItemExceptionsOnly(LegacyCareAction.CLEAN_BETWEEN_TEETH.storageId, true), day(5))
        assertTrue(MoNeed.Care in featuresLoggedOn(switched, saturday, HolidaySchedule()))
        assertEquals(null, seen(owed, saturday).after(switched, saturday).celebrating)
        // Ticking the cleaning is what keeps the day, and that is acted out.
        val cleaned = care().withCareDay(day(5), brushed + cleaning)
        assertEquals(MoNeed.Care, seen(owed, saturday).after(cleaned, saturday).celebrating)
        assertEquals(DayProgress(done = 2, parts = 2, assumed = false), featureProgressOn(cleaned, saturday, HolidaySchedule())[MoNeed.Care])

        // Weight has no ticks: newly logged, it is acted out as ever, and a new day starts afresh.
        val weighed = AppState(measurementLogs = listOf(MeasurementRecord(today, 1L, 80.0)).asMeasurementLogs("weight"))
        assertEquals(MoNeed.Weight, seen(AppState()).after(weighed).celebrating)
        assertEquals(null, seen(AppState()).after(weighed, monday.plusDays(1).toString()).celebrating)
    }

    /** Switched on and changed to a week target, or removed, on the same day, an item never started a task with no record. */
    @Test fun anItemSwitchedOnAndChangedTheSameDayStartsNothing() {
        val friday = day(4)
        val switchedOn = supplementsState().applyEdit(SupplementsChecklistId, ChecklistEdit.SetItemExceptionsOnly(StandardSupplementIds.CREATINE, true), monday)
        listOf(
            switchedOn.applyEdit(SupplementsChecklistId, ChecklistEdit.SetItemTarget(StandardSupplementIds.CREATINE, ChecklistTarget.PerWeek(times = 3)), monday),
            switchedOn.applyEdit(SupplementsChecklistId, ChecklistEdit.RemoveItem(StandardSupplementIds.CREATINE), monday),
        ).forEach { changed ->
            assertEquals(emptyMap<LocalDate, MoStatus>(), MoLedger.of(changed, onlySupplements, friday).history(supplementsRef))
            assertEquals(emptyList<MoDayRecord>(), habitStreak(changed, onlySupplements, friday).history.filter { it.mark != MoDayMark.NothingDue })
        }
        assertEquals(monday, MoLedger.of(switchedOn, onlySupplements, friday).history(supplementsRef).keys.min())
    }

    /** Switching it on or off starts on its day: every earlier day keeps the judgment it had. */
    @Test fun switchingOnOrOffNeverReJudgesAnEarlierDay() {
        val week = (1..7).filter { it != 3 }.fold(careState()) { state, back ->
            val date = monday.minusDays(back.toLong()).toString()
            state.withCareDay(monday.minusDays(back.toLong()), ticks = (dueCareActions(date, LegacyCareSession.MORNING) + dueCareActions(date, LegacyCareSession.EVENING)).map { tickOf(it.session.storageId, it.action.storageId) }.toSet())
        }
        val switched = dailyCare.fold(week) { state, action -> state.applyEdit(CareChecklistId, ChecklistEdit.SetItemExceptionsOnly(action.storageId, true), monday) }
        val friday = day(4)
        fun before(state: AppState) = habitStreak(state, onlyCare, friday).history.filter { it.date < monday }
        assertEquals(before(week), before(switched))
        assertEquals(MoStatus.Missed, MoLedger.of(switched, onlyCare, friday).history(careRef)[monday.minusDays(3)])

        val off = dailyCare.fold(switched) { state, action -> state.applyEdit(CareChecklistId, ChecklistEdit.SetItemExceptionsOnly(action.storageId, false), day(3)) }
        val ledger = MoLedger.of(off, onlyCare, friday).history(careRef)
        assertEquals(List(3) { MoStatus.Kept }, (0..2).map { ledger[day(it)] })
        assertEquals(MoStatus.Missed, ledger[day(3)])
        assertEquals(before(week), habitStreak(off, onlyCare, friday).history.filter { it.date < monday })
    }

    /** A built-in feature with no record yet starts on the first day an item logs only misses. */
    @Test fun aFeatureWithNoRecordStartsWithItsFirstDayOfOnlyMisses() {
        val friday = day(4)
        assertEquals(emptyMap<LocalDate, MoStatus>(), MoLedger.of(supplementsState(), onlySupplements, friday).history(supplementsRef))
        val ids = listOf(StandardSupplementIds.MULTIVITAMIN, StandardSupplementIds.CREATINE, StandardSupplementIds.VITAMIN_D)
        val switched = ids.fold(supplementsState()) { state, id -> state.applyEdit(SupplementsChecklistId, ChecklistEdit.SetItemExceptionsOnly(id, true), monday) }
        val history = MoLedger.of(switched, onlySupplements, friday).history(supplementsRef)
        assertEquals(monday, history.keys.min())
        assertEquals(List(4) { MoStatus.Kept }, (0..3).map { history[day(it)] })
        assertEquals(List(4) { MoDayMark.Perfect }, habitStreak(switched, onlySupplements, friday).history.filter { it.date in monday..day(3) }.map { it.mark })
    }

    /** Statistics count what the rule kept, as Mo does: a day nothing was marked missed on is kept, a logged miss is not. */
    @Test fun statisticsCountAnAssumedDayAsKeptAndALoggedMissAsNot() {
        val friday = day(4)
        // Every week's judged days, this one's included, which the row's number leaves for Sunday.
        fun row(state: AppState) = statisticsSummary(state, friday.toString(), StatisticsRange.FourWeeks).consistency
            .single { it.ref == MoNeed.Care.view.trackerRef }.weeks.let { weeks -> weeks.sumOf { it.kept } to weeks.sumOf { it.counted } }
        assertEquals("Sunday recorded, Monday to Friday assumed", 6 to 6, row(care()))
        val missed = care().withCareDay(day(1), misses = setOf(tickOf(LegacyCareSession.MORNING.storageId, LegacyCareAction.TOOTHBRUSHING.storageId)))
        assertEquals(5 to 6, row(missed))
    }

    /** A Monday item switched on on Thursday and off on Friday was never due with only misses on, so it starts no task. */
    @Test fun aSwitchWhileTheItemIsNeverDueStartsNothing() {
        val thursday = day(3)
        val mondays = supplementsState().applyEdit(SupplementsChecklistId, ChecklistEdit.SetItemTarget(StandardSupplementIds.CREATINE, ChecklistTarget.Weekdays(setOf(java.time.DayOfWeek.MONDAY))), monday)
            .applyEdit(SupplementsChecklistId, ChecklistEdit.SetItemExceptionsOnly(StandardSupplementIds.CREATINE, true), thursday)
        val off = mondays.applyEdit(SupplementsChecklistId, ChecklistEdit.SetItemExceptionsOnly(StandardSupplementIds.CREATINE, false), day(4))
        val later = day(10)
        assertEquals(emptyMap<LocalDate, MoStatus>(), MoLedger.of(off, onlySupplements, later).history(supplementsRef))
        assertEquals(emptyList<MoDayRecord>(), habitStreak(off, onlySupplements, later).history.filter { it.mark != MoDayMark.NothingDue })
        // Left on, the task starts on the next Monday, the first day it is due so.
        assertEquals(day(7), MoLedger.of(mondays, onlySupplements, later).history(supplementsRef).keys.min())
    }
}

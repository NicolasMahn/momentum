package com.example.workouttracker.feature.checklist

import com.example.workouttracker.domain.checklist.recordableOn
import com.example.workouttracker.domain.checklist.removeChecklistDay
import com.example.workouttracker.domain.checklist.ticks
import com.example.workouttracker.domain.checklist.DayRecord
import com.example.workouttracker.domain.checklist.setDayRecord
import com.example.workouttracker.domain.checklist.tickOf
import com.example.workouttracker.domain.supplement.AllSupplementIds
import com.example.workouttracker.domain.compatibility.StandardSupplementIds
import com.example.workouttracker.domain.supplement.YearRoundSupplementIds
import com.example.workouttracker.domain.supplement.supplementDay
import com.example.workouttracker.domain.supplement.supplementsState
import com.example.workouttracker.domain.supplement.testSupplementsChecklist
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import com.example.workouttracker.domain.checklist.WhenAway
import com.example.workouttracker.domain.holiday.DayRange
import com.example.workouttracker.domain.holiday.HolidaySchedule
import com.example.workouttracker.domain.compatibility.LegacyCareAction
import com.example.workouttracker.domain.compatibility.CareChecklistId
import com.example.workouttracker.domain.care.CareDone
import com.example.workouttracker.domain.compatibility.LegacyCareSession
import com.example.workouttracker.domain.compatibility.careChecklist
import com.example.workouttracker.domain.care.careState
import com.example.workouttracker.domain.checklist.ChecklistEdit
import com.example.workouttracker.domain.checklist.ChecklistTarget
import com.example.workouttracker.domain.checklist.applyEdit
import com.example.workouttracker.domain.AppState
import com.example.workouttracker.domain.checklist.AboutDay
import com.example.workouttracker.domain.checklist.ChecklistConfiguration
import com.example.workouttracker.domain.checklist.ChecklistDay
import com.example.workouttracker.domain.checklist.ChecklistItem
import com.example.workouttracker.domain.checklist.Checkpoint
import com.example.workouttracker.domain.checklist.CheckpointTime
import com.example.workouttracker.domain.checklist.Dated
import com.example.workouttracker.domain.checklist.ViewHue
import com.example.workouttracker.domain.checklist.ViewIcon

/** The form and calendar models of a checklist screen, shown with Supplements. */
class ChecklistModelsTest {
    private val supplements = testSupplementsChecklist()

    @Test
    fun aNewDayStartsWithNothingTickedAndNothingToSave() {
        val selection = checklistSelection(supplementsState(), supplements, "2026-09-16", HolidaySchedule())

        assertEquals(ticks(YearRoundSupplementIds), selection.dueTicks)
        assertTrue(selection.selected.ticks.isEmpty())
        assertFalse(selection.hasPersistedRecord)
        assertFalse(selection.hasChanges)
    }

    @Test
    fun historicalAndEmptyDatesHydrateIndependentSelections() {
        val state = supplementsState(
            supplementDay("2026-09-15", 1L, setOf(StandardSupplementIds.CREATINE)),
            supplementDay("2026-09-16", 2L, YearRoundSupplementIds),
        )

        val historical = checklistSelection(state, supplements, "2026-09-15", HolidaySchedule())
        val empty = checklistSelection(state, supplements, "2026-09-14", HolidaySchedule())

        assertEquals(ticks(setOf(StandardSupplementIds.CREATINE)), historical.selected.ticks)
        assertTrue(historical.hasPersistedRecord)
        assertFalse(historical.hasChanges)
        assertTrue(empty.selected.ticks.isEmpty())
        assertFalse(empty.hasPersistedRecord)
    }

    @Test
    fun aRecordedTickThatIsNotDueIsNotPickedButTheRecordStillCounts() {
        val state = supplementsState(supplementDay("2027-04-01", 1L, setOf(StandardSupplementIds.VITAMIN_D)))
        val selection = checklistSelection(state, supplements, "2027-04-01", HolidaySchedule())

        assertTrue(selection.selected.ticks.isEmpty())
        assertTrue(selection.hasPersistedRecord)
    }

    @Test
    fun togglesProduceSingleAndEmptyDraftsWithoutChangingPersistedState() {
        val initial = checklistSelection(supplementsState(), supplements, "2026-09-16", HolidaySchedule())
        val creatine = tickOf("midday", StandardSupplementIds.CREATINE)

        val single = initial.toggle(creatine)
        val empty = single.toggle(creatine)

        assertEquals(setOf(creatine), single.selected.ticks)
        assertTrue(empty.selected.ticks.isEmpty())
        assertTrue(initial.persisted.ticks.isEmpty())
    }

    @Test
    fun winterOffersVitaminDAndSummerDoesNot() {
        fun due(dateKey: String) = checklistSelection(supplementsState(), supplements, dateKey, HolidaySchedule()).dueTicks
        assertEquals(ticks(AllSupplementIds), due("2026-10-01"))
        assertEquals(ticks(AllSupplementIds), due("2027-03-31"))
        assertEquals(ticks(YearRoundSupplementIds), due("2027-04-01"))
        assertEquals(ticks(YearRoundSupplementIds), due("2026-03-01"))
    }

    @Test
    fun backfillAndCorrectionTargetOnlyTheSelectedHistoricalDate() {
        val today = supplementDay("2026-09-17", 1L, YearRoundSupplementIds)
        val initial = supplementsState(today)

        val backfilled = setDayRecord(initial, supplements, "2026-09-15", DayRecord(ticks(setOf(StandardSupplementIds.MULTIVITAMIN))), 2L)
        val corrected = removeChecklistDay(backfilled, supplements.id, "2026-09-15")

        assertEquals(setOf("2026-09-15", "2026-09-17"), backfilled.checklistDays.mapTo(mutableSetOf()) { it.dateKey })
        assertEquals(listOf(today), corrected.checklistDays)
    }

    @Test
    fun historyCoversFourWholeWeeksAcrossMonthBoundaries() {
        // 2026-03-02 is a Monday: the last row holds today and six days still to come.
        val days = checklistHistory(supplementsState(), supplements, "2026-03-02", HolidaySchedule())

        assertEquals(28, days.size)
        assertEquals("2026-02-09", days.first().dateKey)
        assertEquals("2026-03-02", days[21].dateKey)
        assertEquals("2026-03-08", days.last().dateKey)
    }

    @Test
    fun historyDistinguishesEmptyPartialAndCompleteDays() {
        val state = supplementsState(
            supplementDay("2026-03-01", 1L, setOf(StandardSupplementIds.MULTIVITAMIN)),
            supplementDay("2026-03-02", 1L, YearRoundSupplementIds),
        )

        val days = checklistHistory(state, supplements, "2026-03-02", HolidaySchedule()).associateBy { it.dateKey }

        assertEquals(ChecklistDayState.EMPTY, days.getValue("2026-02-28").state)
        assertEquals(ChecklistDayState.PARTIAL, days.getValue("2026-03-01").state)
        assertEquals(ChecklistDayState.COMPLETE, days.getValue("2026-03-02").state)
    }

    @Test
    fun recordsOutsideTheWindowAndAheadOfTodayNeverShow() {
        val state = supplementsState(
            supplementDay("2026-03-02", 1L, YearRoundSupplementIds),
            supplementDay("2026-01-01", 3L, setOf(StandardSupplementIds.MULTIVITAMIN)),
            supplementDay("2026-03-03", 4L, setOf(StandardSupplementIds.MULTIVITAMIN)),
        )

        val days = checklistHistory(state, supplements, "2026-03-02", HolidaySchedule())

        assertEquals(listOf("2026-03-02"), days.filter { it.state != ChecklistDayState.EMPTY }.map { it.dateKey })
    }

    /** Only the weekly cleaning ticked leaves the cell empty with its bar; the daily items alone color it. */
    @Test
    fun aWeekItemsTickIsAMarkAndNeverColorsTheCell() {
        val monday = java.time.LocalDate.parse("2026-10-05")
        val cleaning = tickOf(LegacyCareSession.EVENING.storageId, LegacyCareAction.CLEAN_BETWEEN_TEETH.storageId)
        // Wednesday only the cleaning, Thursday everything; the records come first, as the fixture writes the default configuration with them.
        val records = listOf(CareDone("2026-10-07", LegacyCareSession.EVENING, LegacyCareAction.CLEAN_BETWEEN_TEETH, 1L), CareDone("2026-10-08", LegacyCareSession.EVENING, LegacyCareAction.CLEAN_BETWEEN_TEETH, 2L)) +
            LegacyCareSession.entries.flatMap { session -> listOf(LegacyCareAction.TOOTHBRUSHING, LegacyCareAction.HAIR_FOAM).map { CareDone("2026-10-08", session, it, 2L) } }
        val weekly = careState(*records.toTypedArray())
            .applyEdit(CareChecklistId, ChecklistEdit.SetItemTarget(LegacyCareAction.CLEAN_BETWEEN_TEETH.storageId, ChecklistTarget.PerWeek(times = 1)), monday)
        val days = checklistHistory(weekly, weekly.careChecklist!!, "2026-10-11", HolidaySchedule()).associateBy { it.dateKey }

        assertEquals(ChecklistDayState.EMPTY, days.getValue("2026-10-07").state)
        assertEquals(setOf(cleaning), days.getValue("2026-10-07").weekTicksDone)
        assertEquals(ChecklistDayState.COMPLETE, days.getValue("2026-10-08").state)
        assertEquals(setOf(cleaning), days.getValue("2026-10-08").weekTicksDone)
        assertEquals(ChecklistDayState.EMPTY, days.getValue("2026-10-09").state)
        assertEquals(emptySet<String>(), days.getValue("2026-10-09").weekTicksDone)
    }

    /** The calendar reads the days off as judging does: an item excused while away is not due that day, and sick wins over away. */
    @Test
    fun anExcusedItemIsNotDueOnAnAwayDayInTheCalendar() {
        val monday = java.time.LocalDate.parse("2026-10-05")
        val wednesday = java.time.LocalDate.parse("2026-10-07")
        // Wednesday and Thursday only the brushing, at both sessions; the foam is excused from Monday on.
        val brushedOnly = listOf("2026-10-07", "2026-10-08").flatMap { day -> LegacyCareSession.entries.map { CareDone(day, it, LegacyCareAction.TOOTHBRUSHING, 1L) } }
        val excusedFoam = careState(*brushedOnly.toTypedArray())
            .applyEdit(CareChecklistId, ChecklistEdit.SetItemWhenAway(LegacyCareAction.HAIR_FOAM.storageId, WhenAway.Excused), monday)
        val away = HolidaySchedule(listOf(DayRange(wednesday, wednesday)))
        fun stateOn(dateKey: String, daysOff: HolidaySchedule) =
            checklistHistory(excusedFoam, excusedFoam.careChecklist!!, "2026-10-11", daysOff).single { it.dateKey == dateKey }.state

        assertEquals(ChecklistDayState.COMPLETE, stateOn("2026-10-07", away))
        assertEquals(ChecklistDayState.PARTIAL, stateOn("2026-10-08", away))
        assertEquals(ChecklistDayState.PARTIAL, stateOn("2026-10-07", HolidaySchedule()))
        assertEquals(ChecklistDayState.PARTIAL, stateOn("2026-10-07", away.withSickDays(DayRange(wednesday, wednesday))))
    }

    /** Which day a checklist is about shifts what a due day asks for, never where a record sits: the calendar shows it on its own day. */
    @Test
    fun theCalendarShowsARecordOnItsOwnDayWhateverTheChecklistIsAbout() {
        val lastNight = ChecklistConfiguration(
            id = "chk_night",
            label = "Yesterday",
            icon = ViewIcon.Journal,
            hue = ViewHue.Plum,
            checkpoints = listOf(Checkpoint("cpt_morning", CheckpointTime.Fixed(java.time.LocalTime.of(8, 0)), remind = true)),
            items = listOf(ChecklistItem("itm_violin", "Practiced violin")),
        )
        val aboutYesterday = lastNight.copy(about = Dated.always(AboutDay.Yesterday))
        val records = listOf(ChecklistDay(lastNight.id, "2026-10-13", 1L, setOf("cpt_morning/itm_violin")))
        fun history(checklist: ChecklistConfiguration) =
            checklistHistory(AppState(checklists = listOf(checklist), checklistDays = records), checklist, "2026-10-15", HolidaySchedule())

        assertEquals(history(lastNight), history(aboutYesterday))
        assertEquals(ChecklistDayState.COMPLETE, history(aboutYesterday).single { it.dateKey == "2026-10-13" }.state)
        assertEquals(ChecklistDayState.EMPTY, history(aboutYesterday).single { it.dateKey == "2026-10-14" }.state)
    }

    /** The screen opens on the asked day, a seasonal day with nothing due included; only a day before the checklist existed sends it to today. */
    @Test
    fun theScreenOpensOnTheAskedDayUnlessTheChecklistDidNotExistYet() {
        val newYear = "2027-01-01"
        val january = ChecklistConfiguration(
            id = "chk_night",
            label = "Yesterday",
            icon = ViewIcon.Journal,
            hue = ViewHue.Plum,
            checkpoints = listOf(Checkpoint("cpt_morning", CheckpointTime.Fixed(java.time.LocalTime.of(8, 0)), remind = true)),
            items = listOf(ChecklistItem("itm_violin", "Practiced violin", months = setOf(java.time.Month.JANUARY))),
            about = Dated.always(AboutDay.Yesterday),
        )
        assertEquals("2026-12-31", january.openingDayKey(newYear))
        assertEquals(newYear, january.copy(about = Dated.always(AboutDay.Today)).openingDayKey(newYear))

        val start = java.time.LocalDate.parse(newYear)
        val createdToday = january.copy(checkpoints = january.checkpoints.map { it.copy(from = start) }, items = january.items.map { it.copy(from = start) })
        assertEquals(newYear, createdToday.openingDayKey(newYear))
        assertEquals(newYear, createdToday.openingDayKey("2027-01-02"))
        assertEquals("2027-01-02", createdToday.openingDayKey("2027-01-03"))
        // Items from the beginning at a checkpoint that started today: nothing could be ticked before today either.
        val lateCheckpoint = january.copy(checkpoints = january.checkpoints.map { it.copy(from = start) })
        assertEquals(newYear, lateCheckpoint.openingDayKey(newYear))
        assertEquals(newYear, lateCheckpoint.openingDayKey("2027-01-02"))
    }

    private fun ticks(ids: Set<String>) = ids.mapTo(LinkedHashSet()) { tickOf("midday", it) }

    /**
     * Supplements with creatine logging only misses from Monday: on a day it is due its box shows it
     * done until it is marked missed, a tap marks it missed and another takes the miss back, a tick
     * recorded before the switch comes back with the draft's undo, and on a day it is not due it
     * ticks as ever.
     */
    @Test
    fun anItemThatLogsOnlyMissesIsAssumedDoneUntilItIsMarkedMissed() {
        val monday = java.time.LocalDate.parse("2026-10-12")
        val creatine = tickOf(supplements.checkpoints.single().id, StandardSupplementIds.CREATINE)
        val switched = supplementsState().applyEdit(supplements.id, ChecklistEdit.SetItemExceptionsOnly(StandardSupplementIds.CREATINE, true), monday)
        val checklist = switched.checklists.single { it.id == supplements.id }

        val untouched = checklistSelection(switched, checklist, "2026-10-13", HolidaySchedule())
        assertEquals(ItemMark.Assumed, untouched.markOf(creatine))
        assertFalse("nothing starts recorded", untouched.hasChanges)
        val missed = untouched.toggle(creatine)
        assertEquals(ItemMark.Missed, missed.markOf(creatine))
        assertEquals(DayRecord(misses = setOf(creatine)), missed.selected)
        assertFalse("a miss is saved, never celebrated", missed.addsATick)
        assertEquals(untouched.selected, missed.toggle(creatine).selected)

        // A tick from before the switch stays a recorded tick; marking it missed replaces it, and undoing brings it back.
        val ticked = setDayRecord(switched, checklist, "2026-10-13", DayRecord(setOf(creatine)), 1L)
        val recorded = checklistSelection(ticked, checklist, "2026-10-13", HolidaySchedule())
        assertEquals(ItemMark.Ticked, recorded.markOf(creatine))
        assertEquals(DayRecord(misses = setOf(creatine)), recorded.toggle(creatine).selected)
        assertEquals(recorded.selected, recorded.toggle(creatine).toggle(creatine).selected)

        // Before the switch the item ticks as ever.
        val before = checklistSelection(switched, checklist, "2026-10-11", HolidaySchedule())
        assertEquals(ItemMark.Open, before.markOf(creatine))
        assertEquals(DayRecord(setOf(creatine)), before.toggle(creatine).selected)
        assertTrue(before.toggle(creatine).addsATick)
    }

    /**
     * The calendar shows facts: an assumed day reads empty with a quiet bar, a miss is a recorded
     * fact that leaves the day partial with an empty ring, and TalkBack hears which is which.
     */
    @Test
    fun theCalendarShowsAnAssumedDayAsAssumedAndAMissAsAFact() {
        val monday = java.time.LocalDate.parse("2026-10-12")
        val ids = listOf(StandardSupplementIds.MULTIVITAMIN, StandardSupplementIds.CREATINE, StandardSupplementIds.VITAMIN_D)
        val checkpoint = supplements.checkpoints.single().id
        val switched = ids.fold(supplementsState()) { state, id -> state.applyEdit(supplements.id, ChecklistEdit.SetItemExceptionsOnly(id, true), monday) }
        val withMiss = switched.copy(checklistDays = listOf(ChecklistDay(supplements.id, "2026-10-13", 1L, emptySet(), setOf(tickOf(checkpoint, StandardSupplementIds.CREATINE)))))
        val days = checklistHistory(withMiss, withMiss.checklists.single { it.id == supplements.id }, "2026-10-14", HolidaySchedule()).associateBy { it.dateKey }

        val assumed = days.getValue("2026-10-12")
        assertEquals(ChecklistDayState.EMPTY, assumed.state)
        assertTrue(assumed.showsMarks)
        assertEquals(List(3) { CalendarMark.Assumed }, assumed.marks.values.toList())

        val missedDay = days.getValue("2026-10-13")
        assertEquals(ChecklistDayState.PARTIAL, missedDay.state)
        assertEquals(CalendarMark.Open, missedDay.marks.getValue(tickOf(checkpoint, StandardSupplementIds.CREATINE)))
        assertEquals(CalendarMark.Assumed, missedDay.marks.getValue(tickOf(checkpoint, StandardSupplementIds.MULTIVITAMIN)))

        // Before the switch an empty day shows nothing, as ever.
        val before = days.getValue("2026-10-11")
        assertEquals(ChecklistDayState.EMPTY, before.state)
        assertFalse(before.showsMarks)
    }

    /**
     * A miss recorded and the item changed the same day to a week target: the miss judges nothing,
     * yet the day's form says it is missed and the calendar shows it, as the fact it is. Ticking
     * the item replaces it.
     */
    @Test
    fun aMissThatNoLongerJudgesIsStillShownAsAFact() {
        val monday = java.time.LocalDate.parse("2026-10-12")
        val tuesday = "2026-10-13"
        val creatine = tickOf(supplements.checkpoints.single().id, StandardSupplementIds.CREATINE)
        val switched = supplementsState().applyEdit(supplements.id, ChecklistEdit.SetItemExceptionsOnly(StandardSupplementIds.CREATINE, true), monday)
        val missed = setDayRecord(switched, switched.checklists.single { it.id == supplements.id }, tuesday, DayRecord(misses = setOf(creatine)), 1L)
        val weekly = missed.applyEdit(supplements.id, ChecklistEdit.SetItemTarget(StandardSupplementIds.CREATINE, ChecklistTarget.PerWeek(times = 3)), java.time.LocalDate.parse(tuesday))
        val checklist = weekly.checklists.single { it.id == supplements.id }

        val selection = checklistSelection(weekly, checklist, tuesday, HolidaySchedule())
        assertEquals(ItemMark.Missed, selection.markOf(creatine))
        assertEquals(DayRecord(ticks = setOf(creatine)), selection.toggle(creatine).selected)

        val day = checklistHistory(weekly, checklist, "2026-10-14", HolidaySchedule()).single { it.dateKey == tuesday }
        assertTrue(day.showsMarks)
        assertEquals(CalendarMark.Open, day.marks.getValue(creatine))
    }

    /**
     * A miss and a tick saved today, then their items removed today: neither can be recorded today
     * any more, yet the calendar keeps the miss's ring and the day's description names both, as
     * the facts they are. A mark for an item no configuration knows has nothing to be named by.
     */
    @Test
    fun marksForAnItemRemovedTheSameDayAreStillShown() {
        val monday = java.time.LocalDate.parse("2026-10-12")
        val tuesday = "2026-10-13"
        val checkpoint = supplements.checkpoints.single().id
        val creatine = tickOf(checkpoint, StandardSupplementIds.CREATINE)
        val multivitamin = tickOf(checkpoint, StandardSupplementIds.MULTIVITAMIN)
        val switched = supplementsState().applyEdit(supplements.id, ChecklistEdit.SetItemExceptionsOnly(StandardSupplementIds.CREATINE, true), monday)
            .let { it.copy(checklistDays = listOf(ChecklistDay(supplements.id, tuesday, 1L, setOf(multivitamin, tickOf(checkpoint, "unknown")), setOf(creatine)))) }
        val removed = listOf(StandardSupplementIds.CREATINE, StandardSupplementIds.MULTIVITAMIN)
            .fold(switched) { state, id -> state.applyEdit(supplements.id, ChecklistEdit.RemoveItem(id), java.time.LocalDate.parse(tuesday)) }
        val checklist = removed.checklists.single { it.id == supplements.id }
        assertTrue(checklist.recordableOn(java.time.LocalDate.parse(tuesday)).ticks.none { it == creatine || it == multivitamin })

        val day = checklistHistory(removed, checklist, "2026-10-14", HolidaySchedule()).single { it.dateKey == tuesday }
        assertEquals(CalendarMark.Open, day.marks.getValue(creatine))
        assertEquals(CalendarMark.Ticked, day.marks.getValue(multivitamin))
        assertFalse(tickOf(checkpoint, "unknown") in day.marks)
        assertTrue(day.showsMarks)
        assertEquals(
            listOf(StandardSupplementIds.MULTIVITAMIN, StandardSupplementIds.CREATINE, StandardSupplementIds.VITAMIN_D),
            day.listed.single().items.map { it.id },
        )
    }
}

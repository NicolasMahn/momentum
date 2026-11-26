package de.nicolasmahn.momentum.domain.checklist

import de.nicolasmahn.momentum.domain.AppState
import de.nicolasmahn.momentum.domain.holiday.HolidaySchedule
import de.nicolasmahn.momentum.domain.reminder.Bedtime
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * An item that logs only misses: kept at each checkpoint it is due at unless a miss is recorded
 * there. What is owed (reminders, obligations) and what is unmet (completion, gaps) part ways only
 * for such an item, and a miss is a fact wherever it cannot be one.
 */
class OnlyLogMissesTest {
    private val monday = LocalDate.parse("2026-10-12")
    private val morning = Checkpoint("morning", CheckpointTime.Fixed(LocalTime.of(8, 0)), remind = true)
    private val evening = Checkpoint("evening", CheckpointTime.Fixed(LocalTime.of(21, 0)), remind = true)
    private val timing = Timing(night = Bedtime(LocalTime.of(7, 0), fixedBedtime = null), checkIn = LocalTime.of(20, 0))
    private val nothing = ChecklistRecords { DayRecord() }

    /** Brushing at both checkpoints and mouthwash in the morning, both long established, and floss once a week, ticked. */
    private val teeth = ChecklistConfiguration(
        id = "chk_teeth",
        label = "Teeth",
        icon = ViewIcon.Toothbrush,
        hue = ViewHue.Teal,
        checkpoints = listOf(morning, evening),
        items = listOf(
            ChecklistItem("brush", "Brush", exceptionsOnly = Dated.always(true)),
            ChecklistItem("mouthwash", "Mouthwash", checkpoints = listOf("morning"), exceptionsOnly = Dated.always(true)),
            ChecklistItem("floss", "Floss", checkpoints = listOf("evening"), target = ChecklistTarget.PerWeek(times = 1)),
        ),
    )

    /** The same morning with a vitamin that is ticked as ever beside the brushing. */
    private val mixed = teeth.copy(
        checkpoints = listOf(morning),
        items = listOf(ChecklistItem("brush", "Brush", exceptionsOnly = Dated.always(true)), ChecklistItem("vitamin", "Vitamin")),
    )

    private fun day(offset: Int): LocalDate = monday.plusDays(offset.toLong())

    private fun on(date: LocalDate, ticks: Set<String> = emptySet(), misses: Set<String> = emptySet()) =
        ChecklistRecords { if (it == date) DayRecord(ticks, misses) else DayRecord() }

    @Test fun anUntouchedDayIsKeptOwesNothingAndNeverReminds() {
        val untouched = teeth.progressOn(monday, nothing, HolidaySchedule())
        assertTrue(untouched.isComplete)
        assertEquals(0, untouched.gaps)
        assertFalse(untouched.isOwing)
        assertTrue("kept by assumption alone", untouched.isAssumed)
        // Every checkpoint's due items log only misses, so neither checkpoint reminds.
        assertEquals(emptyList<Obligation>(), untouched.obligations(timing))
        assertEquals(emptySet<String>(), untouched.dueTicks)
        assertEquals(setOf("morning/brush", "morning/mouthwash", "evening/brush"), untouched.exceptionsOnly)
    }

    @Test fun twoMissesAtOneCheckpointAreOneGapAndNeitherIsOwed() {
        val bothMorning = teeth.progressOn(monday, on(monday, misses = setOf("morning/brush", "morning/mouthwash")), HolidaySchedule())
        assertEquals(1, bothMorning.gaps)
        assertFalse(bothMorning.isComplete)
        assertFalse(bothMorning.isAssumed)
        assertEquals(listOf("brush", "mouthwash"), bothMorning.checkpoints.single { it.checkpoint.id == "morning" }.unmet.map { it.id })
        // A miss is settled: no reminder names it and nothing waits for it.
        assertFalse(bothMorning.isOwing)
        assertEquals(emptyList<Obligation>(), bothMorning.obligations(timing))
        val evenToo = teeth.progressOn(monday, on(monday, misses = setOf("morning/brush", "morning/mouthwash", "evening/brush")), HolidaySchedule())
        assertEquals(2, evenToo.gaps)
    }

    @Test fun owedAndUnmetPartOnlyForTheMiss() {
        val progress = mixed.progressOn(monday, on(monday, misses = setOf("morning/brush")), HolidaySchedule())
        val morningProgress = progress.checkpoints.single()
        assertEquals(listOf("vitamin"), morningProgress.open.map { it.id })
        assertEquals(listOf("brush"), morningProgress.missed.map { it.id })
        assertEquals(listOf("brush", "vitamin"), morningProgress.unmet.map { it.id })
        assertEquals(1, progress.gaps)
        assertEquals(listOf("vitamin"), progress.obligations(timing).single().open.map { it.id })
        // Ticking the vitamin answers every obligation, and the day still holds its gap.
        val ticked = mixed.progressOn(monday, on(monday, ticks = setOf("morning/vitamin"), misses = setOf("morning/brush")), HolidaySchedule())
        assertFalse(ticked.isOwing)
        assertEquals(1, ticked.gaps)
    }

    @Test fun aTickOfSomethingDueMeansTheDayIsNotKeptByAssumptionAlone() {
        assertTrue(mixed.progressOn(monday, nothing, HolidaySchedule()).isOwing)
        val vitamin = mixed.progressOn(monday, on(monday, ticks = setOf("morning/vitamin")), HolidaySchedule())
        assertTrue(vitamin.isComplete)
        assertFalse(vitamin.isAssumed)
        // A tick recorded before the switch keeps the brushing done by record.
        val brushedBefore = teeth.progressOn(monday, on(monday, ticks = setOf("morning/brush")), HolidaySchedule())
        assertTrue(brushedBefore.isComplete)
        assertFalse(brushedBefore.isAssumed)
        // A tick of something no configuration knows changes nothing.
        val stray = teeth.progressOn(monday, on(monday, ticks = setOf("morning/unknown")), HolidaySchedule())
        assertTrue(stray.isAssumed)
    }

    /**
     * Before the item logs only misses a miss for it is a fact that judges nothing, as is a miss for
     * a week item, whose week is dealt by its ticks alone.
     */
    @Test fun aMissOnADayTheItemDoesNotLogOnlyMissesJudgesNothing() {
        val wednesday = day(2)
        val fromWednesday = teeth.copy(items = teeth.items.map { if (it.id == "brush") it.copy(exceptionsOnly = Dated(listOf(Dated.Revision(false), Dated.Revision(true, wednesday)))) else it })
        val missedMonday = on(monday, misses = setOf("morning/brush"))
        val withMiss = fromWednesday.progressOn(monday, missedMonday, HolidaySchedule())
        val without = fromWednesday.progressOn(monday, nothing, HolidaySchedule())
        assertEquals(without.checkpoints.map { it.open }, withMiss.checkpoints.map { it.open })
        assertEquals(without.gaps, withMiss.gaps)
        assertEquals(listOf("brush"), withMiss.checkpoints.single { it.checkpoint.id == "morning" }.open.map { it.id })
        assertEquals(setOf("morning/brush"), withMiss.recorded.misses)

        // Sunday is the floss's last chance: a miss recorded for it leaves its gap as it was.
        val sunday = day(6)
        val flossMissed = teeth.progressOn(sunday, on(sunday, misses = setOf("evening/floss")), HolidaySchedule())
        assertEquals(WeekStanding.Gap, flossMissed.weekItems.single().standing)
        assertEquals(teeth.progressOn(sunday, nothing, HolidaySchedule()).gaps, flossMissed.gaps)
    }

    /** Changed on the day to a number of times a week, the item logs its ticks, and the day's miss is a fact. */
    @Test fun aSameDayChangeToAWeekTargetLeavesTheMissAFactAndTheWeekToTheTicks() {
        val wednesday = day(2)
        val weekly = teeth.applyEdit(ChecklistEdit.SetItemTarget("brush", ChecklistTarget.PerWeek(times = 5)), wednesday)
        assertEquals(Dated.always(false), weekly.items.single { it.id == "brush" && it.from == wednesday }.exceptionsOnly)
        val progress = weekly.progressOn(wednesday, on(wednesday, misses = setOf("morning/brush")), HolidaySchedule())
        assertEquals(WeekStanding.Free, progress.weekItems.single { it.item.id == "brush" }.standing)
        assertEquals(weekly.progressOn(wednesday, nothing, HolidaySchedule()).gaps, progress.gaps)
        // The days before the change keep their entry, which logged only misses.
        assertTrue(weekly.progressOn(day(1), nothing, HolidaySchedule()).isComplete)
    }

    /** Switched on or off, the rule starts today: yesterday is judged as it was, whatever it holds. */
    @Test fun switchingOnOrOffNeverReJudgesAnEarlierDay() {
        val ticking = mixed.copy(items = listOf(ChecklistItem("brush", "Brush"), ChecklistItem("vitamin", "Vitamin")))
        val wednesday = day(2)
        val tuesday = day(1)
        val vitaminDaily = ChecklistRecords { DayRecord(setOf("morning/vitamin")) }
        val on = ticking.applyEdit(ChecklistEdit.SetItemExceptionsOnly("brush", true), wednesday)
        assertEquals(Dated(listOf(Dated.Revision(false), Dated.Revision(true, wednesday))), on.items.single { it.id == "brush" }.exceptionsOnly)
        // Tuesday's forgotten brushing stays a gap; from Wednesday the untouched brushing is kept.
        assertEquals(1, on.progressOn(tuesday, vitaminDaily, HolidaySchedule()).gaps)
        assertEquals(0, on.progressOn(wednesday, vitaminDaily, HolidaySchedule()).gaps)

        val off = on.applyEdit(ChecklistEdit.SetItemExceptionsOnly("brush", false), day(4))
        assertEquals(0, off.progressOn(day(3), vitaminDaily, HolidaySchedule()).gaps)
        assertEquals(1, off.progressOn(day(4), vitaminDaily, HolidaySchedule()).gaps)
        assertEquals(1, off.progressOn(tuesday, vitaminDaily, HolidaySchedule()).gaps)
        // Taken back on the day it was made, a switch leaves no revision.
        assertEquals(ticking, ticking.applyEdit(ChecklistEdit.SetItemExceptionsOnly("brush", true), wednesday).applyEdit(ChecklistEdit.SetItemExceptionsOnly("brush", false), wednesday))
    }

    /** Only misses and fewer times on holiday exclude each other: either edit sends the other back, dated alike. */
    @Test fun onlyMissesAndFewerTimesExcludeEachOtherInOneEdit() {
        val wednesday = day(2)
        val lowered = mixed.copy(items = listOf(ChecklistItem("brush", "Brush", awayTimes = 3, whenAway = Dated.always(WhenAway.LowerTarget)), ChecklistItem("vitamin", "Vitamin")))
        val onlyMisses = lowered.applyEdit(ChecklistEdit.SetItemExceptionsOnly("brush", true), wednesday).items.single { it.id == "brush" }
        assertEquals(Dated(listOf(Dated.Revision(WhenAway.LowerTarget), Dated.Revision(WhenAway.Due, wednesday))), onlyMisses.whenAway)
        assertEquals(Dated(listOf(Dated.Revision(false), Dated.Revision(true, wednesday))), onlyMisses.exceptionsOnly)
        assertEquals("the count stays for when fewer times is chosen again", 3, onlyMisses.awayTimes)

        val assuming = mixed.applyEdit(ChecklistEdit.SetItemAwayTimes("brush", 3), monday)
        val fewer = assuming.applyEdit(ChecklistEdit.SetItemWhenAway("brush", WhenAway.LowerTarget), wednesday).items.single { it.id == "brush" && (it.until == null) }
        assertEquals(WhenAway.LowerTarget, fewer.whenAway.on(wednesday))
        assertFalse(fewer.exceptionsOnly.on(wednesday))
        assertTrue("earlier days keep only misses", fewer.exceptionsOnly.on(day(1)))
    }

    @Test fun anItemDueANumberOfTimesAWeekKeepsItsTicks() {
        assertThrows(IllegalArgumentException::class.java) { teeth.applyEdit(ChecklistEdit.SetItemExceptionsOnly("floss", true), monday) }
        assertThrows(IllegalArgumentException::class.java) {
            teeth.copy(items = teeth.items.map { if (it.id == "floss") it.copy(exceptionsOnly = Dated.always(true)) else it })
        }
    }

    /** Validation reads the days each entry is in use, not its whole history. */
    @Test fun validationChecksTheDaysAnEntryIsInUse() {
        val wednesday = day(2)
        val friday = day(4)
        fun brush(whenAway: Dated<WhenAway>, exceptionsOnly: Dated<Boolean>, from: LocalDate? = null) =
            mixed.copy(items = listOf(ChecklistItem("brush", "Brush", from = from, awayTimes = 3, whenAway = whenAway, exceptionsOnly = exceptionsOnly)))
        assertThrows(IllegalArgumentException::class.java) { brush(Dated.always(WhenAway.LowerTarget), Dated.always(true)) }
        assertThrows(IllegalArgumentException::class.java) {
            brush(Dated(listOf(Dated.Revision(WhenAway.Due), Dated.Revision(WhenAway.LowerTarget, friday))), Dated(listOf(Dated.Revision(false), Dated.Revision(true, wednesday))))
        }
        // Fewer times until Wednesday, only misses from then on.
        brush(Dated(listOf(Dated.Revision(WhenAway.LowerTarget), Dated.Revision(WhenAway.Due, wednesday))), Dated(listOf(Dated.Revision(false), Dated.Revision(true, wednesday))))
        // An entry from Friday carries a history from before it began, which no day of it reads.
        brush(Dated(listOf(Dated.Revision(WhenAway.LowerTarget), Dated.Revision(WhenAway.Due, wednesday))), Dated.always(true), from = friday)
    }

    @Test fun aDayIsTickedOrMissedNeverBothForOneItem() {
        assertThrows(IllegalArgumentException::class.java) { DayRecord(ticks = setOf("morning/brush"), misses = setOf("morning/brush")) }
        assertThrows(IllegalArgumentException::class.java) { ChecklistDay(teeth.id, "2026-10-12", 1L, ticks = setOf("morning/brush"), misses = setOf("morning/brush")) }
        // A record of misses alone is a record.
        assertEquals(setOf("morning/brush"), ChecklistDay(teeth.id, "2026-10-12", 1L, ticks = emptySet(), misses = setOf("morning/brush")).misses)
    }

    /** Every save keeps the misses it does not touch: at another checkpoint, and for an item no configuration knows. */
    @Test fun everySaveKeepsTheMissesItDoesNotTouch() {
        val key = monday.toString()
        val state = AppState(
            checklists = listOf(teeth),
            checklistDays = listOf(ChecklistDay(teeth.id, key, 1L, ticks = setOf("evening/floss"), misses = setOf("evening/brush", "morning/gone"))),
        )
        val morningSaved = setCheckpointRecord(state, teeth, key, "morning", DayRecord(misses = setOf("morning/mouthwash")), 2L)
        assertEquals(DayRecord(setOf("evening/floss"), setOf("evening/brush", "morning/gone", "morning/mouthwash")), morningSaved.recordOn(teeth.id, key))

        // The day's form replaces what can be recorded that day and nothing else.
        val daySaved = setDayRecord(state, teeth, key, DayRecord(ticks = setOf("morning/brush")), 3L)
        assertEquals(DayRecord(setOf("morning/brush"), setOf("morning/gone")), daySaved.recordOn(teeth.id, key))

        // Marking all done ticks what is asked and leaves the items that log only misses as they are.
        val mixedState = AppState(checklists = listOf(mixed), checklistDays = listOf(ChecklistDay(mixed.id, key, 1L, ticks = emptySet(), misses = setOf("morning/brush", "morning/gone"))))
        val allDone = tickAllDue(mixedState, mixed, key, 4L, HolidaySchedule())
        assertEquals(DayRecord(setOf("morning/vitamin"), setOf("morning/brush", "morning/gone")), allDone.recordOn(mixed.id, key))
        assertEquals(allDone, tickAllDueAt(mixedState, mixed, key, "morning", 4L, HolidaySchedule()))
        // On an untouched day nothing is asked of the brushing, so it stays assumed.
        assertEquals(DayRecord(setOf("morning/vitamin")), tickAllDue(AppState(checklists = listOf(mixed)), mixed, key, 4L, HolidaySchedule()).recordOn(mixed.id, key))
    }

    @Test fun aRecordOfMissesAloneIsKeptAndRemovedLikeAnyOther() {
        val key = monday.toString()
        val missed = setDayRecord(AppState(checklists = listOf(teeth)), teeth, key, DayRecord(misses = setOf("morning/brush")), 1L)
        assertEquals(listOf(ChecklistDay(teeth.id, key, 1L, ticks = emptySet(), misses = setOf("morning/brush"))), missed.checklistDays)
        assertEquals(AppState(checklists = listOf(teeth)), setDayRecord(missed, teeth, key, DayRecord(), 2L))
    }

    /** On a checklist about yesterday the rule is read on the record's day, as every rule of a record is. */
    @Test fun aboutYesterdayTheRecordsDayDecides() {
        val wednesday = day(2)
        val lastNight = mixed.copy(
            items = listOf(ChecklistItem("brush", "Brush", exceptionsOnly = Dated(listOf(Dated.Revision(false), Dated.Revision(true, wednesday)))), ChecklistItem("vitamin", "Vitamin")),
            about = Dated.always(AboutDay.Yesterday),
        )
        val vitaminDaily = ChecklistRecords { DayRecord(setOf("morning/vitamin")) }
        // Wednesday asks for Tuesday's record, whose brushing still had to be ticked.
        assertEquals(day(1), lastNight.recordDayFor(wednesday))
        assertEquals(1, lastNight.progressOn(lastNight.recordDayFor(wednesday), vitaminDaily, HolidaySchedule()).gaps)
        // Thursday asks for Wednesday's, which logs only misses.
        assertEquals(0, lastNight.progressOn(lastNight.recordDayFor(day(3)), vitaminDaily, HolidaySchedule()).gaps)
    }

    /** A feature with no record yet starts with the first day an item logs only misses, if that comes first. */
    @Test fun aTaskStartsWithItsFirstRecordOrItsFirstDayOfOnlyMisses() {
        val ticking = mixed.copy(items = listOf(ChecklistItem("brush", "Brush"), ChecklistItem("vitamin", "Vitamin")))
        assertEquals(null, ticking.dueFrom(firstRecordedDay = null))
        assertEquals(day(5), ticking.dueFrom(day(5)))
        val wednesday = day(2)
        val switched = ticking.applyEdit(ChecklistEdit.SetItemExceptionsOnly("brush", true), wednesday)
        assertEquals(wednesday, switched.dueFrom(firstRecordedDay = null))
        assertEquals(wednesday, switched.dueFrom(day(5)))
        assertEquals(monday, switched.dueFrom(monday))
    }

    /** Switched on and then removed or given a week target on the same day, an item logged only misses on no day, so it starts nothing. */
    @Test fun aSwitchNoDayOfItsEntryReadsNamesNoDay() {
        val ticking = mixed.copy(items = listOf(ChecklistItem("brush", "Brush"), ChecklistItem("vitamin", "Vitamin")))
        val wednesday = day(2)
        val switched = ticking.applyEdit(ChecklistEdit.SetItemExceptionsOnly("brush", true), wednesday)
        assertEquals(null, switched.applyEdit(ChecklistEdit.SetItemTarget("brush", ChecklistTarget.PerWeek(times = 3)), wednesday).firstExceptionsOnlyDay)
        assertEquals(null, switched.applyEdit(ChecklistEdit.RemoveItem("brush"), wednesday).firstExceptionsOnlyDay)
        // Removed the day after, it logged only misses on Wednesday.
        assertEquals(wednesday, switched.applyEdit(ChecklistEdit.RemoveItem("brush"), day(3)).firstExceptionsOnlyDay)
        // A revision from before an entry began counts from the entry's first day.
        val later = ChecklistItem("brush", "Brush", from = day(4), exceptionsOnly = Dated(listOf(Dated.Revision(false), Dated.Revision(true, wednesday))))
        assertEquals(day(4), ticking.copy(items = listOf(later, ChecklistItem("vitamin", "Vitamin"))).firstExceptionsOnlyDay)
    }

    /**
     * A task starts on the first day an item is due with only misses on, by that day's rules: a
     * Monday item switched on Thursday and off on Friday was never due so, and starts nothing; left
     * on, it starts on the next Monday. A season, or a checkpoint that ended, is read the same way.
     */
    @Test fun aTaskStartsOnTheFirstDayTheItemIsDueWithOnlyMissesOn() {
        val thursday = day(3)
        val friday = day(4)
        fun only(item: ChecklistItem, vararg revisions: Dated.Revision<Boolean>) =
            mixed.copy(items = listOf(item.copy(exceptionsOnly = Dated(listOf(Dated.Revision(false)) + revisions)), ChecklistItem("vitamin", "Vitamin")))
        val mondays = ChecklistItem("brush", "Brush", target = ChecklistTarget.Weekdays(setOf(java.time.DayOfWeek.MONDAY)))
        assertEquals(null, only(mondays, Dated.Revision(true, thursday), Dated.Revision(false, friday)).firstExceptionsOnlyDay)
        assertEquals(day(7), only(mondays, Dated.Revision(true, thursday)).firstExceptionsOnlyDay)

        val november = ChecklistItem("brush", "Brush", months = setOf(java.time.Month.NOVEMBER))
        assertEquals(null, only(november, Dated.Revision(true, thursday), Dated.Revision(false, friday)).firstExceptionsOnlyDay)
        assertEquals(LocalDate.parse("2026-11-01"), only(november, Dated.Revision(true, thursday)).firstExceptionsOnlyDay)

        val endedAt = mixed.copy(checkpoints = listOf(morning.copy(until = day(1)), evening))
        val atMorning = ChecklistItem("brush", "Brush", checkpoints = listOf("morning"), exceptionsOnly = Dated(listOf(Dated.Revision(false), Dated.Revision(true, thursday))))
        assertEquals(null, endedAt.copy(items = listOf(atMorning, ChecklistItem("vitamin", "Vitamin"))).firstExceptionsOnlyDay)
    }
}

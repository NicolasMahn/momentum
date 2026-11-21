package com.example.workouttracker.domain.checklist

import com.example.workouttracker.domain.activity.SeededActivityRules
import com.example.workouttracker.domain.AppState
import com.example.workouttracker.domain.holiday.DayRange
import com.example.workouttracker.domain.holiday.HolidaySchedule
import com.example.workouttracker.domain.reminder.Bedtime
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * An item due a number of times a week, on any days. Weeks are written Monday first: `x` for a
 * day the item was ticked and `.` for one it was not; standings as `D` done, `X` ticked beyond what
 * the week asks, `F` free, `G` gap and `-` for a day that is not one of the week's.
 */
class WeekItemsTest {
    private val monday = LocalDate.parse("2026-10-05")
    private val morning = Checkpoint("morning", CheckpointTime.Fixed(LocalTime.of(8, 0)), remind = true)
    private val evening = Checkpoint("evening", CheckpointTime.Fixed(LocalTime.of(21, 0)), remind = true)
    private val timing = Timing(night = Bedtime(LocalTime.of(7, 0), fixedBedtime = null), checkIn = LocalTime.of(20, 0))

    /** The violin five times a week, twice in a week away. */
    private val violin = ChecklistItem("violin", "Violin", target = ChecklistTarget.PerWeek(times = 5), awayTimes = 2)
    private val practice = ChecklistConfiguration(
        id = "chk_practice",
        label = "Practice",
        icon = ViewIcon.Music,
        hue = ViewHue.Indigo,
        checkpoints = listOf(evening),
        items = listOf(violin),
    )

    /** Teeth brushed morning and evening, and flossing once a week, offered in the evening. */
    private val floss = ChecklistItem("floss", "Floss", checkpoints = listOf("evening"), target = ChecklistTarget.PerWeek(times = 1))
    private val care = ChecklistConfiguration(
        id = "chk_care",
        label = "Care",
        icon = ViewIcon.Toothbrush,
        hue = ViewHue.Teal,
        checkpoints = listOf(morning, evening),
        items = listOf(ChecklistItem("brush", "Brush teeth"), floss),
    )

    @Test fun freeDaysAreDealtInOrderAndEveryEmptyDayAfterThemIsAGap() {
        val ticks = violinOn("x.x....")
        assertEquals("DFDFGGG", practice.standings(ticks))
        val friday = practice.progressOn(day(4), ticks, HolidaySchedule())
        assertEquals(1, friday.gaps)
        assertFalse(friday.isComplete)
        assertEquals(listOf("violin"), friday.obligations(timing).single().open.map { it.id })
        // Wednesday stands at two of the week's five.
        assertEquals(2 to 5, practice.progressOn(day(2), ticks, HolidaySchedule()).weekItems.single().let { it.done to it.asked })
    }

    @Test fun onceAWeekFailsOnlyOnSundayAndADayDoneAnywhereKeepsTheWeek() {
        assertEquals("FFFFFFG", care.standings(brushingWith(flossOn = null)))
        assertEquals("FFDFFFF", care.standings(brushingWith(flossOn = 2)))
        val sunday = care.progressOn(day(6), brushingWith(flossOn = null), HolidaySchedule())
        assertEquals(1, sunday.gaps)
        assertFalse(sunday.isComplete)
        assertEquals(listOf("floss"), sunday.obligations(timing).single().open.map { it.id })
        assertEquals(0 to 1, sunday.weekItems.single().let { it.done to it.asked })
    }

    /** On a free day the item is not due at its checkpoint; once its free days are gone it is, like a daily item. */
    @Test fun aWeekItemIsDueAtItsCheckpointOnlyOnceItsFreeDaysAreUsedUp() {
        val ticks = brushingWith(flossOn = null)
        val saturday = care.progressOn(day(5), ticks, HolidaySchedule())
        assertEquals(listOf("brush"), saturday.checkpoints.single { it.checkpoint.id == "evening" }.due.map { it.id })
        assertTrue(saturday.isComplete)
        assertTrue(saturday.weekItems.single().isFree)
        assertEquals(emptyList<Obligation>(), saturday.obligations(timing))
        val sunday = care.progressOn(day(6), ticks, HolidaySchedule())
        assertEquals(listOf("brush", "floss"), sunday.checkpoints.single { it.checkpoint.id == "evening" }.due.map { it.id })
        assertEquals(setOf("morning/brush", "evening/brush", "evening/floss"), sunday.dueTicks)
        assertTrue(sunday.weekItems.single().isGap)
    }

    /** Each checkpoint with a missing daily item is one gap; a week item past its free days is one more, once. */
    @Test fun aWeekGapCountsOnceBesideTheCheckpointGaps() {
        val nothing = tickRecords { emptySet() }
        val sunday = care.progressOn(day(6), nothing, HolidaySchedule())
        assertEquals(3, sunday.gaps)
        val both = care.copy(items = listOf(ChecklistItem("brush", "Brush teeth"), floss.copy(checkpoints = null)))
        assertEquals(3, both.progressOn(day(6), nothing, HolidaySchedule()).gaps)
        assertEquals(1, practice.progressOn(day(6), nothing, HolidaySchedule()).gaps)
    }

    @Test fun theAwayFormulaMatchesActivitysHolidayTable() {
        (0..7).forEach { awayDays ->
            val away = if (awayDays == 0) HolidaySchedule() else HolidaySchedule(listOf(DayRange(monday, monday.plusDays(awayDays - 1L))))
            assertEquals("$awayDays away days", DaysPerWeek - SeededActivityRules.freeDaysOn(monday, away), timesAsked(times = 5, awayTimes = 2, awayDays = awayDays, days = DaysPerWeek))
        }
        assertEquals(listOf(5, 5, 5, 4, 4, 3, 3, 2), (0..7).map { timesAsked(5, 2, it, DaysPerWeek) })
        // Without a smaller count nothing changes, and a week away that asks nothing asks nothing.
        assertEquals(1, timesAsked(1, 1, 7, DaysPerWeek))
        assertEquals(0, timesAsked(7, 0, 7, DaysPerWeek))
    }

    /** Away days lower a week item only while the item says so; a daily item stays due either way. */
    @Test fun awayDaysLowerAWeekItemOnlyWhereTheItemLowersItsTarget() {
        val wholeWeekAway = HolidaySchedule(listOf(DayRange(monday, day(6))))
        val ticks = violinOn("xx.....")
        val lowered = practice.copy(items = listOf(violin.lowered()))
        assertEquals("DDFFFFF", lowered.standings(ticks, wholeWeekAway))
        assertEquals(2 to 2, lowered.progressOn(day(6), ticks, wholeWeekAway).weekItems.single().let { it.done to it.asked })
        assertEquals("DDFFGGG", practice.standings(ticks, wholeWeekAway))
        // Three away days ask one less; the brushing is still due on them.
        val threeAway = HolidaySchedule(listOf(DayRange(monday, day(2))))
        assertEquals(4, lowered.progressOn(monday, ticks, threeAway).weekItems.single().asked)
        val loweredCare = care.copy(items = listOf(ChecklistItem("brush", "Brush teeth"), floss.copy(target = ChecklistTarget.PerWeek(times = 2), awayTimes = 0).lowered()))
        assertEquals(listOf("brush"), loweredCare.progressOn(monday, tickRecords { emptySet() }, threeAway).checkpoints.single { it.checkpoint.id == "evening" }.open.map { it.id })
    }

    /** Each item reads its own rule on an away day: an excused daily item is not due, one still due is judged as ever. */
    @Test fun anAwayDayExcusesOnlyTheItemsThatSaySo() {
        val away = HolidaySchedule(listOf(DayRange(monday, monday)))
        val mixed = care.copy(items = listOf(ChecklistItem("brush", "Brush teeth").excused(), ChecklistItem("foam", "Hair foam", checkpoints = listOf("morning"))))
        val nothing = tickRecords { emptySet() }
        val awayMonday = mixed.progressOn(monday, nothing, away)
        assertEquals(listOf("morning" to listOf("foam")), awayMonday.checkpoints.map { checkpoint -> checkpoint.checkpoint.id to checkpoint.due.map { it.id } })
        assertEquals(1, awayMonday.gaps)
        assertEquals(listOf("foam"), awayMonday.obligations(timing).single().open.map { it.id })
        // Tuesday is an ordinary day, and the brushing is due again.
        assertEquals(setOf("morning/brush", "evening/brush", "morning/foam"), mixed.progressOn(day(1), nothing, away).dueTicks)
        // A tick on the excused item is recorded and counts for nothing.
        val brushed = tickRecords { if (it == monday) setOf("morning/brush") else emptySet() }
        assertEquals(setOf("morning/brush"), mixed.progressOn(monday, brushed, away).recorded.ticks)
        assertEquals(1, mixed.progressOn(monday, brushed, away).gaps)
    }

    /** An away day every item is excused on asks nothing: it is neither kept nor a gap, and owes no reminder. */
    @Test fun anAwayDayEveryItemIsExcusedOnAsksNothing() {
        val away = HolidaySchedule(listOf(DayRange(monday, day(2))))
        val allExcused = care.copy(items = listOf(ChecklistItem("brush", "Brush teeth").excused(), floss.excused()))
        val nothing = tickRecords { emptySet() }
        (0..2).forEach { offset ->
            val progress = allExcused.progressOn(day(offset), nothing, away)
            assertTrue("$offset", progress.asksNothing)
            assertEquals("$offset", 0, progress.gaps)
            assertEquals("$offset", emptyList<Obligation>(), progress.obligations(timing))
        }
        assertFalse(allExcused.progressOn(day(3), nothing, away).asksNothing)
        // Sick wins over away: a day that is both asks nothing of the streak, yet its items are due and reminded of.
        val sickAndAway = away.withSickDays(DayRange(monday, monday))
        assertEquals(setOf("morning/brush", "evening/brush"), allExcused.progressOn(monday, nothing, sickAndAway).dueTicks)
    }

    /**
     * A change of the away rule midweek is read from its day: the days before keep the free days
     * they were dealt, and the week's free days from then on are the new rule's, less those used.
     */
    @Test fun aDatedAwayRuleJudgesEachDayOfTheWeekByItsOwnRule() {
        val wholeWeekAway = HolidaySchedule(listOf(DayRange(monday, day(6))))
        val nothing = violinOn(".......")
        // Due until Wednesday, then fewer times: Wednesday's gap stays, and the lowered week frees three more days.
        val loweredFromThursday = practice.copy(items = listOf(violin.copy(whenAway = Dated.always(WhenAway.Due).changedFrom(day(3), WhenAway.LowerTarget))))
        assertEquals("FFGFFFG", loweredFromThursday.standings(nothing, wholeWeekAway))
        assertEquals(2 to 5, loweredFromThursday.progressOn(day(3), nothing, wholeWeekAway).weekItems.single().let { it.asked to (7 - it.asked) })
        // Fewer times until Tuesday, then due again: Monday and Tuesday keep their free days, and Wednesday is the first gap.
        val dueFromWednesday = practice.copy(items = listOf(violin.copy(whenAway = Dated.always(WhenAway.LowerTarget).changedFrom(day(2), WhenAway.Due))))
        assertEquals("FFGGGGG", dueFromWednesday.standings(nothing, wholeWeekAway))
        assertEquals(5, dueFromWednesday.progressOn(day(2), nothing, wholeWeekAway).weekItems.single().asked)
        // Without away days the rule changes nothing either way.
        assertEquals("FFGGGGG", loweredFromThursday.standings(nothing))
    }

    /**
     * Every tick is a recorded fact, and only the first as many as the week asks are kept days: a
     * once-a-week item ticked every day keeps Monday and asks nothing after, and a week spent away
     * that asks nothing turns every tick into an extra one.
     */
    @Test fun aTickBeyondWhatTheWeekAsksIsRecordedAndAsksNothing() {
        val everyDay = brushingWith(flossOn = null).let { daily -> tickRecords { daily.on(it).ticks + "evening/floss" } }
        assertEquals("DXXXXXX", care.standings(everyDay))
        val tuesday = care.progressOn(day(1), everyDay, HolidaySchedule())
        val evening = tuesday.checkpoints.single { it.checkpoint.id == "evening" }
        assertEquals(listOf("brush"), evening.due.map { it.id })
        assertEquals(2 to 1, tuesday.weekItems.single().let { it.done to it.asked })
        assertTrue(tuesday.isComplete)
        // The violin five times a week: the sixth and seventh tick are extra, the week stays at five.
        assertEquals("DDDDDXX", practice.standings(violinOn("xxxxxxx")))
        assertEquals(7 to 5, practice.progressOn(day(6), violinOn("xxxxxxx"), HolidaySchedule()).weekItems.single().let { it.done to it.asked })
        assertTrue(practice.progressOn(day(5), violinOn("xxxxxxx"), HolidaySchedule()).asksNothing)
        // Away all week with nothing asked, even one tick is extra: recorded, but nothing to judge.
        val nothingAway = practice.copy(items = listOf(violin.copy(awayTimes = 0).lowered()))
        val wholeWeekAway = HolidaySchedule(listOf(DayRange(monday, day(6))))
        assertEquals("XFFFFFF", nothingAway.standings(violinOn("x......"), wholeWeekAway))
        assertTrue(nothingAway.progressOn(monday, violinOn("x......"), wholeWeekAway).asksNothing)
        assertEquals(1 to 0, nothingAway.progressOn(monday, violinOn("x......"), wholeWeekAway).weekItems.single().let { it.done to it.asked })
    }

    /** An excused item's away days are not its week's days: they use no free day and make no gap. */
    @Test fun excusedAwayDaysAreNotTheWeeksDays() {
        val excused = practice.copy(items = listOf(violin.excused()))
        val away = HolidaySchedule(listOf(DayRange(monday, day(2))))
        assertEquals("---FFGG", excused.standings(violinOn("......."), away))
        assertEquals(2, excused.progressOn(day(6), violinOn("......."), away).weekItems.single().asked)
        assertEquals("---DDFF", excused.standings(violinOn("...xx.."), away))
    }

    /** A sick day is not one of the week's days, so the free days stay for the others, and a tick on it counts for nothing. */
    @Test fun sickDaysUseNoFreeDayAndATickWhileSickCountsForNothing() {
        val sick = HolidaySchedule().withSickDays(DayRange(day(1), day(2)))
        assertEquals("D--DDFF", practice.standings(violinOn("x..xx.."), sick))
        assertEquals(3 to 3, practice.progressOn(day(6), violinOn("x..xx.."), sick).weekItems.single().let { it.done to it.asked })
        assertEquals(3, practice.progressOn(day(6), violinOn("xx.xx.."), sick).weekItems.single().done)
        assertEquals(null, practice.progressOn(day(1), violinOn("xx....."), sick).weekItems.single().standing)
        // Without the sick days the same ticks run out of free days on Sunday.
        assertEquals("DFFDDGG", practice.standings(violinOn("x..xx..")))
    }

    @Test fun aTickAtAnyOfItsCheckpointsCountsOnceADay() {
        val anywhere = care.copy(items = listOf(ChecklistItem("brush", "Brush teeth"), floss.copy(checkpoints = null)))
        val ticks = tickRecords { date -> if (date == day(2)) setOf("morning/brush", "evening/brush", "morning/floss") else setOf("morning/brush", "evening/brush") }
        val wednesday = anywhere.progressOn(day(2), ticks, HolidaySchedule())
        assertEquals(1, wednesday.weekItems.single().done)
        assertTrue(wednesday.isComplete)
        // Due at both checkpoints that day, open at neither: it was done.
        assertEquals(listOf(listOf("brush", "floss"), listOf("brush", "floss")), wednesday.checkpoints.map { cp -> cp.due.map { it.id } })
        assertEquals("FFDFFFF", anywhere.standings(ticks))
    }

    /** A day a week item is free on, with nothing else due, asks nothing; such a day is not judged. */
    @Test fun aFreeDayWithNothingElseDueAsksNothing() {
        val ticks = violinOn("x......")
        assertTrue(practice.progressOn(day(1), ticks, HolidaySchedule()).asksNothing)
        assertFalse(practice.progressOn(monday, ticks, HolidaySchedule()).asksNothing)
        assertFalse(care.progressOn(day(1), brushingWith(flossOn = null), HolidaySchedule()).asksNothing)
    }

    /** A target changed midweek starts today: the days before are not the week item's, so it never fails that week. */
    @Test fun aWeekTargetThatStartsMidweekLeavesTheEarlierDaysTheirRules() {
        val wednesday = day(2)
        val changed = AppState(checklists = listOf(care.copy(items = listOf(ChecklistItem("brush", "Brush teeth"), floss.copy(target = ChecklistTarget.Weekdays(setOf(DayOfWeek.SATURDAY)))))))
            .applyEdit(care.id, ChecklistEdit.SetItemTarget("floss", ChecklistTarget.PerWeek(times = 1)), wednesday)
            .checklists.single()
        assertEquals("--FFFFF", changed.standings(brushingWith(flossOn = null)))
        assertEquals(0, changed.progressOn(day(6), brushingWith(flossOn = null), HolidaySchedule()).weekItems.single().asked)
        // Tuesday still reads the rule it had: the Saturday item was not due, and nothing was a week item.
        assertEquals(emptyList<WeekItemRules>(), changed.rulesOn(day(1)).weekItems)
        assertEquals(setOf("morning/brush", "evening/brush"), changed.rulesOn(day(1)).ticks)
    }

    @Test fun aWeekTargetIsCheckedLikeTheOthers() {
        listOf(
            { ChecklistTarget.PerWeek(times = 0) },
            { ChecklistTarget.PerWeek(times = 8) },
            // On holiday an item asks fewer times than usual, and never fewer than none.
            { practice.copy(items = listOf(violin.copy(target = ChecklistTarget.PerWeek(times = 3), awayTimes = 3))) },
            { violin.copy(awayTimes = -1) },
            { timesAsked(5, 2, 8, DaysPerWeek) },
            // Nothing to lower: the item is due once a week or less.
            { care.copy(items = listOf(ChecklistItem("brush", "Brush teeth", target = ChecklistTarget.Weekdays(setOf(DayOfWeek.SATURDAY))).lowered(), floss)) },
        ).forEach { build -> assertThrows(IllegalArgumentException::class.java) { build() } }
        // A week item without an away number can be lowered, once a week too; it stays due as ever until it gets one. A daily item asks its count.
        care.copy(items = listOf(ChecklistItem("brush", "Brush teeth"), floss.lowered()))
        care.copy(items = listOf(ChecklistItem("brush", "Brush teeth", awayTimes = 3).lowered(), floss))
        practice.copy(items = listOf(violin.lowered()))
    }

    /** The week item is offered every day among what can be recorded, and never among the daily items due. */
    @Test fun aWeekItemIsRecordableEveryDayAndDueThroughItsWeek() {
        (0..6).forEach { offset ->
            assertEquals("$offset", listOf("brush", "floss"), care.recordableOn(day(offset)).checkpoints.single { it.checkpoint.id == "evening" }.items.map { it.id })
            assertEquals("$offset", listOf("brush"), care.rulesOn(day(offset)).checkpoints.single { it.checkpoint.id == "evening" }.items.map { it.id })
            assertEquals("$offset", listOf("floss"), care.rulesOn(day(offset)).weekItems.map { it.item.id })
        }
    }

    private fun day(offset: Int): LocalDate = monday.plusDays(offset.toLong())

    private fun ChecklistItem.lowered() = copy(whenAway = Dated.always(WhenAway.LowerTarget))

    private fun ChecklistItem.excused() = copy(whenAway = Dated.always(WhenAway.Excused))

    /** The violin ticked on every `x` of [week]. */
    private fun violinOn(week: String): ChecklistRecords = tickRecords { date ->
        val offset = date.toEpochDay() - monday.toEpochDay()
        if (offset in 0..6 && week[offset.toInt()] == 'x') setOf("evening/violin") else emptySet()
    }

    /** Teeth brushed at both checkpoints every day, and flossing on day [flossOn] of the week. */
    private fun brushingWith(flossOn: Int?): ChecklistRecords = tickRecords { date ->
        val daily = setOf("morning/brush", "evening/brush")
        if (flossOn != null && date == day(flossOn)) daily + "evening/floss" else daily
    }

    private fun ChecklistConfiguration.standings(ticks: ChecklistRecords, daysOff: HolidaySchedule = HolidaySchedule()): String =
        (0..6).joinToString("") { offset ->
            when (progressOn(day(offset), ticks, daysOff).weekItems.singleOrNull()?.standing) {
                WeekStanding.Done -> "D"
                WeekStanding.Extra -> "X"
                WeekStanding.Free -> "F"
                WeekStanding.Gap -> "G"
                null -> "-"
            }
        }
}

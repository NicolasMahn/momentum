package com.example.workouttracker.domain.checklist

import com.example.workouttracker.domain.AppState
import com.example.workouttracker.domain.holiday.DayRange
import com.example.workouttracker.domain.holiday.HolidaySchedule
import com.example.workouttracker.domain.reminder.Bedtime
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A daily or some-days item that asks fewer times on holiday. Its ordinary days are judged on their
 * own, as ever; its away days of a week are dealt like a week item's week. Standings read Monday
 * first, as in [WeekItemsTest]: `D` done, `X` ticked beyond what is asked, `F` free, `G` gap, and
 * `-` for a day judged on its own or not at all.
 */
class FewerTimesOnHolidayTest {
    private val monday = LocalDate.parse("2026-10-05")
    private val morning = Checkpoint("morning", CheckpointTime.Fixed(LocalTime.of(8, 0)), remind = true)
    private val timing = Timing(night = Bedtime(LocalTime.of(7, 0), fixedBedtime = null), checkIn = LocalTime.of(20, 0))

    /** The Monstera watered every day, three times a week on holiday. */
    private val monstera = ChecklistItem("monstera", "Water Monstera", awayTimes = 3, whenAway = Dated.always(WhenAway.LowerTarget))

    /** Fertilizer on Monday, Wednesday and Friday, once a week on holiday. */
    private val fertilizer = ChecklistItem(
        "fertilizer",
        "Fertilize",
        target = ChecklistTarget.Weekdays(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)),
        awayTimes = 1,
        whenAway = Dated.always(WhenAway.LowerTarget),
    )

    /** The cactus every day, on holiday as ever. */
    private val cactus = ChecklistItem("cactus", "Water cactus")

    private val plants = ChecklistConfiguration(
        id = "chk_plants",
        label = "Plants",
        icon = ViewIcon.Plant,
        hue = ViewHue.Forest,
        checkpoints = listOf(morning),
        items = listOf(monstera, fertilizer, cactus),
    )
    private val nothing = tickRecords { emptySet() }

    @Test fun theFormulaFollowsActivitysTableAndTheAwayDaysAskTheRest() {
        assertEquals(listOf(7, 7, 6, 6, 5, 5, 4, 3), (0..7).map { timesAsked(times = 7, awayTimes = 3, awayDays = it, days = 7) })
        assertEquals(listOf(3, 3, 2, 1), (0..3).map { timesAsked(times = 3, awayTimes = 1, awayDays = it, days = 3) })
        // The ordinary days ask one each, so the away days together ask what the lowered week asks beyond them.
        (1..7).forEach { awayDays ->
            val asked = plants.progressOn(day(7 - awayDays), nothing, awayFrom(7 - awayDays)).weekItems.single { it.item.id == "monstera" }.asked
            assertEquals("$awayDays away days", timesAsked(7, 3, awayDays, 7) - (7 - awayDays), asked)
        }
        assertEquals(listOf(1, 1, 2, 2, 3, 3, 3), (1..7).map { timesAsked(7, 3, it, 7) - (7 - it) })
    }

    /** Thursday to Sunday away: the away days ask two, so two are free; the days before are due as ever. */
    @Test fun ordinaryDaysStayDueAndTheAwayDaysAreDealtAsAWeek() {
        val away = awayFrom(3)
        assertEquals("---FFGG", plants.standings("monstera", nothing, away))
        assertEquals("---DDFF", plants.standings("monstera", ticked("monstera", day(3), day(4)), away))
        // A third tick is a record and nothing more.
        assertEquals("---DDXF", plants.standings("monstera", ticked("monstera", day(3), day(4), day(5)), away))
        assertEquals(3 to 2, plants.progressOn(day(5), ticked("monstera", day(3), day(4), day(5)), away).weekItems.single { it.item.id == "monstera" }.let { it.done to it.asked })
        // Monday is judged on its own day: the Monstera is due, and open until ticked.
        val mondayProgress = plants.progressOn(monday, nothing, away)
        assertEquals(listOf("monstera", "fertilizer", "cactus"), mondayProgress.checkpoints.single().due.map { it.id })
        assertTrue(mondayProgress.weekItems.isEmpty())
        // On a free away day the Monstera is not due; on a gap day it is, like a daily item, and owed.
        val thursday = plants.progressOn(day(3), nothing, away)
        assertEquals(listOf("cactus"), thursday.checkpoints.single().due.map { it.id })
        assertEquals(listOf("cactus"), thursday.obligations(timing).single().open.map { it.id })
        val saturday = plants.progressOn(day(5), nothing, away)
        assertEquals(listOf("monstera", "cactus"), saturday.checkpoints.single().due.map { it.id })
        assertEquals(listOf("monstera", "cactus"), saturday.obligations(timing).single().open.map { it.id })
        assertFalse(saturday.isComplete)
        // The cactus's checkpoint is one gap and the Monstera's away day another; with the cactus done, the Monstera's is the one.
        assertEquals(2, saturday.gaps)
        assertEquals(1, plants.progressOn(day(5), ticked("cactus", day(5)), away).gaps)
    }

    /** Ticked on its free days, the Monstera keeps them and leaves a gap no later; a free day with nothing else due asks nothing. */
    @Test fun aFreeAwayDayWithNothingElseDueAsksNothing() {
        val only = plants.copy(items = listOf(monstera))
        val wholeWeek = awayFrom(0)
        assertEquals("FFFFGGG", only.standings("monstera", nothing, wholeWeek))
        assertTrue(only.progressOn(monday, nothing, wholeWeek).asksNothing)
        assertFalse(only.progressOn(day(4), nothing, wholeWeek).asksNothing)
        assertEquals("DDDXXFF", only.standings("monstera", ticked("monstera", *(0..4).map(::day).toTypedArray()), wholeWeek))
        assertTrue(only.progressOn(day(3), ticked("monstera", *(0..4).map(::day).toTypedArray()), wholeWeek).asksNothing)
    }

    /** Some days: only the scheduled away days are the week's, and the share of them away lowers the count. */
    @Test fun aSomeDaysItemLowersOverItsScheduledAwayDaysAlone() {
        // Wednesday to Sunday away: of Monday, Wednesday and Friday two are away, asking one.
        val away = awayFrom(2)
        assertEquals("--F-G--", plants.standings("fertilizer", nothing, away))
        assertEquals("--D-F--", plants.standings("fertilizer", ticked("fertilizer", day(2)), away))
        assertEquals(1, plants.progressOn(day(2), nothing, away).weekItems.single { it.item.id == "fertilizer" }.asked)
        assertTrue(plants.progressOn(monday, nothing, away).checkpoints.single().due.any { it.id == "fertilizer" })
        // Thursday is not one of its days: not due, and not among the week items either.
        val thursday = plants.progressOn(day(3), nothing, away)
        assertTrue(thursday.checkpoints.single().due.none { it.id == "fertilizer" })
        assertTrue(thursday.weekItems.none { it.item.id == "fertilizer" })
    }

    /** A sick day among the away days is neither free nor a gap, and the week asks one less for it; the item is due on it as a daily item. */
    @Test fun aSickAwayDayStaysASickDay() {
        val sickWednesday = awayFrom(0).withSickDays(DayRange(day(2), day(2)))
        assertEquals("FF-FFGG", plants.standings("monstera", nothing, sickWednesday))
        assertEquals(2, plants.progressOn(day(6), nothing, sickWednesday).weekItems.single { it.item.id == "monstera" }.asked)
        val wednesday = plants.progressOn(day(2), nothing, sickWednesday)
        assertEquals(listOf("monstera", "fertilizer", "cactus"), wednesday.checkpoints.single().due.map { it.id })
        assertTrue(wednesday.weekItems.isEmpty())
        // A tick on the sick day counts for nothing in the week.
        assertEquals(0, plants.progressOn(day(6), ticked("monstera", day(2)), sickWednesday).weekItems.single { it.item.id == "monstera" }.done)
        // The six healthy away days ask two, so a third tick is a record and nothing more.
        val threeTicks = ticked("monstera", monday, day(1), day(3))
        assertEquals("DD-XFFF", plants.standings("monstera", threeTicks, sickWednesday))
        assertEquals(3 to 2, plants.progressOn(day(3), threeTicks, sickWednesday).weekItems.single { it.item.id == "monstera" }.let { it.done to it.asked })
        assertTrue(plants.copy(items = listOf(monstera)).progressOn(day(3), threeTicks, sickWednesday).asksNothing)
    }

    /** A holiday from Thursday to the next Wednesday is two weeks' away days, each dealt on its own. */
    @Test fun aHolidayStraddlingTwoWeeksIsDealtWeekByWeek() {
        val straddling = HolidaySchedule(listOf(DayRange(day(3), day(9))))
        assertEquals("---FFGG", plants.standings("monstera", nothing, straddling))
        // Monday to Wednesday away ask two: one free day, then gaps; Thursday is an ordinary day again.
        assertEquals("FGG----", plants.standings("monstera", nothing, straddling, from = day(7)))
        assertEquals(2, plants.progressOn(day(7), nothing, straddling).weekItems.single { it.item.id == "monstera" }.asked)
        assertTrue(plants.progressOn(day(10), nothing, straddling).checkpoints.single().due.any { it.id == "monstera" })
    }

    /**
     * Each away day reads the rule of its own day: a day lived while the item was still due was
     * judged on its day and stays so, and the lowered days get the week's free days, less none.
     */
    @Test fun theAwayRuleIsReadOnEachDay() {
        val wholeWeek = awayFrom(0)
        val loweredFromThursday = plants.copy(items = listOf(monstera.copy(whenAway = Dated.always(WhenAway.Due).changedFrom(day(3), WhenAway.LowerTarget))))
        assertEquals("---FFFF", loweredFromThursday.standings("monstera", nothing, wholeWeek))
        assertEquals(1, loweredFromThursday.progressOn(monday, nothing, wholeWeek).gaps)
        assertEquals(0, loweredFromThursday.progressOn(day(3), nothing, wholeWeek).weekItems.single().asked)
        val dueFromThursday = plants.copy(items = listOf(monstera.copy(whenAway = Dated.always(WhenAway.LowerTarget).changedFrom(day(3), WhenAway.Due))))
        assertEquals("FFF----", dueFromThursday.standings("monstera", nothing, wholeWeek))
        assertEquals(1, dueFromThursday.progressOn(day(3), nothing, wholeWeek).gaps)
    }

    /** A later edit reaches no earlier day: Monday kept stays kept after the rule, or the count, changes on Wednesday. */
    @Test fun aLaterEditChangesNoEarlierDaysStanding() {
        val wholeWeek = awayFrom(0)
        val only = plants.copy(items = listOf(monstera))
        val mondayTicked = ticked("monstera", monday)
        assertEquals("DFFFFGG", only.standings("monstera", mondayTicked, wholeWeek))
        val dueFromWednesday = AppState(checklists = listOf(only)).applyEdit(only.id, ChecklistEdit.SetItemWhenAway("monstera", WhenAway.Due), day(2)).checklists.single()
        assertEquals("DF-----", dueFromWednesday.standings("monstera", mondayTicked, wholeWeek))
        assertEquals(1 to 3, dueFromWednesday.progressOn(monday, mondayTicked, wholeWeek).weekItems.single().let { it.done to it.asked })
        // The count changed on Wednesday starts a new entry; Monday keeps the old one's reading, and the new one asks nothing more this week.
        val onceFromWednesday = AppState(checklists = listOf(only)).applyEdit(only.id, ChecklistEdit.SetItemAwayTimes("monstera", 1), day(2)).checklists.single()
        assertEquals("DFFFFFF", onceFromWednesday.standings("monstera", mondayTicked, wholeWeek))
        assertEquals(1 to 3, onceFromWednesday.progressOn(monday, mondayTicked, wholeWeek).weekItems.single().let { it.done to it.asked })
        assertEquals(0, onceFromWednesday.progressOn(day(6), mondayTicked, wholeWeek).weekItems.single().asked)
    }

    /** Fewer times without a count keeps the item due as ever, as a week item without one stays. */
    @Test fun anItemWithoutACountStaysDueAsEver() {
        val uncounted = plants.copy(items = listOf(monstera.copy(awayTimes = null)))
        val wholeWeek = awayFrom(0)
        assertEquals("-------", uncounted.standings("monstera", nothing, wholeWeek))
        assertEquals(listOf("monstera"), uncounted.progressOn(day(2), nothing, wholeWeek).checkpoints.single().due.map { it.id })
    }

    /** The configuration alone still lists the Monstera as a daily item; only the days off move it to the week items, and it can be ticked either way. */
    @Test fun theDaysOffMoveALoweredItemFromTheDailyItemsToTheWeekItems() {
        val away = awayFrom(3)
        assertEquals(listOf("monstera", "cactus"), plants.rulesOn(day(3)).checkpoints.single().items.map { it.id })
        assertEquals(emptyList<String>(), plants.rulesOn(day(3)).weekItems.map { it.item.id })
        val due = plants.dueOn(day(3), away)
        assertEquals(listOf("cactus"), due.checkpoints.single().items.map { it.id })
        assertEquals(listOf("monstera"), due.weekItems.map { it.item.id })
        assertFalse(due.weekItems.single().let { WeekItemProgress(it.item, it.target, null, 0, 0) }.isWeekItem)
        assertEquals(listOf("monstera", "fertilizer", "cactus"), plants.recordableOn(day(3)).checkpoints.single().items.map { it.id })
    }

    private fun day(offset: Int): LocalDate = monday.plusDays(offset.toLong())

    /** Away from day [offset] of the week through Sunday. */
    private fun awayFrom(offset: Int): HolidaySchedule = HolidaySchedule(listOf(DayRange(day(offset), day(6))))

    private fun ticked(itemId: String, vararg days: LocalDate): ChecklistRecords = tickRecords { date -> if (date in days) setOf("morning/$itemId") else emptySet() }

    private fun ChecklistConfiguration.standings(itemId: String, ticks: ChecklistRecords, daysOff: HolidaySchedule, from: LocalDate = monday): String =
        (0..6).joinToString("") { offset ->
            when (progressOn(from.plusDays(offset.toLong()), ticks, daysOff).weekItems.firstOrNull { it.item.id == itemId }?.standing) {
                WeekStanding.Done -> "D"
                WeekStanding.Extra -> "X"
                WeekStanding.Free -> "F"
                WeekStanding.Gap -> "G"
                null -> "-"
            }
        }
}

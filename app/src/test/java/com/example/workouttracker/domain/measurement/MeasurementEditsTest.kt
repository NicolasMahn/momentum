package com.example.workouttracker.domain.measurement

import com.example.workouttracker.domain.compatibility.withWeekRule
import com.example.workouttracker.domain.compatibility.weightWhenAway

import com.example.workouttracker.domain.compatibility.weightRewards

import com.example.workouttracker.domain.compatibility.WeightWhenAway

import com.example.workouttracker.domain.compatibility.WeightRewards

import com.example.workouttracker.domain.measurement.recordsOf

import com.example.workouttracker.domain.AppState
import com.example.workouttracker.domain.checklist.ChecklistRewards
import com.example.workouttracker.domain.checklist.Checkpoint
import com.example.workouttracker.domain.checklist.CheckpointTime
import com.example.workouttracker.domain.checklist.Dated
import com.example.workouttracker.domain.checklist.DefaultChecklistRewards
import com.example.workouttracker.domain.checklist.RewardPeriod
import com.example.workouttracker.domain.checklist.ViewHue
import com.example.workouttracker.domain.checklist.ViewIcon
import com.example.workouttracker.domain.checklist.WhenAway
import com.example.workouttracker.domain.measurement.asMeasurementLogs
import com.example.workouttracker.domain.measurement.MeasurementRecord
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import com.example.workouttracker.domain.measurement.MeasurementLog
import com.example.workouttracker.domain.compatibility.WeightMeasurementId
import com.example.workouttracker.domain.measurement.withWeight
import com.example.workouttracker.domain.measurement.applyingMeasurementEdits
import com.example.workouttracker.domain.measurement.firstMeasuredDay
import com.example.workouttracker.domain.compatibility.weightMeasurement

class MeasurementEditsTest {
    private val today = LocalDate.parse("2026-10-14")
    private val weight = testWeightMeasurement()

    /** The name and the look judge no day, so they change everywhere at once and leave no date behind. */
    @Test fun theNameAndTheLookChangePlainly() {
        val edited = weight
            .applyEdit(MeasurementEdit.Rename("Morning weight"), today)
            .applyEdit(MeasurementEdit.SetIcon(ViewIcon.Heart), today)
            .applyEdit(MeasurementEdit.SetHue(ViewHue.Plum), today)
        assertEquals(weight.copy(label = "Morning weight", icon = ViewIcon.Heart, hue = ViewHue.Plum), edited)
    }

    /** The away rule judges days: it changes from the day it is saved, and every earlier day keeps what it had. */
    @Test fun theAwayRuleChangesFromToday() {
        val edited = weight.applyEdit(MeasurementEdit.SetWhenAway(WhenAway.Due), today)
        assertEquals(Dated(listOf(Dated.Revision(WhenAway.Excused), Dated.Revision(WhenAway.Due, today))), edited.whenAway)
        assertEquals(WhenAway.Excused, edited.whenAway.on(today.minusDays(1)))
        // Taken back the same day, it leaves nothing.
        assertEquals(weight, edited.applyEdit(MeasurementEdit.SetWhenAway(WhenAway.Excused), today))
    }

    /**
     * Weight's bank as it was stored earns by the week. Saving its two numbers makes it earn by
     * kept days from that day: the week rule stays for every day before, with its last day, the
     * bank's size keeps its dates, and the days it takes part stay as they were.
     */
    @Test fun aWeekBankEarnsByKeptDaysFromTheDayItsNumbersAreSaved() {
        val edited = weight.applyEdit(MeasurementEdit.SetBankRules(maxPasses = 3, everyKeptDays = 5), today)
        assertNull(edited.weekRewards)
        assertEquals(
            MeasurementBank.Standard(
                ChecklistRewards(Dated(listOf(Dated.Revision(7), Dated.Revision(3, today))), Dated.always(5)),
                EarlierWeeks(keptDaysInWeek = Dated.always(6), until = today.minusDays(1)),
            ),
            edited.rewards,
        )
        assertEquals(weight.copy(rewards = edited.rewards), edited)

        // Only the size that changed takes a revision.
        assertEquals(Dated.always(7), weight.applyEdit(MeasurementEdit.SetBankRules(maxPasses = 7, everyKeptDays = 5), today).standardRewards!!.maxPasses)

        // A week bank's dated numbers and periods come along; a week number saved on the day of the change judged no day and is not kept.
        val first = LocalDate.parse("2026-09-01")
        val dated = weight.withWeekRule(today.minusDays(10), maxPasses = 5, keptDaysInWeek = 4).withWeekRule(today, maxPasses = 5, keptDaysInWeek = 3)
            .applyEdit(MeasurementEdit.LeaveRewards, today.minusDays(3), firstRecordedDay = first)
            .applyEdit(MeasurementEdit.JoinRewards, today.minusDays(1))
        val switched = dated.applyEdit(MeasurementEdit.SetBankRules(maxPasses = 5, everyKeptDays = 7), today)
        assertEquals(
            MeasurementBank.Standard(
                ChecklistRewards(
                    Dated(listOf(Dated.Revision(7), Dated.Revision(5, today.minusDays(10)))),
                    Dated.always(7),
                    listOf(RewardPeriod(first, today.minusDays(4)), RewardPeriod(today.minusDays(1))),
                ),
                EarlierWeeks(Dated(listOf(Dated.Revision(6), Dated.Revision(4, today.minusDays(10)))), until = today.minusDays(1)),
            ),
            switched.rewards,
        )
    }

    /**
     * On the day a week bank began to earn by kept days no day was judged by the new number yet: a
     * second change that day replaces it and leaves no date. A change on a later day is dated,
     * and the week rule stays as it was through both. It never earns by the week again.
     */
    @Test fun aBankThatLeftTheWeekChangesItsNumbersAsEveryDailyBankDoes() {
        val twice = weight
            .applyEdit(MeasurementEdit.SetBankRules(3, 4), today)
            .applyEdit(MeasurementEdit.SetBankRules(2, 5), today)
        val weeks = EarlierWeeks(Dated.always(6), until = today.minusDays(1))
        assertEquals(MeasurementBank.Standard(ChecklistRewards(Dated(listOf(Dated.Revision(7), Dated.Revision(2, today))), Dated.always(5)), weeks), twice.rewards)

        // Set back to the week's own numbers, it still earns by kept days.
        val setBack = twice.applyEdit(MeasurementEdit.SetBankRules(7, 6), today)
        assertEquals(MeasurementBank.Standard(ChecklistRewards(maxPasses = 7, everyKeptDays = 6), weeks), setBack.rewards)

        // A change on a later day keeps the earlier one as history.
        val later = twice.applyEdit(MeasurementEdit.SetBankRules(2, 6), today.plusDays(7))
        assertEquals(
            MeasurementBank.Standard(
                ChecklistRewards(Dated(listOf(Dated.Revision(7), Dated.Revision(2, today))), Dated(listOf(Dated.Revision(5), Dated.Revision(6, today.plusDays(7))))),
                weeks,
            ),
            later.rewards,
        )

        // Leaving closes the period its whole history has been, from its first record, as its week bank did; joining the next day reopens it.
        val first = LocalDate.parse("2026-09-01")
        val left = twice.applyEdit(MeasurementEdit.LeaveRewards, today.plusDays(2), firstRecordedDay = first)
        assertEquals(listOf(RewardPeriod(first, today.plusDays(1))), left.standardRewards!!.periods)
        assertEquals(weeks, (left.rewards as MeasurementBank.Standard).earlierWeeks)
        assertEquals(listOf(RewardPeriod(first)), left.applyEdit(MeasurementEdit.JoinRewards, today.plusDays(2)).standardRewards!!.periods)
    }

    @Test fun anEditTheConfigurationCannotTakeIsRefused() {
        listOf(
            MeasurementEdit.Rename(""),
            MeasurementEdit.Rename("  "),
            MeasurementEdit.Rename(" Weight"),
            MeasurementEdit.SetBankRules(maxPasses = 0, everyKeptDays = 6),
            MeasurementEdit.SetBankRules(maxPasses = 7, everyKeptDays = 0),
            MeasurementEdit.SetWhenAway(WhenAway.LowerTarget),
        ).forEach { edit -> assertThrows(edit.toString(), IllegalArgumentException::class.java) { weight.applyEdit(edit, today) } }
    }

    /** The unit and the step judge no day either: they change at once, a unit to nothing too, and leave no date behind. */
    @Test fun theUnitAndTheStepOfAMadeMeasurementChangePlainly() {
        val waist = testMadeMeasurement()
        assertEquals(waist.copy(unit = "in"), waist.applyEdit(MeasurementEdit.SetUnit("in"), today))
        assertEquals(waist.copy(unit = ""), waist.applyEdit(MeasurementEdit.SetUnit(""), today))
        assertEquals(waist.copy(unit = "km / h"), waist.applyEdit(MeasurementEdit.SetUnit("km / h"), today))
        assertEquals(waist.copy(step = MeasurementStep("0.01")), waist.applyEdit(MeasurementEdit.SetStep(MeasurementStep("0.01")), today))
        assertEquals(waist.copy(step = MeasurementStep("1")), waist.applyEdit(MeasurementEdit.SetStep(MeasurementStep("1")), today))
        assertEquals(waist.copy(step = MeasurementStep("2.5")), waist.applyEdit(MeasurementEdit.SetStep(MeasurementStep("2.5")), today))
        assertEquals(0.05, waist.applyEdit(MeasurementEdit.SetStep(MeasurementStep("0.05")), today).step.value, 0.0)
        // The edit holds what was typed; the configuration refuses what cannot be a unit, and nothing is trimmed or cut on the way.
        listOf(" cm", "cm ", "c\nm", "thirteen long", "\u200Ecm").forEach { unit ->
            assertThrows(unit, IllegalArgumentException::class.java) { waist.applyEdit(MeasurementEdit.SetUnit(unit), today) }
            assertEquals(unit, waist, waist.applyingEdits(listOf(MeasurementEdit.SetUnit(unit)), today))
        }
    }

    /**
     * Weight's unit and step change as a made measurement's do, plainly and without a date, and
     * neither touches its bank or its away rule. A made measurement outside the streak has no
     * bank whose rules could change.
     */
    @Test fun weightTakesAUnitAndAStepAndNeitherTouchesItsBank() {
        assertEquals(weight.copy(unit = "lb"), weight.applyEdit(MeasurementEdit.SetUnit("lb"), today))
        assertEquals(weight.copy(unit = ""), weight.applyEdit(MeasurementEdit.SetUnit(""), today))
        assertEquals(weight.copy(step = MeasurementStep("0.5")), weight.applyEdit(MeasurementEdit.SetStep(MeasurementStep("0.5")), today))
        assertEquals(
            weight.copy(unit = "st", step = MeasurementStep("0.05")),
            weight.applyingEdits(listOf(MeasurementEdit.SetUnit("st"), MeasurementEdit.SetStep(MeasurementStep("0.05"))), today),
        )
        assertThrows(IllegalArgumentException::class.java) { weight.applyEdit(MeasurementEdit.SetUnit("thirteen long"), today) }
        // Saying again what Weight starts as changes nothing and is no error.
        assertEquals(weight, weight.applyEdit(MeasurementEdit.SetUnit("kg"), today).applyEdit(MeasurementEdit.SetStep(MeasurementStep("0.1")), today))

        val waist = testMadeMeasurement()
        val joined = waist.applyEdit(MeasurementEdit.JoinRewards, today)
        listOf(waist, joined).forEach { made ->
            listOf(MeasurementEdit.SetWhenAway(WhenAway.LowerTarget)).forEach { edit ->
                assertThrows(edit.toString(), IllegalArgumentException::class.java) { made.applyEdit(edit, today) }
                assertEquals(edit.toString(), made, made.applyingEdits(listOf(edit), today))
            }
        }
        // Outside the streak there is no bank whose rules could change, and leaving again changes nothing.
        assertThrows(IllegalArgumentException::class.java) { waist.applyEdit(MeasurementEdit.SetBankRules(maxPasses = 4, everyKeptDays = 5), today) }
        assertEquals(waist, waist.applyEdit(MeasurementEdit.LeaveRewards, today))
        assertEquals(waist.copy(label = "Hips", icon = ViewIcon.Heart, hue = ViewHue.Sky), waist.applyingEdits(listOf(MeasurementEdit.Rename("Hips"), MeasurementEdit.SetIcon(ViewIcon.Heart), MeasurementEdit.SetHue(ViewHue.Sky)), today))
    }

    /**
     * Weight leaves and joins the streak on its week bank, dated as a checklist does: leaving
     * closes the period it has been in since its first record, which the state tells from its
     * first weight, and keeps the bank's rules; joining the next day reopens it, a later day
     * starts another, and joining while it takes part changes nothing. Unknown, the first record
     * leaves no period, as on a page's draft.
     */
    @Test fun weightLeavesAndJoinsTheStreakOnItsWeekBank() {
        val first = LocalDate.parse("2026-09-01")
        val left = weight.applyEdit(MeasurementEdit.LeaveRewards, today, firstRecordedDay = first)
        assertEquals(listOf(RewardPeriod(first, today.minusDays(1))), left.weekRewards?.periods)
        assertEquals(weight.weekRewards?.copy(periods = left.weekRewards?.periods), left.weekRewards)
        assertEquals(false, left.weekRewards?.takesPartOn(today))
        assertEquals(true, left.weekRewards?.takesPartOn(today.minusDays(1)))
        assertEquals(emptyList<RewardPeriod>(), weight.applyEdit(MeasurementEdit.LeaveRewards, today).weekRewards?.periods)
        assertEquals(listOf(RewardPeriod(first)), left.applyEdit(MeasurementEdit.JoinRewards, today).weekRewards?.periods)
        assertEquals(listOf(RewardPeriod(first, today.minusDays(1)), RewardPeriod(today.plusDays(2))), left.applyEdit(MeasurementEdit.JoinRewards, today.plusDays(2)).weekRewards?.periods)
        assertEquals(weight, weight.applyEdit(MeasurementEdit.JoinRewards, today))
        assertEquals(listOf(RewardPeriod(first)), weight.applyingEdits(listOf(MeasurementEdit.LeaveRewards, MeasurementEdit.JoinRewards), today, firstRecordedDay = first).weekRewards?.periods)

        val state = AppState(measurementLogs = listOf(MeasurementLog(WeightMeasurementId, today.toString(), 2L, 81.0), MeasurementLog(WeightMeasurementId, first.toString(), 1L, 80.0))).withWeight()
        assertEquals(first, state.firstMeasuredDay(WeightMeasurementId))
        assertNull(AppState().withWeight().firstMeasuredDay(WeightMeasurementId))
        assertEquals(left, state.applyingMeasurementEdits(WeightMeasurementId, listOf(MeasurementEdit.LeaveRewards), today).weightMeasurement)
    }

    /**
     * The reminder of a measurement a person made is switched and moved plainly, in any of the
     * three forms a checkpoint's time has, and stays the one checkpoint `day`: it judges no day,
     * so nothing is dated. Weight's reminder is its checkpoint too and changes the same way.
     */
    @Test fun theReminderOfAMeasurementChangesPlainlyWeightsToo() {
        val waist = testMadeMeasurement()
        assertEquals(Checkpoint("day", CheckpointTime.Fixed(LocalTime.of(9, 0)), remind = false), waist.checkpoint)

        val reminding = waist.applyEdit(MeasurementEdit.SetRemind(true), today)
        assertEquals(waist.copy(checkpoint = Checkpoint("day", CheckpointTime.Fixed(LocalTime.of(9, 0)), remind = true)), reminding)
        listOf(CheckpointTime.Fixed(LocalTime.of(21, 30)), CheckpointTime.AfterWake(15), CheckpointTime.BeforeSleep(30)).forEach { at ->
            assertEquals(at.toString(), waist.copy(checkpoint = Checkpoint("day", at, remind = true)), reminding.applyEdit(MeasurementEdit.SetReminderTime(at), today.plusDays(3)))
        }
        assertEquals(waist, reminding.applyEdit(MeasurementEdit.SetRemind(false), today))

        val at = CheckpointTime.Fixed(LocalTime.of(7, 30))
        assertEquals(weight.checkpoint.copy(remind = true, at = at), weight.applyingEdits(listOf(MeasurementEdit.SetRemind(true), MeasurementEdit.SetReminderTime(at)), today).checkpoint)
    }

    /**
     * A measurement a person made joins the streak on the day it is saved, with the bank every
     * made view starts with, three tokens and one for every seven kept days, and a first period
     * from that day: no earlier day is in it. Joined already, joining again changes nothing.
     */
    @Test fun joiningStartsOnTheDayItIsSavedWithTheBankEveryMadeViewStartsWith() {
        val waist = testMadeMeasurement()
        val joined = waist.applyEdit(MeasurementEdit.JoinRewards, today)

        assertEquals(DefaultChecklistRewards.copy(periods = listOf(RewardPeriod(today))), joined.standardRewards)
        assertEquals(3 to 7, joined.standardRewards!!.let { it.maxPasses.current to it.everyKeptDays.current })
        assertFalse(joined.standardRewards!!.takesPartOn(today.minusDays(1)))
        assertTrue(joined.standardRewards!!.takesPartOn(today))
        assertTrue(joined.standardRewards!!.takesPartOn(today.plusDays(400)))
        assertNull(joined.weekRewards)
        assertEquals(waist.copy(rewards = joined.rewards), joined)
        assertEquals(joined, joined.applyEdit(MeasurementEdit.JoinRewards, today.plusDays(3)))
    }

    /**
     * Leaving ends its part after yesterday: the open period closes there and every closed one
     * stays, so the days it took part are still the days it is judged on. A period that only
     * began today goes; the bank's rules stay for when it joins again, with no period left.
     */
    @Test fun leavingClosesTheOpenPeriodAtYesterdayAndKeepsTheClosedOnes() {
        val joined = testMadeMeasurement().applyEdit(MeasurementEdit.JoinRewards, today)
        val left = joined.applyEdit(MeasurementEdit.LeaveRewards, today.plusDays(10))
        assertEquals(listOf(RewardPeriod(today, today.plusDays(9))), left.standardRewards?.periods)
        assertTrue(left.standardRewards!!.takesPartOn(today.plusDays(9)))
        assertFalse(left.standardRewards!!.takesPartOn(today.plusDays(10)))
        // Left already, leaving again changes nothing.
        assertEquals(left, left.applyEdit(MeasurementEdit.LeaveRewards, today.plusDays(12)))

        val again = left.applyEdit(MeasurementEdit.JoinRewards, today.plusDays(20))
        val leftAgain = again.applyEdit(MeasurementEdit.LeaveRewards, today.plusDays(25))
        assertEquals(listOf(RewardPeriod(today, today.plusDays(9)), RewardPeriod(today.plusDays(20), today.plusDays(24))), leftAgain.standardRewards?.periods)

        // Joined and left on one day, no day of taking part is left: the list is empty and the rules stay.
        val changed = joined.applyEdit(MeasurementEdit.SetBankRules(maxPasses = 5, everyKeptDays = 4), today)
        val sameDay = changed.applyEdit(MeasurementEdit.LeaveRewards, today)
        assertEquals(emptyList<RewardPeriod>(), sameDay.standardRewards?.periods)
        assertEquals(5 to 4, sameDay.standardRewards!!.let { it.maxPasses.current to it.everyKeptDays.current })
        // A period begun today after an earlier one goes alone.
        assertEquals(left.standardRewards?.periods, again.applyEdit(MeasurementEdit.LeaveRewards, today.plusDays(20)).standardRewards?.periods)
    }

    /** Joining again on the day it left reopens the period, so a leave taken back leaves nothing; joining later starts another, and the days between stay outside. */
    @Test fun rejoiningTheSameDayReopensThePeriodAndALaterDayStartsAnother() {
        val joined = testMadeMeasurement().applyEdit(MeasurementEdit.JoinRewards, today)
        val leaveDay = today.plusDays(7)
        val left = joined.applyEdit(MeasurementEdit.LeaveRewards, leaveDay)

        assertEquals(joined, left.applyEdit(MeasurementEdit.JoinRewards, leaveDay))

        val later = left.applyEdit(MeasurementEdit.JoinRewards, leaveDay.plusDays(1))
        assertEquals(listOf(RewardPeriod(today, leaveDay.minusDays(1)), RewardPeriod(leaveDay.plusDays(1))), later.standardRewards?.periods)
        assertFalse(later.standardRewards!!.takesPartOn(leaveDay))
        assertTrue(later.standardRewards!!.takesPartOn(leaveDay.plusDays(1)))
        // It comes back with the rules it had, not the starting ones.
        val changed = joined.applyEdit(MeasurementEdit.SetBankRules(maxPasses = 2, everyKeptDays = 10), today.plusDays(1))
        val back = changed.applyEdit(MeasurementEdit.LeaveRewards, leaveDay).applyEdit(MeasurementEdit.JoinRewards, leaveDay.plusDays(30))
        assertEquals(changed.standardRewards!!.maxPasses, back.standardRewards!!.maxPasses)
        assertEquals(changed.standardRewards!!.everyKeptDays, back.standardRewards!!.everyKeptDays)
    }

    /**
     * The bank's size and the kept days a token takes change from the day they are saved once a
     * day before it took part, and plainly while none did, as a made checklist's do. A second
     * change that day replaces the first, and one taken back leaves no revision.
     */
    @Test fun theBanksRulesAreDatedOnceADayBeforeTodayTookPart() {
        val joined = testMadeMeasurement().applyEdit(MeasurementEdit.JoinRewards, today)

        // Changed on the day it joined, no earlier day was judged by them.
        val sameDay = joined.applyEdit(MeasurementEdit.SetBankRules(maxPasses = 5, everyKeptDays = 4), today)
        assertEquals(Dated.always(5), sameDay.standardRewards!!.maxPasses)
        assertEquals(Dated.always(4), sameDay.standardRewards!!.everyKeptDays)
        assertEquals(joined.standardRewards!!.periods, sameDay.standardRewards!!.periods)

        val later = today.plusDays(8)
        val changed = joined.applyEdit(MeasurementEdit.SetBankRules(maxPasses = 5, everyKeptDays = 4), later)
        assertEquals(Dated(listOf(Dated.Revision(3), Dated.Revision(5, later))), changed.standardRewards!!.maxPasses)
        assertEquals(Dated(listOf(Dated.Revision(7), Dated.Revision(4, later))), changed.standardRewards!!.everyKeptDays)
        assertEquals(3 to 7, changed.standardRewards!!.let { it.maxPasses.on(later.minusDays(1)) to it.everyKeptDays.on(later.minusDays(1)) })
        // Only the number that changed takes a revision.
        assertEquals(Dated.always(3), joined.applyEdit(MeasurementEdit.SetBankRules(maxPasses = 3, everyKeptDays = 14), later).standardRewards!!.maxPasses)

        val twice = changed.applyEdit(MeasurementEdit.SetBankRules(maxPasses = 2, everyKeptDays = 10), later)
        assertEquals(Dated(listOf(Dated.Revision(3), Dated.Revision(2, later))), twice.standardRewards!!.maxPasses)
        assertEquals(Dated(listOf(Dated.Revision(7), Dated.Revision(10, later))), twice.standardRewards!!.everyKeptDays)
        assertEquals(joined, twice.applyEdit(MeasurementEdit.SetBankRules(maxPasses = 3, everyKeptDays = 7), later))

        // Out of the streak again, an earlier period still judged days, so its rules stay dated.
        val left = joined.applyEdit(MeasurementEdit.LeaveRewards, later)
        assertEquals(Dated(listOf(Dated.Revision(3), Dated.Revision(4, later.plusDays(5)))), left.applyEdit(MeasurementEdit.SetBankRules(4, 7), later.plusDays(5)).standardRewards!!.maxPasses)

        listOf(MeasurementEdit.SetBankRules(maxPasses = 0, everyKeptDays = 7), MeasurementEdit.SetBankRules(maxPasses = 3, everyKeptDays = 0)).forEach { edit ->
            assertThrows(edit.toString(), IllegalArgumentException::class.java) { joined.applyEdit(edit, later) }
        }
    }

    /**
     * What a holiday means for a made measurement is dated on its own: it changes from the day it
     * is saved whether or not the measurement counts toward the streak, since its reminder reads
     * it too, and joining or leaving the streak never touches it.
     */
    @Test fun theHolidayRuleOfAMadeMeasurementIsDatedOnItsOwnInAndOutOfTheStreak() {
        val waist = testMadeMeasurement()
        assertEquals(Dated.always(WhenAway.Due), waist.whenAway)

        val notDue = waist.applyEdit(MeasurementEdit.SetWhenAway(WhenAway.Excused), today)
        assertEquals(Dated(listOf(Dated.Revision(WhenAway.Due), Dated.Revision(WhenAway.Excused, today))), notDue.whenAway)
        assertNull(notDue.rewards)
        assertEquals(WhenAway.Due, notDue.whenAway.on(today.minusDays(1)))
        assertEquals(WhenAway.Excused, notDue.whenAway.on(today))
        // Taken back the same day it leaves nothing; a second change that day replaces the first.
        assertEquals(waist, notDue.applyEdit(MeasurementEdit.SetWhenAway(WhenAway.Due), today))
        val dueAgain = notDue.applyEdit(MeasurementEdit.SetWhenAway(WhenAway.Due), today.plusDays(3))
        assertEquals(listOf(null, today, today.plusDays(3)), dueAgain.whenAway.revisions.map { it.from })

        // Joining and leaving keep it, and it changes the same way while it counts.
        val joined = notDue.applyEdit(MeasurementEdit.JoinRewards, today.plusDays(1))
        assertEquals(notDue.whenAway, joined.whenAway)
        val left = joined.applyEdit(MeasurementEdit.LeaveRewards, today.plusDays(5))
        assertEquals(notDue.whenAway, left.whenAway)
        val changedWhileIn = joined.applyEdit(MeasurementEdit.SetWhenAway(WhenAway.Due), today.plusDays(2))
        assertEquals(listOf(null, today, today.plusDays(2)), changedWhileIn.whenAway.revisions.map { it.from })
        assertEquals(joined.rewards, changedWhileIn.rewards)
        // The rule's date is its own: the bank's rules changed on the join day stay plain beside it.
        assertEquals(Dated.always(5), joined.applyEdit(MeasurementEdit.SetBankRules(5, 7), today.plusDays(1)).standardRewards!!.maxPasses)
    }

    /** Joining, the bank's rules and the holiday rule saved together are dated by the day of the save, each as it is alone. */
    @Test fun aMadeMeasurementsStreakEditsAreAppliedInOrderOnTheDayOfTheSave() {
        val waist = testMadeMeasurement()
        val edits = listOf(
            MeasurementEdit.JoinRewards,
            MeasurementEdit.SetBankRules(maxPasses = 4, everyKeptDays = 5),
            MeasurementEdit.SetWhenAway(WhenAway.Excused),
            MeasurementEdit.Rename("Hips"),
        )
        listOf(today, today.plusDays(1)).forEach { day ->
            val saved = waist.applyingEdits(edits, day)
            assertEquals("Hips", saved.label)
            assertEquals(ChecklistRewards(maxPasses = 4, everyKeptDays = 5, periods = listOf(RewardPeriod(day))), saved.standardRewards)
            assertEquals(Dated(listOf(Dated.Revision(WhenAway.Due), Dated.Revision(WhenAway.Excused, day))), saved.whenAway)
            assertEquals(waist.checkpoint, saved.checkpoint)
        }
        // Joined and left before Save, the bank is there with its rules and no day in it.
        val undone = waist.applyingEdits(listOf(MeasurementEdit.JoinRewards, MeasurementEdit.LeaveRewards), today)
        assertEquals(DefaultChecklistRewards.copy(periods = emptyList()), undone.standardRewards)
    }

    /** Saving a made measurement's streak edits reaches its configuration alone: Weight's bank and rule, every record and every other measurement stay. */
    @Test fun aMadeMeasurementsStreakEditsChangeNoRecordAndNoOtherMeasurement() {
        val waist = testMadeMeasurement()
        val mood = testMadeMeasurement(id = "msr_m7xd", label = "Mood", unit = "", step = "1")
        val records = listOf(MeasurementLog(waist.id, "2026-10-13", 5L, 91.5), MeasurementLog(WeightMeasurementId, "2026-10-13", 1L, 81.4), MeasurementLog(mood.id, "2026-10-12", 2L, 7.0))
        val state = AppState(measurementLogs = records, measurements = listOf(weight, waist, mood))
        val edits = listOf(MeasurementEdit.JoinRewards, MeasurementEdit.SetBankRules(4, 5), MeasurementEdit.SetWhenAway(WhenAway.Excused))

        val saved = state.applyingMeasurementEdits(waist.id, edits, today)

        assertEquals(listOf(weight, waist.applyingEdits(edits, today), mood), saved.measurements)
        assertSame(state.measurementLogs, saved.measurementLogs)
        assertEquals(state.copy(measurements = saved.measurements), saved)
        assertEquals(WeightRewards, saved.weightRewards)
        assertEquals(WeightWhenAway, saved.weightWhenAway)
    }

    /**
     * The unit is a label and the step is what the buttons add: changing either converts nothing.
     * Every record stays the very record it was, the measurement's own and every other one's, and
     * so does the other measurement's configuration.
     */
    @Test fun aChangedUnitOrStepChangesNoRecordAndNoOtherMeasurement() {
        val waist = testMadeMeasurement()
        val records = listOf(
            MeasurementLog(waist.id, "2026-10-13", 5L, 91.456),
            MeasurementLog(WeightMeasurementId, "2026-10-13", 1L, 81.4),
            MeasurementLog(waist.id, "2026-10-13", 5L, 91.456),
            MeasurementLog(waist.id, "2026-10-12", 2L, 0.004),
            MeasurementLog(waist.id, "2026-10-11", 3L, -3.0),
        )
        val state = AppState(measurementLogs = records, measurements = listOf(weight, waist))
        listOf(
            listOf(MeasurementEdit.SetUnit("in")),
            listOf(MeasurementEdit.SetUnit("")),
            listOf(MeasurementEdit.SetStep(MeasurementStep("1"))),
            listOf(MeasurementEdit.SetStep(MeasurementStep("0.01"))),
            listOf(MeasurementEdit.SetUnit("mm"), MeasurementEdit.SetStep(MeasurementStep("1")), MeasurementEdit.Rename("Hips")),
        ).forEach { edits ->
            val saved = state.applyingMeasurementEdits(waist.id, edits, today)
            assertEquals(edits.toString(), waist.applyingEdits(edits, today), saved.measurements.single { it.id == waist.id })
            assertSame(edits.toString(), state.measurementLogs, saved.measurementLogs)
            assertEquals(edits.toString(), records, saved.measurementLogs)
            assertEquals(edits.toString(), weight, saved.weightMeasurement)
            assertEquals(edits.toString(), state.copy(measurements = saved.measurements), saved)
        }
        // An edit for a measurement the state does not have changes nothing.
        assertSame(state, state.applyingMeasurementEdits("msr_gone", listOf(MeasurementEdit.SetUnit("in")), today))
    }

    /** A page's edits are applied in order on the day Save runs; one that is refused is left out, so what is shown is what is saved. */
    @Test fun editsAreAppliedInOrderOnTheDayOfTheSaveAndARefusedOneIsLeftOut() {
        val edits = listOf(
            MeasurementEdit.Rename("Scale"),
            MeasurementEdit.SetBankRules(3, 4),
            MeasurementEdit.Rename(" "),
            MeasurementEdit.SetWhenAway(WhenAway.Due),
            MeasurementEdit.Rename("Morning weight"),
        )
        val saved = weight.applyingEdits(edits, today)
        assertEquals("Morning weight", saved.label)
        assertEquals(listOf(null, today), saved.standardRewards!!.maxPasses.revisions.map { it.from })
        assertEquals(listOf(null, today), saved.whenAway.revisions.map { it.from })
        // The same edits saved a day later are dated by that day: a change made before 04:00 and saved after starts on the new day.
        assertEquals(listOf(null, today.plusDays(1)), weight.applyingEdits(edits, today.plusDays(1)).whenAway.revisions.map { it.from })
    }

    /** Saving reaches the configuration and nothing else: no record moves, and edits that change nothing leave the state as it is. */
    @Test fun savingEditsChangesTheConfigurationAndNoRecord() {
        val records = listOf(MeasurementRecord("2026-10-13", 1L, 81.4), MeasurementRecord("2026-10-12", 2L, 81.9))
        val state = AppState(measurementLogs = records.asMeasurementLogs("weight")).withWeight()
        val saved = state.applyingMeasurementEdits(WeightMeasurementId, listOf(MeasurementEdit.SetBankRules(3, 4), MeasurementEdit.SetWhenAway(WhenAway.Due)), today)
        assertEquals(records, saved.recordsOf("weight"))
        assertEquals(state.copy(measurements = saved.measurements), saved)
        assertEquals(3, saved.weightMeasurement!!.standardRewards!!.maxPasses.on(today))
        assertEquals(WhenAway.Due, saved.weightWhenAway.on(today))

        assertSame(state, state.applyingMeasurementEdits(WeightMeasurementId, emptyList(), today))
        // A bank already earning by kept days is left as it is by the numbers it has.
        assertSame(saved, saved.applyingMeasurementEdits(WeightMeasurementId, listOf(MeasurementEdit.SetBankRules(3, 4)), today.plusDays(1)))
        // A state that was never loaded through the repository has no configuration to change.
        val bare = AppState(measurementLogs = records.asMeasurementLogs("weight"))
        assertSame(bare, bare.applyingMeasurementEdits(WeightMeasurementId, listOf(MeasurementEdit.SetBankRules(3, 4)), today))
    }
}

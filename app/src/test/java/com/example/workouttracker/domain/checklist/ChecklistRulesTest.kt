package com.example.workouttracker.domain.checklist

import com.example.workouttracker.domain.reminder.Bedtime
import com.example.workouttracker.domain.compatibility.MorningCareReminderPolicy
import com.example.workouttracker.domain.reminder.ReminderTime
import com.example.workouttracker.domain.compatibility.WakeRelativeReminderPolicy
import com.example.workouttracker.domain.compatibility.fallbackTime
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.Month
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import com.example.workouttracker.domain.holiday.HolidaySchedule

class ChecklistRulesTest {
    private val morning = Checkpoint("morning", fixed("08:00"), remind = true)

    private val medication = ChecklistConfiguration(
        id = "chk_7f3a",
        label = "Medication",
        icon = ViewIcon.Checklist,
        hue = ViewHue.Sky,
        checkpoints = listOf(morning),
        items = listOf(
            ChecklistItem("levo", "Levo"),
            ChecklistItem("iron", "Iron", until = day("2026-11-30")),
            ChecklistItem("vitamin_d", "Vitamin D", months = setOf(Month.DECEMBER, Month.JANUARY), from = day("2026-12-01")),
        ),
    )

    private val timing = Timing(night = Bedtime(LocalTime.of(7, 0), fixedBedtime = null), checkIn = LocalTime.of(20, 0))

    @Test fun eachDateGetsTheEntriesDatedForIt() {
        assertEquals(listOf("levo", "iron"), due("2026-11-30"))
        assertEquals(listOf("levo", "vitamin_d"), due("2026-12-01"))
        assertEquals(listOf("levo"), due("2027-02-01"))
    }

    /** An item added later must not make a day before it incomplete, even when ticked late. */
    @Test fun aLateTickIsJudgedByItsOwnDaysRules() {
        val ticks = setOf("morning/levo", "morning/iron")
        assertTrue(progress("2026-11-30", ticks).isComplete)
        assertFalse(progress("2026-12-01", ticks).isComplete)
    }

    @Test fun onlyATickAtTheCheckpointCountsAndEveryOtherTickIsStillRecorded() {
        val day = progress("2026-11-30", setOf("morning/levo", "evening/iron", "morning/unknown"))
        assertEquals(listOf("iron"), day.checkpoints.single().open.map { it.id })
        assertFalse(day.isComplete)
        assertTrue("ticks that count for nothing are still recorded", "evening/iron" in day.recorded.ticks && "morning/unknown" in day.recorded.ticks)
        assertTrue(progress("2026-11-30", day.recorded.ticks + "morning/iron").isComplete)
    }

    @Test fun anObligationIsOwedFromItsCheckpointWhileItRemindsAndFromTheCheckInOtherwise() {
        val reminding = progress("2026-11-30", setOf("morning/levo")).obligations(timing).single()
        assertEquals(LocalTime.of(8, 0), reminding.since)
        assertEquals(listOf("iron"), reminding.open.map { it.id })

        val silent = medication.copy(checkpoints = listOf(morning.copy(remind = false)))
        assertEquals(LocalTime.of(20, 0), silent.progressOn(day("2026-11-30"), { DayRecord() }, HolidaySchedule()).obligations(timing).single().since)
        assertEquals(emptyList<Obligation>(), progress("2026-11-30", setOf("morning/levo", "morning/iron")).obligations(timing))
    }

    @Test fun aCheckpointRelativeToWakingIsOwedFromTheWakeTimeItIsReadAt() {
        fun owedSince(at: CheckpointTime, wake: LocalTime, fixedBedtime: LocalTime? = null) = medication.copy(checkpoints = listOf(morning.copy(at = at)))
            .progressOn(day("2026-11-30"), { DayRecord() }, HolidaySchedule()).obligations(timing.copy(night = Bedtime(wake, fixedBedtime))).single().since

        assertEquals(LocalTime.of(7, 30), owedSince(CheckpointTime.AfterWake(30), LocalTime.of(7, 0)))
        assertEquals(LocalTime.of(9, 30), owedSince(CheckpointTime.AfterWake(30), LocalTime.of(9, 0)))
        assertEquals(LocalTime.of(22, 45), owedSince(CheckpointTime.BeforeSleep(15), LocalTime.of(7, 0)))
        assertEquals(LocalTime.of(23, 45), owedSince(CheckpointTime.BeforeSleep(15), LocalTime.of(9, 0)))
        // A fixed bedtime moves only what counts back from sleep.
        assertEquals(LocalTime.of(21, 45), owedSince(CheckpointTime.BeforeSleep(15), LocalTime.of(9, 0), LocalTime.of(22, 0)))
        assertEquals(LocalTime.of(9, 30), owedSince(CheckpointTime.AfterWake(30), LocalTime.of(9, 0), LocalTime.of(22, 0)))
    }

    /** A relative checkpoint means exactly what the same words already mean for Weight and evening Care. */
    @Test fun relativeTimesFollowTheRulesWeightAndCareHaveAlwaysHad() {
        (0 until 24 * 60).forEach { minuteOfDay ->
            val wake = LocalTime.of(minuteOfDay / 60, minuteOfDay % 60)
            val alarm = ReminderTime(wake.hour, wake.minute)
            val night = Bedtime(wake, fixedBedtime = null)
            assertEquals("$wake", WakeRelativeReminderPolicy.Weight.fallbackTime(alarm), CheckpointTime.AfterWake(5).on(night))
            assertEquals("$wake", MorningCareReminderPolicy.fallbackTime(alarm), CheckpointTime.AfterWake(5).on(night))
            assertEquals("$wake", WakeRelativeReminderPolicy.Reading.fallbackTime(alarm), CheckpointTime.AfterWake(15).on(night))
            assertEquals("$wake", night.beforeSleep(Duration.ofMinutes(15)), CheckpointTime.BeforeSleep(15).on(night))
        }
    }

    @Test fun anItemRepeatsItsIdOnlyForEntriesThatFollowEachOther() {
        fun withEntries(vararg entries: ChecklistItem) = medication.copy(items = entries.toList())
        withEntries(ChecklistItem("iron", "Iron", until = day("2026-11-30")), ChecklistItem("iron", "Iron", from = day("2026-12-01")))
        listOf(
            listOf(ChecklistItem("iron", "Iron"), ChecklistItem("iron", "Iron")),
            listOf(ChecklistItem("iron", "Iron", until = day("2026-12-01")), ChecklistItem("iron", "Iron", from = day("2026-12-01"))),
            listOf(ChecklistItem("iron", "Iron", from = day("2026-12-01")), ChecklistItem("iron", "Iron")),
        ).forEach { entries -> assertThrows(IllegalArgumentException::class.java) { withEntries(*entries.toTypedArray()) } }
    }

    /** Several checkpoints are stored as a list, but each would need its own save, so Block 4 keeps to one. */
    @Test fun aConfigurationThatCouldNotBeJudgedIsRejected() {
        listOf(
            { medication.copy(checkpoints = emptyList()) },
            { medication.copy(checkpoints = listOf(morning, morning.copy(at = fixed("21:00")))) },
            { medication.copy(items = medication.items + ChecklistItem("iron", "Iron", checkpoints = listOf("evening"))) },
            { ChecklistItem("iron", "Iron", checkpoints = emptyList()) },
            { ChecklistTarget.Weekdays(emptySet()) },
            { ChecklistItem("a/b", "A or B") },
            { ChecklistItem("iron", " Iron") },
            { medication.copy(label = "") },
            { ChecklistItem("iron", "Iron", from = day("2026-12-01"), until = day("2026-11-30")) },
        ).forEach { build -> assertThrows(IllegalArgumentException::class.java) { build() } }
    }

    /** An item due at some checkpoints only, on some weekdays only: each ticks where it is asked. */
    @Test fun anItemIsDueOnlyAtItsCheckpointsAndOnItsWeekdays() {
        val twice = medication.copy(
            checkpoints = listOf(morning, morning.copy(id = "evening", at = fixed("21:00"))),
            items = listOf(
                ChecklistItem("levo", "Levothyroxine", checkpoints = listOf("morning")),
                ChecklistItem("ibu", "Ibuprofen 400"),
                ChecklistItem("iron", "Iron", checkpoints = listOf("evening"), target = ChecklistTarget.Weekdays(setOf(java.time.DayOfWeek.MONDAY))),
            ),
        )
        fun dueOn(date: String) = twice.rulesOn(day(date)).checkpoints.associate { (checkpoint, items) -> checkpoint.id to items.map { it.id } }

        assertEquals(mapOf("morning" to listOf("levo", "ibu"), "evening" to listOf("ibu", "iron")), dueOn("2026-10-05"))
        assertEquals(mapOf("morning" to listOf("levo", "ibu"), "evening" to listOf("ibu")), dueOn("2026-10-06"))
        // Each checkpoint with something open is one gap; the day is complete only when none is.
        val progress = twice.progressOn(day("2026-10-05"), { DayRecord(setOf("morning/levo", "morning/ibu", "evening/ibu")) }, HolidaySchedule())
        assertEquals(listOf("iron"), progress.checkpoints.flatMap { it.open }.map { it.id })
        assertEquals(false, progress.isComplete)
    }

    /** The first day something could be ticked is when an item and a checkpoint it is due at are both in use, whichever of them started later. */
    @Test fun theFirstDayIsWhenAnItemAndItsCheckpointAreBothInUse() {
        assertEquals(null, medication.firstDay)
        val november = day("2026-11-01")
        assertEquals(november, medication.copy(checkpoints = listOf(morning.copy(from = november))).firstDay)
        assertEquals(day("2026-11-05"), medication.copy(items = medication.items.map { it.copy(from = day("2026-11-05")) }).firstDay)
        // An item due only at a later checkpoint starts with that checkpoint, not with the first one.
        val evening = Checkpoint("evening", fixed("21:00"), remind = true, from = day("2026-12-01"))
        val mixed = medication.copy(
            checkpoints = listOf(morning.copy(from = november), evening),
            items = listOf(ChecklistItem("late", "Late", checkpoints = listOf("evening")), ChecklistItem("later", "Later", from = day("2027-01-01"))),
        )
        assertEquals(day("2026-12-01"), mixed.firstDay)
        // An item that ended before its checkpoint began could never be ticked there.
        val never = medication.copy(checkpoints = listOf(morning.copy(from = day("2026-12-01"))), items = listOf(ChecklistItem("iron", "Iron", until = day("2026-11-30"))))
        assertEquals(null, never.firstDay)
    }

    private fun progress(date: String, ticks: Set<String>) = medication.progressOn(day(date), { DayRecord(ticks) }, HolidaySchedule())

    private fun due(date: String): List<String> = medication.rulesOn(day(date)).checkpoints.single().items.map { it.id }

    private fun day(date: String) = LocalDate.parse(date)

    private fun fixed(time: String) = CheckpointTime.Fixed(LocalTime.parse(time))
}

package com.example.workouttracker.feature.settings

import com.example.workouttracker.domain.checklist.ChecklistConfiguration
import com.example.workouttracker.domain.checklist.ChecklistEdit
import com.example.workouttracker.domain.checklist.ChecklistItem
import com.example.workouttracker.domain.checklist.ChecklistTarget
import com.example.workouttracker.domain.checklist.Checkpoint
import com.example.workouttracker.domain.checklist.CheckpointTime
import com.example.workouttracker.domain.checklist.Dated
import com.example.workouttracker.domain.checklist.MoMotif
import com.example.workouttracker.domain.checklist.ViewHue
import com.example.workouttracker.domain.checklist.ViewIcon
import com.example.workouttracker.domain.checklist.WhenAway
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChecklistPickersTest {
    /**
     * The Streak page's "Only log misses" list is read from the page's draft: an item added and not
     * saved is listed, one checked and then made due a number of times a week is gone, and made
     * daily again it is listed unchecked, because the change cleared the choice.
     */
    @Test
    fun theItemsThatCanLogOnlyMissesAreTheDraftsOwn() {
        val today = LocalDate.parse("2026-10-14")
        val teeth = ChecklistConfiguration(
            id = "chk_teeth",
            label = "Teeth",
            icon = ViewIcon.Toothbrush,
            hue = ViewHue.Teal,
            checkpoints = listOf(Checkpoint("cpt_e", CheckpointTime.Fixed(LocalTime.of(21, 0)), remind = false)),
            items = listOf(
                ChecklistItem("itm_b", "Brush"),
                ChecklistItem("itm_f", "Floss", target = ChecklistTarget.PerWeek(times = 1)),
                ChecklistItem("itm_r", "Retainer", target = ChecklistTarget.Weekdays(setOf(DayOfWeek.SUNDAY))),
                ChecklistItem("itm_old", "Mouthwash", until = today.minusDays(1)),
            ),
        )
        val page = ChecklistDraft(teeth)
        fun edit(edit: ChecklistEdit) = assertTrue(page.edit(edit, today, recordedDates = emptySet()))
        fun listed() = page.draft(today, recordedDates = emptySet()).itemsThatCanLogOnlyMisses(today)

        assertEquals(listOf("itm_b", "itm_r"), listed().map { it.id })

        edit(ChecklistEdit.AddItem("itm_n", "Rinse"))
        assertEquals(listOf("itm_b", "itm_r", "itm_n"), listed().map { it.id })

        edit(ChecklistEdit.SetItemExceptionsOnly("itm_b", true))
        assertTrue(listed().single { it.id == "itm_b" }.exceptionsOnly.on(today))
        edit(ChecklistEdit.SetItemTarget("itm_b", ChecklistTarget.PerWeek(times = 3)))
        assertEquals(listOf("itm_r", "itm_n"), listed().map { it.id })

        edit(ChecklistEdit.SetItemTarget("itm_b", null))
        assertEquals(listOf("itm_b", "itm_r", "itm_n"), listed().map { it.id })
        assertFalse(listed().single { it.id == "itm_b" }.exceptionsOnly.on(today))
        // Nothing reached the stored configuration: the list was the draft's all along.
        assertEquals(teeth, page.stored)
    }

    /** Every item can stay due or be excused on holiday; one due more than once a week can ask fewer times, and one that already does keeps the choice. */
    @Test
    fun fewerTimesOnHolidayIsOfferedToAnItemDueMoreThanOnceAWeek() {
        val all = listOf(WhenAway.Due, WhenAway.Excused, WhenAway.LowerTarget)
        assertEquals(all, awayOptions(ChecklistTarget.PerDay, WhenAway.Due))
        assertEquals(all, awayOptions(ChecklistTarget.Weekdays(setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY)), WhenAway.Due))
        assertEquals(listOf(WhenAway.Due, WhenAway.Excused), awayOptions(ChecklistTarget.Weekdays(setOf(DayOfWeek.SATURDAY)), WhenAway.Due))
        assertEquals(all, awayOptions(ChecklistTarget.PerWeek(times = 3), WhenAway.Due))
        assertEquals(listOf(WhenAway.Due, WhenAway.Excused), awayOptions(ChecklistTarget.PerWeek(times = 1), WhenAway.Due))
        assertEquals(all, awayOptions(ChecklistTarget.PerWeek(times = 1), WhenAway.LowerTarget))
    }

    /** A once-a-week item that opened lowered keeps fewer times only while its target is the one it opened with. */
    @Test
    fun anOpenedChoiceIsKeptOnlyWhileTheTargetIsUnchanged() {
        val today = LocalDate.parse("2026-10-14")
        val all = listOf(WhenAway.Due, WhenAway.Excused, WhenAway.LowerTarget)
        val notLowering = listOf(WhenAway.Due, WhenAway.Excused)
        val floss = ChecklistItem("itm_f", "Floss", target = ChecklistTarget.PerWeek(times = 1), whenAway = Dated.always(WhenAway.LowerTarget))
        val flossDue = floss.copy(whenAway = Dated.always(WhenAway.Due))
        assertEquals(all, awayOptionsOf(flossDue, opened = floss, ChecklistTarget.PerDay, today))
        assertEquals(notLowering, awayOptionsOf(flossDue.copy(target = ChecklistTarget.Weekdays(setOf(DayOfWeek.SATURDAY))), opened = floss, ChecklistTarget.PerDay, today))

        val violin = ChecklistItem("itm_v", "Violin", target = ChecklistTarget.PerWeek(times = 5), whenAway = Dated.always(WhenAway.LowerTarget), awayTimes = 2)
        val violinOnce = violin.copy(target = ChecklistTarget.PerWeek(times = 1), whenAway = Dated.always(WhenAway.Due), awayTimes = null)
        assertEquals(notLowering, awayOptionsOf(violinOnce, opened = violin, ChecklistTarget.PerDay, today))
    }

    /** No count offered means the same as still due or not due, for a week item as for any other. */
    @Test
    fun theCountsOfferedLeaveOutStillDueAndNotDue() {
        assertEquals(1..6, awayTimesOffered(ChecklistTarget.PerDay))
        assertEquals(1..2, awayTimesOffered(ChecklistTarget.Weekdays(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY))))
        assertEquals(1..2, awayTimesOffered(ChecklistTarget.PerWeek(times = 3)))
        assertTrue(awayTimesOffered(ChecklistTarget.PerWeek(times = 1)).isEmpty())
    }

    /** A count a week item stored before Block 17, none while lowering or nothing, shows as its own tile while it is the item's. */
    @Test
    fun aCountStoredBeforeBlock17ShowsAsItsOwnTile() {
        val violin = ChecklistItem("itm_v", "Violin", target = ChecklistTarget.PerWeek(times = 5))
        assertEquals(5, legacyAwayTimes(violin, violin.target!!, WhenAway.LowerTarget))
        assertEquals(0, legacyAwayTimes(violin.copy(awayTimes = 0), violin.target!!, WhenAway.LowerTarget))
        // The count is kept whichever rule the item has, so nothing shows for none while still due, and 0 keeps its tile.
        assertEquals(null, legacyAwayTimes(violin, violin.target!!, WhenAway.Due))
        assertEquals(0, legacyAwayTimes(violin.copy(awayTimes = 0), violin.target!!, WhenAway.Due))
        assertEquals(null, legacyAwayTimes(violin.copy(awayTimes = 2), violin.target!!, WhenAway.LowerTarget))
        // Once a week lowered earlier: the one tile of its count, 0 or the usual one.
        val floss = ChecklistItem("itm_f", "Floss", target = ChecklistTarget.PerWeek(times = 1))
        assertEquals(1, legacyAwayTimes(floss, floss.target!!, WhenAway.LowerTarget))
        assertEquals(0, legacyAwayTimes(floss.copy(awayTimes = 0), floss.target!!, WhenAway.LowerTarget))
    }

    /** Fewer times chosen for the first time brings about half the usual count with it, at least one, before the rule. */
    @Test
    fun choosingFewerTimesForTheFirstTimePicksAboutHalfTheUsualCount() {
        val monstera = ChecklistItem("itm_1", "Water Monstera")
        val lower = ChecklistEdit.SetItemWhenAway("itm_1", WhenAway.LowerTarget)
        assertEquals(listOf(ChecklistEdit.SetItemAwayTimes("itm_1", 3), lower), fewerTimesEdits(monstera, ChecklistTarget.PerDay, WhenAway.LowerTarget))
        assertEquals(listOf(ChecklistEdit.SetItemAwayTimes("itm_1", 1), lower), fewerTimesEdits(monstera, ChecklistTarget.Weekdays(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)), WhenAway.LowerTarget))
        assertEquals(listOf(ChecklistEdit.SetItemAwayTimes("itm_1", 1), lower), fewerTimesEdits(monstera, ChecklistTarget.Weekdays(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY)), WhenAway.LowerTarget))
        assertEquals(listOf(ChecklistEdit.SetItemAwayTimes("itm_1", 2), lower), fewerTimesEdits(monstera, ChecklistTarget.PerWeek(times = 5), WhenAway.LowerTarget))
        // A count once chosen is found again, once a week has nothing to pick, and the other rules bring nothing with them.
        assertEquals(listOf(lower), fewerTimesEdits(monstera.copy(awayTimes = 5), ChecklistTarget.PerDay, WhenAway.LowerTarget))
        // An item that lowered without a count before Block 17 asked its usual count; back from still due, it asks that again.
        assertEquals(listOf(lower), fewerTimesEdits(monstera, ChecklistTarget.PerWeek(times = 5), WhenAway.LowerTarget, legacy = 5))
        assertEquals(listOf(lower), fewerTimesEdits(monstera, ChecklistTarget.PerWeek(times = 1), WhenAway.LowerTarget))
        assertEquals(listOf(ChecklistEdit.SetItemWhenAway("itm_1", WhenAway.Excused)), fewerTimesEdits(monstera, ChecklistTarget.PerDay, WhenAway.Excused))
    }

    /** A picker's last row is never short, so each grid of options reads as one block. */
    @Test
    fun everyPickerFillsItsLastRow() {
        assertEquals(0, ViewIcon.entries.size % PickerColumns)
        assertEquals(0, PickerHues.size % PickerColumns)
        // Plain has its own row; the motifs below it fill theirs.
        assertEquals(0, MoMotif.entries.size % MotifColumns)
    }

    /** Red means a warning, so it is the one hue never offered; every other is offered once. */
    @Test
    fun theColorPickerOffersEveryHueButRedOnce() {
        assertEquals(ViewHue.entries - ViewHue.Red, PickerHues.sortedBy { it.ordinal })
    }
}

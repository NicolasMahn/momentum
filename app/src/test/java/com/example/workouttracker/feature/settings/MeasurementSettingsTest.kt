package com.example.workouttracker.feature.settings

import com.example.workouttracker.domain.compatibility.WeightRewards
import com.example.workouttracker.domain.checklist.ChecklistRewards
import com.example.workouttracker.domain.measurement.EarlierWeeks
import com.example.workouttracker.domain.measurement.MeasurementBank
import org.junit.Assert.assertNull
import com.example.workouttracker.domain.AppState
import com.example.workouttracker.domain.checklist.Dated
import com.example.workouttracker.domain.checklist.ViewHue
import com.example.workouttracker.domain.checklist.ViewIcon
import com.example.workouttracker.domain.checklist.WhenAway
import com.example.workouttracker.domain.measurement.MeasurementConfiguration
import com.example.workouttracker.domain.measurement.MeasurementEdit
import com.example.workouttracker.domain.measurement.testWeightMeasurement
import com.example.workouttracker.domain.compatibility.weightMeasurement
import com.example.workouttracker.domain.measurement.withWeight
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Weight's page keeps the ordered edits to its configuration as a draft, its reminder among them,
 * shown over what is stored; whether anything is left to save is read, never remembered.
 */
class MeasurementSettingsTest {
    private val today = LocalDate.parse("2026-10-14")

    private val weight: MeasurementConfiguration get() = requireNotNull(AppState().withWeight().weightMeasurement)

    /** The edits are replayed in order over the stored configuration, and one it refuses is not kept. */
    @Test
    fun theDraftKeepsItsEditsInOrderOverWhatIsStored() {
        val draft = MeasurementDraft(weight, today)
        assertFalse(draft.isDirty)
        assertTrue(draft.edit(MeasurementEdit.Rename("Scale")))
        assertTrue(draft.edit(MeasurementEdit.SetBankRules(maxPasses = 3, everyKeptDays = 4)))
        assertTrue(draft.edit(MeasurementEdit.Rename("Morning weight")))
        assertFalse(draft.edit(MeasurementEdit.SetWhenAway(WhenAway.LowerTarget)))
        assertFalse(draft.edit(MeasurementEdit.SetBankRules(maxPasses = 0, everyKeptDays = 4)))
        assertTrue(draft.edit(MeasurementEdit.SetIcon(ViewIcon.Heart)))

        assertEquals(
            listOf(MeasurementEdit.Rename("Scale"), MeasurementEdit.SetBankRules(3, 4), MeasurementEdit.Rename("Morning weight"), MeasurementEdit.SetIcon(ViewIcon.Heart)),
            draft.edits,
        )
        assertEquals("Morning weight", draft.edited.label)
        assertEquals(ViewIcon.Heart, draft.edited.icon)
        assertEquals(Dated.always(4), draft.edited.standardRewards!!.everyKeptDays)
        // Nothing reached the stored configuration.
        assertEquals(testWeightMeasurement(), draft.stored)
        assertTrue(draft.isDirty && draft.canSave && draft.hasUnsavedWork)

        // A name stored meanwhile by another route stays under the edits made here.
        draft.follow(weight.copy(hue = ViewHue.Plum), today)
        assertEquals(ViewHue.Plum, draft.edited.hue)
        assertEquals("Morning weight", draft.edited.label)
    }

    /** Dirty is read, not remembered: edits that take each other back leave nothing to save or to ask about. */
    @Test
    fun editsThatTakeEachOtherBackLeaveNothingToSave() {
        val draft = MeasurementDraft(weight, today)
        draft.edit(MeasurementEdit.Rename("Scale"))
        draft.edit(MeasurementEdit.SetWhenAway(WhenAway.Due))
        draft.edit(MeasurementEdit.Rename(weight.label))
        draft.edit(MeasurementEdit.SetWhenAway(WhenAway.Excused))
        assertEquals(4, draft.edits.size)
        assertFalse(draft.isDirty)
        assertFalse(draft.canSave)
        assertFalse(draft.hasUnsavedWork)
    }

    /**
     * Weight's bank as it was stored earns by the week. A number changed on its page makes it earn
     * by kept days from the day of the save, and setting the number back takes that change not
     * back: the page still has it to save, with the numbers the week rule had.
     */
    @Test
    fun aChangedNumberMakesAWeekBankEarnByKeptDaysAlsoOnceItIsSetBack() {
        val draft = MeasurementDraft(weight, today)
        assertEquals(WeightRewards, draft.edited.weekRewards)

        assertTrue(draft.edit(MeasurementEdit.SetBankRules(maxPasses = 7, everyKeptDays = 7)))
        assertTrue(draft.edit(MeasurementEdit.SetBankRules(maxPasses = 7, everyKeptDays = 6)))

        assertTrue(draft.isDirty && draft.canSave)
        assertNull(draft.edited.weekRewards)
        assertEquals(
            MeasurementBank.Standard(ChecklistRewards(maxPasses = 7, everyKeptDays = 6), EarlierWeeks(Dated.always(6), until = today.minusDays(1))),
            draft.edited.rewards,
        )
        // The stored configuration earns by the week until Save.
        assertEquals(MeasurementBank.Week(WeightRewards), draft.stored.rewards)

        // Once stored, replaying the edits changes nothing, so nothing is left to save.
        draft.follow(draft.edited, today)
        assertFalse(draft.isDirty)
    }
}

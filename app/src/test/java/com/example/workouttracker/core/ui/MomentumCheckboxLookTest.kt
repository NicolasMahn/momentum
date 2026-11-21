package com.example.workouttracker.core.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class MomentumCheckboxLookTest {
    /**
     * A box names one look, and what a toggle and TalkBack read follows from it: a cross is as
     * unchecked as the empty box, and a box that only ticks never shows a cross or an assumed check.
     */
    @Test
    fun aCrossIsNeverCheckedAndABoxThatOnlyTicksNeverShowsOne() {
        assertEquals(
            mapOf(
                MomentumCheckboxLook.Empty to false,
                MomentumCheckboxLook.Recorded to true,
                MomentumCheckboxLook.Assumed to true,
                MomentumCheckboxLook.Crossed to false,
            ),
            MomentumCheckboxLook.entries.associateWith { it.isChecked },
        )
        assertEquals(MomentumCheckboxLook.Recorded, MomentumCheckboxLook(checked = true))
        assertEquals(MomentumCheckboxLook.Empty, MomentumCheckboxLook(checked = false))
    }
}

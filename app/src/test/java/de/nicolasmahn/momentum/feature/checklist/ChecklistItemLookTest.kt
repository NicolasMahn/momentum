package de.nicolasmahn.momentum.feature.checklist

import de.nicolasmahn.momentum.core.ui.MomentumCheckboxLook
import de.nicolasmahn.momentum.domain.checklist.DayRecord
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * What an item's box shows for the mark its draft holds. The marks and what a press makes of them
 * are `ChecklistModelsTest`'s; this is their look, and the cross belongs to a miss alone.
 */
class ChecklistItemLookTest {
    private val tick = "midday/creatine"

    @Test
    fun aMissWearsTheCrossAndSwingsWithTheCheckItReplaced() {
        // A saved miss opens as a cross, and taking it back is the assumed check again.
        val missed = DayRecord(misses = setOf(tick))
        assertEquals(MomentumCheckboxLook.Crossed, missed.lookOf(logsOnlyMisses = true))
        val takenBack = missed.toggling(tick, logsOnlyMisses = true, storedTick = false)
        assertEquals(MomentumCheckboxLook.Assumed, takenBack.lookOf(logsOnlyMisses = true))
        assertEquals(MomentumCheckboxLook.Crossed, takenBack.toggling(tick, logsOnlyMisses = true, storedTick = false).lookOf(logsOnlyMisses = true))

        // A recorded tick marked missed is a cross too, and comes back as the recorded tick it was.
        val ticked = DayRecord(ticks = setOf(tick))
        assertEquals(MomentumCheckboxLook.Recorded, ticked.lookOf(logsOnlyMisses = true))
        val tickMissed = ticked.toggling(tick, logsOnlyMisses = true, storedTick = true)
        assertEquals(MomentumCheckboxLook.Crossed, tickMissed.lookOf(logsOnlyMisses = true))
        assertEquals(MomentumCheckboxLook.Recorded, tickMissed.toggling(tick, logsOnlyMisses = true, storedTick = true).lookOf(logsOnlyMisses = true))
    }

    @Test
    fun anItemNotTickedYetKeepsTheEmptyBox() {
        assertEquals(MomentumCheckboxLook.Empty, DayRecord().lookOf(logsOnlyMisses = false))
        assertEquals(MomentumCheckboxLook.Recorded, DayRecord(ticks = setOf(tick)).lookOf(logsOnlyMisses = false))
    }

    /** A miss left on an item since made weekly judges nothing, and is still shown until the next press ticks it. */
    @Test
    fun aMissOnAnItemThatTicksAgainKeepsItsCrossUntilItIsTicked() {
        val missed = DayRecord(misses = setOf(tick))
        assertEquals(MomentumCheckboxLook.Crossed, missed.lookOf(logsOnlyMisses = false))
        assertEquals(MomentumCheckboxLook.Recorded, missed.toggling(tick, logsOnlyMisses = false, storedTick = false).lookOf(logsOnlyMisses = false))
    }

    /** What TalkBack hears as checked is the mark's own answer: a cross is as unchecked as an open item. */
    @Test
    fun theBoxIsCheckedExactlyWhenItsMarkIs() {
        ItemMark.entries.forEach { assertEquals(it.name, it.isChecked, it.look.isChecked) }
    }

    private fun DayRecord.lookOf(logsOnlyMisses: Boolean): MomentumCheckboxLook = markOf(tick, logsOnlyMisses).look
}

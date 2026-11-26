package de.nicolasmahn.momentum.feature.mo

import de.nicolasmahn.momentum.domain.mo.MoEvent
import de.nicolasmahn.momentum.domain.mo.MoLevel
import de.nicolasmahn.momentum.domain.mo.MoLevelArrival
import de.nicolasmahn.momentum.domain.mo.MoStreakLoss
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** A lost streak and a level wait for their own screen: no other event hands them over. */
class MoEventLedgerTest {
    private val lost = MoEvent.StreakLost(MoStreakLoss(23, LocalDate.parse("2026-11-11")))
    private val level = MoEvent.LevelReached(MoLevelArrival(MoLevel.Spark, LocalDate.parse("2026-11-12"), LocalDate.parse("2026-11-06")))

    @Test fun aLossComesFirstAndDismissingAnotherEventNeverMarksItSeen() {
        val events = listOf(lost, level, MoEvent.Comeback)
        var ledger = MoEventLedger()
        assertEquals(lost, ledger.next(events))

        // Another event handed over first, as when it played in front: the loss still waits.
        ledger = ledger.handedOver(MoEvent.Comeback, events)
        assertEquals(lost, ledger.next(events))

        ledger = ledger.handedOver(lost, events)
        assertEquals(level, ledger.next(events))
        ledger = ledger.handedOver(level, events)
        assertNull(ledger.next(events))
        assertEquals(setOf("lost/2026-11-11", "level/2026-11-06/Spark"), ledger.momentsSeen)
    }

    @Test fun seeingTheLossHandsNoOrdinaryEventOver() {
        val events = listOf(lost, MoEvent.PerfectWeek)
        val ledger = MoEventLedger().handedOver(lost, events)
        assertEquals(MoEvent.PerfectWeek, ledger.next(events))
    }
}

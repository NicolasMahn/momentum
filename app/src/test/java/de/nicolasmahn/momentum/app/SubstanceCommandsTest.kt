package de.nicolasmahn.momentum.app

import de.nicolasmahn.momentum.domain.AppState
import de.nicolasmahn.momentum.domain.checklist.CheckpointTime
import de.nicolasmahn.momentum.domain.checklist.Dated
import de.nicolasmahn.momentum.domain.substance.OwnSubstance
import de.nicolasmahn.momentum.domain.substance.SubstanceAmountUnit
import de.nicolasmahn.momentum.domain.substance.SubstanceCheckIn
import de.nicolasmahn.momentum.domain.substance.SubstanceEdit
import de.nicolasmahn.momentum.domain.substance.SubstanceGoal
import de.nicolasmahn.momentum.domain.substance.SubstanceKind
import de.nicolasmahn.momentum.domain.substance.SubstanceLog
import de.nicolasmahn.momentum.domain.substance.SubstanceSettings
import de.nicolasmahn.momentum.domain.substance.joiningStreak
import de.nicolasmahn.momentum.persistence.TrackerRepository
import de.nicolasmahn.momentum.persistence.TrackerUpdateResult
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Substances page's save applies its edits on the habit day it runs and stands only with the
 * check-in's alarm behind it, as a checklist's does.
 */
class SubstanceCommandsTest {
    private val today = LocalDate.parse("2026-10-06")
    private val remindAt21 = listOf(SubstanceEdit.Remind(true), SubstanceEdit.CheckInAt(CheckpointTime.Fixed(LocalTime.of(21, 0))))
    private val remindingAt21 = SubstanceSettings.Legacy.copy(checkIn = SubstanceCheckIn(remind = true, at = CheckpointTime.Fixed(LocalTime.of(21, 0))))

    private inner class Fixture(state: AppState, var alarmsSet: Boolean = true, val exactAllowed: Boolean = true, val day: LocalDate = today) {
        val repository = FakeTrackerRepository(state)
        val reconciled = mutableListOf<AppState>()
        var refreshed = 0
        val commands = SubstanceCommands(
            repository = repository,
            reconcile = { reconciled += it; alarmsSet },
            exactAlarmsAllowed = { exactAllowed },
            refresh = { refreshed++ },
            today = { day },
        )
        val stored: SubstanceSettings get() = repository.load().substances
    }

    @Test fun aSaveStoresTheEditsAndSetsItsAlarm() {
        val fixture = Fixture(AppState(substances = SubstanceSettings.Legacy))
        assertTrue(fixture.commands.save(remindAt21))
        assertEquals(remindingAt21, fixture.stored)
        assertEquals(listOf(remindingAt21), fixture.reconciled.map { it.substances })
    }

    @Test fun aSaveWhoseNewAlarmCannotBeSetIsTakenBack() {
        val fixture = Fixture(AppState(substances = SubstanceSettings.Legacy), alarmsSet = false)
        assertFalse(fixture.commands.save(remindAt21))
        assertEquals(SubstanceSettings.Legacy, fixture.stored)
        assertEquals(listOf(remindingAt21, SubstanceSettings.Legacy), fixture.reconciled.map { it.substances })
        assertEquals(0, fixture.refreshed)

        // A new time while on asks for a new alarm too.
        val moved = Fixture(AppState(substances = remindingAt21), alarmsSet = false)
        assertFalse(moved.commands.save(listOf(SubstanceEdit.CheckInAt(CheckpointTime.BeforeSleep(10)))))
        assertEquals(remindingAt21, moved.stored)
    }

    @Test fun withoutAlarmAccessOrWithoutANewAlarmASaveStands() {
        val noAccess = Fixture(AppState(substances = SubstanceSettings.Legacy), alarmsSet = false, exactAllowed = false)
        assertTrue(noAccess.commands.save(remindAt21))
        assertEquals(remindingAt21, noAccess.stored)

        val switchedOff = Fixture(AppState(substances = remindingAt21), alarmsSet = false)
        assertTrue(switchedOff.commands.save(listOf(SubstanceEdit.Remind(false), SubstanceEdit.StayAway(SubstanceKind.CANNABIS.id, false))))
        val off = remindingAt21.copy(
            checkIn = remindingAt21.checkIn.copy(remind = false),
            stayingAway = Dated.always(SubstanceSettings.Legacy.tracked - SubstanceKind.CANNABIS.id),
        )
        assertEquals(off, switchedOff.stored)
    }

    /** The edits are applied on the day Save runs: made before 04:00 and saved after, every dated change is dated by the save's day. */
    @Test fun everyDatedChangeIsDatedByTheDayTheSaveRuns() {
        val tomorrow = today.plusDays(1)
        val joined = SubstanceSettings.Legacy.joiningStreak(today.minusDays(10))
        val fixture = Fixture(AppState(substances = joined), day = tomorrow)
        val edits = listOf(
            SubstanceEdit.SetGoal(SubstanceGoal.StayingAway),
            SubstanceEdit.StayAway(SubstanceKind.CANNABIS.id, false),
            SubstanceEdit.SetExceptionsOnly(false),
            SubstanceEdit.SetRewardRules(5, 7),
        )

        assertTrue(fixture.commands.save(edits))

        val stored = fixture.stored
        assertEquals(listOf(null, tomorrow), stored.goal.revisions.map { it.from })
        assertEquals(SubstanceGoal.Logging, stored.goal.on(today))
        assertEquals(SubstanceGoal.StayingAway, stored.goal.on(tomorrow))
        assertEquals(listOf(null, tomorrow), stored.stayingAway.revisions.map { it.from })
        assertEquals(listOf(null, tomorrow), stored.exceptionsOnly.revisions.map { it.from })
        assertEquals(3, stored.rewards?.maxPasses?.on(today))
        assertEquals(5, stored.rewards?.maxPasses?.on(tomorrow))
    }

    /** An edit the records refuse by the time Save runs, such as removing a substance logged meanwhile, is left out; the rest stands. */
    @Test fun anEditTheRecordsRefuseIsLeftOut() {
        val own = OwnSubstance("sub_k2p9", "Kratom", SubstanceAmountUnit.GRAMS)
        val withRecord = AppState(
            substances = SubstanceSettings(tracked = setOf(own.id), own = listOf(own)),
            substanceLogs = listOf(SubstanceLog("k1", own.id, "2026-10-06", 1L)),
        )
        val fixture = Fixture(withRecord)
        assertTrue(fixture.commands.save(listOf(SubstanceEdit.RemoveOwn(own.id), SubstanceEdit.Track(SubstanceKind.COFFEE.id, true))))
        assertEquals(SubstanceSettings(tracked = setOf(own.id, SubstanceKind.COFFEE.id), own = listOf(own)), fixture.stored)
        assertEquals(1, fixture.reconciled.size)
    }

    private class FakeTrackerRepository(private var state: AppState) : TrackerRepository {
        override fun load(): AppState = state
        override fun save(state: AppState): Boolean = true.also { this.state = state }
        override fun update(transform: (AppState) -> AppState): TrackerUpdateResult {
            val updated = runCatching { transform(state) }.getOrElse { return TrackerUpdateResult.SaveFailed }
            state = updated
            return TrackerUpdateResult.Updated(updated)
        }
    }
}

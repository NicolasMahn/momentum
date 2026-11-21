package com.example.workouttracker.persistence.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.workouttracker.domain.AppState
import com.example.workouttracker.domain.checklist.ChecklistDay
import com.example.workouttracker.domain.measurement.MeasurementLog
import com.example.workouttracker.domain.questions.QuestionEntry
import com.example.workouttracker.domain.substance.SubstanceAmountUnit
import com.example.workouttracker.domain.substance.SubstanceLog
import com.example.workouttracker.domain.substance.SubstanceNoneDay
import com.example.workouttracker.persistence.CurrentTrackerStore
import com.example.workouttracker.persistence.TrackerUpdateResult
import com.example.workouttracker.persistence.backup.CurrentBackupFixture
import com.example.workouttracker.persistence.rows.SqliteRowStore
import com.example.workouttracker.persistence.rows.SyncMarks
import com.example.workouttracker.persistence.rows.SyncedRow
import com.example.workouttracker.persistence.trackerRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Two copies of one person's data, a phone and a tablet, brought together through a server. */
@RunWith(RobolectricTestRunner::class)
class SyncTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val server = MemorySyncServer()
    private val phone = Device("phone")
    private val tablet = Device("tablet")
    private val fixture = CurrentBackupFixture.state

    private inner class Device(name: String) {
        var now = 1_000L
        val rows = SqliteRowStore(context, name = "$name.db", clock = { now })
        val repository = trackerRepository(CurrentTrackerStore(), rows = rows)

        fun sync() = repository.sync(server)
        fun state() = requireNotNull(repository.loadIfReadable())
        fun change(transform: (AppState) -> AppState) = assertTrue(repository.update(transform) is TrackerUpdateResult.Updated)

        /** What this copy shares, every row by its ID, without the order its collections are in here. */
        fun held() = rows.rows().filter { it.kind != "timerSessions" }.map { Triple(it.kind, it.id, it.body) }.sortedWith(compareBy({ it.first }, { it.second }))
    }

    private fun bothHoldTheFixture() {
        assertTrue(phone.repository.save(fixture))
        assertEquals(SyncOutcome.Synced::class, phone.sync()::class)
        tablet.state()
        tablet.sync()
        assertEquals(phone.held(), tablet.held())
    }

    private fun syncBoth() {
        phone.sync(); tablet.sync(); phone.sync()
    }

    /**
     * A new installation's defaults never win over a person's own settings, and what each copy
     * recorded or removed while apart is on both afterwards: a weigh-in added on one, a checklist
     * day removed on the other.
     */
    @Test fun twoCopiesThatChangedApartEndHoldingTheSameRecords() {
        bothHoldTheFixture()
        // Everything but the phone's own timer ledger.
        assertEquals(fixture.copy(recordedTimeSessions = emptySet()), tablet.state())

        val weighIn = MeasurementLog("msr_custom", "2026-10-20", 200, 81.5)
        phone.now = 2_000
        phone.change { it.copy(measurementLogs = it.measurementLogs + weighIn) }
        tablet.now = 3_000
        tablet.change { it.copy(checklistDays = emptyList()) }
        syncBoth()

        assertEquals(phone.held(), tablet.held())
        assertTrue(weighIn in tablet.state().measurementLogs)
        assertEquals(emptyList<ChecklistDay>(), phone.state().checklistDays)
    }

    /** One setting changed on both copies keeps the later change everywhere. */
    @Test fun theLaterOfTwoChangesToOneViewStays() {
        bothHoldTheFixture()
        phone.now = 5_000
        phone.change { state -> state.copy(checklists = state.checklists.map { it.copy(label = "Phone") }) }
        tablet.now = 4_000
        tablet.change { state -> state.copy(checklists = state.checklists.map { it.copy(label = "Tablet") }) }
        syncBoth()
        assertEquals(listOf("Phone"), tablet.state().checklists.map { it.label })
        assertEquals(phone.held(), tablet.held())
    }

    /**
     * A phone whose clock is behind still stamps a change after everything it received: the
     * tablet's clock runs ahead, the phone changes the view after hearing of it, and the phone's
     * change is the later one.
     */
    @Test fun aClockBehindStillStampsAfterWhatItReceived() {
        bothHoldTheFixture()
        tablet.now = 90_000
        tablet.change { state -> state.copy(checklists = state.checklists.map { it.copy(label = "Tablet") }) }
        tablet.sync()
        phone.now = 1_500
        phone.sync()
        phone.change { state -> state.copy(checklists = state.checklists.map { it.copy(label = "Phone, after") }) }
        syncBoth()
        assertEquals(listOf("Phone, after"), tablet.state().checklists.map { it.label })
    }

    /** The rules allow one questions entry a day: answered on both copies, the later answer is the one kept, everywhere. */
    @Test fun theSameDayAnsweredOnTwoCopiesEndsAsOneEntry() {
        bothHoldTheFixture()
        phone.now = 2_000
        phone.change { it.copy(questionEntries = it.questionEntries + QuestionEntry("on-phone", "qst_custom", "2026-10-20", 2_000, mapOf("rest" to "low"))) }
        tablet.now = 3_000
        tablet.change { it.copy(questionEntries = it.questionEntries + QuestionEntry("on-tablet", "qst_custom", "2026-10-20", 3_000, mapOf("rest" to "high"))) }
        syncBoth()

        listOf(phone, tablet).forEach { device ->
            assertEquals(listOf("entry", "on-tablet"), device.state().questionEntries.map { it.id })
        }
        assertEquals(phone.held(), tablet.held())
    }

    /**
     * A screen that read before a sync saves only what it changed: the weigh-in the sync brought
     * stays. Saved whole, as before, the screen's older state would have removed it.
     */
    @Test fun aScreenThatReadBeforeASyncKeepsWhatTheSyncBrought() {
        bothHoldTheFixture()
        val weighIn = MeasurementLog("msr_custom", "2026-10-21", 300, 80.0)
        tablet.now = 2_000
        tablet.change { it.copy(measurementLogs = it.measurementLogs + weighIn) }
        tablet.sync()

        val shown = phone.state()
        phone.now = 3_000
        phone.sync()
        val tick = ChecklistDay("chk_custom", "2026-10-21", 301, setOf("day/stretch"), emptySet())
        assertTrue(phone.repository.save(shown.copy(checklistDays = shown.checklistDays + tick), base = shown))

        val now = phone.state()
        assertTrue(weighIn in now.measurementLogs)
        assertTrue(tick in now.checklistDays)
    }

    /** Two weigh-ins of one day the phone kept on purpose both stay when the tablet corrects one of them. */
    @Test fun aKeptPairOfRecordsSurvivesAnEditOnTheOtherCopy() {
        bothHoldTheFixture()
        val twins = fixture.measurementLogs.filter { it.measurementId == "deleted_measurement" }
        tablet.now = 2_000
        tablet.change { state -> state.copy(measurementLogs = state.measurementLogs.map { if (it === state.measurementLogs.first { log -> log.measurementId == "deleted_measurement" }) it.copy(value = 90.0) else it }) }
        syncBoth()
        listOf(phone, tablet).forEach { device ->
            assertEquals(listOf(90.0, twins.last().value), device.state().measurementLogs.filter { it.measurementId == "deleted_measurement" }.map { it.value })
        }
        assertEquals(phone.held(), tablet.held())
    }

    /**
     * A screen's save is bound to the very read it showed: a state equal to it but read another
     * time, or one no longer remembered, is not guessed at, and the save is refused.
     */
    @Test fun aSaveFromAReadNoLongerKnownIsRefused() {
        assertTrue(phone.repository.save(fixture))
        val shown = phone.state()
        val tick = ChecklistDay("chk_custom", "2026-10-25", 400, setOf("day/stretch"), emptySet())
        assertFalse(phone.repository.save(shown.copy(checklistDays = shown.checklistDays + tick), base = shown.copy()))
        repeat(4) { phone.state() }
        assertFalse(phone.repository.save(shown.copy(checklistDays = shown.checklistDays + tick), base = shown))
        assertFalse(tick in phone.state().checklistDays)
    }

    /**
     * A record a screen adds never takes the ID of another record that arrived since: another copy
     * holds a weigh-in of the 26th under the ID the 25th's would get, and both stay.
     */
    @Test fun aNewRecordNeverTakesTheIdOfOneThatArrived() {
        bothHoldTheFixture()
        val elsewhere = """{"measurement":"msr_custom","dateKey":"2026-10-26","timestampMillis":500,"value":79.5}"""
        server.hold(SyncedRow("measurementLogs", "msr_custom/2026-10-25", elsewhere, "000000000002000-00000-elsewhere", 9))
        val shown = phone.state()
        phone.now = 3_000
        phone.sync()
        val mine = MeasurementLog("msr_custom", "2026-10-25", 600, 80.5)
        assertTrue(phone.repository.save(shown.copy(measurementLogs = shown.measurementLogs + mine), base = shown))
        val logs = phone.state().measurementLogs
        assertTrue(mine in logs)
        assertTrue(MeasurementLog("msr_custom", "2026-10-26", 500, 79.5) in logs)
    }

    /** Two workouts that share an ID are two workouts: one another copy added and one a screen adds both stay. */
    @Test fun twoWorkoutsSharingAnIdBothStay() {
        bothHoldTheFixture()
        val session = fixture.sessions.single()
        val there = session.copy(id = "k", dateKey = "2026-10-25", timestampMillis = 700)
        tablet.now = 2_000
        tablet.change { it.copy(sessions = it.sessions + there) }
        tablet.sync()
        val shown = phone.state()
        phone.now = 3_000
        phone.sync()
        val here = session.copy(id = "k", dateKey = "2026-10-26", timestampMillis = 800)
        assertTrue(phone.repository.save(shown.copy(sessions = shown.sessions + here), base = shown))
        assertTrue(phone.state().sessions.containsAll(listOf(there, here)))
    }

    /** What an update returns is what is stored, so a screen showing it can save from it. */
    @Test fun anUpdatesResultIsABaseToSaveFrom() {
        assertTrue(phone.repository.save(fixture))
        val tick = ChecklistDay("chk_custom", "2026-10-25", 900, setOf("day/stretch"), emptySet())
        val updated = (phone.repository.update { it.copy(checklistDays = it.checklistDays + tick) } as TrackerUpdateResult.Updated).state
        val weighIn = MeasurementLog("msr_custom", "2026-10-25", 901, 80.0)
        assertTrue(phone.repository.save(updated.copy(measurementLogs = updated.measurementLogs + weighIn), base = updated))
        assertTrue(weighIn in phone.state().measurementLogs && tick in phone.state().checklistDays)
    }

    /** A restore that lands while the server answers keeps what was restored and takes nothing from the answer. */
    @Test fun aRestoreDuringAnExchangeTakesNothingFromIt() {
        bothHoldTheFixture()
        tablet.now = 2_000
        tablet.change { it.copy(checklistDays = emptyList()) }
        tablet.sync()
        val (rows, _) = phone.rows.snapshot(emptySet())
        val restoring = SyncServer { request ->
            phone.rows.replaceAll(rows, mapOf(SyncMarks.Held to "restored", SyncMarks.Cursor to null, SyncMarks.Pushed to null))
            server.exchange(request)
        }
        assertEquals(SyncOutcome.HeldAfterRestore, phone.repository.sync(restoring))
        assertEquals(fixture.checklistDays, phone.state().checklistDays)
    }

    /** A screen that only puts records in another order saves that order. */
    @Test fun aScreenThatOnlyReordersSavesTheOrder() {
        assertTrue(phone.repository.save(fixture))
        val shown = phone.state()
        val reordered = shown.copy(checklists = shown.checklists.reversed(), measurementLogs = shown.measurementLogs.reversed())
        assertTrue(phone.repository.save(reordered, base = shown))
        assertEquals(reordered, phone.state())
    }

    /**
     * When what a screen changed cannot stand beside what arrived meanwhile (the same day answered
     * on both), the save is refused and changes nothing: the screen reads again and shows the
     * answer that came, instead of writing its whole older state over everything that arrived.
     */
    @Test fun aScreenWhoseChangeCannotStandBesideASyncIsRefused() {
        bothHoldTheFixture()
        val weighIn = MeasurementLog("msr_custom", "2026-10-22", 2_000, 79.0)
        tablet.now = 2_000
        tablet.change { it.copy(questionEntries = it.questionEntries + QuestionEntry("on-tablet", "qst_custom", "2026-10-22", 2_000), measurementLogs = it.measurementLogs + weighIn) }
        tablet.sync()

        val shown = phone.state()
        phone.sync()
        val synced = phone.held()
        val answered = shown.copy(questionEntries = shown.questionEntries + QuestionEntry("on-phone", "qst_custom", "2026-10-22", 3_000))
        assertFalse(phone.repository.save(answered, base = shown))
        assertEquals(synced, phone.held())
        assertTrue(weighIn in phone.state().measurementLogs)
    }

    /** A day said to be without uses on one copy and with a use on the other keeps the use, whichever arrives first. */
    @Test fun aUseOutweighsANoneDayRecordedApart() {
        bothHoldTheFixture()
        val use = SubstanceLog("use-tablet", "sub_own", "2026-10-24", 3_000, 0.5, SubstanceAmountUnit.PILLS)
        phone.now = 2_000
        phone.change { it.copy(substanceNoneDays = it.substanceNoneDays + SubstanceNoneDay("2026-10-24", 2_000)) }
        tablet.now = 3_000
        tablet.change { it.copy(substanceLogs = it.substanceLogs + use) }
        // The tablet hears of the "none" after its use, the phone of the use after its "none".
        phone.sync(); tablet.sync(); phone.sync(); tablet.sync()

        listOf(phone, tablet).forEach { device ->
            assertTrue(use in device.state().substanceLogs)
            assertEquals(listOf("2026-10-13"), device.state().substanceNoneDays.map { it.dateKey })
        }
        assertEquals(phone.held(), tablet.held())
    }

    /** Which timer runs a phone already added up is its own business: another phone's runs could start in the same millisecond. */
    @Test fun aTimerLedgerStaysOnItsPhone() {
        assertTrue(phone.repository.save(fixture.copy(recordedTimeSessions = setOf(123L))))
        phone.sync()
        tablet.state()
        tablet.sync()
        assertTrue(server.rows.none { it.kind == "timerSessions" })
        assertEquals(emptySet<Long>(), tablet.state().recordedTimeSessions)
        assertEquals(setOf(123L), phone.state().recordedTimeSessions)
    }

    /**
     * Rows this build cannot read, such as a kind a newer build added, are refused together with
     * everything that came with them: nothing changes here and the same rows come again.
     */
    @Test fun rowsThisBuildCannotReadAreRefusedWhole() {
        bothHoldTheFixture()
        val before = phone.held()
        val cursor = phone.rows.meta(SyncMarks.Cursor)
        server.hold(SyncedRow("measurementLogs", "new", """{"measurement":"msr_custom","dateKey":"2026-10-23","timestampMillis":1,"value":80.0}""", "000000000099999-00000-newer"))
        server.hold(SyncedRow("futureKind", "x", "{}", "000000000099999-00001-newer"))

        assertEquals(SyncOutcome.Refused, phone.sync())
        assertEquals(SyncOutcome.Refused, phone.sync())
        assertEquals(before, phone.held())
        assertEquals(cursor, phone.rows.meta(SyncMarks.Cursor))
        assertEquals(fixture, phone.state())
    }

    /**
     * A restore may mean "this phone, as it was" or "everyone's data, as it was", and the two
     * spread differently: syncing waits for that decision, and nothing reaches the other copy.
     */
    @Test fun aRestoreHoldsSyncing() {
        bothHoldTheFixture()
        val (rows, _) = phone.rows.snapshot(emptySet())
        phone.now = 2_000
        phone.rows.replaceAll(rows.filter { it.kind != "checklistDays" }, mapOf(SyncMarks.Held to "restored", SyncMarks.Cursor to null, SyncMarks.Pushed to null))
        assertEquals(SyncOutcome.HeldAfterRestore, phone.sync())
        tablet.sync()
        assertEquals(fixture.checklistDays, tablet.state().checklistDays)
    }

    /** Nothing changes while the server cannot be reached, and the next exchange sends what waited. */
    @Test fun anUnreachableServerChangesNothing() {
        bothHoldTheFixture()
        server.reachable = false
        phone.now = 2_000
        phone.change { it.copy(checklistDays = emptyList()) }
        assertEquals(SyncOutcome.Unreachable, phone.sync())
        server.reachable = true
        syncBoth()
        assertEquals(emptyList<ChecklistDay>(), tablet.state().checklistDays)
    }

    /** A copy whose rows do not hold the state yet sends nothing. */
    @Test fun aCopyWithoutRowsSendsNothing() {
        assertEquals(SyncOutcome.NotReady, phone.sync())
        assertFalse(server.rows.isNotEmpty())
    }
}

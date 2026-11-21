package com.example.workouttracker.reminder

import com.example.workouttracker.domain.AppState
import com.example.workouttracker.domain.SubstancesTrackerRef
import com.example.workouttracker.domain.checklist.CheckpointTime
import com.example.workouttracker.domain.reminder.ReminderSettings
import com.example.workouttracker.domain.reminder.ReminderTime
import com.example.workouttracker.domain.settings.DashboardFeature
import com.example.workouttracker.domain.settings.FeatureVisibilitySettings
import com.example.workouttracker.domain.substance.SubstanceCheckIn
import com.example.workouttracker.domain.substance.SubstanceKind
import com.example.workouttracker.domain.substance.SubstanceLog
import com.example.workouttracker.domain.substance.SubstanceNoneDay
import com.example.workouttracker.domain.substance.SubstanceSettings
import com.example.workouttracker.persistence.TrackerRepository
import com.example.workouttracker.persistence.TrackerUpdateResult
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The check-in asks only whether yesterday is logged, never what was logged, and its action never overwrites a use. */
class SubstanceCheckInTest {
    private val todayKey = "2026-11-20"

    /** The check-in asks about yesterday, the day that is over. */
    private val askedKey = "2026-11-19"
    private val at = CheckpointTime.Fixed(LocalTime.of(21, 0))
    private val on = SubstanceSettings.Legacy.copy(checkIn = SubstanceCheckIn(remind = true, at = at))
    private val use = SubstanceLog("c1", SubstanceKind.CANNABIS.id, askedKey, 1L)

    private class FakeGateway(var exact: Boolean = true) : SubstanceCheckInGateway {
        val scheduled = mutableListOf<Triple<CheckpointTime, LocalDate?, ReminderTime?>>()
        var cancelled = 0
        override fun canScheduleExact() = exact
        override fun schedule(at: CheckpointTime, earliestDate: LocalDate?, fallbackAlarmTime: ReminderTime, fixedBedtime: ReminderTime?) = true.also { scheduled += Triple(at, earliestDate, fixedBedtime) }
        override fun cancel() { cancelled++ }
    }

    private class FakeNotifier : SubstanceCheckInNotifier {
        val shown = mutableListOf<String>()
        override fun show(dateKey: String) = true.also { shown += dateKey }
    }

    private fun coordinator(
        state: AppState,
        gateway: FakeGateway = FakeGateway(),
        notifier: FakeNotifier = FakeNotifier(),
        visibility: FeatureVisibilitySettings = FeatureVisibilitySettings(),
        settings: ReminderSettings = ReminderSettings(),
    ) = SubstanceCheckInCoordinator(FakeTrackerRepository(state), { visibility }, gateway, notifier, { settings }, { todayKey })

    @Test fun itAsksAboutAnUnloggedDayAndSetsTomorrowsAlarm() {
        val gateway = FakeGateway()
        val notifier = FakeNotifier()
        assertTrue(coordinator(AppState(substances = on), gateway, notifier).onReminderDue())
        assertEquals(listOf(askedKey), notifier.shown)
        assertEquals(listOf(Triple<CheckpointTime, LocalDate?, ReminderTime?>(at, LocalDate.parse("2026-11-21"), null)), gateway.scheduled)
    }

    @Test fun aTokenSetAsideKeepsItQuietAndTomorrowsAlarmIsStillSet() {
        val gateway = FakeGateway()
        val notifier = FakeNotifier()
        val quiet = SubstanceCheckInCoordinator(FakeTrackerRepository(AppState(substances = on)), { FeatureVisibilitySettings() }, gateway, notifier, { ReminderSettings() }, { todayKey }) { ref, checkpoint ->
            ref == SubstancesTrackerRef && checkpoint == null
        }
        assertTrue(quiet.onReminderDue())
        assertEquals(emptyList<String>(), notifier.shown)
        assertEquals(1, gateway.scheduled.size)
    }

    @Test fun itStaysQuietOnADayLoggedWithAUseOrWithNone() {
        listOf(
            AppState(substances = on, substanceLogs = listOf(use)),
            AppState(substances = on, substanceNoneDays = listOf(SubstanceNoneDay(askedKey, 1L))),
        ).forEach { state ->
            val notifier = FakeNotifier()
            assertTrue(coordinator(state, notifier = notifier).onReminderDue())
            assertEquals(emptyList<String>(), notifier.shown)
        }
        // A record of today says nothing about yesterday.
        val notifier = FakeNotifier()
        coordinator(AppState(substances = on, substanceLogs = listOf(use.copy(dateKey = todayKey))), notifier = notifier).onReminderDue()
        assertEquals(listOf(askedKey), notifier.shown)
    }

    @Test fun itIsCancelledWhileOffOrWhileSubstancesIsHidden() {
        val off = FakeGateway()
        assertTrue(coordinator(AppState(substances = SubstanceSettings.Legacy), off).reconcile())
        assertEquals(1, off.cancelled)
        assertEquals(emptyList<Triple<CheckpointTime, LocalDate?, ReminderTime?>>(), off.scheduled)

        val hidden = FakeGateway()
        val notifier = FakeNotifier()
        val hiding = FeatureVisibilitySettings().hiding(DashboardFeature.Substances)
        assertTrue(coordinator(AppState(substances = on), hidden, notifier, hiding).onReminderDue())
        assertEquals(1, hidden.cancelled)
        assertEquals(emptyList<String>(), notifier.shown)
    }

    @Test fun itSchedulesTheStoredTimeAgainstTheNightsSettingsAndNeedsExactAlarms() {
        val gateway = FakeGateway()
        val settings = ReminderSettings(fallbackAlarmTime = ReminderTime(6, 30), fixedBedtime = ReminderTime(22, 45))
        val beforeSleep = on.copy(checkIn = on.checkIn.copy(at = CheckpointTime.BeforeSleep(30)))
        assertTrue(coordinator(AppState(substances = beforeSleep), gateway, settings = settings).reconcile())
        assertEquals(listOf(Triple<CheckpointTime, LocalDate?, ReminderTime?>(CheckpointTime.BeforeSleep(30), null, ReminderTime(22, 45))), gateway.scheduled)

        val inexact = FakeGateway(exact = false)
        assertFalse(coordinator(AppState(substances = on), inexact).reconcile())
        assertEquals(0, inexact.scheduled.size)
    }

    @Test fun noneTodayRecordsNoneAndNeverOverwritesAUse() {
        val repository = FakeTrackerRepository(AppState(substances = on))
        assertEquals(SubstanceNoneTodayResult.Saved, SubstanceNoneTodayCoordinator(repository, todayKey = { todayKey }) { 42L }.record(askedKey))
        assertEquals(listOf(SubstanceNoneDay(askedKey, 42L)), repository.load().substanceNoneDays)

        val used = FakeTrackerRepository(AppState(substances = on, substanceLogs = listOf(use)))
        assertEquals(SubstanceNoneTodayResult.HoldsUse, SubstanceNoneTodayCoordinator(used, todayKey = { todayKey }) { 42L }.record(askedKey))
        assertEquals(listOf(use), used.load().substanceLogs)
        assertEquals(emptyList<SubstanceNoneDay>(), used.load().substanceNoneDays)

        val failing = FakeTrackerRepository(AppState(substances = on), saves = false)
        assertEquals(SubstanceNoneTodayResult.SaveFailed, SubstanceNoneTodayCoordinator(failing, todayKey = { todayKey }) { 42L }.record(askedKey))
    }

    /** "None" is said only of a day that is over, also when an older notification carries today. */
    @Test fun noneIsRefusedForADayNotOverYet() {
        val repository = FakeTrackerRepository(AppState(substances = on))
        assertEquals(SubstanceNoneTodayResult.NotOver, SubstanceNoneTodayCoordinator(repository, todayKey = { todayKey }) { 42L }.record(todayKey))
        assertEquals(emptyList<SubstanceNoneDay>(), repository.load().substanceNoneDays)
        assertEquals(SubstanceNoneTodayResult.Saved, SubstanceNoneTodayCoordinator(repository, todayKey = { todayKey }) { 42L }.record(askedKey))
    }

    /** A save that logs the day answers the check-in, with a use as with "none"; one that only changes a logged day does not. */
    @Test fun aSaveThatLogsTheDayAnswersTheCheckIn() {
        val before = AppState(substances = on)
        assertTrue(substancesWereLoggedForDate(before, before.copy(substanceLogs = listOf(use)), askedKey))
        assertTrue(substancesWereLoggedForDate(before, before.copy(substanceNoneDays = listOf(SubstanceNoneDay(askedKey, 1L))), askedKey))
        assertFalse(substancesWereLoggedForDate(before, before.copy(substanceLogs = listOf(use.copy(dateKey = todayKey))), askedKey))
        val saidNone = before.copy(substanceNoneDays = listOf(SubstanceNoneDay(askedKey, 1L)))
        assertFalse(substancesWereLoggedForDate(saidNone, saidNone.copy(substanceNoneDays = emptyList(), substanceLogs = listOf(use)), askedKey))
    }

    private class FakeTrackerRepository(private var state: AppState, private val saves: Boolean = true) : TrackerRepository {
        override fun load(): AppState = state
        override fun save(state: AppState): Boolean = saves.also { if (it) this.state = state }
        override fun update(transform: (AppState) -> AppState): TrackerUpdateResult {
            if (!saves) return TrackerUpdateResult.SaveFailed
            val updated = runCatching { transform(state) }.getOrElse { return TrackerUpdateResult.SaveFailed }
            state = updated
            return TrackerUpdateResult.Updated(updated)
        }
    }
}

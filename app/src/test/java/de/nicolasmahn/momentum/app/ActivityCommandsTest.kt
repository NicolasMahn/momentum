package de.nicolasmahn.momentum.app

import de.nicolasmahn.momentum.domain.AppState
import de.nicolasmahn.momentum.domain.activity.ActivityEdit
import de.nicolasmahn.momentum.domain.activity.ActivityEntry
import de.nicolasmahn.momentum.domain.activity.SeededActivityRules
import de.nicolasmahn.momentum.domain.activity.WeekPlan
import de.nicolasmahn.momentum.domain.activity.applyingActivityEdits
import de.nicolasmahn.momentum.feature.settings.ViewWrite
import de.nicolasmahn.momentum.persistence.TrackerRepository
import de.nicolasmahn.momentum.persistence.TrackerUpdateResult
import de.nicolasmahn.momentum.reminder.activityDays
import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

/** Activity's save moves its reminder with its week: the alarm is set for the days as stored, or the move is taken back. */
class ActivityCommandsTest {
    private val today = LocalDate.parse("2026-10-06")
    private val mondays = AppState(activity = SeededActivityRules).applyingActivityEdits(listOf(ActivityEdit.SetWeek(WeekPlan.of(DayOfWeek.MONDAY))), today.minusDays(7))
    private val toSundays = listOf(ActivityEdit.SetWeek(WeekPlan.of(DayOfWeek.SUNDAY)))
    private val entry = ActivityEntry("e1", "bouldering", "Bouldering", "2026-10-05", 1L)

    private inner class Fixture(state: AppState, val alarmsSet: Boolean = true, val exactAllowed: Boolean = true, val reminderOn: Boolean = true) {
        val repository = FakeTrackerRepository(state)
        val reconciled = mutableListOf<AppState>()
        val commands = ActivityCommands(
            repository = repository,
            reconcile = { reconciled += it; alarmsSet },
            exactAlarmsAllowed = { exactAllowed },
            refresh = {},
            reminderOn = { reminderOn },
            today = { today },
        )
    }

    @Test fun movingTheWeekSetsTheAlarmForTheDaysAsStored() {
        val fixture = Fixture(mondays)
        assertEquals(ViewWrite.Stored, fixture.commands.save(toSundays))
        assertEquals(setOf(DayOfWeek.SUNDAY), fixture.repository.load().activityDays())
        assertEquals(listOf(setOf(DayOfWeek.SUNDAY)), fixture.reconciled.map { it.activityDays() })
    }

    /** A record stored after the page was opened stays under the edits, and the alarm is set from the records as they are now. */
    @Test fun theEditsApplyToTheLatestRecords() {
        val fixture = Fixture(mondays)
        val meanwhile = fixture.repository.load().copy(activityEntries = listOf(entry))
        fixture.repository.save(meanwhile)
        fixture.commands.save(toSundays)
        assertEquals(listOf(entry), fixture.repository.load().activityEntries)
        assertEquals(listOf(entry), fixture.reconciled.single().activityEntries)
    }

    @Test fun aMoveWhoseAlarmCannotBeSetIsTakenBack() {
        val fixture = Fixture(mondays, alarmsSet = false)
        assertEquals(ViewWrite.NotStored, fixture.commands.save(toSundays))
        assertEquals(mondays, fixture.repository.load())
        assertEquals(listOf(setOf(DayOfWeek.SUNDAY), setOf(DayOfWeek.MONDAY)), fixture.reconciled.map { it.activityDays() })

        val noAccess = Fixture(mondays, alarmsSet = false, exactAllowed = false)
        assertEquals(ViewWrite.Stored, noAccess.commands.save(toSundays))
        assertEquals(setOf(DayOfWeek.SUNDAY), noAccess.repository.load().activityDays())

        // With the reminder off there is no alarm to move: a failure elsewhere keeps the week.
        val reminderOff = Fixture(mondays, alarmsSet = false, reminderOn = false)
        assertEquals(ViewWrite.Stored, reminderOff.commands.save(toSundays))
        assertEquals(setOf(DayOfWeek.SUNDAY), reminderOff.repository.load().activityDays())
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

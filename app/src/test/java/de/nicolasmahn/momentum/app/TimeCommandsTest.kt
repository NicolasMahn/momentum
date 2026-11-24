package de.nicolasmahn.momentum.app

import de.nicolasmahn.momentum.persistence.CurrentTrackerStore
import de.nicolasmahn.momentum.persistence.storedDocument
import de.nicolasmahn.momentum.domain.AppState
import de.nicolasmahn.momentum.domain.DayChangeRefusal
import de.nicolasmahn.momentum.domain.DayChangeResult
import de.nicolasmahn.momentum.domain.changingDay
import de.nicolasmahn.momentum.domain.checklist.Dated
import de.nicolasmahn.momentum.domain.checklist.RewardPeriod
import de.nicolasmahn.momentum.domain.time.TimeConfiguration
import de.nicolasmahn.momentum.domain.time.TimeEdit
import de.nicolasmahn.momentum.domain.time.TimeLog
import de.nicolasmahn.momentum.domain.time.TimeLogOrder
import de.nicolasmahn.momentum.domain.time.TimeRewards
import de.nicolasmahn.momentum.domain.time.TimeSession
import de.nicolasmahn.momentum.domain.time.applyEdit
import de.nicolasmahn.momentum.domain.time.applyingEdits
import de.nicolasmahn.momentum.domain.time.loggedDaysOf
import de.nicolasmahn.momentum.domain.compatibility.madeTime
import de.nicolasmahn.momentum.domain.compatibility.readingTime
import de.nicolasmahn.momentum.domain.time.testMadeTime
import de.nicolasmahn.momentum.feature.settings.ViewWrite
import de.nicolasmahn.momentum.feature.time.TimeDayChange
import de.nicolasmahn.momentum.feature.time.TimedMinutes
import de.nicolasmahn.momentum.persistence.PreferencesTrackerRepository
import de.nicolasmahn.momentum.persistence.TrackerStringStore
import de.nicolasmahn.momentum.persistence.trackerRepository
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import de.nicolasmahn.momentum.domain.time.withReading

/**
 * What a time view's setup, its page and its session hand over is stored by the commands every
 * logged view has: a creation under an ID nothing names, edits over the configuration as it is
 * stored, a write taken back where its alarm cannot be set, a delete with every log. A session's
 * minutes are decided against the logs as they are stored, as typed minutes are.
 */
class TimeCommandsTest {
    private val piano = testMadeTime()
    private val readings = TimeLog("reading", "2026-10-13", 5, 25)

    /** The real store with Reading and the views a person made, the commands over it, and the alarms answering as the test says. */
    private class Fixture(logs: List<TimeLog>, made: List<TimeConfiguration> = emptyList(), var alarmsSet: Boolean = true, var hidden: Set<String> = emptySet()) {
        val store = CurrentTrackerStore()
        val repository = trackerRepository(store)
        val handedOver = mutableListOf<AppState>()
        val forgotten = mutableListOf<String>()
        var refreshed = 0

        /** What happens while the alarms are being set, before the answer is known. */
        var afterAlarms: () -> Unit = {}
        val commands = TimeCommands(
            repository,
            reconcile = { state ->
                handedOver += state
                afterAlarms()
                alarmsSet
            },
            exactAlarmsAllowed = { true },
            refresh = { refreshed++ },
            stored = { handedOver += it },
            hidden = { hidden },
            forgetHidden = { forgotten += it },
            today = { Today },
            now = { SaveTime },
        )

        init {
            // A new installation tracks nothing: Reading is seeded here, as the tests stand on it.
            assertTrue(repository.save(repository.load().withReading().let { it.copy(time = it.time + made, timeLogs = logs.sortedWith(TimeLogOrder)) }))
        }

        fun logs() = repository.load().timeLogs
    }


    /** Create stores the setup's view, its starter with the edits made to it, and nothing else: every log and every other configuration is as it was. */
    @Test fun createStoresTheStarterWithTheEditsMadeToIt() {
        val days = Fixture(listOf(readings), made = listOf(piano))
        val starter = testMadeTime(id = "tim_new1", label = "Time")
        val edits = listOf(TimeEdit.Rename("Guitar"), TimeEdit.SetGoal(120), TimeEdit.JoinRewards, TimeEdit.SetBankSize(2))

        assertEquals(ViewWrite.Stored, days.commands.create(starter, edits))

        val created = days.repository.load().madeTime.last()
        assertEquals(starter.copy(label = "Guitar", goal = Dated.always(120), rewards = TimeRewards(maxPasses = 2, periods = listOf(RewardPeriod(Today)))), created)
        assertEquals(listOf(piano, created), days.repository.load().madeTime)
        assertEquals(listOf(readings), days.logs())
        assertEquals(listOf(days.repository.load()), days.handedOver)
    }

    /**
     * The setup's ID was free when it opened. If a configuration made meanwhile, a log of a view
     * nothing knows or a hidden entry names it by the time Create is pressed, nothing is stored;
     * pressed twice, one view is made.
     */
    @Test fun anIdNamedByTheTimeCreateIsPressedIsRefusedAndNothingIsWritten() {
        val starter = testMadeTime(id = "tim_new1")
        listOf(
            Fixture(emptyList(), made = listOf(starter.copy(label = "Made meanwhile"))),
            Fixture(listOf(TimeLog(starter.id, "2026-10-13", 1L, 20))),
            Fixture(emptyList(), hidden = setOf(starter.id)),
        ).forEach { taken ->
            val kept = taken.store.everything.toMap()
            assertEquals(ViewWrite.NotStored, taken.commands.create(starter, listOf(TimeEdit.Rename("Guitar"))))
            assertEquals(kept, taken.store.everything.toMap())
            assertEquals(emptyList<AppState>(), taken.handedOver)
        }

        val days = Fixture(emptyList())
        assertEquals(ViewWrite.Stored, days.commands.create(starter, emptyList()))
        assertEquals(ViewWrite.NotStored, days.commands.create(starter, emptyList()))
        assertEquals(listOf(starter.id), days.repository.load().madeTime.map { it.id })
    }

    /**
     * A page's edits go over the configuration as it is stored at that moment, dated by the habit
     * day the save runs on: a view that took part before today keeps its earlier goal for earlier
     * weeks. Reading's page stores through the same commands.
     */
    @Test fun aSaveAppliesTheEditsOnTheDayItRunsOverWhatIsStored() {
        val joined = piano.applyEdit(TimeEdit.JoinRewards, Today.minusDays(10))
        val days = Fixture(emptyList(), made = listOf(joined))
        val edits = listOf(TimeEdit.Rename("Keys"), TimeEdit.SetGoal(90), TimeEdit.SetRemind(true))

        assertEquals(ViewWrite.Stored, days.commands.save(piano.id, edits))

        val saved = days.repository.load().madeTime.single()
        assertEquals(joined.applyingEdits(edits, Today), saved)
        assertEquals(Dated(listOf(Dated.Revision(60), Dated.Revision(90, Today))), saved.goal)
        assertEquals(ViewWrite.NotStored, Fixture(emptyList(), made = listOf(piano)).also { it.store.writable = false }.commands.save(piano.id, edits))

        assertEquals(ViewWrite.Stored, days.commands.save("reading", listOf(TimeEdit.SetGoal(45))))
        assertEquals(Dated(listOf(Dated.Revision(60), Dated.Revision(45, Today))), days.repository.load().readingTime!!.goal)
    }

    /**
     * With access granted and the alarm not set, a Save and a Create that asked for one are taken
     * back and the store is what it was. Where the take-back cannot be written, the write is
     * still there and is said to be.
     */
    @Test fun aWriteWhoseNewAlarmCannotBeSetIsTakenBackOrSaidToBeStoredWithoutIt() {
        val starter = testMadeTime(id = "tim_new1", label = "Guitar")
        val switchOn = listOf(TimeEdit.Rename("Keys"), TimeEdit.SetRemind(true))

        val days = Fixture(listOf(readings), made = listOf(piano), alarmsSet = false)
        val kept = days.store.everything.toMap()
        assertEquals(ViewWrite.NotStored, days.commands.save(piano.id, switchOn))
        assertEquals(ViewWrite.NotStored, days.commands.create(starter, switchOn))
        assertEquals(kept, days.store.everything.toMap())
        // Each was shown with its change, then without it.
        assertEquals(listOf(listOf("Keys"), listOf("Piano"), listOf("Piano", "Keys"), listOf("Piano")), days.handedOver.map { state -> state.madeTime.map { it.label } })
        // No new alarm is asked for by a name alone.
        assertEquals(ViewWrite.Stored, days.commands.save(piano.id, listOf(TimeEdit.Rename("Keys"))))

        val stuck = Fixture(emptyList(), made = listOf(piano), alarmsSet = false)
        stuck.afterAlarms = { stuck.store.writable = false }
        assertEquals(ViewWrite.StoredWithoutAlarm, stuck.commands.save(piano.id, switchOn))
        assertEquals("Keys", stuck.repository.load().madeTime.single().label)
        assertEquals(1, stuck.refreshed)
    }

    /**
     * Deleting is one update of the store: the configuration and every log that names it are gone
     * together, every other log and configuration is as it was, the alarms are set for what is
     * left and its hidden entry is asked to be dropped. Reading is built in and stays.
     */
    @Test fun deletingRemovesTheViewWithEveryLogOfItAndAsksForItsHiddenEntryToBeDropped() {
        val guitar = testMadeTime(id = "tim_m7xd", label = "Guitar")
        val pianos = listOf(TimeLog(piano.id, "2026-12-24", 6L, 30), TimeLog(piano.id, "2026-10-13", 4L, 20))
        val others = listOf(TimeLog(guitar.id, "2026-10-13", 4L, 45), readings)
        val days = Fixture(pianos + others, made = listOf(piano, guitar))
        assertEquals(2, days.repository.load().loggedDaysOf(piano.id))

        assertTrue(days.commands.delete(piano.id))

        assertEquals(listOf(guitar), days.repository.load().madeTime)
        assertEquals(others.sortedWith(TimeLogOrder), days.logs())
        assertEquals(listOf(days.repository.load()), days.handedOver)
        assertEquals(listOf(piano.id), days.forgotten)
        assertFalse(piano.id in storedDocument(days.store).toString())

        // Its screen, still open, is told so; a second delete finds nothing to delete.
        assertEquals(DayChangeResult.Refused(DayChangeRefusal.Deleted), days.commands.changeDay(TimeDayChange.Add(piano.id, "2026-10-14", opened = null, minutes = 5)))
        assertFalse(days.commands.delete(piano.id))
        assertEquals(listOf(piano.id), days.forgotten)
        assertEquals(listOf(guitar.id), days.repository.load().madeTime.map { it.id })

        // Reading is deleted like any other, with every log of its own, and its hidden entry is asked to be dropped.
        assertTrue(days.commands.delete("reading"))
        assertEquals(listOf(piano.id, "reading"), days.forgotten)
        assertEquals(listOf(guitar), days.repository.load().time)
        assertEquals(others.filterNot { it.viewId == "reading" }.sortedWith(TimeLogOrder), days.logs())
        assertFalse(days.commands.delete("reading"))
    }

    /**
     * A session's minutes are added to what its day holds in the store at that moment, whatever a
     * screen was shown: minutes typed meanwhile stay under them. The day's log is stamped with the
     * session's end, its start joins the recorded sessions in the same write, and a session that
     * comes back adds nothing more.
     */
    @Test fun aSessionsMinutesAreAddedToWhatItsDayHoldsInTheStore() {
        val session = TimeSession(piano.id, startedAtMillis = 1_000_000, dateKey = "2026-10-14", endedAtMillis = 1_000_000 + 25 * Minute)
        val days = Fixture(listOf(readings), made = listOf(piano))
        // Typed for the same day while the session ran.
        assertTrue(days.commands.changeDay(TimeDayChange.Add(piano.id, "2026-10-14", opened = null, minutes = 10)) is DayChangeResult.Stored)
        days.handedOver.clear()

        val stored = days.commands.saveSession(session, endMillis = session.endedAtMillis!!)

        val timed = TimeLog(piano.id, "2026-10-14", session.endedAtMillis!!, 35)
        assertEquals(listOf(timed, readings), days.logs())
        assertEquals(setOf(session.startedAtMillis), days.repository.load().recordedTimeSessions)
        assertEquals(listOf(TimeLog(piano.id, "2026-10-14", SaveTime, 10), readings), (stored as DayChangeResult.Stored).before.timeLogs)
        assertEquals(listOf(stored.after), days.handedOver)

        assertTrue(days.commands.saveSession(session, endMillis = session.endedAtMillis!! + 90 * Minute) is DayChangeResult.Stored)
        assertEquals(listOf(timed, readings), days.logs())
    }

    /**
     * A session whose view was deleted while it waited has nothing to credit: it is refused and
     * the store is left exactly as it is, Reading's included once Reading is deleted. In a state
     * that holds no configuration at all, a preview's or a test's, no view exists to credit.
     */
    @Test fun aSessionOfAViewDeletedMeanwhileIsRefusedAndWritesNothing() {
        val session = TimeSession(piano.id, startedAtMillis = 1_000_000, dateKey = "2026-10-14", endedAtMillis = 1_000_000 + 25 * Minute)
        val days = Fixture(listOf(readings), made = listOf(piano))
        assertTrue(days.commands.delete(piano.id))
        days.handedOver.clear()
        val kept = days.store.everything.toMap()
        val writes = days.store.commits

        assertEquals(DayChangeResult.Refused(DayChangeRefusal.Deleted), days.commands.saveSession(session, endMillis = session.endedAtMillis!!))

        assertEquals(kept, days.store.everything.toMap())
        assertEquals(writes, days.store.commits)
        assertEquals(listOf(readings), days.logs())
        assertEquals(emptySet<Long>(), days.repository.load().recordedTimeSessions)

        // Reading's session is credited while its configuration is there, and refused once Reading is deleted.
        val readingSession = session.copy(viewId = "reading")
        val withReading = Fixture(listOf(readings))
        assertTrue(withReading.commands.saveSession(readingSession, endMillis = readingSession.endedAtMillis!!) is DayChangeResult.Stored)
        assertEquals(listOf(TimeLog("reading", "2026-10-14", readingSession.endedAtMillis!!, 25), readings), withReading.logs())
        assertTrue(withReading.commands.delete("reading"))
        val keptReading = withReading.store.everything.toMap()
        val later = readingSession.copy(startedAtMillis = 2_000_000, endedAtMillis = 2_000_000 + 10 * Minute)
        assertEquals(DayChangeResult.Refused(DayChangeRefusal.Deleted), withReading.commands.saveSession(later, endMillis = later.endedAtMillis!!))
        assertEquals(keptReading, withReading.store.everything.toMap())
        assertEquals(emptyList<TimeLog>(), withReading.logs())
        // Without any configuration, a preview's state, no view exists to credit.
        listOf(readingSession, session).forEach { waiting ->
            assertEquals(DayChangeResult.Refused(DayChangeRefusal.Deleted), AppState().changingDay(TimedMinutes(waiting, waiting.endedAtMillis!!), SaveTime) { error("Nothing is stored for a view that is gone.") })
        }
    }

    private companion object {
        /** When a change is stored, later than every log here. */
        const val SaveTime = 900L
        const val Minute = 60_000L

        /** The habit day the commands run on. */
        val Today: LocalDate = LocalDate.parse("2026-10-14")
    }
}

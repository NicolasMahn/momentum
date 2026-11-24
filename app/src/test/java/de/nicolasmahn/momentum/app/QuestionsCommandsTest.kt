package de.nicolasmahn.momentum.app

import de.nicolasmahn.momentum.persistence.CurrentTrackerStore
import de.nicolasmahn.momentum.persistence.storedDocument
import de.nicolasmahn.momentum.domain.AppState
import de.nicolasmahn.momentum.domain.DayChangeRefusal
import de.nicolasmahn.momentum.domain.DayChangeResult
import de.nicolasmahn.momentum.domain.changingDay
import de.nicolasmahn.momentum.domain.checklist.AboutDay
import de.nicolasmahn.momentum.domain.checklist.Dated
import de.nicolasmahn.momentum.domain.checklist.DefaultChecklistRewards
import de.nicolasmahn.momentum.domain.checklist.RewardPeriod
import de.nicolasmahn.momentum.domain.questions.QuestionEntry
import de.nicolasmahn.momentum.domain.questions.QuestionEntryOrder
import de.nicolasmahn.momentum.domain.questions.QuestionsConfiguration
import de.nicolasmahn.momentum.domain.questions.QuestionsEdit
import de.nicolasmahn.momentum.domain.questions.answeredDaysOf
import de.nicolasmahn.momentum.domain.questions.applyEdit
import de.nicolasmahn.momentum.domain.questions.applyingEdits
import de.nicolasmahn.momentum.domain.compatibility.madeQuestions
import de.nicolasmahn.momentum.domain.questions.testMadeQuestions
import de.nicolasmahn.momentum.domain.questions.testQuestion
import de.nicolasmahn.momentum.feature.questions.QuestionsDayChange
import de.nicolasmahn.momentum.feature.settings.ViewWrite
import de.nicolasmahn.momentum.persistence.PreferencesTrackerRepository
import de.nicolasmahn.momentum.persistence.TrackerStringStore
import de.nicolasmahn.momentum.persistence.trackerRepository
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import de.nicolasmahn.momentum.domain.questions.withEating
import de.nicolasmahn.momentum.persistence.TrackerUpdateResult

/**
 * A questions view's screen stores one day's answers against the entries as they are stored: the
 * view must still exist and the day's entry must be the one the form opened on. Each save stamps
 * the time of writing, a first one and one over a saved day alike.
 */
class QuestionsCommandsTest {
    private val sleep = testMadeQuestions(questions = listOf(testQuestion(id = "qn_rest"), testQuestion(id = "qn_mood", answers = 3)))
    private val yesterdays = QuestionEntry("yesterday", "eating", "2026-10-13", 5, mapOf("quality" to "good"), "Late lunch")

    /** The real store with Eating and the views a person made, the commands over it, and the alarms answering as the test says. */
    private class Fixture(entries: List<QuestionEntry>, made: List<QuestionsConfiguration> = emptyList(), var alarmsSet: Boolean = true, var hidden: Set<String> = emptySet()) {
        val store = CurrentTrackerStore()
        val repository = trackerRepository(store)
        val handedOver = mutableListOf<AppState>()
        val forgotten = mutableListOf<String>()
        var refreshed = 0

        /** What happens while the alarms are being set, before the answer is known. */
        var afterAlarms: () -> Unit = {}
        val commands = QuestionsCommands(
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
            // A new installation tracks nothing: Eating is seeded here, as the tests stand on it.
            assertTrue(repository.save(repository.load().withEating().let { it.copy(questions = it.questions + made, questionEntries = entries) }))
        }

        fun entries() = repository.load().questionEntries
    }


    private fun save(dateKey: String, opened: QuestionEntry?, answers: Map<String, String> = emptyMap(), note: String? = null, viewId: String = "eating", newEntryId: String = "new") =
        QuestionsDayChange.Save(viewId, dateKey, opened, answers, note, newEntryId)

    /** A first save of a day is one entry under the new ID, stamped with the time it is stored at, and the screen is handed the records as they are now. */
    @Test fun aFirstSaveStoresTheDaysEntryStampedWithTheTimeOfWriting() {
        val days = Fixture(listOf(yesterdays))

        val stored = days.commands.changeDay(save("2026-10-14", opened = null, answers = mapOf("quality" to "fair", "amount" to "much_more"), note = "  Dinner  "))

        val saved = QuestionEntry("new", "eating", "2026-10-14", SaveTime, mapOf("quality" to "fair", "amount" to "much_more"), "Dinner")
        assertEquals(listOf(saved, yesterdays), days.entries())
        assertEquals(listOf(saved, yesterdays), (stored as DayChangeResult.Stored).after.questionEntries)
        assertEquals(listOf(yesterdays), stored.before.questionEntries)
        assertEquals(listOf(stored.after), days.handedOver)
    }

    /** A day saved again keeps its entry's ID and takes the new time of writing, whatever changed and when nothing did. */
    @Test fun savingADayAgainKeepsItsIdAndStampsTheTimeAgain() {
        val days = Fixture(listOf(yesterdays))

        assertTrue(days.commands.changeDay(save("2026-10-13", opened = yesterdays, answers = mapOf("quality" to "good"), note = "Late lunch", newEntryId = "unused")) is DayChangeResult.Stored)

        assertEquals(listOf(yesterdays.copy(timestampMillis = SaveTime)), days.entries())
    }

    /**
     * An entry stored since the form opened is never written over: a save and a removal that did
     * not open on it are refused, write nothing at all, and hand the screen what is stored.
     */
    @Test fun anEntryStoredSinceTheFormOpenedIsNotUndone() {
        val days = Fixture(listOf(yesterdays))
        val meanwhile = QuestionEntry("meanwhile", "eating", "2026-10-14", 7, mapOf("amount" to "much_less"))
        assertTrue(days.repository.save(days.repository.load().let { it.copy(questionEntries = listOf(meanwhile) + it.questionEntries) }))
        val writes = days.store.commits

        assertEquals(DayChangeResult.Refused(DayChangeRefusal.ChangedElsewhere), days.commands.changeDay(save("2026-10-14", opened = null, answers = mapOf("quality" to "poor"))))
        assertEquals(DayChangeResult.Refused(DayChangeRefusal.ChangedElsewhere), days.commands.changeDay(save("2026-10-13", opened = yesterdays.copy(note = "Lunch"))))
        assertEquals(DayChangeResult.Refused(DayChangeRefusal.ChangedElsewhere), days.commands.changeDay(QuestionsDayChange.Remove("eating", "2026-10-14", opened = meanwhile.copy(timestampMillis = 6))))
        assertEquals(DayChangeResult.Refused(DayChangeRefusal.ChangedElsewhere), days.commands.changeDay(QuestionsDayChange.Remove("eating", "2026-10-12", opened = yesterdays)))

        assertEquals(listOf(meanwhile, yesterdays), days.entries())
        assertEquals("A refused change writes nothing", writes, days.store.commits)
        assertEquals(List(4) { listOf(meanwhile, yesterdays) }, days.handedOver.map { it.questionEntries })

        // The day beside it is saved, and what was stored meanwhile stays.
        assertTrue(days.commands.changeDay(save("2026-10-12", opened = null)) is DayChangeResult.Stored)
        assertEquals(listOf(meanwhile, yesterdays, QuestionEntry("new", "eating", "2026-10-12", SaveTime)), days.entries())
    }

    /** A removal takes that view's entry of that day and no other: another view's of the same day and the view's of another day stay. */
    @Test fun aRemovalTakesThatViewsDayAlone() {
        val sleepDay = QuestionEntry("sleep", sleep.id, "2026-10-13", 4, mapOf("qn_rest" to "a2"))
        val earlier = QuestionEntry("earlier", "eating", "2026-10-12", 3)
        val days = Fixture(listOf(yesterdays, sleepDay, earlier), made = listOf(sleep))

        val removed = days.commands.changeDay(QuestionsDayChange.Remove("eating", "2026-10-13", opened = yesterdays))

        assertTrue(removed is DayChangeResult.Stored)
        assertEquals(listOf(sleepDay, earlier), days.entries())
    }

    /**
     * A save changes the answers of the questions the view has, as it is stored at that moment,
     * and leaves what the day's entry holds for a question removed since.
     */
    @Test fun aSaveLeavesWhatTheEntryHoldsForARemovedQuestion() {
        val stored = QuestionEntry("day", sleep.id, "2026-10-14", 4, mapOf("qn_gone" to "a2", "qn_rest" to "a5", "qn_mood" to "a1"), "Slept in")
        val days = Fixture(listOf(stored), made = listOf(sleep))

        assertTrue(days.commands.changeDay(save("2026-10-14", opened = stored, answers = mapOf("qn_rest" to "a1"), viewId = sleep.id)) is DayChangeResult.Stored)

        assertEquals(listOf(QuestionEntry("day", sleep.id, "2026-10-14", SaveTime, mapOf("qn_gone" to "a2", "qn_rest" to "a1"))), days.entries())
    }

    /** A view deleted while its screen was open has no day to change: nothing is written for it, Eating's included. */
    @Test fun aViewDeletedSinceIsRefused() {
        val sleepDay = QuestionEntry("sleep", sleep.id, "2026-10-13", 4, mapOf("qn_rest" to "a2"))
        val days = Fixture(listOf(sleepDay), made = listOf(sleep))
        assertTrue(days.repository.save(days.repository.load().let { state -> state.copy(questions = state.questions - state.madeQuestions.toSet()) }))
        val writes = days.store.commits

        listOf(
            save("2026-10-14", opened = null, answers = mapOf("qn_rest" to "a1"), viewId = sleep.id),
            save("2026-10-13", opened = sleepDay, viewId = sleep.id),
            QuestionsDayChange.Remove(sleep.id, "2026-10-13", opened = sleepDay),
        ).forEach { change -> assertEquals(change.toString(), DayChangeResult.Refused(DayChangeRefusal.Deleted), days.commands.changeDay(change)) }
        assertEquals(writes, days.store.commits)
        assertEquals(listOf(sleepDay), days.entries())

        // Eating's day is stored while its configuration is there, and refused the same way once Eating is deleted.
        val eatings = Fixture(listOf(yesterdays))
        assertTrue(eatings.commands.changeDay(save("2026-10-14", opened = null, answers = mapOf("quality" to "good"))) is DayChangeResult.Stored)
        assertTrue(eatings.repository.update { state -> state.copy(questions = state.questions.filterNot { it.id == "eating" }) } is TrackerUpdateResult.Updated)
        val keptEatings = eatings.store.everything.toMap()
        listOf(
            save("2026-10-15", opened = null, answers = mapOf("quality" to "good")),
            save("2026-10-13", opened = yesterdays),
            QuestionsDayChange.Remove("eating", "2026-10-13", opened = yesterdays),
        ).forEach { change -> assertEquals(change.toString(), DayChangeResult.Refused(DayChangeRefusal.Deleted), eatings.commands.changeDay(change)) }
        assertEquals(keptEatings, eatings.store.everything.toMap())
        assertEquals(2, eatings.entries().size)
    }

    /** What the store cannot write, and what the rules of an entry refuse, stores nothing and is said to have failed. */
    @Test fun aChangeTheStoreOrTheRulesRefuseStoresNothing() {
        val days = Fixture(listOf(yesterdays))
        val kept = days.store.everything.toMap()
        listOf(
            save("2026-02-30", opened = null),
            save("2026-10-14", opened = null, answers = mapOf("quality" to "superb")),
            save("2026-10-14", opened = null, answers = mapOf("qn_rest" to "a1")),
            save("2026-10-14", opened = null, note = "x".repeat(241)),
            save("2026-10-14", opened = null, newEntryId = "yesterday"),
        ).forEach { change -> assertEquals(change.toString(), DayChangeResult.Failed, days.commands.changeDay(change)) }
        assertEquals(kept, days.store.everything)

        days.store.writable = false
        assertEquals(DayChangeResult.Failed, days.commands.changeDay(save("2026-10-14", opened = null)))
        assertEquals(DayChangeResult.Failed, days.commands.changeDay(QuestionsDayChange.Remove("eating", "2026-10-13", opened = yesterdays)))
        assertEquals(kept, days.store.everything)
        assertEquals(emptyList<AppState>(), days.handedOver)
    }

    /**
     * Create stores the setup's view, its starter with the edits made to it, as made on the habit
     * day it is created, whichever day the setup was opened on, and nothing else: every entry and
     * every other configuration is as it was.
     */
    @Test fun createStoresTheViewAsMadeOnTheDayItIsCreated() {
        val days = Fixture(listOf(yesterdays), made = listOf(sleep))
        val starter = testMadeQuestions(id = "qst_new1", label = "Questions", madeOn = Today.minusDays(1))
        val edits = listOf(QuestionsEdit.Rename("Mood"), QuestionsEdit.SetAbout(AboutDay.Yesterday), QuestionsEdit.JoinRewards)

        assertEquals(ViewWrite.Stored, days.commands.create(starter, edits))

        val created = days.repository.load().madeQuestions.last()
        assertEquals(starter.copy(label = "Mood", about = Dated.always(AboutDay.Yesterday), rewards = DefaultChecklistRewards.copy(periods = listOf(RewardPeriod(Today))), madeOn = Today), created)
        assertEquals(listOf(sleep, created), days.repository.load().madeQuestions)
        assertEquals(listOf(yesterdays), days.entries())
        assertEquals(listOf(days.repository.load()), days.handedOver)
        // Made today and about yesterday, it asks about no day before today.
        assertEquals(null, created.recordDayFor(Today))
        assertEquals(Today, created.recordDayFor(Today.plusDays(1)))
    }

    /**
     * The setup's ID was free when it opened. If a configuration made meanwhile, an entry of a view
     * nothing knows or a hidden entry names it by the time Create is pressed, nothing is stored;
     * pressed twice, one view is made.
     */
    @Test fun anIdNamedByTheTimeCreateIsPressedIsRefusedAndNothingIsWritten() {
        val starter = testMadeQuestions(id = "qst_new1")
        val orphan = QuestionEntry("orphan", starter.id, "2026-10-13", 1L)
        listOf(
            Fixture(emptyList(), made = listOf(starter.copy(label = "Made meanwhile"))),
            Fixture(listOf(orphan)),
            Fixture(emptyList(), hidden = setOf(starter.id)),
        ).forEach { taken ->
            val kept = taken.store.everything.toMap()
            assertEquals(ViewWrite.NotStored, taken.commands.create(starter, listOf(QuestionsEdit.Rename("Mood"))))
            assertEquals(kept, taken.store.everything.toMap())
            assertEquals(emptyList<AppState>(), taken.handedOver)
        }

        val days = Fixture(emptyList())
        assertEquals(ViewWrite.Stored, days.commands.create(starter, emptyList()))
        assertEquals(ViewWrite.NotStored, days.commands.create(starter, emptyList()))
        assertEquals(listOf(starter.id), days.repository.load().madeQuestions.map { it.id })
    }

    /** A page's edits go over the configuration as it is stored at that moment, dated by the habit day the save runs on. */
    @Test fun aSaveAppliesTheEditsOnTheDayItRunsOverWhatIsStored() {
        val joined = sleep.applyEdit(QuestionsEdit.JoinRewards, Today.minusDays(3))
        val days = Fixture(emptyList(), made = listOf(joined))
        val edits = listOf(QuestionsEdit.Rename("Nights"), QuestionsEdit.SetAbout(AboutDay.Yesterday), QuestionsEdit.RemoveQuestion("qn_mood"), QuestionsEdit.SetRemind(true))

        assertEquals(ViewWrite.Stored, days.commands.save(sleep.id, edits))

        val saved = days.repository.load().madeQuestions.single()
        assertEquals(joined.applyingEdits(edits, Today), saved)
        // A day before today was judged, so which day is asked about changes from today on.
        assertEquals(Dated(listOf(Dated.Revision(AboutDay.Today), Dated.Revision(AboutDay.Yesterday, Today))), saved.about)
        assertEquals(ViewWrite.NotStored, Fixture(emptyList(), made = listOf(sleep)).also { it.store.writable = false }.commands.save(sleep.id, edits))
    }

    /**
     * With access granted and the alarm not set, a Save and a Create that asked for one are taken
     * back and the store is what it was. Where the take-back cannot be written, the write is
     * still there and is said to be.
     */
    @Test fun aWriteWhoseNewAlarmCannotBeSetIsTakenBackOrSaidToBeStoredWithoutIt() {
        val starter = testMadeQuestions(id = "qst_new1")
        val switchOn = listOf(QuestionsEdit.Rename("Nights"), QuestionsEdit.SetRemind(true))

        val days = Fixture(listOf(yesterdays), made = listOf(sleep), alarmsSet = false)
        val kept = days.store.everything.toMap()
        assertEquals(ViewWrite.NotStored, days.commands.save(sleep.id, switchOn))
        assertEquals(ViewWrite.NotStored, days.commands.create(starter, switchOn))
        assertEquals(kept, days.store.everything.toMap())
        // Each was shown with its change, then without it.
        assertEquals(listOf(listOf("Nights"), listOf("Sleep"), listOf("Sleep", "Nights"), listOf("Sleep")), days.handedOver.map { state -> state.madeQuestions.map { it.label } })
        // No new alarm is asked for by a name alone.
        assertEquals(ViewWrite.Stored, days.commands.save(sleep.id, listOf(QuestionsEdit.Rename("Nights"))))

        val stuck = Fixture(emptyList(), made = listOf(sleep), alarmsSet = false)
        stuck.afterAlarms = { stuck.store.writable = false }
        assertEquals(ViewWrite.StoredWithoutAlarm, stuck.commands.save(sleep.id, switchOn))
        assertEquals("Nights", stuck.repository.load().madeQuestions.single().label)
        assertEquals(1, stuck.refreshed)
    }

    /**
     * Deleting is one update of the store: the configuration and every entry that names it are gone
     * together, every other entry and configuration is as it was, the alarms are set for what is
     * left and its hidden entry is asked to be dropped. Every other view stays.
     */
    @Test fun deletingRemovesTheViewWithEveryEntryOfItAndAsksForItsHiddenEntryToBeDropped() {
        val mood = testMadeQuestions(id = "qst_m00d", label = "Mood")
        val sleeps = listOf(QuestionEntry("s2", sleep.id, "2026-12-24", 6L), QuestionEntry("s1", sleep.id, "2026-10-13", 4L, mapOf("qn_rest" to "a2")))
        val others = listOf(QuestionEntry("m1", mood.id, "2026-10-13", 4L), yesterdays)
        val days = Fixture(sleeps + others, made = listOf(sleep, mood))
        assertEquals(2, days.repository.load().answeredDaysOf(sleep.id))

        assertTrue(days.commands.delete(sleep.id))

        assertEquals(listOf(mood), days.repository.load().madeQuestions)
        assertEquals(others, days.entries())
        assertEquals(listOf(days.repository.load()), days.handedOver)
        assertEquals(listOf(sleep.id), days.forgotten)
        assertFalse(sleep.id in storedDocument(days.store).toString())

        // Its screen, still open, is told so; a second delete finds nothing to delete.
        assertEquals(DayChangeResult.Refused(DayChangeRefusal.Deleted), days.commands.changeDay(save("2026-10-14", opened = null, viewId = sleep.id)))
        assertFalse(days.commands.delete(sleep.id))
        assertEquals(listOf(sleep.id), days.forgotten)
        assertEquals(listOf(mood.id), days.repository.load().madeQuestions.map { it.id })

        // Eating is deleted like any other, with every entry of its own, and its hidden entry is asked to be dropped.
        assertTrue(days.commands.delete("eating"))
        assertEquals(listOf(sleep.id, "eating"), days.forgotten)
        assertEquals(listOf(mood), days.repository.load().questions)
        assertEquals(others.filterNot { it.viewId == "eating" }.sortedWith(QuestionEntryOrder), days.entries())
        assertFalse(days.commands.delete("eating"))
    }

    private companion object {
        /** When a change is stored, later than every entry here. */
        const val SaveTime = 900L

        /** The habit day the commands run on. */
        val Today: LocalDate = LocalDate.parse("2026-10-14")
    }
}

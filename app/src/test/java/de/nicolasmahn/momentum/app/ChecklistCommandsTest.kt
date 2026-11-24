package de.nicolasmahn.momentum.app

import de.nicolasmahn.momentum.domain.AppState
import de.nicolasmahn.momentum.domain.checklist.ChecklistConfiguration
import de.nicolasmahn.momentum.domain.checklist.ChecklistEdit
import de.nicolasmahn.momentum.domain.checklist.ChecklistItem
import de.nicolasmahn.momentum.domain.checklist.ChecklistTarget
import de.nicolasmahn.momentum.domain.checklist.Checkpoint
import de.nicolasmahn.momentum.domain.checklist.CheckpointTime
import de.nicolasmahn.momentum.domain.checklist.RewardPeriod
import de.nicolasmahn.momentum.domain.checklist.ViewHue
import de.nicolasmahn.momentum.domain.checklist.ViewIcon
import de.nicolasmahn.momentum.domain.checklist.checklist
import de.nicolasmahn.momentum.domain.checklist.starterChecklist
import de.nicolasmahn.momentum.persistence.TrackerRepository
import de.nicolasmahn.momentum.persistence.TrackerUpdateResult
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** A page's edits are stored on the day they are saved, over the records as they are by then. */
class ChecklistCommandsTest {
    /** The page was opened before 04:00; Save or Create came after, on the next habit day. */
    private val pageDay = LocalDate.parse("2026-10-14")
    private val saveDay = pageDay.plusDays(1)
    private val plants = ChecklistConfiguration(
        id = "chk_plants",
        label = "Plants",
        icon = ViewIcon.Plant,
        hue = ViewHue.Forest,
        checkpoints = listOf(Checkpoint("cpt_1", CheckpointTime.Fixed(LocalTime.of(9, 0)), remind = false)),
        items = listOf(ChecklistItem("itm_1", "Water the plants"), ChecklistItem("itm_2", "Feed the plants")),
    )

    private class Fixture(state: AppState, var alarmsSet: Boolean = true, val exactAllowed: Boolean = true) {
        val repository = FakeTrackerRepository(state)
        val reconciled = mutableListOf<AppState>()
        var refreshed = 0
        val commands = ChecklistCommands(
            repository = repository,
            reconcile = { reconciled += it; alarmsSet },
            exactAlarmsAllowed = { exactAllowed },
            refresh = { refreshed++ },
            today = { LocalDate.parse("2026-10-15") },
        )
        val stored: ChecklistConfiguration? get() = repository.load().checklist("chk_plants")
    }

    @Test
    fun savedEditsCountFromTheDayTheyAreSavedNotTheDayTheyWereMade() {
        val fixture = Fixture(AppState(checklists = listOf(plants)))
        val edits = listOf(ChecklistEdit.SetItemTarget("itm_1", ChecklistTarget.Weekdays(setOf(DayOfWeek.MONDAY))), ChecklistEdit.JoinRewards)

        assertTrue(fixture.commands.save(plants.id, edits))

        val entries = requireNotNull(fixture.stored).items.filter { it.id == "itm_1" }
        assertEquals(saveDay.minusDays(1), entries.first().until)
        assertEquals(saveDay, entries.last().from)
        assertEquals(listOf(RewardPeriod(saveDay)), fixture.stored?.rewards?.periods)
        assertEquals(1, fixture.reconciled.size)
    }

    /** A label stored while the page was open stays; the page's item edit lands beside it. */
    @Test
    fun aChangeStoredMeanwhileIsKeptUnderTheSavedEdits() {
        val fixture = Fixture(AppState(checklists = listOf(plants.copy(label = "Garden"))))

        assertTrue(fixture.commands.save(plants.id, listOf(ChecklistEdit.AddItem("itm_3", "Prune"))))

        assertEquals("Garden", fixture.stored?.label)
        assertEquals(listOf("itm_1", "itm_2", "itm_3"), fixture.stored?.items?.map { it.id })
    }

    @Test
    fun anEditTheStoredConfigurationRefusesIsLeftOutAndTheRestIsSaved() {
        val fixture = Fixture(AppState(checklists = listOf(plants.copy(items = plants.items.take(1)))))

        assertTrue(fixture.commands.save(plants.id, listOf(ChecklistEdit.RemoveItem("itm_2"), ChecklistEdit.Rename("Garden"))))

        assertEquals("Garden", fixture.stored?.label)
        assertEquals(listOf("itm_1"), fixture.stored?.items?.map { it.id })
    }

    @Test
    fun aSaveForAChecklistThatIsGoneIsRefused() {
        val fixture = Fixture(AppState())
        assertFalse(fixture.commands.save(plants.id, listOf(ChecklistEdit.Rename("Garden"))))
        assertTrue(fixture.reconciled.isEmpty())
    }

    @Test
    fun aCreationDatesTheStarterAndItsEditsTheDayItIsStored() {
        val fixture = Fixture(AppState())
        val starter = AppState().starterChecklist("Checklist", pageDay)
        val edits = listOf(ChecklistEdit.Rename("Plants"), ChecklistEdit.AddItem("itm_1", "Water the plants"), ChecklistEdit.JoinRewards)

        assertTrue(fixture.commands.create(starter, edits))

        val created = requireNotNull(fixture.repository.load().checklist(starter.id))
        assertEquals("Plants", created.label)
        assertEquals(saveDay, created.checkpoints.single().from)
        assertEquals(saveDay, created.items.single().from)
        assertEquals(listOf(RewardPeriod(saveDay)), created.rewards?.periods)
    }

    /** With access granted and the alarm still not set, the creation is taken back; trying again creates it once. */
    @Test
    fun aCreationWhoseAlarmCannotBeSetIsTakenBackAndCanBeTriedAgain() {
        val fixture = Fixture(AppState(), alarmsSet = false)
        val starter = AppState().starterChecklist("Checklist", pageDay)
        val edits = listOf(
            ChecklistEdit.Rename("Plants"),
            ChecklistEdit.AddItem("itm_1", "Water the plants"),
            ChecklistEdit.SetCheckpointRemind(starter.checkpoints.single().id, remind = true),
        )

        assertFalse(fixture.commands.create(starter, edits))
        assertNull(fixture.repository.load().checklist(starter.id))

        fixture.alarmsSet = true
        assertTrue(fixture.commands.create(starter, edits))
        assertEquals(listOf(starter.id), fixture.repository.load().checklists.map { it.id })
    }

    @Test
    fun aSaveWhoseNewAlarmCannotBeSetIsTakenBack() {
        val fixture = Fixture(AppState(checklists = listOf(plants)), alarmsSet = false)

        assertFalse(fixture.commands.save(plants.id, listOf(ChecklistEdit.SetCheckpointRemind("cpt_1", remind = true))))

        assertEquals(plants, fixture.stored)
        assertEquals(2, fixture.reconciled.size)
    }

    /** Without the access to set alarms, the save stands and the reminder's row explains what is missing. */
    @Test
    fun withoutAlarmAccessASaveStands() {
        val fixture = Fixture(AppState(checklists = listOf(plants)), alarmsSet = false, exactAllowed = false)

        assertTrue(fixture.commands.save(plants.id, listOf(ChecklistEdit.SetCheckpointRemind("cpt_1", remind = true))))

        assertTrue(requireNotNull(fixture.stored).checkpoints.single().remind)
    }

    private class FakeTrackerRepository(initialState: AppState) : TrackerRepository {
        private var state = initialState

        override fun load(): AppState = state

        override fun save(state: AppState): Boolean {
            this.state = state
            return true
        }

        override fun update(transform: (AppState) -> AppState): TrackerUpdateResult {
            val updated = runCatching { transform(state) }.getOrElse { return TrackerUpdateResult.SaveFailed }
            save(updated)
            return TrackerUpdateResult.Updated(updated)
        }
    }
}

package de.nicolasmahn.momentum.domain

import de.nicolasmahn.momentum.domain.checklist.ChecklistConfiguration
import de.nicolasmahn.momentum.domain.checklist.ChecklistItem
import de.nicolasmahn.momentum.domain.checklist.Checkpoint
import de.nicolasmahn.momentum.domain.checklist.CheckpointTime
import de.nicolasmahn.momentum.domain.checklist.ViewHue
import de.nicolasmahn.momentum.domain.compatibility.careChecklist
import de.nicolasmahn.momentum.domain.care.careState
import de.nicolasmahn.momentum.domain.care.withCare
import de.nicolasmahn.momentum.domain.checklist.ViewIcon
import de.nicolasmahn.momentum.domain.checklist.isInUse
import de.nicolasmahn.momentum.domain.checklist.joinRewards
import de.nicolasmahn.momentum.domain.mo.habitStreak
import de.nicolasmahn.momentum.domain.settings.FeatureVisibilitySettings
import de.nicolasmahn.momentum.domain.activity.withActivity
import de.nicolasmahn.momentum.domain.measurement.withWeight
import de.nicolasmahn.momentum.domain.compatibility.MoNeed
import de.nicolasmahn.momentum.domain.mo.keepingWeeks
import de.nicolasmahn.momentum.domain.questions.withEating
import de.nicolasmahn.momentum.domain.compatibility.StandardSupplementIds
import de.nicolasmahn.momentum.domain.supplement.supplementDay
import de.nicolasmahn.momentum.domain.supplement.testSupplementsChecklist
import de.nicolasmahn.momentum.domain.time.withReading
import de.nicolasmahn.momentum.reminder.checklistReminderSlots
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A checklist with no item in use asks nothing: it has no tile on Home, no alarm and no bank in
 * the streak. Only one stored before the last item was guarded can be like that.
 */
class ChecklistsInUseTest {
    private val today = LocalDate.parse("2026-10-14")
    private val plants = ChecklistConfiguration(
        id = "chk_plants",
        label = "Plants",
        icon = ViewIcon.Plant,
        hue = ViewHue.Forest,
        checkpoints = listOf(Checkpoint("cpt_1", CheckpointTime.Fixed(LocalTime.of(9, 0)), remind = true)),
        items = listOf(ChecklistItem("itm_1", "Water the plants")),
    )
    private val empty = plants.copy(id = "chk_empty", label = "Empty", items = emptyList())
    private val ended = plants.copy(id = "chk_ended", label = "Ended", items = listOf(ChecklistItem("itm_1", "Water the plants", until = today.minusDays(1))))
    private val upcoming = plants.copy(id = "chk_soon", label = "Soon", items = listOf(ChecklistItem("itm_1", "Water the plants", from = today.plusDays(1))))
    private val state = AppState(checklists = listOf(plants, empty, ended, upcoming))

    @Test
    fun aChecklistIsInUseWhileAnItemIsInUseFromTodayOn() {
        assertTrue(plants.isInUse(today))
        assertFalse(empty.isInUse(today))
        assertFalse(ended.isInUse(today))
        assertTrue(upcoming.isInUse(today))
        assertTrue(ended.isInUse(today.minusDays(1)))
    }

    @Test
    fun homeShowsTheChecklistsInUseThatAreNotHidden() {
        assertEquals(listOf(plants, upcoming), state.checklistsInUse(FeatureVisibilitySettings(), today))
        assertEquals(listOf(upcoming), state.checklistsInUse(FeatureVisibilitySettings().withChecklistVisibility(plants.id, false, today), today))
    }

    /** A feature's checklist with nothing on it hides its feature wherever the switches are read for use: Home, alarms and the streak. */
    @Test
    fun aFeaturesChecklistWithoutAnItemHidesItsFeature() {
        val emptyCare = requireNotNull(careState().careChecklist).copy(items = emptyList())
        val withCare = state.copy(checklists = state.checklists + emptyCare + testSupplementsChecklist())
        val shown = withCare.visibilityInUse(FeatureVisibilitySettings(), today)

        assertFalse(shown.isVisible(MoNeed.Care.view))
        assertTrue(shown.isVisible(MoNeed.Supplements.view))
        assertFalse(shown.isChecklistVisible(empty.id))
        assertTrue(shown.isChecklistVisible(plants.id))
        // The switch itself is not touched: Settings still shows and keeps it.
        assertTrue(FeatureVisibilitySettings().isVisible(MoNeed.Care.view))
    }

    @Test
    fun aChecklistWithoutAnItemInUseHasNoReminderSlot() {
        val slots = checklistReminderSlots(state, FeatureVisibilitySettings(), today).map { it.viewId }
        assertEquals(listOf(plants.id, upcoming.id), slots)
    }

    /**
     * A checklist with nothing in use is out of the streak's whole history, as it always was, not
     * from today: Supplements whose one item ended yesterday and was ticked only on its first day
     * never brings its unticked days back as misses, so Weight's eight days keep the streak.
     */
    @Test
    fun aChecklistOutOfUseLeavesTheStreakAsItWas() {
        val monday = LocalDate.parse("2026-10-05")
        val today = monday.plusDays(7)
        val supplements = testSupplementsChecklist().let { checklist ->
            checklist.copy(items = checklist.items.filter { it.id == StandardSupplementIds.MULTIVITAMIN }.map { it.copy(until = today.minusDays(1)) })
        }
        val state = AppState().withActivity().withWeight().withEating().withReading().withCare()
            .let { it.copy(checklists = it.checklists + supplements, checklistDays = listOf(supplementDay(monday.toString(), 0, setOf(StandardSupplementIds.MULTIVITAMIN)))) }
            .keepingWeeks(MoNeed.Weight, monday, "xxxxxxx", "x")

        assertEquals(8, habitStreak(state, state.visibilityInUse(FeatureVisibilitySettings(), today), today).days)
    }

    @Test
    fun aChecklistWithoutAnItemInUseHasNoBankInTheStreak() {
        val joined = state.checklists.fold(state) { joined, checklist -> joinRewards(joined, checklist.id, today.minusDays(7)) }
        assertEquals(setOf(plants.id, upcoming.id), habitStreak(joined, FeatureVisibilitySettings(), today).standings.keys.filter { it.type.value == "checklist" }.mapTo(HashSet()) { it.id })
    }
}

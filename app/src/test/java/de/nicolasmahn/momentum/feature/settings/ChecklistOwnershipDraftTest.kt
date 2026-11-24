package de.nicolasmahn.momentum.feature.settings

import de.nicolasmahn.momentum.domain.checklist.AboutDay
import de.nicolasmahn.momentum.domain.checklist.ChecklistConfiguration
import de.nicolasmahn.momentum.domain.checklist.ChecklistEdit
import de.nicolasmahn.momentum.domain.checklist.ChecklistItem
import de.nicolasmahn.momentum.domain.checklist.ChecklistRewards
import de.nicolasmahn.momentum.domain.checklist.Checkpoint
import de.nicolasmahn.momentum.domain.checklist.CheckpointTime
import de.nicolasmahn.momentum.domain.checklist.Dated
import de.nicolasmahn.momentum.domain.checklist.RewardPeriod
import de.nicolasmahn.momentum.domain.checklist.ViewHue
import de.nicolasmahn.momentum.domain.checklist.ViewIcon
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChecklistOwnershipDraftTest {
    private val firstRecord = LocalDate.parse("2026-10-19")
    private val switchDay = firstRecord.plusDays(1)
    private val today = LocalDate.parse("2026-10-25")
    private val configured = ChecklistConfiguration(
        id = "my-routine", label = "Routine", icon = ViewIcon.Checklist, hue = ViewHue.Sky,
        checkpoints = listOf(Checkpoint("check", CheckpointTime.Fixed(LocalTime.of(9, 0)), remind = false)),
        items = listOf(ChecklistItem("task", "Task")), rewards = ChecklistRewards(3, 7),
        about = Dated(listOf(Dated.Revision(AboutDay.Yesterday), Dated.Revision(AboutDay.Today, switchDay))),
    )

    @Test fun theEditorDraftResolvesItsStartFromEveryRawRecordDate() {
        val dates = setOf(firstRecord, switchDay)
        val draft = ChecklistDraft(configured)
        assertTrue(draft.edit(ChecklistEdit.LeaveRewards, today, recordedDates = dates))
        assertEquals(listOf(RewardPeriod(switchDay, today.minusDays(1))), draft.draft(today, dates).rewards!!.periods)
    }

    @Test fun evenAnUnjudgedFactKeepsOwnershipEditsDatedWithoutABankOrMo() {
        val dates = setOf(firstRecord)
        val draft = ChecklistDraft(configured.copy(rewards = null))
        assertTrue(draft.edit(ChecklistEdit.SetAbout(AboutDay.Yesterday), today, recordedDates = dates))
        val edited = draft.draft(today, dates)
        assertEquals(AboutDay.Today, edited.about.on(switchDay))
        assertEquals(AboutDay.Yesterday, edited.about.on(today))
        assertEquals(3, edited.about.revisions.size)
    }
}

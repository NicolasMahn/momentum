package com.example.workouttracker.persistence.json

import com.example.workouttracker.domain.checklist.AboutDay
import com.example.workouttracker.domain.checklist.ChecklistEdit
import com.example.workouttracker.domain.checklist.ChecklistTarget
import com.example.workouttracker.domain.checklist.CheckpointTime
import com.example.workouttracker.domain.checklist.MoMotif
import com.example.workouttracker.domain.checklist.ViewHue
import com.example.workouttracker.domain.checklist.ViewIcon
import com.example.workouttracker.domain.checklist.WhenAway
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.Month
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/** A page's unsaved edits read back exactly as written, every kind of them. */
class ChecklistEditJsonTest {
    @Test
    fun everyEditReadsBackAsWritten() {
        val edits = listOf(
            ChecklistEdit.Rename("Plants"),
            ChecklistEdit.SetIcon(ViewIcon.Plant),
            ChecklistEdit.SetHue(ViewHue.Forest),
            ChecklistEdit.SetMotif(MoMotif.Book),
            ChecklistEdit.SetMotif(null),
            ChecklistEdit.AddCheckpoint("cpt_2", "Evening", CheckpointTime.BeforeSleep(30), remind = true),
            ChecklistEdit.RenameCheckpoint("cpt_2", "Night"),
            ChecklistEdit.RenameCheckpoint("cpt_2", null),
            ChecklistEdit.SetCheckpointTime("cpt_2", CheckpointTime.AfterWake(45)),
            ChecklistEdit.SetCheckpointTime("cpt_2", CheckpointTime.Fixed(LocalTime.of(21, 15))),
            ChecklistEdit.SetCheckpointRemind("cpt_2", remind = false),
            ChecklistEdit.RemoveCheckpoint("cpt_2"),
            ChecklistEdit.SetTarget(ChecklistTarget.PerWeek(2)),
            ChecklistEdit.SetTarget(ChecklistTarget.PerDay),
            ChecklistEdit.AddItem("itm_1", "Water the plants"),
            ChecklistEdit.AddItem("itm_2", "Feed", checkpoints = listOf("cpt_1"), target = ChecklistTarget.PerWeek(3), months = setOf(Month.MARCH, Month.OCTOBER)),
            ChecklistEdit.RenameItem("itm_1", "Mist the plants"),
            ChecklistEdit.SetItemCheckpoints("itm_1", listOf("cpt_1")),
            ChecklistEdit.SetItemCheckpoints("itm_1", null),
            ChecklistEdit.SetItemTarget("itm_1", ChecklistTarget.Weekdays(setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY))),
            ChecklistEdit.SetItemTarget("itm_1", ChecklistTarget.PerDay),
            ChecklistEdit.SetItemTarget("itm_1", null),
            ChecklistEdit.SetItemSeason("itm_1", setOf(Month.JANUARY)),
            ChecklistEdit.SetItemSeason("itm_1", null),
            ChecklistEdit.RemoveItem("itm_2"),
            ChecklistEdit.SetItemWhenAway("itm_1", WhenAway.LowerTarget),
            ChecklistEdit.SetItemAwayTimes("itm_1", 3),
            ChecklistEdit.SetItemAwayTimes("itm_1", null),
            ChecklistEdit.SetItemExceptionsOnly("itm_1", true),
            ChecklistEdit.SetItemExceptionsOnly("itm_1", false),
            ChecklistEdit.SetAbout(AboutDay.Yesterday),
            ChecklistEdit.JoinRewards,
            ChecklistEdit.LeaveRewards,
            ChecklistEdit.SetRewardRules(5, 10),
        )
        assertEquals(edits, AppStateJsonCodec.decodeChecklistEdits(AppStateJsonCodec.encodeChecklistEdits(edits)))
        assertEquals(emptyList<ChecklistEdit>(), AppStateJsonCodec.decodeChecklistEdits(AppStateJsonCodec.encodeChecklistEdits(emptyList())))
    }

    @Test
    fun anEditThatCannotBeReadExactlyIsRefused() {
        assertThrows(RuntimeException::class.java) { AppStateJsonCodec.decodeChecklistEdits("""[{"edit":"paint","label":"x"}]""") }
        assertThrows(RuntimeException::class.java) { AppStateJsonCodec.decodeChecklistEdits("""[{"edit":"icon","icon":"unicorn"}]""") }
        assertThrows(RuntimeException::class.java) { AppStateJsonCodec.decodeChecklistEdits("""[{"edit":"rename"}]""") }
    }
}

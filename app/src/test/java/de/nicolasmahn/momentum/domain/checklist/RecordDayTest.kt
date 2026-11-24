package de.nicolasmahn.momentum.domain.checklist

import de.nicolasmahn.momentum.domain.AppState
import de.nicolasmahn.momentum.domain.compatibility.CareChecklistId
import de.nicolasmahn.momentum.domain.compatibility.CareLabels
import de.nicolasmahn.momentum.domain.compatibility.defaultCareChecklist
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Which record a due day asks for: its own, or the day before's on a checklist about yesterday.
 * The rule is dated, and a record is asked for once however the checklist switches.
 */
class RecordDayTest {
    /** A Thursday. */
    private val today = LocalDate.parse("2026-10-15")
    private val yesterday = today.minusDays(1)
    private val lastNight = ChecklistConfiguration(
        id = "chk_night",
        label = "Yesterday",
        icon = ViewIcon.Journal,
        hue = ViewHue.Plum,
        checkpoints = listOf(Checkpoint("cpt_morning", CheckpointTime.Fixed(LocalTime.of(8, 0)), remind = true)),
        items = listOf(ChecklistItem("itm_phone", "No phone in bed"), ChecklistItem("itm_violin", "Practiced violin")),
        rewards = ChecklistRewards(maxPasses = 3, everyKeptDays = 7, periods = listOf(RewardPeriod(LocalDate.parse("2026-10-05")))),
    )
    private val aboutYesterday = lastNight.copy(about = Dated.always(AboutDay.Yesterday))

    @Test fun aDueDayAsksForTheRecordOfTheDayItIsAbout() {
        assertEquals(today, lastNight.recordDayFor(today))
        assertEquals(yesterday, aboutYesterday.recordDayFor(today))
        assertEquals(today to yesterday, lastNight.recordJudgedOn(today) to aboutYesterday.recordJudgedOn(today))
    }

    /** The reward walk read which record every earlier day asked for, so a checklist in rewards changes it from today; one never judged changes plainly. */
    @Test fun theChoiceIsDatedOnceADayWasJudgedByItAndPlainBefore() {
        val switched = AppState(checklists = listOf(lastNight)).applyEdit(lastNight.id, ChecklistEdit.SetAbout(AboutDay.Yesterday), today).checklist(lastNight.id)!!
        assertEquals(Dated.always(AboutDay.Today).changedFrom(today, AboutDay.Yesterday), switched.about)
        assertEquals(yesterday to yesterday, switched.recordDayFor(yesterday) to switched.recordDayFor(today))

        val outside = lastNight.copy(rewards = null)
        assertEquals(Dated.always(AboutDay.Yesterday), outside.applyEdit(ChecklistEdit.SetAbout(AboutDay.Yesterday), today).about)
        // Changing back on the same day leaves no revision.
        assertEquals(lastNight, lastNight.applyEdit(ChecklistEdit.SetAbout(AboutDay.Yesterday), today).applyEdit(ChecklistEdit.SetAbout(AboutDay.Today), today))
    }

    /**
     * Switching to yesterday on Thursday: Thursday asks for Wednesday's record, which Wednesday
     * already asked for, so Thursday is not judged. Switching back on Monday: Monday asks for its
     * own record, and Sunday's is asked for by no day. Every other record is asked for exactly once.
     */
    @Test fun aRecordIsAskedForOnceHoweverTheChecklistSwitches() {
        val switched = lastNight.applyEdit(ChecklistEdit.SetAbout(AboutDay.Yesterday), today)
        assertNull(switched.recordJudgedOn(today))
        assertEquals(yesterday, switched.recordJudgedOn(yesterday))
        assertEquals(today, switched.recordJudgedOn(today.plusDays(1)))

        val monday = today.plusDays(4)
        val sunday = monday.minusDays(1)
        val back = switched.applyEdit(ChecklistEdit.SetAbout(AboutDay.Today), monday)
        val asked = (-3L..10L).map { today.plusDays(it) }.mapNotNull(back::recordJudgedOn)
        assertEquals(monday, back.recordJudgedOn(monday))
        assertFalse(sunday in asked)
        assertEquals(asked.size, asked.toSet().size)
    }

    @Test fun checklistAboutCanChangeRegardlessOfItsId() {
        val care = defaultCareChecklist(CareLabels("Care", "Morning", "Evening", "Brush teeth", "Hair foam", "Clean between teeth"), remindMorning = true, remindEvening = true)
        assertEquals(today, care.recordDayFor(today))

        val edited = AppState(checklists = listOf(care))
            .applyEdit(CareChecklistId, ChecklistEdit.SetAbout(AboutDay.Yesterday), today)
            .checklist(CareChecklistId)!!
        assertEquals(AboutDay.Yesterday, edited.about.current)
        assertEquals(yesterday, edited.recordDayFor(today))

        val sameConfigurationWithAnotherId = edited.copy(id = "daily-notes")
        assertEquals(edited.about, sameConfigurationWithAnotherId.about)
        assertEquals(yesterday, sameConfigurationWithAnotherId.recordDayFor(today))
    }
}

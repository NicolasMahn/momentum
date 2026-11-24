package de.nicolasmahn.momentum.reminder

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import de.nicolasmahn.momentum.core.text.resolve
import de.nicolasmahn.momentum.domain.checklist.MoMotif
import de.nicolasmahn.momentum.domain.measurement.testWeightMeasurement
import de.nicolasmahn.momentum.domain.questions.testEatingQuestions
import de.nicolasmahn.momentum.domain.supplement.testSupplementsChecklist
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ConfiguredReminderWordsTest {
    private val resources = ApplicationProvider.getApplicationContext<Context>().resources
    private val date = LocalDate.parse("2026-10-14")
    private val days = ReminderDays(date, date)

    @Test fun inheritedIdsDoNotOverrideConfiguredNamesOrKindWording() {
        val measurement = loggedReminderMessage(testWeightMeasurement().copy(label = "Morning number", unit = "cm"), days)
        assertEquals("Morning number", measurement.title.resolve(resources))
        assertFalse(measurement.text.resolve(resources).contains("weight", ignoreCase = true))
        val questions = loggedReminderMessage(testEatingQuestions().copy(label = "Evening answers"), days)
        assertEquals("Evening answers", questions.title.resolve(resources))
        assertFalse(questions.text.resolve(resources).contains("eating", ignoreCase = true))
    }

    @Test fun checklistUsesEditedLabelsEvenForInheritedItemIdsAndAnyMotif() {
        val inherited = testSupplementsChecklist()
        val checklist = inherited.copy(label = "My daily list", items = inherited.items.mapIndexed { index, item -> item.copy(label = "Custom item $index") })
        val checkpoint = checklist.checkpoints.single()
        val plain = checklistReminderMessage(checklist.copy(mo = checklist.mo.copy(motif = null)), checkpoint, checklist.items, days)
        MoMotif.entries.forEach { motif ->
            assertEquals(plain, checklistReminderMessage(checklist.copy(mo = checklist.mo.copy(motif = motif)), checkpoint, checklist.items, days))
        }
        assertEquals("My daily list", plain.title.resolve(resources))
        checklist.items.forEach { item -> assertTrue(plain.text.resolve(resources).contains(item.label)) }
        assertFalse(plain.text.resolve(resources).contains("creatine", ignoreCase = true))
    }
}

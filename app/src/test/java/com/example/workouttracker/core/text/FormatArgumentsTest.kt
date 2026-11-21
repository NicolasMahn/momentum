package com.example.workouttracker.core.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** The comparison `StringTranslationTest` trusts: what a string reads, not which placeholders it spells. */
class FormatArgumentsTest {
    @Test
    fun aBarePlaceholderReadsTheNextArgument() {
        assertEquals(mapOf(1 to setOf('s'), 2 to setOf('s')), formatArguments("Noch offen: %s (%s)"))
        assertEquals(mapOf(1 to setOf('d'), 2 to setOf('s')), formatArguments("%d %% %<d, %s%n"))
    }

    @Test
    fun aTranslationNeverReadsAnArgumentItsCallerDoesNotGive() {
        // The same "%s" twice, so a comparison of placeholder spellings let it through.
        assertNotNull(argumentMismatch("Still open: %s", "Noch offen: %s (%s)"))
        assertNotNull(argumentMismatch("%d days", "%s Tage"))
        assertNull(argumentMismatch("%s and %s", "%2\$s, dann %1\$s"))
    }

    @Test
    fun onlyAOneFormMayLeaveOutTheQuantity() {
        val english = "I've been cold for %1\$d days. One of these would warm me: %2\$s."
        assertNull(argumentMismatch(english, "Seit einem Tag kalt: %2\$s.", isOneForm = true))
        assertNotNull(argumentMismatch(english, "Seit einem Tag kalt: %2\$s.", isOneForm = false))
        assertNotNull(argumentMismatch(english, "Seit %1\$d Tag kalt.", isOneForm = true))
        // With two numbers nothing says which one the plural counts.
        assertNotNull(argumentMismatch("%1\$d of %2\$d freezes", "%1\$d von einem Freeze", isOneForm = true))
    }
}

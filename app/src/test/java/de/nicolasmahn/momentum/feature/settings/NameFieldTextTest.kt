package de.nicolasmahn.momentum.feature.settings

import androidx.compose.runtime.saveable.SaverScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** A name field nothing was typed into shows the latest name; one typed into keeps what was typed. */
class NameFieldTextTest {
    @Test
    fun anUntouchedFieldFollowsTheLatestName() {
        val field = NameFieldText("Plants")
        field.follow("Garden", settled = false)
        assertEquals("Garden", field.text)
        assertFalse(field.typed)
    }

    /** Saved, what was typed is stored, so the field is untouched again and a later stored rename reaches it. */
    @Test
    fun aFieldWhoseTypingIsStoredFollowsTheLatestNameAgain() {
        val field = NameFieldText("Plants")
        field.type("Garden ")
        field.follow("Garden", settled = true)
        assertFalse(field.typed)
        assertEquals("Garden", field.text)

        field.follow("Yard", settled = false)
        assertEquals("Yard", field.text)
    }

    @Test
    fun aFieldTypedIntoKeepsWhatWasTyped() {
        val field = NameFieldText("Plants")
        field.type("Plants ")
        field.follow("Garden", settled = false)
        assertEquals("Plants ", field.text)
        assertTrue(field.typed)

        field.type("")
        field.follow("Garden", settled = false)
        assertEquals("", field.text)
    }

    @Test
    fun theFieldSurvivesBeingSavedAndRestored() {
        val field = NameFieldText("Plants")
        field.type("Plants ")
        val saved = with(NameFieldText.Saver) { SaverScope { true }.save(field) }
        val restored = requireNotNull(NameFieldText.Saver.restore(requireNotNull(saved)))
        assertEquals("Plants ", restored.text)
        assertTrue(restored.typed)
        restored.follow("Garden", settled = false)
        assertEquals("Plants ", restored.text)
    }
}

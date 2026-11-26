package de.nicolasmahn.momentum.testing

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert

/**
 * Asserts what a text field holds. `assertTextEquals` also compares the field's label and
 * supporting text, so it cannot state the value of a labelled field on its own.
 */
fun SemanticsNodeInteraction.assertInputEquals(expected: String): SemanticsNodeInteraction =
    assert(
        SemanticsMatcher("input text = '$expected'") { node ->
            node.config.getOrNull(SemanticsProperties.EditableText)?.text == expected
        },
    )

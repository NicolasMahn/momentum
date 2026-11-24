package de.nicolasmahn.momentum.domain.checklist

/**
 * Whether [text] is one line a person typed that can be stored as it is: no space around it, a
 * number of Unicode code points within [length], and nothing that breaks a line, no control
 * character and no character that only steers the reading direction. Spaces inside it and every
 * printable symbol are kept as typed, and what is refused is refused rather than removed: nothing a
 * person typed is shortened or respelled.
 *
 * The one rule for short text a view shows inside its own sentences and controls: a measurement's
 * unit, a question's text and an answer's label.
 */
internal fun isTypedLine(text: String, length: IntRange): Boolean =
    text == text.trim() &&
        text.codePointCount(0, text.length) in length &&
        text.codePoints().noneMatch { it.breaksALine() || Character.getType(it) == Character.CONTROL.toInt() || it in DirectionControls }

private fun Int.breaksALine(): Boolean = Character.getType(this).let { it == Character.LINE_SEPARATOR.toInt() || it == Character.PARAGRAPH_SEPARATOR.toInt() }

/** The marks, embeddings, overrides and isolates of the Unicode bidirectional algorithm. */
private val DirectionControls: Set<Int> = setOf(0x061C, 0x200E, 0x200F) + (0x202A..0x202E) + (0x2066..0x2069)

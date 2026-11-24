package de.nicolasmahn.momentum.feature.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * What every draft page keeps, written once: the [stored] value as its baseline, the ordered
 * [edits] made on the page, the name fields left blank and the fields holding text typed but not
 * yet added. What a page shows is its edits replayed in order over the latest stored value, never
 * reordered, with one the stored value refuses left out; Save hands the edits over. Only a kind's
 * own [keepInstead] replaces the last edit with one that overrides it, or takes it away when the
 * page is back where it started.
 *
 * Each kind of draft keeps its own rules: what dirty means ([isDirty]), and what its edits are
 * applied with, which it brings to [keep] and to its own replay. Blank names and typed text stay
 * keyed by field, so whoever asks can tell which field holds a page back.
 */
internal abstract class SettingsDraft<S, E>(
    stored: S,
    edits: List<E>,
    blankNames: Set<String>,
    typedFields: Set<String>,
) {
    var stored: S by mutableStateOf(stored)
        protected set
    var edits: List<E> by mutableStateOf(edits)
        private set

    /** The name fields left blank, by what they name. */
    var blankNames: Set<String> by mutableStateOf(blankNames)
        private set

    /** The fields holding text not yet added, by field. */
    protected var typedFields: Set<String> by mutableStateOf(typedFields)
        private set

    /** Whether Save would change anything. */
    abstract val isDirty: Boolean

    /**
     * Whether a field holds something Save cannot store, which holds Save back whatever else
     * changed: a name left blank, and whatever a kind's own fields refuse.
     */
    open val heldBack: Boolean get() = blankNames.isNotEmpty()

    /** Saving needs a change, and nothing that holds it back. */
    val canSave: Boolean get() = isDirty && !heldBack

    /** Whether leaving would lose something: a change, a cleared name or text not yet added. */
    open val hasUnsavedWork: Boolean get() = isDirty || blankNames.isNotEmpty() || typedFields.isNotEmpty()

    /**
     * Whether text typed but not yet added is all the page still holds. Save has nothing to store
     * then, and a page just saved stays open for it instead of returning to the list.
     */
    val holdsOnlyTypedText: Boolean get() = !isDirty && blankNames.isEmpty() && typedFields.isNotEmpty()

    /** Whether the name of [key] is stored: not blank, and no edit naming it waits for Save. */
    fun nameStored(key: String): Boolean = key !in blankNames && edits.none { it.names(key) }

    /** Whether this edit gives [key] its name. */
    protected abstract fun E.names(key: String): Boolean

    /** The key of the name field that goes with what this edit removes, or null where it removes none. */
    protected abstract fun E.removes(): String?

    /**
     * Keeps [edit] unless [applied], the edit over what the page shows, refuses it as the stored
     * value would. Answers whether it was kept.
     */
    protected fun keep(edit: E, applied: () -> S): Boolean = runCatching(applied)
        .onSuccess {
            edits = edits + edit
            // What was removed took its field with it, so a blank there holds nothing back.
            edit.removes()?.let { blankNames = blankNames - it }
        }
        .isSuccess

    /**
     * Keeps [edit] in place of the last edit, which it overrides, unless [applied] refuses it as
     * [keep] would. One that brings the page back to where the last edit started ([undoes]) takes
     * the last edit away instead, so nothing is left to save.
     */
    protected fun keepInstead(edit: E, undoes: Boolean, applied: () -> S): Boolean = runCatching(applied)
        .onSuccess { edits = edits.dropLast(1) + listOfNotNull(edit.takeUnless { undoes }) }
        .isSuccess

    /** The edits were stored, so nothing is left to save. A kind that keeps typed text beside them lets it go here too. */
    open fun saved() {
        edits = emptyList()
    }

    /** The name field for [key] was typed into: it holds [name], or nothing while null. */
    fun named(key: String, name: String?) {
        blankNames = if (name == null) blankNames + key else blankNames - key
    }

    /** The blank names [gone] says are no longer on the page hold nothing back: their fields went with what was removed or hidden. */
    protected fun forgetBlankNames(gone: (String) -> Boolean) {
        blankNames = blankNames.filterNotTo(LinkedHashSet(), gone)
    }

    /** The field [key] holds [text] not yet added. A kind that must know what it holds, and not only that it does, reads it here. */
    open fun typing(key: String, text: String) {
        typedFields = if (text.isBlank()) typedFields - key else typedFields + key
    }
}

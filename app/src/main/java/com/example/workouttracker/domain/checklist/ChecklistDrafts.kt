package com.example.workouttracker.domain.checklist

import com.example.workouttracker.domain.AppState
import java.time.LocalDate

/**
 * [edits] applied one after another from [today]: what a page shows over the latest stored
 * configuration, and what Save stores on the day it is pressed. An edit the configuration no
 * longer takes, such as a rename of an item removed meanwhile, is left out, so what is shown is
 * what is saved. [recordedDates] is what leaving rewards needs; see [applyEdit].
 */
fun ChecklistConfiguration.applyingEdits(edits: List<ChecklistEdit>, today: LocalDate, recordedDates: Set<LocalDate> = emptySet()): ChecklistConfiguration =
    edits.fold(this) { draft, edit -> runCatching { draft.applyEdit(edit, today, recordedDates) }.getOrDefault(draft) }

/** The stored checklist [checklistId] with [edits] applied from [today]; throws without such a checklist. */
fun AppState.applyingEdits(checklistId: String, edits: List<ChecklistEdit>, today: LocalDate): AppState {
    val checklist = requireNotNull(checklist(checklistId)) { "No checklist $checklistId." }
    return saveChecklist(checklist.applyingEdits(edits, today, checklistRecordedDates(checklistId)))
}

/**
 * A starter as it is created on [today]: its checkpoint starts the day the checklist is stored,
 * not the day its setup was opened, which may lie before 04:00. Only a starter has no items.
 */
fun ChecklistConfiguration.startedOn(today: LocalDate): ChecklistConfiguration {
    require(items.isEmpty()) { "Only a starter, with no items, is started on a day." }
    return copy(checkpoints = checkpoints.map { it.copy(from = today) })
}

package de.nicolasmahn.momentum.domain.checklist

/** A current save must reload every configuration and day without merging or discarding facts. */
fun requireStorableChecklists(checklists: List<ChecklistConfiguration>, days: List<ChecklistDay>) {
    require(checklists.distinctBy { it.id }.size == checklists.size) { "Two checklists share an ID." }
    require(days.distinctBy { it.checklistId to it.dateKey }.size == days.size) { "Two checklist records share a day." }
    days.forEach { day ->
        require(day.checklistId.isNotBlank()) { "A checklist record needs its owner's ID." }
        require(day.ticks.none(String::isBlank) && day.misses.none(String::isBlank)) { "A checklist mark must not be blank." }
    }
}

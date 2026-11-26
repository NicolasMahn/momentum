package de.nicolasmahn.momentum

import de.nicolasmahn.momentum.domain.AppState
import de.nicolasmahn.momentum.testfixtures.LegacyCareAction
import de.nicolasmahn.momentum.testfixtures.CareChecklistId
import de.nicolasmahn.momentum.testfixtures.CareLabels
import de.nicolasmahn.momentum.testfixtures.LegacyCareSession
import de.nicolasmahn.momentum.testfixtures.defaultCareChecklist
import de.nicolasmahn.momentum.domain.checklist.ChecklistDay
import de.nicolasmahn.momentum.domain.checklist.ChecklistDayOrder
import de.nicolasmahn.momentum.domain.checklist.tickOf

/** Care as an English installation is seeded, so screens built without the repository have it. */
internal val TestCareChecklist = defaultCareChecklist(
    CareLabels(
        checklist = "Care",
        morning = "Morning",
        evening = "Evening",
        toothbrushing = "Brush teeth",
        hairFoam = "Hair foam",
        cleanBetweenTeeth = "Clean between teeth",
    ),
    remindMorning = false,
    remindEvening = false,
)

/** A state with Care configured and these actions done, each day at its latest action's time. */
internal fun careState(vararg done: Pair<String, Pair<LegacyCareSession, LegacyCareAction>>, timestampMillis: Long = 1L): AppState = AppState(
    checklists = listOf(TestCareChecklist),
    checklistDays = done.groupBy { it.first }.map { (dateKey, sameDay) ->
        ChecklistDay(CareChecklistId, dateKey, timestampMillis, sameDay.mapTo(LinkedHashSet()) { (_, slot) -> tickOf(slot.first.storageId, slot.second.storageId) })
    }.sortedWith(ChecklistDayOrder),
)

/** What Care recorded on [dateKey], as session and action. */
internal fun AppState.careDone(dateKey: String): Set<Pair<LegacyCareSession, LegacyCareAction>> =
    checklistDays.filter { it.checklistId == CareChecklistId && it.dateKey == dateKey }
        .flatMap { it.ticks }
        .mapTo(LinkedHashSet()) { tick ->
            requireNotNull(LegacyCareSession.fromStorageId(tick.substringBefore('/'))) to requireNotNull(LegacyCareAction.fromStorageId(tick.substringAfter('/')))
        }

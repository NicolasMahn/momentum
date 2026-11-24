package de.nicolasmahn.momentum.domain.compatibility

import de.nicolasmahn.momentum.domain.compatibility.MoNeed

import de.nicolasmahn.momentum.domain.mo.MoRecordIndex
import de.nicolasmahn.momentum.domain.questions.areQuestionsLoggedOnTimeFor
import java.time.LocalDate

/** Historical profiles interpret current indexed facts; the index itself knows no preset IDs. */
internal fun MoRecordIndex.isCareComplete(day: LocalDate): Boolean =
    state.careChecklist?.let { progress(it, day).isComplete } ?: false

internal fun MoRecordIndex.isSupplementsComplete(day: LocalDate): Boolean =
    state.supplementsChecklist?.let { progress(it, day).isComplete } ?: false

internal fun MoRecordIndex.isDailyCareComplete(day: LocalDate): Boolean =
    state.careChecklist?.let { checklist ->
        progress(checklist, day).checkpoints.all { checkpoint -> checkpoint.unmet.all { it.target != null } }
    } ?: false

internal fun MoRecordIndex.isEatingLoggedOnTime(day: LocalDate): Boolean {
    val recordDay = day.minusDays(1)
    return areQuestionsLoggedOnTimeFor(questionsOn(EatingQuestionsId, recordDay), EatingQuestionsId, recordDay.toString(), day.toString())
}

internal fun MoRecordIndex.asksNothing(need: MoNeed, day: LocalDate): Boolean = when (need) {
    MoNeed.Supplements -> state.supplementsChecklist
    MoNeed.Care -> state.careChecklist
    else -> null
}?.let { progress(it, day).asksNothing } ?: false

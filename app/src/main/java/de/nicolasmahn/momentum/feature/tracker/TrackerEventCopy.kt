package de.nicolasmahn.momentum.feature.tracker

import de.nicolasmahn.momentum.core.text.UiText
import de.nicolasmahn.momentum.domain.TrackerRef
import de.nicolasmahn.momentum.domain.mo.MoMood
import de.nicolasmahn.momentum.domain.mo.contentKey
import de.nicolasmahn.momentum.feature.mo.MoContentCatalogue
import de.nicolasmahn.momentum.feature.mo.label

/** Earned-token wording names the basis used at the close, never a newly edited goal. */
internal fun trackerTokenEarnedLine(ref: TrackerRef, mood: MoMood): UiText? {
    val content = mood.contentKey(ref)
    val amount = mood.tokenEarningBasisByTracker[ref] ?: return null
    return MoContentCatalogue[content].tokenEarnedLine(mood.label(ref), amount, acrossWeeks = ref in mood.tokenEarnedAcrossWeeks)
}

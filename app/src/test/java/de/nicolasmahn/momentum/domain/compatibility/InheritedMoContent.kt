package de.nicolasmahn.momentum.domain.compatibility

import de.nicolasmahn.momentum.domain.TrackerRef
import de.nicolasmahn.momentum.domain.trackerRef
import de.nicolasmahn.momentum.domain.checklist.ChecklistConfiguration
import de.nicolasmahn.momentum.domain.checklist.MoMotif
import de.nicolasmahn.momentum.domain.mo.MoContentKey
import de.nicolasmahn.momentum.domain.mo.MoViewKind
import de.nicolasmahn.momentum.domain.mo.MoMood
import de.nicolasmahn.momentum.domain.mo.contentKey

/**
 * The content of a task as it is configured, decided here and nowhere else, so a flavor saved on a
 * checklist's page reaches every drawing, line, takeover and reminder. Supplements and Care look
 * and speak as the motif their [checklist] wears, Plain (no motif) included; without a loaded
 * configuration they keep the motif they were built with. Every other task is its view kind.
 */
fun MoNeed.contentKey(checklist: ChecklistConfiguration?): MoContentKey = when (this) {
    MoNeed.Activity -> MoContentKey(MoViewKind.Activity)
    MoNeed.Reading -> MoContentKey(MoViewKind.Time, MoMotif.Book)
    MoNeed.Weight -> MoContentKey(MoViewKind.Measurement, MoMotif.Scale)
    MoNeed.Eating -> MoContentKey(MoViewKind.Questions, MoMotif.Food)
    MoNeed.Supplements -> MoContentKey(MoViewKind.Checklist, if (checklist != null) checklist.motif else MoMotif.Pills)
    MoNeed.Care -> MoContentKey(MoViewKind.Checklist, if (checklist != null) checklist.motif else MoMotif.Teeth)
}

/** [need]'s content by the configuration this mood carries for it. */
fun MoMood.contentKey(need: MoNeed): MoContentKey = contentKey(need.view.trackerRef)

package de.nicolasmahn.momentum.domain.compatibility

import de.nicolasmahn.momentum.domain.TrackerRef
import de.nicolasmahn.momentum.domain.checklist.RewardPeriod
import de.nicolasmahn.momentum.domain.checklist.include
import de.nicolasmahn.momentum.domain.trackerRef
import java.time.LocalDate

/** Stable aliases for historical test reports; production never selects policy by these identities. */
internal val InheritedNeedsByTracker: Map<TrackerRef, MoNeed> = MoNeed.entries.associateBy { it.view.trackerRef }
internal val InheritedNextTokenOrder = listOf(MoNeed.Care, MoNeed.Supplements, MoNeed.Eating, MoNeed.Weight, MoNeed.Activity, MoNeed.Reading)

internal fun inheritedWholeWeek(monday: LocalDate, began: LocalDate?, periods: List<RewardPeriod>?): Boolean =
    began != null && began <= monday && (0L until 7L).all { periods.include(monday.plusDays(it)) }

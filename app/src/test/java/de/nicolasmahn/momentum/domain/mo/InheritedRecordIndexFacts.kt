package de.nicolasmahn.momentum.domain.mo

import de.nicolasmahn.momentum.domain.LoggedViewKind
import de.nicolasmahn.momentum.domain.LoggedViewRef
import de.nicolasmahn.momentum.domain.compatibility.LegacyCareSession
import de.nicolasmahn.momentum.domain.compatibility.WeightMeasurementId
import de.nicolasmahn.momentum.domain.compatibility.careChecklist
import de.nicolasmahn.momentum.domain.compatibility.isCareComplete as inheritedCareComplete
import de.nicolasmahn.momentum.domain.compatibility.isSupplementsComplete as inheritedSupplementsComplete
import de.nicolasmahn.momentum.domain.compatibility.isDailyCareComplete as inheritedDailyCareComplete
import de.nicolasmahn.momentum.domain.compatibility.isEatingLoggedOnTime as inheritedEatingLoggedOnTime
import java.time.LocalDate

/** Historical predicate parity tests use current configuration-driven index operations. */
internal fun MoRecordIndex.isWeightLogged(day: LocalDate): Boolean =
    isLogged(LoggedViewRef(LoggedViewKind.Measurement, WeightMeasurementId), day)

internal fun MoRecordIndex.isCareSessionComplete(day: LocalDate, session: LegacyCareSession): Boolean =
    state.careChecklist?.let { checklist ->
        progress(checklist, day).checkpoints.filter { it.checkpoint.id == session.storageId }.all { it.unmet.isEmpty() }
    } ?: false

internal fun MoRecordIndex.isCareComplete(day: LocalDate): Boolean = inheritedCareComplete(day)
internal fun MoRecordIndex.isSupplementsComplete(day: LocalDate): Boolean = inheritedSupplementsComplete(day)
internal fun MoRecordIndex.isDailyCareComplete(day: LocalDate): Boolean = inheritedDailyCareComplete(day)
internal fun MoRecordIndex.isEatingLoggedOnTime(day: LocalDate): Boolean = inheritedEatingLoggedOnTime(day)

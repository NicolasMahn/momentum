package de.nicolasmahn.momentum.domain.mo

import de.nicolasmahn.momentum.core.time.parseDateKey
import de.nicolasmahn.momentum.domain.TrackerEvaluationContext
import de.nicolasmahn.momentum.domain.TrackerRuntimeProvider
import java.time.LocalDate

/** Observations are determined only by each kind's configured policies and factual records. */
internal fun trackerObservations(context: TrackerEvaluationContext, providers: List<TrackerRuntimeProvider>): List<TrackerObservation> =
    providers.flatMap { it.observations(context) }.sortedBy { it.order }

/** Dates are parsed once at the observation boundary, retaining the latest factual action. */
internal fun Map<String, Long>.datedActions(answers: (LocalDate) -> LocalDate? = { it }): Map<LocalDate, Long> =
    entries.mapNotNull { (key, at) -> parseDateKey(key)?.let(answers)?.let { it to at } }
        .groupBy({ it.first }, { it.second }).mapValues { (_, at) -> at.max() }

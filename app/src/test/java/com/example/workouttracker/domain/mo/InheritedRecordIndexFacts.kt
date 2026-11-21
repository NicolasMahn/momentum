package com.example.workouttracker.domain.mo

import com.example.workouttracker.domain.LoggedViewKind
import com.example.workouttracker.domain.LoggedViewRef
import com.example.workouttracker.domain.compatibility.LegacyCareSession
import com.example.workouttracker.domain.compatibility.WeightMeasurementId
import com.example.workouttracker.domain.compatibility.careChecklist
import com.example.workouttracker.domain.compatibility.isCareComplete as inheritedCareComplete
import com.example.workouttracker.domain.compatibility.isSupplementsComplete as inheritedSupplementsComplete
import com.example.workouttracker.domain.compatibility.isDailyCareComplete as inheritedDailyCareComplete
import com.example.workouttracker.domain.compatibility.isEatingLoggedOnTime as inheritedEatingLoggedOnTime
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

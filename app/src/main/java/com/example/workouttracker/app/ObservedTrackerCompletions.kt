package com.example.workouttracker.app

import com.example.workouttracker.domain.TrackerRef
import com.example.workouttracker.domain.TrackerTypeId
import java.io.Serializable

/** Only a new factual record can make a newly kept tracker celebrate. */
internal data class ObservedTrackerCompletions(
    val dayKey: String,
    val recordedAt: Map<String, Long?>,
    val kept: Set<String>,
    val pending: List<String> = emptyList(),
    val recordedActionKeys: Map<String, Set<String>> = emptyMap(),
    val owedActionKeys: Map<String, Set<String>> = emptyMap(),
) : Serializable {
    constructor(
        dayKey: String,
        kept: Set<TrackerRef>,
        recordedAt: Map<TrackerRef, Long?>,
        recordedActionKeys: Map<TrackerRef, Set<String>> = emptyMap(),
        owedActionKeys: Map<TrackerRef, Set<String>> = emptyMap(),
    ) : this(
        dayKey,
        recordedAt.mapKeys { (ref, _) -> ref.saved },
        kept.filter { it in recordedAt }.mapTo(LinkedHashSet()) { it.saved },
        recordedActionKeys = recordedActionKeys.mapKeys { (ref, _) -> ref.saved },
        owedActionKeys = owedActionKeys.mapKeys { (ref, _) -> ref.saved },
    )

    val celebrating: TrackerRef? get() = pending.firstOrNull()?.let { saved ->
        TrackerRef(TrackerTypeId(saved.substringBefore(':')), saved.substringAfter(':'))
    }

    fun seeing(
        dayKey: String,
        kept: Set<TrackerRef>,
        recordedAt: Map<TrackerRef, Long?>,
        recordedActionKeys: Map<TrackerRef, Set<String>> = emptyMap(),
        owedActionKeys: Map<TrackerRef, Set<String>> = emptyMap(),
    ): ObservedTrackerCompletions {
        val next = ObservedTrackerCompletions(dayKey, kept, recordedAt, recordedActionKeys, owedActionKeys)
        if (dayKey != this.dayKey) return next
        val earned = next.kept.filter { ref ->
            ref !in this.kept && ref in this.recordedAt && when {
                ref in next.owedActionKeys -> next.recordedActionKeys[ref].orEmpty().any { action ->
                    action !in this.recordedActionKeys[ref].orEmpty() && action in this.owedActionKeys[ref].orEmpty()
                }
                else -> next.recordedAt[ref]?.let { timestamp ->
                    this.recordedAt[ref]?.let { timestamp > it } ?: true
                } == true
            }
        }
        return next.copy(pending = (pending.filter { it in next.kept } + earned).distinct())
    }
}

private val TrackerRef.saved get() = "${type.value}:$id"

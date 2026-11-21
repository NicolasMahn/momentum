package com.example.workouttracker.feature.tracker

import com.example.workouttracker.core.text.UiText
import com.example.workouttracker.domain.TrackerRef
import com.example.workouttracker.domain.mo.MoMood
import com.example.workouttracker.domain.mo.contentKey
import com.example.workouttracker.feature.mo.MoContentCatalogue
import com.example.workouttracker.feature.mo.label

/** Earned-token wording names the basis used at the close, never a newly edited goal. */
internal fun trackerTokenEarnedLine(ref: TrackerRef, mood: MoMood): UiText? {
    val content = mood.contentKey(ref)
    val amount = mood.tokenEarningBasisByTracker[ref] ?: return null
    return MoContentCatalogue[content].tokenEarnedLine(mood.label(ref), amount, acrossWeeks = ref in mood.tokenEarnedAcrossWeeks)
}

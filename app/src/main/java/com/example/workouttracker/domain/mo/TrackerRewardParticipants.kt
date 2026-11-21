package com.example.workouttracker.domain.mo

import com.example.workouttracker.domain.TrackerEvaluationContext
import com.example.workouttracker.domain.activity.ActivityRuntime
import com.example.workouttracker.domain.checklist.ChecklistRuntime
import com.example.workouttracker.domain.holiday.HolidaySchedule
import com.example.workouttracker.domain.measurement.MeasurementRuntime
import com.example.workouttracker.domain.questions.QuestionsRuntime
import com.example.workouttracker.domain.reward.TrackerRewardCatalogue
import com.example.workouttracker.domain.settings.FeatureVisibilitySettings
import com.example.workouttracker.domain.substance.SubstanceRuntime
import com.example.workouttracker.domain.time.TimeRuntime
import java.time.LocalDate

/** Existing persisted kinds; additional modules contribute through the same open catalogue. */
private val CurrentRuntimeProviders = listOf(ChecklistRuntime, SubstanceRuntime, MeasurementRuntime, QuestionsRuntime, TimeRuntime, ActivityRuntime)

/** Each kind supplies the policy and facts of its explicit configuration. */
internal fun trackerRewards(
    records: MoRecordIndex,
    visibility: FeatureVisibilitySettings,
    holidays: HolidaySchedule,
    today: LocalDate,
    rewardStart: LocalDate = DefaultRewardStart,
): TrackerRewardCatalogue {
    val context = TrackerEvaluationContext(records, visibility, today, holidays, rewardStart)
    val configured = CurrentRuntimeProviders.flatMap { it.rewards(context) }
    return TrackerRewardCatalogue(configured, trackerObservations(context, CurrentRuntimeProviders))
}

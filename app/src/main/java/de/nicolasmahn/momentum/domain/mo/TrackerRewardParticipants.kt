package de.nicolasmahn.momentum.domain.mo

import de.nicolasmahn.momentum.domain.TrackerEvaluationContext
import de.nicolasmahn.momentum.domain.activity.ActivityRuntime
import de.nicolasmahn.momentum.domain.checklist.ChecklistRuntime
import de.nicolasmahn.momentum.domain.holiday.HolidaySchedule
import de.nicolasmahn.momentum.domain.measurement.MeasurementRuntime
import de.nicolasmahn.momentum.domain.questions.QuestionsRuntime
import de.nicolasmahn.momentum.domain.reward.TrackerRewardCatalogue
import de.nicolasmahn.momentum.domain.settings.FeatureVisibilitySettings
import de.nicolasmahn.momentum.domain.substance.SubstanceRuntime
import de.nicolasmahn.momentum.domain.time.TimeRuntime
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

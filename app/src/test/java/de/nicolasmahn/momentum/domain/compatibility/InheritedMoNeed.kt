package de.nicolasmahn.momentum.domain.compatibility

import de.nicolasmahn.momentum.domain.LoggedViewKind
import de.nicolasmahn.momentum.domain.LoggedViewRef
import de.nicolasmahn.momentum.domain.settings.DashboardFeature
import de.nicolasmahn.momentum.domain.settings.ViewRef

/** Historical save and presentation aliases; current evaluation uses TrackerRef. */
enum class MoNeed(
    val view: ViewRef,
    /**
     * Days of a Monday-to-Sunday week the task may go without a record and still not count as
     * missed. Null for Activity, whose days a week are its configuration's week
     * ([de.nicolasmahn.momentum.domain.activity.ActivityConfiguration.week]).
     */
    val freeDaysPerWeek: Int?,
) {
    /** As many active days a week as its configuration asks, and the rest days fall wherever he wants them. */
    Activity(ViewRef.Feature(DashboardFeature.Activity), freeDaysPerWeek = null),
    Reading(ViewRef.Logged(LoggedViewRef(LoggedViewKind.Time, ReadingTimeId)), freeDaysPerWeek = 1),
    Care(ViewRef.Checklist(CareChecklistId), freeDaysPerWeek = 0),
    Supplements(ViewRef.Checklist(SupplementsChecklistId), freeDaysPerWeek = 0),
    Eating(ViewRef.Logged(LoggedViewRef(LoggedViewKind.Questions, EatingQuestionsId)), freeDaysPerWeek = 1),
    Weight(ViewRef.Logged(LoggedViewRef(LoggedViewKind.Measurement, WeightMeasurementId)), freeDaysPerWeek = 1),
    ;

    /** The same rule said the way a plan is made: six days a week, not one day off; null for Activity, which its configuration says. */
    val expectedDaysPerWeek: Int? get() = freeDaysPerWeek?.let { DAYS_PER_WEEK - it }

    /** Whether the task has a goal a week to reach: every task but one due every day. */
    val hasWeekGoal: Boolean get() = freeDaysPerWeek != 0
}

private const val DAYS_PER_WEEK = 7

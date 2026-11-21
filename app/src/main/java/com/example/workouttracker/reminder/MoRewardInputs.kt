package com.example.workouttracker.reminder

import android.content.Context
import com.example.workouttracker.domain.holiday.HolidaySchedule
import com.example.workouttracker.domain.mo.DefaultRewardStart
import com.example.workouttracker.domain.TrackerRef
import com.example.workouttracker.domain.mo.MoChoices
import com.example.workouttracker.domain.mo.moMoodWithStoredChoices
import com.example.workouttracker.domain.mo.settledByChoice
import com.example.workouttracker.persistence.PreferencesFeatureVisibilityRepository
import com.example.workouttracker.persistence.PreferencesTrackerRepository
import com.example.workouttracker.core.time.currentHabitDate
import com.example.workouttracker.persistence.PreferencesHabitRuleStartRepository
import com.example.workouttracker.persistence.PreferencesHolidayRepository
import com.example.workouttracker.persistence.PreferencesPassChoiceRepository
import java.time.LocalDate

/**
 * What Mo's mood reads besides records and visibility. Home holds these as live state; everything
 * that judges him away from Home (the widget, notifications) loads them here, so a holiday, a
 * chosen pass or the rule start can never count on one surface and not on another.
 */
internal data class MoRewardInputs(
    val holidays: HolidaySchedule = HolidaySchedule(),
    val choices: MoChoices = MoChoices(),
    val rewardStart: LocalDate = DefaultRewardStart,
)

internal fun interface MoRewardInputsSource {
    /** The inputs as they apply on habit date [today]. */
    fun load(today: LocalDate): MoRewardInputs
}

internal fun moRewardInputsSource(context: Context): MoRewardInputsSource {
    val appContext = context.applicationContext
    return MoRewardInputsSource { today ->
        MoRewardInputs(
            holidays = PreferencesHolidayRepository(appContext).load(),
            choices = PreferencesPassChoiceRepository(appContext).load()?.on(today) ?: MoChoices(),
            rewardStart = PreferencesHabitRuleStartRepository(appContext).loadOrStart(today),
        )
    }
}

/**
 * Whether a choice stopped today's asking: for the whole of a task with no [checkpointId], or for
 * one checkpoint of it. A reminder asks this before it speaks, so a rest day said in the app, or a
 * token set aside, is as quiet in a notification as it is with Mo.
 */
internal fun interface QuietedByChoice {
    fun isQuiet(ref: TrackerRef, checkpointId: String?): Boolean
}

/**
 * Read from the mood the stored choices give today, so a choice the facts no longer bear quiets
 * nothing. A mood that cannot be derived quiets nothing either: the reminder speaks.
 */
internal fun quietedByChoice(context: Context): QuietedByChoice {
    val appContext = context.applicationContext
    return QuietedByChoice { ref, checkpointId ->
        runCatching {
            val today = currentHabitDate()
            val rewards = moRewardInputsSource(appContext).load(today)
            val mood = moMoodWithStoredChoices(
                state = PreferencesTrackerRepository(appContext).load(),
                featureVisibility = PreferencesFeatureVisibilityRepository(appContext).load(),
                today = today,
                holidays = rewards.holidays,
                storedChoices = rewards.choices,
                rewardStart = rewards.rewardStart,
            )
            ref in mood.settledByChoice || (checkpointId != null && checkpointId in mood.checkpointsSetAside[ref].orEmpty())
        }.getOrDefault(false)
    }
}

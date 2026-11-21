package com.example.workouttracker.feature.time

import com.example.workouttracker.R
import com.example.workouttracker.core.text.UiText
import com.example.workouttracker.core.text.isolated
import com.example.workouttracker.core.text.pluralText
import com.example.workouttracker.core.text.uiText
import com.example.workouttracker.domain.time.TimeConfiguration

/** Every timer uses its configured name with the shared timer wording. */
internal fun TimeConfiguration.words(): TimeWords = TimeWords(this)

internal class TimeWords(private val view: TimeConfiguration) {
    val start = uiText(R.string.time_start)
    val typeMinutes = uiText(R.string.time_type_minutes)
    val backToTimer = uiText(R.string.time_back_to_timer)
    val minutesLabel = uiText(R.string.time_minutes_label)
    val add = uiText(R.string.time_add)
    val added = uiText(R.string.time_added)
    val saveFailed = uiText(R.string.time_error_save_failed)
    val removalFailed = uiText(R.string.time_error_remove_failed)

    fun minutesError(error: MinutesError) = when (error) {
        is MinutesError.OutOfRange -> uiText(R.string.time_minutes_error_range, error.most)
        MinutesError.OverADay -> uiText(R.string.time_minutes_error_over_day)
    }

    fun weekMinutes(minutes: Int, asks: Int) = pluralText(R.plurals.time_week_minutes, asks, minutes, asks)

    fun weekMinutesSpoken(minutes: Int, asks: Int) = pluralText(R.plurals.time_week_minutes_description, asks, minutes, asks)

    fun remove(minutes: Int?, day: UiText) =
        if (minutes == null) uiText(R.string.time_remove, day) else pluralText(R.plurals.time_remove_minutes, minutes, minutes, day)

    val dayLogged = uiText(R.string.time_day_recorded)
    val dayEmpty = uiText(R.string.time_day_empty)
    val selectDay = uiText(R.string.time_day_select)

    fun sessionNote(note: TimeSessionNote) = when {
        note.leftApp && note.minutes > 0 -> pluralText(R.plurals.time_note_left_app_saved, note.minutes)
        note.leftApp -> uiText(R.string.time_note_left_app_nothing_saved)
        note.minutes == 0 -> uiText(R.string.time_note_nothing_saved)
        else -> null
    }

    fun sessionMinutes(minutes: Int) = pluralText(R.plurals.time_session_minutes, minutes, isolated(view.label), minutes)

    val sessionDone = uiText(R.string.time_session_done)
    val focusMessage = uiText(R.string.time_focus_message)
    val focusAllow = uiText(R.string.time_focus_allow)
    val focusWithout = uiText(R.string.time_focus_without)
}

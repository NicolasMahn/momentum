package com.example.workouttracker.feature.measurement

import com.example.workouttracker.core.format.formatMetricNumber
import kotlin.math.abs
import java.util.Locale
import com.example.workouttracker.R
import com.example.workouttracker.core.format.parseTypedNumber
import com.example.workouttracker.core.text.UiText
import com.example.workouttracker.core.text.isolated
import com.example.workouttracker.core.text.pluralText
import com.example.workouttracker.core.text.uiText
import com.example.workouttracker.domain.measurement.MeasurementConfiguration
import com.example.workouttracker.domain.measurement.MeasurementValuePolicy

/** What a measurement's screen has to say when something went wrong; the words are the measurement's ([MeasurementWords.error]). */
internal enum class MeasurementScreenError {
    /** What was typed is outside the configured value policy. */
    Invalid,
    ChangedBeforeSave,
    SaveFailed,
    ChangedBeforeRemoval,
    RemovalFailed,

    /** The measurement was deleted while its screen was open, so nothing was stored. */
    Deleted,
}

/** Shared measurement wording uses the configured name and unit in every surface. */
internal fun MeasurementConfiguration.words(): MeasurementWords = MeasurementWords(name = label, unit = unit, valuePolicy = valuePolicy)

internal data class MeasurementWords(
    val name: String,
    val unit: String,
    val valuePolicy: MeasurementValuePolicy = MeasurementValuePolicy.Finite,
) {
    /** Plain numbers are unambiguous; an optional configured unit is accepted consistently. */
    fun parse(text: String): Double? {
        val typed = text.trim()
        parseTypedNumber(typed)?.let { return it }
        val number = if (unit.isNotEmpty() && typed.endsWith(unit, ignoreCase = true)) {
            typed.dropLast(unit.length).trimEnd()
        } else {
            typed
        }
        return parseTypedNumber(number)
    }

    val fieldLabel: UiText
        get() = if (unit.isEmpty()) UiText.Verbatim(name) else uiText(R.string.measurement_field_label_with_unit, name, isolated(unit))
    val save get() = uiText(R.string.measurement_save)
    val saved get() = uiText(R.string.measurement_saved)

    fun remove(day: UiText) = uiText(R.string.measurement_remove, day)

    fun previous(value: Double, day: UiText) = uiText(R.string.measurement_previous, shown(value), day)

    fun previousWithChange(value: Double, day: UiText, change: Double) =
        uiText(R.string.measurement_previous_with_change, shown(value), day, shown(change, signed = true))

    fun error(error: MeasurementScreenError) = when (error) {
        MeasurementScreenError.Invalid -> uiText(
            when (valuePolicy) {
                MeasurementValuePolicy.Positive -> R.string.measurement_error_invalid
                MeasurementValuePolicy.Finite -> R.string.measurement_error_invalid_finite
            },
        )
        MeasurementScreenError.ChangedBeforeSave -> uiText(R.string.measurement_error_changed_before_save, name)
        MeasurementScreenError.SaveFailed -> uiText(R.string.measurement_error_save_failed)
        MeasurementScreenError.ChangedBeforeRemoval -> uiText(R.string.measurement_error_changed_before_delete)
        MeasurementScreenError.RemovalFailed -> uiText(R.string.measurement_error_delete_failed)
        MeasurementScreenError.Deleted -> uiText(R.string.measurement_error_deleted, name)
    }


    fun drawn(number: String) = UiText.Verbatim(number)
    val unitBesideTheNumber: String? get() = unit.ifEmpty { null }

    fun spoken(value: Double) = said(value)
    val noneLogged get() = uiText(R.string.measurement_none_logged)

    fun trend(value: Double) = uiText(R.string.measurement_trend, shown(value))

    fun chartEmpty(dayCount: Int) = pluralText(R.plurals.measurement_chart_empty, dayCount, name, dayCount)

    fun chartLine(dayCount: Int, latest: Double, smoothed: Boolean) =
        pluralText(if (smoothed) R.plurals.measurement_chart_trend else R.plurals.measurement_chart_value, dayCount, name, dayCount, said(latest))

    fun chartLineBetween(dayCount: Int, latest: Double, lowest: Double, highest: Double, smoothed: Boolean) = pluralText(
        if (smoothed) R.plurals.measurement_chart_trend_between else R.plurals.measurement_chart_value_between,
        dayCount,
        name,
        dayCount,
        said(latest),
        // The unit is said once, after the higher of the two, as "between 78 and 82 kilograms" says it.
        UiText.Number(lowest),
        said(highest),
    )

    fun selectedNone(day: UiText) = uiText(R.string.measurement_chart_selected_none, day)

    fun selected(day: UiText, value: Double) = uiText(R.string.measurement_chart_selected, day, said(value))

    fun selectedWithTrend(day: UiText, value: Double, trend: Double) =
        uiText(R.string.measurement_chart_selected_with_trend, day, said(value), said(trend))

    /** A reply is read as the field is, so it is asked for and refused in the field's words. */
    val replyPrompt get() = fieldLabel
    val replyInvalid get() = error(MeasurementScreenError.Invalid)
    val replyFailed get() = error(MeasurementScreenError.SaveFailed)

    /**
     * What a − or + button says it adds: the step with its sign and the unit, "−0.5 kg". The sign
     * is the typographic minus the button has always shown, which a formatted number lacks.
     */
    fun stepButton(delta: Double, locale: Locale): UiText =
        withUnit(UiText.Verbatim((if (delta > 0) "+" else "−") + formatMetricNumber(abs(delta), locale)), isolated(unit))

    fun statisticsChange(change: Double) = shown(change, signed = true)

    fun statisticsCaption(latest: Double?) =
        latest?.let { uiText(R.string.measurement_statistics_caption, name, shown(it)) } ?: uiText(R.string.measurement_statistics_caption_nothing, name)

    /** A value as a sentence on screen shows it. */
    private fun shown(value: Double, signed: Boolean = false): UiText = withUnit(UiText.Number(value, signed), isolated(unit))

    /** A value as TalkBack says it. */
    private fun said(value: Double): UiText = withUnit(UiText.Number(value), unit)

    private fun withUnit(number: UiText, unit: String): UiText =
        if (this.unit.isEmpty()) number else uiText(R.string.measurement_value_with_unit, number, unit)
}

package de.nicolasmahn.momentum.feature.measurement

import de.nicolasmahn.momentum.core.time.isValidDateKey
import de.nicolasmahn.momentum.core.format.formatMetricNumber
import de.nicolasmahn.momentum.domain.AppState
import de.nicolasmahn.momentum.domain.measurement.deleteMeasurementForDate
import de.nicolasmahn.momentum.domain.measurement.MeasurementRecord
import de.nicolasmahn.momentum.domain.measurement.MeasurementValuePolicy
import java.util.Locale

internal data class MeasurementDraft(
    val dateKey: String,
    val valueText: String,
    val initialValueText: String,
    val originalLog: MeasurementRecord?,
) {
    val isDirty: Boolean get() = valueText != initialValueText

    /**
     * The draft one step of the − and + buttons on, or null where the field holds no number or the
     * result is outside [valuePolicy]. [parse] reads the field as the measurement's screen
     * does; the result is written as every number is, whatever the step.
     */
    fun adjusted(
        delta: Double,
        locale: Locale,
        parse: (String) -> Double?,
        valuePolicy: MeasurementValuePolicy = MeasurementValuePolicy.Finite,
    ): MeasurementDraft? {
        val value = parse(valueText) ?: return null
        val adjusted = value + delta
        return if (valuePolicy.accepts(adjusted)) copy(valueText = formatMetricNumber(adjusted, locale)) else null
    }
}

/** The field text is written in [locale]'s decimal style; parsing accepts either separator. */
internal fun measurementDraftForDate(logs: List<MeasurementRecord>, dateKey: String, locale: Locale): MeasurementDraft {
    val selected = logs.filter { it.dateKey == dateKey }.maxByOrNull { it.timestampMillis }
    val previous = logs.filter { it.dateKey < dateKey && isValidDateKey(it.dateKey) }
        .maxWithOrNull(compareBy<MeasurementRecord> { it.dateKey }.thenBy { it.timestampMillis })
    val text = (selected?.value ?: previous?.value)?.let { formatMetricNumber(it, locale) }.orEmpty()
    return MeasurementDraft(dateKey, text, text, selected)
}

/** [state] without what [measurementId] holds for [dateKey]; every other record stays as it was, another measurement's of that day too. */
internal fun deleteMeasurementDay(state: AppState, measurementId: String, dateKey: String): AppState =
    state.copy(measurementLogs = deleteMeasurementForDate(state.measurementLogs, measurementId, dateKey))

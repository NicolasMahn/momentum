package de.nicolasmahn.momentum.reminder

import de.nicolasmahn.momentum.app.changeDay
import de.nicolasmahn.momentum.core.time.currentDateKey
import de.nicolasmahn.momentum.core.time.nowMillis
import de.nicolasmahn.momentum.domain.DayChangeRefusal
import de.nicolasmahn.momentum.domain.DayChangeResult
import de.nicolasmahn.momentum.feature.measurement.MeasurementDayChange
import de.nicolasmahn.momentum.feature.measurement.measurementDayRecord
import de.nicolasmahn.momentum.feature.measurement.words
import de.nicolasmahn.momentum.persistence.TrackerRepository

internal sealed interface MeasurementReplyResult {
    data class Saved(val value: Double) : MeasurementReplyResult

    /** What was typed is outside the measurement's value policy. */
    data object Invalid : MeasurementReplyResult

    data object SaveFailed : MeasurementReplyResult

    /** The measurement was deleted since the reminder was sent: there is nothing to save the number for, and nothing to send again. */
    data object Deleted : MeasurementReplyResult
}

/**
 * Stores what was typed into the reply of [measurementId]'s reminder as its number for the day the
 * reminder asked about, through the one measurement command every save of a day goes through.
 *
 * Screen input and notification replies share the configured unit and parsing rules. The
 * current configuration is read at the moment of the action, so a deleted measurement or an
 * unreadable store refuses the write. The day’s current record is replaced through the command.
 */
internal class MeasurementReplyCoordinator(
    private val measurementId: String,
    private val trackerRepository: TrackerRepository,
    private val todayKey: () -> String = ::currentDateKey,
    private val timestampMillis: () -> Long = ::nowMillis,
) {
    fun submit(
        rawInput: CharSequence?,
        targetDateKey: String = todayKey(),
    ): MeasurementReplyResult {
        val reply = rawInput?.toString() ?: return MeasurementReplyResult.Invalid
        val stored = runCatching { trackerRepository.loadIfReadable() }.getOrNull() ?: return MeasurementReplyResult.SaveFailed
        val measurement = stored.measurements.firstOrNull { it.id == measurementId } ?: return MeasurementReplyResult.Deleted
        val value = measurement.words().parse(reply)?.takeIf(measurement::acceptsValue) ?: return MeasurementReplyResult.Invalid

        val change = MeasurementDayChange.Save(measurementId, targetDateKey, opened = stored.measurementDayRecord(measurementId, targetDateKey), value = value)
        return when (val result = trackerRepository.changeDay(change, timestampMillis(), stored = {})) {
            is DayChangeResult.Stored -> MeasurementReplyResult.Saved(value)
            is DayChangeResult.Refused -> when (result.why) {
                DayChangeRefusal.Deleted -> MeasurementReplyResult.Deleted
                // The day's record changed between reading and writing, which only another write
                // of this moment does; sent again, the reply replaces what that wrote.
                DayChangeRefusal.ChangedElsewhere -> MeasurementReplyResult.SaveFailed
            }
            DayChangeResult.Failed -> MeasurementReplyResult.SaveFailed
        }
    }
}

package de.nicolasmahn.momentum.reminder

import androidx.annotation.StringRes
import de.nicolasmahn.momentum.R
import de.nicolasmahn.momentum.core.text.UiText
import de.nicolasmahn.momentum.core.text.joinedBy
import de.nicolasmahn.momentum.core.text.uiText
import de.nicolasmahn.momentum.domain.mo.MoMood
import de.nicolasmahn.momentum.domain.TrackerRef
import de.nicolasmahn.momentum.domain.mo.MoState
import de.nicolasmahn.momentum.domain.mo.isFrozen
import de.nicolasmahn.momentum.feature.mo.label
import de.nicolasmahn.momentum.feature.mo.line
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Formattable
import java.util.Formatter
import java.util.Locale

/** Only overdue tasks prompt a nudge; future tasks and a frozen mood alone stay silent. */
internal fun moNudgeMessage(
    mood: MoMood,
    overdue: List<TrackerRef>,
    nudge: LocalDateTime,
    is24Hour: Boolean,
): MoReminderMessage? = overdue.takeIf { it.isNotEmpty() }?.let {
    mood.overdueMessage(it, nudge, is24Hour)
}

/**
 * The time the way the device shows it: 18:00, or 6 PM and 6:30 PM. A full hour drops its minutes
 * on a twelve-hour clock because that is how it is said out loud.
 */
internal fun moClock(time: LocalTime, is24Hour: Boolean, locale: Locale): String {
    val pattern = when {
        is24Hour -> "HH:mm"
        time.minute == 0 -> "h a"
        else -> "h:mm a"
    }
    return time.format(DateTimeFormatter.ofPattern(pattern, locale))
}

/**
 * The time as a format argument. Resources format their arguments in their own locale, so the
 * clock is written in the language of the sentence it sits in, decided only when that is resolved.
 */
private data class MoClock(val time: LocalTime, val is24Hour: Boolean) : Formattable {
    override fun formatTo(formatter: Formatter, flags: Int, width: Int, precision: Int) {
        formatter.format("%s", moClock(time, is24Hour, formatter.locale() ?: Locale.ROOT))
    }
}

private fun MoMood.checkInMessage() = MoReminderMessage(title = uiText(checkInTitle()), text = line())

@StringRes
private fun MoMood.checkInTitle(): Int = when {
    isFrozen -> R.string.reminder_check_in_title_frozen
    else -> when (state) {
        MoState.Husk -> R.string.reminder_check_in_title_out
        MoState.Cold, MoState.Dimming -> R.string.reminder_check_in_title_cold
        MoState.Steady, MoState.Blazing -> R.string.reminder_check_in_title_warm
    }
}

/**
 * Anger takes fire, which is the rule his look draws by too: a Cold Mo or a Husk has none left, and
 * a frozen one is sealed in ice. They keep their sad voice, narrowed to what is late.
 */
private fun MoMood.overdueMessage(
    overdue: List<TrackerRef>,
    nudge: LocalDateTime,
    is24Hour: Boolean,
): MoReminderMessage {
    if (isFrozen || state < MoState.Dimming) return copy(missingTrackersToday = overdue).checkInMessage()
    return FumingLines.at(nudge).say(
        clock = MoClock(nudge.toLocalTime(), is24Hour),
        needs = overdue.map { label(it) }.joinedBy(", "),
    )
}

/**
 * Playful, never cruel: he fumes about the clock and names exactly what to do, never a logged
 * value. Every line works for one task and for several.
 */
private class FumingLine(
    @param:StringRes private val title: Int,
    @param:StringRes private val text: Int,
) {
    fun say(clock: MoClock, needs: UiText) =
        MoReminderMessage(title = uiText(title), text = uiText(text, clock, needs))
}

private val FumingLines = listOf(
    FumingLine(R.string.reminder_fuming_title_1, R.string.reminder_fuming_text_1),
    FumingLine(R.string.reminder_fuming_title_2, R.string.reminder_fuming_text_2),
    FumingLine(R.string.reminder_fuming_title_3, R.string.reminder_fuming_text_3),
    FumingLine(R.string.reminder_fuming_title_4, R.string.reminder_fuming_text_4),
)

/**
 * The same nudge always yields the same words, so re-posting it never rewrites it. The hour moves
 * the rotation through a day and the date moves it against the hour, which keeps the last nudge of
 * one day and the first of the next apart as well.
 */
private fun List<FumingLine>.at(nudge: LocalDateTime): FumingLine =
    this[Math.floorMod(nudge.hour - nudge.toLocalDate().toEpochDay(), size.toLong()).toInt()]

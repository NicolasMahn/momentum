package de.nicolasmahn.momentum.core.time

import androidx.annotation.StringRes
import de.nicolasmahn.momentum.R
import java.time.DayOfWeek

/** Two letters, because a narrow single letter is ambiguous: T is both Tuesday and Thursday. */
@StringRes
internal fun calendarWeekdayShortLabel(dayOfWeek: DayOfWeek): Int = when (dayOfWeek) {
    DayOfWeek.MONDAY -> R.string.weekday_short_monday
    DayOfWeek.TUESDAY -> R.string.weekday_short_tuesday
    DayOfWeek.WEDNESDAY -> R.string.weekday_short_wednesday
    DayOfWeek.THURSDAY -> R.string.weekday_short_thursday
    DayOfWeek.FRIDAY -> R.string.weekday_short_friday
    DayOfWeek.SATURDAY -> R.string.weekday_short_saturday
    DayOfWeek.SUNDAY -> R.string.weekday_short_sunday
}

package de.nicolasmahn.momentum.feature.time

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalResources
import de.nicolasmahn.momentum.core.text.resolve
import de.nicolasmahn.momentum.core.ui.DayCover
import de.nicolasmahn.momentum.core.ui.LocalViewColors
import de.nicolasmahn.momentum.core.ui.RecentDayCalendar
import de.nicolasmahn.momentum.core.ui.RecentDayCalendarStyle
import de.nicolasmahn.momentum.core.ui.blankDayStyle

/**
 * The recent days of one time view, in the view's color where a day has a log. [covers] are the
 * days Mo's streak covered without one, by date key, for a view that is one of his tasks.
 */
@Composable
internal fun TimeHistory(
    window: TimeHistoryWindow,
    words: TimeWords,
    selectedDateKey: String?,
    todayKey: String,
    testTagPrefix: String,
    onDayClick: (TimeHistoryDay) -> Unit,
    covers: Map<String, DayCover> = emptyMap(),
) {
    val colors = LocalViewColors.current
    val resources = LocalResources.current
    RecentDayCalendar(
        days = window.days,
        dateKey = TimeHistoryDay::dateKey,
        selectedDateKey = selectedDateKey,
        todayKey = todayKey,
        onDayClick = onDayClick,
        dayStyle = { day ->
            if (day.isLogged) RecentDayCalendarStyle(containerColor = colors.accent, contentColor = colors.onAccent) else blankDayStyle()
        },
        statusDescription = { day -> (if (day.isLogged) words.dayLogged else words.dayEmpty).resolve(resources) },
        // Tapping a day only selects it; the form above is where it is edited or removed.
        actionDescription = { words.selectDay.resolve(resources) },
        testTagPrefix = "${testTagPrefix}_history",
        cover = { day -> covers[day.dateKey] },
    )
}

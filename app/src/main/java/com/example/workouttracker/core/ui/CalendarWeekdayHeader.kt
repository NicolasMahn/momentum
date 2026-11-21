package com.example.workouttracker.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.workouttracker.core.time.CalendarWeekdays
import com.example.workouttracker.core.locale.currentLocale
import com.example.workouttracker.core.time.calendarWeekdayShortLabel
import androidx.compose.ui.res.stringResource
import java.time.format.TextStyle

@Composable
internal fun CalendarWeekdayHeader(modifier: Modifier = Modifier) {
    val locale = currentLocale()
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        CalendarWeekdays.forEach { weekday ->
            val fullName = weekday.getDisplayName(TextStyle.FULL, locale)
            Text(
                text = stringResource(calendarWeekdayShortLabel(weekday)),
                modifier = Modifier
                    .weight(1f)
                    .clearAndSetSemantics { contentDescription = fullName },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

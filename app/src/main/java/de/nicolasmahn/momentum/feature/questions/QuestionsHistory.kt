package de.nicolasmahn.momentum.feature.questions

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.unit.dp
import de.nicolasmahn.momentum.core.text.resolve
import de.nicolasmahn.momentum.core.ui.DayCover
import de.nicolasmahn.momentum.core.ui.RecentDayCalendar
import de.nicolasmahn.momentum.core.ui.RecentDayCalendarStyle
import de.nicolasmahn.momentum.core.ui.blankDayStyle
import de.nicolasmahn.momentum.domain.checklist.ViewHue
import de.nicolasmahn.momentum.domain.questions.QuestionScale
import de.nicolasmahn.momentum.domain.questions.answerOn

/**
 * The recent days of one questions view. A day is colored by its answer to the view's first
 * question, so that question's tiles are the calendar's legend, and in the neutral of "Not sure"
 * where it has none; it shows one dot per question the view [asked], filled where it is answered.
 * [covers] are the days Mo's streak covered without an entry, by date key.
 */
@Composable
internal fun QuestionsHistory(
    window: QuestionsHistoryWindow,
    asked: List<QuestionScale>,
    hue: ViewHue,
    words: QuestionsWords,
    selectedDateKey: String?,
    todayKey: String,
    testTagPrefix: String,
    onDayClick: (QuestionsHistoryDay) -> Unit,
    covers: Map<String, DayCover> = emptyMap(),
) {
    val resources = LocalResources.current
    val first = asked.first()
    RecentDayCalendar(
        days = window.days,
        dateKey = QuestionsHistoryDay::dateKey,
        selectedDateKey = selectedDateKey,
        todayKey = todayKey,
        onDayClick = onDayClick,
        dayStyle = { day ->
            // An entry without an answer to the first question means "not sure", which is its own
            // grey; only a day with no entry at all stays empty.
            val step = day.entry?.let { entry ->
                entry.answerOn(first)?.let { answerStep(hue, first.answerIds.indexOf(it), first.answerIds.size) } ?: NotSureStep
            }
            step?.let { RecentDayCalendarStyle(containerColor = it.fill, contentColor = it.ink) } ?: blankDayStyle()
        },
        statusDescription = { day -> questionsHistoryStatus(day, words).resolve(resources) },
        actionDescription = { day -> (if (day.entry == null) words.selectDay else words.openDay).resolve(resources) },
        testTagPrefix = "${testTagPrefix}_history",
        cover = { day -> covers[day.dateKey] },
        decoration = { day, contentColor ->
            val entry = day.entry
            if (entry != null) {
                Row(
                    modifier = Modifier.align(Alignment.BottomCenter),
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    asked.forEach { scale ->
                        val answered = entry.answerOn(scale) != null
                        Surface(
                            modifier = Modifier.size(5.dp),
                            shape = CircleShape,
                            color = if (answered) contentColor else MaterialTheme.colorScheme.surfaceVariant,
                            border = if (answered) null else BorderStroke(1.dp, contentColor),
                        ) {}
                    }
                }
            }
        },
    )
}

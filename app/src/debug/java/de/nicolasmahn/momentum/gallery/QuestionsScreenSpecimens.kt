package de.nicolasmahn.momentum.gallery

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import de.nicolasmahn.momentum.domain.AppState
import de.nicolasmahn.momentum.domain.changingDay
import de.nicolasmahn.momentum.domain.checklist.AboutDay
import de.nicolasmahn.momentum.domain.checklist.Checkpoint
import de.nicolasmahn.momentum.domain.checklist.CheckpointTime
import de.nicolasmahn.momentum.domain.checklist.Dated
import de.nicolasmahn.momentum.domain.checklist.DayCheckpointId
import de.nicolasmahn.momentum.domain.checklist.ViewHue
import de.nicolasmahn.momentum.domain.checklist.ViewIcon
import de.nicolasmahn.momentum.domain.checklist.WhenAway
import de.nicolasmahn.momentum.domain.questions.Answer
import de.nicolasmahn.momentum.domain.questions.Question
import de.nicolasmahn.momentum.domain.questions.QuestionEntry
import de.nicolasmahn.momentum.domain.questions.QuestionEntryOrder
import de.nicolasmahn.momentum.domain.questions.QuestionsConfiguration
import de.nicolasmahn.momentum.feature.questions.words
import de.nicolasmahn.momentum.feature.questions.QuestionsScreen
import java.time.LocalDate
import java.time.LocalTime

/** The questions screen as Eating's destination shows it. */
internal val eatingScreenSpecimens = listOf(
    GallerySpecimen("QuestionsScreen, Eating") { QuestionsScreenSpecimen(DemoEatingQuestions, testTagPrefix = "gallery_eating_screen") },
)

/**
 * The questions screen in the words of a view a person made: three questions of two, three and
 * five answers, the first on the view's ramp, one with a help text and one long enough to wrap.
 * It opens on today: with nothing picked while today has no answers, and on today's answers with
 * the lasting check once it has.
 */
internal val madeQuestionsScreenSpecimens = listOf(
    GallerySpecimen("QuestionsScreen, a made view") { QuestionsScreenSpecimen(DemoSleepQuestions) },
    GallerySpecimen("QuestionsScreen, an answered day") { QuestionsScreenSpecimen(DemoSleepQuestions, answeredToday = true) },
    GallerySpecimen("QuestionsScreen, nothing answered") { QuestionsScreenSpecimen(DemoSleepQuestions, days = 0) },
)

internal val DemoSleepQuestions = QuestionsConfiguration(
    id = "qst_s1ee",
    label = "Sleep",
    icon = ViewIcon.Sleep,
    hue = ViewHue.Navy,
    questions = listOf(
        Question("qn_rest", "How rested do you feel?", listOf(Answer("an_low", "Not at all"), Answer("an_mid", "Somewhat"), Answer("an_high", "Fully")), help = "Think of the whole morning, not only the first minutes after the alarm.\n\nAn answer is never good or bad: it only says how it was."),
        Question("qn_screen", "Did you put the phone away an hour before going to bed, as you planned to?", listOf(Answer("an_no", "No"), Answer("an_yes", "Yes"))),
        Question("qn_hours", "How long did you sleep?", listOf(Answer("an_h4", "Under 5 h"), Answer("an_h5", "5 to 6 h"), Answer("an_h6", "6 to 7 h"), Answer("an_h7", "7 to 8 h"), Answer("an_h8", "Over 8 h"))),
    ),
    about = Dated.always(AboutDay.Today),
    rewards = null,
    whenAway = Dated.always(WhenAway.Due),
    checkpoint = Checkpoint(id = DayCheckpointId, at = CheckpointTime.Fixed(LocalTime.of(9, 0)), remind = false),
    madeOn = LocalDate.parse(fixtureDateKey(60)),
)

/** Today answered in full, with a note, as the made view's screen shows a day that has answers. */
private val DemoSleepToday = QuestionEntry(
    id = "demo-sleep-0",
    viewId = DemoSleepQuestions.id,
    dateKey = FixtureTodayKey,
    timestampMillis = 1,
    answers = mapOf("qn_rest" to "an_high", "qn_screen" to "an_yes", "qn_hours" to "an_h7"),
    note = "Window open all night.",
)

/**
 * The real screen over fake entries, saving into memory so a day can be answered, changed and
 * removed. The state holds Eating's entries beside the made view's on the same days, so a specimen
 * also shows that a screen reads its own. It scrolls itself, so it needs a frame of fixed height.
 */
@Composable
private fun QuestionsScreenSpecimen(view: QuestionsConfiguration, days: Int = 24, answeredToday: Boolean = false, testTagPrefix: String = "gallery_${view.id}") {
    var state by remember {
        mutableStateOf(
            fixtureAppState.copy(
                questions = fixtureAppState.questions + DemoSleepQuestions,
                // Yesterday back to [days] ago, a few days left out; every fourth leaves the first question as "Not sure".
                questionEntries = (
                    fixtureAppState.questionEntries + listOfNotNull(DemoSleepToday.takeIf { answeredToday }) + (1..days).filter { it % 6 != 0 }.map { daysAgo ->
                        QuestionEntry(
                            id = "demo-sleep-$daysAgo",
                            viewId = DemoSleepQuestions.id,
                            dateKey = fixtureDateKey(daysAgo),
                            timestampMillis = 1,
                            answers = buildMap {
                                if (daysAgo % 4 != 0) put("qn_rest", listOf("an_low", "an_mid", "an_high")[daysAgo % 3])
                                if (daysAgo % 3 != 0) put("qn_screen", if (daysAgo % 2 == 0) "an_yes" else "an_no")
                                put("qn_hours", listOf("an_h4", "an_h5", "an_h6", "an_h7", "an_h8")[daysAgo % 5])
                            },
                            note = "Woke up twice.".takeIf { daysAgo % 7 == 0 },
                        )
                    }
                    ).sortedWith(QuestionEntryOrder),
            ),
        )
    }
    ScreenFrame(height = 860.dp) {
        QuestionsScreen(
            state = state,
            view = view,
            words = view.words(),
            onDayChange = { change -> state.changingDay(change, timestampMillis = 2) { updated -> state = updated; true } },
            todayKey = FixtureTodayKey,
            testTagPrefix = testTagPrefix,
        )
    }
}

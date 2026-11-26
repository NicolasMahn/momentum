package de.nicolasmahn.momentum.domain.questions

import de.nicolasmahn.momentum.domain.compatibility.madeQuestions

import de.nicolasmahn.momentum.domain.compatibility.defaultEatingQuestions

import de.nicolasmahn.momentum.domain.AppState
import de.nicolasmahn.momentum.domain.checklist.AboutDay
import de.nicolasmahn.momentum.domain.checklist.Checkpoint
import de.nicolasmahn.momentum.domain.checklist.CheckpointTime
import de.nicolasmahn.momentum.domain.checklist.Dated
import de.nicolasmahn.momentum.domain.checklist.DayCheckpointId
import de.nicolasmahn.momentum.domain.checklist.ViewHue
import de.nicolasmahn.momentum.domain.checklist.ViewIcon
import de.nicolasmahn.momentum.domain.checklist.WhenAway
import java.time.LocalDate
import java.time.LocalTime

/** Eating's configuration as a new English installation is seeded with it. */
internal fun testEatingQuestions() = defaultEatingQuestions(label = "Eating")

/** [this] with Eating configured, as every loaded state is, before any view a person made. */
internal fun AppState.withEating(configuration: QuestionsConfiguration = testEatingQuestions()): AppState =
    copy(questions = listOf(configuration) + madeQuestions)

/** A question with [answers] answers, `a1` to the last, labelled by their place. */
internal fun testQuestion(id: String = "qn_rest", text: String = "How rested do you feel?", answers: Int = 5, help: String? = null) =
    Question(id = id, text = text, answers = (1..answers).map { Answer("a$it", "Level $it") }, help = help)

/** The day [testMadeQuestions] was made. */
internal val TestQuestionsMadeOn: LocalDate = LocalDate.parse("2026-10-13")

/** A questions view a person made, as it starts: about today, outside the streak, still due on holiday, its reminder off at 09:00. */
internal fun testMadeQuestions(
    id: String = "qst_k2p9",
    label: String = "Sleep",
    questions: List<Question> = listOf(testQuestion()),
    about: AboutDay = AboutDay.Today,
    madeOn: LocalDate = TestQuestionsMadeOn,
) = QuestionsConfiguration(
    id = id,
    label = label,
    icon = ViewIcon.Sleep,
    hue = ViewHue.Navy,
    questions = questions,
    about = Dated.always(about),
    rewards = null,
    whenAway = Dated.always(WhenAway.Due),
    checkpoint = Checkpoint(id = DayCheckpointId, at = CheckpointTime.Fixed(LocalTime.of(9, 0)), remind = false),
    madeOn = madeOn,
)

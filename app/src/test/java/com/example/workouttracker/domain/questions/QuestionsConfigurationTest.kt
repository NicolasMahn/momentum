package com.example.workouttracker.domain.questions

import com.example.workouttracker.domain.compatibility.EatingRewards

import com.example.workouttracker.domain.checklist.AboutDay
import com.example.workouttracker.domain.checklist.Dated
import com.example.workouttracker.domain.checklist.DefaultChecklistRewards
import com.example.workouttracker.domain.checklist.RewardPeriod
import com.example.workouttracker.domain.checklist.WhenAway
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class QuestionsConfigurationTest {
    private val eating = testEatingQuestions()
    private val sleep = testMadeQuestions()
    private val day = TestQuestionsMadeOn

    /** The bank a made view starts with, joined on [day]. */
    private val joined = DefaultChecklistRewards.copy(periods = listOf(RewardPeriod(day)))

    private fun refused(build: () -> Any) {
        assertThrows(IllegalArgumentException::class.java) { build() }
    }

    /** A question's text is one line as typed, of 1 to 120 code points. */
    @Test fun aQuestionsTextIsOneLineOfOneTo120CodePoints() {
        listOf("How did you sleep?", "x", "Wie war's?", "מה שלומך", "🍎".repeat(120), "a".repeat(120), "two  spaces inside").forEach { text ->
            assertTrue(text, isValidQuestionText(text))
        }
        listOf("", " ", " leading", "trailing ", "a".repeat(121), "🍎".repeat(121), "two\nlines", "a\tb", "a\u0000b", "‏a", "a b").forEach { text ->
            assertFalse("'$text'", isValidQuestionText(text))
            refused { testQuestion(text = text) }
        }
        assertEquals(120, MaximumQuestionTextLength)
    }

    /** An answer's label is one line as typed, of 1 to 24 code points. */
    @Test fun anAnswersLabelIsOneLineOfOneTo24CodePoints() {
        listOf("Good", "x", "Sehr gut", "🍎".repeat(24), "a".repeat(24)).forEach { label -> assertTrue(label, isValidAnswerLabel(label)) }
        listOf("", " ", " Good", "Good ", "a".repeat(25), "🍎".repeat(25), "Go\nod", "Go\tod", "‮Good").forEach { label ->
            assertFalse("'$label'", isValidAnswerLabel(label))
            refused { Answer("a1", label) }
        }
        assertEquals(24, MaximumAnswerLabelLength)
    }

    /** A help text has no space around it and 1 to 500 code points, and may run over several lines. */
    @Test fun aHelpTextIsTrimmedHasAtMost500CodePointsAndMayHoldLineBreaks() {
        listOf("One line.", "Two\nlines.", "A paragraph.\n\nAnd another.", "a".repeat(500), "🍎".repeat(500)).forEach { help ->
            assertTrue(isValidQuestionHelp(help))
            assertEquals(help, testQuestion(help = help).help)
        }
        listOf("", " ", "\n", " leading", "trailing\n", "a".repeat(501), "🍎".repeat(501)).forEach { help ->
            assertFalse("'$help'", isValidQuestionHelp(help))
            refused { testQuestion(help = help) }
        }
        assertNull(testQuestion().help)
        assertEquals(500, MaximumQuestionHelpLength)
    }

    /** Two to five answers, each under an ID of its own. */
    @Test fun aQuestionHasTwoToFiveAnswersWithDistinctIds() {
        (2..5).forEach { count -> assertEquals(count, testQuestion(answers = count).answers.size) }
        refused { testQuestion(answers = 1) }
        refused { testQuestion(answers = 6) }
        refused { Question("qn_rest", "How rested?", listOf(Answer("a1", "Less"), Answer("a1", "More"))) }
        refused { Question("", "How rested?", testQuestion().answers) }
        refused { Answer("a 1", "Less") }
    }

    /** One to five questions have distinct IDs; a view ID is a generic nonblank key. */
    @Test fun aViewHasOneToFiveQuestionsWithDistinctIdsAndAGenericViewId() {
        val five = (1..5).map { testQuestion(id = "qn_$it") }
        assertEquals(5, sleep.copy(questions = five).questions.size)
        refused { sleep.copy(questions = emptyList()) }
        refused { sleep.copy(questions = five + testQuestion(id = "qn_6")) }
        refused { sleep.copy(questions = listOf(testQuestion(), testQuestion())) }
        listOf("", " ", "qst k2p9", "qst/k2p9").forEach { id -> refused { sleep.copy(id = id) } }
        assertEquals("sleep", sleep.copy(id = "sleep").id)
        assertEquals("qst_k2p9x", sleep.copy(id = "qst_k2p9x").id)
    }

    /** A view has its one checkpoint; its creation day and bank periods are optional for every ID. */
    @Test fun aViewHasItsCheckpointAndOptionalCreationDayAndBankPeriods() {
        assertNull(sleep.rewards)
        assertEquals(joined, sleep.copy(rewards = joined).rewards)
        assertEquals(emptyList<RewardPeriod>(), sleep.copy(rewards = joined.copy(periods = emptyList())).rewards?.periods)
        assertEquals(EatingRewards, sleep.copy(rewards = EatingRewards).rewards)
        refused { sleep.copy(checkpoint = sleep.checkpoint!!.copy(id = "evening")) }
        refused { sleep.copy(checkpoint = sleep.checkpoint!!.copy(label = "Morning")) }
        refused { sleep.copy(checkpoint = sleep.checkpoint!!.copy(from = day)) }
        assertNull(sleep.copy(madeOn = null).madeOn)
        refused { sleep.copy(whenAway = Dated.always(WhenAway.LowerTarget)) }
        refused { sleep.copy(label = " ") }
    }

    @Test fun everyViewOwnsItsCompletePromptsAndDerivesScalesFromTheirOrder() {
        val prompts = listOf(
            testQuestion("rest", "How rested?", answers = 2, help = "Include naps."),
            testQuestion("energy", "How much energy?", answers = 3),
        )
        listOf("eating", "sleep", "arbitrary-question-tracker").forEach { id ->
            val view = sleep.copy(id = id, questions = prompts)
            assertEquals(prompts, view.questions)
            assertEquals(listOf(QuestionScale("rest", listOf("a1", "a2")), QuestionScale("energy", listOf("a1", "a2", "a3"))), view.scales)
            assertEquals(view.scales.reversed(), view.copy(questions = prompts.reversed()).scales)
            assertEquals("Include naps.", view.questions.first().help)
            refused { view.copy(questions = emptyList()) }
        }
    }

    /** What a view asks is its questions' IDs with their answers' in order: Eating's under the names its reflections stored. */
    @Test fun aViewsScalesAreItsQuestionsAndTheirAnswersInOrder() {
        assertEquals(
            listOf(
                QuestionScale("quality", listOf("poor", "fair", "good", "very_good", "excellent")),
                QuestionScale("amount", listOf("much_less", "a_little_less", "about_the_same", "a_little_more", "much_more")),
            ),
            eating.scales,
        )
        val made = sleep.copy(questions = listOf(testQuestion(id = "qn_rest", answers = 2), testQuestion(id = "qn_mood", answers = 3)))
        assertEquals(listOf(QuestionScale("qn_rest", listOf("a1", "a2")), QuestionScale("qn_mood", listOf("a1", "a2", "a3"))), made.scales)
    }

    /** A due day asks about itself or the day before, by the rule of the due day, and never about a day before the view was made. */
    @Test fun aDueDayAsksAboutItsDayOrTheDayBeforeAndNeverBeforeTheViewWasMade() {
        assertEquals(day, sleep.recordDayFor(day))
        assertEquals(day.plusDays(3), sleep.recordDayFor(day.plusDays(3)))
        assertNull(sleep.recordDayFor(day.minusDays(1)))
        assertEquals(day.minusDays(1), sleep.copy(madeOn = null).recordDayFor(day.minusDays(1)))

        val lookingBack = testMadeQuestions(about = AboutDay.Yesterday)
        assertNull("Made today, it does not owe yesterday", lookingBack.recordDayFor(day))
        assertEquals(day, lookingBack.recordDayFor(day.plusDays(1)))

        // Eating has no such day: every due day asks about the one before.
        assertEquals(LocalDate.parse("2020-01-01"), eating.recordDayFor(LocalDate.parse("2020-01-02")))
    }

    /** On the day a view switches to yesterday the day before already asked for that entry, so it is asked for once. */
    @Test fun anEntryIsAskedForOnceWhenAViewSwitchesToYesterday() {
        val switch = day.plusDays(5)
        val switched = sleep.copy(about = Dated(listOf(Dated.Revision(AboutDay.Today), Dated.Revision(AboutDay.Yesterday, switch))))
        assertEquals(switch.minusDays(1), switched.recordJudgedOn(switch.minusDays(1)))
        assertNull(switched.recordJudgedOn(switch))
        assertEquals(switch, switched.recordJudgedOn(switch.plusDays(1)))
        // A reminder on the switch day still asks for the entry.
        assertEquals(switch.minusDays(1), switched.recordDayFor(switch))
        assertNull(testMadeQuestions(about = AboutDay.Yesterday).recordJudgedOn(day))
    }

    /** A state holds every view under an ID of its own, Eating among them or deleted. */
    @Test fun aStateHoldsEveryViewUnderItsOwnId() {
        requireStorableQuestions(listOf(eating), emptyList())
        requireStorableQuestions(listOf(eating, sleep, testMadeQuestions(id = "qst_m7xd")), emptyList())
        requireStorableQuestions(emptyList(), emptyList())
        requireStorableQuestions(listOf(sleep), emptyList())
        refused { requireStorableQuestions(listOf(eating, eating), emptyList()) }
        refused { requireStorableQuestions(listOf(eating, sleep, sleep.copy(label = "Again")), emptyList()) }
        refused { requireStorableQuestions(listOf(eating), listOf(QuestionEntry("a", "eating", "2026-02-30", 0))) }
    }
}

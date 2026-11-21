package com.example.workouttracker.domain.questions

import com.example.workouttracker.domain.checklist.AboutDay
import com.example.workouttracker.domain.checklist.ChecklistRewards
import com.example.workouttracker.domain.checklist.CheckpointTime
import com.example.workouttracker.domain.checklist.Dated
import com.example.workouttracker.domain.checklist.DefaultChecklistRewards
import com.example.workouttracker.domain.checklist.RewardPeriod
import com.example.workouttracker.domain.checklist.ViewHue
import com.example.workouttracker.domain.checklist.ViewIcon
import com.example.workouttracker.domain.checklist.WhenAway
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import com.example.workouttracker.domain.AppState
import com.example.workouttracker.domain.questions.QuestionEntry
import com.example.workouttracker.domain.questions.withEating
import com.example.workouttracker.domain.questions.applyingQuestionsEdits
import com.example.workouttracker.domain.questions.firstDueDayOf
import com.example.workouttracker.domain.compatibility.eatingQuestions
import java.time.LocalDate

class QuestionsEditsTest {
    private val today = TestQuestionsMadeOn.plusDays(7)
    private val eating = testEatingQuestions()
    private val sleep = testMadeQuestions(questions = listOf(testQuestion(id = "qn_rest"), testQuestion(id = "qn_mood", answers = 3)))

    private fun QuestionsConfiguration.refuses(edit: QuestionsEdit) {
        assertThrows(edit.toString(), IllegalArgumentException::class.java) { applyEdit(edit, today) }
    }

    /** The name and the look judge no day, so they change everywhere at once, Eating's as a made view's. */
    @Test fun theNameAndTheLookChangePlainly() {
        listOf(eating, sleep).forEach { view ->
            val edited = view
                .applyEdit(QuestionsEdit.Rename("Evening"), today)
                .applyEdit(QuestionsEdit.SetIcon(ViewIcon.Heart), today)
                .applyEdit(QuestionsEdit.SetHue(ViewHue.Plum), today)
            assertEquals(view.copy(label = "Evening", icon = ViewIcon.Heart, hue = ViewHue.Plum), edited)
            view.refuses(QuestionsEdit.Rename(" "))
        }
    }

    /** Eating's two numbers and its holiday rule have always judged days: each changes from the day it is saved, and every earlier day keeps what it had. */
    @Test fun eatingsBankAndItsHolidayRuleChangeFromToday() {
        val edited = eating
            .applyEdit(QuestionsEdit.SetRewardRules(maxPasses = 5, everyKeptDays = 3), today)
            .applyEdit(QuestionsEdit.SetWhenAway(WhenAway.Excused), today)
        assertEquals(Dated(listOf(Dated.Revision(3), Dated.Revision(5, today))), edited.rewards!!.maxPasses)
        assertEquals(Dated(listOf(Dated.Revision(7), Dated.Revision(3, today))), edited.rewards!!.everyKeptDays)
        assertEquals(Dated(listOf(Dated.Revision(WhenAway.Due), Dated.Revision(WhenAway.Excused, today))), edited.whenAway)
        assertEquals(3, edited.rewards!!.maxPasses.on(today.minusDays(1)))
        assertEquals(7, edited.rewards!!.everyKeptDays.on(today.minusDays(1)))
        assertEquals(WhenAway.Due, edited.whenAway.on(today.minusDays(1)))
        assertNull("It stays in the streak over its whole history", edited.rewards!!.periods)

        // Only the number that changed takes a revision.
        val onlyTheDays = eating.applyEdit(QuestionsEdit.SetRewardRules(maxPasses = 3, everyKeptDays = 5), today)
        assertEquals(Dated.always(3), onlyTheDays.rewards!!.maxPasses)
        assertEquals(5, onlyTheDays.rewards!!.everyKeptDays.current)
    }

    /** A revision that starts today judged no day yet: a second change that day replaces it, and one taken back leaves nothing. */
    @Test fun aSecondChangeOnTheSameDayReplacesTheFirstAndATakenBackOneLeavesNothing() {
        val twice = eating.applyingEdits(listOf(QuestionsEdit.SetRewardRules(5, 3), QuestionsEdit.SetRewardRules(2, 4), QuestionsEdit.SetWhenAway(WhenAway.Excused)), today)
        assertEquals(Dated(listOf(Dated.Revision(3), Dated.Revision(2, today))), twice.rewards!!.maxPasses)
        assertEquals(Dated(listOf(Dated.Revision(7), Dated.Revision(4, today))), twice.rewards!!.everyKeptDays)

        val takenBack = twice.applyingEdits(listOf(QuestionsEdit.SetRewardRules(3, 7), QuestionsEdit.SetWhenAway(WhenAway.Due)), today)
        assertEquals(eating, takenBack)
    }

    @Test fun everyConfiguredPromptCanBeEditedForAnyTrackerId() {
        listOf(eating, sleep, sleep.copy(id = "arbitrary-question-tracker")).forEach { view ->
            val question = view.questions.first()
            val edited = view.applyingEdits(listOf(
                QuestionsEdit.RenameQuestion(question.id, "How was today?"),
                QuestionsEdit.SetQuestionHelp(question.id, "Think about the whole day."),
                QuestionsEdit.RenameAnswer(question.id, question.answers.first().id, "A little"),
                QuestionsEdit.AddQuestion(testQuestion(id = "new_prompt")),
            ), today)
            assertEquals(view.questions.size + 1, edited.questions.size)
            assertEquals("How was today?", edited.questions.first().text)
            assertEquals("Think about the whole day.", edited.questions.first().help)
            assertEquals("A little", edited.questions.first().answers.first().label)
            assertEquals(view.questions.first().id, edited.questions.first().id)
            assertEquals(view, edited.copy(questions = view.questions))
            view.refuses(QuestionsEdit.SetWhenAway(WhenAway.LowerTarget))
            view.refuses(QuestionsEdit.SetRewardRules(maxPasses = 0, everyKeptDays = 7))
        }
    }

    /**
     * Eating leaves and joins the streak as a made view does, dated: leaving closes the period it
     * has been in since its first due day, the day after its first reflection, which the state
     * tells, and keeps the bank's rules; joining the next day reopens it. A made view's first due
     * day is its first entry's, which it asks about on the day itself.
     */
    @Test fun eatingLeavesAndJoinsTheStreakFromItsFirstReflectionsDueDay() {
        val first = LocalDate.parse("2026-09-01")
        val left = eating.applyEdit(QuestionsEdit.LeaveRewards, today, firstRecordedDay = first.plusDays(1))
        assertEquals(listOf(RewardPeriod(first.plusDays(1), today.minusDays(1))), left.rewards?.periods)
        assertEquals(eating.rewards?.copy(periods = left.rewards?.periods), left.rewards)
        assertEquals(emptyList<RewardPeriod>(), eating.applyEdit(QuestionsEdit.LeaveRewards, today).rewards?.periods)
        assertEquals(listOf(RewardPeriod(first.plusDays(1))), left.applyEdit(QuestionsEdit.JoinRewards, today).rewards?.periods)
        assertEquals(eating, eating.applyEdit(QuestionsEdit.JoinRewards, today))

        val state = AppState(questionEntries = listOf(QuestionEntry("e2", "eating", today.toString(), 2L), QuestionEntry("e1", "eating", first.toString(), 1L, mapOf("quality" to "good")))).withEating()
        assertEquals(first.plusDays(1), state.firstDueDayOf(eating))
        assertNull(AppState().withEating().firstDueDayOf(eating))
        assertEquals(left, state.applyingQuestionsEdits("eating", listOf(QuestionsEdit.LeaveRewards), today).eatingQuestions)
        val preCreation = QuestionEntry("s1", sleep.id, first.toString(), 1L, mapOf("qn_rest" to "a1"))
        val orphaned = AppState(questions = listOf(sleep), questionEntries = listOf(preCreation))
        assertNull(orphaned.firstDueDayOf(sleep))
        assertEquals(listOf(preCreation), orphaned.questionEntries)
        val creationDay = requireNotNull(sleep.madeOn)
        val current = QuestionEntry("s2", sleep.id, creationDay.toString(), 2L, mapOf("qn_rest" to "a1"))
        val withCurrent = orphaned.copy(questionEntries = orphaned.questionEntries + current)
        assertEquals(creationDay, withCurrent.firstDueDayOf(sleep))
        assertEquals(listOf(preCreation, current), withCurrent.questionEntries)
    }

    /** Questions and answers are added last, renamed in place and removed, in the order they were added. */
    @Test fun aMadeViewsQuestionsAndAnswersAreAddedRenamedAndRemoved() {
        val energy = Question("qn_energy", "How much energy?", listOf(Answer("low", "Low"), Answer("high", "High")))
        val edited = sleep.applyingEdits(
            listOf(
                QuestionsEdit.AddQuestion(energy),
                QuestionsEdit.RenameQuestion("qn_rest", "How well did you sleep?"),
                QuestionsEdit.SetQuestionHelp("qn_rest", "Think of the whole night."),
                QuestionsEdit.AddAnswer("qn_energy", Answer("most", "Most")),
                QuestionsEdit.RenameAnswer("qn_energy", "low", "Little"),
                QuestionsEdit.RemoveAnswer("qn_mood", "a2"),
                QuestionsEdit.RemoveQuestion("qn_rest"),
            ),
            today,
        )
        assertEquals(
            listOf(
                Question("qn_mood", "How rested do you feel?", listOf(Answer("a1", "Level 1"), Answer("a3", "Level 3"))),
                Question("qn_energy", "How much energy?", listOf(Answer("low", "Little"), Answer("high", "High"), Answer("most", "Most"))),
            ),
            edited.questions,
        )
        val helped = sleep.applyEdit(QuestionsEdit.SetQuestionHelp("qn_rest", "Think of the whole night."), today)
        assertEquals("Think of the whole night.", helped.questions.first().help)
        assertNull(helped.applyEdit(QuestionsEdit.SetQuestionHelp("qn_rest", null), today).questions.first().help)
        // None of it is dated: the questions are as stored, with nothing that says since when.
        assertEquals(sleep.copy(questions = edited.questions), edited)
    }

    /** A view keeps one to five questions and a question two to five answers, each under an ID of its own and with text that can be stored. */
    @Test fun aMadeViewRefusesAQuestionOrAnAnswerTooManyTooFewOrTwice() {
        val full = sleep.copy(questions = (1..5).map { testQuestion(id = "qn_$it") })
        full.refuses(QuestionsEdit.AddQuestion(testQuestion(id = "qn_6")))
        sleep.refuses(QuestionsEdit.AddQuestion(testQuestion(id = "qn_rest")))
        sleep.refuses(QuestionsEdit.RemoveQuestion("qn_unknown"))
        sleep.applyEdit(QuestionsEdit.RemoveQuestion("qn_rest"), today).refuses(QuestionsEdit.RemoveQuestion("qn_mood"))
        sleep.refuses(QuestionsEdit.AddAnswer("qn_rest", Answer("a6", "Level 6")))
        sleep.refuses(QuestionsEdit.AddAnswer("qn_mood", Answer("a1", "Again")))
        sleep.refuses(QuestionsEdit.AddAnswer("qn_unknown", Answer("a1", "Level 1")))
        sleep.refuses(QuestionsEdit.RenameAnswer("qn_mood", "a9", "Level 9"))
        sleep.refuses(QuestionsEdit.RemoveAnswer("qn_mood", "a9"))
        sleep.applyEdit(QuestionsEdit.RemoveAnswer("qn_mood", "a1"), today).refuses(QuestionsEdit.RemoveAnswer("qn_mood", "a2"))
        sleep.refuses(QuestionsEdit.RenameQuestion("qn_rest", ""))
        sleep.refuses(QuestionsEdit.RenameQuestion("qn_rest", "two\nlines"))
        sleep.refuses(QuestionsEdit.RenameQuestion("qn_unknown", "How?"))
        sleep.refuses(QuestionsEdit.RenameAnswer("qn_rest", "a1", "x".repeat(25)))
        sleep.refuses(QuestionsEdit.SetQuestionHelp("qn_rest", ""))
    }

    /** A made view joins and leaves as a made checklist does: with the standard bank from today, and out after yesterday with its judged days kept. */
    @Test fun aMadeViewJoinsAndLeavesTheStreakWithTheStandardBank() {
        val joined = sleep.applyEdit(QuestionsEdit.JoinRewards, today)
        assertEquals(DefaultChecklistRewards.copy(periods = listOf(RewardPeriod(today))), joined.rewards)
        // Left the day it joined, no day was judged and no period is left; the rules stay for the next time.
        assertEquals(emptyList<RewardPeriod>(), joined.applyEdit(QuestionsEdit.LeaveRewards, today).rewards!!.periods)

        val left = joined.applyEdit(QuestionsEdit.LeaveRewards, today.plusDays(3))
        assertEquals(listOf(RewardPeriod(today, today.plusDays(2))), left.rewards!!.periods)
        assertEquals(listOf(RewardPeriod(today, today.plusDays(2)), RewardPeriod(today.plusDays(9))), left.applyEdit(QuestionsEdit.JoinRewards, today.plusDays(9)).rewards!!.periods)
        assertNull(sleep.applyEdit(QuestionsEdit.LeaveRewards, today).rewards)
        sleep.refuses(QuestionsEdit.SetRewardRules(5, 3))
    }

    /** Its bank's numbers and the day it asks about are dated once a day before today took part, and change plainly before that. */
    @Test fun aMadeViewsRulesAndItsAboutAreDatedOnlyOnceADayWasJudged() {
        val joinedToday = sleep.applyEdit(QuestionsEdit.JoinRewards, today)
        val plain = joinedToday.applyingEdits(listOf(QuestionsEdit.SetRewardRules(5, 3), QuestionsEdit.SetAbout(AboutDay.Yesterday)), today)
        assertEquals(ChecklistRewards(Dated.always(5), Dated.always(3), listOf(RewardPeriod(today))), plain.rewards)
        assertEquals(Dated.always(AboutDay.Yesterday), plain.about)
        assertEquals(Dated.always(AboutDay.Yesterday), sleep.applyEdit(QuestionsEdit.SetAbout(AboutDay.Yesterday), today).about)

        val later = today.plusDays(4)
        val dated = joinedToday.applyingEdits(listOf(QuestionsEdit.SetRewardRules(5, 3), QuestionsEdit.SetAbout(AboutDay.Yesterday)), later)
        assertEquals(Dated(listOf(Dated.Revision(3), Dated.Revision(5, later))), dated.rewards!!.maxPasses)
        assertEquals(Dated(listOf(Dated.Revision(7), Dated.Revision(3, later))), dated.rewards!!.everyKeptDays)
        assertEquals(Dated(listOf(Dated.Revision(AboutDay.Today), Dated.Revision(AboutDay.Yesterday, later))), dated.about)
        assertEquals(joinedToday.about, dated.applyEdit(QuestionsEdit.SetAbout(AboutDay.Today), later).about)
    }

    /** What an away day means is dated in or out of the streak, because the reminder reads the rule of the day it asks about. */
    @Test fun aMadeViewsHolidayRuleIsAlwaysDated() {
        val excused = sleep.applyEdit(QuestionsEdit.SetWhenAway(WhenAway.Excused), today)
        assertEquals(Dated(listOf(Dated.Revision(WhenAway.Due), Dated.Revision(WhenAway.Excused, today))), excused.whenAway)
        assertEquals(sleep, excused.applyEdit(QuestionsEdit.SetWhenAway(WhenAway.Due), today))
        sleep.refuses(QuestionsEdit.SetWhenAway(WhenAway.LowerTarget))
    }

    /** Its reminder, on or off and at what time, judges no day and is not dated. */
    @Test fun aMadeViewsReminderChangesPlainly() {
        val at = CheckpointTime.AfterWake(20)
        val edited = sleep.applyingEdits(listOf(QuestionsEdit.SetRemind(true), QuestionsEdit.SetReminderTime(at)), today)
        assertEquals(sleep.copy(checkpoint = sleep.checkpoint!!.copy(remind = true, at = at)), edited)
    }
}

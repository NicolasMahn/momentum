package com.example.workouttracker.persistence.json

import com.example.workouttracker.domain.checklist.AboutDay
import com.example.workouttracker.domain.checklist.CheckpointTime
import com.example.workouttracker.domain.checklist.ViewHue
import com.example.workouttracker.domain.checklist.ViewIcon
import com.example.workouttracker.domain.checklist.WhenAway
import com.example.workouttracker.domain.questions.Answer
import com.example.workouttracker.domain.questions.Question
import com.example.workouttracker.domain.questions.QuestionsEdit
import com.example.workouttracker.domain.questions.testEatingQuestions
import com.example.workouttracker.domain.questions.testMadeQuestions
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The edits a questions view's page keeps across process death come back exactly as they were made, and its configuration with them. */
class QuestionsEditJsonTest {
    @Test
    fun everyEditComesBackAsItWasMade() {
        val edits = listOf(
            QuestionsEdit.Rename("Sleep 🙂"),
            QuestionsEdit.SetIcon(ViewIcon.Sleep),
            QuestionsEdit.SetHue(ViewHue.Navy),
            QuestionsEdit.AddQuestion(Question("qn_rest", "How rested?", listOf(Answer("an_low", "Not at all"), Answer("an_high", "Fully")), help = "Two\nlines")),
            QuestionsEdit.AddQuestion(Question("qn_mood", "Mood?", listOf(Answer("a1", "Low"), Answer("a2", "High")))),
            QuestionsEdit.RenameQuestion("qn_rest", "How rested do you feel?"),
            QuestionsEdit.SetQuestionHelp("qn_rest", "Think of the whole night."),
            QuestionsEdit.SetQuestionHelp("qn_rest", null),
            QuestionsEdit.RemoveQuestion("qn_mood"),
            QuestionsEdit.AddAnswer("qn_rest", Answer("an_mid", "Somewhat")),
            QuestionsEdit.RenameAnswer("qn_rest", "an_mid", "A little"),
            QuestionsEdit.RemoveAnswer("qn_rest", "an_low"),
            QuestionsEdit.SetAbout(AboutDay.Yesterday),
            QuestionsEdit.JoinRewards,
            QuestionsEdit.LeaveRewards,
            QuestionsEdit.SetRewardRules(maxPasses = 4, everyKeptDays = 5),
            QuestionsEdit.SetWhenAway(WhenAway.Excused),
            QuestionsEdit.SetRemind(true),
            QuestionsEdit.SetReminderTime(CheckpointTime.Fixed(LocalTime.of(21, 30))),
            QuestionsEdit.SetReminderTime(CheckpointTime.AfterWake(20)),
            QuestionsEdit.SetReminderTime(CheckpointTime.BeforeSleep(45)),
        )
        // Each of the seventeen kinds of edit stands in the list.
        assertEquals(17, edits.map { it::class }.toSet().size)

        assertEquals(edits, AppStateJsonCodec.decodeQuestionsEdits(AppStateJsonCodec.encodeQuestionsEdits(edits)))
    }

    @Test
    fun aConfigurationComesBackAsItWasAndWhatCannotBeReadIsRefused() {
        listOf(testEatingQuestions(), testMadeQuestions()).forEach { view ->
            assertEquals(view, AppStateJsonCodec.decodeQuestionsView(AppStateJsonCodec.encodeQuestionsView(view)))
        }
        listOf(
            """[{"edit":"renameQuestion","question":"qn_rest"}]""",
            """[{"edit":"addQuestion","question":{"id":"qn_rest","text":"","answers":[]}}]""",
            """[{"edit":"about","about":"tomorrow"}]""",
            """[{"edit":"unit","unit":"cm"}]""",
            """["rename"]""",
        ).forEach { raw -> assertTrue(raw, runCatching { AppStateJsonCodec.decodeQuestionsEdits(raw) }.isFailure) }
    }
}

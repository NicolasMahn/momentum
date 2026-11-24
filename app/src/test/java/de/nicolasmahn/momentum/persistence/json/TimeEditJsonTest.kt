package de.nicolasmahn.momentum.persistence.json

import de.nicolasmahn.momentum.domain.checklist.CheckpointTime
import de.nicolasmahn.momentum.domain.checklist.MoMotif
import de.nicolasmahn.momentum.domain.checklist.RewardPeriod
import de.nicolasmahn.momentum.domain.checklist.ViewHue
import de.nicolasmahn.momentum.domain.checklist.ViewIcon
import de.nicolasmahn.momentum.domain.checklist.WhenAway
import de.nicolasmahn.momentum.domain.time.TimeEdit
import de.nicolasmahn.momentum.domain.time.TimeRewards
import de.nicolasmahn.momentum.domain.checklist.Dated
import de.nicolasmahn.momentum.domain.time.TokenCover
import de.nicolasmahn.momentum.domain.time.TokenStep
import de.nicolasmahn.momentum.domain.time.TokenSteps
import de.nicolasmahn.momentum.domain.time.testMadeTime
import de.nicolasmahn.momentum.domain.time.testReadingTime
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The edits a time view's page keeps across process death come back exactly as they were made, and its configuration with them. */
class TimeEditJsonTest {
    @Test
    fun everyEditComesBackAsItWasMade() {
        val edits = listOf(
            TimeEdit.Rename("Klavier 🎹"),
            TimeEdit.SetIcon(ViewIcon.Music),
            TimeEdit.SetHue(ViewHue.Navy),
            TimeEdit.SetMotif(MoMotif.Book),
            TimeEdit.SetMotif(null),
            TimeEdit.SetGoal(10080),
            TimeEdit.JoinRewards,
            TimeEdit.LeaveRewards,
            TimeEdit.SetBankSize(4),
            TimeEdit.SetTokenSteps(TokenSteps(listOf(TokenStep(100, 1), TokenStep(150, 3), TokenStep(1000, 5)))),
            TimeEdit.SetTokenCover(TokenCover.Minutes(20)),
            TimeEdit.SetTokenCover(TokenCover.QuarterOfGoal),
            TimeEdit.SetWhenAway(WhenAway.Excused),
            TimeEdit.SetRemind(true),
            TimeEdit.SetReminderTime(CheckpointTime.Fixed(LocalTime.of(21, 30))),
            TimeEdit.SetReminderTime(CheckpointTime.AfterWake(20)),
            TimeEdit.SetReminderTime(CheckpointTime.BeforeSleep(45)),
        )
        // Each of the thirteen kinds of edit stands in the list.
        assertEquals(13, edits.map { it::class }.toSet().size)

        assertEquals(edits, AppStateJsonCodec.decodeTimeEdits(AppStateJsonCodec.encodeTimeEdits(edits)))
    }

    @Test
    fun aConfigurationComesBackAsItWasAndWhatCannotBeReadIsRefused() {
        val joined = testMadeTime(rewards = TimeRewards(maxPasses = 2, periods = listOf(RewardPeriod(LocalDate.parse("2026-10-05"))), tokenSteps = TokenSteps(listOf(TokenStep(100, 4)))))
        val covered = joined.copy(rewards = joined.rewards!!.copy(tokenCover = Dated(listOf(Dated.Revision(TokenCover.QuarterOfGoal), Dated.Revision(TokenCover.Minutes(20), LocalDate.parse("2026-10-12"))))))
        listOf(testReadingTime(), testReadingTime().copy(mo = de.nicolasmahn.momentum.domain.mo.TrackerMoPolicy()), testMadeTime(), testMadeTime().copy(mo = de.nicolasmahn.momentum.domain.mo.TrackerMoPolicy(motif = MoMotif.Book)), joined, covered).forEach { view ->
            assertEquals(view, AppStateJsonCodec.decodeTimeView(AppStateJsonCodec.encodeTimeView(view)))
        }
        listOf(
            """[{"edit":"goal"}]""",
            """[{"edit":"goal","minutes":"60"}]""",
            """[{"edit":"bankSize","maxPasses":2.5}]""",
            """[{"edit":"tokenSteps"}]""",
            """[{"edit":"tokenSteps","steps":[]}]""",
            """[{"edit":"tokenSteps","steps":[{"percent":100,"tokens":1},{"percent":100,"tokens":2}]}]""",
            """[{"edit":"tokenSteps","steps":[{"percent":100,"tokens":"1"}]}]""",
            """[{"edit":"tokensPerWeek","tokens":2}]""",
            """[{"edit":"tokenCover"}]""",
            """[{"edit":"tokenCover","cover":0}]""",
            """[{"edit":"tokenCover","cover":10081}]""",
            """[{"edit":"tokenCover","cover":"20"}]""",
            """[{"edit":"tokenCover","cover":20.5}]""",
            """[{"edit":"tokenCover","cover":"half"}]""",
            """[{"edit":"motif","motif":"jazz"}]""",
            """[{"edit":"motif","motif":7}]""",
            """[{"edit":"whenAway","whenAway":"sometimes"}]""",
            """[{"edit":"bankRules","maxPasses":3,"everyKeptDays":7}]""",
            """["rename"]""",
        ).forEach { raw -> assertTrue(raw, runCatching { AppStateJsonCodec.decodeTimeEdits(raw) }.isFailure) }
    }
}

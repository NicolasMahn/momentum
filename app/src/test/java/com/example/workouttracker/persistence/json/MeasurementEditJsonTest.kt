package com.example.workouttracker.persistence.json

import org.junit.Assert.assertNull
import com.example.workouttracker.domain.checklist.CheckpointTime
import com.example.workouttracker.domain.checklist.Dated
import com.example.workouttracker.domain.checklist.ViewHue
import com.example.workouttracker.domain.checklist.ViewIcon
import com.example.workouttracker.domain.checklist.WhenAway
import com.example.workouttracker.domain.measurement.MeasurementBank
import com.example.workouttracker.domain.measurement.MeasurementEdit
import com.example.workouttracker.domain.measurement.MeasurementRewards
import com.example.workouttracker.domain.measurement.MeasurementStep
import com.example.workouttracker.domain.measurement.applyingEdits
import com.example.workouttracker.domain.measurement.testMadeMeasurement
import com.example.workouttracker.domain.measurement.testWeightMeasurement
import com.example.workouttracker.persistence.plainJson
import java.time.LocalDate
import java.time.LocalTime
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/** What Weight's page keeps across process death reads back exactly as written: its baseline and every kind of edit. */
class MeasurementEditJsonTest {
    @Test
    fun everyEditReadsBackAsWritten() {
        val edits = listOf(
            MeasurementEdit.Rename("Morning weight"),
            MeasurementEdit.SetIcon(ViewIcon.Scale),
            MeasurementEdit.SetIcon(ViewIcon.Heart),
            MeasurementEdit.SetHue(ViewHue.Plum),
            MeasurementEdit.SetUnit("cm"),
            MeasurementEdit.SetUnit(""),
            // What was typed is kept as typed while it is an edit; the configuration decides whether it takes it.
            MeasurementEdit.SetUnit(" km / h \n"),
            MeasurementEdit.SetStep(MeasurementStep("1")),
            MeasurementEdit.SetStep(MeasurementStep("0.01")),
            MeasurementEdit.SetStep(MeasurementStep("0.5")),
            MeasurementEdit.SetStep(MeasurementStep("2.5")),
            MeasurementEdit.JoinRewards,
            MeasurementEdit.SetBankRules(maxPasses = 4, everyKeptDays = 5),
            MeasurementEdit.LeaveRewards,
            MeasurementEdit.SetWhenAway(WhenAway.Due),
            MeasurementEdit.SetWhenAway(WhenAway.Excused),
            MeasurementEdit.SetRemind(true),
            MeasurementEdit.SetRemind(false),
            MeasurementEdit.SetReminderTime(CheckpointTime.Fixed(LocalTime.of(21, 30))),
            MeasurementEdit.SetReminderTime(CheckpointTime.AfterWake(15)),
            MeasurementEdit.SetReminderTime(CheckpointTime.BeforeSleep(30)),
        )
        assertEquals(edits, AppStateJsonCodec.decodeMeasurementEdits(AppStateJsonCodec.encodeMeasurementEdits(edits)))
        assertEquals(emptyList<MeasurementEdit>(), AppStateJsonCodec.decodeMeasurementEdits(AppStateJsonCodec.encodeMeasurementEdits(emptyList())))
    }

    /**
     * `bankRules` are the two numbers of a measurement's bank. `rewardRules`, the week rule
     * Weight's page saved while a week's logged days could be set, is no edit any more.
     */
    @Test
    fun theBanksRulesAreSavedUnderTheirOwnNameAndTheWeekRuleIsNoEditAnyMore() {
        fun saved(edit: MeasurementEdit) = JSONArray(AppStateJsonCodec.encodeMeasurementEdits(listOf(edit))).getJSONObject(0)
        assertEquals(setOf("edit", "maxPasses", "everyKeptDays"), saved(MeasurementEdit.SetBankRules(3, 7)).keys().asSequence().toSet())
        assertEquals("bankRules", saved(MeasurementEdit.SetBankRules(3, 7)).get("edit"))
        assertEquals("joinRewards", saved(MeasurementEdit.JoinRewards).get("edit"))
        assertEquals("leaveRewards", saved(MeasurementEdit.LeaveRewards).get("edit"))

        listOf(
            """[{"edit":"rewardRules","maxPasses":3,"keptDaysInWeek":4}]""",
            """[{"edit":"bankRules","maxPasses":3,"keptDaysInWeek":4}]""",
            """[{"edit":"bankRules","maxPasses":3}]""",
            """[{"edit":"bankRules","maxPasses":"3","everyKeptDays":7}]""",
        ).forEach { raw -> assertThrows(raw, RuntimeException::class.java) { AppStateJsonCodec.decodeMeasurementEdits(raw) } }

        // Applied, the saved rules make Weight's week bank earn by kept days, and change a made measurement's bank.
        val day = LocalDate.parse("2026-10-14")
        val rules = AppStateJsonCodec.decodeMeasurementEdits("""[{"edit":"bankRules","maxPasses":5,"everyKeptDays":4}]""")
        val weight = testWeightMeasurement().applyingEdits(rules, day)
        assertNull(weight.weekRewards)
        assertEquals(5 to 4, weight.standardRewards!!.let { it.maxPasses.current to it.everyKeptDays.current })
        val joined = testMadeMeasurement().applyingEdits(listOf(MeasurementEdit.JoinRewards), day)
        assertEquals(5 to 4, joined.applyingEdits(rules, day.plusDays(3)).standardRewards!!.let { it.maxPasses.current to it.everyKeptDays.current })
    }

    /** The reminder's edits have names no earlier edit had, and each holds exactly its own value, the time as a checkpoint writes it. */
    @Test
    fun theRemindersEditsAreSavedUnderNamesOfTheirOwn() {
        val saved = AppStateJsonCodec.encodeMeasurementEdits(
            listOf(
                MeasurementEdit.SetRemind(true),
                MeasurementEdit.SetReminderTime(CheckpointTime.Fixed(LocalTime.of(21, 30))),
                MeasurementEdit.SetReminderTime(CheckpointTime.AfterWake(15)),
                MeasurementEdit.SetReminderTime(CheckpointTime.BeforeSleep(30)),
            ),
        )
        assertEquals(
            plainJson(JSONArray("""[{"edit":"remind","remind":true},{"edit":"reminderTime","at":"21:30"},{"edit":"reminderTime","at":"wake+15"},{"edit":"reminderTime","at":"sleep-30"}]""")),
            plainJson(JSONArray(saved)),
        )
        listOf(
            """[{"edit":"remind","remind":"true"}]""",
            """[{"edit":"remind"}]""",
            """[{"edit":"reminderTime","at":"9:00"}]""",
            """[{"edit":"reminderTime","at":"wake+05"}]""",
            """[{"edit":"reminderTime","at":540}]""",
            """[{"edit":"checkpointRemind","checkpoint":"day","remind":true}]""",
        ).forEach { raw -> assertThrows(raw, RuntimeException::class.java) { AppStateJsonCodec.decodeMeasurementEdits(raw) } }
    }

    /** The step's edit is saved under its own name, with the step as the decimal it is, a text in the one way a step is written. */
    @Test
    fun theStepsEditIsSavedAsItsDecimal() {
        val saved = AppStateJsonCodec.encodeMeasurementEdits(listOf("0.5", "0.05", "2.5", "1", "1000").map { MeasurementEdit.SetStep(MeasurementStep(it)) } + MeasurementEdit.SetStep(MeasurementStep("0.50")))
        assertEquals(
            plainJson(JSONArray("""[{"edit":"step","step":"0.5"},{"edit":"step","step":"0.05"},{"edit":"step","step":"2.5"},{"edit":"step","step":"1"},{"edit":"step","step":"1000"},{"edit":"step","step":"0.5"}]""")),
            plainJson(JSONArray(saved)),
        )
        listOf(
            """[{"edit":"step","step":0.5}]""",
            """[{"edit":"step","step":1}]""",
            """[{"edit":"step","step":"0.50"}]""",
            """[{"edit":"step","step":"0.125"}]""",
            """[{"edit":"step","step":"0"}]""",
            """[{"edit":"step","step":"1000.01"}]""",
            """[{"edit":"step","step":"half"}]""",
            """[{"edit":"step"}]""",
            """[{"edit":"step","decimals":1}]""",
            """[{"edit":"decimals","decimals":1}]""",
        ).forEach { raw -> assertThrows(raw, RuntimeException::class.java) { AppStateJsonCodec.decodeMeasurementEdits(raw) } }
    }

    @Test
    fun theTrendSpansEditIsSavedAsItsDays() {
        val edits = listOf(MeasurementEdit.SetTrendDays(7), MeasurementEdit.SetTrendDays(28))
        assertEquals(edits, AppStateJsonCodec.decodeMeasurementEdits(AppStateJsonCodec.encodeMeasurementEdits(edits)))
        listOf(
            """[{"edit":"trendDays","days":"7"}]""",
            """[{"edit":"trendDays","days":7.5}]""",
            """[{"edit":"trendDays"}]""",
        ).forEach { raw -> assertThrows(raw, RuntimeException::class.java) { AppStateJsonCodec.decodeMeasurementEdits(raw) } }
    }

    @Test
    fun theBaselineReadsBackWithItsDatedRules() {
        val day = LocalDate.parse("2026-10-14")
        val changed = testWeightMeasurement().copy(
            label = "Morning weight",
            rewards = MeasurementBank.Week(MeasurementRewards(Dated(listOf(Dated.Revision(7), Dated.Revision(3, day))), Dated.always(6))),
            whenAway = Dated(listOf(Dated.Revision(WhenAway.Excused), Dated.Revision(WhenAway.Due, day))),
        )
        // A measurement a person made reads back as one, outside the streak or in it with its rule and its reminder.
        val counting = testMadeMeasurement().applyingEdits(listOf(MeasurementEdit.JoinRewards, MeasurementEdit.SetBankRules(4, 5), MeasurementEdit.SetWhenAway(WhenAway.Excused)), day)
        val left = counting.applyingEdits(listOf(MeasurementEdit.SetBankRules(2, 9), MeasurementEdit.LeaveRewards, MeasurementEdit.SetWhenAway(WhenAway.Due)), day.plusDays(4))
        val inPounds = changed.copy(unit = "lb", step = MeasurementStep("0.5"))
        listOf(testWeightMeasurement(), changed, inPounds, testMadeMeasurement(), testMadeMeasurement(unit = "", step = "1"), testMadeMeasurement(step = "2.5"), counting, left).forEach { measurement ->
            assertEquals(measurement, AppStateJsonCodec.decodeMeasurement(AppStateJsonCodec.encodeMeasurement(measurement)))
        }
    }

    @Test
    fun anEditThatCannotBeReadExactlyIsRefused() {
        listOf(
            """[{"edit":"aim","aim":80}]""",
            """[{"edit":"unit","unit":7}]""",
            """[{"edit":"unit"}]""",
            """[{"edit":"icon","icon":"rocket"}]""",
            """[{"edit":"rewardRules","maxPasses":"3","keptDaysInWeek":4}]""",
            """[{"edit":"whenAway","whenAway":"sometimes"}]""",
            """["rename"]""",
        ).forEach { raw -> assertThrows(raw, RuntimeException::class.java) { AppStateJsonCodec.decodeMeasurementEdits(raw) } }
    }
}

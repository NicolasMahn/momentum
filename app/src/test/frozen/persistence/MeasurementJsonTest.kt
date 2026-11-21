package com.example.workouttracker.persistence.json

import com.example.workouttracker.domain.measurement.MeasurementValuePolicy

import com.example.workouttracker.persistence.json.introducedInFormat

import com.example.workouttracker.domain.AppState
import com.example.workouttracker.domain.checklist.ChecklistRewards
import com.example.workouttracker.domain.checklist.Checkpoint
import com.example.workouttracker.domain.checklist.CheckpointTime
import com.example.workouttracker.domain.checklist.Dated
import com.example.workouttracker.domain.checklist.RewardPeriod
import com.example.workouttracker.domain.checklist.ViewHue
import com.example.workouttracker.domain.checklist.ViewIcon
import com.example.workouttracker.domain.checklist.WhenAway
import com.example.workouttracker.domain.measurement.MeasurementBank
import com.example.workouttracker.domain.measurement.MeasurementConfiguration
import com.example.workouttracker.domain.measurement.MeasurementLog
import com.example.workouttracker.domain.measurement.MeasurementRewards
import com.example.workouttracker.domain.measurement.MeasurementStep
import com.example.workouttracker.domain.compatibility.WeightMeasurementId
import com.example.workouttracker.domain.compatibility.defaultWeightCheckpoint
import com.example.workouttracker.domain.measurement.DefaultMeasurementCheckpoint
import com.example.workouttracker.domain.measurement.testMadeMeasurement
import com.example.workouttracker.domain.measurement.testWeightMeasurement
import com.example.workouttracker.domain.measurement.withWeight
import com.example.workouttracker.domain.supplement.testSupplementsChecklist
import com.example.workouttracker.persistence.FrozenMoodOfVersion25
import com.example.workouttracker.persistence.FrozenWaistOfVersion25
import com.example.workouttracker.persistence.claimedAs
import com.example.workouttracker.persistence.encodedAsVersion
import com.example.workouttracker.persistence.frozenVersion25
import com.example.workouttracker.persistence.frozenVersion27
import com.example.workouttracker.persistence.legacyTrackerJson
import com.example.workouttracker.persistence.readBefore27
import java.time.LocalDate
import java.time.LocalTime
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import com.example.workouttracker.domain.questions.withEating
import com.example.workouttracker.domain.time.withReading
import com.example.workouttracker.domain.activity.withActivity
import com.example.workouttracker.persistence.readBefore29
import com.example.workouttracker.persistence.readBefore30

class MeasurementJsonTest {
    private val day = LocalDate.parse("2026-10-12")

    /** Weight renamed and recolored, with its bank, its number of days and its away rule each changed on a day. */
    private val changed = MeasurementConfiguration(
        id = WeightMeasurementId,
        valuePolicy = MeasurementValuePolicy.Positive,
        label = "Morning weight",
        icon = ViewIcon.Heart,
        hue = ViewHue.Plum,
        unit = "kg",
        step = MeasurementStep("0.1"),
        rewards = MeasurementBank.Week(MeasurementRewards(
            maxPasses = Dated(listOf(Dated.Revision(7), Dated.Revision(3, day))),
            keptDaysInWeek = Dated(listOf(Dated.Revision(6), Dated.Revision(4, day), Dated.Revision(5, day.plusDays(7)))),
        )),
        whenAway = Dated(listOf(Dated.Revision(WhenAway.Excused), Dated.Revision(WhenAway.Due, day.plusDays(1)))),
        checkpoint = defaultWeightCheckpoint(remind = false),
    )

    /** The bank a made view starts with, joined on [day]. */
    private val joined = ChecklistRewards(maxPasses = 3, everyKeptDays = 7, periods = listOf(RewardPeriod(day)))

    /** A waist that counts toward the streak since [day], is not due on holiday, and reminds half an hour after waking. */
    private val counting = testMadeMeasurement().copy(
        rewards = MeasurementBank.Standard(joined),
        whenAway = Dated.always(WhenAway.Excused),
        checkpoint = Checkpoint("day", CheckpointTime.AfterWake(30), remind = true),
    )

    @Test fun weightsDefaultIsStoredInTheContractsShape() {
        val stored = JSONObject(AppStateJsonCodec.encodeVersion30(AppState().withWeight().withEating().withReading().withActivity())).getJSONArray("measurements")
        val expected = JSONArray(
            """[{"view":"measurement","id":"weight","label":"Weight","icon":"scale","hue":"sky","unit":"kg","step":"0.1",""" +
                """"checkpoints":[{"id":"day","at":"wake+5","remind":false}],"rewards":{"maxPasses":7,"earn":{"keptDaysInWeek":6}},"whenAway":"excused"}]""",
        )
        assertEquals(plain(expected), plain(stored))
        assertEquals(AppState().withWeight().withEating().withReading().withActivity(), AppStateJsonCodec.decodeVersion30(AppStateJsonCodec.encodeVersion30(AppState().withWeight().withEating().withReading().withActivity())))
    }

    /**
     * A measurement a person made, as it starts, holds its name, its look, its unit, its step and
     * its reminder, off at 09:00, and nothing of the streak: no bank, not even an empty one, and no
     * away rule while it is still due as ever.
     */
    @Test fun aMadeMeasurementIsStoredWithItsUnitItsStepAndItsReminderAndNothingOfTheStreak() {
        val mood = testMadeMeasurement(id = "msr_m7xd", label = "Mood", unit = "", step = "1")
        val state = AppState(measurements = listOf(testWeightMeasurement(), testMadeMeasurement(), mood)).withEating().withReading().withActivity()
        val stored = JSONObject(AppStateJsonCodec.encodeVersion30(state)).getJSONArray("measurements")
        assertEquals(
            plain(JSONObject("""{"view":"measurement","id":"msr_k2p9","label":"Waist","icon":"ruler","hue":"plum","unit":"cm","step":"0.1","checkpoints":[{"id":"day","at":"09:00","remind":false}]}""")),
            plain(stored.getJSONObject(1)),
        )
        assertEquals(
            plain(JSONObject("""{"view":"measurement","id":"msr_m7xd","label":"Mood","icon":"ruler","hue":"plum","unit":"","step":"1","checkpoints":[{"id":"day","at":"09:00","remind":false}]}""")),
            plain(stored.getJSONObject(2)),
        )
        assertEquals(state, AppStateJsonCodec.decodeVersion30(AppStateJsonCodec.encodeVersion30(state)))
        assertEquals(AppStateJsonCodec.encodeVersion30(state), AppStateJsonCodec.encodeVersion30(AppStateJsonCodec.decodeVersion30(AppStateJsonCodec.encodeVersion30(state))))
    }

    /**
     * One that counts toward the streak and reminds is stored in the contract's shape: its one
     * checkpoint `day`, the bank a checklist has under the same `rewards` Weight's week bank is
     * under, with `periods` always there, an empty list once it left with no day kept, and what an
     * away day means once it is not still due as ever.
     */
    @Test fun aMadeMeasurementThatCountsAndRemindsIsStoredInTheContractsShape() {
        val state = AppState(measurements = listOf(testWeightMeasurement(), counting, counting.copy(id = "msr_left", rewards = MeasurementBank.Standard(joined.copy(periods = emptyList()))))).withEating().withReading().withActivity()
        val stored = JSONObject(AppStateJsonCodec.encodeVersion30(state)).getJSONArray("measurements")
        assertEquals(
            plain(
                JSONObject(
                    """{"view":"measurement","id":"msr_k2p9","label":"Waist","icon":"ruler","hue":"plum","unit":"cm","step":"0.1",""" +
                        """"checkpoints":[{"id":"day","at":"wake+30","remind":true}],""" +
                        """"rewards":{"maxPasses":3,"earn":{"everyKeptDays":7},"periods":[{"from":"2026-10-12"}]},"whenAway":"excused"}""",
                ),
            ),
            plain(stored.getJSONObject(1)),
        )
        assertEquals(plain(JSONObject("""{"maxPasses":3,"earn":{"everyKeptDays":7},"periods":[]}""")), plain(stored.getJSONObject(2).get("rewards")))
        assertEquals(state, AppStateJsonCodec.decodeVersion30(AppStateJsonCodec.encodeVersion30(state)))
        assertEquals(AppStateJsonCodec.encodeVersion30(state), AppStateJsonCodec.encodeVersion30(AppStateJsonCodec.decodeVersion30(AppStateJsonCodec.encodeVersion30(state))))
    }

    /**
     * Versions 25 to 29 round-trip with and without measurements a person made, in their order,
     * with a unit of every kind kept exactly as typed and each of the three steps the older ones
     * could hold, and with records of a made measurement, of Weight and of one nothing knows side
     * by side. Versions 26 to 29 also round-trip every form a made measurement's parts can take:
     * in the streak, out of it again, its rules changed on a day, not due on holiday from a day,
     * and reminding at each kind of time.
     */
    @Test fun versions25To29RoundTripWithAndWithoutMadeMeasurements() {
        val units = listOf("cm", "", "%", "bpm", "km / h", "°C", "ml·kg⁻¹", "כ״ס", "🍎 a day", "twelve chars")
        val made = units.mapIndexed { index, unit -> testMadeMeasurement(id = "msr_aaa$index", label = "Made $index", unit = unit, step = listOf("1", "0.1", "0.01")[index % 3]) }
        val records = listOf(
            MeasurementLog("msr_aaa3", "2026-10-12", 9, 61.0),
            MeasurementLog(WeightMeasurementId, "2026-10-12", 8, 82.5),
            MeasurementLog("msr_gone", "2026-10-12", 7, -3.0),
            MeasurementLog("msr_aaa3", "2026-10-12", 9, 61.0),
            MeasurementLog("msr_aaa1", "2026-10-11", 0, 0.0),
        )
        listOf(emptyList(), made.take(1), made, made.reversed()).forEach { measurements ->
            val state = AppState(measurements = listOf(changed) + measurements, measurementLogs = records).withEating().withReading().withActivity()
            val encoded = AppStateJsonCodec.encodeVersion30(state)
            val decoded = AppStateJsonCodec.decodeVersion30(encoded)
            assertEquals(state, decoded)
            assertEquals(measurements.map { it.unit }, decoded.measurements.drop(1).map { it.unit })
            assertEquals(encoded, AppStateJsonCodec.encodeVersion30(decoded))
            assertEquals(false, AppStateJsonCodec.lostRecords(encoded, decoded))
            // Version 28 held the same measurements, each with its step.
            assertEquals(state.readBefore29(), AppStateJsonCodec.decodeVersion28(legacyTrackerJson(state, 28).toString()))
            // Versions 26 and 27 held them with the decimal places of each step.
            val version27 = legacyTrackerJson(state, 27)
            assertEquals(false, version27.toString().contains("\"step\""))
            assertEquals(state.readBefore29(), AppStateJsonCodec.decodeVersion27(version27.toString()))
            assertEquals(state.readBefore27(), AppStateJsonCodec.decodeVersion26(legacyTrackerJson(state, 26).toString()))
            // Version 25 held the same measurements without a checkpoint, and reads them as these.
            val version25 = legacyTrackerJson(state, 25)
            assertEquals(false, version25.toString().contains("checkpoints"))
            assertEquals(state.readBefore27(), AppStateJsonCodec.decodeVersion25(version25.toString()))
        }
        // Weight need not come first.
        val weightLast = AppState(measurements = made.take(2) + changed).withEating().withReading().withActivity()
        assertEquals(weightLast, AppStateJsonCodec.decodeVersion30(AppStateJsonCodec.encodeVersion30(weightLast)))
        assertEquals(weightLast.readBefore27(), AppStateJsonCodec.decodeVersion25(legacyTrackerJson(weightLast, 25).toString()))

        val everyForm = listOf(
            counting,
            counting.copy(rewards = MeasurementBank.Standard(joined.copy(periods = emptyList()))),
            counting.copy(rewards = MeasurementBank.Standard(joined.copy(periods = listOf(RewardPeriod(day.minusDays(30), day.minusDays(20)), RewardPeriod(day.minusDays(9), day.minusDays(1)))))),
            counting.copy(rewards = MeasurementBank.Standard(joined.copy(periods = listOf(RewardPeriod(day.minusDays(30), day.minusDays(20)), RewardPeriod(day))))),
            counting.copy(
                rewards = MeasurementBank.Standard(
                    ChecklistRewards(
                        maxPasses = Dated(listOf(Dated.Revision(3), Dated.Revision(5, day.plusDays(1)))),
                        everyKeptDays = Dated(listOf(Dated.Revision(7), Dated.Revision(4, day.plusDays(1)), Dated.Revision(10, day.plusDays(8)))),
                        periods = listOf(RewardPeriod(day)),
                    ),
                ),
            ),
            counting.copy(whenAway = Dated(listOf(Dated.Revision(WhenAway.Due), Dated.Revision(WhenAway.Excused, day)))),
            counting.copy(whenAway = Dated(listOf(Dated.Revision(WhenAway.Excused), Dated.Revision(WhenAway.Due, day)))),
            // Outside the streak a measurement still reminds, and still says what a holiday means.
            counting.copy(rewards = null),
            counting.copy(rewards = null, whenAway = Dated.always(WhenAway.Due)),
            counting.copy(checkpoint = Checkpoint("day", CheckpointTime.Fixed(LocalTime.of(21, 30)), remind = true)),
            counting.copy(checkpoint = Checkpoint("day", CheckpointTime.BeforeSleep(15), remind = false)),
        )
        everyForm.forEach { measurement ->
            val state = AppState(measurements = listOf(measurement, changed), measurementLogs = records).withEating().withReading().withActivity()
            val encoded = AppStateJsonCodec.encodeVersion30(state)
            assertEquals(encoded, state, AppStateJsonCodec.decodeVersion30(encoded))
            assertEquals(encoded, AppStateJsonCodec.encodeVersion30(AppStateJsonCodec.decodeVersion30(encoded)))
            assertEquals(encoded, state.readBefore29(), AppStateJsonCodec.decodeVersion28(legacyTrackerJson(state, 28).toString()))
            assertEquals(encoded, state.readBefore29(), AppStateJsonCodec.decodeVersion27(legacyTrackerJson(state, 27).toString()))
            assertEquals(encoded, state.readBefore27(), AppStateJsonCodec.decodeVersion26(legacyTrackerJson(state, 26).toString()))
        }
        val together = AppState(measurements = listOf(changed) + everyForm.mapIndexed { index, form -> form.copy(id = "msr_bbb$index") }).withEating().withReading().withActivity()
        assertEquals(together, AppStateJsonCodec.decodeVersion30(AppStateJsonCodec.encodeVersion30(together)))
    }

    /**
     * Version 28 holds a step of any size the rule allows, on Weight too, and Weight in any unit
     * or none: each reads back as it was stored and is written again as the same bytes. The step
     * is written as its decimal, a text, in the one way a step is written.
     */
    @Test fun version28RoundTripsAStepOfAnySizeAndWeightInAnyUnit() {
        val steps = listOf("0.5", "0.05", "2.5", "1", "0.1", "0.01", "0.07", "12.34", "999.99", "1000")
        val made = steps.mapIndexed { index, step -> testMadeMeasurement(id = "msr_aaa$index", label = "Made $index", step = step) }
        listOf(
            changed.copy(unit = "lb", step = MeasurementStep("0.5")),
            changed.copy(unit = "st", step = MeasurementStep("0.05")),
            changed.copy(unit = "", step = MeasurementStep("2.5")),
            changed.copy(unit = "KG"),
            changed.copy(step = MeasurementStep("1")),
            changed,
        ).forEach { weight ->
            val state = AppState(measurements = listOf(weight) + made).withEating().withReading().withActivity()
            val encoded = AppStateJsonCodec.encodeVersion30(state)
            val decoded = AppStateJsonCodec.decodeVersion30(encoded)
            assertEquals(state, decoded)
            assertEquals(encoded, AppStateJsonCodec.encodeVersion30(decoded))
            val stored = JSONObject(encoded).getJSONArray("measurements")
            assertEquals(listOf(weight.step.written) + steps, (0 until stored.length()).map { stored.getJSONObject(it).get("step") })
            assertEquals(weight.unit, stored.getJSONObject(0).get("unit"))
            assertEquals(false, encoded.contains("decimals"))
        }
    }

    /**
     * Versions 25 to 29 refuse what they cannot hold instead of repairing it: a missing or second
     * Weight, a repeated ID, a made measurement under Weight's ID, a missing or extra field, a
     * week rule on a made measurement, and a unit that cannot be one. Each version is handed the
     * payload in its own shape, with `decimals` before version 28 and `step` from it on.
     */
    @Test fun versions25To29RefuseWhatTheyCannotHoldAndRepairNothing() {
        val state = AppState(measurements = listOf(changed, testMadeMeasurement())).withEating().withReading().withActivity()
        val current = AppStateJsonCodec.encodeVersion30(state)
        val decoders: Map<Int, (String) -> AppState> = mapOf(
            29 to AppStateJsonCodec::decodeVersion29,
            28 to AppStateJsonCodec::decodeVersion28,
            27 to AppStateJsonCodec::decodeVersion27,
            26 to AppStateJsonCodec::decodeVersion26,
            25 to AppStateJsonCodec::decodeVersion25,
        )
        decoders.forEach { (version, decode) ->
            val valid = claimedAs(current, version)
            val stepField = if (version >= 28) "step" else "decimals"
            val read = when {
                version == 29 -> state.readBefore30()
                version >= 27 -> state.readBefore29()
                else -> state.readBefore27()
            }
            assertEquals("version $version", read, decode(valid.toString()))
            assertEquals("version $version", true, valid.getJSONArray("measurements").getJSONObject(1).has(stepField))
            fun root(change: (JSONObject) -> Unit) = JSONObject(valid.toString()).apply(change).toString()
            fun weight(change: (JSONObject) -> Unit) = root { change(it.getJSONArray("measurements").getJSONObject(0)) }
            fun made(change: (JSONObject) -> Unit) = root { change(it.getJSONArray("measurements").getJSONObject(1)) }
            fun added(entry: (JSONArray) -> Any) = root { stored -> stored.getJSONArray("measurements").let { it.put(entry(it)) } }
            val weightsBank = valid.getJSONArray("measurements").getJSONObject(0).getJSONObject("rewards").toString()
            val refused = listOf(
                "no field" to root { it.remove("measurements") },
                "no list" to root { it.put("measurements", JSONObject()) },
                "an empty list" to root { it.put("measurements", JSONArray()) },
                "no Weight" to root { it.getJSONArray("measurements").remove(0) },
                "no object" to added { "msr_zzzz" },
                "a second Weight" to added { JSONObject(it.getJSONObject(0).toString()) },
                "a second Weight under another name" to added { JSONObject(it.getJSONObject(0).toString()).put("label", "Evening weight") },
                "a repeated ID" to added { JSONObject(it.getJSONObject(1).toString()) },
                "a repeated ID under another name" to added { JSONObject(it.getJSONObject(1).toString()).put("label", "Hips").put("unit", "in") },
                "a made measurement under Weight's ID" to added { JSONObject(it.getJSONObject(1).toString()).put("id", "weight") },
                "only a made measurement under Weight's ID" to weight { it.remove("rewards"); it.remove("whenAway") },
                "Weight under another ID" to weight { it.put("id", "msr_zzzz") },
                "Weight with a unit that cannot be one" to weight { it.put("unit", "thirteen long") },
                "Weight without a bank" to weight { it.remove("rewards") },
                "Weight with a null bank" to weight { it.put("rewards", JSONObject.NULL) },
                "Weight without an away rule" to weight { it.remove("whenAway") },
                "Weight with a reminder" to weight { it.put("remind", true) },
                "a week rule on a made measurement" to made { it.put("rewards", JSONObject(weightsBank)) },
                "a null bank on a made measurement" to made { it.put("rewards", JSONObject.NULL) },
                "a due away rule on a made measurement" to made { it.put("whenAway", "due") },
                "a week rule and an away rule on a made measurement" to made { it.put("rewards", JSONObject(weightsBank)).put("whenAway", "excused") },
                "a reminder on a made measurement" to made { it.put("remind", false) },
                "reward periods on a made measurement" to made { it.put("periods", JSONArray()) },
                "an aim" to made { it.put("aim", 80) },
                "a made measurement of another view" to made { it.put("view", "checklist") },
                "an unknown icon" to made { it.put("icon", "rocket") },
                "an unknown hue" to made { it.put("hue", "turquoise") },
                "a blank name" to made { it.put("label", " ") },
                "an ID with a space" to made { it.put("id", "msr k2p9") },
                "an ID that is no text" to made { it.put("id", 7) },
                "a unit with a line break" to made { it.put("unit", "c\nm") },
                "a unit with a tab" to made { it.put("unit", "c\tm") },
                "a unit with a space before it" to made { it.put("unit", " cm") },
                "a unit with a space after it" to made { it.put("unit", "cm ") },
                "a unit of spaces" to made { it.put("unit", "  ") },
                "a unit with a direction mark" to made { it.put("unit", "cm‎") },
                "a unit with a direction override" to made { it.put("unit", "‮mc‬") },
                "a unit of thirteen code points" to made { it.put("unit", "thirteen long") },
                "a unit that is a number" to made { it.put("unit", 7) },
                "a null unit" to made { it.put("unit", JSONObject.NULL) },
                "a null $stepField" to made { it.put(stepField, JSONObject.NULL) },
                "a decimals and a step" to made { it.put("decimals", 1).put("step", "0.1") },
            ) + listOf("view", "id", "label", "icon", "hue", "unit", stepField).flatMap { field ->
                listOf("Weight with no $field" to weight { it.remove(field) }, "a made measurement with no $field" to made { it.remove(field) })
            }
            refused.forEach { (what, raw) -> assertThrows("version $version: $what", RuntimeException::class.java) { decode(raw) } }
        }
    }

    /**
     * What a step may say, version 28 refuses by the rule a step has and repairs nothing: a step
     * of nothing or less, one above 1000, one with more than two decimal places, a number that is
     * no text, a decimal written another way than the app writes it, and the decimal places older
     * versions held in its place. Weight's is refused by the same rule and no other.
     */
    @Test fun version28RefusesAStepThatBreaksItsRuleAndRepairsNothing() {
        val state = AppState(measurements = listOf(changed, testMadeMeasurement())).withEating().withReading().withActivity()
        val valid = AppStateJsonCodec.encodeVersion30(state)
        fun measurement(index: Int, change: (JSONObject) -> Unit) = JSONObject(valid).apply { change(getJSONArray("measurements").getJSONObject(index)) }.toString()
        val steps: Map<String, Any> = mapOf(
            "nothing" to "0",
            "nothing, with places" to "0.00",
            "less than nothing" to "-0.5",
            "more than 1000" to "1000.01",
            "far more than 1000" to "1000000",
            "three decimal places" to "0.125",
            "three places, the last a zero" to "0.500",
            "a zero that says nothing after it" to "0.50",
            "a point and nothing after it" to "5.",
            "nothing before the point" to ".5",
            "a zero before it" to "05",
            "a sign" to "+1",
            "an exponent" to "1e2",
            "a comma" to "0,5",
            "spaces around it" to " 0.5 ",
            "its unit" to "0.5 cm",
            "no number" to "half",
            "empty text" to "",
            "not a number" to "NaN",
            "a JSON number" to 0.5,
            "a whole JSON number" to 1,
            "a list" to JSONArray().put("0.5"),
            "null" to JSONObject.NULL,
        )
        listOf(0 to "Weight", 1 to "a made measurement").forEach { (index, whose) ->
            steps.forEach { (what, step) ->
                assertThrows("$whose: $what", RuntimeException::class.java) { AppStateJsonCodec.decodeVersion30(measurement(index) { it.put("step", step) }) }
            }
            listOf(
                "decimal places instead of a step" to measurement(index) { it.remove("step"); it.put("decimals", 1) },
                "decimal places beside the step" to measurement(index) { it.put("decimals", 1) },
                "no step" to measurement(index) { it.remove("step") },
            ).forEach { (what, raw) -> assertThrows("$whose: $what", RuntimeException::class.java) { AppStateJsonCodec.decodeVersion30(raw) } }
        }
        // A version 27 payload is not the current version's, whatever it claims, and the other way round.
        val version27 = legacyTrackerJson(state, 27)
        assertEquals(state.readBefore29(), AppStateJsonCodec.decodeVersion27(version27.toString()))
        assertThrows(RuntimeException::class.java) { AppStateJsonCodec.decodeVersion30(version27.toString()) }
        assertThrows(RuntimeException::class.java) { AppStateJsonCodec.decodeVersion30(JSONObject(version27.toString()).put("schemaVersion", 29).toString()) }
        assertThrows(RuntimeException::class.java) { AppStateJsonCodec.decodeVersion27(valid) }
        assertThrows(RuntimeException::class.java) { AppStateJsonCodec.decodeVersion27(JSONObject(valid).put("schemaVersion", 27).toString()) }
    }

    /**
     * A version 27 payload, written out as that version wrote it with Weight and a made
     * measurement of each of the three numbers of decimal places, reads with the steps 1, 0.1 and
     * 0.01 they stood for and everything else as it was stored. Written by the current version it
     * is the same state, with each step said as its decimal and no `decimals` left.
     */
    @Test fun aVersion27PayloadReadsWithTheStepsItsDecimalPlacesStoodFor() {
        val stored = frozenVersion27()

        val read = AppStateJsonCodec.decodeVersion27(stored)

        assertEquals(
            listOf(
                testWeightMeasurement(),
                testMadeMeasurement(),
                MeasurementConfiguration(
                    id = "msr_m7xd",
                    label = "Mood",
                    icon = ViewIcon.Mood,
                    hue = ViewHue.Teal,
                    unit = "",
                    step = MeasurementStep("1"),
                    rewards = MeasurementBank.Standard(ChecklistRewards(maxPasses = 3, everyKeptDays = 7, periods = listOf(RewardPeriod(LocalDate.parse("2026-10-06"))))),
                    whenAway = Dated.always(WhenAway.Excused),
                    checkpoint = Checkpoint("day", CheckpointTime.BeforeSleep(30), remind = true),
                ),
                MeasurementConfiguration(
                    id = "msr_b4fq",
                    label = "Body fat",
                    icon = ViewIcon.Ruler,
                    hue = ViewHue.Olive,
                    unit = "ratio",
                    step = MeasurementStep("0.01"),
                    rewards = null,
                    whenAway = Dated.always(WhenAway.Due),
                    checkpoint = Checkpoint("day", CheckpointTime.AfterWake(15), remind = false),
                ),
            ),
            read.measurements,
        )
        assertEquals(listOf("0.1", "0.1", "1", "0.01"), read.measurements.map { it.step.written })
        assertEquals(listOf(0.1, 0.1, 1.0, 0.01), read.measurements.map { it.step.value })

        val written = AppStateJsonCodec.encodeVersion30(read.withReading().withActivity())
        assertEquals(read.withReading().withActivity(), AppStateJsonCodec.decodeVersion30(written))
        val measurements = JSONObject(written).getJSONArray("measurements")
        assertEquals(listOf("0.1", "0.1", "1", "0.01"), (0 until measurements.length()).map { measurements.getJSONObject(it).get("step") })
        assertEquals(false, written.contains("decimals"))
        // Nothing but the field that says the step differs between what was stored and what is
        // written, and Weight's checkpoint, which version 27 did not hold and is written off.
        val before = JSONObject(stored).getJSONArray("measurements")
        (0 until measurements.length()).forEach { index ->
            val was = JSONObject(before.getJSONObject(index).toString()).apply { remove("decimals") }
            val isNow = JSONObject(measurements.getJSONObject(index).toString()).apply { remove("step") }
            if (index == 0) assertEquals(plain(JSONArray("""[{"id":"day","at":"wake+5","remind":false}]""")), plain(isNow.remove("checkpoints")))
            assertEquals(plain(was), plain(isNow))
        }
    }

    /**
     * Versions 25 to 27 are read by the rules they had, now that the configuration itself no
     * longer asks them: `decimals` is 0, 1 or 2, and Weight is `kg` with one decimal place. A
     * payload that says otherwise is refused as it was, although version 28 could hold what it
     * says, so lifting the rule for version 28 makes no older payload readable. The payloads are
     * written out as those versions wrote them.
     */
    @Test fun versions25To27StillRefuseAnotherUnitOrStepForWeightAndDecimalPlacesNoStepHad() {
        fun weightOf27(unit: String = "\"kg\"", decimals: String = "1") =
            """{"view":"measurement","id":"weight","label":"Weight","icon":"scale","hue":"sky","unit":$unit,"decimals":$decimals,""" +
                """"rewards":{"maxPasses":7,"earn":{"keptDaysInWeek":6}},"whenAway":"excused"}"""
        fun waistOf27(decimals: String) =
            """{"view":"measurement","id":"msr_k2p9","label":"Waist","icon":"ruler","hue":"plum","unit":"cm","decimals":$decimals,""" +
                """"checkpoints":[{"id":"day","at":"09:00","remind":false}]}"""
        fun waistOf25(decimals: String) = """{"view":"measurement","id":"msr_k2p9","label":"Waist","icon":"ruler","hue":"plum","unit":"cm","decimals":$decimals}"""

        assertEquals(2, AppStateJsonCodec.decodeVersion27(frozenVersion27(listOf(weightOf27(), waistOf27("2")))).measurements.size)
        assertEquals(2, AppStateJsonCodec.decodeVersion25(frozenVersion25(made = listOf(waistOf25("2")), weight = weightOf27())).measurements.size)

        val refusedOfWeight = mapOf(
            "Weight in pounds" to weightOf27(unit = "\"lb\""),
            "Weight in Kilograms" to weightOf27(unit = "\"KG\""),
            "Weight without a unit" to weightOf27(unit = "\"\""),
            "Weight in steps of 1" to weightOf27(decimals = "0"),
            "Weight in steps of 0.01" to weightOf27(decimals = "2"),
            "Weight in pounds and steps of 1" to weightOf27(unit = "\"lb\"", decimals = "0"),
            "Weight with three decimal places" to weightOf27(decimals = "3"),
        )
        val refusedDecimals = mapOf(
            "three decimal places" to "3",
            "ten decimal places" to "10",
            "fewer than no decimal places" to "-1",
            "decimal places as text" to "\"1\"",
            "half a decimal place" to "1.5",
            "a step in their place" to "\"0.1\"",
            "null" to "null",
        )
        refusedOfWeight.forEach { (what, weight) ->
            val version27 = frozenVersion27(listOf(weight, waistOf27("1")))
            assertThrows("version 27: $what", RuntimeException::class.java) { AppStateJsonCodec.decodeVersion27(version27) }
            assertThrows("version 26: $what", RuntimeException::class.java) { AppStateJsonCodec.decodeVersion26(claimedAs(version27, 26).toString()) }
            assertThrows("version 25: $what", RuntimeException::class.java) { AppStateJsonCodec.decodeVersion25(frozenVersion25(made = listOf(waistOf25("1")), weight = weight)) }
        }
        refusedDecimals.forEach { (what, decimals) ->
            val version27 = frozenVersion27(listOf(weightOf27(), waistOf27(decimals)))
            assertThrows("version 27: $what", RuntimeException::class.java) { AppStateJsonCodec.decodeVersion27(version27) }
            assertThrows("version 26: $what", RuntimeException::class.java) { AppStateJsonCodec.decodeVersion26(claimedAs(version27, 26).toString()) }
            assertThrows("version 25: $what", RuntimeException::class.java) { AppStateJsonCodec.decodeVersion25(frozenVersion25(made = listOf(waistOf25(decimals)))) }
        }
        // What version 28 may say, an older version does not read for claiming to be older.
        listOf(27 to AppStateJsonCodec::decodeVersion27, 26 to AppStateJsonCodec::decodeVersion26, 25 to AppStateJsonCodec::decodeVersion25).forEach { (version, decode) ->
            listOf(
                testWeightMeasurement().copy(unit = "lb") to testMadeMeasurement(),
                testWeightMeasurement().copy(step = MeasurementStep("0.5")) to testMadeMeasurement(),
                testWeightMeasurement() to testMadeMeasurement(step = "0.5"),
                testWeightMeasurement() to testMadeMeasurement(step = "2.5"),
            ).forEach { (weight, waist) ->
                val newer = AppStateJsonCodec.encodeVersion30(AppState(measurements = listOf(weight, waist)).withEating().withReading().withActivity())
                assertThrows("version $version: $weight $waist", RuntimeException::class.java) { decode(claimedAs(newer, version).toString()) }
            }
        }
    }

    /**
     * What version 26 gave a made measurement, it and versions 27 and 28 hold exactly or not at all. Refused, never
     * repaired: a week rule on a made measurement; the standard bank on Weight; a made
     * measurement's bank without `periods`; no checkpoint, a second one, another checkpoint ID,
     * or a name or dates on it; `lowerTarget`; and a rule or a setting written in a form the
     * version would never write. Periods and a checkpoint on Weight arrived in version 30: it
     * reads them, and the versions before refuse them.
     */
    @Test fun versions26To28RefuseEveryPartOfAMadeMeasurementTheyCannotHold() {
        val state = AppState(measurements = listOf(changed, counting)).withEating().withReading().withActivity()
        val valid = JSONObject(AppStateJsonCodec.encodeVersion30(state))
        assertEquals(state, AppStateJsonCodec.decodeVersion30(valid.toString()))
        fun root(change: (JSONObject) -> Unit) = JSONObject(valid.toString()).apply(change).toString()
        fun weight(change: (JSONObject) -> Unit) = root { change(it.getJSONArray("measurements").getJSONObject(0)) }
        fun made(change: (JSONObject) -> Unit) = root { change(it.getJSONArray("measurements").getJSONObject(1)) }
        fun bank(change: (JSONObject) -> Unit) = made { change(it.getJSONObject("rewards")) }
        fun checkpoint(change: (JSONObject) -> Unit) = made { change(it.getJSONArray("checkpoints").getJSONObject(0)) }
        val weightsBank = valid.getJSONArray("measurements").getJSONObject(0).getJSONObject("rewards").toString()
        val standardBank = valid.getJSONArray("measurements").getJSONObject(1).getJSONObject("rewards").toString()
        val checkpoints = valid.getJSONArray("measurements").getJSONObject(1).getJSONArray("checkpoints").toString()
        val refused = listOf(
            "a week rule on a made measurement" to made { it.put("rewards", JSONObject(weightsBank)) },
            "a week rule with periods on a made measurement" to made { it.put("rewards", JSONObject(weightsBank).put("periods", JSONArray("""[{"from":"2026-10-12"}]"""))) },
            "a week's way of earning in a made measurement's bank" to bank { it.put("earn", JSONObject().put("keptDaysInWeek", 6)) },
            "two ways of earning" to bank { it.getJSONObject("earn").put("keptDaysInWeek", 6) },
            "no way of earning" to bank { it.put("earn", JSONObject()) },
            "another way of earning" to bank { it.put("earn", JSONObject().put("everyAmountInWeek", 60)) },
            "earning that is no object" to bank { it.put("earn", 7) },
            "the standard bank on Weight" to weight { it.put("rewards", JSONObject(standardBank)) },
            "the standard bank without periods on Weight" to weight { it.put("rewards", JSONObject(standardBank).apply { remove("periods") }) },
            "no checkpoints on Weight, said so" to weight { it.put("checkpoints", JSONArray()) },
            "a made measurement's bank without periods" to bank { it.remove("periods") },
            "null periods" to bank { it.put("periods", JSONObject.NULL) },
            "periods that are no list" to bank { it.put("periods", "2026-10-12") },
            "a period without a start" to bank { it.put("periods", JSONArray("""[{"until":"2026-10-12"}]""")) },
            "a period on no day" to bank { it.put("periods", JSONArray("""[{"from":"2026-02-30"}]""")) },
            "a period that ends before it starts" to bank { it.put("periods", JSONArray("""[{"from":"2026-10-12","until":"2026-10-11"}]""")) },
            "periods out of order" to bank { it.put("periods", JSONArray("""[{"from":"2026-10-12","until":"2026-10-13"},{"from":"2026-10-01"}]""")) },
            "an open period before the last" to bank { it.put("periods", JSONArray("""[{"from":"2026-10-01"},{"from":"2026-10-12"}]""")) },
            "a period with more in it" to bank { it.put("periods", JSONArray("""[{"from":"2026-10-12","paused":true}]""")) },
            "no bank size" to bank { it.remove("maxPasses") },
            "an empty bank" to bank { it.put("maxPasses", 0) },
            "a token for no kept day" to bank { it.getJSONObject("earn").put("everyKeptDays", 0) },
            "a bank size as text" to bank { it.put("maxPasses", "3") },
            "one revision" to bank { it.put("maxPasses", JSONArray("""[{"value":3}]""")) },
            "a revision that changes nothing" to bank { it.put("maxPasses", JSONArray("""[{"value":3},{"value":3,"from":"2026-10-13"}]""")) },
            "a null bank" to made { it.put("rewards", JSONObject.NULL) },
            "a bank that is no object" to made { it.put("rewards", true) },
            "no checkpoints" to made { it.remove("checkpoints") },
            "an empty list of checkpoints" to made { it.put("checkpoints", JSONArray()) },
            "null checkpoints" to made { it.put("checkpoints", JSONObject.NULL) },
            "a checkpoint outside a list" to made { it.put("checkpoints", it.getJSONArray("checkpoints").getJSONObject(0)) },
            "a second checkpoint" to made { it.getJSONArray("checkpoints").put(JSONObject("""{"id":"evening","at":"21:00","remind":false}""")) },
            "the same checkpoint twice" to made { it.getJSONArray("checkpoints").put(JSONObject(it.getJSONArray("checkpoints").getJSONObject(0).toString())) },
            "a checkpoint that is no object" to made { it.put("checkpoints", JSONArray().put("day")) },
            "another checkpoint ID" to checkpoint { it.put("id", "cpt_k2p9") },
            "a checkpoint ID in capitals" to checkpoint { it.put("id", "Day") },
            "a name on the checkpoint" to checkpoint { it.put("label", "Morning") },
            "a start on the checkpoint" to checkpoint { it.put("from", "2026-10-12") },
            "an end on the checkpoint" to checkpoint { it.put("until", "2026-10-20") },
            "a checkpoint without a time" to checkpoint { it.remove("at") },
            "a checkpoint without its switch" to checkpoint { it.remove("remind") },
            "a time that is none" to checkpoint { it.put("at", "9:00") },
            "an hour that is none" to checkpoint { it.put("at", "24:00") },
            "a time before waking" to checkpoint { it.put("at", "wake-5") },
            "a switch as text" to checkpoint { it.put("remind", "true") },
            "a checkpoint with more in it" to checkpoint { it.put("sound", "bell") },
            "a lowered target" to made { it.put("whenAway", "lowerTarget") },
            "a lowered target from a day" to made { it.put("whenAway", JSONArray("""[{"value":"excused"},{"value":"lowerTarget","from":"2026-10-13"}]""")) },
            "a lowered target for Weight" to weight { it.put("whenAway", "lowerTarget") },
            "still due as ever, written out" to made { it.put("whenAway", "due") },
            "an unknown away rule" to made { it.put("whenAway", "sometimes") },
            "an away rule of one revision" to made { it.put("whenAway", JSONArray("""[{"value":"excused"}]""")) },
            "a null away rule" to made { it.put("whenAway", JSONObject.NULL) },
            "a reminder beside the checkpoint" to made { it.put("remind", true) },
            "a target" to made { it.put("target", JSONObject().put("per", "day")) },
        )
        refused.forEach { (what, raw) ->
            assertThrows(what, RuntimeException::class.java) { AppStateJsonCodec.decodeVersion30(raw) }
            assertThrows("version 27: $what", RuntimeException::class.java) { AppStateJsonCodec.decodeVersion27(claimedAs(raw, 27).toString()) }
            assertThrows("version 26: $what", RuntimeException::class.java) { AppStateJsonCodec.decodeVersion26(claimedAs(raw, 26).toString()) }
        }
        // Weight's checkpoint is its own from version 30, where the versions before read it with the one the migration builds.
        assertThrows(RuntimeException::class.java) { AppStateJsonCodec.decodeVersion30(weight { it.remove("checkpoints") }) }
        val weekBank = requireNotNull(changed.weekRewards)
        listOf(
            "periods on Weight" to weight { it.getJSONObject("rewards").put("periods", JSONArray("""[{"from":"2026-10-12"}]""")) } to
                changed.copy(rewards = MeasurementBank.Week(weekBank.copy(periods = listOf(RewardPeriod(LocalDate.parse("2026-10-12")))))),
            "empty periods on Weight" to weight { it.getJSONObject("rewards").put("periods", JSONArray()) } to
                changed.copy(rewards = MeasurementBank.Week(weekBank.copy(periods = emptyList()))),
            "a checkpoint of its own on Weight" to weight { it.put("checkpoints", JSONArray(checkpoints)) } to changed.copy(checkpoint = counting.checkpoint),
        ).forEach { (case, weight) ->
            val (what, raw) = case
            assertEquals(what, listOf(weight, counting), AppStateJsonCodec.decodeVersion30(raw).measurements)
            assertThrows("version 27: $what", RuntimeException::class.java) { AppStateJsonCodec.decodeVersion27(claimedAs(raw, 27).toString()) }
            assertThrows("version 26: $what", RuntimeException::class.java) { AppStateJsonCodec.decodeVersion26(claimedAs(raw, 26).toString()) }
        }
    }

    /**
     * Version 25 reads as it did: a made measurement comes back with exactly what that version
     * could not say as its defaults, its reminder off at 09:00, no bank and still due on holiday,
     * and Weight with its dated rules as they were. What arrived in version 26 it refuses: a
     * checkpoint, a bank or an away rule on a made measurement, and the standard bank on Weight.
     * The payload is written out as version 25 wrote it.
     */
    @Test fun version25ReadsAMadeMeasurementWithExactlyItsDefaultsAndRefusesWhatArrivedInVersion26() {
        val editedWeight = """{"view":"measurement","id":"weight","label":"Morning weight","icon":"heart","hue":"plum","unit":"kg","decimals":1,""" +
            """"rewards":{"maxPasses":[{"value":7},{"value":3,"from":"2026-10-12"}],"earn":{"keptDaysInWeek":[{"value":6},{"value":4,"from":"2026-10-12"},{"value":5,"from":"2026-10-19"}]}},""" +
            """"whenAway":[{"value":"excused"},{"value":"due","from":"2026-10-13"}]}"""
        val stored = frozenVersion25(made = listOf(FrozenWaistOfVersion25, FrozenMoodOfVersion25), weight = editedWeight)

        val read = AppStateJsonCodec.decodeVersion25(stored).measurements

        val reminderOff = Checkpoint("day", CheckpointTime.Fixed(LocalTime.of(9, 0)), remind = false)
        assertEquals(
            listOf(
                changed,
                MeasurementConfiguration("msr_k2p9", "Waist", ViewIcon.Ruler, ViewHue.Plum, unit = "cm", step = MeasurementStep("0.1"), rewards = null, whenAway = Dated.always(WhenAway.Due), checkpoint = reminderOff),
                MeasurementConfiguration("msr_m7xd", "Mood", ViewIcon.Mood, ViewHue.Teal, unit = "", step = MeasurementStep("1"), rewards = null, whenAway = Dated.always(WhenAway.Due), checkpoint = reminderOff),
            ),
            read,
        )
        // Written by the current version it is the same state, now with the checkpoint said.
        assertEquals(read, AppStateJsonCodec.decodeVersion30(AppStateJsonCodec.encodeVersion30(AppStateJsonCodec.decodeVersion25(stored).withEating().withReading().withActivity())).measurements)

        fun made(change: (JSONObject) -> Unit) = JSONObject(stored).apply { change(getJSONArray("measurements").getJSONObject(1)) }.toString()
        fun weight(change: (JSONObject) -> Unit) = JSONObject(stored).apply { change(getJSONArray("measurements").getJSONObject(0)) }.toString()
        val standardBank = """{"maxPasses":3,"earn":{"everyKeptDays":7},"periods":[{"from":"2026-10-12"}]}"""
        listOf(
            "a checkpoint" to made { it.put("checkpoints", JSONArray("""[{"id":"day","at":"09:00","remind":false}]""")) },
            "no checkpoints, said so" to made { it.put("checkpoints", JSONArray()) },
            "the standard bank" to made { it.put("rewards", JSONObject(standardBank)) },
            "a bank that left" to made { it.put("rewards", JSONObject(standardBank).put("periods", JSONArray())) },
            "a bank without periods" to made { it.put("rewards", JSONObject(standardBank).apply { remove("periods") }) },
            "an away rule" to made { it.put("whenAway", "excused") },
            "still due, written out" to made { it.put("whenAway", "due") },
            "the standard bank on Weight" to weight { it.put("rewards", JSONObject(standardBank)) },
            "a checkpoint on Weight" to weight { it.put("checkpoints", JSONArray("""[{"id":"day","at":"09:00","remind":false}]""")) },
        ).forEach { (what, raw) -> assertThrows(what, RuntimeException::class.java) { AppStateJsonCodec.decodeVersion25(raw) } }
        // A version 26 payload is not version 25's, whatever it claims.
        val version26 = legacyTrackerJson(AppState(measurements = listOf(testWeightMeasurement(), testMadeMeasurement())).withEating().withReading().withActivity(), 26)
        assertEquals(false, version26.getJSONArray("measurements").getJSONObject(0).has("checkpoints"))
        assertEquals(listOf(defaultWeightCheckpoint(remind = false), DefaultMeasurementCheckpoint), AppStateJsonCodec.decodeVersion26(version26.toString()).measurements.map { it.checkpoint })
        assertThrows(RuntimeException::class.java) { AppStateJsonCodec.decodeVersion25(JSONObject(version26.toString()).put("schemaVersion", 25).toString()) }
        assertThrows(RuntimeException::class.java) { AppStateJsonCodec.decodeVersion26(stored) }
        assertThrows(RuntimeException::class.java) { AppStateJsonCodec.decodeVersion26(JSONObject(stored).put("schemaVersion", 26).toString()) }
    }

    /** A value that changed is its revisions, as everywhere; one that never did stays plain beside it. */
    @Test fun everyDatedFormSurvivesTheStateEncoding() {
        val state = AppState().withWeight(changed).withEating().withReading().withActivity()
        val encoded = AppStateJsonCodec.encodeVersion30(state)
        assertEquals(state, AppStateJsonCodec.decodeVersion30(encoded))
        assertEquals(state.readBefore27(), AppStateJsonCodec.decodeVersion25(legacyTrackerJson(state, 25).toString()))
        assertEquals(state.readBefore27(), AppStateJsonCodec.decodeVersion24(legacyTrackerJson(state, 24).toString()))
        assertEquals(state.readBefore27(), AppStateJsonCodec.decodeVersion23(legacyTrackerJson(state, 23).toString()))
        val stored = JSONObject(encoded).getJSONArray("measurements").getJSONObject(0)
        assertEquals(
            plain(JSONArray("""[{"value":7},{"value":3,"from":"2026-10-12"}]""")),
            plain(stored.getJSONObject("rewards").get("maxPasses")),
        )
        assertEquals(
            plain(JSONArray("""[{"value":6},{"value":4,"from":"2026-10-12"},{"value":5,"from":"2026-10-19"}]""")),
            plain(stored.getJSONObject("rewards").getJSONObject("earn").get("keptDaysInWeek")),
        )
        assertEquals(plain(JSONArray("""[{"value":"excused"},{"value":"due","from":"2026-10-13"}]""")), plain(stored.get("whenAway")))

        val onlyTheBank = AppState().withWeight(testWeightMeasurement().let { it.copy(rewards = MeasurementBank.Week(it.weekRewards!!.copy(maxPasses = changed.weekRewards!!.maxPasses))) }).withEating().withReading().withActivity()
        assertEquals(onlyTheBank, AppStateJsonCodec.decodeVersion30(AppStateJsonCodec.encodeVersion30(onlyTheBank)))
        val plainBesideDated = JSONObject(AppStateJsonCodec.encodeVersion30(onlyTheBank)).getJSONArray("measurements").getJSONObject(0)
        assertEquals(6, plainBesideDated.getJSONObject("rewards").getJSONObject("earn").get("keptDaysInWeek"))
        assertEquals("excused", plainBesideDated.get("whenAway"))
    }

    /**
     * Versions 23 and 24 hold exactly Weight's configuration, complete, and read it as strictly as
     * they did. Its bank and its away rule judge every day, so anything else makes the state
     * unreadable: nothing is ignored, and nothing is repaired by putting the default in. What
     * arrived in version 25 they refuse too: a unit, a step and a second measurement. Read by
     * them the configuration comes back with what it holds, and with the kilograms and the step
     * of 0.1 that were the code's.
     */
    @Test fun versions23And24RefuseAnythingButOneCompleteWeightConfigurationAndGainItsUnitAndStep() {
        val state = AppState().withWeight(changed).withEating().withReading().withActivity()
        val decoders: Map<Int, (String) -> AppState> = mapOf(24 to AppStateJsonCodec::decodeVersion24, 23 to AppStateJsonCodec::decodeVersion23)
        decoders.forEach { (version, decode) ->
            val valid = legacyTrackerJson(state, version)
            assertEquals("version $version", setOf("view", "id", "label", "icon", "hue", "rewards", "whenAway"), valid.getJSONArray("measurements").getJSONObject(0).keys().asSequence().toSet())
            val decoded = decode(valid.toString())
            assertEquals("version $version", state.readBefore27(), decoded)
            assertEquals("version $version", "kg" to MeasurementStep("0.1"), decoded.measurements.single().let { it.unit to it.step })

            fun root(change: (JSONObject) -> Unit) = JSONObject(valid.toString()).apply(change).toString()
            fun weight(change: (JSONObject) -> Unit) = root { change(it.getJSONArray("measurements").getJSONObject(0)) }
            fun revisions(raw: String) = JSONArray(raw)
            val made = """{"view":"measurement","id":"msr_k2p9","label":"Waist","icon":"ruler","hue":"plum","unit":"cm","decimals":1}"""
            val refused = listOf(
                "no field" to root { it.remove("measurements") },
                "no list" to root { it.put("measurements", JSONObject()) },
                "an empty list" to root { it.put("measurements", JSONArray()) },
                "no object" to root { it.put("measurements", JSONArray().put("weight")) },
                "a second entry" to root { it.getJSONArray("measurements").put(JSONObject(it.getJSONArray("measurements").getJSONObject(0).toString())) },
                "a second measurement" to root { it.getJSONArray("measurements").put(JSONObject(it.getJSONArray("measurements").getJSONObject(0).toString()).put("id", "msr_k2p9")) },
                "a measurement a person made" to root { it.getJSONArray("measurements").put(JSONObject(made)) },
                "a measurement a person made, without a unit or a step" to root { it.getJSONArray("measurements").put(JSONObject(made).apply { remove("unit"); remove("decimals") }) },
                "another ID" to weight { it.put("id", "msr_k2p9") },
                "another view" to weight { it.put("view", "checklist") },
                "a unit" to weight { it.put("unit", "kg") },
                "a step" to weight { it.put("decimals", 1) },
                "a unit and a step" to weight { it.put("unit", "kg").put("decimals", 1) },
                "a reminder" to weight { it.put("remind", true) },
                "reward periods" to weight { it.getJSONObject("rewards").put("periods", JSONArray()) },
                "no rewards" to weight { it.put("rewards", JSONObject.NULL) },
                "a checklist's way of earning" to weight { it.getJSONObject("rewards").put("earn", JSONObject().put("everyKeptDays", 7)) },
                "two ways of earning" to weight { it.getJSONObject("rewards").getJSONObject("earn").put("everyKeptDays", 7) },
                "no bank" to weight { it.getJSONObject("rewards").put("maxPasses", 0) },
                "no day to log" to weight { it.getJSONObject("rewards").getJSONObject("earn").put("keptDaysInWeek", 0) },
                "an eighth day" to weight { it.getJSONObject("rewards").getJSONObject("earn").put("keptDaysInWeek", 8) },
                "a number as text" to weight { it.getJSONObject("rewards").put("maxPasses", "7") },
                "half a token" to weight { it.getJSONObject("rewards").put("maxPasses", 2.5) },
                "an unknown icon" to weight { it.put("icon", "rocket") },
                "an unknown hue" to weight { it.put("hue", "turquoise") },
                "a blank name" to weight { it.put("label", " ") },
                "an unknown away rule" to weight { it.put("whenAway", "sometimes") },
                "a lowered target" to weight { it.put("whenAway", "lowerTarget") },
                "a lowered target from a day" to weight { it.put("whenAway", revisions("""[{"value":"excused"},{"value":"lowerTarget","from":"2026-10-13"}]""")) },
                "one revision" to weight { it.put("whenAway", revisions("""[{"value":"excused"}]""")) },
                "no revision" to weight { it.put("whenAway", revisions("[]")) },
                "a first revision with a start" to weight { it.put("whenAway", revisions("""[{"value":"excused","from":"2026-10-12"},{"value":"due","from":"2026-10-13"}]""")) },
                "a later revision without a start" to weight { it.put("whenAway", revisions("""[{"value":"excused"},{"value":"due"}]""")) },
                "revisions out of order" to weight { it.getJSONObject("rewards").put("maxPasses", revisions("""[{"value":7},{"value":3,"from":"2026-10-12"},{"value":4,"from":"2026-10-11"}]""")) },
                "a revision that changes nothing" to weight { it.getJSONObject("rewards").put("maxPasses", revisions("""[{"value":7},{"value":7,"from":"2026-10-12"}]""")) },
                "a revision on no day" to weight { it.getJSONObject("rewards").put("maxPasses", revisions("""[{"value":7},{"value":3,"from":"2026-02-30"}]""")) },
                "a revision with more in it" to weight { it.getJSONObject("rewards").put("maxPasses", revisions("""[{"value":7},{"value":3,"from":"2026-10-12","until":"2026-10-20"}]""")) },
                "a revision that is no object" to weight { it.getJSONObject("rewards").getJSONObject("earn").put("keptDaysInWeek", revisions("""[6,4]""")) },
            ) + listOf("view", "id", "label", "icon", "hue", "rewards", "whenAway").map { field -> "no $field" to weight { it.remove(field) } } +
                listOf("maxPasses", "earn").map { field -> "no $field" to weight { it.getJSONObject("rewards").remove(field) } } +
                ("no keptDaysInWeek" to weight { it.getJSONObject("rewards").getJSONObject("earn").remove("keptDaysInWeek") })
            refused.forEach { (what, raw) -> assertThrows("version $version: $what", RuntimeException::class.java) { decode(raw) } }
        }
        // What Weight's bank and away rule may say is refused in versions 25 and 26 as it always
        // was; periods on its bank arrived in version 30, which reads them where version 25 refuses them.
        val current = JSONObject(AppStateJsonCodec.encodeVersion30(state))
        fun currentWeight(change: (JSONObject) -> Unit) = JSONObject(current.toString()).apply { change(getJSONArray("measurements").getJSONObject(0)) }.toString()
        val withEmptyPeriods = currentWeight { it.getJSONObject("rewards").put("periods", JSONArray()) }
        assertEquals(emptyList<RewardPeriod>(), AppStateJsonCodec.decodeVersion30(withEmptyPeriods).measurements.single().weekRewards?.periods)
        assertThrows(withEmptyPeriods, RuntimeException::class.java) { AppStateJsonCodec.decodeVersion25(claimedAs(withEmptyPeriods, 25).toString()) }
        listOf(
            currentWeight { it.getJSONObject("rewards").put("maxPasses", 0) },
            currentWeight { it.getJSONObject("rewards").getJSONObject("earn").put("keptDaysInWeek", 8) },
            currentWeight { it.getJSONObject("rewards").put("earn", JSONObject().put("everyKeptDays", 7)) },
            currentWeight { it.put("whenAway", "lowerTarget") },
            currentWeight { it.put("whenAway", JSONArray("""[{"value":"excused"}]""")) },
            currentWeight { it.getJSONObject("rewards").put("maxPasses", JSONArray("""[{"value":7},{"value":7,"from":"2026-10-12"}]""")) },
        ).forEach { raw ->
            assertThrows(raw, RuntimeException::class.java) { AppStateJsonCodec.decodeVersion30(raw) }
            assertThrows(raw, RuntimeException::class.java) { AppStateJsonCodec.decodeVersion25(claimedAs(raw, 25).toString()) }
        }
    }

    /**
     * Version 22 and older held Weight's rules in the code: they read without a configuration,
     * which the repository then seeds, and refuse the field rather than drop it.
     */
    @Test fun version22AndOlderReadWithoutAConfigurationAndRefuseTheField() {
        val state = AppState(checklists = listOf(testSupplementsChecklist())).withWeight().withEating().withReading().withActivity()
        val decoders: Map<Int, (String) -> AppState> = mapOf(
            22 to AppStateJsonCodec::decodeVersion22,
            21 to AppStateJsonCodec::decodeVersion21,
            15 to AppStateJsonCodec::decodeVersion15,
            10 to AppStateJsonCodec::decodeVersion10,
            9 to AppStateJsonCodec::decodeVersion9,
            8 to AppStateJsonCodec::decodeVersion8,
            7 to AppStateJsonCodec::decodeVersion7,
            2 to AppStateJsonCodec::decodeVersion2,
            1 to AppStateJsonCodec::decodeLegacy,
        )
        decoders.forEach { (version, decode) ->
            val stored = legacyTrackerJson(state, version)
            assertEquals("version $version", false, stored.has("measurements"))
            assertEquals("version $version", emptyList<MeasurementConfiguration>(), decode(stored.toString()).measurements)
            val withTheField = JSONObject(stored.toString()).put("measurements", JSONArray().put(testWeightMeasurement().encode()))
            assertThrows("version $version", RuntimeException::class.java) { decode(withTheField.toString()) }
        }
        // A changed rule cannot be written in an older shape at all.
        assertThrows(RuntimeException::class.java) { legacyTrackerJson(AppState().withWeight(changed).withEating().withReading().withActivity(), 22) }
    }

    /** Scale, Ruler, Pulse and Mood joined the icons in version 23: a checklist may wear each from then on, and an older version refuses it there. */
    @Test fun theIconsOfVersion23AreRefusedByOlderVersionsForAChecklistToo() {
        val arrived = ViewIcon.entries.filter { it.introducedInFormat == 23 }
        assertEquals(listOf(ViewIcon.Scale, ViewIcon.Ruler, ViewIcon.Pulse, ViewIcon.Mood), arrived)
        arrived.forEach { icon ->
            val worn = AppState(checklists = listOf(testSupplementsChecklist().copy(icon = icon))).withWeight().withEating().withReading().withActivity()
            assertEquals(icon.storageId, worn, AppStateJsonCodec.decodeVersion30(AppStateJsonCodec.encodeVersion30(worn)))
            assertEquals(icon.storageId, worn.readBefore27(), AppStateJsonCodec.decodeVersion23(legacyTrackerJson(worn, 23).toString()))
            assertEquals(icon.storageId, JSONObject(AppStateJsonCodec.encodeVersion30(worn)).getJSONArray("checklists").getJSONObject(0).getString("icon"))
            assertThrows(icon.storageId, RuntimeException::class.java) { AppStateJsonCodec.decodeVersion22(worn.encodedAsVersion(22).toString()) }
            assertThrows(icon.storageId, RuntimeException::class.java) { AppStateJsonCodec.decodeVersion13(worn.encodedAsVersion(13).toString()) }
            assertThrows(icon.storageId, RuntimeException::class.java) { legacyTrackerJson(worn, 22) }
        }
        // The same payload with an icon version 22 knew is one it reads.
        val sunny = AppState(checklists = listOf(testSupplementsChecklist().copy(icon = ViewIcon.Sun))).withWeight().withEating().withReading().withActivity()
        assertEquals(sunny.checklists, AppStateJsonCodec.decodeVersion22(sunny.encodedAsVersion(22).toString()).checklists)
    }

    /** JSON as maps and lists, so two objects compare by content and not by key order. */
    private fun plain(value: Any?): Any? = when (value) {
        is JSONObject -> value.keys().asSequence().associateWith { plain(value.get(it)) }
        is JSONArray -> (0 until value.length()).map { plain(value.get(it)) }
        else -> value
    }
}

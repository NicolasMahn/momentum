package com.example.workouttracker.persistence.json

import com.example.workouttracker.persistence.readBefore23
import com.example.workouttracker.persistence.claimedAs
import com.example.workouttracker.domain.measurement.withWeight
import com.example.workouttracker.domain.AppState
import com.example.workouttracker.domain.checklist.CheckpointTime
import com.example.workouttracker.domain.checklist.Dated
import com.example.workouttracker.domain.checklist.DefaultChecklistRewards
import com.example.workouttracker.domain.checklist.RewardPeriod
import com.example.workouttracker.domain.checklist.WhenAway
import com.example.workouttracker.domain.substance.OwnSubstance
import com.example.workouttracker.domain.substance.SubstanceAmountUnit
import com.example.workouttracker.domain.substance.SubstanceCheckIn
import com.example.workouttracker.domain.substance.SubstanceEdit
import com.example.workouttracker.domain.substance.SubstanceGoal
import com.example.workouttracker.domain.substance.SubstanceKind
import com.example.workouttracker.domain.substance.SubstanceLog
import com.example.workouttracker.domain.substance.SubstanceNoneDay
import com.example.workouttracker.domain.substance.SubstanceSettings
import com.example.workouttracker.persistence.legacyTrackerJson
import java.time.LocalDate
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import com.example.workouttracker.domain.questions.withEating
import com.example.workouttracker.domain.time.withReading
import com.example.workouttracker.domain.activity.withActivity

/**
 * Tracker state 19 stores which substances are tracked and the person's own, 20 the check-in and
 * the streak, 21 the goal, exceptions only and the stance as its revisions; every version before
 * 19 had the three fixed and reads as those three tracked.
 */
class SubstanceJsonTest {
    private val own = OwnSubstance("sub_k2p9", "Kratom", SubstanceAmountUnit.GRAMS)
    private val chosen = SubstanceSettings(tracked = setOf(own.id, SubstanceKind.CANNABIS.id, SubstanceKind.COFFEE.id), own = listOf(own))
    private val day = LocalDate.parse("2026-09-14")

    @Test
    fun theChoiceRoundTripsAndIsWrittenInCatalogueOrderWhateverOrderItWasChosenIn() {
        val state = AppState(
            substances = chosen,
            substanceLogs = listOf(
                SubstanceLog("k1", own.id, "2026-09-16", 100L, 1.5, SubstanceAmountUnit.GRAMS),
                SubstanceLog("c1", SubstanceKind.COFFEE.id, "2026-09-16", 90L, 2.0, SubstanceAmountUnit.CUPS),
            ),
        ).withWeight().withEating().withReading().withActivity()

        val encoded = AppStateJsonCodec.encodeVersion30(state)

        assertEquals(state, AppStateJsonCodec.decodeVersion30(encoded))
        val substances = JSONObject(encoded).getJSONObject("substances")
        assertEquals(listOf("cannabis", "coffee", "sub_k2p9"), substances.getJSONArray("tracked").strings())
        val stored = substances.getJSONArray("own").getJSONObject(0)
        assertEquals(1, substances.getJSONArray("own").length())
        assertEquals(listOf("sub_k2p9", "Kratom", "grams"), listOf(stored.getString("id"), stored.getString("label"), stored.getString("unit")))
        // A record still names its substance under the field the three always had.
        assertEquals("sub_k2p9", JSONObject(encoded).getJSONArray("substanceLogs").getJSONObject(0).getString("kind"))
    }

    @Test
    fun version18AndOlderReadAsTheThreeTrackedAndRefuseTheNewShape() {
        val three = AppState(
            substances = SubstanceSettings.Legacy,
            substanceLogs = listOf(
                SubstanceLog("c1", SubstanceKind.CANNABIS.id, "2026-09-16", 100L, 0.5, SubstanceAmountUnit.GRAMS),
                SubstanceLog("a1", SubstanceKind.ALCOHOL.id, "2026-09-15", 90L),
                SubstanceLog("t1", SubstanceKind.TOBACCO.id, "2026-09-14", 80L, 3.0, SubstanceAmountUnit.USES),
            ),
        ).withWeight().withEating().withReading().withActivity()
        listOf(18, 17, 10, 8, 7, 6, 5).forEach { version ->
            val json = legacyTrackerJson(three, version)
            assertEquals("version $version", three.readBefore23(), decode(version, json.toString()))
            assertEquals("version $version", false, json.has("substances"))
        }
        // Before version 5 there were no substance records; the three were still the ones tracked.
        assertEquals(SubstanceSettings.Legacy, AppStateJsonCodec.decodeVersion2(legacyTrackerJson(AppState(), 2).toString()).substances)
        assertEquals(SubstanceSettings.Legacy, AppStateJsonCodec.decodeLegacy(legacyTrackerJson(AppState(), 1).toString()).substances)

        val newShapeAs18 = claimedAs(AppStateJsonCodec.encodeVersion30(three), 18).toString()
        assertThrows(RuntimeException::class.java) { AppStateJsonCodec.decodeVersion18(newShapeAs18) }
        assertThrows(RuntimeException::class.java) { legacyTrackerJson(AppState(substances = chosen), 18) }
        assertThrows(RuntimeException::class.java) { legacyTrackerJson(AppState(substanceLogs = listOf(SubstanceLog("k", SubstanceKind.COFFEE.id, "2026-09-16", 1L))), 18) }
    }

    /** Read strictly, as a checklist is: what this version would not write back unchanged is refused. */
    @Test
    fun theChoiceIsReadStrictly() {
        val valid = JSONObject(AppStateJsonCodec.encodeVersion30(AppState(substances = chosen).withWeight().withEating().withReading().withActivity()))
        fun changed(change: (JSONObject) -> Unit) = JSONObject(valid.toString()).apply { change(getJSONObject("substances")) }.toString()
        listOf(
            changed { it.put("limits", JSONObject()) },
            changed { it.remove("own") },
            changed { it.put("tracked", JSONArray().put("sub_k2p9").put("sub_k2p9")) },
            changed { it.put("tracked", JSONArray().put("sub_zzzz")) },
            changed { it.put("tracked", JSONArray().put(7)) },
            changed { it.getJSONArray("own").getJSONObject(0).put("unit", "litres") },
            changed { it.getJSONArray("own").getJSONObject(0).put("label", "") },
            changed { it.getJSONArray("own").getJSONObject(0).put("id", "coffee") },
            changed { it.getJSONArray("own").getJSONObject(0).put("id", "sub_k2p9 ") },
            changed { it.getJSONArray("own").getJSONObject(0).put("id", "sub k2p9") },
            changed { it.getJSONArray("own").getJSONObject(0).put("hue", "plum") },
            changed { it.getJSONArray("own").put(JSONObject(it.getJSONArray("own").getJSONObject(0).toString())) },
            JSONObject(valid.toString()).apply { remove("substances") }.toString(),
            JSONObject(valid.toString()).put("substances", JSONArray()).toString(),
        ).forEach { raw -> assertThrows(raw, RuntimeException::class.java) { AppStateJsonCodec.decodeVersion30(raw) } }
    }

    /**
     * Every version decodes exactly what it decoded before this block: 18 and older read the field
     * trimmed and know the three, so a padded kind still loads as it did; 19 reads the ID as written
     * and leaves a padded one out as lost.
     */
    @Test
    fun everyVersionBefore19ReadsAPaddedKindAsItAlwaysDidAndVersion19Exactly() {
        val record = SubstanceLog("c1", SubstanceKind.CANNABIS.id, "2026-09-16", 100L, 0.5, SubstanceAmountUnit.GRAMS)
        fun padded(json: JSONObject) = json.apply { getJSONArray("substanceLogs").getJSONObject(0).put("kind", " cannabis ") }.toString()

        (5..18).forEach { version ->
            val raw = padded(legacyTrackerJson(AppState(substances = SubstanceSettings.Legacy, substanceLogs = listOf(record)), version))
            assertEquals("version $version", listOf(record), decode(version, raw).substanceLogs)
        }
        val exact = padded(JSONObject(AppStateJsonCodec.encodeVersion30(AppState(substanceLogs = listOf(record)).withWeight().withEating().withReading().withActivity())))
        val decoded = AppStateJsonCodec.decodeVersion30(exact)
        assertEquals(emptyList<SubstanceLog>(), decoded.substanceLogs)
        assertEquals(true, AppStateJsonCodec.lostRecords(exact, decoded))
    }

    /** A record left out beside a kept one with the same ID, or two kept ones merged, is lost: the rule comes from the decode itself. */
    @Test
    fun aRecordIsLostTheMomentItIsLeftOutOrMergedWhateverItsId() {
        fun raw(vararg entries: String) = JSONObject(AppStateJsonCodec.encodeVersion30(AppState().withWeight().withEating().withReading().withActivity())).put("substanceLogs", JSONArray(entries.map(::JSONObject))).toString()
        val kept = """{"id":"same","kind":"cannabis","dateKey":"2026-09-16","timestampMillis":100}"""
        val unknownSameId = """{"id":"same","kind":"sub_gone","dateKey":"2026-09-16","timestampMillis":200}"""
        val duplicate = """{"id":"same","kind":"cannabis","dateKey":"2026-09-16","timestampMillis":300}"""
        val other = """{"id":"other","kind":"alcohol","dateKey":"2026-09-15","timestampMillis":50}"""

        listOf(raw(kept, unknownSameId), raw(unknownSameId, kept), raw(kept, duplicate), raw(kept, other, "{}")).forEach { payload ->
            assertEquals(payload, true, AppStateJsonCodec.lostRecords(payload, AppStateJsonCodec.decodeVersion30(payload)))
        }
        val whole = raw(kept, other)
        assertEquals(false, AppStateJsonCodec.lostRecords(whole, AppStateJsonCodec.decodeVersion30(whole)))
        assertEquals(listOf("same"), AppStateJsonCodec.decodeVersion30(raw(kept, unknownSameId)).substanceLogs.map(SubstanceLog::id))
    }

    /** The page's baseline and its edits round trip without a version, every edit included. */
    @Test
    fun thePagesDraftRoundTripsWithoutAVersion() {
        listOf(chosen, SubstanceSettings(), SubstanceSettings.Legacy, datedChoice()).forEach { settings ->
            assertEquals(settings, AppStateJsonCodec.decodeSubstances(AppStateJsonCodec.encodeSubstances(settings)))
        }
        val edits = listOf(
            SubstanceEdit.Track(SubstanceKind.COFFEE.id, true),
            SubstanceEdit.Track(SubstanceKind.CANNABIS.id, false),
            SubstanceEdit.StayAway(SubstanceKind.COFFEE.id, true),
            SubstanceEdit.AddOwn("sub_k2p9", "Kratom", SubstanceAmountUnit.GRAMS),
            SubstanceEdit.RenameOwn("sub_k2p9", "Mitragyna"),
            SubstanceEdit.RemoveOwn("sub_k2p9"),
            SubstanceEdit.Remind(true),
            SubstanceEdit.CheckInAt(CheckpointTime.BeforeSleep(30)),
            SubstanceEdit.SetGoal(SubstanceGoal.StayingAway),
            SubstanceEdit.SetGoal(null),
            SubstanceEdit.SetExceptionsOnly(false),
            SubstanceEdit.SetRewardRules(5, 10),
            SubstanceEdit.SetWhenAway(WhenAway.Excused),
        )
        assertEquals(edits, AppStateJsonCodec.decodeSubstanceEdits(AppStateJsonCodec.encodeSubstanceEdits(edits)))
        assertThrows(RuntimeException::class.java) { AppStateJsonCodec.decodeSubstanceEdits("""[{"edit":"forget"}]""") }
    }

    /** Version 21 stores the days that said "none", the stance, the check-in and the streak as version 20 did; version 19 reads none of it. */
    @Test
    fun noneDaysTheStanceTheCheckInAndTheStreakRoundTrip() {
        val state = AppState(
            substances = chosen.copy(
                stayingAway = Dated.always(setOf(own.id)),
                checkIn = SubstanceCheckIn(remind = true, at = CheckpointTime.BeforeSleep(30)),
                rewards = DefaultChecklistRewards.copy(periods = listOf(RewardPeriod(LocalDate.parse("2026-09-10"), LocalDate.parse("2026-09-12")), RewardPeriod(day))),
                whenAway = Dated(listOf(Dated.Revision(WhenAway.Due), Dated.Revision(WhenAway.Excused, LocalDate.parse("2026-09-15")))),
            ),
            substanceLogs = listOf(SubstanceLog("k1", own.id, "2026-09-16", 100L, 1.5, SubstanceAmountUnit.GRAMS)),
            substanceNoneDays = listOf(SubstanceNoneDay("2026-09-15", 90L), SubstanceNoneDay("2026-09-14", 80L)),
        ).withWeight().withEating().withReading().withActivity()

        val encoded = AppStateJsonCodec.encodeVersion30(state)

        assertEquals(state, AppStateJsonCodec.decodeVersion30(encoded))
        val substances = JSONObject(encoded).getJSONObject("substances")
        assertEquals(listOf("sub_k2p9"), substances.getJSONArray("stayingAway").getJSONObject(0).getJSONArray("ids").strings())
        assertEquals("sleep-30", substances.getJSONObject("checkIn").getString("at"))
        assertEquals(JSONObject.NULL, JSONObject(AppStateJsonCodec.encodeVersion30(AppState().withWeight().withEating().withReading().withActivity())).getJSONObject("substances").get("rewards"))
        assertEquals("2026-09-15", JSONObject(encoded).getJSONArray("substanceNoneDays").getJSONObject(0).getString("dateKey"))
        assertFalse(AppStateJsonCodec.lostRecords(encoded, state))
    }

    /**
     * Version 21 stores the goal and exceptions only through the shared dated codec, and the stance
     * as its own revisions: `{ "ids": [...] }`, later ones with `from`, the IDs in catalogue order
     * with the person's own after.
     */
    @Test
    fun theGoalExceptionsOnlyAndTheStanceRoundTripDatedInVersion21() {
        val state = AppState(substances = datedChoice()).withWeight().withEating().withReading().withActivity()

        val encoded = AppStateJsonCodec.encodeVersion30(state)

        assertEquals(state, AppStateJsonCodec.decodeVersion30(encoded))
        val substances = JSONObject(encoded).getJSONObject("substances")
        val stance = substances.getJSONArray("stayingAway")
        assertEquals(2, stance.length())
        assertFalse(stance.getJSONObject(0).has("from"))
        assertEquals(listOf("cannabis"), stance.getJSONObject(0).getJSONArray("ids").strings())
        assertEquals("2026-09-14", stance.getJSONObject(1).getString("from"))
        assertEquals(listOf("cannabis", "sub_k2p9"), stance.getJSONObject(1).getJSONArray("ids").strings())
        assertEquals("logging", substances.getJSONArray("goal").getJSONObject(0).getString("value"))
        assertEquals("stayingAway", substances.getJSONArray("goal").getJSONObject(1).getString("value"))
        assertEquals("2026-09-14", substances.getJSONArray("goal").getJSONObject(1).getString("from"))
        assertEquals(false, substances.getJSONArray("exceptionsOnly").getJSONObject(1).getBoolean("value"))
        assertEquals("2026-09-15", substances.getJSONArray("exceptionsOnly").getJSONObject(1).getString("from"))
    }

    /** The dated set is always an array of revision objects: one for a set that never changed, `{ "ids": [] }` for an empty one, never a plain value. */
    @Test
    fun aStanceThatNeverChangedAndAnEmptyStanceAreOneRevisionEach() {
        val unchanged = JSONObject(AppStateJsonCodec.encodeVersion30(AppState(substances = SubstanceSettings.Legacy).withWeight().withEating().withReading().withActivity())).getJSONObject("substances")
        assertEquals("""[{"ids":["alcohol","tobacco","cannabis"]}]""", unchanged.getJSONArray("stayingAway").toString())
        assertEquals("logging", unchanged.getString("goal"))
        assertEquals(true, unchanged.getBoolean("exceptionsOnly"))

        val empty = JSONObject(AppStateJsonCodec.encodeVersion30(AppState().withWeight().withEating().withReading().withActivity())).getJSONObject("substances")
        assertEquals("""[{"ids":[]}]""", empty.getJSONArray("stayingAway").toString())
        assertEquals(SubstanceSettings(), AppStateJsonCodec.decodeVersion30(AppStateJsonCodec.encodeVersion30(AppState().withWeight().withEating().withReading().withActivity())).substances)
    }

    /**
     * Version 20 reads as the logging goal, exceptions only and the stance as stored, one revision
     * from the beginning; it refuses the new fields and the revision shape, and a dated stance or
     * another goal cannot be written in its shape.
     */
    @Test
    fun version20ReadsAsTheLoggingGoalAndRefusesWhatVersion21Added() {
        val twenty = AppState(
            substances = chosen.copy(
                stayingAway = Dated.always(setOf(own.id)),
                checkIn = SubstanceCheckIn(remind = true),
                rewards = DefaultChecklistRewards.copy(periods = listOf(RewardPeriod(day))),
            ),
            substanceNoneDays = listOf(SubstanceNoneDay("2026-09-15", 5L)),
        ).withWeight().withEating().withReading().withActivity()
        val json = legacyTrackerJson(twenty, 20)
        assertEquals(twenty.readBefore23(), AppStateJsonCodec.decodeVersion20(json.toString()))
        assertEquals(listOf("sub_k2p9"), json.getJSONObject("substances").getJSONArray("stayingAway").strings())
        assertFalse(json.getJSONObject("substances").has("goal"))
        assertFalse(json.getJSONObject("substances").has("exceptionsOnly"))

        val twentyOne = JSONObject(AppStateJsonCodec.encodeVersion30(twenty.copy(substances = twenty.substances.copy(goal = Dated.always(SubstanceGoal.StayingAway), exceptionsOnly = Dated.always(false)))))
        assertThrows(RuntimeException::class.java) { AppStateJsonCodec.decodeVersion20(claimedAs(twentyOne.toString(), 20).toString()) }
        listOf("goal", "exceptionsOnly", "stayingAway").forEach { field ->
            val raw = legacyTrackerJson(twenty, 20).apply { getJSONObject("substances").put(field, twentyOne.getJSONObject("substances").get(field)) }
            assertThrows(field, RuntimeException::class.java) { AppStateJsonCodec.decodeVersion20(raw.toString()) }
        }
        assertThrows(RuntimeException::class.java) { legacyTrackerJson(twenty.copy(substances = twenty.substances.copy(goal = Dated.always(SubstanceGoal.StayingAway))), 20) }
        assertThrows(RuntimeException::class.java) { legacyTrackerJson(twenty.copy(substances = twenty.substances.copy(exceptionsOnly = Dated.always(false))), 20) }
        assertThrows(RuntimeException::class.java) { legacyTrackerJson(AppState(substances = datedChoice()), 20) }
    }

    @Test
    fun version19ReadsEveryTrackedSubstanceAsStayedAwayFromAndRefusesWhatVersion20Added() {
        val nineteen = AppState(
            substances = chosen.copy(stayingAway = Dated.always(chosen.tracked)),
            substanceLogs = listOf(SubstanceLog("k1", own.id, "2026-09-16", 100L, 1.5, SubstanceAmountUnit.GRAMS)),
        ).withWeight().withEating().withReading().withActivity()
        val json = legacyTrackerJson(nineteen, 19)
        assertEquals(nineteen.readBefore23(), AppStateJsonCodec.decodeVersion19(json.toString()))
        assertFalse(json.getJSONObject("substances").has("stayingAway"))
        assertFalse(json.has("substanceNoneDays"))

        val twenty = legacyTrackerJson(nineteen.copy(substances = nineteen.substances.copy(stayingAway = Dated.always(emptySet()), checkIn = SubstanceCheckIn(remind = true), rewards = DefaultChecklistRewards.copy(periods = listOf(RewardPeriod(day)))), substanceNoneDays = listOf(SubstanceNoneDay("2026-09-15", 5L))), 20)
        assertThrows(RuntimeException::class.java) { AppStateJsonCodec.decodeVersion19(JSONObject(twenty.toString()).put("schemaVersion", 19).toString()) }
        listOf("stayingAway", "checkIn", "rewards", "whenAway").forEach { field ->
            val raw = legacyTrackerJson(nineteen, 19).apply { getJSONObject("substances").put(field, twenty.getJSONObject("substances").get(field)) }
            assertThrows(field, RuntimeException::class.java) { AppStateJsonCodec.decodeVersion19(raw.toString()) }
        }
        assertThrows(RuntimeException::class.java) { AppStateJsonCodec.decodeVersion19(legacyTrackerJson(nineteen, 19).put("substanceNoneDays", twenty.getJSONArray("substanceNoneDays")).toString()) }
        assertThrows(RuntimeException::class.java) { legacyTrackerJson(AppState(substanceNoneDays = listOf(SubstanceNoneDay("2026-09-15", 5L))), 19) }
        assertThrows(RuntimeException::class.java) { legacyTrackerJson(AppState(substances = SubstanceSettings.Legacy.copy(stayingAway = Dated.always(emptySet()))), 19) }
    }

    @Test
    fun theVersion20And21FieldsAreReadStrictly() {
        val valid = JSONObject(AppStateJsonCodec.encodeVersion30(AppState(substances = chosen.copy(stayingAway = Dated.always(setOf(own.id)))).withWeight().withEating().withReading().withActivity()))
        fun changed(change: (JSONObject) -> Unit) = JSONObject(valid.toString()).apply { change(getJSONObject("substances")) }.toString()
        fun stance(raw: String) = changed { it.put("stayingAway", JSONArray(raw)) }
        listOf(
            changed { it.remove("stayingAway") },
            // The flat set of version 20, and every revision shape this version would not write back.
            stance("""["sub_k2p9"]"""),
            stance("""[]"""),
            stance("""[{"ids":["tobacco"]}]"""),
            stance("""[{"ids":["sub_k2p9","sub_k2p9"]}]"""),
            stance("""[{"ids":["sub_k2p9"]},{"ids":["cannabis"]}]"""),
            stance("""[{"from":"2026-09-14","ids":["sub_k2p9"]}]"""),
            stance("""[{"ids":[]},{"from":"2026-09-14","ids":[]}]"""),
            stance("""[{"ids":[]},{"from":"2026-09-14","ids":["sub_k2p9"]},{"from":"2026-09-14","ids":["cannabis"]}]"""),
            stance("""[{"ids":["sub_k2p9"],"value":["sub_k2p9"]}]"""),
            stance("""[{"ids":["sub_k2p9"],"from":"2026-9-14"}]"""),
            changed { it.remove("goal") },
            changed { it.put("goal", "sometimes") },
            changed { it.put("goal", JSONArray().put(JSONObject().put("value", "logging"))) },
            changed { it.remove("exceptionsOnly") },
            changed { it.put("exceptionsOnly", "yes") },
            changed { it.getJSONObject("checkIn").put("snooze", 5) },
            changed { it.getJSONObject("checkIn").put("at", "25:00") },
            changed { it.getJSONObject("checkIn").put("remind", "yes") },
            changed { it.put("whenAway", "lowerTarget") },
            changed { it.put("whenAway", "sometimes") },
            changed { it.put("rewards", JSONObject().put("maxPasses", 3)) },
            // A bank without dated periods would be judged from the first record and rewritten with none on leaving.
            changed { it.put("rewards", JSONObject().put("maxPasses", 3).put("earn", JSONObject().put("everyKeptDays", 7))) },
            JSONObject(valid.toString()).put("substanceNoneDays", JSONObject()).toString(),
            JSONObject(valid.toString()).apply { remove("substanceNoneDays") }.toString(),
        ).forEach { raw -> assertThrows(raw, RuntimeException::class.java) { AppStateJsonCodec.decodeVersion30(raw) } }
    }

    /** A "none" beside a use of the same day, or two for one day, is damage: left out or merged, and reported, so its bytes are kept. */
    @Test
    fun aNoneDayBesideAUseOrSaidTwiceIsLostNotKept() {
        fun raw(logs: String, nones: String) = JSONObject(AppStateJsonCodec.encodeVersion30(AppState().withWeight().withEating().withReading().withActivity())).put("substanceLogs", JSONArray(logs)).put("substanceNoneDays", JSONArray(nones)).toString()
        val use = """[{"id":"c1","kind":"cannabis","dateKey":"2026-09-16","timestampMillis":100}]"""
        val sameDay = """[{"dateKey":"2026-09-16","timestampMillis":200}]"""
        val otherDay = """[{"dateKey":"2026-09-15","timestampMillis":200}]"""
        val twice = """[{"dateKey":"2026-09-15","timestampMillis":200},{"dateKey":"2026-09-15","timestampMillis":300}]"""
        val invalid = """[{"dateKey":"2026-9-15","timestampMillis":200}]"""

        val beside = raw(use, sameDay)
        assertEquals(emptyList<SubstanceNoneDay>(), AppStateJsonCodec.decodeVersion30(beside).substanceNoneDays)
        assertTrue(AppStateJsonCodec.lostRecords(beside, AppStateJsonCodec.decodeVersion30(beside)))

        val apart = raw(use, otherDay)
        assertEquals(listOf(SubstanceNoneDay("2026-09-15", 200L)), AppStateJsonCodec.decodeVersion30(apart).substanceNoneDays)
        assertFalse(AppStateJsonCodec.lostRecords(apart, AppStateJsonCodec.decodeVersion30(apart)))

        val merged = raw("[]", twice)
        assertEquals(listOf(SubstanceNoneDay("2026-09-15", 300L)), AppStateJsonCodec.decodeVersion30(merged).substanceNoneDays)
        assertTrue(AppStateJsonCodec.lostRecords(merged, AppStateJsonCodec.decodeVersion30(merged)))
        assertTrue(AppStateJsonCodec.lostRecords(raw("[]", invalid), AppStateJsonCodec.decodeVersion30(raw("[]", invalid))))
    }

    /** Staying away since the 14th, from cannabis and then Kratom too, with the check-in rule from the 15th. */
    private fun datedChoice() = chosen.copy(
        stayingAway = Dated(listOf(Dated.Revision(setOf(SubstanceKind.CANNABIS.id)), Dated.Revision(setOf(SubstanceKind.CANNABIS.id, own.id), day))),
        rewards = DefaultChecklistRewards.copy(periods = listOf(RewardPeriod(day.minusDays(7)))),
        goal = Dated(listOf(Dated.Revision(SubstanceGoal.Logging), Dated.Revision(SubstanceGoal.StayingAway, day))),
        exceptionsOnly = Dated(listOf(Dated.Revision(true), Dated.Revision(false, day.plusDays(1)))),
    )

    private fun JSONArray.strings(): List<String> = (0 until length()).map { getString(it) }

    private fun decode(version: Int, raw: String): AppState = when (version) {
        20 -> AppStateJsonCodec.decodeVersion20(raw)
        19 -> AppStateJsonCodec.decodeVersion19(raw)
        18 -> AppStateJsonCodec.decodeVersion18(raw)
        17 -> AppStateJsonCodec.decodeVersion17(raw)
        16 -> AppStateJsonCodec.decodeVersion16(raw)
        15 -> AppStateJsonCodec.decodeVersion15(raw)
        14 -> AppStateJsonCodec.decodeVersion14(raw)
        13 -> AppStateJsonCodec.decodeVersion13(raw)
        12 -> AppStateJsonCodec.decodeVersion12(raw)
        11 -> AppStateJsonCodec.decodeVersion11(raw)
        10 -> AppStateJsonCodec.decodeVersion10(raw)
        9 -> AppStateJsonCodec.decodeVersion9(raw)
        8 -> AppStateJsonCodec.decodeVersion8(raw)
        7 -> AppStateJsonCodec.decodeVersion7(raw)
        6 -> AppStateJsonCodec.decodeVersion6(raw)
        5 -> AppStateJsonCodec.decodeVersion5(raw)
        else -> error("Substance records arrived in version 5")
    }
}

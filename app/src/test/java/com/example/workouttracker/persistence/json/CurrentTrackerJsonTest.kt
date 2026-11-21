package com.example.workouttracker.persistence.json

import com.example.workouttracker.persistence.storedDocument
import com.example.workouttracker.domain.AppState
import com.example.workouttracker.domain.activity.ActivityEntry
import com.example.workouttracker.domain.activity.testActivityConfiguration
import com.example.workouttracker.domain.checklist.AboutDay
import com.example.workouttracker.domain.checklist.ChecklistConfiguration
import com.example.workouttracker.domain.checklist.ChecklistDay
import com.example.workouttracker.domain.checklist.ChecklistItem
import com.example.workouttracker.domain.checklist.ChecklistRewards
import com.example.workouttracker.domain.checklist.Checkpoint
import com.example.workouttracker.domain.checklist.CheckpointTime
import com.example.workouttracker.domain.checklist.Dated
import com.example.workouttracker.domain.checklist.MoMotif
import com.example.workouttracker.domain.checklist.RewardPeriod
import com.example.workouttracker.domain.checklist.ViewHue
import com.example.workouttracker.domain.checklist.ViewIcon
import com.example.workouttracker.domain.measurement.MeasurementBank
import com.example.workouttracker.domain.measurement.MeasurementLog
import com.example.workouttracker.domain.measurement.MeasurementRewards
import com.example.workouttracker.domain.measurement.MeasurementValuePolicy
import com.example.workouttracker.domain.measurement.testMadeMeasurement
import com.example.workouttracker.domain.mo.TrackerMoPolicy
import com.example.workouttracker.domain.questions.Answer
import com.example.workouttracker.domain.questions.Question
import com.example.workouttracker.domain.questions.QuestionsEdit
import com.example.workouttracker.domain.questions.applyEdit
import com.example.workouttracker.persistence.CurrentTrackerStore
import com.example.workouttracker.persistence.trackerRepository
import com.example.workouttracker.domain.questions.QuestionEntry
import com.example.workouttracker.domain.questions.testMadeQuestions
import com.example.workouttracker.domain.substance.OwnSubstance
import com.example.workouttracker.domain.substance.SubstanceAmountUnit
import com.example.workouttracker.domain.substance.SubstanceLog
import com.example.workouttracker.domain.substance.SubstanceNoneDay
import com.example.workouttracker.domain.substance.SubstanceSettings
import com.example.workouttracker.domain.time.TimeLog
import com.example.workouttracker.domain.time.TimeRewards
import com.example.workouttracker.domain.time.testMadeTime
import java.time.LocalDate
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class CurrentTrackerJsonTest {
    private val mo = TrackerMoPolicy(enabled = true, motif = MoMotif.Book)
    private val checklist = ChecklistConfiguration(
        id = "ordinary-checklist", label = "Plants 🌱", icon = ViewIcon.Plant, hue = ViewHue.Forest,
        checkpoints = listOf(Checkpoint("morning", CheckpointTime.AfterWake(5), remind = false)),
        items = listOf(ChecklistItem("water", "Water")),
        about = Dated(listOf(Dated.Revision(AboutDay.Today), Dated.Revision(AboutDay.Yesterday, LocalDate.parse("2026-10-20")))),
        mo = mo,
    )

    @Test fun everyCurrentCapabilityAndRecordRoundTripsWithoutDependingOnItsId() {
        val standard = ChecklistRewards(3, 7, listOf(RewardPeriod(LocalDate.parse("2026-10-01"))))
        val finite = testMadeMeasurement(id = "weight", unit = "°C").copy(valuePolicy = MeasurementValuePolicy.Finite, rewards = MeasurementBank.Standard(standard), mo = mo)
        val positive = testMadeMeasurement(id = "sensor-positive").copy(valuePolicy = MeasurementValuePolicy.Positive, rewards = MeasurementBank.Week(MeasurementRewards(2, 3)), mo = mo)
        val configured = testMadeQuestions(id = "eating").copy(madeOn = null, about = Dated.always(AboutDay.Today), mo = mo)
        val wellbeing = testMadeQuestions(id = "wellbeing", questions = listOf(Question("feeling", "How do you feel?", listOf(Answer("low", "Less"), Answer("high", "More")), "Include the whole day."))).copy(mo = mo)
        val timer = testMadeTime(id = "ordinary-timer", rewards = TimeRewards(3)).copy(mo = mo)
        val own = OwnSubstance("custom-herb", "Herb", SubstanceAmountUnit.GRAMS)
        val state = AppState(
            activity = testActivityConfiguration().copy(mo = mo),
            activityEntries = listOf(ActivityEntry("entry", "unlisted", "  Bouldering  ", "2026-10-20", 5L, notes = "  Nice!  ")),
            checklists = listOf(checklist),
            checklistDays = listOf(ChecklistDay(checklist.id, "2026-10-20", 4L, setOf("morning/water")), ChecklistDay("absent-config", "2026-10-21", 3L, emptySet(), setOf("unknown/item"))),
            measurements = listOf(finite, positive),
            measurementLogs = listOf(MeasurementLog(finite.id, "2026-10-20", 3L, -2.5), MeasurementLog(positive.id, "2026-10-21", 2L, 0.0), MeasurementLog("absent-config", "2026-10-19", 1L, 20.0)),
            questions = listOf(configured, wellbeing),
            questionEntries = listOf(QuestionEntry("q1", configured.id, "2026-10-20", 3L, mapOf("qn_rest" to "a1"), "  Note  "), QuestionEntry("q2", wellbeing.id, "2026-10-21", 2L, mapOf("feeling" to "high"))),
            time = listOf(timer),
            timeLogs = listOf(TimeLog(timer.id, "2026-10-20", 3L), TimeLog("absent-config", "2026-10-21", 2L, 25)),
            substances = SubstanceSettings(own = listOf(own), tracked = setOf(own.id), mo = mo, label = "Evenings", icon = ViewIcon.Drink, hue = ViewHue.Berry),
            substanceLogs = listOf(SubstanceLog("use1", own.id, "2026-10-20", 2L, 0.5, SubstanceAmountUnit.GRAMS)),
            substanceNoneDays = listOf(SubstanceNoneDay("2026-10-21", 1L)),
        )
        val encoded = AppStateJsonCodec.encode(state)
        assertEquals(state, AppStateJsonCodec.decodeVersion32(encoded))
        val root = JSONObject(encoded)
        assertEquals(32, root.getInt("schemaVersion"))
        val measurement = root.getJSONArray("measurements").getJSONObject(0)
        assertEquals("finite", measurement.getString("valuePolicy"))
        assertEquals(MoMotif.Book.storageId, measurement.getJSONObject("mo").getString("motif"))
        assertFalse(measurement.has("motif"))
        assertFalse(root.getJSONArray("questions").getJSONObject(0).has("source"))
        val storedPrompt = root.getJSONArray("questions").getJSONObject(1).getJSONArray("questions").getJSONObject(0)
        assertEquals("How do you feel?", storedPrompt.getString("text"))
        assertEquals("Include the whole day.", storedPrompt.getString("help"))
        assertEquals("More", storedPrompt.getJSONArray("answers").getJSONObject(1).getString("label"))
        assertEquals(state.checklists.single(), AppStateJsonCodec.decodeChecklist(AppStateJsonCodec.encodeChecklist(checklist)))
        assertEquals(finite, AppStateJsonCodec.decodeMeasurement(AppStateJsonCodec.encodeMeasurement(finite)))
        assertEquals(configured, AppStateJsonCodec.decodeQuestionsView(AppStateJsonCodec.encodeQuestionsView(configured)))
        assertEquals(wellbeing, AppStateJsonCodec.decodeQuestionsView(AppStateJsonCodec.encodeQuestionsView(wellbeing)))
        assertEquals(timer, AppStateJsonCodec.decodeTimeView(AppStateJsonCodec.encodeTimeView(timer)))
        assertEquals(state.substances, AppStateJsonCodec.decodeSubstances(AppStateJsonCodec.encodeSubstances(state.substances)))
        val storedSubstances = root.getJSONObject("substances")
        assertEquals(listOf("Evenings", "drink", "berry"), listOf("label", "icon", "hue").map(storedSubstances::getString))
        // A view never named stores no name, and wears the look it always had.
        val unnamed = JSONObject(AppStateJsonCodec.encodeSubstances(SubstanceSettings()))
        assertFalse(unnamed.has("label"))
        assertEquals(listOf("science", "pink"), listOf("icon", "hue").map(unnamed::getString))
        listOf("label", "icon", "hue").forEach(storedSubstances::remove)
        assertEquals(state.substances.copy(label = null, icon = ViewIcon.Science, hue = ViewHue.Pink), AppStateJsonCodec.decodeSubstances(storedSubstances.toString()))
        assertEquals(state.activity, AppStateJsonCodec.decodeActivityConfiguration(AppStateJsonCodec.encodeActivityConfiguration(state.activity!!)))
    }

    @Test fun arbitraryStoredPromptsCanBeSavedEditedAndLoadedWithoutAContentRegistry() {
        val prompt = Question("energy", "How much energy do you have?", listOf(Answer("little", "A little"), Answer("much", "A lot")), "Consider the whole day.\nInclude rest.")
        val original = testMadeQuestions(id = "checkin-any-id", label = "Energy", questions = listOf(prompt))
        val store = CurrentTrackerStore()
        val repository = trackerRepository(store)
        assertTrue(repository.save(AppState(questions = listOf(original))))
        val loaded = repository.loadIfReadable()!!.questions.single()
        assertEquals(original, loaded)
        val edited = loaded.applyEdit(QuestionsEdit.RenameQuestion("energy", "How do you feel now?"), LocalDate.parse("2026-10-25"))
            .applyEdit(QuestionsEdit.RenameAnswer("energy", "little", "Some"), LocalDate.parse("2026-10-25"))
        assertTrue(repository.save(AppState(questions = listOf(edited))))
        assertEquals(edited, repository.loadIfReadable()!!.questions.single())
        assertEquals(prompt.help, edited.questions.single().help)
        val stored = storedDocument(store)
            .getJSONArray("questions").getJSONObject(0)
        assertFalse(stored.has("source"))
        assertEquals("How do you feel now?", stored.getJSONArray("questions").getJSONObject(0).getString("text"))
        assertEquals("Some", stored.getJSONArray("questions").getJSONObject(0).getJSONArray("answers").getJSONObject(0).getString("label"))
    }

    @Test fun absentActivityAndDisabledMoAreWrittenExplicitly() {
        val state = AppState(checklists = listOf(checklist.copy(mo = TrackerMoPolicy())))
        val root = JSONObject(AppStateJsonCodec.encode(state))
        assertTrue(root.has("activity"))
        assertTrue(root.isNull("activity"))
        val policy = root.getJSONArray("checklists").getJSONObject(0).getJSONObject("mo")
        assertEquals(false, policy.getBoolean("enabled"))
        assertTrue(policy.has("motif"))
        assertTrue(policy.isNull("motif"))
        assertEquals(state, AppStateJsonCodec.decodeVersion32(root.toString()))
    }

    @Test fun allOlderAndFutureSchemasAreRefused() {
        val root = JSONObject(AppStateJsonCodec.encode(AppState()))
        (0..33).filter { it != 32 }.forEach { version ->
            assertRefused(JSONObject(root.toString()).put("schemaVersion", version))
        }
        assertRefused(JSONObject(root.toString()).put("schemaVersion", "32"))
        val decimalVersion = root.toString().replace("\"schemaVersion\":32", "\"schemaVersion\":32.0")
        assertTrue(runCatching { AppStateJsonCodec.decodeVersion32(decimalVersion) }.isFailure)
        assertRefused(JSONObject(root.toString()).put("weightLogs", JSONArray()))
        assertTrue(runCatching { AppStateJsonCodec.decodeVersion32(root.toString() + " trailing") }.isFailure)
    }

    @Test fun currentConfigsCannotOmitCapabilitiesOrSmuggleInHistoricalFields() {
        val state = AppState(checklists = listOf(checklist), measurements = listOf(testMadeMeasurement()), time = listOf(testMadeTime()), questions = listOf(testMadeQuestions()))
        listOf("checklists", "measurements", "time", "questions").forEach { collection ->
            val root = JSONObject(AppStateJsonCodec.encode(state))
            val config = root.getJSONArray(collection).getJSONObject(0)
            config.remove("mo")
            assertRefused(root)
        }
        val cases: List<(JSONObject) -> Unit> = listOf(
            { it.getJSONArray("time").getJSONObject(0).put("motif", "reading") },
            { it.getJSONArray("time").getJSONObject(0).put("mo", JSONObject().put("enabled", true)) },
            { it.getJSONArray("checklists").getJSONObject(0).getJSONObject("mo").put("enabled", "true") },
            { it.getJSONArray("checklists").getJSONObject(0).getJSONObject("mo").put("motif", "Book") },
            { it.getJSONArray("measurements").getJSONObject(0).remove("valuePolicy") },
            { it.getJSONArray("measurements").getJSONObject(0).put("step", "0.10") },
            { it.getJSONArray("questions").getJSONObject(0).remove("questions") },
            { it.getJSONArray("questions").getJSONObject(0).put("questions", JSONArray()) },
            { it.getJSONArray("questions").getJSONObject(0).put("source", JSONObject().put("kind", "configured")) },
        )
        cases.forEach { change -> assertRefused(JSONObject(AppStateJsonCodec.encode(state)).also(change)) }
    }

    /** A measurement's trend span is always written, as the whole number of days it is; one stored before it existed follows 21 days. */
    @Test fun aMeasurementsTrendSpanIsStoredAndRefusedOutsideItsRange() {
        val state = AppState(measurements = listOf(testMadeMeasurement().copy(trendDays = 7)))
        val raw = AppStateJsonCodec.encode(state)
        assertEquals(7, JSONObject(raw).getJSONArray("measurements").getJSONObject(0).get("trendDays"))
        assertEquals(state.measurements, AppStateJsonCodec.decodeVersion32(raw).measurements)
        val cases: List<(JSONObject) -> Unit> = listOf(
            { it.put("trendDays", "7") },
            { it.put("trendDays", 7.5) },
            { it.put("trendDays", 1) },
            { it.put("trendDays", 91) },
        )
        cases.forEach { change -> assertRefused(JSONObject(raw).also { change(it.getJSONArray("measurements").getJSONObject(0)) }) }
        val before = JSONObject(raw).also { it.getJSONArray("measurements").getJSONObject(0).remove("trendDays") }
        assertEquals(21, AppStateJsonCodec.decodeVersion32(before.toString()).measurements.single().trendDays)
    }

    /** Whether Activity asks how long it took is always written, as a Boolean; a configuration stored before it existed asks for none. */
    @Test fun whetherActivityAsksForADurationIsStoredAndOffWhereOlderDataLacksIt() {
        val state = AppState(activity = testActivityConfiguration().copy(logsDuration = true))
        val raw = AppStateJsonCodec.encode(state)
        assertEquals(true, JSONObject(raw).getJSONObject("activity").get("logsDuration"))
        assertEquals(state.activity, AppStateJsonCodec.decodeVersion32(raw).activity)
        assertEquals(false, JSONObject(AppStateJsonCodec.encode(AppState(activity = testActivityConfiguration()))).getJSONObject("activity").get("logsDuration"))
        listOf<Any>("true", 1, JSONObject.NULL).forEach { value -> assertRefused(JSONObject(raw).also { it.getJSONObject("activity").put("logsDuration", value) }) }
        val before = JSONObject(raw).also { it.getJSONObject("activity").remove("logsDuration") }
        assertEquals(state.activity!!.copy(logsDuration = false), AppStateJsonCodec.decodeVersion32(before.toString()).activity)
    }

    @Test fun aDamagedRecordRefusesTheWholePayloadInsteadOfDroppingOrRepairingIt() {
        val state = AppState(checklists = listOf(checklist))
        val cases: List<Pair<String, Any>> = listOf(
            "measurementLogs" to JSONObject().put("measurement", "sensor").put("dateKey", "2026-10-20").put("timestampMillis", "1").put("value", 2),
            "measurementLogs" to JSONObject().put("measurement", "sensor").put("dateKey", "bad").put("timestampMillis", 1).put("value", 2),
            "checklistDays" to JSONObject().put("checklistId", "plants").put("dateKey", "2026-10-20").put("timestampMillis", 1).put("ticks", JSONArray().put("a/b").put("a/b")),
            "checklistDays" to JSONObject().put("checklistId", "plants").put("dateKey", "2026-10-20").put("timestampMillis", 1).put("ticks", JSONArray().put("a/b")).put("misses", JSONArray().put("a/b")),
            "timeLogs" to JSONObject().put("time", "timer").put("dateKey", "2026-10-20").put("timestampMillis", 1).put("minutes", 0),
            "questionEntries" to JSONObject().put("id", "entry").put("questions", "questions").put("dateKey", "2026-10-20").put("timestampMillis", 1).put("answers", JSONObject().put("question", 7)),
            "substanceLogs" to JSONObject().put("id", "use").put("kind", "unknown").put("dateKey", "2026-10-20").put("timestampMillis", 1),
            "substanceNoneDays" to JSONObject().put("dateKey", "2026-10-20").put("timestampMillis", 1).put("unknown", true),
            "sessions" to JSONObject().put("id", "session"),
            "activityEntries" to "not an entry",
        )
        cases.forEach { (field, record) ->
            assertRefused(JSONObject(AppStateJsonCodec.encode(state)).put(field, JSONArray().put(record)))
        }
    }

    @Test fun duplicateDailyRecordsAreRefusedAndMeasurementFactsRemainSeparate() {
        val root = JSONObject(AppStateJsonCodec.encode(AppState()))
        val log = JSONObject().put("time", "timer").put("dateKey", "2026-10-20").put("timestampMillis", 1)
        assertRefused(JSONObject(root.toString()).put("timeLogs", JSONArray().put(log).put(log)))
        val day = JSONObject().put("checklistId", "plants").put("dateKey", "2026-10-20").put("timestampMillis", 1).put("ticks", JSONArray().put("a/b"))
        assertRefused(JSONObject(root.toString()).put("checklistDays", JSONArray().put(day).put(day)))
        val record = MeasurementLog("sensor", "2026-10-20", 1L, -1.0)
        val state = AppState(measurementLogs = listOf(record, record))
        assertEquals(state, AppStateJsonCodec.decodeVersion32(AppStateJsonCodec.encode(state)))
    }

    private fun assertRefused(root: JSONObject) {
        assertTrue(root.toString(), runCatching { AppStateJsonCodec.decodeVersion32(root.toString()) }.isFailure)
    }
}

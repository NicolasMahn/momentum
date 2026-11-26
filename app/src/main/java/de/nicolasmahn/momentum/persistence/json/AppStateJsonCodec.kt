package de.nicolasmahn.momentum.persistence.json

import de.nicolasmahn.momentum.domain.AppState
import de.nicolasmahn.momentum.domain.activity.ActivityConfiguration
import de.nicolasmahn.momentum.domain.activity.ActivityEdit
import de.nicolasmahn.momentum.domain.checklist.ChecklistConfiguration
import de.nicolasmahn.momentum.domain.checklist.ChecklistEdit
import de.nicolasmahn.momentum.domain.measurement.MeasurementConfiguration
import de.nicolasmahn.momentum.domain.measurement.MeasurementEdit
import de.nicolasmahn.momentum.domain.questions.QuestionEntry
import de.nicolasmahn.momentum.domain.questions.QuestionsConfiguration
import de.nicolasmahn.momentum.domain.questions.QuestionsEdit
import de.nicolasmahn.momentum.domain.substance.SubstanceEdit
import de.nicolasmahn.momentum.domain.substance.SubstanceSettings
import de.nicolasmahn.momentum.domain.time.TimeConfiguration
import de.nicolasmahn.momentum.domain.time.TimeEdit
import org.json.JSONObject

internal object AppStateJsonCodec {
    private const val SchemaVersion = 32

    fun encode(state: AppState): String {
        requireCurrentTrackerFormat(state)
        val raw = JSONObject()
            .put("schemaVersion", SchemaVersion)
            .put("measurementLogs", encodeMeasurementLogs(state.measurementLogs))
            .put("activity", state.activity?.encode() ?: JSONObject.NULL)
            .put("sessions", encodeWorkoutSessions(state.sessions))
            .put("activityEntries", encodeActivityEntries(state.activityEntries))
            .put("substanceLogs", encodeSubstanceLogs(state.substanceLogs))
            .put("substanceNoneDays", encodeSubstanceNoneDays(state.substanceNoneDays))
            .put("substances", encodeSubstanceSettings(state.substances))
            .put("checklists", encodeChecklists(state.checklists))
            .put("checklistDays", encodeChecklistDays(state.checklistDays))
            .put("measurements", encodeMeasurements(state.measurements))
            .put("questions", encodeQuestions(state.questions))
            .put("questionEntries", encodeQuestionEntries(state.questionEntries))
            .put("time", encodeTime(state.time))
            .put("timeLogs", encodeTimeLogs(state.timeLogs))
            .toString()
        // Session deduplication is committed beside this payload, in its own store entry.
        require(decodeVersion32(raw) == state.copy(recordedTimeSessions = emptySet())) { "Tracker state cannot be read back completely" }
        return raw
    }

    fun decodeVersion32(raw: String): AppState {
        val root = parseObject(raw)
        root.requireFields(required = setOf(
            "schemaVersion", "measurementLogs", "activity", "sessions", "activityEntries",
            "substanceLogs", "substanceNoneDays", "substances", "checklists", "checklistDays",
            "measurements", "questions", "questionEntries", "time", "timeLogs",
        ))
        require(root.get("schemaVersion") == SchemaVersion) { "Unsupported tracker state schema; external conversion required" }
        val substances = root.getJSONObject("substances").decodeSubstanceSettings()
        val logs = root.decodeSubstanceLogs(substances)
        return AppState(
            measurementLogs = root.decodeMeasurementLogs(),
            activity = if (root.isNull("activity")) null else root.getJSONObject("activity").decodeActivityConfiguration(),
            sessions = root.decodeWorkoutSessions(),
            activityEntries = root.decodeActivityEntries(),
            substanceLogs = logs,
            substanceNoneDays = root.decodeSubstanceNoneDays(logs),
            substances = substances,
            checklists = root.decodeChecklists(),
            checklistDays = root.decodeChecklistDays(),
            measurements = root.decodeMeasurements(),
            questions = root.decodeQuestions(),
            questionEntries = root.decodeQuestionEntries(),
            time = root.decodeTime(),
            timeLogs = root.decodeTimeLogs(),
        ).also(::requireCurrentTrackerFormat)
    }

    /**
     * One configuration as the current version writes it, for a draft a page keeps across process
     * death. It is never stored, so it has no version of its own: [decodeChecklist] reads what the
     * same build wrote.
     */
    fun encodeChecklist(checklist: ChecklistConfiguration): String =
        encodeChecklists(listOf(checklist)).getJSONObject(0).toString()

    fun decodeChecklist(raw: String): ChecklistConfiguration =
        parseObject(raw).decodeChecklist()

    /** The edits a page made and has not saved yet, kept across process death beside its draft; never stored either. */
    fun encodeChecklistEdits(edits: List<ChecklistEdit>): String = editsToJson(edits).toString()

    fun decodeChecklistEdits(raw: String): List<ChecklistEdit> =
        parseArray(raw).toChecklistEdits()

    /** The Substances page's baseline, kept across process death beside the page; never stored, so it has no version of its own. */
    fun encodeSubstances(settings: SubstanceSettings): String = encodeSubstanceSettings(settings).toString()

    fun decodeSubstances(raw: String): SubstanceSettings = parseObject(raw).decodeSubstanceSettings()

    /** The edits the Substances page made and has not saved yet, kept beside its baseline; never stored either. */
    fun encodeSubstanceEdits(edits: List<SubstanceEdit>): String = substanceEditsToJson(edits).toString()

    fun decodeSubstanceEdits(raw: String): List<SubstanceEdit> = parseArray(raw).toSubstanceEdits()

    /** A measurement's page keeps its baseline across process death beside the page; never stored, so it has no version of its own. */
    fun encodeMeasurement(measurement: MeasurementConfiguration): String = measurement.encode().toString()

    fun decodeMeasurement(raw: String): MeasurementConfiguration = parseObject(raw).decodeMeasurement()

    /** The edits a measurement's page made and has not saved yet, kept beside its baseline; never stored either. */
    fun encodeMeasurementEdits(edits: List<MeasurementEdit>): String = measurementEditsToJson(edits).toString()

    fun decodeMeasurementEdits(raw: String): List<MeasurementEdit> = parseArray(raw).toMeasurementEdits()

    /** One questions view's configuration as the current version writes it, with its explicit source, for a draft its page keeps across process death; never stored. */
    fun encodeQuestionsView(view: QuestionsConfiguration): String = view.encode().toString()

    fun decodeQuestionsView(raw: String): QuestionsConfiguration = parseObject(raw).decodeQuestionsView()

    /** The edits a questions view's page or setup made and has not saved yet, kept across process death beside its draft; never stored either. */
    fun encodeQuestionsEdits(edits: List<QuestionsEdit>): String = questionsEditsToJson(edits).toString()

    fun decodeQuestionsEdits(raw: String): List<QuestionsEdit> = parseArray(raw).toQuestionsEdits()

    /** One time view's configuration as the current version writes it, with its declared policy, for a draft its page keeps across process death; never stored. */
    fun encodeTimeView(view: TimeConfiguration): String = view.encode().toString()

    fun decodeTimeView(raw: String): TimeConfiguration = parseObject(raw).decodeTimeView()

    /** The edits a time view's page or setup made and has not saved yet, kept across process death beside its draft; never stored either. */
    fun encodeTimeEdits(edits: List<TimeEdit>): String = timeEditsToJson(edits).toString()

    fun decodeTimeEdits(raw: String): List<TimeEdit> = parseArray(raw).toTimeEdits()

    /** Activity's configuration as the current version writes it, for the draft its page keeps across process death; never stored. */
    fun encodeActivityConfiguration(configuration: ActivityConfiguration): String = configuration.encode().toString()

    fun decodeActivityConfiguration(raw: String): ActivityConfiguration = parseObject(raw).decodeActivityConfiguration()

    /** The edits Activity's page made and has not saved yet, kept across process death beside its draft; never stored either. */
    fun encodeActivityEdits(edits: List<ActivityEdit>): String = activityEditsToJson(edits).toString()

    fun decodeActivityEdits(raw: String): List<ActivityEdit> = parseArray(raw).toActivityEdits()

    /**
     * One day's answers as the current version writes them, for the entry a questions view's
     * screen opened on, kept across process death beside what was picked; never stored. Written as
     * storage writes it, it comes back equal to the entry the store holds, which a save is decided
     * against.
     */
    fun encodeQuestionEntry(entry: QuestionEntry): String = entry.encode().toString()

    fun decodeQuestionEntry(raw: String): QuestionEntry = parseObject(raw).decodeQuestionEntry()

}

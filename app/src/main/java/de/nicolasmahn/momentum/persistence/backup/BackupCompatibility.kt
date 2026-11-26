package de.nicolasmahn.momentum.persistence.backup

import de.nicolasmahn.momentum.persistence.FeatureVisibilitySettingsCodec
import de.nicolasmahn.momentum.persistence.HolidayScheduleCodec
import de.nicolasmahn.momentum.persistence.PassChoicesCodec
import de.nicolasmahn.momentum.persistence.PreferencesFeatureVisibilityRepository
import de.nicolasmahn.momentum.persistence.PreferencesHabitRuleStartRepository
import de.nicolasmahn.momentum.persistence.PreferencesHolidayRepository
import de.nicolasmahn.momentum.persistence.PreferencesPassChoiceRepository
import de.nicolasmahn.momentum.persistence.PreferencesReminderSettingsRepository
import de.nicolasmahn.momentum.persistence.PreferencesTrackerRepository
import de.nicolasmahn.momentum.persistence.RecordedTimeSessionsCodec
import de.nicolasmahn.momentum.persistence.ReminderSettingsCodec
import de.nicolasmahn.momentum.persistence.json.AppStateJsonCodec
import de.nicolasmahn.momentum.persistence.parseHabitRuleStart
import java.math.BigDecimal
import org.json.JSONArray
import org.json.JSONObject
import de.nicolasmahn.momentum.persistence.json.AddedAfterBaseline
import de.nicolasmahn.momentum.persistence.json.StoredPayload
import de.nicolasmahn.momentum.persistence.json.WhenAbsent
import org.json.JSONTokener
import de.nicolasmahn.momentum.persistence.rows.RowsBackupStore
import de.nicolasmahn.momentum.persistence.rows.TrackerRows

/** Validates current data without repository defaults, repairs, or writes. Archives from before schema 32 are converted outside the app. */
fun validateRestoreCompatibility(original: BackupSnapshot) {
    // A store added after schema 32 that the snapshot lacks is empty, as restoring leaves it.
    val snapshot = BackupArchive.withAddedStores(original.stores)
    require(snapshot.stores.keys == CurrentStoreKeys.keys + RowsBackupStore.StoreName) { "Backup must contain every personal store" }
    snapshot.stores.forEach { (name, values) ->
        // The rows store's entries are rows, checked below.
        if (name != RowsBackupStore.StoreName) require(CurrentStoreKeys.getValue(name).containsAll(values.keys)) { "Unsupported keys in $name" }
    }
    val tracker = snapshot.stores.getValue(PreferencesTrackerRepository.PREFERENCES_NAME)
    val (rows, format) = RowsBackupStore.rows(snapshot.stores.getValue(RowsBackupStore.StoreName))
    if (rows.isEmpty()) {
        require(format == null) { "A layout without rows" }
    } else {
        // A backup from before the rows holds the document instead; one holding both is ambiguous.
        require(PreferencesTrackerRepository.STATE_V32_KEY !in tracker && PreferencesTrackerRepository.RECORDED_TIME_SESSIONS_KEY !in tracker) {
            "A backup holds the tracker state both as rows and as a document"
        }
        require(format == PreferencesTrackerRepository.RowsFormat) { "Unsupported rows layout" }
        val (document, sessions) = TrackerRows.assemble(rows)
        requireTrackerRestored(parseObject(document), parseObject(AppStateJsonCodec.encode(AppStateJsonCodec.decodeVersion32(document))))
        require(sessions.all { it >= 0 }) { "Invalid timer session" }
    }
    require((PreferencesTrackerRepository.STATE_V32_KEY in tracker) ==
        (PreferencesTrackerRepository.RECORDED_TIME_SESSIONS_KEY in tracker)) { "Tracker state and timer bookkeeping must be restored together" }
    tracker[PreferencesTrackerRepository.STATE_V32_KEY]?.let { raw ->
        requireTrackerRestored(parseObject(raw), parseObject(AppStateJsonCodec.encode(AppStateJsonCodec.decodeVersion32(raw))))
    }
    tracker[PreferencesHolidayRepository.SettingsKey]?.let { raw ->
        requireLossless(parseObject(raw), parseObject(HolidayScheduleCodec.encode(HolidayScheduleCodec.decode(raw))), StoredPayload.Holidays)
    }
    tracker[PreferencesPassChoiceRepository.SettingsKey]?.let { raw ->
        requireLossless(parseObject(raw), parseObject(PassChoicesCodec.encode(PassChoicesCodec.decode(raw))), StoredPayload.PassChoices)
    }
    tracker[PreferencesHabitRuleStartRepository.SettingsKey]?.let(::parseHabitRuleStart)
    tracker[PreferencesTrackerRepository.RECORDED_TIME_SESSIONS_KEY]?.let { raw ->
        requireLossless(parsePayload(raw), parsePayload(RecordedTimeSessionsCodec.encode(RecordedTimeSessionsCodec.decode(raw))), StoredPayload.TimerSessions, unordered = true)
    }
    snapshot.stores.getValue(PreferencesReminderSettingsRepository.PREFERENCES_NAME)[PreferencesReminderSettingsRepository.SETTINGS_KEY]?.let { raw ->
        val settings = requireNotNull(ReminderSettingsCodec.decode(raw)) { "Invalid reminder settings" }
        requireLossless(parseObject(raw), parseObject(ReminderSettingsCodec.encode(settings)), StoredPayload.Reminders)
    }
    snapshot.stores.getValue(PreferencesFeatureVisibilityRepository.PREFERENCES_NAME)[PreferencesFeatureVisibilityRepository.SETTINGS_KEY]?.let { raw ->
        requireLossless(parseObject(raw), parseObject(FeatureVisibilitySettingsCodec.encode(FeatureVisibilitySettingsCodec.decode(raw))), StoredPayload.Visibility)
    }
}

/**
 * Whether [snapshot] is a backup from before schema 32: its tracker state is a readable document
 * of an earlier version, and none in a form this app writes. This app can neither check nor
 * restore it (it is converted outside the app), and it is still the record of its day.
 *
 * The state is the one of the highest version it holds. Lower ones are copies kept from before
 * a migration, and a readable one of those says nothing about a damaged state above it.
 */
internal fun isFromBeforeSchema32(snapshot: BackupSnapshot): Boolean {
    val tracker = snapshot.stores[PreferencesTrackerRepository.PREFERENCES_NAME].orEmpty()
    if (PreferencesTrackerRepository.STATE_V32_KEY in tracker || !snapshot.stores[RowsBackupStore.StoreName].isNullOrEmpty()) return false
    val state = tracker.keys.maxByOrNull { key -> EarlierStateKey.matchEntire(key)?.groupValues?.get(1)?.toInt() ?: 0 }
        ?.takeIf(EarlierStateKey::matches) ?: return false
    return runCatching { parseObject(tracker.getValue(state)) }.isSuccess
}

private val EarlierStateKey = Regex("state_v([1-9]|[12][0-9]|3[01])")

private val CurrentStoreKeys = mapOf(
    PreferencesTrackerRepository.PREFERENCES_NAME to setOf(
        PreferencesTrackerRepository.STATE_V32_KEY,
        PreferencesTrackerRepository.RECORDED_TIME_SESSIONS_KEY,
        PreferencesHolidayRepository.SettingsKey,
        PreferencesPassChoiceRepository.SettingsKey,
        PreferencesHabitRuleStartRepository.SettingsKey,
    ),
    PreferencesReminderSettingsRepository.PREFERENCES_NAME to setOf(PreferencesReminderSettingsRepository.SETTINGS_KEY),
    PreferencesFeatureVisibilityRepository.PREFERENCES_NAME to setOf(PreferencesFeatureVisibilityRepository.SETTINGS_KEY),
)

/**
 * Independent of the decoder: a dropped, invented or reordered record, a changed configuration, or
 * an unknown field must fail restore. Only a field added after schema 32 may appear in the
 * re-encoding where the original lacks it.
 */
internal fun requireTrackerRestored(original: JSONObject, restored: JSONObject) {
    require(original.get("schemaVersion") is Int && original.getInt("schemaVersion") == 32) { "Unsupported tracker version" }
    requireLossless(original, restored, StoredPayload.TrackerState)
}

private fun requireLossless(original: Any, restored: Any, payload: StoredPayload, unordered: Boolean = false) {
    require(sameValue(original, restored, payload, path = "", unordered = unordered)) { "Restore would change stored data" }
}

/**
 * Whether [restored] holds exactly what [original] does at [path] of [payload]. The one difference
 * allowed is a field added after schema 32, at the place it was added, holding what its absence
 * means: a backup from before it lacks the field, and its re-encoding writes the fallback.
 */
private fun sameValue(original: Any, restored: Any, payload: StoredPayload, path: String, unordered: Boolean = false): Boolean = when {
    original is JSONObject && restored is JSONObject -> {
        val keys = original.keys().asSequence().toSet()
        val restoredKeys = restored.keys().asSequence().toSet()
        restoredKeys.containsAll(keys) &&
            (restoredKeys - keys).all { name -> holdsAbsence(payload, path, name, restored.get(name)) } &&
            keys.all { key -> sameValue(original.get(key), restored.get(key), payload, childPath(path, key), key in UnorderedCollections) }
    }
    original is JSONArray && restored is JSONArray -> {
        val elementPath = "$path[]"
        if (original.length() != restored.length()) false
        else if (unordered) {
            // Match once per element, so duplicates cannot disguise a missing record.
            val remaining = (0 until restored.length()).map { restored.get(it) }.toMutableList()
            (0 until original.length()).all { index ->
                val match = remaining.indexOfFirst { sameValue(original.get(index), it, payload, elementPath) }
                (match >= 0).also { if (it) remaining.removeAt(match) }
            }
        } else (0 until original.length()).all { sameValue(original.get(it), restored.get(it), payload, elementPath) }
    }
    original is Number && restored is Number -> BigDecimal(original.toString()).compareTo(BigDecimal(restored.toString())) == 0
    else -> original == restored
}

private fun holdsAbsence(payload: StoredPayload, owner: String, name: String, value: Any): Boolean =
    when (val absent = AddedAfterBaseline.field(payload, owner, name)?.absent) {
        is WhenAbsent.Fallback -> sameValue(absent.value, value, payload, childPath(owner, name))
        WhenAbsent.NoRecords -> value is JSONArray && value.length() == 0
        // A fact that was not recorded is not written, so it cannot appear.
        WhenAbsent.NotRecorded, null -> false
    }

private fun childPath(path: String, key: String) = if (path.isEmpty()) key else "$path.$key"

// Only sets may change order. Records, configured presentation, and dated revisions stay exact.
private val UnorderedCollections = setOf("ticks", "misses", "trackers", "tracked", "ids", "months", "weekdays")

private fun parseObject(raw: String): JSONObject = parsePayload(raw) as? JSONObject ?: error("Stored payload must be an object")

private fun parsePayload(raw: String): Any {
    require('\u0000' !in raw) { "Invalid JSON control character" }
    val parser = JSONTokener(raw)
    val value = parser.nextValue()
    require(parser.nextClean() == '\u0000') { "Unexpected trailing stored data" }
    return value
}

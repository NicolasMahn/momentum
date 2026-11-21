package com.example.workouttracker.persistence.backup

import com.example.workouttracker.domain.AppState
import com.example.workouttracker.domain.measurement.MeasurementValuePolicy
import com.example.workouttracker.domain.mo.TrackerMoPolicy
import com.example.workouttracker.domain.questions.Answer
import com.example.workouttracker.domain.questions.Question
import com.example.workouttracker.persistence.*
import com.example.workouttracker.persistence.json.AppStateJsonCodec
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class BackupCompatibilityTest {
    private val trackerStore = PreferencesTrackerRepository.PREFERENCES_NAME
    private val trackerKey = PreferencesTrackerRepository.STATE_V32_KEY
    private fun emptySnapshot() = BackupSnapshot(BackupArchive.StoreNames.associateWith { emptyMap<String, String>() })
    private fun snapshot(store: String, key: String, raw: String): BackupSnapshot {
        val pair = when {
            store != trackerStore -> emptyMap()
            key == trackerKey -> mapOf(PreferencesTrackerRepository.RECORDED_TIME_SESSIONS_KEY to "[]")
            key == PreferencesTrackerRepository.RECORDED_TIME_SESSIONS_KEY -> mapOf(trackerKey to AppStateJsonCodec.encode(AppState()))
            else -> emptyMap()
        }
        return BackupSnapshot(emptySnapshot().stores + (store to (pair + (key to raw))))
    }
    private fun tracker(raw: String) = snapshot(trackerStore, trackerKey, raw)
    private fun current() = JSONObject(AppStateJsonCodec.encode(CurrentBackupFixture.state))
    private fun rejected(block: () -> Unit) = assertTrue("Expected rejection", runCatching(block).isFailure)

    @Test fun completeCurrentSnapshotSurvivesArchiveAndRestoreWithoutChangingAnyValue() {
        val original = CurrentBackupFixture.snapshot()
        val restored = BackupArchive.decode(BackupArchive.encode(original))
        validateRestoreCompatibility(restored)
        assertEquals(original, restored)
        val values = restored.stores.getValue(trackerStore)
        assertEquals(CurrentBackupFixture.state.copy(recordedTimeSessions = emptySet()), AppStateJsonCodec.decodeVersion32(values.getValue(trackerKey)))
        assertEquals(CurrentBackupFixture.state.recordedTimeSessions, RecordedTimeSessionsCodec.decode(values.getValue(PreferencesTrackerRepository.RECORDED_TIME_SESSIONS_KEY)))
        assertEquals(CurrentBackupFixture.holidays, HolidayScheduleCodec.decode(values.getValue(PreferencesHolidayRepository.SettingsKey)))
        assertEquals(CurrentBackupFixture.passChoices, PassChoicesCodec.decode(values.getValue(PreferencesPassChoiceRepository.SettingsKey)))
        assertEquals(CurrentBackupFixture.ruleStart, parseHabitRuleStart(values.getValue(PreferencesHabitRuleStartRepository.SettingsKey)))
        assertEquals(CurrentBackupFixture.reminders, ReminderSettingsCodec.decode(restored.stores.getValue(PreferencesReminderSettingsRepository.PREFERENCES_NAME).getValue(PreferencesReminderSettingsRepository.SETTINGS_KEY)))
        assertEquals(CurrentBackupFixture.visibility, FeatureVisibilitySettingsCodec.decode(restored.stores.getValue(PreferencesFeatureVisibilityRepository.PREFERENCES_NAME).getValue(PreferencesFeatureVisibilityRepository.SETTINGS_KEY)))
    }

    @Test fun explicitCapabilitiesAndDisabledMoRoundTripWithoutInferringAnythingFromIds() {
        val before = CurrentBackupFixture.state
        val quiet = TrackerMoPolicy()
        val state = before.copy(
            activity = before.activity?.copy(mo = quiet),
            checklists = before.checklists.map { it.copy(mo = quiet) },
            measurements = before.measurements.map { it.copy(mo = quiet, valuePolicy = MeasurementValuePolicy.Positive) },
            questions = before.questions.map { it.copy(mo = quiet, questions = listOf(Question("rest", "How restored do you feel?", listOf(Answer("low", "Less"), Answer("high", "More")), "Include naps and breaks."))) },
            time = before.time.map { it.copy(mo = quiet) },
            substances = before.substances.copy(mo = quiet),
        )
        val raw = AppStateJsonCodec.encode(state)
        validateRestoreCompatibility(tracker(raw))
        val decoded = AppStateJsonCodec.decodeVersion32(raw)
        assertEquals(state.copy(recordedTimeSessions = emptySet()), decoded)
        assertEquals(before.measurementLogs, decoded.measurementLogs)
        assertEquals(before.questionEntries, decoded.questionEntries)
    }

    @Test fun anEmptyInstallationAndEachOptionalCurrentStoreValueAreValid() {
        validateRestoreCompatibility(emptySnapshot())
        validateRestoreCompatibility(tracker(AppStateJsonCodec.encode(AppState())))
        CurrentBackupFixture.snapshot().stores.forEach { (store, values) ->
            values.forEach { (key, raw) -> validateRestoreCompatibility(snapshot(store, key, raw)) }
        }
    }

    @Test fun everyObsoleteFutureUnknownOrRollbackKeyIsRejectedEvenBesideCurrentData() {
        val extraKeys = mapOf(
            trackerStore to ((1..30).map { "state_v$it" } + listOf("state_v32", "state_v031", "unreadable_state_v32", "aims_v1", "habit_pass_choices_v1", "holiday_settings_v2", "habit_rules_started_v2", "reading_sessions_recorded_v2", "unknown")),
            PreferencesReminderSettingsRepository.PREFERENCES_NAME to ((1..12).map { "settings_v$it" } + listOf("settings_v14", "unknown")),
            PreferencesFeatureVisibilityRepository.PREFERENCES_NAME to listOf("settings_v2", "unknown"),
        )
        extraKeys.forEach { (store, keys) -> keys.forEach { key ->
            val clean = CurrentBackupFixture.snapshot()
            val invalid = BackupSnapshot(clean.stores + (store to (clean.stores.getValue(store) + (key to "{broken"))))
            rejected { validateRestoreCompatibility(invalid) }
        } }
        rejected { validateRestoreCompatibility(BackupSnapshot(emptyMap())) }
        rejected { validateRestoreCompatibility(BackupSnapshot(emptySnapshot().stores + ("other" to emptyMap()))) }
    }

    @Test fun oldAndFuturePayloadVersionsAreRejectedUnderCurrentKeys() {
        val payloads = listOf(
            Triple(trackerStore, trackerKey, "schemaVersion"),
            Triple(PreferencesReminderSettingsRepository.PREFERENCES_NAME, PreferencesReminderSettingsRepository.SETTINGS_KEY, "version"),
            Triple(PreferencesFeatureVisibilityRepository.PREFERENCES_NAME, PreferencesFeatureVisibilityRepository.SETTINGS_KEY, "version"),
            Triple(trackerStore, PreferencesHolidayRepository.SettingsKey, "version"),
            Triple(trackerStore, PreferencesPassChoiceRepository.SettingsKey, "version"),
        )
        val clean = CurrentBackupFixture.snapshot()
        payloads.forEach { (store, key, field) ->
            val raw = clean.stores.getValue(store).getValue(key)
            val version = JSONObject(raw).getInt(field)
            listOf(0, version - 1, version + 1, "$version", version + 0.5).forEach { wrong ->
                rejected { validateRestoreCompatibility(snapshot(store, key, JSONObject(raw).put(field, wrong).toString())) }
            }
        }
    }

    @Test fun malformedTrailingOrUnknownPayloadContentIsRejected() {
        CurrentBackupFixture.snapshot().stores.forEach { (store, values) -> values.forEach { (key, raw) ->
            val wrongShape = if (raw.startsWith("[")) "{}" else "[]"
            listOf("", "{broken", "null", wrongShape, raw + " trailing", raw + '\u0000').forEach { bad ->
                rejected { validateRestoreCompatibility(snapshot(store, key, bad)) }
            }
            if (raw.startsWith("{")) {
                rejected { validateRestoreCompatibility(snapshot(store, key, JSONObject(raw).put("unknown", true).toString())) }
            }
        } }
    }

    @Test fun everyRecordCollectionRejectsAnInvalidEntryRatherThanDroppingIt() {
        listOf("measurementLogs", "sessions", "activityEntries", "substanceLogs", "substanceNoneDays", "checklistDays", "questionEntries", "timeLogs").forEach { field ->
            listOf(JSONObject(), "not a record", JSONObject.NULL).forEach { invalid ->
                val damaged = current().apply { getJSONArray(field).put(invalid) }
                rejected { validateRestoreCompatibility(tracker(damaged.toString())) }
            }
        }
        current().keys().asSequence().toList().forEach { field ->
            rejected { validateRestoreCompatibility(tracker(current().apply { remove(field) }.toString())) }
        }
    }

    @Test fun independentLossCheckDetectsEveryChangedRecordAndConfiguration() {
        val original = current()
        val collectionNames = listOf("measurementLogs", "sessions", "activityEntries", "substanceLogs", "substanceNoneDays", "checklistDays", "questionEntries", "timeLogs", "checklists", "measurements", "questions", "time")
        collectionNames.forEach { field ->
            val removed = current().apply { getJSONArray(field).remove(0) }
            rejected { requireTrackerRestored(original, removed) }
            val changed = current().apply { getJSONArray(field).getJSONObject(0).put("timestampMillis", 9999) }
            rejected { requireTrackerRestored(original, changed) }
            val extra = current().apply { getJSONArray(field).put(getJSONArray(field).getJSONObject(0)) }
            rejected { requireTrackerRestored(original, extra) }
        }
        listOf("activity", "substances").forEach { field ->
            rejected { requireTrackerRestored(original, current().apply { getJSONObject(field).put("unknown", true) }) }
        }
        // The two identical measurement facts in the fixture must remain two facts.
        rejected { requireTrackerRestored(original, current().apply { getJSONArray("measurementLogs").remove(2) }) }
    }

    /**
     * Whether Activity asks how long it took is restored as it was stored, neither flipped nor
     * dropped; a backup from before it existed restores with it off and nothing else.
     */
    @Test fun whetherActivityAsksForADurationIsRestoredAsStored() {
        val original = current()
        assertEquals(true, original.getJSONObject("activity").get("logsDuration"))
        rejected { requireTrackerRestored(original, current().apply { getJSONObject("activity").put("logsDuration", false) }) }
        rejected { requireTrackerRestored(original, current().apply { getJSONObject("activity").remove("logsDuration") }) }
        val before = current().apply { getJSONObject("activity").remove("logsDuration") }
        requireTrackerRestored(before, current().apply { getJSONObject("activity").put("logsDuration", false) })
        rejected { requireTrackerRestored(before, current()) }
    }

    @Test fun moPoliciesArePreservedForAllSixTrackerKinds() {
        fun policies(json: JSONObject): List<JSONObject> = listOf(json.getJSONObject("activity"), json.getJSONObject("substances")) +
            listOf("checklists", "measurements", "questions", "time").map { json.getJSONArray(it).getJSONObject(0) }
        val original = current()
        repeat(6) { index ->
            listOf<(JSONObject) -> Unit>(
                { it.put("enabled", false) }, { it.put("motif", JSONObject.NULL) }, { it.put("motif", "reading") },
            ).forEach { change ->
                val changed = current()
                val policy = policies(changed)[index].getJSONObject("mo")
                val before = policy.toString()
                change(policy)
                if (before != policy.toString()) rejected { requireTrackerRestored(original, changed) }
            }
            val missing = current().also { policies(it)[index].remove("mo") }
            rejected { validateRestoreCompatibility(tracker(missing.toString())) }
            val future = current().also { policies(it)[index].getJSONObject("mo").put("future", true) }
            rejected { validateRestoreCompatibility(tracker(future.toString())) }
        }
    }

    @Test fun configuredQuestionAndRoutineOrderRemainPartOfTheLosslessContract() {
        val original = current()
        val changed = current().apply {
            val answers = getJSONArray("questions").getJSONObject(0).getJSONArray("questions").getJSONObject(0).getJSONArray("answers")
            val first = answers.get(0)
            answers.put(0, answers.get(1)).put(1, first)
        }
        rejected { requireTrackerRestored(original, changed) }
        val session = current().apply {
            val sets = getJSONArray("sessions").getJSONObject(0).getJSONArray("exerciseLogs").getJSONObject(0).getJSONArray("sets")
            val first = sets.get(0)
            sets.put(0, sets.get(1)).put(1, first)
        }
        rejected { requireTrackerRestored(original, session) }
    }

    @Test fun stateAndBookkeepingMustBeRestoredAsOnePair() {
        val clean = CurrentBackupFixture.snapshot()
        listOf(trackerKey, PreferencesTrackerRepository.RECORDED_TIME_SESSIONS_KEY).forEach { key ->
            rejected { validateRestoreCompatibility(BackupSnapshot(clean.stores + (trackerStore to (clean.stores.getValue(trackerStore) - key)))) }
        }
    }

    @Test fun timerBookkeepingCannotLoseEntriesOrBeConfusedWithRecords() {
        val key = PreferencesTrackerRepository.RECORDED_TIME_SESSIONS_KEY
        listOf("[1,1]", "[-1]", "[1.5]", "[\"1\"]", "{}").forEach { raw ->
            rejected { validateRestoreCompatibility(snapshot(trackerStore, key, raw)) }
        }
        val state = AppStateJsonCodec.decodeVersion32(current().toString())
        assertTrue(state.recordedTimeSessions.isEmpty())
        assertEquals(CurrentBackupFixture.state.timeLogs, state.timeLogs)
    }
}

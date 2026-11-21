package com.example.workouttracker.persistence

import com.example.workouttracker.domain.checklist.ChecklistRewards
import com.example.workouttracker.domain.measurement.applyingMeasurementEdits
import com.example.workouttracker.domain.measurement.EarlierWeeks
import com.example.workouttracker.domain.measurement.MeasurementEdit
import com.example.workouttracker.domain.measurement.MeasurementRewards
import com.example.workouttracker.domain.measurement.MeasurementBank
import com.example.workouttracker.domain.checklist.Checkpoint
import com.example.workouttracker.domain.checklist.CheckpointTime
import com.example.workouttracker.domain.checklist.ChecklistConfiguration
import com.example.workouttracker.domain.checklist.ChecklistItem
import com.example.workouttracker.domain.checklist.ChecklistTarget
import com.example.workouttracker.domain.checklist.Dated
import com.example.workouttracker.domain.checklist.ViewHue
import com.example.workouttracker.domain.checklist.ViewIcon
import com.example.workouttracker.domain.checklist.WhenAway
import com.example.workouttracker.domain.measurement.MeasurementLog
import com.example.workouttracker.domain.measurement.testWeightMeasurement
import com.example.workouttracker.domain.activity.testActivityConfiguration
import com.example.workouttracker.persistence.backup.BackupArchive
import com.example.workouttracker.persistence.backup.BackupSnapshot
import com.example.workouttracker.persistence.backup.CurrentBackupFixture
import com.example.workouttracker.persistence.backup.requireTrackerRestored
import com.example.workouttracker.persistence.backup.validateRestoreCompatibility
import com.example.workouttracker.persistence.json.AddedAfterBaseline
import com.example.workouttracker.persistence.json.AddedField
import com.example.workouttracker.persistence.json.StoredPayload
import com.example.workouttracker.persistence.json.WhenAbsent
import com.example.workouttracker.persistence.json.AppStateJsonCodec
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.Month
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Stored data grows without conversions: what schema 32 wrote is read, saved and restored by the
 * current code, and anything added after it reads its fixed fallback where older data lacks it.
 *
 * Each sample in `formats/` holds every store as the code of its time wrote it, and is never
 * rewritten: a change that cannot read one is a breaking change, which needs a plan of its own, not
 * a new sample. A collection or store added later gets a sample of its own once it exists, since
 * schema 32's sample cannot hold it. `FORMAT_SAMPLE_WRITE=<file>` writes a missing sample once.
 */
class StoredGrowthTest {
    @Test
    fun theFormatsDirectoryHoldsExactlyTheSamplesThisTestReads() {
        val directory = File(requireNotNull(javaClass.classLoader!!.getResource("formats")) { "formats/ is missing" }.toURI())
        assertEquals(Samples.toSet(), directory.list()!!.toSet())
    }

    @Test
    fun whatSchema32StoredIsReadSavedAndRestored() {
        val snapshot = schema32()
        Samples.forEach { validateRestoreCompatibility(sample(it)) }

        val trackerStore = MemoryStore(snapshot.stores.getValue(PreferencesTrackerRepository.PREFERENCES_NAME))
        val repository = trackerRepository(trackerStore)
        val state = requireNotNull(repository.loadIfReadable()) { "The schema 32 sample must stay readable" }
        assertTrue(repository.save(state))
        assertEquals(state, repository.loadIfReadable())

        val reminders = PreferencesReminderSettingsRepository(MemoryStore(snapshot.stores.getValue(PreferencesReminderSettingsRepository.PREFERENCES_NAME)))
        assertEquals(CurrentBackupFixture.reminders, reminders.load())
        val visibility = PreferencesFeatureVisibilityRepository(MemoryStore(snapshot.stores.getValue(PreferencesFeatureVisibilityRepository.PREFERENCES_NAME)))
        assertEquals(CurrentBackupFixture.visibility, visibility.load())
    }

    /**
     * Every addition with what its absence means, written out by hand: a fallback is what the app did
     * before the setting existed and never changes, so a changed declaration fails here. The one
     * exception is whether Activity asks how long it took: its field always stood on the screen, and
     * absent it reads as off, because the owner asked for the field to be gone.
     */
    @Test
    fun everyAdditionIsPinnedWithWhatItsAbsenceMeans() {
        val pinned = listOf(
            AddedField(StoredPayload.TrackerState, "measurements[]", "trendDays", WhenAbsent.Fallback(21)),
            AddedField(StoredPayload.TrackerState, "time[].rewards", "tokenCover", WhenAbsent.Fallback("quarterOfGoal")),
            AddedField(StoredPayload.TrackerState, "measurements[].rewards", "earlierWeeks", WhenAbsent.NotRecorded),
            AddedField(StoredPayload.TrackerState, "substances", "label", WhenAbsent.NotRecorded),
            AddedField(StoredPayload.TrackerState, "substances", "icon", WhenAbsent.Fallback("science")),
            AddedField(StoredPayload.TrackerState, "substances", "hue", WhenAbsent.Fallback("pink")),
            AddedField(StoredPayload.TrackerState, "activity", "logsDuration", WhenAbsent.Fallback(false)),
            AddedField(StoredPayload.PassChoices, "", "checkpointChoices", WhenAbsent.NoRecords),
            AddedField(StoredPayload.PassChoices, "", "weekChoices", WhenAbsent.NoRecords),
        )
        assertEquals(pinned, AddedAfterBaseline.fields)
    }

    /** A backup made before a field was added lacks it; it still restores, and the field reads what its absence means. */
    @Test
    fun everyFieldAddedLaterReadsItsAbsenceWhereOlderDataLacksIt() {
        val older = schema32().withoutAddedFields()
        validateRestoreCompatibility(older)
        val state = AppStateJsonCodec.decodeVersion32(older.stores.getValue(PreferencesTrackerRepository.PREFERENCES_NAME).getValue(PreferencesTrackerRepository.STATE_V32_KEY))
        assertTrue(state.measurements.isNotEmpty())
        state.measurements.forEach { assertEquals(it.id, 21, it.trendDays) }
        val banks = state.time.mapNotNull { it.rewards }
        assertTrue(banks.isNotEmpty())
        banks.forEach { assertEquals(com.example.workouttracker.domain.checklist.Dated.always(com.example.workouttracker.domain.time.TokenCover.QuarterOfGoal), it.tokenCover) }
        // Substances had one fixed look: the flask in pink, under the app's own word.
        assertEquals(null, state.substances.label)
        assertEquals(ViewIcon.Science, state.substances.icon)
        assertEquals(ViewHue.Pink, state.substances.hue)
        // Activity asks for no duration until Settings switches it on, and what was recorded stays.
        assertEquals(false, requireNotNull(state.activity).logsDuration)
        assertTrue(state.sessions.any { it.durationMinutes != null })
        // The choices schema 32 stored name whole tasks alone: no checkpoint and no week was chosen.
        val choices = PassChoicesCodec.decode(older.stores.getValue(PreferencesTrackerRepository.PREFERENCES_NAME).getValue(PreferencesPassChoiceRepository.SettingsKey))
        assertTrue(choices.trackers.isNotEmpty())
        assertTrue(choices.checkpoints.isEmpty() && choices.weeks.isEmpty())
    }

    /**
     * Weight's bank as schema 32 stored it earns by the week, and is read and saved as it was. Once
     * its numbers are saved it earns by kept days and keeps the week rule in a field of its own,
     * with the last day that rule judged: read back exactly, restored from a backup, and neither
     * dropped nor invented by a restore.
     */
    @Test
    fun aWeekBankIsReadAsStoredAndKeepsItsWeekRuleOnceItEarnsByKeptDays() {
        fun weightBank(state: String) = JSONObject(state).getJSONArray("measurements").let { all -> (0 until all.length()).map(all::getJSONObject) }
            .single { it.getString("id") == "weight" }.getJSONObject("rewards")
        val stored = AppStateJsonCodec.decodeVersion32(trackerState())
        val weight = stored.measurements.single { it.id == "weight" }
        val asStored = """{"maxPasses":7,"earn":{"keptDaysInWeek":6}}"""
        assertEquals(fields(JSONObject(asStored)), fields(weightBank(trackerState())))
        assertEquals(MeasurementBank.Week(MeasurementRewards(maxPasses = 7, keptDaysInWeek = 6)), weight.rewards)
        assertEquals(fields(JSONObject(asStored)), fields(weightBank(AppStateJsonCodec.encode(stored))))

        val changed = stored.applyingMeasurementEdits("weight", listOf(MeasurementEdit.SetBankRules(maxPasses = 7, everyKeptDays = 7)), LocalDate.parse("2026-11-15"))
        val encoded = AppStateJsonCodec.encode(changed)
        val byKeptDays = """{"maxPasses":7,"earn":{"everyKeptDays":7},"earlierWeeks":{"keptDaysInWeek":6,"until":"2026-11-14"}}"""
        assertEquals(fields(JSONObject(byKeptDays)), fields(weightBank(encoded)))
        assertEquals(changed, AppStateJsonCodec.decodeVersion32(encoded))
        assertEquals(
            MeasurementBank.Standard(ChecklistRewards(maxPasses = 7, everyKeptDays = 7), EarlierWeeks(Dated.always(6), LocalDate.parse("2026-11-14"))),
            AppStateJsonCodec.decodeVersion32(encoded).measurements.single { it.id == "weight" }.rewards,
        )

        val snapshot = schema32()
        val tracker = snapshot.stores.getValue(PreferencesTrackerRepository.PREFERENCES_NAME) + (PreferencesTrackerRepository.STATE_V32_KEY to encoded)
        validateRestoreCompatibility(BackupSnapshot(snapshot.stores + (PreferencesTrackerRepository.PREFERENCES_NAME to tracker)))

        requireTrackerRestored(JSONObject(encoded), JSONObject(encoded))
        val withoutTheWeekRule = JSONObject(encoded).also { weightBankIn(it).remove("earlierWeeks") }
        assertTrue(runCatching { requireTrackerRestored(JSONObject(encoded), withoutTheWeekRule) }.isFailure)
        assertTrue(runCatching { requireTrackerRestored(withoutTheWeekRule, JSONObject(encoded)) }.isFailure)
        val anotherLastDay = JSONObject(encoded).also { weightBankIn(it).getJSONObject("earlierWeeks").put("until", "2026-11-13") }
        assertTrue(runCatching { requireTrackerRestored(JSONObject(encoded), anotherLastDay) }.isFailure)

        // The week rule is read strictly: whole, with its last day, and only beside a bank earning by kept days.
        listOf<(JSONObject) -> Unit>(
            { it.getJSONObject("earlierWeeks").remove("until") },
            { it.getJSONObject("earlierWeeks").remove("keptDaysInWeek") },
            { it.getJSONObject("earlierWeeks").put("keptDaysInWeek", 8) },
            { it.getJSONObject("earlierWeeks").put("until", "yesterday") },
            { it.getJSONObject("earlierWeeks").put("invented", true) },
            { it.put("earlierWeeks", 6) },
            { it.put("earn", JSONObject().put("keptDaysInWeek", 6)) },
        ).forEachIndexed { index, damage ->
            val damaged = JSONObject(encoded).also { damage(weightBankIn(it)) }.toString()
            assertTrue("case $index", runCatching { AppStateJsonCodec.decodeVersion32(damaged) }.isFailure)
        }
    }

    /** A stored object as its fields by name, so two that hold the same are equal whatever order they were written in. */
    private fun fields(json: JSONObject): Map<String, Any> =
        json.keys().asSequence().associateWith { name -> json.get(name).let { if (it is JSONObject) fields(it) else it } }

    private fun weightBankIn(state: JSONObject): JSONObject = state.getJSONArray("measurements").let { all -> (0 until all.length()).map(all::getJSONObject) }
        .single { it.getString("id") == "weight" }.getJSONObject("rewards")

    /** The choices as stored since a checkpoint and a week can be chosen: read, and saved again as they were. */
    @Test
    fun choicesByCheckpointAndWeekAreReadAndSavedAsStored() {
        val stored = sample(ChoicesByCheckpointAndWeek).stores.getValue(PreferencesTrackerRepository.PREFERENCES_NAME).getValue(PreferencesPassChoiceRepository.SettingsKey)
        val choices = PassChoicesCodec.decode(stored)
        assertTrue(choices.trackers.isNotEmpty() && choices.checkpoints.isNotEmpty() && choices.weeks.isNotEmpty())
        assertEquals(stored, PassChoicesCodec.encode(choices))
    }

    @Test
    fun aRestoreMayOnlyAddFieldsDeclaredAsAddedLater() {
        val original = JSONObject(trackerState())
        val reencoded = { JSONObject(trackerState()) }
        requireTrackerRestored(original, reencoded())

        val withoutAdded = JSONObject(trackerState()).also { it.getJSONArray("measurements").getJSONObject(0).remove("trendDays") }
        requireTrackerRestored(withoutAdded, reencoded())
        val notTheFallback = reencoded().also { it.getJSONArray("measurements").getJSONObject(0).put("trendDays", 7) }
        assertTrue(runCatching { requireTrackerRestored(withoutAdded, notTheFallback) }.isFailure)

        val refused = listOf<(JSONObject) -> Unit>(
            // An unknown field, a dropped, invented, duplicated or reordered record, and a changed value.
            { it.getJSONArray("measurements").getJSONObject(0).put("invented", true) },
            { it.getJSONArray("measurementLogs").remove(0) },
            { it.getJSONArray("measurementLogs").put(JSONObject().put("measurement", "msr_custom").put("dateKey", "2026-10-15").put("timestampMillis", 1).put("value", 1)) },
            { logs -> logs.getJSONArray("measurementLogs").let { it.put(it.getJSONObject(0)) } },
            { root -> root.getJSONArray("measurementLogs").let { logs -> val first = logs.get(0); logs.put(0, logs.get(1)); logs.put(1, first) } },
            { it.getJSONArray("measurementLogs").getJSONObject(0).put("value", 1) },
            // An added field anywhere but where it was added, or holding anything but its fallback where the original lacks it.
            { it.put("trendDays", 21) },
            { it.getJSONArray("measurementLogs").getJSONObject(0).put("trendDays", 21) },
            { it.getJSONObject("activity").put("trendDays", 21) },
        )
        refused.forEachIndexed { index, change ->
            val restored = reencoded().also(change)
            assertTrue("case $index", runCatching { requireTrackerRestored(original, restored) }.isFailure)
        }
    }

    private fun trackerState(): String =
        schema32().stores.getValue(PreferencesTrackerRepository.PREFERENCES_NAME).getValue(PreferencesTrackerRepository.STATE_V32_KEY)

    /** This snapshot as a build from before every addition wrote it: each declared field removed where it was added. */
    private fun BackupSnapshot.withoutAddedFields(): BackupSnapshot {
        val payloads = mapOf(
            StoredPayload.TrackerState to (PreferencesTrackerRepository.PREFERENCES_NAME to PreferencesTrackerRepository.STATE_V32_KEY),
            StoredPayload.Reminders to (PreferencesReminderSettingsRepository.PREFERENCES_NAME to PreferencesReminderSettingsRepository.SETTINGS_KEY),
            StoredPayload.Visibility to (PreferencesFeatureVisibilityRepository.PREFERENCES_NAME to PreferencesFeatureVisibilityRepository.SETTINGS_KEY),
            StoredPayload.Holidays to (PreferencesTrackerRepository.PREFERENCES_NAME to PreferencesHolidayRepository.SettingsKey),
            StoredPayload.PassChoices to (PreferencesTrackerRepository.PREFERENCES_NAME to PreferencesPassChoiceRepository.SettingsKey),
        )
        val result = stores.mapValues { (_, values) -> values.toMutableMap() }
        AddedAfterBaseline.fields.forEach { field ->
            val (store, key) = requireNotNull(payloads[field.payload]) { "No sample payload for ${field.payload}" }
            val raw = result.getValue(store).getValue(key)
            val json = JSONObject(raw)
            owners(json, field.owner).forEach { it.remove(field.name) }
            result.getValue(store)[key] = json.toString()
        }
        return BackupSnapshot(result)
    }

    /**
     * The objects at [owner] below [root], the same path the lossless check builds: names joined by
     * dots, each `[]` one step into every element of an array. A null, a number or another shape
     * where the path expects an object or an array holds no owner.
     */
    private fun owners(root: JSONObject, owner: String): List<JSONObject> {
        val steps = owner.split('.').filter { it.isNotEmpty() }.flatMap { segment ->
            val name = segment.substringBefore("[]")
            listOf(name) + List((segment.length - name.length) / 2) { "[]" }
        }
        val reached = steps.fold(listOf<Any>(root)) { values, step ->
            if (step == "[]") values.filterIsInstance<JSONArray>().flatMap { array -> (0 until array.length()).map(array::get) }
            else values.filterIsInstance<JSONObject>().mapNotNull { it.opt(step)?.takeUnless { value -> value == JSONObject.NULL } }
        }
        return reached.filterIsInstance<JSONObject>()
    }

    @Test
    fun theOwnersOfAnAdditionAreFoundWhateverShapesStandBesideThem() {
        val root = JSONObject("""{"a":[{"r":null},{"r":{"x":1}},{"r":7}],"m":[[{"k":1}],[{"k":2},3]],"s":[{"p":7},{"p":[{"q":1}]}]}""")
        assertEquals(listOf(1), owners(root, "a[].r").map { it.getInt("x") })
        assertEquals(listOf(1, 2), owners(root, "m[][]").map { it.getInt("k") })
        assertEquals(listOf(1), owners(root, "s[].p[]").map { it.getInt("q") })
        assertEquals(listOf(root), owners(root, ""))
    }

    private fun schema32(): BackupSnapshot = sample(Schema32)

    /** A committed sample, with any store added after it empty, as restoring it would leave that store. */
    private fun sample(name: String): BackupSnapshot {
        writeSampleIfAsked(name)
        val resource = requireNotNull(javaClass.classLoader!!.getResource("formats/$name")) { "The committed sample formats/$name is missing" }
        val json = JSONObject(resource.readText())
        return BackupArchive.withAddedStores(json.keys().asSequence().associateWith { store ->
            val values = json.getJSONObject(store)
            values.keys().asSequence().associateWith { values.getString(it) }
        })
    }

    /** Only on request, and only once: a sample that exists is never replaced. */
    private fun writeSampleIfAsked(name: String) {
        if (System.getenv("FORMAT_SAMPLE_WRITE") != name) return
        val file = File("src/test/resources/formats/$name")
        check(!file.exists()) { "formats/$name exists and is never rewritten" }
        file.parentFile?.mkdirs()
        file.writeText(sampleJson(sampleSnapshot()).toString(2) + "\n")
    }

    private fun sampleJson(snapshot: BackupSnapshot) = JSONObject().apply {
        snapshot.stores.toSortedMap().forEach { (name, values) -> put(name, JSONObject(values.toSortedMap())) }
    }

    /** The complete backup fixture, with the shapes it lacks: an item's season, dates, checkpoints, target and holiday count, a week target, and a week bank. */
    private fun sampleSnapshot(): BackupSnapshot {
        val day = LocalDate.parse("2026-10-13")
        val shapes = ChecklistConfiguration(
            id = "chk_shapes", label = "Shapes", icon = ViewIcon.Plant, hue = ViewHue.Forest,
            checkpoints = listOf(
                Checkpoint("morning", CheckpointTime.AfterWake(5), remind = false),
                Checkpoint("evening", CheckpointTime.Fixed(LocalTime.of(20, 30)), remind = true),
            ),
            target = ChecklistTarget.PerWeek(3),
            items = listOf(
                ChecklistItem("season", "Season", months = setOf(Month.JANUARY, Month.OCTOBER), from = day, until = day.plusDays(30), checkpoints = listOf("evening")),
                ChecklistItem("weekdays", "Weekdays", target = ChecklistTarget.Weekdays(setOf(DayOfWeek.MONDAY, DayOfWeek.SATURDAY))),
                ChecklistItem("fewer", "Fewer", target = ChecklistTarget.PerWeek(4), awayTimes = 2, whenAway = Dated.always(WhenAway.LowerTarget)),
            ),
        )
        val weight = testWeightMeasurement()
        val state = CurrentBackupFixture.state.let { fixture ->
            val activity = requireNotNull(fixture.activity)
            fixture.copy(
                // The default gym plan's exercises have a starting weight and a step.
                activity = activity.copy(activities = activity.activities + testActivityConfiguration().activities),
                checklists = fixture.checklists + shapes,
                measurements = fixture.measurements + weight,
                measurementLogs = fixture.measurementLogs + MeasurementLog(weight.id, "2026-10-14", 112, 81.4),
            )
        }
        val base = CurrentBackupFixture.snapshot()
        val tracker = base.stores.getValue(PreferencesTrackerRepository.PREFERENCES_NAME) +
            (PreferencesTrackerRepository.STATE_V32_KEY to AppStateJsonCodec.encode(state))
        return BackupSnapshot(base.stores + (PreferencesTrackerRepository.PREFERENCES_NAME to tracker))
    }

    private class MemoryStore(initial: Map<String, String>) : TrackerStringStore {
        val values = initial.toMutableMap()
        override fun keys(): Set<String> = values.keys.toSet()
        override fun read(key: String) = values[key]
        override fun write(key: String, value: String) = writeAll(mapOf(key to value))
        override fun writeAll(values: Map<String, String>): Boolean = true.also { this.values.putAll(values) }
        override fun remove(keys: Set<String>): Boolean = true.also { values -= keys }
    }

    private companion object {
        const val Schema32 = "schema-32.json"

        /** Written when a token could first be set aside for one checkpoint, and for a week's amount. */
        const val ChoicesByCheckpointAndWeek = "choices-by-checkpoint-and-week.json"
        val Samples = listOf(Schema32, ChoicesByCheckpointAndWeek)
    }
}

package de.nicolasmahn.momentum.persistence

import de.nicolasmahn.momentum.domain.TrackerRef
import de.nicolasmahn.momentum.domain.TrackerTypeId
import de.nicolasmahn.momentum.domain.mo.MoChoices
import de.nicolasmahn.momentum.persistence.backup.BackupArchive
import de.nicolasmahn.momentum.persistence.backup.BackupSnapshot
import de.nicolasmahn.momentum.persistence.backup.validateRestoreCompatibility
import java.time.LocalDate
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PreferencesPassChoiceRepositoryTest {
    private val day = LocalDate.parse("2026-10-04")
    private val counter = TrackerRef(TrackerTypeId("counter"), "shared-id")
    private val timer = TrackerRef(TrackerTypeId("time"), "shared-id")
    private val choice = PassChoices(day, setOf(counter, timer))

    @Test fun genericChoicesSurviveBackupAndOnlyQuietTheirOwnDay() {
        val store = MemoryStore()
        assertTrue(PreferencesPassChoiceRepository(store).save(choice))
        val archive = BackupArchive.encode(snapshot(store.values))
        val restored = BackupArchive.decode(archive)
        validateRestoreCompatibility(restored)
        val loaded = PreferencesPassChoiceRepository(MemoryStore(restored.stores.getValue("workout_tracker"))).load()!!
        assertEquals(choice, loaded)
        assertEquals(setOf(counter, timer), loaded.forDay(day))
        assertEquals(emptySet<TrackerRef>(), loaded.forDay(day.plusDays(1)))
        assertEquals(emptySet<TrackerRef>(), loaded.forDay(day.minusDays(1)))
    }

    /** A value stored before checkpoints and weeks could be chosen names whole tasks alone, and reads as it always did. */
    @Test fun aValueStoredBeforeCheckpointsAndWeeksReadsAsNoneChosen() {
        val older = """{"version":2,"day":"2026-10-04","trackers":[{"type":"counter","id":"shared-id"},{"type":"time","id":"shared-id"}]}"""
        assertEquals(choice, PassChoicesCodec.decode(older))
        assertEquals(MoChoices(tasks = setOf(counter, timer)), PassChoicesCodec.decode(older).on(day))
        // A backup holding it restores, though saving it again writes the two empty collections.
        validateRestoreCompatibility(snapshot(mapOf(PreferencesPassChoiceRepository.SettingsKey to older)))
        val saved = JSONObject(PassChoicesCodec.encode(PassChoicesCodec.decode(older)))
        assertEquals(0, saved.getJSONArray("checkpointChoices").length())
        assertEquals(0, saved.getJSONArray("weekChoices").length())
    }

    @Test fun checkpointAndWeekChoicesSurviveBackupAndHoldForTheirDayAndTheirWeek() {
        val checklist = TrackerRef(TrackerTypeId("checklist"), "care")
        val monday = LocalDate.parse("2026-09-28")
        val chosen = PassChoices(day, setOf(counter), checkpoints = mapOf(checklist to setOf("morning", "evening")), weeks = mapOf(timer to monday))
        val store = MemoryStore()
        assertTrue(PreferencesPassChoiceRepository(store).save(chosen))
        val restored = BackupArchive.decode(BackupArchive.encode(snapshot(store.values)))
        validateRestoreCompatibility(restored)
        val loaded = PreferencesPassChoiceRepository(MemoryStore(restored.stores.getValue("workout_tracker"))).load()!!
        assertEquals(chosen, loaded)

        // A day's choices end with their day; a week's hold from its Monday to its Sunday.
        assertEquals(MoChoices(setOf(counter, timer), mapOf(checklist to setOf("morning", "evening"))), loaded.on(day))
        assertEquals(MoChoices(setOf(timer)), loaded.on(monday))
        assertEquals(MoChoices(), loaded.on(day.plusDays(1)))
        assertEquals(MoChoices(), loaded.on(monday.minusDays(1)))
        // The next choice starts from what still holds: on a new day of the same week, the week's alone.
        assertEquals(chosen, loaded.carriedTo(day))
        assertEquals(PassChoices(day.minusDays(1), weeks = mapOf(timer to monday)), loaded.carriedTo(day.minusDays(1)))
        assertEquals(PassChoices(day.plusDays(1)), loaded.carriedTo(day.plusDays(1)))
    }

    @Test fun malformedCheckpointAndWeekChoicesAreRejected() {
        listOf(
            """"checkpointChoices":[{"type":"checklist","id":"care"}],"weekChoices":[]""",
            """"checkpointChoices":[{"type":"checklist","id":"care","checkpoint":""}],"weekChoices":[]""",
            """"checkpointChoices":[{"type":"checklist","id":"care","checkpoint":7}],"weekChoices":[]""",
            """"checkpointChoices":[{"type":"checklist","id":"care","checkpoint":"morning","extra":1}],"weekChoices":[]""",
            """"checkpointChoices":[{"type":"checklist","id":"care","checkpoint":"morning"},{"type":"checklist","id":"care","checkpoint":"morning"}],"weekChoices":[]""",
            """"checkpointChoices":{},"weekChoices":[]""",
            """"checkpointChoices":[],"weekChoices":[{"type":"time","id":"reading"}]""",
            // A week starts on a Monday, and a task is chosen for one week at a time.
            """"checkpointChoices":[],"weekChoices":[{"type":"time","id":"reading","monday":"2026-09-29"}]""",
            """"checkpointChoices":[],"weekChoices":[{"type":"time","id":"reading","monday":"2026-09-28"},{"type":"time","id":"reading","monday":"2026-09-21"}]""",
            """"checkpointChoices":[],"weekChoices":[{"type":"time","id":"reading","monday":"soon"}]""",
            """"checkpointChoices":[],"weekChoices":null""",
        ).forEach { added ->
            val raw = """{"version":2,"day":"2026-10-04","trackers":[],$added}"""
            assertThrows(raw, RuntimeException::class.java) { PassChoicesCodec.decode(raw) }
        }
    }

    @Test fun encodingIsOrderedByNamespaceThenInstanceId() {
        val secondCounter = TrackerRef(counter.type, "another-id")
        val first = choice.copy(trackers = linkedSetOf(timer, counter, secondCounter))
        val second = choice.copy(trackers = linkedSetOf(secondCounter, counter, timer))
        val encoded = PassChoicesCodec.encode(first)
        assertEquals(encoded, PassChoicesCodec.encode(second))
        val refs = JSONObject(encoded).getJSONArray("trackers")
        assertEquals("counter", refs.getJSONObject(0).getString("type"))
        assertEquals("another-id", refs.getJSONObject(0).getString("id"))
        assertEquals("shared-id", refs.getJSONObject(1).getString("id"))
        assertEquals("time", refs.getJSONObject(2).getString("type"))
        assertEquals(first, PassChoicesCodec.decode(encoded))
    }

    @Test fun anEmptyChoiceIsDistinctFromNoStoredChoice() {
        val store = MemoryStore()
        val repository = PreferencesPassChoiceRepository(store)
        assertNull(repository.load())
        assertTrue(repository.save(PassChoices(day)))
        assertEquals(PassChoices(day), repository.load())
        assertEquals(setOf(PreferencesPassChoiceRepository.SettingsKey), store.values.keys)
    }

    @Test fun readsDoNotWriteAndSavesOnlyWriteTheCurrentKey() {
        val store = MemoryStore(mapOf(PreferencesPassChoiceRepository.SettingsKey to PassChoicesCodec.encode(choice)))
        var written = 0
        val repository = PreferencesPassChoiceRepository(store, onWritten = { written++ })
        assertEquals(choice, repository.load())
        assertTrue(store.writtenKeys.isEmpty())
        assertEquals(0, written)
        assertTrue(repository.save(choice.copy(trackers = setOf(timer))))
        assertEquals(listOf("habit_pass_choices_v2"), store.writtenKeys)
        assertEquals(1, written)
    }

    @Test fun failedWritesPreservePreviousChoicesAndDoNotNotifyReaders() {
        val original = PassChoicesCodec.encode(choice)
        val store = MemoryStore(mapOf(PreferencesPassChoiceRepository.SettingsKey to original))
        var written = 0
        val repository = PreferencesPassChoiceRepository(store, onWritten = { written++ })
        store.acceptWrites = false
        assertFalse(repository.save(PassChoices(day.plusDays(1))))
        assertEquals(mapOf(PreferencesPassChoiceRepository.SettingsKey to original), store.values)
        assertEquals(choice, repository.load())
        assertEquals(0, written)
        store.throwOnWrite = true
        assertFalse(repository.save(PassChoices(day.plusDays(1))))
        assertEquals(choice, repository.load())
        assertEquals(0, written)
    }

    @Test fun aFuturePayloadCannotBeReadOrOverwritten() {
        val raw = """{"version":3,"day":"2026-10-04","trackers":[]}"""
        val store = MemoryStore(mapOf(PreferencesPassChoiceRepository.SettingsKey to raw))
        val repository = PreferencesPassChoiceRepository(store)
        assertThrows(RuntimeException::class.java) { repository.load() }
        assertFalse(repository.save(choice))
        assertEquals(mapOf(PreferencesPassChoiceRepository.SettingsKey to raw), store.values)
        assertTrue(store.writtenKeys.isEmpty())
    }

    @Test fun anUnreadablePayloadCannotBeOverwritten() {
        val raw = """{"version":2,"day":"2026-10-04","trackers":[{"type":"counter","id":""}]}"""
        val store = MemoryStore(mapOf(PreferencesPassChoiceRepository.SettingsKey to raw))
        val repository = PreferencesPassChoiceRepository(store)
        assertThrows(RuntimeException::class.java) { repository.load() }
        assertFalse(repository.save(choice))
        assertEquals(raw, store.values[PreferencesPassChoiceRepository.SettingsKey])
        assertTrue(store.writtenKeys.isEmpty())
    }

    @Test fun duplicateReferencesAndMalformedReferenceFieldsAreRejected() {
        listOf(
            """[{"type":"counter","id":"a"},{"type":"counter","id":"a"}]""",
            """[{"type":"counter","id":"a","unknown":true}]""",
            """[{"type":"counter"}]""",
            """[{"id":"a"}]""",
            """[{"type":"Counter","id":"a"}]""",
            """[{"type":"counter.invalid","id":"a"}]""",
            """[{"type":"","id":"a"}]""",
            """[{"type":1,"id":"a"}]""",
            """[{"type":null,"id":"a"}]""",
            """[{"type":"counter","id":""}]""",
            """[{"type":"counter","id":"  "}]""",
            """[{"type":"counter","id":12}]""",
            """[{"type":"counter","id":null}]""",
            """["counter:a"]""",
            """[null]""",
            """{}""",
            """null""",
        ).forEach { trackers ->
            val raw = """{"version":2,"day":"2026-10-04","trackers":$trackers}"""
            assertThrows(raw, RuntimeException::class.java) { PassChoicesCodec.decode(raw) }
        }
    }

    @Test fun theCurrentSchemaRejectsUnknownMissingAndMistypedFields() {
        listOf(
            """{"version":2,"day":"2026-10-04","trackers":[],"extra":true}""",
            """{"version":2,"day":"2026-10-04"}""",
            """{"version":2,"trackers":[]}""",
            """{"day":"2026-10-04","trackers":[]}""",
            """{"version":"2","day":"2026-10-04","trackers":[]}""",
            """{"version":2.0,"day":"2026-10-04","trackers":[]}""",
            """{"version":2,"day":"2026-02-30","trackers":[]}""",
            """{"version":2,"day":20261004,"trackers":[]}""",
            """{"version":2,"day":null,"trackers":[]}""",
            """{"version":2,"day":"2026-10-04","trackers":[]} trailing""",
            """{"version":2,"day":"2026-10-04","trackers":[]}""" + '\u0000',
            """{"version":1,"day":"2026-10-04","needs":["Reading"]}""",
            "[]",
        ).forEach { raw ->
            assertThrows(raw, RuntimeException::class.java) { PassChoicesCodec.decode(raw) }
        }
    }

    @Test fun malformedCurrentChoicesAreRejectedOnRestore() {
        val raw = """{"version":2,"day":"2026-10-04","trackers":[{"type":"counter","id":"a","extra":true}]}"""
        assertThrows(RuntimeException::class.java) {
            validateRestoreCompatibility(snapshot(mapOf(PreferencesPassChoiceRepository.SettingsKey to raw)))
        }
    }

    private fun snapshot(values: Map<String, String>) = BackupSnapshot(mapOf(
        "workout_tracker" to values,
        "momentum_reminders" to emptyMap(),
        "momentum_feature_visibility" to emptyMap(),
    ))

    private class MemoryStore(initial: Map<String, String> = emptyMap()) : TrackerStringStore {
        val values = initial.toMutableMap()
        val writtenKeys = mutableListOf<String>()
        var acceptWrites = true
        var throwOnWrite = false

        override fun keys(): Set<String> = values.keys.toSet()
        override fun read(key: String) = values[key]

        override fun writeAll(values: Map<String, String>): Boolean {
            if (throwOnWrite) error("Write failed")
            if (!acceptWrites) return false
            this.values.putAll(values)
            writtenKeys += values.keys
            return true
        }

        override fun write(key: String, value: String): Boolean = writeAll(mapOf(key to value))
    }
}

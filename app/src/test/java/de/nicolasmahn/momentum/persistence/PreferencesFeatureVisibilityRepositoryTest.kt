package de.nicolasmahn.momentum.persistence

import de.nicolasmahn.momentum.domain.LoggedViewKind
import de.nicolasmahn.momentum.domain.LoggedViewRef
import de.nicolasmahn.momentum.domain.checklist.Dated
import de.nicolasmahn.momentum.domain.settings.DashboardFeature
import de.nicolasmahn.momentum.domain.settings.FeatureVisibilitySettings
import de.nicolasmahn.momentum.domain.settings.ViewRef
import java.time.LocalDate
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PreferencesFeatureVisibilityRepositoryTest {
    private val monday = LocalDate.parse("2026-10-12")
    private fun on(day: Int) = monday.plusDays(day.toLong())

    @Test
    fun missingSettingsKeepExistingFeaturesVisibleButStatisticsHidden() {
        val settings = PreferencesFeatureVisibilityRepository(FakeStringStore()).load()

        assertEquals(FeatureVisibilitySettings(), settings)
        DashboardFeature.entries
            .filterNot { it == DashboardFeature.Statistics }
            .forEach { feature ->
                assertTrue(feature.name, settings.isVisible(feature))
            }
        assertFalse(settings.isVisible(DashboardFeature.Statistics))
    }

    /**
     * Version 7 is the one shape written: a dated entry per hidden thing, under its kind, plain
     * while it never changed and its revisions once it has, each group only while it holds one.
     */
    @Test
    fun versionSevenHoldsOneDatedEntryPerHiddenThingUnderItsKind() {
        val store = FakeStringStore()
        val repository = PreferencesFeatureVisibilityRepository(store)
        fun stored() = JSONObject(store.read(PreferencesFeatureVisibilityRepository.SETTINGS_KEY)!!)
        val settings = FeatureVisibilitySettings(disabledFeatures = setOf(DashboardFeature.Statistics), hiddenChecklists = setOf("care"))
            .withVisibility(DashboardFeature.Substances, visible = false, today = on(0))
            .withVisibility(DashboardFeature.Substances, visible = true, today = on(21))
            .withVisibility(LoggedViewRef(LoggedViewKind.Measurement, "weight"), visible = false, today = on(8))

        assertTrue(repository.save(settings))
        assertEquals(
            plainJson(
                JSONObject(
                    """{"version":7,"features":{"statistics":true,"substances":[{"value":false},{"value":true,"from":"2026-10-12"},{"value":false,"from":"2026-11-02"}]},""" +
                        """"checklists":{"care":true},"measurements":{"weight":[{"value":false},{"value":true,"from":"2026-10-20"}]}}""",
                ),
            ),
            plainJson(stored()),
        )
        assertEquals(settings, repository.load())
        assertTrue(repository.load().showsOn(LoggedViewRef(LoggedViewKind.Measurement, "weight"), on(7)))
        assertFalse(repository.load().showsOn(LoggedViewRef(LoggedViewKind.Measurement, "weight"), on(8)))
        assertTrue(repository.load().isVisible(DashboardFeature.Substances))

        assertTrue(repository.save(FeatureVisibilitySettings(disabledFeatures = emptySet())))
        assertEquals(plainJson(JSONObject("""{"version":7}""")), plainJson(stored()))
        assertTrue(repository.save(FeatureVisibilitySettings().withVisibility(LoggedViewRef(LoggedViewKind.Questions, "qst_k2p9"), visible = false, today = on(1)).withVisibility(LoggedViewRef(LoggedViewKind.Time, "tim_k2p9"), visible = false, today = on(1))))
        assertEquals(
            plainJson(JSONObject("""{"version":7,"features":{"statistics":true},"questions":{"qst_k2p9":[{"value":false},{"value":true,"from":"2026-10-13"}]},"time":{"tim_k2p9":[{"value":false},{"value":true,"from":"2026-10-13"}]}}""")),
            plainJson(stored()),
        )
    }

    /** The plan's example payload reads as it says, and is written back as it is. */
    @Test
    fun theExampleOfVersionSevenRoundTrips() {
        val raw = """{"version":7,"features":{"statistics":true,"substances":[{"value":true},{"value":false,"from":"2026-11-02"}]},"checklists":{"care":true},"measurements":{"weight":[{"value":false},{"value":true,"from":"2026-10-20"}]}}"""
        val store = FakeStringStore().apply { putRaw(PreferencesFeatureVisibilityRepository.SETTINGS_KEY, raw) }
        val repository = PreferencesFeatureVisibilityRepository(store)

        val settings = repository.load()
        assertEquals(
            FeatureVisibilitySettings(
                mapOf(
                    ViewRef.Feature(DashboardFeature.Statistics) to Dated.always(true),
                    ViewRef.Feature(DashboardFeature.Substances) to Dated(listOf(Dated.Revision(true), Dated.Revision(false, LocalDate.parse("2026-11-02")))),
                    ViewRef.Checklist("care") to Dated.always(true),
                    ViewRef.Logged(LoggedViewRef(LoggedViewKind.Measurement, "weight")) to Dated(listOf(Dated.Revision(false), Dated.Revision(true, LocalDate.parse("2026-10-20")))),
                ),
            ),
            settings,
        )
        assertTrue(repository.save(settings))
        assertEquals(plainJson(JSONObject(raw)), plainJson(JSONObject(store.read(PreferencesFeatureVisibilityRepository.SETTINGS_KEY)!!)))
    }

    /** Anything version 7 would not write back as it is, it refuses: the settings then start afresh. */
    @Test
    fun aVersionSevenPayloadMustBeExactlyWhatIsWritten() {
        listOf(
            // Weight is a measurement, never a feature; a name no feature has is refused with it.
            """{"version":7,"features":{"weight":true}}""",
            """{"version":7,"features":{"mood":true}}""",
            // A view never hidden has no entry, and a setting that never changed is written plainly.
            """{"version":7,"features":{"statistics":false}}""",
            """{"version":7,"features":{"statistics":[{"value":true}]}}""",
            """{"version":7,"checklists":{"care":[{"value":false,"from":"2026-10-12"},{"value":true,"from":"2026-10-13"}]}}""",
            // A group is written only while it holds an entry, and an entry is a Boolean under an ID.
            """{"version":7,"features":{}}""",
            """{"version":7,"measurements":{"":true}}""",
            """{"version":7,"measurements":{"weight":"yes"}}""",
            """{"version":7,"measurements":["weight"]}""",
            """{"version":7,"disabledFeatures":[]}""",
            """{"version":7,"hiddenViews":{"a":true}}""",
            """{"version":8}""",
            """{"version":"7"}""",
            "not json",
        ).forEach { raw ->
            val store = FakeStringStore().apply { putRaw(PreferencesFeatureVisibilityRepository.SETTINGS_KEY, raw) }
            assertEquals(raw, FeatureVisibilitySettings(), PreferencesFeatureVisibilityRepository(store).load())
        }
    }

    @Test
    fun oldAndFutureVisibilityFormatsAreNotMigrated() {
        (1..8).filter { it != 7 }.forEach { version ->
            val raw = """{"version":$version,"disabledFeatures":["weight"]}"""
            val store = FakeStringStore().apply { putRaw(PreferencesFeatureVisibilityRepository.SETTINGS_KEY, raw) }
            assertTrue(runCatching { FeatureVisibilitySettingsCodec.decode(raw) }.isFailure)
            assertEquals(FeatureVisibilitySettings(), PreferencesFeatureVisibilityRepository(store).load())
            assertEquals(raw, store.read(PreferencesFeatureVisibilityRepository.SETTINGS_KEY))
        }
    }

    @Test
    fun failedSaveIsObservableAndKeepsPersistedSettings() {
        val initial = FeatureVisibilitySettings(disabledFeatures = emptySet(), hiddenMeasurements = setOf("weight"))
        val store = FakeStringStore()
        val repository = PreferencesFeatureVisibilityRepository(store)
        assertTrue(repository.save(initial))
        store.acceptWrites = false

        val saved = repository.save(initial.withVisibility(DashboardFeature.Activity, visible = false, today = on(0)))

        assertFalse(saved)
        assertEquals(initial, PreferencesFeatureVisibilityRepository(store).load())
    }

    private class FakeStringStore : TrackerStringStore {
        private val values = mutableMapOf<String, String>()
        var acceptWrites = true

        override fun keys(): Set<String> = values.keys.toSet()
        override fun read(key: String): String? = values[key]

        override fun writeAll(values: Map<String, String>): Boolean = values.all { (key, value) -> write(key, value) }

        override fun write(key: String, value: String): Boolean {
            if (!acceptWrites) return false
            values[key] = value
            return true
        }

        fun putRaw(key: String, value: String) {
            values[key] = value
        }
    }
}

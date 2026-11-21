package com.example.workouttracker.persistence

import android.content.Context
import com.example.workouttracker.domain.LoggedViewKind
import com.example.workouttracker.domain.LoggedViewRef
import com.example.workouttracker.domain.checklist.Dated
import com.example.workouttracker.domain.settings.DashboardFeature
import com.example.workouttracker.domain.settings.FeatureVisibilitySettings
import com.example.workouttracker.domain.settings.ViewRef
import com.example.workouttracker.persistence.backup.PersonalDataLock
import com.example.workouttracker.persistence.json.decodeDated
import com.example.workouttracker.persistence.json.encode
import com.example.workouttracker.persistence.json.parseObject
import com.example.workouttracker.persistence.json.putOptional
import com.example.workouttracker.widget.MoWidgetProvider
import org.json.JSONObject

interface FeatureVisibilityRepository {
    fun load(): FeatureVisibilitySettings

    fun save(settings: FeatureVisibilitySettings): Boolean
}

class PreferencesFeatureVisibilityRepository internal constructor(
    private val store: TrackerStringStore,
    private val onWritten: () -> Unit = {},
) : FeatureVisibilityRepository {
    // A hidden feature is no longer one of Mo's tasks, so the home-screen Mo has to follow.
    constructor(context: Context) : this(
        personalStringStore(context, PREFERENCES_NAME),
        onWritten = { MoWidgetProvider.refresh(context) },
    )

    override fun load(): FeatureVisibilitySettings = runCatching {
        store.read(SETTINGS_KEY)?.let(FeatureVisibilitySettingsCodec::decode)
    }.getOrNull() ?: FeatureVisibilitySettings()

    override fun save(settings: FeatureVisibilitySettings): Boolean = runCatching {
        store.write(SETTINGS_KEY, FeatureVisibilitySettingsCodec.encode(settings))
    }.getOrDefault(false).also { written -> if (written) onWritten() }

    /**
     * Saves [settings] only when the current key is absent, checking and writing under one lock.
     */
    fun saveIfNoneStored(settings: FeatureVisibilitySettings): Boolean = runCatching {
        synchronized(PersonalDataLock) {
            store.read(SETTINGS_KEY) == null && store.write(SETTINGS_KEY, FeatureVisibilitySettingsCodec.encode(settings))
        }
    }.getOrDefault(false).also { written -> if (written) onWritten() }

    internal companion object {
        const val PREFERENCES_NAME = "momentum_feature_visibility"
        const val SETTINGS_KEY = "settings_v1"
    }
}

/** Dated visibility entries keyed by feature or tracker kind. Only the current format is read. */
internal object FeatureVisibilitySettingsCodec {
    private const val Version = 7
    private const val Features = "features"
    private const val Checklists = "checklists"
    private const val Measurements = "measurements"
    private const val Questions = "questions"
    private const val Time = "time"

    fun encode(settings: FeatureVisibilitySettings): String {
        fun group(entries: Map<String, Dated<Boolean>>): JSONObject? =
            entries.takeIf { it.isNotEmpty() }?.let { held -> JSONObject().apply { held.toSortedMap().forEach { (id, entry) -> put(id, entry.encode { it }) } } }
        fun logged(kind: LoggedViewKind) = settings.hidden.entries
            .mapNotNull { (key, entry) -> (key as? ViewRef.Logged)?.view?.takeIf { it.kind == kind }?.let { it.id to entry } }.toMap()
        return JSONObject()
            .put("version", Version)
            .putOptional(Features, group(settings.hidden.entries.mapNotNull { (key, entry) -> (key as? ViewRef.Feature)?.let { it.feature.storageId to entry } }.toMap()))
            .putOptional(Checklists, group(settings.hidden.entries.mapNotNull { (key, entry) -> (key as? ViewRef.Checklist)?.let { it.id to entry } }.toMap()))
            .putOptional(Measurements, group(logged(LoggedViewKind.Measurement)))
            .putOptional(Questions, group(logged(LoggedViewKind.Questions)))
            .putOptional(Time, group(logged(LoggedViewKind.Time)))
            .toString()
    }

    fun decode(raw: String): FeatureVisibilitySettings {
        val json = parseObject(raw)
        require(json.get("version") == Version) { "Unsupported visibility version" }
        return json.decodeVersion7()
    }

    private fun JSONObject.decodeVersion7(): FeatureVisibilitySettings {
        require(setOf("version", Features, Checklists, Measurements, Questions, Time).containsAll(keys().asSequence().toSet())) { "Unsupported visibility fields" }
        fun group(name: String, key: (String) -> ViewRef): List<Pair<ViewRef, Dated<Boolean>>> {
            if (!has(name)) return emptyList()
            val entries = get(name) as? JSONObject ?: error("$name must be an object")
            require(entries.length() > 0) { "$name is written only while it holds an entry" }
            return entries.keys().asSequence().map { id ->
                val entry = entries.decodeDated(id) { raw -> raw as? Boolean ?: error("An entry is a Boolean") }
                require(entry.isDated || entry.current) { "An entry is written only while hidden now or ever changed" }
                key(id) to entry
            }.toList()
        }
        fun id(name: String, id: String): String = id.takeIf { it.isNotBlank() } ?: error("$name entries are IDs")
        return FeatureVisibilitySettings(
            (group(Features) { id -> ViewRef.Feature(DashboardFeature.fromStorageId(id) ?: error("Unknown feature: $id")) } +
                group(Checklists) { ViewRef.Checklist(id(Checklists, it)) } +
                group(Measurements) { ViewRef.Logged(LoggedViewRef(LoggedViewKind.Measurement, id(Measurements, it))) } +
                group(Questions) { ViewRef.Logged(LoggedViewRef(LoggedViewKind.Questions, id(Questions, it))) } +
                group(Time) { ViewRef.Logged(LoggedViewRef(LoggedViewKind.Time, id(Time, it))) })
                .toMap(),
        )
    }
}

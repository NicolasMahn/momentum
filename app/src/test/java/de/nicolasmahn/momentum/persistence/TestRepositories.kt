package de.nicolasmahn.momentum.persistence

import de.nicolasmahn.momentum.domain.activity.ActivityLabels
import de.nicolasmahn.momentum.domain.activity.defaultActivityConfiguration
import de.nicolasmahn.momentum.domain.activity.routine.GymLabels
import de.nicolasmahn.momentum.domain.activity.testActivityConfiguration
import de.nicolasmahn.momentum.persistence.rows.MemoryRowStore
import de.nicolasmahn.momentum.persistence.rows.RowStore

/** The same Activity-only seed as a fresh installation. */
@Suppress("UNUSED_PARAMETER")
internal fun testConfigurationDefaults(reminders: TrackerStringStore? = null): ConfigurationDefaults =
    ConfigurationDefaults { testActivityConfiguration() }

internal val GermanDefaults: ConfigurationDefaults = ConfigurationDefaults { defaultActivityConfiguration(GermanActivityLabels) }

/** Activity's words as a German installation seeds them, every one unlike its English word. */
internal val GermanActivityLabels = ActivityLabels(
    activity = "Aktivität",
    bouldering = "Bouldern",
    cycling = "Radfahren",
    jogging = "Joggen",
    gym = GymLabels(
        gym = "Studio",
        dayA = "Tag A",
        dayB = "Tag B",
        dayC = "Tag C",
        exercise = mapOf(
            "bench_press" to "Bankdrücken", "back_squat" to "Kniebeuge", "row" to "Rudern", "lateral_raises" to "Seitheben",
            "triceps_pushdowns" to "Trizepsdrücken am Kabel", "pull_ups" to "Klimmzüge", "dips" to "Dips", "bulgarian_split_squat" to "Bulgarische Kniebeuge",
            "preacher_hammer_curls" to "Hammercurls an der Scottbank", "barbell_deadlift" to "Kreuzheben mit Langhantel", "overhead_press" to "Schulterdrücken",
            "face_pulls" to "Face Pulls", "hamstrings" to "Beinbeuger",
        )::getValue,
    ),
)

/** The rows that go with [store], as one installation has both: repositories made over the same store share them. */
internal fun rowsBeside(store: TrackerStringStore): MemoryRowStore =
    (store as? CurrentTrackerStore)?.rows ?: synchronized(RowsBeside) { RowsBeside.getOrPut(store) { MemoryRowStore() } }

private val RowsBeside = java.util.WeakHashMap<TrackerStringStore, MemoryRowStore>()

/** The tracker state the rows beside [store] hold, put back together into the document the codec writes. */
internal fun storedDocument(store: TrackerStringStore): org.json.JSONObject =
    org.json.JSONObject(de.nicolasmahn.momentum.persistence.rows.TrackerRows.assemble(rowsBeside(store).rows()).first)

internal fun trackerRepository(
    store: TrackerStringStore,
    reminders: TrackerStringStore? = null,
    onWritten: () -> Unit = {},
    onNewInstallation: () -> Unit = {},
    rows: RowStore = rowsBeside(store),
    keepBeforeRows: (Map<String, String>) -> Boolean = { true },
) = PreferencesTrackerRepository(store, onWritten, testConfigurationDefaults(reminders), onNewInstallation, rows, keepBeforeRows)

/** Global reminder settings are independent of the tracker store. */
@Suppress("UNUSED_PARAMETER")
internal fun reminderRepository(store: TrackerStringStore, trackerStore: TrackerStringStore) =
    PreferencesReminderSettingsRepository(store)

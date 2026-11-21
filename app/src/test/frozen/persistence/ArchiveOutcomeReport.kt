package com.example.workouttracker.persistence

import com.example.workouttracker.domain.compatibility.isSupplementsComplete

import com.example.workouttracker.core.text.UiText
import com.example.workouttracker.domain.AppState
import com.example.workouttracker.domain.CheckpointPart
import com.example.workouttracker.domain.LoggedView
import com.example.workouttracker.domain.compatibility.weightMeasurement
import com.example.workouttracker.domain.compatibility.eatingQuestions
import com.example.workouttracker.domain.compatibility.readingTime
import com.example.workouttracker.domain.compatibility.CareChecklistId
import com.example.workouttracker.domain.compatibility.LegacyCareSession
import com.example.workouttracker.domain.compatibility.isDone
import com.example.workouttracker.domain.checklist.setCheckpointRemind
import com.example.workouttracker.domain.holiday.HolidaySchedule
import com.example.workouttracker.domain.compatibility.isLoggedFor
import com.example.workouttracker.domain.mo.MoRecordIndex
import com.example.workouttracker.domain.mo.MoDayRecord
import com.example.workouttracker.domain.compatibility.moAsked
import com.example.workouttracker.domain.compatibility.moFreeDays
import com.example.workouttracker.domain.compatibility.moOverdue
import com.example.workouttracker.domain.mo.outcome
import com.example.workouttracker.domain.mo.outcomeText
import com.example.workouttracker.domain.mo.compatibility.InheritedMoEvaluation
import com.example.workouttracker.domain.mo.compatibility.InheritedDayReport
import com.example.workouttracker.domain.trackerRef
import com.example.workouttracker.domain.reminder.ReminderSettings
import com.example.workouttracker.domain.reminder.ReminderTime
import com.example.workouttracker.domain.settings.DashboardFeature
import com.example.workouttracker.domain.settings.FeatureVisibilitySettings
import com.example.workouttracker.domain.settings.ViewRef
import com.example.workouttracker.domain.compatibility.MoNeed
import com.example.workouttracker.domain.compatibility.SupplementsCheckpointId
import com.example.workouttracker.domain.compatibility.SupplementsChecklistId
import com.example.workouttracker.feature.statistics.StatisticsRange
import com.example.workouttracker.feature.statistics.statisticsSummary
import com.example.workouttracker.reminder.ChecklistReminderCoordinator
import com.example.workouttracker.reminder.ChecklistReminderGateway
import com.example.workouttracker.reminder.ChecklistReminderRuntime
import com.example.workouttracker.reminder.FeatureVisibilitySource
import com.example.workouttracker.reminder.ReminderDays
import com.example.workouttracker.reminder.ReminderSlot
import com.example.workouttracker.reminder.ReminderViewKind
import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import java.util.TimeZone
import javax.xml.parsers.DocumentBuilderFactory
import com.example.workouttracker.persistence.backup.BackupArchive
import com.example.workouttracker.persistence.backup.BackupSnapshot
import com.example.workouttracker.persistence.backup.validateRestoreCompatibility
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.w3c.dom.Element

/**
 * Writes every outcome a migration must keep, read from an unpacked personal archive, so the same
 * report from the code before and after a change can be compared line by line. It never runs on
 * its own: set `MOMENTUM_ARCHIVE_PREFS` to an unpacked `shared_prefs` folder (a copy, never the
 * archive itself) and `MOMENTUM_ARCHIVE_REPORT` to the file to write.
 *
 * The archive is loaded the way the app loads it, through the repositories, so the report also
 * covers the migration and the seeding of default configurations.
 */
class ArchiveOutcomeReport {
    @Test fun writeTheOutcomesOfAPersonalArchive() {
        val prefs = System.getenv("MOMENTUM_ARCHIVE_PREFS")
        val report = System.getenv("MOMENTUM_ARCHIVE_REPORT")
        assumeTrue("Set MOMENTUM_ARCHIVE_PREFS and MOMENTUM_ARCHIVE_REPORT to compare an archive", prefs != null && report != null)
        val zone = TimeZone.getDefault()
        // Eating cutoffs are judged in the default zone; the phone's is Berlin.
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Berlin"))
        try {
            File(report!!).writeText(render(File(prefs!!)).joinToString("\n", postfix = "\n"))
        } finally {
            TimeZone.setDefault(zone)
        }
    }

    /**
     * The archive as it is restores under the current validation, and once migrated it survives a
     * backup round trip with every record, the configurations and the reminder switch.
     */
    @Test fun aPersonalArchiveMigratesAndSurvivesBackupAndRestore() {
        val prefs = System.getenv("MOMENTUM_ARCHIVE_PREFS")?.let(::File)
        assumeTrue("Set MOMENTUM_ARCHIVE_PREFS to check an archive", prefs != null)
        fun stores() = BackupArchive.StoreNames.associateWith { readPreferences(prefs!!.resolve("$it.xml")) }
        validateRestoreCompatibility(BackupSnapshot(stores()))

        val migrated = stores().mapValues { (_, values) -> MemoryStore(values) }
        val tracker = migrated.getValue(PreferencesTrackerRepository.PREFERENCES_NAME)
        val reminderStore = migrated.getValue(PreferencesReminderSettingsRepository.PREFERENCES_NAME)
        val loaded = loadArchive(tracker, reminderStore)
        assertTrue(tracker.read(PreferencesTrackerRepository.STATE_V30_KEY) != null)
        assertTrue(reminderStore.read(PreferencesReminderSettingsRepository.SETTINGS_KEY) != null)

        val restored = BackupArchive.decode(BackupArchive.encode(BackupSnapshot(migrated.mapValues { it.value.values.toMap() })))
        validateRestoreCompatibility(restored)
        val restoredTracker = MemoryStore(restored.stores.getValue(PreferencesTrackerRepository.PREFERENCES_NAME))
        val again = loadArchive(restoredTracker, MemoryStore(restored.stores.getValue(PreferencesReminderSettingsRepository.PREFERENCES_NAME)))
        assertEquals(loaded.state, again.state)
        assertEquals(loaded.reminders, again.reminders)
    }

    private fun render(prefs: File): List<String> {
        val tracker = MemoryStore(readPreferences(prefs.resolve("workout_tracker.xml")))
        val reminderStore = MemoryStore(readPreferences(prefs.resolve("momentum_reminders.xml")))
        val visibility = PreferencesFeatureVisibilityRepository(MemoryStore(readPreferences(prefs.resolve("momentum_feature_visibility.xml")))).load()
        val holidays = tracker.read(PreferencesHolidayRepository.SettingsKey)?.let(HolidayScheduleCodec::decode) ?: HolidaySchedule()
        val rewardStart = parseHabitRuleStart(requireNotNull(tracker.read(PreferencesHabitRuleStartRepository.SettingsKey)))
        val passChoices = tracker.read(PreferencesPassChoiceRepository.SettingsKey)?.let(PassChoicesCodec::decode)
        val loaded = loadArchive(tracker, reminderStore)
        val state = loaded.state
        val reminders = loaded.reminders
        // Keep the report's historical line shape while each feature reminder now lives only in
        // its stored checkpoint configuration.
        fun switch(name: String, view: LoggedView?) = "$name=${name.replaceFirstChar(Char::uppercase)}ReminderSettings(enabled=${view?.checkpoint?.remind == true})"
        fun checkpointSwitch(checklistId: String, checkpointId: String) = state.checklists
            .firstOrNull { it.id == checklistId }
            ?.checkpoints
            ?.firstOrNull { it.id == checkpointId }
            ?.remind == true
        val printed = buildString {
            append("ReminderSettings(")
            append(switch("weight", state.weightMeasurement))
            append(", workout=${reminders.workout}")
            append(", supplements=SupplementReminderSettings(enabled=${checkpointSwitch(SupplementsChecklistId, SupplementsCheckpointId)})")
            append(", ${switch("reading", state.readingTime)}")
            append(", care=CareReminderSettings(morning=CareSessionReminderSettings(enabled=${checkpointSwitch(CareChecklistId, LegacyCareSession.MORNING.storageId)}), evening=CareSessionReminderSettings(enabled=${checkpointSwitch(CareChecklistId, LegacyCareSession.EVENING.storageId)}))")
            append(", ${switch("eating", state.eatingQuestions)}")
            append(", moCheckIn=${reminders.moCheckIn}")
            append(", fallbackAlarmTime=${reminders.fallbackAlarmTime}")
            reminders.fixedBedtime?.let { append(", fixedBedtime=$it") }
            append(')')
        }
        // The hidden features in the undated set form the report was frozen with, under the names and
        // in the order the features had before five of them became views of their kind.
        val lines = mutableListOf("reminders $printed", "visibility ${ReportedPlaces.filterNot { (_, view) -> visibility.isVisible(view) }.map { (name, _) -> name }.toSet()}")

        val records = MoRecordIndex(state, holidays)
        days(LocalDate.parse("2025-01-01"), LocalDate.parse("2027-12-31")).forEach { day ->
            val key = day.toString()
            lines += "day $key complete=${records.isSupplementsComplete(day)} " +
                "logged=${ReportedTasks.filter { state.isLoggedFor(it, key, holidays) }} " +
                "care=${LegacyCareSession.entries.map { state.isDone(MoNeed.Care, CheckpointPart(it.storageId), key, holidays) }}"
        }

        days(LocalDate.parse("2026-06-01"), LocalDate.parse("2026-10-10")).forEach { today ->
            val chosen = passChoices?.forDay(today).orEmpty()
            val evaluation = InheritedMoEvaluation(state, visibility, today, holidays, rewardStart, chosen)
            val mood = evaluation.mood
            val streak = evaluation.streak
            lines += "mood $today ${mood.outcome()}"
            lines += "streak $today ${outcomeText(streak)}"
            lines += "free $today ${moFreeDays(state, visibility, today, holidays, rewardStart)}"
            StatisticsRange.entries.forEach { range ->
                lines += "statistics $today $range ${outcomeText(statisticsSummary(state, today.toString(), range, visibility, inheritedHistory(streak.history)))}"
            }
            listOf(true, false).forEach { remind ->
                val switched = withCheckpointReminders(state, reminders, remind)
                val switchedMood = InheritedMoEvaluation(switched.state, visibility, today, holidays, rewardStart, chosen).current
                lines += "due $today $remind " + (0 until 24 * 60 step 5).joinToString(";") { minute ->
                    val now = LocalTime.of(minute / 60, minute % 60)
                    "${moAsked(switchedMood, switched.reminders, now)}${moOverdue(switchedMood, switched.reminders, now)}"
                }
                lines += "reminder $today $remind ${reminderShows(switched.state, switched.reminders, visibility, today.toString())}"
                lines += "care-reminder $today $remind " +
                    LegacyCareSession.entries.map { careReminderShows(switched.state, switched.reminders, visibility, today.toString(), it) }
            }
        }
        return lines
    }

    private class Loaded(val state: AppState, val reminders: ReminderSettings)

    /** The places the report has always named, in the order it named them, each with the view that is hidden in its name. */
    private val ReportedPlaces: List<Pair<String, ViewRef>> = listOf(
        "Activity" to ViewRef.Feature(DashboardFeature.Activity),
        "Weight" to MoNeed.Weight.view,
        "Supplements" to MoNeed.Supplements.view,
        "Reading" to MoNeed.Reading.view,
        "Substances" to ViewRef.Feature(DashboardFeature.Substances),
        "Care" to MoNeed.Care.view,
        "Eating" to MoNeed.Eating.view,
        "Statistics" to ViewRef.Feature(DashboardFeature.Statistics),
    )

    /** Those of [ReportedPlaces] that can be logged, in that order: Substances and Statistics never were. */
    private val ReportedTasks = listOf(MoNeed.Activity, MoNeed.Weight, MoNeed.Supplements, MoNeed.Reading, MoNeed.Care, MoNeed.Eating)

    /** Statistics accepts canonical day records; map the frozen report's inherited identities back to references. */
    private fun inheritedHistory(history: List<InheritedDayReport>) =
        history.map { row ->
            MoDayRecord(
                date = row.date,
                mark = row.mark,
                coveredTrackers = row.covered.mapTo(linkedSetOf()) { it.view.trackerRef },
                restedTrackers = if (row.rested) setOf(MoNeed.Activity.view.trackerRef) else emptySet(),
            )
        }

    // ---- The part that differs between code versions: how the archive is loaded and switched.

    /** The records and settings exactly as the app's repositories load them, migrations included. */
    private fun loadArchive(tracker: MemoryStore, reminderStore: MemoryStore): Loaded = Loaded(
        state = requireNotNull(trackerRepository(tracker, reminderStore).loadIfReadable()) { "Unreadable archive" },
        reminders = reminderRepository(reminderStore, tracker).load(),
    )

    /** The same records with Supplements' and Care's reminders switched in their checkpoints. */
    private fun withCheckpointReminders(state: AppState, reminders: ReminderSettings, remind: Boolean) = Loaded(
        state = LegacyCareSession.entries.fold(setCheckpointRemind(state, SupplementsChecklistId, SupplementsCheckpointId, remind)) { switched, session ->
            setCheckpointRemind(switched, CareChecklistId, session.storageId, remind)
        },
        reminders = reminders,
    )

    private fun careReminderShows(state: AppState, reminders: ReminderSettings, visibility: FeatureVisibilitySettings, today: String, session: LegacyCareSession): Boolean =
        checkpointReminderShows(state, reminders, visibility, today, CareChecklistId, session.storageId)

    private fun reminderShows(state: AppState, reminders: ReminderSettings, visibility: FeatureVisibilitySettings, today: String): Boolean =
        checkpointReminderShows(state, reminders, visibility, today, SupplementsChecklistId, SupplementsCheckpointId)

    /** Whether the reminder of [checklistId]'s [checkpointId] rang today with a notification, through the coordinator every checkpoint rings through. */
    private fun checkpointReminderShows(
        state: AppState,
        reminders: ReminderSettings,
        visibility: FeatureVisibilitySettings,
        today: String,
        checklistId: String,
        checkpointId: String,
    ): Boolean {
        var shown = false
        ChecklistReminderCoordinator(
            trackerRepository = object : TrackerRepository {
                override fun load() = state
                override fun save(state: AppState) = error("read only")
                override fun update(transform: (AppState) -> AppState) = error("read only")
            },
            featureVisibility = FeatureVisibilitySource { visibility },
            gateway = AcceptingGateway,
            runtime = ForgettingRuntime,
            notifier = { _, _, _, _, _, _ -> shown = true; true },
            loggedNotifier = { _, _, _, _ -> true },
            reminderSettings = { reminders },
            todayKey = { today },
        ).onReminderDue(ReminderViewKind.Checklist, checklistId, checkpointId)
        return shown
    }

    private object AcceptingGateway : ChecklistReminderGateway {
        override fun canScheduleExact() = true
        override fun shown() = emptySet<String>()
        override fun schedule(slot: ReminderSlot, earliestDate: LocalDate?, fallbackAlarmTime: ReminderTime, fixedBedtime: ReminderTime?) = true
        override fun cancel(key: String) = Unit
        override fun dismiss(key: String) = Unit
        override fun keepUnsaved(key: String, why: UiText) = false
    }

    private object ForgettingRuntime : ChecklistReminderRuntime {
        override fun scheduled() = emptySet<String>()
        override fun record(keys: Set<String>) = Unit
        override fun posted() = emptyMap<String, ReminderDays>()
        override fun posted(key: String, days: ReminderDays) = Unit
        override fun dismissed(key: String) = Unit
        override fun earliest() = emptyMap<String, LocalDate>()
        override fun earliest(key: String, day: LocalDate) = Unit
    }

    // ---- Shared by every code version.

    private class MemoryStore(initial: Map<String, String>) : TrackerStringStore {
        val values = initial.toMutableMap()
        override fun read(key: String) = values[key]
        override fun write(key: String, value: String): Boolean = true.also { values[key] = value }
        override fun writeAll(values: Map<String, String>): Boolean = values.all { (key, value) -> write(key, value) }
    }

    private fun readPreferences(file: File): Map<String, String> {
        if (!file.exists()) return emptyMap()
        val root = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).documentElement
        return (0 until root.childNodes.length).map { root.childNodes.item(it) }.filterIsInstance<Element>()
            .filter { it.tagName == "string" }
            .associate { it.getAttribute("name") to it.textContent }
    }

    private fun days(first: LocalDate, last: LocalDate) =
        generateSequence(first) { it.plusDays(1) }.takeWhile { it <= last }.toList()
}

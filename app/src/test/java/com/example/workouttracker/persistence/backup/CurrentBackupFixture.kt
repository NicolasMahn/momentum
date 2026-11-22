package com.example.workouttracker.persistence.backup

import com.example.workouttracker.domain.checklist.ViewIcon
import com.example.workouttracker.domain.checklist.ViewHue
import com.example.workouttracker.domain.LoggedViewKind
import com.example.workouttracker.domain.LoggedViewRef
import com.example.workouttracker.domain.AppState
import com.example.workouttracker.domain.TrackerRef
import com.example.workouttracker.domain.TrackerTypeId
import com.example.workouttracker.domain.activity.*
import com.example.workouttracker.domain.activity.route.Route
import com.example.workouttracker.domain.activity.route.RoutePoint
import com.example.workouttracker.domain.activity.routine.*
import com.example.workouttracker.domain.checklist.*
import com.example.workouttracker.domain.holiday.*
import com.example.workouttracker.domain.measurement.*
import com.example.workouttracker.domain.mo.TrackerMoPolicy
import com.example.workouttracker.domain.questions.*
import com.example.workouttracker.domain.reminder.*
import com.example.workouttracker.domain.settings.*
import com.example.workouttracker.domain.substance.*
import com.example.workouttracker.domain.time.*
import com.example.workouttracker.persistence.*
import com.example.workouttracker.persistence.json.AppStateJsonCodec
import com.example.workouttracker.persistence.rows.RowsBackupStore
import com.example.workouttracker.persistence.rows.TrackerRows
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

/** A complete current installation, with arbitrary tracker IDs and records whose configuration was deleted. */
internal object CurrentBackupFixture {
    private val day = LocalDate.parse("2026-10-13")
    private fun <T> changed(before: T, after: T) = Dated(listOf(Dated.Revision(before), Dated.Revision(after, day)))
    private val checkpoint = Checkpoint(DayCheckpointId, CheckpointTime.Fixed(LocalTime.of(9, 15)), remind = true)
    private val periods = listOf(RewardPeriod(day, day.plusDays(3)), RewardPeriod(day.plusDays(5)))
    private val bank = ChecklistRewards(changed(2, 4), changed(3, 5), periods)

    val state = AppState(
        activity = ActivityConfiguration(
            label = "Move 🧗", icon = ViewIcon.Bolt, hue = ViewHue.Plum,
            week = changed(WeekPlan.of(DayOfWeek.MONDAY, DayOfWeek.FRIDAY), WeekPlan.of(DayOfWeek.TUESDAY, DayOfWeek.SATURDAY)),
            whenAway = changed(WhenAway.Due, WhenAway.Excused), awayDays = Dated.always(1),
            rewards = ActivityRewards(changed(2, 4), periods = periods),
            activities = listOf(ListedActivity("act_climb", "Climbing", ViewIcon.Bolt, removed = true,
                routine = Routine(listOf(RoutineDay("route_a", "Technique", listOf(
                    RoutineExercise("pull", "Pull ups", 2, 4..10, null, 6),
                ))))),
                ListedActivity("act_run", "Running", ViewIcon.Running, recordsRoute = true)),
            mo = TrackerMoPolicy(true, MoMotif.Running),
            logsDuration = true,
        ),
        sessions = listOf(WorkoutSession("session", "act_climb", "route_a", "2026-10-14", 102,
            listOf(ExerciseLog("pull", "Original exercise name", listOf(LoggedSet(-15.0, 8.0), LoggedSet(null, 6.0)))), 75)),
        activityEntries = listOf(
            ActivityEntry("run", "act_run", "Running", "2026-10-14", 104, 1, route = Route(listOf(
                listOf(RoutePoint.of(48.77586, 9.18293, 0), RoutePoint.of(48.77612, 9.18355, 9)),
                listOf(RoutePoint.of(48.77650, 9.18421, 70), RoutePoint.of(-0.00001, -179.99999, 81)),
            ))),
            ActivityEntry("activity", "act_climb", "Original activity name", "2026-10-14", 103, 30, "private note 🧗"),
        ),
        checklists = listOf(ChecklistConfiguration(
            id = "chk_custom", label = "Evening", icon = ViewIcon.Sleep, hue = ViewHue.Plum,
            checkpoints = listOf(checkpoint), rewards = bank,
            items = listOf(ChecklistItem("stretch", "Stretch", whenAway = changed(WhenAway.Due, WhenAway.Excused)),
                ChecklistItem("water", "Water", exceptionsOnly = changed(false, true))),
            about = changed(AboutDay.Today, AboutDay.Yesterday), mo = TrackerMoPolicy(true, MoMotif.Teeth),
        )),
        checklistDays = listOf(ChecklistDay("chk_custom", "2026-10-14", 104, setOf("day/stretch", "old/removed"), setOf("day/water"))),
        measurements = listOf(MeasurementConfiguration(
            id = "msr_custom", label = "Temperature", icon = ViewIcon.Ruler, hue = ViewHue.Plum,
            unit = "°C", step = MeasurementStep("0.05"), rewards = MeasurementBank.Standard(bank),
            whenAway = changed(WhenAway.Due, WhenAway.Excused), checkpoint = checkpoint,
            valuePolicy = MeasurementValuePolicy.Finite, mo = TrackerMoPolicy(true, MoMotif.Scale),
        )),
        measurementLogs = listOf(MeasurementLog("msr_custom", "2026-10-14", 105, -2.5),
            MeasurementLog("deleted_measurement", "2026-10-14", 106, 87.65),
            MeasurementLog("deleted_measurement", "2026-10-14", 106, 87.65)),
        questions = listOf(QuestionsConfiguration(
            id = "qst_custom", label = "Rest", icon = ViewIcon.Sleep, hue = ViewHue.Navy,
            questions = listOf(Question("rest", "How rested?", listOf(Answer("low", "Low"), Answer("high", "High")), "Include naps")),
            about = changed(AboutDay.Today, AboutDay.Yesterday), rewards = bank,
            whenAway = changed(WhenAway.Due, WhenAway.Excused), checkpoint = checkpoint,
            madeOn = day.minusDays(2), mo = TrackerMoPolicy(true, MoMotif.Food),
        )),
        questionEntries = listOf(QuestionEntry("entry", "qst_custom", "2026-10-14", 107,
            mapOf("rest" to "high", "removed_prompt" to "old_answer"), "private note")),
        time = listOf(TimeConfiguration(
            id = "tim_custom", label = "Piano", icon = ViewIcon.Music, hue = ViewHue.Navy,
            goal = changed(60, 120), rewards = TimeRewards(changed(2, 5), periods),
            whenAway = changed(WhenAway.Due, WhenAway.Excused), checkpoint = checkpoint,
            mo = TrackerMoPolicy(true, MoMotif.Book),
        )),
        timeLogs = listOf(TimeLog("tim_custom", "2026-10-14", 108, 45), TimeLog("deleted_timer", "2026-10-13", 109)),
        recordedTimeSessions = setOf(1757930400000L, 1757930401000L),
        substances = SubstanceSettings(
            tracked = setOf("coffee", "sub_own"), own = listOf(OwnSubstance("sub_own", "Personal", SubstanceAmountUnit.PILLS)),
            stayingAway = changed(emptySet(), setOf("sub_own")),
            checkIn = SubstanceCheckIn(true, CheckpointTime.BeforeSleep(20)),
            rewards = bank, whenAway = changed(WhenAway.Due, WhenAway.Excused),
            goal = changed(SubstanceGoal.Logging, SubstanceGoal.StayingAway), exceptionsOnly = changed(true, false),
            mo = TrackerMoPolicy(true, MoMotif.Pills),
            label = "Evenings", icon = ViewIcon.Drink, hue = ViewHue.Berry,
        ),
        substanceLogs = listOf(SubstanceLog("use", "sub_own", "2026-10-14", 110, 0.5, SubstanceAmountUnit.PILLS)),
        substanceNoneDays = listOf(SubstanceNoneDay("2026-10-13", 111)),
    )
    val reminders = ReminderSettings(
        workout = WorkoutReminderSettings(true, ReminderTime(19, 30)),
        moCheckIn = MoCheckInSettings(false, ReminderTime(21, 5)),
        fallbackAlarmTime = ReminderTime(7, 15), fixedBedtime = ReminderTime(23, 40),
    )
    val visibility = FeatureVisibilitySettings(hiddenMeasurements = setOf("deleted_measurement"))
        .withVisibility(LoggedViewRef(LoggedViewKind.Time, "tim_custom"), false, day)
    val holidays = HolidaySchedule(listOf(DayRange(day, day.plusDays(2))), listOf(DayRange(day.plusDays(1), day.plusDays(3))))
    val passChoices = PassChoices(
        day, setOf(TrackerRef(TrackerTypeId("time"), "tim_custom"), TrackerRef(TrackerTypeId("checklist"), "chk_custom")),
        checkpoints = mapOf(TrackerRef(TrackerTypeId("checklist"), "chk_custom") to setOf("morning", "evening")),
        // 2026-10-12 is the Monday of the fixture's week.
        weeks = mapOf(TrackerRef(TrackerTypeId("time"), "tim_custom") to day.minusDays(1)),
    )
    val ruleStart: LocalDate = day.minusDays(10)

    /** A backup as a build before the rows made it: the tracker state as the document `state_v32`, and the rows store empty. */
    fun snapshot() = BackupArchive.withAddedStores(documentStores())

    /** The same installation as a current backup holds it: the tracker state as rows, and no document. */
    fun rowsSnapshot(): BackupSnapshot {
        var next = 0
        val rows = TrackerRows.changes(emptyList(), state, newId = { "row-${next++}" }).written
        val stores = documentStores()
        val tracker = stores.getValue(PreferencesTrackerRepository.PREFERENCES_NAME) -
            setOf(PreferencesTrackerRepository.STATE_V32_KEY, PreferencesTrackerRepository.RECORDED_TIME_SESSIONS_KEY)
        return BackupArchive.withAddedStores(stores + mapOf(
            PreferencesTrackerRepository.PREFERENCES_NAME to tracker,
            RowsBackupStore.StoreName to RowsBackupStore.values(rows, PreferencesTrackerRepository.RowsFormat),
        ))
    }

    private fun documentStores() = mapOf(
        PreferencesTrackerRepository.PREFERENCES_NAME to mapOf(
            PreferencesTrackerRepository.STATE_V32_KEY to AppStateJsonCodec.encode(state),
            PreferencesTrackerRepository.RECORDED_TIME_SESSIONS_KEY to RecordedTimeSessionsCodec.encode(state.recordedTimeSessions),
            PreferencesHolidayRepository.SettingsKey to HolidayScheduleCodec.encode(holidays),
            PreferencesPassChoiceRepository.SettingsKey to PassChoicesCodec.encode(passChoices),
            PreferencesHabitRuleStartRepository.SettingsKey to ruleStart.toString(),
        ),
        PreferencesReminderSettingsRepository.PREFERENCES_NAME to mapOf(PreferencesReminderSettingsRepository.SETTINGS_KEY to ReminderSettingsCodec.encode(reminders)),
        PreferencesFeatureVisibilityRepository.PREFERENCES_NAME to mapOf(PreferencesFeatureVisibilityRepository.SETTINGS_KEY to FeatureVisibilitySettingsCodec.encode(visibility)),
    )
}

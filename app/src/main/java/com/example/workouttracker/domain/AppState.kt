package com.example.workouttracker.domain

import com.example.workouttracker.domain.activity.ActivityConfiguration
import com.example.workouttracker.domain.activity.ActivityEntry
import com.example.workouttracker.domain.activity.routine.WorkoutSession
import com.example.workouttracker.domain.checklist.ChecklistConfiguration
import com.example.workouttracker.domain.checklist.ChecklistDay
import com.example.workouttracker.domain.measurement.MeasurementConfiguration
import com.example.workouttracker.domain.measurement.MeasurementLog
import com.example.workouttracker.domain.questions.QuestionEntry
import com.example.workouttracker.domain.questions.QuestionEntryOrder
import com.example.workouttracker.domain.questions.QuestionsConfiguration
import com.example.workouttracker.domain.substance.SubstanceLog
import com.example.workouttracker.domain.substance.SubstanceNoneDay
import com.example.workouttracker.domain.substance.SubstanceSettings
import com.example.workouttracker.domain.time.TimeConfiguration
import com.example.workouttracker.domain.time.TimeLog
import com.example.workouttracker.domain.time.TimeLogOrder

data class AppState(
    /**
     * Every measurement's records, each naming its measurement by ID, in the order storage keeps:
     * by date, newest first, and those of one date as they were stored. A record of an ID no
     * configuration knows is kept as it is and read by nothing.
     */
    val measurementLogs: List<MeasurementLog> = emptyList(),
    /** Routine sessions, each naming its activity and routine day, newest first. */
    val sessions: List<WorkoutSession> = emptyList(),
    /** Recorded activity entries, each naming its configured activity, newest first. */
    val activityEntries: List<ActivityEntry> = emptyList(),
    /**
     * Activity's configuration, saved with its records like the checklists: its days a week, its
     * holiday rule and its bank judge weeks, every entry names one of its activities by ID and
     * every session one of its routine days. Null when the tracker is not configured; any stored
     * records remain facts without enrolling the absent tracker in rewards.
     */
    val activity: ActivityConfiguration? = null,
    /** Saved with the records, because a day can only be read against the rules it had. */
    val checklists: List<ChecklistConfiguration> = emptyList(),
    val checklistDays: List<ChecklistDay> = emptyList(),
    /**
     * Numerical tracker configurations, saved with their records. Reward and away policies judge
     * days independently of recorded values; every record names its measurement's stable ID.
     */
    val measurements: List<MeasurementConfiguration> = emptyList(),
    val substanceLogs: List<SubstanceLog> = emptyList(),
    /** The days the person said "none" on; a day holds uses or one of these, never both. */
    val substanceNoneDays: List<SubstanceNoneDay> = emptyList(),
    /** Which substances are tracked and the person's own; saved with the records, which name them by ID. */
    val substances: SubstanceSettings = SubstanceSettings(),
    /**
     * Question tracker configurations, saved with their entries. Each entry names its view's
     * stable ID; dated policies judge the due day separately from the day an entry is about.
     */
    val questions: List<QuestionsConfiguration> = emptyList(),
    /**
     * Every questions view's entries, one per view and date, each naming its view by ID, in the
     * order storage keeps ([QuestionEntryOrder]). An entry of an ID no configuration knows is
     * preserved and ignored until its configuration is available.
     */
    val questionEntries: List<QuestionEntry> = emptyList(),
    /**
     * Every timer's configuration, saved with its logs: a view's goal, its bank and its away rule
     * judge weeks, and every log names its view by the ID it has here.
     */
    val time: List<TimeConfiguration> = emptyList(),
    /**
     * Every time view's logs, one per view and date, each naming its view by ID, in the order
     * storage keeps ([TimeLogOrder]). A log of an ID no configuration knows is preserved and
     * ignored until its configuration is available.
     */
    val timeLogs: List<TimeLog> = emptyList(),
    /**
     * Start times of the timer sessions whose minutes [timeLogs] already holds. Saved in the same
     * commit as those minutes, so a session that comes back after the process died is recognised
     * however the day's records changed since.
     * Bookkeeping, never a record.
     */
    val recordedTimeSessions: Set<Long> = emptySet(),
)

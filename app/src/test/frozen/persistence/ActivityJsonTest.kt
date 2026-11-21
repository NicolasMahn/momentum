package com.example.workouttracker.persistence.json

import com.example.workouttracker.persistence.json.introducedInFormat

import com.example.workouttracker.domain.AppState
import com.example.workouttracker.domain.activity.ActivityEdit
import com.example.workouttracker.domain.activity.ActivityRewards
import com.example.workouttracker.domain.checklist.RewardPeriod
import com.example.workouttracker.domain.activity.ActivityEntry
import com.example.workouttracker.domain.activity.ActivityTokenStep
import com.example.workouttracker.domain.activity.ActivityTokenSteps
import com.example.workouttracker.domain.activity.DefaultActivityWeek
import com.example.workouttracker.domain.activity.WeekPlan
import com.example.workouttracker.domain.activity.ListedActivity
import com.example.workouttracker.domain.activity.SeededActivityRules
import com.example.workouttracker.domain.activity.applyEdit
import com.example.workouttracker.domain.activity.routine.ExerciseLog
import com.example.workouttracker.domain.activity.routine.ExerciseWeight
import com.example.workouttracker.domain.activity.routine.LoggedSet
import com.example.workouttracker.domain.activity.routine.RoutineDay
import com.example.workouttracker.domain.activity.routine.RoutineExercise
import com.example.workouttracker.domain.activity.routine.WorkoutSession
import com.example.workouttracker.domain.activity.requireStorableActivity
import com.example.workouttracker.domain.activity.testActivityConfiguration
import com.example.workouttracker.domain.activity.withActivity
import com.example.workouttracker.domain.checklist.ViewHue
import com.example.workouttracker.domain.checklist.ViewIcon
import com.example.workouttracker.domain.checklist.WhenAway
import com.example.workouttracker.domain.measurement.withWeight
import com.example.workouttracker.domain.questions.withEating
import com.example.workouttracker.domain.time.withReading
import com.example.workouttracker.persistence.FrozenVersion29WithActivities
import com.example.workouttracker.persistence.claimedAs
import com.example.workouttracker.persistence.legacyTrackerJson
import com.example.workouttracker.persistence.plainJson
import com.example.workouttracker.persistence.stateOfFrozenVersion29WithActivities
import java.time.DayOfWeek
import java.time.LocalDate
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ActivityJsonTest {
    private val day = LocalDate.parse("2026-10-13")
    private val seeded = AppState().withWeight().withEating().withReading().withActivity()

    /**
     * The seeded configuration with every dated setting changed on a day, an activity added and
     * one removed, a routine day and an exercise added, changed and removed, an exercise without a
     * weight, and a routine on a second activity.
     */
    private val configured = testActivityConfiguration()
        .applyEdit(ActivityEdit.Rename("Sport"), day)
        .applyEdit(ActivityEdit.SetIcon(ViewIcon.Running), day)
        .applyEdit(ActivityEdit.SetHue(ViewHue.Teal), day)
        .applyEdit(ActivityEdit.SetWeek(DefaultActivityWeek.withDays(setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY)).planning(DayOfWeek.THURSDAY, "cycling")), day)
        .applyEdit(ActivityEdit.SetWhenAway(WhenAway.Excused), day.plusDays(1))
        .applyEdit(ActivityEdit.SetAwayDays(3), day.plusDays(2))
        .applyEdit(ActivityEdit.SetBankSize(5), day.plusDays(3))
        .applyEdit(ActivityEdit.SetTokenSteps(ActivityTokenSteps(listOf(ActivityTokenStep(0, 1), ActivityTokenStep(1, 2)))), day.plusDays(4))
        .applyEdit(ActivityEdit.AddActivity(ListedActivity("act_yoga", "Yoga", ViewIcon.Bolt)), day)
        .applyEdit(ActivityEdit.AddActivity(ListedActivity("act_swim", "Swimming", ViewIcon.Swimming)), day)
        .applyEdit(ActivityEdit.RemoveActivity("act_swim"), day)
        .applyEdit(ActivityEdit.AddRoutineDay("gym", RoutineDay("day_d", "Legs", listOf(RoutineExercise("ex_1", "Leg press", 4, 10..12, ExerciseWeight(80.0, 5.0), 10)))), day)
        .applyEdit(ActivityEdit.AddExercise("day_d", RoutineExercise("ex_2", "Calf raise", 3, 12..15, ExerciseWeight(40.0, 2.5), 12)), day)
        .applyEdit(ActivityEdit.AddExercise("day_d", RoutineExercise("ex_3", "Plank", 3, 1..1, null, 1)), day)
        .applyEdit(ActivityEdit.RemoveExercise("ex_2"), day)
        .applyEdit(ActivityEdit.RemoveRoutineDay("C"), day)
        .applyEdit(ActivityEdit.AddRoutineDay("act_yoga", RoutineDay("day_flow", "Flow", listOf(RoutineExercise("ex_sun", "Sun salutation", 2, 5..10, null, 5)))), day)

    private val entries = listOf(
        ActivityEntry("e2", "act_yoga", "Yoga", "2026-10-14", 30, 45, "Stretchy"),
        ActivityEntry("e1", "act_gone", "Bowling", "2026-10-13", 20),
    )
    private val sessions = listOf(
        WorkoutSession("2026-10-14-day_d", "gym", "day_d", "2026-10-14", 40, listOf(ExerciseLog("ex_1", "Leg press", listOf(LoggedSet(80.0, 10.0), LoggedSet(85.0, 9.5))), ExerciseLog("ex_3", "Plank", listOf(LoggedSet(null, 1.0)))), durationMinutes = 50),
        WorkoutSession("2026-10-13-day_flow", "act_yoga", "day_flow", "2026-10-13", 35, listOf(ExerciseLog("ex_sun", "Sun salutation", listOf(LoggedSet(null, 8.0), LoggedSet(2.0, 6.0))))),
        WorkoutSession("2026-10-12-C", "gym", "C", "2026-10-12", 10, listOf(ExerciseLog("face_pulls", "Face Pulls", listOf(LoggedSet(17.5, 15.0))), ExerciseLog("ex_old", "Shrugs", emptyList()))),
    )
    private val state = AppState(sessions = sessions, activityEntries = entries).withWeight().withEating().withReading().withActivity(configured)

    private fun refused(what: String, raw: String) {
        assertThrows(what, RuntimeException::class.java) { AppStateJsonCodec.decodeVersion30(raw) }
    }

    @Test fun theSeededConfigurationIsStoredInTheShapeOfTheContract() {
        val stored = JSONObject(AppStateJsonCodec.encodeVersion30(seeded))
        val expected = JSONObject(
            """{"view":"activity","id":"activity","label":"Activity","icon":"hiking","hue":"green",""" +
                """"week":{"days":[{"day":"MON","activity":"gym"},{"day":"TUE","activity":"bouldering"},{"day":"WED","activity":"gym"},{"day":"THU"},{"day":"FRI","activity":"gym"}]},""" +
                """"whenAway":"lowerTarget","awayDays":2,"rewards":{"maxPasses":3,"tokenSteps":{"steps":[{"daysMore":0,"tokens":1}]}},""" +
                """"activities":[{"id":"gym","label":"Gym","icon":"dumbbell","routine":{"days":[""" +
                """{"id":"A","label":"Day A","exercises":[""" +
                """{"id":"bench_press","label":"Bench Press","sets":3,"reps":{"from":8,"to":10},"start":{"weightKg":22.5,"reps":10},"stepKg":2.5},""" +
                """{"id":"back_squat","label":"Back Squat","sets":3,"reps":{"from":8,"to":10},"start":{"weightKg":60,"reps":9},"stepKg":1},""" +
                """{"id":"row","label":"Row","sets":3,"reps":{"from":8,"to":12},"start":{"weightKg":40,"reps":8},"stepKg":1},""" +
                """{"id":"lateral_raises","label":"Lateral Raises","sets":3,"reps":{"from":12,"to":15},"start":{"weightKg":4,"reps":12},"stepKg":1},""" +
                """{"id":"triceps_pushdowns","label":"Triceps Pushdowns","sets":2,"reps":{"from":12,"to":15},"start":{"weightKg":12.5,"reps":15},"stepKg":2.5}]},""" +
                """{"id":"B","label":"Day B","exercises":[""" +
                """{"id":"pull_ups","label":"Pull Ups","sets":3,"reps":{"from":6,"to":10},"start":{"weightKg":-19,"reps":8},"stepKg":4},""" +
                """{"id":"dips","label":"Dips","sets":3,"reps":{"from":8,"to":10},"start":{"weightKg":0,"reps":8},"stepKg":1},""" +
                """{"id":"bulgarian_split_squat","label":"Bulgarian Split Squat","sets":3,"reps":{"from":8,"to":10},"start":{"weightKg":15,"reps":10},"stepKg":2.5},""" +
                """{"id":"preacher_hammer_curls","label":"Preacher Hammer Curls","sets":3,"reps":{"from":10,"to":12},"start":{"weightKg":10,"reps":10},"stepKg":2.5}]},""" +
                """{"id":"C","label":"Day C","exercises":[""" +
                """{"id":"barbell_deadlift","label":"Barbell Deadlift","sets":3,"reps":{"from":6,"to":8},"start":{"weightKg":70,"reps":8},"stepKg":1},""" +
                """{"id":"overhead_press","label":"Overhead Press","sets":3,"reps":{"from":6,"to":10},"start":{"weightKg":10,"reps":8},"stepKg":1},""" +
                """{"id":"face_pulls","label":"Face Pulls","sets":3,"reps":{"from":15,"to":20},"start":{"weightKg":17.5,"reps":15},"stepKg":2.5},""" +
                """{"id":"hamstrings","label":"Hamstrings","sets":3,"reps":{"from":10,"to":12},"start":{"weightKg":50,"reps":10},"stepKg":2.5}]}]}},""" +
                """{"id":"bouldering","label":"Bouldering","icon":"bouldering"},{"id":"cycling","label":"Cycling","icon":"bicycle"},{"id":"jogging","label":"Jogging","icon":"running"}]}""",
        )
        assertEquals(plainJson(expected), plainJson(stored.getJSONObject("activity")))
        assertEquals(30, stored.getInt("schemaVersion"))
        assertFalse(stored.has("activityTypes"))
        assertEquals(seeded, AppStateJsonCodec.decodeVersion30(stored.toString()))
    }

    /**
     * A dated setting is its revisions once it changed; a removed activity, day or exercise says so
     * and nothing else does; an exercise without a weight has no start weight and no step; an
     * activity carries its routine only while it has one; an entry holds its minutes and notes only
     * where it has them; and a session names its activity, a set its weight only where it has one.
     */
    @Test fun aChangedConfigurationAndItsRecordsAreStoredAsWritten() {
        val stored = JSONObject(AppStateJsonCodec.encodeVersion30(state))
        val configuration = stored.getJSONObject("activity")
        assertEquals(
            plainJson(
                JSONArray(
                    """[{"value":{"days":[{"day":"MON","activity":"gym"},{"day":"TUE","activity":"bouldering"},{"day":"WED","activity":"gym"},{"day":"THU"},{"day":"FRI","activity":"gym"}]}},""" +
                        """{"value":{"days":[{"day":"MON","activity":"gym"},{"day":"TUE","activity":"bouldering"},{"day":"WED","activity":"gym"},{"day":"THU","activity":"cycling"}]},"from":"2026-10-13"}]""",
                ),
            ),
            plainJson(configuration.getJSONArray("week")),
        )
        assertEquals(plainJson(JSONArray("""[{"value":"lowerTarget"},{"value":"excused","from":"2026-10-14"}]""")), plainJson(configuration.getJSONArray("whenAway")))
        assertEquals(plainJson(JSONArray("""[{"value":2},{"value":3,"from":"2026-10-15"}]""")), plainJson(configuration.getJSONArray("awayDays")))
        assertEquals(
            plainJson(JSONObject("""{"maxPasses":[{"value":3},{"value":5,"from":"2026-10-16"}],"tokenSteps":[{"value":{"steps":[{"daysMore":0,"tokens":1}]}},{"value":{"steps":[{"daysMore":0,"tokens":1},{"daysMore":1,"tokens":2}]},"from":"2026-10-17"}]}""")),
            plainJson(configuration.getJSONObject("rewards")),
        )
        assertEquals("Sport" to "running", configuration.getString("label") to configuration.getString("icon"))
        val activities = configuration.getJSONArray("activities")
        assertEquals(
            plainJson(JSONObject("""{"id":"act_yoga","label":"Yoga","icon":"bolt","routine":{"days":[{"id":"day_flow","label":"Flow","exercises":[{"id":"ex_sun","label":"Sun salutation","sets":2,"reps":{"from":5,"to":10},"start":{"reps":5}}]}]}}""")),
            plainJson(activities.getJSONObject(4)),
        )
        assertEquals(plainJson(JSONObject("""{"id":"act_swim","label":"Swimming","icon":"swimming","removed":true}""")), plainJson(activities.getJSONObject(5)))
        assertTrue((0 until 4).none { activities.getJSONObject(it).has("removed") })
        assertTrue((1 until 4).none { activities.getJSONObject(it).has("routine") })
        val gym = activities.getJSONObject(0).getJSONObject("routine")
        assertEquals(true, gym.getJSONArray("days").getJSONObject(2).getBoolean("removed"))
        val legs = gym.getJSONArray("days").getJSONObject(3)
        assertEquals(
            plainJson(JSONObject("""{"id":"day_d","label":"Legs","exercises":[{"id":"ex_1","label":"Leg press","sets":4,"reps":{"from":10,"to":12},"start":{"weightKg":80,"reps":10},"stepKg":5},{"id":"ex_2","label":"Calf raise","removed":true,"sets":3,"reps":{"from":12,"to":15},"start":{"weightKg":40,"reps":12},"stepKg":2.5},{"id":"ex_3","label":"Plank","sets":3,"reps":{"from":1,"to":1},"start":{"reps":1}}]}""")),
            plainJson(legs),
        )
        assertEquals(
            plainJson(JSONArray("""[{"id":"e2","activity":"act_yoga","name":"Yoga","dateKey":"2026-10-14","timestampMillis":30,"durationMinutes":45,"notes":"Stretchy"},{"id":"e1","activity":"act_gone","name":"Bowling","dateKey":"2026-10-13","timestampMillis":20}]""")),
            plainJson(stored.getJSONArray("activityEntries")),
        )
        assertEquals(
            plainJson(JSONObject("""{"id":"2026-10-14-day_d","activity":"gym","day":"day_d","dateKey":"2026-10-14","timestampMillis":40,"durationMinutes":50,"exerciseLogs":[{"exerciseId":"ex_1","exerciseName":"Leg press","sets":[{"weightKg":80,"reps":10},{"weightKg":85,"reps":9.5}]},{"exerciseId":"ex_3","exerciseName":"Plank","sets":[{"reps":1}]}]}""")),
            plainJson(stored.getJSONArray("sessions").getJSONObject(0)),
        )
        assertEquals(
            plainJson(JSONObject("""{"id":"2026-10-13-day_flow","activity":"act_yoga","day":"day_flow","dateKey":"2026-10-13","timestampMillis":35,"exerciseLogs":[{"exerciseId":"ex_sun","exerciseName":"Sun salutation","sets":[{"reps":8},{"weightKg":2,"reps":6}]}]}""")),
            plainJson(stored.getJSONArray("sessions").getJSONObject(1)),
        )
    }

    @Test fun version30RoundTripsEveryFormAndEveryRecord() {
        val left = testActivityConfiguration().copy(rewards = ActivityRewards(3, periods = listOf(RewardPeriod(day, day.plusDays(2)), RewardPeriod(day.plusDays(5)))))
        val neverIn = testActivityConfiguration().copy(rewards = ActivityRewards(3, periods = emptyList()))
        listOf(state, seeded, state.copy(activity = testActivityConfiguration().applyEdit(ActivityEdit.SetWeek(WeekPlan.of(DayOfWeek.SUNDAY)), day)), state.copy(activity = left), state.copy(activity = neverIn)).forEach { form ->
            val encoded = AppStateJsonCodec.encodeVersion30(form)
            val decoded = AppStateJsonCodec.decodeVersion30(encoded)
            assertEquals(form, decoded)
            assertEquals(encoded, AppStateJsonCodec.encodeVersion30(decoded))
            assertFalse(AppStateJsonCodec.lostRecords(encoded, decoded))
        }
        // The draft a page keeps reads back as the configuration it wrote.
        assertEquals(configured, AppStateJsonCodec.decodeActivityConfiguration(AppStateJsonCodec.encodeActivityConfiguration(configured)))
    }

    /**
     * The decoder refuses what the configuration refuses and anything it would not write back
     * unchanged, and so does every record: nothing is dropped, mended or replaced by a default.
     * The store's write guard refuses every state that can be built with such a record, by the
     * same rules.
     */
    @Test fun version30RefusesWhatItWouldNotWriteBackAndTheWriteGuardRefusesTheSameStates() {
        val valid = AppStateJsonCodec.encodeVersion30(state)
        fun root(change: (JSONObject) -> Unit) = JSONObject(valid).apply(change).toString()
        fun configuration(change: (JSONObject) -> Unit) = root { change(it.getJSONObject("activity")) }
        fun activity(index: Int, change: (JSONObject) -> Unit) = configuration { change(it.getJSONArray("activities").getJSONObject(index)) }
        fun gym(change: (JSONObject) -> Unit) = activity(0) { change(it.getJSONObject("routine")) }
        fun exercise(change: (JSONObject) -> Unit) = gym { change(it.getJSONArray("days").getJSONObject(0).getJSONArray("exercises").getJSONObject(0)) }
        fun weightless(change: (JSONObject) -> Unit) = gym { change(it.getJSONArray("days").getJSONObject(3).getJSONArray("exercises").getJSONObject(2)) }
        fun entry(change: (JSONObject) -> Unit) = root { change(it.getJSONArray("activityEntries").getJSONObject(0)) }
        fun session(change: (JSONObject) -> Unit) = root { change(it.getJSONArray("sessions").getJSONObject(0)) }
        fun log(change: (JSONObject) -> Unit) = session { change(it.getJSONArray("exerciseLogs").getJSONObject(0)) }
        fun set(change: (JSONObject) -> Unit) = log { change(it.getJSONArray("sets").getJSONObject(0)) }
        val refusals = listOf(
            "no configuration" to root { it.remove("activity") },
            "a configuration that is no object" to root { it.put("activity", JSONArray()) },
            "the old types beside it" to root { it.put("activityTypes", JSONArray()) },
            "another view kind" to configuration { it.put("view", "time") },
            "another ID" to configuration { it.put("id", "sport") },
            "a field more" to configuration { it.put("motif", "running") },
            "a field less" to configuration { it.remove("awayDays") },
            "the gym plan of Block 34 beside the activities" to configuration { it.put("gym", JSONObject().put("on", true).put("label", "Gym").put("days", JSONArray())) },
            "a blank name" to configuration { it.put("label", " ") },
            "an unknown icon" to configuration { it.put("icon", "kettlebell") },
            "an unknown hue" to configuration { it.put("hue", "turquoise") },
            "the days a week of Block 34" to configuration { it.put("daysAWeek", 5) },
            "a week that is no object" to configuration { it.put("week", 5) },
            "a week with no day" to configuration { it.put("week", JSONObject("""{"days":[]}""")) },
            "a week with a day twice" to configuration { it.put("week", JSONObject("""{"days":[{"day":"MON"},{"day":"MON"}]}""")) },
            "a week out of the week's order" to configuration { it.put("week", JSONObject("""{"days":[{"day":"TUE"},{"day":"MON"}]}""")) },
            "a week with an unknown day" to configuration { it.put("week", JSONObject("""{"days":[{"day":"FUNDAY"}]}""")) },
            "a week with a day that is no text" to configuration { it.put("week", JSONObject("""{"days":[{"day":1}]}""")) },
            "a planned activity nothing lists" to configuration { it.put("week", JSONObject("""{"days":[{"day":"MON","activity":"act_none"}]}""")) },
            "a planned activity written as null" to configuration { it.put("week", JSONObject("""{"days":[{"day":"MON","activity":null}]}""")) },
            "a planned day with a field more" to configuration { it.put("week", JSONObject("""{"days":[{"day":"MON","routine":"A"}]}""")) },
            "a week with a field more" to configuration { it.put("week", JSONObject("""{"days":[{"day":"MON"}],"target":1}""")) },
            "a plain setting written as one revision" to configuration { it.put("week", JSONArray().put(JSONObject().put("value", JSONObject("""{"days":[{"day":"MON"}]}""")))) },
            "an unknown holiday answer" to configuration { it.put("whenAway", "sometimes") },
            "a count a week away that is no count" to configuration { it.put("awayDays", 0) },
            "fewer days asked than days a week" to configuration { it.put("whenAway", "lowerTarget"); it.put("awayDays", 6) },
            "fewer days at one day a week" to configuration { it.put("whenAway", "lowerTarget"); it.put("week", JSONObject("""{"days":[{"day":"MON"}]}""")); it.put("awayDays", 1) },
            "a bank of none" to configuration { it.getJSONObject("rewards").put("maxPasses", 0) },
            "a bank of ten" to configuration { it.getJSONObject("rewards").put("maxPasses", 10) },
            "a bank without steps" to configuration { it.getJSONObject("rewards").remove("tokenSteps") },
            "steps that are no object" to configuration { it.getJSONObject("rewards").put("tokenSteps", 1) },
            "steps with none" to configuration { it.getJSONObject("rewards").put("tokenSteps", JSONObject("""{"steps":[]}""")) },
            "steps that do not start at the days" to configuration { it.getJSONObject("rewards").put("tokenSteps", JSONObject("""{"steps":[{"daysMore":1,"tokens":1}]}""")) },
            "steps out of order" to configuration { it.getJSONObject("rewards").put("tokenSteps", JSONObject("""{"steps":[{"daysMore":0,"tokens":1},{"daysMore":2,"tokens":1}]}""")) },
            "a step of no tokens" to configuration { it.getJSONObject("rewards").put("tokenSteps", JSONObject("""{"steps":[{"daysMore":0,"tokens":0}]}""")) },
            "a step of six tokens" to configuration { it.getJSONObject("rewards").put("tokenSteps", JSONObject("""{"steps":[{"daysMore":0,"tokens":6}]}""")) },
            "a step with a field more" to configuration { it.getJSONObject("rewards").put("tokenSteps", JSONObject("""{"steps":[{"daysMore":0,"tokens":1,"percent":100}]}""")) },
            "steps beyond the room the week leaves" to configuration { it.getJSONObject("rewards").put("tokenSteps", JSONObject("""{"steps":[{"daysMore":0,"tokens":1},{"daysMore":1,"tokens":1},{"daysMore":2,"tokens":1},{"daysMore":3,"tokens":1},{"daysMore":4,"tokens":1}]}""")) },
            "an activity with a field more" to activity(0) { it.put("hue", "green") },
            "an activity without an icon" to activity(0) { it.remove("icon") },
            "an activity with a blank name" to activity(0) { it.put("label", "") },
            "an activity with an untrimmed name" to activity(0) { it.put("label", " Bouldering") },
            "two activities of one ID" to activity(0) { it.put("id", "cycling") },
            "two activities of one name" to activity(0) { it.put("label", "cycling") },
            "an activity named as a removed one" to activity(0) { it.put("label", "SWIMMING") },
            "an activity with a slash in its ID" to activity(0) { it.put("id", "a/b") },
            "an activity with removed written false" to activity(0) { it.put("removed", false) },
            "an activity with removed as text" to activity(5) { it.put("removed", "true") },
            "a routine that is no object" to activity(0) { it.put("routine", JSONArray()) },
            "a routine with a field more" to gym { it.put("on", true) },
            "a routine with a field less" to gym { it.remove("days") },
            "a routine without days" to gym { it.put("days", JSONArray()) },
            "a routine of eight days" to gym { it.put("days", JSONArray().apply { (0 until 8).forEach { index -> put(JSONObject(it.getJSONArray("days").getJSONObject(0).toString()).put("id", "d$index").put("exercises", JSONArray().put(JSONObject(it.getJSONArray("days").getJSONObject(0).getJSONArray("exercises").getJSONObject(0).toString()).put("id", "e$index")))) } }) },
            "two days of one ID" to gym { it.getJSONArray("days").getJSONObject(1).put("id", "A") },
            "two days of one ID across activities" to activity(4) { it.getJSONObject("routine").getJSONArray("days").getJSONObject(0).put("id", "A") },
            "a day with a field more" to gym { it.getJSONArray("days").getJSONObject(0).put("on", true) },
            "a day without exercises" to gym { it.getJSONArray("days").getJSONObject(0).put("exercises", JSONArray()) },
            "two exercises of one ID across days" to gym { it.getJSONArray("days").getJSONObject(1).getJSONArray("exercises").getJSONObject(0).put("id", "bench_press") },
            "two exercises of one ID across activities" to activity(4) { it.getJSONObject("routine").getJSONArray("days").getJSONObject(0).getJSONArray("exercises").getJSONObject(0).put("id", "bench_press") },
            "an exercise with a starting weight and no step" to exercise { it.remove("stepKg") },
            "an exercise with a step and no starting weight" to exercise { it.getJSONObject("start").remove("weightKg") },
            "a weightless exercise given a step alone" to weightless { it.put("stepKg", 2.5) },
            "a weightless exercise given a starting weight alone" to weightless { it.getJSONObject("start").put("weightKg", 20) },
            "a starting weight written as null" to exercise { it.getJSONObject("start").put("weightKg", JSONObject.NULL) },
            "a step written as null" to exercise { it.put("stepKg", JSONObject.NULL) },
            "an exercise with a field more" to exercise { it.put("notes", "") },
            "an exercise without sets" to exercise { it.put("sets", 0) },
            "an exercise of eleven sets" to exercise { it.put("sets", 11) },
            "a rep range upside down" to exercise { it.put("reps", JSONObject().put("from", 10).put("to", 8)) },
            "a rep range with a field more" to exercise { it.put("reps", JSONObject().put("from", 8).put("to", 10).put("target", 9)) },
            "reps of none" to exercise { it.put("reps", JSONObject().put("from", 0).put("to", 10)) },
            "reps beyond a hundred" to exercise { it.put("reps", JSONObject().put("from", 8).put("to", 101)) },
            "a starting weight as text" to exercise { it.put("start", JSONObject().put("weightKg", "22.5").put("reps", 10)) },
            "a starting weight below minus two hundred" to exercise { it.put("start", JSONObject().put("weightKg", -200.5).put("reps", 10)) },
            "a starting weight above five hundred" to exercise { it.put("start", JSONObject().put("weightKg", 500.5).put("reps", 10)) },
            "starting reps of none" to exercise { it.put("start", JSONObject().put("weightKg", 22.5).put("reps", 0)) },
            "starting reps that are not whole" to exercise { it.put("start", JSONObject().put("weightKg", 22.5).put("reps", 9.5)) },
            "a step below a quarter" to exercise { it.put("stepKg", 0.2) },
            "a step above twenty" to exercise { it.put("stepKg", 20.5) },
            "a step as text" to exercise { it.put("stepKg", "2.5") },
            "entries that are no list" to root { it.put("activityEntries", JSONObject()) },
            "an entry that is no object" to root { it.getJSONArray("activityEntries").put("no entry") },
            "an entry with the old type field" to entry { it.put("activityTypeId", "act_yoga") },
            "an entry with the old name field" to entry { it.remove("name"); it.put("activityName", "Yoga") },
            "an entry without an activity" to entry { it.remove("activity") },
            "an entry with a blank activity" to entry { it.put("activity", "") },
            "an entry without a name" to entry { it.remove("name") },
            "an entry with a blank name" to entry { it.put("name", " ") },
            "an entry without a day" to entry { it.remove("dateKey") },
            "an entry with a time that is text" to entry { it.put("timestampMillis", "30") },
            "an entry with no minutes written out" to entry { it.put("durationMinutes", JSONObject.NULL) },
            "an entry with minutes of none" to entry { it.put("durationMinutes", 0) },
            "an entry with minutes that are not whole" to entry { it.put("durationMinutes", 45.5) },
            "an entry with notes that are no text" to entry { it.put("notes", 7) },
            "sessions that are no list" to root { it.put("sessions", JSONObject()) },
            "a session that is no object" to root { it.getJSONArray("sessions").put(JSONObject()) },
            "a session without an activity" to session { it.remove("activity") },
            "a session with a blank activity" to session { it.put("activity", "") },
            "a session without a day" to session { it.remove("day") },
            "a session with a blank day" to session { it.put("day", "") },
            "a session without a time" to session { it.remove("timestampMillis") },
            "a session with minutes of none" to session { it.put("durationMinutes", 0) },
            "a session with a field more" to session { it.put("notes", "x") },
            "a session without logs" to session { it.remove("exerciseLogs") },
            "a log without its exercise" to log { it.remove("exerciseId") },
            "a log with a blank exercise" to log { it.put("exerciseId", " ") },
            "a log without a name" to log { it.remove("exerciseName") },
            "a log with a blank name" to log { it.put("exerciseName", " ") },
            "a log without sets" to log { it.remove("sets") },
            "a log with a field more" to log { it.put("removed", true) },
            "a set without reps" to set { it.remove("reps") },
            "a set with a weight as text" to set { it.put("weightKg", "80") },
            "a set with a weight written as null" to set { it.put("weightKg", JSONObject.NULL) },
            "a set with a field more" to set { it.put("rpe", 8) },
        )
        refusals.forEach { (what, raw) -> refused(what, raw) }

        // The same rules at the write guard, for every state a record can be built into.
        val guarded = listOf(
            "a blank entry ID" to state.copy(activityEntries = entries + ActivityEntry("", "act_yoga", "Yoga", "2026-10-15", 1)),
            "a blank activity" to state.copy(activityEntries = entries + ActivityEntry("e3", "", "Yoga", "2026-10-15", 1)),
            "a blank entry name" to state.copy(activityEntries = entries + ActivityEntry("e3", "act_yoga", " ", "2026-10-15", 1)),
            "a blank entry day" to state.copy(activityEntries = entries + ActivityEntry("e3", "act_yoga", "Yoga", "", 1)),
            "entry minutes of none" to state.copy(activityEntries = entries + ActivityEntry("e3", "act_yoga", "Yoga", "2026-10-15", 1, 0)),
            "a blank session ID" to state.copy(sessions = sessions + WorkoutSession("", "gym", "A", "2026-10-15", 1, emptyList())),
            "a blank session activity" to state.copy(sessions = sessions + WorkoutSession("s", "", "A", "2026-10-15", 1, emptyList())),
            "a blank session day" to state.copy(sessions = sessions + WorkoutSession("s", "gym", "", "2026-10-15", 1, emptyList())),
            "a blank session date" to state.copy(sessions = sessions + WorkoutSession("s", "gym", "A", "", 1, emptyList())),
            "session minutes of none" to state.copy(sessions = sessions + WorkoutSession("s", "gym", "A", "2026-10-15", 1, emptyList(), durationMinutes = 0)),
            "a log of a blank exercise" to state.copy(sessions = sessions + WorkoutSession("s", "gym", "A", "2026-10-15", 1, listOf(ExerciseLog("", "Nothing", emptyList())))),
            "a log under a blank name" to state.copy(sessions = sessions + WorkoutSession("s", "gym", "A", "2026-10-15", 1, listOf(ExerciseLog("ex_gone", " ", listOf(LoggedSet(20.0, 8.0)))))),
            "no configuration" to state.copy(activity = null),
        )
        guarded.forEach { (what, damaged) ->
            assertThrows(what, IllegalArgumentException::class.java) { requireStorableActivity(damaged.activity, damaged.activityEntries, damaged.sessions) }
        }
        requireStorableActivity(state.activity, state.activityEntries, state.sessions)
    }

    /** The sixteen icons that arrived with the configuration, the eight sports of Block 35 among them, are refused by every older version, on a checklist too. */
    @Test fun olderVersionsRefuseTheIconsOfVersion30() {
        listOf(ViewIcon.Hiking, ViewIcon.Kayaking).forEach { icon ->
            val worn = AppState().withWeight().withEating().withReading().withActivity().let { it.copy(time = it.time.map { view -> view.copy(icon = icon) }) }
            assertEquals(worn, AppStateJsonCodec.decodeVersion30(AppStateJsonCodec.encodeVersion30(worn)))
            assertThrows(RuntimeException::class.java) { AppStateJsonCodec.decodeVersion29(claimedAs(AppStateJsonCodec.encodeVersion30(worn), 29).toString()) }
            assertThrows(IllegalArgumentException::class.java) { legacyTrackerJson(worn, 29) }
        }
        assertEquals(16, ViewIcon.entries.count { it.introducedInFormat == 30 })
    }

    /**
     * A version 29 payload, written out as that version wrote it, reads as version 30 with Gym
     * first and the plan as its routine, each entry under the activity of its name, an entry named
     * "gym" under Gym, each session the Gym activity's with its day its letter's, and the activities
     * the names make, under the IDs point 8 of the plan gives them, deterministically: reading it
     * twice gives equal states. A day that is not one of the three is refused rather than read as
     * the first, and so is everything version 29 refused.
     */
    @Test fun aVersion29PayloadReadsAsVersion30WithEachEntryUnderTheActivityOfItsName() {
        val decoded = AppStateJsonCodec.decodeVersion29(FrozenVersion29WithActivities)
        assertEquals(stateOfFrozenVersion29WithActivities(), decoded)
        assertEquals(decoded, AppStateJsonCodec.decodeVersion29(FrozenVersion29WithActivities))
        assertEquals(
            listOf("gym", "bouldering", "cycling", "jogging", "type-yoga", "v29-trail-run", "type-swim", "type-pilates"),
            decoded.activityRules().activities.map { it.id },
        )
        assertEquals(SeededActivityRules.activity("gym"), decoded.activityRules().activity("gym"))
        assertEquals(listOf("gym", "gym", "gym"), decoded.sessions.map { it.activityId })
        val withGymEntry = JSONObject(FrozenVersion29WithActivities).apply {
            getJSONArray("activityEntries").put(JSONObject("""{"id":"e-gym","activityTypeId":"type-gym","activityName":" gym ","dateKey":"2026-10-11","timestampMillis":1760200000000}"""))
        }.toString()
        val gymEntry = AppStateJsonCodec.decodeVersion29(withGymEntry)
        assertEquals("gym" to "gym", gymEntry.activityEntries.first { it.id == "e-gym" }.let { it.activityId to it.name })
        assertEquals(decoded.activityRules(), gymEntry.activityRules())
        assertEquals(listOf("type-swim"), decoded.activityRules().activities.filter { it.removed }.map { it.id })
        assertEquals(mapOf("e-trail" to "Trail run", "e-yoga-1" to "yOGA", "e-jog" to "jogging"), decoded.activityEntries.filter { it.id in setOf("e-trail", "e-yoga-1", "e-jog") }.associate { it.id to it.name })
        // Written as version 30 and read again, it is the same state, and its old types are gone.
        val written = AppStateJsonCodec.encodeVersion30(decoded)
        assertEquals(decoded, AppStateJsonCodec.decodeVersion30(written))
        assertFalse(JSONObject(written).has("activityTypes"))
        assertEquals(30, JSONObject(written).getInt("schemaVersion"))

        fun session(change: (JSONObject) -> Unit) = JSONObject(FrozenVersion29WithActivities).apply { change(getJSONArray("sessions").getJSONObject(0)) }.toString()
        listOf(session { it.put("day", "D") }, session { it.put("day", "a") }, session { it.remove("day") }, session { it.put("day", "") }).forEach { raw ->
            assertThrows(RuntimeException::class.java) { AppStateJsonCodec.decodeVersion29(raw) }
        }
        assertThrows(RuntimeException::class.java) { AppStateJsonCodec.decodeVersion29(JSONObject(FrozenVersion29WithActivities).put("activity", JSONObject()).toString()) }
        assertThrows(RuntimeException::class.java) { AppStateJsonCodec.decodeVersion30(FrozenVersion29WithActivities) }
    }

    /**
     * What version 29 left out, the migration leaves out: a blank name, minutes of none, a type
     * without a name. An ID a type had that could not be one, or that an earlier activity or a
     * seeded one took, is made from the name, numbered where that is taken too.
     */
    @Test fun theMigrationKeepsWhatVersion29KeptAndMakesIdsItCannotTakeFromTheName() {
        val types = JSONArray(
            """[{"id":"has space","name":"Trail run","createdAtMillis":1},{"id":"type-b","name":"a-b","createdAtMillis":2},{"id":"bouldering","name":"Walking","createdAtMillis":3},""" +
                """{"id":"type-b","name":"Rowing","createdAtMillis":4},{"name":"no id","createdAtMillis":5},{"id":"later","name":"Pilates","createdAtMillis":6}]""",
        )
        val entries = JSONArray(
            """[{"id":"x1","activityTypeId":"t","activityName":"a b","dateKey":"2026-10-01","timestampMillis":10},""" +
                """{"id":"x2","activityTypeId":"t","activityName":"a/b","dateKey":"2026-10-01","timestampMillis":10},""" +
                """{"id":"x3","activityTypeId":"t","activityName":"  ","dateKey":"2026-10-02","timestampMillis":11},""" +
                """{"id":"x4","activityTypeId":"t","activityName":"Walking","dateKey":"2026-10-02","timestampMillis":12,"durationMinutes":0},""" +
                """{"id":"x5","activityTypeId":"t","activityName":"Trail run","dateKey":"2026-10-03","timestampMillis":9},""" +
                """{"id":"x6","activityTypeId":"later","activityName":"pilates","dateKey":"2026-10-03","timestampMillis":9},""" +
                """{"id":"x7","activityTypeId":"t","activityName":"A-B","dateKey":"2026-10-04","timestampMillis":11}]""",
        )
        val raw = JSONObject(FrozenVersion29WithActivities).put("activityTypes", types).put("activityEntries", entries).toString()
        val decoded = AppStateJsonCodec.decodeVersion29(raw)
        // Entries' names first by their first entry's time, ties by the stored order; then the types never used, in the order created.
        assertEquals(
            listOf(
                "gym" to "Gym", "bouldering" to "Bouldering", "cycling" to "Cycling", "jogging" to "Jogging",
                "v29-trail-run" to "Trail run", "later" to "pilates", "v29-a-b" to "a b", "v29-a-b-2" to "a/b", "type-b" to "A-B",
                "v29-walking" to "Walking", "v29-rowing" to "Rowing",
            ),
            decoded.activityRules().activities.map { it.id to it.label },
        )
        assertEquals(listOf("x1", "x2", "x5", "x6", "x7"), decoded.activityEntries.map { it.id }.sorted())
        assertEquals(mapOf("x1" to "v29-a-b", "x2" to "v29-a-b-2", "x5" to "v29-trail-run", "x6" to "later", "x7" to "type-b"), decoded.activityEntries.associate { it.id to it.activityId })
        assertEquals(decoded, AppStateJsonCodec.decodeVersion29(raw))
    }

    /**
     * A group of several types no entry names is placed, named and given its ID by its earliest
     * type, by its time and then by the stored order; the lowest index of any of its types plays
     * no part, so a later type stored first cannot push the group ahead.
     */
    @Test fun typesNeverUsedAreOrderedByTheirEarliestTypeNotByTheFirstStored() {
        val types = JSONArray("""[{"id":"a","name":"Yoga","createdAtMillis":20},{"id":"b","name":"Rowing","createdAtMillis":10},{"id":"a","name":"yoga","createdAtMillis":10}]""")
        val raw = JSONObject(FrozenVersion29WithActivities).put("activityTypes", types).put("activityEntries", JSONArray()).toString()

        val decoded = AppStateJsonCodec.decodeVersion29(raw)

        assertEquals(listOf("b" to "Rowing", "a" to "yoga"), decoded.activityRules().activities.drop(4).map { it.id to it.label })
        assertEquals(decoded, AppStateJsonCodec.decodeVersion29(raw))
    }

    private fun AppState.activityRules() = requireNotNull(activity)
}

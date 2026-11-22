package com.example.workouttracker.persistence.json

import com.example.workouttracker.domain.activity.ActivityEdit
import com.example.workouttracker.domain.activity.ActivityTokenStep
import com.example.workouttracker.domain.activity.ActivityTokenSteps
import com.example.workouttracker.domain.activity.ListedActivity
import com.example.workouttracker.domain.activity.WeekPlan
import com.example.workouttracker.domain.activity.routine.ExerciseWeight
import com.example.workouttracker.domain.activity.routine.RoutineDay
import com.example.workouttracker.domain.activity.routine.RoutineExercise
import com.example.workouttracker.domain.checklist.ViewHue
import com.example.workouttracker.domain.checklist.ViewIcon
import com.example.workouttracker.domain.checklist.WhenAway
import java.time.DayOfWeek
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/** Every edit Activity's page can make crosses process death as it was made, and what cannot be read exactly is refused. */
class ActivityEditJsonTest {
    private val exercise = RoutineExercise("ex_1", "Leg press", 4, 10..12, ExerciseWeight(80.0, 5.0), 10)

    @Test fun everyEditRoundTrips() {
        val edits = listOf(
            ActivityEdit.Rename("Sport"),
            ActivityEdit.SetIcon(ViewIcon.Running),
            ActivityEdit.SetHue(ViewHue.Teal),
            ActivityEdit.SetLogsDuration(true),
            ActivityEdit.SetLogsDuration(false),
            ActivityEdit.SetWeek(WeekPlan.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.SATURDAY).planning(DayOfWeek.MONDAY, "gym")),
            ActivityEdit.SetTokenSteps(ActivityTokenSteps(listOf(ActivityTokenStep(0, 1), ActivityTokenStep(1, 2)))),
            ActivityEdit.SetWhenAway(WhenAway.Excused),
            ActivityEdit.SetAwayDays(3),
            ActivityEdit.SetBankSize(5),
            ActivityEdit.JoinRewards,
            ActivityEdit.LeaveRewards,
            ActivityEdit.AddActivity(ListedActivity("act_yoga", "Yoga", ViewIcon.Bolt)),
            ActivityEdit.RenameActivity("act_yoga", "Hot yoga"),
            ActivityEdit.SetActivityIcon("act_yoga", ViewIcon.Swimming),
            ActivityEdit.SetActivityRecordsRoute("act_yoga", true),
            ActivityEdit.MoveActivityUp("act_yoga"),
            ActivityEdit.RemoveActivity("act_yoga"),
            ActivityEdit.AddRoutineDay("act_yoga", RoutineDay("day_d", "Legs", listOf(exercise))),
            ActivityEdit.RenameRoutineDay("day_d", "Lower"),
            ActivityEdit.MoveRoutineDayUp("day_d"),
            ActivityEdit.RemoveRoutineDay("day_d"),
            ActivityEdit.RemoveRoutine("act_yoga"),
            ActivityEdit.AddExercise("day_d", exercise.copy(id = "ex_2", weight = ExerciseWeight(-12.5, 0.25))),
            ActivityEdit.AddExercise("day_d", exercise.copy(id = "ex_3", weight = null)),
            ActivityEdit.RenameExercise("ex_2", "Calf raise"),
            ActivityEdit.MoveExerciseUp("ex_2"),
            ActivityEdit.RemoveExercise("ex_2"),
            ActivityEdit.SetExerciseNumbers("ex_1", 3, 8..10, ExerciseWeight(42.5, 2.5), 9),
            ActivityEdit.SetExerciseNumbers("ex_1", 3, 8..10, null, 9),
        )
        val encoded = AppStateJsonCodec.encodeActivityEdits(edits)
        assertEquals(edits, AppStateJsonCodec.decodeActivityEdits(encoded))
        assertEquals(listOf(ActivityEdit.JoinRewards, ActivityEdit.LeaveRewards), AppStateJsonCodec.decodeActivityEdits("""[{"edit":"joinRewards"},{"edit":"leaveRewards"}]"""))
        assertEquals(encoded, AppStateJsonCodec.encodeActivityEdits(AppStateJsonCodec.decodeActivityEdits(encoded)))
    }

    @Test fun anEditThatCannotBeReadExactlyIsRefused() {
        listOf(
            """[{"edit":"unknown"}]""",
            """[{"edit":"daysAWeek","days":4}]""",
            """[{"edit":"week","week":{"days":[]}}]""",
            """[{"edit":"week","week":{"days":[{"day":"MON"},{"day":"MON"}]}}]""",
            """[{"edit":"week","week":{"days":[{"day":"MON","activity":null}]}}]""",
            """[{"edit":"tokenSteps","tokenSteps":{"steps":[{"daysMore":1,"tokens":1}]}}]""",
            """[{"edit":"icon","icon":"kettlebell"}]""",
            """[{"edit":"logsDuration","logsDuration":"true"}]""",
            """[{"edit":"logsDuration"}]""",
            """[{"edit":"addActivity","activity":{"id":"act_x","label":"","icon":"bolt"}}]""",
            """[{"edit":"exerciseNumbers","exercise":"ex_1","sets":3,"reps":{"from":8,"to":10},"start":{"weightKg":"42.5","reps":9},"stepKg":2.5}]""",
            """[{"edit":"exerciseNumbers","exercise":"ex_1","sets":3,"reps":{"from":8,"to":10},"start":{"weightKg":42.5,"reps":9}}]""",
            """[{"edit":"exerciseNumbers","exercise":"ex_1","sets":3,"reps":{"from":8,"to":10},"start":{"reps":9},"stepKg":2.5}]""",
            """[{"edit":"addDay","activity":"act_yoga","day":{"id":"day_x","label":"Legs","exercises":[]}}]""",
            """[{"edit":"addDay","day":{"id":"day_x","label":"Legs","exercises":[{"id":"ex_x","label":"Leg press","sets":3,"reps":{"from":8,"to":10},"start":{"reps":9}}]}}]""",
        ).forEach { raw -> assertThrows(raw, RuntimeException::class.java) { AppStateJsonCodec.decodeActivityEdits(JSONArray(raw).toString()) } }
    }
}

package com.example.workouttracker.persistence.json

import com.example.workouttracker.domain.activity.ActivityEdit
import com.example.workouttracker.domain.activity.ListedActivity
import com.example.workouttracker.domain.activity.routine.ExerciseWeight
import com.example.workouttracker.domain.activity.routine.RoutineDay
import com.example.workouttracker.domain.activity.routine.RoutineExercise
import com.example.workouttracker.domain.checklist.ViewHue
import com.example.workouttracker.domain.checklist.ViewIcon
import com.example.workouttracker.domain.checklist.WhenAway
import org.json.JSONArray
import org.json.JSONObject

/*
 * The edits Activity's settings page has made but not saved, as it keeps them across process
 * death. They are never stored with the records, so they have no version of their own: what is
 * read is what the same build wrote, and anything that cannot be read exactly is refused. An
 * activity, a day or an exercise an edit adds is written as the configuration writes it.
 */

internal fun activityEditsToJson(edits: List<ActivityEdit>): JSONArray = JSONArray().apply { edits.forEach { put(it.encode()) } }

internal fun JSONArray.toActivityEdits(): List<ActivityEdit> = objects().map { it.decodeActivityEdit() }

private fun ActivityEdit.encode(): JSONObject = when (this) {
    is ActivityEdit.Rename -> edit("rename").put("label", label)
    is ActivityEdit.SetIcon -> edit("icon").put("icon", icon.storageId)
    is ActivityEdit.SetHue -> edit("hue").put("hue", hue.storageId)
    is ActivityEdit.SetLogsDuration -> edit("logsDuration").put("logsDuration", logsDuration)
    is ActivityEdit.SetWeek -> edit("week").put("week", plan.encode())
    is ActivityEdit.SetTokenSteps -> edit("tokenSteps").put("tokenSteps", steps.encode())
    is ActivityEdit.SetWhenAway -> edit("whenAway").put("whenAway", whenAway.storageId)
    is ActivityEdit.SetAwayDays -> edit("awayDays").put("days", days)
    is ActivityEdit.SetBankSize -> edit("bankSize").put("maxPasses", maxPasses)
    ActivityEdit.JoinRewards -> edit("joinRewards")
    ActivityEdit.LeaveRewards -> edit("leaveRewards")
    is ActivityEdit.AddActivity -> edit("addActivity").put("activity", activity.encodeForEdit())
    is ActivityEdit.RenameActivity -> edit("renameActivity").put("activity", activityId).put("label", label)
    is ActivityEdit.SetActivityIcon -> edit("activityIcon").put("activity", activityId).put("icon", icon.storageId)
    is ActivityEdit.SetActivityRecordsRoute -> edit("activityRecordsRoute").put("activity", activityId).put("recordsRoute", recordsRoute)
    is ActivityEdit.MoveActivityUp -> edit("moveActivityUp").put("activity", activityId)
    is ActivityEdit.RemoveActivity -> edit("removeActivity").put("activity", activityId)
    is ActivityEdit.AddRoutineDay -> edit("addDay").put("activity", activityId).put("day", day.encodeForEdit())
    is ActivityEdit.RenameRoutineDay -> edit("renameDay").put("day", dayId).put("label", label)
    is ActivityEdit.MoveRoutineDayUp -> edit("moveDayUp").put("day", dayId)
    is ActivityEdit.RemoveRoutineDay -> edit("removeDay").put("day", dayId)
    is ActivityEdit.RemoveRoutine -> edit("removeRoutine").put("activity", activityId)
    is ActivityEdit.AddExercise -> edit("addExercise").put("day", dayId).put("exercise", exercise.encodeForEdit())
    is ActivityEdit.RenameExercise -> edit("renameExercise").put("exercise", exerciseId).put("label", label)
    is ActivityEdit.MoveExerciseUp -> edit("moveExerciseUp").put("exercise", exerciseId)
    is ActivityEdit.RemoveExercise -> edit("removeExercise").put("exercise", exerciseId)
    is ActivityEdit.SetExerciseNumbers -> edit("exerciseNumbers")
        .put("exercise", exerciseId)
        .put("sets", sets)
        .put("reps", JSONObject().put("from", reps.first).put("to", reps.last))
        .put("start", JSONObject().putOptional("weightKg", weight?.startKg).put("reps", startReps))
        .putOptional("stepKg", weight?.stepKg)
}

private fun JSONObject.decodeActivityEdit(): ActivityEdit = when (val kind = text("edit")) {
    "rename" -> ActivityEdit.Rename(text("label"))
    "icon" -> ActivityEdit.SetIcon(icon())
    "hue" -> ActivityEdit.SetHue(ViewHue.fromStorageId(text("hue")) ?: error("Unknown hue"))
    "logsDuration" -> ActivityEdit.SetLogsDuration(get("logsDuration") as? Boolean ?: error("logsDuration must be a Boolean"))
    "week" -> ActivityEdit.SetWeek(getJSONObject("week").decodeWeekPlan())
    "tokenSteps" -> ActivityEdit.SetTokenSteps(getJSONObject("tokenSteps").decodeActivityTokenSteps())
    "whenAway" -> ActivityEdit.SetWhenAway(WhenAway.fromStorageId(text("whenAway")) ?: error("Unknown whenAway"))
    "awayDays" -> ActivityEdit.SetAwayDays(wholeNumber(get("days")))
    "bankSize" -> ActivityEdit.SetBankSize(wholeNumber(get("maxPasses")))
    "joinRewards" -> ActivityEdit.JoinRewards
    "leaveRewards" -> ActivityEdit.LeaveRewards
    "addActivity" -> ActivityEdit.AddActivity(getJSONObject("activity").decodeListedActivityForEdit())
    "renameActivity" -> ActivityEdit.RenameActivity(text("activity"), text("label"))
    "activityIcon" -> ActivityEdit.SetActivityIcon(text("activity"), icon())
    "activityRecordsRoute" -> ActivityEdit.SetActivityRecordsRoute(text("activity"), get("recordsRoute") as? Boolean ?: error("recordsRoute must be a Boolean"))
    "moveActivityUp" -> ActivityEdit.MoveActivityUp(text("activity"))
    "removeActivity" -> ActivityEdit.RemoveActivity(text("activity"))
    "addDay" -> ActivityEdit.AddRoutineDay(text("activity"), getJSONObject("day").decodeRoutineDayForEdit())
    "renameDay" -> ActivityEdit.RenameRoutineDay(text("day"), text("label"))
    "moveDayUp" -> ActivityEdit.MoveRoutineDayUp(text("day"))
    "removeDay" -> ActivityEdit.RemoveRoutineDay(text("day"))
    "removeRoutine" -> ActivityEdit.RemoveRoutine(text("activity"))
    "addExercise" -> ActivityEdit.AddExercise(text("day"), getJSONObject("exercise").decodeRoutineExerciseForEdit())
    "renameExercise" -> ActivityEdit.RenameExercise(text("exercise"), text("label"))
    "moveExerciseUp" -> ActivityEdit.MoveExerciseUp(text("exercise"))
    "removeExercise" -> ActivityEdit.RemoveExercise(text("exercise"))
    "exerciseNumbers" -> getJSONObject("start").let { start ->
        ActivityEdit.SetExerciseNumbers(
            exerciseId = text("exercise"),
            sets = wholeNumber(get("sets")),
            reps = getJSONObject("reps").let { wholeNumber(it.get("from"))..wholeNumber(it.get("to")) },
            weight = exerciseWeightForEdit(start),
            startReps = wholeNumber(start.get("reps")),
        )
    }
    else -> error("Unknown edit $kind")
}

private fun JSONObject.icon(): ViewIcon = ViewIcon.fromStorageId(text("icon")) ?: error("Unknown icon")

private fun ListedActivity.encodeForEdit(): JSONObject = JSONObject().put("id", id).put("label", label).put("icon", icon.storageId)

private fun JSONObject.decodeListedActivityForEdit(): ListedActivity = ListedActivity(id = text("id"), label = text("label"), icon = icon())

private fun RoutineDay.encodeForEdit(): JSONObject = JSONObject()
    .put("id", id)
    .put("label", label)
    .put("exercises", JSONArray().apply { exercises.forEach { put(it.encodeForEdit()) } })

private fun JSONObject.decodeRoutineDayForEdit(): RoutineDay =
    RoutineDay(id = text("id"), label = text("label"), exercises = array("exercises").objects().map { it.decodeRoutineExerciseForEdit() })

private fun RoutineExercise.encodeForEdit(): JSONObject = JSONObject()
    .put("id", id)
    .put("label", label)
    .put("sets", sets)
    .put("reps", JSONObject().put("from", reps.first).put("to", reps.last))
    .put("start", JSONObject().putOptional("weightKg", weight?.startKg).put("reps", startReps))
    .putOptional("stepKg", weight?.stepKg)

private fun JSONObject.decodeRoutineExerciseForEdit(): RoutineExercise = getJSONObject("start").let { start ->
    RoutineExercise(
        id = text("id"),
        label = text("label"),
        sets = wholeNumber(get("sets")),
        reps = getJSONObject("reps").let { wholeNumber(it.get("from"))..wholeNumber(it.get("to")) },
        weight = exerciseWeightForEdit(start),
        startReps = wholeNumber(start.get("reps")),
    )
}

/** An exercise's weight as the configuration writes it: its start's `weightKg` with `stepKg` beside it, both or neither. */
private fun JSONObject.exerciseWeightForEdit(start: JSONObject): ExerciseWeight? {
    require(start.has("weightKg") == has("stepKg")) { "An exercise has a starting weight and a step, or neither" }
    return if (has("stepKg")) ExerciseWeight(decimalNumber(start.get("weightKg")), decimalNumber(get("stepKg"))) else null
}

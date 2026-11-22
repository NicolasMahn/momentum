package com.example.workouttracker.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.workouttracker.R
import com.example.workouttracker.core.format.typedDecimal
import com.example.workouttracker.core.locale.currentLocale
import com.example.workouttracker.core.ui.MomentumDropdown
import com.example.workouttracker.core.ui.ReservedMessage
import com.example.workouttracker.domain.activity.ActivityConfiguration
import com.example.workouttracker.domain.activity.ActivityEdit
import com.example.workouttracker.domain.activity.ActivityTokenSteps
import com.example.workouttracker.domain.activity.ActivityViewId
import com.example.workouttracker.domain.activity.AwayDays
import com.example.workouttracker.domain.activity.DaysAWeek
import com.example.workouttracker.domain.activity.ListedActivity
import com.example.workouttracker.domain.activity.NamedActivityIds
import com.example.workouttracker.domain.activity.OtherActivityIcon
import com.example.workouttracker.domain.activity.TokensAStep
import com.example.workouttracker.domain.activity.WeekPlan
import com.example.workouttracker.domain.activity.applyEdit
import com.example.workouttracker.domain.activity.applyingEdits
import com.example.workouttracker.domain.activity.freshActivityId
import com.example.workouttracker.domain.activity.freshExerciseId
import com.example.workouttracker.domain.activity.freshRoutineDayId
import com.example.workouttracker.domain.activity.routine.ExerciseWeight
import com.example.workouttracker.domain.activity.routine.RepsASet
import com.example.workouttracker.domain.activity.routine.RoutineDay
import com.example.workouttracker.domain.activity.routine.RoutineExercise
import com.example.workouttracker.domain.activity.routine.StartWeightKg
import com.example.workouttracker.domain.activity.routine.StepKg
import com.example.workouttracker.domain.checklist.ViewHue
import com.example.workouttracker.domain.checklist.ViewIcon
import com.example.workouttracker.domain.checklist.WhenAway
import com.example.workouttracker.domain.reminder.ReminderSettings
import com.example.workouttracker.feature.settings.ActivitySettingsDraft.Companion.activityKey
import com.example.workouttracker.feature.settings.ActivitySettingsDraft.Companion.dayKey
import com.example.workouttracker.feature.settings.ActivitySettingsDraft.Companion.exerciseKey
import com.example.workouttracker.feature.settings.ActivitySettingsDraft.Companion.numberKey
import com.example.workouttracker.persistence.json.AppStateJsonCodec
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle

/** The typed numbers of an exercise, each a field of its own under the exercise's key ([field]) and in its tag ([tag]). */
internal enum class ExerciseNumber(val field: String, val tag: String) {
    RepsFrom("repsFrom", "reps_from"),
    RepsTo("repsTo", "reps_to"),
    StartWeight("startWeight", "start_weight"),
    StartReps("startReps", "start_reps"),
    Step("step", "step"),
}

/**
 * Activity's settings page as a draft ([ConfigurationDraft]): its name and look, its week, the
 * activities a person lists with their routines, its bank with its steps and its holiday rule, all as
 * ordered [ActivityEdit]s over the stored configuration. The reminder is the page's other draft
 * ([FeatureSettingsDraft]); one Save stores both ([BuiltInViewSave]).
 *
 * Every field on the page is known by the path of the thing it belongs to, the view's own, an
 * activity's, a day's under its activity, an exercise's under its activity and each of an
 * exercise's numbers under it. A field that leaves the screen keeps nothing of its own, as on a
 * level of the page that is left, so the draft keeps what every name and number field holds as
 * typed, by that path, until Save ([texts]), and the text of "New activity" until it is added
 * ([newActivityText]); a field shown again reads its text here first. A name field left blank
 * holds Save back ([blankNames]); an activity's name another activity has, removed ones included,
 * is refused in its field ([takenNames]); text that is no number the exercise may have is refused
 * ([refusedNumbers]) and holds Save back as a blank name does; so does a name another activity has
 * typed under "New activity" ([newActivityTaken]). Removing an activity drops what its fields and
 * everything under its path held, removing a routine what its days' and exercises' held, and
 * removing a day or an exercise what theirs held, so nothing hidden or gone ever holds Save back.
 */
internal class ActivitySettingsDraft(
    stored: ActivityConfiguration,
    today: LocalDate,
    edits: List<ActivityEdit> = emptyList(),
    blankNames: Set<String> = emptySet(),
    pendingText: Set<String> = emptySet(),
    takenNames: Set<String> = emptySet(),
    texts: Map<String, String> = emptyMap(),
    refusedNumbers: Set<String> = emptySet(),
    newActivityText: String = "",
    newActivityIcon: ViewIcon? = null,
) : ConfigurationDraft<ActivityConfiguration, ActivityEdit>(stored, today, edits, blankNames, pendingText) {
    /** The field holding a new activity's name not yet added, by field. */
    val pendingText: Set<String> get() = typedFields

    /** What "New activity" holds as typed, until it is added. */
    var newActivityText: String by mutableStateOf(newActivityText)
        private set

    /** The icon chosen for the activity being made, which also gives it its color; none until one is chosen. */
    var newActivityIcon: ViewIcon? by mutableStateOf(newActivityIcon)
        private set

    fun chooseNewActivityIcon(icon: ViewIcon) {
        newActivityIcon = icon
    }

    /** Whether the activity being made has both a name no other activity has and an icon. */
    val canAddNewActivity: Boolean get() = newActivityName.isNotEmpty() && !newActivityTaken && newActivityIcon != null

    /** Adds the activity being made and starts the next one empty; its ID, or null while it cannot be added. */
    fun addNewActivity(named: NamedActivityIds): String? {
        val icon = newActivityIcon?.takeIf { canAddNewActivity } ?: return null
        return addActivity(newActivityName, named, icon)?.also {
            typing(NewActivityField, "")
            newActivityIcon = null
        }
    }

    /** What "New activity" holds, without the space around it. */
    val newActivityName: String get() = newActivityText.trim()

    /** Whether "New activity" holds a name another activity has, which Add refuses and Save waits on, as on any refused field. */
    val newActivityTaken: Boolean get() = newActivityName.isNotEmpty() && isTaken(newActivityName)

    /** The activity name fields holding a name another activity has, by key. */
    var takenNames: Set<String> by mutableStateOf(takenNames)
        private set

    /** What each name and number field holds as typed, by its path, until it is saved. */
    var texts: Map<String, String> by mutableStateOf(texts)
        private set

    /** The number fields holding text that is no number their exercise may have. */
    var refusedNumbers: Set<String> by mutableStateOf(refusedNumbers)
        private set

    override val heldBack: Boolean get() = super.heldBack || takenNames.isNotEmpty() || refusedNumbers.isNotEmpty() || newActivityTaken

    override fun typing(key: String, text: String) {
        super.typing(key, text)
        if (key == NewActivityField) newActivityText = text
    }

    override val hasUnsavedWork: Boolean get() = super.hasUnsavedWork || takenNames.isNotEmpty() || refusedNumbers.isNotEmpty()

    override fun ActivityConfiguration.applying(edit: ActivityEdit, today: LocalDate): ActivityConfiguration = applyEdit(edit, today)

    override fun ActivityConfiguration.applyingAll(edits: List<ActivityEdit>, today: LocalDate): ActivityConfiguration = applyingEdits(edits, today)

    override fun ActivityEdit.names(key: String): Boolean = when (this) {
        is ActivityEdit.Rename -> key == ActivityViewId
        is ActivityEdit.AddActivity -> key == activityKey(activity.id)
        is ActivityEdit.RenameActivity -> key == activityKey(activityId)
        is ActivityEdit.AddRoutineDay -> key == dayKey(activityId, day.id) || day.exercises.any { key == exerciseKey(activityId, it.id) }
        is ActivityEdit.RenameRoutineDay -> key == dayKeyOf(dayId)
        is ActivityEdit.AddExercise -> key == dayKeyOf(dayId)?.let { exerciseKey(edited.activityOfDay(dayId)?.id.orEmpty(), exercise.id) }
        is ActivityEdit.RenameExercise -> key == exerciseKeyOf(exerciseId)
        else -> false
    }

    override fun ActivityEdit.removes(): String? = when (this) {
        is ActivityEdit.RemoveActivity -> activityKey(activityId)
        is ActivityEdit.RemoveRoutineDay -> dayKeyOf(dayId)
        is ActivityEdit.RemoveExercise -> exerciseKeyOf(exerciseId)
        else -> null
    }

    /** The key of the day [dayId]'s name field, under the activity whose routine has it; null for a day no routine knows. */
    fun dayKeyOf(dayId: String): String? = edited.activityOfDay(dayId)?.let { dayKey(it.id, dayId) }

    /** The key of the exercise [exerciseId]'s name field, under the activity whose routine has it; null for one no routine knows. */
    fun exerciseKeyOf(exerciseId: String): String? = edited.activityOfExercise(exerciseId)?.let { exerciseKey(it.id, exerciseId) }

    override fun rename(label: String): Boolean = edit(ActivityEdit.Rename(label))

    override fun setIcon(icon: ViewIcon): Boolean = edit(ActivityEdit.SetIcon(icon))

    override fun setHue(hue: ViewHue): Boolean = edit(ActivityEdit.SetHue(hue))

    /** Logging an activity asks how long it took, or does not. */
    fun logDuration(logsDuration: Boolean): Boolean = edit(ActivityEdit.SetLogsDuration(logsDuration))

    /** The week's chosen days become [chosen], at least one; a day that stays keeps what is planned on it. */
    fun setWeekDays(chosen: Set<DayOfWeek>): Boolean = runCatching { edited.week.on(today).withDays(chosen) }.map { edit(ActivityEdit.SetWeek(it)) }.getOrDefault(false)

    /** [activityId] planned on [day], null for any activity. */
    fun planOn(day: DayOfWeek, activityId: String?): Boolean = edit(ActivityEdit.SetWeek(edited.week.on(today).planning(day, activityId)))

    fun setTokenSteps(steps: ActivityTokenSteps): Boolean = edit(ActivityEdit.SetTokenSteps(steps))

    fun setWhenAway(whenAway: WhenAway): Boolean = edit(ActivityEdit.SetWhenAway(whenAway))

    fun setAwayDays(days: Int): Boolean = edit(ActivityEdit.SetAwayDays(days))

    fun setBankSize(maxPasses: Int): Boolean = edit(ActivityEdit.SetBankSize(maxPasses))

    /** Counts toward the streak from today, or stops after yesterday. */
    fun takePart(takesPart: Boolean): Boolean = edit(if (takesPart) ActivityEdit.JoinRewards else ActivityEdit.LeaveRewards)

    /**
     * The name field of the activity [activityId] holds [name], or nothing while null. A name
     * another activity has, removed ones included, by the one rule names are compared by, is
     * refused: the field keeps it, the draft keeps the last name that could be stored, and Save
     * waits until the field holds a name of its own.
     */
    fun nameActivity(activityId: String, name: String?) {
        val key = activityKey(activityId)
        named(key, name)
        val taken = name != null && edited.activityNamed(name).let { it != null && it.id != activityId }
        takenNames = if (taken) takenNames + key else takenNames - key
        if (name != null && !taken) edit(ActivityEdit.RenameActivity(activityId, name))
    }

    /** Whether the name field of [activityId] holds a name another activity has. */
    fun nameTaken(activityId: String): Boolean = activityKey(activityId) in takenNames

    /** The name field [key] holds [text] as typed, kept for when it is shown again. */
    fun keepText(key: String, text: String) {
        texts = texts + (key to text)
    }

    /** What the field [key] holds as typed, or null while nothing typed waits for Save. */
    fun textOf(key: String): String? = texts[key]

    /** Whether an activity, removed ones included, has [name], which neither a rename nor "New activity" may take. */
    fun isTaken(name: String): Boolean = edited.activityNamed(name) != null

    /**
     * One activity more, last in the list, called [name] with Other's icon, under an ID nothing
     * names: none in [named], none the page shows and none it gave out since. Refused while
     * another activity has the name.
     */
    /** Adds [name] at the end wearing [icon], which also gives it its color; the activity's ID, or null where the name is taken. */
    fun addActivity(name: String, named: NamedActivityIds, icon: ViewIcon = OtherActivityIcon): String? {
        if (isTaken(name)) return null
        val drawn = edits.filterIsInstance<ActivityEdit.AddActivity>().map { it.activity.id }
        val id = freshActivityId(named.activities + edited.activities.map { it.id } + drawn)
        return id.takeIf { edit(ActivityEdit.AddActivity(ListedActivity(id, name, icon))) }
    }

    fun setActivityIcon(activityId: String, icon: ViewIcon): Boolean = edit(ActivityEdit.SetActivityIcon(activityId, icon))

    /** The activity is recorded with GPS from today's log on, or no longer. */
    fun recordRoute(activityId: String, recordsRoute: Boolean): Boolean = edit(ActivityEdit.SetActivityRecordsRoute(activityId, recordsRoute))

    fun moveActivityUp(activityId: String): Boolean = edit(ActivityEdit.MoveActivityUp(activityId))

    /** Removes an activity; whatever its name field and every field under its path held goes with it. */
    fun removeActivity(activityId: String): Boolean {
        val taken = edit(ActivityEdit.RemoveActivity(activityId))
        if (taken) forget { it.isAtOrUnder(activityKey(activityId)) }
        return taken
    }

    fun nameDay(dayId: String, name: String?) {
        val key = dayKeyOf(dayId) ?: return
        named(key, name)
        name?.let { edit(ActivityEdit.RenameRoutineDay(dayId, it)) }
    }

    fun nameExercise(exerciseId: String, name: String?) {
        val key = exerciseKeyOf(exerciseId) ?: return
        named(key, name)
        name?.let { edit(ActivityEdit.RenameExercise(exerciseId, it)) }
    }

    /**
     * One day more, last in [activityId]'s routine, called [label], with one exercise under IDs
     * nothing names ([named], the configuration's and the ones the page gave out since). An
     * activity without a routine gets one with this day. The exercise is still to be named: its
     * field shows nothing until it is, and Save waits.
     */
    fun addRoutineDay(activityId: String, label: String, exercisePlaceholder: String, named: NamedActivityIds): Boolean {
        val dayId = freshRoutineDayId(named.days + routineDayIds() + drawnDayIds())
        val exerciseId = freshExerciseId(named.exercises + routineExerciseIds() + drawnExerciseIds())
        val taken = edit(ActivityEdit.AddRoutineDay(activityId, RoutineDay(dayId, label, listOf(newExercise(exerciseId, exercisePlaceholder)))))
        if (taken) toBeNamed(exerciseKey(activityId, exerciseId))
        return taken
    }

    /** One exercise more, last in its day, still to be named as a new day's is. */
    fun addExercise(dayId: String, placeholder: String, named: NamedActivityIds): Boolean {
        val activity = edited.activityOfDay(dayId) ?: return false
        val exerciseId = freshExerciseId(named.exercises + routineExerciseIds() + drawnExerciseIds())
        val taken = edit(ActivityEdit.AddExercise(dayId, newExercise(exerciseId, placeholder)))
        if (taken) toBeNamed(exerciseKey(activity.id, exerciseId))
        return taken
    }

    /** The name field [key] of something just added is blank, and holds Save back until it is named. */
    private fun toBeNamed(key: String) {
        named(key, null)
        keepText(key, "")
    }

    fun moveDayUp(dayId: String): Boolean = edit(ActivityEdit.MoveRoutineDayUp(dayId))

    fun moveExerciseUp(exerciseId: String): Boolean = edit(ActivityEdit.MoveExerciseUp(exerciseId))

    /** Removes a day with its exercises; whatever their fields held goes with them. */
    fun removeRoutineDay(dayId: String): Boolean {
        val activity = edited.activityOfDay(dayId) ?: return false
        val gone = listOf(dayKey(activity.id, dayId)) + activity.day(dayId)?.exercises.orEmpty().map { exerciseKey(activity.id, it.id) }
        val taken = edit(ActivityEdit.RemoveRoutineDay(dayId))
        if (taken) forget { key -> gone.any { key.isAtOrUnder(it) } }
        return taken
    }

    /** Removes an activity's whole routine; whatever its days' and exercises' fields held goes with it. */
    fun removeRoutine(activityId: String): Boolean {
        val taken = edit(ActivityEdit.RemoveRoutine(activityId))
        if (taken) forget { it.startsWith("${activityKey(activityId)}/day/") || it.startsWith("${activityKey(activityId)}/exercise/") }
        return taken
    }

    /** Removes an exercise; whatever its fields held goes with it. */
    fun removeExercise(exerciseId: String): Boolean {
        val key = exerciseKeyOf(exerciseId) ?: return false
        val taken = edit(ActivityEdit.RemoveExercise(exerciseId))
        if (taken) forget { it.isAtOrUnder(key) }
        return taken
    }

    fun setSets(exerciseId: String, sets: Int): Boolean = setNumbers(exerciseId) { it.copy(sets = sets) }

    /**
     * The exercise [exerciseId] is done with a weight, or without one. Without, its start weight
     * and its step leave the page, and whatever their fields held goes with them; with one again,
     * it starts from the weight stored for it, or from a new exercise's.
     */
    fun setWeighted(exerciseId: String, weighted: Boolean): Boolean {
        val weight = if (weighted) stored.routineExercise(exerciseId)?.weight ?: NewExerciseWeight else null
        val taken = setNumbers(exerciseId) { it.copy(weight = weight) }
        if (taken && !weighted) {
            val gone = listOf(ExerciseNumber.StartWeight, ExerciseNumber.Step).mapNotNull { numberKeyOf(exerciseId, it) }
            forget { it in gone }
        }
        return taken
    }

    /**
     * The field of [number] of the exercise [exerciseId] was typed into. What was [typed], without
     * the space around it, becomes the number where the exercise may have it: a whole number of
     * reps, or kilograms as a decimal with either separator and at most two places, within its own
     * limits and, for the range's ends, on its side of the other end. Otherwise the field keeps
     * it, refused. A refused text is taken the moment the other end makes room for it.
     */
    fun typeNumber(exerciseId: String, number: ExerciseNumber, typed: String) {
        val key = numberKeyOf(exerciseId, number) ?: return
        texts = texts + (key to typed)
        refusedNumbers = if (takeNumber(exerciseId, number, typed)) refusedNumbers - key else refusedNumbers + key
        settleNumbers(exerciseId)
    }

    /** What the field of [number] holds, or null while nothing typed waits for Save. */
    fun numberText(exerciseId: String, number: ExerciseNumber): String? = numberKeyOf(exerciseId, number)?.let { texts[it] }

    fun numberRefused(exerciseId: String, number: ExerciseNumber): Boolean = numberKeyOf(exerciseId, number) in refusedNumbers

    /**
     * Whether a field at [path] or under it holds what Save cannot store: a blank name, a name
     * another activity has, or a refused number. The row that leads to it says so.
     */
    fun holdsAnError(path: String): Boolean = sequenceOf(blankNames, takenNames, refusedNumbers).flatten().any { it == path || it.startsWith("$path/") }

    private fun numberKeyOf(exerciseId: String, number: ExerciseNumber): String? = exerciseKeyOf(exerciseId)?.let { "$it/${number.field}" }

    /**
     * The other end of the rep range that refuses the field of [number], where its text is a count
     * of reps on the wrong side of it: the most "from" may be, or the least "to" may be. Null where
     * the text is refused for not being a count of reps at all.
     */
    fun repsBoundRefusing(exerciseId: String, number: ExerciseNumber): Int? {
        if (!numberRefused(exerciseId, number)) return null
        val reps = numberText(exerciseId, number)?.let(::wholeNumber)?.takeIf { it in RepsASet } ?: return null
        val range = edited.routineExercise(exerciseId)?.reps ?: return null
        return when (number) {
            ExerciseNumber.RepsFrom -> range.last.takeIf { reps > it }
            ExerciseNumber.RepsTo -> range.first.takeIf { reps < it }
            else -> null
        }
    }

    override fun saved() {
        super.saved()
        texts = emptyMap()
    }

    /** Whether [typed] is a number the exercise may have for [number] now, taken into the draft where it is; a weight field of an exercise without a weight takes nothing. */
    private fun takeNumber(exerciseId: String, number: ExerciseNumber, typed: String): Boolean {
        val exercise = edited.routineExercise(exerciseId) ?: return false
        return when (number) {
            ExerciseNumber.RepsFrom -> wholeNumber(typed)?.takeIf { it in RepsASet && it <= exercise.reps.last }?.let { from -> setNumbers(exerciseId) { it.copy(reps = from..it.reps.last) } }
            ExerciseNumber.RepsTo -> wholeNumber(typed)?.takeIf { it in RepsASet && it >= exercise.reps.first }?.let { to -> setNumbers(exerciseId) { it.copy(reps = it.reps.first..to) } }
            ExerciseNumber.StartWeight -> kilograms(typed)?.takeIf { it in StartWeightKg }?.let { kg -> setNumbers(exerciseId) { it.copy(weight = it.weight?.copy(startKg = kg)) } }
            ExerciseNumber.StartReps -> wholeNumber(typed)?.takeIf { it in RepsASet }?.let { reps -> setNumbers(exerciseId) { it.copy(startReps = reps) } }
            ExerciseNumber.Step -> typedStepKg(typed)?.let { step -> setNumbers(exerciseId) { it.copy(weight = it.weight?.copy(stepKg = step)) } }
        } ?: false
    }

    /** A refused text the other end of the range has since made room for is taken, and may make room for the other. */
    private fun settleNumbers(exerciseId: String) {
        var settled: String?
        do {
            settled = ExerciseNumber.entries
                .mapNotNull { number -> numberKeyOf(exerciseId, number)?.let { it to number } }
                .firstOrNull { (key, number) -> key in refusedNumbers && texts[key]?.let { takeNumber(exerciseId, number, it) } == true }
                ?.first
            settled?.let { refusedNumbers = refusedNumbers - it }
        } while (settled != null)
    }

    /** The exercise's numbers as [change] makes them, kept as one edit; numbers the routine refuses are not taken. */
    private fun setNumbers(exerciseId: String, change: (RoutineExercise) -> RoutineExercise): Boolean {
        val exercise = edited.routineExercise(exerciseId)?.let(change) ?: return false
        return edit(ActivityEdit.SetExerciseNumbers(exerciseId, exercise.sets, exercise.reps, exercise.weight, exercise.startReps))
    }

    /** Whether this key is the field at [path] or one under it, and not a field whose path only starts alike. */
    private fun String.isAtOrUnder(path: String): Boolean = this == path || startsWith("$path/")

    /** Lets go of what every field [gone] says is no longer on the page held. */
    private fun forget(gone: (String) -> Boolean) {
        forgetBlankNames(gone)
        takenNames = takenNames.filterNotTo(LinkedHashSet(), gone)
        texts = texts.filterKeys { !gone(it) }
        refusedNumbers = refusedNumbers.filterNotTo(LinkedHashSet(), gone)
    }

    private fun routineDayIds(): List<String> = edited.activities.flatMap { activity -> activity.routine?.days.orEmpty().map { it.id } }

    private fun routineExerciseIds(): List<String> = edited.activities.flatMap { activity -> activity.routine?.days.orEmpty().flatMap { day -> day.exercises.map { it.id } } }

    private fun drawnDayIds(): List<String> = edits.filterIsInstance<ActivityEdit.AddRoutineDay>().map { it.day.id }

    private fun drawnExerciseIds(): List<String> =
        edits.filterIsInstance<ActivityEdit.AddRoutineDay>().flatMap { added -> added.day.exercises.map { it.id } } +
            edits.filterIsInstance<ActivityEdit.AddExercise>().map { it.exercise.id }

    companion object {
        /** The key of "New activity", whose text the draft reads to refuse a name another activity has. */
        const val NewActivityField = "newActivity"

        /** An activity's path, under which its days' and exercises' keys lie, so removing it finds them all. */
        fun activityKey(activityId: String): String = "activities/$activityId"

        fun dayKey(activityId: String, dayId: String): String = "${activityKey(activityId)}/day/$dayId"

        fun exerciseKey(activityId: String, exerciseId: String): String = "${activityKey(activityId)}/exercise/$exerciseId"

        fun numberKey(activityId: String, exerciseId: String, number: ExerciseNumber): String = "${exerciseKey(activityId, exerciseId)}/${number.field}"

        /** What a new exercise asks until its numbers are set: three sets of eight to ten reps, from 20 kg, by 2.5 kg. */
        fun newExercise(id: String, label: String): RoutineExercise =
            RoutineExercise(id, label, sets = NewExerciseSets, reps = NewExerciseReps, weight = NewExerciseWeight, startReps = NewExerciseStartReps)

        private const val NewExerciseSets = 3
        private val NewExerciseReps = 8..10
        private val NewExerciseWeight = ExerciseWeight(startKg = 20.0, stepKg = 2.5)
        private const val NewExerciseStartReps = 10

        /** Only digits are a count here: a sign or a decimal separator is not. */
        private fun wholeNumber(typed: String): Int? = typed.trim().takeIf { text -> text.isNotEmpty() && text.all(Char::isDigit) }?.toIntOrNull()

        /** Kilograms as typed, with either decimal separator and at most two places, as a measurement's step is written. */
        /** An exercise's step as typed, or null where it is none the routine takes ([StepKg]). */
        fun typedStepKg(typed: String): Double? = kilograms(typed)?.takeIf { it in StepKg }

        private fun kilograms(typed: String): Double? = typedDecimal(typed).takeIf(Kilograms::matches)?.toDoubleOrNull()

        private val Kilograms = Regex("""-?\d+(\.\d{1,2})?""")

        val Saver: Saver<ActivitySettingsDraft, Any> = listSaver(
            save = {
                listOf(
                    AppStateJsonCodec.encodeActivityConfiguration(it.stored),
                    AppStateJsonCodec.encodeActivityEdits(it.edits),
                    ArrayList(it.blankNames),
                    ArrayList(it.pendingText),
                    it.today.toString(),
                    ArrayList(it.takenNames),
                    ArrayList(it.texts.keys),
                    ArrayList(it.texts.values),
                    ArrayList(it.refusedNumbers),
                    it.newActivityText,
                    it.newActivityIcon?.storageId.orEmpty(),
                )
            },
            restore = {
                ActivitySettingsDraft(
                    stored = AppStateJsonCodec.decodeActivityConfiguration(it[0] as String),
                    edits = AppStateJsonCodec.decodeActivityEdits(it[1] as String),
                    blankNames = (it[2] as List<*>).mapTo(LinkedHashSet()) { key -> key as String },
                    pendingText = (it[3] as List<*>).mapTo(LinkedHashSet()) { key -> key as String },
                    today = LocalDate.parse(it[4] as String),
                    takenNames = (it[5] as List<*>).mapTo(LinkedHashSet()) { key -> key as String },
                    texts = (it[6] as List<*>).map { key -> key as String }.zip((it[7] as List<*>).map { text -> text as String }).toMap(),
                    refusedNumbers = (it[8] as List<*>).mapTo(LinkedHashSet()) { key -> key as String },
                    newActivityText = it[9] as String,
                    newActivityIcon = ViewIcon.fromStorageId(it[10] as String),
                )
            },
        )
    }
}

/** The one Save of Activity's page: its configuration's draft beside its reminder's ([BuiltInViewSave]). */
internal typealias ActivityPageSave = BuiltInViewSave<ActivityConfiguration, ActivityEdit, ActivitySettingsDraft>

/**
 * Activity's second part holds the activities a person lists with their routines, and its third
 * the week with its reminder, each under its own word.
 */
internal val ActivityOwnPart = OwnPart(R.string.activity_settings_activities_part, tagName = "activities", remindersLabel = R.string.activity_settings_week_part, remindersTagName = "week")

/**
 * Activity's settings use [BuiltInViewSettingsPage] to coordinate its configuration and reminder, with four
 * parts a made view's has, each over the one draft ([ActivitySettingsDraft]): Look, its name,
 * icon and color; Activities, the activities a person lists, each on a page of its own with its
 * routine, a routine day on one under it ([ActivitiesPart]); Week, the days of the week and what
 * is planned on each, with the workout reminder's row over its own draft, which belongs to
 * Settings with the permissions it may need ([reminder]), handed the edited configuration, whose
 * week it names the days by, and the way to pick its time ([ActivityWeek]); and Streak, whether
 * it counts, what a week earns, how many tokens its bank holds and what a holiday means for it
 * ([ActivityStreak]). New activities, days and exercises take IDs nothing in [named] has.
 *
 * Which level Activities shows is the page's to keep, as text, across a look at another part,
 * rotation and process death, and the frame scrolls to the top when it changes. The page opens on
 * Look and the list; a host that shows another part or level first, as the gallery does, says
 * which through [opensOn] and [opensAt].
 */
@Composable
internal fun ActivitySettingsPage(
    save: ActivityPageSave,
    settings: ReminderSettings,
    named: NamedActivityIds,
    onSave: () -> Unit,
    reminder: @Composable (edited: ActivityConfiguration, onEditTime: () -> Unit) -> Unit,
    leaveRequested: Boolean,
    onLeaveAnswered: (discard: Boolean) -> Unit,
    onUnsavedWorkChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    message: String? = null,
    opensOn: ChecklistPage = LoggedViewParts.first(),
    opensAt: ActivitiesLevel = ActivitiesLevel.List,
    /** Saving switches Activity on, so Save is offered even with nothing edited ([BuiltInViewSettingsPage]). */
    switchesOn: Boolean = false,
) {
    val draft = save.configuration
    var pickingTime by rememberSaveable { mutableStateOf(false) }
    var level by rememberSaveable { mutableStateOf(opensAt.path) }
    val typedText = stringResource(R.string.settings_typed_text_pending)
    val next = stringResource(R.string.checklist_setup_next)
    val done = stringResource(R.string.activity_settings_new_activity_done)
    BuiltInViewSettingsPage(
        save = save,
        settings = settings,
        onSave = onSave,
        parts = LoggedViewParts,
        leaveRequested = leaveRequested,
        onLeaveAnswered = onLeaveAnswered,
        onUnsavedWorkChange = onUnsavedWorkChange,
        modifier = modifier,
        message = message,
        opensOn = opensOn,
        hint = typedText.takeIf { draft.holdsOnlyTypedText }?.let { BarLine(it, "feature_settings_hint") },
        partLabel = { ActivityOwnPart.nameOf(it) },
        partTagName = ActivityOwnPart::tagNameOf,
        scrollKey = level,
        switchesOn = switchesOn,
        // Making an activity is a setup of its own inside the part: its steps hold the bar.
        barButton = { shown ->
            val at = ActivitiesLevel.of(level)
            when {
                shown != ChecklistPage.Items || !at.makesAnActivity -> null
                at == ActivitiesLevel.New -> BarButton(next, draft.canAddNewActivity, "feature_settings_new_activity_next") {
                    draft.addNewActivity(named)?.let { level = ActivitiesLevel.Extras(it).path }
                }
                else -> BarButton(done, enabled = true, testTag = "feature_settings_new_activity_done") { level = ActivitiesLevel.List.path }
            }
        },
        dialogs = {
            if (pickingTime) {
                SettingsTimePickerDialog(
                    initialTime = save.reminder.timeIn(settings),
                    onDismiss = { pickingTime = false },
                    onConfirm = { time ->
                        pickingTime = false
                        save.reminder.at(time)
                    },
                )
            }
        },
    ) { shown, edited, nameField ->
        ActivityPart(
            part = shown,
            view = edited,
            draft = draft,
            named = named,
            testTag = "feature_settings",
            nameField = nameField,
            level = ActivitiesLevel.of(level),
            onLevel = { level = it.path },
        ) { reminder(edited) { pickingTime = true } }
    }
}

/**
 * One part of Activity's page over [view] as [draft] is changing it: Look, Activities
 * ([ActivitiesPart]) at its [level], Week ([ActivityWeek]) and Streak ([ActivityStreak]) are the
 * page's own, and [reminder] is the workout reminder's row, which is Settings' and stands last on
 * Week. [nameField] is Look's name. Every tag follows from [testTag].
 */
@Composable
internal fun ActivityPart(
    part: ChecklistPage,
    view: ActivityConfiguration,
    draft: ActivitySettingsDraft,
    named: NamedActivityIds,
    testTag: String,
    nameField: @Composable () -> Unit,
    level: ActivitiesLevel = ActivitiesLevel.List,
    onLevel: (ActivitiesLevel) -> Unit = {},
    reminder: @Composable () -> Unit,
) {
    when (part) {
        ChecklistPage.Look -> LoggedViewLook(view = view, edits = draft, testTag = testTag, icons = ActivityIcons) { nameField() }

        ChecklistPage.Items -> ActivitiesPart(view = view, draft = draft, named = named, level = level, onLevel = onLevel, testTag = testTag)

        ChecklistPage.Reminders -> ActivityWeek(view = view, draft = draft, testTag = testTag, reminder = reminder)

        ChecklistPage.Streak -> ActivityStreak(view = view, draft = draft, testTag = testTag)
    }
}

/**
 * How Activity stands to the streak, over [view] as [draft] is changing it, each edit dated from
 * today: whether it counts, the switch every view has ([StreakSwitch]); while it does, what a week
 * earns by its days ([ActivityTokenStepRows]) and how many tokens its bank holds; and always what
 * a holiday means for it, still due, not due or, with more than one day a week, fewer days with
 * how many a week away asks, on the control a checklist item's holiday rule has
 * ([AwayRuleEditor]), since its reminder reads it too. Tags follow from [testTag]: the switch
 * `<testTag>_rewards`, the steps as the shared rows have them, the bank `<testTag>_max_passes`, a
 * holiday answer `<testTag>_away_<rule>` and a count `<testTag>_away_days_<n>`.
 */
@Composable
internal fun ActivityStreak(view: ActivityConfiguration, draft: ActivitySettingsDraft, testTag: String) {
    val today = draft.today
    val daysAWeek = view.daysAWeekOn(today)
    val takesPart = view.rewards.takesPartOn(today)
    Column(verticalArrangement = Arrangement.spacedBy(28.dp)) {
        StreakSwitch(takesPart = takesPart, testTag = testTag, onTakePart = { draft.takePart(it) })
        if (takesPart) {
            ActivityTokenStepRows(steps = view.rewards.tokenSteps.on(today), days = daysAWeek, draft = draft, testTag = testTag)
            BankSizeStepper(view.rewards.maxPasses.on(today), testTag, draft::setBankSize)
        }
        EditorGroup(stringResource(R.string.checklist_editor_away)) {
            AwayRuleEditor(
                rule = view.whenAway.on(today),
                // Fewer days needs a day to leave out: at one day a week there is none.
                options = listOf(WhenAway.Due, WhenAway.Excused) + listOfNotNull(WhenAway.LowerTarget.takeIf { daysAWeek > DaysAWeek.first }),
                testTag = "${testTag}_away",
                onChoose = { draft.setWhenAway(it) },
                count = AwayCountChoice(
                    label = stringResource(R.string.activity_settings_away_days),
                    counts = (AwayDays.first until daysAWeek).toList(),
                    chosen = view.awayDays.on(today),
                    spoken = { pluralStringResource(R.plurals.activity_settings_away_days_spoken, it, it) },
                    tag = { "${testTag}_away_days_$it" },
                    onChoose = draft::setAwayDays,
                ),
                optionLabel = { option ->
                    stringResource(
                        when (option) {
                            WhenAway.Due -> R.string.checklist_editor_away_due
                            WhenAway.Excused -> R.string.checklist_editor_away_excused
                            WhenAway.LowerTarget -> R.string.activity_settings_away_lower
                        },
                    )
                },
            )
        }
    }
}

/**
 * The steps Activity's week earns tokens by ([TokenStepRows]), in days: "At your days", then a day
 * more for each further step, since the steps are consecutive; beside each, what that comes to
 * by the week's [days] as the draft has them today. "Add a step" waits while the steps fill the
 * room the days leave. Tags are the shared rows', with the days `<testTag>_step_<number>_days`.
 */
@Composable
private fun ActivityTokenStepRows(steps: ActivityTokenSteps, days: Int, draft: ActivitySettingsDraft, testTag: String) {
    val atYourDays = stringResource(R.string.activity_settings_step_days)
    @Composable fun lead(daysMore: Int): String = if (daysMore == 0) atYourDays else pluralStringResource(R.plurals.activity_settings_step_days_more, daysMore, daysMore)
    @Composable fun weekDays(count: Int): String = pluralStringResource(R.plurals.activity_settings_step_week_days, count, count)
    TokenStepRows(
        rows = steps.steps.mapIndexed { index, step ->
            val text = lead(step.daysMore)
            TokenStepRow(
                lead = { modifier -> Text(text = text, modifier = modifier, style = MaterialTheme.typography.bodyLarge) },
                middle = weekDays(days + step.daysMore),
                tokens = step.tokens,
                tokensLabel = if (index == 0) stringResource(R.string.activity_settings_step_tokens_at_days) else stringResource(R.string.settings_step_tokens_at, index + 1),
                onTokens = { draft.setTokenSteps(steps.withTokens(index, it)) },
                onRemove = if (index == 0) null else ({ draft.setTokenSteps(steps.without(index)) }),
                removeKey = step,
            )
        },
        tokens = TokensAStep,
        leadWidth = widestTextWidth(listOf(atYourDays) + (1 until DaysAWeek.last).map { lead(it) }, MaterialTheme.typography.bodyLarge),
        widestMiddle = weekDays(DaysAWeek.last),
        total = stepsTotal(days + steps.steps.last().daysMore, steps.total),
        // Seven days and a step of the most tokens on every day of a week of one make the longest sentence.
        widestTotal = stepsTotal(DaysAWeek.last, TokensAStep.last * ActivityTokenSteps.roomFor(DaysAWeek.first)),
        canAdd = steps.canGrow(days),
        onAdd = { draft.setTokenSteps(steps.grown()) },
        testTag = testTag,
        middleTag = "days",
    )
}

/** What a week of [days] active days or more earns in all, [tokens]. */
@Composable
private fun stepsTotal(days: Int, tokens: Int): String =
    pluralStringResource(R.plurals.activity_settings_steps_total, days, days, pluralStringResource(R.plurals.settings_tokens, tokens, tokens))

/**
 * The week, over [view] as [draft] is changing it: its days as the seven toggles a some-days item
 * picks with, with the line under them saying what they earn ([WeekDaysEditor]); what is planned
 * on each chosen day ([PlannedDays]); and last the workout [reminder], which comes on those days
 * and names what is planned.
 */
@Composable
internal fun ActivityWeek(view: ActivityConfiguration, draft: ActivitySettingsDraft, testTag: String, reminder: @Composable () -> Unit) {
    val week = view.week.on(draft.today)
    Column(verticalArrangement = Arrangement.spacedBy(28.dp)) {
        WeekDaysEditor(chosen = week.chosen, steps = view.rewards.tokenSteps.on(draft.today), testTag = testTag) { draft.setWeekDays(it) }
        PlannedDays(view = view, week = week, testTag = testTag) { day, activityId -> draft.planOn(day, activityId) }
        reminder()
    }
}

/**
 * One row per chosen day, in the week's order: a field named by the day that opens the list of
 * what may be planned on it, "Any activity" first and then every activity still offered. A day
 * planned for an activity removed since names it until something else is chosen. A row is tagged
 * `<testTag>_week_plan_<1..7>` by its weekday and an option `…_<activity ID>`, or `…_any`.
 */
@Composable
private fun PlannedDays(view: ActivityConfiguration, week: WeekPlan, testTag: String, onPlan: (DayOfWeek, String?) -> Boolean) {
    val locale = currentLocale()
    val anyActivity = stringResource(R.string.activity_settings_any_activity)
    val options: List<String?> = listOf(null) + view.retainedActivities.map { it.id }
    EditorGroup(stringResource(R.string.activity_settings_planned)) {
        week.days.forEach { planned ->
            val tag = "${testTag}_week_plan_${planned.day.value}"
            MomentumDropdown(
                label = planned.day.getDisplayName(TextStyle.FULL, locale),
                options = options,
                selected = planned.activityId,
                onSelect = { if (it != planned.activityId) onPlan(planned.day, it) },
                optionLabel = { id -> id?.let { view.activity(it)?.label } ?: anyActivity },
                modifier = Modifier.fillMaxWidth(),
                testTag = tag,
                optionTestTag = { id -> "${tag}_${id ?: "any"}" },
            )
        }
    }
}

/**
 * The week's days as the seven toggles a some-days item picks with, Monday first, at least one
 * chosen, and under them what their number means with the week's [steps]: what the active days
 * earn, how many days are left as rest days and, with more than one step, the most more days
 * earn. The line keeps the room of its longest form, so choosing a day moves nothing. A day is
 * tagged `<testTag>_week_day_<1..7>`, the line `<testTag>_week_note`.
 */
@Composable
private fun WeekDaysEditor(chosen: Set<DayOfWeek>, steps: ActivityTokenSteps, testTag: String, onChange: (Set<DayOfWeek>) -> Boolean) {
    EditorGroup(stringResource(R.string.activity_settings_days_a_week)) {
        WeekdayToggles(
            chosen = chosen,
            testTag = { "${testTag}_week_day_${it.value}" },
            onChange = onChange,
        )
        ReservedMessage(
            text = daysNote(chosen.size, steps.steps.first().tokens, steps.total.takeIf { steps.steps.size > 1 }),
            testTag = "${testTag}_week_note",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            // The most a step adds, with the most steps the days leave room for, is every day's longest form.
            reserving = DaysAWeek.flatMap { days ->
                val room = ActivityTokenSteps.roomFor(days)
                TokensAStep.map { tokens -> daysNote(days, tokens, (TokensAStep.last * room).takeIf { room > 1 }) }
            },
        )
    }
}

/**
 * What [days] a week mean: that reaching them earns the first step's [tokens], the rest days that
 * leaves, and, where further steps add to it, the most a week earns in all, [upTo].
 */
@Composable
private fun daysNote(days: Int, tokens: Int, upTo: Int?): String {
    val rest = DaysAWeek.last - days
    return listOfNotNull(
        pluralStringResource(R.plurals.activity_settings_days_note, days, days, pluralStringResource(R.plurals.settings_tokens, tokens, tokens)),
        if (rest == 0) stringResource(R.string.activity_settings_no_rest_days_note) else pluralStringResource(R.plurals.activity_settings_rest_days_note, rest, rest),
        upTo?.let { pluralStringResource(R.plurals.activity_settings_days_note_more, it, it) },
    ).joinToString(" ")
}

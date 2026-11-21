package com.example.workouttracker.feature.settings

import androidx.compose.runtime.saveable.SaverScope
import com.example.workouttracker.domain.activity.ActivityTokenStep
import com.example.workouttracker.domain.activity.ActivityTokenSteps
import com.example.workouttracker.domain.activity.ActivityViewId
import com.example.workouttracker.domain.activity.NamedActivityIds
import com.example.workouttracker.domain.activity.applyingEdits
import com.example.workouttracker.domain.activity.testActivityConfiguration
import com.example.workouttracker.domain.checklist.Dated
import com.example.workouttracker.domain.checklist.ViewHue
import com.example.workouttracker.domain.checklist.ViewIcon
import com.example.workouttracker.domain.checklist.WhenAway
import com.example.workouttracker.feature.settings.ActivitySettingsDraft.Companion.activityKey
import com.example.workouttracker.feature.settings.ActivitySettingsDraft.Companion.dayKey
import com.example.workouttracker.feature.settings.ActivitySettingsDraft.Companion.exerciseKey
import com.example.workouttracker.feature.settings.ActivitySettingsDraft.Companion.numberKey
import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Activity's page is one draft over its four parts: what was typed and chosen is kept as edits
 * over the stored configuration, by the stable key of the thing each field belongs to; a name or
 * a number the configuration refuses holds Save back without replacing what was typed; what a
 * removed or hidden field held goes with it; and all of it survives process death.
 */
class ActivitySettingsDraftTest {
    private val today = LocalDate.parse("2026-10-14")
    private val seeded = testActivityConfiguration()
    private val nothingNamed = NamedActivityIds()

    private fun ActivitySettingsDraft.restored(): ActivitySettingsDraft {
        val saved = requireNotNull(with(ActivitySettingsDraft.Saver) { SaverScope { true }.save(this@restored) })
        return requireNotNull(ActivitySettingsDraft.Saver.restore(saved))
    }

    private fun ActivitySettingsDraft.newest(): String = edited.activities.last().id

    @Test
    fun oneDraftHoldsTheChangesOfAllFourParts() {
        val draft = ActivitySettingsDraft(seeded, today)
        assertFalse(draft.canSave)

        assertTrue(draft.rename("Sport"))
        assertTrue(draft.setIcon(ViewIcon.Ball))
        assertTrue(draft.setHue(ViewHue.Teal))
        assertTrue(draft.logDuration(true))
        assertTrue(draft.setWeekDays(setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY)))
        assertFalse("A week chooses at least one day", draft.setWeekDays(emptySet()))
        assertTrue(draft.planOn(DayOfWeek.THURSDAY, "jogging"))
        assertTrue(draft.setTokenSteps(ActivityTokenSteps(listOf(ActivityTokenStep(0, 2), ActivityTokenStep(1, 1)))))
        assertFalse("Steps beyond the room are refused", draft.setTokenSteps(ActivityTokenSteps(List(5) { ActivityTokenStep(it, 1) })))
        assertTrue(draft.setBankSize(5))
        assertTrue(draft.setAwayDays(3))
        assertFalse("A week away asks fewer days than the week", draft.setAwayDays(4))
        assertTrue(draft.setWhenAway(WhenAway.Excused))
        assertNotNull(draft.addActivity("Yoga", nothingNamed))
        val yoga = draft.newest()
        assertTrue(yoga.startsWith("act_"))
        assertTrue(draft.moveActivityUp(yoga))
        assertTrue(draft.setActivityIcon(yoga, ViewIcon.Spa))
        assertTrue(draft.removeActivity("cycling"))
        draft.nameDay("A", "Push")
        draft.nameExercise("bench_press", "Bench")
        assertTrue(draft.setSets("bench_press", 4))
        draft.typeNumber("bench_press", ExerciseNumber.StartWeight, "22,5")
        assertTrue(draft.moveExerciseUp("back_squat"))
        assertTrue(draft.moveDayUp("B"))
        assertTrue(draft.removeExercise("row"))

        val edited = draft.edited
        assertEquals("Sport", edited.label)
        assertEquals(ViewIcon.Ball to ViewHue.Teal, edited.icon to edited.hue)
        assertTrue(edited.logsDuration)
        // Activity's weeks were always judged, so each rule changes from today on.
        assertEquals(listOf(5, 4), edited.week.revisions.map { it.value.size })
        assertEquals(today, edited.week.revisions.last().from)
        assertEquals(listOf("gym", "bouldering", "gym", "jogging"), edited.week.on(today).days.map { it.activityId })
        assertEquals(Dated(listOf(Dated.Revision(seeded.rewards.tokenSteps.current), Dated.Revision(ActivityTokenSteps(listOf(ActivityTokenStep(0, 2), ActivityTokenStep(1, 1))), today))), edited.rewards.tokenSteps)
        assertEquals(Dated(listOf(Dated.Revision(3), Dated.Revision(5, today))), edited.rewards.maxPasses)
        assertEquals(Dated(listOf(Dated.Revision(2), Dated.Revision(3, today))), edited.awayDays)
        assertEquals(Dated(listOf(Dated.Revision(WhenAway.LowerTarget), Dated.Revision(WhenAway.Excused, today))), edited.whenAway)
        // Moved up, Yoga passes Jogging; Cycling, removed since, keeps its place in the list.
        assertEquals(listOf("gym", "bouldering", "cycling", yoga, "jogging"), edited.activities.map { it.id })
        assertEquals(listOf(false, false, true, false, false), edited.activities.map { it.removed })
        assertEquals("Yoga" to ViewIcon.Spa, edited.activity(yoga)!!.let { it.label to it.icon })
        val gym = edited.activity("gym")!!.routine!!
        assertEquals(listOf("B", "A", "C"), gym.days.map { it.id })
        assertEquals("Push", gym.day("A")!!.label)
        assertEquals(listOf("back_squat", "bench_press", "row", "lateral_raises", "triceps_pushdowns"), gym.day("A")!!.exercises.map { it.id })
        assertTrue(gym.exercise("row")!!.removed)
        assertEquals("Bench" to 4, gym.exercise("bench_press")!!.let { it.label to it.sets })
        assertEquals(22.5, gym.exercise("bench_press")!!.weight!!.startKg, 0.0)
        assertEquals(seeded.applyingEdits(draft.edits, today), edited)
        assertTrue(draft.canSave)

        // Stored meanwhile, the edits are shown over what is stored now, dated by the day it is; once stored, nothing is left to save.
        draft.follow(seeded.copy(label = "Stored meanwhile"), today.plusDays(1))
        assertEquals("Sport", draft.edited.label)
        assertEquals(today.plusDays(1), draft.edited.week.revisions.last().from)
        val stored = draft.edited
        draft.saved()
        draft.follow(stored, today.plusDays(1))
        assertFalse(draft.isDirty)
        assertFalse(draft.hasUnsavedWork)
    }

    /** A name another activity has, removed ones included, is refused in its field and in "New activity", and holds Save back until it is changed. */
    @Test
    fun aNameAnotherActivityHasIsRefusedWithoutReplacingWhatWasTyped() {
        val draft = ActivitySettingsDraft(seeded, today)
        draft.nameActivity("bouldering", "jogging ")
        assertTrue(draft.nameTaken("bouldering"))
        assertTrue(draft.heldBack && draft.hasUnsavedWork)
        assertEquals("Bouldering", draft.edited.activity("bouldering")!!.label)

        draft.nameActivity("bouldering", "Climbing")
        assertFalse(draft.nameTaken("bouldering"))
        assertEquals("Climbing", draft.edited.activity("bouldering")!!.label)
        assertTrue(draft.canSave)
        // Its own name, in any case, is no other's.
        draft.nameActivity("bouldering", "CLIMBING")
        assertFalse(draft.nameTaken("bouldering"))

        assertTrue(draft.removeActivity("cycling"))
        assertTrue(draft.isTaken("Cycling"))
        assertNull("A removed activity keeps its name", draft.addActivity("cycling", nothingNamed))
        assertNotNull(draft.addActivity("Swimming", nothingNamed))
        assertTrue(draft.isTaken("swimming"))

        // "New activity" refuses a taken name as a row's field does, and holds Save back while it holds one; another name is only text to add or clear.
        draft.typing(ActivitySettingsDraft.NewActivityField, " jogging ")
        assertTrue(draft.newActivityTaken)
        assertFalse(draft.canSave)
        draft.typing(ActivitySettingsDraft.NewActivityField, "Swim")
        assertFalse(draft.newActivityTaken)
        assertTrue(draft.canSave && draft.hasUnsavedWork)
        draft.typing(ActivitySettingsDraft.NewActivityField, "")
        assertEquals(emptySet<String>(), draft.pendingText)

        draft.nameActivity("jogging", null)
        assertEquals(setOf(activityKey("jogging")), draft.blankNames)
        assertFalse(draft.canSave)
        draft.nameActivity("jogging", "climbing")
        assertTrue(draft.nameTaken("jogging"))
        assertTrue(draft.removeActivity("jogging"))
        assertFalse(draft.nameTaken("jogging"))
        assertTrue(draft.canSave)
    }

    /** New IDs differ from everything the state names, the page shows and the page gave out since. */
    @Test
    fun newIdsAreOnesNothingNames() {
        val draft = ActivitySettingsDraft(seeded, today)
        val named = NamedActivityIds(activities = setOf("act_aaaa"), days = setOf("day_aaaa"), exercises = setOf("ex_aaaa"))
        repeat(3) { assertNotNull(draft.addActivity("Sport $it", named)) }
        val added = draft.edited.activities.drop(4).map { it.id }
        assertEquals(3, added.distinct().size)
        assertTrue(added.none { it == "act_aaaa" })

        assertTrue(draft.addRoutineDay("gym", "Day D", exercisePlaceholder = "Exercise", named = named))
        assertTrue(draft.addExercise("A", placeholder = "Exercise", named = named))
        val days = draft.edited.activity("gym")!!.routine!!.days
        val exercises = days.flatMap { day -> day.exercises.map { it.id } }
        assertEquals(listOf("A", "B", "C"), days.dropLast(1).map { it.id })
        assertTrue(days.last().id.startsWith("day_") && days.last().id != "day_aaaa")
        assertEquals(exercises.size, exercises.distinct().size)
        assertTrue(exercises.none { it == "ex_aaaa" })
        assertEquals("Day D", days.last().label)
        // Both new exercises are still to be named, so Save waits for them.
        assertEquals(setOf(exerciseKey("gym", days.last().exercises.single().id), exerciseKey("gym", days.first { it.id == "A" }.exercises.last().id)), draft.blankNames)
        assertFalse(draft.canSave)
        assertEquals(ActivitySettingsDraft.newExercise("x", "Exercise").copy(id = days.last().exercises.single().id), days.last().exercises.single())
    }

    /**
     * An exercise's numbers are typed: whole reps, kilograms with either separator and at most two
     * places, each within its own limits, and the range's ends on their side of each other. Text
     * that is none of that stays in the field, refused, and is taken once the other end makes room.
     */
    @Test
    fun typedNumbersAreTakenWhereTheExerciseMayHaveThemAndRefusedOtherwise() {
        val draft = ActivitySettingsDraft(seeded, today)
        val bench = "bench_press"
        fun bench() = draft.edited.routineExercise(bench)!!

        draft.typeNumber(bench, ExerciseNumber.StartWeight, " 22,5 ")
        assertEquals(22.5, bench().weight!!.startKg, 0.0)
        draft.typeNumber(bench, ExerciseNumber.StartWeight, "-19")
        assertEquals(-19.0, bench().weight!!.startKg, 0.0)
        assertEquals("-19", draft.numberText(bench, ExerciseNumber.StartWeight))
        listOf("", "abc", "600", "-201", "1.234", "1e2").forEach { refused ->
            draft.typeNumber(bench, ExerciseNumber.StartWeight, refused)
            assertTrue(refused, draft.numberRefused(bench, ExerciseNumber.StartWeight))
            assertEquals(refused, -19.0, bench().weight!!.startKg, 0.0)
            assertFalse(refused, draft.canSave)
        }
        draft.typeNumber(bench, ExerciseNumber.StartWeight, "20")
        assertFalse(draft.numberRefused(bench, ExerciseNumber.StartWeight))

        draft.typeNumber(bench, ExerciseNumber.Step, "0,25")
        assertEquals(0.25, bench().weight!!.stepKg, 0.0)
        draft.typeNumber(bench, ExerciseNumber.Step, "0.2")
        assertTrue(draft.numberRefused(bench, ExerciseNumber.Step))
        draft.typeNumber(bench, ExerciseNumber.Step, "20")
        assertEquals(20.0, bench().weight!!.stepKg, 0.0)

        draft.typeNumber(bench, ExerciseNumber.StartReps, "12")
        assertEquals(12, bench().startReps)
        draft.typeNumber(bench, ExerciseNumber.StartReps, "101")
        assertTrue(draft.numberRefused(bench, ExerciseNumber.StartReps))
        assertNull(draft.repsBoundRefusing(bench, ExerciseNumber.StartReps))
        draft.typeNumber(bench, ExerciseNumber.StartReps, "8")

        // From above to: refused by the other end, and taken once that end rises above it.
        draft.typeNumber(bench, ExerciseNumber.RepsFrom, "12")
        assertTrue(draft.numberRefused(bench, ExerciseNumber.RepsFrom))
        assertEquals(10, draft.repsBoundRefusing(bench, ExerciseNumber.RepsFrom))
        assertEquals(8..10, bench().reps)
        draft.typeNumber(bench, ExerciseNumber.RepsTo, "15")
        assertFalse(draft.numberRefused(bench, ExerciseNumber.RepsFrom))
        assertEquals(12..15, bench().reps)
        draft.typeNumber(bench, ExerciseNumber.RepsTo, "11")
        assertTrue(draft.numberRefused(bench, ExerciseNumber.RepsTo))
        assertEquals(12, draft.repsBoundRefusing(bench, ExerciseNumber.RepsTo))
        draft.typeNumber(bench, ExerciseNumber.RepsTo, "0")
        assertNull(draft.repsBoundRefusing(bench, ExerciseNumber.RepsTo))
        draft.typeNumber(bench, ExerciseNumber.RepsFrom, "6")
        assertTrue("0 is no count of reps whatever the other end", draft.numberRefused(bench, ExerciseNumber.RepsTo))
        draft.typeNumber(bench, ExerciseNumber.RepsTo, "8")
        assertEquals(6..8, bench().reps)
        assertFalse(draft.heldBack)
    }

    /**
     * What a removed thing's fields held goes with it, its own name as typed included, and so does
     * everything under a removed day, a removed routine and a removed activity. The names are
     * typed as the page's fields type them, kept as text beside the name.
     */
    @Test
    fun removingDropsWhatTheFieldsUnderItHeld() {
        val draft = ActivitySettingsDraft(seeded, today)
        draft.keepText(dayKey("gym", "A"), "")
        draft.nameDay("A", null)
        draft.keepText(exerciseKey("gym", "bench_press"), " ")
        draft.nameExercise("bench_press", null)
        draft.typeNumber("back_squat", ExerciseNumber.Step, "no")
        draft.typeNumber("pull_ups", ExerciseNumber.StartWeight, "no")
        draft.keepText(activityKey("jogging"), "cycling")
        draft.nameActivity("jogging", "cycling")
        draft.nameActivity("gym", null)
        assertEquals(setOf(dayKey("gym", "A"), exerciseKey("gym", "bench_press"), activityKey("gym")), draft.blankNames)
        assertEquals(setOf(numberKey("gym", "back_squat", ExerciseNumber.Step), numberKey("gym", "pull_ups", ExerciseNumber.StartWeight)), draft.refusedNumbers)

        assertTrue(draft.removeRoutineDay("A"))
        assertEquals(setOf(activityKey("gym")), draft.blankNames)
        assertEquals(setOf(numberKey("gym", "pull_ups", ExerciseNumber.StartWeight)), draft.refusedNumbers)
        assertEquals(mapOf(numberKey("gym", "pull_ups", ExerciseNumber.StartWeight) to "no", activityKey("jogging") to "cycling"), draft.texts)
        assertTrue(draft.removeExercise("pull_ups"))
        assertTrue(draft.refusedNumbers.isEmpty())
        assertEquals(mapOf(activityKey("jogging") to "cycling"), draft.texts)
        assertTrue(draft.nameTaken("jogging"))
        assertTrue(draft.removeActivity("jogging"))
        assertFalse(draft.nameTaken("jogging"))
        assertTrue(draft.texts.isEmpty())
        assertFalse("The gym's name is still blank", draft.canSave)

        draft.nameActivity("gym", "Studio")
        draft.nameDay("B", null)
        draft.typeNumber("dips", ExerciseNumber.Step, "no")
        assertTrue(draft.removeRoutine("gym"))
        assertTrue(draft.blankNames.isEmpty() && draft.refusedNumbers.isEmpty() && draft.texts.isEmpty())
        assertTrue(draft.canSave)
        // Removed, the routine keeps its days for the sessions that name them and offers none; a day added makes it a routine of one.
        assertFalse(draft.edited.activity("gym")!!.hasRoutine)
        assertEquals(listOf("A", "B", "C"), draft.edited.activity("gym")!!.routine!!.days.map { it.id })
        assertFalse(draft.removeRoutine("gym"))
        assertTrue(draft.addRoutineDay("gym", "Day D", "Exercise", nothingNamed))
        assertEquals("Studio", draft.edited.activity("gym")!!.label)
        assertEquals(listOf("Day D"), draft.edited.activity("gym")!!.routine!!.retainedDays.map { it.label })
        val dayD = draft.edited.activity("gym")!!.routine!!.retainedDays.single()
        assertFalse("The last day stays", draft.removeRoutineDay(dayD.id))
        draft.nameExercise(dayD.exercises.single().id, "Squat")
        // An activity removed takes its routine's fields with it.
        draft.nameDay(dayD.id, null)
        draft.typeNumber(dayD.exercises.single().id, ExerciseNumber.Step, "no")
        assertTrue(draft.removeActivity("gym"))
        assertTrue(draft.blankNames.isEmpty() && draft.refusedNumbers.isEmpty() && draft.texts.isEmpty())
        assertTrue(draft.canSave)
    }

    /**
     * The draft survives process death with its edits, its blank and taken names, the day, what
     * each name and number field holds as typed, a new exercise's blank name among them, and which
     * numbers are refused.
     */
    @Test
    fun theDraftSurvivesProcessDeath() {
        val draft = ActivitySettingsDraft(seeded, today)
        draft.rename("Sport")
        draft.named(ActivityViewId, null)
        draft.typing(ActivitySettingsDraft.NewActivityField, "Jogging")
        draft.keepText(activityKey("bouldering"), "Jogging ")
        draft.nameActivity("bouldering", "Jogging")
        draft.typeNumber("bench_press", ExerciseNumber.RepsFrom, "12")
        draft.typeNumber("bench_press", ExerciseNumber.StartWeight, "22,5")
        draft.addRoutineDay("gym", "Day D", "Exercise", nothingNamed)

        val restored = draft.restored()

        assertEquals(draft.stored, restored.stored)
        assertEquals(draft.edits, restored.edits)
        assertEquals(draft.blankNames, restored.blankNames)
        assertEquals(setOf(ActivitySettingsDraft.NewActivityField), restored.pendingText)
        assertEquals("Jogging", restored.newActivityName)
        assertTrue(restored.newActivityTaken)
        assertEquals(today, restored.today)
        assertEquals(setOf(activityKey("bouldering")), restored.takenNames)
        val newExercise = draft.edited.activity("gym")!!.routine!!.days.last().exercises.single().id
        assertEquals(
            mapOf(
                activityKey("bouldering") to "Jogging ",
                numberKey("gym", "bench_press", ExerciseNumber.RepsFrom) to "12",
                numberKey("gym", "bench_press", ExerciseNumber.StartWeight) to "22,5",
                exerciseKey("gym", newExercise) to "",
            ),
            restored.texts,
        )
        assertEquals("Jogging ", restored.textOf(activityKey("bouldering")))
        assertEquals(setOf(numberKey("gym", "bench_press", ExerciseNumber.RepsFrom)), restored.refusedNumbers)
        assertEquals(draft.edited, restored.edited)
        assertTrue(restored.heldBack && restored.hasUnsavedWork)
        // Saved, the typed numbers show what is stored again.
        restored.nameActivity("bouldering", "Bouldering")
        restored.typeNumber("bench_press", ExerciseNumber.RepsFrom, "9")
        restored.named(ActivityViewId, "Sport")
        restored.nameExercise(restored.edited.activity("gym")!!.routine!!.days.last().exercises.single().id, "Squat")
        assertFalse("The taken name under New activity still holds Save back", restored.canSave)
        restored.typing(ActivitySettingsDraft.NewActivityField, "Yoga")
        assertTrue(restored.canSave)
        restored.saved()
        assertTrue(restored.texts.isEmpty() && restored.edits.isEmpty())
    }
}

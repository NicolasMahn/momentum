package com.example.workouttracker.feature.logactivity

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.workouttracker.core.text.UiText
import com.example.workouttracker.core.text.resolve
import com.example.workouttracker.core.ui.theme.MomentumHues
import com.example.workouttracker.core.ui.theme.MomentumRamps
import com.example.workouttracker.domain.AppState
import com.example.workouttracker.domain.activity.ActivityEntry
import com.example.workouttracker.domain.activity.GymActivityId
import com.example.workouttracker.domain.activity.ListedActivity
import com.example.workouttracker.domain.activity.SeededActivityRules
import com.example.workouttracker.domain.activity.SeededGym
import com.example.workouttracker.domain.activity.activityRules
import com.example.workouttracker.domain.activity.testActivityConfiguration
import com.example.workouttracker.domain.checklist.ViewIcon
import com.example.workouttracker.domain.activity.routine.ExerciseLog
import com.example.workouttracker.domain.activity.routine.LoggedSet
import com.example.workouttracker.domain.activity.routine.Routine
import com.example.workouttracker.domain.activity.routine.RoutineDay
import com.example.workouttracker.domain.activity.routine.RoutineExercise
import com.example.workouttracker.domain.activity.routine.WorkoutSession
import com.example.workouttracker.domain.measurement.asMeasurementLogs
import com.example.workouttracker.domain.measurement.MeasurementRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ActivityModelsTest {
    @Test
    fun recordActivity_addsANewNameOnceAndReusesItIgnoringCaseAndSpace() {
        val first = recordActivity(
            state = AppState(),
            rawName = "  Yoga  ",
            dateKey = "2026-09-15",
            durationMinutes = null,
            notes = null,
            newActivityId = "type-1",
            newEntryId = "entry-1",
            timestampMillis = 100L,
        ).success()

        val second = recordActivity(
            state = first.state,
            rawName = "yOgA",
            dateKey = "2026-09-16",
            durationMinutes = null,
            notes = null,
            newActivityId = "unused-activity-id",
            newEntryId = "entry-2",
            timestampMillis = 200L,
        ).success()

        assertEquals(listOf(ListedActivity("type-1", "Yoga", ViewIcon.Bolt)), second.state.activityRules.activities.drop(4))
        assertEquals(listOf("Yoga", "Yoga"), second.state.activityEntries.map { it.name })
        assertEquals(listOf("type-1", "type-1"), second.state.activityEntries.map { it.activityId })
        assertSame(second.entry, second.state.activityEntries.first())
    }

    /** A seeded name is the seeded activity's, whatever its case, and adds nothing to the list. */
    @Test
    fun recordActivity_savesASeededNameUnderTheSeededActivity() {
        val result = recordActivity(
            state = AppState(),
            rawName = "bOuLdErInG",
            dateKey = "2026-09-15",
            durationMinutes = null,
            notes = null,
            newActivityId = "unused",
            newEntryId = "entry-1",
            timestampMillis = 100L,
        ).success()

        assertEquals(ActivityEntry("entry-1", "bouldering", "Bouldering", "2026-09-15", 100L), result.entry)
        assertEquals(null, result.state.activity)
    }

    @Test
    fun recordActivity_allowsDifferentAndRepeatedActivitiesOnTheSameDay() {
        val first = record("entry-1", "type-1", "Yoga", 100L, AppState())
        val second = record("entry-2", "type-2", "Bowling", 300L, first)
        val result = record("entry-3", "unused", "bowling", 200L, second)

        assertEquals(listOf("entry-2", "entry-3", "entry-1"), result.activityEntries.map { it.id })
        assertEquals(listOf("Yoga", "Bowling"), result.activityRules.activities.drop(4).map { it.label })
    }

    @Test
    fun recordActivity_bringsARemovedActivityBackAtTheEndOfTheList() {
        val removed = ListedActivity("old", "Bowling", ViewIcon.Swimming, removed = true)
        val other = ListedActivity("other", "Pilates", ViewIcon.Bolt)
        val configured = testActivityConfiguration().let { it.copy(activities = it.activities + removed + other) }

        val result = recordActivity(
            state = AppState(activity = configured),
            rawName = "bowling",
            dateKey = "2026-09-15",
            durationMinutes = null,
            notes = null,
            newActivityId = "new",
            newEntryId = "entry",
            timestampMillis = 30L,
        ).success()

        assertEquals(listOf("gym", "bouldering", "cycling", "jogging", "other", "old"), result.state.activityRules.activities.map { it.id })
        assertEquals(removed.copy(removed = false), result.state.activityRules.activities.last())
        assertEquals("old", result.entry.activityId)
        assertEquals("Bowling", result.entry.name)
    }

    @Test
    fun recordActivity_preservesExistingWeightAndWorkoutDataExactly() {
        val weights = listOf(MeasurementRecord("2026-09-14", 10L, 88.45)).asMeasurementLogs("weight")
        val sessions = listOf(workoutSession(id = "workout-1", day = "B", timestampMillis = 20L))
        val initial = AppState(measurementLogs = weights, sessions = sessions)

        val result = recordActivity(
            state = initial,
            rawName = "Climbing / mobility",
            dateKey = "2026-09-15",
            durationMinutes = 45,
            notes = "  Felt smooth  ",
            newActivityId = "type-1",
            newEntryId = "entry-1",
            timestampMillis = 30L,
        ).success().state

        assertSame(weights, result.measurementLogs)
        assertSame(sessions, result.sessions)
        assertEquals(45, result.activityEntries.single().durationMinutes)
        assertEquals("Felt smooth", result.activityEntries.single().notes)
    }

    @Test
    fun recordActivity_returnsInvalidForBadInput() {
        fun result(name: String, dateKey: String, duration: Int?) = recordActivity(
            state = AppState(),
            rawName = name,
            dateKey = dateKey,
            durationMinutes = duration,
            notes = null,
            newActivityId = "type-1",
            newEntryId = "entry-1",
            timestampMillis = 100L,
        )

        assertTrue(result("  ", "2026-09-15", null) is RecordActivityResult.Invalid)
        assertTrue(result("Bowling", "2026-9-15", null) is RecordActivityResult.Invalid)
        assertTrue(result("Bowling", "2026-02-30", null) is RecordActivityResult.Invalid)
        assertTrue(result("Bowling", "2026-09-15", 0) is RecordActivityResult.Invalid)
        assertTrue(result("Bowling", "2026-09-15", -1) is RecordActivityResult.Invalid)
    }

    @Test
    fun activityTimelineForDate_combinesActivitiesAndWorkoutsWithoutPersistingDuplicates() {
        val workout = workoutSession(id = "workout-1", day = "C", timestampMillis = 200L)
        val state = recordActivity(
            state = AppState(sessions = listOf(workout)),
            rawName = "Bouldering",
            dateKey = "2026-09-15",
            durationMinutes = 60,
            notes = "Technical session",
            newActivityId = "type-1",
            newEntryId = "entry-1",
            timestampMillis = 300L,
        ).success().state

        val timeline = activityTimelineForDate(state, "2026-09-15")

        assertEquals(listOf("Bouldering", "Gym · Day C"), timeline.map { it.title.text() })
        assertEquals(listOf("60 min", "1 set"), timeline.map { it.detail?.text() })
        assertEquals(listOf("activity:entry-1", "workout:workout-1"), timeline.map { it.id })
        assertEquals(1, state.activityEntries.size)
        assertEquals("Bouldering", state.activityEntries.single().name)
    }

    @Test
    fun activityTimelineForDate_onlyIncludesTheRequestedDay() {
        val state = AppState(
            activityEntries = listOf(
                ActivityEntry("entry-1", "type-1", "Bowling", "2026-09-14", 300L),
                ActivityEntry("entry-2", "type-1", "Bowling", "2026-09-15", 200L),
            ),
            sessions = listOf(workoutSession("workout-1", "A", 100L, "2026-09-15")),
        )

        assertEquals(
            listOf("Bowling", "Gym · Day A"),
            activityTimelineForDate(state, "2026-09-15").map { it.title.text() },
        )
    }

    @Test
    fun anActivityIsNamedByItsConfigurationWhateverSpellingAnEntryKept() {
        val state = AppState(
            activityEntries = listOf(
                ActivityEntry("lower", "cycling", "cycling", "2026-09-15", 200L),
                ActivityEntry("upper", "cycling", "Cycling", "2026-09-15", 100L),
                // One the configuration never knew keeps the name its entry was logged under.
                ActivityEntry("gone", "type-gone", "Spinning", "2026-09-15", 50L),
            ),
        )

        val timeline = activityTimelineForDate(state, "2026-09-15")
        assertEquals(listOf("Cycling", "Cycling", "Spinning"), timeline.map { it.title.text() })
        val markers = activityHistoryGrid(state, todayKey = "2026-09-15", weekCount = 1)
            .days.single { it.dateKey == "2026-09-15" }.activities
        // One activity, one dot per day.
        assertEquals(listOf("Cycling", "Spinning"), markers.map { it.label.text() })
        assertEquals(listOf(ActivityMark.Listed("cycling"), ActivityMark.Listed("type-gone")), markers.map { it.mark })
    }

    @Test
    fun activityTimelineForDate_doesNotProjectPersistedNotes() {
        val entry = ActivityEntry(
            id = "entry-1",
            activityId = "type-1",
            name = "Bowling",
            dateKey = "2026-09-15",
            timestampMillis = 100L,
            notes = "Legacy note remains persisted",
        )

        val timeline = activityTimelineForDate(AppState(activityEntries = listOf(entry)), "2026-09-15")

        assertEquals("Legacy note remains persisted", entry.notes)
        assertEquals(null, timeline.single().detail)
    }

    @Test
    fun updateActivity_changesEditableFieldsAndPreservesOtherState() {
        val originalType = ListedActivity("type-1", "Bowling", ViewIcon.Bolt)
        val originalEntry = ActivityEntry(
            id = "entry-1",
            activityId = originalType.id,
            name = originalType.label,
            dateKey = "2026-09-14",
            timestampMillis = 100L,
            durationMinutes = 30,
            notes = "Legacy note",
        )
        val otherEntry = ActivityEntry(
            id = "entry-2",
            activityId = originalType.id,
            name = originalType.label,
            dateKey = "2026-09-14",
            timestampMillis = 90L,
        )
        val weights = listOf(MeasurementRecord(dateKey = "2026-09-14", timestampMillis = 90L, value = 75.0)).asMeasurementLogs("weight")
        val sessions = listOf(workoutSession("gym-1", "A", 80L, "2026-09-14"))
        val state = AppState(
            measurementLogs = weights,
            sessions = sessions,
            activity = testActivityConfiguration().let { it.copy(activities = it.activities + originalType) },
            activityEntries = listOf(originalEntry, otherEntry),
        )

        val result = updateActivity(
            state = state,
            entryId = originalEntry.id,
            rawName = "Hiking",
            dateKey = "2026-09-15",
            durationMinutes = 45,
            newActivityId = "type-2",
        ).success()

        assertSame(weights, result.state.measurementLogs)
        assertSame(sessions, result.state.sessions)
        assertEquals(
            ActivityEntry("entry-1", "type-2", "Hiking", "2026-09-15", 100L, 45, "Legacy note"),
            result.entry,
        )
        assertEquals(listOf("Bowling", "Hiking"), result.state.activityRules.activities.drop(4).map { it.label })
        assertTrue(otherEntry in result.state.activityEntries)
    }

    /**
     * An entry keeps the name it was logged under, which the older counting rule reads, and its
     * notes as they are, while only its day or duration changes: under its own tile, and under
     * Other with the label it shows. Moved to another activity, it is logged under that label.
     */
    @Test
    fun updateActivity_keepsTheRecordedNameAndTheNotesWhileTheActivityStays() {
        val german = testActivityConfiguration().let { rules -> rules.copy(activities = rules.activities.map { if (it.id == "cycling") it.copy(label = "Radfahren") else it }) }
        val migrated = ActivityEntry("entry-1", "cycling", "Cycling", "2026-09-14", 100L, durationMinutes = 30, notes = "  ride  ")
        val state = AppState(activity = german, activityEntries = listOf(migrated))

        listOf(
            updateActivityOf(state, "entry-1", "cycling", "2026-09-15", 45),
            updateActivity(state, "entry-1", rawName = " radfahren ", dateKey = "2026-09-15", durationMinutes = 45),
        ).forEach { result ->
            assertEquals(migrated.copy(dateKey = "2026-09-15", durationMinutes = 45), result.success().entry)
            assertSame(german, result.success().state.activity)
        }

        val moved = updateActivity(state, "entry-1", rawName = "Bouldering", dateKey = "2026-09-15", durationMinutes = 45).success().entry
        assertEquals(migrated.copy(activityId = "bouldering", name = "Bouldering", dateKey = "2026-09-15", durationMinutes = 45), moved)
        assertEquals(moved, updateActivityOf(state, "entry-1", "bouldering", "2026-09-15", 45).success().entry)
    }

    /** An entry of an activity the configuration never knew, or no longer offers, keeps it until another activity is chosen for it. */
    @Test
    fun updateActivity_keepsAnActivityTheConfigurationDoesNotOfferUntilAnotherIsChosen() {
        val yoga = ListedActivity("act_yoga", "Yoga", ViewIcon.Bolt)
        val pilates = ListedActivity("act_pilates", "Pilates", ViewIcon.Bolt, removed = true)
        val rules = testActivityConfiguration().let { it.copy(activities = it.activities + yoga + pilates) }
        val unknown = ActivityEntry("unknown", "act_gone", "Yoga", "2026-09-14", 100L)
        val removed = ActivityEntry("removed", pilates.id, "Pilates", "2026-09-14", 90L)
        val state = AppState(activity = rules, activityEntries = listOf(unknown, removed))

        // The name each shows is no choice of another activity, by name or by ID.
        assertEquals(unknown.copy(durationMinutes = 20), updateActivity(state, "unknown", rawName = "Yoga", dateKey = "2026-09-14", durationMinutes = 20).success().entry)
        assertEquals(unknown.copy(durationMinutes = 20), updateActivityOf(state, "unknown", "act_gone", "2026-09-14", 20).success().entry)
        val stillRemoved = updateActivity(state, "removed", rawName = "pilates", dateKey = "2026-09-14", durationMinutes = 20).success()
        assertEquals(removed.copy(durationMinutes = 20), stillRemoved.entry)
        assertSame(rules, stillRemoved.state.activity)

        // Yoga's tile, chosen, takes the entry; a removed activity cannot be chosen by its ID.
        assertEquals(unknown.copy(activityId = yoga.id, name = yoga.label, durationMinutes = 20), updateActivityOf(state, "unknown", yoga.id, "2026-09-14", 20).success().entry)
        assertEquals(RecordActivityResult.Invalid(ActivityProblem.NotFound), updateActivityOf(state, "unknown", pilates.id, "2026-09-14", 20))
    }

    @Test
    fun deleteActivity_removesOnlyTheSelectedEntryAndKeepsTheActivity() {
        val type = ListedActivity("type-1", "Bowling", ViewIcon.Bolt)
        val first = ActivityEntry("entry-1", type.id, type.label, "2026-09-14", 100L)
        val second = ActivityEntry("entry-2", type.id, type.label, "2026-09-15", 200L)
        val sessions = listOf(workoutSession("gym-1", "B", 80L, "2026-09-14"))
        val state = AppState(
            sessions = sessions,
            activity = testActivityConfiguration().let { it.copy(activities = it.activities + type) },
            activityEntries = listOf(first, second),
        )

        val updated = (deleteActivity(state, first.id) as DeleteActivityResult.Success).state

        assertEquals(listOf(second), updated.activityEntries)
        assertSame(state.activity, updated.activity)
        assertSame(sessions, updated.sessions)
    }

    @Test
    fun deleteWorkoutSession_removesOnlyThatSessionAndKeepsLoggedActivities() {
        val entries = listOf(ActivityEntry("entry-1", "type-1", "Bowling", "2026-09-14", 100L))
        val target = workoutSession("gym-1", "B", 80L, "2026-09-14")
        val other = workoutSession("gym-2", "A", 90L, "2026-09-15")
        val state = AppState(
            sessions = listOf(target, other),
            activityEntries = entries,
        )

        val updated = (deleteWorkoutSession(state, target.id) as DeleteActivityResult.Success).state

        assertEquals(listOf(other), updated.sessions)
        assertSame(entries, updated.activityEntries)
        assertEquals(
            DeleteActivityResult.Invalid("Workout not found."),
            deleteWorkoutSession(updated, target.id),
        )
    }

    @Test
    fun activityHistoryGrid_ordersWholeWeeksEndingWithTodaysWeek() {
        // 2026-03-04 is a Wednesday.
        val grid = activityHistoryGrid(
            state = AppState(),
            todayKey = "2026-03-04",
            weekCount = 2,
        )

        assertEquals(
            listOf(
                "2026-02-23", "2026-02-24", "2026-02-25", "2026-02-26",
                "2026-02-27", "2026-02-28", "2026-03-01",
                "2026-03-02", "2026-03-03", "2026-03-04", "2026-03-05",
                "2026-03-06", "2026-03-07", "2026-03-08",
            ),
            grid.days.map { it.dateKey },
        )
    }

    @Test
    fun activityHistoryGrid_keepsDaysAfterTodayEmpty() {
        val state = AppState(
            sessions = listOf(workoutSession("ahead", "A", 100L, "2026-03-06")),
            activityEntries = listOf(
                ActivityEntry("ahead-entry", "type-1", "Jogging", "2026-03-05", 200L),
                ActivityEntry("today-entry", "type-1", "Jogging", "2026-03-04", 300L),
            ),
        )

        val grid = activityHistoryGrid(state, todayKey = "2026-03-04", weekCount = 1)

        assertEquals(
            listOf("2026-03-04"),
            grid.days.filter { it.activities.isNotEmpty() }.map { it.dateKey },
        )
    }

    @Test
    fun activityHistoryGrid_mapsWorkoutPlansIntoTheirCalendarDays() {
        val state = AppState(
            sessions = listOf(
                workoutSession("a", "A", 100L, "2026-09-13"),
                workoutSession("b", "B", 200L, "2026-09-14"),
                workoutSession("c", "C", 300L, "2026-09-15"),
                workoutSession("old", "A", 50L, "2026-09-10"),
            ),
        )

        // 2026-09-15 is a Tuesday, so one week shows Monday and today; the rest is outside it.
        val grid = activityHistoryGrid(state, todayKey = "2026-09-15", weekCount = 1)
        val recordedDays = grid.days.filter { it.activities.isNotEmpty() }

        assertEquals(
            listOf("2026-09-14" to ActivityMark.RoutineDay(GymActivityId, "B"), "2026-09-15" to ActivityMark.RoutineDay(GymActivityId, "C")),
            recordedDays.map { it.dateKey to it.activities.single().mark },
        )
        assertEquals(
            listOf("Gym · Day B", "Gym · Day C"),
            recordedDays.map { it.activities.single().label.text() },
        )
    }

    @Test
    fun activityHistoryGrid_groupsAnActivitysEntriesIntoOneDotWithoutChangingRecords() {
        val state = AppState(
            activityEntries = listOf(
                ActivityEntry("one", "bouldering", "bOuLdErInG", "2026-09-14", 100L),
                ActivityEntry("two", "bouldering", "Bouldering", "2026-09-14", 200L),
            ),
        )

        val grid = activityHistoryGrid(state, todayKey = "2026-09-15")
        val boulderingDay = grid.days.single { it.dateKey == "2026-09-14" }

        assertEquals(1, boulderingDay.activities.size)
        assertEquals(ActivityMark.Listed("bouldering"), boulderingDay.activities.first().mark)
        assertEquals("Bouldering", boulderingDay.activities.first().label.text())
        assertEquals(2, state.activityEntries.size)
    }

    @Test
    fun aTileAndTheHistoryMarkerOfItsActivityShareOneLook() {
        val rules = SeededActivityRules
        val jogging = rules.activities.single { it.label == "Jogging" }
        val marker = activityHistoryGrid(
            state = AppState(
                activityEntries = listOf(
                    ActivityEntry("jog", jogging.id, "Jogging", "2026-09-15", 100L),
                ),
            ),
            todayKey = "2026-09-15",
        ).days.single { it.dateKey == "2026-09-15" }.activities.single()

        assertEquals(ActivityMark.Listed(jogging.id), marker.mark)
        assertEquals(rules.lookOf(ActivityMark.Listed(jogging.id)), rules.lookOf(marker.mark))
        assertEquals("activity_choice_jogging", rules.lookOf(marker.mark).testTag)
    }

    /** With the default configuration the tiles are Gym, Bouldering and Cycling, then Other, by name. */
    @Test
    fun theDefaultTilesAreGymBoulderingCyclingAndOther() {
        val rules = testActivityConfiguration()
        assertEquals(listOf("Gym", "Bouldering", "Cycling", "Other"), rules.tiles().map { rules.labelOf(it).text() })
    }

    /** One row of four at most: the first activities not removed, Gym first at the start, then Other, which offers the rest. */
    @Test
    fun theTilesAreTheFirstActivitiesAndOtherAndOtherOffersTheRest() {
        val seeded = testActivityConfiguration()
        val yoga = ListedActivity("act_yoga", "Yoga", ViewIcon.Bolt)
        fun ids(tiles: List<ActivityMark>) = tiles.map { (it as? ActivityMark.Listed)?.activityId ?: it.toString() }

        assertEquals(listOf("gym", "bouldering", "cycling", "Other"), ids(seeded.tiles()))
        assertEquals(listOf("jogging"), seeded.activitiesUnderOther().map { it.id })

        val withYoga = seeded.copy(activities = seeded.activities + yoga)
        assertEquals(listOf("gym", "bouldering", "cycling", "Other"), ids(withYoga.tiles()))
        assertEquals(listOf("jogging", "act_yoga"), withYoga.activitiesUnderOther().map { it.id })

        // A removed activity has no tile and is not offered either, the gym like any other.
        val gymRemoved = withYoga.copy(activities = withYoga.activities.map { if (it.id == GymActivityId) it.copy(removed = true) else it })
        assertEquals(listOf("bouldering", "cycling", "jogging", "Other"), ids(gymRemoved.tiles()))
        assertEquals(listOf("act_yoga"), gymRemoved.activitiesUnderOther().map { it.id })
        val boulderingRemoved = gymRemoved.copy(activities = gymRemoved.activities.map { if (it.id == "bouldering") it.copy(removed = true) else it })
        assertEquals(listOf("cycling", "jogging", "act_yoga", "Other"), ids(boulderingRemoved.tiles()))
        assertEquals(emptyList<ListedActivity>(), boulderingRemoved.activitiesUnderOther())

        // Fewer activities make a shorter row; Other is always last.
        val one = seeded.copy(activities = seeded.activities.filter { it.id == "bouldering" }, week = seeded.week.map { it.withoutActivity(GymActivityId) })
        assertEquals(listOf("bouldering", "Other"), ids(one.tiles()))
    }

    @Test
    fun gymDaysHaveDistinctColorsFromTheSharedActivityPalette() {
        val rules = SeededActivityRules
        val days = SeededGym.routine!!.days
        val dayColors = days.map { rules.lookOf(ActivityMark.RoutineDay(GymActivityId, it.id)).containerColor }

        assertEquals(days.size, dayColors.toSet().size)
    }

    @Test
    fun everyTileAndGymDayOwnsAColorNoOtherUses() {
        val rules = SeededActivityRules
        val marks = SeededGym.routine!!.days.map { ActivityMark.RoutineDay(GymActivityId, it.id) } +
            rules.activities.map { ActivityMark.Listed(it.id) } + ActivityMark.Other
        val colors = marks.map { rules.lookOf(it).containerColor }

        assertEquals(marks.size, colors.toSet().size)
    }

    /** A routine day of another activity wears that activity's color, as its tile does. */
    @Test
    fun aRoutineDayOfAnotherActivityWearsItsActivitysColor() {
        val wall = Routine(
            listOf("A", "B", "C").map { letter ->
                RoutineDay("wall_$letter", "Wall $letter", listOf(RoutineExercise("ex_wall_$letter", "Problems $letter", 3, 3..6, null, 4)))
            },
        )
        val rules = testActivityConfiguration().let { seeded ->
            seeded.copy(activities = seeded.activities.map { if (it.id == "bouldering") it.copy(routine = wall) else it })
        }
        val tile = rules.lookOf(ActivityMark.Listed("bouldering"))
        listOf("wall_A", "wall_B", "wall_C").forEach { day ->
            val look = rules.lookOf(ActivityMark.RoutineDay("bouldering", day))
            assertEquals(tile.containerColor to tile.contentColor, look.containerColor to look.contentColor)
        }
    }

    @Test
    fun greenBelongsToGymAlone() {
        // Three activities with the catch-all icon, whose IDs land on each color the palette hands out.
        val rules = testActivityConfiguration().let { seeded ->
            seeded.copy(activities = seeded.activities + listOf("a", "b", "c").map { ListedActivity(it, "Sport $it", ViewIcon.Bolt) })
        }
        val gymMarks = listOf(ActivityMark.Listed(GymActivityId)) + SeededGym.routine!!.days.map { ActivityMark.RoutineDay(GymActivityId, it.id) }
        val otherMarks = rules.activities.filterNot { it.id == GymActivityId }.map { ActivityMark.Listed(it.id) } + ActivityMark.Other
        val greenFamily = MomentumRamps.GreenWide.map { it.fill }.toSet()
        val greenNeighbours = setOf(MomentumHues.Green.face, MomentumHues.Teal.face)

        assertTrue(gymMarks.all { rules.lookOf(it).containerColor in greenFamily })
        assertEquals(3, listOf("a", "b", "c").map { rules.lookOf(ActivityMark.Listed(it)).containerColor }.toSet().size)
        assertTrue(
            otherMarks.none { mark ->
                val color = rules.lookOf(mark).containerColor
                color in greenFamily || color in greenNeighbours
            },
        )
    }

    @Test
    fun activityHistoryGrid_keepsRecordedNamesAndMultipleActivitiesPerDay() {
        val state = AppState(
            activityEntries = listOf(
                ActivityEntry("one", "cycling", "Cycling", "2026-09-14", 100L),
                ActivityEntry("two", "cycling", "Cycling", "2026-09-15", 200L),
                // An activity the configuration never knew is shown under the name its entry keeps.
                ActivityEntry("three", "type-gone", "bowling", "2026-09-15", 300L),
                ActivityEntry("old", "jogging", "Jogging", "2026-09-01", 50L),
            ),
        )

        val grid = activityHistoryGrid(state, todayKey = "2026-09-15", weekCount = 1)
        val today = grid.days.single { it.dateKey == "2026-09-15" }

        assertEquals(listOf("bowling", "Cycling"), today.activities.map { it.label.text() })
        // Two activities on one day, so the calendar cell draws two dots rather than picking one.
        assertEquals(listOf(ActivityMark.Listed("type-gone"), ActivityMark.Listed("cycling")), today.activities.map { it.mark })
        assertEquals(2, today.activities.size)
        assertEquals(3, state.activityEntries.count { it.dateKey in grid.days.map(ActivityHistoryDay::dateKey) })
        assertFalse(today.activities.any { it.label.text().equals("Other", ignoreCase = true) })
    }

    private fun record(
        entryId: String,
        typeId: String,
        name: String,
        timestampMillis: Long,
        state: AppState,
    ): AppState = recordActivity(
        state = state,
        rawName = name,
        dateKey = "2026-09-15",
        durationMinutes = null,
        notes = null,
        newActivityId = typeId,
        newEntryId = entryId,
        timestampMillis = timestampMillis,
    ).success().state

    private fun UiText.text(): String =
        resolve(ApplicationProvider.getApplicationContext<Context>().resources)

    private fun RecordActivityResult.success(): RecordActivityResult.Success {
        assertTrue(this is RecordActivityResult.Success)
        return this as RecordActivityResult.Success
    }

    private fun workoutSession(
        id: String,
        day: String,
        timestampMillis: Long,
        dateKey: String = "2026-09-15",
    ) = WorkoutSession(
        id = id,
        activityId = GymActivityId,
        day = day,
        dateKey = dateKey,
        timestampMillis = timestampMillis,
        exerciseLogs = listOf(
            ExerciseLog(
                exerciseId = "bench_press",
                exerciseName = "Bench Press",
                sets = listOf(LoggedSet(weightKg = 22.5, reps = 10.0)),
            ),
        ),
    )
}

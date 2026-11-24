package de.nicolasmahn.momentum.reminder


import de.nicolasmahn.momentum.domain.compatibility.ReadingTimeId
import de.nicolasmahn.momentum.domain.time.TimeLog
import de.nicolasmahn.momentum.core.text.UiText
import de.nicolasmahn.momentum.domain.AppState
import de.nicolasmahn.momentum.domain.LoggedView
import de.nicolasmahn.momentum.domain.measurement.testWeightMeasurement
import de.nicolasmahn.momentum.domain.measurement.withWeight
import de.nicolasmahn.momentum.domain.questions.testEatingQuestions
import de.nicolasmahn.momentum.domain.questions.withEating
import de.nicolasmahn.momentum.domain.time.withReading
import de.nicolasmahn.momentum.domain.activity.GymActivityId
import de.nicolasmahn.momentum.domain.activity.PlannedDay
import de.nicolasmahn.momentum.domain.activity.WeekPlan
import de.nicolasmahn.momentum.domain.activity.testActivityConfiguration
import de.nicolasmahn.momentum.domain.activity.withActivity
import de.nicolasmahn.momentum.domain.compatibility.LegacyCareSession
import de.nicolasmahn.momentum.domain.compatibility.careChecklist
import de.nicolasmahn.momentum.domain.care.careState
import de.nicolasmahn.momentum.domain.checklist.ChecklistConfiguration
import de.nicolasmahn.momentum.domain.checklist.ChecklistItem
import de.nicolasmahn.momentum.domain.checklist.ChecklistTarget
import de.nicolasmahn.momentum.domain.checklist.Checkpoint
import de.nicolasmahn.momentum.domain.checklist.CheckpointTime
import de.nicolasmahn.momentum.domain.checklist.ViewHue
import de.nicolasmahn.momentum.domain.checklist.ViewIcon
import de.nicolasmahn.momentum.domain.checklist.progressOf
import de.nicolasmahn.momentum.domain.holiday.DayRange
import de.nicolasmahn.momentum.domain.holiday.HolidaySchedule
import de.nicolasmahn.momentum.domain.mo.MoDayMark
import de.nicolasmahn.momentum.domain.mo.MoMood
import de.nicolasmahn.momentum.domain.trackerRef
import de.nicolasmahn.momentum.domain.compatibility.MoNeed
import de.nicolasmahn.momentum.domain.mo.MoState
import de.nicolasmahn.momentum.domain.mo.MoStreak
import de.nicolasmahn.momentum.domain.time.DefaultWeeklyGoalMinutes
import de.nicolasmahn.momentum.domain.reminder.ReminderSettings
import de.nicolasmahn.momentum.domain.reminder.ReminderTime
import de.nicolasmahn.momentum.domain.reminder.WorkoutReminderSettings
import de.nicolasmahn.momentum.domain.settings.FeatureVisibilitySettings
import de.nicolasmahn.momentum.domain.substance.SubstanceCheckIn
import de.nicolasmahn.momentum.domain.substance.SubstanceNoneDay
import de.nicolasmahn.momentum.domain.substance.SubstanceSettings
import de.nicolasmahn.momentum.domain.compatibility.SupplementsCheckpointId
import de.nicolasmahn.momentum.domain.compatibility.supplementsChecklist
import de.nicolasmahn.momentum.domain.supplement.supplementsState
import de.nicolasmahn.momentum.persistence.ReminderSettingsRepository
import de.nicolasmahn.momentum.persistence.TrackerRepository
import de.nicolasmahn.momentum.persistence.TrackerUpdateResult
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import de.nicolasmahn.momentum.domain.time.testReadingTime
import de.nicolasmahn.momentum.domain.checklist.Dated
import de.nicolasmahn.momentum.domain.checklist.WhenAway

/**
 * A test sends what the real reminder sends: each case runs the real coordinator over an untouched
 * day and compares what it hands its notifier with what the test hands the same notifier.
 */
class TestReminderTest {
    /** A Wednesday. */
    private val today = LocalDate.parse("2026-10-14")
    private val todayKey = today.toString()
    private val medication = ChecklistConfiguration(
        id = "chk_7f3a",
        label = "Medication",
        icon = ViewIcon.Checklist,
        hue = ViewHue.Sky,
        checkpoints = listOf(
            Checkpoint("morning", CheckpointTime.AfterWake(30), label = "Morning", remind = true),
            Checkpoint("evening", CheckpointTime.Fixed(LocalTime.of(21, 0)), label = "Evening", remind = true),
        ),
        items = listOf(
            ChecklistItem("itm_levo", "Levothyroxine", checkpoints = listOf("morning")),
            ChecklistItem("itm_ibu", "Ibuprofen 400"),
            ChecklistItem("itm_iron", "Iron", checkpoints = listOf("evening"), target = ChecklistTarget.Weekdays(setOf(DayOfWeek.SATURDAY))),
        ),
    )

    @Test fun aCheckpointsTestNamesWhatItsCoordinatorNamesOnTheDayItRingsOn() {
        val state = AppState(checklists = listOf(medication))
        var handed: TestReminder.Checklist? = null
        val coordinator = ChecklistReminderCoordinator(
            trackerRepository = readOnly(state),
            featureVisibility = FeatureVisibilitySource { FeatureVisibilitySettings() },
            gateway = object : ChecklistReminderGateway {
                override fun canScheduleExact() = true
                override fun shown() = emptySet<String>()
                override fun schedule(slot: ReminderSlot, earliestDate: LocalDate?, fallbackAlarmTime: ReminderTime, fixedBedtime: ReminderTime?) = true
                override fun cancel(key: String) = Unit
                override fun dismiss(key: String) = Unit
                override fun keepUnsaved(key: String, why: UiText) = false
            },
            runtime = object : ChecklistReminderRuntime {
                override fun scheduled() = emptySet<String>()
                override fun record(keys: Set<String>) = Unit
                override fun posted() = emptyMap<String, ReminderDays>()
                override fun posted(key: String, days: ReminderDays) = Unit
                override fun dismissed(key: String) = Unit
                override fun earliest() = emptyMap<String, LocalDate>()
                override fun earliest(key: String, day: LocalDate) = Unit
            },
            notifier = { checklist, checkpoint, open, key, days, _ ->
                assertEquals(slotKey(checklist.id, checkpoint.id), key)
                handed = TestReminder.Checklist(checklist, checkpoint, open, days.dueDay)
                true
            },
            loggedNotifier = { _, _, _, _ -> error("No measurement reminds here.") },
            reminderSettings = { ReminderSettings() },
            todayKey = { todayKey },
        )
        medication.checkpoints.forEach { checkpoint ->
            handed = null
            coordinator.onReminderDue(ReminderViewKind.Checklist, medication.id, checkpoint.id)
            assertEquals(checkpoint.id, handed, checklistTestReminder(medication, checkpoint, state.progressOf(medication, todayKey, HolidaySchedule()), today))
        }
    }

    /** Whether a tap opens the view is Settings' to say; it changes nothing of what is said. */
    @Test fun openingTheViewChangesWhereATapLeadsAndNotTheWords() {
        val test = requireNotNull(checklistTestReminder(medication, medication.checkpoints.first(), progressOf(medication), today))
        assertEquals(false, test.opens)
        val opening = test.opening(true)
        assertEquals(true, opening.opens)
        assertEquals(test.words(mood = null), opening.words(mood = null))
        assertEquals(test, opening.opening(false))
    }

    @Test fun aCheckpointWithNothingDueTodayHasNothingToSend() {
        val saturdaysOnly = medication.copy(items = medication.items.filter { it.id == "itm_iron" })
        val evening = saturdaysOnly.checkpoints.last()
        assertNull(checklistTestReminder(saturdaysOnly, evening, progressOf(saturdaysOnly), today))
        assertEquals(
            TestReminder.Checklist(saturdaysOnly, evening, saturdaysOnly.items, today),
            checklistTestReminder(saturdaysOnly, evening, progressOf(saturdaysOnly, LocalDate.parse("2026-10-17")), today),
        )
    }

    /** Supplements' test is the checklist test its coordinator's notification is, at its checkpoint. */
    @Test fun supplementsTestIsWhatItsCoordinatorHandsItsNotifier() {
        val state = supplementsState(remind = true)
        val checklist = requireNotNull(state.supplementsChecklist)
        val checkpoint = checklist.checkpoints.single { it.id == SupplementsCheckpointId }
        assertEquals(handedFor(state, checklist, checkpoint), checklistTestReminder(checklist, checkpoint, state.progressOf(checklist, todayKey, HolidaySchedule()), today))
    }

    @Test fun caresTestIsWhatItsCoordinatorHandsItsNotifier() {
        val state = careState(remindMorning = true, remindEvening = true)
        val checklist = requireNotNull(state.careChecklist)
        LegacyCareSession.entries.forEach { session ->
            val checkpoint = checklist.checkpoints.single { it.id == session.storageId }
            assertEquals(session.name, handedFor(state, checklist, checkpoint), checklistTestReminder(checklist, checkpoint, state.progressOf(checklist, todayKey, HolidaySchedule()), today))
        }
    }

    /** Weight's test asks about today, as its coordinator's notification does. */
    @Test fun weightsTestAsksForTodayAsItsCoordinatorDoes() {
        val weight = testWeightMeasurement().let { it.copy(checkpoint = it.checkpoint.copy(remind = true)) }
        assertEquals(loggedHandedFor(AppState().withWeight(weight), weight), loggedTestReminder(weight, today, HolidaySchedule()))
    }

    /** Reading's test says the whole week is still to read, as if nothing were, even once the week is read and the real reminder stays quiet. */
    @Test fun readingsTestSaysTheWholeWeekIsStillToReadWhateverWasRead() {
        val reading = testReadingTime().let { it.copy(checkpoint = it.checkpoint.copy(remind = true)) }
        val test = loggedTestReminder(reading, today, HolidaySchedule())
        assertEquals(loggedHandedFor(AppState().withReading(reading), reading), test)
        assertEquals(DefaultWeeklyGoalMinutes, test?.minutesToGo)

        val weekRead = AppState(timeLogs = listOf(TimeLog(ReadingTimeId, "2026-10-12", 1L, minutes = DefaultWeeklyGoalMinutes))).withReading(reading)
        assertNull(loggedHandedFor(weekRead, reading))
        assertEquals(test, loggedTestReminder(reading, today, HolidaySchedule()))

        // A sick day makes the week's goal smaller, for the test as for the streak: 60 minutes over six days.
        val monday = LocalDate.parse("2026-10-12")
        val sickMonday = HolidaySchedule(sickDays = listOf(DayRange(monday, monday)))
        assertEquals(52, loggedTestReminder(reading, today, sickMonday)?.minutesToGo)

        // The whole week is what Reading's configuration asks: another goal, and less for an away day it is not due on.
        assertEquals(90, loggedTestReminder(reading.copy(goal = Dated.always(90)), today, HolidaySchedule())?.minutesToGo)
        val awayMonday = HolidaySchedule(holidays = listOf(DayRange(monday, monday)))
        assertEquals(60, loggedTestReminder(reading, today, awayMonday)?.minutesToGo)
        assertEquals(52, loggedTestReminder(reading.copy(whenAway = Dated.always(WhenAway.Excused)), today, awayMonday)?.minutesToGo)
    }

    /** The test sends what the coordinator would for the day it is tapped on, the planned activity with its next routine day, whatever the day: the week's days decide only when the real one comes. */
    @Test fun activitysTestIsTheCoordinatorsSubjectWhateverTheDay() {
        val workout = WorkoutReminderSettings(enabled = true)
        // 2026-10-14 is a Wednesday, with the gym planned on it.
        val records = AppState().withActivity(testActivityConfiguration().copy(week = Dated.always(WeekPlan(listOf(PlannedDay(DayOfWeek.WEDNESDAY, GymActivityId)))), whenAway = Dated.always(WhenAway.Due)))
        var handed: MoReminderSubject.Activity? = null
        WorkoutReminderCoordinator(
            settingsRepository = settings(ReminderSettings(workout = workout)),
            trackerRepository = readOnly(records),
            scheduler = object : WorkoutReminderScheduler {
                override fun replace(settings: WorkoutReminderSettings, days: Set<DayOfWeek>, earliestLocalDate: LocalDate?) = true
            },
            notifier = { subject -> handed = subject; true },
            todayKey = { todayKey },
            wallClockDate = { today },
        ).onReminderDue()
        val subject = activityReminderSubject(records, today.dayOfWeek)
        assertEquals(MoReminderSubject.Activity(GymActivityId, UiText.Verbatim("Gym"), UiText.Verbatim("Day A"), label = "Activity"), subject)
        assertEquals(subject, handed)
        assertEquals(TestReminder.Activity(subject), TestReminder.Activity(activityReminderSubject(records, today.dayOfWeek)))
        // On a day the week does not choose nothing is planned, and the test says so as the reminder would on such a day.
        assertEquals(MoReminderSubject.Activity(activityId = null, planned = null, nextDay = null, label = "Activity"), activityReminderSubject(records, DayOfWeek.THURSDAY))
    }

    /** Eating's test is about the day that just ended, as its coordinator's notification is. */
    @Test fun eatingsTestIsAboutTheDayThatJustEndedAsItsCoordinatorsIs() {
        val eating = testEatingQuestions().let { it.copy(checkpoint = it.checkpoint.copy(remind = true)) }
        val test = loggedTestReminder(eating, today, HolidaySchedule())
        assertEquals(ReminderDays(today, today.minusDays(1)), test?.days)
        assertEquals(loggedHandedFor(AppState().withEating(eating), eating), test)
    }

    /** The check-in asks about the day its coordinator does while that day is not logged; the test asks even once it is. */
    @Test fun substancesTestAsksAboutTheAskedDayAsItsCoordinatorDoes() {
        val settings = SubstanceSettings(checkIn = SubstanceCheckIn(remind = true))
        fun coordinator(state: AppState, handed: (String) -> Unit) = SubstanceCheckInCoordinator(
            trackerRepository = readOnly(state),
            featureVisibility = FeatureVisibilitySource { FeatureVisibilitySettings() },
            gateway = object : SubstanceCheckInGateway {
                override fun canScheduleExact() = true
                override fun schedule(at: CheckpointTime, earliestDate: LocalDate?, fallbackAlarmTime: ReminderTime, fixedBedtime: ReminderTime?) = true
                override fun cancel() = Unit
            },
            notifier = { dateKey -> handed(dateKey); true },
            reminderSettings = { ReminderSettings() },
            todayKey = { todayKey },
        )
        var handed: String? = null
        coordinator(AppState(substances = settings)) { handed = it }.onReminderDue()
        // Settings sends the check-in about the day the coordinator asks about: yesterday.
        val askedKey = de.nicolasmahn.momentum.domain.substance.substanceAskedDay(today).toString()
        assertEquals(TestReminder.Substances(askedKey), TestReminder.Substances(requireNotNull(handed)))

        val logged = AppState(substances = settings, substanceNoneDays = listOf(SubstanceNoneDay(askedKey, 1L)))
        coordinator(logged) { error("Nothing is asked once the day is logged") }.onReminderDue()
    }

    /** He fumes about everything he asks about today, kept or not, in his ledger's order; a task a free day covers is not asked for. */
    @Test fun mosTestFumesAboutEveryTaskHeAsksAboutToday() {
        val mood = mood(missingToday = listOf(MoNeed.Reading), keptToday = listOf(MoNeed.Activity), freeToday = listOf(MoNeed.Weight))
        assertEquals(TestReminder.MoCheckIn(mood, listOf(MoNeed.Activity, MoNeed.Reading).map { it.view.trackerRef }), moCheckInTestReminder(mood))
    }

    @Test fun moHasNothingToSendWhileHeAsksAboutNothing() {
        assertNull(moCheckInTestReminder(null))
        assertNull(moCheckInTestReminder(mood(freeToday = listOf(MoNeed.Weight))))
    }

    /** What the coordinator hands its notifier when [checkpoint] of [checklist] rings today over [state], as a test. */
    private fun handedFor(state: AppState, checklist: ChecklistConfiguration, checkpoint: Checkpoint): TestReminder.Checklist? {
        var handed: TestReminder.Checklist? = null
        coordinator(state, onChecklist = { handed = it }, onLogged = { error("No logged view rings here.") })
            .onReminderDue(ReminderViewKind.Checklist, checklist.id, checkpoint.id)
        return handed
    }

    /** What the coordinator hands its notifier when [view] rings today over [state], as a test, or null where it stays quiet. */
    private fun loggedHandedFor(state: AppState, view: LoggedView): TestReminder.Logged? {
        var handed: TestReminder.Logged? = null
        coordinator(state, onChecklist = { error("No checklist rings here.") }, onLogged = { handed = it })
            .onReminderDue(view.kind.reminderKind, view.id, view.checkpoint.id)
        return handed
    }

    private fun coordinator(state: AppState, onChecklist: (TestReminder.Checklist) -> Unit, onLogged: (TestReminder.Logged) -> Unit) = ChecklistReminderCoordinator(
        trackerRepository = readOnly(state),
        featureVisibility = FeatureVisibilitySource { FeatureVisibilitySettings() },
        gateway = object : ChecklistReminderGateway {
            override fun canScheduleExact() = true
            override fun shown() = emptySet<String>()
            override fun schedule(slot: ReminderSlot, earliestDate: LocalDate?, fallbackAlarmTime: ReminderTime, fixedBedtime: ReminderTime?) = true
            override fun cancel(key: String) = Unit
            override fun dismiss(key: String) = Unit
            override fun keepUnsaved(key: String, why: UiText) = false
        },
        runtime = MemoryReminderRuntime(),
        notifier = { checklist, checkpoint, open, _, days, _ -> onChecklist(TestReminder.Checklist(checklist, checkpoint, open, days.dueDay)); true },
        loggedNotifier = object : LoggedReminderNotifier {
            override fun show(view: LoggedView, key: String, days: ReminderDays, quietly: Boolean) = error("A logged view's words are handed what they name.")
            override fun show(view: LoggedView, minutesToGo: Int?, key: String, days: ReminderDays, quietly: Boolean): Boolean {
                onLogged(TestReminder.Logged(view, days, minutesToGo = minutesToGo))
                return true
            }
        },
        reminderSettings = { ReminderSettings() },
        todayKey = { todayKey },
    )

    private fun progressOf(checklist: ChecklistConfiguration, day: LocalDate = today) =
        AppState(checklists = listOf(checklist)).progressOf(checklist, day.toString(), HolidaySchedule())

    private fun readOnly(state: AppState) = object : TrackerRepository {
        override fun load() = state
        override fun save(state: AppState) = error("read only")
        override fun update(transform: (AppState) -> AppState): TrackerUpdateResult = error("read only")
    }

    private fun settings(settings: ReminderSettings) = object : ReminderSettingsRepository {
        override fun load() = settings
        override fun save(settings: ReminderSettings) = error("read only")
    }

    private fun mood(
        missingToday: List<MoNeed> = emptyList(),
        keptToday: List<MoNeed> = emptyList(),
        freeToday: List<MoNeed> = emptyList(),
    ) = MoMood(
        state = MoState.Steady,
        missingTrackersToday = missingToday.map { it.view.trackerRef },
        keptTrackersToday = keptToday.map { it.view.trackerRef },
        freeTrackersToday = freeToday.map { it.view.trackerRef },
        latestTrackerToday = keptToday.lastOrNull()?.view?.trackerRef,
        dueToday = missingToday.size + keptToday.size + freeToday.size,
        daysCold = 0,
        streak = MoStreak(
            days = 0,
            freezes = 0,
            freezeCapacity = 3,
            week = listOf(MoDayMark.Open) + List(6) { MoDayMark.Upcoming },
            weekCanEarnFreeze = true,
        ),
    )
}

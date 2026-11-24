package de.nicolasmahn.momentum.reminder

import de.nicolasmahn.momentum.domain.mo.MoChoices
import de.nicolasmahn.momentum.domain.time.TimeLog
import de.nicolasmahn.momentum.domain.settings.showingOnly
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import de.nicolasmahn.momentum.core.text.resolve
import de.nicolasmahn.momentum.domain.AppState
import de.nicolasmahn.momentum.domain.checklist.Checkpoint
import de.nicolasmahn.momentum.domain.checklist.CheckpointTime
import de.nicolasmahn.momentum.domain.measurement.MeasurementLog
import de.nicolasmahn.momentum.domain.measurement.testMadeMeasurement
import de.nicolasmahn.momentum.domain.mo.TrackerMoPolicy
import de.nicolasmahn.momentum.domain.ref
import de.nicolasmahn.momentum.domain.trackerRef
import de.nicolasmahn.momentum.domain.compatibility.LegacyCareSession
import de.nicolasmahn.momentum.domain.holiday.DayRange
import de.nicolasmahn.momentum.domain.holiday.HolidaySchedule
import de.nicolasmahn.momentum.domain.mo.MoMood
import de.nicolasmahn.momentum.domain.compatibility.MoNeed
import de.nicolasmahn.momentum.domain.mo.keeping
import de.nicolasmahn.momentum.domain.supplement.withSupplements
import de.nicolasmahn.momentum.domain.mo.moMood
import de.nicolasmahn.momentum.domain.mo.moMoodWithStoredChoices
import de.nicolasmahn.momentum.domain.reminder.MoCheckInSettings
import de.nicolasmahn.momentum.domain.reminder.ReminderSettings
import de.nicolasmahn.momentum.domain.reminder.ReminderTime
import de.nicolasmahn.momentum.domain.reminder.WorkoutReminderSettings
import de.nicolasmahn.momentum.domain.settings.FeatureVisibilitySettings
import de.nicolasmahn.momentum.persistence.ReminderSettingsRepository
import de.nicolasmahn.momentum.persistence.TrackerRepository
import de.nicolasmahn.momentum.persistence.TrackerUpdateResult
import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import de.nicolasmahn.momentum.domain.care.withCare
import de.nicolasmahn.momentum.domain.care.everyCareAction
import de.nicolasmahn.momentum.domain.care.careState
import de.nicolasmahn.momentum.domain.compatibility.defaultReadingTime
import de.nicolasmahn.momentum.domain.compatibility.defaultWeightMeasurement
import de.nicolasmahn.momentum.domain.measurement.withWeight

@RunWith(RobolectricTestRunner::class)
class MoCheckInCoordinatorTest {
    private val today = "2026-09-16"

    private val practice = testMadeMeasurement(id = "daily_practice", label = "Daily practice").copy(
        mo = TrackerMoPolicy(enabled = true),
        checkpoint = Checkpoint("day", CheckpointTime.AfterWake(15), remind = false),
    )
    private fun AppState.recordPractice(day: LocalDate): AppState = copy(
        measurements = measurements + listOf(practice).filter { configured -> measurements.none { it.id == configured.id } },
        measurementLogs = measurementLogs.filterNot { it.measurementId == practice.id && it.dateKey == day.toString() } +
            MeasurementLog(practice.id, day.toString(), day.toEpochDay(), 1.0),
    )
    private val practiceYesterday = AppState().recordPractice(LocalDate.parse("2026-09-13"))
        .recordPractice(LocalDate.parse("2026-09-15"))
    private val practiceToday = practiceYesterday.recordPractice(LocalDate.parse(today))

    private fun AppState.practiceReminded() = copy(measurements = measurements.map { view ->
        if (view.id == practice.id) view.copy(checkpoint = view.checkpoint.copy(remind = true)) else view
    })

    private fun AppState.weightReminded(): AppState {
        val configured = defaultWeightMeasurement(label = "Weight", remind = true)
        return copy(measurements = measurements.filterNot { it.id == configured.id } + configured)
    }

    private val careReminderSettings = ReminderSettings()

    // Both switches are on, as in the settings above; in the app they are Care's checkpoints.
    private val morningCareDone = careState(
        *everyCareAction("2026-09-28"),
        *everyCareAction("2026-09-29").filter { it.session == LegacyCareSession.MORNING }.toTypedArray(),
        remindMorning = true,
        remindEvening = true,
    )

    @Test
    fun futureEveningCareDoesNotSpeakAtAStale20AlarmOr21Nudge() {
        val fixture = Fixture(state = morningCareDone, settings = careReminderSettings)

        assertEquals(true, fixture.coordinator.onReminderDue("2026-09-29T20:00"))
        assertEquals(true, fixture.coordinator.onReminderDue("2026-09-29T21:00"))

        assertEquals(emptyList<Shown>(), fixture.notifier.shown)
        assertEquals(2, fixture.notifier.cancelCalls)
        assertEquals(2, fixture.scheduler.calls.size)
    }

    @Test
    fun aFutureCareSessionDoesNotKeepAnEarlierNotification() {
        val fixture = Fixture(
            state = morningCareDone,
            settings = careReminderSettings,
            now = "2026-09-29T20:15",
        )

        fixture.coordinator.retireIfSettled()

        assertEquals(1, fixture.notifier.cancelCalls)
    }

    @Test
    fun overdueTasksAreNudgedThroughoutTheDayAndStopAfterCompletion() {
        val fixture = Fixture(state = practiceYesterday.practiceReminded())

        listOf(12, 15, 18, 21).forEach { hour ->
            assertEquals(true, fixture.coordinator.onReminderDue("2026-09-16T$hour:00"))
        }

        assertEquals(4, fixture.notifier.shown.size)
        assertEquals(listOf(true, true, true, true), fixture.notifier.shown.map { it.angry })
        fixture.trackerRepository.save(practiceToday.practiceReminded())
        assertEquals(true, fixture.coordinator.onReminderDue("2026-09-17T00:00"))
        assertEquals(4, fixture.notifier.shown.size)
        assertEquals(1, fixture.notifier.cancelCalls)
        assertEquals(5, fixture.scheduler.calls.size)
    }

    @Test
    fun anOverdueTaskMakesHimFumeAtANudgeAndTheNextOneIsScheduled() {
        val fixture = Fixture(state = practiceYesterday.practiceReminded())

        assertEquals(true, fixture.coordinator.onReminderDue("2026-09-16T15:00"))

        assertEquals(
            listOf(
                Shown(
                    title = "Mo is boiling over",
                    text = "It's 15:00! Overdue: Daily practice. Off you go, and watch me cool down.",
                    angry = true,
                ),
            ),
            fixture.notifier.shown,
        )
        assertEquals(
            listOf(ScheduleCall(MoCheckInSettings(), LocalDateTime.parse("2026-09-16T15:00"))),
            fixture.scheduler.calls,
        )
    }

    @Test
    fun aLateStartIsToldEverythingWhoseTimeHasPassedAndNothingStillToCome() {
        val sunday = LocalDate.parse("2026-09-13")
        val tuesday = LocalDate.parse("2026-09-15")
        // Each configured daily task is overdue; Activity is asked for later in the day.
        val nothingDoneToday = AppState()
            .keeping(MoNeed.Activity, sunday)
            .keeping(MoNeed.Weight, sunday).keeping(MoNeed.Weight, tuesday)
            .keeping(MoNeed.Care, tuesday)
            .keeping(MoNeed.Supplements, tuesday)
            // The settings below show Supplements' and Care's morning switches on, which in the app
            // are their checkpoints', as the measurements' reminders are.
            .withSupplements(remind = true)
            .withCare(remindMorning = true)
            .weightReminded()
            .recordPractice(sunday).recordPractice(tuesday)
            .practiceReminded()
        val settings = ReminderSettings(
            workout = WorkoutReminderSettings(enabled = true, fixedTime = ReminderTime(18, 0)),
        )
        val fixture = Fixture(state = nothingDoneToday, settings = settings, is24Hour = false)

        fixture.coordinator.onReminderDue("2026-09-16T15:00")
        fixture.coordinator.onReminderDue("2026-09-16T21:00")

        assertEquals(
            listOf(
                "It's 3 PM! Overdue: Supplements, Care, Weight, Daily practice. " +
                    "Off you go, and watch me cool down.",
                "9 PM, and I'm still waiting for: Supplements, Care, Weight, Daily practice, Activity. " +
                    "Go now, before I boil over.",
            ),
            fixture.notifier.shown.map { it.text },
        )
    }

    @Test
    fun aStale20AlarmReschedulesWithoutSendingACalmSummary() {
        val fixture = Fixture(state = practiceYesterday)

        assertEquals(true, fixture.coordinator.onReminderDue("2026-09-16T20:00"))
        assertEquals(emptyList<Shown>(), fixture.notifier.shown)
        assertEquals(1, fixture.scheduler.calls.size)
        assertEquals(LocalDateTime.parse("2026-09-16T20:00"), fixture.scheduler.calls.single().firedNudge)

        assertEquals(true, fixture.coordinator.onReminderDue("2026-09-16T21:00"))
        assertEquals(listOf("Mo is heating up"), fixture.notifier.shown.map { it.title })
    }

    @Test
    fun aFinishedDayAndADayWithNothingDueStaySilentButKeepScheduling() {
        listOf(practiceToday.practiceReminded(), AppState()).forEach { state ->
            val fixture = Fixture(state = state)

            assertEquals(true, fixture.coordinator.onReminderDue("2026-09-16T20:00"))

            assertEquals(emptyList<Shown>(), fixture.notifier.shown)
            assertEquals(1, fixture.scheduler.calls.size)
        }
    }

    @Test
    fun aTaskOfAHiddenFeatureIsNeitherOpenNorOverdue() {
        val fixture = Fixture(
            state = practiceYesterday.practiceReminded(),
            visibility = FeatureVisibilitySettings(disabledFeatures = emptySet()).hiding(practice.ref),
        )

        assertEquals(true, fixture.coordinator.onReminderDue("2026-09-16T20:00"))

        assertEquals(emptyList<Shown>(), fixture.notifier.shown)
    }

    @Test
    fun disabledCheckInCancelsItsAlarmAndStaysSilent() {
        val fixture = Fixture(
            state = practiceYesterday.practiceReminded(),
            settings = ReminderSettings(moCheckIn = MoCheckInSettings(enabled = false)),
        )

        assertEquals(true, fixture.coordinator.onReminderDue("2026-09-16T20:00"))

        assertEquals(listOf(false), fixture.scheduler.calls.map { it.settings.enabled })
        assertEquals(emptyList<Shown>(), fixture.notifier.shown)
    }

    @Test
    fun disablingNudgesRetiresAnOverdueNotification() {
        val fixture = Fixture(
            state = practiceYesterday.practiceReminded(),
            settings = ReminderSettings(moCheckIn = MoCheckInSettings(enabled = false)),
            now = "2026-09-16T15:00",
        )

        fixture.coordinator.reconcile()

        assertEquals(1, fixture.notifier.cancelCalls)
        assertEquals(listOf(false), fixture.scheduler.calls.map { it.settings.enabled })
    }

    @Test
    fun blockedNotificationAndFailedSchedulingAreObservable() {
        val blocked = Fixture(state = practiceYesterday.practiceReminded(), notifierResult = false)
        assertEquals(false, blocked.coordinator.onReminderDue("2026-09-16T15:00"))

        val unscheduled = Fixture(state = practiceYesterday.practiceReminded(), schedulerResult = false)
        assertEquals(false, unscheduled.coordinator.onReminderDue("2026-09-16T15:00"))
        assertEquals(1, unscheduled.notifier.shown.size)
    }

    @Test
    fun anAlarmThatCarriesNoNudgeSpeaksForTheMinuteItArrivesIn() {
        val fixture = Fixture(state = practiceYesterday, now = "2026-09-16T20:00:07")

        fixture.coordinator.onReminderDue(scheduledNudge = null)

        assertEquals(emptyList<Shown>(), fixture.notifier.shown)
        assertEquals(
            listOf(LocalDateTime.parse("2026-09-16T20:00")),
            fixture.scheduler.calls.map { it.firedNudge },
        )
    }

    @Test
    fun beforeTheEveningCheckInItLeavesOnceNothingIsLateAnyMore() {
        // The daily measurement is overdue from 09:15; the workout is only asked for at 18:00.
        val settings = ReminderSettings(workout = WorkoutReminderSettings(enabled = true))
        val activityStillToCome = practiceYesterday.practiceReminded().keeping(MoNeed.Activity, LocalDate.parse("2026-09-13"))
        val fixture = Fixture(state = activityStillToCome, settings = settings, now = "2026-09-16T15:20")

        fixture.coordinator.retireIfSettled()
        assertEquals(0, fixture.notifier.cancelCalls)

        fixture.coordinator.retireIfSettled(activityStillToCome.recordPractice(LocalDate.parse(today)))
        assertEquals(1, fixture.notifier.cancelCalls)
    }

    @Test
    fun anOpenTaskWithoutAnythingOverdueDoesNotKeepANotification() {
        val fixture = Fixture(state = practiceYesterday, now = "2026-09-16T20:00:30")

        fixture.coordinator.retireIfSettled()
        assertEquals(1, fixture.notifier.cancelCalls)

        fixture.coordinator.retireIfSettled(practiceToday)
        assertEquals(2, fixture.notifier.cancelCalls)
    }

    @Test
    fun aSilentNudgeTakesDownWhatAnEarlierOneSaid() {
        val fixture = Fixture(state = practiceYesterday)

        fixture.coordinator.onReminderDue("2026-09-16T12:00")

        assertEquals(emptyList<Shown>(), fixture.notifier.shown)
        assertEquals(1, fixture.notifier.cancelCalls)
    }

    @Test
    fun reconcileSchedulesTheNextNudgeAndRetiresASettledCheckIn() {
        val settings = ReminderSettings(
            moCheckIn = MoCheckInSettings(fixedTime = ReminderTime(21, 30)),
        )
        val fixture = Fixture(state = practiceToday, settings = settings)

        assertEquals(true, fixture.coordinator.reconcile())

        assertEquals(
            listOf(ScheduleCall(settings.moCheckIn, firedNudge = null)),
            fixture.scheduler.calls,
        )
        assertEquals(1, fixture.notifier.cancelCalls)
    }

    @Test
    fun afterMidnightCompletionSettlesThePreviousHabitDay() {
        val fixture = Fixture(state = practiceToday.practiceReminded(), now = "2026-09-17T01:00")

        fixture.coordinator.retireIfSettled()

        assertEquals(1, fixture.notifier.cancelCalls)
    }

    @Test
    fun afterMidnightNudgeUsesThePreviousHabitDaysRecords() {
        val fixture = Fixture(state = practiceToday.practiceReminded())

        fixture.coordinator.onReminderDue("2026-09-17T01:00")

        assertEquals(emptyList<Shown>(), fixture.notifier.shown)
        assertEquals(1, fixture.notifier.cancelCalls)
    }

    @Test
    fun afterMidnightAnOverdueTaskStillKeepsItsNotification() {
        val fixture = Fixture(state = practiceYesterday, now = "2026-09-17T01:00")

        fixture.coordinator.retireIfSettled()

        assertEquals(0, fixture.notifier.cancelCalls)
    }

    @Test
    fun aHolidayThatExcusesWeighingKeepsTheCheckInAsQuietAsHome() {
        assertQuietLikeHome(MoRewardInputs(holidays = HolidaySchedule(listOf(DayRange(wednesday, wednesday)))))
    }

    @Test
    fun aChosenPassKeepsTheCheckInAsQuietAsHome() {
        assertQuietLikeHome(MoRewardInputs(choices = MoChoices(setOf(MoNeed.Weight.view.trackerRef))))
    }

    @Test
    fun persistedRuleStartDoesNotChangeTheConfiguredTimerIntoADailyTask() {
        val weekly = AppState(
            time = listOf(defaultReadingTime(label = "Weekly practice", remind = true)),
            timeLogs = listOf(TimeLog("reading", "2026-09-15", 1L, 15)),
        )
        val rewards = MoRewardInputs(rewardStart = LocalDate.parse("2026-09-14"))
        val before = moMood(weekly, FeatureVisibilitySettings(), LocalDate.parse(today))
        val home = homeMood(weekly, FeatureVisibilitySettings(), LocalDate.parse(today), rewards)
        assertEquals(emptyList<de.nicolasmahn.momentum.domain.TrackerRef>(), before.missingTrackersToday)
        assertEquals(before.missingTrackersToday, home.missingTrackersToday)
        val fixture = Fixture(state = weekly, rewards = rewards)

        assertEquals(true, fixture.coordinator.onReminderDue("2026-09-16T15:00"))
        assertEquals(emptyList<Shown>(), fixture.notifier.shown)
        assertEquals(listOf(LocalDate.parse(today)), fixture.rewardDays)
    }

    @Test
    fun choosingAPassTakesDownACheckInThatIsAlreadyShowing() {
        var rewards = MoRewardInputs()
        val state = AppState()
            .keeping(MoNeed.Weight, wednesday.minusDays(2))
            .keeping(MoNeed.Weight, wednesday.minusDays(1))
            .weightReminded()
        val fixture = Fixture(
            state = state,
            visibility = showingOnly(MoNeed.Weight),
            now = "2026-09-30T12:30",
            rewardSource = { rewards },
        )
        assertEquals(true, fixture.coordinator.onReminderDue("2026-09-30T12:00"))
        assertEquals(1, fixture.notifier.shown.size)
        assertEquals(0, fixture.notifier.cancelCalls)

        rewards = MoRewardInputs(choices = MoChoices(setOf(MoNeed.Weight.view.trackerRef)))
        fixture.coordinator.retireIfSettled(state)

        assertEquals(1, fixture.notifier.cancelCalls)
    }

    /**
     * A stored choice that no longer stands quiets nothing, here as on Home: Care set aside while
     * both its sessions are open and its bank holds one token leaves the check-in showing.
     */
    @Test
    fun aStoredChoiceThatNoLongerStandsLeavesTheCheckInShowing() {
        var rewards = MoRewardInputs()
        val state = AppState()
            .keeping(MoNeed.Care, wednesday.minusDays(2))
            .keeping(MoNeed.Care, wednesday.minusDays(1))
            .withCare(remindMorning = true)
        val fixture = Fixture(
            state = state,
            settings = ReminderSettings(),
            visibility = showingOnly(MoNeed.Care),
            now = "2026-09-30T12:30",
            rewardSource = { rewards },
        )
        assertEquals(true, fixture.coordinator.onReminderDue("2026-09-30T12:00"))
        assertEquals(1, fixture.notifier.shown.size)

        rewards = MoRewardInputs(choices = MoChoices(setOf(MoNeed.Care.view.trackerRef)))
        fixture.coordinator.retireIfSettled(state)

        assertEquals(0, fixture.notifier.cancelCalls)
    }

    private val wednesday = LocalDate.parse("2026-09-30")

    /** Weighing is overdue at noon by the rules alone; [rewards] must excuse it, here as on Home. */
    private fun assertQuietLikeHome(rewards: MoRewardInputs) {
        val state = AppState()
            .keeping(MoNeed.Weight, wednesday.minusDays(2))
            .keeping(MoNeed.Weight, wednesday.minusDays(1))
            .weightReminded()
        val onlyWeight = showingOnly(MoNeed.Weight)
        assertEquals(true, MoNeed.Weight.view.trackerRef in moMood(state, onlyWeight, wednesday).missingTrackersToday)
        assertEquals(false, MoNeed.Weight.view.trackerRef in homeMood(state, onlyWeight, wednesday, rewards).missingTrackersToday)
        val fixture = Fixture(
            state = state,
            visibility = onlyWeight,
            rewards = rewards,
        )

        assertEquals(true, fixture.coordinator.onReminderDue("2026-09-30T12:00"))

        assertEquals(emptyList<Shown>(), fixture.notifier.shown)
        assertEquals(listOf(wednesday), fixture.rewardDays)
    }

    /** What Home derives: the same call MomentumApp makes with its live holidays, passes and rule start. */
    private fun homeMood(
        state: AppState,
        visibility: FeatureVisibilitySettings,
        day: LocalDate,
        rewards: MoRewardInputs,
    ) = moMoodWithStoredChoices(state, visibility, day, rewards.holidays, rewards.choices, rewards.rewardStart)

    private inner class Fixture(
        state: AppState,
        settings: ReminderSettings = ReminderSettings(),
        visibility: FeatureVisibilitySettings = FeatureVisibilitySettings(),
        rewards: MoRewardInputs = MoRewardInputs(),
        rewardSource: () -> MoRewardInputs = { rewards },
        schedulerResult: Boolean = true,
        notifierResult: Boolean = true,
        is24Hour: Boolean = true,
        now: String = "${today}T09:00",
    ) {
        val scheduler = FakeScheduler(schedulerResult)
        val notifier = FakeNotifier(notifierResult)
        val trackerRepository = FakeTrackerRepository(state)
        val rewardDays = mutableListOf<LocalDate>()
        val rewardInputs = MoRewardInputsSource { day -> rewardSource().also { rewardDays += day } }
        val coordinator = MoCheckInCoordinator(
            settingsRepository = FakeSettingsRepository(settings),
            trackerRepository = trackerRepository,
            scheduler = scheduler,
            notifier = notifier,
            rewardInputs = rewardInputs,
            featureVisibility = FeatureVisibilitySource { visibility },
            is24Hour = { is24Hour },
            now = { LocalDateTime.parse(now) },
        )
    }

    private data class ScheduleCall(
        val settings: MoCheckInSettings,
        val firedNudge: LocalDateTime?,
    )

    private data class Shown(
        val title: String,
        val text: String,
        val angry: Boolean,
    )

    private class FakeScheduler(private val result: Boolean) : MoCheckInScheduler {
        val calls = mutableListOf<ScheduleCall>()

        override fun replace(
            settings: MoCheckInSettings,
            firedNudge: LocalDateTime?,
        ): Boolean {
            calls += ScheduleCall(settings, firedNudge)
            return result
        }
    }

    private class FakeNotifier(private val result: Boolean) : MoCheckInNotifier {
        val shown = mutableListOf<Shown>()
        var cancelCalls = 0

        override fun show(
            message: MoReminderMessage,
            mood: MoMood,
            angry: Boolean,
        ): Boolean {
            val resources = ApplicationProvider.getApplicationContext<Context>().resources
            shown += Shown(message.title.resolve(resources), message.text.resolve(resources), angry)
            return result
        }

        override fun cancel() {
            cancelCalls += 1
        }
    }

    private class FakeSettingsRepository(
        private val settings: ReminderSettings,
    ) : ReminderSettingsRepository {
        override fun load(): ReminderSettings = settings

        override fun save(settings: ReminderSettings): Boolean = true
    }

    private class FakeTrackerRepository(
        private var state: AppState,
    ) : TrackerRepository {
        override fun load(): AppState = state

        override fun save(state: AppState): Boolean {
            this.state = state
            return true
        }

        override fun update(transform: (AppState) -> AppState): TrackerUpdateResult {
            state = transform(state)
            return TrackerUpdateResult.Updated(state)
        }
    }
}

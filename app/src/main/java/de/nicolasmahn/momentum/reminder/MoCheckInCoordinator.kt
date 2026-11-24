package de.nicolasmahn.momentum.reminder

import de.nicolasmahn.momentum.core.time.habitDate
import de.nicolasmahn.momentum.domain.AppState
import de.nicolasmahn.momentum.domain.mo.MoMood
import de.nicolasmahn.momentum.domain.mo.moMoodWithStoredChoices
import de.nicolasmahn.momentum.domain.mo.moOverdueTrackers
import de.nicolasmahn.momentum.domain.reminder.MoCheckInSettings
import de.nicolasmahn.momentum.domain.reminder.ReminderSettings
import de.nicolasmahn.momentum.domain.settings.FeatureVisibilitySettings
import de.nicolasmahn.momentum.persistence.ReminderSettingsRepository
import de.nicolasmahn.momentum.persistence.TrackerRepository
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

internal interface MoCheckInScheduler {
    /** [firedNudge] is the nudge being answered, so the alarm moves on to the one after it. */
    fun replace(
        settings: MoCheckInSettings,
        firedNudge: LocalDateTime? = null,
    ): Boolean
}

internal interface MoCheckInNotifier {
    /** Shows the one check-in notification, alerting again even when it replaces an earlier one. */
    fun show(message: MoReminderMessage, mood: MoMood, angry: Boolean): Boolean

    fun cancel()
}

/**
 * Mo looks in several times a day and only speaks when something is overdue.
 *
 * He belongs to no dashboard feature, so his check-in is not gated by feature visibility. Hidden
 * features still shape it, because his mood only counts what is visible.
 */
internal class MoCheckInCoordinator(
    private val settingsRepository: ReminderSettingsRepository,
    private val trackerRepository: TrackerRepository,
    private val scheduler: MoCheckInScheduler,
    private val notifier: MoCheckInNotifier,
    private val rewardInputs: MoRewardInputsSource,
    private val featureVisibility: FeatureVisibilitySource = FeatureVisibilitySource {
        FeatureVisibilitySettings()
    },
    private val is24Hour: () -> Boolean = { true },
    private val now: () -> LocalDateTime = LocalDateTime::now,
) {
    fun reconcile(): Boolean {
        retireIfSettled()
        return scheduler.replace(settingsRepository.load().moCheckIn)
    }

    /**
     * [scheduledNudge] is the moment the alarm was set for. He speaks for that moment rather than
     * for the clock, so a late delivery names only tasks overdue at the scheduled moment.
     * Only an alarm set before nudges existed arrives without one.
     */
    fun onReminderDue(scheduledNudge: String? = null): Boolean {
        val reminders = settingsRepository.load()
        val settings = reminders.moCheckIn
        val nudge = scheduledNudge?.let(::parseNudge) ?: now().truncatedTo(ChronoUnit.MINUTES)
        val nextScheduled = scheduler.replace(settings = settings, firedNudge = nudge)
        if (!settings.enabled) return nextScheduled

        // A mood that cannot be derived leaves nothing truthful to say, so he stays silent.
        val mood = moodOf(habitDate(nudge), trackerRepository::load) ?: return false
        val overdue = moOverdueTrackers(mood, reminders, nudge.toLocalTime())
        val message = moNudgeMessage(
            mood = mood,
            overdue = overdue,
            nudge = nudge,
            is24Hour = is24Hour(),
        )
        if (message == null) {
            retireIfSettled(mood, reminders, nudge)
            return nextScheduled
        }
        return notifier.show(message, mood, angry = overdue.isNotEmpty()) && nextScheduled
    }

    /**
     * Takes a check-in that is already showing back down once it has nothing left to say. Any
     * record can settle the day, so this runs after every save rather than per feature.
     */
    fun retireIfSettled(state: AppState? = null) {
        val at = now()
        val mood = moodOf(habitDate(at)) { state ?: trackerRepository.load() } ?: return
        retireIfSettled(mood, settingsRepository.load(), at)
    }

    private fun retireIfSettled(mood: MoMood, reminders: ReminderSettings, at: LocalDateTime) {
        if (!reminders.moCheckIn.enabled || moOverdueTrackers(mood, reminders, at.toLocalTime()).isEmpty()) {
            notifier.cancel()
        }
    }

    private fun moodOf(today: LocalDate, state: () -> AppState): MoMood? = runCatching {
        val rewards = rewardInputs.load(today)
        moMoodWithStoredChoices(
            state = state(),
            featureVisibility = featureVisibility.load(),
            today = today,
            holidays = rewards.holidays,
            storedChoices = rewards.choices,
            rewardStart = rewards.rewardStart,
        )
    }.getOrNull()
}

private fun parseNudge(nudge: String): LocalDateTime? =
    runCatching { LocalDateTime.parse(nudge) }.getOrNull()

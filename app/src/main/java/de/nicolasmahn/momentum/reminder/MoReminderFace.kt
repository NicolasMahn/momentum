package de.nicolasmahn.momentum.reminder

import android.content.Context
import android.graphics.Bitmap
import de.nicolasmahn.momentum.core.ui.theme.MomentumTransparent
import de.nicolasmahn.momentum.domain.mo.MoMood
import de.nicolasmahn.momentum.domain.mo.moMoodWithStoredChoices
import de.nicolasmahn.momentum.feature.mo.MoLook
import de.nicolasmahn.momentum.feature.mo.moPicture
import de.nicolasmahn.momentum.feature.mo.renderMoBitmap
import de.nicolasmahn.momentum.persistence.PreferencesFeatureVisibilityRepository
import de.nicolasmahn.momentum.persistence.PreferencesReminderSettingsRepository
import de.nicolasmahn.momentum.persistence.PreferencesTrackerRepository
import de.nicolasmahn.momentum.core.time.currentHabitDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Mo on a reminder: the mood his words follow and his resting face for the large icon. He is drawn
 * on nothing, so the system tints the notification's own surface behind him.
 */
internal class MoReminderFace(
    val mood: MoMood?,
    val largeIcon: Bitmap?,
)

/**
 * Deriving a mood reads storage and drawing him allocates a bitmap, and neither is worth a lost
 * reminder: any failure leaves a plain notification without Mo, which still says what to do.
 */
internal fun moReminderFace(context: Context): MoReminderFace {
    val mood = runCatching {
        val today = currentHabitDate()
        val rewards = moRewardInputsSource(context).load(today)
        moMoodWithStoredChoices(
            state = PreferencesTrackerRepository(context).load(),
            featureVisibility = PreferencesFeatureVisibilityRepository(context).load(),
            today = today,
            holidays = rewards.holidays,
            storedChoices = rewards.choices,
            rewardStart = rewards.rewardStart,
        )
    }.getOrNull()
    return MoReminderFace(mood = mood, largeIcon = mood?.let { moLargeIcon(context, it) })
}

/**
 * Mo's resting face for a mood the caller already derived, or null when he cannot be drawn.
 * [angry] says something is overdue; his look decides whether he still burns enough to show it.
 */
internal fun moLargeIcon(
    context: Context,
    mood: MoMood,
    angry: Boolean = false,
): Bitmap? = runCatching {
    val reminders = PreferencesReminderSettingsRepository(context).load()
    // The habit day, like the app's, so all three surfaces pick the same scene.
    val now = LocalDateTime.of(currentHabitDate(), LocalTime.now())
    renderMoBitmap(
        context = context,
        // The same scene the app and the widget show at this hour, at rest.
        look = MoLook.of(mood, angry = angry, scene = moPicture(mood, reminders, now, context.currentBedtime(reminders)).scene),
        heightDp = FigureHeightDp,
        ground = MomentumTransparent,
    )
}.getOrNull()

private const val FigureHeightDp = 96f

package de.nicolasmahn.momentum.feature.mo

import de.nicolasmahn.momentum.domain.mo.MoFeeling
import de.nicolasmahn.momentum.domain.mo.MoMood
import de.nicolasmahn.momentum.domain.mo.TrackerAsked
import de.nicolasmahn.momentum.domain.mo.moAskedTrackers
import de.nicolasmahn.momentum.domain.mo.moFeeling
import de.nicolasmahn.momentum.domain.reminder.Bedtime
import de.nicolasmahn.momentum.domain.reminder.ReminderSettings
import de.nicolasmahn.momentum.feature.mo.acts.MoScene
import de.nicolasmahn.momentum.feature.mo.acts.moScene
import java.time.LocalDateTime

/**
 * Mo as he stands at one moment, before anything passing such as a save takes over: what his
 * reminders have asked for, how he feels about it, the scene he plays and the look that results.
 */
internal class MoPicture(
    val asked: TrackerAsked,
    val feeling: MoFeeling,
    val scene: MoScene?,
    val look: MoLook,
    /**
     * Which of an activity's acts a save plays today. It is the day itself, so tomorrow's save
     * plays the next act with nothing to remember in between.
     */
    val saveTurn: Int,
) {
    val angry: Boolean get() = asked.overdueBy.isNotEmpty()
}

/**
 * The one way from a mood to what he shows, so Home, the widget and a reminder's face show the same
 * Mo at the same moment. [now] is on the habit day, as every scene is.
 */
internal fun moPicture(mood: MoMood, reminders: ReminderSettings, now: LocalDateTime, bedtime: Bedtime): MoPicture {
    val asked = moAskedTrackers(mood, reminders, now.toLocalTime())
    val scene = moScene(mood, asked, now, bedtime)
    return MoPicture(
        asked = asked,
        feeling = moFeeling(mood, asked, now, bedtime),
        scene = scene,
        look = MoLook.of(mood, angry = asked.overdueBy.isNotEmpty(), scene = scene),
        saveTurn = now.toLocalDate().toEpochDay().toInt(),
    )
}

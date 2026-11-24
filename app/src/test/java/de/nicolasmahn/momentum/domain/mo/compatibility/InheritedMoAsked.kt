package de.nicolasmahn.momentum.domain.compatibility

import de.nicolasmahn.momentum.domain.mo.*
import de.nicolasmahn.momentum.domain.trackerRef
import de.nicolasmahn.momentum.domain.reminder.Bedtime
import de.nicolasmahn.momentum.domain.reminder.ReminderSettings
import java.time.LocalDateTime
import java.time.LocalTime

data class MoAsked(
    val waited: Map<MoNeed, List<Int>> = emptyMap(),
    val overdueBy: Map<MoNeed, Int> = emptyMap(),
)

/** Adapts inherited snapshots at their boundary; current surfaces already carry tracker references. */
internal fun inheritedTrackerAsked(asked: MoAsked): TrackerAsked = TrackerAsked(
    waited = asked.waited.mapKeys { (need, _) -> need.view.trackerRef },
    overdueBy = asked.overdueBy.mapKeys { (need, _) -> need.view.trackerRef },
)

/** Compatibility projection for saved choices and callers still using inherited aliases. */
fun moAsked(mood: MoMood, reminders: ReminderSettings, now: LocalTime): MoAsked = moAskedTrackers(mood, reminders, now).let {
    MoAsked(it.waited.inheritedProjection(), it.overdueBy.inheritedProjection())
}

fun moOverdue(mood: MoMood, reminders: ReminderSettings, now: LocalTime): List<MoNeed> =
    moOverdueTrackers(mood, reminders, now).mapNotNull(InheritedNeedsByTracker::get)

/** Historical snapshots still carry inherited task aliases; runtime surfaces pass [TrackerAsked]. */
fun moFeeling(mood: MoMood, asked: MoAsked, now: LocalDateTime, bedtime: Bedtime): MoFeeling =
    moFeeling(mood, inheritedTrackerAsked(asked), now, bedtime)

fun moOccasion(mood: MoMood, asked: MoAsked, now: LocalDateTime, bedtime: Bedtime): MoOccasion =
    moOccasion(mood, inheritedTrackerAsked(asked), now, bedtime)

package com.example.workouttracker.domain.substance

import com.example.workouttracker.domain.AppState
import com.example.workouttracker.domain.checklist.CheckpointTime
import com.example.workouttracker.domain.checklist.Dated
import com.example.workouttracker.domain.checklist.MoMotif
import com.example.workouttracker.domain.checklist.ViewHue
import com.example.workouttracker.domain.checklist.ViewIcon
import com.example.workouttracker.domain.checklist.WhenAway
import com.example.workouttracker.domain.checklist.withRules
import java.time.LocalDate

/**
 * Every change the Substances page can make, as typed operations applied on the habit day they are
 * saved, as a checklist's [com.example.workouttracker.domain.checklist.ChecklistEdit]s are. Each is
 * checked against the settings it changes, and none reaches a day before [applyEdit]'s `today`:
 *
 * - The view's look, which substances are tracked, the person's own, their names and the check-in
 *   judge no day, so they apply at once.
 * - The goal, the stance, exceptions only, the bank's rules and what an away day means judge days,
 *   so each changes from today on and keeps its earlier value for earlier days ([Dated]), once any
 *   earlier day was judged by Substances' settings; before that they change plainly.
 * - Untracking a substance takes it out of the stance from today on; earlier days keep it.
 *
 * A revision that starts today was never used to judge a day, so a second change on the same day
 * replaces it, and a change taken back leaves no revision.
 */
sealed interface SubstanceEdit {
    /** What the view is called from now on, as typed; null goes back to the app's own word. */
    data class Rename(val label: String?) : SubstanceEdit

    data class SetIcon(val icon: ViewIcon) : SubstanceEdit

    data class SetHue(val hue: ViewHue) : SubstanceEdit

    /** Mo's flavor; null is Plain. */
    data class SetMotif(val motif: MoMotif?) : SubstanceEdit

    /** Tracks or untracks [id]; a newly tracked substance is just tracked, and an untracked one leaves the stance. */
    data class Track(val id: String, val tracked: Boolean) : SubstanceEdit

    /** Whether the person stays away from the tracked substance [id], from today on. */
    data class StayAway(val id: String, val stayingAway: Boolean) : SubstanceEdit

    /** A substance of the person's own, tracked from the start: that is why it was named. */
    data class AddOwn(val id: String, val label: String, val unit: SubstanceAmountUnit) : SubstanceEdit

    data class RenameOwn(val id: String, val label: String) : SubstanceEdit

    /** Refused while any stance before today names it, as the store refuses one with records. */
    data class RemoveOwn(val id: String) : SubstanceEdit

    data class Remind(val remind: Boolean) : SubstanceEdit

    data class CheckInAt(val at: CheckpointTime) : SubstanceEdit

    /** Counts toward the streak from today with [goal]; null leaves the streak after yesterday. */
    data class SetGoal(val goal: SubstanceGoal?) : SubstanceEdit

    /** Under staying away, whether a day with nothing logged is kept, from today on. */
    data class SetExceptionsOnly(val exceptionsOnly: Boolean) : SubstanceEdit

    /** The bank's size and the kept days a token takes, from today on. */
    data class SetRewardRules(val maxPasses: Int, val everyKeptDays: Int) : SubstanceEdit

    data class SetWhenAway(val whenAway: WhenAway) : SubstanceEdit
}

/** [edit] applied to these settings from the habit day [today]. Throws for an edit the settings cannot take. */
fun SubstanceSettings.applyEdit(edit: SubstanceEdit, today: LocalDate): SubstanceSettings = when (edit) {
    is SubstanceEdit.Rename -> copy(label = edit.label)
    is SubstanceEdit.SetIcon -> copy(icon = edit.icon)
    is SubstanceEdit.SetHue -> copy(hue = edit.hue)
    is SubstanceEdit.SetMotif -> copy(mo = mo.copy(motif = edit.motif))
    is SubstanceEdit.Track -> if (edit.tracked) {
        require(substance(edit.id) != null) { "Only a known substance can be tracked." }
        copy(tracked = tracked + edit.id)
    } else {
        copy(tracked = tracked - edit.id, stayingAway = stanceFrom(today, stayingAway.current - edit.id))
    }
    is SubstanceEdit.StayAway -> {
        require(edit.id in tracked) { "Only a tracked substance can be one to stay away from." }
        copy(stayingAway = stanceFrom(today, if (edit.stayingAway) stayingAway.current + edit.id else stayingAway.current - edit.id))
    }
    is SubstanceEdit.AddOwn -> {
        require(substance(edit.id) == null) { "Substance ${edit.id} exists." }
        copy(own = own + OwnSubstance(edit.id, edit.label, edit.unit), tracked = tracked + edit.id)
    }
    is SubstanceEdit.RenameOwn -> {
        require(own.any { it.id == edit.id }) { "No own substance ${edit.id}." }
        copy(own = own.map { if (it.id == edit.id) it.copy(label = edit.label) else it })
    }
    // The settings refuse a stance naming a substance that is gone, so one an earlier revision
    // names cannot be removed; one stayed away from only since today leaves no revision behind.
    is SubstanceEdit.RemoveOwn -> {
        require(own.any { it.id == edit.id }) { "No own substance ${edit.id}." }
        copy(own = own.filterNot { it.id == edit.id }, tracked = tracked - edit.id, stayingAway = stanceFrom(today, stayingAway.current - edit.id))
    }
    is SubstanceEdit.Remind -> copy(checkIn = checkIn.copy(remind = edit.remind))
    is SubstanceEdit.CheckInAt -> copy(checkIn = checkIn.copy(at = edit.at))
    is SubstanceEdit.SetGoal -> when (val goal = edit.goal) {
        // A bank that never held a day is not kept, nor a goal or rule no day was judged by:
        // switched on and off before Save, nothing is left.
        // Out from today, no day reads a goal or rule that starts today, so they go back to
        // yesterday's: a goal chosen and taken back before Save leaves nothing.
        null -> leavingStreak(today).let { left ->
            if (left.rewards?.periods.isNullOrEmpty()) {
                left.copy(rewards = null, goal = Dated.always(SubstanceGoal.Logging), exceptionsOnly = Dated.always(true))
            } else {
                val yesterday = today.minusDays(1)
                left.copy(
                    goal = left.goal.changedFrom(today, left.goal.on(yesterday)),
                    exceptionsOnly = left.exceptionsOnly.changedFrom(today, left.exceptionsOnly.on(yesterday)),
                )
            }
        }
        else -> {
            val dated = judgedBefore(today)
            joiningStreak(today).let { joined -> joined.copy(goal = joined.goal.changedFrom(today, goal, dated)) }
        }
    }
    is SubstanceEdit.SetExceptionsOnly -> copy(exceptionsOnly = exceptionsOnly.changedFrom(today, edit.exceptionsOnly, judgedBefore(today)))
    is SubstanceEdit.SetRewardRules -> {
        val rewards = requireNotNull(rewards) { "Substances takes no part in the streak." }
        copy(rewards = rewards.withRules(today, edit.maxPasses, edit.everyKeptDays, dated = judgedBefore(today)))
    }
    is SubstanceEdit.SetWhenAway -> awayRuleFrom(today, edit.whenAway)
}

/**
 * [edits] applied one after another from [today]: what the page shows over the latest stored
 * settings, and what Save stores on the day it is pressed. An edit the settings no longer take,
 * such as a rename of a substance removed meanwhile, is left out, so what is shown is what is saved.
 */
fun SubstanceSettings.applyingEdits(edits: List<SubstanceEdit>, today: LocalDate): SubstanceSettings =
    edits.fold(this) { draft, edit -> runCatching { draft.applyEdit(edit, today) }.getOrDefault(draft) }

/**
 * The stored settings with [edits] applied from [today], each checked against the records too: an
 * edit the records refuse by then, such as removing an own substance logged meanwhile, is left out
 * like one the settings refuse.
 */
fun AppState.applyingSubstanceEdits(edits: List<SubstanceEdit>, today: LocalDate): AppState =
    edits.fold(this) { state, edit -> runCatching { saveSubstanceSettings(state, state.substances.applyEdit(edit, today)) }.getOrDefault(state) }

/** The stance from [today] on, dated only once an earlier day was judged by Substances' settings. */
private fun SubstanceSettings.stanceFrom(today: LocalDate, stance: Set<String>): Dated<Set<String>> =
    stayingAway.changedFrom(today, stance, judgedBefore(today))

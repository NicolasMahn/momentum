package com.example.workouttracker.domain.checklist

import com.example.workouttracker.domain.AppState
import java.time.LocalDate
import java.time.Month
import kotlin.random.Random

/**
 * Every change a person can make to a checklist, as typed operations: Settings makes them, and
 * anything that edits a configuration later uses the same ones. Each is checked against the
 * configuration it changes, and none reaches a day before [applyEdit]'s `today`:
 *
 * - The look, the labels, a checkpoint's time and its reminder are not dated: they judge no day,
 *   so they apply everywhere at once, and a rename shows on old days too.
 * - What is due is dated (contract, "How changes are handled"): an added item or checkpoint starts
 *   today, a removed one ends yesterday and stays in the configuration so old days still read it,
 *   and changing an item ends its entry yesterday and starts a new one with the same ID today.
 * - What an away day means for an item, whether it logs only misses, which day a due day is about
 *   and the reward rules change from today on, keeping their earlier values for earlier days
 *   ([Dated]). Only an item due more than once a week can ask fewer times while away, and it goes
 *   back to due when it stops being one; how many times a week on holiday asks sits beside the
 *   target and is dated like it. Only misses and fewer times exclude each other: choosing either
 *   sends the other back in the same edit, dated the same way.
 *
 * An entry that starts today was never used to judge a day, so a second change on the same day
 * replaces it instead of ending it.
 */
sealed interface ChecklistEdit {
    data class Rename(val label: String) : ChecklistEdit

    data class SetIcon(val icon: ViewIcon) : ChecklistEdit

    data class SetHue(val hue: ViewHue) : ChecklistEdit

    /** Null for no motif: Mo then speaks with the checklist's generic words. */
    data class SetMotif(val motif: MoMotif?) : ChecklistEdit

    data class AddCheckpoint(val checkpointId: String, val label: String?, val at: CheckpointTime, val remind: Boolean) : ChecklistEdit

    /** Null removes the label, which a checklist with one checkpoint does not show. */
    data class RenameCheckpoint(val checkpointId: String, val label: String?) : ChecklistEdit

    data class SetCheckpointTime(val checkpointId: String, val at: CheckpointTime) : ChecklistEdit

    data class SetCheckpointRemind(val checkpointId: String, val remind: Boolean) : ChecklistEdit

    /** Items due only there end with it; the others stop being due there. */
    data class RemoveCheckpoint(val checkpointId: String) : ChecklistEdit

    data class AddItem(
        val itemId: String,
        val label: String,
        val checkpoints: List<String>? = null,
        val target: ChecklistTarget? = null,
        val months: Set<Month>? = null,
    ) : ChecklistEdit

    data class RenameItem(val itemId: String, val label: String) : ChecklistEdit

    /** Null for every checkpoint of the checklist. */
    data class SetItemCheckpoints(val itemId: String, val checkpoints: List<String>?) : ChecklistEdit

    /** Null for the checklist's own target. */
    data class SetItemTarget(val itemId: String, val target: ChecklistTarget?) : ChecklistEdit

    /** Null for the whole year. */
    data class SetItemSeason(val itemId: String, val months: Set<Month>?) : ChecklistEdit

    data class RemoveItem(val itemId: String) : ChecklistEdit

    /** Fewer times while away is for an item due more than once a week. */
    data class SetItemWhenAway(val itemId: String, val whenAway: WhenAway) : ChecklistEdit

    /**
     * How many times a week on holiday asks while the item asks fewer times; null for no count,
     * which keeps it due as ever. Dated like a target change, as the count sits beside the target.
     */
    data class SetItemAwayTimes(val itemId: String, val times: Int?) : ChecklistEdit

    /**
     * Whether the item logs only misses from today on: kept on a due day unless marked missed. For
     * an item due every day or on chosen weekdays; choosing it sends fewer times on holiday back
     * to still due.
     */
    data class SetItemExceptionsOnly(val itemId: String, val exceptionsOnly: Boolean) : ChecklistEdit

    /** Which day each due day is about, from today on. */
    data class SetAbout(val about: AboutDay) : ChecklistEdit

    /** Takes part in rewards from today, with the rules it had or the default ones. */
    data object JoinRewards : ChecklistEdit

    /** Stops taking part after yesterday; the days it took part keep their judgments. */
    data object LeaveRewards : ChecklistEdit

    /** The bank's size and the kept days a pass takes, from today on. */
    data class SetRewardRules(val maxPasses: Int, val everyKeptDays: Int) : ChecklistEdit
}

/**
 * [edit] applied to the checklist [checklistId], starting on the habit day [today]. Throws for an
 * edit the configuration cannot take, such as removing its last checkpoint or its last item.
 */
fun AppState.applyEdit(checklistId: String, edit: ChecklistEdit, today: LocalDate): AppState =
    replacing(checklistId) { it.applyEdit(edit, today, checklistRecordedDates(checklistId)) }

/**
 * [edit] applied to this configuration alone, as a page applies it to a draft before anything is
 * saved. [recordedDates] preserves recorded-day ownership when About changes and closes an
 * implicit participation period when leaving rewards.
 */
fun ChecklistConfiguration.applyEdit(edit: ChecklistEdit, today: LocalDate, recordedDates: Set<LocalDate> = emptySet()): ChecklistConfiguration =
    when (edit) {
        ChecklistEdit.JoinRewards -> joiningRewards(today)
        ChecklistEdit.LeaveRewards -> leavingRewards(today, recordedDates)
        else -> edited(edit, today, recordedDates)
    }

/** The stored configuration replaced by [checklist], the draft its page saved; its ID names which. */
fun AppState.saveChecklist(checklist: ChecklistConfiguration): AppState = replacing(checklist.id) { checklist }

/**
 * Removes a checklist with every day recorded for it. The records
 * go with it because they belong to its items and nothing else could read them. Its reminders
 * and its hidden-checklist entry live outside the records, so the caller drops those.
 */
fun AppState.deleteChecklist(checklistId: String): AppState {
    requireNotNull(checklist(checklistId)) { "No checklist $checklistId." }
    return copy(
        checklists = checklists.filterNot { it.id == checklistId },
        checklistDays = checklistDays.filterNot { it.checklistId == checklistId },
    )
}

/**
 * A checklist as a person starts one on [today]: one checkpoint from today, nothing in it yet, out
 * of rewards, in a deep hue no other checklist wears while one is left; the built-in views wear the
 * vivid ones, and red is kept for warnings. It is a one-time copy that
 * remembers nothing of where it came from; everything else is set with [ChecklistEdit]s, so a
 * change on its first day leaves nothing behind.
 */
fun AppState.starterChecklist(label: String, today: LocalDate, random: Random = Random.Default, hidden: Set<String> = emptySet()): ChecklistConfiguration =
    ChecklistConfiguration(
        id = freshId("chk", namedChecklistIds + hidden, random),
        label = label,
        icon = ViewIcon.Checklist,
        hue = starterHue(worn = checklists.mapTo(HashSet()) { it.hue }),
        checkpoints = listOf(Checkpoint(freshId("cpt", emptySet(), random), CheckpointTime.Fixed(StarterTime), remind = false, from = today)),
        items = emptyList(),
    )

private val StarterTime = java.time.LocalTime.of(9, 0)

/**
 * A starter checklist as a schedule starts: a clock for its icon and a second checkpoint in the
 * evening, both without a name yet, since a schedule's times are the person's own to name.
 */
fun ChecklistConfiguration.asSchedule(random: Random = Random.Default): ChecklistConfiguration {
    val first = checkpoints.single()
    return copy(
        icon = ViewIcon.Schedule,
        checkpoints = checkpoints + first.copy(id = freshId("cpt", setOf(first.id), random), at = CheckpointTime.Fixed(ScheduleEveningTime)),
    )
}

private val ScheduleEveningTime = java.time.LocalTime.of(20, 0)

private val StarterHues: List<ViewHue> = ViewHue.entries.filter { it.isDeep } + ViewHue.entries.filterNot { it.isDeep || it == ViewHue.Red }

/**
 * The color a view a person starts wears: the first of the starter colors, the deep ones before the
 * vivid, that is not among [worn], or the first of all once every one is worn.
 */
fun starterHue(worn: Set<ViewHue>): ViewHue = StarterHues.firstOrNull { it !in worn } ?: StarterHues.first()

/** Configurations and orphan facts both reserve their owner's identity. */
val AppState.namedChecklistIds: Set<String>
    get() = checklists.mapTo(HashSet()) { it.id } + checklistDays.map { it.checklistId }

/** An orphan record or retained visibility entry still owns its ID after a failed cleanup. */
fun AppState.addChecklist(checklist: ChecklistConfiguration, hidden: Set<String> = emptySet()): AppState {
    require(checklist.id !in namedChecklistIds && checklist.id !in hidden) { "Checklist ${checklist.id} is named already." }
    return copy(checklists = checklists + checklist)
}

/** The checkpoints in use from [today] on, the ones an item can be due at. */
fun ChecklistConfiguration.liveCheckpoints(today: LocalDate): List<Checkpoint> =
    checkpoints.filter { it.until == null || it.until >= today }

/**
 * The items in use from [today] on, one entry each: the entry that applies today, or the one that
 * starts next. An item that ended before today is not among them.
 */
fun ChecklistConfiguration.liveItems(today: LocalDate): List<ChecklistItem> = items.live(today)

/**
 * Whether the checklist has an item in use from [today] on. One without asks nothing: it has no
 * tile on Home, no alarm, no bank in the streak, and Settings says to add an item. Only a checklist
 * stored before the last item was guarded can be without one; the setup never makes one.
 */
fun ChecklistConfiguration.isInUse(today: LocalDate): Boolean = liveItems(today).isNotEmpty()

private fun List<ChecklistItem>.live(today: LocalDate): List<ChecklistItem> =
    groupBy { it.id }.values.mapNotNull { entries -> entries.liveEntry(today) }
        .sortedBy { live -> indexOfFirst { it.id == live.id } }

/**
 * The checkpoints from [today] on with no item due at them: a time of the day with nothing to tick.
 * A checklist ends the setup and leaves its editor without any.
 */
fun ChecklistConfiguration.emptyCheckpoints(today: LocalDate): List<Checkpoint> {
    val items = liveItems(today)
    return liveCheckpoints(today).filter { checkpoint -> items.none { it.checkpoints?.contains(checkpoint.id) ?: true } }
}

/**
 * Whether the checklist is a schedule on [today]: ticked at several times of the day, each its
 * own section. It is no stored kind of its own, only a checklist with more than one checkpoint.
 */
fun ChecklistConfiguration.isSchedule(today: LocalDate): Boolean = liveCheckpoints(today).size > 1

/**
 * Whether a checkpoint can go on [today]: another one stays, and some item stays with it. A
 * checklist keeps at least one checkpoint and one item in use, so its screen always has something
 * to show. The editor asks here before it offers the removal, and [applyEdit] refuses without it.
 */
fun ChecklistConfiguration.canRemoveCheckpoint(checkpointId: String, today: LocalDate): Boolean {
    val live = liveCheckpoints(today)
    if (live.size < 2 || live.none { it.id == checkpointId }) return false
    // An item named only here ends with the checkpoint; one due everywhere, or elsewhere too, stays.
    val items = liveItems(today)
    return items.isEmpty() || items.any { item -> item.checkpoints?.any { it != checkpointId } ?: true }
}

/** An ID nothing in [taken] uses, such as `itm_k2p9`; IDs are never shown. */
fun freshId(prefix: String, taken: Collection<String>, random: Random = Random.Default): String =
    generateSequence { prefix + "_" + (1..4).map { IdAlphabet[random.nextInt(IdAlphabet.length)] }.joinToString("") }
        .first { it !in taken }

/** Every ID a new item of this checklist must differ from, ended items included. */
val ChecklistConfiguration.itemIds: Set<String> get() = items.mapTo(HashSet()) { it.id }

val ChecklistConfiguration.checkpointIds: Set<String> get() = checkpoints.mapTo(HashSet()) { it.id }

private const val IdAlphabet = "abcdefghijkmnpqrstuvwxyz23456789"

private fun ChecklistConfiguration.edited(edit: ChecklistEdit, today: LocalDate, recordedDates: Set<LocalDate>): ChecklistConfiguration = when (edit) {
    is ChecklistEdit.Rename -> copy(label = edit.label)
    is ChecklistEdit.SetIcon -> copy(icon = edit.icon)
    is ChecklistEdit.SetHue -> copy(hue = edit.hue)
    is ChecklistEdit.SetMotif -> copy(mo = mo.copy(motif = edit.motif))
    is ChecklistEdit.AddCheckpoint -> {
        require(edit.checkpointId !in checkpointIds) { "Checkpoint ${edit.checkpointId} exists." }
        copy(checkpoints = checkpoints + Checkpoint(edit.checkpointId, edit.at, edit.label, edit.remind, from = today))
    }
    is ChecklistEdit.RenameCheckpoint -> withCheckpoint(edit.checkpointId) { it.copy(label = edit.label) }
    is ChecklistEdit.SetCheckpointTime -> withCheckpoint(edit.checkpointId) { it.copy(at = edit.at) }
    is ChecklistEdit.SetCheckpointRemind -> withCheckpoint(edit.checkpointId) { it.copy(remind = edit.remind) }
    is ChecklistEdit.RemoveCheckpoint -> removingCheckpoint(edit.checkpointId, today)
    is ChecklistEdit.AddItem -> {
        require(edit.itemId !in itemIds) { "Item ${edit.itemId} exists." }
        edit.checkpoints?.let { requireLive(it, today) }
        copy(items = items + ChecklistItem(edit.itemId, edit.label, edit.months, from = today, checkpoints = edit.checkpoints, target = edit.target))
    }
    is ChecklistEdit.RenameItem -> {
        require(items.any { it.id == edit.itemId }) { "No item ${edit.itemId}." }
        // A label is not dated, so every entry of the item takes it.
        copy(items = items.map { if (it.id == edit.itemId) it.copy(label = edit.label) else it })
    }
    is ChecklistEdit.SetItemCheckpoints -> {
        edit.checkpoints?.let { requireLive(it, today) }
        copy(items = items.changing(edit.itemId, today) { it.copy(checkpoints = edit.checkpoints) })
    }
    is ChecklistEdit.SetItemTarget -> copy(items = items.changing(edit.itemId, today) { it.withTarget(edit.target, default = target) })
    is ChecklistEdit.SetItemAwayTimes -> copy(items = items.changing(edit.itemId, today) { it.copy(awayTimes = edit.times) })
    is ChecklistEdit.SetItemSeason -> copy(items = items.changing(edit.itemId, today) { it.copy(months = edit.months) })
    is ChecklistEdit.RemoveItem -> {
        require(liveItems(today).size > 1) { "Checklist $id keeps an item." }
        copy(items = items.ending(edit.itemId, today))
    }
    is ChecklistEdit.SetItemWhenAway -> {
        val live = items.filter { it.id == edit.itemId }.liveEntry(today) ?: error("No item ${edit.itemId} in use.")
        // The rule is dated inside the entry, so the entry is not split. The calendar reads it on
        // every day the entry covers, in or out of rewards, so it is dated whenever the entry had
        // a day before today; one that only started today takes it plainly. Fewer times cannot
        // stand beside only misses, so choosing it turns that off from the same day.
        val dated = live.from == null || live.from < today
        val lowering = edit.whenAway == WhenAway.LowerTarget
        copy(
            items = items.map {
                if (it == live) {
                    it.copy(
                        whenAway = it.whenAway.changedFrom(today, edit.whenAway, dated),
                        exceptionsOnly = if (lowering) it.exceptionsOnly.changedFrom(today, false, dated) else it.exceptionsOnly,
                    )
                } else {
                    it
                }
            },
        )
    }
    is ChecklistEdit.SetItemExceptionsOnly -> {
        val live = items.filter { it.id == edit.itemId }.liveEntry(today) ?: error("No item ${edit.itemId} in use.")
        require(!edit.exceptionsOnly || (live.target ?: target).canBeExceptionsOnly) { "Item ${edit.itemId} is due a number of times a week, so it logs its ticks." }
        // Dated like the away rule, inside the entry; only misses cannot stand beside fewer times
        // on holiday, so choosing it sends that back to due from the same day.
        val dated = live.from == null || live.from < today
        val lowered = live.whenAway.current == WhenAway.LowerTarget
        copy(
            items = items.map {
                if (it == live) {
                    it.copy(
                        exceptionsOnly = it.exceptionsOnly.changedFrom(today, edit.exceptionsOnly, dated),
                        whenAway = if (edit.exceptionsOnly && lowered) it.whenAway.changedFrom(today, WhenAway.Due, dated) else it.whenAway,
                    )
                } else {
                    it
                }
            },
        )
    }
    // The reward walk is the one reader of which record an earlier day asked for, so the change is
    // dated whenever a day before today was judged, and plain otherwise.
    is ChecklistEdit.SetAbout -> copy(about = about.changedFrom(today, edit.about, mo.enabled || recordedDates.isNotEmpty() || judgedBefore(today)))
    is ChecklistEdit.SetRewardRules -> {
        val rewards = requireNotNull(rewards) { "Checklist $id takes no part in rewards." }
        copy(rewards = rewards.withRules(today, edit.maxPasses, edit.everyKeptDays, dated = judgedBefore(today)))
    }
    ChecklistEdit.JoinRewards, ChecklistEdit.LeaveRewards -> error("Applied through applyEdit.")
}

/**
 * The entry with [target], or the checklist's [default], as [changing] starts it today, its away
 * rule and count read for that target: fewer times means nothing for an item due once a week or
 * less, so one that becomes so goes back to due in the same step and keeps no count, and a lowering
 * it had earlier reads as due there too; a count the new usual count no longer allows is clamped to
 * one below it. An item due a number of times a week logs its ticks, so only misses goes off with
 * that target. The ended entry keeps its own history, so no earlier day reads differently, and no
 * entry is ever built lowered or assuming where nothing can be. The editor offers the choices
 * again once the item's target allows them.
 */
private fun ChecklistItem.withTarget(target: ChecklistTarget?, default: ChecklistTarget): ChecklistItem {
    val usual = target ?: default
    return copy(
        target = target,
        whenAway = whenAway.map { it.forTarget(usual) },
        awayTimes = if (usual.canAskFewerTimes) awayTimes?.coerceAtMost(usual.timesAWeek - 1) else null,
        exceptionsOnly = if (usual.canBeExceptionsOnly) exceptionsOnly else Dated.always(false),
    )
}

/**
 * Whether the configured reward participation includes a day before [today]. An item's away
 * rule is read by the calendar on every day its entry covers and remains dated independently.
 */
private fun ChecklistConfiguration.judgedBefore(today: LocalDate): Boolean =
    rewards?.judgedBefore(today) == true

private fun ChecklistConfiguration.withCheckpoint(checkpointId: String, change: (Checkpoint) -> Checkpoint): ChecklistConfiguration {
    require(checkpoints.any { it.id == checkpointId }) { "No checkpoint $checkpointId." }
    return copy(checkpoints = checkpoints.map { if (it.id == checkpointId) change(it) else it })
}

private fun ChecklistConfiguration.requireLive(names: List<String>, today: LocalDate) {
    val live = liveCheckpoints(today).mapTo(HashSet()) { it.id }
    require(names.all { it in live }) { "An item is due only at checkpoints in use." }
}

/**
 * Ends a checkpoint after yesterday, or drops it if it only started today. It stops reminding, and
 * every item due there stops being due there: one due nowhere else ends with it.
 */
private fun ChecklistConfiguration.removingCheckpoint(checkpointId: String, today: LocalDate): ChecklistConfiguration {
    val checkpoint = liveCheckpoints(today).firstOrNull { it.id == checkpointId } ?: error("No checkpoint $checkpointId in use.")
    require(canRemoveCheckpoint(checkpointId, today)) { "Checklist $id keeps a checkpoint and an item." }
    val remaining = liveItems(today).filter { it.checkpoints?.contains(checkpointId) == true }.fold(items) { items, item ->
        val others = item.checkpoints.orEmpty() - checkpointId
        if (others.isEmpty()) items.ending(item.id, today) else items.changing(item.id, today) { it.copy(checkpoints = others) }
    }
    val startsToday = checkpoint.from != null && checkpoint.from >= today
    return copy(
        items = remaining,
        checkpoints = if (startsToday) {
            checkpoints.filterNot { it.id == checkpointId }
        } else {
            checkpoints.map { if (it.id == checkpointId) it.copy(until = today.minusDays(1), remind = false) else it }
        },
    )
}

/**
 * Changes what an item asks for from [today] on: its entry ends yesterday and a new one with the
 * same ID starts today, so earlier days keep the entry they were judged by.
 */
private fun List<ChecklistItem>.changing(itemId: String, today: LocalDate, change: (ChecklistItem) -> ChecklistItem): List<ChecklistItem> {
    val live = filter { it.id == itemId }.liveEntry(today) ?: error("No item $itemId in use.")
    val changed = change(live)
    if (changed == live) return this
    val replacement = if (live.from != null && live.from >= today) {
        listOf(changed)
    } else {
        listOf(live.copy(until = today.minusDays(1)), changed.copy(from = today))
    }
    return flatMap { if (it == live) replacement else listOf(it) }.mergingAdjacent(itemId)
}

/** Ends an item after yesterday, or drops its entry if it only started today. Its records stay. */
private fun List<ChecklistItem>.ending(itemId: String, today: LocalDate): List<ChecklistItem> {
    val live = filter { it.id == itemId }.liveEntry(today) ?: error("No item $itemId in use.")
    val ended = if (live.from != null && live.from >= today) emptyList() else listOf(live.copy(until = today.minusDays(1)))
    return flatMap { if (it == live) ended else listOf(it) }
}

/** The entry in use today, or the one that starts next; entries of one item never overlap. */
private fun List<ChecklistItem>.liveEntry(today: LocalDate): ChecklistItem? =
    sortedBy { it.from ?: LocalDate.MIN }.firstOrNull { it.until == null || it.until >= today }

/**
 * Two entries of one item that follow each other and ask for the same become one again, as when a
 * change is undone on the day it was made.
 */
private fun List<ChecklistItem>.mergingAdjacent(itemId: String): List<ChecklistItem> {
    val entries = filter { it.id == itemId }.sortedBy { it.from ?: LocalDate.MIN }
    val merged = entries.fold(mutableListOf<ChecklistItem>()) { kept, entry ->
        val previous = kept.lastOrNull()
        if (previous != null && previous.until != null && entry.from == previous.until.plusDays(1) &&
            previous.copy(from = null, until = null) == entry.copy(from = null, until = null)
        ) {
            kept[kept.lastIndex] = previous.copy(until = entry.until)
        } else {
            kept += entry
        }
        kept
    }
    if (merged.size == entries.size) return this
    val first = indexOfFirst { it.id == itemId }
    return filterNot { it.id == itemId }.toMutableList().apply { addAll(first, merged) }
}

private fun AppState.replacing(checklistId: String, change: (ChecklistConfiguration) -> ChecklistConfiguration): AppState {
    val current = requireNotNull(checklist(checklistId)) { "No checklist $checklistId." }
    val updated = change(current)
    return if (updated == current) this else copy(checklists = checklists.map { if (it.id == checklistId) updated else it })
}

package de.nicolasmahn.momentum.app

import de.nicolasmahn.momentum.domain.AppState
import de.nicolasmahn.momentum.domain.DayChange
import de.nicolasmahn.momentum.domain.DayChangeResult
import de.nicolasmahn.momentum.domain.LoggedView
import de.nicolasmahn.momentum.feature.settings.ViewWrite
import de.nicolasmahn.momentum.persistence.TrackerRepository
import java.time.LocalDate

/**
 * Stores what the page and the screen of a logged view hand over, a measurement's, a questions
 * view's or a time view's, each against the records as they are stored at that moment and never
 * against the state a screen was shown. A kind brings how the state holds its configurations;
 * everything else is the same for all of them, so it is here once.
 *
 * A page hands over the edits made: they are applied to the latest stored configuration on the
 * habit day they are stored, as [ChecklistCommands] applies a checklist's. So a change made before
 * 04:00 and saved after counts from the day it was saved, and a change stored meanwhile stays under
 * them. A screen hands over one day's record or its removal ([changeDay]).
 *
 * The setup hands over a new view ([create]), and a page may delete its own ([delete]), each one
 * update of the store.
 *
 * A view a person made reminds, so a Save and a Create settle as a checklist's do ([settle]):
 * [reconcile] takes the new records, shows them, sets their alarms and says whether every alarm
 * could be set, and a write that asks for a new alarm which cannot be set, with access granted, is
 * taken back. What came of it is said as it is stored ([ViewWrite]). A day's change sets no alarm:
 * [stored] is handed the records as they are now, after it and after one that was refused because
 * they had changed.
 *
 * Whether a view is hidden is kept in the visibility settings, another file. [hidden] reads the
 * IDs of this kind it hides as they are stored at that moment, and [forgetHidden] drops one, in a
 * write of its own that may fail.
 */
internal abstract class LoggedViewCommands<C : LoggedView, E, D : DayChange>(
    private val repository: TrackerRepository,
    private val reconcile: (AppState) -> Boolean,
    private val exactAlarmsAllowed: () -> Boolean,
    private val refresh: () -> Unit,
    private val stored: (AppState) -> Unit,
    private val hidden: () -> Set<String>,
    private val forgetHidden: (viewId: String) -> Unit,
    private val today: () -> LocalDate,
    private val now: () -> Long,
) {
    /** The stored configuration [viewId] of this kind, or null when that instance is absent. */
    protected abstract fun AppState.view(viewId: String): C?

    /** The stored configuration [viewId] with [edits] applied from [day]; a state without it is left as it is. */
    protected abstract fun AppState.applyingEdits(viewId: String, edits: List<E>, day: LocalDate): AppState

    /** This state with [starter] as a view made on [day]. Throws where anything names its ID by now, an entry among [hidden] included. */
    protected abstract fun AppState.adding(starter: C, hidden: Set<String>, day: LocalDate): AppState

    /** This state without the view [viewId] a person made and without every record of it. */
    protected abstract fun AppState.deleting(viewId: String): AppState

    /** This state with [view] in place of the configuration stored under its ID. */
    protected abstract fun AppState.with(view: C): AppState

    fun save(viewId: String, edits: List<E>): ViewWrite {
        val day = today()
        var before: C? = null
        val updated = repository.update { state ->
            before = state.view(viewId)
            state.applyingEdits(viewId, edits, day)
        }.updatedState() ?: return ViewWrite.NotStored
        val previous = before
        val after = updated.view(viewId)
        return settled(
            updated = updated,
            needsAnAlarm = after != null && after.checkpoint.needsANewAlarmSince(previous?.checkpoint),
            holdsTheWrite = { state -> state.view(viewId) == after },
            takenBack = { state -> if (state == updated && previous != null) state.with(previous) else state },
        )
    }

    /**
     * Stores a view the setup made: [starter] with the [edits] made to it, and nothing else. Inside
     * the store's update its ID must still be one nothing names, no configuration, no record and
     * no hidden entry; a creation whose ID was taken meanwhile is refused and writes nothing, so
     * pressing again or a setup restored after it created can never make a second one.
     *
     * A creation taken back leaves no configuration, no record and no alarm, so trying again
     * creates the view once, under the ID the setup kept.
     */
    fun create(starter: C, edits: List<E>): ViewWrite {
        val day = today()
        val updated = repository.update { state -> state.adding(starter, hidden(), day).applyingEdits(starter.id, edits, day) }.updatedState()
            ?: return ViewWrite.NotStored
        val created = requireNotNull(updated.view(starter.id))
        return settled(
            updated = updated,
            needsAnAlarm = created.checkpoint.remind,
            holdsTheWrite = { state -> state.view(starter.id) != null },
            takenBack = { state -> if (state.view(starter.id) == created) state.deleting(starter.id) else state },
        )
    }

    /**
     * What a write came to once its alarms were to be set. Where it did not stand, the answer is
     * read from the store and never assumed: a write that could not be taken back, because the
     * store could not write or the records had changed, is still there and is said to be.
     */
    private fun settled(updated: AppState, needsAnAlarm: Boolean, holdsTheWrite: (AppState) -> Boolean, takenBack: (AppState) -> AppState): ViewWrite = when {
        repository.settle(updated, needsAnAlarm, reconcile, exactAlarmsAllowed, refresh, takenBack) -> ViewWrite.Stored
        holdsTheWrite(repository.load()) -> ViewWrite.StoredWithoutAlarm
        else -> ViewWrite.NotStored
    }

    /**
     * Deletes a view a person made: its configuration and every record of it, in one update of the
     * store. Reconciling then cancels its alarm and takes its notification down.
     *
     * Its hidden entry would outlive it, so that is dropped next, in a second write. Where that one
     * fails the view is deleted all the same: the entry left behind hides nothing, its ID is given
     * to no new view, and the next save of the visibility settings drops it.
     */
    fun delete(viewId: String): Boolean {
        val updated = repository.update { state -> state.deleting(viewId) }.updatedState() ?: return false
        reconcile(updated)
        forgetHidden(viewId)
        return true
    }

    /** Saves or removes one day's record, against the records as they are stored ([changeDay]). A save stamps the time of writing. */
    fun changeDay(change: D): DayChangeResult = storeDay(change)

    /** Stores a day's change of this kind that its screen does not ask for, the same way. */
    protected fun storeDay(change: DayChange): DayChangeResult = repository.changeDay(change, now(), stored)
}

package com.example.workouttracker.domain.checklist

import java.time.LocalDate

/**
 * A setting that decides how days are judged, as it was over time. Outcomes are never stored, so a
 * change that reached back would re-judge every earlier day; a change instead starts on a day, and
 * the value before it stays for the days before. The first revision applies from the beginning,
 * each later one from its own [Revision.from], oldest first.
 */
data class Dated<T>(val revisions: List<Revision<T>>) {
    /** [value] from [from] on; only the first revision has no start. */
    data class Revision<T>(val value: T, val from: LocalDate? = null)

    init {
        require(revisions.isNotEmpty() && revisions.first().from == null) { "A dated setting starts with a value from the beginning." }
        val starts = revisions.drop(1).map { requireNotNull(it.from) { "Only the first revision has no start." } }
        require(starts.zipWithNext().all { (earlier, later) -> earlier < later }) { "Revisions are in order, one per day." }
        require(revisions.zipWithNext().none { (earlier, later) -> earlier.value == later.value }) { "A revision changes the value." }
    }

    /** The value now and from now on. */
    val current: T get() = revisions.last().value

    /** Whether it ever changed, which decides how it is stored. */
    val isDated: Boolean get() = revisions.size > 1

    fun on(date: LocalDate): T = revisionOn(date).value

    /** The revision that applies on [date]: the first one, or the latest that started on or before it. */
    fun revisionOn(date: LocalDate): Revision<T> = revisions.last { it.from == null || it.from <= date }

    /** Whether [predicate] holds for any value this ever had. */
    fun ever(predicate: (T) -> Boolean): Boolean = revisions.any { predicate(it.value) }

    /**
     * Each value through [transform], from the same days. A revision that no longer changes the
     * value folds into the one before it, so values that all read alike come back as never changed.
     */
    fun <R> map(transform: (T) -> R): Dated<R> = Dated(
        revisions.fold(mutableListOf<Revision<R>>()) { kept, revision ->
            val value = transform(revision.value)
            if (kept.isEmpty() || kept.last().value != value) kept += Revision(value, revision.from)
            kept
        },
    )

    /**
     * [value] from [today] on, every earlier day keeping what it had. A second change on the same
     * day replaces the first, and changing back to the earlier value leaves no revision behind.
     */
    fun changedFrom(today: LocalDate, value: T): Dated<T> {
        if (value == current) return this
        val kept = revisions.filter { it.from == null || it.from < today }
        return Dated(if (kept.last().value == value) kept else kept + Revision(value, today))
    }

    /**
     * [value] from [today] on, kept as a dated revision only while [dated]: a setting no earlier day
     * was judged by changes plainly, as the contract has every such setting change.
     */
    fun changedFrom(today: LocalDate, value: T, dated: Boolean): Dated<T> =
        if (dated) changedFrom(today, value) else always(value)

    /**
     * Whether [predicate] holds, for this value and [other]'s on the same day, on any day from
     * [from] to [until], null reaching the beginning or for ever. Both are constant between the
     * days either one changes, so the first day and each change after it are every day to read.
     */
    fun <B> everWith(other: Dated<B>, from: LocalDate? = null, until: LocalDate? = null, predicate: (T, B) -> Boolean): Boolean {
        val changes = (revisions + other.revisions).mapNotNull { it.from }.filter { (from == null || it > from) && (until == null || it <= until) }
        return (listOf(from) + changes).any { day -> predicate(valueOn(day), other.valueOn(day)) }
    }

    private fun valueOn(day: LocalDate?): T = if (day == null) revisions.first().value else on(day)

    companion object {
        /** A setting that never changed. */
        fun <T> always(value: T): Dated<T> = Dated(listOf(Revision(value)))
    }
}

package com.example.workouttracker.persistence.rows

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** How another copy's rows are merged, without a database: the rules a store applies in one transaction. */
class RemoteRowsTest {
    /** A day's questions entry: one a view and day, as the stored format allows. */
    private val merge = object : RowMerge {
        override fun shared(kind: String) = true
        override fun uniqueKeys(kind: String, body: String) = listOf(body.substringBefore(':'))
        override fun contradicted(live: List<StoredRow>) = emptySet<RowKey>()
        override fun readable(rows: List<StoredRow>) = true
    }
    private var ticks = 0
    private fun stamp(millis: Int, device: String = "d") = "%015d-00000-%s".format(millis, device)
    private val here = { stamp(1_000 + ++ticks, "here") }

    /**
     * Every row's latest version is taken before pairs are settled: a stored answer `x`, then `y`
     * and a newer `x` for the same day in one answer, leave the newer `x` and remove `y`.
     */
    @Test fun aPairIsSettledOverEveryVersionReceived() {
        val stored = mapOf(RowKey("questionEntries", "x") to RowEntry("day1:x", 0, stamp(10), deleted = false))
        val remote = listOf(
            SyncedRow("questionEntries", "y", "day1:y", stamp(20)),
            SyncedRow("questionEntries", "x", "day1:x again", stamp(30)),
        )
        val changes = mergeRemote(stored, remote, merge, here)
        assertEquals("day1:x again", changes.getValue(RowKey("questionEntries", "x")).body)
        assertFalse(changes.getValue(RowKey("questionEntries", "x")).deleted)
        assertEquals(true, changes.getValue(RowKey("questionEntries", "y")).deleted)
    }

    /** A row that comes back from a tombstone goes after the live rows, never onto a position one of them holds. */
    @Test fun aRowComingBackTakesAFreePosition() {
        val stored = mapOf(
            RowKey("questionEntries", "a") to RowEntry("", 0, stamp(10), deleted = true),
            RowKey("questionEntries", "b") to RowEntry("day2:b", 0, stamp(11), deleted = false),
        )
        val changes = mergeRemote(stored, listOf(SyncedRow("questionEntries", "a", "day1:a", stamp(20))), merge, here)
        assertEquals(1L, changes.getValue(RowKey("questionEntries", "a")).position)
    }
}

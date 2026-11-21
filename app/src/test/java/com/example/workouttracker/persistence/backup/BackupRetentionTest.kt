package com.example.workouttracker.persistence.backup

import java.time.Duration
import java.time.Instant
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupRetentionTest {
    private fun keep(vararg writtenAt: String) = keep(writtenAt.map(Instant::parse))
    private fun keep(writtenAt: Collection<Instant>) = backupsToKeep(writtenAt) { it }

    @Test fun keepsTheTwoNewestTheLastOfSevenDaysAndTheLastOfThreeMonths() {
        val kept = keep(
            // Four months, each with an early and a late backup on its last active day.
            "2026-07-30T08:00:00Z", "2026-07-30T20:00:00Z",
            "2026-08-30T08:00:00Z", "2026-08-30T20:00:00Z",
            "2026-09-29T08:00:00Z", "2026-09-29T20:00:00Z",
            // Nine days of October, three backups on the newest day.
            "2026-10-01T09:00:00Z", "2026-10-02T09:00:00Z", "2026-10-03T07:00:00Z", "2026-10-03T09:00:00Z",
            "2026-10-04T09:00:00Z", "2026-10-05T09:00:00Z", "2026-10-06T09:00:00Z", "2026-10-07T09:00:00Z",
            "2026-10-08T09:00:00Z", "2026-10-09T09:00:00Z", "2026-10-09T10:00:00Z", "2026-10-09T11:00:00Z",
        )

        assertEquals(
            setOf(
                "2026-10-09T11:00:00Z", "2026-10-09T10:00:00Z",
                "2026-10-08T09:00:00Z", "2026-10-07T09:00:00Z", "2026-10-06T09:00:00Z",
                "2026-10-05T09:00:00Z", "2026-10-04T09:00:00Z", "2026-10-03T09:00:00Z",
                "2026-09-29T20:00:00Z", "2026-08-30T20:00:00Z",
            ).map(Instant::parse).toSet(),
            kept,
        )
    }

    @Test fun daysWithoutABackupDoNotUseUpTheSevenDays() {
        val threeWeeksApart = (0L until 9).map { Instant.parse("2026-01-01T12:00:00Z").plus(Duration.ofDays(it * 21)) }

        val kept = keep(threeWeeksApart)

        assertTrue(kept.containsAll(threeWeeksApart.takeLast(KeptDays)))
    }

    @Test fun aSingleBackupIsKept() {
        assertEquals(setOf(Instant.parse("2026-10-09T11:00:00Z")), keep("2026-10-09T11:00:00Z"))
    }

    /**
     * What a person relies on, whatever the history looks like: the newest backup is never given
     * up, a tidy folder stays as it is, and it never holds more than twelve.
     */
    @Test fun anyHistoryKeepsItsNewestStaysSmallAndIsLeftAloneOnceThinned() {
        val random = Random(20261118)
        repeat(200) {
            val start = Instant.parse("2026-01-01T00:00:00Z")
            val history = List(random.nextInt(1, 400)) { start.plusSeconds(random.nextLong(0, 300L * 24 * 3600)) }.toSet()

            val kept = keep(history)

            assertTrue(history.max() in kept)
            assertTrue(kept.size <= KeptNewest + KeptDays + KeptMonths)
            assertEquals(kept, keep(kept))
        }
    }

    /** The backup that ends a month is the one that month is remembered by, from the day it is written. */
    @Test fun aMonthsLastBackupSurvivesEveryThinningUntilThreeLaterMonthsExist() {
        var folder = emptySet<Instant>()
        val juneEnd = Instant.parse("2026-06-30T23:59:59Z")
        var now = Instant.parse("2026-06-01T08:00:00Z")
        val random = Random(7)
        while (now < Instant.parse("2026-09-01T00:00:00Z")) {
            folder = keep(folder + now)
            if (now < juneEnd && now.plus(Duration.ofHours(13)) > juneEnd) folder = keep(folder + juneEnd)
            if (now > juneEnd) assertTrue("June's last backup is gone on $now", juneEnd in folder)
            now = now.plus(Duration.ofMinutes(random.nextLong(1, 13 * 60)))
        }
    }
}

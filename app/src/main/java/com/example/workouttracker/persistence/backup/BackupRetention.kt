package com.example.workouttracker.persistence.backup

import java.time.Instant
import java.time.YearMonth
import java.time.ZoneOffset

/**
 * Which backups stay: the newest ones against a damaged file, and the last one of each recent day
 * and month against damaged records, which every later backup copies faithfully.
 *
 * Days and months are the latest that hold a backup, not the latest on the calendar. The rule is
 * then decided by the backups alone: a wrong phone clock cannot empty it, and someone who returns
 * after three weeks still has seven days to go back to. They are UTC, so the same backups always
 * give the same answer.
 */
internal fun <B> backupsToKeep(backups: Collection<B>, writtenAt: (B) -> Instant): Set<B> {
    val newestFirst = backups.sortedByDescending(writtenAt)
    fun newestOfEach(periods: Int, period: (Instant) -> Any) =
        newestFirst.distinctBy { period(writtenAt(it)) }.take(periods)
    return buildSet {
        addAll(newestFirst.take(KeptNewest))
        addAll(newestOfEach(KeptDays) { it.atOffset(ZoneOffset.UTC).toLocalDate() })
        addAll(newestOfEach(KeptMonths) { YearMonth.from(it.atOffset(ZoneOffset.UTC)) })
    }
}

internal const val KeptNewest = 2
internal const val KeptDays = 7
internal const val KeptMonths = 3

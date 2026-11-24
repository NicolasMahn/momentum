package de.nicolasmahn.momentum.domain.checklist

import java.time.LocalDate

/** Records holding ticks alone, as every record did before misses; [ticks] says each day's. */
internal fun tickRecords(ticks: (LocalDate) -> Set<String>): ChecklistRecords = ChecklistRecords { DayRecord(ticks(it)) }

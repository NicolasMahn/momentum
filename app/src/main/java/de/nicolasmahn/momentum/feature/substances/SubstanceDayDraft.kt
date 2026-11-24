package de.nicolasmahn.momentum.feature.substances

import de.nicolasmahn.momentum.core.format.formatMetricNumber
import de.nicolasmahn.momentum.core.format.parseMetricNumber
import de.nicolasmahn.momentum.core.time.isValidDateKey
import de.nicolasmahn.momentum.domain.AppState
import de.nicolasmahn.momentum.domain.substance.SubstanceLog
import de.nicolasmahn.momentum.domain.substance.SubstanceLogOrder
import de.nicolasmahn.momentum.domain.substance.SubstanceNoneDay
import de.nicolasmahn.momentum.domain.substance.SubstanceNoneDayOrder
import de.nicolasmahn.momentum.domain.substance.SubstanceSettings
import java.util.Locale

internal data class SubstanceDayRow(
    val key: String,
    val id: String?,
    val substanceId: String,
    val amountText: String,
)

/**
 * A day as it is being edited: its uses as [rows], or [none] when the person checked and had
 * nothing. The two exclude each other: a use replaces none, and none can only be chosen once the
 * day holds no use ([canSayNone]), so the uses are removed first, inline, as any record is.
 */
internal data class SubstanceDayDraft(
    val dateKey: String,
    val rows: List<SubstanceDayRow>,
    val none: Boolean = false,
    val deletedIds: Set<String> = emptySet(),
    private val originalRows: List<SubstanceDayRow> = rows,
    private val originalNone: Boolean = none,
    private val originalLogs: Map<String, SubstanceLog> = emptyMap(),
    /** Where each substance sits in the list the screen shows, so the rows keep that order. */
    private val order: Map<String, Int> = emptyMap(),
) {
    init {
        require(!none || rows.isEmpty()) { "A day holds uses or none, never both." }
    }

    val isDirty: Boolean
        get() = rows != originalRows || deletedIds.isNotEmpty() || none != originalNone

    val canSayNone: Boolean
        get() = rows.isEmpty()

    /** Whether the day said none before the draft was opened. */
    internal val wasNone: Boolean
        get() = originalNone

    fun toggle(substanceId: String): SubstanceDayDraft {
        val matchingRows = rows.filter { it.substanceId == substanceId }
        if (matchingRows.isNotEmpty()) {
            return copy(
                rows = rows.filterNot { it.substanceId == substanceId },
                deletedIds = deletedIds + matchingRows.mapNotNull(SubstanceDayRow::id),
            )
        }

        val deletedOriginals = originalRows.filter { row ->
            row.substanceId == substanceId && row.id in deletedIds
        }
        return if (deletedOriginals.isNotEmpty()) {
            copy(
                none = false,
                rows = (rows + deletedOriginals).sortedWith(rowOrder),
                deletedIds = deletedIds - deletedOriginals.mapNotNull(SubstanceDayRow::id).toSet(),
            )
        } else {
            copy(none = false, rows = (rows + newRow(substanceId)).sortedWith(rowOrder))
        }
    }

    /** Says none, or takes it back; a day that still holds a use stays as it is. */
    fun toggleNone(): SubstanceDayDraft = when {
        none -> copy(none = false)
        canSayNone -> copy(none = true)
        else -> this
    }

    fun updateAmount(
        key: String,
        amountText: String,
    ): SubstanceDayDraft = copy(
        rows = rows.map { row ->
            if (row.key == key) row.copy(amountText = amountText) else row
        },
    )

    fun markDeleted(id: String): SubstanceDayDraft {
        if (rows.none { it.id == id }) return this
        return copy(
            rows = rows.filterNot { it.id == id },
            deletedIds = deletedIds + id,
        )
    }

    fun invalidAmountKeys(): Set<String> = rows
        .filterTo(mutableSetOf()) { row ->
            if (row.amountText.isBlank()) return@filterTo false
            val amount = parseMetricNumber(row.amountText)
            amount == null || !amount.isFinite() || amount <= 0.0
        }
        .mapTo(mutableSetOf(), SubstanceDayRow::key)

    internal fun originalRow(id: String): SubstanceDayRow? =
        originalRows.firstOrNull { it.id == id }

    internal fun originalLog(id: String): SubstanceLog? = originalLogs[id]

    private fun newRow(substanceId: String): SubstanceDayRow {
        val existingKeys = rows.mapTo(mutableSetOf(), SubstanceDayRow::key)
        var index = 1
        var key: String
        do {
            key = "new-$substanceId-$index"
            index += 1
        } while (key in existingKeys)
        return SubstanceDayRow(key = key, id = null, substanceId = substanceId, amountText = "")
    }

    private val rowOrder: Comparator<SubstanceDayRow>
        get() = compareBy<SubstanceDayRow> { order[it.substanceId] ?: order.size }
            .thenBy { it.id == null }
}

/** Amounts are written in [locale]'s decimal style; parsing accepts either separator. */
internal fun substanceDayDraft(
    logs: List<SubstanceLog>,
    dateKey: String,
    locale: Locale,
    substances: SubstanceSettings,
    noneDays: List<SubstanceNoneDay> = emptyList(),
): SubstanceDayDraft {
    val order = substances.all.withIndex().associate { (index, substance) -> substance.id to index }
    val rows = logs
        .asSequence()
        .filter { it.dateKey == dateKey }
        .sortedWith(
            compareBy<SubstanceLog> { order[it.substanceId] ?: order.size }
                .thenByDescending { it.timestampMillis }
                .thenBy { it.id },
        )
        .map { log ->
            SubstanceDayRow(
                key = log.id,
                id = log.id,
                substanceId = log.substanceId,
                amountText = log.amount?.let { formatMetricNumber(it, locale) }.orEmpty(),
            )
        }
        .toList()
    return SubstanceDayDraft(
        dateKey = dateKey,
        rows = rows,
        // A stored day never holds both; a damaged one is read as its uses, which stay visible.
        none = rows.isEmpty() && noneDays.any { it.dateKey == dateKey },
        originalLogs = logs
            .filter { it.dateKey == dateKey }
            .associateBy(SubstanceLog::id),
        order = order,
    )
}

internal fun reconcileSubstanceDayDraft(
    state: AppState,
    draft: SubstanceDayDraft,
    idFactory: () -> String,
    timestampMillis: Long,
): AppState {
    require(isValidSubstanceDateKey(draft.dateKey)) { "Date must use a valid YYYY-MM-DD value." }
    require(timestampMillis >= 0) { "Timestamp must not be negative." }
    require(draft.invalidAmountKeys().isEmpty()) { "Amounts must be positive or blank." }
    require(
        draft.rows.groupBy(SubstanceDayRow::substanceId).values.none { rows ->
            rows.size > 1 && rows.any { it.id == null }
        },
    ) { "A day can contain only one new record for each substance." }
    if (hasSubstanceDayConflict(state, draft)) return state

    fun unitOf(row: SubstanceDayRow) = row.parsedAmount()?.let {
        requireNotNull(state.substances.substance(row.substanceId)) { "A record must name a known substance." }.unit
    }

    val persistedRows = draft.rows.mapNotNull { row -> row.id?.let { it to row } }.toMap()
    val generatedIds = mutableSetOf<String>()

    val reconciledExisting = state.substanceLogs.mapNotNull { log ->
        if (log.dateKey != draft.dateKey) return@mapNotNull log
        if (log.id in draft.deletedIds) return@mapNotNull null

        val row = persistedRows[log.id] ?: return@mapNotNull log
        val original = draft.originalRow(log.id)
        if (original == null || row.amountText == original.amountText) {
            log
        } else if (log != draft.originalLog(log.id)) {
            log
        } else {
            log.copy(
                timestampMillis = timestampMillis,
                amount = row.parsedAmount(),
                amountUnit = unitOf(row),
            )
        }
    }

    val newLogs = draft.rows.filter { it.id == null }.map { row ->
        val id = idFactory()
        require(id.isNotBlank()) { "Substance log ID must not be blank." }
        require(
            state.substanceLogs.none { it.id == id } && generatedIds.add(id),
        ) { "Substance log ID must be unique." }
        SubstanceLog(
            id = id,
            substanceId = row.substanceId,
            dateKey = draft.dateKey,
            timestampMillis = timestampMillis,
            amount = row.parsedAmount(),
            amountUnit = unitOf(row),
        )
    }

    val reconciled = (reconciledExisting + newLogs).sortedWith(SubstanceLogOrder)
    // None stands only on a day without uses, with the time it was first said; a use replaces it.
    val others = state.substanceNoneDays.filterNot { it.dateKey == draft.dateKey }
    val noneDays = when {
        !draft.none || reconciled.any { it.dateKey == draft.dateKey } -> others
        draft.wasNone -> state.substanceNoneDays
        else -> (others + SubstanceNoneDay(draft.dateKey, timestampMillis)).sortedWith(SubstanceNoneDayOrder)
    }
    return if (reconciled == state.substanceLogs && noneDays == state.substanceNoneDays) state else state.copy(substanceLogs = reconciled, substanceNoneDays = noneDays)
}

internal fun hasSubstanceDayConflict(
    state: AppState,
    draft: SubstanceDayDraft,
): Boolean {
    val currentById = state.substanceLogs.associateBy(SubstanceLog::id)
    val changedIds = draft.rows.filter { row ->
        val id = row.id ?: return@filter false
        row != draft.originalRow(id)
    }.mapNotNullTo(mutableSetOf(), SubstanceDayRow::id)
    if (changedIds.any { id -> currentById[id] != draft.originalLog(id) }) return true

    val touchedSubstances = buildSet {
        draft.rows.filter { it.id == null }.mapTo(this, SubstanceDayRow::substanceId)
        changedIds.mapNotNullTo(this) { id -> draft.originalRow(id)?.substanceId }
        draft.deletedIds.mapNotNullTo(this) { id -> draft.originalRow(id)?.substanceId }
    }
    if (state.substanceLogs.any { log ->
            log.dateKey == draft.dateKey &&
                log.substanceId in touchedSubstances &&
                draft.originalLog(log.id) == null
        }
    ) {
        return true
    }

    if (draft.deletedIds.any { id -> currentById[id]?.let { it != draft.originalLog(id) } == true }) return true

    // Saying none over a use logged meanwhile would overwrite it; the day is reloaded instead.
    val storedNone = state.substanceNoneDays.any { it.dateKey == draft.dateKey }
    return draft.none && (storedNone != draft.wasNone || state.substanceLogs.any { it.dateKey == draft.dateKey && draft.originalLog(it.id) == null })
}

internal fun isValidSubstanceDateKey(dateKey: String): Boolean = isValidDateKey(dateKey)

private fun SubstanceDayRow.parsedAmount(): Double? =
    amountText.takeUnless(String::isBlank)?.let(::parseMetricNumber)

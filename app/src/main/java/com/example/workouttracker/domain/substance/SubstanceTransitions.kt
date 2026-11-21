package com.example.workouttracker.domain.substance

import com.example.workouttracker.core.time.requireValidDateKey
import com.example.workouttracker.domain.AppState
import com.example.workouttracker.domain.checklist.WhenAway
import com.example.workouttracker.domain.checklist.joining
import com.example.workouttracker.domain.checklist.judgedBefore
import com.example.workouttracker.domain.checklist.leaving
import java.time.LocalDate

/** Saves a use. A day holds uses or "none", so a use on a day that said none replaces that. */
fun saveSubstanceLog(
    state: AppState,
    log: SubstanceLog,
): AppState {
    require(log.id.isNotBlank()) { "Substance log ID must not be blank." }
    requireValidDateKey(log.dateKey)
    require(log.timestampMillis >= 0) { "Timestamp must not be negative." }
    val substance = requireNotNull(state.substances.substance(log.substanceId)) { "A record must name a known substance." }
    requireValidAmount(log, substance)
    require(
        state.substanceLogs.none { existing ->
            existing.id != log.id &&
                existing.dateKey == log.dateKey &&
                existing.substanceId == log.substanceId
        },
    ) { "A day can contain only one record for each substance." }

    return state.copy(
        substanceLogs = (state.substanceLogs.filterNot { it.id == log.id } + log).sortedWith(SubstanceLogOrder),
        substanceNoneDays = state.substanceNoneDays.filterNot { it.dateKey == log.dateKey },
    )
}

fun deleteSubstanceLog(
    state: AppState,
    id: String,
): AppState {
    require(id.isNotBlank()) { "Substance log ID must not be blank." }
    val updated = state.substanceLogs.filterNot { it.id == id }
    return if (updated.size == state.substanceLogs.size) state else state.copy(substanceLogs = updated)
}

/**
 * Records "none" for [dateKey]: the person checked and had nothing. It never stands beside a use,
 * and never replaces one: a day that holds a use is refused, so whoever asks must remove the uses
 * first, or open the day instead, as the check-in's action does.
 */
fun saveSubstanceNoneDay(
    state: AppState,
    dateKey: String,
    timestampMillis: Long,
): AppState {
    requireValidDateKey(dateKey)
    require(timestampMillis >= 0) { "Timestamp must not be negative." }
    require(state.substanceLogs.none { it.dateKey == dateKey }) { "A day with a use cannot say none." }
    val noneDay = SubstanceNoneDay(dateKey, timestampMillis)
    return state.copy(substanceNoneDays = (state.substanceNoneDays.filterNot { it.dateKey == dateKey } + noneDay).sortedWith(SubstanceNoneDayOrder))
}

fun deleteSubstanceNoneDay(
    state: AppState,
    dateKey: String,
): AppState {
    val updated = state.substanceNoneDays.filterNot { it.dateKey == dateKey }
    return if (updated.size == state.substanceNoneDays.size) state else state.copy(substanceNoneDays = updated)
}

/** Whether [dateKey] was logged with a use or "none"; see [isSubstancesLoggedFor]. */
fun AppState.isSubstancesLoggedFor(dateKey: String): Boolean = isSubstancesLoggedFor(substanceLogs, substanceNoneDays, dateKey)

/**
 * Which substances are tracked, the person's own and what Substances asks, as the Substances page
 * saved them. A record is a fact that outlives any choice here: an own substance with records
 * cannot go, and keeps the unit its amounts were counted in.
 */
fun saveSubstanceSettings(
    state: AppState,
    settings: SubstanceSettings,
): AppState {
    val recorded = state.substanceLogs.mapTo(HashSet()) { it.substanceId }
    val before = state.substances.own.associateBy { it.id }
    val after = settings.own.associateBy { it.id }
    require((before.keys - after.keys).none { it in recorded }) { "An own substance with records stays." }
    require(after.all { (id, own) -> id !in recorded || before[id]?.unit == own.unit }) {
        "An own substance with records keeps its unit."
    }
    return if (settings == state.substances) state else state.copy(substances = settings)
}

/**
 * Logging counts toward the streak from [today], with the bank a checklist starts with. Earlier
 * days stay outside the walk, so joining can never break a streak; joining again after leaving
 * opens a new period.
 */
fun SubstanceSettings.joiningStreak(today: LocalDate): SubstanceSettings = copy(rewards = rewards.joining(today))

/**
 * Logging stops counting after yesterday, so the days judged keep their judgments; a period that
 * only started today leaves nothing behind. The bank's settings stay for when it joins again.
 */
fun SubstanceSettings.leavingStreak(today: LocalDate): SubstanceSettings = copy(rewards = rewards?.leaving(today))

/**
 * What an away day asks from [today] on; earlier away days keep what they meant. A rule no earlier
 * day was judged by, as while Substances never counted toward the streak before today, changes
 * plainly, as the contract has every such setting change.
 */
fun SubstanceSettings.awayRuleFrom(today: LocalDate, rule: WhenAway): SubstanceSettings =
    copy(whenAway = whenAway.changedFrom(today, rule, judgedBefore(today)))

/**
 * Whether a day before [today] was judged by Substances' settings: one it took part in the streak
 * on. Otherwise no earlier day ever read them, so a change needs no dated revision.
 */
internal fun SubstanceSettings.judgedBefore(today: LocalDate): Boolean = rewards?.judgedBefore(today) == true

private fun requireValidAmount(log: SubstanceLog, substance: Substance) {
    if (log.amount == null) {
        require(log.amountUnit == null) { "An amount unit requires an amount." }
        return
    }

    require(log.amount.isFinite() && log.amount > 0.0) { "Amount must be positive." }
    require(log.amountUnit == substance.unit) {
        "Amount unit must match the selected substance."
    }
}

/** Newest first, as every list of substance records is kept. */
val SubstanceLogOrder: Comparator<SubstanceLog> = compareByDescending<SubstanceLog> { it.dateKey }
    .thenByDescending { it.timestampMillis }
    .thenBy { it.id }

val SubstanceNoneDayOrder: Comparator<SubstanceNoneDay> = compareByDescending { it.dateKey }

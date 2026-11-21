package com.example.workouttracker.domain.reward

import com.example.workouttracker.domain.TrackerRef
import com.example.workouttracker.domain.checklist.Dated
import com.example.workouttracker.domain.mo.TrackerObservation
import java.time.LocalDate

/** Configuration chooses ordering; identity never changes how the walk evaluates a target. */
data class RewardOrder(
    val judge: Int = Int.MAX_VALUE,
    val earn: Int = Int.MAX_VALUE,
    val nextToken: Int = Int.MAX_VALUE,
)

/** One configured bank and the facts needed to present it. No tracker-family discriminator. */
class TrackerReward(
    val participant: RewardParticipant<TrackerRef>,
    val bankShownOn: (LocalDate) -> Boolean,
    val order: RewardOrder = RewardOrder(),
    /** A weekly target can still show today's factual log before its week is settled. */
    val recordedOn: ((LocalDate) -> Boolean)? = null,
    /** The amount named when a closed earning policy is presented, owned by its tracker. */
    val earningBasisOn: (LocalDate) -> Int = { day -> participant.rulesOn(day).earn.basisAmount() },
) {
    val ref: TrackerRef get() = participant.key

    /** Whether the rule of [day] fills the bank by kept days counted across weeks, and not by a week's days or amount. */
    fun earnsAcrossWeeksOn(day: LocalDate): Boolean = participant.rulesOn(day).earn is Earn.EveryKeptDays
}

/** Every reward participant, indexed once; stable sorting preserves configuration order. */
class TrackerRewardCatalogue(entries: List<TrackerReward>, val observations: List<TrackerObservation> = emptyList()) {
    init {
        require(entries.map { it.ref }.distinct().size == entries.size) { "A tracker has two reward participants." }
        require(observations.map { it.ref }.distinct().size == observations.size) { "A tracker has two observation sources." }
    }

    val entries = entries.sortedBy { it.order.judge }
    val participants = this.entries.map { it.participant }
    val earnOrder = entries.sortedBy { it.order.earn }.map { it.ref }
    val nextTokenOrder = entries.sortedBy { it.order.nextToken }.map { it.ref }
}

/** One daily/checkpoint target, including explicit policies for excused days and participation. */
fun dailyTrackerReward(
    ref: TrackerRef,
    today: LocalDate,
    rulesOn: (LocalDate) -> RewardRules,
    began: LocalDate?,
    visibility: Dated<Boolean>,
    openOn: (LocalDate) -> Int,
    takesPartOn: (LocalDate) -> Boolean,
    bankShownOn: (LocalDate) -> Boolean,
    excusedOn: (LocalDate) -> Boolean = { false },
    order: RewardOrder = RewardOrder(),
    earningBasisOn: ((LocalDate) -> Int)? = null,
): TrackerReward = TrackerReward(
    participant = RewardParticipant(ref, rulesOn(today), RewardTarget.Daily(openOn, excusedOn), began, visibility, takesPartOn, rulesOn),
    bankShownOn = bankShownOn,
    order = order,
    earningBasisOn = earningBasisOn ?: { rulesOn(it).earn.basisAmount() },
)

/** Weekly targets use the same bank contract as daily targets. */
fun weeklyTrackerReward(
    ref: TrackerRef,
    today: LocalDate,
    rulesOn: (LocalDate) -> RewardRules,
    target: RewardTarget,
    began: LocalDate?,
    visibility: Dated<Boolean>,
    takesPartOn: (LocalDate) -> Boolean,
    bankShownOn: (LocalDate) -> Boolean,
    order: RewardOrder = RewardOrder(),
    recordedOn: ((LocalDate) -> Boolean)? = null,
    earningBasisOn: ((LocalDate) -> Int)? = null,
): TrackerReward = TrackerReward(
    participant = RewardParticipant(ref, rulesOn(today), target, began, visibility, takesPartOn, rulesOn),
    bankShownOn = bankShownOn,
    order = order,
    recordedOn = recordedOn,
    earningBasisOn = earningBasisOn ?: { rulesOn(it).earn.basisAmount() },
)

private fun Earn.basisAmount(): Int = when (this) {
    is Earn.EveryKeptDays -> days
    is Earn.KeptDaysInWeek -> steps.first().days
    is Earn.AmountStepsInWeek -> steps.first().amount
}

package de.nicolasmahn.momentum.domain.reward

import de.nicolasmahn.momentum.domain.checklist.Dated
import de.nicolasmahn.momentum.domain.holiday.HolidaySchedule
import de.nicolasmahn.momentum.domain.mo.MoDayMark
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A missed day names the views that broke it, as the walk judged them that day: a view that joined
 * the streak only today was no part of yesterday, and one that left today still was.
 */
class MissedViewsTest {
    private val today = LocalDate.parse("2026-11-20")
    private val start = today.minusDays(10)
    private val rules = RewardRules(maxPasses = 1, earn = Earn.EveryKeptDays(7))

    /** A view that misses every day it takes part on. */
    private fun neverDone(key: String, takesPartOn: (LocalDate) -> Boolean = { true }) = RewardParticipant(
        key = key,
        rules = rules,
        target = RewardTarget.Daily(openOn = { 1 }),
        began = start,
        visibility = Dated.always(true),
        takesPartOn = takesPartOn,
    )

    @Test fun aViewThatJoinedTodayIsNoPartOfYesterdaysMissAndOneThatLeftTodayIs() {
        val participants = listOf(
            neverDone("old"),
            neverDone("joinedToday") { it >= today },
            neverDone("leftToday") { it < today },
        )
        val walk = rewardWalk(participants, participants.map { it.key }, today, HolidaySchedule())
        val yesterday = walk.history.single { it.date == today.minusDays(1) }

        assertEquals(MoDayMark.Missed, yesterday.mark)
        assertEquals(listOf("old", "leftToday"), yesterday.missed)
        // Only a missed day names anything; a day a pass or a freeze covered names none.
        assertEquals(emptyList<String>(), walk.history.first { it.mark != MoDayMark.Missed }.missed)
    }

    /** The views their own bank could not pay for come first: filling them in is what could save the day. */
    @Test fun theViewsTheBanksCouldNotPayForComeFirst() {
        val twoGaps = RewardParticipant("twoGaps", rules, RewardTarget.Daily(openOn = { 2 }), start, Dated.always(true))
        val participants = listOf(neverDone("oneGap"), twoGaps)
        val walk = rewardWalk(participants, participants.map { it.key }, today, HolidaySchedule())
        val firstMiss = walk.history.first { it.mark == MoDayMark.Missed }
        assertEquals("twoGaps", firstMiss.missed.first())
    }
}

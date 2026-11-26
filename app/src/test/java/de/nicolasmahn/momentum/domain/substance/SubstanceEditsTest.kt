package de.nicolasmahn.momentum.domain.substance

import de.nicolasmahn.momentum.domain.checklist.MoMotif
import de.nicolasmahn.momentum.domain.checklist.ViewIcon
import de.nicolasmahn.momentum.domain.checklist.ViewHue
import de.nicolasmahn.momentum.domain.AppState
import de.nicolasmahn.momentum.domain.checklist.Dated
import de.nicolasmahn.momentum.domain.checklist.RewardPeriod
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Substances page's edits, applied on a day: dated once an earlier day was judged, plain before, and never reaching an earlier day. */
class SubstanceEditsTest {
    private val today = LocalDate.parse("2026-10-06")
    private val yesterday = today.minusDays(1)
    private val cannabis = SubstanceKind.CANNABIS.id
    private val alcohol = SubstanceKind.ALCOHOL.id
    private val tobacco = SubstanceKind.TOBACCO.id
    private val coffee = SubstanceKind.COFFEE.id

    /** Logging has counted for ten days, so every earlier day was judged by these settings. */
    private val judged = SubstanceSettings.Legacy.joiningStreak(today.minusDays(10))

    private val changes = listOf(
        SubstanceEdit.SetGoal(SubstanceGoal.StayingAway),
        SubstanceEdit.StayAway(cannabis, false),
        SubstanceEdit.SetExceptionsOnly(false),
        SubstanceEdit.SetRewardRules(5, 7),
    )

    /** The look judges no day: it applies at once, dates nothing, and a name taken back leaves the app's own word. */
    @Test
    fun theLookAppliesAtOnceAndDatesNothing() {
        val looked = judged.applyingEdits(
            listOf(SubstanceEdit.Rename("Evenings"), SubstanceEdit.SetIcon(ViewIcon.Drink), SubstanceEdit.SetHue(ViewHue.Berry), SubstanceEdit.SetMotif(MoMotif.Food)),
            today,
        )

        assertEquals(judged.copy(label = "Evenings", icon = ViewIcon.Drink, hue = ViewHue.Berry, mo = judged.mo.copy(motif = MoMotif.Food)), looked)
        assertEquals(judged.mo.enabled, looked.mo.enabled)
        assertNull(looked.applyEdit(SubstanceEdit.Rename(null), today).label)
        assertNull(looked.applyEdit(SubstanceEdit.SetMotif(null), today).mo.motif)
        // A blank name is none, and is refused as every view's is.
        assertTrue(runCatching { judged.applyEdit(SubstanceEdit.Rename(" "), today) }.isFailure)
        assertEquals(judged, judged.applyingEdits(listOf(SubstanceEdit.Rename("")), today))
    }

    @Test
    fun aChangeIsDatedOnlyOnceAnEarlierDayWasJudged() {
        val fresh = SubstanceSettings.Legacy.applyingEdits(changes, today)
        assertEquals(Dated.always(SubstanceGoal.StayingAway), fresh.goal)
        assertEquals(Dated.always(setOf(alcohol, tobacco)), fresh.stayingAway)
        assertEquals(Dated.always(false), fresh.exceptionsOnly)
        assertEquals(Dated.always(5), fresh.rewards?.maxPasses)
        assertEquals(listOf(RewardPeriod(today)), fresh.rewards?.periods)

        val dated = judged.applyingEdits(changes, today)
        assertEquals(SubstanceGoal.Logging to SubstanceGoal.StayingAway, dated.goal.on(yesterday) to dated.goal.on(today))
        assertEquals(SubstanceSettings.Legacy.tracked to setOf(alcohol, tobacco), dated.stayingAway.on(yesterday) to dated.stayingAway.on(today))
        assertEquals(true to false, dated.exceptionsOnly.on(yesterday) to dated.exceptionsOnly.on(today))
        assertEquals(3 to 5, dated.rewards?.maxPasses?.on(yesterday) to dated.rewards?.maxPasses?.on(today))
        assertEquals(listOf(RewardPeriod(today.minusDays(10))), dated.rewards?.periods)
    }

    /** Untracking takes a substance out of the stance from today on; earlier days keep it, and tracked again it starts just tracked. */
    @Test
    fun untrackingLeavesTheStanceFromTodayOn() {
        val untracked = judged.applyEdit(SubstanceEdit.Track(cannabis, false), today)
        assertFalse(untracked.isTracked(cannabis))
        assertEquals(SubstanceSettings.Legacy.tracked, untracked.stayingAway.on(yesterday))
        assertEquals(setOf(alcohol, tobacco), untracked.stayingAway.on(today))

        val back = untracked.applyEdit(SubstanceEdit.Track(cannabis, true), today)
        assertTrue(back.isTracked(cannabis))
        assertFalse(back.isStayingAway(cannabis))
        assertEquals(SubstanceSettings.Legacy.tracked, back.stayingAway.on(yesterday))
    }

    @Test
    fun aChangeMadeAndTakenBackTheSameDayLeavesNoRevision() {
        val back = judged
            .applyEdit(SubstanceEdit.StayAway(cannabis, false), today).applyEdit(SubstanceEdit.StayAway(cannabis, true), today)
            .applyEdit(SubstanceEdit.SetGoal(SubstanceGoal.StayingAway), today).applyEdit(SubstanceEdit.SetGoal(SubstanceGoal.Logging), today)
            .applyEdit(SubstanceEdit.SetExceptionsOnly(false), today).applyEdit(SubstanceEdit.SetExceptionsOnly(true), today)
            .applyEdit(SubstanceEdit.SetRewardRules(5, 7), today).applyEdit(SubstanceEdit.SetRewardRules(3, 7), today)
        assertEquals(judged, back)
    }

    /** Off leaves after yesterday, keeping the days judged; a bank that never held a day is not kept at all. */
    @Test
    fun offLeavesAfterYesterdayAndABankThatNeverHeldADayIsNotKept() {
        val left = judged.applyEdit(SubstanceEdit.SetGoal(null), today)
        assertEquals(listOf(RewardPeriod(today.minusDays(10), yesterday)), left.rewards?.periods)
        assertNull(left.goalOn(today))
        assertEquals(SubstanceGoal.Logging, left.goalOn(yesterday))

        val joinedToday = SubstanceSettings.Legacy.applyEdit(SubstanceEdit.SetGoal(SubstanceGoal.Logging), today)
        assertNull(joinedToday.applyEdit(SubstanceEdit.SetGoal(null), today).rewards)
        assertNull(joinedToday.applyEdit(SubstanceEdit.SetRewardRules(5, 7), today).applyEdit(SubstanceEdit.SetGoal(null), today).rewards)
    }

    /** What the settings cannot take is refused, and the edits applied over them leave such an edit out and keep the rest. */
    @Test
    fun aRefusedEditIsLeftOutOfAppliedEdits() {
        assertThrows(IllegalArgumentException::class.java) { SubstanceSettings.Legacy.applyEdit(SubstanceEdit.StayAway(coffee, true), today) }
        assertThrows(IllegalArgumentException::class.java) { SubstanceSettings.Legacy.applyEdit(SubstanceEdit.SetRewardRules(5, 7), today) }
        assertThrows(IllegalArgumentException::class.java) { SubstanceSettings.Legacy.applyEdit(SubstanceEdit.Track("sub_zzzz", true), today) }
        assertThrows(IllegalArgumentException::class.java) { SubstanceSettings.Legacy.applyEdit(SubstanceEdit.RenameOwn("sub_zzzz", "Kratom"), today) }
        assertThrows(IllegalArgumentException::class.java) { SubstanceSettings.Legacy.applyEdit(SubstanceEdit.AddOwn(coffee, "Coffee", SubstanceAmountUnit.CUPS), today) }

        val applied = SubstanceSettings.Legacy.applyingEdits(
            listOf(SubstanceEdit.StayAway(coffee, true), SubstanceEdit.Track(coffee, true), SubstanceEdit.StayAway(coffee, true)),
            today,
        )
        assertEquals(SubstanceSettings.Legacy.tracked + coffee, applied.stayingAway.current)
    }

    /** An own substance a stance before today names cannot be removed, nor one with records; the state leaves such an edit out and keeps the rest. */
    @Test
    fun anOwnSubstanceAnEarlierStanceNamesOrWithRecordsStays() {
        val own = OwnSubstance("sub_k2p9", "Kratom", SubstanceAmountUnit.GRAMS)
        val stayed = SubstanceSettings(tracked = setOf(own.id), own = listOf(own), stayingAway = Dated.always(setOf(own.id))).joiningStreak(yesterday)
        assertThrows(IllegalArgumentException::class.java) { stayed.applyEdit(SubstanceEdit.RemoveOwn(own.id), today) }
        val untracked = stayed.applyEdit(SubstanceEdit.Track(own.id, false), today)
        assertEquals(setOf(own.id), untracked.stayingAway.on(yesterday))
        assertEquals(emptySet<String>(), untracked.stayingAway.on(today))

        val withRecord = AppState(
            substances = SubstanceSettings(tracked = setOf(own.id), own = listOf(own)),
            substanceLogs = listOf(SubstanceLog("k1", own.id, "2026-10-06", 1L)),
        )
        val applied = withRecord.applyingSubstanceEdits(listOf(SubstanceEdit.RemoveOwn(own.id), SubstanceEdit.Track(coffee, true)), today)
        assertEquals(setOf(own.id, coffee), applied.substances.tracked)
        assertEquals(listOf(own), applied.substances.own)
    }

    /** A day asks something while the goal is logging, or while the stance of that day names a substance. */
    @Test
    fun aDayWithNothingToStayAwayFromAsksNothing() {
        val nothing = SubstanceSettings.Legacy.applyingEdits(
            listOf(SubstanceEdit.SetGoal(SubstanceGoal.StayingAway)) + SubstanceSettings.Legacy.tracked.map { SubstanceEdit.StayAway(it, false) },
            today.minusDays(10),
        )
        assertFalse(nothing.asksOn(today))
        assertTrue(nothing.applyEdit(SubstanceEdit.SetGoal(SubstanceGoal.Logging), today).asksOn(today))
        val chosen = nothing.applyEdit(SubstanceEdit.StayAway(cannabis, true), today)
        assertFalse(chosen.asksOn(yesterday))
        assertTrue(chosen.asksOn(today))
    }
}

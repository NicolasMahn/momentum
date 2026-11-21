package com.example.workouttracker.feature.settings

import com.example.workouttracker.domain.checklist.MoMotif
import com.example.workouttracker.domain.checklist.ViewIcon
import com.example.workouttracker.domain.checklist.ViewHue
import com.example.workouttracker.domain.checklist.Dated
import com.example.workouttracker.domain.checklist.RewardPeriod
import com.example.workouttracker.domain.checklist.WhenAway
import com.example.workouttracker.domain.substance.OwnSubstance
import com.example.workouttracker.domain.substance.SubstanceAmountUnit
import com.example.workouttracker.domain.substance.SubstanceEdit
import com.example.workouttracker.domain.substance.SubstanceGoal
import com.example.workouttracker.domain.substance.SubstanceKind
import com.example.workouttracker.domain.substance.SubstanceSettings
import com.example.workouttracker.domain.substance.joiningStreak
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** The Substances page keeps the edits made over the stored choice; the store waits for Save, which applies them on its day. */
class SubstanceSettingsDraftTest {
    private val today: LocalDate = LocalDate.parse("2026-10-06")
    private val cannabis = SubstanceKind.CANNABIS.id
    private val coffee = SubstanceKind.COFFEE.id

    @Test
    fun aNewOwnSubstanceGetsAFreshIdAndIsTrackedFromTheStart() {
        val draft = SubstanceSettingsDraft(SubstanceSettings.Legacy, today)

        val id = draft.add("Kratom", SubstanceAmountUnit.GRAMS)

        assertTrue(id.startsWith("sub_"))
        assertEquals(listOf(OwnSubstance(id, "Kratom", SubstanceAmountUnit.GRAMS)), draft.edited.own)
        assertTrue(draft.edited.isTracked(id))
        assertTrue(draft.canSave)
        // A second one never reuses the first's ID.
        assertTrue(draft.add("Kava", SubstanceAmountUnit.CUPS) != id)
    }

    @Test
    fun removingAnOwnSubstanceUntracksItAndForgetsItsBlankName() {
        val draft = SubstanceSettingsDraft(SubstanceSettings.Legacy, today)
        val id = draft.add("Kratom", SubstanceAmountUnit.GRAMS)
        draft.named(id, null)
        assertFalse(draft.canSave)

        assertTrue(draft.edit(SubstanceEdit.RemoveOwn(id)))

        assertEquals(SubstanceSettings.Legacy, draft.edited)
        assertFalse(draft.isDirty)
        assertFalse(draft.hasUnsavedWork)
    }

    @Test
    fun aBlankNameAndATypedNewNameHoldTheWayOutButOnlyAChangeCanBeSaved() {
        val own = OwnSubstance("sub_k2p9", "Kratom", SubstanceAmountUnit.GRAMS)
        val stored = SubstanceSettings(tracked = setOf(own.id), own = listOf(own))
        val draft = SubstanceSettingsDraft(stored, today)
        assertFalse(draft.hasUnsavedWork)

        draft.typing("Ka")
        assertTrue(draft.hasUnsavedWork)
        assertFalse(draft.canSave)
        draft.typing("")
        assertFalse(draft.hasUnsavedWork)

        draft.named(own.id, null)
        assertTrue(draft.hasUnsavedWork)
        assertFalse(draft.canSave)
        assertFalse(draft.nameStored(own.id))
        draft.named(own.id, "Mitragyna")
        assertTrue(draft.edit(SubstanceEdit.RenameOwn(own.id, "Mitragyna")))
        assertTrue(draft.canSave)
        assertFalse(draft.nameStored(own.id))

        val saved = draft.edited
        draft.saved()
        draft.follow(saved, today)
        assertFalse(draft.isDirty)
        assertTrue(draft.nameStored(own.id))
        assertEquals("Mitragyna", draft.stored.own.single().label)
    }

    /** A typed new name is all the page holds once nothing waits for Save and no name is blank: that is when a saved page stays open for it. */
    @Test
    fun aTypedNewNameIsAllThePageHoldsOnceItsEditsAreSavedAndEveryNameIsFilledIn() {
        val own = OwnSubstance("sub_k2p9", "Kratom", SubstanceAmountUnit.GRAMS)
        val draft = SubstanceSettingsDraft(SubstanceSettings(tracked = setOf(own.id), own = listOf(own)), today)
        draft.typing("Kava")
        assertTrue(draft.edit(SubstanceEdit.Track(coffee, true)))
        assertFalse(draft.holdsOnlyTypedText)

        val saved = draft.edited
        draft.saved()
        // Save asks at once, before the stored choice has reached the page.
        assertTrue(draft.holdsOnlyTypedText)
        draft.follow(saved, today)
        assertTrue(draft.holdsOnlyTypedText)
        assertTrue(draft.hasUnsavedWork)
        assertFalse(draft.canSave)

        draft.named(own.id, null)
        assertFalse(draft.holdsOnlyTypedText)
        draft.named(own.id, "Kratom")
        assertTrue(draft.holdsOnlyTypedText)

        draft.typing("")
        assertFalse(draft.holdsOnlyTypedText)
        assertFalse(draft.hasUnsavedWork)
    }

    /** A choice stored meanwhile reaches the page under its own edits, which stay on top of it. */
    @Test
    fun thePageFollowsTheStoredChoiceUnderItsOwnEdits() {
        val draft = SubstanceSettingsDraft(SubstanceSettings.Legacy, today)
        val tobacco = SubstanceSettings(tracked = setOf(SubstanceKind.TOBACCO.id))

        draft.follow(tobacco, today)
        assertEquals(tobacco, draft.edited)
        assertFalse(draft.isDirty)

        assertTrue(draft.edit(SubstanceEdit.Track(coffee, true)))
        draft.follow(SubstanceSettings.Legacy, today)
        assertEquals(SubstanceSettings.Legacy.tracked + coffee, draft.edited.tracked)
        assertEquals(SubstanceSettings.Legacy, draft.stored)
        assertTrue(draft.isDirty)
    }

    /** An edit the latest choice refuses, such as a rename of a substance removed meanwhile, is left out, so what is shown is what is saved. */
    @Test
    fun anEditTheLatestChoiceRefusesIsLeftOut() {
        val own = OwnSubstance("sub_k2p9", "Kratom", SubstanceAmountUnit.GRAMS)
        val draft = SubstanceSettingsDraft(SubstanceSettings(tracked = setOf(own.id), own = listOf(own)), today)
        assertTrue(draft.edit(SubstanceEdit.RenameOwn(own.id, "Mitragyna")))

        draft.follow(SubstanceSettings(), today)

        assertEquals(SubstanceSettings(), draft.edited)
        assertFalse(draft.isDirty)
    }

    @Test
    fun aRefusedEditIsNotKept() {
        val draft = SubstanceSettingsDraft(SubstanceSettings.Legacy, today)
        assertFalse(draft.edit(SubstanceEdit.StayAway(coffee, true)))
        assertFalse(draft.edit(SubstanceEdit.SetRewardRules(5, 7)))
        assertEquals(emptyList<SubstanceEdit>(), draft.edits)
        assertFalse(draft.isDirty)
    }

    /** The view's own name is a field of the page: renamed it waits for Save, and taken back it leaves nothing to save. */
    @Test
    fun theViewsNameIsEditedLikeAnyOtherAndTakenBackLeavesNothing() {
        val draft = SubstanceSettingsDraft(SubstanceSettings.Legacy, today)
        assertTrue(draft.nameStored(SubstancesNameKey))

        assertTrue(draft.rename("Evenings"))
        assertEquals("Evenings", draft.edited.label)
        assertFalse(draft.nameStored(SubstancesNameKey))
        assertTrue(draft.canSave)

        assertTrue(draft.edit(SubstanceEdit.Rename(null)))
        assertNull(draft.edited.label)
        assertFalse(draft.isDirty)

        assertTrue(draft.setIcon(ViewIcon.Drink) && draft.setHue(ViewHue.Berry) && draft.setMotif(MoMotif.Food))
        assertEquals(SubstanceSettings.Legacy.copy(icon = ViewIcon.Drink, hue = ViewHue.Berry, mo = SubstanceSettings.Legacy.mo.copy(motif = MoMotif.Food)), draft.edited)
        // A blank name holds Save back until it is typed again.
        draft.named(SubstancesNameKey, null)
        assertFalse(draft.canSave)
    }

    @Test
    fun theDraftSurvivesProcessDeathWhole() {
        val draft = SubstanceSettingsDraft(SubstanceSettings.Legacy, today)
        val id = draft.add("Kratom", SubstanceAmountUnit.GRAMS)
        listOf(
            SubstanceEdit.Track(SubstanceKind.ALCOHOL.id, false),
            SubstanceEdit.StayAway(id, true),
            SubstanceEdit.SetGoal(SubstanceGoal.StayingAway),
            SubstanceEdit.SetExceptionsOnly(false),
            SubstanceEdit.SetRewardRules(5, 10),
            SubstanceEdit.SetWhenAway(WhenAway.Excused),
            SubstanceEdit.Rename("Evenings"),
            SubstanceEdit.Rename(null),
            SubstanceEdit.SetIcon(ViewIcon.Drink),
            SubstanceEdit.SetHue(ViewHue.Berry),
            SubstanceEdit.SetMotif(MoMotif.Food),
            SubstanceEdit.SetMotif(null),
        ).forEach { assertTrue(draft.edit(it)) }
        draft.named(id, null)
        draft.typing("Kava")

        val saved = with(SubstanceSettingsDraft.Saver) { SaverScopeStub.save(draft) }
        val restored = requireNotNull(SubstanceSettingsDraft.Saver.restore(requireNotNull(saved)))

        assertEquals(draft.stored, restored.stored)
        assertEquals(draft.edits, restored.edits)
        assertEquals(draft.edited, restored.edited)
        assertEquals(setOf(id), restored.blankNames)
        assertTrue(restored.pendingText)
        assertEquals(today, restored.today)
    }

    /** Untracking a substance ends staying away from it; tracked again later, it is just tracked, as a newly tracked one is. */
    @Test
    fun untrackingASubstanceEndsStayingAwayFromItAndTrackingStartsAsJustTracking() {
        val draft = SubstanceSettingsDraft(SubstanceSettings.Legacy, today)
        assertTrue(draft.edited.isStayingAway(cannabis))

        assertTrue(draft.edit(SubstanceEdit.Track(cannabis, false)))
        assertTrue(draft.edit(SubstanceEdit.Track(cannabis, true)))
        assertTrue(draft.edited.isTracked(cannabis))
        assertFalse(draft.edited.isStayingAway(cannabis))

        assertTrue(draft.edit(SubstanceEdit.Track(coffee, true)))
        assertFalse(draft.edited.isStayingAway(coffee))
        assertTrue(draft.edit(SubstanceEdit.StayAway(coffee, true)))
        assertTrue(draft.edited.isStayingAway(coffee))
        // Removing an own substance forgets its stance with it.
        val id = draft.add("Kratom", SubstanceAmountUnit.GRAMS)
        assertTrue(draft.edit(SubstanceEdit.StayAway(id, true)))
        assertTrue(draft.edit(SubstanceEdit.RemoveOwn(id)))
        assertEquals(setOf(coffee), draft.edited.stayingAway.current - SubstanceSettings.Legacy.tracked)
    }

    /** Off, Logging and Staying away: joining starts a period today, leaving closes it after yesterday, and a bank that never held a day is not kept. */
    @Test
    fun theGoalTilesJoinAndLeaveTheStreak() {
        val draft = SubstanceSettingsDraft(SubstanceSettings.Legacy, today)
        assertNull(draft.edited.goalOn(today))

        assertTrue(draft.edit(SubstanceEdit.SetGoal(SubstanceGoal.StayingAway)))
        assertEquals(SubstanceGoal.StayingAway, draft.edited.goalOn(today))
        assertEquals(listOf(RewardPeriod(today)), draft.edited.rewards?.periods)
        // Nothing was judged before, so the goal changes plainly.
        assertEquals(Dated.always(SubstanceGoal.StayingAway), draft.edited.goal)
        assertTrue(draft.edit(SubstanceEdit.SetGoal(null)))
        assertNull(draft.edited.rewards)
        assertFalse(draft.isDirty)

        val joined = SubstanceSettings.Legacy.joiningStreak(today.minusDays(10))
        val leaving = SubstanceSettingsDraft(joined, today)
        assertTrue(leaving.edit(SubstanceEdit.SetGoal(null)))
        assertEquals(listOf(RewardPeriod(today.minusDays(10), today.minusDays(1))), leaving.edited.rewards?.periods)
        assertNull(leaving.edited.goalOn(today))
        assertTrue(leaving.edit(SubstanceEdit.SetGoal(SubstanceGoal.Logging)))
        assertEquals(listOf(RewardPeriod(today.minusDays(10))), leaving.edited.rewards?.periods)
    }

    /** Out after an earlier period, choosing a goal and going back to Off leaves nothing to save. */
    @Test
    fun aGoalChosenAndTakenBackAfterAnEarlierPeriodLeavesNothing() {
        val stayedAway = SubstanceSettings.Legacy.joiningStreak(today.minusDays(10))
            .let { it.copy(goal = Dated.always(SubstanceGoal.StayingAway), exceptionsOnly = Dated.always(false)) }
        val out = SubstanceSettingsDraft(stayedAway, today.minusDays(3)).apply { edit(SubstanceEdit.SetGoal(null)) }.edited
        val draft = SubstanceSettingsDraft(out, today)

        assertTrue(draft.edit(SubstanceEdit.SetGoal(SubstanceGoal.Logging)))
        assertEquals(SubstanceGoal.Logging, draft.edited.goalOn(today))
        assertTrue(draft.edit(SubstanceEdit.SetGoal(null)))
        assertEquals(out, draft.edited)
        assertFalse(draft.isDirty)
    }

    /** Counting since an earlier day, choosing Off and the same goal again leaves nothing to save. */
    @Test
    fun leavingTakenBackBeforeSaveLeavesNothing() {
        val counting = SubstanceSettings.Legacy.joiningStreak(today.minusDays(10))
        val draft = SubstanceSettingsDraft(counting, today)

        assertTrue(draft.edit(SubstanceEdit.SetGoal(null)))
        assertTrue(draft.isDirty)
        assertTrue(draft.edit(SubstanceEdit.SetGoal(SubstanceGoal.Logging)))
        assertEquals(counting, draft.edited)
        assertFalse(draft.isDirty)
    }

    /**
     * The edits are applied on the day the page shows, so a draft left unsaved past 04:00 moves
     * whole to the new day: the goal, the stance, the check-in rule, the bank and the away rule
     * are all dated by it. Taken back before Save, every change leaves no revision and nothing
     * to save.
     */
    @Test
    fun aDraftLeftPast0400DatesEveryChangeByTheNewDayAndAChangeTakenBackLeavesNothing() {
        val joined = SubstanceSettings.Legacy.joiningStreak(today.minusDays(10))
        val draft = SubstanceSettingsDraft(joined, today)
        listOf(
            SubstanceEdit.SetGoal(SubstanceGoal.StayingAway),
            SubstanceEdit.StayAway(cannabis, false),
            SubstanceEdit.SetExceptionsOnly(false),
            SubstanceEdit.SetRewardRules(5, 7),
            SubstanceEdit.SetWhenAway(WhenAway.Excused),
        ).forEach { assertTrue(draft.edit(it)) }
        assertEquals(listOf(null, today), draft.edited.goal.revisions.map { it.from })

        val tomorrow = today.plusDays(1)
        draft.follow(joined, tomorrow)
        val moved = draft.edited
        assertEquals(listOf(null, tomorrow), moved.goal.revisions.map { it.from })
        assertEquals(listOf(null, tomorrow), moved.stayingAway.revisions.map { it.from })
        assertEquals(listOf(null, tomorrow), moved.exceptionsOnly.revisions.map { it.from })
        assertEquals(listOf(null, tomorrow), moved.rewards?.maxPasses?.revisions?.map { it.from })
        assertEquals(listOf(null, tomorrow), moved.whenAway.revisions.map { it.from })
        assertTrue(draft.isDirty)

        listOf(
            SubstanceEdit.SetGoal(SubstanceGoal.Logging),
            SubstanceEdit.StayAway(cannabis, true),
            SubstanceEdit.SetExceptionsOnly(true),
            SubstanceEdit.SetRewardRules(3, 7),
            SubstanceEdit.SetWhenAway(WhenAway.Due),
        ).forEach { assertTrue(draft.edit(it)) }
        assertEquals(joined, draft.edited)
        assertFalse(draft.isDirty)
        assertFalse(draft.canSave)
    }

    /** An own substance a stance before today names can be untracked but not removed, as one with records cannot; one named by today's stance alone can go. */
    @Test
    fun anOwnSubstanceAnEarlierStanceNamesCannotBeRemoved() {
        val own = OwnSubstance("sub_k2p9", "Kratom", SubstanceAmountUnit.GRAMS)
        val yesterday = today.minusDays(1)
        val stayed = SubstanceSettings(tracked = setOf(own.id), own = listOf(own), stayingAway = Dated.always(setOf(own.id))).joiningStreak(yesterday)
        val draft = SubstanceSettingsDraft(stayed, today)
        assertFalse(draft.canRemove(own.id))
        assertFalse(draft.edit(SubstanceEdit.RemoveOwn(own.id)))
        assertTrue(draft.edit(SubstanceEdit.Track(own.id, false)))
        assertEquals(emptySet<String>(), draft.edited.stayingAway.on(today))
        assertEquals(setOf(own.id), draft.edited.stayingAway.on(yesterday))

        val fresh = SubstanceSettingsDraft(SubstanceSettings.Legacy.joiningStreak(yesterday), today)
        val id = fresh.add("Kava", SubstanceAmountUnit.CUPS)
        assertTrue(fresh.edit(SubstanceEdit.StayAway(id, true)))
        assertTrue(fresh.canRemove(id))
        assertTrue(fresh.edit(SubstanceEdit.RemoveOwn(id)))
        assertEquals(SubstanceSettings.Legacy.joiningStreak(yesterday), fresh.edited)
    }

    private object SaverScopeStub : androidx.compose.runtime.saveable.SaverScope {
        override fun canBeSaved(value: Any): Boolean = true
    }
}

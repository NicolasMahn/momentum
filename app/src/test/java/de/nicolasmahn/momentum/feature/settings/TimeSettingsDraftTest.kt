package de.nicolasmahn.momentum.feature.settings

import androidx.compose.runtime.saveable.SaverScope
import de.nicolasmahn.momentum.domain.AppState
import de.nicolasmahn.momentum.domain.checklist.CheckpointTime
import de.nicolasmahn.momentum.domain.checklist.Dated
import de.nicolasmahn.momentum.domain.checklist.MoMotif
import de.nicolasmahn.momentum.domain.checklist.RewardPeriod
import de.nicolasmahn.momentum.domain.checklist.ViewHue
import de.nicolasmahn.momentum.domain.checklist.ViewIcon
import de.nicolasmahn.momentum.domain.checklist.WhenAway
import de.nicolasmahn.momentum.domain.time.TimeConfiguration
import de.nicolasmahn.momentum.domain.time.TimeEdit
import de.nicolasmahn.momentum.domain.time.DefaultTokenSteps
import de.nicolasmahn.momentum.domain.time.TimeRewards
import de.nicolasmahn.momentum.domain.time.TokenStep
import de.nicolasmahn.momentum.domain.time.TokenSteps
import de.nicolasmahn.momentum.domain.time.addTime
import de.nicolasmahn.momentum.domain.time.applyingEdits
import de.nicolasmahn.momentum.domain.time.applyingTimeEdits
import de.nicolasmahn.momentum.domain.compatibility.madeTime
import de.nicolasmahn.momentum.domain.time.starterTime
import de.nicolasmahn.momentum.domain.time.testMadeTime
import de.nicolasmahn.momentum.domain.time.testReadingTime
import de.nicolasmahn.momentum.domain.time.withReading
import de.nicolasmahn.momentum.domain.activity.withActivity
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import de.nicolasmahn.momentum.domain.compatibility.defaultReadingCheckpoint

/**
 * A time view's page is one draft over its four parts, and its setup one more: what was typed and
 * chosen is kept as edits over the stored configuration, a goal that cannot be one holds Save and
 * Create back without replacing what was typed, and both survive process death.
 */
class TimeSettingsDraftTest {
    private val today = LocalDate.parse("2026-10-14")
    private val piano = testMadeTime()

    private fun TimeSettingsDraft.restored(): TimeSettingsDraft {
        val saved = requireNotNull(with(TimeSettingsDraft.Saver) { SaverScope { true }.save(this@restored) })
        return requireNotNull(TimeSettingsDraft.Saver.restore(saved))
    }

    /** Every part changes the one draft, and what the page shows is the edits over what is stored, applied on today. */
    @Test fun oneDraftHoldsTheChangesOfAllFourParts() {
        val draft = TimeSettingsDraft(piano, today)
        assertFalse(draft.isDirty)
        assertFalse(draft.canSave)

        assertTrue(draft.rename("Keys"))
        assertTrue(draft.setIcon(ViewIcon.Art))
        assertTrue(draft.setHue(ViewHue.Teal))
        assertTrue(draft.setMotif(MoMotif.Book))
        assertFalse("Mo has no pills for a timer", draft.setMotif(MoMotif.Pills))
        draft.typeGoal(" 90 ")
        assertTrue(draft.setRemind(true))
        assertTrue(draft.setReminderTime(CheckpointTime.Fixed(LocalTime.of(18, 30))))
        assertTrue(draft.takePart(true))
        assertTrue(draft.setBankSize(2))
        assertTrue(draft.setWhenAway(WhenAway.Excused))
        assertTrue(draft.addStep())
        assertTrue(draft.setStepTokens(2, 4))
        assertFalse("A step adds five at most", draft.setStepTokens(2, 6))
        assertFalse("and one at least", draft.setStepTokens(0, 0))

        val edited = draft.edited
        assertEquals("Keys", edited.label)
        assertEquals(ViewIcon.Art to ViewHue.Teal, edited.icon to edited.hue)
        assertEquals(MoMotif.Book, edited.motif)
        // Outside the streak until today, its goal changes plainly.
        assertEquals(Dated.always(90), edited.goal)
        assertEquals(TimeRewards(maxPasses = 2, periods = listOf(RewardPeriod(today)), tokenSteps = steps(100 to 1, 200 to 1, 300 to 4)), edited.rewards)
        assertEquals(WhenAway.Excused, edited.whenAway.on(today))
        assertEquals(WhenAway.Due, edited.whenAway.on(today.minusDays(1)))
        assertEquals(CheckpointTime.Fixed(LocalTime.of(18, 30)), edited.checkpoint?.at)
        assertTrue(edited.checkpoint?.remind == true && draft.canSave)
        assertEquals(piano.applyingEdits(draft.edits, today), edited)

        // Stored meanwhile, the edits are shown over what is stored now; once stored, nothing is left to save.
        draft.follow(piano.copy(label = "Stored meanwhile"), today)
        assertEquals("Keys", draft.edited.label)
        draft.follow(edited, today)
        assertFalse(draft.isDirty)
        assertFalse(draft.hasUnsavedWork)
    }

    /**
     * The goal field takes whole minutes a week can ask and nothing else. Text that is no such
     * number is kept by the field alone: the draft keeps the last goal that could be stored, holds
     * Save back, and says so until the field holds a goal again.
     */
    @Test fun aGoalThatCannotBeOneHoldsSaveBackAndReplacesNothing() {
        val draft = TimeSettingsDraft(piano, today)
        draft.typeGoal("45")
        assertTrue(draft.canSave)
        assertFalse(draft.goalStored)

        listOf("", " ", "0", "10081", "4.5", "4,5", "-5", "+5", "1e2", "an hour").forEach { refused ->
            draft.typeGoal(refused)
            assertTrue(refused, draft.goalRefused)
            assertTrue(refused, draft.heldBack && draft.hasUnsavedWork)
            assertFalse(refused, draft.canSave)
            assertEquals(refused, 45, draft.edited.goal.current)
        }
        listOf("1" to 1, "10080" to 10080, "007" to 7, " 60 " to 60).forEach { (typed, minutes) ->
            draft.typeGoal(typed)
            assertFalse(typed, draft.goalRefused)
            assertEquals(typed, minutes, draft.edited.goal.current)
        }
        // Back on the stored goal, nothing is left to save and the field shows what is stored again.
        assertFalse(draft.canSave)
        assertFalse(draft.hasUnsavedWork)
        // A goal a view already took part under is dated: earlier weeks keep the one they had.
        val joined = TimeSettingsDraft(piano.copy(rewards = TimeRewards(maxPasses = 3, periods = listOf(RewardPeriod(today.minusDays(9))))), today)
        joined.typeGoal("120")
        assertEquals(Dated(listOf(Dated.Revision(60), Dated.Revision(120, today))), joined.edited.goal)
    }

    /** The draft survives process death with its edits, a blank name, the day, a refused goal and what each step's field holds. */
    @Test fun theDraftSurvivesProcessDeath() {
        val draft = TimeSettingsDraft(piano, today)
        draft.rename("Keys")
        draft.named(piano.id, null)
        draft.takePart(true)
        draft.addStep()
        draft.typeStepPercent(1, "250")
        draft.typeStepPercent(2, "twice")
        draft.setMotif(MoMotif.Book)
        draft.typeGoal("an hour")

        val restored = draft.restored()

        assertEquals(draft.stored, restored.stored)
        assertEquals(draft.edits, restored.edits)
        assertEquals(setOf(piano.id), restored.blankNames)
        assertEquals(today, restored.today)
        assertTrue(restored.goalRefused)
        assertEquals(mapOf(1 to "250", 2 to "twice"), restored.stepTexts)
        assertEquals(setOf(2), restored.refusedSteps)
        assertEquals("twice", restored.stepText(2))
        assertEquals(draft.edited, restored.edited)
        assertFalse(restored.canSave)
    }

    private fun steps(vararg steps: Pair<Int, Int>) = TokenSteps(steps.map { (percent, tokens) -> TokenStep(percent, tokens) })

    /**
     * A further step's percent is typed. A whole number above the step before and below the step
     * after, within 101 to 1000, becomes the step's; anything else stays in its field, refused,
     * holds Save back and keeps a step from being added. A refused text is taken as soon as its
     * neighbors make room for it. A step's tokens are its own, one to five.
     */
    @Test fun aStepsPercentIsTypedBetweenItsNeighborsAndRefusedTextHoldsSaveBack() {
        val draft = TimeSettingsDraft(piano.copy(rewards = TimeRewards(maxPasses = 3, periods = listOf(RewardPeriod(today.minusDays(9))))), today)
        assertEquals(DefaultTokenSteps, draft.steps)
        assertEquals("200", draft.stepText(1))
        assertTrue(draft.canAddStep)

        draft.addStep()
        assertEquals(steps(100 to 1, 200 to 1, 300 to 1), draft.steps)
        assertEquals("300", draft.stepText(2))
        draft.typeStepPercent(2, " 250 ")
        assertEquals(steps(100 to 1, 200 to 1, 250 to 1), draft.steps)
        assertEquals(" 250 ", draft.stepText(2))
        assertFalse(draft.stepRefused(2))

        // Its own percent again is no change; the step before's, the step after's and anything outside 101 to 1000 is refused.
        draft.typeStepPercent(1, "200")
        assertFalse(draft.stepRefused(1))
        listOf("", "250", "100", "50", "1001", "2.5", "-300", "twice", "+300").forEach { refused ->
            draft.typeStepPercent(1, refused)
            assertTrue(refused, draft.stepRefused(1))
            assertEquals(refused, refused, draft.stepText(1))
            assertEquals(refused, steps(100 to 1, 200 to 1, 250 to 1), draft.steps)
            assertTrue(refused, draft.stepsRefused && draft.heldBack && draft.hasUnsavedWork)
            assertFalse(refused, draft.canSave)
            assertFalse(refused, draft.canAddStep)
            assertFalse(refused, draft.addStep())
        }
        // Too high for its place while the third step stands at 250: taken the moment that step moves up.
        draft.typeStepPercent(1, "260")
        assertTrue(draft.stepRefused(1))
        draft.typeStepPercent(2, "300")
        assertFalse(draft.stepRefused(1))
        assertEquals(steps(100 to 1, 260 to 1, 300 to 1), draft.steps)
        assertTrue(draft.canSave)
        // The tokens of a step are its own: a later step may add fewer than the one before it.
        assertTrue(draft.setStepTokens(2, 5))
        assertTrue(draft.setStepTokens(1, 4))
        assertTrue(draft.setStepTokens(0, 5))
        assertTrue(draft.setStepTokens(2, 2))
        assertEquals(steps(100 to 5, 260 to 4, 300 to 2), draft.steps)
        assertTrue("five tokens are no bar to one more step", draft.canAddStep)
        assertTrue(draft.addStep())
        assertEquals(steps(100 to 5, 260 to 4, 300 to 2, 400 to 1), draft.steps)
        assertTrue(draft.removeStep(3))

        // Removed, the step below moves up with what its field holds, and a refused text above may be taken.
        draft.typeStepPercent(1, "350")
        draft.typeStepPercent(2, "1200")
        assertEquals(setOf(1, 2), draft.refusedSteps)
        assertTrue(draft.removeStep(1))
        assertEquals(steps(100 to 5, 300 to 2), draft.steps)
        assertEquals("1200", draft.stepText(1))
        assertEquals(setOf(1), draft.refusedSteps)
        assertFalse(draft.removeStep(0))
        draft.typeStepPercent(1, "350")
        assertEquals(steps(100 to 5, 350 to 2), draft.steps)
        // A day before today took part, so the steps are dated from today.
        assertEquals(Dated(listOf(Dated.Revision(DefaultTokenSteps), Dated.Revision(steps(100 to 5, 350 to 2), today))), draft.edited.rewards!!.tokenSteps)

        // Out of the streak, the rows are out of sight with whatever their fields held, so nothing hidden holds Save back.
        draft.typeStepPercent(1, "none")
        assertFalse(draft.canSave)
        assertTrue(draft.takePart(false))
        assertEquals(emptyMap<Int, String>(), draft.stepTexts)
        assertFalse(draft.stepsRefused)
        assertTrue(draft.canSave)
        assertTrue(draft.takePart(true))
        assertEquals("350", draft.stepText(1))

        // Saved, the fields show the steps as stored.
        draft.typeStepPercent(1, "0400")
        assertEquals("0400", draft.stepText(1))
        val stored = draft.edited
        draft.saved()
        draft.follow(stored, today)
        assertEquals("400", draft.stepText(1))
        assertFalse(draft.hasUnsavedWork)
    }

    /**
     * Reading's configuration is drafted by the same draft: its name, its look, its goal, its
     * bank's size, its tokens a week and its holiday rule change, each dated where it judges
     * weeks. What is not Reading's to change is refused and leaves no edit: it always counts, and
     * its reminder is its own.
     */
    @Test fun readingsConfigurationIsDraftedToo() {
        val reading = testReadingTime()
        val draft = TimeSettingsDraft(reading, today)

        assertTrue(draft.rename("Books"))
        assertTrue(draft.setHue(ViewHue.Teal))
        assertTrue(draft.setMotif(null))
        draft.typeGoal("90")
        assertTrue(draft.setBankSize(4))
        assertTrue(draft.removeStep(1))
        assertTrue(draft.setWhenAway(WhenAway.Excused))
        // Reading leaves and joins the streak as any view does; a draft knows no first record, so leaving leaves no period on it.
        assertTrue(draft.takePart(false))
        assertEquals(emptyList<RewardPeriod>(), draft.edited.rewards?.periods)
        assertTrue(draft.takePart(true))
        // Its reminder is its checkpoint, as a made view's is.
        assertTrue(draft.setRemind(true))
        assertTrue(draft.setReminderTime(CheckpointTime.AfterWake(20)))

        val edited = draft.edited
        assertEquals("Books", edited.label)
        assertNull(edited.motif)
        assertEquals(Dated(listOf(Dated.Revision(60), Dated.Revision(90, today))), edited.goal)
        assertEquals(
            TimeRewards(maxPasses = Dated(listOf(Dated.Revision(3), Dated.Revision(4, today))), periods = listOf(RewardPeriod(today)), tokenSteps = Dated(listOf(Dated.Revision(DefaultTokenSteps), Dated.Revision(steps(100 to 1), today)))),
            edited.rewards,
        )
        assertEquals(WhenAway.Excused, edited.whenAway.on(today))
        assertEquals(defaultReadingCheckpoint(remind = true).copy(at = CheckpointTime.AfterWake(20)), edited.checkpoint)
        assertEquals(11, draft.edits.size)
        assertEquals(edited, draft.restored().edited)
    }

    private val records = AppState().withReading().withActivity()

    private fun setup() = TimeSetupState(records.starterTime("Time"), today)

    private fun TimeSetupState.restored(): TimeSetupState {
        val saved = requireNotNull(with(TimeSetupState.Saver) { SaverScope { true }.save(this@restored) })
        return requireNotNull(TimeSetupState.Saver.restore(saved))
    }

    /**
     * The setup walks the four parts. Look waits for a name and Goal for a goal that can be one;
     * Create waits for both, hands over the starter with the edits made, and creates once.
     */
    @Test fun theSetupWaitsForANameAndAGoalAndCreatesOnce() {
        val state = setup()
        assertEquals(listOf(ChecklistPage.Look, ChecklistPage.Items, ChecklistPage.Reminders, ChecklistPage.Streak), state.steps.parts)
        assertFalse(state.canContinue)
        assertFalse(state.canCreate)
        assertFalse(state.touched)
        assertEquals(ViewWrite.NotStored, state.create { _, _ -> error("Nothing is created without a name.") })

        state.rename("Piano")
        assertTrue(state.canContinue && state.touched)
        state.next()
        assertEquals(ChecklistPage.Items, state.steps.step)
        state.form.typeGoal("lots")
        assertFalse(state.canContinue)
        assertFalse(state.canCreate)
        state.next()
        assertEquals(ChecklistPage.Items, state.steps.step)
        state.form.typeGoal("90")
        state.next()
        state.form.setRemind(true)
        state.next()
        state.form.takePart(true)
        assertTrue(state.canCreate)
        // A step's percent that cannot be one holds Create, as a goal that cannot be one does.
        state.form.typeStepPercent(1, "half")
        assertFalse(state.canContinue)
        assertFalse(state.canCreate)
        state.form.typeStepPercent(1, "150")
        assertTrue(state.canCreate)

        var created = 0
        val store = { starter: TimeConfiguration, edits: List<TimeEdit> ->
            created += 1
            val made = records.addTime(starter).applyingTimeEdits(starter.id, edits, today).madeTime.single()
            assertEquals(state.starter.id, made.id)
            assertEquals("Piano", made.label)
            assertEquals(Dated.always(90), made.goal)
            assertEquals(listOf(RewardPeriod(today)), made.rewards?.periods)
            assertTrue(made.checkpoint?.remind == true)
            ViewWrite.Stored
        }
        assertEquals(ViewWrite.Stored, state.create(store))
        assertEquals(ViewWrite.Stored, state.create(store))
        assertEquals(1, created)
        assertFalse(state.touched)
    }

    /** A setup under way survives process death with the ID its view will have, its step, its name and a goal that was refused. */
    @Test fun aSetupUnderWaySurvivesProcessDeath() {
        val state = setup()
        state.rename("Piano")
        state.next()
        state.form.typeGoal("lots")

        val restored = state.restored()

        assertEquals(state.starter, restored.starter)
        assertEquals("Piano", restored.name)
        assertTrue(restored.nameTouched)
        assertEquals(ChecklistPage.Items, restored.steps.step)
        assertTrue(restored.form.goalRefused)
        assertFalse(restored.canCreate)
        restored.form.typeGoal("45")
        assertTrue(restored.canCreate)
        assertEquals(45, restored.form.edited.goal.current)
    }
}

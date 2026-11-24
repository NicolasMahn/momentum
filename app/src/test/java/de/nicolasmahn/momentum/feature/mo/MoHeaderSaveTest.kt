package de.nicolasmahn.momentum.feature.mo

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import de.nicolasmahn.momentum.core.ui.theme.WorkoutTrackerTheme
import de.nicolasmahn.momentum.domain.TrackerRef
import de.nicolasmahn.momentum.domain.checklist.MoMotif
import de.nicolasmahn.momentum.domain.compatibility.MoNeed
import de.nicolasmahn.momentum.domain.mo.MoContentKey
import de.nicolasmahn.momentum.domain.mo.MoMood
import de.nicolasmahn.momentum.domain.mo.MoNeglect
import de.nicolasmahn.momentum.domain.mo.MoProp
import de.nicolasmahn.momentum.domain.mo.MoSaveAct
import de.nicolasmahn.momentum.domain.mo.MoState
import de.nicolasmahn.momentum.domain.mo.MoStreak
import de.nicolasmahn.momentum.domain.mo.MoViewKind
import de.nicolasmahn.momentum.domain.trackerRef
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * What Mo on Home does about a save, followed on a held clock. With animations on, a save is
 * taken in 450 ms after it arrives, the hop it starts is over within about a second, and he acts
 * it out for nine seconds from when it shows. Each test is one thing that happens in the middle
 * of that.
 */
@RunWith(RobolectricTestRunner::class)
class MoHeaderSaveTest {
    @get:Rule
    val compose = createComposeRule()

    private var mood by mutableStateOf(mood(MoState.Steady))
    private var pending by mutableStateOf<TrackerRef?>(null)
    private var atHome by mutableStateOf(true)
    private var taken = 0
    private lateinit var reaction: MoSaveReaction

    @Test
    fun aSaveIsTakenAsItShowsAndIsActedOutForNineSeconds() {
        pending = weight
        start()

        after(300)
        assertEquals("Home is still sliding in", 0, taken)
        assertNull(reaction.playing)

        after(200)
        assertEquals(1, taken)
        assertEquals(MoSaveAct.Weighing, reaction.playing?.activity)
        assertTrue("an act for a save keeps the floor still", reaction.hopsAlone)

        after(8_800)
        assertNotNull("still within its nine seconds", reaction.playing)
        after(300)
        assertNull(reaction.playing)
        assertEquals(1, taken)
    }

    @Test
    fun aHomeLeftInTheMiddleOfTheHopDoesNotFindTheSaveWaiting() {
        pending = weight
        start()
        after(560)
        assertTrue("he is in the air", reaction.hop.value > 0f)

        leaveAndReturn()
        after(3_000)

        assertEquals(1, taken)
        assertNull("the save was shown once already", reaction.playing)
        assertEquals(0, reaction.reactions)
    }

    @Test
    fun aHomeLeftBeforeTheSaveShowedPlaysItOnReturn() {
        pending = weight
        start()
        after(200)

        leaveAndReturn()
        assertEquals(0, taken)
        after(600)

        assertEquals(1, taken)
        assertEquals(MoSaveAct.Weighing, reaction.playing?.activity)
    }

    @Test
    fun aHopComesDownWhateverBecomesOfTheSave() {
        pending = weight
        start()
        after(560)
        assertTrue(reaction.hop.value > 0f)

        // The day rolls over and takes the list of saves with it; then he goes cold as well.
        pending = null
        mood = mood(MoState.Cold)
        after(3_000)

        assertEquals(0f, reaction.hop.value)
        assertNull(reaction.playing)
    }

    @Test
    fun aBurstOfFireBurnsOutWhenAnotherSaveCutsItShort() {
        pending = activity
        start()
        after(700)
        assertTrue("the fire is out and about", reaction.burst.value < 1f)

        pending = weight
        after(4_000)

        assertEquals(1f, reaction.burst.value)
        assertEquals(0f, reaction.hop.value)
        assertEquals(MoSaveAct.Weighing, reaction.playing?.activity)
    }

    @Test
    fun theFloorKeepsStillWhenHeStopsActingItOutInTheAir() {
        pending = care
        start()
        after(560)
        assertEquals(MoSaveAct.Brushing, reaction.playing?.activity)
        assertTrue(reaction.hop.value > 0f)

        // A sore mouth, halfway up: his day is back at once, and he lands on the floor he left.
        mood = mood(MoState.Steady, sore = true)
        after(48)
        assertNull(reaction.playing)
        assertTrue("still in the air", reaction.hop.value > 0f)
        assertTrue("and still the only thing off the floor", reaction.hopsAlone)

        after(3_000)
        assertEquals(0f, reaction.hop.value)
    }

    @Test
    fun aSaveHeStoppedActingOutDoesNotBeginAgain() {
        pending = weight
        start()
        after(1_500)
        assertNotNull(reaction.playing)

        mood = mood(MoState.Cold)
        after(100)
        assertNull(reaction.playing)

        mood = mood(MoState.Steady)
        after(3_000)
        assertNull(reaction.playing)
        assertEquals(1, reaction.reactions)
    }

    @Test
    fun aSaveThatArrivesWhileHeIsColdIsNotKeptForLater() {
        mood = mood(MoState.Cold)
        pending = weight
        start()
        after(600)

        assertEquals("it is taken all the same", 1, taken)
        assertNull(reaction.playing)
        assertEquals("and he hops for it", 1, reaction.reactions)
        assertEquals("with his day around him, as one picture", false, reaction.hopsAlone)

        mood = mood(MoState.Steady)
        after(3_000)
        assertNull(reaction.playing)
    }

    @Test
    fun aSecondSaveOfTheSameTaskStartsItsOwnNineSeconds() {
        pending = weight
        start()
        after(3_000)
        val first = reaction.playing

        pending = weight
        after(500)
        assertEquals(2, taken)
        assertNotSame(first, reaction.playing)

        after(8_800)
        assertNotNull("nine seconds from the second save, not from the first", reaction.playing)
        after(300)
        assertNull(reaction.playing)
    }

    @Test
    fun aSaveHeDoesNotActOutLeavesAnEarlierOnePlaying() {
        mood = mood(MoState.Steady, sore = true)
        pending = weight
        start()
        after(1_500)
        val weighing = reaction.playing

        pending = care
        after(500)

        assertEquals(2, taken)
        assertSame("a sore mouth does not brush, and the scale stays", weighing, reaction.playing)
        assertEquals("he hops for it all the same", 2, reaction.reactions)
        assertTrue(reaction.hopsAlone)
    }

    @Test
    fun aSaveTakenInWithAnimationsOffBringsHimToRestAtOnce() {
        var animated = true
        val reaction = driven { animated }
        take { reaction.accept(MoSaveAct.Running, 0, burning) }
        after(300)
        take { reaction.accept(MoSaveAct.Weighing, 0, burning) }
        after(120)
        assertTrue("in the air", reaction.hop.value > 0f)
        assertTrue("with the fire still out", reaction.burst.value < 1f)

        // Animations are switched off, and the next save arrives before he has landed.
        animated = false
        var height = reaction.hop.value
        take { reaction.accept(MoSaveAct.Reading, 0, burning) }
        repeat(2) {
            assertTrue("he moved from $height to ${reaction.hop.value} with animations off", abs(reaction.hop.value) <= abs(height))
            height = reaction.hop.value
            compose.mainClock.advanceTimeByFrame()
        }

        assertEquals(0f, reaction.hop.value)
        assertEquals(1f, reaction.burst.value)
        assertEquals(MoSaveAct.Reading, reaction.playing?.activity)
        after(12_000)
        assertEquals(0f, reaction.hop.value)
        assertEquals(1f, reaction.burst.value)
    }

    @Test
    fun theWayAHopLiftsHimOnlyChangesWhileHeIsOnTheFloor() {
        val reaction = driven { true }
        // A sore mouth does not brush: his day's act hops with him, as one picture.
        take { reaction.accept(MoSaveAct.Brushing, 0, sore) }
        after(120)
        assertEquals(false, reaction.hopsAlone)
        assertTrue(reaction.hop.value > 0f)

        // The next save he does act out, and that hop lifts him alone. He is still in the air.
        take { reaction.accept(MoSaveAct.Weighing, 0, sore) }
        var alone = reaction.hopsAlone
        var changes = 0
        var hopped = false
        repeat(250) {
            compose.mainClock.advanceTimeByFrame()
            if (reaction.hopsAlone != alone) {
                alone = reaction.hopsAlone
                changes++
                assertEquals("how high he was when the floor changed hands", 0f, reaction.hop.value, 0.001f)
            } else if (changes == 1 && reaction.hop.value > 0.5f) {
                hopped = true
            }
        }

        assertEquals(1, changes)
        assertTrue(reaction.hopsAlone)
        assertTrue("the second save got a hop of its own", hopped)
        assertEquals(0f, reaction.hop.value)
    }

    @Test
    fun aSaveThatReachesHimWithHisWholePictureInTheAirShowsOnceHeStands() {
        // A sore mouth does not brush for show, so for his teeth his day hops with him as one
        // picture. The weight comes in before that picture is back on the floor.
        mood = mood(MoState.Steady, sore = true)
        pending = care
        start()
        after(500)
        assertEquals(1, taken)
        assertNull(reaction.playing)
        assertEquals(false, reaction.hopsAlone)

        pending = weight
        var comingDown = 0
        var frames = 0
        while (reaction.playing == null && frames++ < 120) {
            compose.mainClock.advanceTimeByFrame()
            if (taken == 2 && reaction.playing == null) {
                comingDown++
                assertEquals("lifted alone before the scale is there", false, reaction.hopsAlone)
            }
        }

        // The scale stands on the floor. Shown in a picture that is in the air, it would come
        // down with him.
        assertEquals(MoSaveAct.Weighing, reaction.playing?.activity)
        assertTrue("the save came in while he was in the air", comingDown > 0)
        assertEquals("how high he was as the scale appeared", 0f, reaction.hop.value)
        assertTrue("from here on only he leaves the floor", reaction.hopsAlone)

        after(8_800)
        assertNotNull("nine seconds from when it showed", reaction.playing)
        after(300)
        assertNull(reaction.playing)
    }

    @Test
    fun aWorkoutAfterAHopBurstsIntoFireAndBringsHimDown() {
        val reaction = driven { true }
        take { reaction.accept(MoSaveAct.Weighing, 0, burning) }
        after(120)
        assertTrue(reaction.hop.value > 0f)

        take { reaction.accept(MoSaveAct.Running, 0, burning) }
        after(300)
        assertTrue("the fire is out and about", reaction.burst.value > 0f && reaction.burst.value < 1f)
        after(4_000)

        assertEquals(0f, reaction.hop.value)
        assertEquals(1f, reaction.burst.value)
        assertEquals(MoSaveAct.Running, reaction.playing?.activity)
    }

    @Test
    fun aFireStillOutKeepsBurningWhileHeTouchesDownForTheNextSave() {
        val reaction = driven { true }
        // Three things were overdue. In his tantrum he acts nothing out: the workout sets him on
        // fire, and for the teeth he hops with his day around him.
        take { reaction.accept(MoSaveAct.Running, 0, fuming) }
        after(200)
        take { reaction.accept(MoSaveAct.Brushing, 0, fuming) }
        after(120)
        assertTrue("in the air", reaction.hop.value > 0f)
        assertEquals(false, reaction.hopsAlone)

        // The third save ends the tantrum and lifts him alone, so he touches down first. The
        // fire must not wait for that.
        take { reaction.accept(MoSaveAct.Weighing, 0, burning) }
        var height = reaction.hop.value
        var fire = reaction.burst.value
        var waiting = 0
        var frames = 0
        while (!reaction.hopsAlone && frames < 60) {
            compose.mainClock.advanceTimeByFrame()
            if (reaction.hop.value < height) {
                frames++
                assertTrue("the fire stood still in frame $frames of his touching down", reaction.burst.value > fire)
            } else {
                // Taking over costs every motion a frame or two before it moves again.
                assertTrue("he never started down", ++waiting <= 4)
            }
            height = reaction.hop.value
            fire = reaction.burst.value
        }
        assertTrue("he did touch down for it, over $frames frames", frames in 2..59)
        assertTrue("and the fire was still out when he had", fire < 1f)
    }

    /** A reaction by itself, moved as the header moves it. */
    private fun driven(animated: () -> Boolean): MoSaveReaction {
        val reaction = MoSaveReaction()
        compose.mainClock.autoAdvance = false
        compose.setContent { LaunchedEffect(reaction) { reaction.drive(animated) } }
        return reaction
    }

    /** With nothing on screen nothing asks for a frame, so a change has to be handed over. */
    private fun take(change: () -> Unit) {
        compose.runOnIdle(change)
        compose.waitForIdle()
    }

    private val burning = MoLook(MoState.Steady)
    private val fuming = MoLook(MoState.Steady, angry = true)
    private val sore = MoLook(MoState.Steady, neglect = listOf(MoNeglectedDrawing(MoProp.LostTeeth, MoNeglect.Mild)))

    private fun start() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            WorkoutTrackerTheme {
                if (atHome) {
                    // A Home entered again has a reaction of its own, as it has in the app.
                    val entered = remember { MoSaveReaction() }.also { reaction = it }
                    MoHeader(
                        mood = mood,
                        moHeight = 300.dp,
                        celebrate = pending,
                        // As the app does it: the list of saves waiting is emptied.
                        onCelebrated = {
                            taken++
                            pending = null
                        },
                        reaction = entered,
                    )
                }
            }
        }
    }

    private fun after(millis: Long) = compose.mainClock.advanceTimeBy(millis)

    private fun leaveAndReturn() {
        val left = reaction
        atHome = false
        // With nothing on screen nothing asks for a frame, so the change has to be handed over.
        compose.waitForIdle()
        after(100)
        atHome = true
        compose.waitForIdle()
        compose.mainClock.advanceTimeByFrame()
        assertNotSame("Home was entered anew", left, reaction)
    }

}

/**
 * The same header on a phone that slows its animations to a fifth of their speed. His motions
 * stretch and the header's own waits do not, so a save meets him where it cannot at the usual
 * speed, and a wait that is over in a frame there lasts long enough to be told apart here.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
class MoHeaderSlowedSaveTest {
    @get:Rule
    val compose = createComposeRule(effectContext = object : MotionDurationScale {
        override val scaleFactor = 5f
    })

    private var pending by mutableStateOf<TrackerRef?>(null)
    private var taken = 0
    private val reaction = MoSaveReaction()

    @Test
    fun theNineSecondsCountFromWhenASaveShowsAndNotFromWhenItCameIn() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            WorkoutTrackerTheme {
                MoHeader(
                    mood = mood(MoState.Steady, sore = true),
                    moHeight = 300.dp,
                    celebrate = pending,
                    onCelebrated = {
                        taken++
                        pending = null
                    },
                    reaction = reaction,
                )
            }
        }
        // A sore mouth does not brush for show: his day hops with him, and slowly.
        pending = care
        compose.mainClock.advanceTimeBy(500)
        assertEquals(1, taken)

        pending = weight
        framesUntil("the weight was never taken in") { taken == 2 }
        val cameIn = compose.mainClock.currentTime
        assertTrue("in the air", reaction.hop.value > 0.3f)
        assertNull("acted out with his picture in the air", reaction.playing)
        framesUntil("he never stood") { reaction.playing != null }
        val shown = compose.mainClock.currentTime
        assertTrue("he took ${shown - cameIn} ms to come down", shown - cameIn >= 150)
        assertEquals("how high he was as the scale appeared", 0f, reaction.hop.value)

        compose.mainClock.advanceTimeBy(cameIn + 9_050 - compose.mainClock.currentTime)
        assertNotNull("over nine seconds after it came in", reaction.playing)
        compose.mainClock.advanceTimeBy(shown + 9_050 - compose.mainClock.currentTime)
        assertNull("still there nine seconds after it showed", reaction.playing)
    }

    private fun framesUntil(otherwise: String, reached: () -> Boolean) {
        var frames = 0
        while (!reached()) {
            assertTrue(otherwise, frames++ < 300)
            compose.mainClock.advanceTimeByFrame()
        }
    }
}

private val weight = MoNeed.Weight.view.trackerRef
private val care = MoNeed.Care.view.trackerRef
private val activity = MoNeed.Activity.view.trackerRef

private fun mood(state: MoState, sore: Boolean = false) = MoMood(
    state = state,
    dueToday = 3,
    daysCold = 0,
    streak = MoStreak(days = 5, freezes = 1, freezeCapacity = 3, week = emptyList(), weekCanEarnFreeze = false),
    trackerContent = mapOf(
        weight to MoContentKey(MoViewKind.Checklist, MoMotif.Scale),
        care to MoContentKey(MoViewKind.Checklist, MoMotif.Teeth),
        activity to MoContentKey(MoViewKind.Activity),
    ),
    // Neglected teeth stay with him however today goes, and keep him from brushing for show.
    trackerNeglect = if (sore) mapOf(care to MoNeglect.Mild) else emptyMap(),
)

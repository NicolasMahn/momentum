package de.nicolasmahn.momentum.feature.mo

import android.os.Looper
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import de.nicolasmahn.momentum.domain.mo.MoNeglect
import de.nicolasmahn.momentum.domain.mo.MoProp
import de.nicolasmahn.momentum.domain.mo.MoSaveAct
import de.nicolasmahn.momentum.domain.mo.MoState
import java.time.Duration
import kotlin.math.abs
import kotlin.random.Random
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowChoreographer

/**
 * His reaction to a save on an attached window's dispatcher and frame clock.
 *
 * [MoHeaderSaveTest] walks through the same reaction on the Compose test rule, which starts a
 * launched coroutine at once. A window queues it, as a phone does, so two motions that both want
 * the fire or the hop meet in another order here. The reaction is driven directly: the header
 * around it and what it draws belong to [MoHeaderSaveTest] and to the films.
 *
 * Every frame of every case is held to the laws of his motion, see [Home]. A case adds only the
 * order of events it is named for.
 */
@RunWith(RobolectricTestRunner::class)
@LooperMode(LooperMode.Mode.PAUSED)
class MoSaveReactionWindowTest {
    private val calm = MoLook(MoState.Steady)
    private val fuming = MoLook(MoState.Steady, angry = true)
    private val sore = MoLook(MoState.Steady, neglect = listOf(MoNeglectedDrawing(MoProp.LostTeeth, MoNeglect.Mild)))
    private val open = mutableListOf<Home>()

    @Before
    fun frameClock() {
        ShadowChoreographer.setPaused(true)
        ShadowChoreographer.setFrameDelay(Duration.ofMillis(FRAME_MILLIS))
    }

    @After
    fun leave() = open.toList().forEach { it.leave() }

    @Test
    fun aWorkoutBurstsIntoFireAndBurnsOut() {
        val home = Home()
        home.save(MoSaveAct.Running, calm)
        home.frames(A_SECOND)
        assertTrue("still on fire a second in", home.onFire)

        home.rest()
        assertEquals("fires lit", 1, home.fires)
        home.assertAtRest()
    }

    @Test
    fun aWorkoutInTheMiddleOfAHopBurstsIntoFireAndBringsHimDown() {
        val home = Home()
        home.save(MoSaveAct.Weighing, calm)
        home.frames(8)
        assertTrue("in the air", home.inTheAir)
        val height = home.reaction.hop.value

        home.save(MoSaveAct.Running, calm)
        home.frames(6)
        assertTrue("on fire", home.onFire)
        assertTrue("and lower than the fire caught him", home.reaction.hop.value < height)

        home.rest()
        assertEquals("fires lit", 1, home.fires)
        home.assertAtRest()
    }

    @Test
    fun aSecondWorkoutTakesTheFireOverFromTheFirst() {
        val home = Home()
        home.save(MoSaveAct.Running, calm)
        home.frames(20)
        assertTrue("the first fire is out and about", home.onFire)

        home.save(MoSaveAct.Running, calm)
        home.frames(15)
        assertEquals("fires lit", 2, home.fires)
        assertTrue("the second burns", home.onFire)

        home.rest()
        assertEquals("fires lit", 2, home.fires)
        home.assertAtRest()
    }

    @Test
    fun aHopLeavesAnEarlierFireToBurnOut() {
        val home = Home()
        home.save(MoSaveAct.Running, calm)
        home.frames(20)
        assertTrue("the fire is out and about", home.onFire)

        home.save(MoSaveAct.Weighing, calm)
        home.framesToTheTop()
        assertEquals("times at the top of a hop", 1, home.tops)
        assertTrue("with the fire burning on", home.onFire)

        home.rest()
        assertEquals("fires lit", 1, home.fires)
        home.assertAtRest()
    }

    @Test
    fun aWorkoutThatHasHimTouchDownFirstStillBurstsIntoFire() {
        val home = Home()
        // In his tantrum he acts nothing out and hops with his day around him. The workout that
        // ends the tantrum lifts him alone, so he touches down before it sets him on fire.
        home.save(MoSaveAct.Brushing, fuming)
        home.frames(8)
        assertTrue("in the air", home.inTheAir)
        assertFalse("lifted alone", home.reaction.hopsAlone)

        home.save(MoSaveAct.Running, calm)
        home.rest() {
            if (!home.reaction.hopsAlone) assertEquals("fires lit before he stands", 0, home.fires)
        }

        assertTrue("lifted alone", home.reaction.hopsAlone)
        assertEquals("fires lit", 1, home.fires)
        home.assertAtRest()
    }

    @Test
    fun aFireStillOutBurnsOnWhileHeTouchesDownForTheNextWorkout() {
        val home = Home()
        home.save(MoSaveAct.Running, calm)
        home.frames(10)
        // He loses his temper, so the next save has him hop with his day around him.
        home.reaction.keepWhile(fuming)
        home.save(MoSaveAct.Brushing, fuming)
        home.frames(8)
        assertTrue("in the air", home.inTheAir)
        assertTrue("with the first fire still out", home.onFire)
        assertFalse("lifted alone", home.reaction.hopsAlone)

        home.save(MoSaveAct.Running, calm)
        home.frames(14) {
            if (!home.reaction.hopsAlone) {
                assertEquals("fires lit before he stands", 1, home.fires)
                assertTrue("the first fire went out before he stands", home.onFire)
            }
        }

        assertTrue("lifted alone", home.reaction.hopsAlone)
        assertEquals("fires lit", 2, home.fires)
        assertTrue("the second burns", home.onFire)

        home.rest()
        home.assertAtRest()
    }

    @Test
    fun theSameSaveAgainLiftsHimAgain() {
        val home = Home()
        repeat(2) { time ->
            home.save(MoSaveAct.Weighing, calm)
            home.rest()
            assertEquals("times at the top of a hop", time + 1, home.tops)
            home.assertAtRest()
        }
    }

    @Test
    fun aSaveThatCatchesHimAtTheTopBringsHimStraightDown() {
        val home = Home()
        home.save(MoSaveAct.Weighing, calm)
        home.framesToTheTop()

        // The header's own wait keeps saves further apart than a rise takes. A phone that slows
        // its animations down stretches the rise and not the wait, and then one arrives here.
        home.save(MoSaveAct.Weighing, calm)
        home.restWithoutComingUpAgain { frame ->
            if (frame == 4) assertTrue("on his way down", home.reaction.hop.value < 1f)
        }
        assertEquals("times at the top of a hop", 1, home.tops)
        home.assertAtRest()
    }

    @Test
    fun slowedDownASaveJustUnderTheTopStillBringsHimDownAtOnce() {
        // Slowed down, a rise lasts long enough for the header to hand a save over anywhere in it,
        // and a wait that takes a frame at the usual speed takes long enough to be seen.
        val home = Home(slowed = 5f)
        home.save(MoSaveAct.Weighing, calm)
        home.framesUntil("he never got near the top") { home.reaction.hop.value >= HOP_TOP }
        val caught = home.reaction.hop.value
        assertTrue("just under the top", caught < 1f)

        home.save(MoSaveAct.Weighing, calm)
        home.restWithoutComingUpAgain { frame ->
            if (frame == A_QUARTER_SECOND) assertTrue("lower than the save caught him", home.reaction.hop.value < caught)
        }
        home.assertAtRest()
    }

    @Test
    fun aSaveHeActsOutShowsOnlyOnceHisWholePictureHasLanded() {
        val home = Home()
        // In his tantrum he acts nothing out: his day hops with him, as one picture.
        home.save(MoSaveAct.Brushing, fuming)
        home.frames(8)
        assertTrue("in the air", home.inTheAir)
        assertFalse("lifted alone", home.reaction.hopsAlone)

        // What the next save shows stands on the floor, and the floor is in the air with him.
        home.save(MoSaveAct.Weighing, calm)
        assertNull("acted out with his picture in the air", home.reaction.playing)
        home.framesUntil("he never stood") { home.reaction.hopsAlone }
        assertEquals(MoSaveAct.Weighing, home.reaction.playing?.activity)

        home.rest()
        assertEquals("times at the top of a hop", 1, home.tops)
        home.assertAtRest()
    }

    @Test
    fun theLaterOfTwoSavesThatWaitForHimToStandIsTheOneHeActsOut() {
        val home = Home()
        home.save(MoSaveAct.Brushing, fuming)
        home.frames(8)
        assertTrue("in the air", home.inTheAir)

        home.save(MoSaveAct.Weighing, calm)
        home.frames(2)
        assertNull("acted out with his picture in the air", home.reaction.playing)
        home.save(MoSaveAct.Reading, calm)
        home.framesUntil("he never stood") { home.reaction.hopsAlone }
        assertEquals(MoSaveAct.Reading, home.reaction.playing?.activity)

        home.rest()
        home.assertAtRest()
    }

    @Test
    fun aSaveHeCanNoLongerActOutStillGetsItsHopOnceHeHasTouchedDown() {
        val home = Home()
        home.save(MoSaveAct.Brushing, fuming)
        home.frames(8)
        home.save(MoSaveAct.Weighing, calm)
        home.frames(2)
        assertTrue("in the air", home.inTheAir)

        // His temper is back before he stands. The touchdown is under way and he finishes it,
        // then hops for the save with his day around him.
        home.reaction.keepWhile(fuming)
        home.rest { assertNull("acted out after all", home.reaction.playing) }

        assertFalse("lifted alone", home.reaction.hopsAlone)
        assertEquals("times at the top of a hop", 1, home.tops)
        home.assertAtRest()
    }

    @Test
    fun aSaveHeDoesNotActOutLeavesTheOneThatWaitsForHimToStand() {
        val home = Home()
        home.save(MoSaveAct.Brushing, sore)
        home.frames(8)
        home.save(MoSaveAct.Weighing, sore)
        home.frames(2)
        assertTrue("in the air", home.inTheAir)
        assertNull("acted out with his picture in the air", home.reaction.playing)

        // A sore mouth does not brush for show. The scale goes on waiting for him to stand.
        home.save(MoSaveAct.Brushing, sore)
        home.framesUntil("he never stood") { home.reaction.hopsAlone }
        assertEquals(MoSaveAct.Weighing, home.reaction.playing?.activity)

        home.rest()
        assertEquals("times at the top of a hop", 1, home.tops)
        home.assertAtRest()
    }

    @Test
    fun slowedDownASaveAtTheEndOfATouchdownDoesNotStartItOver() {
        val home = Home(slowed = 5f)
        home.save(MoSaveAct.Brushing, fuming)
        home.framesUntil("he never left the floor") { home.reaction.hop.value > 0.5f }
        home.save(MoSaveAct.Weighing, calm)
        home.framesUntil("he never came near the floor") { home.reaction.hop.value < 0.05f }
        assertFalse("lifted alone", home.reaction.hopsAlone)

        // A hair above the floor, with the last of his touchdown still to come.
        home.save(MoSaveAct.Running, calm)
        home.frames(A_QUARTER_SECOND)
        assertTrue("lifted alone", home.reaction.hopsAlone)
        assertEquals(MoSaveAct.Running, home.reaction.playing?.activity)

        home.rest()
        assertEquals("fires lit", 1, home.fires)
        home.assertAtRest()
    }

    @Test
    fun animationsSwitchedOffAsHeTouchesDownLeaveHimOnTheFloor() {
        val home = Home()
        home.save(MoSaveAct.Brushing, fuming)
        home.frames(8)
        home.save(MoSaveAct.Weighing, calm)
        home.frames(2)
        assertTrue("in the air", home.inTheAir)

        // The hop he touches down for is still to come. With animations off it would be over in
        // one frame, which would show him at the top once and on the floor again.
        var height = home.reaction.hop.value
        home.switchAnimationsOff()
        repeat(32) { frame ->
            home.frames(1)
            // The phone takes a frame or two to pass the setting on. In those he may stay where
            // he is or come nearer the floor, and never move away from it.
            val now = home.reaction.hop.value
            assertTrue("he moved from $height to $now with animations off", abs(now) <= abs(height))
            height = now
            if (frame >= 2) home.assertAtRest()
        }
        assertEquals(MoSaveAct.Weighing, home.reaction.playing?.activity)
    }

    /**
     * Workouts and every other kind of save, from the same frame to 44 frames apart, his temper
     * coming and going between them. The header waits before it hands a save over, so the closest
     * of these are closer than it brings them at the usual speed. On a phone that slows its
     * animations down, his motions stretch and the wait does not, and saves do meet him anywhere.
     */
    @Test
    fun whateverSavesComeInHeEndsAtRestAndTheFloorNeverMovesUnderHimInTheAir() {
        repeat(RUNS) { run ->
            val random = Random(run)
            val home = Home("run $run: ")
            fun save() {
                val look = if (random.nextInt(3) == 0) fuming else calm
                home.reaction.keepWhile(look)
                home.save(if (random.nextBoolean()) MoSaveAct.Running else MoSaveAct.entries.random(random), look)
            }
            repeat(random.nextInt(2, 6)) {
                save()
                home.frames(random.nextInt(0, 45))
            }
            home.rest()
            home.assertAtRest()

            // From rest nothing else is moving, so what he does now is his answer to this save.
            save()
            home.rest()
            home.assertAnsweredTheLastSave()
            home.assertAtRest()
            home.leave()
        }
    }

    /**
     * A window with nothing in it but his reaction, moved the way the header moves it.
     *
     * It looks at him after every frame and fails on the first one that breaks a law of his
     * motion, whatever the case around it is about:
     * - A fire burns at one even pace to its end. It never jumps ahead and never falls back; it
     *   only starts again, from nothing, and only while the last save is a workout. A save that
     *   takes it over and a new start may each cost it [STILL_FRAMES] frames in all. Nothing
     *   else holds it up.
     * - Off the floor he keeps moving, with the same allowance for each save, and by no more
     *   than half a hop in a frame.
     * - Higher than [BOUNCE] he turns round at most twice for a save. Lower, every turn is at
     *   most half as far from the floor as the one before, the way a landing rings out, and he
     *   never sinks deeper than [BOUNCE] through the floor.
     * - How he is lifted changes only while he stands on the floor, and a save is only acted out
     *   while he stands or is lifted alone.
     * - A save that has had the time is answered before the next: for a workout the fire has
     *   been seen at nothing since, for any other he has been seen within a hundredth of the top.
     *
     * [slowed] is the phone's own setting that stretches every animation. The fire's pace follows
     * it; the frames allowed for a takeover do not, since those are frames and not animations.
     * Once animations are switched off the laws of motion end: he is put at rest, in one step.
     */
    private inner class Home(private val where: String = "", private val slowed: Float = 1f) {
        val reaction = MoSaveReaction()
        private val activity: ActivityController<ComponentActivity> = run {
            Settings.Global.putFloat(RuntimeEnvironment.getApplication().contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, slowed)
            Robolectric.buildActivity(ComponentActivity::class.java).setup()
        }

        val onFire: Boolean get() = reaction.burst.value > 0f && reaction.burst.value < 1f
        val inTheAir: Boolean get() = reaction.hop.value > 0f

        /** How often a fire was seen to start again from nothing. */
        var fires = 0
            private set

        /** How often he was seen to arrive at the top of a hop. */
        var tops = 0
            private set

        private var burst = reaction.burst.value
        private var hop = reaction.hop.value
        private var alone = reaction.hopsAlone
        private var heading = 0
        private var fireStill = 0
        private var hopStill = 0
        private var turns = 0
        private var lastLowTurn = Float.MAX_VALUE
        private var workout = false
        private var answered = true
        private var sinceTheSave = 0
        private var animated = true

        init {
            open += this
            activity.get().setContent { LaunchedEffect(reaction) { reaction.drive { true } } }
            frames(4)
        }

        fun save(activity: MoSaveAct, look: MoLook) {
            if (sinceTheSave >= TO_ANSWER * slowed) assertAnsweredTheLastSave()
            reaction.accept(activity, 0, look)
            workout = activity == MoSaveAct.Running
            // He is at the top already: that is where this save would take him.
            answered = !workout && reaction.hop.value >= HOP_TOP
            sinceTheSave = 0
            fireStill = 0
            hopStill = 0
            turns = 0
            lastLowTurn = Float.MAX_VALUE
        }

        fun switchAnimationsOff() {
            val resolver = RuntimeEnvironment.getApplication().contentResolver
            Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
            resolver.notifyChange(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), null)
            animated = false
        }

        fun frames(count: Int, each: () -> Unit = {}) = repeat(count) {
            frame()
            sinceTheSave++
            if (animated) {
                fireKeepsItsLaws(reaction.burst.value)
                hopKeepsItsLaws(reaction.hop.value)
            }
            each()
        }

        /** Longer than a fire burns and than a landing rings on. */
        fun rest(each: () -> Unit = {}) = frames((TO_REST * slowed).toInt(), each)

        /**
         * To rest in the hop he is in: a save that meets him in the air and lifts him the same
         * way gets that hop, so once he is down from it he stays down. [each] is told the frame.
         */
        fun restWithoutComingUpAgain(each: (frame: Int) -> Unit = {}) {
            var frame = 0
            var down = false
            rest {
                val height = reaction.hop.value
                if (height < BOUNCE) down = true
                if (down) assertTrue("${where}he came up again, to $height", height < BOUNCE)
                each(++frame)
            }
        }

        fun framesToTheTop() = framesUntil("he never got to the top") { reaction.hop.value == 1f }

        fun framesUntil(otherwise: String, reached: () -> Boolean) {
            var waited = 0
            while (!reached()) {
                assertTrue("$where$otherwise", waited++ < TO_ANSWER * slowed)
                frames(1)
            }
        }

        private fun fireKeepsItsLaws(now: Float) {
            val burnt = now - burst
            when {
                burnt < 0f -> {
                    assertEquals("${where}where a fire that starts again starts", 0f, now)
                    assertTrue("${where}a fire was lit for a save that is no workout", workout)
                    fires++
                    fireStill = 0
                }
                burnt > 0f -> {
                    assertTrue("${where}the fire jumped ahead, from $burst to $now", burnt <= FIRE_STEP / slowed * 1.1f)
                    assertTrue("${where}the fire slowed down, from $burst to $now", burnt >= FIRE_STEP / slowed * 0.9f || now == 1f)
                }
                now < 1f -> assertTrue("${where}the fire was held up at $now", ++fireStill <= STILL_FRAMES)
            }
            if (workout && now == 0f) answered = true
            burst = now
        }

        private fun hopKeepsItsLaws(now: Float) {
            if (reaction.hopsAlone != alone) {
                assertEquals("${where}how high he is as his lift changes", 0f, now)
                alone = reaction.hopsAlone
                lastLowTurn = Float.MAX_VALUE
            }
            if (reaction.playing != null && now != 0f) assertTrue("${where}a save is acted out in a picture that is $now in the air", reaction.hopsAlone)
            val moved = now - hop
            assertTrue("${where}he jumped from $hop to $now", abs(moved) <= HOP_STEP)
            assertTrue("${where}he sank through the floor, to $now", now >= -BOUNCE)
            if (moved == 0f) {
                if (now != 0f) assertTrue("${where}he hung in the air at $now", ++hopStill <= STILL_FRAMES)
            } else {
                val direction = if (moved > 0f) 1 else -1
                if (direction == -heading) {
                    if (hop > BOUNCE) {
                        assertTrue("${where}he shook in the air, turning at $hop", ++turns <= TURNS)
                    } else {
                        assertTrue("${where}he shook on the floor, turning at $hop after $lastLowTurn", abs(hop) <= lastLowTurn / 2)
                        lastLowTurn = abs(hop)
                    }
                }
                heading = direction
            }
            // Up there a landing is still to come, and it rings out from its own first bounce.
            if (now > BOUNCE) lastLowTurn = Float.MAX_VALUE
            if (now >= HOP_TOP) {
                if (hop < HOP_TOP) tops++
                if (!workout) answered = true
            }
            hop = now
        }

        fun assertAtRest() {
            assertEquals("${where}how high he is", 0f, reaction.hop.value)
            assertEquals("${where}how far the fire has burnt", 1f, reaction.burst.value)
        }

        /** For a workout the fire has been seen at nothing since, for any other save he has been seen at the top. */
        fun assertAnsweredTheLastSave() = assertTrue("${where}the last save, ${if (workout) "a workout" else "no workout"}, went unanswered", answered)

        fun leave() {
            if (!open.remove(this)) return
            activity.pause().stop().destroy()
            // Compose's dispatcher outlives a test method and remembers that it has asked for a
            // frame. Robolectric drops that frame between methods, the dispatcher goes on waiting
            // for it, and nothing moves in a later method's windows, which share that dispatcher.
            repeat(2) { frame() }
        }
    }

    private fun frame() = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(FRAME_MILLIS))

    private companion object {
        const val FRAME_MILLIS = 16L
        const val RUNS = 150

        /** The first frame a full second after a save. */
        const val A_SECOND = 63

        /** The frames in which a save is answered wherever it catches him: a touchdown, then a rise. */
        const val TO_ANSWER = 26

        /** Longer than a fire burns and than a landing rings on, whichever the fire's length is. */
        const val TO_REST = (MoSaveReaction.BURST_MILLIS / FRAME_MILLIS).toInt() + 120

        /** How much of itself a fire burns in a frame. */
        const val FIRE_STEP = FRAME_MILLIS.toFloat() / MoSaveReaction.BURST_MILLIS

        /**
         * Frames a motion may lose to being taken over: the one in which the old motion is
         * stopped, the one in which the new one starts, and two more for the scheduler.
         */
        const val STILL_FRAMES = 4

        /** Long enough to see him come down, and far too short to wait out a slowed rise. */
        const val A_QUARTER_SECOND = 16

        /** More of a hop than the fastest of his motions, the touchdown, covers in a frame. */
        const val HOP_STEP = 0.5f
        const val HOP_TOP = 0.99f

        /** Higher than his landing bounces back up, and deeper than it dips below the floor. */
        const val BOUNCE = 0.3f

        /** Where a save turns him round, and the top of the hop it starts. */
        const val TURNS = 2
    }
}

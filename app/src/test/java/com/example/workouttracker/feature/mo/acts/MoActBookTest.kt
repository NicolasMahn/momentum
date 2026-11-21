package com.example.workouttracker.feature.mo.acts

import com.example.workouttracker.domain.mo.saveAct
import com.example.workouttracker.domain.time.TimerMotifs
import com.example.workouttracker.feature.mo.MoBodies
import java.time.LocalDate
import com.example.workouttracker.domain.mo.MoNeglect
import com.example.workouttracker.domain.mo.MoProp
import com.example.workouttracker.domain.mo.MoState
import com.example.workouttracker.feature.mo.MoLook
import com.example.workouttracker.feature.mo.MoNeglectedDrawing
import com.example.workouttracker.domain.mo.MoIntensity
import com.example.workouttracker.domain.mo.MoSaveAct
import com.example.workouttracker.domain.mo.MoOccasion
import com.example.workouttracker.feature.mo.MoBodyKind
import com.example.workouttracker.feature.mo.MoPose
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MoActBookTest {
    @Test
    fun everyActHasAUniqueIdThatNamesItsOccasion() {
        val ids = MoActBook.map { it.id }

        assertEquals("duplicate ids: ${ids.groupBy { it }.filterValues { it.size > 1 }.keys}", ids.size, ids.toSet().size)
        MoActBook.forEach { act ->
            assertTrue("${act.id} should start with its occasion", act.id.startsWith(act.occasion.name.lowercase() + "_"))
            assertTrue("${act.id} is not snake_case", Regex("[a-z][a-z0-9_]*").matches(act.id))
            assertTrue("${act.id} has no body to play on", act.bodies.isNotEmpty())
            assertEquals("${act.id}: only a Doing act names a save activity, and it must", act.occasion == MoOccasion.Doing, act.saveAct != null)
        }
    }

    @Test
    fun everyOccasionHasSomethingToPlayOnTheBodyItHappensOn() {
        (MoOccasion.entries - MoOccasion.Doing).forEach { occasion ->
            val bodies = when (occasion) {
                MoOccasion.Out -> listOf(MoBodyKind.Husk)
                MoOccasion.Cold -> listOf(MoBodyKind.Droplet)
                MoOccasion.Frozen -> MoBodyKind.entries
                else -> listOf(MoBodyKind.Tall, MoBodyKind.Low)
            }
            bodies.forEach { body ->
                assertNotNull("nothing to play for $occasion on $body", pickMoAct(MoActBook, occasion, body, turn = 0))
            }
        }
    }

    @Test
    fun everyBurningLookCanPlayEveryActivityAndHomeCanVaryTheFrequentOnes() {
        val frequent = setOf(MoSaveAct.Brushing, MoSaveAct.Reading, MoSaveAct.Eating, MoSaveAct.Running)
        for (state in MoState.entries) for (activity in MoSaveAct.entries) {
            val scene = MoLook(state, doing = activity).playingScene
            if (state < MoState.Dimming) {
                assertEquals(null, scene)
            } else {
                assertEquals("$state $activity", activity, scene?.act?.saveAct)
                assertTrue(state.bodyKind() in scene!!.act.bodies)
                if (activity in frequent) {
                    val next = moDoingScene(activity, state.bodyKind(), turn = 1)
                    assertTrue("only one $activity on $state", next?.act != scene.act)
                }
            }
        }
    }

    @Test
    fun aSoreMouthPreventsEvenAnExplicitBrushingScene() {
        val sore = listOf(MoNeglectedDrawing(MoProp.LostTeeth, MoNeglect.Mild))
        for (state in listOf(MoState.Steady, MoState.Dimming)) {
            val brush = moDoingScene(MoSaveAct.Brushing, state.bodyKind(), turn = 0)
            assertEquals(null, MoLook(state, doing = MoSaveAct.Brushing, neglect = sore).playingScene)
            assertEquals(null, MoLook(state, scene = brush, neglect = sore).playingScene)
            assertNotNull(MoLook(state, doing = MoSaveAct.Reading, neglect = sore).playingScene)
        }
    }

    @Test
    fun onlyAMoWhoBurnsAndIsFreeToActsOutASave() {
        val day = MoScene(FakeAct("idle_fake"), MoVariant(1))
        val reading = moDoingScene(MoSaveAct.Reading, MoBodyKind.Tall, turn = 0)

        // A tantrum and the ice leave no room for it, whether Home hands over the act or only names the activity.
        assertEquals(day, MoLook(MoState.Steady, doing = MoSaveAct.Reading, angry = true, scene = day).playingScene)
        assertEquals(null, MoLook(MoState.Steady, angry = true, scene = reading).playingScene)
        assertEquals(day, MoLook(MoState.Steady, doing = MoSaveAct.Reading, frozen = true, scene = day).playingScene)
        assertEquals(null, MoLook(MoState.Steady, frozen = true, scene = reading).playingScene)
        // A cold Mo and a Husk keep to their day.
        assertEquals(day, MoLook(MoState.Cold, doing = MoSaveAct.Reading, scene = day).playingScene)
        assertEquals(day, MoLook(MoState.Husk, doing = MoSaveAct.Reading, scene = day).playingScene)
        // Otherwise the save takes the place of the day's act.
        assertEquals(reading, MoLook(MoState.Steady, doing = MoSaveAct.Reading, scene = reading).playingScene)
        assertEquals(MoSaveAct.Reading, MoLook(MoState.Steady, doing = MoSaveAct.Reading, scene = day).playingScene?.act?.saveAct)
    }

    @Test
    fun aTimerShowsTheOneActWrittenToBeWatchedForAnHour() {
        // Every activity a timer can show needs one, or the hour-long picture would be whichever act sorts first.
        for (motif in TimerMotifs) {
            val forASession = MoActBook.filter { it.saveAct == motif.saveAct && it.forASession }
            assertEquals("${motif.saveAct}: ${forASession.map { it.id }}", 1, forASession.size)
            assertEquals(MoBodies.Burning, forASession.single().bodies)
            // A timer names the activity and nothing else.
            assertEquals(forASession.single(), MoLook(MoState.Steady, doing = motif.saveAct).playingScene?.act)
        }
        assertTrue("only an act for a save can be a session's", MoActBook.filter { it.forASession }.all { it.occasion == MoOccasion.Doing })

        val acts = listOf(
            FakeAct("doing_reading_a", MoOccasion.Doing, saveAct = MoSaveAct.Reading),
            FakeAct("doing_reading_b", MoOccasion.Doing, saveAct = MoSaveAct.Reading, forASession = true),
        )
        assertEquals("doing_reading_b", moSessionScene(MoSaveAct.Reading, MoBodyKind.Tall, acts)?.act?.id)
        assertEquals("doing_reading_a", moSessionScene(MoSaveAct.Reading, MoBodyKind.Tall, acts.take(1))?.act?.id)
    }

    @Test
    fun aSaveIsActedOutDifferentlyFromOneDayToTheNext() {
        // Home's turn is the day. A task is celebrated once on the day it is kept, so whatever its
        // checkpoints and whether or not the streak moved, tomorrow's save plays the next act.
        val frequent = setOf(MoSaveAct.Brushing, MoSaveAct.Reading, MoSaveAct.Eating, MoSaveAct.Running)
        val monday = LocalDate.parse("2026-11-16").toEpochDay().toInt()
        for (body in MoBodies.Burning) for (activity in MoSaveAct.entries) {
            val week = (monday until monday + 7).map { day -> moDoingScene(activity, body, turn = day)!! }
            assertTrue("$activity on $body", week.all { it.act.saveAct == activity && body in it.act.bodies })
            if (activity in frequent) {
                week.zipWithNext().forEach { (today, tomorrow) -> assertTrue("$activity on $body repeats", today.act != tomorrow.act) }
            }
        }
    }

    @Test
    fun aSaveHeDoesNotActOutLeavesHisDayAlone() {
        val day = MoScene(FakeAct("idle_fake"), MoVariant(1))
        val sore = listOf(MoNeglectedDrawing(MoProp.LostTeeth, MoNeglect.Mild))

        // A sore mouth keeps him from brushing, and his day's act goes on instead of making room for nothing.
        assertEquals(false, MoLook(MoState.Steady, neglect = sore, scene = day).actsOut(MoSaveAct.Brushing))
        assertEquals(day, MoLook(MoState.Steady, doing = MoSaveAct.Brushing, neglect = sore, scene = day).playingScene)
        assertEquals(true, MoLook(MoState.Steady, neglect = sore, scene = day).actsOut(MoSaveAct.Reading))
        for (state in MoState.entries) for (activity in MoSaveAct.entries) {
            val burning = state.bodyKind() in MoBodies.Burning
            assertEquals("$state $activity", burning, MoLook(state, scene = day).actsOut(activity))
            assertEquals("$state $activity angry", false, MoLook(state, angry = true, scene = day).actsOut(activity))
            assertEquals("$state $activity frozen", false, MoLook(state, frozen = true, scene = day).actsOut(activity))
        }
    }

    @Test
    fun aSaveIsActedOutOnlyByActsOfThatActivityAndBody() {
        val acts = listOf(
            FakeAct("doing_reading_a", MoOccasion.Doing, saveAct = MoSaveAct.Reading),
            FakeAct("doing_reading_b", MoOccasion.Doing, saveAct = MoSaveAct.Reading),
            FakeAct("doing_flossing", MoOccasion.Doing, saveAct = MoSaveAct.Brushing, bodies = setOf(MoBodyKind.Tall)),
            FakeAct("idle_fake"),
        )
        fun played(saveAct: MoSaveAct, body: MoBodyKind) = (0 until 20).mapNotNull { moDoingScene(saveAct, body, turn = it, book = acts)?.act?.id }.toSet()

        assertEquals(setOf("doing_reading_a", "doing_reading_b"), played(MoSaveAct.Reading, MoBodyKind.Tall))
        assertEquals(setOf("doing_flossing"), played(MoSaveAct.Brushing, MoBodyKind.Tall))
        assertEquals(emptySet<String>(), played(MoSaveAct.Brushing, MoBodyKind.Low))
        assertEquals(emptySet<String>(), played(MoSaveAct.Weighing, MoBodyKind.Tall))
    }

    @Test
    fun anOccasionsActsAllComeRoundBeforeAnyRepeats() {
        val acts = List(7) { FakeAct("idle_fake_$it") }

        (0L until 5L).forEach { round ->
            val played = (0 until 7).map { pickMoAct(acts, MoOccasion.Idle, MoBodyKind.Tall, turn = round * 7 + it)!!.id }
            assertEquals("round $round repeated an act: $played", 7, played.toSet().size)
        }
        val firstRound = (0 until 7).map { pickMoAct(acts, MoOccasion.Idle, MoBodyKind.Tall, turn = it.toLong())!!.id }
        val secondRound = (7 until 14).map { pickMoAct(acts, MoOccasion.Idle, MoBodyKind.Tall, turn = it.toLong())!!.id }
        assertTrue("every round should be reshuffled", firstRound != secondRound)
    }

    @Test
    fun aFeelingWithAnIntensityPlaysOnlyActsOfThatIntensityOrTheNearestThereIs() {
        val acts = listOf(
            FakeAct("angry_tapping", MoOccasion.Angry, intensity = MoIntensity.Mild),
            FakeAct("angry_glaring", MoOccasion.Angry, intensity = MoIntensity.Mild),
            FakeAct("angry_tantrum", MoOccasion.Angry, intensity = MoIntensity.Strong),
        )
        fun played(intensity: MoIntensity?) = (0L until 12L).map { pickMoAct(acts, MoOccasion.Angry, MoBodyKind.Tall, it, intensity)!!.id }.toSet()

        assertEquals(setOf("angry_tapping", "angry_glaring"), played(MoIntensity.Mild))
        assertEquals(setOf("angry_tantrum"), played(MoIntensity.Strong))
        // Nothing is Medium here, and Mild and Strong are equally near, so both may play.
        assertEquals(acts.map { it.id }.toSet(), played(MoIntensity.Medium))
        assertEquals(acts.map { it.id }.toSet(), played(null))
    }

    @Test
    fun anOccasionWithoutAnActForThisBodyFallsBackToAnIdleOne() {
        val acts = listOf(FakeAct("idle_fake"), FakeAct("happy_fake", MoOccasion.Happy, setOf(MoBodyKind.Tall)))

        assertEquals("happy_fake", pickMoAct(acts, MoOccasion.Happy, MoBodyKind.Tall, turn = 3)!!.id)
        assertEquals("idle_fake", pickMoAct(acts, MoOccasion.Happy, MoBodyKind.Low, turn = 3)!!.id)
        assertEquals("idle_fake", pickMoAct(acts, MoOccasion.Hungry, MoBodyKind.Tall, turn = 3)!!.id)
        assertEquals(null, pickMoAct(acts, MoOccasion.Cold, MoBodyKind.Droplet, turn = 3))
    }

    @Test
    fun everyPoseIsSoundAtAnyMomentOnEveryBodyItClaims() {
        val moments = listOf(0f, 0.37f, 1.9f, 12.5f, 3_600f, 86_400f)
        MoActBook.forEach { act ->
            act.bodies.filter { it != MoBodyKind.Husk }.forEach { body ->
                moments.forEach { t ->
                    listOf(MoVariant.Plain, MoVariant(7), MoVariant(-123_456)).forEach { variant ->
                        val pose = act.pose(t, variant, body)
                        val numbers = listOf(
                            pose.sway, pose.flare, pose.squash, pose.tilt, pose.shake, pose.offset.x, pose.offset.y,
                            pose.leftArm.reach.x, pose.leftArm.reach.y, pose.rightArm.reach.x, pose.rightArm.reach.y,
                            pose.legs.left.lift, pose.legs.right.lift, pose.leftEye.open, pose.rightEye.open,
                        )
                        assertTrue("${act.id} on $body at $t is not finite: $numbers", numbers.all { it.isFinite() })
                        // He may hop and lean, but an act that throws him across the screen is a bug.
                        assertTrue("${act.id} on $body at $t moves him too far: ${pose.offset}", pose.offset.getDistance() < 140f)
                        assertTrue("${act.id} on $body at $t tilts him too far: ${pose.tilt}", pose.tilt in -60f..60f)
                        assertTrue("${act.id} on $body at $t flares too far: ${pose.flare}", pose.flare in -40f..90f)
                    }
                }
            }
        }
    }

    private class FakeAct(
        id: String,
        occasion: MoOccasion = MoOccasion.Idle,
        bodies: Set<MoBodyKind> = setOf(MoBodyKind.Tall, MoBodyKind.Low),
        intensity: MoIntensity = MoIntensity.Medium,
        saveAct: MoSaveAct? = null,
        override val forASession: Boolean = false,
    ) : MoAct(id, occasion, bodies, intensity, saveAct) {
        override fun pose(t: Float, v: MoVariant, body: MoBodyKind) = MoPose()
    }
}

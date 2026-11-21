package com.example.workouttracker.persistence.json

import com.example.workouttracker.persistence.json.introducedInFormat

import com.example.workouttracker.domain.compatibility.ReadingTimeId
import com.example.workouttracker.domain.AppState
import com.example.workouttracker.domain.checklist.Checkpoint
import com.example.workouttracker.domain.checklist.CheckpointTime
import com.example.workouttracker.domain.checklist.Dated
import com.example.workouttracker.domain.checklist.MoMotif
import com.example.workouttracker.domain.checklist.RewardPeriod
import com.example.workouttracker.domain.checklist.ViewIcon
import com.example.workouttracker.domain.checklist.WhenAway
import com.example.workouttracker.domain.measurement.testMadeMeasurement
import com.example.workouttracker.domain.measurement.withWeight
import com.example.workouttracker.domain.questions.testMadeQuestions
import com.example.workouttracker.domain.questions.withEating
import com.example.workouttracker.domain.supplement.testSupplementsChecklist
import com.example.workouttracker.domain.time.DefaultTokenSteps
import com.example.workouttracker.domain.time.TimeLog
import com.example.workouttracker.domain.time.TimeRewards
import com.example.workouttracker.domain.time.TimerMotifs
import com.example.workouttracker.domain.time.TokenStep
import com.example.workouttracker.domain.time.TokenSteps
import com.example.workouttracker.domain.time.tokenStepsOfTokensPerWeek
import com.example.workouttracker.domain.time.testMadeTime
import com.example.workouttracker.domain.time.testReadingTime
import com.example.workouttracker.domain.compatibility.readingTime
import com.example.workouttracker.domain.time.withReading
import com.example.workouttracker.domain.activity.withActivity
import com.example.workouttracker.persistence.FrozenReadingLogsOfVersion28
import com.example.workouttracker.persistence.FrozenVersion29WithTokensAWeek
import com.example.workouttracker.persistence.FrozenVersion29WithoutTokensAWeekOrAFlavor
import com.example.workouttracker.persistence.claimedAs
import com.example.workouttracker.persistence.encodedAsVersion
import com.example.workouttracker.persistence.frozenVersion28
import com.example.workouttracker.persistence.legacyTrackerJson
import com.example.workouttracker.persistence.plainJson
import com.example.workouttracker.persistence.readBefore29
import com.example.workouttracker.persistence.stateOfFrozenVersion29
import com.example.workouttracker.persistence.stateOfFrozenVersion29WithTokensAWeek
import java.time.LocalDate
import java.time.LocalTime
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class TimeJsonTest {
    private val day = LocalDate.parse("2026-10-13")

    private fun steps(vararg steps: Pair<Int, Int>) = TokenSteps(steps.map { (percent, tokens) -> TokenStep(percent, tokens) })

    /** The two steps every bank starts with, and one list of three, as they are stored. */
    private val defaultStepsJson = """{"steps":[{"percent":100,"tokens":1},{"percent":200,"tokens":1}]}"""
    private val threeStepsJson = """{"steps":[{"percent":100,"tokens":4},{"percent":150,"tokens":3},{"percent":1000,"tokens":5}]}"""

    /** A made view in the streak with two periods, its goal, its bank's size, its steps and its holiday rule changed on a day, reminding after waking. */
    private val piano = testMadeTime().copy(
        goal = Dated(listOf(Dated.Revision(60), Dated.Revision(90, day.plusDays(4)))),
        rewards = TimeRewards(
            maxPasses = Dated(listOf(Dated.Revision(3), Dated.Revision(2, day.plusDays(4)))),
            periods = listOf(RewardPeriod(day, day.plusDays(1)), RewardPeriod(day.plusDays(3))),
            tokenSteps = Dated(listOf(Dated.Revision(DefaultTokenSteps), Dated.Revision(steps(100 to 4, 150 to 3, 1000 to 5), day.plusDays(6)))),
        ),
        whenAway = Dated(listOf(Dated.Revision(WhenAway.Due), Dated.Revision(WhenAway.Excused, day.plusDays(5)))),
        checkpoint = Checkpoint("day", CheckpointTime.AfterWake(20), remind = true),
    )

    private val logs = listOf(
        TimeLog("reading", "2026-10-14", 30, 25),
        TimeLog(piano.id, "2026-10-14", 20, 1440),
        TimeLog("reading", "2026-10-13", 10),
        TimeLog("tim_gone", "2026-10-13", 0, 1),
    )

    private val state = AppState(timeLogs = logs).withWeight().withEating().withReading().withActivity().let { it.copy(time = it.time + piano) }

    private fun refused(what: String, raw: String) {
        assertThrows(what, RuntimeException::class.java) { AppStateJsonCodec.decodeVersion30(raw) }
    }

    @Test fun readingsDefaultIsStoredInTheShapeOfItsKind() {
        val seeded = AppState().withWeight().withEating().withReading().withActivity()
        val stored = JSONObject(AppStateJsonCodec.encodeVersion30(seeded))
        val expected = JSONArray("""[{"view":"time","id":"reading","label":"Reading","icon":"book","hue":"yellow","motif":"reading","goal":60,"rewards":{"maxPasses":3,"tokenSteps":$defaultStepsJson},"whenAway":"due","checkpoints":[{"id":"day","at":"18:00","remind":false}]}]""")
        assertEquals(plainJson(expected), plainJson(stored.getJSONArray("time")))
        assertEquals(0, stored.getJSONArray("timeLogs").length())
        assertEquals(30, stored.getInt("schemaVersion"))
        assertFalse(stored.has("readingLogs"))
        assertEquals(seeded, AppStateJsonCodec.decodeVersion30(stored.toString()))
    }

    /** A made view holds its flavor, Plain written out, its goal and its checkpoint, a bank once it ever counted, and each dated setting as its revisions; one value of the steps is an object, so a list of them is never read as revisions. */
    @Test fun aMadeViewIsStoredWithItsGoalItsCheckpointAndItsBank() {
        val plain = testMadeTime(id = "tim_m7xd", label = "Guitar", goal = 45).copy(motif = MoMotif.Book)
        val stored = JSONObject(AppStateJsonCodec.encodeVersion30(state.copy(time = state.time + plain))).getJSONArray("time")
        assertEquals(
            plainJson(
                JSONObject(
                    """{"view":"time","id":"tim_k2p9","label":"Piano","icon":"music","hue":"navy","motif":"plain",""" +
                        """"goal":[{"value":60},{"value":90,"from":"2026-10-17"}],""" +
                        """"rewards":{"maxPasses":[{"value":3},{"value":2,"from":"2026-10-17"}],"tokenSteps":[{"value":$defaultStepsJson},{"value":$threeStepsJson,"from":"2026-10-19"}],""" +
                        """"periods":[{"from":"2026-10-13","until":"2026-10-14"},{"from":"2026-10-16"}]},""" +
                        """"whenAway":[{"value":"due"},{"value":"excused","from":"2026-10-18"}],""" +
                        """"checkpoints":[{"id":"day","at":"wake+20","remind":true}]}""",
                ),
            ),
            plainJson(stored.getJSONObject(1)),
        )
        assertEquals(
            plainJson(JSONObject("""{"view":"time","id":"tim_m7xd","label":"Guitar","icon":"music","hue":"navy","motif":"reading","goal":45,"whenAway":"due","checkpoints":[{"id":"day","at":"09:00","remind":false}]}""")),
            plainJson(stored.getJSONObject(2)),
        )
    }

    /** A log names its view under `time`, and only a mark of Reading's is without its minutes. */
    @Test fun aLogIsStoredWithItsViewItsDayItsTimeAndItsMinutes() {
        val stored = JSONObject(AppStateJsonCodec.encodeVersion30(state)).getJSONArray("timeLogs")
        assertEquals(
            plainJson(
                JSONArray(
                    """[{"time":"reading","dateKey":"2026-10-14","timestampMillis":30,"minutes":25},""" +
                        """{"time":"tim_k2p9","dateKey":"2026-10-14","timestampMillis":20,"minutes":1440},""" +
                        """{"time":"reading","dateKey":"2026-10-13","timestampMillis":10},""" +
                        """{"time":"tim_gone","dateKey":"2026-10-13","timestampMillis":0,"minutes":1}]""",
                ),
            ),
            plainJson(stored),
        )
    }

    /** Every form a time view can take crosses the encoding and comes back equal, and so does every log, a log of a view nothing knows included. */
    @Test fun version29RoundTripsEveryViewAndEveryLog() {
        val forms = listOf(
            state,
            state.copy(
                time = listOf(
                    testReadingTime().copy(
                        label = "Books",
                        icon = ViewIcon.Heart,
                        motif = null,
                        goal = Dated(listOf(Dated.Revision(60), Dated.Revision(1, day), Dated.Revision(10080, day.plusDays(7)))),
                        rewards = TimeRewards(Dated(listOf(Dated.Revision(3), Dated.Revision(9, day))), tokenSteps = Dated(listOf(Dated.Revision(DefaultTokenSteps), Dated.Revision(steps(100 to 1), day), Dated.Revision(steps(100 to 1, 200 to 2, 300 to 3, 400 to 4, 500 to 5), day.plusDays(7))))),
                        whenAway = Dated(listOf(Dated.Revision(WhenAway.Due), Dated.Revision(WhenAway.Excused, day), Dated.Revision(WhenAway.Due, day.plusDays(2)))),
                    ),
                    testMadeTime(id = "tim_aaa1").copy(rewards = TimeRewards(maxPasses = 3, periods = emptyList()), whenAway = Dated.always(WhenAway.Excused)),
                    testMadeTime(id = "tim_aaa2").copy(rewards = TimeRewards(maxPasses = 1, periods = listOf(RewardPeriod(day)), tokenSteps = steps(100 to 4, 101 to 5))),
                    testMadeTime(id = "tim_aaa3").copy(icon = ViewIcon.Timer, motif = MoMotif.Book, checkpoint = Checkpoint("day", CheckpointTime.BeforeSleep(15), remind = true)),
                    testMadeTime(id = "tim_aaa4").copy(icon = ViewIcon.Language, checkpoint = Checkpoint("day", CheckpointTime.Fixed(LocalTime.of(21, 30)), remind = false)),
                    piano,
                ),
            ),
            AppState().withWeight().withEating().withReading().withActivity(),
        )
        forms.forEach { form ->
            val encoded = AppStateJsonCodec.encodeVersion30(form)
            val decoded = AppStateJsonCodec.decodeVersion30(encoded)
            assertEquals(form, decoded)
            assertEquals(encoded, AppStateJsonCodec.encodeVersion30(decoded))
            assertFalse(AppStateJsonCodec.lostRecords(encoded, decoded))
        }
    }

    /**
     * The first builds that stored version 29, on 2026-10-13, wrote no steps, no number of tokens
     * a week and no flavor: every bank earned two a week, and Mo had his book for Reading alone.
     * A state they wrote, kept here byte for byte, reads as exactly that and as everything it
     * stored, so nothing a person sees or earns changes. Written again it holds the flavor and
     * the steps those two tokens are, and reads back the same.
     */
    @Test fun aVersion29PayloadOfTheFirstBuildsReadsAsTwoTokensAWeekAndTheFlavorEachViewHad() {
        val stored = JSONObject(FrozenVersion29WithoutTokensAWeekOrAFlavor).getJSONArray("time")
        (0 until stored.length()).map(stored::getJSONObject).forEach { view ->
            assertFalse(view.has("motif"))
            assertFalse(view.optJSONObject("rewards")?.has("tokensPerWeek") == true)
        }

        val decoded = AppStateJsonCodec.decodeVersion29(FrozenVersion29WithoutTokensAWeekOrAFlavor)

        val (reading, piano, guitar) = decoded.time
        assertEquals(listOf("reading", "tim_k2p9", "tim_m7xd"), decoded.time.map { it.id })
        assertEquals(listOf(MoMotif.Book, null, null), decoded.time.map { it.motif })
        assertEquals(Dated.always(DefaultTokenSteps), reading.rewards!!.tokenSteps)
        assertEquals(Dated.always(DefaultTokenSteps), piano.rewards!!.tokenSteps)
        assertEquals(null, guitar.rewards)
        assertEquals(stateOfFrozenVersion29(), decoded)
        assertFalse(AppStateJsonCodec.lostRecords(FrozenVersion29WithoutTokensAWeekOrAFlavor, decoded))

        val written = AppStateJsonCodec.encodeVersion30(decoded)
        val views = JSONObject(written).getJSONArray("time")
        assertEquals(listOf("reading", "plain", "plain"), (0 until 3).map { views.getJSONObject(it).getString("motif") })
        assertEquals(listOf(plainJson(JSONObject(defaultStepsJson)), plainJson(JSONObject(defaultStepsJson))), (0 until 2).map { plainJson(views.getJSONObject(it).getJSONObject("rewards").getJSONObject("tokenSteps")) })
        assertTrue((0 until 2).none { views.getJSONObject(it).getJSONObject("rewards").has("tokensPerWeek") })
        assertFalse(views.getJSONObject(2).has("rewards"))
        assertEquals(decoded, AppStateJsonCodec.decodeVersion30(written))
        // Only one of the two missing is read the same way: each has its own default.
        val withFlavorOnly = JSONObject(FrozenVersion29WithoutTokensAWeekOrAFlavor).apply { getJSONArray("time").getJSONObject(0).put("motif", "plain") }
        val plainReading = AppStateJsonCodec.decodeVersion29(withFlavorOnly.toString()).time.first()
        assertEquals(null, plainReading.motif)
        assertEquals(Dated.always(DefaultTokenSteps), plainReading.rewards!!.tokenSteps)
        val withStepsOnly = JSONObject(FrozenVersion29WithoutTokensAWeekOrAFlavor).apply { getJSONArray("time").getJSONObject(0).getJSONObject("rewards").put("tokenSteps", JSONObject(threeStepsJson)) }
        val three = AppStateJsonCodec.decodeVersion29(withStepsOnly.toString()).time.first()
        assertEquals(MoMotif.Book, three.motif)
        assertEquals(Dated.always(steps(100 to 4, 150 to 3, 1000 to 5)), three.rewards!!.tokenSteps)
    }

    /**
     * The builds of 2026-10-14 stored how many tokens a week earns as a number, `tokensPerWeek`,
     * 1 to 5 and dated as the goal is. A bank stored so reads it as a step of one token at each
     * whole goal up to it, 100 % for 1 up to N × 100 % for N, revision by revision, which earn
     * exactly what the number earned; written again it holds the steps and no number. A bank
     * that holds both is refused, as a number no steps can be.
     */
    @Test fun aVersion29PayloadWithTokensAWeekReadsAsTheStepsOfEachWholeGoalUpToIt() {
        val stored = JSONObject(AppStateJsonCodec.encodeVersion30(state)).apply {
            val views = getJSONArray("time")
            views.getJSONObject(0).getJSONObject("rewards").apply { remove("tokenSteps") }.put("tokensPerWeek", JSONArray("""[{"value":2},{"value":5,"from":"2026-10-20"},{"value":1,"from":"2026-10-27"}]"""))
            views.getJSONObject(1).getJSONObject("rewards").apply { remove("tokenSteps") }.put("tokensPerWeek", 3)
        }.toString()

        val decoded = AppStateJsonCodec.decodeVersion30(stored)

        val (reading, piano) = decoded.time
        assertEquals(
            Dated(listOf(Dated.Revision(DefaultTokenSteps), Dated.Revision(tokenStepsOfTokensPerWeek(5), day.plusDays(7)), Dated.Revision(steps(100 to 1), day.plusDays(14)))),
            reading.rewards!!.tokenSteps,
        )
        assertEquals(Dated.always(steps(100 to 1, 200 to 1, 300 to 1)), piano.rewards!!.tokenSteps)
        assertEquals(state.copy(time = listOf(reading, piano)), decoded)
        assertFalse(AppStateJsonCodec.lostRecords(stored, decoded))
        val written = JSONObject(AppStateJsonCodec.encodeVersion30(decoded)).getJSONArray("time")
        assertTrue((0 until 2).none { written.getJSONObject(it).getJSONObject("rewards").has("tokensPerWeek") })
        assertEquals(
            plainJson(JSONObject("""{"steps":[{"percent":100,"tokens":1},{"percent":200,"tokens":1},{"percent":300,"tokens":1}]}""")),
            plainJson(written.getJSONObject(1).getJSONObject("rewards").getJSONObject("tokenSteps")),
        )
        assertEquals(decoded, AppStateJsonCodec.decodeVersion30(AppStateJsonCodec.encodeVersion30(decoded)))

        val both = JSONObject(stored).apply { getJSONArray("time").getJSONObject(1).getJSONObject("rewards").put("tokenSteps", JSONObject(defaultStepsJson)) }
        refused("a bank with its steps and the number they came from", both.toString())
    }

    /**
     * The builds of 2026-10-14 stored how many tokens a week earns as a number. A state they
     * wrote, kept here byte for byte, reads each number as the steps of each whole goal up to
     * it, revision by revision, and as everything else it stored; written again it holds the
     * steps and no number, and reads back the same.
     */
    @Test fun aVersion29PayloadOfTheBuildsWithTokensAWeekReadsAsTheStepsEachNumberStoodFor() {
        val stored = JSONObject(FrozenVersion29WithTokensAWeek).getJSONArray("time")
        assertEquals(listOf(true, true, false, true), (0 until 4).map { stored.getJSONObject(it).optJSONObject("rewards")?.has("tokensPerWeek") == true })
        assertTrue((0 until 4).none { stored.getJSONObject(it).optJSONObject("rewards")?.has("tokenSteps") == true })

        val decoded = AppStateJsonCodec.decodeVersion29(FrozenVersion29WithTokensAWeek)

        assertEquals(stateOfFrozenVersion29WithTokensAWeek(), decoded)
        val (reading, piano, guitar, harp) = decoded.time
        assertEquals(Dated(listOf(Dated.Revision(DefaultTokenSteps), Dated.Revision(steps(100 to 1, 200 to 1, 300 to 1), day.plusDays(1)))), reading.rewards!!.tokenSteps)
        assertEquals(Dated.always(steps(100 to 1)), piano.rewards!!.tokenSteps)
        assertEquals(null, guitar.rewards)
        assertEquals(Dated.always(steps(100 to 1, 200 to 1, 300 to 1, 400 to 1, 500 to 1)), harp.rewards!!.tokenSteps)
        assertFalse(AppStateJsonCodec.lostRecords(FrozenVersion29WithTokensAWeek, decoded))

        val written = AppStateJsonCodec.encodeVersion30(decoded)
        val views = JSONObject(written).getJSONArray("time")
        assertTrue((0 until 4).none { views.getJSONObject(it).optJSONObject("rewards")?.has("tokensPerWeek") == true })
        assertEquals(
            plainJson(JSONArray("""[{"value":$defaultStepsJson},{"value":{"steps":[{"percent":100,"tokens":1},{"percent":200,"tokens":1},{"percent":300,"tokens":1}]},"from":"2026-10-14"}]""")),
            plainJson(views.getJSONObject(0).getJSONObject("rewards").getJSONArray("tokenSteps")),
        )
        assertEquals(plainJson(JSONObject("""{"steps":[{"percent":100,"tokens":1}]}""")), plainJson(views.getJSONObject(1).getJSONObject("rewards").getJSONObject("tokenSteps")))
        assertEquals(decoded, AppStateJsonCodec.decodeVersion30(written))
    }

    /** Timer logs return in stored order, with each record still naming its timer. */
    @Test fun logsComeBackByDateNewestFirst() {
        val raw = JSONObject(AppStateJsonCodec.encodeVersion30(state)).apply {
            val stored = getJSONArray("timeLogs")
            put("timeLogs", JSONArray((0 until stored.length()).map(stored::get).reversed()))
        }.toString()
        val decoded = AppStateJsonCodec.decodeVersion30(raw)
        assertEquals(logs, decoded.timeLogs)
        assertEquals(listOf(TimeLog(ReadingTimeId, "2026-10-14", 30, 25), TimeLog(ReadingTimeId, "2026-10-13", 10)), decoded.timeLogs.filter { it.viewId == ReadingTimeId })
    }

    /**
     * What `time` holds is Reading at most once, complete, and made views by their own rules.
     * Anything else is refused, never repaired or replaced by the default. Version 29 held
     * Reading exactly once, without a checkpoint and without periods, which arrived in version 30.
     */
    @Test fun version29RefusesATimeViewItCannotHoldAndRepairsNothing() {
        val valid = JSONObject(AppStateJsonCodec.encodeVersion30(state))
        assertEquals(state, AppStateJsonCodec.decodeVersion30(valid.toString()))
        fun root(change: (JSONObject) -> Unit) = JSONObject(valid.toString()).apply(change).toString()
        fun views(change: (JSONArray) -> Unit) = root { change(it.getJSONArray("time")) }
        fun reading(change: (JSONObject) -> Unit) = views { change(it.getJSONObject(0)) }
        fun made(change: (JSONObject) -> Unit) = views { change(it.getJSONObject(1)) }
        val storedReading = valid.getJSONArray("time").getJSONObject(0).toString()
        val storedMade = valid.getJSONArray("time").getJSONObject(1).toString()
        val dayCheckpoint = """[{"id":"day","at":"09:00","remind":false}]"""

        val reading = state.readingTime!!
        listOf(
            "an empty list" to views { it.remove(1); it.remove(0) } to emptyList(),
            "no Reading" to views { it.remove(0) } to listOf(piano),
            "Reading with periods" to reading { it.getJSONObject("rewards").put("periods", JSONArray("""[{"from":"2026-10-13"}]""")) } to
                listOf(reading.copy(rewards = reading.rewards?.copy(periods = listOf(RewardPeriod(LocalDate.parse("2026-10-13"))))), piano),
            "Reading with no periods, said so" to reading { it.getJSONObject("rewards").put("periods", JSONArray()) } to
                listOf(reading.copy(rewards = reading.rewards?.copy(periods = emptyList())), piano),
            "Reading with a checkpoint of its own" to reading { it.put("checkpoints", JSONArray("""[{"id":"day","at":"09:00","remind":true}]""")) } to
                listOf(reading.copy(checkpoint = Checkpoint("day", CheckpointTime.Fixed(LocalTime.of(9, 0)), remind = true)), piano),
        ).forEach { (case, time) ->
            val (what, raw) = case
            assertEquals(what, time, AppStateJsonCodec.decodeVersion30(raw).time)
            assertThrows("version 29: $what", RuntimeException::class.java) { AppStateJsonCodec.decodeVersion29(claimedAs(raw, 29).toString()) }
        }

        val refused = listOf(
            "no list" to root { it.remove("time") },
            "a list that is none" to root { it.put("time", JSONObject()) },
            "a second Reading" to views { it.put(JSONObject(storedReading)) },
            "a second Reading under another name" to views { it.put(JSONObject(storedReading).put("label", "Books")) },
            "a made view twice" to views { it.put(JSONObject(storedMade)) },
            "a made view twice under another name" to views { it.put(JSONObject(storedMade).put("label", "Guitar")) },
            "a view that is no object" to views { it.put("reading") },
            "another kind of view" to reading { it.put("view", "questions") },
            "an unknown icon" to reading { it.put("icon", "novel") },
            "an unknown hue" to reading { it.put("hue", "beige") },
            "an unknown flavor" to reading { it.put("motif", "jazz") },
            "a flavor of no name" to reading { it.put("motif", "") },
            "a flavor in capitals" to reading { it.put("motif", "Reading") },
            "a flavor that is a number" to reading { it.put("motif", 7) },
            "a null flavor" to reading { it.put("motif", JSONObject.NULL) },
            "a flavor called none" to reading { it.put("motif", "none") },
            "a made view with an unknown flavor" to made { it.put("motif", "jazz") },
            "a second flavor" to made { it.put("flavor", "reading") },
            "a blank name" to reading { it.put("label", " ") },
            "Reading without its checkpoint" to reading { it.remove("checkpoints") },
            "Reading with no checkpoint, said so" to reading { it.put("checkpoints", JSONArray()) },
            "Reading with two checkpoints" to reading { it.put("checkpoints", JSONArray(dayCheckpoint).put(JSONObject("""{"id":"evening","at":"21:00","remind":false}"""))) },
            "Reading without its bank" to reading { it.remove("rewards") },
            "Reading with a null bank" to reading { it.put("rewards", JSONObject.NULL) },
            "Reading with a bank of none" to reading { it.getJSONObject("rewards").put("maxPasses", 0) },
            "Reading with a bank that earns by kept days" to reading { it.put("rewards", JSONObject("""{"maxPasses":3,"earn":{"everyKeptDays":7}}""")) },
            "Reading with a bank that says what it earns by" to reading { it.getJSONObject("rewards").put("earn", JSONObject("""{"everyAmountInWeek":60,"maxPerWeek":2}""")) },
            "Reading with a bank that says how much it covers" to reading { it.getJSONObject("rewards").put("coverAtMost", 2) },
            "Reading with a bank's size as text" to reading { it.getJSONObject("rewards").put("maxPasses", "3") },
            "no step" to reading { it.getJSONObject("rewards").put("tokenSteps", JSONObject("""{"steps":[]}""")) },
            "the steps as a bare list" to reading { it.getJSONObject("rewards").put("tokenSteps", JSONObject(defaultStepsJson).getJSONArray("steps")) },
            "the steps as a number" to reading { it.getJSONObject("rewards").put("tokenSteps", 2) },
            "the steps as text" to reading { it.getJSONObject("rewards").put("tokenSteps", defaultStepsJson) },
            "null steps" to reading { it.getJSONObject("rewards").put("tokenSteps", JSONObject.NULL) },
            "the steps with a field more" to reading { it.getJSONObject("rewards").put("tokenSteps", JSONObject(defaultStepsJson).put("goal", 60)) },
            "a first step not at the goal" to reading { it.getJSONObject("rewards").put("tokenSteps", JSONObject("""{"steps":[{"percent":150,"tokens":1}]}""")) },
            "a further step below the goal" to reading { it.getJSONObject("rewards").put("tokenSteps", JSONObject("""{"steps":[{"percent":100,"tokens":1},{"percent":50,"tokens":2}]}""")) },
            "a further step at the goal" to reading { it.getJSONObject("rewards").put("tokenSteps", JSONObject("""{"steps":[{"percent":100,"tokens":1},{"percent":100,"tokens":2}]}""")) },
            "a step beyond ten times the goal" to reading { it.getJSONObject("rewards").put("tokenSteps", JSONObject("""{"steps":[{"percent":100,"tokens":1},{"percent":1001,"tokens":2}]}""")) },
            "a step not above the one before" to reading { it.getJSONObject("rewards").put("tokenSteps", JSONObject("""{"steps":[{"percent":100,"tokens":1},{"percent":300,"tokens":2},{"percent":200,"tokens":3}]}""")) },
            "a step with no token" to reading { it.getJSONObject("rewards").put("tokenSteps", JSONObject("""{"steps":[{"percent":100,"tokens":0}]}""")) },
            "a step with six tokens" to reading { it.getJSONObject("rewards").put("tokenSteps", JSONObject("""{"steps":[{"percent":100,"tokens":6}]}""")) },
            "six steps" to reading { it.getJSONObject("rewards").put("tokenSteps", JSONObject("""{"steps":[{"percent":100,"tokens":1},{"percent":200,"tokens":2},{"percent":300,"tokens":3},{"percent":400,"tokens":4},{"percent":500,"tokens":5},{"percent":600,"tokens":5}]}""")) },
            "a step's percent as text" to reading { it.getJSONObject("rewards").put("tokenSteps", JSONObject("""{"steps":[{"percent":"100","tokens":1}]}""")) },
            "a step's tokens with a fraction" to reading { it.getJSONObject("rewards").put("tokenSteps", JSONObject("""{"steps":[{"percent":100,"tokens":1.5}]}""")) },
            "a step with a field more" to reading { it.getJSONObject("rewards").put("tokenSteps", JSONObject("""{"steps":[{"percent":100,"tokens":1,"minutes":60}]}""")) },
            "a step without its tokens" to reading { it.getJSONObject("rewards").put("tokenSteps", JSONObject("""{"steps":[{"percent":100}]}""")) },
            "steps of one revision" to reading { it.getJSONObject("rewards").put("tokenSteps", JSONArray("""[{"value":$defaultStepsJson}]""")) },
            "steps that change to what they were" to reading { it.getJSONObject("rewards").put("tokenSteps", JSONArray("""[{"value":$defaultStepsJson},{"value":$defaultStepsJson,"from":"2026-10-14"}]""")) },
            "steps that changed to six tokens on a day" to reading { it.getJSONObject("rewards").put("tokenSteps", JSONArray("""[{"value":$defaultStepsJson},{"value":{"steps":[{"percent":100,"tokens":6}]},"from":"2026-10-14"}]""")) },
            "steps beside the bank" to reading { it.put("tokenSteps", JSONObject(defaultStepsJson)) },
            "the steps and the number they came from" to reading { it.getJSONObject("rewards").put("tokensPerWeek", 2) },
            "Reading earning no token a week" to reading { it.getJSONObject("rewards").apply { remove("tokenSteps") }.put("tokensPerWeek", 0) },
            "Reading earning six tokens a week" to reading { it.getJSONObject("rewards").apply { remove("tokenSteps") }.put("tokensPerWeek", 6) },
            "tokens a week below none" to reading { it.getJSONObject("rewards").apply { remove("tokenSteps") }.put("tokensPerWeek", -2) },
            "tokens a week beyond any count of steps" to reading { it.getJSONObject("rewards").apply { remove("tokenSteps") }.put("tokensPerWeek", Int.MAX_VALUE) },
            "tokens a week beyond any count of steps on a day" to reading { it.getJSONObject("rewards").apply { remove("tokenSteps") }.put("tokensPerWeek", JSONArray("""[{"value":2},{"value":${Int.MAX_VALUE},"from":"2026-10-14"}]""")) },
            "tokens a week as text" to reading { it.getJSONObject("rewards").apply { remove("tokenSteps") }.put("tokensPerWeek", "2") },
            "tokens a week with a fraction" to reading { it.getJSONObject("rewards").apply { remove("tokenSteps") }.put("tokensPerWeek", 2.5) },
            "null tokens a week" to reading { it.getJSONObject("rewards").apply { remove("tokenSteps") }.put("tokensPerWeek", JSONObject.NULL) },
            "tokens a week of one revision" to reading { it.getJSONObject("rewards").apply { remove("tokenSteps") }.put("tokensPerWeek", JSONArray("""[{"value":2}]""")) },
            "tokens a week that change to what they were" to reading { it.getJSONObject("rewards").apply { remove("tokenSteps") }.put("tokensPerWeek", JSONArray("""[{"value":2},{"value":2,"from":"2026-10-14"}]""")) },
            "tokens a week that changed to six on a day" to reading { it.getJSONObject("rewards").apply { remove("tokenSteps") }.put("tokensPerWeek", JSONArray("""[{"value":2},{"value":6,"from":"2026-10-14"}]""")) },
            "tokens a week beside the bank" to reading { it.put("tokensPerWeek", 2) },
            "a made view with a step not above the one before" to made { it.getJSONObject("rewards").put("tokenSteps", JSONObject("""{"steps":[{"percent":100,"tokens":1},{"percent":200,"tokens":2},{"percent":200,"tokens":3}]}""")) },
            "a made view earning six tokens a week" to made { it.getJSONObject("rewards").apply { remove("tokenSteps") }.put("tokensPerWeek", 6) },
            "a made view earning no token a week from a day" to made { it.getJSONObject("rewards").apply { remove("tokenSteps") }.put("tokensPerWeek", JSONArray("""[{"value":2},{"value":0,"from":"2026-10-14"}]""")) },
            "Reading asking fewer minutes on holiday" to reading { it.put("whenAway", "lowerTarget") },
            "Reading with an unknown away rule" to reading { it.put("whenAway", "sometimes") },
            "Reading with an away rule of one revision" to reading { it.put("whenAway", JSONArray("""[{"value":"excused"}]""")) },
            "Reading with a reminder" to reading { it.put("remind", true) },
            "Reading with a gap of its own" to reading { it.put("gapSize", 15) },
            "Reading with a target" to reading { it.put("target", JSONObject("""{"per":"week","amount":60,"unit":"minutes"}""")) },
            "a goal of nothing" to reading { it.put("goal", 0) },
            "a goal below nothing" to reading { it.put("goal", -60) },
            "a goal of more than a week" to reading { it.put("goal", 10081) },
            "a goal as text" to reading { it.put("goal", "60") },
            "a goal with a fraction" to reading { it.put("goal", 60.5) },
            "a null goal" to reading { it.put("goal", JSONObject.NULL) },
            "a goal of one revision" to reading { it.put("goal", JSONArray("""[{"value":60}]""")) },
            "a goal that changes to what it was" to reading { it.put("goal", JSONArray("""[{"value":60},{"value":60,"from":"2026-10-14"}]""")) },
            "a goal that changed to nothing on a day" to reading { it.put("goal", JSONArray("""[{"value":60},{"value":0,"from":"2026-10-14"}]""")) },
            "a goal whose revisions are out of order" to reading { it.put("goal", JSONArray("""[{"value":60},{"value":90,"from":"2026-10-14"},{"value":45,"from":"2026-10-13"}]""")) },
            "a made view under Reading's ID" to made { it.put("id", "reading") },
            "a made view under a questions view's ID" to made { it.put("id", "qst_k2p9") },
            "a made view under an ID one character short" to made { it.put("id", "tim_k2p") },
            "a made view under an ID one character long" to made { it.put("id", "tim_k2p9x") },
            "a made view under an ID in capitals" to made { it.put("id", "tim_K2P9") },
            "an ID that is no text" to made { it.put("id", 7) },
            "a made view with Reading's bank" to made { it.getJSONObject("rewards").remove("periods") },
            "a made view with a null bank" to made { it.put("rewards", JSONObject.NULL) },
            "a made view with a bank that earns by kept days" to made { it.getJSONObject("rewards").put("earn", JSONObject("""{"everyKeptDays":7}""")) },
            "periods that overlap" to made { it.getJSONObject("rewards").put("periods", JSONArray("""[{"from":"2026-10-13","until":"2026-10-16"},{"from":"2026-10-15"}]""")) },
            "an open period before another" to made { it.getJSONObject("rewards").put("periods", JSONArray("""[{"from":"2026-10-13"},{"from":"2026-10-15"}]""")) },
            "a period without a start" to made { it.getJSONObject("rewards").put("periods", JSONArray("""[{"until":"2026-10-15"}]""")) },
            "a period that starts on no date" to made { it.getJSONObject("rewards").put("periods", JSONArray("""[{"from":"2026-02-30"}]""")) },
            "no checkpoint" to made { it.remove("checkpoints") },
            "an empty list of checkpoints" to made { it.put("checkpoints", JSONArray()) },
            "a second checkpoint" to made { it.getJSONArray("checkpoints").put(JSONObject("""{"id":"evening","at":"21:00","remind":false}""")) },
            "another checkpoint ID" to made { it.getJSONArray("checkpoints").getJSONObject(0).put("id", "cpt_k2p9") },
            "a named checkpoint" to made { it.getJSONArray("checkpoints").getJSONObject(0).put("label", "Morning") },
            "a dated checkpoint" to made { it.getJSONArray("checkpoints").getJSONObject(0).put("from", "2026-10-13") },
            "a checkpoint at no time" to made { it.getJSONArray("checkpoints").getJSONObject(0).put("at", "noon") },
            "a made view asking fewer minutes on holiday" to made { it.put("whenAway", "lowerTarget") },
            "the day it was made" to made { it.put("madeOn", "2026-10-13") },
            "a unit" to made { it.put("unit", "h") },
        ) + listOf("view", "id", "label", "icon", "hue", "goal", "whenAway").flatMap { field ->
            listOf("Reading with no $field" to reading { it.remove(field) }, "a made view with no $field" to made { it.remove(field) })
        } + (MoMotif.entries - TimerMotifs.toSet()).flatMap { motif ->
            // Every motif a checklist can wear and Mo has nothing for while a timer runs.
            listOf("Reading in the flavor ${motif.storageId}" to reading { it.put("motif", motif.storageId) }, "a made view in the flavor ${motif.storageId}" to made { it.put("motif", motif.storageId) })
        }
        assertEquals(5, MoMotif.entries.size - TimerMotifs.size)
        refused.forEach { (what, raw) -> refused(what, raw) }
    }

    /**
     * A log is taken exactly as written or not at all: one that breaks a rule, or that is written
     * in a form the version would never write, makes the state unreadable. It is never left out,
     * merged with another of its date or given minutes it did not have.
     */
    @Test fun version29RefusesALogItCannotHoldAndRepairsNothing() {
        val valid = JSONObject(AppStateJsonCodec.encodeVersion30(state))
        fun root(change: (JSONObject) -> Unit) = JSONObject(valid.toString()).apply(change).toString()
        fun stored(change: (JSONArray) -> Unit) = root { change(it.getJSONArray("timeLogs")) }
        fun readings(change: (JSONObject) -> Unit) = stored { change(it.getJSONObject(0)) }
        fun pianos(change: (JSONObject) -> Unit) = stored { change(it.getJSONObject(1)) }
        val first = valid.getJSONArray("timeLogs").getJSONObject(0).toString()
        val pianosLog = valid.getJSONArray("timeLogs").getJSONObject(1).toString()

        val refused = listOf(
            "no list" to root { it.remove("timeLogs") },
            "a list that is none" to root { it.put("timeLogs", JSONObject()) },
            "a log that is no object" to stored { it.put("2026-10-12") },
            "a null log" to stored { it.put(JSONObject.NULL) },
            "a blank view" to readings { it.put("time", "") },
            "a view of spaces" to readings { it.put("time", " ") },
            "a view that is a number" to readings { it.put("time", 7) },
            "a date that is none" to readings { it.put("dateKey", "2026-02-30") },
            "a date with space around it" to readings { it.put("dateKey", " 2026-10-14 ") },
            "a date written short" to readings { it.put("dateKey", "2026-10-9") },
            "a date that is a number" to readings { it.put("dateKey", 20261014) },
            "a time before the epoch" to readings { it.put("timestampMillis", -1) },
            "a time as text" to readings { it.put("timestampMillis", "30") },
            "a time with a fraction" to readings { it.put("timestampMillis", 30.5) },
            "a null time" to readings { it.put("timestampMillis", JSONObject.NULL) },
            "no minutes at all" to readings { it.put("minutes", 0) },
            "minutes below none" to readings { it.put("minutes", -5) },
            "more minutes than a day has" to readings { it.put("minutes", 1441) },
            "minutes as text" to readings { it.put("minutes", "25") },
            "minutes with a fraction" to readings { it.put("minutes", 25.5) },
            "null minutes" to readings { it.put("minutes", JSONObject.NULL) },
            "a made view's log without minutes" to pianos { it.remove("minutes") },
            "a log without minutes of a view nothing knows" to stored { it.getJSONObject(3).remove("minutes") },
            "two logs of one view for one date" to stored { it.put(JSONObject(first).put("timestampMillis", 31).put("minutes", 5)) },
            "a log twice" to stored { it.put(JSONObject(first)) },
            "two logs of a made view for one date" to stored { it.put(JSONObject(pianosLog).put("timestampMillis", 99)) },
            "the minutes under their old name" to readings { it.put("durationMinutes", it.remove("minutes")) },
            "the old name beside the minutes" to readings { it.put("durationMinutes", 25) },
            "an ID" to readings { it.put("id", "2026-10-14:30") },
            "the logs of version 28 beside the logs" to root { it.put("readingLogs", JSONArray()) },
        ) + listOf("time", "dateKey", "timestampMillis").map { field -> "a log with no $field" to readings { it.remove(field) } }
        refused.forEach { (what, raw) -> refused(what, raw) }

        // What the rules allow side by side: two views with a log each for one date, and a log of a view nothing knows.
        val decoded = AppStateJsonCodec.decodeVersion30(valid.toString())
        assertEquals(2, decoded.timeLogs.count { it.dateKey == "2026-10-14" })
        assertEquals(1, decoded.timeLogs.single { it.viewId == "tim_gone" }.minutes)
    }

    /**
     * Every version from 4 to 28 held Reading's logs under `readingLogs`. Read by its own decoder
     * each comes back as one timer log of `reading`, field for field, in the same order.
     * No version before 29 holds a time view.
     */
    @Test fun everyOlderVersionsReadingLogsBecomeLogsOfReadingOneForOne() {
        val readingLogs = listOf(
            TimeLog(ReadingTimeId, "2026-09-15", 300L, 45),
            TimeLog(ReadingTimeId, "2026-09-14", 200L, 1440),
            TimeLog(ReadingTimeId, "2026-09-13", 100L),
            TimeLog(ReadingTimeId, "2026-09-11", 0L, 1),
        )
        val current = AppState(timeLogs = readingLogs).withWeight().withEating().withReading().withActivity()
        val decoders: Map<Int, (String) -> AppState> = mapOf(
            4 to AppStateJsonCodec::decodeVersion4, 5 to AppStateJsonCodec::decodeVersion5, 7 to AppStateJsonCodec::decodeVersion7,
            8 to AppStateJsonCodec::decodeVersion8, 10 to AppStateJsonCodec::decodeVersion10, 16 to AppStateJsonCodec::decodeVersion16,
            22 to AppStateJsonCodec::decodeVersion22, 24 to AppStateJsonCodec::decodeVersion24, 26 to AppStateJsonCodec::decodeVersion26,
            27 to AppStateJsonCodec::decodeVersion27, 28 to AppStateJsonCodec::decodeVersion28,
        )
        decoders.forEach { (version, decode) ->
            val stored = legacyTrackerJson(current, version)
            assertFalse("version $version", stored.has("time") || stored.has("timeLogs"))
            assertEquals("version $version", readingLogs.size, stored.getJSONArray("readingLogs").length())
            assertEquals("version $version", setOf("dateKey", "timestampMillis", "durationMinutes"), stored.getJSONArray("readingLogs").getJSONObject(0).keys().asSequence().toSet())
            assertEquals("version $version", setOf("dateKey", "timestampMillis"), stored.getJSONArray("readingLogs").getJSONObject(2).keys().asSequence().toSet())

            val decoded = decode(stored.toString())
            assertEquals("version $version", current.timeLogs, decoded.timeLogs)
            assertEquals("version $version", readingLogs, decoded.timeLogs.filter { it.viewId == ReadingTimeId })
            assertEquals("version $version", emptyList<Any>(), decoded.time)
            // Written as the current version, with the configurations seeded, it is the same list again.
            assertEquals("version $version", readingLogs, AppStateJsonCodec.decodeVersion30(AppStateJsonCodec.encodeVersion30(decoded.withWeight().withEating().withReading().withActivity())).timeLogs.filter { it.viewId == ReadingTimeId })
        }
        // Before version 4 there were none.
        assertEquals(emptyList<TimeLog>(), AppStateJsonCodec.decodeVersion3(legacyTrackerJson(current.copy(timeLogs = emptyList()), 3).toString()).timeLogs)
    }

    /**
     * A version 28 payload written out as that version wrote it, with logs that have minutes, a
     * mark without, three logs for one date and two that cannot be read, becomes timer logs after
     * the original repairs: of a date the newest wins, and an unreadable log is left out.
     */
    @Test fun aVersion28PayloadMigratesDirectlyToTimerLogsWithoutInventingMinutes() {
        val decoded = AppStateJsonCodec.decodeVersion28(frozenVersion28(FrozenReadingLogsOfVersion28))
        assertEquals(
            listOf(
                TimeLog("reading", "2026-10-12", 1_760_292_000_000, 25),
                TimeLog("reading", "2026-10-11", 1_760_205_600_000, 1440),
                TimeLog("reading", "2026-10-10", 1_760_119_200_000, null),
                TimeLog("reading", "2026-10-09", 200, 40),
                TimeLog("reading", "2026-10-07", 0, 1),
            ),
            decoded.timeLogs,
        )
        val asItWasRead = listOf(
            TimeLog(ReadingTimeId, "2026-10-12", 1_760_292_000_000, 25),
            TimeLog(ReadingTimeId, "2026-10-11", 1_760_205_600_000, 1440),
            TimeLog(ReadingTimeId, "2026-10-10", 1_760_119_200_000),
            TimeLog(ReadingTimeId, "2026-10-09", 200, 40),
            TimeLog(ReadingTimeId, "2026-10-07", 0, 1),
        )
        assertEquals(asItWasRead, decoded.timeLogs.filter { it.viewId == ReadingTimeId })
        assertEquals(emptyList<Any>(), decoded.time)

        // Seeded and written as version 29, it holds those five logs and reads back as them.
        val written = AppStateJsonCodec.encodeVersion30(decoded.withReading().withActivity())
        val migrated = AppStateJsonCodec.decodeVersion30(written)
        assertEquals(asItWasRead, migrated.timeLogs.filter { it.viewId == ReadingTimeId })
        assertEquals(decoded.timeLogs, migrated.timeLogs)
        assertEquals(listOf(testReadingTime()), migrated.time)
        assertEquals(5, JSONObject(written).getJSONArray("timeLogs").length())
        assertEquals(setOf("time", "dateKey", "timestampMillis"), JSONObject(written).getJSONArray("timeLogs").getJSONObject(2).keys().asSequence().toSet())
    }

    /** The payload other tests hand version 28 is what the one written out by hand is. */
    @Test fun theVersion28PayloadOtherTestsWriteIsTheOneThatVersionWrote() {
        val seeded = AppState(
            timeLogs = listOf(TimeLog(ReadingTimeId, "2026-10-12", 1_760_292_000_000, 25), TimeLog(ReadingTimeId, "2026-10-10", 1_760_119_200_000)),
        ).withWeight().withEating().withReading().withActivity()
        val frozen = frozenVersion28("""[{"dateKey":"2026-10-12","timestampMillis":1760292000000,"durationMinutes":25},{"dateKey":"2026-10-10","timestampMillis":1760119200000}]""")
        assertEquals(plainJson(JSONObject(frozen)), plainJson(legacyTrackerJson(seeded, 28)))
        assertEquals(seeded.readBefore29(), AppStateJsonCodec.decodeVersion28(frozen))
    }

    /** The two shapes never stand in one state: version 29 refuses `readingLogs`, and every older version `time` and `timeLogs`. */
    @Test fun version29RefusesReadingLogsAndEveryOlderVersionRefusesTimeViews() {
        val onlyReading = state.copy(time = listOf(testReadingTime()), timeLogs = logs.filter { it.viewId == "reading" })
        val current = AppStateJsonCodec.encodeVersion30(onlyReading)
        val version28 = claimedAs(current, 28)
        assertEquals(emptyList<Any>(), AppStateJsonCodec.decodeVersion28(version28.toString()).time)
        assertEquals(2, AppStateJsonCodec.decodeVersion28(version28.toString()).timeLogs.filter { it.viewId == ReadingTimeId }.size)

        refused("a version 28 payload claiming to be version 29", JSONObject(version28.toString()).put("schemaVersion", 29).toString())
        refused("a version 28 payload", version28.toString())
        assertThrows(RuntimeException::class.java) { AppStateJsonCodec.decodeVersion28(current) }
        assertThrows(RuntimeException::class.java) { AppStateJsonCodec.decodeVersion28(JSONObject(current).put("schemaVersion", 28).toString()) }
        val views = JSONObject(current).get("time")
        val stored = JSONObject(current).get("timeLogs")
        listOf<(JSONObject) -> Unit>({ it.put("time", views) }, { it.put("timeLogs", stored) }, { it.put("time", JSONArray()) }, { it.put("timeLogs", JSONArray()) }).forEach { change ->
            assertThrows(RuntimeException::class.java) { AppStateJsonCodec.decodeVersion28(JSONObject(version28.toString()).apply(change).toString()) }
            assertThrows(RuntimeException::class.java) { AppStateJsonCodec.decodeVersion27(claimedAs(current, 27).apply(change).toString()) }
            assertThrows(RuntimeException::class.java) { AppStateJsonCodec.decodeVersion23(claimedAs(current, 23).apply(change).toString()) }
            assertThrows(RuntimeException::class.java) { AppStateJsonCodec.decodeVersion4(legacyTrackerJson(AppState().withWeight().withEating().withReading().withActivity(), 4).apply(change).toString()) }
        }
        assertTrue(JSONObject(current).has("time"))
    }

    /** Book, Timer, Language and Art joined the icons in version 29: a view of any kind may wear each from then on, and an older version refuses it there. */
    @Test fun theIconsOfVersion29AreRefusedByOlderVersionsOnEveryKindOfView() {
        val arrived = ViewIcon.entries.filter { it.introducedInFormat == 29 }
        assertEquals(listOf(ViewIcon.Book, ViewIcon.Timer, ViewIcon.Language, ViewIcon.Art), arrived)
        val seeded = AppState().withWeight().withEating().withReading().withActivity()
        arrived.forEach { icon ->
            val worn = listOf(
                seeded.copy(checklists = listOf(testSupplementsChecklist().copy(icon = icon))),
                seeded.copy(measurements = seeded.measurements + testMadeMeasurement().copy(icon = icon)),
                seeded.copy(questions = seeded.questions + testMadeQuestions().copy(icon = icon)),
                seeded.copy(time = seeded.time + testMadeTime().copy(icon = icon)),
            )
            worn.forEach { state -> assertEquals(icon.storageId, state, AppStateJsonCodec.decodeVersion30(AppStateJsonCodec.encodeVersion30(state))) }
            // The same payload in an older version's shape is one that version never wrote.
            worn.take(3).forEach { state ->
                assertThrows(icon.storageId, RuntimeException::class.java) { AppStateJsonCodec.decodeVersion28(state.encodedAsVersion(28).toString()) }
                assertThrows(icon.storageId, RuntimeException::class.java) { AppStateJsonCodec.decodeVersion27(state.encodedAsVersion(27).toString()) }
                assertThrows(icon.storageId, RuntimeException::class.java) { legacyTrackerJson(state, 28) }
            }
            assertThrows(icon.storageId, RuntimeException::class.java) { AppStateJsonCodec.decodeVersion23(worn.first().encodedAsVersion(23).toString()) }
        }
        // With an icon version 28 knew, the same payloads are ones it reads.
        assertEquals(seeded.readBefore29(), AppStateJsonCodec.decodeVersion28(seeded.encodedAsVersion(28).toString()))
    }
}

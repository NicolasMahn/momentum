package com.example.workouttracker.persistence.json

import com.example.workouttracker.domain.compatibility.EatingQuestionsId
import com.example.workouttracker.domain.questions.eatingEntry
import com.example.workouttracker.domain.AppState
import com.example.workouttracker.domain.checklist.AboutDay
import com.example.workouttracker.domain.checklist.ChecklistRewards
import com.example.workouttracker.domain.checklist.Checkpoint
import com.example.workouttracker.domain.checklist.CheckpointTime
import com.example.workouttracker.domain.checklist.Dated
import com.example.workouttracker.domain.checklist.RewardPeriod
import com.example.workouttracker.domain.checklist.ViewHue
import com.example.workouttracker.domain.checklist.ViewIcon
import com.example.workouttracker.domain.checklist.WhenAway
import com.example.workouttracker.domain.measurement.withWeight
import com.example.workouttracker.domain.questions.Answer
import com.example.workouttracker.domain.questions.Question
import com.example.workouttracker.domain.questions.QuestionEntry
import com.example.workouttracker.domain.questions.TestQuestionsMadeOn
import com.example.workouttracker.domain.questions.testEatingQuestions
import com.example.workouttracker.domain.compatibility.eatingQuestions
import java.time.LocalDate
import com.example.workouttracker.domain.questions.testMadeQuestions
import com.example.workouttracker.domain.questions.testQuestion
import com.example.workouttracker.domain.questions.withEating
import com.example.workouttracker.persistence.FrozenReflectionsOfVersion26
import com.example.workouttracker.persistence.claimedAs
import com.example.workouttracker.persistence.frozenVersion26
import com.example.workouttracker.persistence.legacyTrackerJson
import com.example.workouttracker.persistence.plainJson
import com.example.workouttracker.persistence.readBefore27
import java.time.LocalTime
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import com.example.workouttracker.domain.time.withReading
import com.example.workouttracker.domain.activity.withActivity
import com.example.workouttracker.persistence.readBefore29

class QuestionsJsonTest {
    private val day = TestQuestionsMadeOn

    /** A made view with a help text, in the streak about yesterday from a day, its rules and its holiday rule dated, reminding after waking. */
    private val sleep = testMadeQuestions(
        questions = listOf(
            Question("qn_rest", "How rested do you feel?", listOf(Answer("an_low", "Not at all"), Answer("an_high", "Fully")), help = "Think of the whole night.\nNot only the morning."),
            testQuestion(id = "qn_dreams", text = "Träume? 🙂", answers = 3),
        ),
    ).copy(
        about = Dated(listOf(Dated.Revision(AboutDay.Today), Dated.Revision(AboutDay.Yesterday, day.plusDays(4)))),
        rewards = ChecklistRewards(
            maxPasses = Dated(listOf(Dated.Revision(3), Dated.Revision(2, day.plusDays(4)))),
            everyKeptDays = Dated.always(7),
            periods = listOf(RewardPeriod(day, day.plusDays(1)), RewardPeriod(day.plusDays(3))),
        ),
        whenAway = Dated(listOf(Dated.Revision(WhenAway.Due), Dated.Revision(WhenAway.Excused, day.plusDays(5)))),
        checkpoint = Checkpoint("day", CheckpointTime.AfterWake(20), remind = true),
    )

    private val entries = listOf(
        QuestionEntry("eating-1", "eating", "2026-10-14", 30, mapOf("quality" to "good", "amount" to "about_the_same"), "Late lunch"),
        QuestionEntry("sleep-1", sleep.id, "2026-10-14", 20, mapOf("qn_rest" to "an_high", "qn_gone" to "an_old")),
        QuestionEntry("eating-2", "eating", "2026-10-13", 10),
        QuestionEntry("gone-1", "qst_gone", "2026-10-13", 0, mapOf("qn_any" to "a1"), note = ""),
    )

    private val state = AppState(questionEntries = entries).withWeight().withEating().withReading().withActivity().let { it.copy(questions = it.questions + sleep) }

    /** Refused by the current version, and by version 27, which first held questions views, handed the payload in its own shape. */
    private fun refused(what: String, raw: String) {
        assertThrows(what, RuntimeException::class.java) { AppStateJsonCodec.decodeVersion30(raw) }
        assertThrows("version 27: $what", RuntimeException::class.java) { AppStateJsonCodec.decodeVersion27(claimedAs(raw, 27).toString()) }
    }

    @Test fun eatingsDefaultIsStoredInTheShapeOfItsKind() {
        val stored = JSONObject(AppStateJsonCodec.encodeVersion30(AppState().withWeight().withEating().withReading().withActivity()))
        val expected = JSONArray(
            """[{"view":"questions","id":"eating","label":"Eating","icon":"food","hue":"orange","about":"yesterday",""" +
                """"rewards":{"maxPasses":3,"earn":{"everyKeptDays":7}},"whenAway":"due","checkpoints":[{"id":"day","at":"wake+15","remind":false}]}]""",
        )
        assertEquals(plainJson(expected), plainJson(stored.getJSONArray("questions")))
        assertEquals(0, stored.getJSONArray("questionEntries").length())
        assertEquals(30, stored.getInt("schemaVersion"))
        assertFalse(stored.has("eatingReflections"))
        assertEquals(AppState().withWeight().withEating().withReading().withActivity(), AppStateJsonCodec.decodeVersion30(stored.toString()))
    }

    /** A made view holds its questions, each with its answers in order and its help where it has one, its checkpoint and the day it was made. */
    @Test fun aMadeViewIsStoredWithItsQuestionsItsCheckpointAndTheDayItWasMade() {
        val plain = testMadeQuestions(questions = listOf(testQuestion(answers = 2)))
        val stored = JSONObject(AppStateJsonCodec.encodeVersion30(AppState().withWeight().withEating().withReading().withActivity().let { it.copy(questions = it.questions + plain + sleep.copy(id = "qst_m7xd")) })).getJSONArray("questions")
        assertEquals(
            plainJson(
                JSONObject(
                    """{"view":"questions","id":"qst_k2p9","label":"Sleep","icon":"sleep","hue":"navy",""" +
                        """"questions":[{"id":"qn_rest","text":"How rested do you feel?","answers":[{"id":"a1","label":"Level 1"},{"id":"a2","label":"Level 2"}]}],""" +
                        """"about":"today","whenAway":"due","checkpoints":[{"id":"day","at":"09:00","remind":false}],"madeOn":"2026-10-13"}""",
                ),
            ),
            plainJson(stored.getJSONObject(1)),
        )
        assertEquals(
            plainJson(
                JSONObject(
                    """{"view":"questions","id":"qst_m7xd","label":"Sleep","icon":"sleep","hue":"navy","questions":[""" +
                        """{"id":"qn_rest","text":"How rested do you feel?","answers":[{"id":"an_low","label":"Not at all"},{"id":"an_high","label":"Fully"}],"help":"Think of the whole night.\nNot only the morning."},""" +
                        """{"id":"qn_dreams","text":"Träume? 🙂","answers":[{"id":"a1","label":"Level 1"},{"id":"a2","label":"Level 2"},{"id":"a3","label":"Level 3"}]}],""" +
                        """"about":[{"value":"today"},{"value":"yesterday","from":"2026-10-17"}],""" +
                        """"rewards":{"maxPasses":[{"value":3},{"value":2,"from":"2026-10-17"}],"earn":{"everyKeptDays":7},"periods":[{"from":"2026-10-13","until":"2026-10-14"},{"from":"2026-10-16"}]},""" +
                        """"whenAway":[{"value":"due"},{"value":"excused","from":"2026-10-18"}],""" +
                        """"checkpoints":[{"id":"day","at":"wake+20","remind":true}],"madeOn":"2026-10-13"}""",
                ),
            ),
            plainJson(stored.getJSONObject(2)),
        )
    }

    /** An entry names its view; "Not sure" stores nothing, so a day with no answer holds an empty object, and a note is left out where there is none. */
    @Test fun anEntryIsStoredWithItsViewItsAnswersAndItsNote() {
        val stored = JSONObject(AppStateJsonCodec.encodeVersion30(state)).getJSONArray("questionEntries")
        assertEquals(
            plainJson(
                JSONArray(
                    """[{"id":"eating-1","questions":"eating","dateKey":"2026-10-14","timestampMillis":30,"answers":{"quality":"good","amount":"about_the_same"},"note":"Late lunch"},""" +
                        """{"id":"sleep-1","questions":"qst_k2p9","dateKey":"2026-10-14","timestampMillis":20,"answers":{"qn_rest":"an_high","qn_gone":"an_old"}},""" +
                        """{"id":"eating-2","questions":"eating","dateKey":"2026-10-13","timestampMillis":10,"answers":{}},""" +
                        """{"id":"gone-1","questions":"qst_gone","dateKey":"2026-10-13","timestampMillis":0,"answers":{"qn_any":"a1"},"note":""}]""",
                ),
            ),
            plainJson(stored),
        )
    }

    /** What is stored is read back as it was, every configuration and every entry, and written again it is the same bytes. */
    @Test fun versions27And28RoundTripEveryViewAndEveryEntry() {
        val eatingChanged = testEatingQuestions().copy(
            label = "Food",
            icon = ViewIcon.Heart,
            hue = ViewHue.Rust,
            rewards = ChecklistRewards(Dated(listOf(Dated.Revision(3), Dated.Revision(5, day))), Dated(listOf(Dated.Revision(7), Dated.Revision(4, day), Dated.Revision(10, day.plusDays(7))))),
            whenAway = Dated(listOf(Dated.Revision(WhenAway.Due), Dated.Revision(WhenAway.Excused, day), Dated.Revision(WhenAway.Due, day.plusDays(2)))),
        )
        val forms = listOf(
            state,
            state.withEating(eatingChanged),
            // Weight need not come first, and Eating need not either.
            state.copy(questions = listOf(sleep, testEatingQuestions())),
            state.copy(
                questions = state.questions + listOf(
                    testMadeQuestions(id = "qst_aaa1", questions = (1..5).map { testQuestion(id = "qn_$it", answers = it.coerceAtLeast(2)) }),
                    testMadeQuestions(id = "qst_aaa2", about = AboutDay.Yesterday).copy(rewards = ChecklistRewards(3, 7, periods = emptyList()), whenAway = Dated.always(WhenAway.Excused)),
                    testMadeQuestions(id = "qst_aaa3").copy(checkpoint = Checkpoint("day", CheckpointTime.BeforeSleep(15), remind = true)),
                    testMadeQuestions(id = "qst_aaa4").copy(checkpoint = Checkpoint("day", CheckpointTime.Fixed(LocalTime.of(21, 30)), remind = false)),
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
            // Versions 27 and 28 held every view and every entry as the current version does.
            assertEquals(form.readBefore29(), AppStateJsonCodec.decodeVersion27(legacyTrackerJson(form, 27).toString()))
            assertEquals(form.readBefore29(), AppStateJsonCodec.decodeVersion28(legacyTrackerJson(form, 28).toString()))
        }
    }

    /** Entries come back in the order the state holds them, whatever order they were stored in. */
    @Test fun entriesComeBackByDateNewestFirst() {
        val raw = JSONObject(AppStateJsonCodec.encodeVersion30(state)).apply {
            val stored = getJSONArray("questionEntries")
            put("questionEntries", JSONArray((0 until stored.length()).map(stored::get).reversed()))
        }.toString()
        assertEquals(entries, AppStateJsonCodec.decodeVersion30(raw).questionEntries)
    }

    /**
     * What `questions` holds is Eating at most once, complete, and made views by their own rules.
     * Anything else is refused, never repaired or replaced by the default. Version 27 held Eating
     * exactly once, without a checkpoint and without periods, which arrived in version 30.
     */
    @Test fun versions27And28RefuseAQuestionsViewTheyCannotHoldAndRepairNothing() {
        val valid = JSONObject(AppStateJsonCodec.encodeVersion30(state))
        assertEquals(state, AppStateJsonCodec.decodeVersion30(valid.toString()))
        fun root(change: (JSONObject) -> Unit) = JSONObject(valid.toString()).apply(change).toString()
        fun views(change: (JSONArray) -> Unit) = root { change(it.getJSONArray("questions")) }
        fun eating(change: (JSONObject) -> Unit) = views { change(it.getJSONObject(0)) }
        fun made(change: (JSONObject) -> Unit) = views { change(it.getJSONObject(1)) }
        fun question(change: (JSONObject) -> Unit) = made { change(it.getJSONArray("questions").getJSONObject(0)) }
        fun answer(change: (JSONObject) -> Unit) = question { change(it.getJSONArray("answers").getJSONObject(0)) }
        val storedEating = valid.getJSONArray("questions").getJSONObject(0).toString()
        val storedMade = valid.getJSONArray("questions").getJSONObject(1)
        val oneQuestion = storedMade.getJSONArray("questions").getJSONObject(1).toString()

        // Eating's checkpoint is its own from version 30, where version 27 reads it with the one the migration builds.
        assertThrows(RuntimeException::class.java) { AppStateJsonCodec.decodeVersion30(eating { it.remove("checkpoints") }) }
        val eating = state.eatingQuestions!!
        listOf(
            "an empty list" to views { it.remove(1); it.remove(0) } to emptyList(),
            "no Eating" to views { it.remove(0) } to listOf(sleep),
            "Eating with periods" to eating { it.getJSONObject("rewards").put("periods", JSONArray("""[{"from":"2026-10-13"}]""")) } to
                listOf(eating.copy(rewards = eating.rewards?.copy(periods = listOf(RewardPeriod(LocalDate.parse("2026-10-13"))))), sleep),
            "Eating with a checkpoint of its own" to eating { it.put("checkpoints", JSONArray("""[{"id":"day","at":"09:00","remind":true}]""")) } to
                listOf(eating.copy(checkpoint = Checkpoint("day", CheckpointTime.Fixed(LocalTime.of(9, 0)), remind = true)), sleep),
        ).forEach { (case, questions) ->
            val (what, raw) = case
            assertEquals(what, questions, AppStateJsonCodec.decodeVersion30(raw).questions)
            assertThrows("version 27: $what", RuntimeException::class.java) { AppStateJsonCodec.decodeVersion27(claimedAs(raw, 27).toString()) }
        }

        val refused = listOf(
            "no list" to root { it.remove("questions") },
            "a list that is none" to root { it.put("questions", JSONObject()) },
            "a second Eating" to views { it.put(JSONObject(storedEating)) },
            "a made view twice" to views { it.put(JSONObject(storedMade.toString())) },
            "a view that is no object" to views { it.put("eating") },
            "another kind of view" to eating { it.put("view", "checklist") },
            "an unknown icon" to eating { it.put("icon", "fork") },
            "an unknown hue" to eating { it.put("hue", "beige") },
            "a blank name" to eating { it.put("label", " ") },
            "Eating with stored questions" to eating { it.put("questions", JSONArray().put(JSONObject(oneQuestion))) },
            "Eating with no questions, said so" to eating { it.put("questions", JSONArray()) },
            "Eating about today" to eating { it.put("about", "today") },
            "Eating about today from a day" to eating { it.put("about", JSONArray("""[{"value":"yesterday"},{"value":"today","from":"2026-10-14"}]""")) },
            "Eating without its bank" to eating { it.remove("rewards") },
            "Eating with a null bank" to eating { it.put("rewards", JSONObject.NULL) },
            "Eating with a week bank" to eating { it.put("rewards", JSONObject("""{"maxPasses":7,"earn":{"keptDaysInWeek":6}}""")) },
            "Eating with a bank of none" to eating { it.getJSONObject("rewards").put("maxPasses", 0) },
            "Eating with no checkpoint, said so" to eating { it.put("checkpoints", JSONArray()) },
            "Eating with the day it was made" to eating { it.put("madeOn", "2026-10-13") },
            "Eating asking fewer times on holiday" to eating { it.put("whenAway", "lowerTarget") },
            "Eating with an unknown away rule" to eating { it.put("whenAway", "sometimes") },
            "Eating with an away rule of one revision" to eating { it.put("whenAway", JSONArray("""[{"value":"excused"}]""")) },
            "Eating with a reminder" to eating { it.put("remind", true) },
            "a made view under Eating's ID" to made { it.put("id", "eating") },
            "a made view under a measurement's ID" to made { it.put("id", "msr_k2p9") },
            "a made view under an ID one character short" to made { it.put("id", "qst_k2p") },
            "no questions" to made { it.remove("questions") },
            "an empty list of questions" to made { it.put("questions", JSONArray()) },
            "six questions" to made { view -> (3..6).forEach { view.getJSONArray("questions").put(JSONObject(oneQuestion).put("id", "qn_$it")) } },
            "a question twice" to made { it.getJSONArray("questions").put(JSONObject(oneQuestion)) },
            "a question that is no object" to made { it.getJSONArray("questions").put("How?") },
            "a question without an ID" to question { it.remove("id") },
            "a question without text" to question { it.remove("text") },
            "a question with empty text" to question { it.put("text", "") },
            "a question with space around its text" to question { it.put("text", " How rested? ") },
            "a question over two lines" to question { it.put("text", "How\nrested?") },
            "a question of 121 characters" to question { it.put("text", "x".repeat(121)) },
            "a question without answers" to question { it.remove("answers") },
            "a question with one answer" to question { it.getJSONArray("answers").remove(1) },
            "a question with six answers" to question { q -> (3..6).forEach { q.getJSONArray("answers").put(JSONObject().put("id", "an_$it").put("label", "More")) } },
            "an answer twice" to question { it.getJSONArray("answers").put(JSONObject(it.getJSONArray("answers").getJSONObject(0).toString())) },
            "an answer without an ID" to answer { it.remove("id") },
            "an answer without a label" to answer { it.remove("label") },
            "an answer with an empty label" to answer { it.put("label", "") },
            "an answer of 25 characters" to answer { it.put("label", "x".repeat(25)) },
            "an answer over two lines" to answer { it.put("label", "Not\nat all") },
            "an answer with a color" to answer { it.put("hue", "orange") },
            "an empty help text" to question { it.put("help", "") },
            "a help text with space around it" to question { it.put("help", "Think.\n") },
            "a help text of 501 characters" to question { it.put("help", "x".repeat(501)) },
            "a null help text" to question { it.put("help", JSONObject.NULL) },
            "a help text per answer" to answer { it.put("help", "Never") },
            "a made view with Eating's bank" to made { it.getJSONObject("rewards").remove("periods") },
            "a made view with a week bank" to made { it.put("rewards", JSONObject("""{"maxPasses":7,"earn":{"keptDaysInWeek":6}}""")) },
            "a made view with a null bank" to made { it.put("rewards", JSONObject.NULL) },
            "no checkpoint" to made { it.remove("checkpoints") },
            "an empty list of checkpoints" to made { it.put("checkpoints", JSONArray()) },
            "a second checkpoint" to made { it.getJSONArray("checkpoints").put(JSONObject("""{"id":"evening","at":"21:00","remind":false}""")) },
            "another checkpoint ID" to made { it.getJSONArray("checkpoints").getJSONObject(0).put("id", "cpt_k2p9") },
            "a named checkpoint" to made { it.getJSONArray("checkpoints").getJSONObject(0).put("label", "Morning") },
            "a dated checkpoint" to made { it.getJSONArray("checkpoints").getJSONObject(0).put("from", "2026-10-13") },
            "no day it was made" to made { it.remove("madeOn") },
            "a day it was made that is no date" to made { it.put("madeOn", "2026-02-30") },
            "a made view asking fewer times on holiday" to made { it.put("whenAway", "lowerTarget") },
            "an unknown about" to made { it.put("about", "tomorrow") },
            "a unit" to made { it.put("unit", "h") },
        ) + listOf("view", "id", "label", "icon", "hue", "about", "whenAway").flatMap { field ->
            listOf("Eating with no $field" to eating { it.remove(field) }, "a made view with no $field" to made { it.remove(field) })
        }
        refused.forEach { (what, raw) -> refused(what, raw) }
    }

    /**
     * An entry is taken exactly as written or not at all: one that breaks a rule, or that is
     * written in a form the version would never write, makes the state unreadable. It is never
     * left out, trimmed, merged with another or given another ID.
     */
    @Test fun versions27And28RefuseAnEntryTheyCannotHoldAndRepairNothing() {
        val valid = JSONObject(AppStateJsonCodec.encodeVersion30(state))
        fun root(change: (JSONObject) -> Unit) = JSONObject(valid.toString()).apply(change).toString()
        fun stored(change: (JSONArray) -> Unit) = root { change(it.getJSONArray("questionEntries")) }
        fun eating(change: (JSONObject) -> Unit) = stored { change(it.getJSONObject(0)) }
        fun made(change: (JSONObject) -> Unit) = stored { change(it.getJSONObject(1)) }
        val first = valid.getJSONArray("questionEntries").getJSONObject(0).toString()

        val refused = listOf(
            "no list" to root { it.remove("questionEntries") },
            "a list that is none" to root { it.put("questionEntries", JSONObject()) },
            "an entry that is no object" to stored { it.put("eating-3") },
            "a null entry" to stored { it.put(JSONObject.NULL) },
            "a blank ID" to eating { it.put("id", " ") },
            "an ID that is a number" to eating { it.put("id", 7) },
            "a blank view" to eating { it.put("questions", "") },
            "a view that is a number" to eating { it.put("questions", 7) },
            "a date that is none" to eating { it.put("dateKey", "2026-02-30") },
            "a date with space around it" to eating { it.put("dateKey", " 2026-10-14 ") },
            "a date written short" to eating { it.put("dateKey", "2026-10-9") },
            "a time before the epoch" to eating { it.put("timestampMillis", -1) },
            "a time as text" to eating { it.put("timestampMillis", "30") },
            "a time with a fraction" to eating { it.put("timestampMillis", 30.5) },
            "a null time" to eating { it.put("timestampMillis", JSONObject.NULL) },
            "answers as a list" to eating { it.put("answers", JSONArray().put("good")) },
            "null answers" to eating { it.put("answers", JSONObject.NULL) },
            "an answer that is a number" to eating { it.getJSONObject("answers").put("quality", 3) },
            "a null answer" to eating { it.getJSONObject("answers").put("quality", JSONObject.NULL) },
            "a blank answer" to made { it.getJSONObject("answers").put("qn_rest", " ") },
            "an answer to a blank question" to made { it.getJSONObject("answers").put("", "an_high") },
            "an answer Eating does not have" to eating { it.getJSONObject("answers").put("quality", "superb") },
            "an answer of Eating's other question" to eating { it.getJSONObject("answers").put("quality", "much_less") },
            "a question Eating does not have" to eating { it.getJSONObject("answers").put("mood", "good") },
            "a note of 241 characters" to eating { it.put("note", "x".repeat(241)) },
            "a note that is a number" to eating { it.put("note", 7) },
            "a null note" to eating { it.put("note", JSONObject.NULL) },
            "an ID twice" to stored { it.put(JSONObject(first).put("dateKey", "2026-10-01")) },
            "an ID twice, in two views" to stored { it.put(JSONObject(first).put("questions", "qst_m7xd").put("answers", JSONObject())) },
            "two entries of one view for one date" to stored { it.put(JSONObject(first).put("id", "eating-3")) },
            "the quality beside the answers" to eating { it.put("quality", "good") },
            "the reflections of version 26 beside the entries" to root { it.put("eatingReflections", JSONArray()) },
        ) + listOf("id", "questions", "dateKey", "timestampMillis", "answers").map { field -> "an entry with no $field" to eating { it.remove(field) } }
        refused.forEach { (what, raw) -> refused(what, raw) }

        // What a made view's entry may hold that Eating's may not: an answer its view no longer has.
        assertEquals("an_old", AppStateJsonCodec.decodeVersion30(valid.toString()).questionEntries.first { it.id == "sleep-1" }.answers["qn_gone"])
    }

    /**
     * Every version from 7 to 26 held Eating's reflections under `eatingReflections`. Read by its
     * own decoder each comes back as one entry of `eating`, field for field, in the same order,
     * without filling absent answers; no version before 27 holds a questions view.
     */
    @Test fun everyOlderVersionsReflectionsBecomeEntriesOfEatingOneForOne() {
        val reflections = listOf(
            eatingEntry("both", "2026-09-15", 300L, "very_good", "a_little_more", "Dinner with friends"),
            eatingEntry("quality", "2026-09-14", 200L, quality = "poor"),
            eatingEntry("amount", "2026-09-13", 100L, amount = "much_less"),
            eatingEntry("none", "2026-09-12", 50L),
            eatingEntry("note", "2026-09-11", 0L, note = "Travelling"),
        )
        val current = AppState(questionEntries = reflections).withWeight().withEating().withReading().withActivity()
        val decoders: Map<Int, (String) -> AppState> = mapOf(
            7 to AppStateJsonCodec::decodeVersion7, 8 to AppStateJsonCodec::decodeVersion8, 9 to AppStateJsonCodec::decodeVersion9,
            10 to AppStateJsonCodec::decodeVersion10, 16 to AppStateJsonCodec::decodeVersion16, 22 to AppStateJsonCodec::decodeVersion22,
            23 to AppStateJsonCodec::decodeVersion23, 24 to AppStateJsonCodec::decodeVersion24, 25 to AppStateJsonCodec::decodeVersion25,
            26 to AppStateJsonCodec::decodeVersion26,
        )
        decoders.forEach { (version, decode) ->
            val stored = legacyTrackerJson(current, version)
            assertFalse("version $version", stored.has("questions") || stored.has("questionEntries"))
            assertEquals("version $version", reflections.size, stored.getJSONArray("eatingReflections").length())
            assertEquals("version $version", setOf("id", "dateKey", "timestampMillis", "quality", "amount", "note"), stored.getJSONArray("eatingReflections").getJSONObject(0).keys().asSequence().toSet())

            val decoded = decode(stored.toString())
            assertEquals("version $version", current.questionEntries, decoded.questionEntries)
            assertEquals("version $version", reflections, decoded.questionEntries.filter { it.viewId == EatingQuestionsId })
            assertEquals("version $version", emptyList<Any>(), decoded.questions)
            // Written as the current version, with Eating's configuration seeded, it is the same list again.
            assertEquals("version $version", reflections, AppStateJsonCodec.decodeVersion30(AppStateJsonCodec.encodeVersion30(decoded.withWeight().withEating().withReading().withActivity())).questionEntries.filter { it.viewId == EatingQuestionsId })
        }
        // Before version 7 there were none.
        assertEquals(emptyList<QuestionEntry>(), AppStateJsonCodec.decodeVersion6(legacyTrackerJson(current.copy(questionEntries = emptyList()), 6).toString()).questionEntries)
    }

    /** A version 26 payload written out as that version wrote it reads as entries of Eating, and back as the reflections it held. */
    @Test fun aVersion26PayloadReadsAsEntriesAndBackThroughTheReflectionsFieldForField() {
        val decoded = AppStateJsonCodec.decodeVersion26(frozenVersion26(FrozenReflectionsOfVersion26))
        assertEquals(
            listOf(
                QuestionEntry("both", "eating", "2026-09-15", 1_757_950_400_000, mapOf("quality" to "very_good", "amount" to "a_little_more"), "Dinner with friends"),
                QuestionEntry("quality", "eating", "2026-09-14", 1_757_860_400_000, mapOf("quality" to "poor")),
                QuestionEntry("amount", "eating", "2026-09-13", 1_757_774_000_000, mapOf("amount" to "much_less")),
                QuestionEntry("none", "eating", "2026-09-12", 1_757_687_600_000),
                QuestionEntry("note", "eating", "2026-09-11", 0, note = "Travelling"),
            ),
            decoded.questionEntries,
        )
        assertEquals(
            listOf(
                eatingEntry("both", "2026-09-15", 1_757_950_400_000, "very_good", "a_little_more", "Dinner with friends"),
                eatingEntry("quality", "2026-09-14", 1_757_860_400_000, quality = "poor"),
                eatingEntry("amount", "2026-09-13", 1_757_774_000_000, amount = "much_less"),
                eatingEntry("none", "2026-09-12", 1_757_687_600_000),
                eatingEntry("note", "2026-09-11", 0, note = "Travelling"),
            ),
            decoded.questionEntries.filter { it.viewId == EatingQuestionsId },
        )
    }

    /** The two shapes never stand in one state: versions 27 and 28 refuse `eatingReflections`, and every older version `questions` and `questionEntries`. */
    @Test fun versions27And28RefuseReflectionsAndEveryOlderVersionRefusesQuestions() {
        val current = AppStateJsonCodec.encodeVersion30(state.copy(questions = listOf(testEatingQuestions()), questionEntries = entries.filter { it.viewId == "eating" }))
        val version26 = claimedAs(current, 26)
        assertEquals(state.readBefore27().questions, AppStateJsonCodec.decodeVersion26(version26.toString()).questions)
        assertEquals(2, AppStateJsonCodec.decodeVersion26(version26.toString()).questionEntries.filter { it.viewId == EatingQuestionsId }.size)

        refused("a version 26 payload claiming to be version 27", JSONObject(version26.toString()).put("schemaVersion", 27).toString())
        refused("a version 26 payload", version26.toString())
        assertThrows(RuntimeException::class.java) { AppStateJsonCodec.decodeVersion26(current) }
        assertThrows(RuntimeException::class.java) { AppStateJsonCodec.decodeVersion26(JSONObject(current).put("schemaVersion", 26).toString()) }
        val views = JSONObject(current).get("questions")
        val stored = JSONObject(current).get("questionEntries")
        listOf<(JSONObject) -> Unit>({ it.put("questions", views) }, { it.put("questionEntries", stored) }, { it.put("questions", JSONArray()) }, { it.put("questionEntries", JSONArray()) }).forEach { change ->
            assertThrows(RuntimeException::class.java) { AppStateJsonCodec.decodeVersion26(JSONObject(version26.toString()).apply(change).toString()) }
            assertThrows(RuntimeException::class.java) { AppStateJsonCodec.decodeVersion23(claimedAs(current, 23).apply(change).toString()) }
            assertThrows(RuntimeException::class.java) { AppStateJsonCodec.decodeVersion7(legacyTrackerJson(AppState().withWeight().withEating().withReading().withActivity(), 7).apply(change).toString()) }
        }
        assertTrue(JSONObject(current).has("questions"))
    }
}

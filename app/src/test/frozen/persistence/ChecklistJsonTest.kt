package com.example.workouttracker.persistence.json

import com.example.workouttracker.persistence.json.introducedInFormat

import com.example.workouttracker.persistence.readBefore23
import com.example.workouttracker.domain.measurement.withWeight
import com.example.workouttracker.domain.AppState
import com.example.workouttracker.domain.checklist.AboutDay
import com.example.workouttracker.domain.checklist.ChecklistConfiguration
import com.example.workouttracker.domain.checklist.ChecklistDay
import com.example.workouttracker.domain.checklist.ChecklistItem
import com.example.workouttracker.domain.checklist.ChecklistRewards
import com.example.workouttracker.domain.checklist.Dated
import com.example.workouttracker.domain.checklist.MoMotif
import com.example.workouttracker.domain.checklist.RewardPeriod
import com.example.workouttracker.domain.checklist.WhenAway
import com.example.workouttracker.domain.checklist.Checkpoint
import com.example.workouttracker.domain.checklist.CheckpointTime
import com.example.workouttracker.domain.checklist.ViewHue
import com.example.workouttracker.domain.checklist.ViewIcon
import com.example.workouttracker.domain.care.testCareChecklist
import com.example.workouttracker.persistence.legacyTrackerJson
import com.example.workouttracker.persistence.encodedAsVersion
import com.example.workouttracker.persistence.readBefore19
import com.example.workouttracker.domain.supplement.supplementDay
import com.example.workouttracker.domain.supplement.testSupplementsChecklist
import java.time.LocalDate
import java.time.LocalTime
import java.time.Month
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import com.example.workouttracker.domain.checklist.ChecklistTarget
import com.example.workouttracker.domain.questions.withEating
import com.example.workouttracker.domain.time.withReading
import com.example.workouttracker.domain.activity.withActivity

class ChecklistJsonTest {
    /** The second example of the contract, with every field Block 4 stores. */
    private val medication = ChecklistConfiguration(
        id = "chk_7f3a",
        label = "Medikamente",
        icon = ViewIcon.Checklist,
        hue = ViewHue.Sky,
        checkpoints = listOf(Checkpoint("morning", CheckpointTime.AfterWake(30), label = "Morgens", remind = true, from = LocalDate.parse("2026-11-01"))),
        items = listOf(
            ChecklistItem("itm_k2p9", "Levothyroxin"),
            ChecklistItem("itm_m4zt", "Ibuprofen 400"),
            ChecklistItem("itm_q81d", "Eisen", months = setOf(Month.NOVEMBER, Month.JANUARY), until = LocalDate.parse("2026-11-30")),
        ),
    )

    @Test fun configurationsAndTheirDaysSurviveTheStateEncoding() {
        val state = AppState(
            checklists = listOf(testSupplementsChecklist(remind = true), medication),
            checklistDays = listOf(
                ChecklistDay("chk_7f3a", "2026-11-30", 2L, setOf("morning/itm_k2p9", "morning/itm_q81d")),
                supplementDay("2026-11-30", 1L, setOf("creatine", "omega_3")),
            ),
        ).withWeight().withEating().withReading().withActivity()
        assertEquals(state.copy(checklistDays = state.checklistDays.sortedByDescending { it.timestampMillis }), AppStateJsonCodec.decodeVersion30(AppStateJsonCodec.encodeVersion30(state)))
    }

    @Test fun theStoredShapeIsTheContractsAndKeepsTheSeasonInItsOwnOrder() {
        val season = setOf(Month.OCTOBER, Month.NOVEMBER, Month.DECEMBER, Month.JANUARY, Month.FEBRUARY, Month.MARCH)
        val stored = JSONObject(AppStateJsonCodec.encodeVersion30(AppState(checklists = listOf(testSupplementsChecklist())).withWeight().withEating().withReading().withActivity()))
            .getJSONArray("checklists").getJSONObject(0)
        val expected = JSONObject(
            """{"view":"checklist","id":"supplements","label":"Supplements","icon":"medication","hue":"purple","motif":"pills",""" +
                """"checkpoints":[{"id":"midday","at":"12:30","remind":false}],"target":{"per":"day"},""" +
                """"rewards":{"maxPasses":3,"earn":{"everyKeptDays":7}},"items":[{"id":"multivitamin","label":"Multivitamin"},""" +
                """{"id":"creatine","label":"Creatine"},{"id":"vitamin_d","label":"Vitamin D","months":[10,11,12,1,2,3],"from":"2026-10-01"}]}""",
        )
        assertEquals(plain(expected), plain(stored))
        assertEquals(season, testSupplementsChecklist().items.last().months)
    }

    /** A configuration decides how days are judged, so one that cannot be read exactly is never replaced. */
    @Test fun aConfigurationThatCannotBeReadExactlyMakesTheStateUnreadable() {
        val valid = JSONObject(AppStateJsonCodec.encodeVersion30(AppState(checklists = listOf(medication)).withWeight().withEating().withReading().withActivity()))
        fun changed(change: (JSONObject) -> Unit) = JSONObject(valid.toString()).apply { change(getJSONArray("checklists").getJSONObject(0)) }.toString()
        listOf(
            changed { it.put("hue", "turquoise") },
            changed { it.put("icon", "rocket") },
            changed { it.put("view", "reading") },
            changed { it.remove("label") },
            changed { it.put("whenAway", "excused") },
            changed { it.put("target", JSONObject().put("per", "month")) },
            changed { it.put("target", JSONObject().put("per", "week").put("times", 8)) },
            changed { it.getJSONArray("items").getJSONObject(0).put("target", JSONObject().put("per", "week").put("times", 3).put("awayTimes", 3)) },
            changed { it.getJSONArray("items").getJSONObject(0).put("target", JSONObject().put("per", "week").put("times", "3")) },
            changed { it.getJSONArray("checkpoints").getJSONObject(0).put("at", "8:00") },
            changed { it.getJSONArray("checkpoints").getJSONObject(0).put("remind", "yes") },
            changed { it.getJSONArray("items").getJSONObject(2).put("months", org.json.JSONArray(listOf(11, 11))) },
            changed { it.getJSONArray("items").getJSONObject(2).put("months", org.json.JSONArray(listOf(13))) },
            changed { it.getJSONArray("items").getJSONObject(0).put("checkpoints", org.json.JSONArray(listOf("evening"))) },
            changed { it.getJSONArray("items").getJSONObject(0).put("checkpoints", org.json.JSONArray(listOf("morning", "morning"))) },
            changed { it.getJSONArray("items").getJSONObject(0).put("target", JSONObject().put("weekdays", org.json.JSONArray(listOf("SAT", "SAT")))) },
            changed { it.getJSONArray("items").getJSONObject(0).put("target", JSONObject().put("weekdays", org.json.JSONArray(listOf("Saturday")))) },
            changed { it.getJSONArray("items").getJSONObject(0).put("target", JSONObject().put("weekdays", org.json.JSONArray())) },
            changed { it.getJSONArray("checkpoints").put(JSONObject().put("id", "morning").put("at", "21:00").put("remind", false)) },
            changed { it.getJSONArray("checkpoints").getJSONObject(0).put("at", "wake+05") },
            JSONObject(valid.toString()).apply { getJSONArray("checklists").put(getJSONArray("checklists").getJSONObject(0)) }.toString(),
            changed { it.getJSONObject("target").put("whenAway", "lowerTarget") },
            changed { it.getJSONObject("target").put("whenAway", "sometimes") },
            changed { it.getJSONArray("items").getJSONObject(0).put("target", JSONObject().put("per", "day").put("whenAway", "excused")) },
            changed { it.getJSONArray("items").getJSONObject(0).put("whenAway", "sometimes") },
            // Only an item due more than once a week can ask fewer times, and no more often than usual.
            changed { it.getJSONArray("items").getJSONObject(0).put("target", JSONObject().put("weekdays", org.json.JSONArray(listOf("SAT")))).put("whenAway", "lowerTarget") },
            changed { it.getJSONArray("items").getJSONObject(0).put("awayTimes", 7) },
            changed { it.getJSONArray("items").getJSONObject(0).put("awayTimes", -1) },
            changed { it.put("motif", "stars") },
        ).forEach { raw -> assertThrows(raw, RuntimeException::class.java) { AppStateJsonCodec.decodeVersion30(raw) } }
    }

    /**
     * What an away day means sits on each item from version 16, left unwritten while it is due as
     * ever. Before, the checklist said it once in its target, from version 11: an older payload's
     * rule comes back on each of its items, so every day is judged as it was; version 16 no longer
     * reads it there, version 15 never stored it on an item, and version 10 never stored it at all.
     */
    @Test fun whenAwayMovedFromTheChecklistsTargetToEachItemInVersion16() {
        val excused = medication.copy(items = medication.items.map { if (it.id == "itm_k2p9") it.copy(whenAway = Dated.always(WhenAway.Excused)) else it })
        val state = AppState(checklists = listOf(excused)).withWeight().withEating().withReading().withActivity()
        assertEquals(state, AppStateJsonCodec.decodeVersion30(AppStateJsonCodec.encodeVersion30(state)))
        val items = JSONObject(AppStateJsonCodec.encodeVersion30(state)).getJSONArray("checklists").getJSONObject(0).getJSONArray("items")
        assertEquals("excused", items.getJSONObject(0).getString("whenAway"))
        assertEquals(false, items.getJSONObject(1).has("whenAway"))
        // An item that says nothing is due while away, as the contract's default.
        val silent = JSONObject(AppStateJsonCodec.encodeVersion30(state)).apply { getJSONArray("checklists").getJSONObject(0).getJSONArray("items").getJSONObject(0).remove("whenAway") }
        assertEquals(AppState(checklists = listOf(medication)).withWeight().withEating().withReading().withActivity(), AppStateJsonCodec.decodeVersion30(silent.toString()))

        val allExcused = AppState(checklists = listOf(medication.copy(items = medication.items.map { it.copy(whenAway = Dated.always(WhenAway.Excused)) }))).withWeight().withEating().withReading().withActivity()
        val version15 = legacyTrackerJson(allExcused, 15)
        assertEquals("excused", version15.getJSONArray("checklists").getJSONObject(0).getJSONObject("target").getString("whenAway"))
        assertEquals(allExcused.readBefore19(), AppStateJsonCodec.decodeVersion15(version15.toString()))
        assertThrows(RuntimeException::class.java) { AppStateJsonCodec.decodeVersion16(JSONObject(version15.toString()).put("schemaVersion", 16).toString()) }
        assertThrows(RuntimeException::class.java) { AppStateJsonCodec.decodeVersion15(state.encodedAsVersion(15).toString()) }
        val version10 = legacyTrackerJson(AppState(checklists = listOf(medication)), 10).apply { getJSONArray("checklists").getJSONObject(0).getJSONObject("target").put("whenAway", "excused") }
        assertThrows(RuntimeException::class.java) { AppStateJsonCodec.decodeVersion10(version10.toString()) }
    }

    /**
     * A checklist-wide lowered target reached only its week items; every other item stayed due. So
     * reads a version 15 payload, revisions and all, and the days of an away week are judged exactly
     * as before. A version 16 item cannot lower what is not a week target, however it got there.
     */
    @Test fun aLoweredChecklistOfVersion15ReadsAsLoweredWeekItemsAndDueDailyItems() {
        val day = LocalDate.parse("2026-11-02")
        val mixed = medication.copy(
            items = listOf(
                ChecklistItem("itm_k2p9", "Levothyroxin"),
                ChecklistItem("itm_m4zt", "Ibuprofen 400", target = ChecklistTarget.PerWeek(times = 3), awayTimes = 1, whenAway = Dated.always(WhenAway.Due).changedFrom(day, WhenAway.LowerTarget)),
            ),
        )
        val state = AppState(checklists = listOf(mixed)).withWeight().withEating().withReading().withActivity()
        val version15 = legacyTrackerJson(state, 15)
        assertEquals(
            listOf(mapOf("value" to "due"), mapOf("value" to "lowerTarget", "from" to "2026-11-02")),
            plain(version15.getJSONArray("checklists").getJSONObject(0).getJSONObject("target").get("whenAway")),
        )
        assertEquals(state.readBefore19(), AppStateJsonCodec.decodeVersion15(version15.toString()))
        val saturdayLowered = JSONObject(AppStateJsonCodec.encodeVersion30(state)).apply {
            getJSONArray("checklists").getJSONObject(0).getJSONArray("items").getJSONObject(0)
                .put("target", JSONObject().put("weekdays", org.json.JSONArray(listOf("SAT")))).put("whenAway", "lowerTarget")
        }
        assertThrows(RuntimeException::class.java) { AppStateJsonCodec.decodeVersion30(saturdayLowered.toString()) }
    }

    /**
     * How many times a week on holiday asks sits on the item from version 18, for a daily or
     * some-days item as for a week item. Versions 15 to 17 kept it in a week target, the item's own
     * or the checklist's for an item without one: it comes back on the item, so every week item keeps
     * its count; version 18 refuses it in a target, and an older version refuses it on an item.
     */
    @Test fun howManyTimesAWeekOnHolidayAsksMovedToTheItemInVersion18() {
        val lowered = medication.copy(
            items = listOf(
                ChecklistItem("itm_k2p9", "Levothyroxin", awayTimes = 3, whenAway = Dated.always(WhenAway.LowerTarget)),
                ChecklistItem("itm_m4zt", "Ibuprofen 400", target = ChecklistTarget.Weekdays(setOf(java.time.DayOfWeek.MONDAY, java.time.DayOfWeek.THURSDAY)), awayTimes = 1, whenAway = Dated.always(WhenAway.LowerTarget)),
                // A count is kept whichever rule the item has, so choosing fewer times again finds it.
                ChecklistItem("itm_q81d", "Eisen", target = ChecklistTarget.PerWeek(times = 4), awayTimes = 2),
            ),
        )
        val state = AppState(checklists = listOf(lowered)).withWeight().withEating().withReading().withActivity()
        assertEquals(state, AppStateJsonCodec.decodeVersion30(AppStateJsonCodec.encodeVersion30(state)))
        val items = JSONObject(AppStateJsonCodec.encodeVersion30(state)).getJSONArray("checklists").getJSONObject(0).getJSONArray("items")
        assertEquals(listOf(3, 1, 2), (0..2).map { items.getJSONObject(it).getInt("awayTimes") })
        assertEquals(mapOf("per" to "week", "times" to 4), plain(items.getJSONObject(2).getJSONObject("target")))
        // A lowered item without a count stays due as ever, and is stored without one.
        val uncounted = AppState(checklists = listOf(medication.copy(items = listOf(ChecklistItem("itm_k2p9", "Levothyroxin", whenAway = Dated.always(WhenAway.LowerTarget)))))).withWeight().withEating().withReading().withActivity()
        assertEquals(uncounted, AppStateJsonCodec.decodeVersion30(AppStateJsonCodec.encodeVersion30(uncounted)))
        assertEquals(false, JSONObject(AppStateJsonCodec.encodeVersion30(uncounted)).getJSONArray("checklists").getJSONObject(0).getJSONArray("items").getJSONObject(0).has("awayTimes"))

        // Version 17 kept a week item's count in its target, and refuses one on an item or on a daily item.
        val weekly = AppState(checklists = listOf(medication.copy(items = listOf(lowered.items[2])))).withWeight().withEating().withReading().withActivity()
        val version17 = legacyTrackerJson(weekly, 17)
        assertEquals(mapOf("per" to "week", "times" to 4, "awayTimes" to 2), plain(version17.getJSONArray("checklists").getJSONObject(0).getJSONArray("items").getJSONObject(0).getJSONObject("target")))
        assertEquals(weekly.readBefore19(), AppStateJsonCodec.decodeVersion17(version17.toString()))
        assertThrows(RuntimeException::class.java) { AppStateJsonCodec.decodeVersion17(weekly.encodedAsVersion(17).toString()) }
        assertThrows(RuntimeException::class.java) { legacyTrackerJson(state, 17) }
        assertThrows(RuntimeException::class.java) { AppStateJsonCodec.decodeVersion18(JSONObject(version17.toString()).put("schemaVersion", 18).toString()) }
        // The checklist's own week target could carry the count too; it reaches every item without a target of its own.
        val inherited = AppState(checklists = listOf(medication.copy(target = ChecklistTarget.PerWeek(times = 2), items = listOf(medication.items[0], medication.items[1].copy(target = ChecklistTarget.PerDay), medication.items[2])))).withWeight().withEating().withReading().withActivity()
            .encodedAsVersion(17)
            .apply { getJSONArray("checklists").getJSONObject(0).getJSONObject("target").put("awayTimes", 1) }
        val read = AppStateJsonCodec.decodeVersion17(inherited.toString()).checklists.single()
        assertEquals(ChecklistTarget.PerWeek(times = 2), read.target)
        assertEquals(listOf(1, null, 1), read.items.map { it.awayTimes })
    }

    /** Several checkpoints, and items that name theirs or their own target, arrived with version 10. */
    @Test fun anOlderVersionNeverHoldsWhatOnlyVersion10Wrote() {
        val valid = legacyTrackerJson(AppState(checklists = listOf(medication)), 9)
        AppStateJsonCodec.decodeVersion9(valid.toString())
        fun changed(change: (JSONObject) -> Unit) = JSONObject(valid.toString()).apply { change(getJSONArray("checklists").getJSONObject(0)) }.toString()
        listOf(
            changed { it.getJSONArray("items").getJSONObject(0).put("checkpoints", org.json.JSONArray(listOf("morning"))) },
            changed { it.getJSONArray("items").getJSONObject(0).put("target", JSONObject().put("weekdays", org.json.JSONArray(listOf("SAT")))) },
            changed { it.getJSONArray("checkpoints").put(JSONObject().put("id", "evening").put("at", "21:00").put("remind", false)) },
            changed { it.put("target", JSONObject().put("weekdays", org.json.JSONArray(listOf("SAT")))) },
            // Care's ID was free before Care became a checklist, but an older state never used it.
            changed { it.put("id", "care") },
        ).forEach { raw -> assertThrows(raw, RuntimeException::class.java) { AppStateJsonCodec.decodeVersion9(raw) } }
    }

    @Test fun caresConfigurationIsStoredInTheContractsShapeAndSurvivesTheEncoding() {
        val state = AppState(checklists = listOf(testSupplementsChecklist(), testCareChecklist(remindEvening = true))).withWeight().withEating().withReading().withActivity()
        assertEquals(state, AppStateJsonCodec.decodeVersion30(AppStateJsonCodec.encodeVersion30(state)))
        val stored = JSONObject(AppStateJsonCodec.encodeVersion30(state)).getJSONArray("checklists").getJSONObject(1)
        val expected = JSONObject(
            """{"view":"checklist","id":"care","label":"Care","icon":"toothbrush","hue":"teal","motif":"teeth",""" +
                """"checkpoints":[{"id":"morning","label":"Morning","at":"wake+5","remind":false},{"id":"evening","label":"Evening","at":"sleep-15","remind":true}],""" +
                """"target":{"per":"day"},"rewards":{"maxPasses":1,"earn":{"everyKeptDays":14}},"items":[""" +
                """{"id":"toothbrushing","label":"Brush teeth"},{"id":"hair_foam","label":"Hair foam"},""" +
                """{"id":"clean_between_teeth","label":"Clean between teeth","from":"2026-10-03","checkpoints":["evening"],"target":{"weekdays":["SAT"]}}]}""",
        )
        assertEquals(plain(expected), plain(stored))
    }

    @Test fun rewardPeriodsSurviveTheStateEncoding() {
        val joined = medication.copy(
            rewards = com.example.workouttracker.domain.checklist.ChecklistRewards(
                maxPasses = 3,
                everyKeptDays = 7,
                periods = listOf(
                    com.example.workouttracker.domain.checklist.RewardPeriod(LocalDate.parse("2026-10-05"), LocalDate.parse("2026-10-11")),
                    com.example.workouttracker.domain.checklist.RewardPeriod(LocalDate.parse("2026-10-19")),
                ),
            ),
        )
        val state = AppState(checklists = listOf(joined)).withWeight().withEating().withReading().withActivity()
        assertEquals(state, AppStateJsonCodec.decodeVersion30(AppStateJsonCodec.encodeVersion30(state)))
    }

    @Test fun legacySupplementLogsBecomeTicksAtTheOneCheckpointUnknownIdsIncluded() {
        val legacy = """{"schemaVersion":7,"weightLogs":[],"sessions":[],"activityTypes":[],"activityEntries":[],"readingLogs":[],"substanceLogs":[],"careCompletions":[],"eatingReflections":[],""" +
            """"supplementLogs":[{"dateKey":"2026-10-01","timestampMillis":5,"takenSupplementIds":[" creatine","omega_3"]}]}"""
        assertEquals(
            listOf(ChecklistDay("supplements", "2026-10-01", 5L, setOf("midday/creatine", "midday/omega_3"))),
            AppStateJsonCodec.decodeVersion7(legacy).checklistDays,
        )
    }

    /**
     * A setting that changed is stored as its revisions from version 12, and one that never changed
     * stays plain.
     */
    @Test fun datedSettingsSurviveTheStateEncoding() {
        val day = LocalDate.parse("2026-11-02")
        val changed = medication.copy(
            items = medication.items.map { if (it.id == "itm_k2p9") it.copy(whenAway = Dated.always(WhenAway.Due).changedFrom(day, WhenAway.Excused)) else it },
            rewards = ChecklistRewards(maxPasses = Dated.always(3).changedFrom(day, 5), everyKeptDays = Dated.always(7), periods = listOf(RewardPeriod(day))),
        )
        val state = AppState(checklists = listOf(changed)).withWeight().withEating().withReading().withActivity()
        assertEquals(state, AppStateJsonCodec.decodeVersion30(AppStateJsonCodec.encodeVersion30(state)))

        val stored = JSONObject(AppStateJsonCodec.encodeVersion30(state)).getJSONArray("checklists").getJSONObject(0)
        assertEquals(
            listOf(mapOf("value" to "due"), mapOf("value" to "excused", "from" to "2026-11-02")),
            plain(stored.getJSONArray("items").getJSONObject(0).get("whenAway")),
        )
        assertEquals(listOf(mapOf("value" to 3), mapOf("value" to 5, "from" to "2026-11-02")), plain(stored.getJSONObject("rewards").get("maxPasses")))
        assertEquals(7, stored.getJSONObject("rewards").getJSONObject("earn").get("everyKeptDays"))
    }

    /**
     * Versions 12 and 13 could put a checklist away. It comes back as an ordinary checklist with
     * everything it had, and the reward period archiving ended stays ended; the flag itself is
     * neither written nor accepted since version 14, and never was before 12.
     */
    @Test fun anArchivedChecklistOfVersion12Or13ReadsAsAnOrdinaryOne() {
        val day = LocalDate.parse("2026-11-02")
        val left = medication.copy(rewards = ChecklistRewards(3, 7, periods = listOf(RewardPeriod(day.minusDays(30), day.minusDays(1)))))
        val state = AppState(checklists = listOf(left), checklistDays = listOf(ChecklistDay("chk_7f3a", "2026-11-01", 2L, setOf("morning/itm_k2p9")))).withWeight().withEating().withReading().withActivity()
        fun archivedAs(version: Int, flag: Any = true) = state.encodedAsVersion(version)
            .apply { getJSONArray("checklists").getJSONObject(0).put("archived", flag) }
            .toString()

        assertEquals(state.readBefore19(), AppStateJsonCodec.decodeVersion12(archivedAs(12)))
        assertEquals(state.readBefore19(), AppStateJsonCodec.decodeVersion13(archivedAs(13)))
        assertEquals(false, AppStateJsonCodec.encodeVersion30(state).contains("archived"))
        listOf(
            { AppStateJsonCodec.decodeVersion14(archivedAs(14)) },
            { AppStateJsonCodec.decodeVersion11(archivedAs(11)) },
            // Those versions wrote the flag only as true.
            { AppStateJsonCodec.decodeVersion13(archivedAs(13, flag = false)) },
            { AppStateJsonCodec.decodeVersion13(archivedAs(13, flag = "yes")) },
        ).forEach { decode -> assertThrows(RuntimeException::class.java) { decode() } }
    }

    /** The borrowed motifs arrived in version 14; an older payload naming one is refused. */
    @Test fun onlyVersion14StoresABorrowedMotif() {
        MoMotif.entries.filter { it.introducedInFormat == 14 }.forEach { motif ->
            val state = AppState(checklists = listOf(medication.copy(motif = motif))).withWeight().withEating().withReading().withActivity()
            assertEquals(state, AppStateJsonCodec.decodeVersion30(AppStateJsonCodec.encodeVersion30(state)))
            assertEquals(motif.storageId, JSONObject(AppStateJsonCodec.encodeVersion30(state)).getJSONArray("checklists").getJSONObject(0).getString("motif"))
            val asVersion13 = state.encodedAsVersion(13).toString()
            assertThrows(motif.name, RuntimeException::class.java) { AppStateJsonCodec.decodeVersion13(asVersion13) }
        }
        val pills = AppState(checklists = listOf(medication.copy(motif = MoMotif.Pills))).withWeight().withEating().withReading().withActivity()
        assertEquals(pills.readBefore19(), AppStateJsonCodec.decodeVersion13(pills.encodedAsVersion(13).toString()))
    }

    /** Dated settings arrived in version 12; an older payload holding one is refused. */
    @Test fun onlyVersion12StoresDatedSettings() {
        val day = LocalDate.parse("2026-11-02")
        // As version 12 wrote it, under version 11's number: the rule sat in the checklist's target then.
        fun asVersion11(checklist: ChecklistConfiguration) = legacyTrackerJson(AppState(checklists = listOf(checklist)), 12).put("schemaVersion", 11).toString()
        listOf(
            medication.copy(items = medication.items.map { it.copy(whenAway = Dated.always(WhenAway.Due).changedFrom(day, WhenAway.Excused)) }),
            medication.copy(rewards = ChecklistRewards(Dated.always(3), Dated.always(7).changedFrom(day, 2))),
        ).forEach { checklist -> assertThrows(RuntimeException::class.java) { AppStateJsonCodec.decodeVersion11(asVersion11(checklist)) } }
        val plainVersion11 = asVersion11(medication)
        assertEquals(AppState(checklists = listOf(medication)).withWeight().withEating().withReading().withActivity().readBefore19(), AppStateJsonCodec.decodeVersion11(plainVersion11))

        val valid = JSONObject(AppStateJsonCodec.encodeVersion30(AppState(checklists = listOf(medication.copy(rewards = ChecklistRewards(3, 7)))).withWeight().withEating().withReading().withActivity()))
        fun changed(change: (JSONObject) -> Unit) = JSONObject(valid.toString()).apply { change(getJSONArray("checklists").getJSONObject(0)) }.toString()
        listOf(
            // A setting that never changed is written plainly, so one revision alone is not read.
            changed { it.getJSONArray("items").getJSONObject(0).put("whenAway", org.json.JSONArray().put(JSONObject().put("value", "due"))) },
            changed {
                it.getJSONObject("rewards").put(
                    "maxPasses",
                    org.json.JSONArray().put(JSONObject().put("value", 3)).put(JSONObject().put("value", 3).put("from", "2026-11-02")),
                )
            },
            changed {
                it.getJSONObject("rewards").put(
                    "maxPasses",
                    org.json.JSONArray().put(JSONObject().put("value", 3).put("from", "2026-11-01")).put(JSONObject().put("value", 4).put("from", "2026-11-02")),
                )
            },
        ).forEach { raw -> assertThrows(raw, RuntimeException::class.java) { AppStateJsonCodec.decodeVersion30(raw) } }
    }

    /** The deep hues arrived in version 13; an older payload naming one is refused. */
    @Test fun onlyVersion13StoresADeepHueOrANewIcon() {
        listOf(medication.copy(hue = ViewHue.Rust), medication.copy(icon = ViewIcon.Journal)).forEach { looked ->
            val state = AppState(checklists = listOf(looked)).withWeight().withEating().withReading().withActivity()
            assertEquals(state, AppStateJsonCodec.decodeVersion30(AppStateJsonCodec.encodeVersion30(state)))
            assertEquals(state.readBefore19(), AppStateJsonCodec.decodeVersion13(state.encodedAsVersion(13).toString()))
            val asVersion12 = state.encodedAsVersion(12).toString()
            assertThrows(RuntimeException::class.java) { AppStateJsonCodec.decodeVersion12(asVersion12) }
        }
        assertEquals(AppState(checklists = listOf(medication)).withWeight().withEating().withReading().withActivity().readBefore19(), AppStateJsonCodec.decodeVersion12(AppState(checklists = listOf(medication)).withWeight().withEating().withReading().withActivity().encodedAsVersion(12).toString()))
    }

    /**
     * An item due a number of times a week, with what a week away asks, and the lowered away rule
     * arrived in version 15: they survive the encoding in the contract's shape, and an older
     * payload holding either is refused.
     */
    @Test fun onlyVersion15StoresAWeekTargetOrALoweredTarget() {
        val weekly = medication.copy(
            items = medication.items.map { if (it.id == "itm_m4zt") it.copy(target = ChecklistTarget.PerWeek(times = 3), awayTimes = 1, whenAway = Dated.always(WhenAway.LowerTarget)) else it } +
                ChecklistItem("itm_floss", "Zahnseide", target = ChecklistTarget.PerWeek(), whenAway = Dated.always(WhenAway.LowerTarget)),
        )
        val state = AppState(checklists = listOf(weekly)).withWeight().withEating().withReading().withActivity()
        assertEquals(state, AppStateJsonCodec.decodeVersion30(AppStateJsonCodec.encodeVersion30(state)))
        val items = JSONObject(AppStateJsonCodec.encodeVersion30(state)).getJSONArray("checklists").getJSONObject(0).getJSONArray("items")
        assertEquals(mapOf("per" to "week", "times" to 3), plain(items.getJSONObject(1).getJSONObject("target")))
        assertEquals(1, items.getJSONObject(1).getInt("awayTimes"))
        assertEquals(mapOf("per" to "week", "times" to 1), plain(items.getJSONObject(3).getJSONObject("target")))
        assertEquals("lowerTarget", items.getJSONObject(1).getString("whenAway"))
        // The checklist's own target may be weekly too, and `times` left out reads as once a week.
        val wholeWeekly = AppState(checklists = listOf(medication.copy(target = ChecklistTarget.PerWeek(times = 2)))).withWeight().withEating().withReading().withActivity()
        assertEquals(wholeWeekly, AppStateJsonCodec.decodeVersion30(AppStateJsonCodec.encodeVersion30(wholeWeekly)))
        val onlyWeek = AppState(checklists = listOf(weekly.copy(items = weekly.items.map { it.copy(whenAway = Dated.always(WhenAway.Due)) }))).withWeight().withEating().withReading().withActivity()
        val bare = JSONObject(AppStateJsonCodec.encodeVersion30(onlyWeek))
            .apply { getJSONArray("checklists").getJSONObject(0).getJSONArray("items").getJSONObject(3).put("target", JSONObject().put("per", "week")) }
        assertEquals(onlyWeek, AppStateJsonCodec.decodeVersion30(bare.toString()))

        // Version 15 held both, the lowering said once for the checklist and the count in the week target; version 14 holds neither.
        val version15 = legacyTrackerJson(state, 15)
        assertEquals("lowerTarget", version15.getJSONArray("checklists").getJSONObject(0).getJSONObject("target").getString("whenAway"))
        assertEquals(mapOf("per" to "week", "times" to 3, "awayTimes" to 1), plain(version15.getJSONArray("checklists").getJSONObject(0).getJSONArray("items").getJSONObject(1).getJSONObject("target")))
        assertEquals(state.readBefore19(), AppStateJsonCodec.decodeVersion15(version15.toString()))
        assertThrows(RuntimeException::class.java) { AppStateJsonCodec.decodeVersion14(JSONObject(version15.toString()).put("schemaVersion", 14).toString()) }
        assertThrows(RuntimeException::class.java) { AppStateJsonCodec.decodeVersion14(legacyTrackerJson(onlyWeek, 15).put("schemaVersion", 14).toString()) }
        assertThrows(RuntimeException::class.java) { legacyTrackerJson(onlyWeek, 14) }
        assertEquals(AppState(checklists = listOf(medication)).withWeight().withEating().withReading().withActivity().readBefore19(), AppStateJsonCodec.decodeVersion14(legacyTrackerJson(AppState(checklists = listOf(medication)), 14).toString()))
    }

    /**
     * Which day a due day is about arrived in version 17, written only while it is not today, plain
     * or as its revisions; every older version reads a checklist as about today, and version 16
     * refuses the field.
     */
    @Test fun whichDayAChecklistIsAboutArrivedInVersion17() {
        val day = LocalDate.parse("2026-11-02")
        val aboutYesterday = AppState(checklists = listOf(medication.copy(about = Dated.always(AboutDay.Yesterday)))).withWeight().withEating().withReading().withActivity()
        assertEquals(aboutYesterday, AppStateJsonCodec.decodeVersion30(AppStateJsonCodec.encodeVersion30(aboutYesterday)))
        assertEquals("yesterday", JSONObject(AppStateJsonCodec.encodeVersion30(aboutYesterday)).getJSONArray("checklists").getJSONObject(0).getString("about"))
        assertEquals(false, JSONObject(AppStateJsonCodec.encodeVersion30(AppState(checklists = listOf(medication)).withWeight().withEating().withReading().withActivity())).getJSONArray("checklists").getJSONObject(0).has("about"))

        val switched = AppState(checklists = listOf(medication.copy(about = Dated.always(AboutDay.Today).changedFrom(day, AboutDay.Yesterday)))).withWeight().withEating().withReading().withActivity()
        assertEquals(switched, AppStateJsonCodec.decodeVersion30(AppStateJsonCodec.encodeVersion30(switched)))
        assertEquals(
            listOf(mapOf("value" to "today"), mapOf("value" to "yesterday", "from" to "2026-11-02")),
            plain(JSONObject(AppStateJsonCodec.encodeVersion30(switched)).getJSONArray("checklists").getJSONObject(0).get("about")),
        )

        val version16 = legacyTrackerJson(AppState(checklists = listOf(medication)), 16)
        assertEquals(AppState(checklists = listOf(medication)).withWeight().withEating().withReading().withActivity().readBefore19(), AppStateJsonCodec.decodeVersion16(version16.toString()))
        assertThrows(RuntimeException::class.java) { AppStateJsonCodec.decodeVersion16(aboutYesterday.encodedAsVersion(16).toString()) }
        assertThrows(RuntimeException::class.java) { legacyTrackerJson(aboutYesterday, 16) }
        val unknown = JSONObject(AppStateJsonCodec.encodeVersion30(aboutYesterday)).apply { getJSONArray("checklists").getJSONObject(0).put("about", "someday") }
        assertThrows(RuntimeException::class.java) { AppStateJsonCodec.decodeVersion30(unknown.toString()) }
        // A feature's checklist is about today; one stored otherwise cannot be read.
        val careYesterday = JSONObject(AppStateJsonCodec.encodeVersion30(AppState(checklists = listOf(testCareChecklist())).withWeight().withEating().withReading().withActivity())).apply { getJSONArray("checklists").getJSONObject(0).put("about", "yesterday") }
        assertThrows(RuntimeException::class.java) { AppStateJsonCodec.decodeVersion30(careYesterday.toString()) }
    }

    /** A page keeps its draft as one configuration, read back exactly as the current version wrote it. */
    @Test fun aDraftConfigurationReadsBackAsWritten() {
        val draft = medication.copy(
            motif = MoMotif.Pills,
            rewards = ChecklistRewards(maxPasses = 3, everyKeptDays = 7, periods = listOf(RewardPeriod(LocalDate.parse("2026-11-01")))),
            items = medication.items + ChecklistItem(
                "itm_w",
                "Walk",
                target = ChecklistTarget.PerWeek(3),
                awayTimes = 1,
                from = LocalDate.parse("2026-11-02"),
                whenAway = Dated.always(WhenAway.Due).changedFrom(LocalDate.parse("2026-11-20"), WhenAway.LowerTarget),
            ),
        )
        assertEquals(draft, AppStateJsonCodec.decodeChecklist(AppStateJsonCodec.encodeChecklist(draft)))
        assertEquals(draft, AppStateJsonCodec.decodeVersion30(AppStateJsonCodec.encodeVersion30(AppState(checklists = listOf(draft)).withWeight().withEating().withReading().withActivity())).checklists.single())
    }

    /**
     * Whether an item logs only misses and a day's misses arrived in version 22, each left out while
     * unused; version 21 refuses the setting and leaves out a day holding a miss, and every older
     * version reads as no misses and never only misses.
     */
    @Test fun onlyLoggingMissesAndADaysMissesArrivedInVersion22() {
        val day = LocalDate.parse("2026-11-02")
        val established = medication.copy(
            items = medication.items.map { item ->
                if (item.id == "itm_k2p9") item.copy(exceptionsOnly = Dated.always(false).changedFrom(day, true)) else item
            },
        )
        val state = AppState(
            checklists = listOf(established),
            checklistDays = listOf(
                ChecklistDay(established.id, "2026-11-03", 3L, ticks = emptySet(), misses = setOf("morning/itm_k2p9")),
                ChecklistDay(established.id, "2026-11-02", 2L, ticks = setOf("morning/itm_m4zt"), misses = setOf("morning/itm_k2p9")),
                ChecklistDay(established.id, "2026-11-01", 1L, ticks = setOf("morning/itm_k2p9")),
            ),
        ).withWeight().withEating().withReading().withActivity()
        val encoded = AppStateJsonCodec.encodeVersion30(state)
        assertEquals(state, AppStateJsonCodec.decodeVersion30(encoded))
        val stored = JSONObject(encoded)
        assertEquals(
            listOf(mapOf("value" to false), mapOf("value" to true, "from" to "2026-11-02")),
            plain(stored.getJSONArray("checklists").getJSONObject(0).getJSONArray("items").getJSONObject(0).get("exceptionsOnly")),
        )
        assertEquals(false, stored.getJSONArray("checklists").getJSONObject(0).getJSONArray("items").getJSONObject(1).has("exceptionsOnly"))
        assertEquals(mapOf("checklistId" to "chk_7f3a", "dateKey" to "2026-11-03", "timestampMillis" to 3, "ticks" to emptyList<String>(), "misses" to listOf("morning/itm_k2p9")), plain(stored.getJSONArray("checklistDays").getJSONObject(0)))
        assertEquals(false, stored.getJSONArray("checklistDays").getJSONObject(2).has("misses"))
        assertEquals(false, AppStateJsonCodec.lostRecords(encoded, AppStateJsonCodec.decodeVersion30(encoded)))

        // Version 21 refuses the setting outright, and leaves out a day with a miss as a record it cannot read.
        assertThrows(RuntimeException::class.java) { AppStateJsonCodec.decodeVersion21(AppState(checklists = listOf(established)).withWeight().withEating().withReading().withActivity().encodedAsVersion(21).toString()) }
        val missesIn21 = AppState(checklists = listOf(medication), checklistDays = state.checklistDays).withWeight().withEating().withReading().withActivity().encodedAsVersion(21).toString()
        assertEquals(listOf("2026-11-01"), AppStateJsonCodec.decodeVersion21(missesIn21).checklistDays.map { it.dateKey })
        assertThrows(RuntimeException::class.java) { legacyTrackerJson(state, 21) }
        val unchanged = AppState(checklists = listOf(medication), checklistDays = listOf(state.checklistDays.last())).withWeight().withEating().withReading().withActivity()
        assertEquals(unchanged.readBefore23(), AppStateJsonCodec.decodeVersion21(legacyTrackerJson(unchanged, 21).toString()))
    }

    /** A damaged list of misses leaves the day its ticks, and the loss is reported so the payload is kept. */
    @Test fun aDamagedMissIsLostInsideADayThatIsKept() {
        val state = AppState(
            checklists = listOf(medication),
            checklistDays = listOf(ChecklistDay(medication.id, "2026-11-02", 2L, ticks = setOf("morning/itm_m4zt"), misses = setOf("morning/itm_k2p9"))),
        ).withWeight().withEating().withReading().withActivity()
        fun damaged(misses: Any): String = JSONObject(AppStateJsonCodec.encodeVersion30(state)).apply { getJSONArray("checklistDays").getJSONObject(0).put("misses", misses) }.toString()
        listOf(
            damaged(org.json.JSONArray().put(7)),
            damaged("morning/itm_k2p9"),
            damaged(org.json.JSONArray().put("morning/itm_k2p9").put("morning/itm_k2p9")),
            damaged(org.json.JSONArray().put("morning/itm_m4zt")),
        ).forEach { raw ->
            val decoded = AppStateJsonCodec.decodeVersion30(raw)
            assertEquals(raw, listOf(ChecklistDay(medication.id, "2026-11-02", 2L, setOf("morning/itm_m4zt"))), decoded.checklistDays)
            assertEquals(raw, true, AppStateJsonCodec.lostRecords(raw, decoded))
        }
    }

    /** JSON as maps and lists, so two objects compare by content and not by key order. */
    private fun plain(value: Any?): Any? = when (value) {
        is JSONObject -> value.keys().asSequence().associateWith { plain(value.get(it)) }
        is org.json.JSONArray -> (0 until value.length()).map { plain(value.get(it)) }
        else -> value
    }
}

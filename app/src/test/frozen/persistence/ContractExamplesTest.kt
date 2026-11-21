package com.example.workouttracker.persistence.json

import java.io.File
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Every JSON example in `docs/configurable-views.md` is a shape today's decoders read, so the
 * contract cannot drift from the code it describes. An example that elides with "…" is read
 * without what it leaves out; one that says "..." is an outline of the shared fields and is not read.
 */
class ContractExamplesTest {
    private val contract = listOf(File("../docs/configurable-views.md"), File("docs/configurable-views.md")).first { it.exists() }.readText()
    private val schema = AppStateJsonCodec.checklistSchema(30)

    @Test
    fun everyExampleDecodesWithTodaysDecoders() {
        val refused = Regex("```json\\n(.*?)```", RegexOption.DOT_MATCHES_ALL).findAll(contract).mapNotNull { block ->
            val line = contract.substring(0, block.range.first).count { it == '\n' } + 1
            runCatching { read(block.groupValues[1]) }.exceptionOrNull()?.let { "line $line: ${it.message}" }
        }.toList()
        assertEquals(emptyList<String>(), refused)
    }

    private fun read(example: String) {
        val text = withoutElisions(example).trim()
        when {
            "..." in text -> Unit
            text.startsWith("\"checkpoints\"") -> fragment(text).getJSONArray("checkpoints").let { array -> (0 until array.length()).forEach { array.getJSONObject(it).decodeCheckpoint() } }
            text.startsWith("\"target\"") -> fragment(text).getJSONObject("target").decodeTarget(schema)
            text.startsWith("\"substances\"") -> fragment(text).getJSONObject("substances").decodeSubstanceSettings(SubstanceSettingsSchema.of21(schema))
            // The dated form, an item's setting and a bank's, each read in a checklist of its own.
            text.startsWith("{ \"id\": \"itm_") -> text.lines().let { (item, rewards) ->
                AppStateJsonCodec.decodeChecklist(
                    """{ "view": "checklist", "id": "chk_7f3a", "label": "Medication", "icon": "medication", "hue": "sky", "checkpoints": [ { "id": "morning", "at": "08:00", "remind": false } ], "target": { "per": "day" }, "items": [ ${item.trim().trimEnd(',')} ], ${rewards.trim().dropLast(1)}, "periods": [ { "from": "2026-10-13" } ] } }""",
                )
            }
            else -> record(text, JSONObject(text))
        }
    }

    private fun record(text: String, json: JSONObject) {
        when {
            json.has("view") -> when (json.getString("view")) {
                "checklist" -> AppStateJsonCodec.decodeChecklist(text)
                "measurement" -> AppStateJsonCodec.decodeMeasurement(text)
                "questions" -> AppStateJsonCodec.decodeQuestionsView(text)
                "time" -> AppStateJsonCodec.decodeTimeView(text)
                "activity" -> AppStateJsonCodec.decodeActivityConfiguration(text)
                else -> error("Unknown view kind")
            }
            json.has("questions") -> AppStateJsonCodec.decodeQuestionEntry(text)
            json.has("activity") -> json.decodeActivityEntry()
            json.has("time") -> require(JSONObject().put("timeLogs", JSONArray().put(json)).decodeTimeLogs().size == 1) { "The time log was not read" }
            json.has("measurement") -> require(JSONObject().put("measurementLogs", JSONArray().put(json)).decodeMeasurementLogs().size == 1) { "The measurement log was not read" }
            else -> error("No decoder reads this example")
        }
    }

    private fun fragment(text: String) = JSONObject("{$text}")

    private fun withoutElisions(example: String): String =
        example.lines().filterNot { it.trim().trimEnd(',') == "…" }.joinToString("\n").replace(Regex(",(\\s*[}\\]])"), "$1")
}

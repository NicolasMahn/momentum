package com.example.workouttracker.persistence.json

import com.example.workouttracker.domain.measurement.recordsOf

import com.example.workouttracker.domain.AppState
import com.example.workouttracker.domain.measurement.MeasurementLog
import com.example.workouttracker.domain.measurement.withWeight
import com.example.workouttracker.domain.measurement.MeasurementRecord
import com.example.workouttracker.persistence.claimedAs
import com.example.workouttracker.persistence.legacyTrackerJson
import com.example.workouttracker.persistence.readBefore27
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import com.example.workouttracker.domain.questions.withEating
import com.example.workouttracker.domain.time.withReading
import com.example.workouttracker.domain.activity.withActivity

class MeasurementLogJsonTest {
    private fun weight(dateKey: String, timestampMillis: Long, value: Double) = MeasurementLog("weight", dateKey, timestampMillis, value)
    private fun other(dateKey: String, timestampMillis: Long, value: Double) = MeasurementLog("msr_k2p9", dateKey, timestampMillis, value)

    /**
     * Everything a stored record may be, in the order storage keeps: several records of one day
     * with two at the same time, two alike in everything, one at the time 0, a value of zero and
     * one below, and a date after any today.
     */
    private val weights = listOf(
        weight("2099-01-01", 9, 90.0),
        weight("2026-10-08", 5, 70.0),
        weight("2026-10-08", 9, 82.0),
        weight("2026-10-08", 9, 85.0),
        weight("2026-10-07", 7, 81.5),
        weight("2026-10-07", 7, 81.5),
        weight("2026-10-06", 0, 80.0),
        weight("2026-10-05", 3, 0.0),
        weight("2026-10-04", 2, -1.5),
    )

    private val olderDecoders: Map<Int, (String) -> AppState> = mapOf(
        1 to AppStateJsonCodec::decodeLegacy,
        2 to AppStateJsonCodec::decodeVersion2,
        3 to AppStateJsonCodec::decodeVersion3,
        4 to AppStateJsonCodec::decodeVersion4,
        5 to AppStateJsonCodec::decodeVersion5,
        6 to AppStateJsonCodec::decodeVersion6,
        7 to AppStateJsonCodec::decodeVersion7,
        8 to AppStateJsonCodec::decodeVersion8,
        9 to AppStateJsonCodec::decodeVersion9,
        10 to AppStateJsonCodec::decodeVersion10,
        11 to AppStateJsonCodec::decodeVersion11,
        12 to AppStateJsonCodec::decodeVersion12,
        13 to AppStateJsonCodec::decodeVersion13,
        14 to AppStateJsonCodec::decodeVersion14,
        15 to AppStateJsonCodec::decodeVersion15,
        16 to AppStateJsonCodec::decodeVersion16,
        17 to AppStateJsonCodec::decodeVersion17,
        18 to AppStateJsonCodec::decodeVersion18,
        19 to AppStateJsonCodec::decodeVersion19,
        20 to AppStateJsonCodec::decodeVersion20,
        21 to AppStateJsonCodec::decodeVersion21,
        22 to AppStateJsonCodec::decodeVersion22,
        23 to AppStateJsonCodec::decodeVersion23,
    )

    @Test fun aRecordIsStoredWithItsMeasurementItsDayItsTimeAndItsNumber() {
        val stored = JSONObject(AppStateJsonCodec.encodeVersion30(AppState(measurementLogs = listOf(weight("2026-10-08", 9, 82.5), other("2026-10-08", 4, 61.0))).withWeight().withEating().withReading().withActivity()))

        assertEquals(30, stored.getInt("schemaVersion"))
        assertFalse(stored.has("weightLogs"))
        assertEquals(
            plain(
                JSONArray(
                    """[{"measurement":"weight","dateKey":"2026-10-08","timestampMillis":9,"value":82.5},""" +
                        """{"measurement":"msr_k2p9","dateKey":"2026-10-08","timestampMillis":4,"value":61}]""",
                ),
            ),
            plain(stored.getJSONArray("measurementLogs")),
        )
    }

    /** Nothing is merged, made unique, reordered or judged: every record comes back, a measurement nothing knows beside Weight's. */
    @Test fun everyVersionSince24RoundTripsEveryRecordInItsOrder() {
        val mixed = weights.take(3) + other("2026-10-08", 9, 61.0) + other("2026-10-08", 9, 61.0) + weights.drop(3) + other("2026-10-01", 1, 60.0)
        listOf(weights, mixed, emptyList()).forEach { records ->
            val state = AppState(measurementLogs = records).withWeight().withEating().withReading().withActivity()
            val encoded = AppStateJsonCodec.encodeVersion30(state)
            val decoded = readAsEveryVersionSince24Does(encoded)

            assertEquals(records, decoded.measurementLogs)
            assertEquals(state, decoded)
            assertFalse(AppStateJsonCodec.lostRecords(encoded, decoded))
            assertEquals(encoded, AppStateJsonCodec.encodeVersion30(decoded))
        }
    }

    /**
     * The records come back as the weight records before them did: by date, newest first, and
     * those of one date in their stored order, whichever measurement each belongs to.
     */
    @Test fun recordsStoredOutOfDateOrderComeBackByDateNewestFirstAndInStoredOrderWithinADate() {
        val unordered = listOf(
            weight("2026-10-06", 1, 78.0),
            other("2026-10-08", 9, 61.0),
            weight("2026-10-08", 3, 82.0),
            weight("2026-10-07", 2, 80.0),
            weight("2026-10-08", 3, 84.0),
            other("2026-10-07", 1, 60.0),
        )
        val decoded = readAsEveryVersionSince24Does(AppStateJsonCodec.encodeVersion30(AppState(measurementLogs = unordered).withWeight().withEating().withReading().withActivity()))

        assertEquals(
            listOf(
                other("2026-10-08", 9, 61.0),
                weight("2026-10-08", 3, 82.0),
                weight("2026-10-08", 3, 84.0),
                weight("2026-10-07", 2, 80.0),
                other("2026-10-07", 1, 60.0),
                weight("2026-10-06", 1, 78.0),
            ),
            decoded.measurementLogs,
        )
        assertEquals(
            listOf(MeasurementRecord("2026-10-08", 3, 82.0), MeasurementRecord("2026-10-08", 3, 84.0), MeasurementRecord("2026-10-07", 2, 80.0), MeasurementRecord("2026-10-06", 1, 78.0)),
            decoded.recordsOf("weight"),
        )
    }

    /**
     * Every older version's weight records arrive as records of `weight`, one for one: the number
     * as the value, the same day and time, in the same order, with nothing merged or dropped.
     */
    @Test fun everyOlderVersionsWeightRecordsBecomeRecordsOfWeightOneForOne() {
        val state = AppState(measurementLogs = weights).withWeight().withEating().withReading().withActivity()
        olderDecoders.forEach { (version, decode) ->
            val stored = legacyTrackerJson(state, version)
            assertFalse("version $version", stored.has("measurementLogs"))
            assertEquals("version $version", weights.size, stored.getJSONArray("weightLogs").length())
            assertEquals("version $version", setOf("dateKey", "timestampMillis", "weightKg"), stored.getJSONArray("weightLogs").getJSONObject(0).keys().asSequence().toSet())

            val decoded = decode(stored.toString())
            assertEquals("version $version", weights, decoded.measurementLogs)
            assertEquals("version $version", weights.map { MeasurementRecord(it.dateKey, it.timestampMillis, it.value) }, decoded.recordsOf("weight"))
            // Written as the current version, it is the same list again.
            assertEquals("version $version", weights, AppStateJsonCodec.decodeVersion30(AppStateJsonCodec.encodeVersion30(decoded.withWeight().withEating().withReading().withActivity())).measurementLogs)
        }
    }

    /** The two shapes never stand in one state: every version since 24 refuses `weightLogs`, and every older version `measurementLogs`. */
    @Test fun everyVersionSince24RefusesWeightLogsAndEveryOlderVersionRefusesMeasurementLogs() {
        val state = AppState(measurementLogs = weights).withWeight().withEating().withReading().withActivity()
        val current = AppStateJsonCodec.encodeVersion30(state)
        val oldRecords = legacyTrackerJson(state, 23).getJSONArray("weightLogs")
        fun changed(change: (JSONObject) -> Unit) = JSONObject(current).apply(change).toString()

        listOf(
            changed { it.put("weightLogs", oldRecords) },
            changed { it.put("weightLogs", JSONArray()) },
            changed { it.remove("measurementLogs") },
            changed { it.remove("measurementLogs"); it.put("weightLogs", oldRecords) },
            changed { it.put("measurementLogs", JSONObject()) },
        ).forEach { raw ->
            assertThrows(raw, RuntimeException::class.java) { AppStateJsonCodec.decodeVersion30(raw) }
            assertThrows(raw, RuntimeException::class.java) { AppStateJsonCodec.decodeVersion25(claimedAs(raw, 25).toString()) }
            assertThrows(raw, RuntimeException::class.java) { AppStateJsonCodec.decodeVersion24(claimedAs(raw, 24).toString()) }
        }
        assertThrows(RuntimeException::class.java) { AppStateJsonCodec.decodeVersion24(legacyTrackerJson(state, 23).put("schemaVersion", 24).toString()) }
        assertThrows(RuntimeException::class.java) { AppStateJsonCodec.decodeVersion30(JSONObject(current).apply { remove("measurementLogs") }.put("weightLogs", oldRecords).toString()) }

        olderDecoders.forEach { (version, decode) ->
            val stored = legacyTrackerJson(state, version)
            assertThrows("version $version", RuntimeException::class.java) { decode(JSONObject(stored.toString()).put("measurementLogs", JSONArray()).toString()) }
            assertThrows("version $version", RuntimeException::class.java) {
                decode(JSONObject(stored.toString()).put("measurementLogs", JSONObject(current).getJSONArray("measurementLogs")).toString())
            }
        }
        assertThrows(RuntimeException::class.java) { AppStateJsonCodec.decodeVersion23(JSONObject(current).put("schemaVersion", 23).toString()) }
    }

    /**
     * An entry that cannot be read is left out while every good one loads, and it counts as lost;
     * it never fails the decode, which would send the repository back to an older copy.
     */
    @Test fun aDamagedEntryIsLeftOutAndCountedAsLostWhileEveryGoodOneLoads() {
        val good = listOf(weight("2026-10-08", 9, 82.0), other("2026-10-08", 4, 61.0), weight("2026-10-07", 2, 80.0))
        val clean = AppStateJsonCodec.encodeVersion30(AppState(measurementLogs = good).withWeight().withEating().withReading().withActivity())
        val damagedEntries = listOf(
            """{"dateKey":"2026-10-08","timestampMillis":5,"value":83}""",
            """{"measurement":"","dateKey":"2026-10-08","timestampMillis":5,"value":83}""",
            """{"measurement":" ","dateKey":"2026-10-08","timestampMillis":5,"value":83}""",
            """{"measurement":7,"dateKey":"2026-10-08","timestampMillis":5,"value":83}""",
            """{"measurement":null,"dateKey":"2026-10-08","timestampMillis":5,"value":83}""",
            """{"measurement":"weight","timestampMillis":5,"value":83}""",
            """{"measurement":"weight","dateKey":"","timestampMillis":5,"value":83}""",
            """{"measurement":"weight","dateKey":"2026-10-08","timestampMillis":5}""",
            """{"measurement":"weight","dateKey":"2026-10-08","timestampMillis":5,"value":"heavy"}""",
            """{"measurement":"weight","dateKey":"2026-10-08","timestampMillis":5,"value":null}""",
            """{"measurement":"weight","dateKey":"2026-10-08","timestampMillis":5,"weightKg":83}""",
            """"2026-10-08"""",
            "83",
            "null",
            "[]",
        )
        damagedEntries.forEach { entry ->
            val raw = JSONObject(clean).apply {
                val logs = getJSONArray("measurementLogs")
                put("measurementLogs", JSONArray("[${logs.get(0)},$entry,${logs.get(1)},${logs.get(2)}]"))
            }.toString()
            val decoded = readAsEveryVersionSince24Does(raw)

            assertEquals(entry, good, decoded.measurementLogs)
            assertTrue(entry, AppStateJsonCodec.lostRecords(raw, decoded))
        }
        assertFalse(AppStateJsonCodec.lostRecords(clean, readAsEveryVersionSince24Does(clean)))
    }

    /**
     * No stricter rule than a weight record ever met: a record without a time has the time 0, and a
     * day, a time or a number written loosely is read as what it says. None of it is damage. Only
     * the ID is taken exactly as written.
     */
    @Test fun aRecordIsReadAsLenientlyAsAWeightRecordAlwaysWas() {
        val raw = JSONObject(AppStateJsonCodec.encodeVersion30(AppState().withWeight().withEating().withReading().withActivity())).put(
            "measurementLogs",
            JSONArray(
                """[
                    {"measurement":"weight","dateKey":"2026-10-08","value":82},
                    {"measurement":"weight","dateKey":" 2026-10-07 ","timestampMillis":"2","value":"80.5"},
                    {"measurement":"weight","dateKey":"someday","timestampMillis":3,"value":0},
                    {"measurement":"Weight","dateKey":"2026-10-06","timestampMillis":4,"value":-1.5}
                ]""",
            ),
        ).toString()
        val decoded = readAsEveryVersionSince24Does(raw)

        assertEquals(
            listOf(weight("someday", 3, 0.0), weight("2026-10-08", 0, 82.0), weight("2026-10-07", 2, 80.5), MeasurementLog("Weight", "2026-10-06", 4, -1.5)),
            decoded.measurementLogs,
        )
        assertFalse(AppStateJsonCodec.lostRecords(raw, decoded))
        assertEquals(listOf(MeasurementRecord("someday", 3, 0.0), MeasurementRecord("2026-10-08", 0, 82.0), MeasurementRecord("2026-10-07", 2, 80.5)), decoded.recordsOf("weight"))
    }

    /**
     * [raw], a payload of the current version, as that version reads it. The records are as version
     * 24 has them, so the same payload claimed as version 26, 25 or 24 must read as the very same
     * state, but for Eating's configuration, which no version before 27 held.
     */
    private fun readAsEveryVersionSince24Does(raw: String): AppState = AppStateJsonCodec.decodeVersion30(raw).also { current ->
        assertEquals(current.readBefore27(), AppStateJsonCodec.decodeVersion26(claimedAs(raw, 26).toString()))
        assertEquals(current.readBefore27(), AppStateJsonCodec.decodeVersion25(claimedAs(raw, 25).toString()))
        assertEquals(current.readBefore27(), AppStateJsonCodec.decodeVersion24(claimedAs(raw, 24).toString()))
    }

    /** JSON as maps and lists, so two objects compare by content and not by key order. */
    private fun plain(value: Any?): Any? = when (value) {
        is JSONObject -> value.keys().asSequence().associateWith { plain(value.get(it)) }
        is JSONArray -> (0 until value.length()).map { plain(value.get(it)) }
        else -> value
    }
}

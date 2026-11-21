package com.example.workouttracker.persistence.sync

import com.example.workouttracker.persistence.rows.SyncedRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The exchange's JSON reads back exactly as written and refuses anything else, as the stored format does. */
class SyncWireTest {
    private val row = SyncedRow("measurementLogs", "a", """{"value":1}""", "000000000001000-00000-phone", 3)
    private val tombstone = SyncedRow("checklistDays", "chk/2026-10-26", null, "000000000001000-00001-phone", 0)

    @Test fun anExchangeReadsBackAsWritten() {
        val request = SyncRequest("phone", "rows-1", null, listOf(row, tombstone))
        assertEquals(request, SyncWire.decodeRequest(SyncWire.encode(request)))
        val response = SyncResponse("rows-1", "42", listOf(tombstone, row))
        assertEquals(response, SyncWire.decodeResponse(SyncWire.encode(response)))
    }

    @Test fun anythingElseIsRefused() {
        val valid = """{"protocol":1,"format":"rows-1","cursor":"1","rows":[{"kind":"k","id":"i","body":"{}","changed":"000000000001000-00000-d","position":0}]}"""
        assertEquals(1, SyncWire.decodeResponse(valid).rows.size)
        listOf(
            "another protocol" to valid.replace("\"protocol\":1", "\"protocol\":2"),
            "a field more" to valid.replace("\"cursor\"", "\"extra\":1,\"cursor\""),
            "no cursor" to valid.replace("\"cursor\":\"1\",", ""),
            "no layout" to valid.replace("\"format\":\"rows-1\",", ""),
            "a body as an object" to valid.replace("\"body\":\"{}\"", "\"body\":{}"),
            "a stamp that is no clock reading" to valid.replace("000000000001000-00000-d", "yesterday"),
            "a position as text" to valid.replace("\"position\":0", "\"position\":\"0\""),
            "a blank kind" to valid.replace("\"kind\":\"k\"", "\"kind\":\"\""),
            "one row twice" to valid.replace("}]}", "},{\"kind\":\"k\",\"id\":\"i\",\"body\":null,\"changed\":\"000000000001000-00001-d\",\"position\":0}]}"),
        ).forEach { (case, raw) ->
            assertTrue(case, runCatching { SyncWire.decodeResponse(raw) }.isFailure)
        }
    }
}

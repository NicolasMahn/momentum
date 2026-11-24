package de.nicolasmahn.momentum.feature.about

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedbackSenderTest {
    @Test
    fun leftOutFactsAreSentAsNullAndTheTextTrimmed() {
        val json = JSONObject(FeedbackMessage(FeedbackKind.Idea, "  More colors \n", facts = null, language = "de-DE").toJson())

        assertEquals("idea", json.getString("kind"))
        assertEquals("More colors", json.getString("text"))
        assertTrue(json.isNull("facts"))
        assertEquals("de-DE", json.getString("language"))
    }

    @Test
    fun onlyARefusalToTakeMoreAsksThePersonToWait() {
        assertEquals(FeedbackOutcome.Sent, feedbackOutcome(201))
        assertEquals(FeedbackOutcome.TooMany, feedbackOutcome(429))
        assertEquals(FeedbackOutcome.Failed, feedbackOutcome(400))
        assertEquals(FeedbackOutcome.Failed, feedbackOutcome(502))
    }
}

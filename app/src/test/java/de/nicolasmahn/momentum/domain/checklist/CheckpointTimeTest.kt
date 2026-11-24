package de.nicolasmahn.momentum.domain.checklist

import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CheckpointTimeTest {
    @Test fun everyFormSurvivesARoundTrip() {
        mapOf(
            "12:30" to CheckpointTime.Fixed(LocalTime.of(12, 30)),
            "00:00" to CheckpointTime.Fixed(LocalTime.MIDNIGHT),
            "wake+5" to CheckpointTime.AfterWake(5),
            "wake+0" to CheckpointTime.AfterWake(0),
            "sleep-15" to CheckpointTime.BeforeSleep(15),
            "sleep-1439" to CheckpointTime.BeforeSleep(1439),
        ).forEach { (stored, time) ->
            assertEquals(stored, time, parseCheckpointTime(stored))
            assertEquals(stored, time.toString())
        }
    }

    /** Only the form it writes, so two spellings never store the same time. */
    @Test fun anythingElseIsNotATime() {
        listOf(
            "7:30", "24:00", "12:60", "12:30:00", " 12:30", "",
            "wake+", "wake+05", "wake+-5", "wake-5", "wake+1440", "Wake+5",
            "sleep+15", "sleep-", "sleep-015", "noon",
        ).forEach { assertNull(it, parseCheckpointTime(it)) }
    }
}

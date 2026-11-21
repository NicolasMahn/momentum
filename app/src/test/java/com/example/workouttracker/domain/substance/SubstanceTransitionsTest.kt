package com.example.workouttracker.domain.substance

import com.example.workouttracker.domain.AppState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test
import com.example.workouttracker.domain.checklist.Dated
import com.example.workouttracker.domain.checklist.WhenAway
import java.time.LocalDate
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import com.example.workouttracker.domain.checklist.DefaultChecklistRewards

class SubstanceTransitionsTest {
    @Test
    fun savePreservesMultipleKindsAndRejectsASecondSameKindOnOneDate() {
        val cannabis = log("cannabis-1", SubstanceKind.CANNABIS, 100L)
        val alcohol = log("alcohol-1", SubstanceKind.ALCOHOL, 200L)
        val tobacco = log("tobacco-1", SubstanceKind.TOBACCO, 300L)

        val result = listOf(cannabis, alcohol, tobacco).fold(AppState()) { state, item ->
            saveSubstanceLog(state, item)
        }
        assertThrows(IllegalArgumentException::class.java) {
            saveSubstanceLog(
                result,
                log("cannabis-2", SubstanceKind.CANNABIS, 400L),
            )
        }

        assertEquals(3, result.substanceLogs.size)
        assertEquals(
            setOf(SubstanceKind.CANNABIS.id, SubstanceKind.ALCOHOL.id, SubstanceKind.TOBACCO.id),
            result.substanceLogs.mapTo(mutableSetOf(), SubstanceLog::substanceId),
        )
    }

    @Test
    fun editingOneEntryDoesNotReplaceOtherEntriesOnTheDate() {
        val first = log("first", SubstanceKind.CANNABIS, 100L)
        val second = log("second", SubstanceKind.ALCOHOL, 200L)
        val state = AppState(substanceLogs = listOf(second, first))

        val result = saveSubstanceLog(
            state,
            first.copy(
                substanceId = SubstanceKind.TOBACCO.id,
                timestampMillis = 300L,
                amount = 3.0,
                amountUnit = SubstanceAmountUnit.USES,
            ),
        )

        assertEquals(2, result.substanceLogs.size)
        assertEquals(SubstanceKind.TOBACCO.id, result.substanceLogs.first { it.id == "first" }.substanceId)
        assertEquals(second, result.substanceLogs.first { it.id == "second" })
    }

    @Test
    fun editingIntoAnOccupiedDateAndKindIsRejected() {
        val cannabis = log("cannabis", SubstanceKind.CANNABIS, 100L)
        val alcohol = log("alcohol", SubstanceKind.ALCOHOL, 200L)
        val state = AppState(substanceLogs = listOf(alcohol, cannabis))

        assertThrows(IllegalArgumentException::class.java) {
            saveSubstanceLog(
                state,
                alcohol.copy(substanceId = SubstanceKind.CANNABIS.id),
            )
        }
    }

    @Test
    fun deleteRemovesOnlyTheChosenEntryAndUnknownIdIsIdempotent() {
        val first = log("first", SubstanceKind.CANNABIS, 100L)
        val second = log("second", SubstanceKind.CANNABIS, 200L)
        val state = AppState(substanceLogs = listOf(second, first))

        val result = deleteSubstanceLog(state, "first")

        assertEquals(listOf(second), result.substanceLogs)
        assertSame(result, deleteSubstanceLog(result, "missing"))
    }

    @Test
    fun amountMustBePositiveAndUseTheKindsUnit() {
        assertThrows(IllegalArgumentException::class.java) {
            saveSubstanceLog(
                AppState(),
                log("bad", SubstanceKind.ALCOHOL, 100L).copy(
                    amount = 2.0,
                    amountUnit = SubstanceAmountUnit.GRAMS,
                ),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            saveSubstanceLog(
                AppState(),
                log("bad", SubstanceKind.TOBACCO, 100L).copy(
                    amount = 0.0,
                    amountUnit = SubstanceAmountUnit.USES,
                ),
            )
        }
    }

    @Test
    fun invalidCalendarDateIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            saveSubstanceLog(
                AppState(),
                log("bad-date", SubstanceKind.CANNABIS, 100L).copy(dateKey = "2026-02-30"),
            )
        }
    }

    @Test
    fun aRecordNamesAKnownSubstanceCatalogueOrOwn() {
        val own = OwnSubstance("sub_k2p9", "Kratom", SubstanceAmountUnit.GRAMS)
        val state = AppState(substances = SubstanceSettings(tracked = setOf(own.id), own = listOf(own)))

        val saved = saveSubstanceLog(state, SubstanceLog("own-1", own.id, "2026-09-16", 100L, 2.0, SubstanceAmountUnit.GRAMS))

        assertEquals(listOf(own.id), saved.substanceLogs.map(SubstanceLog::substanceId))
        assertThrows(IllegalArgumentException::class.java) {
            saveSubstanceLog(state, SubstanceLog("own-2", own.id, "2026-09-17", 100L, 2.0, SubstanceAmountUnit.USES))
        }
        assertThrows(IllegalArgumentException::class.java) {
            saveSubstanceLog(AppState(), SubstanceLog("stranger", "sub_zzzz", "2026-09-16", 100L))
        }
        // Tracking decides what is offered, never what a record may say.
        assertEquals(1, saveSubstanceLog(AppState(), log("coffee", SubstanceKind.COFFEE, 100L)).substanceLogs.size)
    }

    /** A record is a fact that outlives the choice: an own substance with records stays, with its unit. */
    @Test
    fun anOwnSubstanceWithRecordsCanNeitherGoNorChangeItsUnit() {
        val own = OwnSubstance("sub_k2p9", "Kratom", SubstanceAmountUnit.GRAMS)
        val settings = SubstanceSettings(tracked = setOf(own.id), own = listOf(own))
        val recorded = saveSubstanceLog(
            AppState(substances = settings),
            SubstanceLog("own-1", own.id, "2026-09-16", 100L),
        )

        assertThrows(IllegalArgumentException::class.java) { saveSubstanceSettings(recorded, SubstanceSettings()) }
        assertThrows(IllegalArgumentException::class.java) {
            saveSubstanceSettings(recorded, settings.copy(own = listOf(own.copy(unit = SubstanceAmountUnit.USES))))
        }

        val untracked = saveSubstanceSettings(recorded, settings.copy(tracked = emptySet()))
        val renamed = saveSubstanceSettings(recorded, settings.copy(own = listOf(own.copy(label = "Mitragyna"))))
        assertEquals(emptySet<String>(), untracked.substances.tracked)
        assertEquals(recorded.substanceLogs, untracked.substanceLogs)
        assertEquals("Mitragyna", renamed.substances.own.single().label)
        // Without records the substance goes, and the same state comes back untouched.
        assertEquals(SubstanceSettings(), saveSubstanceSettings(AppState(substances = settings), SubstanceSettings()).substances)
        assertSame(recorded, saveSubstanceSettings(recorded, settings))
    }

    @Test
    fun theChoiceOnlyNamesKnownSubstancesAndKeepsOwnIdsApart() {
        assertThrows(IllegalArgumentException::class.java) { SubstanceSettings(tracked = setOf("sub_zzzz")) }
        // An ID with a space would be read back as a different, unknown one.
        assertThrows(IllegalArgumentException::class.java) { OwnSubstance("sub_k2p9 ", "Kratom", SubstanceAmountUnit.GRAMS) }
        assertThrows(IllegalArgumentException::class.java) { OwnSubstance("sub k2p9", "Kratom", SubstanceAmountUnit.GRAMS) }
        assertThrows(IllegalArgumentException::class.java) {
            SubstanceSettings(own = listOf(OwnSubstance("coffee", "Coffee", SubstanceAmountUnit.CUPS)))
        }
        assertThrows(IllegalArgumentException::class.java) {
            SubstanceSettings(own = listOf(OwnSubstance("sub_a", "A", SubstanceAmountUnit.USES), OwnSubstance("sub_a", "B", SubstanceAmountUnit.USES)))
        }
        assertEquals(
            listOf(SubstanceKind.ALCOHOL, SubstanceKind.TOBACCO, SubstanceKind.CANNABIS),
            SubstanceSettings.Legacy.trackedSubstances,
        )
        assertEquals(emptyList<Substance>(), SubstanceSettings().trackedSubstances)
    }

    /** A day holds uses or "none", never both: a use replaces none, and none never overwrites a use. */
    @Test
    fun noneAndAUseExcludeEachOther() {
        val none = saveSubstanceNoneDay(AppState(), "2026-09-16", 5L)
        assertEquals(listOf(SubstanceNoneDay("2026-09-16", 5L)), none.substanceNoneDays)
        assertTrue(none.isSubstancesLoggedFor("2026-09-16"))
        assertFalse(none.isSubstancesLoggedFor("2026-09-15"))

        val used = saveSubstanceLog(none, log("c", SubstanceKind.CANNABIS, 10L))
        assertEquals(emptyList<SubstanceNoneDay>(), used.substanceNoneDays)
        assertTrue(used.isSubstancesLoggedFor("2026-09-16"))
        assertThrows(IllegalArgumentException::class.java) { saveSubstanceNoneDay(used, "2026-09-16", 20L) }

        // Said again, a day keeps one "none"; taken back, it is unlogged; other days keep theirs, newest first.
        assertEquals(listOf(SubstanceNoneDay("2026-09-16", 30L)), saveSubstanceNoneDay(none, "2026-09-16", 30L).substanceNoneDays)
        assertSame(none, deleteSubstanceNoneDay(none, "2026-09-15"))
        assertEquals(emptyList<SubstanceNoneDay>(), deleteSubstanceNoneDay(none, "2026-09-16").substanceNoneDays)
        assertEquals(listOf("2026-09-18", "2026-09-16"), saveSubstanceNoneDay(none, "2026-09-18", 1L).substanceNoneDays.map { it.dateKey })
        assertThrows(IllegalArgumentException::class.java) { saveSubstanceNoneDay(none, "2026-9-16", 1L) }
    }

    @Test
    fun theSettingsGuardTheStayingAwayChoiceAndTheAwayRule() {
        assertThrows(IllegalArgumentException::class.java) { SubstanceSettings(tracked = setOf(SubstanceKind.COFFEE.id), stayingAway = Dated.always(setOf(SubstanceKind.CANNABIS.id))) }
        assertThrows(IllegalArgumentException::class.java) { SubstanceSettings(whenAway = Dated.always(WhenAway.LowerTarget)) }
        // Logging joined the streak after periods existed, so a bank without a dated start is not a shape it can take.
        assertThrows(IllegalArgumentException::class.java) { SubstanceSettings(rewards = DefaultChecklistRewards) }
        assertEquals(SubstanceSettings.Legacy.tracked, SubstanceSettings.Legacy.stayingAway.current)
        assertFalse(SubstanceSettings().countsTowardStreakOn(LocalDate.parse("2026-09-16")))
        assertTrue(SubstanceSettings().joiningStreak(LocalDate.parse("2026-09-16")).countsTowardStreakOn(LocalDate.parse("2026-09-16")))
    }

    private fun log(id: String, kind: SubstanceKind, timestamp: Long) = SubstanceLog(
        id = id,
        substanceId = kind.id,
        dateKey = "2026-09-16",
        timestampMillis = timestamp,
    )
}

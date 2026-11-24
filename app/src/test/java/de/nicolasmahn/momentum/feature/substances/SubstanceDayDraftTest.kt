package de.nicolasmahn.momentum.feature.substances

import de.nicolasmahn.momentum.core.format.parseMetricNumber
import de.nicolasmahn.momentum.domain.AppState
import de.nicolasmahn.momentum.domain.substance.SubstanceAmountUnit
import de.nicolasmahn.momentum.domain.substance.SubstanceKind
import de.nicolasmahn.momentum.domain.substance.OwnSubstance
import de.nicolasmahn.momentum.domain.substance.SubstanceLog
import de.nicolasmahn.momentum.domain.substance.SubstanceSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale
import de.nicolasmahn.momentum.domain.substance.SubstanceNoneDay
import org.junit.Assert.assertSame

class SubstanceDayDraftTest {
    @Test
    fun amountIsPrefilledInTheScreenLanguageAndStaysValid() {
        val log = SubstanceLog("c1", SubstanceKind.CANNABIS.id, "2026-09-16", 0L, 0.5, SubstanceAmountUnit.GRAMS)

        val draft = substanceDayDraft(listOf(log), "2026-09-16", Locale.GERMAN, SubstanceSettings.Legacy)

        assertEquals(listOf("0,5"), draft.rows.map(SubstanceDayRow::amountText))
        assertTrue(draft.invalidAmountKeys().isEmpty())
        assertFalse(draft.isDirty)
    }

    @Test
    fun historicalDayCanSaveTwoKindsAndReloadBoth() {
        val dateKey = "2026-09-16"
        val draft = substanceDayDraft(emptyList(), dateKey, Locale.US, SubstanceSettings.Legacy)
            .toggle(SubstanceKind.CANNABIS.id)
            .toggle(SubstanceKind.ALCOHOL.id)
            .withAmount(SubstanceKind.CANNABIS, "0.5")
            .withAmount(SubstanceKind.ALCOHOL, "2")

        val saved = reconcileSubstanceDayDraft(
            state = AppState(),
            draft = draft,
            idFactory = sequenceOf("cannabis", "alcohol").iterator()::next,
            timestampMillis = 1_000L,
        )
        val reloaded = substanceDayDraft(saved.substanceLogs, dateKey, Locale.US, SubstanceSettings.Legacy)

        // Rows keep the catalogue's order, whatever order they were chosen in.
        assertEquals(
            listOf(SubstanceKind.ALCOHOL.id, SubstanceKind.CANNABIS.id),
            reloaded.rows.map(SubstanceDayRow::substanceId),
        )
        assertEquals(
            listOf(2.0, 0.5),
            reloaded.rows.map { row -> parseMetricNumber(row.amountText) },
        )
        assertEquals(
            setOf(SubstanceAmountUnit.GRAMS, SubstanceAmountUnit.DRINKS),
            saved.substanceLogs.mapNotNullTo(mutableSetOf(), SubstanceLog::amountUnit),
        )
    }

    @Test
    fun editingOnePersistedRowPreservesItsSameDaySibling() {
        val cannabis = log("cannabis", SubstanceKind.CANNABIS, "2026-09-16", 100L, 0.5)
        val alcohol = log("alcohol", SubstanceKind.ALCOHOL, "2026-09-16", 200L, 2.0)
        val state = AppState(substanceLogs = listOf(alcohol, cannabis))
        val draft = substanceDayDraft(state.substanceLogs, "2026-09-16", Locale.US, SubstanceSettings.Legacy)
            .updateAmount("alcohol", "3")

        val saved = reconcileSubstanceDayDraft(
            state = state,
            draft = draft,
            idFactory = { error("Editing must not allocate an ID") },
            timestampMillis = 300L,
        )

        assertEquals(cannabis, saved.substanceLogs.single { it.id == cannabis.id })
        assertEquals(3.0, saved.substanceLogs.single { it.id == alcohol.id }.amount)
        assertEquals(300L, saved.substanceLogs.single { it.id == alcohol.id }.timestampMillis)
    }

    @Test
    fun switchingDatesRebuildsTheDraftWithoutLeakingUnsavedChoices() {
        val firstDate = "2026-09-15"
        val secondDate = "2026-09-16"
        val logs = listOf(
            log("first", SubstanceKind.CANNABIS, firstDate, 100L, 0.5),
            log("second", SubstanceKind.ALCOHOL, secondDate, 200L, 2.0),
        )
        val unsavedFirstDateDraft = substanceDayDraft(logs, firstDate, Locale.US, SubstanceSettings.Legacy)
            .toggle(SubstanceKind.TOBACCO.id)

        val secondDateDraft = substanceDayDraft(logs, secondDate, Locale.US, SubstanceSettings.Legacy)
        val firstDateReloaded = substanceDayDraft(logs, firstDate, Locale.US, SubstanceSettings.Legacy)

        assertTrue(unsavedFirstDateDraft.rows.any { it.substanceId == SubstanceKind.TOBACCO.id })
        assertEquals(listOf(SubstanceKind.ALCOHOL.id), secondDateDraft.rows.map(SubstanceDayRow::substanceId))
        assertEquals(listOf(SubstanceKind.CANNABIS.id), firstDateReloaded.rows.map(SubstanceDayRow::substanceId))
        assertFalse(firstDateReloaded.rows.any { it.substanceId == SubstanceKind.TOBACCO.id })
    }

    @Test
    fun togglingAKindStagesExactRemovalAndCanRestoreIt() {
        val first = log("first", SubstanceKind.CANNABIS, "2026-09-16", 100L, 0.5)
        val second = log("second", SubstanceKind.CANNABIS, "2026-09-16", 200L, 0.75)
        val persistedDraft = substanceDayDraft(listOf(first, second), "2026-09-16", Locale.US, SubstanceSettings.Legacy)

        val afterPersistedToggle = persistedDraft.toggle(SubstanceKind.CANNABIS.id)
        val restoredPersisted = afterPersistedToggle.toggle(SubstanceKind.CANNABIS.id)
        val withUnsavedTobacco = persistedDraft.toggle(SubstanceKind.TOBACCO.id)
        val afterUnsavedTobaccoToggle = withUnsavedTobacco.toggle(SubstanceKind.TOBACCO.id)

        assertTrue(afterPersistedToggle.rows.none { it.substanceId == SubstanceKind.CANNABIS.id })
        assertEquals(setOf("first", "second"), afterPersistedToggle.deletedIds)
        assertEquals(
            listOf("second", "first"),
            restoredPersisted.rows.mapNotNull(SubstanceDayRow::id),
        )
        assertTrue(restoredPersisted.deletedIds.isEmpty())
        assertFalse(afterUnsavedTobaccoToggle.rows.any { it.substanceId == SubstanceKind.TOBACCO.id })
    }

    @Test
    fun normalDraftCannotCreateASecondRecordForTheSameKind() {
        val once = substanceDayDraft(emptyList(), "2026-09-16", Locale.US, SubstanceSettings.Legacy)
            .toggle(SubstanceKind.ALCOHOL.id)
        val removed = once.toggle(SubstanceKind.ALCOHOL.id)
        val selectedAgain = removed.toggle(SubstanceKind.ALCOHOL.id)

        assertEquals(1, once.rows.count { it.substanceId == SubstanceKind.ALCOHOL.id })
        assertTrue(removed.rows.none { it.substanceId == SubstanceKind.ALCOHOL.id })
        assertEquals(1, selectedAgain.rows.count { it.substanceId == SubstanceKind.ALCOHOL.id })
    }

    @Test
    fun concurrentSameKindInsertionConflictsInsteadOfCreatingADuplicate() {
        val draft = substanceDayDraft(emptyList(), "2026-09-16", Locale.US, SubstanceSettings.Legacy)
            .toggle(SubstanceKind.ALCOHOL.id)
        val concurrent = log("concurrent", SubstanceKind.ALCOHOL, "2026-09-16", 100L, 2.0)
        val concurrentState = AppState(substanceLogs = listOf(concurrent))

        assertTrue(hasSubstanceDayConflict(concurrentState, draft))
        assertEquals(
            concurrentState,
            reconcileSubstanceDayDraft(
                state = concurrentState,
                draft = draft,
                idFactory = { "new" },
                timestampMillis = 200L,
            ),
        )
    }

    @Test
    fun exactDeletionPreservesUntouchedDuplicateSameKindRecords() {
        val first = log("first", SubstanceKind.CANNABIS, "2026-09-16", 100L, 0.5)
        val second = log("second", SubstanceKind.CANNABIS, "2026-09-16", 200L, 0.75)
        val alcohol = log("alcohol", SubstanceKind.ALCOHOL, "2026-09-16", 300L, 2.0)
        val state = AppState(substanceLogs = listOf(alcohol, second, first))
        val draft = substanceDayDraft(state.substanceLogs, "2026-09-16", Locale.US, SubstanceSettings.Legacy)
            .markDeleted("first")

        val saved = reconcileSubstanceDayDraft(
            state = state,
            draft = draft,
            idFactory = { error("Deleting must not allocate an ID") },
            timestampMillis = 400L,
        )

        assertEquals(setOf("second", "alcohol"), saved.substanceLogs.mapTo(mutableSetOf(), SubstanceLog::id))
        assertEquals(second, saved.substanceLogs.single { it.id == second.id })
        assertEquals(alcohol, saved.substanceLogs.single { it.id == alcohol.id })
    }

    @Test
    fun concurrentSameIdChangeIsDetectedAndNeverOverwrittenByTheStaleDraft() {
        val original = log("alcohol", SubstanceKind.ALCOHOL, "2026-09-16", 100L, 2.0)
        val draft = substanceDayDraft(listOf(original), "2026-09-16", Locale.US, SubstanceSettings.Legacy)
            .updateAmount("alcohol", "3")
        val concurrent = original.copy(timestampMillis = 200L, amount = 4.0)
        val concurrentSibling = log(
            "tobacco",
            SubstanceKind.TOBACCO,
            "2026-09-16",
            250L,
            1.0,
        )
        val concurrentState = AppState(substanceLogs = listOf(concurrentSibling, concurrent))

        assertTrue(hasSubstanceDayConflict(concurrentState, draft))

        val reconciled = reconcileSubstanceDayDraft(
            state = concurrentState,
            draft = draft,
            idFactory = { error("Editing must not allocate an ID") },
            timestampMillis = 300L,
        )

        assertEquals(concurrent, reconciled.substanceLogs.single { it.id == concurrent.id })
        assertEquals(
            concurrentSibling,
            reconciled.substanceLogs.single { it.id == concurrentSibling.id },
        )
    }

    @Test
    fun deletingARecordConflictsWithAConcurrentChangeToThatExactId() {
        val original = log("alcohol", SubstanceKind.ALCOHOL, "2026-09-16", 100L, 2.0)
        val deleteDraft = substanceDayDraft(listOf(original), "2026-09-16", Locale.US, SubstanceSettings.Legacy)
            .markDeleted(original.id)
        val concurrentlyEdited = original.copy(timestampMillis = 200L, amount = 3.0)
        val concurrentState = AppState(substanceLogs = listOf(concurrentlyEdited))

        assertTrue(
            hasSubstanceDayConflict(
                state = concurrentState,
                draft = deleteDraft,
            ),
        )
        assertEquals(
            concurrentState,
            reconcileSubstanceDayDraft(
                state = concurrentState,
                draft = deleteDraft,
                idFactory = { error("Deleting must not allocate an ID") },
                timestampMillis = 300L,
            ),
        )
    }

    /** A record of a substance no longer tracked is still the day's: it is listed, and it can go. */
    @Test
    fun aRecordOfAnUntrackedSubstanceStaysInTheDayAndCanBeRemoved() {
        val coffee = log("coffee", SubstanceKind.COFFEE, "2026-09-16", 100L, 2.0)
        val state = AppState(substanceLogs = listOf(coffee), substances = SubstanceSettings.Legacy)
        val draft = substanceDayDraft(state.substanceLogs, "2026-09-16", Locale.US, state.substances)

        assertEquals(listOf(SubstanceKind.COFFEE.id), draft.rows.map(SubstanceDayRow::substanceId))
        assertFalse(draft.isDirty)

        val removed = reconcileSubstanceDayDraft(
            state = state,
            draft = draft.markDeleted("coffee"),
            idFactory = { error("Deleting must not allocate an ID") },
            timestampMillis = 200L,
        )
        assertTrue(removed.substanceLogs.isEmpty())
    }

    /** An own substance's amount is counted in the unit it was named with, and its row comes after the catalogue's. */
    @Test
    fun anOwnSubstanceSavesItsAmountInItsOwnUnitAfterTheCatalogue() {
        val own = OwnSubstance("sub_k2p9", "Kratom", SubstanceAmountUnit.GRAMS)
        val state = AppState(substances = SubstanceSettings(tracked = setOf(own.id, SubstanceKind.COFFEE.id), own = listOf(own)))
        val draft = substanceDayDraft(emptyList(), "2026-09-16", Locale.US, state.substances)
            .toggle(own.id)
            .toggle(SubstanceKind.COFFEE.id)
            .updateAmount("new-${own.id}-1", "1.5")

        val saved = reconcileSubstanceDayDraft(state, draft, idFactory = sequenceOf("a", "b").iterator()::next, timestampMillis = 100L)

        assertEquals(listOf(SubstanceKind.COFFEE.id, own.id), draft.rows.map(SubstanceDayRow::substanceId))
        assertEquals(SubstanceAmountUnit.GRAMS, saved.substanceLogs.single { it.substanceId == own.id }.amountUnit)
        assertEquals(null, saved.substanceLogs.single { it.substanceId == SubstanceKind.COFFEE.id }.amount)
    }

    /** "None today" is the day's answer when there is nothing to log; it never stands beside a use. */
    @Test
    fun noneIsADraftOfItsOwnAndExcludesAUse() {
        val dateKey = "2026-09-16"
        val draft = substanceDayDraft(emptyList(), dateKey, Locale.US, SubstanceSettings.Legacy)
        assertTrue(draft.canSayNone)
        assertFalse(draft.none)
        val none = draft.toggleNone()
        assertTrue(none.none)
        assertTrue(none.isDirty)

        // A use replaces none, and none cannot be said over a use: the draft stays as it is.
        val use = none.toggle(SubstanceKind.CANNABIS.id)
        assertFalse(use.none)
        assertFalse(use.canSayNone)
        assertSame(use, use.toggleNone())

        val saved = reconcileSubstanceDayDraft(AppState(), none, idFactory = { error("None allocates no ID") }, timestampMillis = 100L)
        assertEquals(listOf(SubstanceNoneDay(dateKey, 100L)), saved.substanceNoneDays)
        assertTrue(saved.substanceLogs.isEmpty())

        // Reloaded, the day says none and is clean; saved again, it keeps the time it was first said.
        val reloaded = substanceDayDraft(saved.substanceLogs, dateKey, Locale.US, SubstanceSettings.Legacy, saved.substanceNoneDays)
        assertTrue(reloaded.none)
        assertFalse(reloaded.isDirty)
        assertSame(saved, reconcileSubstanceDayDraft(saved, reloaded, idFactory = { error("Nothing new") }, timestampMillis = 200L))

        val withUse = reconcileSubstanceDayDraft(saved, reloaded.toggle(SubstanceKind.CANNABIS.id), idFactory = sequenceOf("c").iterator()::next, timestampMillis = 300L)
        assertTrue(withUse.substanceNoneDays.isEmpty())
        assertEquals(listOf(SubstanceKind.CANNABIS.id), withUse.substanceLogs.map(SubstanceLog::substanceId))
        assertTrue(reconcileSubstanceDayDraft(saved, reloaded.toggleNone(), idFactory = { error("Nothing new") }, timestampMillis = 400L).substanceNoneDays.isEmpty())
    }

    /** None said over a day that was logged meanwhile would overwrite a record, so the day is reloaded instead. */
    @Test
    fun noneSaidOverADayLoggedMeanwhileIsAConflict() {
        val dateKey = "2026-09-16"
        val draft = substanceDayDraft(emptyList(), dateKey, Locale.US, SubstanceSettings.Legacy).toggleNone()

        val usedMeanwhile = AppState(substanceLogs = listOf(log("c", SubstanceKind.CANNABIS, dateKey, 1L, 0.5)))
        assertTrue(hasSubstanceDayConflict(usedMeanwhile, draft))
        assertSame(usedMeanwhile, reconcileSubstanceDayDraft(usedMeanwhile, draft, idFactory = { error("Nothing new") }, timestampMillis = 2L))

        val saidMeanwhile = AppState(substanceNoneDays = listOf(SubstanceNoneDay(dateKey, 1L)))
        assertTrue(hasSubstanceDayConflict(saidMeanwhile, draft))
        assertFalse(hasSubstanceDayConflict(AppState(substanceNoneDays = listOf(SubstanceNoneDay("2026-09-15", 1L))), draft))
    }

    private fun SubstanceDayDraft.withAmount(
        kind: SubstanceKind,
        amountText: String,
    ): SubstanceDayDraft {
        val row = rows.single { it.substanceId == kind.id }
        return updateAmount(requireNotNull(row.key), amountText)
    }

    private fun log(
        id: String,
        kind: SubstanceKind,
        dateKey: String,
        timestampMillis: Long,
        amount: Double,
    ) = SubstanceLog(
        id = id,
        substanceId = kind.id,
        dateKey = dateKey,
        timestampMillis = timestampMillis,
        amount = amount,
        amountUnit = kind.unit,
    )
}

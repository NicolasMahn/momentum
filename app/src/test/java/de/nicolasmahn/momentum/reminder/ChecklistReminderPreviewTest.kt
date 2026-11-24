package de.nicolasmahn.momentum.reminder

import de.nicolasmahn.momentum.R
import de.nicolasmahn.momentum.core.text.UiText
import de.nicolasmahn.momentum.core.text.joinedWithAnd
import de.nicolasmahn.momentum.core.text.uiText
import de.nicolasmahn.momentum.domain.AppState
import de.nicolasmahn.momentum.domain.checklist.AboutDay
import de.nicolasmahn.momentum.domain.checklist.Dated
import de.nicolasmahn.momentum.domain.checklist.recordDayFor
import de.nicolasmahn.momentum.domain.compatibility.CareChecklistId
import de.nicolasmahn.momentum.domain.compatibility.LegacyCareSession
import de.nicolasmahn.momentum.domain.compatibility.careChecklist
import de.nicolasmahn.momentum.domain.care.careState
import de.nicolasmahn.momentum.domain.checklist.ChecklistConfiguration
import de.nicolasmahn.momentum.domain.checklist.ChecklistDay
import de.nicolasmahn.momentum.domain.checklist.ChecklistEdit
import de.nicolasmahn.momentum.domain.checklist.ChecklistItem
import de.nicolasmahn.momentum.domain.checklist.ChecklistTarget
import de.nicolasmahn.momentum.domain.checklist.Checkpoint
import de.nicolasmahn.momentum.domain.checklist.CheckpointTime
import de.nicolasmahn.momentum.domain.checklist.ViewHue
import de.nicolasmahn.momentum.domain.checklist.ViewIcon
import de.nicolasmahn.momentum.domain.checklist.applyEdit
import de.nicolasmahn.momentum.domain.checklist.progressOf
import de.nicolasmahn.momentum.domain.holiday.HolidaySchedule
import de.nicolasmahn.momentum.domain.reminder.ReminderSettings
import de.nicolasmahn.momentum.domain.reminder.ReminderTime
import de.nicolasmahn.momentum.domain.settings.FeatureVisibilitySettings
import de.nicolasmahn.momentum.domain.compatibility.SupplementsCheckpointId
import de.nicolasmahn.momentum.domain.compatibility.SupplementsChecklistId
import de.nicolasmahn.momentum.domain.compatibility.supplementsChecklist
import de.nicolasmahn.momentum.domain.supplement.supplementsState
import de.nicolasmahn.momentum.persistence.TrackerRepository
import de.nicolasmahn.momentum.persistence.TrackerUpdateResult
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The preview on a checklist's Reminders tab says exactly what the reminder says: each case runs
 * the real coordinator over an untouched day and compares the words it hands its notifier with the
 * preview's.
 */
class ChecklistReminderPreviewTest {
    private val today = LocalDate.parse("2026-10-14")
    private val todayKey = today.toString()
    private val medication = ChecklistConfiguration(
        id = "chk_7f3a",
        label = "Medication",
        icon = ViewIcon.Checklist,
        hue = ViewHue.Sky,
        checkpoints = listOf(
            Checkpoint("morning", CheckpointTime.AfterWake(30), label = "Morning", remind = true),
            Checkpoint("evening", CheckpointTime.Fixed(LocalTime.of(21, 0)), label = "Evening", remind = true),
        ),
        items = listOf(
            ChecklistItem("itm_levo", "Levothyroxine", checkpoints = listOf("morning")),
            ChecklistItem("itm_ibu", "Ibuprofen 400"),
            // Wednesday 2026-10-14 is no Saturday.
            ChecklistItem("itm_iron", "Iron", checkpoints = listOf("evening"), target = ChecklistTarget.Weekdays(setOf(DayOfWeek.SATURDAY))),
        ),
    )

    @Test fun aChecklistsPreviewIsTheNotificationItsCoordinatorSends() {
        var sent: MoReminderMessage? = null
        val coordinator = sending(AppState(checklists = listOf(medication))) { sent = it }
        medication.checkpoints.forEach { checkpoint ->
            sent = null
            coordinator.onReminderDue(ReminderViewKind.Checklist, medication.id, checkpoint.id)
            assertEquals(checkpoint.id, assertNotNull(sent), checklistTestReminder(medication, checkpoint, progressOf(medication), today)?.words(null))
        }
    }

    /** A checklist about yesterday previews yesterday's items, said to be yesterday's, as its reminder sends them when all are open. */
    @Test fun aChecklistAboutYesterdayPreviewsWhatItsReminderSaysAboutYesterday() {
        val lastNight = medication.copy(about = Dated.always(AboutDay.Yesterday))
        val state = AppState(checklists = listOf(lastNight))
        var sent: MoReminderMessage? = null
        sending(state) { sent = it }.onReminderDue(ReminderViewKind.Checklist, lastNight.id, "morning")
        val morning = lastNight.checkpoints.first()
        val preview = checklistTestReminder(lastNight, morning, state.progressOf(lastNight, lastNight.recordDayFor(today).toString(), HolidaySchedule()), today)?.words(null)
        assertEquals(assertNotNull(sent), preview)
        val both = listOf("Levothyroxine", "Ibuprofen 400").map { UiText.Verbatim(it) }.joinedWithAnd()
        assertEquals(uiText(R.string.checklist_reminder_open_yesterday, both), assertNotNull(preview).text)
        // Yesterday's levothyroxine taken, the reminder names the rest; the preview still shows the whole line.
        val taken = state.copy(checklistDays = listOf(ChecklistDay(lastNight.id, today.minusDays(1).toString(), 1L, setOf("morning/itm_levo"))))
        sending(taken) { sent = it }.onReminderDue(ReminderViewKind.Checklist, lastNight.id, "morning")
        assertEquals(uiText(R.string.checklist_reminder_open_yesterday, listOf(UiText.Verbatim("Ibuprofen 400")).joinedWithAnd()), assertNotNull(sent).text)
    }

    /** The real coordinator over [state], handing what it would send to [onSent]. */
    private fun sending(state: AppState, onSent: (MoReminderMessage) -> Unit) = ChecklistReminderCoordinator(
        trackerRepository = readOnly(state),
        featureVisibility = FeatureVisibilitySource { FeatureVisibilitySettings() },
        gateway = object : ChecklistReminderGateway {
            override fun canScheduleExact() = true
                override fun shown() = emptySet<String>()
            override fun schedule(slot: ReminderSlot, earliestDate: LocalDate?, fallbackAlarmTime: ReminderTime, fixedBedtime: ReminderTime?) = true
            override fun cancel(key: String) = Unit
            override fun dismiss(key: String) = Unit
            override fun keepUnsaved(key: String, why: UiText) = false
        },
        runtime = object : ChecklistReminderRuntime {
            override fun scheduled() = emptySet<String>()
            override fun record(keys: Set<String>) = Unit
            override fun posted() = emptyMap<String, ReminderDays>()
            override fun posted(key: String, days: ReminderDays) = Unit
            override fun dismissed(key: String) = Unit
            override fun earliest() = emptyMap<String, LocalDate>()
            override fun earliest(key: String, day: LocalDate) = Unit
        },
        notifier = { checklist, checkpoint, open, _, days, _ -> onSent(checklistReminderMessage(checklist, checkpoint, open, days)); true },
        loggedNotifier = { _, _, _, _ -> error("No measurement reminds here.") },
        reminderSettings = { ReminderSettings() },
        todayKey = { todayKey },
    )

    @Test fun aCheckpointWithNothingDueTodayHasNoReminderToPreview() {
        val saturdaysOnly = medication.copy(items = medication.items.filter { it.id == "itm_iron" })
        assertNull(checklistTestReminder(saturdaysOnly, saturdaysOnly.checkpoints.last(), progressOf(saturdaysOnly), today)?.words(null))
        assertNotNull(checklistTestReminder(saturdaysOnly, saturdaysOnly.checkpoints.last(), progressOf(saturdaysOnly, LocalDate.parse("2026-10-17")), today)?.words(null))
    }

    /** Supplements' preview is what its reminder says, in Mo's voice, naming its own items by their words. */
    @Test fun supplementsPreviewIsWhatItsReminderSays() {
        val state = supplementsState(remind = true)
        var sent: MoReminderMessage? = null
        sending(state) { sent = it }.onReminderDue(ReminderViewKind.Checklist, SupplementsChecklistId, SupplementsCheckpointId)
        val checklist = requireNotNull(state.supplementsChecklist)
        val checkpoint = checklist.checkpoints.single { it.id == SupplementsCheckpointId }
        val preview = checklistTestReminder(checklist, checkpoint, state.progressOf(checklist, todayKey, HolidaySchedule()), today)?.words(null)
        assertEquals(assertNotNull(sent), preview)
        assertEquals(checklistReminderMessage(checklist, checkpoint, checklist.items.filter { it.id != "vitamin_d" || today.month in requireNotNull(it.months) }, checklist.reminderDaysOn(today)), preview)
    }

    /** Creatine once a week: on one of its free days neither the reminder nor the preview names it. */
    @Test fun supplementsReminderAndPreviewLeaveOutAnItemOnItsFreeDay() {
        val wednesday = LocalDate.parse("2026-10-14")
        val state = supplementsState(remind = true)
            .applyEdit("supplements", ChecklistEdit.SetItemTarget("creatine", ChecklistTarget.PerWeek(times = 1)), wednesday.minusDays(2))
        val checklist = requireNotNull(state.supplementsChecklist)
        var sent: MoReminderMessage? = null
        sending(state) { sent = it }.onReminderDue(ReminderViewKind.Checklist, SupplementsChecklistId, SupplementsCheckpointId)
        val open = checklist.items.filter { it.id in setOf("multivitamin", "vitamin_d") }
        assertEquals(checklistReminderMessage(checklist, checklist.checkpoints.single(), open, checklist.reminderDaysOn(today)), assertNotNull(sent))
        val preview = checklistTestReminder(checklist, checklist.checkpoints.single(), state.progressOf(checklist, todayKey, HolidaySchedule()), today)?.words(null)
        assertEquals(sent, preview)
    }

    /** A checkpoint Supplements was given speaks in Mo's voice too, naming what is due there. */
    @Test fun aCheckpointGivenToSupplementsSpeaksInMosVoiceToo() {
        val evening = CheckpointTime.Fixed(LocalTime.of(20, 0))
        val state = supplementsState(remind = true)
            .applyEdit("supplements", ChecklistEdit.AddCheckpoint("cpt_eve", "Evening", evening, remind = true), today)
        val checklist = requireNotNull(state.supplementsChecklist)
        val added = checklist.checkpoints.single { it.id == "cpt_eve" }
        val due = checklist.items.filter { it.id != "vitamin_d" || today.month in requireNotNull(it.months) }
        assertEquals(checklistReminderMessage(checklist, added, due, checklist.reminderDaysOn(today)), checklistTestReminder(checklist, added, state.progressOf(checklist, todayKey, HolidaySchedule()), today)?.words(null))
    }

    /** Care's preview at each session is what its reminder says there, in Mo's voice. */
    @Test fun carePreviewIsWhatItsReminderSays() {
        val state = careState(remindMorning = true, remindEvening = true)
        val checklist = requireNotNull(state.careChecklist)
        LegacyCareSession.entries.forEach { session ->
            var sent: MoReminderMessage? = null
            sending(state) { sent = it }.onReminderDue(ReminderViewKind.Checklist, CareChecklistId, session.storageId)
            val checkpoint = checklist.checkpoints.single { it.id == session.storageId }
            val open = state.progressOf(checklist, todayKey, HolidaySchedule()).openAt(session.storageId)
            assertEquals(session.name, checklistReminderMessage(checklist, checkpoint, open, checklist.reminderDaysOn(today)), assertNotNull(sent))
            assertEquals(session.name, sent, checklistTestReminder(checklist, checkpoint, state.progressOf(checklist, todayKey, HolidaySchedule()), today)?.words(null))
        }
    }

    /**
     * A week item is named only on a day skipping it would cost, in the preview as in the reminder:
     * 2026-10-18 is the Sunday of a week nothing was walked on.
     */
    @Test fun aWeekItemIsPreviewedOnlyOnceItsFreeDaysAreUsedUp() {
        val weekly = medication.copy(items = medication.items + ChecklistItem("itm_walk", "Walk", checkpoints = listOf("evening"), target = ChecklistTarget.PerWeek(times = 1)))
        val evening = weekly.checkpoints.last()
        val sunday = LocalDate.parse("2026-10-18")
        val quiet = checklistTestReminder(weekly, evening, progressOf(weekly), today)?.words(null)
        assertEquals(checklistReminderMessage(weekly, evening, weekly.items.filter { it.id == "itm_ibu" }, weekly.reminderDaysOn(today)), quiet)
        val nudged = checklistTestReminder(weekly, evening, progressOf(weekly, sunday), today)?.words(null)
        assertEquals(checklistReminderMessage(weekly, evening, weekly.items.filter { it.id == "itm_ibu" || it.id == "itm_walk" }, weekly.reminderDaysOn(today)), nudged)
        // Walked on Tuesday, Sunday asks for the pills alone again.
        val walked = AppState(checklists = listOf(weekly), checklistDays = listOf(ChecklistDay(weekly.id, "2026-10-13", 1L, setOf("evening/itm_walk"))))
        assertEquals(quiet, checklistTestReminder(weekly, evening, walked.progressOf(weekly, sunday.toString(), HolidaySchedule()), today)?.words(null))
    }

    private fun progressOf(checklist: ChecklistConfiguration, day: LocalDate = today) =
        AppState(checklists = listOf(checklist)).progressOf(checklist, day.toString(), HolidaySchedule())

    private fun readOnly(state: AppState) = object : TrackerRepository {
        override fun load() = state
        override fun save(state: AppState) = error("read only")
        override fun update(transform: (AppState) -> AppState): TrackerUpdateResult = error("read only")
    }


    private fun <T : Any> assertNotNull(value: T?): T = requireNotNull(value) { "The coordinator did not notify." }
}

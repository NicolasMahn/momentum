package de.nicolasmahn.momentum.persistence

import android.content.Context
import de.nicolasmahn.momentum.domain.holiday.DayRange
import de.nicolasmahn.momentum.domain.holiday.HolidaySchedule
import de.nicolasmahn.momentum.persistence.json.parseObject
import de.nicolasmahn.momentum.widget.MoWidgetProvider
import java.time.LocalDate
import org.json.JSONArray
import org.json.JSONObject

interface HolidayRepository {
    fun load(): HolidaySchedule
    fun save(schedule: HolidaySchedule): Boolean
}

class PreferencesHolidayRepository internal constructor(
    private val store: TrackerStringStore,
    private val onWritten: () -> Unit = {},
) : HolidayRepository {
    constructor(context: Context) : this(
        personalStringStore(context, PreferencesTrackerRepository.PREFERENCES_NAME),
        onWritten = { MoWidgetProvider.refresh(context) },
    )

    override fun load(): HolidaySchedule = store.read(SettingsKey)
        ?.let(HolidayScheduleCodec::decode)
        ?: HolidaySchedule()

    override fun save(schedule: HolidaySchedule): Boolean = runCatching {
        store.write(SettingsKey, HolidayScheduleCodec.encode(schedule))
    }.getOrDefault(false).also { if (it) onWritten() }

    internal companion object {
        const val SettingsKey = "holiday_settings_v1"
    }
}

/** Current holiday and sick-day ranges; prior formats are converted outside the app. */
internal object HolidayScheduleCodec {
    fun encode(schedule: HolidaySchedule): String = JSONObject()
        .put("version", 2)
        .put("ranges", schedule.holidays.encode())
        .put("sickRanges", schedule.sickDays.encode())
        .toString()

    fun decode(raw: String): HolidaySchedule {
        val json = parseObject(raw)
        val version = json.get("version") as? Int ?: error("Invalid holiday settings version")
        require(version == 2) { "Unsupported holiday settings version" }
        require(json.keys().asSequence().toSet() == setOf("version", "ranges", "sickRanges"))
        return HolidaySchedule(
            holidays = json.ranges("ranges"),
            sickDays = json.ranges("sickRanges"),
        )
    }

    private fun List<DayRange>.encode() = JSONArray().apply {
        forEach { range -> put(JSONObject().put("start", range.start.toString()).put("end", range.end.toString())) }
    }

    private fun JSONObject.ranges(field: String): List<DayRange> {
        val array = get(field) as? JSONArray ?: error("Invalid $field")
        return (0 until array.length()).map { index ->
            val range = array.get(index) as? JSONObject ?: error("Invalid range")
            require(range.keys().asSequence().toSet() == setOf("start", "end"))
            DayRange(
                start = LocalDate.parse(range.get("start") as? String ?: error("Invalid start date")),
                end = LocalDate.parse(range.get("end") as? String ?: error("Invalid end date")),
            )
        }
    }
}

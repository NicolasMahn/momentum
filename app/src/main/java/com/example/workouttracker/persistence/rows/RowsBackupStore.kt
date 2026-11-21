package com.example.workouttracker.persistence.rows

import org.json.JSONObject

/**
 * The rows as a backup holds them: the store `momentum_rows`, one entry per live row, named
 * `kind/id`, holding its position and its body exactly as stored, and the layout under
 * [FormatEntry]. It is a store added after schema 32, so a backup from before the rows lacks it.
 */
internal object RowsBackupStore {
    const val StoreName = com.example.workouttracker.persistence.json.RowsStoreName
    const val FormatEntry = "~format"

    fun values(rows: List<StoredRow>, format: String?): Map<String, String> = buildMap {
        rows.forEach { row ->
            put("${row.kind}/${row.id}", envelope(row))
        }
        format?.let { put(FormatEntry, it) }
    }

    private fun envelope(row: StoredRow): String = JSONObject().put("position", row.position).put("body", row.body).toString()

    /** The rows and the layout [values] hold; anything else in them is refused. */
    fun rows(values: Map<String, String>): Pair<List<StoredRow>, String?> {
        val rows = values.filterKeys { it != FormatEntry }.map { (key, raw) ->
            val kind = key.substringBefore('/')
            val id = key.substringAfter('/', missingDelimiterValue = "")
            require(kind.isNotEmpty() && id.isNotEmpty()) { "Invalid row name" }
            val entry = JSONObject(raw)
            require(entry.keys().asSequence().toSet() == setOf("position", "body")) { "Invalid row" }
            val position = (entry.get("position") as? Number)?.toLong()?.takeIf { it >= 0 && entry.get("position") !is Double } ?: error("Invalid row position")
            val row = StoredRow(kind, id, entry.get("body") as? String ?: error("Invalid row body"), position)
            // A restore compares what it wrote with what the database gives back, which is this
            // exact spelling; any other would never compare equal, and the restore never finish.
            require(raw == envelope(row)) { "A row is not written as a backup writes it" }
            row
        }
        return rows to values[FormatEntry]
    }
}

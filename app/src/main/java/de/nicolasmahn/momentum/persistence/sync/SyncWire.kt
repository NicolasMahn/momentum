package de.nicolasmahn.momentum.persistence.sync

import de.nicolasmahn.momentum.persistence.json.array
import de.nicolasmahn.momentum.persistence.json.objects
import de.nicolasmahn.momentum.persistence.json.parseObject
import de.nicolasmahn.momentum.persistence.json.requireFields
import de.nicolasmahn.momentum.persistence.json.text
import de.nicolasmahn.momentum.persistence.rows.SyncedRow
import org.json.JSONArray
import org.json.JSONObject

/**
 * The exchange as JSON, for a server that does not exist yet: fixed now, so it starts from a
 * protocol and not from the phone's classes. Strict both ways, like the stored format. A
 * tombstone's body is null. [Protocol] is the exchange's own shape and changes only when the
 * meaning of one of its fields does; `format` is the layout of the rows' bodies, which the two
 * sides compare before either takes anything.
 */
internal object SyncWire {
    const val Protocol = 1

    fun encode(request: SyncRequest): String = JSONObject()
        .put("protocol", Protocol)
        .put("device", request.device)
        .put("format", request.format)
        .put("cursor", request.cursor ?: JSONObject.NULL)
        .put("rows", encodeRows(request.rows))
        .toString()

    fun decodeRequest(raw: String): SyncRequest {
        val root = parseObject(raw).apply { requireFields(setOf("protocol", "device", "format", "cursor", "rows")) }
        requireProtocol(root)
        val cursor = if (root.isNull("cursor")) null else root.text("cursor")
        return SyncRequest(root.text("device").also { require(it.isNotBlank()) { "A device has an ID" } }, root.format(), cursor, decodeRows(root.array("rows")))
    }

    fun encode(response: SyncResponse): String = JSONObject()
        .put("protocol", Protocol)
        .put("format", response.format)
        .put("cursor", response.cursor)
        .put("rows", encodeRows(response.rows))
        .toString()

    fun decodeResponse(raw: String): SyncResponse {
        val root = parseObject(raw).apply { requireFields(setOf("protocol", "format", "cursor", "rows")) }
        requireProtocol(root)
        return SyncResponse(root.format(), root.text("cursor"), decodeRows(root.array("rows")))
    }

    private fun JSONObject.format() = text("format").also { require(it.isNotBlank()) { "A layout has a name" } }

    private fun requireProtocol(root: JSONObject) = require(root.get("protocol") == Protocol) { "Unsupported sync protocol" }

    private fun encodeRows(rows: List<SyncedRow>) = JSONArray().apply {
        rows.forEach { row ->
            put(JSONObject().put("kind", row.kind).put("id", row.id).put("body", row.body ?: JSONObject.NULL).put("changed", row.changed).put("position", row.position))
        }
    }

    private fun decodeRows(rows: JSONArray): List<SyncedRow> = rows.objects().map { row ->
        row.requireFields(setOf("kind", "id", "body", "changed", "position"))
        val body = if (row.isNull("body")) null else row.text("body")
        val position = (row.get("position") as? Number)?.takeIf { it is Int || it is Long }?.toLong() ?: error("A row's position is a whole number")
        SyncedRow(row.text("kind"), row.text("id"), body, row.text("changed"), position).also {
            require(it.kind.isNotBlank() && it.id.isNotBlank()) { "A row has a kind and an ID" }
            require(StampPattern.matches(it.changed)) { "A row's stamp is not a clock reading" }
        }
    }.also { decoded -> require(decoded.distinctBy { it.kind to it.id }.size == decoded.size) { "A row appears twice" } }

    private val StampPattern = Regex("""\d{15}-\d{5}-[^\s]+""")
}

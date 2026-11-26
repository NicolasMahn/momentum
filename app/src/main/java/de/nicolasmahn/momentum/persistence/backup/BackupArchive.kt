package de.nicolasmahn.momentum.persistence.backup

import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest
import java.time.Instant
import java.math.BigDecimal
import org.json.JSONArray
import org.json.JSONObject
import de.nicolasmahn.momentum.persistence.json.AddedAfterBaseline
import org.json.JSONTokener

data class BackupSnapshot(val stores: Map<String, Map<String, String>>)

/** Readable, portable copies. The checksum detects damage; it does not authenticate the author. */
object BackupArchive {
    const val MaxArchiveBytes = 16 * 1024 * 1024
    /** The stores schema 32 had; every backup holds them. */
    private val BaselineStoreNames: Set<String> = setOf(
        "workout_tracker",
        "momentum_reminders",
        "momentum_feature_visibility",
    )
    val StoreNames: Set<String> = BaselineStoreNames + AddedAfterBaseline.stores

    /**
     * [stores] as this build keeps them. Every store schema 32 had must be there; a store added
     * after it may be missing from a backup or journal written before it, and is then empty, so
     * restoring leaves it empty.
     */
    internal fun withAddedStores(stores: Map<String, Map<String, String>>): BackupSnapshot {
        require(stores.keys.containsAll(BaselineStoreNames) && StoreNames.containsAll(stores.keys)) { "Backup must contain every personal store" }
        return BackupSnapshot(StoreNames.associateWith { stores[it].orEmpty() })
    }
    private val envelopeKeys = setOf("format", "version", "createdAt", "stores", "readableData", "sha256")

    fun encode(snapshot: BackupSnapshot): ByteArray {
        val stores = JSONObject()
        withAddedStores(snapshot.stores).stores.toSortedMap().forEach { (name, values) ->
            stores.put(name, JSONObject().apply {
                values.toSortedMap().forEach { (key, value) -> put(key, value) }
            })
        }
        val envelope = JSONObject()
            .put("format", "momentum-backup")
            .put("version", 1)
            .put("createdAt", Instant.now().toString())
            .put("stores", stores)
            .put("readableData", readableData(stores))
        val canonical = canonicalJson(envelope).toByteArray(Charsets.UTF_8)
        require(canonical.size <= MaxArchiveBytes) { "Backup is too large" }
        envelope.put("sha256", checksum(canonical))
        return envelope.toString(2).toByteArray(Charsets.UTF_8).also {
            require(it.size <= MaxArchiveBytes) { "Backup is too large" }
        }
    }

    /** Preserves raw data for rescue copies as well; current-format checks happen before import. */
    fun decode(bytes: ByteArray): BackupSnapshot {
        require(bytes.size in 1..MaxArchiveBytes) { "Invalid backup size" }
        val raw = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes)).toString()
        require('\u0000' !in raw) { "Invalid JSON control character" }
        val parser = JSONTokener(raw)
        val envelope = parser.nextValue() as? JSONObject ?: error("Backup must be an object")
        require(parser.nextClean() == '\u0000') { "Unexpected trailing backup content" }
        require(envelope.keys().asSequence().toSet() == envelopeKeys) { "Invalid backup envelope" }
        require(envelope.get("format") == "momentum-backup") { "Unsupported backup format" }
        require(envelope.get("version") is Int && envelope.getInt("version") == 1) { "Unsupported backup version" }
        val createdAt = envelope.get("createdAt") as? String ?: error("Invalid backup timestamp")
        Instant.parse(createdAt)
        val stores = envelope.get("stores") as? JSONObject ?: error("Invalid backup stores")
        val snapshot = withAddedStores(stores.keys().asSequence().associateWith { name ->
            val values = stores.get(name) as? JSONObject ?: error("Invalid personal store")
            values.keys().asSequence().associateWith { key ->
                values.get(key) as? String ?: error("Invalid stored value")
            }
        })
        val readable = envelope.get("readableData") as? JSONObject ?: error("Invalid readable backup view")
        require(canonicalJson(readable) == canonicalJson(readableData(stores))) { "Readable backup view disagrees with stored data" }
        val expected = envelope.remove("sha256") as? String ?: error("Invalid backup checksum")
        require(expected.matches(Regex("[0-9a-f]{64}"))) { "Invalid backup checksum" }
        require(checksum(canonicalJson(envelope).toByteArray(Charsets.UTF_8)) == expected) { "Backup checksum mismatch" }
        return snapshot
    }

    private fun checksum(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }

    // A fixed serialization avoids Android/JVM JSON implementations choosing different key order
    // or string escapes. Raw strings remain authoritative; the readable view is always derived.
    internal fun canonicalJson(value: Any): String = when (value) {
        is JSONObject -> value.keys().asSequence().sorted().joinToString(",", "{", "}") { key ->
            "${quote(key)}:${canonicalJson(value.get(key))}"
        }
        is String -> quote(value)
        is JSONArray -> (0 until value.length()).joinToString(",", "[", "]") { canonicalJson(value.get(it)) }
        is Number -> BigDecimal(value.toString()).stripTrailingZeros().toPlainString()
        is Boolean -> value.toString()
        JSONObject.NULL -> "null"
        else -> error("Unsupported archive value")
    }

    private fun readableData(stores: JSONObject): JSONObject = JSONObject().apply {
        stores.keys().asSequence().forEach { storeName ->
            val values = stores.getJSONObject(storeName)
            put(storeName, JSONObject().apply {
                values.keys().asSequence().forEach { key ->
                    val raw = values.getString(key)
                    val parsed = runCatching {
                        require('\u0000' !in raw)
                        val parser = JSONTokener(raw)
                        val value = parser.nextValue()
                        require(parser.nextClean() == '\u0000')
                        require(value is JSONObject || value is JSONArray)
                        value
                    }.getOrNull()
                    put(key, parsed ?: raw)
                }
            })
        }
    }

    private fun quote(value: String): String = buildString {
        append('"')
        value.forEach { character ->
            when {
                character == '"' || character == '\\' -> { append('\\'); append(character) }
                character.code < 32 || character.isSurrogate() -> {
                    append("\\u")
                    append(character.code.toString(16).padStart(4, '0'))
                }
                else -> append(character)
            }
        }
        append('"')
    }
}

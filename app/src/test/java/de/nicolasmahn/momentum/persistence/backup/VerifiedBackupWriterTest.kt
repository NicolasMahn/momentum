package de.nicolasmahn.momentum.persistence.backup

import java.io.IOException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VerifiedBackupWriterTest {
    private val snapshot = CurrentBackupFixture.snapshot()

    @Test fun verifiesExactValuesFromEveryStore() {
        val destination = Destination()
        val id = VerifiedBackupWriter(destination).write(snapshot, "backup")
        assertEquals(listOf(id), destination.readIds)
        assertEquals(snapshot, BackupArchive.decode(destination.generations.getValue(id)))
    }

    @Test fun interruptedWritePreservesPreviousVerifiedGeneration() {
        val destination = Destination()
        val writer = VerifiedBackupWriter(destination)
        val first = writer.write(snapshot, "backup")
        val preserved = destination.generations.getValue(first).copyOf()
        destination.interruptWrite = true
        assertTrue(runCatching { writer.write(snapshot, "backup") }.isFailure)
        assertArrayEquals(preserved, destination.generations.getValue(first))
        assertEquals(snapshot, BackupArchive.decode(destination.generations.getValue(first)))
        assertEquals(2, destination.generations.size)
        assertEquals(listOf(first), destination.readIds)
    }

    @Test fun truncatedReadbackNeverReportsSuccess() {
        val destination = Destination().apply { transformRead = { it.copyOf(it.size / 2) } }
        assertTrue(runCatching { VerifiedBackupWriter(destination).write(snapshot, "backup") }.isFailure)
    }

    @Test fun tamperedReadbackNeverReportsSuccess() {
        val destination = Destination().apply {
            transformRead = { bytes ->
                bytes.toString(Charsets.UTF_8)
                    .replace("Piano", "Organ")
                    .toByteArray(Charsets.UTF_8)
            }
        }
        assertTrue(runCatching { VerifiedBackupWriter(destination).write(snapshot, "backup") }.isFailure)
    }

    @Test fun validArchiveContainingDifferentRecordsIsRejected() {
        val other = CurrentBackupFixture.snapshot().let { source ->
            BackupSnapshot(source.stores + ("workout_tracker" to source.stores.getValue("workout_tracker").mapValues { (_, value) -> value.replace("Piano", "Organ") }))
        }
        val substitute = BackupArchive.encode(other)
        val destination = Destination().apply { transformRead = { substitute } }
        assertTrue(runCatching { VerifiedBackupWriter(destination).write(snapshot, "backup") }.isFailure)
    }

    @Test fun malformedReadbackNeverReportsSuccess() {
        val destination = Destination().apply { transformRead = { "invalid JSON".toByteArray() } }
        assertTrue(runCatching { VerifiedBackupWriter(destination).write(snapshot, "backup") }.isFailure)
    }

    @Test fun readFailurePreservesPriorGeneration() {
        val destination = Destination()
        val writer = VerifiedBackupWriter(destination)
        val first = writer.write(snapshot, "backup")
        val preserved = destination.generations.getValue(first).copyOf()
        destination.transformRead = { throw IOException("Unavailable") }
        assertTrue(runCatching { writer.write(snapshot, "backup") }.isFailure)
        assertArrayEquals(preserved, destination.generations.getValue(first))
    }

    @Test fun obsoleteOrMalformedStoresAreRejectedBeforeCreatingANewGeneration() {
        val destination = Destination()
        val writer = VerifiedBackupWriter(destination)
        val first = writer.write(snapshot, "backup")
        val preserved = destination.generations.getValue(first).copyOf()
        val malformed = BackupSnapshot(snapshot.stores + ("workout_tracker" to mapOf(
            "state_v7" to "{malformed",
            "state_v1" to """{"weightLogs":[],"sessions":[]}""",
        )))

        assertTrue(runCatching { writer.write(malformed, "backup") }.isFailure)

        assertArrayEquals(preserved, destination.generations.getValue(first))
        assertEquals(1, destination.generations.size)
        assertEquals(listOf(first), destination.readIds)
    }

    @Test fun damagedRecordsAboutToBeReplacedAreKeptExactly() {
        val destination = Destination()
        val damaged = BackupSnapshot(snapshot.stores + ("workout_tracker" to mapOf("state_v7" to "{malformed")))

        val id = VerifiedBackupWriter(destination).preserve(damaged, "copy")

        assertEquals(damaged, BackupArchive.decode(destination.generations.getValue(id)))
        assertEquals(listOf(id), destination.readIds)
    }

    @Test fun aPreservedCopyThatDoesNotReadBackFails() {
        val destination = Destination().apply { transformRead = { it.copyOf(it.size / 2) } }
        assertTrue(runCatching { VerifiedBackupWriter(destination).preserve(snapshot, "copy") }.isFailure)
    }

    private class Destination : BackupDestination {
        val generations = linkedMapOf<String, ByteArray>()
        val readIds = mutableListOf<String>()
        var interruptWrite = false
        var transformRead: (ByteArray) -> ByteArray = { it }

        override fun writeNew(name: String, bytes: ByteArray): String {
            val id = "generation-${generations.size + 1}"
            generations[id] = if (interruptWrite) bytes.copyOf(bytes.size / 2) else bytes.copyOf()
            if (interruptWrite) throw IOException("Interrupted write")
            return id
        }

        override fun read(id: String): ByteArray {
            readIds += id
            return transformRead(generations.getValue(id).copyOf())
        }

        override fun delete(id: String) = error("The writer never removes a generation")
        override fun list(): List<BackupFile> = error("The writer never looks at the folder")
    }
}

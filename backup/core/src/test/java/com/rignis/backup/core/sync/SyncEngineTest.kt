package com.rignis.backup.core.sync

import com.rignis.auth.domain.CanAuthenticate
import com.rignis.auth.domain.CipherManager
import com.rignis.auth.domain.EncryptedData
import com.rignis.backup.core.crypto.BackupCipher
import com.rignis.backup.core.crypto.BackupEnvelope
import com.rignis.backup.core.crypto.BackupKeyHolder
import com.rignis.backup.core.crypto.SecretPayload
import com.rignis.backup.core.testing.FakeDriveClient
import com.rignis.backup.core.testing.FakeSyncDataStore
import com.rignis.store.api.SecretSyncRef
import com.rignis.store.api.StagedBackupBlob
import com.rignis.store.api.SyncItemStatus
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.security.SecureRandom

// A pass-through Keystore stand-in: encryptData/decryptData just round-trip
// bytes with a fixed marker, since SyncEngine only cares that push never
// needs it and pull/park call it exactly once per secret with the right body.
private class FakeCipherManager : CipherManager {
    override suspend fun encryptData(data: ByteArray): EncryptedData = EncryptedData(data, byteArrayOf(9))
    override suspend fun decryptData(body: ByteArray, iv: ByteArray): Result<ByteArray> = Result.success(body)
    override suspend fun canAuthenticate(): CanAuthenticate = CanAuthenticate.YES
}

class SyncEngineTest {

    private lateinit var driveClient: FakeDriveClient
    private lateinit var syncDataStore: FakeSyncDataStore
    private lateinit var keyHolder: BackupKeyHolder
    private lateinit var cipher: BackupCipher
    private lateinit var engine: SyncEngine
    private val cipherManager = FakeCipherManager()
    private val epoch = "epoch-1"

    @Before
    fun setUp() {
        driveClient = FakeDriveClient()
        syncDataStore = FakeSyncDataStore()
        keyHolder = BackupKeyHolder()
        val rawKey = ByteArray(32).also { SecureRandom().nextBytes(it) }
        keyHolder.unlock(rawKey, epoch)
        cipher = BackupCipher(BackupCipher.keyFrom(rawKey))
        engine = SyncEngine(driveClient, syncDataStore, keyHolder)
    }

    private fun remoteEnvelopeFor(secretId: String, version: Long, title: String, body: ByteArray): ByteArray {
        val sealed = cipher.seal(secretId, version, SecretPayload.pack(title, body))
        return BackupEnvelope.pack(sealed.iv, sealed.cipherText)
    }

    @Test
    fun run_withNoBackupKey_doesNothing() = runTest {
        keyHolder.lock()
        syncDataStore.putSecret(SecretSyncRef("id1", "t", 1, 100, null, null))

        val outcome = engine.run("token", cipherManager)

        assertEquals(SyncOutcome(0, 0, 0, 0, 0), outcome)
    }

    @Test
    fun run_localOnlyAndStaged_pushesAsNewFile() = runTest {
        syncDataStore.putSecret(SecretSyncRef("id1", "Wi-Fi", 1, 100, null, null))
        val sealed = cipher.seal("id1", 1, SecretPayload.pack("Wi-Fi", "secret".toByteArray()))
        syncDataStore.stageBlob(StagedBackupBlob("id1", 1, sealed.cipherText, sealed.iv, epoch))

        val outcome = engine.run("token", cipherManager)

        assertEquals(1, outcome.pushed)
        assertEquals(1L, syncDataStore.secrets["id1"]?.lastSyncedVersion)
        val fileId = driveClient.listAppDataFiles("token").getOrThrow().single().fileId
        assertEquals("id1", driveClient.propertiesOf(fileId)?.get("secretId"))
        assertEquals("false", driveClient.propertiesOf(fileId)?.get("deleted"))
    }

    @Test
    fun run_localOnlyButNotStaged_isSkippedAndRecordedNotStaged() = runTest {
        syncDataStore.putSecret(SecretSyncRef("id1", "Wi-Fi", 1, 100, null, null))

        val outcome = engine.run("token", cipherManager)

        assertEquals(1, outcome.skippedNotStaged)
        assertEquals(0, outcome.pushed)
        assertEquals(SyncItemStatus.NOT_STAGED, syncDataStore.itemStates["id1"]?.status)
        assertTrue(driveClient.listAppDataFiles("token").getOrThrow().isEmpty())
    }

    @Test
    fun run_remoteOnlyNotDeleted_pullsAndAppliesLocally() = runTest {
        val content = remoteEnvelopeFor("id1", 1, "Wi-Fi", "secret-body".toByteArray())
        driveClient.seedFile("id1.bin", mapOf("secretId" to "id1", "version" to "1", "deleted" to "false"), content)

        val outcome = engine.run("token", cipherManager)

        assertEquals(1, outcome.pulled)
        val applied = syncDataStore.appliedEntries["id1"]
        assertEquals("Wi-Fi", applied?.title)
        assertEquals("secret-body", applied?.encryptedBody?.let { String(it) })
        assertEquals(1L, syncDataStore.secrets["id1"]?.lastSyncedVersion)
    }

    @Test
    fun run_remoteOnlyDeleted_doesNothing() = runTest {
        driveClient.seedFile("id1.bin", mapOf("secretId" to "id1", "version" to "1", "deleted" to "true"), ByteArray(0))

        val outcome = engine.run("token", cipherManager)

        assertEquals(SyncOutcome(0, 0, 0, 0, 0), outcome)
        assertNull(syncDataStore.secrets["id1"])
    }

    @Test
    fun run_bothSidesChangedDifferently_parksConflictInsteadOfPicking() = runTest {
        syncDataStore.putSecret(SecretSyncRef("id1", "Local title", 3, 100, null, 1))
        val sealed = cipher.seal("id1", 3, SecretPayload.pack("Local title", "local-body".toByteArray()))
        syncDataStore.stageBlob(StagedBackupBlob("id1", 3, sealed.cipherText, sealed.iv, epoch))
        val remoteContent = remoteEnvelopeFor("id1", 2, "Remote title", "remote-body".toByteArray())
        driveClient.seedFile(
            "id1.bin", mapOf("secretId" to "id1", "version" to "2", "deleted" to "false"), remoteContent
        )

        val outcome = engine.run("token", cipherManager)

        assertEquals(1, outcome.conflicts)
        assertEquals(0, outcome.pushed)
        assertEquals(0, outcome.pulled)
        val parked = syncDataStore.parkedRemotes["id1"]
        assertEquals("Remote title", parked?.title)
        assertEquals(SyncItemStatus.CONFLICT, syncDataStore.itemStates["id1"]?.status)
        // Local content must be left untouched until the user resolves it.
        assertEquals(3L, syncDataStore.secrets["id1"]?.version)
    }

    @Test
    fun run_bothChangedButSameVersion_marksSyncedInsteadOfConflict() = runTest {
        syncDataStore.putSecret(SecretSyncRef("id1", "title", 2, 100, null, null))
        val fileId = driveClient.seedFile("id1.bin", mapOf("secretId" to "id1", "version" to "2", "deleted" to "false"), ByteArray(0))

        val outcome = engine.run("token", cipherManager)

        assertEquals(SyncOutcome(0, 0, 0, 0, 0), outcome)
        assertEquals(2L, syncDataStore.secrets["id1"]?.lastSyncedVersion)
        assertEquals(fileId, driveClient.listAppDataFiles("token").getOrThrow().single().fileId)
    }

    @Test
    fun run_onlyLocalChangedSinceLastSync_pushesPatchToExistingFile() = runTest {
        syncDataStore.putSecret(SecretSyncRef("id1", "title", 2, 100, null, 1))
        val fileId = driveClient.seedFile(
            "id1.bin", mapOf("secretId" to "id1", "version" to "1", "deleted" to "false"), ByteArray(0)
        )
        val sealed = cipher.seal("id1", 2, SecretPayload.pack("title", "new-body".toByteArray()))
        syncDataStore.stageBlob(StagedBackupBlob("id1", 2, sealed.cipherText, sealed.iv, epoch))

        val outcome = engine.run("token", cipherManager)

        assertEquals(1, outcome.pushed)
        assertEquals("2", driveClient.propertiesOf(fileId)?.get("version"))
        assertEquals(2L, syncDataStore.secrets["id1"]?.lastSyncedVersion)
    }

    @Test
    fun run_onlyRemoteChangedSinceLastSync_pullsWithoutConflict() = runTest {
        syncDataStore.putSecret(SecretSyncRef("id1", "old title", 1, 100, null, 1))
        val content = remoteEnvelopeFor("id1", 2, "new title", "new-body".toByteArray())
        driveClient.seedFile("id1.bin", mapOf("secretId" to "id1", "version" to "2", "deleted" to "false"), content)

        val outcome = engine.run("token", cipherManager)

        assertEquals(1, outcome.pulled)
        assertEquals(0, outcome.conflicts)
        assertEquals("new title", syncDataStore.appliedEntries["id1"]?.title)
    }

    @Test
    fun run_localTombstone_pushesDeletedFlagToRemote() = runTest {
        syncDataStore.putSecret(SecretSyncRef("id1", "title", 2, 100, 100, 1))
        val fileId = driveClient.seedFile(
            "id1.bin", mapOf("secretId" to "id1", "version" to "1", "deleted" to "false"), ByteArray(0)
        )
        val sealed = cipher.seal("id1", 2, SecretPayload.pack("", ByteArray(0)))
        syncDataStore.stageBlob(StagedBackupBlob("id1", 2, sealed.cipherText, sealed.iv, epoch))

        val outcome = engine.run("token", cipherManager)

        assertEquals(1, outcome.pushed)
        assertEquals("true", driveClient.propertiesOf(fileId)?.get("deleted"))
    }

    @Test
    fun run_remoteTombstonePull_appliesLocalTombstone() = runTest {
        syncDataStore.putSecret(SecretSyncRef("id1", "title", 1, 100, null, 1))
        driveClient.seedFile(
            "id1.bin", mapOf("secretId" to "id1", "version" to "2", "deleted" to "true"), ByteArray(0)
        )

        val outcome = engine.run("token", cipherManager)

        assertEquals(1, outcome.pulled)
        assertTrue(syncDataStore.secrets["id1"]?.isDeleted == true)
        assertEquals(2L, syncDataStore.secrets["id1"]?.version)
    }
}

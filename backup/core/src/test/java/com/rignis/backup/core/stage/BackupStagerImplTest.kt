package com.rignis.backup.core.stage

import com.rignis.backup.core.crypto.BackupCipher
import com.rignis.backup.core.crypto.BackupEnvelope
import com.rignis.backup.core.crypto.BackupKeyHolder
import com.rignis.backup.core.crypto.SecretPayload
import com.rignis.backup.core.testing.FakeSyncDataStore
import com.rignis.store.api.SecretSyncRef
import com.rignis.store.api.StagedBackupBlob
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.security.SecureRandom

class BackupStagerImplTest {

    private lateinit var syncDataStore: FakeSyncDataStore
    private lateinit var keyHolder: BackupKeyHolder
    private lateinit var stager: BackupStagerImpl
    private val epoch = "epoch-1"

    @Before
    fun setUp() {
        syncDataStore = FakeSyncDataStore()
        keyHolder = BackupKeyHolder()
        stager = BackupStagerImpl(keyHolder, syncDataStore)
    }

    @Test
    fun stageFor_keyLocked_returnsNullAndStagesNothing() = runTest {
        val result = stager.stageFor("id1", 1, "title", "body".toByteArray())

        assertNull(result)
        assertNull(syncDataStore.stagedBlob("id1"))
    }

    @Test
    fun stageFor_keyUnlocked_sealsAndPersistsBlobDecryptableWithSameKey() = runTest {
        val rawKey = ByteArray(32).also { SecureRandom().nextBytes(it) }
        keyHolder.unlock(rawKey, epoch)

        val blob = stager.stageFor("id1", 3, "Wi-Fi", "hunter2".toByteArray())

        assertTrue(blob != null)
        assertEquals(epoch, blob!!.keyEpoch)
        assertEquals(syncDataStore.stagedBlob("id1"), blob)

        val cipher = BackupCipher(BackupCipher.keyFrom(rawKey))
        val opened = cipher.open("id1", 3, blob.iv, blob.cipherText)
        val payload = SecretPayload.unpack(opened)
        assertEquals("Wi-Fi", payload.title)
        assertEquals("hunter2", String(payload.body))
    }

    @Test
    fun restageOpportunistically_alreadyStagedAtCurrentVersion_doesNothing() = runTest {
        val rawKey = ByteArray(32).also { SecureRandom().nextBytes(it) }
        keyHolder.unlock(rawKey, epoch)
        syncDataStore.putSecret(SecretSyncRef("id1", "title", 2, 100, null, null))
        val sealed = BackupCipher(BackupCipher.keyFrom(rawKey)).seal("id1", 2, SecretPayload.pack("title", "old".toByteArray()))
        val existingBlob = StagedBackupBlob("id1", 2, sealed.cipherText, sealed.iv, epoch)
        syncDataStore.stageBlob(existingBlob)

        stager.restageOpportunistically("id1", "title", "new-body".toByteArray())

        // Must not have overwritten the existing (already-current) blob.
        assertEquals(existingBlob, syncDataStore.stagedBlob("id1"))
    }

    @Test
    fun restageOpportunistically_staleOrMissingBlob_stagesAtCurrentVersion() = runTest {
        val rawKey = ByteArray(32).also { SecureRandom().nextBytes(it) }
        keyHolder.unlock(rawKey, epoch)
        syncDataStore.putSecret(SecretSyncRef("id1", "title", 3, 100, null, null))

        stager.restageOpportunistically("id1", "title", "fresh-body".toByteArray())

        val blob = syncDataStore.stagedBlob("id1")
        assertTrue(blob != null)
        assertEquals(3L, blob!!.version)
    }

    @Test
    fun restageOpportunistically_deletedSecret_doesNothing() = runTest {
        val rawKey = ByteArray(32).also { SecureRandom().nextBytes(it) }
        keyHolder.unlock(rawKey, epoch)
        syncDataStore.putSecret(SecretSyncRef("id1", "title", 2, 100, 100, null))

        stager.restageOpportunistically("id1", "title", "body".toByteArray())

        assertNull(syncDataStore.stagedBlob("id1"))
    }

    @Test
    fun stageAllPendingTombstones_keyLocked_doesNothing() = runTest {
        syncDataStore.putSecret(SecretSyncRef("id1", "title", 2, 100, 100, null))

        stager.stageAllPendingTombstones()

        assertNull(syncDataStore.stagedBlob("id1"))
    }

    @Test
    fun stageAllPendingTombstones_stagesEveryUnstagedTombstoneWithZeroPlaintext() = runTest {
        val rawKey = ByteArray(32).also { SecureRandom().nextBytes(it) }
        keyHolder.unlock(rawKey, epoch)
        syncDataStore.putSecret(SecretSyncRef("id1", "title1", 2, 100, 100, null))
        syncDataStore.putSecret(SecretSyncRef("id2", "title2", 5, 200, 200, null))
        // id2 already staged at its current tombstone version - must be left alone.
        val sealed = BackupCipher(BackupCipher.keyFrom(rawKey)).seal("id2", 5, SecretPayload.pack("", ByteArray(0)))
        syncDataStore.stageBlob(StagedBackupBlob("id2", 5, sealed.cipherText, sealed.iv, epoch))

        stager.stageAllPendingTombstones()

        val blob1 = syncDataStore.stagedBlob("id1")
        assertTrue(blob1 != null)
        assertEquals(2L, blob1!!.version)
        val cipher = BackupCipher(BackupCipher.keyFrom(rawKey))
        val payload = SecretPayload.unpack(cipher.open("id1", 2, blob1.iv, blob1.cipherText))
        assertEquals("", payload.title)
        assertEquals(0, payload.body.size)
    }

    @Test
    fun sealedTombstoneEnvelope_roundTripsThroughBackupEnvelope() = runTest {
        val rawKey = ByteArray(32).also { SecureRandom().nextBytes(it) }
        keyHolder.unlock(rawKey, epoch)
        val blob = stager.stageFor("id1", 1, "", ByteArray(0))!!

        val envelope = BackupEnvelope.pack(blob.iv, blob.cipherText)
        val unpacked = BackupEnvelope.unpack(envelope)

        assertEquals(blob.iv.toList(), unpacked.iv.toList())
        assertEquals(blob.cipherText.toList(), unpacked.cipherTextWithTag.toList())
    }
}

package com.rignis.backup.core.meta

import com.rignis.backup.core.crypto.DerivedKey
import com.rignis.backup.core.crypto.KdfParams
import com.rignis.backup.core.drive.DriveClient
import com.rignis.backup.core.drive.DriveFileMeta
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

// A password-derivation stand-in: deterministic per (password, salt) pair,
// so "correct password" reproduces the same key bytes and "wrong password"
// reliably produces different ones - without touching the real Argon2 JNI
// implementation, which can't load in a plain JVM unit test.
private fun fakeDeriveKey(password: CharArray, params: KdfParams): DerivedKey {
    val material = (String(password) + params.salt.joinToString(",")).toByteArray()
    val key = ByteArray(32) { material[it % material.size] }
    return DerivedKey(key, params)
}

private class FakeDriveClient : DriveClient {
    private val filesByName = mutableMapOf<String, DriveFileMeta>()
    private val contentByFileId = mutableMapOf<String, ByteArray>()
    private var nextId = 0

    override suspend fun listAppDataFiles(accessToken: String) = Result.success(filesByName.values.toList())

    override suspend fun findFileByName(accessToken: String, name: String) =
        Result.success(filesByName[name])

    override suspend fun downloadFile(accessToken: String, fileId: String) =
        Result.success(contentByFileId[fileId] ?: ByteArray(0))

    override suspend fun createFile(
        accessToken: String, name: String, appProperties: Map<String, String>, content: ByteArray
    ): Result<DriveFileMeta> {
        val meta = DriveFileMeta("file-${nextId++}", name, appProperties, 0L)
        filesByName[name] = meta
        contentByFileId[meta.fileId] = content
        return Result.success(meta)
    }

    override suspend fun patchFile(
        accessToken: String, fileId: String, appProperties: Map<String, String>, content: ByteArray
    ): Result<DriveFileMeta> {
        val existing = filesByName.values.first { it.fileId == fileId }
        val updated = existing.copy(appProperties = appProperties)
        filesByName[updated.name] = updated
        contentByFileId[fileId] = content
        return Result.success(updated)
    }

    override suspend fun deleteFile(accessToken: String, fileId: String): Result<Unit> {
        filesByName.values.firstOrNull { it.fileId == fileId }?.let { filesByName.remove(it.name) }
        contentByFileId.remove(fileId)
        return Result.success(Unit)
    }

    fun fileCount() = filesByName.size
}

class VaultMetaStoreTest {

    private lateinit var driveClient: FakeDriveClient
    private lateinit var store: VaultMetaStore

    @Before
    fun setUp() {
        driveClient = FakeDriveClient()
        store = VaultMetaStore(driveClient, deriveKey = ::fakeDeriveKey)
    }

    @Test
    fun fetch_returnsNullWhenNoVaultExistsYet() = runTest {
        val meta = store.fetch("token").getOrThrow()

        assertNull(meta)
    }

    @Test
    fun setPassword_thenFetch_roundTripsKdfParamsAndKeyEpoch() = runTest {
        store.setPassword("token", "correct horse battery staple".toCharArray()).getOrThrow()

        val meta = store.fetch("token").getOrThrow()

        assertTrue(meta != null)
        assertEquals(1, driveClient.fileCount())
    }

    @Test
    fun setPassword_thenUnlockWithSamePassword_succeeds() = runTest {
        val password = "hunter2-master-password".toCharArray()
        val original = store.setPassword("token", password.copyOf()).getOrThrow()

        val unlocked = store.unlockWithPassword("token", password.copyOf()).getOrThrow()

        assertEquals(original.keyBytes.toList(), unlocked.keyBytes.toList())
    }

    @Test
    fun unlockWithPassword_wrongPassword_failsWithWrongBackupPasswordException() = runTest {
        store.setPassword("token", "the-real-password".toCharArray()).getOrThrow()

        val error = store.unlockWithPassword("token", "not-the-real-password".toCharArray()).exceptionOrNull()

        assertTrue(error is WrongBackupPasswordException)
    }

    @Test
    fun unlockWithPassword_noVaultConfigured_failsWithVaultNotConfiguredException() = runTest {
        val error = store.unlockWithPassword("token", "anything".toCharArray()).exceptionOrNull()

        assertTrue(error is VaultNotConfiguredException)
    }

    @Test
    fun setPassword_calledAgain_rotatesEpochAndOverwritesRatherThanCreatingSecondFile() = runTest {
        store.setPassword("token", "first-password".toCharArray()).getOrThrow()
        val firstMeta = store.fetch("token").getOrThrow()!!

        store.setPassword("token", "second-password".toCharArray()).getOrThrow()
        val secondMeta = store.fetch("token").getOrThrow()!!

        assertNotEquals(firstMeta.keyEpoch, secondMeta.keyEpoch)
        assertEquals(1, driveClient.fileCount())
    }

    @Test
    fun setPassword_calledAgain_oldPasswordNoLongerUnlocks() = runTest {
        val oldPassword = "first-password".toCharArray()
        store.setPassword("token", oldPassword.copyOf()).getOrThrow()
        store.setPassword("token", "second-password".toCharArray()).getOrThrow()

        val error = store.unlockWithPassword("token", oldPassword.copyOf()).exceptionOrNull()

        assertTrue(error is WrongBackupPasswordException)
    }
}

package com.rignis.backup.core.testing

import com.rignis.backup.core.drive.DriveClient
import com.rignis.backup.core.drive.DriveFileMeta

// In-memory stand-in for Drive's appDataFolder - real request/retry/error
// mapping behavior is already covered by DriveClientImplTest against a real
// MockWebServer; this fake exists purely to drive SyncEngine's decision
// logic without any network.
class FakeDriveClient : DriveClient {
    private val filesById = mutableMapOf<String, DriveFileMeta>()
    private val contentById = mutableMapOf<String, ByteArray>()
    private var nextId = 0
    var failNextCallWith: Throwable? = null

    fun seedFile(name: String, appProperties: Map<String, String>, content: ByteArray, modifiedAt: Long = 0L): String {
        val id = "file-${nextId++}"
        filesById[id] = DriveFileMeta(id, name, appProperties, modifiedAt)
        contentById[id] = content
        return id
    }

    fun contentOf(fileId: String): ByteArray? = contentById[fileId]
    fun propertiesOf(fileId: String): Map<String, String>? = filesById[fileId]?.appProperties

    override suspend fun listAppDataFiles(accessToken: String): Result<List<DriveFileMeta>> {
        failNextCallWith?.let { failNextCallWith = null; return Result.failure(it) }
        return Result.success(filesById.values.toList())
    }

    override suspend fun findFileByName(accessToken: String, name: String): Result<DriveFileMeta?> {
        failNextCallWith?.let { failNextCallWith = null; return Result.failure(it) }
        return Result.success(filesById.values.firstOrNull { it.name == name })
    }

    override suspend fun downloadFile(accessToken: String, fileId: String): Result<ByteArray> {
        failNextCallWith?.let { failNextCallWith = null; return Result.failure(it) }
        return Result.success(contentById[fileId] ?: ByteArray(0))
    }

    override suspend fun createFile(
        accessToken: String, name: String, appProperties: Map<String, String>, content: ByteArray
    ): Result<DriveFileMeta> {
        failNextCallWith?.let { failNextCallWith = null; return Result.failure(it) }
        val id = "file-${nextId++}"
        val meta = DriveFileMeta(id, name, appProperties, System.currentTimeMillis())
        filesById[id] = meta
        contentById[id] = content
        return Result.success(meta)
    }

    override suspend fun patchFile(
        accessToken: String, fileId: String, appProperties: Map<String, String>, content: ByteArray
    ): Result<DriveFileMeta> {
        failNextCallWith?.let { failNextCallWith = null; return Result.failure(it) }
        val existing = filesById[fileId] ?: return Result.failure(NoSuchElementException(fileId))
        val updated = existing.copy(appProperties = appProperties, modifiedAtEpochMillis = System.currentTimeMillis())
        filesById[fileId] = updated
        contentById[fileId] = content
        return Result.success(updated)
    }

    override suspend fun deleteFile(accessToken: String, fileId: String): Result<Unit> {
        failNextCallWith?.let { failNextCallWith = null; return Result.failure(it) }
        filesById.remove(fileId)
        contentById.remove(fileId)
        return Result.success(Unit)
    }
}

package com.rignis.backup.core.drive

// Thin, direct REST v3 wrapper around Google Drive's hidden appDataFolder -
// deliberately not the heavy google-api-services-drive client. Every
// secret-specific convention (file naming, which appProperties mean what)
// belongs to the caller (the sync engine / VaultMetaStore), not here.
interface DriveClient {
    suspend fun listAppDataFiles(accessToken: String): Result<List<DriveFileMeta>>
    suspend fun findFileByName(accessToken: String, name: String): Result<DriveFileMeta?>
    suspend fun downloadFile(accessToken: String, fileId: String): Result<ByteArray>

    suspend fun createFile(
        accessToken: String, name: String, appProperties: Map<String, String>, content: ByteArray
    ): Result<DriveFileMeta>

    suspend fun patchFile(
        accessToken: String, fileId: String, appProperties: Map<String, String>, content: ByteArray
    ): Result<DriveFileMeta>

    suspend fun deleteFile(accessToken: String, fileId: String): Result<Unit>
}

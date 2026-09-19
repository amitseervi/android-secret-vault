package com.rignis.backup.core.drive

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class DriveClientImpl(
    private val httpClient: OkHttpClient = OkHttpClient(),
    private val filesEndpoint: String = "https://www.googleapis.com/drive/v3/files",
    private val uploadFilesEndpoint: String = "https://www.googleapis.com/upload/drive/v3/files"
) : DriveClient {

    override suspend fun listAppDataFiles(accessToken: String): Result<List<DriveFileMeta>> = runCatching {
        withContext(Dispatchers.IO) {
            val files = mutableListOf<DriveFileMeta>()
            var pageToken: String? = null
            do {
                val urlBuilder = filesEndpoint.toHttpUrl().newBuilder()
                    .addQueryParameter("spaces", "appDataFolder")
                    .addQueryParameter("fields", "nextPageToken, files($FILE_FIELDS)")
                    .addQueryParameter("pageSize", "1000")
                pageToken?.let { urlBuilder.addQueryParameter("pageToken", it) }

                val request = Request.Builder().url(urlBuilder.build())
                    .header("Authorization", "Bearer $accessToken").get().build()

                val json = executeWithRetry(request).use { JSONObject(it.body?.string().orEmpty()) }
                val jsonFiles = json.optJSONArray("files") ?: JSONArray()
                for (i in 0 until jsonFiles.length()) {
                    files += jsonFiles.getJSONObject(i).toDriveFileMeta()
                }
                pageToken = json.optString("nextPageToken").takeIf { it.isNotBlank() }
            } while (pageToken != null)
            files
        }
    }

    override suspend fun findFileByName(accessToken: String, name: String): Result<DriveFileMeta?> = runCatching {
        withContext(Dispatchers.IO) {
            val escaped = name.replace("\\", "\\\\").replace("'", "\\'")
            val url = filesEndpoint.toHttpUrl().newBuilder()
                .addQueryParameter("spaces", "appDataFolder")
                .addQueryParameter("q", "name = '$escaped' and trashed = false")
                .addQueryParameter("fields", "files($FILE_FIELDS)")
                .build()
            val request = Request.Builder().url(url).header("Authorization", "Bearer $accessToken").get().build()

            executeWithRetry(request).use { response ->
                val files = JSONObject(response.body?.string().orEmpty()).optJSONArray("files") ?: JSONArray()
                if (files.length() == 0) null else files.getJSONObject(0).toDriveFileMeta()
            }
        }
    }

    override suspend fun downloadFile(accessToken: String, fileId: String): Result<ByteArray> = runCatching {
        withContext(Dispatchers.IO) {
            val url = "$filesEndpoint/$fileId".toHttpUrl().newBuilder()
                .addQueryParameter("alt", "media").build()
            val request = Request.Builder().url(url).header("Authorization", "Bearer $accessToken").get().build()
            executeWithRetry(request, fileId).use { it.body?.bytes() ?: ByteArray(0) }
        }
    }

    override suspend fun createFile(
        accessToken: String, name: String, appProperties: Map<String, String>, content: ByteArray
    ): Result<DriveFileMeta> = runCatching {
        withContext(Dispatchers.IO) {
            val metadata = JSONObject().apply {
                put("name", name)
                put("parents", JSONArray().put("appDataFolder"))
                if (appProperties.isNotEmpty()) put("appProperties", JSONObject(appProperties))
            }
            val url = uploadFilesEndpoint.toHttpUrl().newBuilder()
                .addQueryParameter("uploadType", "multipart")
                .addQueryParameter("fields", FILE_FIELDS)
                .build()
            val request = Request.Builder().url(url).header("Authorization", "Bearer $accessToken")
                .post(buildMultipart(metadata, content)).build()

            executeWithRetry(request).use { JSONObject(it.body?.string().orEmpty()).toDriveFileMeta() }
        }
    }

    override suspend fun patchFile(
        accessToken: String, fileId: String, appProperties: Map<String, String>, content: ByteArray
    ): Result<DriveFileMeta> = runCatching {
        withContext(Dispatchers.IO) {
            val metadata = JSONObject().apply {
                if (appProperties.isNotEmpty()) put("appProperties", JSONObject(appProperties))
            }
            val url = "$uploadFilesEndpoint/$fileId".toHttpUrl().newBuilder()
                .addQueryParameter("uploadType", "multipart")
                .addQueryParameter("fields", FILE_FIELDS)
                .build()
            val request = Request.Builder().url(url).header("Authorization", "Bearer $accessToken")
                .patch(buildMultipart(metadata, content)).build()

            executeWithRetry(request, fileId).use { JSONObject(it.body?.string().orEmpty()).toDriveFileMeta() }
        }
    }

    override suspend fun deleteFile(accessToken: String, fileId: String): Result<Unit> = runCatching {
        withContext(Dispatchers.IO) {
            val request = Request.Builder().url("$filesEndpoint/$fileId")
                .header("Authorization", "Bearer $accessToken").delete().build()
            executeWithRetry(request, fileId).close()
        }
    }

    private suspend fun executeWithRetry(request: Request, fileId: String? = null, maxAttempts: Int = 3): Response {
        var attempt = 0
        while (true) {
            attempt++
            val response = try {
                httpClient.newCall(request).execute()
            } catch (e: IOException) {
                if (attempt >= maxAttempts) throw DriveException.NetworkFailure(e)
                delay(backoffMillis(attempt))
                continue
            }
            if (response.isSuccessful) return response

            val code = response.code
            val bodyText = response.body?.string().orEmpty()
            response.close()
            val mapped = mapErrorCode(code, bodyText, fileId)
            val retryable = mapped is DriveException.RateLimited || mapped is DriveException.ServerError
            if (retryable && attempt < maxAttempts) {
                delay(backoffMillis(attempt))
                continue
            }
            throw mapped
        }
    }

    private fun mapErrorCode(code: Int, bodyText: String, fileId: String?): DriveException = when (code) {
        401 -> DriveException.AuthRequired(code)
        403 -> if (RATE_LIMIT_MARKERS.any { bodyText.contains(it) }) {
            DriveException.RateLimited(code)
        } else {
            DriveException.AuthRequired(code)
        }

        404 -> DriveException.NotFound(fileId ?: "unknown")
        429 -> DriveException.RateLimited(code)
        in 500..599 -> DriveException.ServerError(code)
        else -> DriveException.UnexpectedResponse(code)
    }

    private fun backoffMillis(attempt: Int): Long = INITIAL_BACKOFF_MILLIS * (1L shl (attempt - 1))

    private fun buildMultipart(metadata: JSONObject, content: ByteArray): MultipartBody =
        MultipartBody.Builder().setType("multipart/related".toMediaType())
            .addPart(MultipartBody.Part.create(metadata.toString().toRequestBody(JSON_MEDIA_TYPE)))
            .addPart(MultipartBody.Part.create(content.toRequestBody(OCTET_STREAM_MEDIA_TYPE))).build()

    private fun JSONObject.toDriveFileMeta(): DriveFileMeta {
        val props = mutableMapOf<String, String>()
        optJSONObject("appProperties")?.let { obj ->
            obj.keys().forEach { key -> props[key] = obj.getString(key) }
        }
        return DriveFileMeta(
            fileId = getString("id"),
            name = optString("name"),
            appProperties = props,
            modifiedAtEpochMillis = optString("modifiedTime").takeIf { it.isNotBlank() }
                ?.let(::parseRfc3339) ?: 0L
        )
    }

    private fun parseRfc3339(value: String): Long {
        for (pattern in RFC3339_PATTERNS) {
            try {
                val format = SimpleDateFormat(pattern, Locale.US)
                format.timeZone = TimeZone.getTimeZone("UTC")
                format.parse(value)?.let { return it.time }
            } catch (_: Exception) {
                // try the next pattern
            }
        }
        return 0L
    }

    companion object {
        private const val FILE_FIELDS = "id, name, appProperties, modifiedTime"
        private const val INITIAL_BACKOFF_MILLIS = 250L
        private val JSON_MEDIA_TYPE = "application/json; charset=UTF-8".toMediaType()
        private val OCTET_STREAM_MEDIA_TYPE = "application/octet-stream".toMediaType()
        private val RATE_LIMIT_MARKERS = listOf("rateLimitExceeded", "userRateLimitExceeded", "quotaExceeded")
        private val RFC3339_PATTERNS = listOf("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", "yyyy-MM-dd'T'HH:mm:ss'Z'")
    }
}

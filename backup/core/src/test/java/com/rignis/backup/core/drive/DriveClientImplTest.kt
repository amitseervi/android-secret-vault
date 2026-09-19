package com.rignis.backup.core.drive

import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DriveClientImplTest {

    private lateinit var server: MockWebServer
    private lateinit var client: DriveClientImpl

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = DriveClientImpl(
            httpClient = OkHttpClient(),
            filesEndpoint = server.url("/drive/v3/files").toString(),
            uploadFilesEndpoint = server.url("/upload/drive/v3/files").toString()
        )
    }

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun listAppDataFiles_followsPageTokenAcrossPages() = runTest {
        server.enqueue(
            MockResponse.Builder().body(
                JSONObject().put("nextPageToken", "page-2").put(
                    "files", filesJsonArray(fileJson("id-1", "secret-1.bin", mapOf("secretId" to "secret-1")))
                ).toString()
            ).build()
        )
        server.enqueue(
            MockResponse.Builder().body(
                JSONObject().put(
                    "files", filesJsonArray(fileJson("id-2", "secret-2.bin", mapOf("secretId" to "secret-2")))
                ).toString()
            ).build()
        )

        val files = client.listAppDataFiles("token").getOrThrow()

        assertEquals(listOf("id-1", "id-2"), files.map { it.fileId })
        assertEquals(2, server.requestCount)
        val firstRequest = server.takeRequest()
        val secondRequest = server.takeRequest()
        assertFalse(firstRequest.target.contains("pageToken"))
        assertTrue(secondRequest.target.contains("pageToken=page-2"))
    }

    @Test
    fun findFileByName_returnsNullWhenNoFileMatches() = runTest {
        server.enqueue(MockResponse.Builder().body(JSONObject().put("files", filesJsonArray()).toString()).build())

        val result = client.findFileByName("token", "_vault_meta.json").getOrThrow()

        assertNull(result)
    }

    @Test
    fun findFileByName_returnsMatchWithAppProperties() = runTest {
        server.enqueue(
            MockResponse.Builder().body(
                JSONObject().put(
                    "files", filesJsonArray(fileJson("id-1", "_vault_meta.json", mapOf("kind" to "vault_meta")))
                ).toString()
            ).build()
        )

        val result = client.findFileByName("token", "_vault_meta.json").getOrThrow()

        assertEquals("id-1", result?.fileId)
        assertEquals("vault_meta", result?.appProperties?.get("kind"))
    }

    @Test
    fun downloadFile_returnsRawBytes() = runTest {
        server.enqueue(MockResponse.Builder().body("hello-drive").build())

        val bytes = client.downloadFile("token", "id-1").getOrThrow()

        assertEquals("hello-drive", String(bytes))
    }

    @Test
    fun createFile_sendsMultipartRelatedRequestAndParsesResponse() = runTest {
        server.enqueue(MockResponse.Builder().body(fileJson("new-id", "secret-1.bin", emptyMap()).toString()).build())

        val result = client.createFile(
            "token", "secret-1.bin", mapOf("secretId" to "secret-1", "version" to "1"), "cipher-bytes".toByteArray()
        ).getOrThrow()

        assertEquals("new-id", result.fileId)
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertTrue(request.headers["Content-Type"]?.contains("multipart/related") == true)
        val body = request.body?.utf8().orEmpty()
        assertTrue(body.contains("secret-1.bin"))
        assertTrue(body.contains("cipher-bytes"))
        assertTrue(body.contains("secretId"))
    }

    @Test
    fun patchFile_targetsUploadEndpointWithFileId() = runTest {
        server.enqueue(MockResponse.Builder().body(fileJson("id-1", "secret-1.bin", emptyMap()).toString()).build())

        client.patchFile("token", "id-1", mapOf("version" to "2"), "new-bytes".toByteArray()).getOrThrow()

        val request = server.takeRequest()
        assertEquals("PATCH", request.method)
        assertTrue(request.target.contains("/upload/drive/v3/files/id-1"))
    }

    @Test
    fun deleteFile_sendsDeleteAndSucceedsOnNoContent() = runTest {
        server.enqueue(MockResponse.Builder().code(204).build())

        client.deleteFile("token", "id-1").getOrThrow()

        assertEquals("DELETE", server.takeRequest().method)
    }

    @Test
    fun execute_mapsAuthErrorWithoutRetrying() = runTest {
        server.enqueue(MockResponse.Builder().code(401).body("{}").build())

        val error = client.downloadFile("token", "id-1").exceptionOrNull()

        assertTrue(error is DriveException.AuthRequired)
        assertEquals(401, (error as DriveException.AuthRequired).httpCode)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun execute_retriesOnServerErrorThenSucceeds() = runTest {
        server.enqueue(MockResponse.Builder().code(503).body("server exploded").build())
        server.enqueue(MockResponse.Builder().body("recovered-bytes").build())

        val bytes = client.downloadFile("token", "id-1").getOrThrow()

        assertEquals("recovered-bytes", String(bytes))
        assertEquals(2, server.requestCount)
    }

    @Test
    fun execute_givesUpAfterMaxAttemptsOnRepeatedServerError() = runTest {
        repeat(3) { server.enqueue(MockResponse.Builder().code(500).body("still broken").build()) }

        val error = client.downloadFile("token", "id-1").exceptionOrNull()

        assertTrue(error is DriveException.ServerError)
        assertEquals(500, (error as DriveException.ServerError).httpCode)
        assertEquals(3, server.requestCount)
    }

    @Test
    fun execute_mapsMissingFileAsNotFound() = runTest {
        server.enqueue(MockResponse.Builder().code(404).body("{}").build())

        val error = client.downloadFile("token", "missing-id").exceptionOrNull()

        assertTrue(error is DriveException.NotFound)
        assertEquals("missing-id", (error as DriveException.NotFound).fileId)
    }

    private fun fileJson(id: String, name: String, appProperties: Map<String, String>): JSONObject =
        JSONObject().apply {
            put("id", id)
            put("name", name)
            put("modifiedTime", "2024-01-01T00:00:00.000Z")
            if (appProperties.isNotEmpty()) put("appProperties", JSONObject(appProperties))
        }

    private fun filesJsonArray(vararg files: JSONObject) = JSONArray().apply { files.forEach { put(it) } }
}

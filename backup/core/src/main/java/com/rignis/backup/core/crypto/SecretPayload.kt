package com.rignis.backup.core.crypto

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

// The plaintext packed together before backup-encryption: title + body as
// one payload. Locally the title is stored unencrypted (see SecretData),
// but the whole point of a zero-knowledge cloud backup is that Drive never
// sees it either - so it travels inside the same sealed envelope as the
// body, not as Drive appProperties or an unencrypted field.
object SecretPayload {
    data class Unpacked(val title: String, val body: ByteArray)

    fun pack(title: String, body: ByteArray): ByteArray {
        val titleBytes = title.toByteArray(StandardCharsets.UTF_8)
        val buffer = ByteBuffer.allocate(4 + titleBytes.size + body.size)
        buffer.putInt(titleBytes.size)
        buffer.put(titleBytes)
        buffer.put(body)
        return buffer.array()
    }

    fun unpack(payload: ByteArray): Unpacked {
        require(payload.size >= 4) { "payload too short to contain a title length" }
        val buffer = ByteBuffer.wrap(payload)
        val titleLength = buffer.int
        require(titleLength in 0..(payload.size - 4)) { "payload truncated (bad title length)" }
        val titleBytes = ByteArray(titleLength)
        buffer.get(titleBytes)
        val body = ByteArray(buffer.remaining())
        buffer.get(body)
        return Unpacked(String(titleBytes, StandardCharsets.UTF_8), body)
    }
}

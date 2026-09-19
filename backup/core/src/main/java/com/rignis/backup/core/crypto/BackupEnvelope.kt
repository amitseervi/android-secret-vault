package com.rignis.backup.core.crypto

// Wire format for a single backup-encrypted secret's bytes, as they'd be
// written to one Drive file: magic + format version + iv length + iv +
// (AES-GCM ciphertext || 16-byte tag). Packing the IV alongside the
// ciphertext is only needed for the single-blob-per-file upload payload -
// locally, iv and cipherText are kept as separate columns (see
// StagedBackupBlob), so this is unpack/pack only, never how data rests on
// device.
object BackupEnvelope {
    private val MAGIC = byteArrayOf('R'.code.toByte(), 'G'.code.toByte(), 'N'.code.toByte(), 'S'.code.toByte())
    private const val FORMAT_VERSION: Byte = 1

    data class Unpacked(val iv: ByteArray, val cipherTextWithTag: ByteArray)

    fun pack(iv: ByteArray, cipherTextWithTag: ByteArray): ByteArray {
        require(iv.size in 1..255) { "iv length must fit in a single byte, was ${iv.size}" }
        val out = ByteArray(MAGIC.size + 1 + 1 + iv.size + cipherTextWithTag.size)
        var offset = 0
        MAGIC.copyInto(out, offset)
        offset += MAGIC.size
        out[offset++] = FORMAT_VERSION
        out[offset++] = iv.size.toByte()
        iv.copyInto(out, offset)
        offset += iv.size
        cipherTextWithTag.copyInto(out, offset)
        return out
    }

    fun unpack(envelope: ByteArray): Unpacked {
        require(envelope.size >= MAGIC.size + 2) { "envelope too short" }
        for (i in MAGIC.indices) {
            require(envelope[i] == MAGIC[i]) { "bad envelope magic" }
        }
        var offset = MAGIC.size
        val formatVersion = envelope[offset++]
        require(formatVersion == FORMAT_VERSION) { "unsupported envelope format version $formatVersion" }
        val ivLength = envelope[offset++].toInt() and 0xFF
        require(envelope.size >= offset + ivLength) { "envelope truncated (missing iv)" }
        val iv = envelope.copyOfRange(offset, offset + ivLength)
        offset += ivLength
        val cipherTextWithTag = envelope.copyOfRange(offset, envelope.size)
        return Unpacked(iv, cipherTextWithTag)
    }
}

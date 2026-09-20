package com.rignis.backup.core.crypto

import java.security.SecureRandom
import java.util.Random

// A user-facing backup key: XXXX-XXXX-XXXX-XXXX, 16 symbols from a
// 32-character Crockford-style alphabet (no 0/O, 1/I/L, U ambiguity) for
// 80 bits of entropy - random rather than memorable, since it's normally
// retrieved via biometric (see BackupCodeStore) and only ever typed by
// hand when linking a second device.
object BackupCodeGenerator {
    private const val ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"
    private const val SYMBOL_COUNT = 16
    private const val GROUP_SIZE = 4
    private const val RANDOM_BYTE_COUNT = (SYMBOL_COUNT * 5) / 8 // 80 bits exactly

    fun generate(random: Random = SecureRandom()): String {
        val bytes = ByteArray(RANDOM_BYTE_COUNT)
        random.nextBytes(bytes)

        val symbols = StringBuilder(SYMBOL_COUNT)
        var bitBuffer = 0L
        var bitsInBuffer = 0
        var byteIndex = 0
        while (symbols.length < SYMBOL_COUNT) {
            if (bitsInBuffer < 5) {
                bitBuffer = (bitBuffer shl 8) or (bytes[byteIndex++].toLong() and 0xFF)
                bitsInBuffer += 8
            }
            val shift = bitsInBuffer - 5
            val index = ((bitBuffer shr shift) and 0x1F).toInt()
            symbols.append(ALPHABET[index])
            bitsInBuffer -= 5
        }
        return symbols.toString().chunked(GROUP_SIZE).joinToString("-")
    }

    // Accepts what the user actually types: case-insensitive, tolerant of
    // missing/extra hyphens or surrounding whitespace.
    fun normalize(input: String): String =
        input.trim().uppercase().filter { it != '-' }.chunked(GROUP_SIZE).joinToString("-")
}

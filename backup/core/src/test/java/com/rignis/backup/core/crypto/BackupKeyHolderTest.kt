package com.rignis.backup.core.crypto

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.SecureRandom

class BackupKeyHolderTest {

    @Test
    fun initialState_isLockedWithNoKey() {
        val holder = BackupKeyHolder()

        assertFalse(holder.available.value)
        assertNull(holder.keyEpoch)
        assertNull(holder.cipherOrNull())
    }

    @Test
    fun unlock_makesKeyAvailableAndUsable() {
        val holder = BackupKeyHolder()
        val rawKey = ByteArray(32).also { SecureRandom().nextBytes(it) }

        holder.unlock(rawKey, epoch = "epoch-1")

        assertTrue(holder.available.value)
        assertEquals("epoch-1", holder.keyEpoch)
        val cipher = holder.cipherOrNull()
        assertNotNull(cipher)

        // The held key must actually work for seal/open.
        val sealed = cipher!!.seal("secret-1", 1L, "hunter2".toByteArray())
        assertArrayEquals("hunter2".toByteArray(), cipher.open("secret-1", 1L, sealed.iv, sealed.cipherText))
    }

    @Test
    fun lock_wipesKeyAndFlipsAvailableToFalse() {
        val holder = BackupKeyHolder()
        val rawKey = ByteArray(32).also { SecureRandom().nextBytes(it) }
        holder.unlock(rawKey, epoch = "epoch-1")

        holder.lock()

        assertFalse(holder.available.value)
        assertNull(holder.keyEpoch)
        assertNull(holder.cipherOrNull())
    }

    @Test
    fun unlock_overwritesPreviousKeyAndEpoch() {
        val holder = BackupKeyHolder()
        holder.unlock(ByteArray(32) { 1 }, epoch = "epoch-1")

        holder.unlock(ByteArray(32) { 2 }, epoch = "epoch-2")

        assertEquals("epoch-2", holder.keyEpoch)
    }
}

package com.rignis.backup.core.crypto

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Arrays

// Holds the derived backup key in memory only, for the app process's
// lifetime - never serialised, never persisted anywhere. Matches the
// decision that sync is user-triggered/on-app-open only, with no key
// material surviving beyond the session.
class BackupKeyHolder {
    private val _available = MutableStateFlow(false)
    val available: StateFlow<Boolean> get() = _available

    @Volatile
    private var rawKey: ByteArray? = null

    @Volatile
    var keyEpoch: String? = null
        private set

    fun unlock(rawKey: ByteArray, epoch: String) {
        this.rawKey = rawKey
        this.keyEpoch = epoch
        _available.value = true
    }

    fun lock() {
        rawKey?.let { Arrays.fill(it, 0) }
        rawKey = null
        keyEpoch = null
        _available.value = false
    }

    fun cipherOrNull(): BackupCipher? {
        val key = rawKey ?: return null
        return BackupCipher(BackupCipher.keyFrom(key))
    }
}

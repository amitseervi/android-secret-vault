package com.rignis.backup.api

import com.rignis.store.api.StagedBackupBlob
import kotlinx.coroutines.flow.StateFlow

// The push-side hook ViewModels call right where they already hold
// plaintext in memory, so the sync pass itself never needs to re-decrypt
// the Keystore-protected body.
interface BackupStager {
    val isKeyAvailable: StateFlow<Boolean>

    // Encrypts [plaintextBody] under the in-memory Argon2-derived key; null
    // when the key is locked (the item is simply left NOT_STAGED).
    suspend fun stageFor(
        secretId: String, version: Long, title: String, plaintextBody: ByteArray
    ): StagedBackupBlob?

    // Opportunistic re-stage when plaintext is already in hand (e.g. right
    // after the user's own unlock/copy biometric prompt) - zero extra prompts.
    suspend fun restageOpportunistically(secretId: String, title: String, plaintextBody: ByteArray)

    // Tombstones carry no plaintext, so these can always be re-staged with
    // zero prompts - this is what makes deletions always propagate.
    suspend fun stageAllPendingTombstones()
}

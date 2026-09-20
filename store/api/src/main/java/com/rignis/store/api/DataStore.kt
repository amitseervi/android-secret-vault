package com.rignis.store.api

import kotlinx.coroutines.flow.Flow

// Returned by insert/update so a caller that just wrote plaintext (e.g.
// DetailViewModel, right before it's discarded) can hand id+version to
// BackupStager.stageFor without a redundant read-back.
data class SavedSecretRef(val id: String, val version: Long)

interface DataStore {
    // Null when the secret doesn't exist or has been (soft-)deleted.
    suspend fun getDataById(id: String): EncryptedDataItem?
    suspend fun getAllData(): Flow<List<EncryptedDataRef>>
    suspend fun insertItem(entry: EncryptedDataEntry): SavedSecretRef

    suspend fun updateExisting(id: String, entry: EncryptedDataEntry): SavedSecretRef

    suspend fun deleteDataById(id: String)
}
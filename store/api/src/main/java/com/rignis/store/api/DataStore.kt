package com.rignis.store.api

import kotlinx.coroutines.flow.Flow

interface DataStore {
    // Null when the secret doesn't exist or has been (soft-)deleted.
    suspend fun getDataById(id: String): EncryptedDataItem?
    suspend fun getAllData(): Flow<List<EncryptedDataRef>>
    suspend fun insertItem(entry: EncryptedDataEntry)

    suspend fun updateExisting(id: String, entry: EncryptedDataEntry)

    suspend fun deleteDataById(id: String)
}
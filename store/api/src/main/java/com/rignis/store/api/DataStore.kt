package com.rignis.store.api

import kotlinx.coroutines.flow.Flow

interface DataStore {
    val syncStatus: Flow<SyncStatus>
    suspend fun getDataById(id: String): EncryptedDataItem
    suspend fun getAllData(): Flow<List<EncryptedDataRef>>
    suspend fun insertItem(entry: EncryptedDataEntry)

    suspend fun updateExisting(id: String, entry: EncryptedDataEntry)

    suspend fun deleteDataById(id: String)
    suspend fun uploadDataToCloud(conflictResolver: ConflictResolver)
    suspend fun syncFromCloud(conflictResolver: ConflictResolver)
}
package com.rignis.store.core

import com.rignis.common.ExecutorFactory
import com.rignis.store.api.ConflictResolver
import com.rignis.store.api.DataStore
import com.rignis.store.api.EncryptedDataEntry
import com.rignis.store.api.EncryptedDataItem
import com.rignis.store.api.EncryptedDataRef
import com.rignis.store.api.SyncStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.UUID

class DataStoreImpl(
    private val dataStoreFactory: DataStoreFactory, private val executorFactory: ExecutorFactory
) : DataStore {
    private val _syncStatus: MutableStateFlow<SyncStatus> = MutableStateFlow(SyncStatus.Idle(0L))
    override val syncStatus: Flow<SyncStatus>
        get() = _syncStatus

    override suspend fun getDataById(id: String): EncryptedDataItem =
        withContext(executorFactory.backgroundDispatcher) {
            return@withContext EntityMapper.toEncryptedDataItem(
                dataStoreFactory.db.secretStoreDao().getSecretById(id)
            )
        }

    override suspend fun getAllData(): Flow<List<EncryptedDataRef>> {
        return dataStoreFactory.db.secretStoreDao().getAllSecretData()
            .map { list -> list.map { item -> EntityMapper.toEncryptedDataRef(item) } }
    }

    override suspend fun insertItem(entry: EncryptedDataEntry) =
        withContext(executorFactory.backgroundDispatcher) {
            val id = UUID.randomUUID().toString()
            dataStoreFactory.db.secretStoreDao().insertItem(EntityMapper.toEntity(id, entry))
        }

    override suspend fun updateExisting(
        id: String, entry: EncryptedDataEntry
    ) = withContext(executorFactory.backgroundDispatcher) {
        dataStoreFactory.db.secretStoreDao().insertItem(EntityMapper.toEntity(id, entry))
    }

    override suspend fun deleteDataById(id: String) =
        withContext(executorFactory.backgroundDispatcher) {
            dataStoreFactory.db.secretStoreDao().deleteItem(id)
        }

    override suspend fun uploadDataToCloud(conflictResolver: ConflictResolver) {
        TODO("Not yet implemented")
    }

    override suspend fun syncFromCloud(conflictResolver: ConflictResolver) {
        TODO("Not yet implemented")
    }
}
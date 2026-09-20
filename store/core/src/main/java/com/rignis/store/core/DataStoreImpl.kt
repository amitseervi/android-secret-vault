package com.rignis.store.core

import com.rignis.common.ExecutorFactory
import com.rignis.store.api.DataStore
import com.rignis.store.api.EncryptedDataEntry
import com.rignis.store.api.EncryptedDataItem
import com.rignis.store.api.EncryptedDataRef
import com.rignis.store.api.SavedSecretRef
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.UUID

class DataStoreImpl(
    private val dataStoreFactory: DataStoreFactory, private val executorFactory: ExecutorFactory
) : DataStore {

    override suspend fun getDataById(id: String): EncryptedDataItem? =
        withContext(executorFactory.backgroundDispatcher) {
            dataStoreFactory.db.secretStoreDao().getSecretById(id)
                ?.let { EntityMapper.toEncryptedDataItem(it) }
        }

    override suspend fun getAllData(): Flow<List<EncryptedDataRef>> {
        return dataStoreFactory.db.secretStoreDao().getAllSecretData()
            .map { list -> list.map { item -> EntityMapper.toEncryptedDataRef(item) } }
    }

    override suspend fun insertItem(entry: EncryptedDataEntry): SavedSecretRef =
        withContext(executorFactory.backgroundDispatcher) {
            val id = UUID.randomUUID().toString()
            val now = System.currentTimeMillis()
            dataStoreFactory.db.secretStoreDao().insertItem(
                EntityMapper.toEntity(id, entry, version = 1L, updatedAt = now, lastSyncedVersion = null)
            )
            SavedSecretRef(id, 1L)
        }

    override suspend fun updateExisting(
        id: String, entry: EncryptedDataEntry
    ): SavedSecretRef = withContext(executorFactory.backgroundDispatcher) {
        val dao = dataStoreFactory.db.secretStoreDao()
        val existing = dao.getRawById(id)
        val nextVersion = (existing?.version ?: 0L) + 1L
        dao.insertItem(
            EntityMapper.toEntity(
                id, entry,
                version = nextVersion,
                updatedAt = System.currentTimeMillis(),
                lastSyncedVersion = existing?.lastSyncedVersion
            )
        )
        SavedSecretRef(id, nextVersion)
    }

    override suspend fun deleteDataById(id: String) =
        withContext(executorFactory.backgroundDispatcher) {
            dataStoreFactory.db.secretStoreDao().softDelete(id, System.currentTimeMillis())
        }
}

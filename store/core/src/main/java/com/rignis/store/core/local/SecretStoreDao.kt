package com.rignis.store.core.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SecretStoreDao {
    @Query("SELECT id,title FROM secret_data")
    fun getAllSecretData(): Flow<List<SecretDataRef>>

    @Query("SELECT * FROM secret_data WHERE id=:id")
    suspend fun getSecretById(id: String): SecretData

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(data: SecretData)

    @Query("DELETE FROM secret_data WHERE id=:id")
    suspend fun deleteItem(id: String)
}
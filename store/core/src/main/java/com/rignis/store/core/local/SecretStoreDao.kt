package com.rignis.store.core.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SecretStoreDao {
    @Query("SELECT id,title FROM secret_data WHERE deleted_at IS NULL ORDER BY title COLLATE NOCASE")
    fun getAllSecretData(): Flow<List<SecretDataRef>>

    @Query("SELECT * FROM secret_data WHERE id=:id AND deleted_at IS NULL")
    suspend fun getSecretById(id: String): SecretData?

    // Ignores the deleted_at filter - used internally to preserve version /
    // lastSyncedVersion bookkeeping when writing an update, not for display.
    @Query("SELECT * FROM secret_data WHERE id=:id")
    suspend fun getRawById(id: String): SecretData?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(data: SecretData)

    // Soft delete: keeps a tombstone (deleted_at set, version bumped) so a
    // deletion can propagate to the cloud instead of the secret silently
    // reappearing on the next pull from an out-of-date remote copy.
    @Query("UPDATE secret_data SET deleted_at=:at, updated_at=:at, version=version+1 WHERE id=:id")
    suspend fun softDelete(id: String, at: Long)

    @Query(
        "DELETE FROM secret_data WHERE deleted_at IS NOT NULL AND deleted_at < :cutoff " +
                "AND last_synced_version IS NOT NULL AND last_synced_version = version"
    )
    suspend fun purgeConfirmedTombstones(cutoff: Long)
}

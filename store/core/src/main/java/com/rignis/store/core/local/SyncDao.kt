package com.rignis.store.core.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncDao {

    @Query(
        """SELECT id, title, version,
           updated_at AS updatedAt, deleted_at AS deletedAt, last_synced_version AS lastSyncedVersion
           FROM secret_data"""
    )
    suspend fun allSyncRefs(): List<SecretSyncRefRow>

    @Query(
        """SELECT COUNT(*) FROM secret_data s
           LEFT JOIN secret_backup_blob b ON b.secret_id = s.id AND b.version = s.version
           WHERE b.secret_id IS NULL OR s.last_synced_version IS NULL OR s.last_synced_version != s.version"""
    )
    fun observeNotBackedUpCount(): Flow<Int>

    @Query(
        """SELECT id FROM secret_data s WHERE s.deleted_at IS NULL AND NOT EXISTS
           (SELECT 1 FROM secret_backup_blob b WHERE b.secret_id = s.id AND b.version = s.version)"""
    )
    suspend fun idsNeedingStaging(): List<String>

    @Query("SELECT * FROM secret_backup_blob WHERE secret_id = :secretId")
    suspend fun stagedBlob(secretId: String): SecretBackupBlob?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun stageBlob(blob: SecretBackupBlob)

    @Query("DELETE FROM secret_backup_blob")
    suspend fun clearAllStagedBlobs()

    @Query("DELETE FROM secret_backup_blob WHERE key_epoch != :epoch")
    suspend fun clearStaleEpochBlobs(epoch: String)

    @Query("SELECT * FROM secret_remote_staged WHERE secret_id = :secretId")
    suspend fun parkedRemote(secretId: String): SecretRemoteStaged?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun parkRemote(parked: SecretRemoteStaged)

    @Query("DELETE FROM secret_remote_staged WHERE secret_id = :secretId")
    suspend fun clearParkedRemote(secretId: String)

    @Query("SELECT * FROM sync_item_state WHERE status IN ('CONFLICT', 'FAILED', 'NOT_STAGED')")
    fun observeIssues(): Flow<List<SyncItemState>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertItemState(state: SyncItemState)

    @Query("DELETE FROM sync_item_state WHERE secret_id = :secretId")
    suspend fun clearItemState(secretId: String)

    @Delete
    suspend fun deleteBlob(blob: SecretBackupBlob)
}

package com.rignis.store.core.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [SecretData::class, UserSetting::class, SecretBackupBlob::class,
        SecretRemoteStaged::class, SyncItemState::class], version = 3
)
abstract class SecretRoomDatabase : RoomDatabase() {
    abstract fun secretStoreDao(): SecretStoreDao

    abstract fun userSettingDao(): UserSettingDao

    abstract fun syncDao(): SyncDao
}

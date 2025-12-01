package com.rignis.store.core.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [SecretData::class, UserSetting::class], version = 2
)
abstract class SecretRoomDatabase : RoomDatabase() {
    abstract fun secretStoreDao(): SecretStoreDao

    abstract fun userSettingDao(): UserSettingDao
}
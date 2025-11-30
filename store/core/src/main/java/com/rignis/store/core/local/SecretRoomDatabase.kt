package com.rignis.store.core.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [SecretData::class], version = 1)
abstract class SecretRoomDatabase : RoomDatabase() {
    abstract fun secretStoreDao(): SecretStoreDao
}
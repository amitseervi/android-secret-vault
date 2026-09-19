package com.rignis.store.core

import android.content.Context
import androidx.room.Room
import com.rignis.store.core.local.Migrations
import com.rignis.store.core.local.SecretRoomDatabase

class DataStoreFactory(context: Context) {
    internal val db =
        Room.databaseBuilder(context, SecretRoomDatabase::class.java, "rignis_db")
            .addMigrations(Migrations.MIGRATION_2_3)
            .build()
}
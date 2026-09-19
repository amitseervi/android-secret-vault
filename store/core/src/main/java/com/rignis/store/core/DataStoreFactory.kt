package com.rignis.store.core

import android.content.Context
import androidx.room.Room
import com.rignis.store.core.local.Migrations
import com.rignis.store.core.local.SecretRoomDatabase

class DataStoreFactory private constructor(internal val db: SecretRoomDatabase) {

    constructor(context: Context) : this(
        Room.databaseBuilder(context, SecretRoomDatabase::class.java, "rignis_db")
            .addMigrations(Migrations.MIGRATION_2_3)
            .build()
    )

    companion object {
        // Test-only: build against a caller-provided database (e.g. an
        // in-memory Room instance) instead of the real on-disk file, so
        // instrumented tests don't touch real app data or need migrations.
        fun forTesting(db: SecretRoomDatabase): DataStoreFactory = DataStoreFactory(db)
    }
}

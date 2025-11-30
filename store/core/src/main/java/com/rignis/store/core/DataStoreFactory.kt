package com.rignis.store.core

import android.content.Context
import androidx.room.Room
import com.rignis.store.core.local.SecretRoomDatabase

class DataStoreFactory(context: Context) {
    internal val db =
        Room.databaseBuilder(context, SecretRoomDatabase::class.java, "rignis_db").build()
}
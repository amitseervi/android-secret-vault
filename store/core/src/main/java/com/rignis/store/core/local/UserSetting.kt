package com.rignis.store.core.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_setting")
data class UserSetting(
    @PrimaryKey @ColumnInfo("key") val key: String, @ColumnInfo("value") val value: String
)
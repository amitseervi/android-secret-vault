package com.rignis.store.core.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface UserSettingDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(userSettings: UserSetting)

    @Query("SELECT * FROM user_setting WHERE `key` = :key LIMIT 1")
    fun getUserSetting(key: String): Flow<UserSetting?>
}
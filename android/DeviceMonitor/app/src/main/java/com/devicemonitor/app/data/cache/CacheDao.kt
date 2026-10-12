package com.devicemonitor.app.data.cache

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface CacheDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(cache: CacheEntity)

    @Query("SELECT * FROM cache WHERE `key` = :key")
    suspend fun getByKey(key: String): CacheEntity?

    @Query("DELETE FROM cache WHERE `key` = :key")
    suspend fun deleteByKey(key: String)

    @Query("DELETE FROM cache")
    suspend fun clearAll()
}

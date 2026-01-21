package com.kandc.acscore.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ScoreDao {

    @Query("SELECT * FROM scores ORDER BY createdAt DESC")
    suspend fun getAll(): List<ScoreEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: ScoreEntity)
}
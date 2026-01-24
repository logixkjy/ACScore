package com.kandc.acscore.data.setlist.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SetlistDao {

    @Query("SELECT * FROM setlists ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<SetlistEntity>>

    @Query("SELECT * FROM setlists ORDER BY createdAt DESC")
    suspend fun getAllOnce(): List<SetlistEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: SetlistEntity)

    @Query("DELETE FROM setlists WHERE id = :id")
    suspend fun deleteById(id: String)
}
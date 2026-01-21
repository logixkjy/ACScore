package com.kandc.acscore.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ScoreDao {

    @Query("SELECT * FROM scores ORDER BY createdAt DESC")
    suspend fun getAll(): List<ScoreEntity>

    // ✅ title LIKE 검색 (내림차순 유지)
    @Query(
        """
        SELECT * FROM scores
        WHERE title LIKE '%' || :query || '%' ESCAPE '\'
        ORDER BY createdAt DESC
        """
    )
    suspend fun searchByTitle(query: String): List<ScoreEntity>

    @Query(
        """
    SELECT * FROM scores
    WHERE title LIKE '%' || :titleQuery || '%' ESCAPE '\'
       OR chosung LIKE '%' || :chosungQueryWithSpace || '%'
       OR REPLACE(chosung, ' ', '') LIKE '%' || :chosungQueryNoSpace || '%'
    ORDER BY createdAt DESC
    """
    )
    suspend fun searchCombinedNormalized(
        titleQuery: String,
        chosungQueryWithSpace: String,
        chosungQueryNoSpace: String
    ): List<ScoreEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: ScoreEntity)
}
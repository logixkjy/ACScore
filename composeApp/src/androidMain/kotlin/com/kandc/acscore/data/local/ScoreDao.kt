package com.kandc.acscore.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

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

    @Query("SELECT * FROM scores WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): ScoreEntity?

    @Query("SELECT * FROM scores WHERE contentHash = :hash LIMIT 1")
    suspend fun findByContentHash(hash: String): ScoreEntity?

    @Query("DELETE FROM scores WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("UPDATE scores SET title = :title, chosung = :chosung WHERE id = :id")
    suspend fun updateTitle(id: String, title: String, chosung: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ScoreEntity)

    @Update
    suspend fun update(entity: ScoreEntity)
}
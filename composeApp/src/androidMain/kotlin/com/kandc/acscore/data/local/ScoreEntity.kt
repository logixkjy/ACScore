package com.kandc.acscore.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "scores")
data class ScoreEntity(
    @PrimaryKey val id: String,
    val title: String,
    val fileName: String,
    val createdAt: Long
)
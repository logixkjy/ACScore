package com.kandc.acscore.data.setlist.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "setlists")
data class SetlistEntity(
    @PrimaryKey val id: String,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    /**
     * 곡 목록(ScoreId 리스트)을 JSON 문자열로 저장
     * - T5-1에서는 비어있음
     * - T5-2부터 add/remove/reorder로 값이 채워짐
     */
    val itemIdsJson: String,
)
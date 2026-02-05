package com.kandc.acscore.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "scores",
    indices = [
        Index(value = ["contentHash"]) // 중복 조회 속도용 (유니크는 정책상 DAO에서 처리)
    ]
)
data class ScoreEntity(
    @PrimaryKey val id: String,
    val title: String,
    val fileName: String,
    val chosung: String,
    val createdAt: Long,

    /**
     * PDF 내용 기반 해시(sha256).
     * - null: 아직 계산/백필 안된 레거시 데이터
     * - import 중복 판정은 이 값으로 처리
     */
    val contentHash: String? = null
)
package com.kandc.acscore.shared.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class Setlist(
    val id: String,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    val itemIds: List<String>, // ScoreId 리스트(문자열) - T5-2부터 사용
)
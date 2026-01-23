package com.kandc.acscore.data.model

import kotlinx.serialization.Serializable

@Serializable
data class Score(
    val id: String,
    val title: String,
    val fileName: String,
    val createdAt: Long
)
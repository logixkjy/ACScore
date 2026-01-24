package com.kandc.acscore.shared.domain.repository

import com.kandc.acscore.shared.domain.model.Setlist
import kotlinx.coroutines.flow.Flow

interface SetlistRepository {
    fun observeAll(): Flow<List<Setlist>>
    suspend fun getAll(): List<Setlist>
    suspend fun create(name: String): Setlist
    suspend fun delete(id: String)
}
package com.kandc.acscore.shared.domain.repository

import com.kandc.acscore.shared.domain.model.Setlist
import kotlinx.coroutines.flow.Flow

interface SetlistRepository {
    fun observeAll(): Flow<List<Setlist>>
    fun observeById(id: String): Flow<Setlist?>

    suspend fun getAll(): List<Setlist>
    suspend fun getById(id: String): Setlist?

    suspend fun create(name: String): Setlist
    suspend fun rename(id: String, newName: String)

    /** T5-2에서 핵심: 세트리스트의 itemIds만 업데이트 */
    suspend fun updateItems(id: String, itemIds: List<String>)

    suspend fun delete(id: String)
}
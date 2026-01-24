package com.kandc.acscore.data.setlist

import com.kandc.acscore.data.setlist.db.SetlistDao
import com.kandc.acscore.data.setlist.db.SetlistEntity
import com.kandc.acscore.shared.domain.model.Setlist
import com.kandc.acscore.shared.domain.repository.SetlistRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

class SetlistRoomRepository(
    private val dao: SetlistDao,
    private val json: Json = Json { ignoreUnknownKeys = true }
) : SetlistRepository {

    override fun observeAll(): Flow<List<Setlist>> =
        dao.observeAll().map { list -> list.map { it.toDomain(json) } }

    override suspend fun getAll(): List<Setlist> =
        dao.getAllOnce().map { it.toDomain(json) }

    override suspend fun create(name: String): Setlist {
        val now = System.currentTimeMillis()
        val model = Setlist(
            id = UUID.randomUUID().toString(),
            name = name,
            createdAt = now,
            updatedAt = now,
            itemIds = emptyList(),
        )
        dao.upsert(model.toEntity(json))
        return model
    }

    override suspend fun delete(id: String) {
        dao.deleteById(id)
    }

    private fun SetlistEntity.toDomain(json: Json): Setlist {
        val itemIds = runCatching {
            json.decodeFromString<List<String>>(itemIdsJson)
        }.getOrElse { emptyList() } // 깨진 데이터여도 크래시 방지
        return Setlist(
            id = id,
            name = name,
            createdAt = createdAt,
            updatedAt = updatedAt,
            itemIds = itemIds
        )
    }

    private fun Setlist.toEntity(json: Json): SetlistEntity =
        SetlistEntity(
            id = id,
            name = name,
            createdAt = createdAt,
            updatedAt = updatedAt,
            itemIdsJson = runCatching { json.encodeToString(itemIds) }.getOrElse { "[]" }
        )
}
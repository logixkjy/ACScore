package com.kandc.acscore.shard.domain.usecase

import com.kandc.acscore.shard.domain.repository.ScoreRepository

class RenameScoreTitleUseCase(
    private val repository: ScoreRepository
) {
    suspend operator fun invoke(id: String, newTitle: String): Result<Unit> {
        val title = newTitle.trim()
        if (id.isBlank() || title.isEmpty()) {
            return Result.failure(IllegalArgumentException("invalid title"))
        }
        return repository.renameScoreTitle(id, title)
    }
}
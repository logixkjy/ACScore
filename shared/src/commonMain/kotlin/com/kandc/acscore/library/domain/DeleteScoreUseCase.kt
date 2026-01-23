package com.kandc.acscore.library.domain

import com.kandc.acscore.data.repository.ScoreRepository

class DeleteScoreUseCase(
    private val repository: ScoreRepository
) {
    suspend operator fun invoke(id: String): Result<Unit> {
        if (id.isBlank()) return Result.failure(IllegalArgumentException("invalid id"))
        return repository.deleteScore(id)
    }
}
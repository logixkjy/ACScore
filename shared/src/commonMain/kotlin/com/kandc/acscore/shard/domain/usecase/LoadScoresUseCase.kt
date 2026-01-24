package com.kandc.acscore.shard.domain.usecase

import com.kandc.acscore.shard.domain.repository.ScoreRepository

class LoadScoresUseCase(
    private val repository: ScoreRepository
) {
    suspend operator fun invoke() = repository.loadScores()
}
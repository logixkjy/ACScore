package com.kandc.acscore.shard.domain.usecase

import com.kandc.acscore.shard.domain.repository.ScoreRepository

class SearchScoresUseCase(
    private val repository: ScoreRepository
) {
    suspend operator fun invoke(query: String) = repository.searchScores(query)
}
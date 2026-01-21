package com.kandc.acscore.domain

import com.kandc.acscore.data.repository.ScoreRepository

class SearchScoresUseCase(
    private val repository: ScoreRepository
) {
    suspend operator fun invoke(query: String) = repository.searchScores(query)
}
package com.kandc.acscore.domain

import com.kandc.acscore.data.repository.ScoreRepository

class LoadScoresUseCase(
    private val repository: ScoreRepository
) {
    suspend operator fun invoke() = repository.loadScores()
}
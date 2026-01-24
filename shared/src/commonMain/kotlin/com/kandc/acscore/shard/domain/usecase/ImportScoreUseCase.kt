package com.kandc.acscore.shard.domain.usecase

import com.kandc.acscore.shard.domain.repository.ScoreRepository

class ImportScoreUseCase(
    private val repository: ScoreRepository
) {
    suspend operator fun invoke(uriString: String) = repository.importPdf(uriString)
}
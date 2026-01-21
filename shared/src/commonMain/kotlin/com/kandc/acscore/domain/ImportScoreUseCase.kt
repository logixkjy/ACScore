package com.kandc.acscore.domain

import com.kandc.acscore.data.repository.ScoreRepository

class ImportScoreUseCase(
    private val repository: ScoreRepository
) {
    suspend operator fun invoke(uriString: String) = repository.importPdf(uriString)
}
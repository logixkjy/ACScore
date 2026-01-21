package com.kandc.acscore.data.repository

import com.kandc.acscore.data.model.Score

interface ScoreRepository {

    suspend fun loadScores(): List<Score>

    /**
     * Android: Uri.toString() 전달
     * shared: Uri/Context 모름
     */
    suspend fun importPdf(sourceUriString: String): Result<Score>
}
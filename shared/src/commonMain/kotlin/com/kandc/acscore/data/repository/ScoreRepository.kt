package com.kandc.acscore.data.repository

import com.kandc.acscore.data.model.Score

interface ScoreRepository {

    suspend fun loadScores(): List<Score>

    /**
     * Android: Uri.toString() 전달
     * shared: Uri/Context 모름
     */
    suspend fun importPdf(sourceUriString: String): Result<Score>

    /** 제목(title) 기준 검색 (빈 문자열이면 전체) */
    suspend fun searchScores(query: String): List<Score>
}
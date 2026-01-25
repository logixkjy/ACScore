package com.kandc.acscore.shared.domain.usecase

import com.kandc.acscore.shared.domain.repository.SetlistRepository

class AddScoreToSetlistUseCase(
    private val repo: SetlistRepository
) {
    suspend operator fun invoke(setlistId: String, scoreId: String) {
        val current = repo.getById(setlistId) ?: return
        if (current.itemIds.contains(scoreId)) return // 중복 방지
        repo.updateItems(setlistId, current.itemIds + scoreId)
    }
}
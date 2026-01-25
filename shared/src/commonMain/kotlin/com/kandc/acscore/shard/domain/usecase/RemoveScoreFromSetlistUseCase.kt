package com.kandc.acscore.shared.domain.usecase

import com.kandc.acscore.shared.domain.repository.SetlistRepository

class RemoveScoreFromSetlistUseCase(
    private val repo: SetlistRepository
) {
    suspend operator fun invoke(setlistId: String, scoreId: String) {
        val current = repo.getById(setlistId) ?: return
        repo.updateItems(setlistId, current.itemIds.filterNot { it == scoreId })
    }
}
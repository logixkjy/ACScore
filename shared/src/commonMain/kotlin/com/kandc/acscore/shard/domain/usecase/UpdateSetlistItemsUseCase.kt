package com.kandc.acscore.shared.domain.usecase

import com.kandc.acscore.shared.domain.repository.SetlistRepository

class UpdateSetlistItemsUseCase(
    private val repo: SetlistRepository
) {
    suspend operator fun invoke(setlistId: String, itemIds: List<String>) {
        require(setlistId.isNotBlank())
        repo.updateItems(setlistId, itemIds)
    }
}
package com.kandc.acscore.shared.domain.usecase

import com.kandc.acscore.shared.domain.repository.SetlistRepository

class DeleteSetlistUseCase(
    private val repo: SetlistRepository
) {
    suspend operator fun invoke(id: String) {
        require(id.isNotBlank())
        repo.delete(id)
    }
}
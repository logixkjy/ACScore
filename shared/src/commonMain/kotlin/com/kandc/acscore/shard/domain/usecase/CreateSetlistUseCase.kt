package com.kandc.acscore.shared.domain.usecase

import com.kandc.acscore.shared.domain.model.Setlist
import com.kandc.acscore.shared.domain.repository.SetlistRepository

class CreateSetlistUseCase(
    private val repo: SetlistRepository
) {
    suspend operator fun invoke(name: String): Setlist {
        val trimmed = name.trim()
        require(trimmed.isNotBlank()) { "Setlist name is blank" }
        return repo.create(trimmed)
    }
}
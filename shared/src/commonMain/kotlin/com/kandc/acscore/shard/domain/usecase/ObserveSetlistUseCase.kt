package com.kandc.acscore.shared.domain.usecase

import com.kandc.acscore.shared.domain.model.Setlist
import com.kandc.acscore.shared.domain.repository.SetlistRepository
import kotlinx.coroutines.flow.Flow

class ObserveSetlistUseCase(
    private val repo: SetlistRepository
) {
    operator fun invoke(id: String): Flow<Setlist?> = repo.observeById(id)
}
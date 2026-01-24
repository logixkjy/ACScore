package com.kandc.acscore.shared.domain.usecase

import com.kandc.acscore.shared.domain.model.Setlist
import com.kandc.acscore.shared.domain.repository.SetlistRepository
import kotlinx.coroutines.flow.Flow

class ObserveSetlistsUseCase(
    private val repo: SetlistRepository
) {
    operator fun invoke(): Flow<List<Setlist>> = repo.observeAll()
}
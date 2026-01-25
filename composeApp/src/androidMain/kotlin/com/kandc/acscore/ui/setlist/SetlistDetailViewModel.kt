package com.kandc.acscore.ui.setlist

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kandc.acscore.di.SetlistDi
import com.kandc.acscore.shared.domain.model.Setlist
import com.kandc.acscore.shared.domain.usecase.AddScoreToSetlistUseCase
import com.kandc.acscore.shared.domain.usecase.ObserveSetlistUseCase
import com.kandc.acscore.shared.domain.usecase.RemoveScoreFromSetlistUseCase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SetlistDetailViewModel(
    context: Context,
    private val setlistId: String
) : ViewModel() {

    private val repo = SetlistDi.provideRepository(context)
    private val observeSetlist = ObserveSetlistUseCase(repo)
    private val addScore = AddScoreToSetlistUseCase(repo)
    private val removeScore = RemoveScoreFromSetlistUseCase(repo)

    val setlist: StateFlow<Setlist?> =
        observeSetlist(setlistId)
            .catch { emit(null) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun add(scoreId: String, onError: (String) -> Unit = {}) {
        viewModelScope.launch {
            runCatching { addScore(setlistId, scoreId) }
                .onFailure { onError(it.message ?: "추가 실패") }
        }
    }

    fun remove(scoreId: String, onError: (String) -> Unit = {}) {
        viewModelScope.launch {
            runCatching { removeScore(setlistId, scoreId) }
                .onFailure { onError(it.message ?: "삭제 실패") }
        }
    }
}
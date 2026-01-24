package com.kandc.acscore.ui.setlist

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kandc.acscore.di.SetlistDi
import com.kandc.acscore.shared.domain.model.Setlist
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SetlistListViewModel(
    context: Context
) : ViewModel() {

    private val repo = SetlistDi.provideRepository(context)
    private val useCases = SetlistDi.provideUseCases(repo)

    val setlists: StateFlow<List<Setlist>> =
        useCases.observeSetlists()
            .catch { emit(emptyList()) } // DB 이슈여도 크래시 방지
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun createSetlist(name: String, onError: (String) -> Unit = {}) {
        viewModelScope.launch {
            runCatching {
                useCases.createSetlist(name)
            }.onFailure {
                onError(it.message ?: "생성에 실패했어요.")
            }
        }
    }

    fun deleteSetlist(id: String, onError: (String) -> Unit = {}) {
        viewModelScope.launch {
            runCatching {
                useCases.deleteSetlist(id)
            }.onFailure {
                onError(it.message ?: "삭제에 실패했어요.")
            }
        }
    }
}
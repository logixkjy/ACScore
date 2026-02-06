package com.kandc.acscore.ui.setlist

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kandc.acscore.di.SetlistDi
import com.kandc.acscore.shared.domain.model.Setlist
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SetlistListViewModel(
    context: Context
) : ViewModel() {

    private val repo = SetlistDi.provideRepository(context)
    private val useCases = SetlistDi.provideUseCases(repo)

    // ✅ 여기서 Flow 타입을 강제로 고정 (Any?로 무너지는 거 방지)
    private val setlistsFlow: Flow<List<Setlist>> = useCases.observeSetlists()

    val setlists: StateFlow<List<Setlist>> =
        setlistsFlow
            .map { list ->
                list.sortedBy { it.name.lowercase() } // ✅ 이름순 정렬 (대소문자 무시)
            }
            .catch { emit(emptyList()) }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                emptyList()
            )

    fun createSetlist(name: String, onError: (String) -> Unit = {}) {
        viewModelScope.launch {
            runCatching { useCases.createSetlist(name) }
                .onFailure { onError(it.message ?: "생성에 실패했어요.") }
        }
    }

    fun deleteSetlist(id: String, onError: (String) -> Unit = {}) {
        viewModelScope.launch {
            runCatching { useCases.deleteSetlist(id) }
                .onFailure { onError(it.message ?: "삭제에 실패했어요.") }
        }
    }

    fun createSetlistForImport(
        name: String,
        itemIds: List<String>,
        onSuccess: (setlistId: String) -> Unit,
        onError: (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            runCatching {
                // 1) 세트리스트 생성 (Setlist 반환)
                val safeName = name.trim().ifBlank { "Setlist" }
                val created = useCases.createSetlist(safeName)
                val setlistId = created.id

                // 2) 아이템 저장 (순서 유지)
                useCases.updateItems(setlistId, itemIds)

                setlistId
            }.onSuccess { id ->
                onSuccess(id)
            }.onFailure {
                onError(it.message ?: "세트리스트 생성에 실패했어요.")
            }
        }
    }
}
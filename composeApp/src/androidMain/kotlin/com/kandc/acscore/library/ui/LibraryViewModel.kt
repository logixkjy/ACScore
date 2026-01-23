package com.kandc.acscore.library.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kandc.acscore.data.model.Score
import com.kandc.acscore.domain.ImportScoreUseCase
import com.kandc.acscore.domain.LoadScoresUseCase
import com.kandc.acscore.domain.SearchScoresUseCase
import com.kandc.acscore.library.domain.DeleteScoreUseCase
import com.kandc.acscore.library.domain.RenameScoreTitleUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.Collator
import java.util.Locale

class LibraryViewModel(
    private val loadScores: LoadScoresUseCase,
    private val importScore: ImportScoreUseCase,
    private val searchScores: SearchScoresUseCase,
    private val deleteScoreUseCase: DeleteScoreUseCase,                 // ✅ 추가
    private val renameScoreTitleUseCase: RenameScoreTitleUseCase        // ✅ 추가
) : ViewModel() {

    private val _scores = MutableStateFlow<List<Score>>(emptyList())
    val scores: StateFlow<List<Score>> = _scores

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _isImporting = MutableStateFlow(false)
    val isImporting: StateFlow<Boolean> = _isImporting.asStateFlow()

    private var searchJob: Job? = null
    private val debounceMs = 300L

    private val koCollator: Collator = Collator.getInstance(Locale.KOREAN).apply {
        strength = Collator.TERTIARY
    }

    private fun sortByTitle(list: List<Score>): List<Score> =
        list.sortedWith { a, b -> koCollator.compare(a.title, b.title) }

    fun refresh() {
        viewModelScope.launch {
            val q = _query.value.trim()
            _scores.value = sortByTitle(if (q.isEmpty()) loadScores() else searchScores(q))
        }
    }

    fun setQuery(value: String) {
        _query.value = value
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(debounceMs)
            val q = _query.value.trim()
            _scores.value = sortByTitle(if (q.isEmpty()) loadScores() else searchScores(q))
        }
    }

    fun clearQuery() = setQuery("")

    fun importUris(uriStrings: List<String>) {
        val list = uriStrings.filter { it.isNotBlank() }
        if (list.isEmpty()) return

        viewModelScope.launch {
            if (_isImporting.value) return@launch
            _isImporting.value = true
            try {
                val result = withContext(Dispatchers.IO) {
                    for (uri in list) {
                        val r = importScore(uri)
                        if (r.isFailure) return@withContext r
                    }
                    Result.success(Unit)
                }

                result
                    .onSuccess { refresh() }
                    .onFailure { e -> _error.value = e.message ?: "Import failed" }
            } finally {
                _isImporting.value = false
            }
        }
    }

    fun import(uriString: String) = importUris(listOf(uriString))

    fun consumeError() { _error.value = null }
    fun emitError(message: String) { _error.value = message }

    // ✅ T3: 삭제
    fun deleteScore(id: String) {
        viewModelScope.launch {
            deleteScoreUseCase(id)
                .onSuccess { refresh() }
                .onFailure { _error.value = it.message ?: "삭제 실패" }
        }
    }

    // ✅ T3: 이름 변경(title)
    fun renameTitle(id: String, newTitle: String) {
        viewModelScope.launch {
            renameScoreTitleUseCase(id, newTitle)
                .onSuccess { refresh() }
                .onFailure { _error.value = it.message ?: "이름 변경 실패" }
        }
    }
}
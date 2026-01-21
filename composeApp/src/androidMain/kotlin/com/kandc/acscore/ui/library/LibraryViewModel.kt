package com.kandc.acscore.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kandc.acscore.data.model.Score
import com.kandc.acscore.domain.ImportScoreUseCase
import com.kandc.acscore.domain.LoadScoresUseCase
import com.kandc.acscore.domain.SearchScoresUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class LibraryViewModel(
    private val loadScores: LoadScoresUseCase,
    private val importScore: ImportScoreUseCase,
    private val searchScores: SearchScoresUseCase,
) : ViewModel() {

    private val _scores = MutableStateFlow<List<Score>>(emptyList())
    val scores: StateFlow<List<Score>> = _scores

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    fun refresh() {
        viewModelScope.launch {
            val q = _query.value.trim()
            _scores.value = if (q.isEmpty()) loadScores() else searchScores(q)
        }
    }

    fun setQuery(value: String) {
        _query.value = value
        refresh()
    }

    fun import(uriString: String) {
        viewModelScope.launch {
            importScore(uriString)
                .onSuccess { refresh() }
                .onFailure { e -> _error.value = e.message ?: "Import failed" }
        }
    }

    fun consumeError() {
        _error.value = null
    }
}
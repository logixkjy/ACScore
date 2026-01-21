package com.kandc.acscore.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kandc.acscore.data.model.Score
import com.kandc.acscore.domain.ImportScoreUseCase
import com.kandc.acscore.domain.LoadScoresUseCase
import com.kandc.acscore.domain.SearchScoresUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.text.Collator
import java.util.Locale

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

    fun clearQuery() {
        setQuery("")
    }

    fun import(uriString: String) {
        viewModelScope.launch {
            importScore(uriString)
                .onSuccess {
                    refresh()
                }
                .onFailure { e ->
                    _error.value = e.message ?: "Import failed"
                }
        }
    }

    fun consumeError() {
        _error.value = null
    }
}
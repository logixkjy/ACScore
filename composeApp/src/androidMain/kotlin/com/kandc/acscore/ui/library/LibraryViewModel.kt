package com.kandc.acscore.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kandc.acscore.data.model.Score
import com.kandc.acscore.domain.ImportScoreUseCase
import com.kandc.acscore.domain.LoadScoresUseCase
import com.kandc.acscore.domain.SearchScoresUseCase
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
) : ViewModel() {

    private val _scores = MutableStateFlow<List<Score>>(emptyList())
    val scores: StateFlow<List<Score>> = _scores

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    // ✅ Import 진행 상태 (검은 화면/무반응처럼 보이는 문제 해결용)
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

    fun clearQuery() {
        setQuery("")
    }

    /**
     * ✅ 다중 선택 Import 지원
     * - 내부적으로 기존 importScore(uri) 유스케이스를 여러 번 호출
     * - 무거운 작업은 IO로 보내고, UI에는 isImporting으로 로딩 표시
     */
    fun importUris(uriStrings: List<String>) {
        val list = uriStrings.filter { it.isNotBlank() }
        if (list.isEmpty()) return

        viewModelScope.launch {
            if (_isImporting.value) return@launch // 중복 실행 방지
            _isImporting.value = true

            try {
                // import 자체가 무거울 수 있으니 IO에서 실행
                val result = withContext(Dispatchers.IO) {
                    // 하나라도 실패하면 즉시 실패로 처리(원하면 부분 성공 로직으로 확장 가능)
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

    /**
     * 기존 단일 Import는 유지하되, 내부적으로 다중 Import로 위임
     */
    fun import(uriString: String) {
        importUris(listOf(uriString))
    }

    fun consumeError() {
        _error.value = null
    }

    fun emitError(message: String) {
        _error.value = message
    }
}
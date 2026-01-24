package com.kandc.acscore.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.kandc.acscore.shard.domain.usecase.ImportScoreUseCase
import com.kandc.acscore.shard.domain.usecase.LoadScoresUseCase
import com.kandc.acscore.shard.domain.usecase.SearchScoresUseCase
import com.kandc.acscore.shard.domain.usecase.DeleteScoreUseCase
import com.kandc.acscore.shard.domain.usecase.RenameScoreTitleUseCase

class LibraryViewModelFactory(
    private val load: LoadScoresUseCase,
    private val import: ImportScoreUseCase,
    private val search: SearchScoresUseCase,
    private val deleteScoreUseCase: DeleteScoreUseCase,           // ✅ 추가
    private val renameScoreTitleUseCase: RenameScoreTitleUseCase  // ✅ 추가
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return LibraryViewModel(
            loadScores = load,
            importScore = import,
            searchScores = search,
            deleteScoreUseCase = deleteScoreUseCase,
            renameScoreTitleUseCase = renameScoreTitleUseCase
        ) as T
    }
}
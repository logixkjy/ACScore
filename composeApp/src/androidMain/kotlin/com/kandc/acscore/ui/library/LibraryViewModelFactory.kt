package com.kandc.acscore.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.kandc.acscore.domain.ImportScoreUseCase
import com.kandc.acscore.domain.LoadScoresUseCase
import com.kandc.acscore.domain.SearchScoresUseCase

class LibraryViewModelFactory(
    private val load: LoadScoresUseCase,
    private val import: ImportScoreUseCase,
    private val search: SearchScoresUseCase,
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return LibraryViewModel(load, import, search) as T
    }
}
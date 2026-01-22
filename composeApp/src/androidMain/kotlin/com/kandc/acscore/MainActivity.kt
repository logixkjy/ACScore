package com.kandc.acscore

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.lifecycle.ViewModelProvider
import com.arkivanov.decompose.defaultComponentContext
import com.kandc.acscore.data.AndroidScoreRepository
import com.kandc.acscore.data.local.DbProvider
import com.kandc.acscore.domain.ImportScoreUseCase
import com.kandc.acscore.domain.LoadScoresUseCase
import com.kandc.acscore.domain.SearchScoresUseCase
import com.kandc.acscore.root.RootComponentImpl
import com.kandc.acscore.root.RootContent
import com.kandc.acscore.ui.library.LibraryViewModel
import com.kandc.acscore.ui.library.LibraryViewModelFactory

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // --- 기존 VM 생성 로직 유지 ---
        val db = DbProvider.get(this)
        val repo = AndroidScoreRepository(
            context = this,
            dao = db.scoreDao()
        )

        val load = LoadScoresUseCase(repo)
        val import = ImportScoreUseCase(repo)
        val search = SearchScoresUseCase(repo)

        val libraryVm = ViewModelProvider(
            this,
            LibraryViewModelFactory(load, import, search)
        )[LibraryViewModel::class.java]

        // --- Decompose Root 생성 (Library VM 주입) ---
        val root = RootComponentImpl(
            componentContext = defaultComponentContext(),
            libraryViewModel = libraryVm
        )

        setContent {
            MaterialTheme {
                Surface {
                    RootContent(component = root)
                }
            }
        }
    }
}
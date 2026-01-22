package com.kandc.acscore

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModelProvider
import com.arkivanov.decompose.defaultComponentContext
import com.kandc.acscore.data.AndroidScoreRepository
import com.kandc.acscore.data.local.DbProvider
import com.kandc.acscore.domain.ImportScoreUseCase
import com.kandc.acscore.domain.LoadScoresUseCase
import com.kandc.acscore.domain.SearchScoresUseCase
import com.kandc.acscore.root.MainHostScreen
import com.kandc.acscore.root.RootComponent
import com.kandc.acscore.root.RootComponentImpl
import com.kandc.acscore.root.RootUiViewModel
import com.kandc.acscore.ui.library.LibraryViewModel
import com.kandc.acscore.ui.library.LibraryViewModelFactory
import com.kandc.acscore.viewer.session.ViewerSessionViewModel

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // Repo / UseCases
        val db = DbProvider.get(this)
        val repo = AndroidScoreRepository(context = this, dao = db.scoreDao())
        val load = LoadScoresUseCase(repo)
        val import = ImportScoreUseCase(repo)
        val search = SearchScoresUseCase(repo)

        // Library VM (Activity scope)
        val libraryVmFactory = LibraryViewModelFactory(load, import, search)
        val libraryViewModel: LibraryViewModel =
            ViewModelProvider(this, libraryVmFactory)[LibraryViewModel::class.java]

        // Viewer session VM (Activity scope) : 탭/페이지 유지
        val viewerSessionVm: ViewerSessionViewModel =
            ViewModelProvider(this)[ViewerSessionViewModel::class.java]

        val rootUiVm: RootUiViewModel =
            ViewModelProvider(this)[RootUiViewModel::class.java]

        // ✅ 핵심: defaultComponentContext()는 onCreate에서 딱 1번만 생성
        val componentContext = defaultComponentContext()

        setContent {
            MaterialTheme {
                Surface {
                    // Root는 Activity 인스턴스 동안만 유지하면 충분 (탭/페이지는 viewerSessionVm이 유지)
                    val root: RootComponent = remember {
                        RootComponentImpl(
                            componentContext = componentContext,
                            libraryViewModel = libraryViewModel,
                            viewerSessionStore = viewerSessionVm.store,
                            rootUiViewModel = rootUiVm
                        )
                    }

                    MainHostScreen(root)
                }
            }
        }
    }
}
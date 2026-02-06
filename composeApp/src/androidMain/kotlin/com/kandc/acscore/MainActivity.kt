package com.kandc.acscore

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModelProvider
import com.arkivanov.decompose.defaultComponentContext
import com.kandc.acscore.data.AndroidScoreRepository
import com.kandc.acscore.data.local.DbProvider
import com.kandc.acscore.root.MainHostScreen
import com.kandc.acscore.root.RootComponent
import com.kandc.acscore.root.RootComponentImpl
import com.kandc.acscore.root.RootUiViewModel
import com.kandc.acscore.session.viewer.ViewerSessionViewModel
import com.kandc.acscore.share.IncomingAcsetBus
import com.kandc.acscore.shard.domain.usecase.DeleteScoreUseCase
import com.kandc.acscore.shard.domain.usecase.ImportScoreUseCase
import com.kandc.acscore.shard.domain.usecase.LoadScoresUseCase
import com.kandc.acscore.shard.domain.usecase.RenameScoreTitleUseCase
import com.kandc.acscore.shard.domain.usecase.SearchScoresUseCase
import com.kandc.acscore.ui.library.LibraryViewModel
import com.kandc.acscore.ui.library.LibraryViewModelFactory
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

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
        val delete = DeleteScoreUseCase(repo)
        val rename = RenameScoreTitleUseCase(repo)

        // Library VM (Activity scope)
        val libraryVmFactory = LibraryViewModelFactory(load, import, search, delete, rename)
        val libraryViewModel: LibraryViewModel =
            ViewModelProvider(this, libraryVmFactory)[LibraryViewModel::class.java]

        // Viewer session VM (Activity scope) : 탭/페이지 유지
        val viewerSessionVm: ViewerSessionViewModel =
            ViewModelProvider(this)[ViewerSessionViewModel::class.java]

        val rootUiVm: RootUiViewModel =
            ViewModelProvider(this)[RootUiViewModel::class.java]

        // ✅ 핵심: defaultComponentContext()는 onCreate에서 딱 1번만 생성
        val componentContext = defaultComponentContext()
        val initialAcsetUri: Uri? = extractIncomingAcsetUri(intent)

        setContent {
            MaterialTheme {
                Surface {
                    val root: RootComponent = remember {
                        RootComponentImpl(
                            componentContext = componentContext,
                            libraryViewModel = libraryViewModel,
                            viewerSessionStore = viewerSessionVm.store,
                            rootUiViewModel = rootUiVm
                        )
                    }

                    /**
                     * ✅ 여기서 이벤트를 Root/UI로 넘겨서 "Import 시작" 트리거로 쓰면 됨
                     * 지금은 RootUiViewModel에 함수가 없으니, 우선 TODO로 연결 포인트만 만들어둔다.
                     */
                    LaunchedEffect(Unit) {
                        // 1) Handle incoming acset while the app is already running
                        launch {
                            IncomingAcsetBus.events.collectLatest { uri ->
                                root.handleIncomingAcset(uri)
                            }
                        }

                        // 2) Handle cold-start acset (sent before the collector was active)
                        initialAcsetUri?.let { uri ->
                            root.handleIncomingAcset(uri)
                        }
                    }

                    MainHostScreen(root)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // ✅ 앱이 떠있는 상태에서 다시 파일을 탭했을 때
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent) {
        val uri = extractIncomingAcsetUri(intent) ?: return
        IncomingAcsetBus.emit(uri)
    }

    private fun extractIncomingAcsetUri(intent: Intent): Uri? {
        val candidates: List<Uri> = when (intent.action) {
            Intent.ACTION_VIEW -> listOfNotNull(intent.data)

            Intent.ACTION_SEND -> {
                val fromExtra = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                val fromClip = intent.clipData?.getItemAt(0)?.uri
                listOfNotNull(fromExtra, fromClip)
            }

            Intent.ACTION_SEND_MULTIPLE -> {
                val list = intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM).orEmpty()
                val clip = intent.clipData?.let { cd ->
                    (0 until cd.itemCount).mapNotNull { idx -> cd.getItemAt(idx)?.uri }
                }.orEmpty()
                (list + clip).distinct()
            }

            else -> {
                // Some providers use clipData even for VIEW
                val fromClip = intent.clipData?.getItemAt(0)?.uri
                listOfNotNull(fromClip)
            }
        }

        val uri = candidates.firstOrNull { u ->
            val s = u.toString()
            s.endsWith(".acset", ignoreCase = true) || s.contains(".acset", ignoreCase = true)
        } ?: return null

        // Try to keep read permission across process restarts (best-effort)
        runCatching {
            val flags = intent.flags
            val takeFlags = flags and
                    (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            if (takeFlags != 0) {
                contentResolver.takePersistableUriPermission(uri, takeFlags)
            }
        }

        return uri
    }
}
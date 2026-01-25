package com.kandc.acscore.root

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.kandc.acscore.ui.common.SegmentedTabs
import com.kandc.acscore.ui.library.LibraryScreen
import com.kandc.acscore.ui.setlist.ScorePickItem
import com.kandc.acscore.ui.setlist.SetlistDetailScreen
import com.kandc.acscore.ui.setlist.SetlistListScreen
import com.kandc.acscore.ui.viewer.TabbedViewerScreen
import com.kandc.acscore.viewer.domain.ViewerOpenRequest
import java.io.File
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import com.kandc.acscore.di.SetlistDi
import com.kandc.acscore.shared.domain.usecase.AddScoreToSetlistUseCase

private enum class HomeTab { Library, Setlists }

@Composable
fun MainHostScreen(component: RootComponent) {
    val overlayOpen by component.isLibraryOverlayOpen.collectAsState()
    var selectedTab by rememberSaveable { mutableStateOf(HomeTab.Library) }

    // ✅ Setlists 내 네비(리스트 ↔ 상세)
    var openedSetlistId by rememberSaveable { mutableStateOf<String?>(null) }
    var pickingForSetlistId by rememberSaveable { mutableStateOf<String?>(null) }
    var pickingSelectedIds by rememberSaveable { mutableStateOf<Set<String>>(emptySet()) } // ✅ 추가
    val scope = rememberCoroutineScope()

    Box(Modifier.fillMaxSize()) {
        // 아래: Viewer
        TabbedViewerScreen(
            sessionStore = component.viewerSessionStore,
            onRequestOpenLibrary = {
                selectedTab = HomeTab.Library
                openedSetlistId = null
                component.openLibraryOverlay()
            },
            modifier = Modifier.fillMaxSize()
        )

        // 위: Overlay
        LibraryOverlay(
            visible = overlayOpen,
            onDismiss = { component.closeLibraryOverlay() }
        ) {
            Column(Modifier.fillMaxSize()) {
                // 세그먼트 컨트롤을 조금 아래로
                SegmentedTabs(
                    titles = listOf("Library", "Setlists"),
                    selectedIndex = if (selectedTab == HomeTab.Library) 0 else 1,
                    onSelect = { idx ->
                        selectedTab = if (idx == 0) HomeTab.Library else HomeTab.Setlists
                        if (selectedTab == HomeTab.Library) openedSetlistId = null
                    },
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .padding(top = 16.dp, bottom = 8.dp)
                )

                when (selectedTab) {
                    HomeTab.Library -> {
                        val context = LocalContext.current
                        val scope = rememberCoroutineScope()

                        // ✅ Setlist repo/usecase (반드시 remember로 고정)
                        val setlistRepo = remember(context.applicationContext) {
                            com.kandc.acscore.di.SetlistDi.provideRepository(context.applicationContext)
                        }
                        val addToSetlist = remember(setlistRepo) {
                            com.kandc.acscore.shared.domain.usecase.AddScoreToSetlistUseCase(setlistRepo)
                        }

                        LibraryScreen(
                            vm = component.libraryViewModel,

                            onOpenViewer = { scoreId, title, fileName ->
                                val filePath = File(context.filesDir, "scores/$fileName").absolutePath
                                component.onScoreSelected(
                                    ViewerOpenRequest(scoreId = scoreId, title = title, filePath = filePath)
                                )
                            },

                            onPickScore = pickingForSetlistId?.let { targetSetlistId ->
                                { scoreId, title, fileName ->
                                    scope.launch {
                                        runCatching {
                                            addToSetlist(targetSetlistId, scoreId)
                                        }.onSuccess {
                                            // ✅ 연속 선택: 라이브러리에 남아있기
                                            pickingSelectedIds = pickingSelectedIds + scoreId

                                            // ✅ 돌아갈 때를 대비해서 상세 id는 유지해두자
                                            openedSetlistId = targetSetlistId

                                            // ❌ 아래 3개는 연속 선택에서는 하지 않는다
                                            // selectedTab = HomeTab.Setlists
                                            // pickingForSetlistId = null
                                            // pickingSelectedIds = emptySet()

                                        }.onFailure { e ->
                                            component.libraryViewModel.emitError(e.message ?: "세트리스트 추가 실패")
                                        }
                                    }
                                }
                            },

                            onCancelPick = if (pickingForSetlistId != null) {
                                {
                                    selectedTab = HomeTab.Setlists
                                    openedSetlistId = pickingForSetlistId
                                    pickingForSetlistId = null
                                    pickingSelectedIds = emptySet()
                                }
                            } else null,

                            pickedScoreIds = pickingSelectedIds
                        )
                    }

                    HomeTab.Setlists -> {
                        // ✅ 라이브러리 후보 리스트(ScorePickItem) 만들기
                        // 아래 함수에서 "너의 실제 라이브러리 모델"에 맞게 매핑만 맞춰주면 됨.
                        val candidates = rememberLibraryCandidates(component)

                        val id = openedSetlistId
                        if (id == null) {
                            SetlistListScreen(
                                onOpenSetlist = { setlistId ->
                                    openedSetlistId = setlistId
                                }
                            )
                        } else {
                            val context = LocalContext.current
                            SetlistDetailScreen(
                                setlistId = id,
                                libraryCandidates = candidates,
                                onBack = { openedSetlistId = null },
                                onOpenViewer = { scoreId, title, fileName ->
                                    val filePath = File(context.filesDir, "scores/$fileName").absolutePath
                                    component.onScoreSelected(
                                        ViewerOpenRequest(
                                            scoreId = scoreId,
                                            title = title,
                                            filePath = filePath
                                        )
                                    )
                                },
                                onRequestPickFromLibrary = { currentItemIds ->
                                    pickingForSetlistId = id
                                    pickingSelectedIds = currentItemIds.toSet()
                                    selectedTab = HomeTab.Library
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun rememberLibraryCandidates(component: RootComponent): List<ScorePickItem> {
    val scores by component.libraryViewModel.scores.collectAsState()

    return remember(scores) {
        scores.map { s ->
            ScorePickItem(
                scoreId = s.id,        // ✅ Score.id
                title = s.title,       // ✅ Score.title
                fileName = s.fileName  // ✅ Score.fileName
            )
        }
    }
}
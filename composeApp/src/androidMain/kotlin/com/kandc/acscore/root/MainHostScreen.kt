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

private enum class HomeTab { Library, Setlists }

@Composable
fun MainHostScreen(component: RootComponent) {
    val overlayOpen by component.isLibraryOverlayOpen.collectAsState()
    var selectedTab by rememberSaveable { mutableStateOf(HomeTab.Library) }

    // ✅ Setlists 내 네비(리스트 ↔ 상세)
    var openedSetlistId by rememberSaveable { mutableStateOf<String?>(null) }

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
                        openedSetlistId = null

                        val context = LocalContext.current
                        LibraryScreen(
                            vm = component.libraryViewModel,
                            onOpenViewer = { scoreId, title, fileName ->
                                val filePath = File(context.filesDir, "scores/$fileName").absolutePath
                                component.onScoreSelected(
                                    ViewerOpenRequest(
                                        scoreId = scoreId,
                                        title = title,
                                        filePath = filePath
                                    )
                                )
                            }
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
package com.kandc.acscore.root

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.kandc.acscore.ui.common.SegmentedTabs
import com.kandc.acscore.ui.library.LibraryScreen
import com.kandc.acscore.ui.setlist.SetlistListScreen
import com.kandc.acscore.ui.viewer.TabbedViewerScreen
import com.kandc.acscore.viewer.domain.ViewerOpenRequest
import java.io.File

private enum class HomeTab { Library, Setlists }

@Composable
fun MainHostScreen(component: RootComponent) {
    val overlayOpen by component.isLibraryOverlayOpen.collectAsState()
    var selectedTab by rememberSaveable { mutableStateOf(HomeTab.Library) }

    Box(Modifier.fillMaxSize()) {
        // 아래: Viewer (항상 유지)
        TabbedViewerScreen(
            sessionStore = component.viewerSessionStore,
            onRequestOpenLibrary = {
                selectedTab = HomeTab.Library
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
                // ✅ iOS 세그먼트 컨트롤 느낌의 상단 전환 UI
                SegmentedTabs(
                    titles = listOf("Library", "Setlists"),
                    selectedIndex = if (selectedTab == HomeTab.Library) 0 else 1,
                    onSelect = { idx ->
                        selectedTab = if (idx == 0) HomeTab.Library else HomeTab.Setlists
                    },
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .padding(top = 16.dp, bottom = 8.dp)
                )

                when (selectedTab) {
                    HomeTab.Library -> {
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
                        SetlistListScreen(
                            onOpenSetlist = { _ ->
                                // TODO(T5-2): 상세 화면 이동
                            }
                        )
                    }
                }
            }
        }
    }
}
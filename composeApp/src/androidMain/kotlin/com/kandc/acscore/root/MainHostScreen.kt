package com.kandc.acscore.root

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.zIndex
import androidx.compose.ui.unit.dp
import com.kandc.acscore.ui.library.LibraryScreen
import com.kandc.acscore.viewer.domain.ViewerOpenRequest
import com.kandc.acscore.viewer.ui.TabbedViewerScreen
import java.io.File
import androidx.compose.ui.platform.LocalContext

@Composable
fun MainHostScreen(component: RootComponent) {
    val overlayOpen by component.isLibraryOverlayOpen.collectAsState()

    Box(Modifier.fillMaxSize()) {
        // 1) 아래: Viewer
        TabbedViewerScreen(
            sessionStore = component.viewerSessionStore,
            onRequestOpenLibrary = { component.openLibraryOverlay() }
        )

        // 2) 위: Library Overlay (2/3 패널 + 상단 1/3 반투명)
        LibraryOverlay(
            visible = overlayOpen,
            onDismiss = { component.closeLibraryOverlay() }
        ) {
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
    }
}
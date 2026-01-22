package com.kandc.acscore.root

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.kandc.acscore.ui.library.LibraryScreen
import com.kandc.acscore.viewer.domain.ViewerOpenRequest
import com.kandc.acscore.viewer.ui.TabbedViewerScreen
import java.io.File

@Composable
fun MainHostScreen(component: RootComponent) {
    val overlayOpen by component.isLibraryOverlayOpen.collectAsState()

    Box(Modifier.fillMaxSize()) {
        // 아래: Viewer
        TabbedViewerScreen(
            sessionStore = component.viewerSessionStore,
            onRequestOpenLibrary = { component.openLibraryOverlay() },
            modifier = Modifier.fillMaxSize()
        )

        // 위: Library Overlay
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
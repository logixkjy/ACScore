package com.kandc.acscore.root

import androidx.compose.runtime.Composable
import com.arkivanov.decompose.extensions.compose.jetpack.stack.Children
import com.arkivanov.decompose.extensions.compose.jetpack.stack.animation.stackAnimation
import com.kandc.acscore.ui.library.LibraryScreen
import com.kandc.acscore.viewer.ui.PdfViewerScreen
import com.kandc.acscore.viewer.domain.ViewerOpenRequest

@Composable
fun RootContent(component: RootComponent) {
    Children(
        stack = component.stack,
        animation = stackAnimation()
    ) { child ->
        when (val instance = child.instance) {
            is RootComponent.Child.Library -> {
                LibraryScreen(
                    vm = instance.component.vm,
                    onOpenViewer = { id, title, path ->
                        instance.component.openViewer(
                            ViewerOpenRequest(
                                scoreId = id,
                                title = title,
                                filePath = path
                            )
                        )
                    }
                )
            }
            is RootComponent.Child.Viewer -> {
                PdfViewerScreen(request = instance.component.request)
            }
        }
    }
}
package com.kandc.acscore.root

import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.value.Value
import com.kandc.acscore.ui.library.LibraryViewModel
import com.kandc.acscore.viewer.domain.ViewerOpenRequest
import com.kandc.acscore.viewer.session.ViewerSessionStore
import kotlinx.coroutines.flow.StateFlow

interface RootComponent {
    val stack: Value<ChildStack<RootConfig, Child>>

    val libraryViewModel: LibraryViewModel
    val viewerSessionStore: ViewerSessionStore

    val isLibraryOverlayOpen: StateFlow<Boolean>
    fun openLibraryOverlay()
    fun closeLibraryOverlay()

    fun onScoreSelected(request: ViewerOpenRequest)

    sealed interface Child {
        data class Main(val component: MainComponent) : Child
    }
}

interface MainComponent
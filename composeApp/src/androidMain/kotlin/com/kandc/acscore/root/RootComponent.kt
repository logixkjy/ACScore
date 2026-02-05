package com.kandc.acscore.root

import android.net.Uri
import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.value.Value
import com.kandc.acscore.ui.library.LibraryViewModel
import com.kandc.acscore.viewer.domain.ViewerOpenRequest
import com.kandc.acscore.session.viewer.ViewerSessionStore
import kotlinx.coroutines.flow.StateFlow

interface RootComponent {
    val stack: Value<ChildStack<RootConfig, Child>>

    val libraryViewModel: LibraryViewModel
    val viewerSessionStore: ViewerSessionStore

    val isLibraryOverlayOpen: StateFlow<Boolean>
    fun openLibraryOverlay()
    fun closeLibraryOverlay()

    fun onScoreSelected(request: ViewerOpenRequest)

    // ✅ (추가) 외부 공유/탭으로 들어온 .acset 처리 진입점
    val pendingAcsetUri: StateFlow<Uri?>
    fun handleIncomingAcset(uri: Uri)
    fun consumePendingAcset()

    sealed interface Child {
        data class Main(val component: MainComponent) : Child
    }
}

interface MainComponent
package com.kandc.acscore.root

import android.net.Uri
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.router.stack.StackNavigation
import com.arkivanov.decompose.router.stack.childStack
import com.arkivanov.decompose.value.Value
import com.kandc.acscore.ui.library.LibraryViewModel
import com.kandc.acscore.viewer.domain.ViewerOpenRequest
import com.kandc.acscore.session.viewer.ViewerSessionStore
import kotlinx.coroutines.flow.StateFlow

class RootComponentImpl(
    componentContext: ComponentContext,
    override val libraryViewModel: LibraryViewModel,
    override val viewerSessionStore: ViewerSessionStore,
    private val rootUiViewModel: RootUiViewModel
) : RootComponent, ComponentContext by componentContext {

    private val navigation = StackNavigation<RootConfig>()

    override val isLibraryOverlayOpen: StateFlow<Boolean> =
        rootUiViewModel.isLibraryOverlayOpen

    // ✅ (추가)
    override val pendingAcsetUri: StateFlow<Uri?> =
        rootUiViewModel.pendingAcsetUri

    override val stack: Value<ChildStack<RootConfig, RootComponent.Child>> =
        childStack(
            source = navigation,
            initialConfiguration = RootConfig.Main,
            handleBackButton = true
        ) { _, _ ->
            RootComponent.Child.Main(object : MainComponent {})
        }

    override fun openLibraryOverlay() {
        rootUiViewModel.openLibraryOverlay(userInitiated = true)
    }

    override fun closeLibraryOverlay() {
        rootUiViewModel.closeLibraryOverlay(userInitiated = true)
    }

    override fun onScoreSelected(request: ViewerOpenRequest) {
        viewerSessionStore.openOrActivate(request)
        rootUiViewModel.onScoreSelectedAndCloseOverlay()
    }

    // ✅ (추가)
    override fun handleIncomingAcset(uri: Uri) {
        rootUiViewModel.onIncomingAcset(uri)
    }

    override fun consumePendingAcset() {
        rootUiViewModel.consumePendingAcset()
    }
}
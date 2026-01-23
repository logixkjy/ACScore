package com.kandc.acscore.root

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.router.stack.StackNavigation
import com.arkivanov.decompose.router.stack.childStack
import com.arkivanov.decompose.value.Value
import com.kandc.acscore.library.ui.LibraryViewModel
import com.kandc.acscore.viewer.domain.ViewerOpenRequest
import com.kandc.acscore.viewer.session.ViewerSessionStore
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
        // ✅ 악보 선택으로 닫힌 것도 “다시 자동으로 열지 않음”에 포함
        rootUiViewModel.onScoreSelectedAndCloseOverlay()
    }
}
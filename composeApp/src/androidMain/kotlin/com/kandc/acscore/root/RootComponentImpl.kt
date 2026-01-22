package com.kandc.acscore.root

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.router.stack.StackNavigation
import com.arkivanov.decompose.router.stack.childStack
import com.arkivanov.decompose.value.Value
import com.kandc.acscore.ui.library.LibraryViewModel
import com.kandc.acscore.viewer.domain.ViewerOpenRequest
import com.kandc.acscore.viewer.session.ViewerSessionStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class RootComponentImpl(
    componentContext: ComponentContext,
    override val libraryViewModel: LibraryViewModel
) : RootComponent, ComponentContext by componentContext {

    private val navigation = StackNavigation<RootConfig>()

    override val viewerSessionStore: ViewerSessionStore = ViewerSessionStore()

    private val _isLibraryOverlayOpen = MutableStateFlow(true) // ✅ 피아스코어처럼 시작 시 열림
    override val isLibraryOverlayOpen: StateFlow<Boolean> = _isLibraryOverlayOpen

    override val stack: Value<ChildStack<RootConfig, RootComponent.Child>> =
        childStack(
            source = navigation,
            initialConfiguration = RootConfig.Main,
            handleBackButton = true
        ) { _, _ ->
            RootComponent.Child.Main(object : MainComponent {})
        }

    override fun openLibraryOverlay() {
        _isLibraryOverlayOpen.value = true
    }

    override fun closeLibraryOverlay() {
        _isLibraryOverlayOpen.value = false
    }

    override fun onScoreSelected(request: ViewerOpenRequest) {
        viewerSessionStore.openOrActivate(request)
        closeLibraryOverlay()
    }
}
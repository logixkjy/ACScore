package com.kandc.acscore.root

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.router.stack.StackNavigation
import com.arkivanov.decompose.router.stack.childStack
import com.arkivanov.decompose.router.stack.pop
import com.arkivanov.decompose.router.stack.push
import com.arkivanov.decompose.value.Value
import com.kandc.acscore.ui.library.LibraryViewModel
import com.kandc.acscore.viewer.domain.ViewerOpenRequest

class RootComponentImpl(
    componentContext: ComponentContext,
    private val libraryViewModel: LibraryViewModel
) : RootComponent, ComponentContext by componentContext {

    private val navigation = StackNavigation<RootConfig>()

    override val stack: Value<ChildStack<RootConfig, RootComponent.Child>> =
        childStack(
            source = navigation,
            initialConfiguration = RootConfig.Library,
            handleBackButton = true,
            childFactory = ::createChild
        )

    override fun openViewer(request: ViewerOpenRequest) {
        openViewerMany(listOf(request), initialIndex = 0)
    }

    override fun openViewerMany(requests: List<ViewerOpenRequest>, initialIndex: Int) {
        val items = requests.map {
            RootConfig.Viewer.ViewerItem(
                scoreId = it.scoreId,
                title = it.title,
                filePath = it.filePath
            )
        }
        navigation.push(RootConfig.Viewer(items = items, initialActiveIndex = initialIndex))
    }

    override fun onBack() {
        navigation.pop()
    }

    private fun createChild(
        config: RootConfig,
        childContext: ComponentContext
    ): RootComponent.Child {
        return when (config) {
            RootConfig.Library -> {
                RootComponent.Child.Library(
                    LibraryComponentImpl(
                        componentContext = childContext,
                        libraryViewModel = libraryViewModel,
                        onOpenViewer = ::openViewer
                    )
                )
            }

            is RootConfig.Viewer -> {
                // ViewerComponent가 탭 상태를 내부에서 관리하도록 초기 요청들 전달
                val initialRequests = config.items.map {
                    ViewerOpenRequest(
                        scoreId = it.scoreId,
                        title = it.title,
                        filePath = it.filePath
                    )
                }
                RootComponent.Child.Viewer(
                    ViewerComponentImpl(
                        componentContext = childContext,
                        initialRequests = initialRequests,
                        initialActiveIndex = config.initialActiveIndex,
                        onBack = ::onBack
                    )
                )
            }
        }
    }
}
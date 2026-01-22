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
        navigation.push(
            RootConfig.Viewer(
                scoreId = request.scoreId,
                title = request.title,
                filePath = request.filePath
            )
        )
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
                RootComponent.Child.Viewer(
                    ViewerComponentImpl(
                        componentContext = childContext,
                        request = ViewerOpenRequest(
                            scoreId = config.scoreId,
                            title = config.title,
                            filePath = config.filePath
                        ),
                        onBack = ::onBack
                    )
                )
            }
        }
    }
}
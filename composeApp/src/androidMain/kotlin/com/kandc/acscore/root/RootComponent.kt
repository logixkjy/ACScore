package com.kandc.acscore.root

import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.value.Value
import com.kandc.acscore.viewer.domain.ViewerOpenRequest

interface RootComponent {
    val stack: Value<ChildStack<RootConfig, Child>>

    fun openViewer(request: ViewerOpenRequest)
    fun onBack()

    sealed interface Child {
        data class Library(val component: LibraryComponent) : Child
        data class Viewer(val component: ViewerComponent) : Child
    }
}
package com.kandc.acscore.root

import com.arkivanov.decompose.ComponentContext
import com.kandc.acscore.ui.library.LibraryViewModel
import com.kandc.acscore.viewer.domain.ViewerOpenRequest

class LibraryComponentImpl(
    componentContext: ComponentContext,
    private val libraryViewModel: LibraryViewModel,
    private val onOpenViewer: (ViewerOpenRequest) -> Unit
) : LibraryComponent, ComponentContext by componentContext {

    override val vm: LibraryViewModel = libraryViewModel

    override fun openViewer(request: ViewerOpenRequest) {
        onOpenViewer(request)
    }
}
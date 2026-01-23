package com.kandc.acscore.root

import com.kandc.acscore.library.ui.LibraryViewModel
import com.kandc.acscore.viewer.domain.ViewerOpenRequest

interface LibraryComponent {
    val vm: LibraryViewModel
    fun openViewer(request: ViewerOpenRequest)
}
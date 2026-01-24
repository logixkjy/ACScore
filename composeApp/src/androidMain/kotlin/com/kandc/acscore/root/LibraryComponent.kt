package com.kandc.acscore.root

import com.kandc.acscore.ui.library.LibraryViewModel
import com.kandc.acscore.viewer.domain.ViewerOpenRequest

interface LibraryComponent {
    val vm: LibraryViewModel
    fun openViewer(request: ViewerOpenRequest)
}
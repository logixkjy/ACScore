package com.kandc.acscore.root

import com.kandc.acscore.viewer.domain.ViewerOpenRequest

interface ViewerComponent {
    val request: ViewerOpenRequest
    fun onBack()
}
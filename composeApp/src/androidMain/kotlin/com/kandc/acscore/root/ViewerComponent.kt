package com.kandc.acscore.root

import com.kandc.acscore.viewer.domain.ViewerOpenRequest
import com.kandc.acscore.session.viewer.ViewerSessionStore

interface ViewerComponent {
    val sessionStore: ViewerSessionStore

    fun selectTab(tabId: String)
    fun closeTab(tabId: String)
    fun addTab(request: ViewerOpenRequest, makeActive: Boolean = true)
    fun addTabs(requests: List<ViewerOpenRequest>, initialActiveIndex: Int = 0)
    fun onBack()
}
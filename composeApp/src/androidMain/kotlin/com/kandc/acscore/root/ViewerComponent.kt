package com.kandc.acscore.root

import com.kandc.acscore.viewer.domain.ViewerOpenRequest
import kotlinx.coroutines.flow.StateFlow

interface ViewerComponent {
    val tabs: StateFlow<List<ViewerTabUi>>
    val activeTabId: StateFlow<String?>

    fun selectTab(tabId: String)
    fun closeTab(tabId: String)

    fun onBack()
}

data class ViewerTabUi(
    val tabId: String,
    val title: String,
    val request: ViewerOpenRequest
)
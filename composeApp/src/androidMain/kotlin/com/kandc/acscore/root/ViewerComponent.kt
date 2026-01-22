package com.kandc.acscore.root

import com.kandc.acscore.viewer.domain.ViewerOpenRequest
import kotlinx.coroutines.flow.StateFlow

interface ViewerComponent {
    val tabs: StateFlow<List<ViewerTabUi>>
    val activeTabId: StateFlow<String?>

    fun selectTab(tabId: String)
    fun closeTab(tabId: String)

    // ✅ 추가: 탭 추가 (A 정책에 필요)
    fun addTab(request: ViewerOpenRequest, makeActive: Boolean = true)
    fun addTabs(requests: List<ViewerOpenRequest>, initialActiveIndex: Int = 0)

    fun onBack()
}

data class ViewerTabUi(
    val tabId: String,
    val title: String,
    val request: ViewerOpenRequest
)
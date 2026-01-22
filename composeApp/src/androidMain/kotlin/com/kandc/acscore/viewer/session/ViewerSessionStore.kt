package com.kandc.acscore.viewer.session

import com.kandc.acscore.viewer.domain.ViewerOpenRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID

data class ViewerSessionTab(
    val tabId: String,
    val request: ViewerOpenRequest,
    val lastPage: Int = 0
)

data class ViewerSessionState(
    val tabs: List<ViewerSessionTab> = emptyList(),
    val activeTabId: String? = null
)

class ViewerSessionStore {

    private val _state = MutableStateFlow(ViewerSessionState())
    val state: StateFlow<ViewerSessionState> = _state

    fun openOrActivate(request: ViewerOpenRequest) {
        _state.update { s ->
            val existing = s.tabs.firstOrNull { it.request.filePath == request.filePath }
            if (existing != null) {
                s.copy(activeTabId = existing.tabId)
            } else {
                val newTab = ViewerSessionTab(
                    tabId = UUID.randomUUID().toString(),
                    request = request,
                    lastPage = 0
                )
                s.copy(
                    tabs = s.tabs + newTab,
                    activeTabId = newTab.tabId
                )
            }
        }
    }

    fun setActive(tabId: String) {
        _state.update { s ->
            if (s.tabs.any { it.tabId == tabId }) s.copy(activeTabId = tabId) else s
        }
    }

    fun closeTab(tabId: String) {
        _state.update { s ->
            val newTabs = s.tabs.filterNot { it.tabId == tabId }
            val newActive = when {
                newTabs.isEmpty() -> null
                s.activeTabId == tabId -> newTabs.first().tabId
                else -> s.activeTabId
            }
            s.copy(tabs = newTabs, activeTabId = newActive)
        }
    }

    fun updateLastPage(tabId: String, page: Int) {
        _state.update { s ->
            s.copy(
                tabs = s.tabs.map { t ->
                    if (t.tabId == tabId) t.copy(lastPage = page.coerceAtLeast(0)) else t
                }
            )
        }
    }
}
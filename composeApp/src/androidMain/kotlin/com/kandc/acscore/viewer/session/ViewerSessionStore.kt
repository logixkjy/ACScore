package com.kandc.acscore.viewer.session

import com.kandc.acscore.viewer.domain.ViewerOpenRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID

data class ViewerTabState(
    val tabId: String,
    val request: ViewerOpenRequest,
    val lastPage: Int = 0
)

data class ViewerSessionState(
    val tabs: List<ViewerTabState> = emptyList(),
    val activeTabId: String? = null
)

class ViewerSessionStore {

    private val _state = MutableStateFlow(ViewerSessionState())
    val state: StateFlow<ViewerSessionState> = _state

    fun setActive(tabId: String) {
        val exists = _state.value.tabs.any { it.tabId == tabId }
        if (exists) _state.update { it.copy(activeTabId = tabId) }
    }

    fun closeTab(tabId: String) {
        _state.update { s ->
            val remaining = s.tabs.filterNot { it.tabId == tabId }
            val nextActive =
                when {
                    remaining.isEmpty() -> null
                    s.activeTabId != tabId -> s.activeTabId
                    else -> remaining.first().tabId
                }
            s.copy(tabs = remaining, activeTabId = nextActive)
        }
    }

    /**
     * ✅ 단일 탭: 같은 filePath면 기존 탭 활성화, 아니면 새 탭 추가+활성화
     */
    fun openOrActivate(request: ViewerOpenRequest) {
        _state.update { s ->
            val existing = s.tabs.firstOrNull { it.request.filePath == request.filePath }
            if (existing != null) {
                s.copy(activeTabId = existing.tabId)
            } else {
                val newTab = ViewerTabState(
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

    /**
     * ✅ 여러 탭을 한 번에 열기 (중복 제거)
     * - requests 중 기존에 없는 것만 새로 추가
     * - initialActiveIndex가 가리키는 request가 이미 있으면 그 탭 활성화
     * - 새로 추가된 탭이 있으면, 가능한 한 initialActiveIndex에 해당하는 "새 탭"을 활성화
     */
    fun openMany(requests: List<ViewerOpenRequest>, initialActiveIndex: Int = 0) {
        if (requests.isEmpty()) return

        _state.update { s ->
            val existingByPath = s.tabs.associateBy { it.request.filePath }

            val targetReq = requests.getOrNull(initialActiveIndex)
            val alreadyTargetTab = targetReq?.let { existingByPath[it.filePath] }

            // 새로 추가될 것만
            val newOnes = requests.filter { it.filePath !in existingByPath }

            if (newOnes.isEmpty()) {
                // 전부 중복이면 targetReq가 가리키는 기존 탭(있으면) 활성화
                return@update if (alreadyTargetTab != null) {
                    s.copy(activeTabId = alreadyTargetTab.tabId)
                } else {
                    s // 변화 없음
                }
            }

            val newTabs = newOnes.map { req ->
                ViewerTabState(
                    tabId = UUID.randomUUID().toString(),
                    request = req,
                    lastPage = 0
                )
            }

            val merged = s.tabs + newTabs

            // 활성 탭 결정:
            // 1) targetReq가 기존 탭이면 그걸 유지
            // 2) 아니면 targetReq가 새로 추가된 탭이면 그 탭
            // 3) 아니면 새로 추가된 첫 탭
            val activeTabId =
                when {
                    alreadyTargetTab != null -> alreadyTargetTab.tabId
                    targetReq != null -> {
                        val inNew = newTabs.firstOrNull { it.request.filePath == targetReq.filePath }
                        inNew?.tabId ?: newTabs.first().tabId
                    }
                    else -> newTabs.first().tabId
                }

            s.copy(tabs = merged, activeTabId = activeTabId)
        }
    }

    fun updateLastPage(tabId: String, lastPage: Int) {
        _state.update { s ->
            val updated = s.tabs.map { tab ->
                if (tab.tabId == tabId) tab.copy(lastPage = lastPage) else tab
            }
            s.copy(tabs = updated)
        }
    }
}
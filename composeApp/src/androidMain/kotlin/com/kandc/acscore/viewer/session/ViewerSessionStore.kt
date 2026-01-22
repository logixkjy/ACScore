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
    val tabs: List<ViewerTabState> = emptyList(), // ✅ 순서 자체가 MRU(최근 사용순)
    val activeTabId: String? = null
)

class ViewerSessionStore(
    // ✅ 탭 닫힐 때(실제로 제거될 때) filePath를 알려줌 → cache.clearByFile(fileKey) 같은 정리용
    private val onTabClosed: (filePath: String) -> Unit = {}
) {

    private val _state = MutableStateFlow(ViewerSessionState())
    val state: StateFlow<ViewerSessionState> = _state

    private fun moveToFront(list: List<ViewerTabState>, tabId: String): List<ViewerTabState> {
        val idx = list.indexOfFirst { it.tabId == tabId }
        if (idx <= 0) return list
        val tab = list[idx]
        return buildList(list.size) {
            add(tab)
            for (i in list.indices) if (i != idx) add(list[i])
        }
    }

    fun setActive(tabId: String) {
        _state.update { s ->
            if (s.tabs.none { it.tabId == tabId }) return@update s
            val reordered = moveToFront(s.tabs, tabId)
            s.copy(tabs = reordered, activeTabId = tabId)
        }
    }

    /**
     * ✅ 닫을 때 active 선정: 오른쪽 우선(없으면 왼쪽)
     */
    fun closeTab(tabId: String) {
        // ✅ update 밖에서 먼저 “닫힐 탭”을 찾아서 filePath 확보
        val closingFilePath = _state.value.tabs.firstOrNull { it.tabId == tabId }?.request?.filePath

        _state.update { s ->
            val idx = s.tabs.indexOfFirst { it.tabId == tabId }
            if (idx < 0) return@update s

            val wasActive = (s.activeTabId == tabId)
            val remaining = s.tabs.filterNot { it.tabId == tabId }

            val nextActive =
                if (!wasActive) {
                    s.activeTabId?.takeIf { id -> remaining.any { it.tabId == id } }
                } else {
                    when {
                        remaining.isEmpty() -> null
                        idx < remaining.size -> remaining[idx].tabId
                        idx - 1 >= 0 -> remaining[idx - 1].tabId
                        else -> remaining.first().tabId
                    }
                }

            val finalTabs =
                if (nextActive != null) moveToFront(remaining, nextActive) else remaining

            s.copy(tabs = finalTabs, activeTabId = nextActive)
        }

        // ✅ 상태 업데이트가 끝난 뒤에 캐시 정리 콜백 호출 (안전)
        if (closingFilePath != null) {
            runCatching { onTabClosed(closingFilePath) }
        }
    }

    /**
     * ✅ 단일 탭:
     * - 같은 filePath면 기존 탭 활성화 + MRU 앞으로
     * - 아니면 새 탭 생성 + active + MRU 앞으로(=0번)
     */
    fun openOrActivate(request: ViewerOpenRequest) {
        _state.update { s ->
            val existing = s.tabs.firstOrNull { it.request.filePath == request.filePath }
            if (existing != null) {
                val reordered = moveToFront(s.tabs, existing.tabId)
                s.copy(tabs = reordered, activeTabId = existing.tabId)
            } else {
                val newTab = ViewerTabState(
                    tabId = UUID.randomUUID().toString(),
                    request = request,
                    lastPage = 0
                )
                s.copy(
                    tabs = listOf(newTab) + s.tabs,
                    activeTabId = newTab.tabId
                )
            }
        }
    }

    /**
     * ✅ 여러 탭을 한 번에 열기 (중복 제거)
     */
    fun openMany(requests: List<ViewerOpenRequest>, initialActiveIndex: Int = 0) {
        if (requests.isEmpty()) return

        _state.update { s ->
            val existingByPath = s.tabs.associateBy { it.request.filePath }
            val targetReq = requests.getOrNull(initialActiveIndex)
            val alreadyTargetTab = targetReq?.let { existingByPath[it.filePath] }

            val newOnes = requests.filter { it.filePath !in existingByPath }

            if (newOnes.isEmpty()) {
                if (alreadyTargetTab != null) {
                    val reordered = moveToFront(s.tabs, alreadyTargetTab.tabId)
                    return@update s.copy(tabs = reordered, activeTabId = alreadyTargetTab.tabId)
                }
                return@update s
            }

            val newTabs = newOnes.map { req ->
                ViewerTabState(
                    tabId = UUID.randomUUID().toString(),
                    request = req,
                    lastPage = 0
                )
            }

            val merged = newTabs + s.tabs

            val activeTabId =
                when {
                    alreadyTargetTab != null -> alreadyTargetTab.tabId
                    targetReq != null -> {
                        val inNew = newTabs.firstOrNull { it.request.filePath == targetReq.filePath }
                        inNew?.tabId ?: newTabs.first().tabId
                    }
                    else -> newTabs.first().tabId
                }

            val finalTabs = moveToFront(merged, activeTabId)
            s.copy(tabs = finalTabs, activeTabId = activeTabId)
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

    // ✅ persistence용
    fun toSnapshot(): ViewerSessionSnapshot {
        val st = _state.value
        return ViewerSessionSnapshot(
            tabs = st.tabs.map {
                ViewerSessionSnapshot.TabSnapshot(
                    tabId = it.tabId,
                    scoreId = it.request.scoreId,
                    title = it.request.title,
                    filePath = it.request.filePath,
                    lastPage = it.lastPage
                )
            },
            activeTabId = st.activeTabId
        )
    }

    fun restoreFromSnapshot(snapshot: ViewerSessionSnapshot) {
        val restoredTabs: List<ViewerTabState> = snapshot.tabs.mapNotNull { tab ->
            val req = ViewerOpenRequest(
                scoreId = tab.scoreId,
                title = tab.title,
                filePath = tab.filePath
            )
            val f = java.io.File(req.filePath)
            if (!f.exists()) return@mapNotNull null

            ViewerTabState(
                tabId = tab.tabId,
                request = req,
                lastPage = tab.lastPage
            )
        }

        val active =
            snapshot.activeTabId?.takeIf { id -> restoredTabs.any { it.tabId == id } }
                ?: restoredTabs.firstOrNull()?.tabId

        val finalTabs = if (active != null) moveToFront(restoredTabs, active) else restoredTabs

        _state.value = ViewerSessionState(
            tabs = finalTabs,
            activeTabId = active
        )
    }
}
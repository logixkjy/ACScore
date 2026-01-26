package com.kandc.acscore.session.viewer

import com.kandc.acscore.viewer.domain.ViewerOpenRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File
import java.util.UUID

data class ViewerTabState(
    val tabId: String,
    val request: ViewerOpenRequest,
    val lastPage: Int = 0,

    // ✅ 탭 표시용 제목 (단일: 곡명, 세트리스트: 세트리스트명)
    val tabTitle: String = request.title,

    // ✅ setlist 모드면 채워짐
    val setlistId: String? = null,
    val setlistRequests: List<ViewerOpenRequest>? = null,

    // ✅ "이미 열린 세트리스트 탭"을 다른 곡으로 점프시키기 위한 트리거
    val jumpToScoreId: String? = null,
    val jumpToken: Long = 0L,
)

data class ViewerSessionState(
    val tabs: List<ViewerTabState> = emptyList(), // MRU (0번이 최신)
    val activeTabId: String? = null
)

class ViewerSessionStore(
    private val onTabClosed: (filePath: String) -> Unit = {}
) {
    private val _state = MutableStateFlow(ViewerSessionState())
    val state: StateFlow<ViewerSessionState> = _state.asStateFlow()

    // -------------------------
    // MRU utils
    // -------------------------
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

    fun closeTab(tabId: String) {
        val closing = _state.value.tabs.firstOrNull { it.tabId == tabId }
        val closingFilePath = closing?.request?.filePath

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

        if (closingFilePath != null) runCatching { onTabClosed(closingFilePath) }
    }

    fun updateLastPage(tabId: String, lastPage: Int) {
        _state.update { s ->
            val updated = s.tabs.map { t ->
                if (t.tabId == tabId) t.copy(lastPage = lastPage) else t
            }
            s.copy(tabs = updated)
        }
    }

    // -------------------------
    // Single open
    // -------------------------
    fun openOrActivate(request: ViewerOpenRequest) {
        _state.update { s ->
            // ✅ 단일 탭은 setlistRequests==null 인 탭 중에서만 중복 처리
            val existing = s.tabs.firstOrNull {
                it.setlistRequests == null && it.request.filePath == request.filePath
            }
            if (existing != null) {
                val reordered = moveToFront(s.tabs, existing.tabId)
                s.copy(tabs = reordered, activeTabId = existing.tabId)
            } else {
                val newTab = ViewerTabState(
                    tabId = UUID.randomUUID().toString(),
                    request = request,
                    lastPage = 0,
                    tabTitle = request.title,
                    setlistId = null,
                    setlistRequests = null
                )
                s.copy(
                    tabs = listOf(newTab) + s.tabs,
                    activeTabId = newTab.tabId
                )
            }
        }
    }

    /**
     * ✅ 여러 개 단일 탭 열기(중복 제거)
     */
    fun openMany(requests: List<ViewerOpenRequest>, initialActiveIndex: Int = 0) {
        if (requests.isEmpty()) return

        _state.update { s ->
            val existingByPath = s.tabs
                .filter { it.setlistRequests == null }
                .associateBy { it.request.filePath }

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
                    lastPage = 0,
                    tabTitle = req.title
                )
            }

            val merged = newTabs + s.tabs

            val activeTabId =
                when {
                    alreadyTargetTab != null -> alreadyTargetTab.tabId
                    targetReq != null ->
                        newTabs.firstOrNull { it.request.filePath == targetReq.filePath }?.tabId
                            ?: newTabs.first().tabId
                    else -> newTabs.first().tabId
                }

            val finalTabs = moveToFront(merged, activeTabId)
            s.copy(tabs = finalTabs, activeTabId = activeTabId)
        }
    }

    // -------------------------
    // Setlist open/activate + jump
    // -------------------------
    /**
     * ✅ setlist 탭:
     * - setlistId가 같은 탭이 이미 있으면: 그 탭 활성화 + jumpToScoreId 갱신(점프) + setlistRequests 갱신
     * - 없으면 새 탭 생성
     */
    fun openSetlist(
        setlistId: String,
        setlistTitle: String,
        requests: List<ViewerOpenRequest>,
        initialScoreId: String? = null
    ) {
        if (requests.isEmpty()) return

        _state.update { s ->
            val existing = s.tabs.firstOrNull { it.setlistId == setlistId && it.setlistRequests != null }

            val startIndex = initialScoreId?.let { id ->
                requests.indexOfFirst { it.scoreId == id }.takeIf { it >= 0 }
            } ?: 0

            if (existing != null) {
                val updatedTabs = s.tabs.map { t ->
                    if (t.tabId != existing.tabId) t
                    else t.copy(
                        request = requests[startIndex],
                        tabTitle = setlistTitle,
                        setlistId = setlistId,
                        setlistRequests = requests,

                        // ✅ 점프 트리거
                        jumpToScoreId = requests[startIndex].scoreId,
                        jumpToken = t.jumpToken + 1,

                        // ✅ 선택 곡 "첫 페이지"로 다시 시작하고 싶으면 lastPage를 -1로
                        lastPage = -1
                    )
                }

                val reordered = moveToFront(updatedTabs, existing.tabId)
                return@update s.copy(tabs = reordered, activeTabId = existing.tabId)
            }

            val newTab = ViewerTabState(
                tabId = UUID.randomUUID().toString(),
                request = requests[startIndex],
                lastPage = -1, // ✅ initialScoreId 기준 시작
                tabTitle = setlistTitle,
                setlistId = setlistId,
                setlistRequests = requests,
                jumpToScoreId = requests[startIndex].scoreId,
                jumpToken = 1L
            )

            s.copy(
                tabs = listOf(newTab) + s.tabs,
                activeTabId = newTab.tabId
            )
        }
    }

    // -------------------------
    // Persistence (기존 스냅샷 유지)
    // - MVP: 단일 탭 복원은 OK
    // - setlist 탭은 setlistRequests를 스냅샷에 포함하지 않으므로 "단일처럼" 복원됨
    // -------------------------
    fun toSnapshot(): ViewerSessionSnapshot {
        val st = _state.value
        return ViewerSessionSnapshot(
            tabs = st.tabs.map { tab ->
                ViewerSessionSnapshot.TabSnapshot(
                    tabId = tab.tabId,
                    scoreId = tab.request.scoreId,
                    title = tab.request.title,
                    filePath = tab.request.filePath,
                    lastPage = tab.lastPage
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
            val f = File(req.filePath)
            if (!f.exists()) return@mapNotNull null

            ViewerTabState(
                tabId = tab.tabId,
                request = req,
                lastPage = tab.lastPage,
                tabTitle = req.title,
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
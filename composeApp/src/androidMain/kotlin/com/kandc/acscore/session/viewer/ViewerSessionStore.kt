package com.kandc.acscore.session.viewer

import com.kandc.acscore.viewer.domain.ViewerOpenRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File
import java.util.UUID

enum class ViewerTabMode { Single, Setlist }

data class ViewerTabState(
    val tabId: String,
    val mode: ViewerTabMode = ViewerTabMode.Single,

    // ✅ 현재 표시용 request (Single은 이 값이 실제 파일)
    // ✅ Setlist는 "현재 선택된 곡"을 의미 (title 표시/탭 내부에서 현재 곡 추적용)
    val request: ViewerOpenRequest,

    // ✅ 마지막 페이지(단일: pdf page, 세트리스트: "global page" 또는 네가 정의한 값)
    val lastPage: Int = 0,

    // ---------- Setlist 모드에서만 ----------
    val setlistId: String? = null,
    val setlistName: String? = null,
    val setlistRequests: List<ViewerOpenRequest>? = null,

    // ✅ 이미 열려있는 세트리스트 탭을 "특정 곡으로 점프"시키기 위한 트리거
    // 탭 뷰어에서 소비 후 clearPendingJump(tabId)로 지워주면 됨.
    val pendingJumpScoreId: String? = null
)

data class ViewerSessionState(
    val tabs: List<ViewerTabState> = emptyList(),
    val activeTabId: String? = null
)

// -------------------------
// ✅ Snapshot (Persistence)
// -------------------------
data class ViewerSessionSnapshot(
    val tabs: List<TabSnapshot>,
    val activeTabId: String?
) {
    data class TabSnapshot(
        val tabId: String,
        val mode: String,
        val scoreId: String,
        val title: String,
        val filePath: String,
        val lastPage: Int,
        val setlistId: String? = null,
        val setlistName: String? = null,
        val setlistRequests: List<ReqSnapshot>? = null
    )

    data class ReqSnapshot(
        val scoreId: String,
        val title: String,
        val filePath: String
    )
}

class ViewerSessionStore(
    // ✅ 탭 닫힐 때 정리 필요하면 사용 (PdfBitmapCache clear 등)
    private val onTabClosed: (filePath: String) -> Unit = {}
) {
    private val _state = MutableStateFlow(ViewerSessionState())
    val state: StateFlow<ViewerSessionState> = _state.asStateFlow()

    // ✅ MRU(최근 사용) 앞으로 이동
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

            val finalTabs = if (nextActive != null) moveToFront(remaining, nextActive) else remaining
            s.copy(tabs = finalTabs, activeTabId = nextActive)
        }

        if (closingFilePath != null) {
            runCatching { onTabClosed(closingFilePath) }
        }
    }

    fun updateLastPage(tabId: String, page: Int) {
        _state.update { s ->
            s.copy(
                tabs = s.tabs.map { t ->
                    if (t.tabId == tabId) t.copy(lastPage = page) else t
                }
            )
        }
    }

    /**
     * ✅ 단일 PDF 열기 (중복이면 활성화)
     */
    fun openOrActivate(request: ViewerOpenRequest) {
        _state.update { s ->
            val existing = s.tabs.firstOrNull {
                it.mode == ViewerTabMode.Single && it.request.filePath == request.filePath
            }
            if (existing != null) {
                val reordered = moveToFront(s.tabs, existing.tabId)
                s.copy(tabs = reordered, activeTabId = existing.tabId)
            } else {
                val tabId = UUID.randomUUID().toString()
                val newTab = ViewerTabState(
                    tabId = tabId,
                    mode = ViewerTabMode.Single,
                    request = request,
                    lastPage = 0
                )
                s.copy(
                    tabs = listOf(newTab) + s.tabs,
                    activeTabId = tabId
                )
            }
        }
    }

    /**
     * ✅ 여러 PDF를 한 번에 열기 (중복 제거)
     * (ViewerComponentImpl에서 쓰던 openMany 미해결 문제 해결용)
     */
    fun openMany(requests: List<ViewerOpenRequest>, initialActiveIndex: Int = 0) {
        if (requests.isEmpty()) return

        _state.update { s ->
            val existingByPath = s.tabs
                .filter { it.mode == ViewerTabMode.Single }
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
                    mode = ViewerTabMode.Single,
                    request = req,
                    lastPage = 0
                )
            }

            val merged = newTabs + s.tabs
            val activeTabId =
                when {
                    alreadyTargetTab != null -> alreadyTargetTab.tabId
                    targetReq != null -> newTabs.firstOrNull { it.request.filePath == targetReq.filePath }?.tabId
                        ?: newTabs.first().tabId
                    else -> newTabs.first().tabId
                }

            val finalTabs = moveToFront(merged, activeTabId)
            s.copy(tabs = finalTabs, activeTabId = activeTabId)
        }
    }

    /**
     * ✅ Setlist 이어보기 탭 열기/활성화 (핵심)
     * - tab title은 setlistName
     * - 이미 열려 있으면 그 탭 활성화 + pendingJumpScoreId 갱신(=중간 곡으로 점프)
     * - 없으면 새 setlist 탭 생성
     */
    fun openSetlistOrActivate(
        setlistId: String,
        setlistName: String,
        requests: List<ViewerOpenRequest>,
        initialScoreId: String? = null
    ) {
        if (requests.isEmpty()) return

        _state.update { s ->
            val existing = s.tabs.firstOrNull {
                it.mode == ViewerTabMode.Setlist && it.setlistId == setlistId
            }

            if (existing != null) {
                val updated = s.tabs.map { t ->
                    if (t.tabId == existing.tabId) {
                        // ✅ order가 바뀌었을 수 있으니 requests도 갱신
                        // ✅ 점프 요청도 갱신
                        t.copy(
                            setlistName = setlistName,
                            setlistRequests = requests,
                            pendingJumpScoreId = initialScoreId
                        )
                    } else t
                }
                val reordered = moveToFront(updated, existing.tabId)
                return@update s.copy(tabs = reordered, activeTabId = existing.tabId)
            }

            val startIndex = initialScoreId?.let { id ->
                requests.indexOfFirst { it.scoreId == id }.takeIf { it >= 0 }
            } ?: 0

            val tabId = UUID.randomUUID().toString()
            val newTab = ViewerTabState(
                tabId = tabId,
                mode = ViewerTabMode.Setlist,
                request = requests[startIndex],          // "현재 곡" 표시용
                lastPage = 0,                            // setlist viewer에서 계산/갱신
                setlistId = setlistId,
                setlistName = setlistName,
                setlistRequests = requests,
                pendingJumpScoreId = initialScoreId      // 새 탭도 동일하게 start 처리 가능
            )

            s.copy(
                tabs = listOf(newTab) + s.tabs,
                activeTabId = tabId
            )
        }
    }

    /**
     * ✅ Setlist 탭에서 점프 소비 후 호출(한 번만 실행되게)
     */
    fun clearPendingJump(tabId: String) {
        _state.update { s ->
            s.copy(
                tabs = s.tabs.map { t ->
                    if (t.tabId == tabId) t.copy(pendingJumpScoreId = null) else t
                }
            )
        }
    }

    // -------------------------
    // ✅ Snapshot persistence
    // -------------------------
    fun toSnapshot(): ViewerSessionSnapshot {
        val st = _state.value
        return ViewerSessionSnapshot(
            tabs = st.tabs.map { tab ->
                val mode = tab.mode.name
                ViewerSessionSnapshot.TabSnapshot(
                    tabId = tab.tabId,
                    mode = mode,
                    scoreId = tab.request.scoreId,
                    title = tab.request.title,
                    filePath = tab.request.filePath,
                    lastPage = tab.lastPage,
                    setlistId = tab.setlistId,
                    setlistName = tab.setlistName,
                    setlistRequests = tab.setlistRequests?.map {
                        ViewerSessionSnapshot.ReqSnapshot(it.scoreId, it.title, it.filePath)
                    }
                )
            },
            activeTabId = st.activeTabId
        )
    }

    fun restoreFromSnapshot(snapshot: ViewerSessionSnapshot) {
        val restoredTabs: List<ViewerTabState> = snapshot.tabs.mapNotNull { snap ->
            val mode = runCatching { ViewerTabMode.valueOf(snap.mode) }.getOrNull() ?: ViewerTabMode.Single

            val req = ViewerOpenRequest(
                scoreId = snap.scoreId,
                title = snap.title,
                filePath = snap.filePath
            )

            // ✅ 파일 없는 탭은 복원 제외(단일)
            if (mode == ViewerTabMode.Single) {
                val f = File(req.filePath)
                if (!f.exists()) return@mapNotNull null
            }

            val setlistReqs = snap.setlistRequests?.map { r ->
                ViewerOpenRequest(r.scoreId, r.title, r.filePath)
            }

            ViewerTabState(
                tabId = snap.tabId,
                mode = mode,
                request = req,
                lastPage = snap.lastPage,
                setlistId = snap.setlistId,
                setlistName = snap.setlistName,
                setlistRequests = setlistReqs,
                pendingJumpScoreId = null
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
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

    val tabTitle: String = request.title,

    val setlistId: String? = null,
    val setlistRequests: List<ViewerOpenRequest>? = null,

    // ✅ 세트리스트: "곡 선택 점프" 전용
    val jumpToScoreId: String? = null,
    val jumpTokenScore: Long = 0L,

    // ✅ 공통: "페이지 점프" 전용 (prev/next/first/last)
    val jumpToGlobalPage: Int? = null,
    val jumpTokenPage: Long = 0L,

    // ✅ 이 탭에서 목록 버튼 눌렀을 때 돌아갈 오버레이
    val returnPicker: ViewerPickerContext = ViewerPickerContext.library(),
)

data class ViewerSessionState(
    val tabs: List<ViewerTabState> = emptyList(),
    val activeTabId: String? = null,
    val lastPicker: ViewerPickerContext = ViewerPickerContext.library()
)

class ViewerSessionStore(
    private val onTabClosed: (filePath: String) -> Unit = {}
) {
    private val _state = MutableStateFlow(ViewerSessionState())
    val state: StateFlow<ViewerSessionState> = _state.asStateFlow()

    fun setLastPicker(ctx: ViewerPickerContext) {
        _state.update { it.copy(lastPicker = ctx) }
    }

    fun setActive(tabId: String) {
        _state.update { s ->
            if (s.tabs.none { it.tabId == tabId }) return@update s
            s.copy(activeTabId = tabId)
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

            s.copy(tabs = remaining, activeTabId = nextActive)
        }

        if (closingFilePath != null) runCatching { onTabClosed(closingFilePath) }
    }

    fun updateLastPage(tabId: String, lastPage: Int) {
        _state.update { s ->
            s.copy(
                tabs = s.tabs.map { t ->
                    if (t.tabId == tabId) t.copy(lastPage = lastPage.coerceAtLeast(0)) else t
                }
            )
        }
    }

    // -------------------------
    // Single open
    // -------------------------
    fun openOrActivate(request: ViewerOpenRequest) {
        _state.update { s ->
            val existing = s.tabs.firstOrNull {
                it.setlistRequests == null && it.request.filePath == request.filePath
            }

            if (existing != null) {
                s.copy(activeTabId = existing.tabId, lastPicker = existing.returnPicker)
            } else {
                val newTab = ViewerTabState(
                    tabId = UUID.randomUUID().toString(),
                    request = request,
                    lastPage = 0,
                    tabTitle = request.title,
                    setlistId = null,
                    setlistRequests = null,
                    returnPicker = ViewerPickerContext.library()
                )
                s.copy(
                    tabs = s.tabs + newTab,
                    activeTabId = newTab.tabId,
                    lastPicker = newTab.returnPicker
                )
            }
        }
    }

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

            val picker = ViewerPickerContext.setlistDetail(setlistId)
            val selectedScoreId = requests[startIndex].scoreId

            if (existing != null) {
                s.copy(
                    tabs = s.tabs.map { t ->
                        if (t.tabId != existing.tabId) t
                        else t.copy(
                            request = requests[startIndex],
                            tabTitle = setlistTitle,
                            setlistId = setlistId,
                            setlistRequests = requests,
                            returnPicker = picker,
                            jumpToScoreId = selectedScoreId,
                            jumpTokenScore = t.jumpTokenScore + 1
                        )
                    },
                    activeTabId = existing.tabId,
                    lastPicker = picker
                )
            } else {
                val newTab = ViewerTabState(
                    tabId = UUID.randomUUID().toString(),
                    request = requests[startIndex],
                    lastPage = 0,
                    tabTitle = setlistTitle,
                    setlistId = setlistId,
                    setlistRequests = requests,
                    returnPicker = picker,
                    jumpToScoreId = selectedScoreId,
                    jumpTokenScore = 1L
                )
                s.copy(
                    tabs = s.tabs + newTab,
                    activeTabId = newTab.tabId,
                    lastPicker = picker
                )
            }
        }
    }

    // ✅ prev/next/first/last 버튼용 점프
    fun requestJump(tabId: String, targetGlobalPage: Int) {
        _state.update { s ->
            s.copy(
                tabs = s.tabs.map { t ->
                    if (t.tabId != tabId) t
                    else {
                        val safe = targetGlobalPage.coerceAtLeast(0)
                        t.copy(
                            lastPage = safe,
                            jumpToGlobalPage = safe,
                            jumpTokenPage = t.jumpTokenPage + 1
                        )
                    }
                }
            )
        }
    }

    // -------------------------
    // Persistence (기존 Snapshot 유지)
    // -------------------------
    fun toSnapshot(): ViewerSessionSnapshot {
        val st = _state.value
        return ViewerSessionSnapshot(
            tabs = st.tabs.map { tab ->
                ViewerSessionSnapshot.TabSnapshot(
                    tabId = tab.tabId,
                    scoreId = tab.request.scoreId,
                    title = tab.tabTitle,
                    filePath = tab.request.filePath,
                    lastPage = tab.lastPage,
                    tabTitle = tab.tabTitle,
                    setlistId = tab.setlistId,
                    setlist = tab.setlistRequests?.map { r ->
                        ViewerSessionSnapshot.RequestSnapshot(r.scoreId, r.title, r.filePath)
                    },
                    picker = tab.returnPicker.toSnapshot()
                )
            },
            activeTabId = st.activeTabId,
            lastPicker = st.lastPicker.toSnapshot()
        )
    }

    fun restoreFromSnapshot(snapshot: ViewerSessionSnapshot) {
        val restoredTabs: List<ViewerTabState> = snapshot.tabs.mapNotNull { tab ->
            val isSetlist = !tab.setlist.isNullOrEmpty()
            val picker = tab.picker?.toPickerContext() ?: ViewerPickerContext.library()

            if (isSetlist) {
                val reqs = tab.setlist!!.map { r ->
                    ViewerOpenRequest(r.scoreId, r.title, r.filePath)
                }.filter { File(it.filePath).exists() }

                if (reqs.isEmpty()) return@mapNotNull null

                val currentReq =
                    reqs.firstOrNull { it.scoreId == tab.scoreId }
                        ?: reqs.firstOrNull { it.filePath == tab.filePath }
                        ?: reqs.first()

                ViewerTabState(
                    tabId = tab.tabId,
                    request = currentReq,
                    lastPage = tab.lastPage.coerceAtLeast(0),
                    tabTitle = tab.tabTitle ?: tab.title,
                    setlistId = tab.setlistId,
                    setlistRequests = reqs,
                    returnPicker = picker,
                    jumpToScoreId = null,
                    jumpTokenScore = 0L,
                    jumpToGlobalPage = null,
                    jumpTokenPage = 0L
                )
            } else {
                if (!File(tab.filePath).exists()) return@mapNotNull null
                ViewerTabState(
                    tabId = tab.tabId,
                    request = ViewerOpenRequest(tab.scoreId, tab.title, tab.filePath),
                    lastPage = tab.lastPage.coerceAtLeast(0),
                    tabTitle = tab.tabTitle ?: tab.title,
                    setlistId = null,
                    setlistRequests = null,
                    returnPicker = picker,
                    jumpToScoreId = null,
                    jumpTokenScore = 0L,
                    jumpToGlobalPage = null,
                    jumpTokenPage = 0L
                )
            }
        }

        val active =
            snapshot.activeTabId?.takeIf { id -> restoredTabs.any { it.tabId == id } }
                ?: restoredTabs.firstOrNull()?.tabId

        _state.value = ViewerSessionState(
            tabs = restoredTabs,
            activeTabId = active,
            lastPicker = snapshot.lastPicker?.toPickerContext()
                ?: restoredTabs.firstOrNull { it.tabId == active }?.returnPicker
                ?: ViewerPickerContext.library()
        )
    }

    private fun ViewerPickerContext.toSnapshot(): ViewerSessionSnapshot.PickerSnapshot =
        when (kind) {
            ViewerPickerContext.Kind.Library ->
                ViewerSessionSnapshot.PickerSnapshot(kind = "library", setlistId = null)

            ViewerPickerContext.Kind.SetlistDetail ->
                ViewerSessionSnapshot.PickerSnapshot(kind = "setlistDetail", setlistId = setlistId)

            // 혹시 Kind.Setlists가 있어도 Snapshot은 library/detail만 쓰므로 library로 처리
            else ->
                ViewerSessionSnapshot.PickerSnapshot(kind = "library", setlistId = null)
        }

    private fun ViewerSessionSnapshot.PickerSnapshot.toPickerContext(): ViewerPickerContext =
        when (kind) {
            "setlistDetail" -> ViewerPickerContext.setlistDetail(setlistId ?: return ViewerPickerContext.library())
            else -> ViewerPickerContext.library()
        }

    // ✅ 기존 호출부 호환용: openMany(requests, initialIndex:Int)
    fun openMany(
        requests: List<ViewerOpenRequest>,
        initialIndex: Int
    ) {
        if (requests.isEmpty()) return

        val picker = _state.value.lastPicker

        _state.update { s ->
            val tabs = s.tabs.toMutableList()
            var lastTouchedTabId: String? = s.activeTabId

            // 1) 요청들 전부 탭으로 열기(중복은 재사용)
            val openedTabIdsInOrder = mutableListOf<String>()

            requests.forEach { req ->
                val existing = tabs.firstOrNull {
                    it.setlistRequests == null && it.request.filePath == req.filePath
                }

                val tabId = if (existing != null) {
                    existing.tabId
                } else {
                    val newTab = ViewerTabState(
                        tabId = UUID.randomUUID().toString(),
                        request = req,
                        lastPage = 0,
                        tabTitle = req.title,
                        setlistId = null,
                        setlistRequests = null,
                        returnPicker = picker
                    )
                    tabs.add(newTab)
                    newTab.tabId
                }

                openedTabIdsInOrder.add(tabId)
                lastTouchedTabId = tabId
            }

            // 2) initialIndex에 해당하는 탭을 active로
            val clamped = initialIndex.coerceIn(0, openedTabIdsInOrder.lastIndex)
            val activeId = openedTabIdsInOrder.getOrNull(clamped) ?: lastTouchedTabId

            s.copy(
                tabs = tabs,
                activeTabId = activeId,
                lastPicker = picker
            )
        }
    }
}
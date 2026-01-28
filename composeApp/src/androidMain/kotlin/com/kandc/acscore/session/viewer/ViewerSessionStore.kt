package com.kandc.acscore.session.viewer

import android.util.Log
import com.kandc.acscore.viewer.domain.ViewerOpenRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File
import java.util.UUID

/**
 * ✅ 뷰어에서 "Library" 버튼을 눌렀을 때 되돌아갈 오버레이(선택 화면) 컨텍스트
 */
data class ViewerPickerContext(
    val kind: Kind,
    val setlistId: String? = null
) {
    enum class Kind { Library, SetlistDetail }

    companion object {
        fun library() = ViewerPickerContext(Kind.Library, null)
        fun setlistDetail(setlistId: String) = ViewerPickerContext(Kind.SetlistDetail, setlistId)
    }
}

data class ViewerTabState(
    val tabId: String,
    val request: ViewerOpenRequest,
    val lastPage: Int = 0,

    // ✅ 탭 표시용 제목 (단일: 곡명, 세트리스트: 세트리스트명)
    val tabTitle: String = request.title,

    // ✅ setlist 모드면 채워짐
    val setlistId: String? = null,
    val setlistRequests: List<ViewerOpenRequest>? = null,

    // ✅ 이미 열린 세트리스트 탭 점프 트리거
    val jumpToScoreId: String? = null,
    val jumpToken: Long = 0L,

    // ✅ "페이지 번호로 점프" (단일/세트리스트 공통)
    val jumpToGlobalPage: Int? = null,

    // ✅ 이 탭에서 Library 버튼을 눌렀을 때 되돌아갈 오버레이
    val returnPicker: ViewerPickerContext = ViewerPickerContext.library(),
)

data class ViewerSessionState(
    val tabs: List<ViewerTabState> = emptyList(), // MRU (0번이 최신)
    val activeTabId: String? = null,

    // ✅ 앱 시작/메인 탭 복원에 쓰는 "마지막 선택 오버레이"
    val lastPicker: ViewerPickerContext = ViewerPickerContext.library()
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

    private fun activeTabOf(s: ViewerSessionState): ViewerTabState? {
        return s.tabs.firstOrNull { it.tabId == s.activeTabId } ?: s.tabs.firstOrNull()
    }

    fun setActive(tabId: String) {
        _state.update { s ->
            if (s.tabs.none { it.tabId == tabId }) return@update s
            val reordered = moveToFront(s.tabs, tabId)
            val newState = s.copy(tabs = reordered, activeTabId = tabId)
            val active = activeTabOf(newState)
            newState.copy(lastPicker = active?.returnPicker ?: newState.lastPicker)
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

            val newState = s.copy(tabs = finalTabs, activeTabId = nextActive)

            val active = activeTabOf(newState)
            newState.copy(lastPicker = active?.returnPicker ?: newState.lastPicker)
        }

        if (closingFilePath != null) runCatching { onTabClosed(closingFilePath) }
    }

    fun updateLastPage(tabId: String, lastPage: Int) {
        // ✅ 스와이프/자연 이동 기록용 (스크롤 명령 X)
        _state.update { s ->
            val updated = s.tabs.map { t ->
                if (t.tabId == tabId) t.copy(lastPage = lastPage.coerceAtLeast(0)) else t
            }
            s.copy(tabs = updated)
        }
    }

    // -------------------------
    // Single open
    // -------------------------
    fun openOrActivate(request: ViewerOpenRequest) {
        _state.update { s ->
            val existing = s.tabs.firstOrNull { it.setlistRequests == null && it.request.filePath == request.filePath }
            if (existing != null) {
                val reordered = moveToFront(s.tabs, existing.tabId)
                val newState = s.copy(tabs = reordered, activeTabId = existing.tabId)
                newState.copy(lastPicker = existing.returnPicker)
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
                    tabs = listOf(newTab) + s.tabs,
                    activeTabId = newTab.tabId,
                    lastPicker = newTab.returnPicker
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
                    val newState = s.copy(tabs = reordered, activeTabId = alreadyTargetTab.tabId)
                    return@update newState.copy(lastPicker = alreadyTargetTab.returnPicker)
                }
                return@update s
            }

            val newTabs = newOnes.map { req ->
                ViewerTabState(
                    tabId = UUID.randomUUID().toString(),
                    request = req,
                    lastPage = 0,
                    tabTitle = req.title,
                    returnPicker = ViewerPickerContext.library()
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
            val activeTab = finalTabs.firstOrNull { it.tabId == activeTabId }
            s.copy(tabs = finalTabs, activeTabId = activeTabId, lastPicker = activeTab?.returnPicker ?: s.lastPicker)
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

            if (existing != null) {
                val updatedTabs = s.tabs.map { t ->
                    if (t.tabId != existing.tabId) t
                    else t.copy(
                        request = requests[startIndex],
                        tabTitle = setlistTitle,
                        setlistId = setlistId,
                        setlistRequests = requests,
                        returnPicker = picker,

                        // ✅ 점프 트리거
                        jumpToScoreId = requests[startIndex].scoreId,
                        jumpToken = t.jumpToken + 1,

                        // ✅ lastPage 유지
                        lastPage = t.lastPage
                    )
                }

                val reordered = moveToFront(updatedTabs, existing.tabId)
                val newState = s.copy(tabs = reordered, activeTabId = existing.tabId)
                return@update newState.copy(lastPicker = picker)
            }

            val newTab = ViewerTabState(
                tabId = UUID.randomUUID().toString(),
                request = requests[startIndex],
                lastPage = 0,
                tabTitle = setlistTitle,
                setlistId = setlistId,
                setlistRequests = requests,
                returnPicker = picker,
                jumpToScoreId = requests[startIndex].scoreId,
                jumpToken = 1L
            )

            val merged = listOf(newTab) + s.tabs
            s.copy(tabs = merged, activeTabId = newTab.tabId, lastPicker = picker)
        }
    }

    /**
     * ✅ UI 버튼으로 페이지 이동할 때는 이걸 사용
     * - lastPage 업데이트 + Pager가 스크롤하도록 jump 토큰 발행
     */
    fun requestJump(tabId: String, targetGlobalPage: Int) {
        _state.update { s ->
            val updated = s.tabs.map { t ->
                if (t.tabId != tabId) t
                else {
                    val safe = targetGlobalPage.coerceAtLeast(0)
                    t.copy(
                        lastPage = safe,
                        jumpToGlobalPage = safe,
                        jumpToken = t.jumpToken + 1
                    )
                }
            }
            s.copy(tabs = updated)
        }
    }

    // -------------------------
    // Persistence
    // -------------------------
    fun toSnapshot(): ViewerSessionSnapshot {
        val st = _state.value

        return ViewerSessionSnapshot(
            tabs = st.tabs.map { tab ->
                ViewerSessionSnapshot.TabSnapshot(
                    tabId = tab.tabId,
                    scoreId = tab.request.scoreId,
                    title = tab.tabTitle, // ✅ 탭 제목(세트리스트명) 유지
                    filePath = tab.request.filePath,
                    lastPage = tab.lastPage,
                    tabTitle = tab.tabTitle,
                    setlistId = tab.setlistId,
                    setlist = tab.setlistRequests?.map { r ->
                        ViewerSessionSnapshot.RequestSnapshot(
                            scoreId = r.scoreId,
                            title = r.title,
                            filePath = r.filePath
                        )
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

            if (isSetlist) {
                val requests = tab.setlist!!.map { r ->
                    ViewerOpenRequest(scoreId = r.scoreId, title = r.title, filePath = r.filePath)
                }.filter { File(it.filePath).exists() }

                if (requests.isEmpty()) return@mapNotNull null

                val currentReq =
                    requests.firstOrNull { it.scoreId == tab.scoreId }
                        ?: requests.firstOrNull { it.filePath == tab.filePath }
                        ?: requests.first()

                val safeLastPage = tab.lastPage.coerceAtLeast(0)

                ViewerTabState(
                    tabId = tab.tabId,
                    request = currentReq,
                    lastPage = safeLastPage,
                    tabTitle = tab.tabTitle ?: tab.title,
                    setlistId = tab.setlistId,
                    setlistRequests = requests,
                    returnPicker = tab.picker?.toPickerContext()
                        ?: tab.setlistId?.let { ViewerPickerContext.setlistDetail(it) }
                        ?: ViewerPickerContext.library(),
                    jumpToScoreId = null,
                    jumpToken = 0L
                )
            } else {
                val safeLastPage = tab.lastPage.coerceAtLeast(0)

                ViewerTabState(
                    tabId = tab.tabId,
                    request = ViewerOpenRequest(tab.scoreId, tab.title, tab.filePath),
                    lastPage = safeLastPage,
                    tabTitle = tab.tabTitle ?: tab.title,
                    setlistId = null,
                    setlistRequests = null,
                    returnPicker = tab.picker?.toPickerContext() ?: ViewerPickerContext.library(),
                    jumpToScoreId = null,
                    jumpToken = 0L
                )
            }
        }

        val active =
            snapshot.activeTabId?.takeIf { id -> restoredTabs.any { it.tabId == id } }
                ?: restoredTabs.firstOrNull()?.tabId

        val finalTabs = if (active != null) moveToFront(restoredTabs, active) else restoredTabs

        val activeTab = finalTabs.firstOrNull { it.tabId == active } ?: finalTabs.firstOrNull()

        _state.value = ViewerSessionState(
            tabs = finalTabs,
            activeTabId = active,
            lastPicker = snapshot.lastPicker?.toPickerContext()
                ?: activeTab?.returnPicker
                ?: ViewerPickerContext.library()
        )
    }

    // -------------------------
    // Snapshot mapping
    // -------------------------
    private fun ViewerPickerContext.toSnapshot(): ViewerSessionSnapshot.PickerSnapshot {
        return when (kind) {
            ViewerPickerContext.Kind.Library ->
                ViewerSessionSnapshot.PickerSnapshot(kind = "library", setlistId = null)

            ViewerPickerContext.Kind.SetlistDetail ->
                ViewerSessionSnapshot.PickerSnapshot(kind = "setlistDetail", setlistId = setlistId)
        }
    }

    private fun ViewerSessionSnapshot.PickerSnapshot.toPickerContext(): ViewerPickerContext {
        return when (kind) {
            "setlistDetail" -> {
                val id = setlistId ?: return ViewerPickerContext.library()
                ViewerPickerContext.setlistDetail(id)
            }
            else -> ViewerPickerContext.library()
        }
    }
}
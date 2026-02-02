package com.kandc.acscore.session.viewer

import android.util.Log
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

    // ✅ 이미 열린 세트리스트 탭 점프 트리거
    val jumpToScoreId: String? = null,
    val jumpToken: Long = 0L,

    // ✅ "페이지 번호로 점프" (단일/세트리스트 공통)
    val jumpToGlobalPage: Int? = null,

    // ✅ (참고용) 이 탭이 "처음 열릴 때" 어떤 picker에서 왔는지
    // 규약 #8 때문에 실제 이동은 state.lastPicker만 사용하도록 추천
    val returnPicker: ViewerPickerContext = ViewerPickerContext.library(),
)

data class ViewerSessionState(
    // ✅ 규약 #7: 탭 순서는 고정(생성 순서 유지), 활성화한다고 재정렬하지 않는다.
    val tabs: List<ViewerTabState> = emptyList(),
    val activeTabId: String? = null,

    // ✅ 규약 #8: "목록 버튼"은 항상 이 lastPicker로 이동
    // ✅ 이 값은 setLastPicker()로만 업데이트되도록 강제
    val lastPicker: ViewerPickerContext = ViewerPickerContext.library()
)

class ViewerSessionStore(
    private val onTabClosed: (filePath: String) -> Unit = {}
) {
    private val _state = MutableStateFlow(ViewerSessionState())
    val state: StateFlow<ViewerSessionState> = _state.asStateFlow()

    private fun activeTabOf(s: ViewerSessionState): ViewerTabState? {
        return s.tabs.firstOrNull { it.tabId == s.activeTabId } ?: s.tabs.firstOrNull()
    }

    /**
     * ✅ 규약 #8: lastPicker는 "목록 화면에서 사용자가 실제로 보고 있는 화면"이 바뀔 때만 갱신
     * - 예: Library(단일 목록) / SetlistDetail(특정 세트 상세)
     */
    fun setLastPicker(ctx: ViewerPickerContext) {
        _state.update { s -> s.copy(lastPicker = ctx) }
    }

    /**
     * ✅ 규약 #7: 탭 활성화는 순서 변경 없이 activeTabId만 변경
     */
    fun setActive(tabId: String) {
        _state.update { s ->
            if (s.tabs.none { it.tabId == tabId }) return@update s
            s.copy(activeTabId = tabId)
        }
    }

    /**
     * ✅ 규약 #7: 닫아도 탭 순서 재정렬 X
     * ✅ 규약 #8: closeTab이 lastPicker를 건드리지 않도록 함
     */
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
            val updated = s.tabs.map { t ->
                if (t.tabId == tabId) t.copy(lastPage = lastPage.coerceAtLeast(0)) else t
            }
            s.copy(tabs = updated)
        }
    }

    /**
     * ✅ 버튼/외부 이벤트로 페이지 점프 요청
     * - 탭의 lastPage를 즉시 바꾸지 말고(렌더 완료 후 onGlobalPageChanged에서 저장)
     * - 점프 트리거만 세팅
     */
    fun requestJump(tabId: String, targetGlobalPage: Int) {
        val token = System.currentTimeMillis()
        _state.update { s ->
            val updated = s.tabs.map { t ->
                if (t.tabId == tabId) {
                    t.copy(
                        jumpToGlobalPage = targetGlobalPage.coerceAtLeast(0),
                        // 단일 jumpToken 필드를 "global 점프 토큰"으로도 활용
                        jumpToken = token
                    )
                } else t
            }
            s.copy(tabs = updated)
        }
    }

    // -------------------------
    // Single open
    // -------------------------

    /**
     * ✅ sourcePicker를 넘긴 경우에만 lastPicker/returnPicker 갱신
     * - Picker 화면에서 열 때: sourcePicker 넘김(= lastPicker 갱신)
     * - Viewer 내부에서 열 때: null (lastPicker 유지)
     */
    fun openOrActivate(
        request: ViewerOpenRequest,
        sourcePicker: ViewerPickerContext? = null
    ) {
        _state.update { s ->
            val existing = s.tabs.firstOrNull {
                it.setlistRequests == null && it.request.filePath == request.filePath
            }
            if (existing != null) {
                // ✅ 순서 재정렬 X, activeTabId만 변경
                val newTabs = if (sourcePicker != null) {
                    s.tabs.map { t ->
                        if (t.tabId == existing.tabId) t.copy(returnPicker = sourcePicker) else t
                    }
                } else {
                    s.tabs
                }

                s.copy(
                    tabs = newTabs,
                    activeTabId = existing.tabId,
                    lastPicker = sourcePicker ?: s.lastPicker
                )
            } else {
                val rp = sourcePicker ?: ViewerPickerContext.library()
                val newTab = ViewerTabState(
                    tabId = UUID.randomUUID().toString(),
                    request = request,
                    lastPage = 0,
                    tabTitle = request.title,
                    setlistId = null,
                    setlistRequests = null,
                    returnPicker = rp
                )

                s.copy(
                    tabs = s.tabs + newTab, // ✅ 생성 순서 유지(뒤에 추가)
                    activeTabId = newTab.tabId,
                    lastPicker = sourcePicker ?: s.lastPicker
                )
            }
        }
    }

    /**
     * ✅ 여러 개 단일 탭 열기(중복 제거)
     * ✅ lastPicker는 sourcePicker가 있을 때만 갱신
     * ✅ 순서 재정렬 X
     */
    fun openMany(
        requests: List<ViewerOpenRequest>,
        initialActiveIndex: Int = 0,
        sourcePicker: ViewerPickerContext? = null
    ) {
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
                    val newTabs = if (sourcePicker != null) {
                        s.tabs.map { t ->
                            if (t.tabId == alreadyTargetTab.tabId) t.copy(returnPicker = sourcePicker) else t
                        }
                    } else s.tabs

                    return@update s.copy(
                        tabs = newTabs,
                        activeTabId = alreadyTargetTab.tabId,
                        lastPicker = sourcePicker ?: s.lastPicker
                    )
                }
                return@update s
            }

            val rp = sourcePicker ?: ViewerPickerContext.library()
            val newTabs = newOnes.map { req ->
                ViewerTabState(
                    tabId = UUID.randomUUID().toString(),
                    request = req,
                    lastPage = 0,
                    tabTitle = req.title,
                    returnPicker = rp
                )
            }

            val merged = s.tabs + newTabs

            val activeTabId =
                when {
                    alreadyTargetTab != null -> alreadyTargetTab.tabId
                    targetReq != null -> merged.lastOrNull { it.setlistRequests == null && it.request.filePath == targetReq.filePath }?.tabId
                        ?: newTabs.first().tabId
                    else -> newTabs.first().tabId
                }

            s.copy(
                tabs = merged,
                activeTabId = activeTabId,
                lastPicker = sourcePicker ?: s.lastPicker
            )
        }
    }

    /**
     * ✅ 세트리스트 탭 열기
     * ✅ sourcePicker 있을 때만 lastPicker 갱신
     * ✅ 순서 재정렬 X
     */
    fun openSetlist(
        setlistId: String,
        setlistTitle: String,
        requests: List<ViewerOpenRequest>,
        initialScoreId: String? = null,
        sourcePicker: ViewerPickerContext? = null
    ) {
        if (requests.isEmpty()) return

        _state.update { s ->
            // 이미 열린 동일 setlist 탭이 있으면 활성만
            val existing = s.tabs.firstOrNull { it.setlistId == setlistId && it.setlistRequests != null }
            if (existing != null) {
                val newTabs = if (sourcePicker != null) {
                    s.tabs.map { t ->
                        if (t.tabId == existing.tabId) t.copy(returnPicker = sourcePicker) else t
                    }
                } else s.tabs

                return@update s.copy(
                    tabs = newTabs,
                    activeTabId = existing.tabId,
                    lastPicker = sourcePicker ?: s.lastPicker
                )
            }

            val startIndex =
                initialScoreId?.let { id -> requests.indexOfFirst { it.scoreId == id } }?.takeIf { it >= 0 }
                    ?: 0

            val rp = sourcePicker ?: ViewerPickerContext.library()
            val newTab = ViewerTabState(
                tabId = UUID.randomUUID().toString(),
                request = requests[startIndex],
                lastPage = 0,
                tabTitle = setlistTitle,
                setlistId = setlistId,
                setlistRequests = requests,
                jumpToScoreId = requests[startIndex].scoreId,
                jumpToken = 1L,
                returnPicker = rp
            )

            s.copy(
                tabs = s.tabs + newTab,
                activeTabId = newTab.tabId,
                lastPicker = sourcePicker ?: s.lastPicker
            )
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
                    title = tab.tabTitle,
                    filePath = tab.request.filePath,
                    lastPage = tab.lastPage,
                    setlistId = tab.setlistId,
                    setlist = tab.setlistRequests?.map { r ->
                        ViewerSessionSnapshot.RequestSnapshot(
                            scoreId = r.scoreId,
                            title = r.title,
                            filePath = r.filePath
                        )
                    }
                )
            },
            activeTabId = st.activeTabId
        )
    }

    fun restoreFromSnapshot(snapshot: ViewerSessionSnapshot) {
        val restoredTabs: List<ViewerTabState> = snapshot.tabs.mapNotNull { tab ->
            val isSetlist = !tab.setlist.isNullOrEmpty()

            if (isSetlist) {
                val reqs = tab.setlist!!.map { r ->
                    ViewerOpenRequest(scoreId = r.scoreId, title = r.title, filePath = r.filePath)
                }.filter { File(it.filePath).exists() }

                if (reqs.isEmpty()) return@mapNotNull null

                val currentReq =
                    reqs.firstOrNull { it.scoreId == tab.scoreId }
                        ?: reqs.firstOrNull { it.filePath == tab.filePath }
                        ?: reqs.first()

                ViewerTabState(
                    tabId = tab.tabId ?: UUID.randomUUID().toString(),
                    request = currentReq,
                    lastPage = tab.lastPage.coerceAtLeast(0),
                    tabTitle = tab.title ?: currentReq.title,
                    setlistId = tab.setlistId,
                    setlistRequests = reqs,
                    jumpToScoreId = null,
                    jumpToken = 0L,
                    returnPicker = ViewerPickerContext.library()
                )
            } else {
                ViewerTabState(
                    tabId = tab.tabId ?: UUID.randomUUID().toString(),
                    request = ViewerOpenRequest(tab.scoreId, tab.title, tab.filePath),
                    lastPage = tab.lastPage.coerceAtLeast(0),
                    tabTitle = tab.title,
                    setlistId = null,
                    setlistRequests = null,
                    jumpToScoreId = null,
                    jumpToken = 0L,
                    returnPicker = ViewerPickerContext.library()
                )
            }
        }

        val active =
            snapshot.activeTabId?.takeIf { id -> restoredTabs.any { it.tabId == id } }
                ?: restoredTabs.firstOrNull()?.tabId

        // ✅ 순서 유지(복원된 순서 그대로)
        _state.value = ViewerSessionState(
            tabs = restoredTabs,
            activeTabId = active,
            // lastPicker는 복원 데이터에 없으니 기본값 유지
            lastPicker = ViewerPickerContext.library()
        )
    }
}
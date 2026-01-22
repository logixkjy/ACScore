package com.kandc.acscore.root

import com.arkivanov.decompose.ComponentContext
import com.kandc.acscore.viewer.domain.ViewerOpenRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID

class ViewerComponentImpl(
    componentContext: ComponentContext,
    initialRequests: List<ViewerOpenRequest>,
    initialActiveIndex: Int,
    private val onBack: () -> Unit
) : ViewerComponent, ComponentContext by componentContext {

    private val _tabs = MutableStateFlow(
        initialRequests.map { req ->
            ViewerTabUi(
                tabId = UUID.randomUUID().toString(),
                title = req.title,
                request = req
            )
        }
    )
    override val tabs: StateFlow<List<ViewerTabUi>> = _tabs

    private val _activeTabId = MutableStateFlow(
        _tabs.value.getOrNull(initialActiveIndex)?.tabId
            ?: _tabs.value.firstOrNull()?.tabId
    )
    override val activeTabId: StateFlow<String?> = _activeTabId

    override fun selectTab(tabId: String) {
        if (_tabs.value.any { it.tabId == tabId }) _activeTabId.value = tabId
    }

    override fun closeTab(tabId: String) {
        _tabs.update { list -> list.filterNot { it.tabId == tabId } }

        val remaining = _tabs.value
        val active = _activeTabId.value

        if (remaining.isEmpty()) {
            onBack()
            return
        }

        if (active == tabId) {
            _activeTabId.value = remaining.first().tabId
        }
    }

    override fun addTab(request: ViewerOpenRequest, makeActive: Boolean) {
        // ✅ 같은 파일을 또 열면 “기존 탭으로 이동” (중복 탭 방지)
        val existing = _tabs.value.firstOrNull { it.request.filePath == request.filePath }
        if (existing != null) {
            if (makeActive) _activeTabId.value = existing.tabId
            return
        }

        val newTab = ViewerTabUi(
            tabId = UUID.randomUUID().toString(),
            title = request.title,
            request = request
        )

        _tabs.update { it + newTab }
        if (makeActive) _activeTabId.value = newTab.tabId
    }

    override fun addTabs(requests: List<ViewerOpenRequest>, initialActiveIndex: Int) {
        if (requests.isEmpty()) return

        // ✅ 중복 제거 (filePath 기준)
        val existingPaths = _tabs.value.map { it.request.filePath }.toSet()
        val newOnes = requests.filter { it.filePath !in existingPaths }

        if (newOnes.isEmpty()) {
            // 전부 중복이면 initialActiveIndex에 해당하는 요청으로 이동 시도
            val target = requests.getOrNull(initialActiveIndex)
            if (target != null) {
                val tab = _tabs.value.firstOrNull { it.request.filePath == target.filePath }
                if (tab != null) _activeTabId.value = tab.tabId
            }
            return
        }

        val newTabs = newOnes.map { req ->
            ViewerTabUi(
                tabId = UUID.randomUUID().toString(),
                title = req.title,
                request = req
            )
        }

        val beforeSize = _tabs.value.size
        _tabs.update { it + newTabs }

        val desiredIndexInNew = initialActiveIndex.coerceIn(0, newTabs.lastIndex)
        val active = newTabs.getOrNull(desiredIndexInNew) ?: newTabs.first()
        _activeTabId.value = active.tabId
    }

    override fun onBack() = onBack.invoke()
}
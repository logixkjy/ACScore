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
        if (_tabs.value.any { it.tabId == tabId }) {
            _activeTabId.value = tabId
        }
    }

    override fun closeTab(tabId: String) {
        _tabs.update { list -> list.filterNot { it.tabId == tabId } }

        val remaining = _tabs.value
        val active = _activeTabId.value

        if (remaining.isEmpty()) {
            // 탭이 다 닫히면 Viewer 종료
            onBack()
            return
        }

        if (active == tabId) {
            // 닫은 탭이 active면 첫 탭으로
            _activeTabId.value = remaining.first().tabId
        }
    }

    override fun onBack() = onBack.invoke()
}
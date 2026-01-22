package com.kandc.acscore.root

import com.arkivanov.decompose.ComponentContext
import com.kandc.acscore.viewer.domain.ViewerOpenRequest
import com.kandc.acscore.viewer.session.ViewerSessionStore

class ViewerComponentImpl(
    componentContext: ComponentContext,
    override val sessionStore: ViewerSessionStore, // ✅ 여기만 override
    private val onBack: () -> Unit
) : ViewerComponent, ComponentContext by componentContext {

    override fun selectTab(tabId: String) {
        sessionStore.setActive(tabId)
    }

    override fun closeTab(tabId: String) {
        sessionStore.closeTab(tabId)
        // 탭이 0개가 되면 뷰어를 닫고 뒤로
        if (sessionStore.state.value.tabs.isEmpty()) {
            onBack()
        }
    }

    override fun addTab(request: ViewerOpenRequest, makeActive: Boolean) {
        // store에서 "중복이면 기존 탭 활성화" 정책을 이미 갖고 있거나(openOrActivate) 갖게 하면 됨
        sessionStore.openOrActivate(request)
        // openOrActivate가 활성화까지 처리하므로 makeActive 파라미터가 필요 없으면 제거해도 됨
        // (지금 정책상 선택 시 활성화가 맞아서 그냥 무시해도 OK)
    }

    override fun addTabs(requests: List<ViewerOpenRequest>, initialActiveIndex: Int) {
        sessionStore.openMany(requests, initialActiveIndex)
    }

    override fun onBack() = onBack.invoke()
}
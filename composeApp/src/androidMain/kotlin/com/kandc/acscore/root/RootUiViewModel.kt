package com.kandc.acscore.root

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class RootUiViewModel : ViewModel() {

    // ✅ 앱 시작 시 자동 표시
    private val _isLibraryOverlayOpen = MutableStateFlow(true)
    val isLibraryOverlayOpen: StateFlow<Boolean> = _isLibraryOverlayOpen

    // ✅ 유저가 한 번이라도 닫았으면(또는 악보 선택으로 닫히면) 이후 자동 재오픈 금지
    private var dismissedOnce: Boolean = false

    fun openLibraryOverlay(userInitiated: Boolean = true) {
        // 유저가 버튼 눌러 여는 건 항상 허용
        _isLibraryOverlayOpen.value = true
    }

    fun closeLibraryOverlay(userInitiated: Boolean = true) {
        _isLibraryOverlayOpen.value = false
        if (userInitiated) dismissedOnce = true
    }

    // ✅ 악보 선택으로 닫힌 것도 “유저가 닫은 것”으로 간주
    fun onScoreSelectedAndCloseOverlay() {
        _isLibraryOverlayOpen.value = false
        dismissedOnce = true
    }

    // (선택) 앱 시작 직후에만 자동 표시를 보장하고 싶으면 호출
    fun ensureAutoShownOnce() {
        if (!dismissedOnce) _isLibraryOverlayOpen.value = true
    }
}
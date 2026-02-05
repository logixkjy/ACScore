package com.kandc.acscore.root

import android.net.Uri
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class RootUiViewModel : ViewModel() {

    // ✅ 앱 시작 시 자동 표시
    private val _isLibraryOverlayOpen = MutableStateFlow(true)
    val isLibraryOverlayOpen: StateFlow<Boolean> = _isLibraryOverlayOpen

    // ✅ 유저가 한 번이라도 닫았으면(또는 악보 선택으로 닫히면) 이후 자동 재오픈 금지
    private var dismissedOnce: Boolean = false

    // ✅ (추가) 외부에서 들어온 .acset 파일 Uri (한 번 처리하고 비움)
    private val _pendingAcsetUri = MutableStateFlow<Uri?>(null)
    val pendingAcsetUri: StateFlow<Uri?> = _pendingAcsetUri

    fun onIncomingAcset(uri: Uri) {
        _pendingAcsetUri.value = uri
    }

    fun consumePendingAcset() {
        _pendingAcsetUri.value = null
    }

    fun openLibraryOverlay(userInitiated: Boolean = true) {
        _isLibraryOverlayOpen.value = true
    }

    fun closeLibraryOverlay(userInitiated: Boolean = true) {
        _isLibraryOverlayOpen.value = false
        if (userInitiated) dismissedOnce = true
    }

    fun onScoreSelectedAndCloseOverlay() {
        _isLibraryOverlayOpen.value = false
        dismissedOnce = true
    }

    fun ensureAutoShownOnce() {
        if (!dismissedOnce) _isLibraryOverlayOpen.value = true
    }
}
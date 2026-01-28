package com.kandc.acscore.session.viewer

/**
 * 뷰어 세션(탭/활성탭/복귀 오버레이 컨텍스트) 스냅샷
 * - SharedPreferences에 JSON으로 저장/복원 (androidMain)
 */
data class ViewerSessionSnapshot(
    val tabs: List<TabSnapshot>,
    val activeTabId: String?,

    // ✅ 마지막으로 사용자가 보고 있던 "선택(오버레이) 화면" 컨텍스트
    // - 앱 시작 시 MainTabs 초기 탭/상세 복원에 사용
    val lastPicker: PickerSnapshot? = null
) {
    data class TabSnapshot(
        val tabId: String,
        val scoreId: String,
        val title: String,
        val filePath: String,
        val lastPage: Int,

        // ✅ 탭 표시용 제목 (단일: 곡명, 세트리스트: 세트리스트명)
        val tabTitle: String? = null,

        // ✅ setlist 이어보기 탭이면 리스트가 존재
        val setlist: List<RequestSnapshot>? = null,

        // ✅ setlist 식별 (있으면 "이미 열린 세트리스트 탭" 복원/매칭에 사용)
        val setlistId: String? = null,

        // ✅ 이 탭에서 Library 버튼을 눌렀을 때 되돌아갈 Picker 컨텍스트
        val picker: PickerSnapshot? = null
    )

    data class RequestSnapshot(
        val scoreId: String,
        val title: String,
        val filePath: String
    )

    data class PickerSnapshot(
        val kind: String,            // "library" | "setlistDetail"
        val setlistId: String? = null
    )
}
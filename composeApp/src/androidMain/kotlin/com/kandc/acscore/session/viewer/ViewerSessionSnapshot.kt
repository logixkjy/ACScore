package com.kandc.acscore.session.viewer

data class ViewerSessionSnapshot(
    val tabs: List<TabSnapshot>,
    val activeTabId: String?
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
        val setlistId: String? = null
    )

    data class RequestSnapshot(
        val scoreId: String,
        val title: String,
        val filePath: String
    )
}
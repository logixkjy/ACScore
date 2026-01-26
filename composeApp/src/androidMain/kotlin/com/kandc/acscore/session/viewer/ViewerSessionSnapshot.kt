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

        // ✅ setlist 이어보기 탭이면 리스트가 존재
        val setlist: List<RequestSnapshot>? = null
    )

    data class RequestSnapshot(
        val scoreId: String,
        val title: String,
        val filePath: String
    )
}
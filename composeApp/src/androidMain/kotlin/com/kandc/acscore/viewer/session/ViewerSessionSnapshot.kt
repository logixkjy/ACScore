package com.kandc.acscore.viewer.session

data class ViewerSessionSnapshot(
    val tabs: List<TabSnapshot>,
    val activeTabId: String?
) {
    data class TabSnapshot(
        val tabId: String,
        val scoreId: String,
        val title: String,
        val filePath: String,
        val lastPage: Int = 0
    )
}
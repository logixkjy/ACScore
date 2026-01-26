package com.kandc.acscore.viewer.domain

/**
 * 세트리스트에서 악보를 열었을 때,
 * Viewer에서 "이전/다음"으로 이어서 열기 위한 컨텍스트.
 */
data class ViewerPlayContext(
    val setlistId: String,
    val orderedScoreIds: List<String>,
    val currentScoreId: String,
) {
    fun currentIndex(): Int = orderedScoreIds.indexOf(currentScoreId)

    fun prevId(): String? {
        val idx = currentIndex()
        return if (idx > 0) orderedScoreIds[idx - 1] else null
    }

    fun nextId(): String? {
        val idx = currentIndex()
        return if (idx != -1 && idx < orderedScoreIds.lastIndex) orderedScoreIds[idx + 1] else null
    }
}
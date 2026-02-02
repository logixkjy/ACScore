package com.kandc.acscore.session.viewer

/**
 * ✅ "목록(오버레이)" 상태를 복원하기 위한 컨텍스트
 *
 * - Library: 단일 악보 목록
 * - Setlists: 세트리스트 목록
 * - SetlistDetail: 특정 세트리스트 상세(곡 선택 화면)
 */
data class ViewerPickerContext(
    val kind: Kind,
    val setlistId: String? = null
) {
    enum class Kind { Library, Setlists, SetlistDetail }

    companion object {
        fun library() = ViewerPickerContext(Kind.Library, null)
        fun setlists() = ViewerPickerContext(Kind.Setlists, null)
        fun setlistDetail(setlistId: String) = ViewerPickerContext(Kind.SetlistDetail, setlistId)
    }
}
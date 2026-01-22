package com.kandc.acscore.viewer.domain

/**
 * 플랫폼 의존 없는 Viewer 입력 모델.
 * - filePath는 플랫폼에서 관리하는 "앱 내부 저장소 경로 문자열"만 전달
 */
data class ViewerOpenRequest(
    val scoreId: String,
    val title: String,
    val filePath: String
)
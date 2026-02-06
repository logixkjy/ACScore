package com.kandc.acscore.shared.share

import kotlinx.serialization.Serializable

/**
 * .acset (암호화된 zip) 내부에 들어있는 manifest.json
 *
 * zip 구조 예시:
 * - manifest.json
 * - scores/<contentHash>.pdf
 */
@Serializable
data class AcsetManifest(
    val version: Int = 1,
    val setlistTitle: String? = null,

    // 세트리스트 순서
    val items: List<Item> = emptyList(),
) {
    @Serializable
    data class Item(
        val title: String,

        // ✅ 구버전/외부 파일 호환: 없으면 decode 되게 기본값 제공
        // (importer에서 빈 값이면 실제 PDF bytes로 hash 계산해서 채우면 됨)
        val contentHash: String = "",

        val fileName: String? = null, // 원본 파일명(옵션)
    )
}
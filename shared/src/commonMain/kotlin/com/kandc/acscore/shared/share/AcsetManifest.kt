package com.kandc.acscore.shared.share

import kotlinx.serialization.SerialName
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
        val contentHash: String, // ✅ dedup key
        val fileName: String? = null, // 원본 파일명(옵션)
    )
}
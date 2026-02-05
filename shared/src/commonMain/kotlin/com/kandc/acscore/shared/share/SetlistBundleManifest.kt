package com.kandc.acscore.shared.share

import kotlinx.serialization.Serializable

@Serializable
data class SetlistBundleManifest(
    val version: Int,
    val title: String,
    val createdAtEpochMs: Long,
    val items: List<SetlistBundleItem>,
    val files: List<SetlistBundleFile>
)

@Serializable
data class SetlistBundleItem(
    val order: Int,
    val title: String,
    val scoreId: String,
    val fileHash: String,
    val originalFileName: String
)

@Serializable
data class SetlistBundleFile(
    val fileHash: String,
    val zipPath: String,
    val size: Long
)
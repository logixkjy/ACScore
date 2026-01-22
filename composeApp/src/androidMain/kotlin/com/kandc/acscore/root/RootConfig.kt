package com.kandc.acscore.root

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
sealed interface RootConfig : Parcelable {

    @Parcelize
    data object Library : RootConfig

    /**
     * Viewer 진입 시 초기 탭들.
     * - MVP: list size = 1
     * - Setlist: 여러 개 전달
     */
    @Parcelize
    data class Viewer(
        val items: List<ViewerItem>,
        val initialActiveIndex: Int = 0
    ) : RootConfig {
        @Parcelize
        data class ViewerItem(
            val scoreId: String,
            val title: String,
            val filePath: String
        ) : Parcelable
    }
}
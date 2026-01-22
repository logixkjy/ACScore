package com.kandc.acscore.root

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
sealed interface RootConfig : Parcelable {

    @Parcelize
    data object Library : RootConfig

    @Parcelize
    data class Viewer(
        val scoreId: String,
        val title: String,
        val filePath: String
    ) : RootConfig

    // T5부터 추가 예정
    // @Serializable data object Setlist : RootConfig
}
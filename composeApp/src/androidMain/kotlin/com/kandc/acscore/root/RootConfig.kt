package com.kandc.acscore.root

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
sealed interface RootConfig : Parcelable {
    @Parcelize
    data object Main : RootConfig
}
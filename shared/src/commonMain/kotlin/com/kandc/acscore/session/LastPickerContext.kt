package com.kandc.acscore.session

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface LastPickerContext {
    @Serializable @SerialName("library")
    data object Library : LastPickerContext

    @Serializable @SerialName("setlistDetail")
    data class SetlistDetail(val setlistId: String) : LastPickerContext
}
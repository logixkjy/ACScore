package com.kandc.acscore.share

import android.net.Uri
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object IncomingAcsetBus {
    private val _events = MutableSharedFlow<Uri>(extraBufferCapacity = 8)
    val events = _events.asSharedFlow()

    fun emit(uri: Uri) {
        _events.tryEmit(uri)
    }
}
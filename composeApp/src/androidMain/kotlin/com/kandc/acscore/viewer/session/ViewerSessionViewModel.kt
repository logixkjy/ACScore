package com.kandc.acscore.viewer.session

import androidx.lifecycle.ViewModel
import com.kandc.acscore.viewer.domain.ViewerOpenRequest

class ViewerSessionViewModel : ViewModel() {

    val store = ViewerSessionStore()

    fun open(request: ViewerOpenRequest) {
        store.openOrActivate(request)
    }

    fun openMany(requests: List<ViewerOpenRequest>, activeIndex: Int = 0) {
        store.openMany(requests, activeIndex)
    }
}
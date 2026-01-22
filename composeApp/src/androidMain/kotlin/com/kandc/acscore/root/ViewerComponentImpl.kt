package com.kandc.acscore.root

import com.arkivanov.decompose.ComponentContext
import com.kandc.acscore.viewer.domain.ViewerOpenRequest

class ViewerComponentImpl(
    componentContext: ComponentContext,
    override val request: ViewerOpenRequest,
    private val onBack: () -> Unit
) : ViewerComponent, ComponentContext by componentContext {

    override fun onBack() = onBack.invoke()
}
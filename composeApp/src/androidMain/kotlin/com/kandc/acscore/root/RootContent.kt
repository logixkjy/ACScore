package com.kandc.acscore.root

import androidx.compose.runtime.Composable
import com.arkivanov.decompose.extensions.compose.jetpack.stack.Children
import com.arkivanov.decompose.extensions.compose.jetpack.stack.animation.stackAnimation

@Composable
fun RootContent(component: RootComponent) {
    Children(
        stack = component.stack,
        animation = stackAnimation()
    ) { child ->
        when (child.instance) {
            is RootComponent.Child.Main -> {
                MainHostScreen(component = component)
            }
        }
    }
}
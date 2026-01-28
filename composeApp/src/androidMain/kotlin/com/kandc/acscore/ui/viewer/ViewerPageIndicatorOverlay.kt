package com.kandc.acscore.ui.viewer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay

@Composable
fun ViewerPageIndicatorOverlay(
    currentPage: Int,
    pageCount: Int?,
    modifier: Modifier = Modifier,
    visibleDurationMs: Long = 700L
) {
    if (pageCount == null || pageCount <= 0) return

    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(currentPage) {
        visible = true
        delay(visibleDurationMs)
        visible = false
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .zIndex(998f),
        contentAlignment = Alignment.TopCenter
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Text(
                text = "${currentPage + 1} / $pageCount",
                style = MaterialTheme.typography.labelLarge,
                color = Color.White,
                modifier = Modifier
                    .padding(top = 20.dp)
                    .background(
                        color = Color.Black.copy(alpha = 0.55f),
                        shape = MaterialTheme.shapes.small
                    )
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
    }
}
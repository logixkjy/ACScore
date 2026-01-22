package com.kandc.acscore.root

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex

/**
 * 피아스코어 스타일:
 * - 왼쪽에 Library 패널이 올라옴(기본 70~75% 폭)
 * - 오른쪽 남은 영역은 반투명 스크림 + 탭 시 닫힘
 */
@Composable
fun LibraryOverlay(
    visible: Boolean,
    onDismiss: () -> Unit,
    panelWidthFraction: Float = 0.75f,
    scrimAlpha: Float = 0.35f,
    content: @Composable () -> Unit
) {
    val progress by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 220),
        label = "overlayProgress"
    )

    if (progress == 0f) return

    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(10f)
    ) {
        // 오른쪽: 반투명 영역(=Viewer가 살짝 보임) + 탭하면 닫힘
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(1f - panelWidthFraction)
                .align(Alignment.CenterEnd)
                .background(Color.Black.copy(alpha = scrimAlpha * progress))
                .clickable { onDismiss() }
        )

        // 왼쪽: Library 패널
        // progress=0이면 왼쪽 밖으로 숨김, 1이면 제자리
        val panelWidth = panelWidthFraction
        val translateX = -(1f - progress) * 1000f // 간단히 큰 값으로 왼쪽으로 밀기

        Surface(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(panelWidth)
                .align(Alignment.CenterStart)
                .graphicsLayer { translationX = translateX },
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 6.dp
        ) {
            content()
        }
    }
}
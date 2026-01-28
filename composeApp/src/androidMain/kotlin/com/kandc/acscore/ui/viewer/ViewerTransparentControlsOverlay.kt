package com.kandc.acscore.ui.viewer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex

@Composable
fun ViewerTransparentControlsOverlay(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,

    // 터치 영역 크기
    edgeWidth: Dp = 72.dp,
    sideHeight: Dp = 220.dp,
    firstHeight: Dp = 72.dp,

    // ✅ First(맨앞) 위치: 라이브러리 버튼 줄 아래
    firstTopOffsetDp: Dp = 80.dp,
    firstStartPaddingDp: Dp = 8.dp,

    feedbackAlpha: Float = 0.12f,
    feedbackColor: Color = Color.Black,

    onPrev: () -> Unit,
    onNext: () -> Unit,
    onFirst: () -> Unit
) {
    if (!enabled) return

    Box(
        modifier = modifier
            .fillMaxSize()
            .zIndex(999f)
    ) {
        // ✅ 오른쪽 중앙: Next
        PressableHotZone(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 8.dp)
                .size(width = edgeWidth, height = sideHeight),
            feedbackColor = feedbackColor,
            feedbackAlpha = feedbackAlpha,
            onClick = onNext
        )

        // ✅ 왼쪽 중앙: Prev (센터 유지!)
        PressableHotZone(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 8.dp)
                .size(width = edgeWidth, height = sideHeight),
            feedbackColor = feedbackColor,
            feedbackAlpha = feedbackAlpha,
            onClick = onPrev
        )

        // ✅ 왼쪽 상단: First (Prev와 분리 배치)
        PressableHotZone(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = firstStartPaddingDp, top = firstTopOffsetDp)
                .size(width = edgeWidth, height = firstHeight),
            feedbackColor = feedbackColor,
            feedbackAlpha = feedbackAlpha,
            onClick = onFirst
        )
    }
}

@Composable
private fun PressableHotZone(
    modifier: Modifier,
    feedbackColor: Color,
    feedbackAlpha: Float,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    Box(
        modifier = modifier
            .background(if (pressed) feedbackColor.copy(alpha = feedbackAlpha) else Color.Transparent)
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick
            )
    )
}
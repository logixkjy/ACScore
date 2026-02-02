package com.kandc.acscore.ui.viewer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.FirstPage
import androidx.compose.material.icons.filled.LastPage
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
    edgeWidth: Dp = 84.dp,
    sideHeight: Dp = 360.dp,

    // 좌측 상단 버튼들(목록 / 처음)
    libraryHeight: Dp = 60.dp,
    firstHeight: Dp = 60.dp,

    // 네비바 "목록" 버튼 아래로 오도록 조절
    topOffsetDp: Dp = 30.dp,
    startPaddingDp: Dp = 8.dp,
    verticalGap: Dp = 20.dp,

    // 눌림 피드백(배경 하이라이트)
    feedbackAlpha: Float = 0.12f,
    feedbackColor: Color = Color.Black,

    // ✅ 눌렸을 때만 뜨는 라벨 스타일
    labelColor: Color = Color.White,
    labelBgColor: Color = Color.Black,
    labelBgAlpha: Float = 0.28f,
    labelPadding: PaddingValues = PaddingValues(horizontal = 10.dp, vertical = 8.dp),

    onLibrary: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onFirst: () -> Unit,
    onLast: () -> Unit,
) {
    if (!enabled) return

    Box(
        modifier = modifier
            .fillMaxSize()
            .zIndex(999f)
    ) {
        // ✅ 오른쪽 중앙: 다음
        PressableLabeledHotZone(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 8.dp)
                .size(width = edgeWidth, height = sideHeight),
            feedbackColor = feedbackColor,
            feedbackAlpha = feedbackAlpha,
            labelBgColor = labelBgColor,
            labelBgAlpha = labelBgAlpha,
            labelPadding = labelPadding,
            labelColor = labelColor,
            icon = { Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = labelColor) },
            text = "다음",
            onClick = onNext
        )

        // ✅ 오른쪽 상단: 마지막 (목록과 같은 Y 라인)
        PressableLabeledHotZone(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = startPaddingDp, top = topOffsetDp + libraryHeight + verticalGap)
                .size(width = edgeWidth, height = libraryHeight), // 목록이랑 같은 높이로 맞춤(가장 찾기 쉬움)
            feedbackColor = feedbackColor,
            feedbackAlpha = feedbackAlpha,
            labelBgColor = labelBgColor,
            labelBgAlpha = labelBgAlpha,
            labelPadding = labelPadding,
            labelColor = labelColor,
            icon = { Icon(Icons.Filled.LastPage, contentDescription = null, tint = labelColor) },
            text = "마지막",
            onClick = onLast
        )

        // ✅ 왼쪽 중앙: 이전
        PressableLabeledHotZone(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 8.dp)
                .size(width = edgeWidth, height = sideHeight),
            feedbackColor = feedbackColor,
            feedbackAlpha = feedbackAlpha,
            labelBgColor = labelBgColor,
            labelBgAlpha = labelBgAlpha,
            labelPadding = labelPadding,
            labelColor = labelColor,
            icon = { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = labelColor) },
            text = "이전",
            onClick = onPrev
        )

        // ✅ 왼쪽 상단: 목록 (Library → 목록)
        PressableLabeledHotZone(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = startPaddingDp, top = topOffsetDp)
                .size(width = edgeWidth, height = libraryHeight),
            feedbackColor = feedbackColor,
            feedbackAlpha = feedbackAlpha,
            labelBgColor = labelBgColor,
            labelBgAlpha = labelBgAlpha,
            labelPadding = labelPadding,
            labelColor = labelColor,
            icon = { Icon(Icons.Filled.List, contentDescription = null, tint = labelColor) },
            text = "목록",
            onClick = onLibrary
        )

        // ✅ 왼쪽 상단: 처음 — 목록 아래
        PressableLabeledHotZone(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(
                    start = startPaddingDp,
                    top = topOffsetDp + libraryHeight + verticalGap
                )
                .size(width = edgeWidth, height = firstHeight),
            feedbackColor = feedbackColor,
            feedbackAlpha = feedbackAlpha,
            labelBgColor = labelBgColor,
            labelBgAlpha = labelBgAlpha,
            labelPadding = labelPadding,
            labelColor = labelColor,
            icon = { Icon(Icons.Filled.FirstPage, contentDescription = null, tint = labelColor) },
            text = "처음",
            onClick = onFirst
        )
    }
}

@Composable
private fun PressableLabeledHotZone(
    modifier: Modifier,
    feedbackColor: Color,
    feedbackAlpha: Float,
    labelBgColor: Color,
    labelBgAlpha: Float,
    labelPadding: PaddingValues,
    labelColor: Color,
    icon: @Composable () -> Unit,
    text: String,
    alwaysHintAlpha: Float = 0f,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val base = if (alwaysHintAlpha > 0f) feedbackColor.copy(alpha = alwaysHintAlpha) else Color.Transparent

    Box(
        modifier = modifier
            .background(
                when {
                    pressed -> feedbackColor.copy(alpha = feedbackAlpha)
                    else -> base
                }
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick
            )
    ) {
        // ✅ 평소에는 안 보이고, 눌렸을 때만 표시
        if (pressed) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(labelBgColor.copy(alpha = labelBgAlpha), shape = MaterialTheme.shapes.large)
                    .padding(labelPadding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // ✅ 상단 아이콘 / 하단 텍스트
                icon()
                Text(
                    text = text,
                    color = labelColor,
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}
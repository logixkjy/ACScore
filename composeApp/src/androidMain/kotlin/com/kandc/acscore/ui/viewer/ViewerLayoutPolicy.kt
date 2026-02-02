package com.kandc.acscore.ui.viewer

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext

data class ViewerLayout(
    val isTablet: Boolean,
    val isLandscape: Boolean,
    val columns: Int
)

@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
fun rememberViewerLayout(): ViewerLayout {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val orientation = configuration.orientation

    // ✅ Activity를 못 찾는 환경(프리뷰/특수 컨텍스트)에서 크래시 방지
    val activity = remember(context) { context.findActivityOrNull() }

    // ✅ @Composable 함수는 remember 블록 안에서 호출하면 안 됨
    val windowSize = if (activity != null) calculateWindowSizeClass(activity) else null

    // ✅ Activity가 없으면 보수적으로 phone 처리
    val isTablet = windowSize?.widthSizeClass?.let { it >= WindowWidthSizeClass.Medium } ?: false

    val isLandscape = orientation == Configuration.ORIENTATION_LANDSCAPE

    // ✅ 폰 가로=1장, 태블릿 가로=2장
    val columns = if (isTablet && isLandscape) 2 else 1

    return remember(isTablet, isLandscape, columns) {
        ViewerLayout(
            isTablet = isTablet,
            isLandscape = isLandscape,
            columns = columns
        )
    }
}

private fun Context.findActivityOrNull(): Activity? {
    return when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivityOrNull()
        else -> null
    }
}
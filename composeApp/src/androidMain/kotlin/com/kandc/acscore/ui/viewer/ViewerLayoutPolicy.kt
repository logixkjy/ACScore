package com.kandc.acscore.ui.viewer

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
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
    val activity = LocalContext.current.findActivity()
    val windowSize = calculateWindowSizeClass(activity)
    val orientation = LocalConfiguration.current.orientation

    val isTablet = windowSize.widthSizeClass >= WindowWidthSizeClass.Medium
    val isLandscape = orientation == Configuration.ORIENTATION_LANDSCAPE
    val columns = if (isTablet && isLandscape) 2 else 1

    return ViewerLayout(
        isTablet = isTablet,
        isLandscape = isLandscape,
        columns = columns
    )
}

private tailrec fun Context.findActivity(): Activity {
    return when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> error("Activity not found from Context")
    }
}
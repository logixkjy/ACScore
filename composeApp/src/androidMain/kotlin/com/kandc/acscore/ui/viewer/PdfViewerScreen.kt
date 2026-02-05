package com.kandc.acscore.ui.viewer

import android.graphics.Bitmap
import android.util.Log
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.kandc.acscore.data.viewer.AndroidPdfRendererHolder
import com.kandc.acscore.data.viewer.PdfBitmapCache
import com.kandc.acscore.data.viewer.PdfPageLoader
import com.kandc.acscore.viewer.domain.ViewerOpenRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PdfViewerScreen(
    request: ViewerOpenRequest,
    modifier: Modifier = Modifier,
    initialPage: Int = 0,
    onPageChanged: (Int) -> Unit = {},
    onPageCountReady: (Int) -> Unit = {},
    onError: (Throwable) -> Unit = {},

    // ✅ 페이지 점프(prev/next/first/last)
    jumpToGlobalPage: Int? = null,
    jumpToken: Long = 0L
) {
    val layout = rememberViewerLayout()

    val sidePadding = 20.dp
    val pageSpacing = 10.dp
    val twoUpInnerGap = 12.dp

    val fileKey = remember(request.filePath) { request.filePath.hashCode().toString() }
    val cache = remember { PdfBitmapCache.default() }
    DisposableEffect(Unit) {
        onDispose {
            // recycle은 없으니 안전
            runCatching { cache.clear() }
        }
    }
    val holder = remember(request.filePath) { AndroidPdfRendererHolder(request.filePath) }

    DisposableEffect(holder) {
        runCatching { holder.open() }.onFailure(onError)
        onDispose { runCatching { holder.close() } }
    }

    var pageCount by remember(request.filePath) { mutableIntStateOf(0) }

    LaunchedEffect(request.filePath) {
        runCatching {
            pageCount = holder.pageCount().coerceAtLeast(0)
            onPageCountReady(pageCount)
        }.onFailure(onError)
    }

    if (pageCount <= 0) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    // ✅ 2-up이면 spread 단위로 페이징
    val pagerPageCount = if (layout.columns == 2) (pageCount + 1) / 2 else pageCount

    fun globalToPager(global: Int): Int {
        val g = global.coerceIn(0, pageCount - 1)
        return if (layout.columns == 2) (g / 2).coerceIn(0, pagerPageCount - 1) else g
    }

    fun pagerToGlobalLeft(pager: Int): Int =
        if (layout.columns == 2) pager * 2 else pager

    // ✅ anchorGlobal: "현재 보고 있는 페어/단일의 기준(global page)"
    var anchorGlobal by remember(request.filePath) { mutableIntStateOf(0) }

    val safeInitial = remember(initialPage, pageCount) {
        initialPage.coerceIn(0, pageCount - 1)
    }

    val pagerState = rememberPagerState(
        initialPage = globalToPager(safeInitial),
        pageCount = { pagerPageCount }
    )

    var readyToReport by remember(request.filePath, layout.columns) { mutableStateOf(false) }

    // ✅ (2) 재실행/복원: 마지막으로 보던 페이지 복원
    LaunchedEffect(request.filePath, layout.columns, pageCount, safeInitial) {
        readyToReport = false
        anchorGlobal = safeInitial
        runCatching { pagerState.scrollToPage(globalToPager(anchorGlobal)) }
        readyToReport = true
    }

    // ✅ (prev/next/first/last) 버튼 점프
    LaunchedEffect(jumpToken, layout.columns, pageCount) {
        if (jumpToken <= 0L) return@LaunchedEffect
        val target = (jumpToGlobalPage ?: return@LaunchedEffect).coerceIn(0, pageCount - 1)

        readyToReport = false
        anchorGlobal = target
        runCatching { pagerState.animateScrollToPage(globalToPager(anchorGlobal)) }
        readyToReport = true
    }

    // ✅ (1) 회전 시 현재 보고있던 "페어/단일" 유지
    LaunchedEffect(layout.columns, pagerState.currentPage, readyToReport, pageCount) {
        if (!readyToReport) return@LaunchedEffect

        if (layout.columns == 1) {
            anchorGlobal = pagerState.currentPage.coerceIn(0, pageCount - 1)
        } else {
            val left = pagerToGlobalLeft(pagerState.currentPage).coerceIn(0, pageCount - 1)
            val right = (left + 1).coerceAtMost(pageCount - 1)
            if (anchorGlobal !in left..right) anchorGlobal = left
        }

        onPageChanged(anchorGlobal)
    }

    val fling = PagerDefaults.flingBehavior(
        state = pagerState,
        snapPositionalThreshold = 0.20f
    )

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val maxWidthPx = with(density) { maxWidth.toPx() }

        // ✅ 폰 가로는 columns==1이므로 1-up + 가로폭에 맞춰 스케일
        val targetPageWidthPx = remember(layout.columns, maxWidthPx) {
            if (layout.columns == 2) {
                val gapPx = with(density) { twoUpInnerGap.toPx() }
                ((maxWidthPx - gapPx) / 2f).toInt().coerceAtLeast(1)
            } else {
                maxWidthPx.toInt().coerceAtLeast(1)
            }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            pageSize = PageSize.Fill,
            contentPadding = PaddingValues(horizontal = sidePadding, vertical = 12.dp),
            pageSpacing = pageSpacing,
            flingBehavior = fling,
            beyondViewportPageCount = 1
        ) { pagerPage ->
            if (layout.columns == 1) {
                PdfSinglePage(
                    holder = holder,
                    cache = cache,
                    fileKey = fileKey,
                    pageIndex = pagerPage,
                    targetWidthPx = targetPageWidthPx
                )
            } else {
                val left = pagerPage * 2
                val right = left + 1

                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(twoUpInnerGap),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        PdfSinglePage(holder, cache, fileKey, left, targetPageWidthPx)
                    }
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        if (right < pageCount) {
                            PdfSinglePage(holder, cache, fileKey, right, targetPageWidthPx)
                        } else {
                            Spacer(Modifier.fillMaxSize())
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PdfSinglePage(
    holder: AndroidPdfRendererHolder,
    cache: PdfBitmapCache,
    fileKey: String,
    pageIndex: Int,
    targetWidthPx: Int
) {
    val loader = remember(holder, cache, fileKey) {
        PdfPageLoader(holder = holder, cache = cache, fileKey = fileKey)
    }

    var bitmap by remember(fileKey, pageIndex, targetWidthPx) { mutableStateOf<Bitmap?>(null) }
    var error by remember(fileKey, pageIndex, targetWidthPx) { mutableStateOf<Throwable?>(null) }
    var retryTick by remember(fileKey, pageIndex, targetWidthPx) { mutableIntStateOf(0) }

    LaunchedEffect(fileKey, pageIndex, targetWidthPx, retryTick) {
        bitmap = null
        error = null

        repeat(2) { attempt0 ->
            val attempt = attempt0 + 1
            try {
                bitmap = loader.loadPage(pageIndex, targetWidthPx)
                error = null
                return@LaunchedEffect
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                Log.w(
                    "PdfViewer",
                    "render failed fileKey=$fileKey page=$pageIndex width=$targetWidthPx attempt=$attempt : ${t.message}",
                    t
                )
                if (attempt0 == 0) delay(80) else error = t
            }
        }
    }

    when {
        bitmap != null -> {
            Image(
                bitmap = requireNotNull(bitmap).asImageBitmap(),
                contentDescription = "page $pageIndex",
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight(Alignment.CenterVertically)
            )
        }

        error != null -> {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("페이지 로딩 실패", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(10.dp))
                TextButton(onClick = { retryTick++ }) { Text("다시 시도") }
            }
        }

        else -> {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }
        }
    }
}
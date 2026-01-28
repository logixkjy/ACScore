package com.kandc.acscore.ui.viewer

import android.graphics.Bitmap
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.ExperimentalFoundationApi
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
    jumpToGlobalPage: Int? = null,
    jumpToken: Long = 0L
) {
    val layout = rememberViewerLayout()

    val sidePadding = 20.dp
    val pageSpacing = 10.dp
    val twoUpInnerGap = 12.dp

    val fileKey = remember(request.filePath) { request.filePath.hashCode().toString() }

    val cache = remember { PdfBitmapCache.default() }
    val holder = remember(request.filePath) { AndroidPdfRendererHolder(request.filePath) }

    DisposableEffect(holder) {
        runCatching { holder.open() }.onFailure(onError)
        onDispose { holder.close() }
    }

    var pageCount by remember(holder) { mutableStateOf(0) }
    LaunchedEffect(holder) {
        runCatching { pageCount = holder.pageCount() }.onFailure(onError)
    }

    // ✅ pageCount가 준비되면 알려주기
    LaunchedEffect(request.filePath, pageCount) {
        if (pageCount > 0) onPageCountReady(pageCount)
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val maxWidthPx = with(density) { maxWidth.toPx() }

        val targetPageWidthPx = remember(layout.columns, maxWidthPx) {
            if (layout.columns == 2) {
                val gapPx = with(density) { twoUpInnerGap.toPx() }
                ((maxWidthPx - gapPx) / 2f).toInt().coerceAtLeast(1)
            } else {
                maxWidthPx.toInt().coerceAtLeast(1)
            }
        }

        val loader = remember(holder, cache, fileKey) {
            PdfPageLoader(holder = holder, cache = cache, fileKey = fileKey)
        }

        if (pageCount <= 0) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@BoxWithConstraints
        }

        val pagerPageCount = if (layout.columns == 2) (pageCount + 1) / 2 else pageCount

        val startPage = remember(request.filePath, layout.columns, pageCount, initialPage) {
            val safe = initialPage.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
            if (layout.columns == 2) (safe / 2).coerceIn(0, (pagerPageCount - 1).coerceAtLeast(0))
            else safe.coerceIn(0, (pagerPageCount - 1).coerceAtLeast(0))
        }

        val pagerState = rememberPagerState(
            initialPage = startPage,
            pageCount = { pagerPageCount }
        )

        LaunchedEffect(jumpToken, pageCount, layout.columns) {
            if (jumpToken <= 0L) return@LaunchedEffect
            val target = jumpToGlobalPage ?: return@LaunchedEffect
            if (pageCount <= 0) return@LaunchedEffect

            val safe = target.coerceIn(0, pageCount - 1)
            val targetPagerPage =
                if (layout.columns == 2) (safe / 2).coerceIn(0, pagerPageCount - 1)
                else safe.coerceIn(0, pagerPageCount - 1)

            if (targetPagerPage != pagerState.currentPage) {
                runCatching { pagerState.animateScrollToPage(targetPagerPage) }
            }
        }

        val fling = PagerDefaults.flingBehavior(
            state = pagerState,
            snapPositionalThreshold = 0.20f
        )

        LaunchedEffect(request.filePath, layout.columns, pagerPageCount, startPage) {
            runCatching { pagerState.scrollToPage(startPage) }
        }

        LaunchedEffect(request.filePath, layout.columns, pagerState.currentPage) {
            val current = if (layout.columns == 2) pagerState.currentPage * 2 else pagerState.currentPage
            onPageChanged(current.coerceAtLeast(0))
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            pageSize = PageSize.Fill,
            contentPadding = PaddingValues(horizontal = sidePadding, vertical = 12.dp),
            pageSpacing = pageSpacing,
            flingBehavior = fling
        ) { page ->
            if (layout.columns == 1) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    PageImage(
                        fileKey = fileKey,
                        pageIndex = page,
                        targetWidthPx = targetPageWidthPx,
                        loader = loader
                    )
                }
            } else {
                val left = page * 2
                val right = left + 1

                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.spacedBy(twoUpInnerGap),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            PageImage(
                                fileKey = fileKey,
                                pageIndex = left,
                                targetWidthPx = targetPageWidthPx,
                                loader = loader
                            )
                        }
                        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            if (right < pageCount) {
                                PageImage(
                                    fileKey = fileKey,
                                    pageIndex = right,
                                    targetWidthPx = targetPageWidthPx,
                                    loader = loader
                                )
                            } else {
                                Spacer(Modifier.fillMaxSize())
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PageImage(
    fileKey: String,
    pageIndex: Int,
    targetWidthPx: Int,
    loader: PdfPageLoader
) {
    var bitmap by remember(fileKey, pageIndex, targetWidthPx) { mutableStateOf<Bitmap?>(null) }
    var error by remember(fileKey, pageIndex, targetWidthPx) { mutableStateOf<Throwable?>(null) }

    // 수동 재시도 트리거
    var retryTick by remember(fileKey, pageIndex, targetWidthPx) { mutableIntStateOf(0) }
    var isLoading by remember(fileKey, pageIndex, targetWidthPx) { mutableStateOf(false) }

    LaunchedEffect(fileKey, pageIndex, targetWidthPx, retryTick) {
        bitmap = null
        error = null
        isLoading = true

        fun log(t: Throwable, attempt: Int) {
            Log.w(
                "PdfViewer",
                "render failed fileKey=$fileKey page=$pageIndex width=$targetWidthPx attempt=$attempt : " +
                        "${t.javaClass.simpleName} ${t.message}",
                t
            )
        }

        try {
            // 1회 자동 재시도 (총 2회)
            repeat(2) { attempt0 ->
                val attempt = attempt0 + 1
                try {
                    val bmp = loader.loadPage(pageIndex, targetWidthPx)
                    bitmap = bmp
                    error = null
                    return@LaunchedEffect
                } catch (ce: CancellationException) {
                    throw ce
                } catch (t: Throwable) {
                    log(t, attempt)
                    if (attempt0 == 0) delay(80) else error = t
                }
            }
        } finally {
            isLoading = false
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
                Spacer(Modifier.height(6.dp))
                Text(
                    "page=${pageIndex + 1} / width=$targetWidthPx",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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
            ) {
                CircularProgressIndicator()
            }
        }
    }
}
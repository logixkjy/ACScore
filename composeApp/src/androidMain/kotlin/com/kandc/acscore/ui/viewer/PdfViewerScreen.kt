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

/**
 * ✅ 단일 PDF 뷰어
 * - globalPage(단일 페이지 인덱스)를 기준으로 상태/저장을 통일
 * - 2-up(가로)일 때만 pager를 spread(스프레드) 단위로 동작시켜 인덱스 꼬임/중복을 방지
 * - 레이아웃(columns) 변경 시 pagerState를 1회 재생성하여 '첫 페어 중복' / '0↔1 반복'을 방지
 * - 외부 점프는 jumpToken 기반으로 animateScrollToPage로만 처리
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PdfViewerScreen(
    request: ViewerOpenRequest,
    modifier: Modifier = Modifier,
    /** 마지막으로 보고 있던 globalPage (0-based) */
    currentGlobalPage: Int = 0,
    onGlobalPageChanged: (Int) -> Unit = {},
    onPageCountReady: (Int) -> Unit = {},
    onError: (Throwable) -> Unit = {},
    /** 버튼/외부 요청에 의한 점프 */
    jumpToGlobalPage: Int? = null,
    jumpToken: Long = 0L,
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

    var pageCount by remember(holder) { mutableIntStateOf(0) }
    LaunchedEffect(holder) {
        runCatching { pageCount = holder.pageCount() }.onFailure(onError)
    }

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

        // ✅ pager는 2-up이면 spread 단위로, 1-up이면 page 단위로
        val spreadCount = remember(pageCount) { (pageCount + 1) / 2 }
        val pagerPageCount = if (layout.columns == 2) spreadCount else pageCount

        val safeGlobal = remember(currentGlobalPage, pageCount) {
            currentGlobalPage.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
        }
        val initialPagerPage = remember(layout.columns, safeGlobal, pagerPageCount) {
            val p = if (layout.columns == 2) (safeGlobal / 2) else safeGlobal
            p.coerceIn(0, (pagerPageCount - 1).coerceAtLeast(0))
        }

        // ✅ 레이아웃 변경(특히 1-up <-> 2-up) 시 pagerState를 새로 만들어 꼬임 방지
        key(request.filePath, layout.columns) {
            val pagerState = rememberPagerState(
                initialPage = initialPagerPage,
                pageCount = { pagerPageCount }
            )

            val fling = PagerDefaults.flingBehavior(
                state = pagerState,
                snapPositionalThreshold = 0.20f
            )

            // ✅ external jump
            LaunchedEffect(jumpToken, pageCount, layout.columns) {
                if (jumpToken <= 0L) return@LaunchedEffect
                val target = jumpToGlobalPage ?: return@LaunchedEffect
                if (pageCount <= 0) return@LaunchedEffect

                val tg = target.coerceIn(0, pageCount - 1)
                val tp = if (layout.columns == 2) (tg / 2) else tg
                val safePager = tp.coerceIn(0, (pagerPageCount - 1).coerceAtLeast(0))

                if (safePager != pagerState.currentPage) {
                    runCatching { pagerState.animateScrollToPage(safePager) }
                        .onFailure { /* ignore */ }
                }
            }

            // ✅ save current global page
            LaunchedEffect(request.filePath, layout.columns, pagerState.currentPage) {
                val global =
                    if (layout.columns == 2) pagerState.currentPage * 2 else pagerState.currentPage
                onGlobalPageChanged(global.coerceIn(0, pageCount - 1))
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
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        PageImage(
                            fileKey = fileKey,
                            pageIndex = pagerPage,
                            targetWidthPx = targetPageWidthPx,
                            loader = loader
                        )
                    }
                } else {
                    val left = pagerPage * 2
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
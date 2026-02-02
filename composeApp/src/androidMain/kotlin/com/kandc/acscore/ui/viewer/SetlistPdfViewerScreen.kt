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
import kotlinx.coroutines.launch

/**
 * ✅ 세트리스트 PDF 뷰어
 * - globalPage(단일 페이지 인덱스)를 기준으로 상태/저장을 통일
 * - 2-up(태블릿 가로)일 때는 pager를 spread 단위로 동작
 * - ✅ 규약 #6: 2-up에서 오른쪽 페이지는 "다음 문서 첫 페이지"까지 포함해 globalRight로 매핑하여 렌더링한다.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SetlistPdfViewerScreen(
    requests: List<ViewerOpenRequest>,
    modifier: Modifier = Modifier,
    currentGlobalPage: Int = 0,
    onGlobalPageChanged: (Int) -> Unit = {},
    onPageCountReady: (Int) -> Unit = {},

    // 곡 선택 점프(해당 score 첫 페이지로)
    jumpToScoreId: String? = null,
    jumpTokenScore: Long = 0L,

    // 페이지 점프(버튼)
    jumpToGlobalPage: Int? = null,
    jumpTokenGlobal: Long = 0L,
) {
    val layout = rememberViewerLayout()

    val sidePadding = 20.dp
    val pageSpacing = 10.dp
    val twoUpInnerGap = 12.dp

    val cache = remember { PdfBitmapCache.default() }

    val requestsKey = remember(requests) {
        requests.joinToString("|") { it.filePath }.hashCode()
    }

    val holders = remember(requestsKey) {
        requests.associate { r -> r.filePath to AndroidPdfRendererHolder(r.filePath) }
    }

    DisposableEffect(holders) {
        holders.values.forEach { h ->
            runCatching { h.open() }.onFailure { e ->
                Log.w("SetlistPdfViewer", "holder open failed: ${e.message}", e)
            }
        }
        onDispose { holders.values.forEach { h -> runCatching { h.close() } } }
    }

    var pageCounts by remember(requestsKey) { mutableStateOf<List<Int>?>(null) }

    LaunchedEffect(requestsKey) {
        val counts = requests.map { r ->
            val h = holders[r.filePath]
            runCatching { h?.pageCount() ?: 0 }.getOrElse { 0 }.coerceAtLeast(0)
        }
        pageCounts = counts
    }

    val counts = pageCounts
    if (counts == null || counts.isEmpty() || counts.all { it <= 0 }) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val prefix = remember(counts) {
        val p = IntArray(counts.size + 1)
        for (i in counts.indices) p[i + 1] = p[i] + counts[i]
        p
    }
    val totalPages = prefix.last().coerceAtLeast(1)

    LaunchedEffect(totalPages) { onPageCountReady(totalPages) }

    fun findFileIndexByGlobalPage(globalPage: Int): Int {
        val g = globalPage.coerceIn(0, totalPages - 1)
        for (i in 0 until counts.size) if (g < prefix[i + 1]) return i
        return counts.lastIndex
    }

    fun localPageOf(globalPage: Int, fileIndex: Int): Int {
        val g = globalPage.coerceIn(0, totalPages - 1)
        return (g - prefix[fileIndex]).coerceAtLeast(0)
    }

    fun globalPageForScoreFirstPage(scoreId: String?): Int {
        if (scoreId == null) return 0
        val idx = requests.indexOfFirst { it.scoreId == scoreId }.takeIf { it >= 0 } ?: 0
        return prefix[idx].coerceIn(0, totalPages - 1)
    }

    val spreadCount = remember(totalPages) { (totalPages + 1) / 2 }
    val pagerPageCount = if (layout.columns == 2) spreadCount else totalPages

    val safeGlobal = remember(currentGlobalPage, totalPages) {
        currentGlobalPage.coerceIn(0, totalPages - 1)
    }
    val initialPagerPage = remember(layout.columns, safeGlobal, pagerPageCount) {
        val p = if (layout.columns == 2) (safeGlobal / 2) else safeGlobal
        p.coerceIn(0, (pagerPageCount - 1).coerceAtLeast(0))
    }

    // ✅ 레이아웃 변경/세트 변경 시 pagerState 재생성(꼬임 방지)
    key(requestsKey, layout.columns) {
        val pagerState = rememberPagerState(
            initialPage = initialPagerPage,
            pageCount = { pagerPageCount }
        )

        val fling = PagerDefaults.flingBehavior(
            state = pagerState,
            snapPositionalThreshold = 0.20f
        )

        // ✅ save current global page
        LaunchedEffect(pagerState.currentPage, layout.columns, totalPages) {
            val global =
                if (layout.columns == 2) pagerState.currentPage * 2 else pagerState.currentPage
            onGlobalPageChanged(global.coerceIn(0, totalPages - 1))
        }

        val scope = rememberCoroutineScope()

        // ✅ (1) 곡 점프
        LaunchedEffect(jumpTokenScore, layout.columns, totalPages) {
            if (jumpTokenScore <= 0L) return@LaunchedEffect
            val id = jumpToScoreId ?: return@LaunchedEffect

            val tg = globalPageForScoreFirstPage(id).coerceIn(0, totalPages - 1)
            val tp = if (layout.columns == 2) (tg / 2) else tg
            val safePager = tp.coerceIn(0, (pagerPageCount - 1).coerceAtLeast(0))
            if (safePager != pagerState.currentPage) {
                scope.launch { pagerState.animateScrollToPage(safePager) }
            }
        }

        // ✅ (2) 페이지 점프(버튼)
        LaunchedEffect(jumpTokenGlobal, layout.columns, totalPages) {
            if (jumpTokenGlobal <= 0L) return@LaunchedEffect
            val target = jumpToGlobalPage ?: return@LaunchedEffect

            val tg = target.coerceIn(0, totalPages - 1)
            val tp = if (layout.columns == 2) (tg / 2) else tg
            val safePager = tp.coerceIn(0, (pagerPageCount - 1).coerceAtLeast(0))
            if (safePager != pagerState.currentPage) {
                scope.launch { pagerState.animateScrollToPage(safePager) }
            }
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

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                pageSize = PageSize.Fill,
                contentPadding = PaddingValues(horizontal = sidePadding, vertical = 12.dp),
                pageSpacing = pageSpacing,
                flingBehavior = fling,
                beyondViewportPageCount = 1
            ) { pagerPage ->
                // ✅ pagerPage -> globalLeft
                val globalLeft = if (layout.columns == 2) (pagerPage * 2) else pagerPage

                if (layout.columns == 1) {
                    // 1-up: globalLeft만 렌더링
                    val fileIdx = findFileIndexByGlobalPage(globalLeft)
                    val local = localPageOf(globalLeft, fileIdx)

                    val req = requests[fileIdx]
                    val holder = holders[req.filePath] ?: return@HorizontalPager
                    val fileKey = remember(req.filePath) { req.filePath.hashCode().toString() }
                    val loader = remember(req.filePath) {
                        PdfPageLoader(holder = holder, cache = cache, fileKey = fileKey)
                    }

                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        SetlistPageImage(
                            fileKey = fileKey,
                            pageIndex = local,
                            targetWidthPx = targetPageWidthPx,
                            loader = loader
                        )
                    }
                } else {
                    // ✅ 2-up: 오른쪽은 globalRight로 "전체 세트" 기준 매핑 (규약 #6)
                    val globalRight = globalLeft + 1

                    // left
                    val fileIdxL = findFileIndexByGlobalPage(globalLeft)
                    val localL = localPageOf(globalLeft, fileIdxL)
                    val reqL = requests[fileIdxL]
                    val holderL = holders[reqL.filePath] ?: return@HorizontalPager
                    val fileKeyL = remember(reqL.filePath) { reqL.filePath.hashCode().toString() }
                    val loaderL = remember(reqL.filePath) {
                        PdfPageLoader(holder = holderL, cache = cache, fileKey = fileKeyL)
                    }

                    // right (might be next document)
                    val canShowRight = globalRight < totalPages
                    val fileIdxR = if (canShowRight) findFileIndexByGlobalPage(globalRight) else -1
                    val localR = if (canShowRight) localPageOf(globalRight, fileIdxR) else 0
                    val reqR = if (canShowRight) requests[fileIdxR] else null
                    val holderR = if (canShowRight && reqR != null) holders[reqR.filePath] else null
                    val fileKeyR = if (canShowRight && reqR != null) remember(reqR.filePath) { reqR.filePath.hashCode().toString() } else ""
                    val loaderR = if (canShowRight && reqR != null && holderR != null) {
                        remember(reqR.filePath) { PdfPageLoader(holder = holderR, cache = cache, fileKey = fileKeyR) }
                    } else null

                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.spacedBy(twoUpInnerGap),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            SetlistPageImage(
                                fileKey = fileKeyL,
                                pageIndex = localL,
                                targetWidthPx = targetPageWidthPx,
                                loader = loaderL
                            )
                        }
                        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            if (canShowRight && loaderR != null && reqR != null) {
                                SetlistPageImage(
                                    fileKey = fileKeyR,
                                    pageIndex = localR,
                                    targetWidthPx = targetPageWidthPx,
                                    loader = loaderR
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
private fun SetlistPageImage(
    fileKey: String,
    pageIndex: Int,
    targetWidthPx: Int,
    loader: PdfPageLoader
) {
    var bitmap by remember(fileKey, pageIndex, targetWidthPx) { mutableStateOf<Bitmap?>(null) }
    var error by remember(fileKey, pageIndex, targetWidthPx) { mutableStateOf<Throwable?>(null) }

    var retryTick by remember(fileKey, pageIndex, targetWidthPx) { mutableIntStateOf(0) }
    var isLoading by remember(fileKey, pageIndex, targetWidthPx) { mutableStateOf(false) }

    LaunchedEffect(fileKey, pageIndex, targetWidthPx, retryTick) {
        bitmap = null
        error = null
        isLoading = true

        fun log(t: Throwable, attempt: Int) {
            Log.w(
                "SetlistPdfViewer",
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
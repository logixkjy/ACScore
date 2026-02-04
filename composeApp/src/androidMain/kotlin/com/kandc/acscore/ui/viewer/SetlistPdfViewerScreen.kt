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
fun SetlistPdfViewerScreen(
    requests: List<ViewerOpenRequest>,
    initialScoreId: String?,
    modifier: Modifier = Modifier,
    initialGlobalPage: Int = 0,
    onGlobalPageChanged: (Int) -> Unit = {},
    onPageCountReady: (Int) -> Unit = {},

    // ✅ 곡 선택 점프(세트 상세 목록에서 곡 선택)
    jumpToScoreId: String? = null,
    jumpTokenScore: Long = 0L,

    // ✅ 페이지 점프(prev/next/first/last)
    jumpToGlobalPage: Int? = null,
    jumpTokenPage: Long = 0L
) {
    val layout = rememberViewerLayout()

    val sidePadding = 20.dp
    val pageSpacing = 10.dp
    val twoUpInnerGap = 12.dp

    val cache = remember { PdfBitmapCache.default() }

    val holders = remember(requests) {
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

    // 각 문서 pageCount 취득
    var counts by remember(requests) { mutableStateOf<List<Int>?>(null) }
    LaunchedEffect(requests) {
        counts = requests.map { r ->
            val h = holders[r.filePath]
            runCatching { h?.pageCount() ?: 0 }.getOrElse { 0 }.coerceAtLeast(0)
        }
    }

    val pageCounts = counts
    if (pageCounts == null || pageCounts.isEmpty() || pageCounts.all { it <= 0 }) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    // prefix[i] = i번째 문서 시작 globalPage
    val prefix = remember(pageCounts) {
        val p = IntArray(pageCounts.size + 1)
        for (i in pageCounts.indices) p[i + 1] = p[i] + pageCounts[i]
        p
    }

    val totalPages = prefix.last().coerceAtLeast(1)
    LaunchedEffect(totalPages) { onPageCountReady(totalPages) }

    fun globalFirstPageOfScore(scoreId: String?): Int {
        if (scoreId == null) return 0
        val idx = requests.indexOfFirst { it.scoreId == scoreId }.takeIf { it >= 0 } ?: 0
        return prefix[idx].coerceIn(0, totalPages - 1)
    }

    // ✅ anchorGlobal = "현재 보고 있는 페어/페이지의 기준(global)"
    var anchorGlobal by remember(requests) { mutableIntStateOf(0) }

    // ✅ 2-up이면 spread 단위로 페이징 (global 2장 = pager 1장)
    val pagerPageCount = if (layout.columns == 2) (totalPages + 1) / 2 else totalPages

    fun globalToPager(global: Int): Int {
        val g = global.coerceIn(0, totalPages - 1)
        return if (layout.columns == 2) (g / 2).coerceIn(0, pagerPageCount - 1) else g
    }

    fun pagerToGlobalLeft(pager: Int): Int =
        if (layout.columns == 2) pager * 2 else pager

    // 초기 복원 globalPage 결정
    val initialIndex = remember(requests, initialScoreId) {
        initialScoreId?.let { id ->
            requests.indexOfFirst { it.scoreId == id }.takeIf { it >= 0 }
        } ?: 0
    }

    val safeInitialGlobal = remember(initialIndex, initialGlobalPage, totalPages) {
        val fallback = prefix[initialIndex].coerceIn(0, totalPages - 1)
        if (initialGlobalPage >= 0) initialGlobalPage.coerceIn(0, totalPages - 1) else fallback
    }

    val pagerState = rememberPagerState(
        initialPage = globalToPager(safeInitialGlobal),
        pageCount = { pagerPageCount }
    )

    var readyToReport by remember(requests, layout.columns) { mutableStateOf(false) }

    // ✅ (2) 재실행/복원: 마지막 globalPage로 복원
    LaunchedEffect(requests, layout.columns, pagerPageCount, safeInitialGlobal) {
        readyToReport = false
        anchorGlobal = safeInitialGlobal
        runCatching { pagerState.scrollToPage(globalToPager(anchorGlobal)) }
        readyToReport = true
    }

    // ✅ (3) 세트리스트 목록에서 곡 선택 점프: 선택한 곡의 첫페이지로
    LaunchedEffect(jumpTokenScore, layout.columns, totalPages) {
        if (jumpTokenScore <= 0L) return@LaunchedEffect
        val targetGlobal = globalFirstPageOfScore(jumpToScoreId)

        readyToReport = false
        anchorGlobal = targetGlobal
        runCatching { pagerState.animateScrollToPage(globalToPager(anchorGlobal)) }
        readyToReport = true
    }

    // ✅ prev/next/first/last 점프
    LaunchedEffect(jumpTokenPage, layout.columns, totalPages) {
        if (jumpTokenPage <= 0L) return@LaunchedEffect
        val target = jumpToGlobalPage ?: return@LaunchedEffect
        val safe = target.coerceIn(0, totalPages - 1)

        readyToReport = false
        anchorGlobal = safe
        runCatching { pagerState.animateScrollToPage(globalToPager(anchorGlobal)) }
        readyToReport = true
    }

    // ✅ (1) 회전(세로/가로) 전환 시: 현재 보고 있던 페어/단일이 유지되도록 anchorGlobal 유지
    LaunchedEffect(layout.columns, pagerState.currentPage, readyToReport, totalPages) {
        if (!readyToReport) return@LaunchedEffect

        if (layout.columns == 1) {
            anchorGlobal = pagerState.currentPage.coerceIn(0, totalPages - 1)
        } else {
            val left = pagerToGlobalLeft(pagerState.currentPage).coerceIn(0, totalPages - 1)
            val right = (left + 1).coerceAtMost(totalPages - 1)
            if (anchorGlobal !in left..right) anchorGlobal = left
        }

        onGlobalPageChanged(anchorGlobal)
    }

    // ✅ fling 민감도 (단일 뷰어와 동일 UX)
    val fling = PagerDefaults.flingBehavior(
        state = pagerState,
        snapPositionalThreshold = 0.20f
    )

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
            if (layout.columns == 1) {
                RenderGlobalPage(
                    globalPage = pagerPage,
                    requests = requests,
                    pageCounts = pageCounts,
                    prefix = prefix,
                    totalPages = totalPages,
                    holders = holders,
                    cache = cache,
                    targetWidthPx = targetPageWidthPx
                )
            } else {
                // ✅ (4) 세트리스트 가로모드(태블릿 2-up): 전체 globalPage를 페어로
                // 홀수 문서 끝에서 다음 문서 첫페이지가 우측에 페어로 나오는 구조를 자동으로 만족
                val leftGlobal = pagerPage * 2
                val rightGlobal = leftGlobal + 1

                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(twoUpInnerGap),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        RenderGlobalPage(
                            globalPage = leftGlobal,
                            requests = requests,
                            pageCounts = pageCounts,
                            prefix = prefix,
                            totalPages = totalPages,
                            holders = holders,
                            cache = cache,
                            targetWidthPx = targetPageWidthPx
                        )
                    }
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        if (rightGlobal < totalPages) {
                            RenderGlobalPage(
                                globalPage = rightGlobal,
                                requests = requests,
                                pageCounts = pageCounts,
                                prefix = prefix,
                                totalPages = totalPages,
                                holders = holders,
                                cache = cache,
                                targetWidthPx = targetPageWidthPx
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

@Composable
private fun RenderGlobalPage(
    globalPage: Int,
    requests: List<ViewerOpenRequest>,
    pageCounts: List<Int>,
    prefix: IntArray,
    totalPages: Int,
    holders: Map<String, AndroidPdfRendererHolder>,
    cache: PdfBitmapCache,
    targetWidthPx: Int
) {
    if (totalPages <= 0) return
    val safe = globalPage.coerceIn(0, totalPages - 1)

    // global -> file index
    var fileIdx = 0
    for (i in 0 until pageCounts.size) {
        if (safe < prefix[i + 1]) { fileIdx = i; break }
    }
    val local = (safe - prefix[fileIdx]).coerceAtLeast(0)

    val req = requests[fileIdx]
    val holder = holders[req.filePath] ?: return

    val fileKey = remember(req.filePath) { req.filePath.hashCode().toString() }
    val loader = remember(req.filePath) { PdfPageLoader(holder = holder, cache = cache, fileKey = fileKey) }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        SetlistPageImage(
            fileKey = fileKey,
            pageIndex = local,
            targetWidthPx = targetWidthPx,
            loader = loader
        )
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

    LaunchedEffect(fileKey, pageIndex, targetWidthPx, retryTick) {
        bitmap = null
        error = null

        fun log(t: Throwable, attempt: Int) {
            Log.w(
                "SetlistPdfViewer",
                "render failed fileKey=$fileKey page=$pageIndex width=$targetWidthPx attempt=$attempt : " +
                        "${t.javaClass.simpleName} ${t.message}",
                t
            )
        }

        repeat(2) { attempt0 ->
            val attempt = attempt0 + 1
            try {
                bitmap = loader.loadPage(pageIndex, targetWidthPx)
                error = null
                return@LaunchedEffect
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                log(t, attempt)
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
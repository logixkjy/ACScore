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
import kotlin.math.abs

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SetlistPdfViewerScreen(
    requests: List<ViewerOpenRequest>,
    initialScoreId: String?,
    modifier: Modifier = Modifier,
    initialGlobalPage: Int = 0,
    onGlobalPageChanged: (Int) -> Unit = {},

    // ✅ 이미 열린 탭 점프 지원
    jumpToScoreId: String? = null,
    jumpToken: Long = 0L
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
        onDispose {
            holders.values.forEach { h -> runCatching { h.close() } }
        }
    }

    var pageCounts by remember(requests) { mutableStateOf<List<Int>?>(null) }

    LaunchedEffect(requests) {
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

    fun findFileIndexByGlobalPage(globalPage: Int): Int {
        val g = globalPage.coerceIn(0, totalPages - 1)
        for (i in 0 until counts.size) {
            if (g < prefix[i + 1]) return i
        }
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

    val initialIndex = remember(requests, initialScoreId) {
        initialScoreId?.let { id ->
            requests.indexOfFirst { it.scoreId == id }.takeIf { it >= 0 }
        } ?: 0
    }

    val initialGlobal = remember(initialIndex, counts, prefix, initialGlobalPage) {
        when {
            initialGlobalPage >= 0 -> initialGlobalPage.coerceIn(0, totalPages - 1)
            else -> prefix[initialIndex].coerceIn(0, totalPages - 1)
        }
    }

    val pagerState = rememberPagerState(
        initialPage = initialGlobal,
        pageCount = { totalPages }
    )

    // ✅ fling 동작을 단일 뷰어와 동일하게(명시)
    val fling = PagerDefaults.flingBehavior(
        state = pagerState,
        snapPositionalThreshold = 0.20f // 기본 0.5 → 훨씬 민감
    )

    // page save
    var allowSave by remember { mutableStateOf(false) }

// 첫 렌더 안정화 후에만 저장 허용
    LaunchedEffect(Unit) {
        delay(300)
        allowSave = true
    }

// page save
    LaunchedEffect(pagerState.currentPage, allowSave) {
        if (!allowSave) return@LaunchedEffect
        onGlobalPageChanged(pagerState.currentPage.coerceIn(0, totalPages - 1))
    }

    // ✅ 이미 열린 세트리스트 탭에서 "다른 곡 선택" 시 점프
    val scope = rememberCoroutineScope()
    LaunchedEffect(jumpToken) {
        if (jumpToken <= 0L) return@LaunchedEffect
        val id = jumpToScoreId ?: return@LaunchedEffect

        val target = globalPageForScoreFirstPage(id)
        if (target != pagerState.currentPage) {
            scope.launch { pagerState.animateScrollToPage(target) }
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
            beyondViewportPageCount = 1 // ✅ 다음/이전 한 장 미리 준비 → 스와이프 안정감
        ) { globalPage ->
            val fileIdx = findFileIndexByGlobalPage(globalPage)
            val local = localPageOf(globalPage, fileIdx)

            val req = requests[fileIdx]
            val holder = holders[req.filePath] ?: return@HorizontalPager

            val fileKey = remember(req.filePath) { req.filePath.hashCode().toString() }
            val loader = remember(req.filePath) {
                PdfPageLoader(holder = holder, cache = cache, fileKey = fileKey)
            }

            if (layout.columns == 1) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    SetlistPageImage(
                        fileKey = fileKey,
                        pageIndex = local,
                        targetWidthPx = targetPageWidthPx,
                        loader = loader
                    )
                }
            } else {
                val left = (local / 2) * 2
                val right = left + 1
                val pageCount = counts[fileIdx]

                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(twoUpInnerGap),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        SetlistPageImage(
                            fileKey = fileKey,
                            pageIndex = left,
                            targetWidthPx = targetPageWidthPx,
                            loader = loader
                        )
                    }
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        if (right < pageCount) {
                            SetlistPageImage(
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
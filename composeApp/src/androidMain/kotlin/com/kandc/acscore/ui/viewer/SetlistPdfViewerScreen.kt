package com.kandc.acscore.ui.viewer

import android.graphics.Bitmap
import android.util.Log
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
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
    onGlobalPageChanged: (Int) -> Unit = {}
) {
    val layout = rememberViewerLayout()

    val sidePadding = 20.dp
    val pageSpacing = 10.dp
    val twoUpInnerGap = 12.dp

    // ✅ 세트리스트는 "1-up" 기준으로 합산 페이지를 만든다.
    // (2-up은 각 파일별 2-up row 계산이 꼬이기 쉬워서 MVP에서는 1-up 권장)
    // 그래도 현재 layout.columns를 존중하되, total paging은 1-up으로 하고
    // 2-up이 켜져있으면 한 globalPage에서 2장을 보여주는 방식으로만 처리한다.
    // -> 즉, globalPagerCount는 (layout.columns==2) ? rowCountTotal : pageTotal
    // 여기서는 파일별 pageCount를 구한 뒤, layout.columns에 맞게 pagerCount를 만든다.

    val cache = remember { PdfBitmapCache.default() }

    // holders / loaders (세트리스트 전체를 이어서 넘기기 위해 여러 PDF를 열어둠)
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

    // prefix sums: global 0..total-1
    val prefix = remember(counts) {
        val p = IntArray(counts.size + 1)
        for (i in counts.indices) p[i + 1] = p[i] + counts[i]
        p
    }
    val totalPages = prefix.last().coerceAtLeast(1)

    fun findFileIndexByGlobalPage(globalPage: Int): Int {
        val g = globalPage.coerceIn(0, totalPages - 1)
        // linear search is ok for MVP; can be binary if needed
        for (i in 0 until counts.size) {
            if (g < prefix[i + 1]) return i
        }
        return counts.lastIndex
    }

    fun localPageOf(globalPage: Int, fileIndex: Int): Int {
        val g = globalPage.coerceIn(0, totalPages - 1)
        return (g - prefix[fileIndex]).coerceAtLeast(0)
    }

    val initialIndex = remember(requests, initialScoreId) {
        initialScoreId?.let { id ->
            requests.indexOfFirst { it.scoreId == id }.takeIf { it >= 0 }
        } ?: 0
    }

    val initialGlobal = remember(initialIndex, counts, prefix, initialGlobalPage) {
        // 기존 저장(global)이 있으면 그걸 우선
        if (initialGlobalPage in 0 until totalPages) return@remember initialGlobalPage
        // 아니면 initialScore의 첫 페이지로
        prefix[initialIndex].coerceIn(0, totalPages - 1)
    }

    val pagerState = rememberPagerState(
        initialPage = initialGlobal,
        pageCount = { totalPages }
    )

    // page save
    LaunchedEffect(pagerState.currentPage) {
        onGlobalPageChanged(pagerState.currentPage.coerceIn(0, totalPages - 1))
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
            pageSpacing = pageSpacing
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
                // 2-up: local page를 기준으로 "짝"을 맞춘다
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
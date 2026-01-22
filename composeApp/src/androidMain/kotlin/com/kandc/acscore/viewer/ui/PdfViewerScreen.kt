package com.kandc.acscore.viewer.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kandc.acscore.viewer.data.AndroidPdfRendererHolder
import com.kandc.acscore.viewer.data.PdfBitmapCache
import com.kandc.acscore.viewer.data.PdfPageLoader
import com.kandc.acscore.viewer.domain.ViewerOpenRequest
import kotlinx.coroutines.CancellationException

@Composable
fun PdfViewerScreen(
    request: ViewerOpenRequest,
    modifier: Modifier = Modifier,
    onError: (Throwable) -> Unit = {}
) {
    val layout = rememberViewerLayout()
    val gapDp = 12.dp

    // 파일별 캐시 키(경로 변경/동일경로 케이스 대응)
    val fileKey = remember(request.filePath) { request.filePath.hashCode().toString() }

    val cache = remember { PdfBitmapCache.default() }
    val holder = remember(request.filePath) { AndroidPdfRendererHolder(request.filePath) }

    DisposableEffect(holder) {
        runCatching { holder.open() }
            .onFailure { onError(it) }

        onDispose {
            holder.close()
            // 탭 유지(T2-3)로 갈 때는 탭별 캐시 유지/정책을 따로 잡을 수 있음
            // 지금은 단일 화면 기준으로 유지해도 됨. 필요하면 clear 호출.
            // cache.clear()
        }
    }

    val pageCount = remember { mutableStateOf(0) }
    LaunchedEffect(holder) {
        runCatching { pageCount.value = holder.pageCount() }
            .onFailure { onError(it) }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val maxWidthPx = with(density) { maxWidth.toPx() }

        val targetPageWidthPx = remember(layout.columns, maxWidthPx) {
            if (layout.columns == 2) {
                // 2-up: (전체폭 - gap) / 2
                val gapPx = with(density) { gapDp.toPx() }
                ((maxWidthPx - gapPx) / 2f).toInt().coerceAtLeast(1)
            } else {
                // 1-up: 전체폭 fit-width
                maxWidthPx.toInt().coerceAtLeast(1)
            }
        }

        val loader = remember(holder, cache, fileKey) {
            PdfPageLoader(holder = holder, cache = cache, fileKey = fileKey)
        }

        Column(Modifier.fillMaxSize()) {
            // 상단 타이틀(원하면 TopAppBar로 교체)
            Text(
                text = request.title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            )

            if (pageCount.value <= 0) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                if (layout.columns == 1) {
                    SingleColumnPages(
                        pageCount = pageCount.value,
                        targetWidthPx = targetPageWidthPx,
                        loader = loader
                    )
                } else {
                    TwoColumnPages(
                        pageCount = pageCount.value,
                        targetWidthPx = targetPageWidthPx,
                        gapDp = gapDp,
                        loader = loader
                    )
                }
            }
        }
    }
}

@Composable
private fun SingleColumnPages(
    pageCount: Int,
    targetWidthPx: Int,
    loader: PdfPageLoader
) {
    val indices = remember(pageCount) { (0 until pageCount).toList() }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(items = indices, key = { it }) { pageIndex ->
            PageImage(
                pageIndex = pageIndex,
                targetWidthPx = targetWidthPx,
                loader = loader
            )
        }
    }
}

@Composable
private fun TwoColumnPages(
    pageCount: Int,
    targetWidthPx: Int,
    gapDp: Dp,
    loader: PdfPageLoader
) {
    val rowCount = remember(pageCount) { (pageCount + 1) / 2 }
    val rows = remember(rowCount) { (0 until rowCount).toList() }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(items = rows, key = { it }) { rowIndex ->
            val left = rowIndex * 2
            val right = left + 1

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(gapDp)
            ) {
                Box(Modifier.weight(1f)) {
                    PageImage(
                        pageIndex = left,
                        targetWidthPx = targetWidthPx,
                        loader = loader
                    )
                }
                Box(Modifier.weight(1f)) {
                    if (right < pageCount) {
                        PageImage(
                            pageIndex = right,
                            targetWidthPx = targetWidthPx,
                            loader = loader
                        )
                    } else {
                        Spacer(Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
}

@Composable
private fun PageImage(
    pageIndex: Int,
    targetWidthPx: Int,
    loader: PdfPageLoader
) {
    var bitmap by remember(pageIndex, targetWidthPx) { mutableStateOf<Bitmap?>(null) }
    var error by remember(pageIndex, targetWidthPx) { mutableStateOf<Throwable?>(null) }

    LaunchedEffect(pageIndex, targetWidthPx) {
        bitmap = null
        error = null
        try {
            bitmap = loader.loadPage(pageIndex, targetWidthPx)
        } catch (ce: CancellationException) {
            // 스크롤/리컴포즈로 취소될 수 있음(정상)
            throw ce
        } catch (t: Throwable) {
            error = t
        }
    }

    when {
        bitmap != null -> {
            Image(
                bitmap = requireNotNull(bitmap).asImageBitmap(),
                contentDescription = "page $pageIndex",
                modifier = Modifier.fillMaxWidth()
            )
        }
        error != null -> {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "페이지 로딩 실패: ${error?.message ?: "unknown"}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        else -> {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
    }
}
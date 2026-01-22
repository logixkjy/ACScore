package com.kandc.acscore.viewer.data

import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 백그라운드에서 페이지 로딩 + 캐시 적용
 */
class PdfPageLoader(
    private val holder: AndroidPdfRendererHolder,
    private val cache: PdfBitmapCache,
    private val fileKey: String
) {
    suspend fun loadPage(pageIndex: Int, targetWidthPx: Int): Bitmap = withContext(Dispatchers.Default) {
        val key = "$fileKey:$pageIndex:$targetWidthPx"
        cache.get(key)?.let { return@withContext it }

        val bmp = holder.renderPageFitWidth(pageIndex, targetWidthPx)
        cache.put(key, bmp)
        bmp
    }
}
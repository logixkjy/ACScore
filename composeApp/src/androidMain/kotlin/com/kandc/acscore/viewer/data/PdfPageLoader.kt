package com.kandc.acscore.viewer.data

import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PdfPageLoader(
    private val holder: AndroidPdfRendererHolder,
    private val cache: PdfBitmapCache,
    private val fileKey: String
) {
    suspend fun loadPage(pageIndex: Int, targetWidthPx: Int): Bitmap {
        val key = "$fileKey:$pageIndex:$targetWidthPx"

        cache.get(key)?.let { return it }

        val bmp = withContext(Dispatchers.IO) {
            holder.renderPage(pageIndex, targetWidthPx)
        }

        cache.put(key, bmp)
        return bmp
    }
}
package com.kandc.acscore.viewer.data

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import java.io.File

/**
 * PdfRenderer / ParcelFileDescriptor 리소스 안전 관리.
 * - open/close는 UI 생명주기(DisposableEffect)에서 호출 권장
 * - page 렌더링 시 page.open/close는 함수 내부에서 안전 처리
 */
class AndroidPdfRendererHolder(
    private val filePath: String
) {
    private var pfd: ParcelFileDescriptor? = null
    private var renderer: PdfRenderer? = null

    fun open() {
        if (renderer != null) return
        val file = File(filePath)
        require(file.exists()) { "PDF file not found: $filePath" }

        pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        renderer = PdfRenderer(requireNotNull(pfd))
    }

    fun close() {
        runCatching { renderer?.close() }
        runCatching { pfd?.close() }
        renderer = null
        pfd = null
    }

    fun pageCount(): Int = renderer?.pageCount ?: 0

    fun renderPageFitWidth(pageIndex: Int, targetWidthPx: Int): Bitmap {
        val r = renderer ?: error("PdfRenderer not opened. Call open() first.")
        require(pageIndex in 0 until r.pageCount) { "Invalid pageIndex=$pageIndex" }
        require(targetWidthPx > 0) { "targetWidthPx must be > 0" }

        val page = r.openPage(pageIndex)
        try {
            val pageW = page.width.toFloat()
            val pageH = page.height.toFloat()

            val scale = targetWidthPx / pageW
            val targetHeightPx = (pageH * scale).toInt().coerceAtLeast(1)

            // 안전장치(너무 큰 PDF에서 OOM 방지). 필요 시 조정.
            val safeW = targetWidthPx.coerceAtMost(4096)
            val safeH = targetHeightPx.coerceAtMost(4096)

            val bitmap = Bitmap.createBitmap(safeW, safeH, Bitmap.Config.ARGB_8888)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            return bitmap
        } finally {
            page.close()
        }
    }
}
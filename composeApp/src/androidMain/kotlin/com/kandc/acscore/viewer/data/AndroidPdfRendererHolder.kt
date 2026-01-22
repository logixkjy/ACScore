package com.kandc.acscore.viewer.data

import android.graphics.Bitmap
import android.graphics.Rect
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import kotlin.math.roundToInt

class AndroidPdfRendererHolder(
    private val filePath: String
) {
    private var pfd: ParcelFileDescriptor? = null
    private var renderer: PdfRenderer? = null

    // ✅ 핵심: 한 PDF에 대한 openPage/render/close는 항상 1개씩만 수행
    private val renderMutex = Mutex()

    fun open() {
        if (renderer != null) return
        val file = File(filePath)
        require(file.exists()) { "PDF file not found: $filePath" }

        pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        renderer = PdfRenderer(requireNotNull(pfd))
    }

    fun close() {
        // ✅ close는 최대한 안전하게
        runCatching { renderer?.close() }
        renderer = null

        runCatching { pfd?.close() }
        pfd = null
    }

    fun pageCount(): Int {
        val r = renderer ?: return 0
        return r.pageCount
    }

    /**
     * ✅ 렌더링은 반드시 page.close() 보장 + Mutex로 직렬화
     */
    suspend fun renderPage(
        pageIndex: Int,
        targetWidthPx: Int
    ): Bitmap = renderMutex.withLock {
        val r = renderer ?: throw IllegalStateException("PdfRenderer is not opened")

        var page: PdfRenderer.Page? = null
        try {
            page = r.openPage(pageIndex)

            val width = targetWidthPx.coerceAtLeast(1)
            val scale = width.toFloat() / page.width.toFloat()
            val height = (page.height * scale).roundToInt().coerceAtLeast(1)

            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val dest = Rect(0, 0, width, height)

            page.render(
                bitmap,
                dest,
                null,
                PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY
            )

            bitmap
        } finally {
            // ✅ 이거 빠지면 네가 본 예외가 그대로 터짐
            runCatching { page?.close() }
        }
    }
}
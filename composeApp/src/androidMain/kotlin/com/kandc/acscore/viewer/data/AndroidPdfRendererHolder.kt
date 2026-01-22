package com.kandc.acscore.viewer.data

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import java.io.File

class AndroidPdfRendererHolder(
    private val filePath: String
) {
    private var pfd: ParcelFileDescriptor? = null
    private var renderer: PdfRenderer? = null

    fun open() {
        if (renderer != null) return

        synchronized(this) {
            if (renderer != null) return

            val file = File(filePath)
            require(file.exists()) { "PDF not found: $filePath" }

            val newPfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            val newRenderer = PdfRenderer(newPfd)

            pfd = newPfd
            renderer = newRenderer
        }
    }

    fun pageCount(): Int = renderer?.pageCount ?: 0

    fun renderPage(pageIndex: Int, targetWidthPx: Int): Bitmap {
        val r = renderer ?: throw IllegalStateException("PdfRenderer is not opened")
        if (pageIndex !in 0 until r.pageCount) {
            throw IndexOutOfBoundsException("pageIndex=$pageIndex, pageCount=${r.pageCount}")
        }

        var page: PdfRenderer.Page? = null
        try {
            page = r.openPage(pageIndex)

            val srcW = page.width.coerceAtLeast(1)
            val srcH = page.height.coerceAtLeast(1)

            val scale = targetWidthPx.toFloat() / srcW.toFloat()
            val outW = (srcW * scale).toInt().coerceAtLeast(1)
            val outH = (srcH * scale).toInt().coerceAtLeast(1)

            val bitmap = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            return bitmap
        } finally {
            runCatching { page?.close() }
        }
    }

    fun close() {
        synchronized(this) {
            runCatching { renderer?.close() }
            renderer = null

            runCatching { pfd?.close() }
            pfd = null
        }
    }
}
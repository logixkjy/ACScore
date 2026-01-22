package com.kandc.acscore.viewer.data

import android.graphics.Bitmap
import androidx.collection.LruCache

/**
 * 페이지 렌더링 결과(Bitmap) 캐시.
 * - key = "$fileKey:$pageIndex:$widthPx"
 */
class PdfBitmapCache(maxBytes: Int) {

    private val cache = object : LruCache<String, Bitmap>(maxBytes) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    fun get(key: String): Bitmap? = cache.get(key)

    fun put(key: String, bitmap: Bitmap) {
        cache.put(key, bitmap)
    }

    fun clear() {
        cache.evictAll()
    }

    companion object {
        /**
         * 대략 앱 메모리/기기 성능에 따라 조절.
         * 우선 32MB로 시작.
         */
        fun default(): PdfBitmapCache = PdfBitmapCache(maxBytes = 32 * 1024 * 1024)
    }
}
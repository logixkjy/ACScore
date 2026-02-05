package com.kandc.acscore.data.viewer

import android.graphics.Bitmap
import android.util.LruCache

class PdfBitmapCache private constructor(
    maxSizeBytes: Int
) {

    private val cache = object : LruCache<String, Bitmap>(maxSizeBytes) {
        override fun sizeOf(key: String, value: Bitmap): Int {
            return value.byteCount
        }

        override fun entryRemoved(
            evicted: Boolean,
            key: String,
            oldValue: Bitmap,
            newValue: Bitmap?
        ) {
            // ❌ Compose가 draw 중인 Bitmap일 수 있어서 recycle 금지
            // if (!oldValue.isRecycled) oldValue.recycle()
        }
    }

    fun get(key: String): Bitmap? = cache.get(key)

    fun put(key: String, bitmap: Bitmap) {
        cache.put(key, bitmap)
    }

    fun remove(key: String) {
        cache.remove(key)
    }

    fun clear() {
        cache.evictAll()
    }

    /**
     * ✅ 특정 PDF(fileKey)만 정리
     * key 형식: "$fileKey:$page:$width"
     */
    fun clearByFile(fileKey: String) {
        val keys = mutableListOf<String>()
        cache.snapshot().keys.forEach { key ->
            if (key.startsWith("$fileKey:")) {
                keys += key
            }
        }
        keys.forEach { cache.remove(it) }
    }

    companion object {
        @Volatile
        private var INSTANCE: PdfBitmapCache? = null

        fun default(): PdfBitmapCache {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: create().also { INSTANCE = it }
            }
        }

        private fun create(): PdfBitmapCache {
            val maxMemory = Runtime.getRuntime().maxMemory().toInt()
            val cacheSize = maxMemory / 10
            return PdfBitmapCache(cacheSize)
        }
    }
}
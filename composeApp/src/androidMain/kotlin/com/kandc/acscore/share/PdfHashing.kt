package com.kandc.acscore.share

import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

object PdfHashing {

    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { fis ->
            val buf = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = fis.read(buf)
                if (read <= 0) break
                digest.update(buf, 0, read)
            }
        }
        val hex = digest.digest().joinToString("") { b -> "%02x".format(b) }
        return "sha256:$hex"
    }
}
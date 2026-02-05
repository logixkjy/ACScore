package com.kandc.acscore.share

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object ZipBundler {

    data class FileEntry(
        val zipPath: String,
        val sourceFile: File
    )

    fun createZip(outputZip: File, entries: List<FileEntry>) {
        outputZip.parentFile?.mkdirs()

        FileOutputStream(outputZip).use { fos ->
            ZipOutputStream(fos).use { zos ->
                entries.forEach { entry ->
                    val zipEntry = ZipEntry(entry.zipPath)
                    zos.putNextEntry(zipEntry)

                    FileInputStream(entry.sourceFile).use { fis ->
                        val buf = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            val read = fis.read(buf)
                            if (read <= 0) break
                            zos.write(buf, 0, read)
                        }
                    }

                    zos.closeEntry()
                }
                zos.finish()
            }
        }
    }
}
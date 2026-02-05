package com.kandc.acscore.share

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

object ShareAcset {

    fun share(context: Context, acsetFile: File, title: String) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            acsetFile
        )

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/octet-stream" // 카톡/문자 호환성 최우선
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        context.startActivity(Intent.createChooser(intent, title))
    }
}
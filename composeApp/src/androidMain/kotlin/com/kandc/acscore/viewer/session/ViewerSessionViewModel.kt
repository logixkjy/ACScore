package com.kandc.acscore.viewer.session

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kandc.acscore.viewer.data.PdfBitmapCache
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class ViewerSessionViewModel(app: Application) : AndroidViewModel(app) {

    private val cache = PdfBitmapCache.default()

    val store = ViewerSessionStore(
        onTabClosed = { filePath ->
            val fileKey = filePath.hashCode().toString()
            cache.clearByFile(fileKey)
        }
    )

    private val prefs by lazy {
        app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private var saveJob: Job? = null

    init {
        // 1) 복원
        restore()

        // 2) 변경 감지 → 디바운스로 저장
        viewModelScope.launch {
            store.state.collect {
                scheduleSave()
            }
        }
    }

    private fun restore() {
        val raw = prefs.getString(KEY_SNAPSHOT, null) ?: return
        val snapshot = ViewerSessionSnapshotJson.fromJson(raw) ?: return
        store.restoreFromSnapshot(snapshot)
    }

    private fun scheduleSave() {
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            // 너무 자주 쓰지 않도록 디바운스
            delay(250)
            val snapshot = store.toSnapshot()
            val json = ViewerSessionSnapshotJson.toJson(snapshot)
            prefs.edit().putString(KEY_SNAPSHOT, json).apply()
        }
    }

    companion object {
        private const val PREFS_NAME = "viewer_session"
        private const val KEY_SNAPSHOT = "snapshot_json"
    }
}
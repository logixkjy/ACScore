package com.kandc.acscore.session.viewer

import org.json.JSONArray
import org.json.JSONObject

object ViewerSessionSnapshotJson {

    fun toJson(snapshot: ViewerSessionSnapshot): String {
        val root = JSONObject()
        root.put("activeTabId", snapshot.activeTabId ?: JSONObject.NULL)

        val arr = JSONArray()
        snapshot.tabs.forEach { tab ->
            val o = JSONObject()
            o.put("tabId", tab.tabId)
            o.put("scoreId", tab.scoreId)
            o.put("title", tab.title)
            o.put("filePath", tab.filePath)
            o.put("lastPage", tab.lastPage) // ✅ 추가
            arr.put(o)
        }
        root.put("tabs", arr)
        return root.toString()
    }

    fun fromJson(raw: String): ViewerSessionSnapshot? {
        return try {
            val root = JSONObject(raw)

            val activeTabId =
                if (root.isNull("activeTabId")) null else root.optString("activeTabId", null)

            val tabsArr = root.optJSONArray("tabs") ?: JSONArray()
            val tabs = buildList {
                for (i in 0 until tabsArr.length()) {
                    val o = tabsArr.optJSONObject(i) ?: continue

                    val tabId = o.optString("tabId", "")
                    val scoreId = o.optString("scoreId", "")
                    val title = o.optString("title", "")
                    val filePath = o.optString("filePath", "")
                    val lastPage = o.optInt("lastPage", 0) // ✅ 추가

                    if (tabId.isBlank() || filePath.isBlank()) continue

                    add(
                        ViewerSessionSnapshot.TabSnapshot(
                            tabId = tabId,
                            scoreId = scoreId,
                            title = title,
                            filePath = filePath,
                            lastPage = lastPage
                        )
                    )
                }
            }

            ViewerSessionSnapshot(tabs = tabs, activeTabId = activeTabId)
        } catch (_: Throwable) {
            null
        }
    }
}
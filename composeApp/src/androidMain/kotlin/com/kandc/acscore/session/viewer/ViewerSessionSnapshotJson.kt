package com.kandc.acscore.session.viewer

import org.json.JSONArray
import org.json.JSONObject

object ViewerSessionSnapshotJson {

    fun toJson(snapshot: ViewerSessionSnapshot): String {
        val root = JSONObject()
        root.put("activeTabId", snapshot.activeTabId)

        val tabsArr = JSONArray()
        snapshot.tabs.forEach { tab ->
            val obj = JSONObject()
            obj.put("tabId", tab.tabId)
            obj.put("scoreId", tab.scoreId)
            obj.put("title", tab.title)
            obj.put("filePath", tab.filePath)
            obj.put("lastPage", tab.lastPage)
            tabsArr.put(obj)
        }
        root.put("tabs", tabsArr)

        return root.toString()
    }

    fun fromJson(raw: String): ViewerSessionSnapshot? {
        return runCatching {
            val root = JSONObject(raw)
            val activeTabId = root.optString("activeTabId", null)

            val tabsArr = root.optJSONArray("tabs") ?: JSONArray()
            val tabs = buildList {
                for (i in 0 until tabsArr.length()) {
                    val obj = tabsArr.optJSONObject(i) ?: continue
                    add(
                        ViewerSessionSnapshot.TabSnapshot(
                            tabId = obj.optString("tabId"),
                            scoreId = obj.optString("scoreId"),
                            title = obj.optString("title"),
                            filePath = obj.optString("filePath"),
                            lastPage = obj.optInt("lastPage", 0)
                        )
                    )
                }
            }

            ViewerSessionSnapshot(tabs = tabs, activeTabId = activeTabId)
        }.getOrNull()
    }
}
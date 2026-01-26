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

            // ✅ 추가: 탭 타이틀(세트리스트명이면 여기로)
            // null이면 아예 안 넣어도 되지만, 넣어도 무방
            obj.put("tabTitle", tab.tabTitle)

            // ✅ 추가: setlistId
            obj.put("setlistId", tab.setlistId)

            // ✅ 추가: setlist 이어보기 요청 리스트
            val setlist = tab.setlist
            if (setlist != null) {
                val setlistArr = JSONArray()
                setlist.forEach { r ->
                    val rObj = JSONObject()
                    rObj.put("scoreId", r.scoreId)
                    rObj.put("title", r.title)
                    rObj.put("filePath", r.filePath)
                    setlistArr.put(rObj)
                }
                obj.put("setlist", setlistArr)
            }

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

                    // ✅ setlist 복원 (없으면 null)
                    val setlistArr = obj.optJSONArray("setlist")
                    val setlist: List<ViewerSessionSnapshot.RequestSnapshot>? =
                        if (setlistArr != null) {
                            buildList {
                                for (j in 0 until setlistArr.length()) {
                                    val rObj = setlistArr.optJSONObject(j) ?: continue
                                    add(
                                        ViewerSessionSnapshot.RequestSnapshot(
                                            scoreId = rObj.optString("scoreId"),
                                            title = rObj.optString("title"),
                                            filePath = rObj.optString("filePath")
                                        )
                                    )
                                }
                            }.takeIf { it.isNotEmpty() }
                        } else {
                            null
                        }

                    add(
                        ViewerSessionSnapshot.TabSnapshot(
                            tabId = obj.optString("tabId"),
                            scoreId = obj.optString("scoreId"),
                            title = obj.optString("title"),
                            filePath = obj.optString("filePath"),
                            lastPage = obj.optInt("lastPage", 0),

                            // ✅ 추가 필드들 (없으면 null로 안전)
                            tabTitle = obj.optString("tabTitle", null),
                            setlistId = obj.optString("setlistId", null),

                            // ✅ setlist
                            setlist = setlist
                        )
                    )
                }
            }

            ViewerSessionSnapshot(tabs = tabs, activeTabId = activeTabId)
        }.getOrNull()
    }
}
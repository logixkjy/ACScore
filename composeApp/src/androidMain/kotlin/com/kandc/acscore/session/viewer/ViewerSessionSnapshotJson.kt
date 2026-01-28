package com.kandc.acscore.session.viewer

import org.json.JSONArray
import org.json.JSONObject

object ViewerSessionSnapshotJson {

    private const val KIND_LIBRARY = "library"
    private const val KIND_SETLIST_DETAIL = "setlistDetail"

    fun toJson(snapshot: ViewerSessionSnapshot): String {
        val root = JSONObject()
        root.put("activeTabId", snapshot.activeTabId)

        // ✅ lastPicker
        snapshot.lastPicker?.let { p ->
            val obj = JSONObject()
            obj.put("kind", p.kind)
            obj.put("setlistId", p.setlistId)
            root.put("lastPicker", obj)
        }

        val tabsArr = JSONArray()
        snapshot.tabs.forEach { tab ->
            val obj = JSONObject()
            obj.put("tabId", tab.tabId)
            obj.put("scoreId", tab.scoreId)
            obj.put("title", tab.title)
            obj.put("filePath", tab.filePath)
            obj.put("lastPage", tab.lastPage)

            obj.put("tabTitle", tab.tabTitle)
            obj.put("setlistId", tab.setlistId)

            // ✅ tab.picker
            tab.picker?.let { p ->
                val pObj = JSONObject()
                pObj.put("kind", p.kind)
                pObj.put("setlistId", p.setlistId)
                obj.put("picker", pObj)
            }

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

            val lastPickerObj = root.optJSONObject("lastPicker")
            val lastPicker = lastPickerObj?.let { parsePicker(it) }

            val tabsArr = root.optJSONArray("tabs") ?: JSONArray()
            val tabs = buildList {
                for (i in 0 until tabsArr.length()) {
                    val obj = tabsArr.getJSONObject(i)

                    val setlistArr = obj.optJSONArray("setlist")
                    val setlist = setlistArr?.let { arr ->
                        buildList {
                            for (j in 0 until arr.length()) {
                                val rObj = arr.getJSONObject(j)
                                add(
                                    ViewerSessionSnapshot.RequestSnapshot(
                                        scoreId = rObj.getString("scoreId"),
                                        title = rObj.getString("title"),
                                        filePath = rObj.getString("filePath")
                                    )
                                )
                            }
                        }
                    }

                    val pickerObj = obj.optJSONObject("picker")
                    val picker = pickerObj?.let { parsePicker(it) }

                    add(
                        ViewerSessionSnapshot.TabSnapshot(
                            tabId = obj.getString("tabId"),
                            scoreId = obj.getString("scoreId"),
                            title = obj.getString("title"),
                            filePath = obj.getString("filePath"),
                            lastPage = obj.optInt("lastPage", 0),
                            tabTitle = obj.optString("tabTitle", null),
                            setlist = setlist,
                            setlistId = obj.optString("setlistId", null),
                            picker = picker
                        )
                    )
                }
            }

            ViewerSessionSnapshot(
                tabs = tabs,
                activeTabId = activeTabId,
                lastPicker = lastPicker
            )
        }.getOrNull()
    }

    private fun parsePicker(obj: JSONObject): ViewerSessionSnapshot.PickerSnapshot? {
        val kind = obj.optString("kind", null) ?: return null
        val setlistId = obj.optString("setlistId", null)
        return when (kind) {
            KIND_LIBRARY -> ViewerSessionSnapshot.PickerSnapshot(kind = KIND_LIBRARY, setlistId = null)
            KIND_SETLIST_DETAIL -> ViewerSessionSnapshot.PickerSnapshot(kind = KIND_SETLIST_DETAIL, setlistId = setlistId)
            else -> null
        }
    }
}
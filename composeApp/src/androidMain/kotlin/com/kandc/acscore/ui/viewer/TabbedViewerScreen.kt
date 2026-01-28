package com.kandc.acscore.ui.viewer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.kandc.acscore.session.viewer.ViewerPickerContext
import com.kandc.acscore.session.viewer.ViewerSessionStore

@Composable
fun TabbedViewerScreen(
    sessionStore: ViewerSessionStore,
    onRequestOpenPicker: (ViewerPickerContext) -> Unit,
    modifier: Modifier = Modifier
) {
    val state by sessionStore.state.collectAsState()

    val active = remember(state.tabs, state.activeTabId) {
        state.tabs.firstOrNull { it.tabId == state.activeTabId } ?: state.tabs.firstOrNull()
    }

    var controlsVisible by remember { mutableStateOf(false) }
    var pageCount by remember(state.activeTabId) { mutableStateOf<Int?>(null) }

    Column(modifier.fillMaxSize()) {

        if (controlsVisible) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 10.dp)
                    .padding(top = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = {
                        val ctx = active?.returnPicker ?: state.lastPicker
                        onRequestOpenPicker(ctx)
                    }
                ) { Text("Library") }

                Spacer(Modifier.weight(1f))
                TextButton(onClick = { controlsVisible = false }) { Text("Hide") }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                state.tabs.forEach { tab ->
                    val selected = tab.tabId == active?.tabId

                    Surface(
                        tonalElevation = if (selected) 4.dp else 1.dp,
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = tab.tabTitle,
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier
                                    .padding(end = 8.dp)
                                    .clickable { sessionStore.setActive(tab.tabId) }
                            )
                            Text(
                                text = "✕",
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.clickable { sessionStore.closeTab(tab.tabId) }
                            )
                        }
                    }
                }
            }

            Divider()
        }

        if (active == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("열려있는 악보가 없어요.")
                    Spacer(Modifier.height(8.dp))
                    TextButton(
                        onClick = { onRequestOpenPicker(state.lastPicker) }
                    ) { Text("Library 열기") }
                }
            }
            return
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(active.tabId) {
                    detectTapGestures(onTap = { controlsVisible = !controlsVisible })
                }
        ) {
            val setlistReqs = active.setlistRequests

            if (setlistReqs != null) {
                val latestTabId by rememberUpdatedState(active.tabId)
                SetlistPdfViewerScreen(
                    requests = setlistReqs,
                    initialScoreId = active.request.scoreId,
                    modifier = Modifier.fillMaxSize(),
                    initialGlobalPage = active.lastPage,
                    onGlobalPageChanged = { page ->
                        sessionStore.updateLastPage(latestTabId, page)
                    },
                    onPageCountReady = { count ->
                        pageCount = count
                    },
                    jumpToGlobalPage = active.jumpToGlobalPage,
                    jumpToken = active.jumpToken,
                    jumpToScoreId = active.jumpToScoreId,
                    jumpTokenScore = active.jumpToken
                )

                ViewerTransparentControlsOverlay(
                    enabled = true,
                    onPrev = {
                        sessionStore.requestJump(
                            active.tabId,
                            active.lastPage - 1
                        )
                    },
                    onNext = {
                        sessionStore.requestJump(
                            active.tabId,
                            active.lastPage + 1
                        )
                    },
                    onFirst = {
                        sessionStore.requestJump(
                            active.tabId,
                            0
                        )
                    },
                )

                ViewerPageIndicatorOverlay(
                    currentPage = active.lastPage,
                    pageCount = pageCount
                )
            } else {
                PdfViewerScreen(
                    request = active.request,
                    modifier = Modifier.fillMaxSize(),
                    initialPage = active.lastPage,
                    onPageChanged = { page ->
                        sessionStore.updateLastPage(active.tabId, page)
                    },
                    onPageCountReady = { count ->   // ← 있으면 연결
                        pageCount = count
                    },
                    jumpToGlobalPage = active.jumpToGlobalPage,
                    jumpToken = active.jumpToken
                )

                ViewerTransparentControlsOverlay(
                    enabled = true,
                    onPrev = {
                        sessionStore.requestJump(
                            active.tabId,
                            active.lastPage - 1
                        )
                     },
                    onNext = {
                        sessionStore.requestJump(
                            active.tabId,
                            active.lastPage + 1
                        )
                     },
                    onFirst = {
                        sessionStore.requestJump(
                            active.tabId,
                            0
                        )
                      },
                )

                ViewerPageIndicatorOverlay(
                    currentPage = active.lastPage,
                    pageCount = pageCount
                )
            }
        }
    }
}
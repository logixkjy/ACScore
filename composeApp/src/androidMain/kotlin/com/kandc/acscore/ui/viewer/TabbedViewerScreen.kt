package com.kandc.acscore.ui.viewer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.kandc.acscore.session.viewer.ViewerPickerContext
import com.kandc.acscore.session.viewer.ViewerSessionStore
import kotlinx.coroutines.delay

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

    val layout = rememberViewerLayout()
    val step = if (layout.columns == 2) 2 else 1

    var controlsVisible by remember { mutableStateOf(false) }
    var pageCount by remember(state.activeTabId) { mutableStateOf<Int?>(null) }

    LaunchedEffect(controlsVisible, state.activeTabId) {
        if (!controlsVisible) return@LaunchedEffect
        delay(5_000)
        controlsVisible = false
    }

    if (active == null) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("열려있는 악보가 없어요.")
                Spacer(Modifier.height(8.dp))
                Text(
                    "목록 열기",
                    modifier = Modifier.clickable { onRequestOpenPicker(state.lastPicker) }
                )
            }
        }
        return
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(active.tabId) {
                detectTapGestures(onTap = { controlsVisible = !controlsVisible })
            }
    ) {
        // 1) Viewer
        val setlistReqs = active.setlistRequests
        if (setlistReqs != null) {
            val latestTabId by rememberUpdatedState(active.tabId)

            SetlistPdfViewerScreen(
                requests = setlistReqs,
                modifier = Modifier.fillMaxSize(),
                currentGlobalPage = active.lastPage,
                onGlobalPageChanged = { page -> sessionStore.updateLastPage(latestTabId, page) },
                onPageCountReady = { count -> pageCount = count },
                jumpToScoreId = active.jumpToScoreId,
                jumpTokenScore = active.jumpToken,
                jumpToGlobalPage = active.jumpToGlobalPage,
                jumpTokenGlobal = active.jumpToken
            )
        } else {
            PdfViewerScreen(
                request = active.request,
                modifier = Modifier.fillMaxSize(),
                currentGlobalPage = active.lastPage,
                onGlobalPageChanged = { page -> sessionStore.updateLastPage(active.tabId, page) },
                onPageCountReady = { count -> pageCount = count },
                jumpToGlobalPage = active.jumpToGlobalPage,
                jumpToken = active.jumpToken
            )
        }

        // 2) Transparent controls
        ViewerTransparentControlsOverlay(
            enabled = !controlsVisible,
            onLibrary = { onRequestOpenPicker(state.lastPicker) },
            onPrev = {
                val max = (pageCount ?: Int.MAX_VALUE) - 1
                val target = (active.lastPage - step).coerceAtLeast(0).coerceAtMost(max.coerceAtLeast(0))
                sessionStore.requestJump(active.tabId, target)
            },
            onNext = {
                val max = (pageCount ?: Int.MAX_VALUE) - 1
                val target = (active.lastPage + step).coerceAtLeast(0).coerceAtMost(max.coerceAtLeast(0))
                sessionStore.requestJump(active.tabId, target)
            },
            onFirst = { sessionStore.requestJump(active.tabId, 0) },
            onLast = {
                val lastIndex = (pageCount ?: 1) - 1
                if (lastIndex >= 0) sessionStore.requestJump(active.tabId, lastIndex)
            }
        )

        ViewerPageIndicatorOverlay(
            currentPage = active.lastPage,
            pageCount = pageCount
        )

        // ✅ 전체 dim (원하면 유지)
        if (controlsVisible) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(50f)
                    .background(Color.Black.copy(alpha = 0.28f))
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) {
                        controlsVisible = false
                    }
            )
        }

        // 4) Nav + Tabs overlay
        if (controlsVisible) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .zIndex(100f)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.88f))
                        .padding(horizontal = 10.dp)
                        .padding(top = 10.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = MaterialTheme.shapes.large,
                        tonalElevation = 1.dp,
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.80f),
                        modifier = Modifier
                            .clickable(
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() }
                            ) {
                                onRequestOpenPicker(state.lastPicker)
                                controlsVisible = false
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // ✅ 왼쪽: 뒤로 화살표
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = null,
                                modifier = Modifier.padding(end = 10.dp)
                            )

                            // ✅ 오른쪽: 아이콘 + 텍스트 (위/아래)
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.List,
                                    contentDescription = null
                                )
//                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = "목록",
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }
                    }

                    Spacer(Modifier.weight(1f))

                    Text(
                        text = active.tabTitle,
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )

                    Spacer(Modifier.weight(1f))

                    // ✅ Hide 버튼 삭제 (요구사항)
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.78f))
                        .padding(horizontal = 10.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    state.tabs.forEach { tab ->
                        val selected = tab.tabId == active.tabId

                        Surface(
                            tonalElevation = if (selected) 6.dp else 0.dp,
                            shadowElevation = if (selected) 3.dp else 0.dp,
                            shape = MaterialTheme.shapes.large,
                            color = if (selected)
                                MaterialTheme.colorScheme.surface
                            else
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.55f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = tab.tabTitle,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (selected)
                                        MaterialTheme.colorScheme.onSurface
                                    else
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                                    modifier = Modifier
                                        .padding(end = 10.dp)
                                        .clickable {
                                            sessionStore.setActive(tab.tabId)
                                            controlsVisible = true
                                        }
                                )

                                Text(
                                    text = "✕",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (selected)
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.80f)
                                    else
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.40f),
                                    modifier = Modifier.clickable {
                                        sessionStore.closeTab(tab.tabId)
                                        controlsVisible = true
                                    }
                                )
                            }
                        }
                    }
                }

                Divider()
            }
        }
    }
}
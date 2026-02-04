package com.kandc.acscore.ui.viewer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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

    // ✅ 태블릿 가로(2-up)일 때만 2페이지 단위로 이동, 그 외 1페이지
    val step = if (layout.columns == 2) 2 else 1

    var controlsVisible by remember { mutableStateOf(false) }

    // ✅ 단일: PDF 페이지수 / 세트: totalPages
    var pageCount by remember(state.activeTabId) { mutableStateOf<Int?>(null) }

    // ✅ 컨트롤 노출 시 자동 숨김
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
        // ---------------------------------------------------------------------
        // 1) Viewer (맨 아래 레이어)
        // ---------------------------------------------------------------------
        val setlistReqs = active.setlistRequests
        if (setlistReqs != null) {
            val latestTabId by rememberUpdatedState(active.tabId)

            SetlistPdfViewerScreen(
                requests = setlistReqs,
                initialScoreId = active.request.scoreId,
                modifier = Modifier.fillMaxSize(),
                initialGlobalPage = active.lastPage,
                onGlobalPageChanged = { page -> sessionStore.updateLastPage(latestTabId, page) },
                onPageCountReady = { total -> pageCount = total },

                // ✅ 곡 선택 점프(세트 상세 목록)
                jumpToScoreId = active.jumpToScoreId,
                jumpTokenScore = active.jumpTokenScore,

                // ✅ 페이지 점프(이전/다음/처음/마지막)
                jumpToGlobalPage = active.jumpToGlobalPage,
                jumpTokenPage = active.jumpTokenPage
            )
        } else {
            PdfViewerScreen(
                request = active.request,
                modifier = Modifier.fillMaxSize(),
                initialPage = active.lastPage,
                onPageChanged = { page -> sessionStore.updateLastPage(active.tabId, page) },
                onPageCountReady = { count -> pageCount = count },
                jumpToGlobalPage = active.jumpToGlobalPage,
                jumpToken = active.jumpTokenPage
            )
        }

        // ---------------------------------------------------------------------
        // 2) Transparent controls (컨트롤 노출 중엔 비활성)
        // ---------------------------------------------------------------------
        ViewerTransparentControlsOverlay(
            enabled = !controlsVisible,

            // ✅ 스펙: “현재 보고있는 파일이 속한 목록”이 아니라 “마지막으로 보고 있던 목록”을 연다
            onLibrary = { onRequestOpenPicker(state.lastPicker) },

            onPrev = {
                val max = (pageCount ?: Int.MAX_VALUE) - 1
                val target = (active.lastPage - step)
                    .coerceAtLeast(0)
                    .coerceAtMost(max.coerceAtLeast(0))
                sessionStore.requestJump(active.tabId, target)
            },
            onNext = {
                val max = (pageCount ?: Int.MAX_VALUE) - 1
                val target = (active.lastPage + step)
                    .coerceAtLeast(0)
                    .coerceAtMost(max.coerceAtLeast(0))
                sessionStore.requestJump(active.tabId, target)
            },
            onFirst = {
                sessionStore.requestJump(active.tabId, 0)
            },
            onLast = {
                val lastIndex = ((pageCount ?: return@ViewerTransparentControlsOverlay) - 1)
                if (lastIndex >= 0) sessionStore.requestJump(active.tabId, lastIndex)
            }
        )

        ViewerPageIndicatorOverlay(
            currentPage = active.lastPage,
            pageCount = pageCount
        )

        // ---------------------------------------------------------------------
        // 3) Dim layer (컨트롤 표시 중: 전체 딤 + 터치하면 hide)
        // ---------------------------------------------------------------------
        if (controlsVisible) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(50f)
                    .background(Color.Black.copy(alpha = 0.28f))
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) { controlsVisible = false }
            )
        }

        // ---------------------------------------------------------------------
        // 4) Nav + Tabs overlay (최상단)
        // ---------------------------------------------------------------------
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
                    // < + (아이콘 위 / 텍스트 아래) 목록 버튼
                    Surface(
                        shape = MaterialTheme.shapes.large,
                        tonalElevation = 1.dp,
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.80f),
                        modifier = Modifier.clickable(
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
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = null,
                                modifier = Modifier.padding(end = 10.dp)
                            )
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(imageVector = Icons.Filled.List, contentDescription = null)
                                Text(text = "목록", style = MaterialTheme.typography.labelMedium)
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
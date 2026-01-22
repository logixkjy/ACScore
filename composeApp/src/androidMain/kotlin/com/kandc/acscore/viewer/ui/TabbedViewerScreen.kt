package com.kandc.acscore.viewer.ui

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
import com.kandc.acscore.viewer.session.ViewerSessionStore

@Composable
fun TabbedViewerScreen(
    sessionStore: ViewerSessionStore,
    onRequestOpenLibrary: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by sessionStore.state.collectAsState()

    val active = remember(state.tabs, state.activeTabId) {
        state.tabs.firstOrNull { it.tabId == state.activeTabId } ?: state.tabs.firstOrNull()
    }

    var controlsVisible by remember { mutableStateOf(false) }

    Column(modifier.fillMaxSize()) {

        if (controlsVisible) {
            // ✅ 상단 컨트롤 바 (status bar 고려 + 버튼이 위로 쏠리지 않게 "아래로" 내려줌)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()               // ✅ 상태바 영역 확보
                    .padding(horizontal = 10.dp)       // 좌우 패딩
                    .padding(top = 10.dp, bottom = 10.dp), // ✅ 아래로 내려온 느낌(특히 top)
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = onRequestOpenLibrary,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp) // ✅ 버튼 자체도 살짝 "두툼"
                ) { Text("Library") }

                Spacer(Modifier.weight(1f))

                TextButton(
                    onClick = { controlsVisible = false },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                ) { Text("Hide") }
            }

            // 탭 바
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 10.dp, vertical = 8.dp), // ✅ 살짝 여유
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
                                text = tab.request.title,
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
                    TextButton(onClick = onRequestOpenLibrary) { Text("Library 열기") }
                }
            }
            return
        }

        // ✅ PDF 영역 탭으로 컨트롤 토글 (스크롤/스와이프는 pager가 처리)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(active.tabId) {
                    detectTapGestures(onTap = { controlsVisible = !controlsVisible })
                }
        ) {
            PdfViewerScreen(
                request = active.request,
                modifier = Modifier.fillMaxSize(),
                initialPage = active.lastPage,
                onPageChanged = { page ->
                    sessionStore.updateLastPage(active.tabId, page)
                }
            )
        }
    }
}
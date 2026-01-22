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
import androidx.compose.foundation.layout.statusBarsPadding

@Composable
fun TabbedViewerScreen(
    sessionStore: ViewerSessionStore,
    onRequestOpenLibrary: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by sessionStore.state.collectAsState()
    val active = state.tabs.firstOrNull { it.tabId == state.activeTabId } ?: state.tabs.firstOrNull()

    var controlsVisible by remember { mutableStateOf(false) }

    Column(modifier.fillMaxSize()) {

        if (controlsVisible) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onRequestOpenLibrary) { Text("Library") }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { controlsVisible = false }) { Text("Hide") }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                state.tabs.forEach { tab ->
                    val selected = tab.tabId == active?.tabId

                    Surface(
                        tonalElevation = if (selected) 4.dp else 1.dp,
                        shape = MaterialTheme.shapes.medium,
                        color = if (selected)
                            MaterialTheme.colorScheme.primaryContainer
                        else
                            MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = tab.request.title,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (selected)
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .padding(end = 8.dp)
                                    .clickable { sessionStore.setActive(tab.tabId) }
                            )
                            Text(
                                text = "✕",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (selected)
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant,
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

        // ✅ PDF 영역 탭으로 컨트롤 토글 (스크롤은 그대로)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { controlsVisible = !controlsVisible })
                }
        ) {
            PdfViewerScreen(
                request = active.request,
                modifier = Modifier.fillMaxSize(),
                initialPage = active.lastPage, // ✅ 마지막 페이지 복원
                onPageChanged = { page ->
                    sessionStore.updateLastPage(active.tabId, page) // ✅ 현재 페이지 저장
                }
            )
        }
    }
}
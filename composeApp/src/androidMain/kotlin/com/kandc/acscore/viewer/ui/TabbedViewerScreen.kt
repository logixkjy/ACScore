package com.kandc.acscore.viewer.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kandc.acscore.root.ViewerComponent
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TabbedViewerScreen(
    component: ViewerComponent,
    modifier: Modifier = Modifier
) {
    val tabs by component.tabs.collectAsState()
    val activeId by component.activeTabId.collectAsState()

    val active = tabs.firstOrNull { it.tabId == activeId } ?: tabs.firstOrNull()

    Column(modifier.fillMaxSize()) {
        // Top Bar
        TopAppBar(
            title = { Text(active?.title ?: "Viewer") },
            navigationIcon = {
                IconButton(onClick = component::onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                }
            }
        )

        // Tabs Row (브라우저 탭 느낌의 최소 구현)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            tabs.forEach { tab ->
                val selected = tab.tabId == active?.tabId
                AssistChip(
                    onClick = { component.selectTab(tab.tabId) },
                    label = { Text(tab.title, maxLines = 1) },
                    trailingIcon = {
                        IconButton(
                            onClick = { component.closeTab(tab.tabId) },
                            modifier = Modifier.size(18.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = if (selected)
                            MaterialTheme.colorScheme.primaryContainer
                        else
                            MaterialTheme.colorScheme.surfaceVariant
                    )
                )
            }
        }

        Divider()

        // Active PDF
        if (active != null) {
            PdfViewerScreen(
                request = active.request,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
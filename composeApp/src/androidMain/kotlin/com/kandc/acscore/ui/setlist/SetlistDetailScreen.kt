package com.kandc.acscore.ui.setlist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetlistDetailScreen(
    setlistId: String,
    libraryCandidates: List<ScorePickItem>,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onOpenViewer: (scoreId: String, title: String, fileName: String) -> Unit = { _, _, _ -> },
    onRequestPickFromLibrary: ((currentItemIds: List<String>) -> Unit)? = null,
) {
    val context = LocalContext.current
    val vm = remember(setlistId) { SetlistDetailViewModel(context, setlistId) }
    val setlist by vm.setlist.collectAsState()

    var showPicker by remember { mutableStateOf(false) }

    val current = setlist
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = current?.name ?: "Setlist",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.Close, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            val ids = setlist?.itemIds.orEmpty()
                            if (onRequestPickFromLibrary != null) {
                                onRequestPickFromLibrary(ids)   // ✅ 현재 itemIds 전달
                            } else {
                                showPicker = true // 기존 다이얼로그 방식 유지(백업)
                            }
                        }
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add")
                    }
                }
            )
        }
    ) { padding ->
        val itemIds = current?.itemIds ?: emptyList()
        val map = remember(libraryCandidates) { libraryCandidates.associateBy { it.scoreId } }
        val items = itemIds.mapNotNull { map[it] } // 라이브러리에 없는 건 표시 제외(크래시 방지)

        if (current == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("세트리스트를 불러올 수 없어요.")
            }
            return@Scaffold
        }

        if (items.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("아직 곡이 없어요.\n오른쪽 상단 +로 추가해보세요.")
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(items, key = { it.scoreId }) { item ->
                    ListItem(
                        headlineContent = {
                            Text(item.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        },
                        supportingContent = { Text(item.fileName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        trailingContent = {
                            IconButton(onClick = { vm.remove(item.scoreId) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Remove")
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenViewer(item.scoreId, item.title, item.fileName) }
                    )
                    HorizontalDivider()
                }
            }
        }
    }

    if (showPicker) {
        ScorePickerDialog(
            candidates = libraryCandidates,
            selectedIds = setlist?.itemIds?.toSet() ?: emptySet(),
            onPick = { picked ->
                vm.add(picked.scoreId)
                showPicker = false
            },
            onDismiss = { showPicker = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScorePickerDialog(
    candidates: List<ScorePickItem>,
    selectedIds: Set<String>,
    onPick: (ScorePickItem) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("곡 추가") },
        text = {
            if (candidates.isEmpty()) {
                Text("라이브러리에 악보가 없어요.")
            } else {
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(candidates, key = { it.scoreId }) { item ->
                        val already = selectedIds.contains(item.scoreId)
                        ListItem(
                            headlineContent = { Text(item.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            supportingContent = { Text(item.fileName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            trailingContent = { if (already) Text("추가됨") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !already) { onPick(item) }
                        )
                        HorizontalDivider()
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("닫기") }
        }
    )
}
package com.kandc.acscore.ui.setlist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.consumePositionChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetlistDetailScreen(
    setlistId: String,
    libraryCandidates: List<ScorePickItem>,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onOpenViewer: (scoreId: String, title: String, fileName: String) -> Unit = { _, _, _ -> },
    onRequestPickFromLibrary: ((currentItemIds: List<String>) -> Unit)? = null,
    onReorderItems: (setlistId: String, newItemIds: List<String>) -> Unit,
) {
    val context = LocalContext.current
    val vm = remember(setlistId) { SetlistDetailViewModel(context, setlistId) }
    val setlist by vm.setlist.collectAsState()

    // id -> item
    val map = remember(libraryCandidates) { libraryCandidates.associateBy { it.scoreId } }

    // UI state list (드래그/삭제 즉시 반영)
    val currentIds = setlist?.itemIds.orEmpty()
    val uiIds = remember(setlistId, currentIds) {
        mutableStateListOf<String>().apply { addAll(currentIds) }
    }

    // 백업(다이얼로그 방식) 유지
    var showPicker by rememberSaveable { mutableStateOf(false) }

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
                                onRequestPickFromLibrary(ids)
                            } else {
                                showPicker = true // 백업
                            }
                        }
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add")
                    }
                }
            )
        }
    ) { padding ->
        if (current == null) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("세트리스트를 불러올 수 없어요.")
            }
            return@Scaffold
        }

        if (uiIds.isEmpty()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("아직 곡이 없어요.\n오른쪽 상단 +로 추가해보세요.")
            }
        } else {
            ReorderableIdList(
                ids = uiIds,
                idToTitle = { id -> map[id]?.title ?: "(라이브러리 없음)" },
                idToSubtitle = { id -> map[id]?.fileName ?: "" },
                onClick = { id ->
                    val item = map[id] ?: return@ReorderableIdList
                    onOpenViewer(item.scoreId, item.title, item.fileName)
                },
                onDelete = { id ->
                    // ✅ UI 즉시 반영
                    uiIds.remove(id)

                    // ✅ DB 반영(기존 로직 유지)
                    vm.remove(id)

                    // ✅ itemIds 확정 저장(삭제도 reorder로 확정)
                    onReorderItems(setlistId, uiIds.toList())
                },
                onReorderCommitted = { newOrder ->
                    onReorderItems(setlistId, newOrder)
                },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            )
        }
    }

    // -------------------------
    // (백업) 다이얼로그 기반 추가
    // -------------------------
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

@Composable
private fun ReorderableIdList(
    ids: SnapshotStateList<String>,
    idToTitle: (String) -> String,
    idToSubtitle: (String) -> String,
    onClick: (String) -> Unit,
    onDelete: (String) -> Unit,
    onReorderCommitted: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    var draggingId by remember { mutableStateOf<String?>(null) }
    var dragOffsetY by remember { mutableStateOf(0f) }

    fun move(from: Int, to: Int) {
        if (from == to) return
        val item = ids.removeAt(from)
        ids.add(to, item)
    }

    // ✅ 자동 스크롤 파라미터 (너무 빠르면 조절)
    val edgeThresholdPx = 72f     // 상/하단 이 근처면 스크롤
    val scrollStepPx = 22f        // 한 번에 이동량
    val scrollCooldownMs = 16L    // 60fps 느낌

    LazyColumn(
        state = listState,
        modifier = modifier,
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        itemsIndexed(ids, key = { _, id -> id }) { _, id ->
            val isDragging = (draggingId == id)

            Surface(
                tonalElevation = if (isDragging) 4.dp else 0.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(ids) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                draggingId = id
                                dragOffsetY = 0f
                            },
                            onDragEnd = {
                                draggingId = null
                                dragOffsetY = 0f
                                onReorderCommitted(ids.toList())
                            },
                            onDragCancel = {
                                draggingId = null
                                dragOffsetY = 0f
                            },
                            onDrag = { change, dragAmount ->
                                change.consumePositionChange()
                                dragOffsetY += dragAmount.y

                                val layout = listState.layoutInfo
                                val visible = layout.visibleItemsInfo
                                val current = visible.firstOrNull { it.key == id }
                                    ?: return@detectDragGesturesAfterLongPress

                                // ✅ 현재 아이템 center + dragOffset => targetY
                                val currentCenter = current.offset + current.size / 2
                                val targetY = currentCenter + dragOffsetY

                                // ✅ 타겟 선정: "가장 가까운 center"
                                val target = visible.minByOrNull { info ->
                                    val center = info.offset + info.size / 2
                                    kotlin.math.abs(center - targetY)
                                } ?: return@detectDragGesturesAfterLongPress

                                val from = ids.indexOf(id)
                                val to = ids.indexOf(target.key as String)
                                if (from != -1 && to != -1 && from != to) {
                                    move(from, to)
                                }

                                // ✅ 자동 스크롤: 포인터가 리스트 상/하단 근처면 스크롤
                                val viewportStart = layout.viewportStartOffset.toFloat()
                                val viewportEnd = layout.viewportEndOffset.toFloat()

                                val pointerYInViewport = change.position.y
                                val distToTop = pointerYInViewport - viewportStart
                                val distToBottom = viewportEnd - pointerYInViewport

                                if (distToTop < edgeThresholdPx) {
                                    scope.launch {
                                        listState.scrollBy(-scrollStepPx)
                                        kotlinx.coroutines.delay(scrollCooldownMs)
                                    }
                                } else if (distToBottom < edgeThresholdPx) {
                                    scope.launch {
                                        listState.scrollBy(scrollStepPx)
                                        kotlinx.coroutines.delay(scrollCooldownMs)
                                    }
                                }
                            }
                        )
                    }
                    .clickable { onClick(id) }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.DragHandle,
                        contentDescription = "Reorder",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(10.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = idToTitle(id),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val sub = idToSubtitle(id)
                        if (sub.isNotBlank()) {
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = sub,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    IconButton(onClick = { onDelete(id) }) {
                        Icon(Icons.Default.Delete, contentDescription = "Remove")
                    }
                }
            }
            Divider()
        }
    }
}
package com.kandc.acscore.ui.setlist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.consumePositionChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetlistDetailScreen(
    setlistId: String,
    libraryCandidates: List<ScorePickItem>,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    /**
     * ✅ "세트리스트 이어보기"로 열기 요청
     * - setlistTitle: 탭 이름으로 쓸 세트리스트명
     * - orderedIds: setlist에 들어있는 scoreId 순서
     * - initialScoreId: 사용자가 누른 곡(이 곡 첫 페이지로 시작)
     */
    onOpenSetlistViewer: (setlistId: String, setlistTitle: String, orderedIds: List<String>, initialScoreId: String) -> Unit =
        { _, _, _, _ -> },

    onRequestPickFromLibrary: ((currentItemIds: List<String>) -> Unit)? = null,
    onReorderItems: (setlistId: String, newItemIds: List<String>) -> Unit,
) {
    val context = LocalContext.current
    val vm = remember(setlistId) { SetlistDetailViewModel(context, setlistId) }
    val setlist by vm.setlist.collectAsState()

    // id -> item
    val map = remember(libraryCandidates) { libraryCandidates.associateBy { it.scoreId } }

    val current = setlist
    val currentIds = current?.itemIds.orEmpty()

    // ✅ UI state list (드래그/삭제 즉시 반영)
    val uiIds = remember(setlistId, currentIds) {
        mutableStateListOf<String>().apply { addAll(currentIds) }
    }

    // (백업) 다이얼로그 방식 유지
    var showPicker by rememberSaveable { mutableStateOf(false) }

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
                            val ids = current?.itemIds.orEmpty()
                            if (onRequestPickFromLibrary != null) onRequestPickFromLibrary(ids)
                            else showPicker = true
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
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) { Text("세트리스트를 불러올 수 없어요.") }
            return@Scaffold
        }

        if (uiIds.isEmpty()) {
            Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) { Text("아직 곡이 없어요.\n오른쪽 상단 +로 추가해보세요.") }
        } else {
            ReorderableIdList(
                ids = uiIds,
                idToTitle = { id -> map[id]?.title ?: "(라이브러리 없음)" },
                idToSubtitle = { id -> map[id]?.fileName ?: "" },
                onClick = { id ->
                    // ✅ "세트리스트 이어보기"로 열기
                    val title = current.name.ifBlank { "Setlist" }
                    onOpenSetlistViewer(setlistId, title, uiIds.toList(), id)
                },
                onDelete = { id ->
                    uiIds.remove(id)
                    vm.remove(id)
                    onReorderItems(setlistId, uiIds.toList())
                },
                onReorderCommitted = { newOrder ->
                    onReorderItems(setlistId, newOrder)
                },
                modifier = Modifier.fillMaxSize().padding(padding)
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
                    itemsIndexed(candidates, key = { _, it -> it.scoreId }) { _, item ->
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
        confirmButton = { TextButton(onClick = onDismiss) { Text("닫기") } }
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

    // ✅ 자동 스크롤 throttle
    var autoScrollJob by remember { mutableStateOf<Job?>(null) }

    fun requestAutoScroll(delta: Float) {
        if (autoScrollJob?.isActive == true) return
        autoScrollJob = scope.launch {
            listState.scrollBy(delta)
            delay(16L)
        }
    }

    val edgeThresholdPx = 84f
    val scrollStepPx = 28f

    LazyColumn(
        state = listState,
        modifier = modifier,
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        itemsIndexed(ids, key = { _, id -> id }) { _, id ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(ids) {
                        detectDragGesturesAfterLongPress(
                            onDragEnd = {
                                autoScrollJob?.cancel()
                                autoScrollJob = null
                                onReorderCommitted(ids.toList())
                            },
                            onDragCancel = {
                                autoScrollJob?.cancel()
                                autoScrollJob = null
                            },
                            onDrag = { change, _ ->
                                change.consumePositionChange()

                                val layout = listState.layoutInfo
                                val visible = layout.visibleItemsInfo
                                val current = visible.firstOrNull { it.key == id }
                                    ?: return@detectDragGesturesAfterLongPress

                                // ✅ viewport 기준 손가락 y
                                val pointerYInViewport = current.offset + change.position.y

                                // ✅ 가장 가까운 center로 target 선택
                                val target = visible.minByOrNull { info ->
                                    val center = info.offset + info.size / 2
                                    abs(center - pointerYInViewport)
                                } ?: return@detectDragGesturesAfterLongPress

                                val from = ids.indexOf(id)
                                val to = ids.indexOf(target.key as String)
                                if (from != -1 && to != -1 && from != to) {
                                    val item = ids.removeAt(from)
                                    ids.add(to, item)
                                }

                                // ✅ auto scroll
                                val viewportTop = layout.viewportStartOffset.toFloat()
                                val viewportBottom = layout.viewportEndOffset.toFloat()
                                val distToTop = pointerYInViewport - viewportTop
                                val distToBottom = viewportBottom - pointerYInViewport

                                if (distToTop < edgeThresholdPx) requestAutoScroll(-scrollStepPx)
                                else if (distToBottom < edgeThresholdPx) requestAutoScroll(scrollStepPx)
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
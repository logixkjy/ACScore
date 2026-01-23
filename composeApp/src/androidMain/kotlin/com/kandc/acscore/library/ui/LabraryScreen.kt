package com.kandc.acscore.library.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kandc.acscore.data.model.Score
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.floor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    vm: LibraryViewModel,
    onOpenViewer: (scoreId: String, title: String, fileName: String) -> Unit
) {
    val scores by vm.scores.collectAsState()
    val error by vm.error.collectAsState()
    val query by vm.query.collectAsState()
    val isImporting by vm.isImporting.collectAsState()

    // ✅ 다중 선택 지원
    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) vm.importUris(uris.map { it.toString() })
    }

    LaunchedEffect(Unit) { vm.refresh() }

    // ✅ Import 중 로딩 표시
    if (isImporting) {
        AlertDialog(
            onDismissRequest = { /* 닫지 못하게 */ },
            confirmButton = { },
            title = { Text("가져오는 중…") },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(12.dp))
                    Text("PDF를 앱에 복사하고 있어요.")
                }
            }
        )
    }

    if (error != null) {
        AlertDialog(
            onDismissRequest = { vm.consumeError() },
            confirmButton = { TextButton(onClick = { vm.consumeError() }) { Text("OK") } },
            title = { Text("오류") },
            text = { Text(error ?: "") }
        )
    }

    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // ✅ 인덱스 하이라이트 상태
    var selectedIndexKey by remember { mutableStateOf<String?>(null) }
    var activeIndexKey by remember { mutableStateOf<String?>(null) }

    // ✅ T3: Row 메뉴/다이얼로그 상태
    var menuTarget by remember { mutableStateOf<Score?>(null) }
    var showRename by remember { mutableStateOf(false) }
    var showMetadata by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var renameText by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ACScore Library") },
                actions = {
                    TextButton(
                        enabled = !isImporting,
                        onClick = { picker.launch(arrayOf("application/pdf")) }
                    ) { Text("Import") }
                }
            )
        }
    ) { padding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = vm::setQuery,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                singleLine = true,
                enabled = !isImporting,
                placeholder = { Text("제목/초성 검색 (예: ㅇㄹㄷ, 찬송)") },
                trailingIcon = {
                    if (query.isNotBlank()) {
                        TextButton(onClick = { vm.clearQuery() }) { Text("X") }
                    }
                }
            )

            if (scores.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(if (query.isBlank()) "PDF를 Import 해주세요." else "검색 결과가 없어요.")
                }
            } else {
                val sections = remember(scores) { buildSections(scores) }

                val sectionStartIndex = remember(sections) {
                    val map = mutableMapOf<String, Int>()
                    var index = 0
                    sections.forEach { (header, itemsInSection) ->
                        map[header] = index
                        index += 1
                        index += itemsInSection.size
                    }
                    map
                }

                val sectionRanges = remember(sections) {
                    val ranges = mutableListOf<Triple<String, Int, Int>>() // header, start, endInclusive
                    var index = 0
                    sections.forEach { (header, itemsInSection) ->
                        val start = index
                        val end = index + itemsInSection.size
                        ranges.add(Triple(header, start, end))
                        index = end + 1
                    }
                    ranges
                }

                val visibleIndex by remember { derivedStateOf { listState.firstVisibleItemIndex } }

                LaunchedEffect(visibleIndex, sectionRanges) {
                    val header = sectionRanges.firstOrNull { (_, start, end) ->
                        visibleIndex in start..end
                    }?.first
                    if (header != null && header != selectedIndexKey) {
                        selectedIndexKey = header
                    }
                }

                val indexKeys = remember(sections) { sections.map { it.first } }

                Row(modifier = Modifier.fillMaxSize()) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    ) {
                        sections.forEach { (header, itemsInSection) ->

                            item(key = "header_$header") {
                                SectionHeader(
                                    title = header,
                                    isActive = (header == activeIndexKey),
                                    modifier = Modifier
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                        .clickable(enabled = !isImporting) {
                                            sectionStartIndex[header]?.let { target ->
                                                coroutineScope.launch { listState.animateScrollToItem(target) }
                                            }
                                        }
                                )
                            }

                            items(itemsInSection, key = { it.id }) { score ->
                                var expanded by remember(score.id) { mutableStateOf(false) }

                                ListItem(
                                    headlineContent = {
                                        Text(
                                            text = score.title,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    },
                                    // ✅ fileName 숨김
                                    trailingContent = {
                                        Box {
                                            IconButton(
                                                enabled = !isImporting,
                                                onClick = { expanded = true }
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.MoreVert,
                                                    contentDescription = "메뉴"
                                                )
                                            }

                                            DropdownMenu(
                                                expanded = expanded,
                                                onDismissRequest = { expanded = false }
                                            ) {
                                                DropdownMenuItem(
                                                    text = { Text("이름 변경") },
                                                    onClick = {
                                                        expanded = false
                                                        menuTarget = score
                                                        renameText = score.title
                                                        showRename = true
                                                    }
                                                )
                                                DropdownMenuItem(
                                                    text = { Text("메타데이터 보기") },
                                                    onClick = {
                                                        expanded = false
                                                        menuTarget = score
                                                        showMetadata = true
                                                    }
                                                )
                                                Divider()
                                                DropdownMenuItem(
                                                    text = {
                                                        Text(
                                                            text = "삭제",
                                                            color = MaterialTheme.colorScheme.error
                                                        )
                                                    },
                                                    onClick = {
                                                        expanded = false
                                                        menuTarget = score
                                                        showDeleteConfirm = true
                                                    }
                                                )
                                            }
                                        }
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(enabled = !isImporting) {
                                            onOpenViewer(score.id, score.title, score.fileName)
                                        }
                                )
                                Divider()
                            }
                        }
                    }

                    IndexBar(
                        keys = indexKeys,
                        enabled = !isImporting,
                        onActiveKeyChanged = { activeIndexKey = it },
                        onKeyChange = { key ->
                            sectionStartIndex[key]?.let { target ->
                                coroutineScope.launch { listState.scrollToItem(target) }
                            }
                        }
                    )
                }
            }
        }
    }

    // -------------------------
    // ✅ T3: Dialogs
    // -------------------------

    if (showRename) {
        AlertDialog(
            onDismissRequest = { showRename = false },
            title = { Text("이름 변경") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true,
                    enabled = !isImporting,
                    placeholder = { Text("새 제목") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !isImporting && renameText.trim().isNotEmpty(),
                    onClick = {
                        val target = menuTarget
                        if (target != null) {
                            vm.renameTitle(target.id, renameText.trim())
                        }
                        showRename = false
                        menuTarget = null
                    }
                ) { Text("저장") }
            },
            dismissButton = {
                TextButton(onClick = { showRename = false }) { Text("취소") }
            }
        )
    }

    if (showMetadata) {
        val target = menuTarget
        AlertDialog(
            onDismissRequest = { showMetadata = false },
            confirmButton = { TextButton(onClick = { showMetadata = false }) { Text("닫기") } },
            title = { Text("메타데이터") },
            text = {
                if (target == null) {
                    Text("정보를 불러올 수 없어요.")
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("제목: ${target.title}")
                        Text("파일명: ${target.fileName}")
                        Text(
                            text = "추가일: ${formatDate(target.createdAt)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        )
    }

    if (showDeleteConfirm) {
        val target = menuTarget
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("삭제할까요?") },
            text = { Text("이 작업은 되돌릴 수 없습니다.") },
            confirmButton = {
                TextButton(
                    enabled = !isImporting,
                    onClick = {
                        if (target != null) vm.deleteScore(target.id)
                        showDeleteConfirm = false
                        menuTarget = null
                    }
                ) { Text("삭제", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("취소") }
            }
        )
    }
}

@Composable
private fun IndexBar(
    keys: List<String>,
    enabled: Boolean = true,
    onActiveKeyChanged: (String?) -> Unit,
    onKeyChange: (String) -> Unit
) {
    val baseHeight = 20.dp
    val density = LocalDensity.current
    val baseHeightPx = with(density) { baseHeight.toPx() }

    val scope = rememberCoroutineScope()

    var activeKey by remember { mutableStateOf<String?>(null) }
    var tapReleaseJob by remember { mutableStateOf<Job?>(null) }

    fun keyFromLocalY(localY: Float): String? {
        if (keys.isEmpty()) return null
        val idx = floor(localY / baseHeightPx).toInt().coerceIn(0, keys.lastIndex)
        return keys[idx]
    }

    fun setActiveAndJump(key: String) {
        if (key != activeKey) activeKey = key
        onKeyChange(key)
        onActiveKeyChanged(key)
    }

    Column(
        modifier = Modifier
            .width(44.dp)
            .fillMaxHeight()
            .padding(top = 12.dp, bottom = 12.dp, end = 6.dp)
            .pointerInput(keys, enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures(
                    onTap = { offset ->
                        val key = keyFromLocalY(offset.y) ?: return@detectTapGestures
                        setActiveAndJump(key)

                        tapReleaseJob?.cancel()
                        tapReleaseJob = scope.launch {
                            delay(150)
                            if (activeKey == key) {
                                activeKey = null
                                onActiveKeyChanged(null)
                            }
                        }
                    }
                )
            }
            .pointerInput(keys, enabled) {
                if (!enabled) return@pointerInput
                detectDragGestures(
                    onDragStart = { offset ->
                        val key = keyFromLocalY(offset.y) ?: return@detectDragGestures
                        tapReleaseJob?.cancel()
                        setActiveAndJump(key)
                    },
                    onDrag = { change, _ ->
                        val key = keyFromLocalY(change.position.y) ?: return@detectDragGestures
                        setActiveAndJump(key)
                    },
                    onDragEnd = {
                        activeKey = null
                        onActiveKeyChanged(null)
                    },
                    onDragCancel = {
                        activeKey = null
                        onActiveKeyChanged(null)
                    }
                )
            },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(6.dp))

        keys.forEach { key ->
            val isActive = key == activeKey

            val scale by animateFloatAsState(
                targetValue = if (isActive) 1.25f else 1.0f,
                animationSpec = tween(durationMillis = 120),
                label = "indexScale"
            )

            val bgColor by animateColorAsState(
                targetValue = if (isActive) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surface,
                animationSpec = tween(durationMillis = 120),
                label = "indexBg"
            )

            val textColor by animateColorAsState(
                targetValue = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurfaceVariant,
                animationSpec = tween(durationMillis = 120),
                label = "indexText"
            )

            val borderColor by animateColorAsState(
                targetValue = if (isActive) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surface,
                animationSpec = tween(durationMillis = 120),
                label = "indexBorder"
            )

            val height = if (isActive) 28.dp else baseHeight
            val elevation = if (isActive) 6.dp else 0.dp

            Surface(
                modifier = Modifier
                    .height(height)
                    .fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                color = bgColor,
                tonalElevation = elevation,
                shadowElevation = elevation,
                border = if (isActive) BorderStroke(1.dp, borderColor) else null
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = key,
                        style = if (isActive) MaterialTheme.typography.labelMedium
                        else MaterialTheme.typography.labelSmall,
                        color = textColor,
                        modifier = Modifier.graphicsLayer(scaleX = scale, scaleY = scale)
                    )
                }
            }

            Spacer(Modifier.height(2.dp))
        }

        Spacer(Modifier.height(6.dp))
    }
}

@Composable
private fun SectionHeader(
    title: String,
    isActive: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        tonalElevation = if (isActive) 4.dp else 1.dp,
        shadowElevation = if (isActive) 6.dp else 0.dp,
        shape = MaterialTheme.shapes.small,
        color = if (isActive) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceVariant,
        border = if (isActive) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.primary
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }

            if (isActive) {
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "현재 위치",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}
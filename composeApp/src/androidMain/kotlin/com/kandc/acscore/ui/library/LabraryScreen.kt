package com.kandc.acscore.ui.library

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.BorderStroke
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.floor
import android.content.Context
import java.io.File
import androidx.compose.ui.platform.LocalContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    vm: LibraryViewModel,
    onOpenViewer: (scoreId: String, title: String, filePath: String) -> Unit
) {
    val scores by vm.scores.collectAsState()
    val error by vm.error.collectAsState()
    val query by vm.query.collectAsState()
    val isImporting by vm.isImporting.collectAsState() // ✅ 추가: import 진행 상태

    // ✅ 변경: 다중 선택 지원 (OpenMultipleDocuments)
    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) vm.importUris(uris.map { it.toString() })
    }

    LaunchedEffect(Unit) { vm.refresh() }

    // ✅ 추가: Import 중 로딩 표시 (검은 화면/무반응처럼 보이는 문제 완화)
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
            confirmButton = {
                TextButton(onClick = { vm.consumeError() }) { Text("OK") }
            },
            title = { Text("오류") },
            text = { Text(error ?: "") }
        )
    }

    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // ✅ (1) 인덱스 클릭/자동 강조 하이라이트 상태
    var selectedIndexKey by remember { mutableStateOf<String?>(null) }
    var activeIndexKey by remember { mutableStateOf<String?>(null) }

    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ACScore Library") },
                actions = {
                    TextButton(
                        enabled = !isImporting, // ✅ 추가: import 중 중복 실행 방지
                        onClick = { picker.launch(arrayOf("application/pdf")) }
                    ) {
                        Text("Import")
                    }
                }
            )
        }
    ) { padding ->

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = vm::setQuery,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    singleLine = true,
                    enabled = !isImporting, // ✅ 추가: import 중 검색 입력 잠깐 잠금(원하면 제거 가능)
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

                    // ✅ (2) index -> header 범위 매핑 (현재 스크롤 위치로 섹션 계산)
                    val sectionRanges = remember(sections) {
                        val ranges = mutableListOf<Triple<String, Int, Int>>() // header, start, endInclusive
                        var index = 0
                        sections.forEach { (header, itemsInSection) ->
                            val start = index // header index
                            val end = index + itemsInSection.size // last item index in this section
                            ranges.add(Triple(header, start, end))
                            index = end + 1
                        }
                        ranges
                    }

                    // ✅ 현재 firstVisibleItemIndex 변화 감지 (Flow 없이 안정)
                    val visibleIndex by remember {
                        derivedStateOf { listState.firstVisibleItemIndex }
                    }

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
                                        isActive = (header == activeIndexKey), // ✅ 추가
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
                                    ListItem(
                                        headlineContent = { Text(score.title) },
                                        supportingContent = { Text(score.fileName) },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable(enabled = !isImporting) {
                                                val file = File(context.filesDir, "scores/${score.fileName}")
                                                if (!file.exists()) {
                                                    vm.emitError("파일이 존재하지 않아요: ${score.fileName}")
                                                    return@clickable
                                                }
                                                onOpenViewer(score.id, score.title, file.absolutePath)
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
                                    coroutineScope.launch {
                                        listState.scrollToItem(target)
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
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

    // ✅ 드래그/탭 동안만 활성 하이라이트
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
            .width(44.dp) // ✅ 살짝 넓혀야 확대했을 때 덜 답답함
            .fillMaxHeight()
            .padding(top = 12.dp, bottom = 12.dp, end = 6.dp)

            // ✅ (1) 탭: 150ms 유지 후 해제
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

            // ✅ (2) 드래그: 드래그 동안 계속 활성
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
                targetValue = if (isActive)
                    MaterialTheme.colorScheme.primaryContainer
                else
                    MaterialTheme.colorScheme.surface,
                animationSpec = tween(durationMillis = 120),
                label = "indexBg"
            )

            val textColor by animateColorAsState(
                targetValue = if (isActive)
                    MaterialTheme.colorScheme.onPrimaryContainer
                else
                    MaterialTheme.colorScheme.onSurfaceVariant,
                animationSpec = tween(durationMillis = 120),
                label = "indexText"
            )

            val borderColor by animateColorAsState(
                targetValue = if (isActive)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.surface,
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
                shadowElevation = elevation, // ✅ 실제 그림자 강조
                border = if (isActive)
                    BorderStroke(1.dp, borderColor)
                else
                    null
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = key,
                        style = if (isActive)
                            MaterialTheme.typography.labelMedium
                        else
                            MaterialTheme.typography.labelSmall,
                        color = textColor,
                        modifier = Modifier.graphicsLayer(
                            scaleX = scale,
                            scaleY = scale
                        )
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
                color = if (isActive) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.primary,
            ) {
                Text(
                    text = title,
                    style = if (isActive) MaterialTheme.typography.labelLarge
                    else MaterialTheme.typography.labelLarge,
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
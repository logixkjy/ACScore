package com.kandc.acscore.ui.library

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    vm: LibraryViewModel
) {
    val scores by vm.scores.collectAsState()
    val error by vm.error.collectAsState()
    val query by vm.query.collectAsState()

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) vm.import(uri.toString())
    }

    LaunchedEffect(Unit) { vm.refresh() }

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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ACScore Library") },
                actions = {
                    TextButton(onClick = { picker.launch(arrayOf("application/pdf")) }) {
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

                    // ✅ 현재 firstVisibleItemIndex 변화 감지 (Flow 없이 호환 100%)
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
                                        modifier = Modifier
                                            .padding(horizontal = 12.dp, vertical = 6.dp)
                                            .clickable {
                                                selectedIndexKey = header
                                                sectionStartIndex[header]?.let { target ->
                                                    coroutineScope.launch {
                                                        listState.animateScrollToItem(target)
                                                    }
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
                                            .clickable {
                                                // T2
                                            }
                                    )
                                    Divider()
                                }
                            }
                        }

                        IndexBar(
                            keys = indexKeys,
                            selectedKey = selectedIndexKey,
                            onKeyClick = { key ->
                                selectedIndexKey = key
                                sectionStartIndex[key]?.let { target ->
                                    coroutineScope.launch {
                                        listState.animateScrollToItem(target)
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
    selectedKey: String?,
    onKeyClick: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .width(36.dp)
            .fillMaxHeight()
            .padding(top = 12.dp, bottom = 12.dp, end = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val itemHeight = 20.dp

        Spacer(Modifier.height(6.dp))
        keys.forEach { key ->
            val isSelected = key == selectedKey

            Surface(
                modifier = Modifier
                    .height(itemHeight)
                    .fillMaxWidth()
                    .clickable { onKeyClick(key) },
                shape = MaterialTheme.shapes.small,
                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surface,
                tonalElevation = if (isSelected) 2.dp else 0.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = key,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
    }
}

@Composable
private fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        tonalElevation = 1.dp,
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.primary,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }
    }
}
package com.kandc.acscore.ui.library

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // ✅ Search
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
                // ✅ 섹션 구성 (scores 변경될 때만 재계산)
                val sections = remember(scores) { buildSections(scores) }

                LazyColumn(
                    modifier = Modifier.fillMaxSize()
                ) {
                    sections.forEach { (header, itemsInSection) ->

                        // ✅ stickyHeader 대신 일반 item
                        item(key = "header_$header") {
                            Surface(tonalElevation = 2.dp) {
                                Text(
                                    text = header,
                                    style = MaterialTheme.typography.labelLarge,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp)
                                )
                            }
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
            }
        }
    }
}
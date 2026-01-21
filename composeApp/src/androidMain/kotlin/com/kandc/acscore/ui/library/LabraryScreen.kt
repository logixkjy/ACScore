package com.kandc.acscore.ui.library

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
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

    LaunchedEffect(Unit) {
        vm.refresh()
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
                .padding(padding)
                .fillMaxSize()
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
                        .padding(24.dp)
                ) {
                    Text(if (query.isBlank()) "PDF를 Import 해주세요." else "검색 결과가 없어요.")
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(scores, key = { it.id }) { score ->
                        ListItem(
                            headlineContent = { Text(score.title) },
                            supportingContent = { Text(score.fileName) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    // T2: PdfRenderer 뷰어 연결
                                }
                        )
                        Divider()
                    }
                }
            }
        }
    }
}
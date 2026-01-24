package com.kandc.acscore.ui.setlist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetlistListScreen(
    modifier: Modifier = Modifier,
    onOpenSetlist: (setlistId: String) -> Unit = {}
) {
    val context = LocalContext.current
    val vm = remember { SetlistListViewModel(context) }

    val setlists by vm.setlists.collectAsState()

    var showCreateDialog by remember { mutableStateOf(false) }
    var createName by remember { mutableStateOf("") }

    var snackbarMessage by remember { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(snackbarMessage) {
        val msg = snackbarMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        snackbarMessage = null
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Setlists") },
                actions = {
                    IconButton(onClick = { showCreateDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add Setlist"
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        if (setlists.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("아직 세트리스트가 없어요.\n오른쪽 아래 + 버튼으로 만들어보세요.")
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(setlists, key = { it.id }) { item ->
                    SetlistRow(
                        name = item.name,
                        count = item.itemIds.size,
                        onClick = { onOpenSetlist(item.id) },
                        onDelete = {
                            vm.deleteSetlist(item.id) { snackbarMessage = it }
                        }
                    )
                }
            }
        }
    }

    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = {
                showCreateDialog = false
                createName = ""
            },
            title = { Text("새 세트리스트") },
            text = {
                OutlinedTextField(
                    value = createName,
                    onValueChange = { createName = it.take(40) },
                    singleLine = true,
                    label = { Text("이름") },
                    placeholder = { Text("예: 주일예배") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.createSetlist(createName) { snackbarMessage = it }
                        showCreateDialog = false
                        createName = ""
                    },
                    enabled = createName.trim().isNotBlank()
                ) {
                    Text("생성")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showCreateDialog = false
                        createName = ""
                    }
                ) { Text("취소") }
            }
        )
    }
}

@Composable
private fun SetlistRow(
    name: String,
    count: Int,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    ListItem(
        headlineContent = {
            Text(
                text = name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        supportingContent = { Text("${count}곡") },
        trailingContent = {
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete")
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    )
    HorizontalDivider()
}
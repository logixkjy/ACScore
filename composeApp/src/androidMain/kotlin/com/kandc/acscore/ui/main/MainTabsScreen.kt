package com.kandc.acscore.ui.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import com.kandc.acscore.ui.setlist.SetlistListScreen

@Composable
fun MainTabsScreen(
    modifier: Modifier = Modifier,
    libraryContent: @Composable () -> Unit,
    onOpenSetlist: (setlistId: String) -> Unit = {},

    // ✅ T6-3: 마지막 선택 화면 기반 초기 탭 복원
    initialTab: MainTab = MainTab.Library,
    initialOpenSetlistId: String? = null,

    // (선택) 탭 전환 시 외부 저장이 필요하면 연결
    onTabChanged: (MainTab) -> Unit = {}
) {
    var selected by rememberSaveable(initialTab.name) { mutableStateOf(initialTab) }

    LaunchedEffect(selected) {
        onTabChanged(selected)
    }

    // ✅ 처음 1회: setlist detail로 시작해야 하면 자동 진입 트리거
    LaunchedEffect(initialOpenSetlistId, selected) {
        val id = initialOpenSetlistId ?: return@LaunchedEffect
        if (selected == MainTab.Setlists) {
            onOpenSetlist(id)
        }
    }

    Scaffold(
        modifier = modifier,
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selected == MainTab.Library,
                    onClick = { selected = MainTab.Library },
                    icon = { Icon(Icons.Default.LibraryMusic, contentDescription = "Library") },
                    label = { Text("Library") }
                )
                NavigationBarItem(
                    selected = selected == MainTab.Setlists,
                    onClick = { selected = MainTab.Setlists },
                    icon = { Icon(Icons.Default.QueueMusic, contentDescription = "Setlists") },
                    label = { Text("Setlists") }
                )
            }
        }
    ) { padding ->
        when (selected) {
            MainTab.Library -> {
                Box(modifier = Modifier.padding(padding)) { libraryContent() }
            }
            MainTab.Setlists -> {
                SetlistListScreen(
                    modifier = Modifier.padding(padding),
                    onOpenSetlist = onOpenSetlist
                )
            }
        }
    }
}

enum class MainTab { Library, Setlists }
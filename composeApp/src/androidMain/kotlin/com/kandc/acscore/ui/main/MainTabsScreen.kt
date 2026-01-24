package com.kandc.acscore.ui.main

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

/**
 * MVP v1: 하단 탭으로 "Library / Setlists"
 * - Import 탭은 T1에서 이미 상단 버튼/액션으로도 가능했고,
 *   원하면 T5 이후에 3탭으로 확장하기 쉽도록 구조만 잡아둠.
 */
@Composable
fun MainTabsScreen(
    modifier: Modifier = Modifier,
    libraryContent: @Composable () -> Unit,
    onOpenSetlist: (setlistId: String) -> Unit = {},
) {
    var selected by rememberSaveable { mutableStateOf(MainTab.Library) }

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
                // 기존 Library 화면을 그대로 주입
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier.padding(padding)
                ) { libraryContent() }
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

private enum class MainTab { Library, Setlists }
package com.kandc.acscore.root

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.kandc.acscore.ui.common.SegmentedTabs
import com.kandc.acscore.ui.library.LibraryScreen
import com.kandc.acscore.ui.setlist.ScorePickItem
import com.kandc.acscore.ui.setlist.SetlistDetailScreen
import com.kandc.acscore.ui.setlist.SetlistListScreen
import com.kandc.acscore.ui.viewer.TabbedViewerScreen
import com.kandc.acscore.viewer.domain.ViewerOpenRequest
import java.io.File
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import com.kandc.acscore.di.SetlistDi
import com.kandc.acscore.shared.domain.usecase.AddScoreToSetlistUseCase
import com.kandc.acscore.shared.domain.usecase.UpdateSetlistItemsUseCase

private enum class HomeTab { Library, Setlists }

@Composable
fun MainHostScreen(component: RootComponent) {
    val overlayOpen by component.isLibraryOverlayOpen.collectAsState()
    var selectedTab by rememberSaveable { mutableStateOf(HomeTab.Library) }

    var openedSetlistId by rememberSaveable { mutableStateOf<String?>(null) }
    var pickingForSetlistId by rememberSaveable { mutableStateOf<String?>(null) }
    var pickingSelectedIds by rememberSaveable { mutableStateOf<Set<String>>(emptySet()) }

    Box(Modifier.fillMaxSize()) {
        TabbedViewerScreen(
            sessionStore = component.viewerSessionStore,
            onRequestOpenLibrary = {
                selectedTab = HomeTab.Library
                openedSetlistId = null
                component.openLibraryOverlay()
            },
            modifier = Modifier.fillMaxSize()
        )

        LibraryOverlay(
            visible = overlayOpen,
            onDismiss = { component.closeLibraryOverlay() }
        ) {
            Column(Modifier.fillMaxSize()) {
                SegmentedTabs(
                    titles = listOf("Library", "Setlists"),
                    selectedIndex = if (selectedTab == HomeTab.Library) 0 else 1,
                    onSelect = { idx ->
                        selectedTab = if (idx == 0) HomeTab.Library else HomeTab.Setlists
                        if (selectedTab == HomeTab.Library) openedSetlistId = null
                    },
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .padding(top = 16.dp, bottom = 8.dp)
                )

                when (selectedTab) {
                    HomeTab.Library -> {
                        val context = LocalContext.current
                        val scope = rememberCoroutineScope()

                        val setlistRepo = remember(context.applicationContext) {
                            SetlistDi.provideRepository(context.applicationContext)
                        }
                        val addToSetlist = remember(setlistRepo) {
                            AddScoreToSetlistUseCase(setlistRepo)
                        }

                        LibraryScreen(
                            vm = component.libraryViewModel,
                            onOpenViewer = { scoreId, title, fileName ->
                                component.closeLibraryOverlay()
                                val filePath = File(context.filesDir, "scores/$fileName").absolutePath
                                component.onScoreSelected(
                                    ViewerOpenRequest(scoreId = scoreId, title = title, filePath = filePath)
                                )
                            },
                            onPickScore = pickingForSetlistId?.let { targetSetlistId ->
                                { scoreId, title, fileName ->
                                    scope.launch {
                                        runCatching {
                                            addToSetlist(targetSetlistId, scoreId)
                                        }.onSuccess {
                                            pickingSelectedIds = pickingSelectedIds + scoreId
                                            openedSetlistId = targetSetlistId
                                        }.onFailure { e ->
                                            component.libraryViewModel.emitError(e.message ?: "세트리스트 추가 실패")
                                        }
                                    }
                                }
                            },
                            onCancelPick = if (pickingForSetlistId != null) {
                                {
                                    selectedTab = HomeTab.Setlists
                                    openedSetlistId = pickingForSetlistId
                                    pickingForSetlistId = null
                                    pickingSelectedIds = emptySet()
                                }
                            } else null,
                            onDonePick = if (pickingForSetlistId != null) {
                                {
                                    selectedTab = HomeTab.Setlists
                                    openedSetlistId = pickingForSetlistId
                                    pickingForSetlistId = null
                                    pickingSelectedIds = emptySet()
                                }
                            } else null,
                            pickedScoreIds = pickingSelectedIds
                        )
                    }

                    HomeTab.Setlists -> {
                        val candidates = rememberLibraryCandidates(component)

                        val context = LocalContext.current
                        val scope = rememberCoroutineScope()

                        val setlistRepo = remember(context.applicationContext) {
                            SetlistDi.provideRepository(context.applicationContext)
                        }

                        val updateSetlistItems = remember(setlistRepo) {
                            UpdateSetlistItemsUseCase(setlistRepo)
                        }

                        val id = openedSetlistId
                        if (id == null) {
                            SetlistListScreen(
                                onOpenSetlist = { setlistId ->
                                    openedSetlistId = setlistId
                                }
                            )
                        } else {
                            // ✅ candidates 빠르게 lookup
                            val candidateMap = remember(candidates) { candidates.associateBy { it.scoreId } }

                            SetlistDetailScreen(
                                setlistId = id,
                                libraryCandidates = candidates,
                                onBack = { openedSetlistId = null },

                                // ✅ 핵심: 세트리스트 이어보기로 열기
                                onOpenSetlistViewer = { setlistId, setlistTitle, orderedIds, initialScoreId ->
                                    val requests = orderedIds.mapNotNull { sid ->
                                        val item = candidateMap[sid] ?: return@mapNotNull null
                                        val filePath = File(context.filesDir, "scores/${item.fileName}").absolutePath
                                        ViewerOpenRequest(
                                            scoreId = item.scoreId,
                                            title = item.title,
                                            filePath = filePath
                                        )
                                    }

                                    component.closeLibraryOverlay()

                                    component.viewerSessionStore.openSetlist(
                                        setlistId = setlistId,
                                        setlistTitle = setlistTitle,
                                        requests = requests,
                                        initialScoreId = initialScoreId
                                    )
                                },

                                onRequestPickFromLibrary = { currentItemIds ->
                                    pickingForSetlistId = id
                                    pickingSelectedIds = currentItemIds.toSet()
                                    selectedTab = HomeTab.Library
                                },

                                onReorderItems = { sid, newIds ->
                                    scope.launch {
                                        runCatching {
                                            updateSetlistItems(sid, newIds)
                                        }.onFailure { e ->
                                            component.libraryViewModel.emitError(e.message ?: "순서 저장 실패")
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
}

@Composable
private fun rememberLibraryCandidates(component: RootComponent): List<ScorePickItem> {
    val scores by component.libraryViewModel.scores.collectAsState()
    return remember(scores) {
        scores.map { s ->
            ScorePickItem(
                scoreId = s.id,
                title = s.title,
                fileName = s.fileName
            )
        }
    }
}
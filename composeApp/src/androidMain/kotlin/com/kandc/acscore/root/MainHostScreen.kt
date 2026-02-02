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
import com.kandc.acscore.session.viewer.ViewerPickerContext

private enum class HomeTab { Library, Setlists }

@Composable
fun MainHostScreen(component: RootComponent) {
    val overlayOpen by component.isLibraryOverlayOpen.collectAsState()

    var selectedTab by rememberSaveable { mutableStateOf(HomeTab.Library) }
    var openedSetlistId by rememberSaveable { mutableStateOf<String?>(null) }

    var pickingForSetlistId by rememberSaveable { mutableStateOf<String?>(null) }
    var pickingSelectedIds by rememberSaveable { mutableStateOf<Set<String>>(emptySet()) }

    val sessionState by component.viewerSessionStore.state.collectAsState()

    /**
     * ✅ (1) 오버레이가 열릴 때 lastPicker 기반으로 UI 복원
     */
    LaunchedEffect(overlayOpen) {
        if (!overlayOpen) return@LaunchedEffect

        when (val ctx = sessionState.lastPicker) {
            is ViewerPickerContext -> {
                when (ctx.kind) {
                    ViewerPickerContext.Kind.Library -> {
                        selectedTab = HomeTab.Library
                        openedSetlistId = null
                    }
                    ViewerPickerContext.Kind.Setlists -> {
                        selectedTab = HomeTab.Setlists
                        openedSetlistId = null
                    }
                    ViewerPickerContext.Kind.SetlistDetail -> {
                        selectedTab = HomeTab.Setlists
                        openedSetlistId = ctx.setlistId
                    }
                }
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        TabbedViewerScreen(
            sessionStore = component.viewerSessionStore,
            onRequestOpenPicker = {
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

                        if (selectedTab == HomeTab.Library) {
                            openedSetlistId = null

                            // ✅ (3) 사용자가 "Library 탭"을 보고 있다 = lastPicker 갱신
                            component.viewerSessionStore.setLastPicker(ViewerPickerContext.library())
                        } else {
                            // Setlists 탭으로 이동했지만 아직 상세는 아님
                            if (openedSetlistId == null) {
                                component.viewerSessionStore.setLastPicker(ViewerPickerContext.setlists())
                            } else {
                                component.viewerSessionStore.setLastPicker(
                                    ViewerPickerContext.setlistDetail(openedSetlistId!!)
                                )
                            }
                        }
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

                        // ✅ 오버레이가 Library 화면이라면 lastPicker를 확실히 Library로
                        LaunchedEffect(Unit) {
                            component.viewerSessionStore.setLastPicker(ViewerPickerContext.library())
                        }

                        LibraryScreen(
                            vm = component.libraryViewModel,
                            onOpenViewer = { scoreId, title, fileName ->
                                // ✅ 규약 #8: 마지막 오버레이는 "Library"
                                component.viewerSessionStore.setLastPicker(ViewerPickerContext.library())

                                component.closeLibraryOverlay()
                                val filePath = File(context.filesDir, "scores/$fileName").absolutePath

                                // (기존 로직 유지)
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

                                            // ✅ pick 후에도 결국 사용자는 setlist detail로 돌아가니 lastPicker도 detail로
                                            component.viewerSessionStore.setLastPicker(
                                                ViewerPickerContext.setlistDetail(targetSetlistId)
                                            )
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

                                    // ✅ cancel도 setlist detail로 돌아감
                                    openedSetlistId?.let {
                                        component.viewerSessionStore.setLastPicker(
                                            ViewerPickerContext.setlistDetail(it)
                                        )
                                    }
                                }
                            } else null,
                            onDonePick = if (pickingForSetlistId != null) {
                                {
                                    selectedTab = HomeTab.Setlists
                                    openedSetlistId = pickingForSetlistId
                                    pickingForSetlistId = null
                                    pickingSelectedIds = emptySet()

                                    // ✅ done도 setlist detail로 돌아감
                                    openedSetlistId?.let {
                                        component.viewerSessionStore.setLastPicker(
                                            ViewerPickerContext.setlistDetail(it)
                                        )
                                    }
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
                            // ✅ Setlists 리스트 화면 = lastPicker는 Setlists
                            LaunchedEffect(Unit) {
                                component.viewerSessionStore.setLastPicker(ViewerPickerContext.setlists())
                            }

                            SetlistListScreen(
                                onOpenSetlist = { setlistId ->
                                    openedSetlistId = setlistId

                                    // ✅ (4) 세트 상세로 진입 = lastPicker는 SetlistDetail(setlistId)
                                    component.viewerSessionStore.setLastPicker(
                                        ViewerPickerContext.setlistDetail(setlistId)
                                    )
                                }
                            )
                        } else {
                            // ✅ candidates 빠르게 lookup
                            val candidateMap = remember(candidates) { candidates.associateBy { it.scoreId } }

                            // ✅ Setlist 상세 화면 = lastPicker는 SetlistDetail(id)
                            LaunchedEffect(id) {
                                component.viewerSessionStore.setLastPicker(
                                    ViewerPickerContext.setlistDetail(id)
                                )
                            }

                            SetlistDetailScreen(
                                setlistId = id,
                                libraryCandidates = candidates,
                                onBack = {
                                    openedSetlistId = null

                                    // ✅ (5) 상세에서 뒤로 = Setlists 리스트가 마지막 화면
                                    component.viewerSessionStore.setLastPicker(ViewerPickerContext.setlists())
                                },

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

                                    // ✅ 규약 #8: 마지막 오버레이는 이 setlist detail
                                    val ctx = ViewerPickerContext.setlistDetail(setlistId)
                                    component.viewerSessionStore.setLastPicker(ctx)

                                    component.closeLibraryOverlay()

                                    // ✅ (6) openSetlist에도 sourcePicker를 넘겨두면 탭 returnPicker도 일관됨
                                    component.viewerSessionStore.openSetlist(
                                        setlistId = setlistId,
                                        setlistTitle = setlistTitle,
                                        requests = requests,
                                        initialScoreId = initialScoreId,
                                        sourcePicker = ctx
                                    )
                                },

                                onRequestPickFromLibrary = { currentItemIds ->
                                    pickingForSetlistId = id
                                    pickingSelectedIds = currentItemIds.toSet()
                                    selectedTab = HomeTab.Library

                                    // ✅ pick 모드지만, "마지막 목록 화면"은 여전히 setlist detail로 유지시키는 게 자연스러움
                                    component.viewerSessionStore.setLastPicker(
                                        ViewerPickerContext.setlistDetail(id)
                                    )
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
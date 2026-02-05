package com.kandc.acscore.root

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kandc.acscore.di.SetlistDi
import com.kandc.acscore.session.viewer.ViewerPickerContext
import com.kandc.acscore.shared.domain.usecase.AddScoreToSetlistUseCase
import com.kandc.acscore.shared.domain.usecase.UpdateSetlistItemsUseCase
import com.kandc.acscore.share.AcsetImporter
import com.kandc.acscore.ui.common.SegmentedTabs
import com.kandc.acscore.ui.library.LibraryScreen
import com.kandc.acscore.ui.setlist.ScorePickItem
import com.kandc.acscore.ui.setlist.SetlistDetailScreen
import com.kandc.acscore.ui.setlist.SetlistListScreen
import com.kandc.acscore.ui.viewer.TabbedViewerScreen
import com.kandc.acscore.viewer.domain.ViewerOpenRequest
import java.io.File
import kotlinx.coroutines.launch

private enum class HomeTab { Library, Setlists }

@Composable
fun MainHostScreen(component: RootComponent) {
    val overlayOpen by component.isLibraryOverlayOpen.collectAsState()

    // ✅ Importer 연결에 필요한 공용 상태들
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val importer = remember { AcsetImporter() }
    var isImporting by remember { mutableStateOf(false) }

    var selectedTab by rememberSaveable { mutableStateOf(HomeTab.Library) }
    var openedSetlistId by rememberSaveable { mutableStateOf<String?>(null) }

    var pickingForSetlistId by rememberSaveable { mutableStateOf<String?>(null) }
    var pickingSelectedIds by rememberSaveable { mutableStateOf<Set<String>>(emptySet()) }
// ✅ pick 모드에서 최종 순서를 보존하기 위한 리스트
    var pickingOrderedIds by rememberSaveable { mutableStateOf<List<String>>(emptyList()) }

    // pick 편집용: 최초(기존) 아이디 목록(순서 유지) + 현재 드래프트 선택 Set
    var pickOriginalIds by remember { mutableStateOf<List<String>>(emptyList()) }
    var pickDraftIds by remember { mutableStateOf<Set<String>>(emptySet()) }

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

    // ✅ 외부에서 들어온 .acset Uri 감지 → 가져오기 다이얼로그 + 실행
    val pendingAcsetUri by component.pendingAcsetUri.collectAsState()
    if (pendingAcsetUri != null) {
        val uri: Uri = pendingAcsetUri!!

        AlertDialog(
            onDismissRequest = {
                if (!isImporting) component.consumePendingAcset()
            },
            title = { Text("세트리스트 가져오기") },
            text = {
                if (isImporting) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(12.dp))
                        Text("가져오는 중…", textAlign = TextAlign.Center)
                    }
                } else {
                    Text("공유받은 세트리스트 파일을 가져올까요?")
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !isImporting,
                    onClick = {
                        isImporting = true
                        scope.launch {
                            val result = importer.importFromUri(context, uri)

                            isImporting = false
                            component.consumePendingAcset()

                            when (result) {
                                is AcsetImporter.Result.Success -> {
                                    snackbarHostState.showSnackbar(
                                        "가져오기 완료: +${result.importedCount} (중복 ${result.skippedDuplicateCount}개 재사용)"
                                    )
                                    // TODO(T6-3): setlist 생성 + 상세로 이동
                                    // result.setlistTitle / result.orderedScoreIds 활용
                                }
                                is AcsetImporter.Result.Failure -> {
                                    snackbarHostState.showSnackbar(
                                        result.reason.ifBlank { "가져오기에 실패했어요." }
                                    )
                                }
                            }
                        }
                    }
                ) { Text("가져오기") }
            },
            dismissButton = {
                TextButton(
                    enabled = !isImporting,
                    onClick = { component.consumePendingAcset() }
                ) { Text("취소") }
            }
        )
    }

    Box(Modifier.fillMaxSize()) {
        // ✅ Import 결과 알림(하단)
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )

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
                    titles = listOf("악보", "곡목록"),
                    selectedIndex = if (selectedTab == HomeTab.Library) 0 else 1,
                    onSelect = { idx ->
                        selectedTab = if (idx == 0) HomeTab.Library else HomeTab.Setlists

                        if (selectedTab == HomeTab.Library) {
                            openedSetlistId = null
                            component.viewerSessionStore.setLastPicker(ViewerPickerContext.library())
                        } else {
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
                        val updateItemsUseCase = remember(setlistRepo) {
                            UpdateSetlistItemsUseCase(setlistRepo)
                        }

                        LaunchedEffect(Unit) {
                            component.viewerSessionStore.setLastPicker(ViewerPickerContext.library())
                        }

                        val pickSetlistId = pickingForSetlistId
                        val isPickMode = pickSetlistId != null

                        val exitPick: () -> Unit = {
                            pickingForSetlistId = null
                            pickingSelectedIds = emptySet()   // 기존 상태도 정리 (남아있으면 누수 원인)
                            pickOriginalIds = emptyList()
                            pickDraftIds = emptySet()

                            selectedTab = HomeTab.Setlists
                            openedSetlistId = pickSetlistId
                        }

                        // 1) LibraryScreen의 onPickScore에서 "이미 포함된 곡" 방지 + 선택 후 Setlist로 복귀
                        LibraryScreen(
                            vm = component.libraryViewModel,
                            onOpenViewer = { scoreId, title, fileName ->
                                component.viewerSessionStore.setLastPicker(ViewerPickerContext.library())
                                component.closeLibraryOverlay()
                                val filePath = File(context.filesDir, "scores/$fileName").absolutePath
                                component.onScoreSelected(
                                    ViewerOpenRequest(scoreId = scoreId, title = title, filePath = filePath)
                                )
                            },

                            // ✅ pick 모드: 즉시 DB 반영 X, 체크만 토글
                            onPickScore = if (pickingForSetlistId != null) {
                                { scoreId, _, _ ->
                                    val isSelected = pickingSelectedIds.contains(scoreId)

                                    pickingSelectedIds = if (isSelected) {
                                        pickingSelectedIds - scoreId
                                    } else {
                                        pickingSelectedIds + scoreId
                                    }

                                    pickingOrderedIds = if (isSelected) {
                                        pickingOrderedIds.filterNot { it == scoreId }
                                    } else {
                                        pickingOrderedIds + scoreId // 새로 선택한 곡은 맨 뒤에 추가
                                    }
                                }
                            } else null,

                            onCancelPick = cancel@{
                                val sid = pickingForSetlistId ?: return@cancel

                                pickingForSetlistId = null
                                pickingSelectedIds = emptySet()
                                pickingOrderedIds = emptyList()

                                // ✅ 다시 세트리스트 상세로
                                openedSetlistId = sid
                                selectedTab = HomeTab.Setlists
                                component.viewerSessionStore.setLastPicker(ViewerPickerContext.setlistDetail(sid))
                            },

                            onDonePick = done@{
                                val sid = pickingForSetlistId ?: return@done
                                val newIds = pickingOrderedIds

                                scope.launch {
                                    runCatching { updateItemsUseCase(sid, newIds) }
                                        .onSuccess {
                                            pickingForSetlistId = null
                                            pickingSelectedIds = emptySet()
                                            pickingOrderedIds = emptyList()

                                            openedSetlistId = sid
                                            selectedTab = HomeTab.Setlists
                                            component.viewerSessionStore.setLastPicker(ViewerPickerContext.setlistDetail(sid))
                                        }
                                }
                            },

                            pickedScoreIds = pickingSelectedIds,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    HomeTab.Setlists -> {
                        // ✅ Setlist 화면 진입 시 Library 목록 보장
                        LaunchedEffect(Unit) {
                            component.libraryViewModel.refresh()
                        }

                        val context = LocalContext.current
                        val setlistRepo = remember(context.applicationContext) {
                            SetlistDi.provideRepository(context.applicationContext)
                        }
                        val updateItemsUseCase = remember(setlistRepo) {
                            UpdateSetlistItemsUseCase(setlistRepo)
                        }

                        if (openedSetlistId == null) {
                            LaunchedEffect(Unit) {
                                component.viewerSessionStore.setLastPicker(ViewerPickerContext.setlists())
                            }

                            SetlistListScreen(
                                modifier = Modifier.fillMaxSize(),
                                onOpenSetlist = { setlistId ->
                                    openedSetlistId = setlistId
                                    component.viewerSessionStore.setLastPicker(
                                        ViewerPickerContext.setlistDetail(setlistId)
                                    )
                                }
                            )
                        } else {
                            val setlistId = openedSetlistId!!
                            val scores by component.libraryViewModel.scores.collectAsState()

                            // 2) SetlistDetailScreen 콜백 2개 채우기
                            SetlistDetailScreen(
                                setlistId = setlistId,
                                libraryCandidates = scores.map { s ->
                                    ScorePickItem(
                                        scoreId = s.id,
                                        title = s.title,
                                        fileName = s.fileName,
                                        filePath = File(context.filesDir, "scores/${s.fileName}").absolutePath
                                    )
                                },
                                onBack = {
                                    openedSetlistId = null
                                    component.viewerSessionStore.setLastPicker(ViewerPickerContext.setlists())
                                },
                                onOpenSetlistViewer = { id, setlistTitle, orderedIds, initialScoreId ->
                                    val scoreMap = scores.associateBy { it.id }
                                    val requests = orderedIds.mapNotNull { scoreId ->
                                        val s = scoreMap[scoreId] ?: return@mapNotNull null
                                        val fp = File(context.filesDir, "scores/${s.fileName}").absolutePath
                                        if (!File(fp).exists()) return@mapNotNull null
                                        ViewerOpenRequest(scoreId = s.id, title = s.title, filePath = fp)
                                    }

                                    component.viewerSessionStore.openSetlist(
                                        setlistId = id,
                                        setlistTitle = setlistTitle,
                                        requests = requests,
                                        initialScoreId = initialScoreId
                                    )

                                    // ✅ 뷰어로 들어가게 오버레이 닫기
                                    component.closeLibraryOverlay()
                                },
                                onRequestPickFromLibrary = { currentItemIds ->
                                    pickingForSetlistId = setlistId
                                    pickingSelectedIds = currentItemIds.toSet()
                                    pickingOrderedIds = currentItemIds

                                    // ✅ 곡목록 편집은 Library 탭에서
                                    selectedTab = HomeTab.Library
                                    component.viewerSessionStore.setLastPicker(ViewerPickerContext.library())
                                },
                                onReorderItems = { id, newItemIds ->
                                    scope.launch { updateItemsUseCase(id, newItemIds) }
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }
        }
    }
}
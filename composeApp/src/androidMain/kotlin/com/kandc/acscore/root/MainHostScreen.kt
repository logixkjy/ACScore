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
import com.kandc.acscore.di.SetlistDi
import com.kandc.acscore.shared.domain.usecase.UpdateSetlistItemsUseCase

private enum class HomeTab { Library, Setlists }

@Composable
fun MainHostScreen(component: RootComponent) {
    val overlayOpen by component.isLibraryOverlayOpen.collectAsState()
    var selectedTab by rememberSaveable { mutableStateOf(HomeTab.Library) }

    // Setlists 내 네비 (리스트 ↔ 상세)
    var openedSetlistId by rememberSaveable { mutableStateOf<String?>(null) }

    // -----------------------------
    // ✅ Pick mode(라이브러리에서 체크박스 선택)
    // -----------------------------
    var pickingForSetlistId by rememberSaveable { mutableStateOf<String?>(null) }

    // 세트리스트의 "기존(원본) itemIds" (순서 보존)
    var pickingBaseOrderedIds by rememberSaveable { mutableStateOf<List<String>>(emptyList()) }

    // 현재 체크 상태(기존 + 신규)
    var pickingCheckedIds by rememberSaveable { mutableStateOf<Set<String>>(emptySet()) }

    // 신규로 체크한 것들의 "추가 순서" (완료 시 뒤에 붙임)
    val pickingAddedOrder = remember { mutableStateListOf<String>() }

    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // ✅ setlist usecase (remember로 고정)
    val setlistRepo = remember(context.applicationContext) {
        SetlistDi.provideRepository(context.applicationContext)
    }
    val updateSetlistItems = remember(setlistRepo) {
        UpdateSetlistItemsUseCase(setlistRepo)
    }

    Box(Modifier.fillMaxSize()) {

        // 아래: Viewer
        TabbedViewerScreen(
            sessionStore = component.viewerSessionStore,
            onRequestOpenLibrary = {
                selectedTab = HomeTab.Library
                openedSetlistId = null
                component.openLibraryOverlay()
            },
            modifier = Modifier.fillMaxSize()
        )

        // 위: Overlay
        LibraryOverlay(
            visible = overlayOpen,
            onDismiss = { component.closeLibraryOverlay() }
        ) {
            Column(Modifier.fillMaxSize()) {

                // 세그먼트 컨트롤
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

                    // ---------------------------------------------------------
                    // Library 탭 (Pick mode 포함)
                    // ---------------------------------------------------------
                    HomeTab.Library -> {
                        LibraryScreen(
                            vm = component.libraryViewModel,

                            // 일반 열기 (단일 PDF)
                            onOpenViewer = { scoreId, title, fileName ->
                                val filePath = File(context.filesDir, "scores/$fileName").absolutePath
                                component.onScoreSelected(
                                    ViewerOpenRequest(scoreId = scoreId, title = title, filePath = filePath)
                                )
                                component.closeLibraryOverlay()
                            },

                            // ✅ Pick mode: 탭하면 "체크 토글"만 하고 DB 반영은 '완료'에서 한 번에
                            onPickScore = pickingForSetlistId?.let { _ ->
                                { scoreId, _, _ ->
                                    val isChecked = pickingCheckedIds.contains(scoreId)
                                    if (isChecked) {
                                        // 체크 해제
                                        pickingCheckedIds = pickingCheckedIds - scoreId
                                        pickingAddedOrder.remove(scoreId) // 신규로 추가했던 항목이면 순서에서도 제거
                                    } else {
                                        // 체크
                                        pickingCheckedIds = pickingCheckedIds + scoreId
                                        // 기존에 없던 신규 항목이면 추가 순서 기록
                                        if (!pickingBaseOrderedIds.contains(scoreId) && !pickingAddedOrder.contains(scoreId)) {
                                            pickingAddedOrder.add(scoreId)
                                        }
                                    }
                                }
                            },

                            // ✅ 취소: pick 모드 종료하고 setlist 상세로 복귀(변경 버림)
                            onCancelPick = if (pickingForSetlistId != null) {
                                {
                                    selectedTab = HomeTab.Setlists
                                    openedSetlistId = pickingForSetlistId
                                    pickingForSetlistId = null
                                    pickingBaseOrderedIds = emptyList()
                                    pickingCheckedIds = emptySet()
                                    pickingAddedOrder.clear()
                                }
                            } else null,

                            // ✅ 완료: 체크된 상태를 itemIds로 만들어 "한 번에" 저장
                            onDonePick = if (pickingForSetlistId != null) {
                                {
                                    val sid = pickingForSetlistId ?: return@LibraryScreen

                                    // 새 itemIds:
                                    // 1) 기존 순서 중 체크된 것만 유지
                                    // 2) 신규 추가는 pickingAddedOrder 순서대로 뒤에 붙임
                                    val baseKept = pickingBaseOrderedIds.filter { pickingCheckedIds.contains(it) }
                                    val newAdds = pickingAddedOrder.filter { pickingCheckedIds.contains(it) }
                                    val newIds = (baseKept + newAdds).distinct()

                                    scope.launch {
                                        runCatching {
                                            updateSetlistItems(sid, newIds)
                                        }.onFailure { e ->
                                            component.libraryViewModel.emitError(e.message ?: "세트리스트 저장 실패")
                                        }.onSuccess {
                                            selectedTab = HomeTab.Setlists
                                            openedSetlistId = sid
                                            pickingForSetlistId = null
                                            pickingBaseOrderedIds = emptyList()
                                            pickingCheckedIds = emptySet()
                                            pickingAddedOrder.clear()
                                        }
                                    }
                                }
                            } else null,

                            // ✅ Pick 모드에서 이미 체크된 것 표시(“추가됨”)
                            pickedScoreIds = pickingCheckedIds
                        )
                    }

                    // ---------------------------------------------------------
                    // Setlists 탭 (리스트 ↔ 상세)
                    // ---------------------------------------------------------
                    HomeTab.Setlists -> {
                        val candidates = rememberLibraryCandidates(component)
                        val candidatesMap = remember(candidates) { candidates.associateBy { it.scoreId } }

                        val sid = openedSetlistId
                        if (sid == null) {
                            SetlistListScreen(
                                onOpenSetlist = { setlistId ->
                                    openedSetlistId = setlistId
                                }
                            )
                        } else {
                            SetlistDetailScreen(
                                setlistId = sid,
                                libraryCandidates = candidates,
                                onBack = { openedSetlistId = null },

                                // ✅ 세트리스트에서 곡 열기: "세트리스트 전체를 이어서" 열기
                                // SetlistDetailScreen 쪽 시그니처:
                                // onOpenViewer(scoreId, title, fileName, orderedIds)
                                onOpenViewer = { scoreId, _, _, orderedIds ->
                                    val requests = orderedIds.mapNotNull { id ->
                                        val item = candidatesMap[id] ?: return@mapNotNull null
                                        val filePath = File(context.filesDir, "scores/${item.fileName}").absolutePath
                                        ViewerOpenRequest(
                                            scoreId = item.scoreId,
                                            title = item.title,
                                            filePath = filePath
                                        )
                                    }

                                    if (requests.isNotEmpty()) {
                                        component.viewerSessionStore.openSetlist(
                                            requests = requests,
                                            initialScoreId = scoreId
                                        )
                                        component.closeLibraryOverlay()
                                    }
                                },

                                // ✅ "+ 추가" 눌렀을 때: Library pick 모드로 이동
                                onRequestPickFromLibrary = { currentItemIds ->
                                    pickingForSetlistId = sid

                                    // 원본(순서 보존)
                                    pickingBaseOrderedIds = currentItemIds

                                    // 체크 초기값 = 원본 전체 체크
                                    pickingCheckedIds = currentItemIds.toSet()

                                    // 신규 추가 순서 초기화
                                    pickingAddedOrder.clear()

                                    selectedTab = HomeTab.Library
                                },

                                // reorder 저장
                                onReorderItems = { setlistId, newIds ->
                                    scope.launch {
                                        runCatching {
                                            updateSetlistItems(setlistId, newIds)
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
package com.kandc.acscore.ui.common

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * iOS 세그먼트 컨트롤 느낌의 상단 탭.
 * - Material3의 TabRow를 "segmented"처럼 쓰는 MVP 버전
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SegmentedTabs(
    titles: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    PrimaryTabRow(
        selectedTabIndex = selectedIndex,
        modifier = modifier.fillMaxWidth(),
        // 탭바가 너무 커보이면 padding으로 잡아줌
        // (Material3 버전에 따라 파라미터가 없을 수 있어 생략 가능)
    ) {
        titles.forEachIndexed { index, title ->
            Tab(
                selected = selectedIndex == index,
                onClick = { onSelect(index) },
                text = { Text(title) }
            )
        }
    }
}
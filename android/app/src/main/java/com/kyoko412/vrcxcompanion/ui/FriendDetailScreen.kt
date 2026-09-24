package com.kyoko412.vrcxcompanion.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun FriendDetailScreen(
    name: String,
    state: HistoryUiState,
    onBack: () -> Unit,
    onTab: (HistoryTab) -> Unit,
    onLoadMore: () -> Unit,
    onRefresh: () -> Unit
) {
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onBack) { Text("返回") }
            Text(name, style = MaterialTheme.typography.headlineSmall)
        }
        Row {
            listOf(HistoryTab.Visits to "地图访问", HistoryTab.Encounters to "共同好友见面",
                HistoryTab.Bio to "简介历史").forEach { (tab, label) ->
                TextButton(onClick = { onTab(tab) }) {
                    Text(label, color = if (tab == state.tab) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Button(onClick = onRefresh, enabled = !state.loading) { Text("刷新") }
        if (state.error != null) Text("读取失败：${state.error.message}", color = MaterialTheme.colorScheme.error)
        if (state.loading && visibleCount(state) == 0) Text("正在读取…")
        if (!state.loading && state.error == null && visibleCount(state) == 0) Text("本机暂无记录")
        LazyColumn(modifier = Modifier.weight(1f)) {
            when (state.tab) {
                HistoryTab.Visits -> items(state.visits, key = { it.eventKey }) { visit ->
                    RecordCard {
                        Text(visit.worldName ?: visit.worldId ?: "未知地图", style = MaterialTheme.typography.titleMedium)
                        Text("进入：${localTime(visit.enteredAt)}")
                        Text(if (visit.exitedAt == null) "离开时间未观察到" else "离开：${localTime(visit.exitedAt)}")
                        Text("${durationText(visit.durationMs)} · 去过 ${visit.visitCount} 次")
                    }
                }
                HistoryTab.Encounters -> {
                    item { Text("已确认见面 ${state.qualifiedCount} 次；无法判定 ${state.unknownCount} 次") }
                    items(state.encounters, key = { it.visitKey }) { encounter ->
                        RecordCard {
                            Text(encounter.worldName ?: encounter.worldId ?: "未知地图", style = MaterialTheme.typography.titleMedium)
                            Text("本机观察到：${localTime(encounter.observedAt)}")
                        }
                    }
                }
                HistoryTab.Bio -> items(state.bios, key = { it.id }) { bio ->
                    RecordCard {
                        Text("本机观察到的变化时间：${localTime(bio.observedAt)}")
                        Text("修改前：${bio.previousBio.orEmpty()}")
                        Text("修改后：${bio.bio.orEmpty()}")
                    }
                }
            }
            if (state.nextCursor != null) item {
                Button(onClick = onLoadMore, enabled = !state.loading) { Text("加载更多") }
            }
        }
    }
}

private fun visibleCount(state: HistoryUiState): Int = when (state.tab) {
    HistoryTab.Visits -> state.visits.size
    HistoryTab.Encounters -> state.encounters.size
    HistoryTab.Bio -> state.bios.size
}

@Composable
private fun RecordCard(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        content()
        HorizontalDivider()
    }
}

package com.kyoko412.vrcxcompanion.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun GameLogScreen(state: ConnectionUiState, onBack: () -> Unit,
                  onRefresh: () -> Unit, onLoadMore: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        PageHeader("我的游戏日志", "电脑记录的地图经历", onBack, onRefresh, !state.loading)
        RecordSurface {
            Text("仅显示已确认属于当前账号的记录；旧日志可能缺失",
                style = MaterialTheme.typography.bodySmall, color = CompanionColors.muted)
        }
        if (state.connection != ConnectionState.Ready) {
            Text(state.connection.label, color = CompanionColors.muted)
        }
        if (state.loading && state.records.isEmpty()) SmallLabel("正在读取…")
        if (!state.loading && state.connection == ConnectionState.Ready && state.records.isEmpty()) {
            EmptyRecords()
        }
        LazyColumn(modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(state.records, key = { it.id }) { record ->
                RecordSurface {
                    Text(record.worldName ?: record.worldId ?: "未知地图",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold)
                    SmallLabel("进入 · ${localTime(record.createdAt)}")
                    SmallLabel(durationText(record.durationMs))
                    SmallLabel(record.location)
                }
            }
            if (state.nextCursor != null) item {
                Box(Modifier.fillMaxWidth().padding(top = 10.dp), contentAlignment = Alignment.Center) {
                    OutlinedButton(onClick = onLoadMore, enabled = !state.loading) { Text("加载更多") }
                }
            }
        }
    }
}

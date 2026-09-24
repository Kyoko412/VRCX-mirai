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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun GameLogScreen(state: ConnectionUiState, onBack: () -> Unit,
                  onRefresh: () -> Unit, onLoadMore: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onBack) { Text("返回") }
            Text("我的游戏日志", style = MaterialTheme.typography.headlineSmall)
        }
        Text("仅显示已确认属于当前账号的记录；旧日志可能缺失")
        Text(state.connection.label)
        Button(onClick = onRefresh, enabled = !state.loading) { Text("刷新") }
        if (state.loading && state.records.isEmpty()) Text("正在读取…")
        if (!state.loading && state.connection == ConnectionState.Ready && state.records.isEmpty()) Text("本机暂无记录")
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(state.records, key = { it.id }) { record ->
                Column(Modifier.fillMaxWidth().padding(vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(record.worldName ?: record.worldId ?: "未知地图",
                        style = MaterialTheme.typography.titleMedium)
                    Text("进入：${localTime(record.createdAt)}")
                    Text("${durationText(record.durationMs)} · ${record.location}")
                    HorizontalDivider()
                }
            }
            if (state.nextCursor != null) item {
                Button(onClick = onLoadMore, enabled = !state.loading) { Text("加载更多") }
            }
        }
    }
}

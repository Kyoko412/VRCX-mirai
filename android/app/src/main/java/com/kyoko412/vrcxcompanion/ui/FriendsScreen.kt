package com.kyoko412.vrcxcompanion.ui

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun FriendsScreen(
    state: FriendsUiState,
    onSearch: (String) -> Unit,
    onOpen: (String, String) -> Unit,
    onLoadMore: () -> Unit,
    onRefresh: () -> Unit,
    onBack: () -> Unit
) {
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onBack) { Text("返回") }
            Text("好友", style = MaterialTheme.typography.headlineSmall)
        }
        OutlinedTextField(value = state.search, onValueChange = onSearch,
            label = { Text("搜索好友名称") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        Button(onClick = onRefresh, enabled = !state.loading) { Text("刷新") }
        if (state.error != null) Text("读取失败：${state.error.message}", color = MaterialTheme.colorScheme.error)
        if (state.loading && state.items.isEmpty()) Text("正在读取…")
        if (!state.loading && state.error == null && state.items.isEmpty()) Text("本机暂无记录")
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(state.items, key = { it.id }) { friend ->
                Column(Modifier.fillMaxWidth().clickable { onOpen(friend.id, friend.displayName) }
                    .padding(vertical = 12.dp)) {
                    Text(friend.displayName, style = MaterialTheme.typography.titleMedium)
                }
                HorizontalDivider()
            }
            if (state.nextCursor != null) item {
                Button(onClick = onLoadMore, enabled = !state.loading) { Text("加载更多") }
            }
        }
    }
}

package com.kyoko412.vrcxcompanion.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun FriendsScreen(
    state: FriendsUiState,
    onSearch: (String) -> Unit,
    onOpen: (String, String) -> Unit,
    onLoadMore: () -> Unit,
    onRefresh: () -> Unit,
    onBack: () -> Unit
) {
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        PageHeader("好友记录", "查看好友的地图、见面与简介历史", onBack,
            onRefresh, !state.loading)
        OutlinedTextField(
            value = state.search, onValueChange = onSearch,
            placeholder = { Text("搜索好友名称") },
            leadingIcon = { Text("⌕", fontSize = 25.sp, color = CompanionColors.muted) },
            modifier = Modifier.fillMaxWidth(), singleLine = true,
            shape = RoundedCornerShape(15.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = CompanionColors.surface,
                unfocusedContainerColor = CompanionColors.surface,
                focusedBorderColor = CompanionColors.red,
                unfocusedBorderColor = CompanionColors.outline)
        )
        SmallLabel(if (state.search.isEmpty()) "已加载 ${state.items.size} 位好友"
            else "找到 ${state.items.size} 位好友")
        if (state.error != null) Text("读取失败：${state.error.message}",
            color = MaterialTheme.colorScheme.error)
        if (state.loading && state.items.isEmpty()) SmallLabel("正在读取…")
        if (!state.loading && state.error == null && state.items.isEmpty()) {
            EmptyRecords(if (state.search.isBlank()) "本机暂无记录" else "没有匹配的好友")
        }
        LazyColumn(modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(state.items, key = { it.id }) { friend ->
                Surface(modifier = Modifier.fillMaxWidth()
                    .clickable { onOpen(friend.id, friend.displayName) },
                    shape = RoundedCornerShape(16.dp), color = CompanionColors.surface) {
                    Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Surface(shape = CircleShape, color = CompanionColors.raised,
                            modifier = Modifier.size(42.dp)) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(friend.displayName.firstOrNull()?.uppercase() ?: "?",
                                    color = CompanionColors.red,
                                    fontWeight = FontWeight.Bold)
                            }
                        }
                        Text(friend.displayName, modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium, maxLines = 1,
                            overflow = TextOverflow.Ellipsis)
                        Text("›", color = CompanionColors.muted, fontSize = 24.sp)
                    }
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

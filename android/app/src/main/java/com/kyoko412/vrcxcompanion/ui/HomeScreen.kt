package com.kyoko412.vrcxcompanion.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun HomeScreen(
    computer: String,
    account: String,
    lastRead: String?,
    connection: String,
    onFriends: () -> Unit,
    onGameLog: () -> Unit,
    onRefresh: () -> Unit,
    onUnpair: () -> Unit
) {
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("VRCX 手机记录", style = MaterialTheme.typography.headlineSmall)
        Text("电脑：$computer")
        Text("账号：$account")
        Text("连接：$connection")
        Text("最近成功读取：${localTime(lastRead)}")
        Button(onClick = onRefresh) { Text("刷新连接") }
        Button(onClick = onFriends) { Text("查看好友") }
        Button(onClick = onGameLog) { Text("我的游戏日志") }
        Button(onClick = onUnpair) { Text("解除配对") }
    }
}

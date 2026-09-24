package com.kyoko412.vrcxcompanion.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun HomeScreen(
    computer: String,
    account: String,
    lastRead: String?,
    connection: String,
    onFriends: () -> Unit,
    onGameLog: () -> Unit,
    onRefresh: () -> Unit,
    onRescan: () -> Unit,
    onUnpair: () -> Unit
) {
    var showDetails by remember { mutableStateOf(false) }
    var confirmUnpair by remember { mutableStateOf(false) }
    val connected = connection == ConnectionState.Ready.label

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())
        .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text("VRCX  /  MOBILE", style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold, letterSpacing = 2.sp, color = CompanionColors.red)
            Text("手机记录", style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold)
            Text("在同一 Wi-Fi 下查看电脑保存的记录", color = CompanionColors.muted,
                style = MaterialTheme.typography.bodyMedium)
        }

        RecordSurface {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusDot(if (connected) CompanionColors.green else CompanionColors.red)
                Text(connection, modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (connected) CompanionColors.green else MaterialTheme.colorScheme.error,
                    maxLines = 2)
                TextButton(onClick = onRefresh) { Text("刷新连接") }
            }
            Text(computer, style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold, maxLines = 1,
                overflow = TextOverflow.Ellipsis)
            SmallLabel(if (lastRead == null) "尚未成功读取" else "最近读取 · ${localTime(lastRead)}")
            TextButton(onClick = { showDetails = !showDetails }) {
                Text(if (showDetails) "收起连接详情" else "连接详情")
            }
            if (showDetails) {
                SmallLabel("当前账号 ID")
                Text(account, style = MaterialTheme.typography.bodySmall,
                    color = CompanionColors.muted)
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SmallLabel("记录")
            NavigationCard("好友记录", "地图访问 · 见面记录 · 简介历史", "友", onFriends)
            NavigationCard("我的游戏日志", "仅显示当前账号的游戏记录", "游", onGameLog)
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            SmallLabel("连接管理")
            TextButton(onClick = onRescan) { Text("电脑地址变化？重新扫码") }
            TextButton(onClick = { confirmUnpair = true }) {
                Text("解除配对", color = CompanionColors.muted)
            }
        }
    }

    if (confirmUnpair) AlertDialog(
        onDismissRequest = { confirmUnpair = false },
        title = { Text("解除这台手机的配对？") },
        text = { Text("之后需要重新扫描电脑上的二维码。") },
        confirmButton = { TextButton(onClick = { confirmUnpair = false; onUnpair() }) {
            Text("解除配对", color = MaterialTheme.colorScheme.error)
        } },
        dismissButton = { TextButton(onClick = { confirmUnpair = false }) { Text("取消") } }
    )
}

@Composable
private fun NavigationCard(title: String, subtitle: String, initial: String, onClick: () -> Unit) {
    Surface(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp), color = CompanionColors.surface) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Surface(shape = CircleShape, color = CompanionColors.redTint,
                modifier = Modifier.size(46.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Text(initial, fontWeight = FontWeight.Bold, color = CompanionColors.red)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall,
                    color = CompanionColors.muted, maxLines = 1,
                    overflow = TextOverflow.Ellipsis)
            }
            Text("›", fontSize = 25.sp, color = CompanionColors.muted)
        }
    }
}

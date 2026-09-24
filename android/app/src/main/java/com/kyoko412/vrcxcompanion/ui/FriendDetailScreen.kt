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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.kyoko412.vrcxcompanion.network.BioChangeDto
import com.kyoko412.vrcxcompanion.network.EncounterDto
import com.kyoko412.vrcxcompanion.network.VisitDto

@Composable
fun FriendDetailScreen(
    name: String,
    state: HistoryUiState,
    onBack: () -> Unit,
    onTab: (HistoryTab) -> Unit,
    onLoadMore: () -> Unit,
    onRefresh: () -> Unit
) {
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        PageHeader(name, "好友记录 · 本机观察到的数据", onBack, onRefresh, !state.loading)
        HistoryTabs(state.tab, onTab)
        if (state.error != null) Text("读取失败：${state.error.message}",
            color = MaterialTheme.colorScheme.error)
        if (state.loading && visibleCount(state) == 0) SmallLabel("正在读取…")
        if (!state.loading && state.error == null && visibleCount(state) == 0) EmptyRecords()
        LazyColumn(modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            when (state.tab) {
                HistoryTab.Visits -> items(state.visits, key = { it.eventKey }) { VisitCard(it) }
                HistoryTab.Encounters -> {
                    item {
                        RecordSurface {
                            Text("共同好友见面", style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold)
                            Text("已确认 ${state.qualifiedCount} 次 · 无法判定 ${state.unknownCount} 次",
                                color = CompanionColors.muted,
                                style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    items(state.encounters, key = { it.visitKey }) { EncounterCard(it) }
                }
                HistoryTab.Bio -> items(state.bios, key = { it.id }) { BioCard(it) }
            }
            if (state.nextCursor != null) item {
                Box(Modifier.fillMaxWidth().padding(top = 10.dp), contentAlignment = Alignment.Center) {
                    OutlinedButton(onClick = onLoadMore, enabled = !state.loading) { Text("加载更多") }
                }
            }
        }
    }
}

@Composable
private fun HistoryTabs(selected: HistoryTab, onTab: (HistoryTab) -> Unit) {
    val tabs = listOf(HistoryTab.Visits to "地图访问", HistoryTab.Encounters to "见面记录",
        HistoryTab.Bio to "简介历史")
    Surface(shape = RoundedCornerShape(15.dp), color = CompanionColors.surface) {
        Row(Modifier.fillMaxWidth().padding(4.dp)) {
            tabs.forEach { (tab, label) ->
                val active = tab == selected
                Box(Modifier.weight(1f)
                    .clickable { onTab(tab) }
                    .padding(2.dp), contentAlignment = Alignment.Center) {
                    Surface(shape = RoundedCornerShape(11.dp),
                        color = if (active) CompanionColors.raised else CompanionColors.surface,
                        modifier = Modifier.fillMaxWidth()) {
                        Box(Modifier.padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                            Text(label, style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                                color = if (active) CompanionColors.red else CompanionColors.muted)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VisitCard(visit: VisitDto) {
    RecordSurface {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(visit.worldName ?: visit.worldId ?: "未知地图", Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Surface(shape = RoundedCornerShape(8.dp), color = CompanionColors.redTint) {
                Text("去过 ${visit.visitCount} 次", Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                    style = MaterialTheme.typography.labelSmall, color = CompanionColors.red)
            }
        }
        SmallLabel("进入 · ${localTime(visit.enteredAt)}")
        SmallLabel(if (visit.exitedAt == null) "离开时间未观察到"
            else "离开 · ${localTime(visit.exitedAt)}")
        Text(durationText(visit.durationMs), style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun EncounterCard(encounter: EncounterDto) {
    RecordSurface {
        Text(encounter.worldName ?: encounter.worldId ?: "未知地图",
            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        SmallLabel("本机观察到 · ${localTime(encounter.observedAt)}")
    }
}

@Composable
private fun BioCard(bio: BioChangeDto) {
    RecordSurface {
        SmallLabel("本机观察到的变化 · ${localTime(bio.observedAt)}")
        BioBlock("修改前", bio.previousBio.orEmpty())
        BioBlock("修改后", bio.bio.orEmpty())
    }
}

@Composable
private fun BioBlock(label: String, value: String) {
    var expanded by remember(value) { mutableStateOf(false) }
    var hasOverflow by remember(value) { mutableStateOf(false) }
    val text = value.ifBlank { "（空简介）" }
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        SmallLabel(label, if (label == "修改后") CompanionColors.green else CompanionColors.muted)
        Surface(shape = RoundedCornerShape(12.dp), color = CompanionColors.raised) {
            SelectionContainer {
                Text(text, Modifier.fillMaxWidth().padding(12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = if (expanded) Int.MAX_VALUE else 5,
                    overflow = TextOverflow.Ellipsis,
                    onTextLayout = { if (it.hasVisualOverflow) hasOverflow = true })
            }
        }
        if (hasOverflow || expanded) {
            TextButton(onClick = { expanded = !expanded }) {
                Text(if (expanded) "收起" else "展开全文")
            }
        }
    }
}

private fun visibleCount(state: HistoryUiState): Int = when (state.tab) {
    HistoryTab.Visits -> state.visits.size
    HistoryTab.Encounters -> state.encounters.size
    HistoryTab.Bio -> state.bios.size
}

package com.kyoko412.vrcxcompanion.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object CompanionColors {
    val background = Color(0xFF111216)
    val surface = Color(0xFF1B1D23)
    val raised = Color(0xFF262932)
    val outline = Color(0xFF343740)
    val muted = Color(0xFFA5A9B4)
    val red = Color(0xFFF05462)
    val redTint = Color(0xFF3B222A)
    val green = Color(0xFF62D6A0)
    val greenTint = Color(0xFF1D3830)
}

private val companionScheme = darkColorScheme(
    primary = CompanionColors.red,
    onPrimary = Color.White,
    background = CompanionColors.background,
    onBackground = Color(0xFFF5F5F7),
    surface = CompanionColors.surface,
    onSurface = Color(0xFFF5F5F7),
    surfaceVariant = CompanionColors.raised,
    onSurfaceVariant = CompanionColors.muted,
    outline = CompanionColors.outline,
    error = Color(0xFFFF858D)
)

@Composable
fun CompanionTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = companionScheme, typography = Typography(), content = content)
}

@Composable
fun PageHeader(title: String, subtitle: String? = null, onBack: (() -> Unit)? = null,
               onRefresh: (() -> Unit)? = null, refreshEnabled: Boolean = true) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (onBack != null) {
            Surface(shape = CircleShape, color = CompanionColors.surface,
                modifier = Modifier.size(44.dp).clickable(onClick = onBack)
                    .semantics { contentDescription = "返回" }) {
                Box(contentAlignment = Alignment.Center) {
                    Text("←", fontSize = 23.sp, lineHeight = 26.sp,
                        color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall,
                color = CompanionColors.muted, maxLines = 1)
        }
        if (onRefresh != null) TextButton(onClick = onRefresh, enabled = refreshEnabled) {
            Text("刷新", fontSize = 14.sp)
        }
    }
}

@Composable
fun RecordSurface(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp),
        color = CompanionColors.surface) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}

@Composable
fun SmallLabel(text: String, color: Color = CompanionColors.muted) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = color)
}

@Composable
fun StatusDot(color: Color, modifier: Modifier = Modifier) {
    Box(modifier.size(8.dp).background(color, CircleShape))
}

@Composable
fun EmptyRecords(message: String = "本机暂无记录") {
    Column(Modifier.fillMaxWidth().padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("○", fontSize = 32.sp, color = CompanionColors.muted)
        Text(message, color = CompanionColors.muted)
    }
}

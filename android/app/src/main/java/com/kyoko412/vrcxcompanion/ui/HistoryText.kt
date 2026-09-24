package com.kyoko412.vrcxcompanion.ui

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val localTimeFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

fun localTime(value: String?): String = value?.let {
    runCatching { Instant.parse(it).atZone(ZoneId.systemDefault()).format(localTimeFormat) }
        .getOrDefault(it)
} ?: "未知"

fun durationText(milliseconds: Long?): String = when {
    milliseconds == null -> "时长未观察到"
    milliseconds < 60_000 -> "不到 1 分钟"
    else -> "${milliseconds / 60_000} 分钟"
}

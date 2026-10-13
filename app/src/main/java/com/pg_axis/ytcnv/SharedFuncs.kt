package com.pg_axis.ytcnv

import com.pg_axis.ytcnv.models.QualityOption

fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 KB"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1 -> String.format(java.util.Locale.getDefault(), "%.2f GB", gb)
        mb >= 1 -> String.format(java.util.Locale.getDefault(), "%.1f MB", mb)
        else -> String.format(java.util.Locale.getDefault(), "%.0f KB", kb)
    }
}

fun qualityLabel(option: QualityOption): String {
    val nativePart = if (!option.isNative) " · converted" else ""
    val sizePart = option.sizeBytes?.let { " (${formatBytes(it)})" } ?: ""
    return "${option.displayName}$nativePart$sizePart"
}
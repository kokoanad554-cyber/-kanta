package com.example.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.log10
import kotlin.math.pow

object FormatUtils {
    private val dateFormatter = SimpleDateFormat("yyyy/MM/dd  HH:mm", Locale.US)

    fun formatBytes(bytes: Long): String {
        if (bytes <= 0L) return "0 ب"
        val units = arrayOf("ب", "ك.ب", "م.ب", "ج.ب", "ت.ب")
        val digitGroups = (log10(bytes.toDouble()) / log10(1024.0)).toInt().coerceIn(0, units.lastIndex)
        val value = bytes / 1024.0.pow(digitGroups.toDouble())
        val formatted = if (digitGroups == 0) {
            bytes.toString()
        } else {
            String.format(Locale.US, "%.1f", value)
        }
        return "$formatted ${units[digitGroups]}"
    }

    fun formatDate(millis: Long): String {
        if (millis <= 0L) return "غير معروف"
        return dateFormatter.format(Date(millis))
    }

    fun formatCountItems(count: Int): String = when {
        count == 0 -> "فارغ"
        count == 1 -> "عنصر واحد"
        count == 2 -> "عنصران"
        count in 3..10 -> "$count عناصر"
        else -> "$count عنصراً"
    }
}

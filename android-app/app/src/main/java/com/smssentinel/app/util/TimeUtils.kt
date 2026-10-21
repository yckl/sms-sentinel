package com.smssentinel.app.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object TimeUtils {
    private val clockFormat = SimpleDateFormat("HH:mm", Locale.CHINA)
    private val dateTimeFormat = SimpleDateFormat("MM-dd HH:mm", Locale.CHINA)

    fun clock(timestamp: Long): String = clockFormat.format(Date(timestamp))

    fun dateTime(timestamp: Long): String = dateTimeFormat.format(Date(timestamp))

    fun nextRing(timestamp: Long): String {
        if (timestamp <= 0L) return "—"
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply { timeInMillis = timestamp }
        val sameDay = now.get(Calendar.YEAR) == target.get(Calendar.YEAR) &&
            now.get(Calendar.DAY_OF_YEAR) == target.get(Calendar.DAY_OF_YEAR)
        return if (sameDay) clock(timestamp) else dateTime(timestamp)
    }

    fun isToday(timestamp: Long): Boolean {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply { timeInMillis = timestamp }
        return now.get(Calendar.YEAR) == target.get(Calendar.YEAR) &&
            now.get(Calendar.DAY_OF_YEAR) == target.get(Calendar.DAY_OF_YEAR)
    }

    fun dayLabel(timestamp: Long): String {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply { timeInMillis = timestamp }
        val sameYear = now.get(Calendar.YEAR) == target.get(Calendar.YEAR)
        val dayDiff = if (sameYear) {
            now.get(Calendar.DAY_OF_YEAR) - target.get(Calendar.DAY_OF_YEAR)
        } else {
            -1
        }
        return when {
            dayDiff == 0 -> "今天"
            dayDiff == 1 -> "昨天"
            sameYear -> "${target.get(Calendar.MONTH) + 1}月${target.get(Calendar.DAY_OF_MONTH)}日"
            else -> "${target.get(Calendar.YEAR)}年${target.get(Calendar.MONTH) + 1}月${target.get(Calendar.DAY_OF_MONTH)}日"
        }
    }
}

package com.smssentinel.app.data

import android.content.Context

class SettingsStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("sentinel_settings", Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = prefs.getBoolean("enabled", true)
        set(value) = prefs.edit().putBoolean("enabled", value).apply()

    var targetNumber: String
        get() = prefs.getString("target_number", DEFAULT_NUMBER) ?: DEFAULT_NUMBER
        set(value) = prefs.edit().putString("target_number", value.trim()).apply()

    var intervalMinutes: Int
        get() = prefs.getInt("interval_minutes", 10)
        set(value) = prefs.edit().putInt("interval_minutes", value).apply()

    var ringSeconds: Int
        get() = prefs.getInt("ring_seconds", 60)
        set(value) = prefs.edit().putInt("ring_seconds", value).apply()

    var soundEnabled: Boolean
        get() = prefs.getBoolean("sound_enabled", true)
        set(value) = prefs.edit().putBoolean("sound_enabled", value).apply()

    var vibrationEnabled: Boolean
        get() = prefs.getBoolean("vibration_enabled", true)
        set(value) = prefs.edit().putBoolean("vibration_enabled", value).apply()

    var lockScreenBody: Boolean
        get() = prefs.getBoolean("lock_screen_body", false)
        set(value) = prefs.edit().putBoolean("lock_screen_body", value).apply()

    var filterEnabled: Boolean
        get() = prefs.getBoolean("filter_enabled", true)
        set(value) = prefs.edit().putBoolean("filter_enabled", value).apply()

    var filterKeywords: String
        get() = prefs.getString("filter_keywords", DEFAULT_KEYWORDS) ?: DEFAULT_KEYWORDS
        set(value) = prefs.edit().putString("filter_keywords", value.trim()).apply()

    var backgroundService: Boolean
        get() = prefs.getBoolean("background_service", true)
        set(value) = prefs.edit().putBoolean("background_service", value).apply()

    var retentionDays: Int
        get() = prefs.getInt("retention_days", 30)
        set(value) = prefs.edit().putInt("retention_days", value).apply()

    var onboarded: Boolean
        get() = prefs.getBoolean("onboarded", false)
        set(value) = prefs.edit().putBoolean("onboarded", value).apply()

    fun matchesFilter(body: String): Boolean {
        if (!filterEnabled) return true
        val keywords = filterKeywords
            .split(',', '，', ';', '；', ' ')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        if (keywords.isEmpty()) return true
        return keywords.any { body.contains(it, ignoreCase = true) }
    }

    val intervalMillis: Long get() = intervalMinutes.coerceAtLeast(1) * 60_000L

    fun simulatedBody(): String =
        "【宜城市大数据中心】【OA】在公文交换中您有一份待签收文件：会议通知[宜城市政府办]"

    companion object {
        const val DEFAULT_NUMBER = "10650821192421"
        const val DEFAULT_KEYWORDS = "会议,开会,参会,议程,会场"
    }
}

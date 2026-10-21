package com.smssentinel.app.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager
import com.smssentinel.app.R
import com.smssentinel.app.data.SettingsStore

object NotificationChannels {
    const val CHANNEL_SOUND = "important_sms_call_sound"
    const val CHANNEL_VIBRATE = "important_sms_call_vibrate"
    const val CHANNEL_SILENT = "important_sms_call_silent"
    const val CHANNEL_QUIET = "important_sms_call_quiet"
    const val CHANNEL_SERVICE = "sentinel_monitor"

    fun ensure(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        val ringtone = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val pattern = longArrayOf(0, 500, 300, 500, 300, 900)

        val sound = NotificationChannel(
            CHANNEL_SOUND,
            context.getString(R.string.channel_call),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.channel_call_desc)
            setSound(ringtone, attributes)
            enableVibration(true)
            vibrationPattern = pattern
            lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE
        }

        val vibrate = NotificationChannel(
            CHANNEL_VIBRATE,
            "重要短信来电（仅震动）",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            setSound(null, null)
            enableVibration(true)
            vibrationPattern = pattern
            lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE
        }

        val silent = NotificationChannel(
            CHANNEL_SILENT,
            context.getString(R.string.channel_silent),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.channel_silent_desc)
            setSound(null, null)
            enableVibration(false)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE
        }

        manager.createNotificationChannels(listOf(sound, vibrate, silent))

        val quiet = NotificationChannel(
            CHANNEL_QUIET,
            "重要短信提醒（通知栏）",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "悬浮来电胶囊显示时，通知栏保留一条不打扰的记录"
            setSound(null, null)
            enableVibration(false)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE
        }
        manager.createNotificationChannel(quiet)

        val service = NotificationChannel(
            CHANNEL_SERVICE,
            "后台监听服务",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "保持短信监听在后台运行"
            setSound(null, null)
            enableVibration(false)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE
        }
        manager.createNotificationChannel(service)
    }

    fun fallbackChannel(settings: SettingsStore): String = when {
        settings.soundEnabled -> CHANNEL_SOUND
        settings.vibrationEnabled -> CHANNEL_VIBRATE
        else -> CHANNEL_SILENT
    }
}

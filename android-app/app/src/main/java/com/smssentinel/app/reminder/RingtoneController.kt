package com.smssentinel.app.reminder

import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

class RingtoneController(
    context: Context,
    private val soundEnabled: Boolean,
    private val vibrationEnabled: Boolean,
) {
    private val appContext = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private var ringtone: Ringtone? = null
    private var vibrator: Vibrator? = null
    private var running = false

    private val replay = object : Runnable {
        override fun run() {
            if (!running) return
            ringtone?.play()
            handler.postDelayed(this, REPLAY_MS)
        }
    }

    fun start() {
        if (running) return
        running = true

        val notificationManager = appContext.getSystemService(NotificationManager::class.java)
        val audioManager = appContext.getSystemService(AudioManager::class.java)
        val dnd = notificationManager.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL
        val silent = audioManager.ringerMode != AudioManager.RINGER_MODE_NORMAL

        // 声音遵循系统静音/勿扰；震动是静音场景下唯一的提示手段，不受静音或勿扰限制。
        if (soundEnabled && !dnd && !silent) {
            ringtone = RingtoneManager.getRingtone(
                appContext,
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
            )?.apply {
                audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                play()
            }
            if (ringtone != null) handler.postDelayed(replay, REPLAY_MS)
        }

        if (vibrationEnabled) {
            vibrator = startVibrator(
                VibrationEffect.createWaveform(VIBRATION_PATTERN, 0),
                VIBRATION_PATTERN,
                0,
                appContext,
            )
        }
    }

    fun stop() {
        running = false
        handler.removeCallbacksAndMessages(null)
        ringtone?.stop()
        ringtone = null
        vibrator?.cancel()
        vibrator = null
    }

    companion object {
        private const val REPLAY_MS = 12_000L
        private val VIBRATION_PATTERN = longArrayOf(0, 500, 250, 500, 250, 900)
        private val SHORT_PATTERN = longArrayOf(0, 350, 180, 350)
        private var lastPulseAt = 0L

        private fun getVibrator(context: Context): Vibrator? =
            if (Build.VERSION.SDK_INT >= 31) {
                context.getSystemService(VibratorManager::class.java).defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }

        private fun startVibrator(
            effect: VibrationEffect,
            pattern: LongArray,
            repeat: Int,
            context: Context,
        ): Vibrator? {
            val vibrator = getVibrator(context) ?: return null
            if (!vibrator.hasVibrator()) return null
            if (Build.VERSION.SDK_INT >= 33) {
                val attributes = VibrationAttributes.Builder()
                    .setUsage(VibrationAttributes.USAGE_ALARM)
                    .build()
                vibrator.vibrate(effect, attributes)
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(
                    pattern,
                    repeat,
                    AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build(),
                )
            }
            return vibrator
        }

        @Synchronized
        fun pulse(context: Context): Boolean {
            val now = android.os.SystemClock.elapsedRealtime()
            if (now - lastPulseAt < 1_000L) return false
            lastPulseAt = now
            return startVibrator(
                VibrationEffect.createWaveform(SHORT_PATTERN, -1),
                SHORT_PATTERN,
                -1,
                context,
            ) != null
        }

        fun test(context: Context): Boolean = startVibrator(
            VibrationEffect.createWaveform(SHORT_PATTERN, -1),
            SHORT_PATTERN,
            -1,
            context,
        ) != null
    }
}

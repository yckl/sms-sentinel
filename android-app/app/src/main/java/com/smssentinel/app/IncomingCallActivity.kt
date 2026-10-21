package com.smssentinel.app

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.KeyguardManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.smssentinel.app.data.SettingsStore
import com.smssentinel.app.data.Task
import com.smssentinel.app.data.TaskStore
import com.smssentinel.app.databinding.ActivityIncomingCallBinding
import com.smssentinel.app.notification.CallNotifier
import com.smssentinel.app.reminder.ReminderScheduler
import com.smssentinel.app.reminder.RingtoneController
import com.smssentinel.app.service.MonitoringService
import com.smssentinel.app.util.Insets
import com.smssentinel.app.util.TimeUtils

class IncomingCallActivity : AppCompatActivity() {
    private lateinit var binding: ActivityIncomingCallBinding
    private lateinit var settings: SettingsStore
    private var task: Task? = null
    private var ringtone: RingtoneController? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private val pulseAnimators = mutableListOf<ObjectAnimator>()
    private val handler = Handler(Looper.getMainLooper())
    private val timeout = Runnable {
        leaveWithoutConfirm()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityIncomingCallBinding.inflate(layoutInflater)
        setContentView(binding.root)
        Insets.apply(binding.root)
        MonitoringService.alertSuppressed = true
        setupWindow()

        settings = SettingsStore(this)
        val taskId = intent.getStringExtra(EXTRA_TASK_ID)
        val loaded = taskId?.let { TaskStore.get(this, it) }
        if (loaded == null || !loaded.isPending) {
            finish()
            return
        }

        task = loaded
        render(loaded)
        CallNotifier.cancel(this, loaded.id)
        MonitoringService.stopAlertNow(loaded.id)

        binding.btnAnswer.setOnClickListener { onAnswer() }
        binding.btnDecline.setOnClickListener { onDecline() }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = leaveWithoutConfirm()
        })

        startRinging()
        startPulseAnimation()
        handler.postDelayed(timeout, settings.ringSeconds.coerceIn(15, 120) * 1_000L)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val taskId = intent.getStringExtra(EXTRA_TASK_ID) ?: return
        val loaded = TaskStore.get(this, taskId) ?: return
        if (!loaded.isPending) return
        task = loaded
        render(loaded)
        CallNotifier.cancel(this, loaded.id)
        stopRinging()
        startRinging()
        handler.removeCallbacks(timeout)
        handler.postDelayed(timeout, settings.ringSeconds.coerceIn(15, 120) * 1_000L)
    }

    private fun setupWindow() {
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        @Suppress("DEPRECATION")
        try {
            wakeLock = getSystemService(PowerManager::class.java).newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or
                    PowerManager.ACQUIRE_CAUSES_WAKEUP or
                    PowerManager.ON_AFTER_RELEASE,
                "Sentinel:IncomingCall",
            ).apply { acquire(60_000L) }
        } catch (_: Exception) {
            wakeLock = null
        }
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
    }

    private fun render(current: Task) {
        val keyguard = getSystemService(KeyguardManager::class.java)
        val locked = keyguard.isKeyguardLocked
        val showBody = !locked || settings.lockScreenBody

        binding.tvTag.text = if (current.simulated) "重要短信 · 模拟测试" else getString(R.string.call_tag)
        binding.tvStatus.text = if (current.simulated) {
            "模拟提醒 · 不会写入记录"
        } else {
            "待确认 · 第 ${current.ringCount.coerceAtLeast(1)} 次提醒"
        }
        binding.tvNumber.text = current.sender
        binding.tvBody.text = current.body
        binding.tvMsgMeta.text = "收到 ${TimeUtils.clock(current.receivedAt)} · 来源号码精确匹配"
        binding.tvBadge.text = if (current.simulated) "试" else "!"
        binding.tvFooter.text = if (current.simulated) {
            "模拟提醒不会写入记录 · 可随时挂断"
        } else {
            getString(R.string.call_footer)
        }
        binding.tvNote.text = getString(R.string.call_note)

        binding.cardPrivacy.visibility = if (showBody) View.GONE else View.VISIBLE
        binding.cardMessage.visibility = if (showBody) View.VISIBLE else View.GONE
        binding.tvAnswerLabel.text = if (showBody) {
            getString(R.string.call_answer_unlocked)
        } else {
            getString(R.string.call_answer)
        }
    }

    private fun onAnswer() {
        val keyguard = getSystemService(KeyguardManager::class.java)
        if (keyguard.isKeyguardLocked) {
            keyguard.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
                override fun onDismissSucceeded() {
                    confirmAndFinish(read = true)
                }

                override fun onDismissCancelled() {
                }

                override fun onDismissError() {
                }
            })
        } else {
            confirmAndFinish(read = true)
        }
    }

    private fun onDecline() {
        confirmAndFinish(read = false)
    }

    private fun confirmAndFinish(read: Boolean) {
        val current = task
        if (current != null) {
            if (current.simulated) {
                TaskStore.delete(this, current.id)
            } else {
                TaskStore.confirm(this, current.id, read)
                ReminderScheduler.cancel(this, current.id)
            }
            CallNotifier.cancel(this, current.id)
        }

        stopRinging()
        if (read && current != null && !current.simulated) {
            startActivity(
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    .putExtra(MainActivity.EXTRA_OPEN_TASK, current.id)
            )
        }
        finish()
    }

    private fun leaveWithoutConfirm() {
        val current = task
        if (current != null && current.simulated) {
            TaskStore.delete(this, current.id)
        }
        stopRinging()
        ensurePendingSchedule()
        finish()
    }

    private fun ensurePendingSchedule() {
        val current = task ?: return
        if (current.simulated) return
        val stored = TaskStore.get(this, current.id) ?: return
        if (!stored.isPending) return
        if (stored.nextRingAt <= System.currentTimeMillis()) {
            stored.nextRingAt = System.currentTimeMillis() + settings.intervalMillis
            TaskStore.upsert(this, stored)
        }
        ReminderScheduler.schedule(this, stored)
    }

    private fun startPulseAnimation() {
        val durationScale = runCatching {
            Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        }.getOrDefault(1f)
        if (durationScale == 0f) return

        val rings = listOf(binding.ringInner, binding.ringMiddle, binding.ringOuter)
        rings.forEachIndexed { index, ring ->
            val scaleX = ObjectAnimator.ofFloat(ring, View.SCALE_X, 1f, 1.07f)
            val scaleY = ObjectAnimator.ofFloat(ring, View.SCALE_Y, 1f, 1.07f)
            val alpha = ObjectAnimator.ofFloat(ring, View.ALPHA, 1f, 0.45f)
            listOf(scaleX, scaleY, alpha).forEach { animator ->
                animator.duration = 2200L
                animator.startDelay = index * 240L
                animator.repeatCount = ValueAnimator.INFINITE
                animator.repeatMode = ValueAnimator.REVERSE
                animator.interpolator = AccelerateDecelerateInterpolator()
                animator.start()
                pulseAnimators.add(animator)
            }
        }
    }

    private fun startRinging() {
        ringtone = RingtoneController(this, settings.soundEnabled, settings.vibrationEnabled).also {
            it.start()
        }
    }

    private fun stopRinging() {
        handler.removeCallbacks(timeout)
        ringtone?.stop()
        ringtone = null
    }

    override fun onStart() {
        super.onStart()
        MonitoringService.alertSuppressed = true
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) {
            stopRinging()
            MonitoringService.alertSuppressed = false
            task?.let { current ->
                if (current.isPending && !current.simulated) {
                    MonitoringService.showOverlay(this, current.id)
                }
            }
        }
    }

    override fun onDestroy() {
        stopRinging()
        MonitoringService.alertSuppressed = false
        MonitoringService.stopAlertNow(task?.id)
        pulseAnimators.forEach { it.cancel() }
        pulseAnimators.clear()
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        super.onDestroy()
    }

    companion object {
        const val EXTRA_TASK_ID = "task_id"
    }
}

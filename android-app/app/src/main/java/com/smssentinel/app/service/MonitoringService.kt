package com.smssentinel.app.service

import android.app.KeyguardManager
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.MutableLiveData
import com.smssentinel.app.IncomingCallActivity
import com.smssentinel.app.MainActivity
import com.smssentinel.app.R
import com.smssentinel.app.data.SettingsStore
import com.smssentinel.app.data.TaskStore
import com.smssentinel.app.notification.CallNotifier
import com.smssentinel.app.notification.NotificationChannels
import com.smssentinel.app.reminder.ReminderScheduler
import com.smssentinel.app.reminder.RingtoneController
import com.smssentinel.app.util.Permissions

class MonitoringService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var alertPlayer: RingtoneController? = null
    private lateinit var windowManager: WindowManager
    private var overlayView: View? = null
    private var overlayTaskId: String? = null
    private var alertTaskId: String? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        windowManager = getSystemService(WindowManager::class.java)
        NotificationChannels.ensure(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val settings = SettingsStore(this)
        if (!settings.enabled || !settings.backgroundService) {
            stopSelf()
            return START_NOT_STICKY
        }
        startForeground(NOTIFICATION_ID, buildNotification(settings))
        setRunning(true)
        when (intent?.action) {
            ACTION_ALERT -> startAlert(
                intent.getStringExtra(EXTRA_TASK_ID),
                intent.getLongExtra(EXTRA_RING_MS, 60_000L),
            )
            ACTION_STOP_ALERT -> stopAlert(intent.getStringExtra(EXTRA_TASK_ID))
            ACTION_SHOW_OVERLAY -> showOverlay(intent.getStringExtra(EXTRA_TASK_ID))
            ACTION_HIDE_OVERLAY -> hideOverlay()
        }
        return START_STICKY
    }

    private fun startAlert(taskId: String?, durationMs: Long) {
        if (alertSuppressed || taskId == null) return
        val settings = SettingsStore(this)
        stopRinging()
        alertTaskId = taskId
        alertPlayer = RingtoneController(this, settings.soundEnabled, settings.vibrationEnabled).also {
            it.start()
        }
        // 振铃结束后只停止声音/震动，悬浮胶囊保留到用户确认。
        handler.postDelayed({ stopRinging() }, durationMs.coerceAtLeast(1_000L))
    }

    private fun stopRinging() {
        handler.removeCallbacksAndMessages(null)
        alertPlayer?.stop()
        alertPlayer = null
        alertTaskId = null
    }

    private fun stopAlert(taskId: String?) {
        if (taskId != null && alertTaskId != taskId && overlayTaskId != taskId) return
        stopRinging()
        hideOverlay()
    }

    internal fun stopAlertNow(taskId: String?) = stopAlert(taskId)

    private fun showOverlay(taskId: String?) {
        if (alertSuppressed || taskId == null) {
            hideOverlay()
            return
        }
        if (!SettingsStore(this).enabled) return
        if (!Permissions.canDrawOverlays(this)) return
        val keyguard = getSystemService(KeyguardManager::class.java)
        if (keyguard.isKeyguardLocked) {
            hideOverlay()
            return
        }
        val task = TaskStore.get(this, taskId) ?: return
        if (!task.isPending) return

        hideOverlay()
        val view = LayoutInflater.from(this).inflate(R.layout.view_call_overlay, null)
        val count = TaskStore.pending(this).size
        view.findViewById<TextView>(R.id.overlayTitle).text =
            if (count > 1) "重要短信提醒 · 共 $count 条" else "重要短信提醒"
        view.findViewById<TextView>(R.id.overlayBody).text = task.body
        view.findViewById<View>(R.id.overlayDecline).setOnClickListener {
            declineFromOverlay(task.id)
        }
        view.findViewById<View>(R.id.overlayAnswer).setOnClickListener {
            answerFromOverlay(task.id)
        }
        view.setOnClickListener { answerFromOverlay(task.id) }

        val params = buildOverlayParams()
        try {
            windowManager.addView(view, params)
            overlayView = view
            overlayTaskId = task.id
        } catch (_: Exception) {
            overlayView = null
            overlayTaskId = null
        }
    }

    private fun hideOverlay() {
        val view = overlayView ?: return
        overlayView = null
        overlayTaskId = null
        try {
            windowManager.removeView(view)
        } catch (_: Exception) {
            // 视图可能已被系统移除。
        }
    }

    private fun declineFromOverlay(taskId: String) {
        TaskStore.confirm(this, taskId, read = false)
        ReminderScheduler.cancel(this, taskId)
        CallNotifier.cancel(this, taskId)
        Toast.makeText(this, "已确认（未阅读）", Toast.LENGTH_SHORT).show()
        stopAlert(taskId)
    }

    private fun answerFromOverlay(taskId: String) {
        hideOverlay()
        startActivity(
            Intent(this, IncomingCallActivity::class.java)
                .putExtra(IncomingCallActivity.EXTRA_TASK_ID, taskId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        )
    }

    private fun buildOverlayParams(): WindowManager.LayoutParams {
        val density = resources.displayMetrics.density
        val margin = (12 * density).toInt()
        val (width, _) = if (Build.VERSION.SDK_INT >= 30) {
            val bounds = windowManager.currentWindowMetrics.bounds
            bounds.width() to bounds.height()
        } else {
            resources.displayMetrics.widthPixels to resources.displayMetrics.heightPixels
        }
        val statusBarId = resources.getIdentifier("status_bar_height", "dimen", "android")
        val statusBar = if (statusBarId > 0) resources.getDimensionPixelSize(statusBarId) else (24 * density).toInt()
        return WindowManager.LayoutParams(
            (width - margin * 2).coerceAtLeast((200 * density).toInt()),
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = margin
            y = statusBar + (8 * density).toInt()
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        overlayView?.let {
            try {
                windowManager.updateViewLayout(it, buildOverlayParams())
            } catch (_: Exception) {
                // 忽略旋转期间的瞬时异常。
            }
        }
    }

    private fun buildNotification(settings: SettingsStore): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, NotificationChannels.CHANNEL_SERVICE)
            .setSmallIcon(R.drawable.ic_stat_message)
            .setContentTitle("重要短信哨兵正在监听")
            .setContentText("${settings.targetNumber} · 会议短信会来电提醒")
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    override fun onDestroy() {
        stopAlert(null)
        instance = null
        setRunning(false)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val NOTIFICATION_ID = 6001
        private const val ACTION_ALERT = "com.smssentinel.app.ACTION_ALERT"
        private const val ACTION_STOP_ALERT = "com.smssentinel.app.ACTION_STOP_ALERT"
        private const val ACTION_SHOW_OVERLAY = "com.smssentinel.app.ACTION_SHOW_OVERLAY"
        private const val ACTION_HIDE_OVERLAY = "com.smssentinel.app.ACTION_HIDE_OVERLAY"
        private const val EXTRA_RING_MS = "ring_ms"
        private const val EXTRA_TASK_ID = "task_id"

        @Volatile
        var running: Boolean = false
            private set

        @Volatile
        private var instance: MonitoringService? = null

        @Volatile
        var alertSuppressed: Boolean = false

        val runningLive = MutableLiveData(false)

        private fun setRunning(value: Boolean) {
            if (running == value) return
            running = value
            runningLive.postValue(value)
        }

        fun start(context: Context) {
            val settings = SettingsStore(context)
            if (!settings.enabled || !settings.backgroundService) {
                setRunning(false)
                return
            }
            // 服务启动是异步的；先乐观更新状态，避免开关打开后界面仍显示“未运行”。
            setRunning(true)
            try {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, MonitoringService::class.java),
                )
            } catch (_: Exception) {
                // Android 12+ 可能拒绝后台启动前台服务；下次打开 App 或开机时再尝试。
                setRunning(false)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, MonitoringService::class.java))
            setRunning(false)
        }

        fun startAlert(context: Context, taskId: String, durationMs: Long) {
            if (alertSuppressed) return
            val intent = Intent(context, MonitoringService::class.java)
                .setAction(ACTION_ALERT)
                .putExtra(EXTRA_TASK_ID, taskId)
                .putExtra(EXTRA_RING_MS, durationMs)
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (_: Exception) {
                // 后台启动受限时忽略；下一次前台启动会恢复。
            }
        }

        fun stopAlert(context: Context, taskId: String) {
            stopAlertNow(taskId)
            val intent = Intent(context, MonitoringService::class.java)
                .setAction(ACTION_STOP_ALERT)
                .putExtra(EXTRA_TASK_ID, taskId)
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (_: Exception) {
                // 忽略
            }
        }

        fun stopAlertNow(taskId: String?) {
            instance?.stopAlertNow(taskId)
        }

        fun showOverlay(context: Context, taskId: String) {
            if (alertSuppressed) return
            if (!Permissions.canDrawOverlays(context)) return
            val intent = Intent(context, MonitoringService::class.java)
                .setAction(ACTION_SHOW_OVERLAY)
                .putExtra(EXTRA_TASK_ID, taskId)
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (_: Exception) {
                // 忽略
            }
        }

        fun hideOverlay(context: Context) {
            val intent = Intent(context, MonitoringService::class.java)
                .setAction(ACTION_HIDE_OVERLAY)
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (_: Exception) {
                // 忽略
            }
        }
    }
}

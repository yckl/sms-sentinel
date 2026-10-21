package com.smssentinel.app

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.smssentinel.app.data.SettingsStore
import com.smssentinel.app.data.Task
import com.smssentinel.app.data.TaskStore
import com.smssentinel.app.databinding.ActivityMainBinding
import com.smssentinel.app.notification.CallNotifier
import com.smssentinel.app.reminder.ReminderScheduler
import com.smssentinel.app.service.MonitoringService
import com.smssentinel.app.ui.HomeFragment
import com.smssentinel.app.ui.RecordsFragment
import com.smssentinel.app.ui.SettingsFragment
import com.smssentinel.app.util.Insets
import com.smssentinel.app.util.Permissions
import com.smssentinel.app.util.TimeUtils

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val bannerHandler = Handler(Looper.getMainLooper())
    private var lastSeenReceivedAt = 0L
    private var bannerTaskId: String? = null
    private var currentTab = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        Insets.apply(binding.root)

        binding.navHome.setOnClickListener {
            it.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
            selectTab(0)
        }
        binding.navRecords.setOnClickListener {
            it.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
            selectTab(1)
        }
        binding.navSettings.setOnClickListener {
            it.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
            selectTab(2)
        }

        setupCallBanner()
        lastSeenReceivedAt = TaskStore.pending(this).maxOfOrNull { it.receivedAt } ?: 0L
        TaskStore.revision.observe(this) { onTasksChanged() }

        if (savedInstanceState == null) {
            selectTab(0)
            val settings = SettingsStore(this)
            if (!settings.onboarded) {
                settings.onboarded = true
                startActivity(Intent(this, PermissionActivity::class.java))
            }
        } else {
            selectTab(savedInstanceState.getInt(STATE_TAB, 0))
        }

        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onDestroy() {
        bannerHandler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt(STATE_TAB, currentTab.coerceAtLeast(0))
        super.onSaveInstanceState(outState)
    }

    override fun onStart() {
        super.onStart()
        val settings = SettingsStore(this)
        if (settings.enabled && settings.backgroundService) {
            MonitoringService.start(this)
        } else {
            MonitoringService.stop(this)
        }
    }

    override fun onResume() {
        super.onResume()
        TaskStore.cleanup(this, SettingsStore(this).retentionDays)
    }

    private fun selectTab(index: Int) {
        if (index == currentTab) return
        currentTab = index
        val active = ContextCompat.getColor(this, R.color.blue)
        val inactive = ContextCompat.getColor(this, R.color.muted)
        val items = listOf(
            Triple(binding.navHomeIconWrap, binding.navHomeIcon, binding.navHomeLabel),
            Triple(binding.navRecordsIconWrap, binding.navRecordsIcon, binding.navRecordsLabel),
            Triple(binding.navSettingsIconWrap, binding.navSettingsIcon, binding.navSettingsLabel),
        )
        items.forEachIndexed { i, (wrap, icon, label) ->
            val color = if (i == index) active else inactive
            icon.imageTintList = ColorStateList.valueOf(color)
            label.setTextColor(color)
            label.setTypeface(null, if (i == index) Typeface.BOLD else Typeface.NORMAL)
            wrap.setBackgroundResource(
                if (i == index) R.drawable.bg_tab_active else android.R.color.transparent
            )
            if (i == index) {
                wrap.animate().cancel()
                wrap.scaleX = 0.9f
                wrap.scaleY = 0.9f
                wrap.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(180)
                    .setInterpolator(android.view.animation.OvershootInterpolator(2.2f))
                    .start()
            }
        }
        val fragment = when (index) {
            0 -> HomeFragment()
            1 -> RecordsFragment()
            else -> SettingsFragment()
        }
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
            .replace(R.id.fragmentContainer, fragment)
            .commit()
    }

    private fun setupCallBanner() {
        binding.callBanner.visibility = View.GONE
        binding.callBanner.setOnClickListener {
            bannerTaskId?.let { openCall(it) }
        }
        binding.btnBannerDecline.setOnClickListener {
            val id = bannerTaskId ?: return@setOnClickListener
            TaskStore.confirm(this, id, read = false)
            ReminderScheduler.cancel(this, id)
            CallNotifier.cancel(this, id)
            hideCallBanner()
        }
        binding.btnBannerAnswer.setOnClickListener {
            val id = bannerTaskId ?: return@setOnClickListener
            openCall(id)
            hideCallBanner()
        }
    }

    private fun onTasksChanged() {
        val latest = TaskStore.pending(this).maxByOrNull { it.receivedAt }
        if (latest == null) {
            lastSeenReceivedAt = 0L
            return
        }
        if (latest.receivedAt > lastSeenReceivedAt) {
            lastSeenReceivedAt = latest.receivedAt
            if (SettingsStore(this).enabled) showCallBanner(latest)
        }
    }

    private fun showCallBanner(task: Task) {
        // 已授权悬浮窗时由系统级来电胶囊负责展示，避免应用内重复弹一条。
        if (Permissions.canDrawOverlays(this)) return
        bannerTaskId = task.id
        binding.tvBannerTitle.text = "重要短信提醒"
        binding.tvBannerBody.text = task.body
        if (SettingsStore(this).vibrationEnabled) {
            com.smssentinel.app.reminder.RingtoneController.pulse(this)
        }
        binding.callBanner.animate().cancel()
        binding.callBanner.visibility = View.VISIBLE
        binding.callBanner.alpha = 0f
        binding.callBanner.translationY = -24f
        binding.callBanner.animate().alpha(1f).translationY(0f).setDuration(240).start()
        bannerHandler.removeCallbacksAndMessages(null)
        bannerHandler.postDelayed({ hideCallBanner() }, 12_000L)
    }

    private fun hideCallBanner() {
        bannerHandler.removeCallbacksAndMessages(null)
        if (binding.callBanner.visibility != View.VISIBLE) return
        binding.callBanner.animate().cancel()
        binding.callBanner.animate()
            .alpha(0f)
            .translationY(-18f)
            .setDuration(180)
            .withEndAction {
                binding.callBanner.visibility = View.GONE
                bannerTaskId = null
            }
            .start()
    }

    private fun openCall(taskId: String) {
        startActivity(
            Intent(this, IncomingCallActivity::class.java)
                .putExtra(IncomingCallActivity.EXTRA_TASK_ID, taskId)
        )
    }

    private fun handleIntent(intent: Intent?) {
        val taskId = intent?.getStringExtra(EXTRA_OPEN_TASK) ?: return
        intent.removeExtra(EXTRA_OPEN_TASK)
        binding.root.post { showTaskDetail(taskId) }
    }

    private fun showTaskDetail(taskId: String) {
        val task = TaskStore.get(this, taskId) ?: return
        val builder = MaterialAlertDialogBuilder(this)
            .setTitle("短信详情")
            .setMessage(detailText(task))
            .setNegativeButton("关闭", null)

        if (task.isPending) {
            builder.setPositiveButton("接听并确认") { _, _ ->
                startActivity(
                    Intent(this, IncomingCallActivity::class.java)
                        .putExtra(IncomingCallActivity.EXTRA_TASK_ID, task.id)
                )
            }
            builder.setNeutralButton("挂断确认") { _, _ ->
                TaskStore.confirm(this, task.id, read = false)
                ReminderScheduler.cancel(this, task.id)
                CallNotifier.cancel(this, task.id)
                MonitoringService.stopAlertNow(task.id)
            }
        } else if (task.status == com.smssentinel.app.data.TaskStatus.CONFIRMED_UNREAD) {
            builder.setNeutralButton("恢复为待确认") { _, _ ->
                val delay = SettingsStore(this).intervalMillis
                TaskStore.restore(this, task.id, delay)?.let {
                    ReminderScheduler.schedule(this, it)
                }
            }
        }

        builder.show()
    }

    private fun detailText(task: Task): String {
        val status = when (task.status) {
            com.smssentinel.app.data.TaskStatus.PENDING -> "待确认"
            com.smssentinel.app.data.TaskStatus.CONFIRMED_READ -> "已确认 · 接听（已阅读）"
            com.smssentinel.app.data.TaskStatus.CONFIRMED_UNREAD -> "已确认 · 挂断（未阅读）"
        }
        val confirmed = task.confirmedAt?.let { "\n确认时间：${TimeUtils.dateTime(it)}" } ?: ""
        return "${task.body}\n\n状态：$status\n来源：${task.sender}\n响铃次数：${task.ringCount}$confirmed"
    }

    companion object {
        const val EXTRA_OPEN_TASK = "open_task"
        private const val STATE_TAB = "selected_tab"
    }
}

package com.smssentinel.app

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.smssentinel.app.data.SettingsStore
import com.smssentinel.app.data.TaskStore
import com.smssentinel.app.databinding.ActivityPermissionBinding
import com.smssentinel.app.databinding.ItemPermissionRowBinding
import com.smssentinel.app.reminder.ReminderScheduler
import com.smssentinel.app.service.MonitoringService
import com.smssentinel.app.util.Insets
import com.smssentinel.app.util.OemGuide
import com.smssentinel.app.util.Permissions

class PermissionActivity : AppCompatActivity() {
    private lateinit var binding: ActivityPermissionBinding

    private val smsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { render() }

    private val notificationLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { render() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPermissionBinding.inflate(layoutInflater)
        setContentView(binding.root)
        Insets.apply(binding.root)
        MonitoringService.runningLive.observe(this) { render() }

        binding.btnBack.setOnClickListener { finish() }
        binding.btnRecheck.setOnClickListener { render() }
        binding.btnSimulate.setOnClickListener { simulateCall() }
        binding.btnWakeTest.setOnClickListener { startWakeTest() }
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        val sms = Permissions.hasSms(this)
        val notifications = Permissions.hasNotifications(this)
        val fullScreen = Permissions.canUseFullScreen(this)
        val exact = Permissions.canScheduleExact(this)
        val battery = Permissions.isIgnoringBatteryOptimizations(this)

        val ready = sms && notifications
        binding.tvReady.text = if (ready) "已准备就绪" else "尚未就绪"
        binding.tvReady.setTextColor(
            ContextCompat.getColor(this, if (ready) R.color.green_deep else R.color.amber)
        )
        binding.tvReadyHint.text = if (ready) {
            "关键能力已具备，可以进行真实短信验收"
        } else {
            "还缺关键能力，来电提醒可能无法工作"
        }
        binding.tvReadyHint.setTextColor(
            ContextCompat.getColor(this, if (ready) R.color.green_deep else R.color.amber)
        )
        binding.readyCard.setBackgroundResource(
            if (ready) R.drawable.bg_ready_card else R.drawable.bg_ready_card_warn
        )

        binding.permissionList.removeAllViews()
        addRow("短信接收权限", sms, "已允许", "待授权") {
            if (sms) Permissions.openAppDetails(this)
            else smsLauncher.launch(Manifest.permission.RECEIVE_SMS)
        }
        addRow("通知权限", notifications, "已允许", "待授权") {
            if (Build.VERSION.SDK_INT >= 33 && !notifications) {
                notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                Permissions.openNotificationSettings(this)
            }
        }
        addRow("全屏来电提醒", fullScreen, "已授权", "视权限而定") {
            Permissions.openFullScreenSettings(this)
        }
        addRow("悬浮来电横幅", Permissions.canDrawOverlays(this), "已授权", "待授权") {
            Permissions.openOverlaySettings(this)
        }
        addRow("锁屏通知", notifications, "已开启", "待开启") {
            Permissions.openLockScreenSettings(this)
        }
        addRow("精确定时", exact, "已授权", "待授权") {
            Permissions.openExactAlarmSettings(this)
        }
        addRow("后台运行 / 自启动", battery, "已设置", "未设置") {
            Permissions.openBatterySettings(this)
        }
        addRow(
            "后台监听服务",
            MonitoringService.running,
            "运行中",
            "未运行",
        ) {
            MonitoringService.start(this)
            render()
        }
        addRow("厂商保活设置", true, "查看指引", "查看指引") {
            OemGuide.show(this)
        }
    }

    private fun addRow(
        title: String,
        granted: Boolean,
        grantedText: String,
        deniedText: String,
        action: () -> Unit,
    ) {
        val row = ItemPermissionRowBinding.inflate(layoutInflater, binding.permissionList, true)
        row.permName.text = title
        row.permValue.text = if (granted) grantedText else deniedText
        row.permValue.setTextColor(
            ContextCompat.getColor(this, if (granted) R.color.green_deep else R.color.amber)
        )
        row.permIconWrap.setBackgroundResource(
            if (granted) R.drawable.bg_status_green else R.drawable.bg_status_amber
        )
        row.permIcon.setImageResource(if (granted) R.drawable.ic_check else R.drawable.ic_alert)
        row.permIcon.imageTintList = ContextCompat.getColorStateList(
            this,
            if (granted) R.color.green else R.color.amber,
        )
        row.root.setOnClickListener { action() }
    }

    private fun startWakeTest() {
        val exact = Permissions.canScheduleExact(this)
        MaterialAlertDialogBuilder(this)
            .setTitle("锁屏唤醒测试")
            .setMessage(
                "点击“开始测试”后，请立即按电源键锁屏。\n\n8 秒后应出现全屏来电；如果没有亮屏，请检查：\n· 全屏来电特殊访问\n· 锁屏通知 / 通知亮屏\n· 厂商自启动与省电策略\n\n" +
                    (if (exact) "精确定时：已授权" else "精确定时：未授权，测试时间可能延迟")
            )
            .setPositiveButton("开始测试") { _, _ ->
                val settings = SettingsStore(this)
                val now = System.currentTimeMillis()
                val task = TaskStore.add(
                    context = this,
                    sender = settings.targetNumber,
                    body = "【测试】锁屏唤醒测试：会议通知",
                    receivedAt = now,
                    nextRingAt = now + 8_000L,
                    ringCount = 1,
                    simulated = true,
                )
                ReminderScheduler.schedule(this, task)
                Toast.makeText(this, "已安排 8 秒后测试，请立即锁屏", Toast.LENGTH_LONG).show()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun simulateCall() {
        val settings = SettingsStore(this)
        val task = TaskStore.add(
            context = this,
            sender = settings.targetNumber,
            body = settings.simulatedBody(),
            receivedAt = System.currentTimeMillis(),
            nextRingAt = 0L,
            ringCount = 1,
            simulated = true,
        )
        startActivity(
            Intent(this, IncomingCallActivity::class.java)
                .putExtra(IncomingCallActivity.EXTRA_TASK_ID, task.id)
        )
    }
}

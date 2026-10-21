package com.smssentinel.app.notification

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.content.ContextCompat
import com.smssentinel.app.IncomingCallActivity
import com.smssentinel.app.R
import com.smssentinel.app.data.SettingsStore
import com.smssentinel.app.data.Task
import com.smssentinel.app.data.TaskStore
import com.smssentinel.app.reminder.DeclineReceiver
import com.smssentinel.app.service.MonitoringService
import com.smssentinel.app.util.Permissions

object CallNotifier {
    private const val EXTRA_TASK_ID = "task_id"

    fun notificationId(taskId: String): Int = taskId.hashCode() and 0x7fffffff

    fun show(context: Context, task: Task, pendingCount: Int = 1) {
        if (!Permissions.hasNotifications(context)) return

        TaskStore.pending(context)
            .filter { it.id != task.id }
            .forEach { NotificationManagerCompat.from(context).cancel(notificationId(it.id)) }

        val settings = SettingsStore(context)
        val fullScreenAvailable = Permissions.canUseFullScreen(context)
        val audioManager = context.getSystemService(android.media.AudioManager::class.java)
        val inCall = audioManager.mode == android.media.AudioManager.MODE_IN_CALL ||
            audioManager.mode == android.media.AudioManager.MODE_IN_COMMUNICATION
        val keyguard = context.getSystemService(android.app.KeyguardManager::class.java)
        val locked = keyguard.isKeyguardLocked
        val allowFullScreen = fullScreenAvailable && !inCall
        val serviceCanRing = settings.backgroundService && MonitoringService.running
        val overlayAvailable = !inCall && !locked && serviceCanRing &&
            Permissions.canDrawOverlays(context)
        val channelId = when {
            overlayAvailable -> NotificationChannels.CHANNEL_QUIET
            inCall -> if (settings.vibrationEnabled) {
                NotificationChannels.CHANNEL_VIBRATE
            } else {
                NotificationChannels.CHANNEL_SILENT
            }
            // 由前台服务统一负责铃声/震动，避免通知渠道的震动把持续震动顶掉。
            serviceCanRing -> NotificationChannels.CHANNEL_SILENT
            else -> NotificationChannels.fallbackChannel(settings)
        }

        val callIntent = Intent(context, IncomingCallActivity::class.java)
            .putExtra(EXTRA_TASK_ID, task.id)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val contentIntent = PendingIntent.getActivity(
            context,
            notificationId(task.id),
            callIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val answerIntent = PendingIntent.getActivity(
            context,
            notificationId(task.id) + 1,
            Intent(callIntent),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val declineIntent = PendingIntent.getBroadcast(
            context,
            notificationId(task.id) + 2,
            Intent(context, DeclineReceiver::class.java).putExtra(EXTRA_TASK_ID, task.id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val publicVersion = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_stat_message)
            .setLargeIcon(android.graphics.BitmapFactory.decodeResource(context.resources, R.mipmap.ic_launcher))
            .setContentTitle("重要短信待确认")
            .setContentText("来源 ${task.sender}")
            .setColor(ContextCompat.getColor(context, R.color.coral))
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(contentIntent)
            .build()

        val bodyText = when {
            pendingCount > 1 -> "共 $pendingCount 条重要短信待确认"
            settings.lockScreenBody -> task.body
            else -> "重要短信待确认，点击查看全文"
        }

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_stat_message)
            .setLargeIcon(android.graphics.BitmapFactory.decodeResource(context.resources, R.mipmap.ic_launcher))
            .setContentTitle("重要短信提醒")
            .setContentText(bodyText)
            .setSubText("来源 ${task.sender}")
            .setColor(ContextCompat.getColor(context, R.color.coral))
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setAutoCancel(false)
            .setOnlyAlertOnce(false)
            .setShowWhen(true)
            .setWhen(task.receivedAt)

        if (allowFullScreen && !overlayAvailable) {
            builder.setFullScreenIntent(contentIntent, true)
        }

        if (Build.VERSION.SDK_INT >= 31 && allowFullScreen && !overlayAvailable) {
            val person = Person.Builder()
                .setName("重要短信")
                .setImportant(true)
                .build()
            builder.setStyle(
                NotificationCompat.CallStyle.forIncomingCall(person, declineIntent, answerIntent)
            )
        } else {
            // 全屏不可用时使用带明确按钮的高优先级通知：
            // “挂断确认”直接在后台完成确认，不打开 App。
            builder
                .setStyle(NotificationCompat.BigTextStyle().bigText(bodyText))
                .addAction(R.drawable.ic_call_decline, "挂断确认", declineIntent)
                .addAction(R.drawable.ic_call_answer, "接听查看", answerIntent)
        }

        NotificationManagerCompat.from(context).notify(notificationId(task.id), builder.build())

        // 由前台服务持续振铃/震动；全屏页面启动后会停止服务铃声并接管。
        if (!inCall && serviceCanRing) {
            MonitoringService.startAlert(context, task.id, settings.ringSeconds * 1_000L)
        }
        // 解锁状态：无论当前在哪个 App，都从顶部弹出系统级来电胶囊。
        if (!inCall && !locked) {
            MonitoringService.showOverlay(context, task.id)
        } else {
            MonitoringService.hideOverlay(context)
        }
    }

    fun cancel(context: Context, taskId: String) {
        NotificationManagerCompat.from(context).cancel(notificationId(taskId))
    }
}

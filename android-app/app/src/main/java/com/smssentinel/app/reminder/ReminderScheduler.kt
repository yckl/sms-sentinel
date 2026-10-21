package com.smssentinel.app.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.smssentinel.app.data.Task
import com.smssentinel.app.data.TaskStore

object ReminderScheduler {
    const val EXTRA_TASK_ID = "task_id"

    fun schedule(context: Context, task: Task) {
        if (task.nextRingAt <= 0L || !task.isPending) return
        val manager = context.getSystemService(AlarmManager::class.java)
        val triggerAt = maxOf(task.nextRingAt, System.currentTimeMillis() + 1_000L)
        val pendingIntent = pendingIntent(context, task.id)
        if (Build.VERSION.SDK_INT >= 31 && !manager.canScheduleExactAlarms()) {
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        } else {
            manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        }
    }

    fun cancel(context: Context, taskId: String) {
        val manager = context.getSystemService(AlarmManager::class.java)
        manager.cancel(pendingIntent(context, taskId))
    }

    fun rescheduleAll(context: Context) {
        val now = System.currentTimeMillis()
        val settings = com.smssentinel.app.data.SettingsStore(context)
        TaskStore.pending(context).forEach { task ->
            if (task.nextRingAt <= now) {
                task.nextRingAt = now + settings.intervalMillis
                TaskStore.upsert(context, task)
            }
            schedule(context, task)
        }
    }

    private fun pendingIntent(context: Context, taskId: String): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java)
            .putExtra(EXTRA_TASK_ID, taskId)
        return PendingIntent.getBroadcast(
            context,
            taskId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}

package com.smssentinel.app.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.smssentinel.app.data.TaskStore
import com.smssentinel.app.notification.CallNotifier
import com.smssentinel.app.service.MonitoringService

class DeclineReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getStringExtra(ReminderScheduler.EXTRA_TASK_ID) ?: return
        val task = TaskStore.get(context, taskId) ?: return
        if (!task.isPending) return

        TaskStore.confirm(context, taskId, read = false)
        ReminderScheduler.cancel(context, taskId)
        CallNotifier.cancel(context, taskId)
        MonitoringService.stopAlert(context, taskId)
        Toast.makeText(context, "已确认（未阅读）", Toast.LENGTH_SHORT).show()
    }
}

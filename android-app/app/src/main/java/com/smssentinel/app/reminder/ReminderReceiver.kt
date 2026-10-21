package com.smssentinel.app.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.smssentinel.app.data.SettingsStore
import com.smssentinel.app.data.TaskStore
import com.smssentinel.app.notification.CallNotifier
import com.smssentinel.app.service.MonitoringService

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getStringExtra(ReminderScheduler.EXTRA_TASK_ID) ?: return
        val settings = SettingsStore(context)
        if (!settings.enabled) return

        val task = TaskStore.get(context, taskId) ?: return
        if (!task.isPending) return

        val now = System.currentTimeMillis()
        val updated = TaskStore.bumpRing(context, taskId, now + settings.intervalMillis) ?: return
        ReminderScheduler.schedule(context, updated)
        if (settings.vibrationEnabled) {
            RingtoneController.pulse(context)
        }
        CallNotifier.show(context, updated, pendingCount = TaskStore.pending(context).size)
        MonitoringService.start(context)
    }
}

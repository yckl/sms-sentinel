package com.smssentinel.app.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.smssentinel.app.data.SettingsStore
import com.smssentinel.app.data.Task
import com.smssentinel.app.data.TaskStore
import com.smssentinel.app.notification.CallNotifier
import com.smssentinel.app.reminder.ReminderScheduler
import com.smssentinel.app.service.MonitoringService

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val settings = SettingsStore(context)
        if (!settings.enabled) return

        val now = System.currentTimeMillis()
        val incoming = MessageAssembler.parse(intent)
        val created = mutableListOf<Task>()

        for (sms in incoming) {
            if (!NumberMatcher.matches(sms.sender, settings.targetNumber)) continue
            if (!settings.matchesFilter(sms.body)) continue
            if (TaskStore.isDuplicate(context, sms.sender, sms.timestampMillis, sms.body)) continue

            val task = TaskStore.add(
                context = context,
                sender = sms.sender,
                body = sms.body,
                receivedAt = if (sms.timestampMillis > 0) sms.timestampMillis else now,
                nextRingAt = now + settings.intervalMillis,
                ringCount = 1,
            )
            created.add(task)
            ReminderScheduler.schedule(context, task)
        }

        created.firstOrNull()?.let { task ->
            if (settings.vibrationEnabled) {
                com.smssentinel.app.reminder.RingtoneController.pulse(context)
            }
            CallNotifier.show(context, task, pendingCount = created.size)
        }
        MonitoringService.start(context)
    }
}

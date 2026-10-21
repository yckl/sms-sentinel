package com.smssentinel.app

import android.app.Application
import com.smssentinel.app.notification.NotificationChannels

class SentinelApp : Application() {
    override fun onCreate() {
        super.onCreate()
        NotificationChannels.ensure(this)
    }
}

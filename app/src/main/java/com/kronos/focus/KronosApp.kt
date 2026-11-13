package com.kronos.focus

import android.app.Application
import com.kronos.focus.services.AlarmScheduler

class KronosApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashLog.install(this)
        AlarmScheduler.scheduleDaily(this)
    }
}

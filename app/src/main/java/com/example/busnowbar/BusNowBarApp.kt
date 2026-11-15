package com.example.busnowbar

import android.app.Application
import com.example.busnowbar.work.BusAlarmScheduler
import com.example.busnowbar.work.Notifications

class BusNowBarApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Notifications.ensureChannel(this)
        // 앱이 실행될 때마다 다음 07:00 알람을 복원한다.
        BusAlarmScheduler.scheduleNextRun(this)
    }
}

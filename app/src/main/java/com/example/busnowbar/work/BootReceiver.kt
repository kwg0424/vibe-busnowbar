package com.example.busnowbar.work

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** 재부팅/앱 업데이트 후 AlarmManager 예약이 날아가므로 다시 걸어준다. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            -> BusAlarmScheduler.scheduleNextRun(context)
        }
    }
}

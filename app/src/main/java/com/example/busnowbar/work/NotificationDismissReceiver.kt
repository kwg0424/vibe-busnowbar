package com.example.busnowbar.work

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * 알림을 탭했을 때 폴링을 멈추고 알림을 지운다. 서비스는 다음 날짜(또는 다음 활성 요일)의
 * 알람으로만 다시 시작되므로, 오늘 이 시간대에는 다시 뜨지 않는다.
 */
class NotificationDismissReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        context.getSystemService(NotificationManager::class.java)?.cancel(Notifications.NOTIFICATION_ID)
        context.stopService(Intent(context, BusPollingService::class.java))
    }
}

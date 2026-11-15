package com.example.busnowbar.work

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.busnowbar.data.BusSettingsStore
import com.example.busnowbar.util.DailySchedule
import java.time.ZonedDateTime

/** 설정에서 고른 시각(기본 07:00)에 [BusPollingService]를 깨우는 알람을 예약한다. */
object BusAlarmScheduler {

    private const val TAG = "BusAlarmScheduler"
    private const val REQUEST_CODE = 1

    fun scheduleNextRun(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val settings = BusSettingsStore(context).load()
        val pendingIntent = pendingIntent(context)

        if (!settings.enabled || settings.activeDays.isEmpty()) {
            Log.i(TAG, "기능이 꺼져 있거나 요일이 선택되지 않아 알람을 취소합니다")
            alarmManager.cancel(pendingIntent)
            return
        }

        val triggerAt = System.currentTimeMillis() +
            DailySchedule.delayMillisUntilNext(
                ZonedDateTime.now(),
                settings.startHour,
                settings.startMinute,
                settings.activeDays,
            )

        if (alarmManager.canScheduleExactAlarms()) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        }
        else {
            Log.w(TAG, "정확한 알람 권한이 없어 근사 알람으로 예약합니다")
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        }
    }

    private fun pendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, BusPollingService::class.java)
        return PendingIntent.getForegroundService(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}

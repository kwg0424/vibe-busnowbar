package com.example.busnowbar.util

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.core.content.ContextCompat

object Permissions {

    fun hasNotificationAccess(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * 정확한 알람(Alarm Manager) 예약 권한. Android 12+ 는 설정 화면에서 사용자가 직접 허용해야 한다.
     * 거부된 상태면 [BusAlarmScheduler]가 근사 알람으로 대신 예약한다.
     */
    fun hasExactAlarmAccess(context: Context): Boolean {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return false
        return alarmManager.canScheduleExactAlarms()
    }

    fun exactAlarmSettingsIntent(context: Context): Intent =
        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))

    /** "내 정류소 찾기"(GPS 근처 정류소 검색)에 쓴다 — 정확한 위치가 아니어도(COARSE) 동작한다. */
    fun hasLocationAccess(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    fun locationPermissions(): Array<String> = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
    )

    /**
     * Now Bar/Live Update 로 알림이 "승격"되려면 일반 알림 권한과 별개로 이게 따로 허용돼 있어야
     * 한다. 요청 다이얼로그가 없고 설정 화면([promotedNotificationSettingsIntent])에서만 켤 수 있다.
     */
    fun hasPromotedNotificationAccess(context: Context): Boolean {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return false
        return manager.canPostPromotedNotifications()
    }

    fun promotedNotificationSettingsIntent(context: Context): Intent =
        Intent(Settings.ACTION_APP_NOTIFICATION_PROMOTION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
}

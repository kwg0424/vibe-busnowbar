package com.example.busnowbar.work

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.busnowbar.R

/**
 * 고정된 ID 로 같은 알림을 계속 갱신한다 (notify() 재호출 == Now Bar/Live Update 갱신 시도).
 * Android 16(API 36)의 Notification.ProgressStyle 로 진행 상태를 표현한다 — 이 앱의 minSdk 가
 * 이미 36 이라 버전 분기 없이 바로 써도 된다.
 *
 * ProgressStyle 만으로는 일반 알림 패널에만 뜨고 Now Bar(Live Update)로는 승격되지 않는다.
 * setRequestPromotedOngoing(true) 를 같이 호출해야 "승격 요청" 알림이 되고, 그래야 시스템이
 * Now Bar 후보로 고려한다 — 사용자가 설정에서 이 앱의 "알림 승격"을 허용했을 때만 실제로 뜬다
 * ([com.example.busnowbar.util.Permissions.hasPromotedNotificationAccess] 참고).
 * 이 메서드는 API 36.1(BAKLAVA_1)부터 있어서, minSdk(36)보다 높은 SDK_INT_FULL 체크가 필요하다.
 */
object Notifications {

    private const val CHANNEL_ID = "bus_arrival"
    const val NOTIFICATION_ID = 2001

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "버스 도착 알림",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "출근 시간대 버스 도착정보를 갱신하는 동안 표시됩니다"
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    /**
     * 진행 바는 "다음 갱신까지" 시간을 나타낸다 — 갱신 주기([intervalSeconds])만큼이 전체 길이이고
     * 조회 직후 0에서 시작해 [elapsedSeconds]만큼 차오르다가, 꽉 차면 다시 조회한다.
     */
    fun progress(context: Context, elapsedSeconds: Int, intervalSeconds: Int, arrivalText: String?): Notification {
        val style = Notification.ProgressStyle()
            .addProgressSegment(Notification.ProgressStyle.Segment(intervalSeconds))
            .setProgress(elapsedSeconds.coerceIn(0, intervalSeconds))

        val builder = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_bus)
            .setContentTitle("버스 도착정보")
            .setContentText(arrivalText ?: "도착정보를 조회하는 중입니다")
            .setStyle(style)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(dismissPendingIntent(context))

        requestPromotedOngoing(builder)
        return builder.build()
    }

    fun error(context: Context, message: String): Notification {
        val builder = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_bus)
            .setContentTitle("버스 도착정보")
            .setContentText(message)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(dismissPendingIntent(context))

        requestPromotedOngoing(builder)
        return builder.build()
    }

    /** 알림을 탭하면 폴링을 멈추고 알림을 닫는다 — 같은 시간대엔 다시 뜨지 않고, 다음 예약 시간대에만 다시 뜬다. */
    private fun dismissPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, NotificationDismissReceiver::class.java)
        return PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun requestPromotedOngoing(builder: Notification.Builder) {
        if (Build.VERSION.SDK_INT_FULL >= Build.VERSION_CODES_FULL.BAKLAVA_1) {
            builder.setRequestPromotedOngoing(true)
        }
    }
}

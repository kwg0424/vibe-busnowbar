package com.example.busnowbar.work

import android.app.Notification
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import com.example.busnowbar.data.BusApi
import com.example.busnowbar.data.BusArrival
import com.example.busnowbar.data.BusSettingsStore
import com.example.busnowbar.util.DailySchedule
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.ZonedDateTime

/**
 * 설정에서 고른 시간대(기본 07:00~07:10) 동안 30초 간격으로 버스 도착정보를 조회해
 * 같은 알림(ID 고정)을 갱신한다. 다음 날 알람은 서비스가 시작되는 즉시 다시 예약한다 —
 * 폴링이 끝나기 전에 프로세스가 죽어도 다음 날 실행이 끊기지 않게 하기 위함.
 */
class BusPollingService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pollingJob: Job? = null

    override fun onBind(intent: Intent?) = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(
            Notifications.NOTIFICATION_ID,
            Notifications.progress(this, tick = 0, arrivalText = null),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )

        BusAlarmScheduler.scheduleNextRun(this)
        startPolling(force = intent?.getBooleanExtra(EXTRA_FORCE, false) ?: false)

        return START_NOT_STICKY
    }

    /** [force]가 true면 설정된 시간창을 무시하고 지금부터 10분간 돌린다 — 수동 테스트 버튼용. */
    private fun startPolling(force: Boolean) {
        pollingJob?.cancel()
        pollingJob = serviceScope.launch {
            val settings = BusSettingsStore(this@BusPollingService).load()
            if (!settings.isConfigured) {
                Log.w(TAG, "API 키/정류소 설정이 비어 있어 폴링을 시작하지 않습니다")
                notify(Notifications.error(this@BusPollingService, "설정에서 API 키와 정류소를 입력해주세요"))
                stopSelfSafely()
                return@launch
            }

            // force(수동 테스트)는 전체 기능 꺼짐/요일 설정과 무관하게 항상 돈다.
            // 예약(force=false)으로 들어온 경우에만 막는다 — 알람은 껐을 때 취소되지만,
            // 저장 타이밍에 따라 이미 걸려있던 알람이 뒤늦게 울리는 경우를 한 번 더 막는 안전장치.
            if (!force) {
                if (!settings.enabled) {
                    Log.i(TAG, "전체 기능이 꺼져 있어 폴링을 시작하지 않습니다")
                    stopSelfSafely()
                    return@launch
                }
                if (ZonedDateTime.now().dayOfWeek !in settings.activeDays) {
                    Log.i(TAG, "오늘은 선택된 요일이 아니라 폴링을 시작하지 않습니다")
                    stopSelfSafely()
                    return@launch
                }
            }

            val now = System.currentTimeMillis()
            val endAt = if (force) {
                now + SESSION_DURATION_MS
            }
            else {
                DailySchedule.todayAt(ZonedDateTime.now(), settings.endHour, settings.endMinute)
                    .toInstant()
                    .toEpochMilli()
            }

            if (endAt <= now) {
                Log.i(TAG, "이미 종료 시각을 지나 폴링을 시작하지 않습니다")
                stopSelfSafely()
                return@launch
            }

            var tick = 0
            var nextTickAt = System.currentTimeMillis()

            while (isActive) {
                try {
                    val arrivalList = coroutineScope {
                        settings.stops.map { stop -> async { BusApi.getArrival(settings.apiKey, stop) } }.awaitAll()
                    }
                    notify(Notifications.progress(this@BusPollingService, tick, formatArrivalText(arrivalList)))
                }
                catch (e: CancellationException) {
                    throw e
                }
                catch (e: Exception) {
                    Log.e(TAG, "버스 도착정보 조회 실패", e)
                    notify(Notifications.error(this@BusPollingService, "도착정보를 불러오지 못했습니다"))
                }

                tick++
                nextTickAt += settings.pollIntervalSeconds * 1000L
                if (nextTickAt > endAt) {
                    break
                }

                val delayMs = nextTickAt - System.currentTimeMillis()
                if (delayMs > 0) {
                    delay(delayMs)
                }
            }

            stopSelfSafely()
        }
    }

    private fun notify(notification: Notification) {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        manager.notify(Notifications.NOTIFICATION_ID, notification)
        logPromotionDiagnostics(manager, notification)
    }

    /**
     * samsung_now_bar_development_sources.md(타다 구현기)에서 소개한 진단 방법 —
     * 우리 쪽 코드/권한이 원인인지, OS·OEM 정책이 원인인지 구분하려고 남긴다.
     * hasPromotableCharacteristics()/FLAG_PROMOTED_ONGOING 은 API 36.1(BAKLAVA_1)부터 있다.
     */
    private fun logPromotionDiagnostics(manager: NotificationManager, posted: Notification) {
        if (Build.VERSION.SDK_INT_FULL < Build.VERSION_CODES_FULL.BAKLAVA_1) {
            return
        }

        val promotable = posted.hasPromotableCharacteristics()
        val activePromoted = manager.activeNotifications
            .firstOrNull { it.id == Notifications.NOTIFICATION_ID }
            ?.notification
            ?.let { (it.flags and Notification.FLAG_PROMOTED_ONGOING) != 0 }
        Log.i(TAG, "promotable=$promotable, postedPromotedFlag=$activePromoted")
    }

    private fun stopSelfSafely() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        pollingJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun formatArrivalText(arrivalList: List<BusArrival>): String {
        val lines = arrivalList.map { arrival ->
            val stopLabel = arrival.stop.stopName.take(5)
            if (arrival.noService) {
                "${arrival.stop.routeName}(${stopLabel}) 운행 종료"
            }
            else {
                "${arrival.stop.routeName}(${stopLabel}) · ${arrival.arrmsg1}"
            }
        }
        return if (lines.isEmpty()) "표시할 노선이 없습니다" else lines.joinToString("\n")
    }

    companion object {
        private const val TAG = "BusPollingService"
        private const val SESSION_DURATION_MS = 10 * 60 * 1000L
        const val EXTRA_FORCE = "force"
    }
}

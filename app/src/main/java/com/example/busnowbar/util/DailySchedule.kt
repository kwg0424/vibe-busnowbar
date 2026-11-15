package com.example.busnowbar.util

import java.time.DayOfWeek
import java.time.Duration
import java.time.ZonedDateTime

/**
 * "매일(또는 특정 요일) 정해진 시각" 예약에 쓸 지연 시간 계산.
 *
 * Android 의존성이 없는 순수 로직이라 JVM 단위 테스트 대상이다.
 * ([현재 시각][now] 을 인자로 받는 이유도 테스트에서 시각을 고정하기 위함)
 */
object DailySchedule {

    /** 버스 도착정보 조회 시작 시각 */
    const val START_HOUR = 7
    const val START_MINUTE = 0

    /** 버스 도착정보 조회 종료 시각 */
    const val END_HOUR = 7
    const val END_MINUTE = 10

    private val ALL_DAYS = DayOfWeek.entries.toSet()

    /**
     * [now] 기준으로 다음 [hour]시 [minute]분 정각까지 남은 밀리초.
     * 이미 그 시각을 지났으면 다음 날로 넘긴다. [activeDays]에 없는 요일은 건너뛴다.
     */
    fun delayMillisUntilNext(
        now: ZonedDateTime,
        hour: Int = START_HOUR,
        minute: Int = START_MINUTE,
        activeDays: Set<DayOfWeek> = ALL_DAYS,
    ): Long {
        var next = now.withHour(hour).withMinute(minute).withSecond(0).withNano(0)
        if (!next.isAfter(now)) {
            next = next.plusDays(1)
        }
        // activeDays 가 비어 있으면(요일을 하나도 안 골랐으면) 무한루프를 피하려고 그대로 반환한다 —
        // 호출하는 쪽(BusAlarmScheduler)에서 이 경우 아예 예약하지 않도록 먼저 걸러낸다.
        if (activeDays.isNotEmpty()) {
            while (next.dayOfWeek !in activeDays) {
                next = next.plusDays(1)
            }
        }
        return Duration.between(now, next).toMillis()
    }

    /**
     * [now] 와 같은 날짜의 [hour]시 [minute]분 정각. 날짜를 넘기지 않는다.
     * 호출한 시점이 이 시각보다 늦어도 그대로 과거 시각을 돌려준다
     * (서비스 시작이 종료 시각보다 늦었는지 판단하는 용도).
     */
    fun todayAt(now: ZonedDateTime, hour: Int, minute: Int): ZonedDateTime =
        now.withHour(hour).withMinute(minute).withSecond(0).withNano(0)
}

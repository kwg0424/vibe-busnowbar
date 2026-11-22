package com.example.busnowbar.data

import android.content.Context
import com.example.busnowbar.util.DailySchedule
import java.time.DayOfWeek
import kotlin.math.abs
import org.json.JSONArray
import org.json.JSONObject

/** 사용자가 등록한 정류소 하나(정류소+노선 조합). GPS 검색으로 채우거나 삭제만 가능 — ID는 화면에 노출하지 않는다. */
data class MonitoredStop(
    val stId: Long,
    val busRouteId: Long,
    val stopName: String,
    val routeName: String,
)

/**
 * 알림 일정 하나 — 고른 요일의 [startHour]:[startMinute]~[endHour]:[endMinute] 동안 폴링한다.
 * [paused]면 삭제하지 않고 건너뛴다.
 */
data class AlertSchedule(
    val id: Long,
    val days: Set<DayOfWeek> = DayOfWeek.entries.toSet(),
    val startHour: Int = DailySchedule.START_HOUR,
    val startMinute: Int = DailySchedule.START_MINUTE,
    val endHour: Int = DailySchedule.END_HOUR,
    val endMinute: Int = DailySchedule.END_MINUTE,
    val paused: Boolean = false,
) {
    /** 일시정지가 아니고 요일이 하나라도 골라져 있어야 실제로 동작한다. */
    val isActive: Boolean
        get() = !paused && days.isNotEmpty()
}

/** 사용자가 고른 정류소/일정 설정. */
data class BusSettings(
    /** 전체 기능 켜기/끄기. 꺼져 있으면 예약된 자동 폴링을 하지 않는다("지금 폴링 테스트"는 예외). */
    val enabled: Boolean = true,
    /** 서울시 버스 Open API 서비스키. 처음 설치하면 비어 있고, 설정 화면에서 입력해야 한다. */
    val apiKey: String = "",
    /** CARTO 베이스맵 API 키. 처음 설치하면 비어 있고, 설정 화면에서 입력해야 한다(없어도 지도는 동작). */
    val cartoApiKey: String = "",
    val stops: List<MonitoredStop> = emptyList(),
    /** 알림 일정 목록. 일정마다 요일/시간대/일시정지를 따로 가진다. */
    val schedules: List<AlertSchedule> = listOf(AlertSchedule(id = 1)),
    /** 도착정보를 다시 조회해 알림을 갱신하는 간격(초). 10/15/30 중 선택. */
    val pollIntervalSeconds: Int = DEFAULT_POLL_INTERVAL_SECONDS,
) {
    val isConfigured: Boolean
        get() = apiKey.isNotBlank() && stops.isNotEmpty()

    companion object {
        const val DEFAULT_POLL_INTERVAL_SECONDS = 30
        val POLL_INTERVAL_OPTIONS = listOf(10, 15, 30)
    }
}

class BusSettingsStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): BusSettings = BusSettings(
        enabled = prefs.getBoolean(KEY_ENABLED, true),
        apiKey = prefs.getString(KEY_API_KEY, "").orEmpty(),
        cartoApiKey = prefs.getString(KEY_CARTO_API_KEY, "").orEmpty(),
        stops = parseStops(prefs.getString(KEY_STOPS, null)),
        schedules = loadSchedules(),
        pollIntervalSeconds = nearestPollInterval(
            prefs.getInt(KEY_POLL_INTERVAL_SECONDS, BusSettings.DEFAULT_POLL_INTERVAL_SECONDS),
        ),
    )

    fun save(settings: BusSettings) {
        prefs.edit()
            .putBoolean(KEY_ENABLED, settings.enabled)
            .putString(KEY_API_KEY, settings.apiKey)
            .putString(KEY_CARTO_API_KEY, settings.cartoApiKey)
            .putString(KEY_STOPS, serializeStops(settings.stops))
            .putString(KEY_SCHEDULES, serializeSchedules(settings.schedules))
            .putInt(KEY_POLL_INTERVAL_SECONDS, settings.pollIntervalSeconds)
            .apply()
    }

    /** 예전 버전(요일/시간대 1개 고정)에서 올라온 경우 그 값을 일정 1개로 옮긴다. */
    private fun loadSchedules(): List<AlertSchedule> {
        val raw = prefs.getString(KEY_SCHEDULES, null)
        if (raw != null) {
            return parseSchedules(raw)
        }
        if (!prefs.contains(KEY_START_HOUR) && !prefs.contains(KEY_ACTIVE_DAYS)) {
            return listOf(AlertSchedule(id = 1))
        }
        return listOf(
            AlertSchedule(
                id = 1,
                days = parseActiveDays(prefs.getInt(KEY_ACTIVE_DAYS, ALL_DAYS_MASK)),
                startHour = prefs.getInt(KEY_START_HOUR, DailySchedule.START_HOUR),
                startMinute = prefs.getInt(KEY_START_MINUTE, DailySchedule.START_MINUTE),
                endHour = prefs.getInt(KEY_END_HOUR, DailySchedule.END_HOUR),
                endMinute = prefs.getInt(KEY_END_MINUTE, DailySchedule.END_MINUTE),
            ),
        )
    }

    /** 예전에 저장된 5/20초 같은 값은 새 선택지 중 가장 가까운 값으로 맞춘다. */
    private fun nearestPollInterval(seconds: Int): Int =
        BusSettings.POLL_INTERVAL_OPTIONS.minBy { abs(it - seconds) }

    private fun serializeSchedules(schedules: List<AlertSchedule>): String {
        val array = JSONArray()
        schedules.forEach { schedule ->
            array.put(
                JSONObject()
                    .put("id", schedule.id)
                    .put("days", serializeActiveDays(schedule.days))
                    .put("startHour", schedule.startHour)
                    .put("startMinute", schedule.startMinute)
                    .put("endHour", schedule.endHour)
                    .put("endMinute", schedule.endMinute)
                    .put("paused", schedule.paused),
            )
        }
        return array.toString()
    }

    private fun parseSchedules(raw: String): List<AlertSchedule> {
        val array = JSONArray(raw)
        return buildList {
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                add(
                    AlertSchedule(
                        id = obj.getLong("id"),
                        days = parseActiveDays(obj.getInt("days")),
                        startHour = obj.getInt("startHour"),
                        startMinute = obj.getInt("startMinute"),
                        endHour = obj.getInt("endHour"),
                        endMinute = obj.getInt("endMinute"),
                        paused = obj.optBoolean("paused", false),
                    ),
                )
            }
        }
    }

    private fun serializeStops(stops: List<MonitoredStop>): String {
        val array = JSONArray()
        stops.forEach { stop ->
            array.put(
                JSONObject()
                    .put("stId", stop.stId)
                    .put("busRouteId", stop.busRouteId)
                    .put("stopName", stop.stopName)
                    .put("routeName", stop.routeName),
            )
        }
        return array.toString()
    }

    private fun parseStops(raw: String?): List<MonitoredStop> {
        if (raw.isNullOrBlank()) {
            return emptyList()
        }

        val array = JSONArray(raw)
        return buildList {
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                add(
                    MonitoredStop(
                        stId = obj.getLong("stId"),
                        busRouteId = obj.getLong("busRouteId"),
                        stopName = obj.getString("stopName"),
                        routeName = obj.getString("routeName"),
                    ),
                )
            }
        }
    }

    /** 요일 하나당 비트 하나(월=bit0 ... 일=bit6)로 저장한다. */
    private fun serializeActiveDays(days: Set<DayOfWeek>): Int =
        days.fold(0) { mask, day -> mask or (1 shl (day.value - 1)) }

    private fun parseActiveDays(mask: Int): Set<DayOfWeek> =
        DayOfWeek.entries.filterTo(mutableSetOf()) { day -> (mask and (1 shl (day.value - 1))) != 0 }

    companion object {
        private const val PREFS_NAME = "bus_settings"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_API_KEY = "api_key"
        private const val KEY_CARTO_API_KEY = "carto_api_key"
        private const val KEY_STOPS = "stops"
        private const val KEY_SCHEDULES = "schedules"

        // 일정이 1개로 고정이던 시절의 키. loadSchedules()가 마이그레이션용으로만 읽는다.
        private const val KEY_START_HOUR = "start_hour"
        private const val KEY_START_MINUTE = "start_minute"
        private const val KEY_END_HOUR = "end_hour"
        private const val KEY_END_MINUTE = "end_minute"
        private const val KEY_ACTIVE_DAYS = "active_days"
        private const val KEY_POLL_INTERVAL_SECONDS = "poll_interval_seconds"
        private const val ALL_DAYS_MASK = 0b1111111
    }
}

package com.example.busnowbar.data

import android.content.Context
import com.example.busnowbar.util.DailySchedule
import java.time.DayOfWeek
import org.json.JSONArray
import org.json.JSONObject

/** 사용자가 등록한 정류소 하나(정류소+노선 조합). GPS 검색으로 채우거나 삭제만 가능 — ID는 화면에 노출하지 않는다. */
data class MonitoredStop(
    val stId: Long,
    val busRouteId: Long,
    val stopName: String,
    val routeName: String,
)

/** 서울시 버스 Open API 서비스키와 등록된 정류소 목록. 전부 사용자가 입력/선택한다 — 소스에 두지 않는다. */
data class BusSettings(
    /** 전체 기능 켜기/끄기. 꺼져 있으면 예약된 자동 폴링을 하지 않는다("지금 폴링 테스트"는 예외). */
    val enabled: Boolean = true,
    val apiKey: String = "",
    val stops: List<MonitoredStop> = emptyList(),
    /** 매일 이 시각부터 폴링을 시작한다. */
    val startHour: Int = DailySchedule.START_HOUR,
    val startMinute: Int = DailySchedule.START_MINUTE,
    /** 이 시각이 되면 폴링을 멈춘다. */
    val endHour: Int = DailySchedule.END_HOUR,
    val endMinute: Int = DailySchedule.END_MINUTE,
    /** 자동 폴링을 실행할 요일. 기본은 매일. */
    val activeDays: Set<DayOfWeek> = DayOfWeek.entries.toSet(),
    /** 도착정보를 다시 조회해 알림을 갱신하는 간격(초). 5/10/20/30 중 선택. */
    val pollIntervalSeconds: Int = DEFAULT_POLL_INTERVAL_SECONDS,
) {
    val isConfigured: Boolean
        get() = apiKey.isNotBlank() && stops.isNotEmpty()

    companion object {
        const val DEFAULT_POLL_INTERVAL_SECONDS = 30
        val POLL_INTERVAL_OPTIONS = listOf(5, 10, 20, 30)
    }
}

class BusSettingsStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): BusSettings = BusSettings(
        enabled = prefs.getBoolean(KEY_ENABLED, true),
        apiKey = prefs.getString(KEY_API_KEY, "").orEmpty(),
        stops = parseStops(prefs.getString(KEY_STOPS, null)),
        startHour = prefs.getInt(KEY_START_HOUR, DailySchedule.START_HOUR),
        startMinute = prefs.getInt(KEY_START_MINUTE, DailySchedule.START_MINUTE),
        endHour = prefs.getInt(KEY_END_HOUR, DailySchedule.END_HOUR),
        endMinute = prefs.getInt(KEY_END_MINUTE, DailySchedule.END_MINUTE),
        activeDays = parseActiveDays(prefs.getInt(KEY_ACTIVE_DAYS, ALL_DAYS_MASK)),
        pollIntervalSeconds = prefs.getInt(KEY_POLL_INTERVAL_SECONDS, BusSettings.DEFAULT_POLL_INTERVAL_SECONDS),
    )

    fun save(settings: BusSettings) {
        prefs.edit()
            .putBoolean(KEY_ENABLED, settings.enabled)
            .putString(KEY_API_KEY, settings.apiKey.trim())
            .putString(KEY_STOPS, serializeStops(settings.stops))
            .putInt(KEY_START_HOUR, settings.startHour)
            .putInt(KEY_START_MINUTE, settings.startMinute)
            .putInt(KEY_END_HOUR, settings.endHour)
            .putInt(KEY_END_MINUTE, settings.endMinute)
            .putInt(KEY_ACTIVE_DAYS, serializeActiveDays(settings.activeDays))
            .putInt(KEY_POLL_INTERVAL_SECONDS, settings.pollIntervalSeconds)
            .apply()
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
        private const val KEY_STOPS = "stops"
        private const val KEY_START_HOUR = "start_hour"
        private const val KEY_START_MINUTE = "start_minute"
        private const val KEY_END_HOUR = "end_hour"
        private const val KEY_END_MINUTE = "end_minute"
        private const val KEY_ACTIVE_DAYS = "active_days"
        private const val KEY_POLL_INTERVAL_SECONDS = "poll_interval_seconds"
        private const val ALL_DAYS_MASK = 0b1111111
    }
}

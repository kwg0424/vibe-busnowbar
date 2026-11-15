package com.example.busnowbar.data

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * 서울시 버스 Open API(ws.bus.go.kr)를 중계 서버 없이 기기에서 직접 호출한다.
 * 이 API는 XML로만 응답하므로 [parseServiceResult]가 공통으로 파싱한다.
 */
object BusApi {

    private const val BASE_URL = "http://ws.bus.go.kr/api/rest"
    private const val TIMEOUT_MS = 10_000

    /** [stop] 하나의 실시간 도착정보. ord 없이 노선 전체 도착정보(getArrInfoByRouteAll)에서 stId로 골라낸다. */
    fun getArrival(apiKey: String, stop: MonitoredStop): BusArrival {
        val body = httpGet(
            "$BASE_URL/arrive/getArrInfoByRouteAll",
            listOf("serviceKey" to apiKey, "busRouteId" to stop.busRouteId.toString()),
        )
        val (headerCd, items) = parseServiceResult(body)

        if (headerCd == "4") { // 결과 없음(운행 종료 등)
            return BusArrival(stop, noService = true, arrmsg1 = null, arrmsg2 = null)
        }
        check(headerCd == "0") { "버스 API 오류 (headerCd=$headerCd)" }

        val item = items.firstOrNull { it["stId"]?.toLongOrNull() == stop.stId }
            ?: return BusArrival(stop, noService = true, arrmsg1 = null, arrmsg2 = null)

        return BusArrival(
            stop = stop,
            noService = false,
            arrmsg1 = item["arrmsg1"]?.let(::shortenArrivalMessage),
            arrmsg2 = item["arrmsg2"]?.let(::shortenArrivalMessage),
        )
    }

    /**
     * 현재 위치(WGS84) 주변 0~1500m 안의 정류소를 찾는다.
     * 파라미터 이름은 tmX/tmY 지만 실제로는 TM 투영좌표가 아니라 WGS84 경도/위도를 그대로 받는다
     * (실호출로 확인함 — 공식 문서 설명과 다르다).
     */
    fun findNearbyStations(apiKey: String, lat: Double, lng: Double, radiusMeters: Int): List<NearbyStation> {
        val body = httpGet(
            "$BASE_URL/stationinfo/getStationByPos",
            listOf(
                "serviceKey" to apiKey,
                "tmX" to lng.toString(),
                "tmY" to lat.toString(),
                "radius" to radiusMeters.toString(),
            ),
        )
        val (headerCd, items) = parseServiceResult(body)
        if (headerCd != "0") {
            return emptyList()
        }

        return items.mapNotNull { item ->
            val stId = item["stationId"]?.toLongOrNull() ?: return@mapNotNull null
            NearbyStation(
                stId = stId,
                arsId = item["arsId"].orEmpty(),
                name = item["stationNm"].orEmpty(),
                distanceMeters = item["dist"]?.toDoubleOrNull() ?: 0.0,
            )
        }
    }

    /** 정류소 하나를 지나는 버스 노선 목록을 찾는다. */
    fun findRoutesByStation(apiKey: String, arsId: String): List<RouteOption> {
        val body = httpGet(
            "$BASE_URL/stationinfo/getRouteByStation",
            listOf("serviceKey" to apiKey, "arsId" to arsId),
        )
        val (headerCd, items) = parseServiceResult(body)
        if (headerCd != "0") {
            return emptyList()
        }

        return items.mapNotNull { item ->
            val busRouteId = item["busRouteId"]?.toLongOrNull() ?: return@mapNotNull null
            RouteOption(
                busRouteId = busRouteId,
                name = item["busRouteAbrv"] ?: item["busRouteNm"].orEmpty(),
            )
        }
    }

    private fun shortenArrivalMessage(msg: String): String =
        msg.replace("[", " (")
            .replace("]", ")")
            .replace("후", "")
            .replace("번째 ", "")

    private fun httpGet(url: String, params: List<Pair<String, String>>): String {
        val query = params.joinToString("&") { (key, value) -> "$key=${URLEncoder.encode(value, "UTF-8")}" }
        val connection = URL("$url?$query").openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = TIMEOUT_MS
        connection.readTimeout = TIMEOUT_MS
        connection.setRequestProperty("User-Agent", "Mozilla/5.0")

        try {
            check(connection.responseCode == HttpURLConnection.HTTP_OK) {
                "HTTP ${connection.responseCode}"
            }
            return connection.inputStream.bufferedReader().use { it.readText() }
        }
        finally {
            connection.disconnect()
        }
    }

    /**
     * `<ServiceResult><msgHeader><headerCd>..</headerCd></msgHeader>
     * <msgBody><itemList>..</itemList>*</msgBody></ServiceResult>` 형태를 공통으로 파싱한다.
     * itemList 의 자식 태그는 전부 평평한 key-value 라 중첩 처리가 필요 없다.
     */
    private fun parseServiceResult(xml: String): Pair<String, List<Map<String, String>>> {
        val parser = Xml.newPullParser()
        parser.setInput(StringReader(xml))

        var headerCd = ""
        val items = mutableListOf<Map<String, String>>()
        var currentItem: MutableMap<String, String>? = null
        var currentTag: String? = null

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    currentTag = parser.name
                    if (currentTag == "itemList") {
                        currentItem = mutableMapOf()
                    }
                }

                XmlPullParser.TEXT -> {
                    val text = parser.text?.trim().orEmpty()
                    if (text.isNotEmpty()) {
                        val item = currentItem
                        val tag = currentTag
                        when {
                            item != null && tag != null -> item[tag] = text
                            tag == "headerCd" -> headerCd = text
                        }
                    }
                }

                XmlPullParser.END_TAG -> {
                    if (parser.name == "itemList") {
                        currentItem?.let { items.add(it) }
                        currentItem = null
                    }
                    currentTag = null
                }
            }
            eventType = parser.next()
        }

        return headerCd to items
    }
}

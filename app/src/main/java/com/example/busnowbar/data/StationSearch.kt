package com.example.busnowbar.data

/** event-hub GET /bus/stations/nearby 응답 항목. */
data class NearbyStation(
    val stId: Long,
    val arsId: String,
    val name: String,
    val distanceMeters: Double,
    /** 지도 마커용 WGS84 좌표. 응답에 없으면 0. */
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
)

/** event-hub GET /bus/routes 응답 항목. */
data class RouteOption(
    val busRouteId: Long,
    val name: String,
)

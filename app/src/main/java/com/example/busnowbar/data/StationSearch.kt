package com.example.busnowbar.data

/** event-hub GET /bus/stations/nearby 응답 항목. */
data class NearbyStation(
    val stId: Long,
    val arsId: String,
    val name: String,
    val distanceMeters: Double,
)

/** event-hub GET /bus/routes 응답 항목. */
data class RouteOption(
    val busRouteId: Long,
    val name: String,
)

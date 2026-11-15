package com.example.busnowbar.data

/** [stop] 하나를 조회한 결과. */
data class BusArrival(
    val stop: MonitoredStop,
    val noService: Boolean,
    val arrmsg1: String?,
    val arrmsg2: String?,
)

package com.example.busnowbar.util

import android.annotation.SuppressLint
import android.content.Context
import android.location.LocationManager
import android.location.LocationRequest
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Google Play Services 없이 플랫폼 LocationManager 의 FUSED_PROVIDER(API 31+)로 현재 위치를 구한다.
 * 호출 전에 [Permissions.hasLocationAccess] 로 권한을 확인해야 한다.
 */
object CurrentLocation {

    @SuppressLint("MissingPermission")
    suspend fun get(context: Context): Pair<Double, Double> = suspendCancellableCoroutine { continuation ->
        val locationManager = context.getSystemService(LocationManager::class.java)
        if (locationManager == null || !locationManager.isLocationEnabled) {
            continuation.resumeWithException(IllegalStateException("위치 서비스가 꺼져 있습니다"))
            return@suspendCancellableCoroutine
        }

        val cancellationSignal = CancellationSignal()
        continuation.invokeOnCancellation { cancellationSignal.cancel() }

        // QUALITY_HIGH_ACCURACY 로 요청해야 새 측위를 적극적으로 시도한다 — 기본(BALANCED)이면
        // 공급자가 배터리를 아끼느라 오래된 캐시값을 쓰거나 측위 자체를 미뤄 타임아웃이 잦다.
        val locationRequest = LocationRequest.Builder(0)
            .setQuality(LocationRequest.QUALITY_HIGH_ACCURACY)
            .setMaxUpdates(1)
            .build()

        locationManager.getCurrentLocation(
            LocationManager.FUSED_PROVIDER,
            locationRequest,
            cancellationSignal,
            ContextCompat.getMainExecutor(context),
        ) { location ->
            if (location == null) {
                continuation.resumeWithException(IllegalStateException("현재 위치를 가져오지 못했습니다"))
            }
            else {
                continuation.resume(location.latitude to location.longitude)
            }
        }
    }
}

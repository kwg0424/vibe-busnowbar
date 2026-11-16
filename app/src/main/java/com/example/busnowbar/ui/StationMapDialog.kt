package com.example.busnowbar.ui

import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.busnowbar.data.BusApi
import com.example.busnowbar.data.NearbyStation
import com.example.busnowbar.util.CurrentLocation
import com.example.busnowbar.util.Permissions
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberUpdatedMarkerState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** 지도 중심 기준 정류소 검색 반경(m). API 가 받는 radius 상한은 1500m. */
private const val SEARCH_RADIUS_METERS = 700

/** 목록에 보여줄 정류소 수 — 중심에서 가까운 순. */
private const val MAX_STATIONS = 5

/** 위치를 못 구했을 때 지도를 처음 여는 곳(서울시청). */
private val DEFAULT_CENTER = LatLng(37.5665, 126.9780)
private const val DEFAULT_ZOOM = 16f

/**
 * 구글 지도 + 정류소 목록 전체화면 팝업. 지도를 움직여 멈출 때마다 화면 중심(십자 핀) 기준
 * 가장 가까운 정류소 [MAX_STATIONS]개를 아래 목록에 보여주고, 하나를 누르면 [onStationSelected].
 */
@Composable
fun StationMapDialog(
    apiKey: String,
    mapsApiKey: String,
    onDismiss: () -> Unit,
    onStationSelected: (NearbyStation) -> Unit,
) {
    val context = LocalContext.current
    // 지도 SDK 는 키를 매니페스트 meta-data 에서만 읽는다. 지도를 처음 그리기 전에 설정에서 입력한 값으로 덮어쓴다.
    SideEffect { applyMapsApiKey(context, mapsApiKey) }
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(DEFAULT_CENTER, DEFAULT_ZOOM)
    }

    var locationGranted by remember { mutableStateOf(Permissions.hasLocationAccess(context)) }
    var stations by remember { mutableStateOf<List<NearbyStation>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    suspend fun moveToCurrentLocation() {
        try {
            val (lat, lng) = CurrentLocation.get(context)
            cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(LatLng(lat, lng), DEFAULT_ZOOM))
        }
        catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        }
        catch (e: Exception) {
            message = e.message ?: "현재 위치를 가져오지 못했습니다"
        }
    }

    // 열자마자 권한이 있으면 바로 내 위치로, 없으면 한 번 물어본다(거부해도 지도는 쓸 수 있다).
    var locateRequest by remember { mutableStateOf(if (locationGranted) 1 else 0) }
    val requestLocationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { granted ->
        locationGranted = granted.values.any { it }
        if (locationGranted) {
            locateRequest++
        }
        else {
            message = "위치 권한이 없어 지도를 직접 움직여 찾아야 합니다"
        }
    }
    LaunchedEffect(Unit) {
        if (!locationGranted) {
            requestLocationPermission.launch(Permissions.locationPermissions())
        }
    }
    LaunchedEffect(locateRequest) {
        if (locateRequest > 0) {
            moveToCurrentLocation()
        }
    }

    // 지도 이동이 멈출 때마다(그리고 처음 한 번) 화면 중심 기준으로 다시 검색한다.
    LaunchedEffect(cameraPositionState) {
        snapshotFlow { cameraPositionState.isMoving to cameraPositionState.position.target }
            .filter { (moving, _) -> !moving }
            .map { (_, center) -> center }
            .distinctUntilChanged()
            .collectLatest { center ->
                loading = true
                try {
                    val found = withContext(Dispatchers.IO) {
                        BusApi.findNearbyStations(apiKey, center.latitude, center.longitude, SEARCH_RADIUS_METERS)
                    }
                    stations = found.sortedBy { it.distanceMeters }.take(MAX_STATIONS)
                    message = if (stations.isEmpty()) "이 근처(${SEARCH_RADIUS_METERS}m)에 정류소가 없습니다" else null
                }
                catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                }
                catch (e: Exception) {
                    stations = emptyList()
                    message = "정류소 검색에 실패했습니다: ${e.message ?: ""}"
                }
                finally {
                    loading = false
                }
            }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = Ink) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 4.dp, top = 32.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "정류소 찾기",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                    )
                    TextButton(onClick = onDismiss) { Text("닫기") }
                }

                Box(modifier = Modifier.weight(1f)) {
                    GoogleMap(
                        modifier = Modifier.fillMaxSize(),
                        cameraPositionState = cameraPositionState,
                        properties = MapProperties(isMyLocationEnabled = locationGranted),
                        uiSettings = MapUiSettings(
                            zoomControlsEnabled = false,
                            myLocationButtonEnabled = false,
                        ),
                    ) {
                        stations.forEach { station ->
                            if (station.latitude != 0.0 && station.longitude != 0.0) {
                                Marker(
                                    state = rememberUpdatedMarkerState(LatLng(station.latitude, station.longitude)),
                                    title = station.name,
                                    snippet = "${station.distanceMeters.toInt()}m",
                                )
                            }
                        }
                    }

                    // 검색 기준점(지도 중심) 표시
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(16.dp)
                            .background(Teal, CircleShape)
                            .border(2.dp, Color.White, CircleShape),
                    )

                    OutlinedButton(
                        onClick = {
                            if (locationGranted) {
                                locateRequest++
                            }
                            else {
                                requestLocationPermission.launch(Permissions.locationPermissions())
                            }
                        },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(12.dp),
                        colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                            containerColor = Surface1,
                        ),
                    ) { Text("내 위치") }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        if (loading) "지도 중심 주변 정류소 검색 중…" else "지도 중심에서 가까운 정류소",
                        style = MaterialTheme.typography.titleSmall,
                        color = TextPrimary,
                    )
                    message?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = Amber)
                    }
                    stations.forEach { station ->
                        StationRow(station, onClick = { onStationSelected(station) })
                    }
                }
            }
        }
    }
}

private const val MAPS_API_KEY_META = "com.google.android.geo.API_KEY"

private fun applyMapsApiKey(context: Context, key: String) {
    if (key.isBlank()) {
        return
    }
    val info = context.packageManager.getApplicationInfo(
        context.packageName,
        PackageManager.ApplicationInfoFlags.of(PackageManager.GET_META_DATA.toLong()),
    )
    info.metaData?.putString(MAPS_API_KEY_META, key.trim())
    context.applicationInfo.metaData?.putString(MAPS_API_KEY_META, key.trim())
}

@Composable
private fun StationRow(station: NearbyStation, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = Surface1,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(station.name, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                if (station.arsId.isNotBlank()) {
                    Text(station.arsId, style = MaterialTheme.typography.bodySmall, color = TextMuted)
                }
            }
            Text("${station.distanceMeters.toInt()}m", style = MaterialTheme.typography.bodySmall, color = TealSoft)
        }
    }
}

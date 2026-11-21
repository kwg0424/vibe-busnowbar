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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.busnowbar.data.BusApi
import com.example.busnowbar.data.NearbyStation
import com.example.busnowbar.util.CurrentLocation
import com.example.busnowbar.util.Permissions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.osmdroid.config.Configuration
import org.osmdroid.events.DelayedMapListener
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

/** 지도 중심 기준 정류소 검색 반경(m). API 가 받는 radius 상한은 1500m. */
private const val SEARCH_RADIUS_METERS = 700

/** 목록에 보여줄 정류소 수 — 중심에서 가까운 순. */
private const val MAX_STATIONS = 5

/** 위치를 못 구했을 때 지도를 처음 여는 곳(서울시청). */
private const val DEFAULT_LAT = 37.5665
private const val DEFAULT_LNG = 126.9780
private const val DEFAULT_ZOOM = 16.0

/** 지도를 움직이다 멈춘 걸로 보고 재검색하기까지 기다리는 시간(ms). */
private const val MAP_SETTLE_MS = 400L

private const val OSM_COPYRIGHT_URL = "https://www.openstreetmap.org/copyright"

/**
 * OpenStreetMap(osmdroid) 지도 + 정류소 목록 전체화면 팝업. 지도를 움직여 멈출 때마다
 * 화면 중심(십자 핀) 기준 가장 가까운 정류소 [MAX_STATIONS]개를 아래 목록에 보여주고,
 * 하나를 누르면 [onStationSelected].
 *
 * 구글 지도와 달리 API 키가 필요 없다. 대신 OSM 타일 사용 정책상
 * (1) 앱을 식별할 수 있는 User-Agent 를 보내야 하고 (2) 저작자 표시를 노출해야 해서,
 * 전자는 [ensureOsmConfigured], 후자는 지도 위 "© OpenStreetMap" 링크로 처리한다.
 */
@Composable
fun StationMapDialog(
    apiKey: String,
    onDismiss: () -> Unit,
    onStationSelected: (NearbyStation) -> Unit,
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current

    var mapView by remember { mutableStateOf<MapView?>(null) }
    var centerLat by remember { mutableStateOf(DEFAULT_LAT) }
    var centerLng by remember { mutableStateOf(DEFAULT_LNG) }

    var locationGranted by remember { mutableStateOf(Permissions.hasLocationAccess(context)) }
    var stations by remember { mutableStateOf<List<NearbyStation>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

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
    LaunchedEffect(locateRequest, mapView) {
        val map = mapView
        if (locateRequest > 0 && map != null) {
            try {
                val (lat, lng) = CurrentLocation.get(context)
                map.controller.animateTo(GeoPoint(lat, lng))
            }
            catch (e: CancellationException) {
                throw e
            }
            catch (e: Exception) {
                message = e.message ?: "현재 위치를 가져오지 못했습니다"
            }
        }
    }

    // 지도 중심이 바뀔 때마다(그리고 처음 한 번) 그 좌표로 다시 검색한다.
    LaunchedEffect(centerLat, centerLng) {
        loading = true
        try {
            val found = withContext(Dispatchers.IO) {
                BusApi.findNearbyStations(apiKey, centerLat, centerLng, SEARCH_RADIUS_METERS)
            }
            stations = found.sortedBy { it.distanceMeters }.take(MAX_STATIONS)
            message = if (stations.isEmpty()) "이 근처(${SEARCH_RADIUS_METERS}m)에 정류소가 없습니다" else null
        }
        catch (e: CancellationException) {
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

    // MapView 는 Compose 가 아니라 직접 onResume/onPause/onDetach 를 받아야 타일 스레드가 샌다.
    // 키에 mapView 를 넣으면 안 된다 — factory 에서 mapView 가 채워지는 순간 이 이펙트가 재시작되면서
    // onDispose 가 방금 만든 MapView 에 onDetach() 를 걸어 타일 캐시 writer 를 닫아버린다.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView?.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView?.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView?.onDetach()
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
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            // MapView 를 만들기 전에 끝내야 한다 — 첫 타일 요청이 바로 나가기 때문.
                            ensureOsmConfigured(ctx)
                            MapView(ctx).apply {
                                setTileSource(TileSourceFactory.MAPNIK)
                                setMultiTouchControls(true)
                                zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
                                controller.setZoom(DEFAULT_ZOOM)
                                controller.setCenter(GeoPoint(DEFAULT_LAT, DEFAULT_LNG))
                                // 손을 떼고 잠깐 멈췄을 때만 검색한다 — 드래그 중엔 호출이 쏟아진다.
                                addMapListener(
                                    DelayedMapListener(
                                        object : MapListener {
                                            override fun onScroll(event: ScrollEvent?): Boolean {
                                                centerLat = mapCenter.latitude
                                                centerLng = mapCenter.longitude
                                                return false
                                            }

                                            override fun onZoom(event: ZoomEvent?): Boolean {
                                                centerLat = mapCenter.latitude
                                                centerLng = mapCenter.longitude
                                                return false
                                            }
                                        },
                                        MAP_SETTLE_MS,
                                    ),
                                )
                                applyOsmUserAgent(ctx)
                                onResume()
                                mapView = this
                            }
                        },
                        update = { map ->
                            map.overlays.removeAll { it is Marker }
                            stations.forEach { station ->
                                if (station.latitude != 0.0 && station.longitude != 0.0) {
                                    map.overlays.add(
                                        Marker(map).apply {
                                            position = GeoPoint(station.latitude, station.longitude)
                                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                                            title = station.name
                                            snippet = "${station.distanceMeters.toInt()}m"
                                        },
                                    )
                                }
                            }
                            map.invalidate()
                        },
                    )

                    // 검색 기준점(지도 중심) 표시
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(16.dp)
                            .background(Teal, CircleShape)
                            .border(2.dp, Color.White, CircleShape),
                    )

                    // OSM 타일 사용 조건 — 저작자 표시는 지도 위에 항상 보여야 한다.
                    Text(
                        "© OpenStreetMap",
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(8.dp)
                            .background(Surface1, RoundedCornerShape(6.dp))
                            .clickable { uriHandler.openUri(OSM_COPYRIGHT_URL) }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted,
                        textDecoration = TextDecoration.Underline,
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
                        colors = ButtonDefaults.outlinedButtonColors(containerColor = Surface1),
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

/**
 * osmdroid 전역 설정.
 *
 * OSM 타일 정책(https://operations.osmfoundation.org/policies/tiles/)은 앱을 식별할 수 있는
 * User-Agent 를 요구한다. 기본값("osmdroid")은 물론이고 패키지명(com.example.*)도
 * "설정 안 한 샘플 앱"으로 간주돼 403 Access blocked 가 돌아온다.
 * 그래서 앱 이름/버전 + 연락처(저장소 주소) 형식으로 보낸다.
 */
private fun ensureOsmConfigured(context: Context) {
    val app = context.applicationContext
    Configuration.getInstance().load(app, app.getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
    applyOsmUserAgent(app)
}

/**
 * MapView 생성 과정에서 osmdroid 가 설정을 다시 읽어 UA 를 패키지명으로 되돌리는 경우가 있어,
 * 타일 요청이 시작되기 직전에 한 번 더 확정한다.
 */
private fun applyOsmUserAgent(context: Context) {
    val app = context.applicationContext
    val version = runCatching {
        app.packageManager.getPackageInfo(app.packageName, PackageManager.PackageInfoFlags.of(0)).versionName
    }.getOrNull().orEmpty().ifBlank { "1.0" }

    Configuration.getInstance().userAgentValue =
        "BusNowBar/$version (+https://github.com/kwg0424/vibe-busnowbar)"
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

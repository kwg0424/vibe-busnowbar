package com.example.busnowbar.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.busnowbar.data.BusApi
import com.example.busnowbar.data.MonitoredStop
import com.example.busnowbar.data.NearbyStation
import com.example.busnowbar.data.RouteOption
import com.example.busnowbar.util.CurrentLocation
import com.example.busnowbar.util.Permissions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** GPS 기반 주변 정류소 검색 반경(m). Seoul Open API 가 받는 radius 의 상한은 1500m. */
private const val NEARBY_RADIUS_METERS = 300

private data class RoutePickerState(
    val station: NearbyStation,
    val routes: List<RouteOption>,
    val checked: Set<Long> = emptySet(),
)

data class HomeState(
    val apiKey: String,
    val enabled: Boolean,
    val isConfigured: Boolean,
    val stops: List<MonitoredStop>,
    /** 동작 중인 알림 일정 요약. 없으면 null. */
    val scheduleSummary: String?,
    val pollIntervalSeconds: Int,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: HomeState,
    onOpenSettings: () -> Unit,
    onStopsChanged: (List<MonitoredStop>) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var statusMessage by remember { mutableStateOf<String?>(null) }
    var showMap by remember { mutableStateOf(false) }
    var locating by remember { mutableStateOf(false) }
    var stationCandidates by remember { mutableStateOf<List<NearbyStation>?>(null) }
    var routePicker by remember { mutableStateOf<RoutePickerState?>(null) }

    fun findRoutes(station: NearbyStation) {
        scope.launch {
            statusMessage = null
            try {
                val routes = withContext(Dispatchers.IO) {
                    BusApi.findRoutesByStation(state.apiKey, station.arsId)
                }
                if (routes.isEmpty()) {
                    statusMessage = "이 정류소를 지나는 노선이 없습니다"
                }
                else {
                    routePicker = RoutePickerState(station, routes)
                }
            }
            catch (e: Exception) {
                statusMessage = "노선 검색에 실패했습니다: ${e.message ?: ""}"
            }
        }
    }

    fun findNearbyStationByGps() {
        scope.launch {
            statusMessage = null
            locating = true
            try {
                val (lat, lng) = CurrentLocation.get(context)
                val stations = withContext(Dispatchers.IO) {
                    BusApi.findNearbyStations(state.apiKey, lat, lng, NEARBY_RADIUS_METERS)
                }
                if (stations.isEmpty()) {
                    statusMessage = "주변 ${NEARBY_RADIUS_METERS}m 안에 정류소가 없습니다"
                }
                else {
                    stationCandidates = stations
                }
            }
            catch (e: Exception) {
                statusMessage = "정류소 검색에 실패했습니다: ${e.message ?: ""}"
            }
            finally {
                locating = false
            }
        }
    }

    val requestLocationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { granted ->
        if (granted.values.any { it }) {
            findNearbyStationByGps()
        }
        else {
            statusMessage = "위치 권한이 필요합니다"
        }
    }

    fun onFindStationByGpsClick() {
        if (state.apiKey.isBlank()) {
            statusMessage = "먼저 설정에서 API 키를 입력해주세요"
            return
        }
        if (Permissions.hasLocationAccess(context)) {
            findNearbyStationByGps()
        }
        else {
            requestLocationPermission.launch(Permissions.locationPermissions())
        }
    }

    fun onFindStationByMapClick() {
        if (state.apiKey.isBlank()) {
            statusMessage = "먼저 설정에서 API 키를 입력해주세요"
            return
        }
        statusMessage = null
        showMap = true
    }

    Scaffold(
        containerColor = Ink,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "BusNowBar",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                actions = {
                    TextButton(onClick = onOpenSettings) { Text("설정") }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Ink,
                    titleContentColor = TextPrimary,
                ),
            )
        },
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionCard {
                Text(
                    if (state.enabled && state.scheduleSummary != null) {
                        "${state.scheduleSummary}, ${state.pollIntervalSeconds}초 간격으로 아래 정류소들의 도착정보를 알림으로 갱신합니다."
                    }
                    else if (state.enabled) {
                        "동작 중인 알림 일정이 없습니다. 설정에서 일정을 추가하거나 일시정지를 풀어주세요."
                    }
                    else {
                        "전체 기능이 꺼져 있습니다. 설정에서 켜야 자동으로 갱신됩니다."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (state.enabled) TextMuted else Amber,
                )
            }

            SectionCard {
                Text("정류소 추가", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                Text(
                    "현재 위치 주변 정류소를 찾아, 그 정류소를 지나는 노선 중 알림받을 것을 체크해서 추가합니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted,
                )
                Button(
                    onClick = { onFindStationByGpsClick() },
                    enabled = !locating,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (locating) "위치 확인 중…" else "내 정류소 찾기 (GPS)") }

                if (state.apiKey.isBlank()) {
                    Text(
                        "먼저 설정에서 서울시 버스 API 키를 입력해주세요",
                        style = MaterialTheme.typography.bodySmall,
                        color = Amber,
                    )
                }

                // OpenStreetMap 은 API 키가 필요 없어서 항상 쓸 수 있다.
                Button(
                    onClick = { onFindStationByMapClick() },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("내 정류소 찾기 (지도)") }

                statusMessage?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = Amber)
                }
            }

            SectionCard {
                Text("내 정류소 목록", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                if (state.stops.isEmpty()) {
                    Text(
                        "등록된 정류소가 없습니다",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted,
                    )
                }
                else {
                    state.stops.forEach { stop ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(stop.stopName, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                                Text(stop.routeName, style = MaterialTheme.typography.bodySmall, color = TextMuted)
                            }
                            TextButton(onClick = { onStopsChanged(state.stops - stop) }) {
                                Text("삭제", color = Danger)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }

    stationCandidates?.let { stations ->
        AlertDialog(
            onDismissRequest = { stationCandidates = null },
            title = { Text("정류소를 선택하세요") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    stations.forEach { station ->
                        TextButton(
                            onClick = {
                                stationCandidates = null
                                findRoutes(station)
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                "${station.name} · ${station.distanceMeters.toInt()}m",
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { stationCandidates = null }) { Text("취소") }
            },
        )
    }

    if (showMap) {
        StationMapDialog(
            apiKey = state.apiKey,
            onDismiss = { showMap = false },
            onStationSelected = { station ->
                showMap = false
                findRoutes(station)
            },
        )
    }

    routePicker?.let { picker ->
        AlertDialog(
            onDismissRequest = { routePicker = null },
            title = { Text("노선을 선택하세요") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    picker.routes.forEach { route ->
                        val checked = picker.checked.contains(route.busRouteId)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = { isChecked ->
                                    val updated = if (isChecked) {
                                        picker.checked + route.busRouteId
                                    }
                                    else {
                                        picker.checked - route.busRouteId
                                    }
                                    routePicker = picker.copy(checked = updated)
                                },
                            )
                            Text(route.name, color = TextPrimary)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val newStops = picker.routes
                            .filter { picker.checked.contains(it.busRouteId) }
                            .map { route ->
                                MonitoredStop(
                                    stId = picker.station.stId,
                                    busRouteId = route.busRouteId,
                                    stopName = picker.station.name,
                                    routeName = route.name,
                                )
                            }
                        val merged = (state.stops + newStops).distinctBy { it.stId to it.busRouteId }
                        val addedCount = merged.size - state.stops.size
                        onStopsChanged(merged)
                        statusMessage = if (addedCount > 0) {
                            "${addedCount}개 노선을 추가했습니다"
                        }
                        else {
                            "선택한 노선이 없습니다"
                        }
                        routePicker = null
                    },
                    enabled = picker.checked.isNotEmpty(),
                ) { Text("추가") }
            },
            dismissButton = {
                TextButton(onClick = { routePicker = null }) { Text("취소") }
            },
        )
    }
}

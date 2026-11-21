package com.example.busnowbar

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.busnowbar.data.BusSettingsStore
import com.example.busnowbar.ui.BusNowBarTheme
import com.example.busnowbar.ui.HomeScreen
import com.example.busnowbar.ui.HomeState
import com.example.busnowbar.ui.PermissionState
import com.example.busnowbar.ui.SettingsScreen
import com.example.busnowbar.ui.daysSummaryLabel
import com.example.busnowbar.ui.formatTime
import com.example.busnowbar.util.Permissions
import com.example.busnowbar.work.BusAlarmScheduler
import com.example.busnowbar.work.BusPollingService

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BusNowBarTheme { BusNowBarRoot() }
        }
    }
}

private enum class Screen { Home, Settings }

@Composable
private fun BusNowBarRoot() {
    val context = LocalContext.current
    val settingsStore = remember { BusSettingsStore(context) }

    var screen by remember { mutableStateOf(Screen.Home) }
    var settings by remember { mutableStateOf(settingsStore.load()) }
    var permissionTick by remember { mutableStateOf(0) }

    BackHandler(enabled = screen != Screen.Home) { screen = Screen.Home }

    // 알림/정확한 알람 권한은 설정 앱(Settings)을 거쳐 바뀔 수 있어, 화면이 다시 보일 때마다 새로 읽는다.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                permissionTick++
                settings = settingsStore.load()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val notificationGranted = remember(permissionTick) { Permissions.hasNotificationAccess(context) }
    val exactAlarmGranted = remember(permissionTick) { Permissions.hasExactAlarmAccess(context) }
    val promotedNotificationGranted = remember(permissionTick) { Permissions.hasPromotedNotificationAccess(context) }

    val requestNotificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { permissionTick++ }

    when (screen) {
        Screen.Home -> HomeScreen(
            state = HomeState(
                apiKey = settings.apiKey,
                enabled = settings.enabled,
                isConfigured = settings.isConfigured,
                stops = settings.stops,
                scheduleSummary = settings.schedules
                    .filter { it.isActive }
                    .takeIf { it.isNotEmpty() }
                    ?.joinToString(" / ") { schedule ->
                        val days = daysSummaryLabel(schedule.days)
                        "$days ${formatTime(schedule.startHour, schedule.startMinute)}~${formatTime(schedule.endHour, schedule.endMinute)}"
                    },
                pollIntervalSeconds = settings.pollIntervalSeconds,
            ),
            onOpenSettings = { screen = Screen.Settings },
            onStopsChanged = { updatedStops ->
                val updated = settings.copy(stops = updatedStops)
                settingsStore.save(updated)
                settings = updated
            },
        )

        Screen.Settings -> SettingsScreen(
            initial = settings,
            permissionState = PermissionState(
                notificationGranted = notificationGranted,
                exactAlarmGranted = exactAlarmGranted,
                promotedNotificationGranted = promotedNotificationGranted,
            ),
            onTestPolling = {
                val intent = Intent(context, BusPollingService::class.java)
                    .putExtra(BusPollingService.EXTRA_FORCE, true)
                ContextCompat.startForegroundService(context, intent)
            },
            onRequestPermission = {
                // 한 번에 하나씩 — 설정 화면 2개가 겹쳐 뜨는 걸 피하려고 우선순위대로 처리한다.
                // 사용자가 돌아오면 permissionTick 이 갱신되고, 다시 누르면 다음 항목으로 넘어간다.
                when {
                    !notificationGranted ->
                        requestNotificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                    !exactAlarmGranted ->
                        context.startActivity(Permissions.exactAlarmSettingsIntent(context))
                    !promotedNotificationGranted ->
                        context.startActivity(Permissions.promotedNotificationSettingsIntent(context))
                }
            },
            onSave = { updated ->
                settingsStore.save(updated)
                settings = updated
                // 일정(시작 시각/요일/일시정지)이나 켜짐 여부가 바뀌었을 수 있으니 이미 걸려있던 알람을 다시 예약한다 —
                // 이걸 안 하면 저장해도 예전 설정 그대로 알람이 울리거나(또는 안 꺼진다).
                BusAlarmScheduler.scheduleNextRun(context)
                Toast.makeText(context, "저장했습니다", Toast.LENGTH_SHORT).show()
                screen = Screen.Home
            },
            onClose = { screen = Screen.Home },
        )
    }
}

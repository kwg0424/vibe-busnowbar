package com.example.busnowbar

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.busnowbar.data.BusSettingsStore
import com.example.busnowbar.ui.BusNowBarTheme
import com.example.busnowbar.ui.HomeScreen
import com.example.busnowbar.ui.HomeState
import com.example.busnowbar.ui.daysSummaryLabel
import com.example.busnowbar.ui.formatTime

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BusNowBarTheme { BusNowBarRoot() }
        }
        // API 키 입력칸에 브라우저에서 저장된 자동완성 제안(예: data.go.kr 에서 저장한 값)이
        // 그대로 뜨는 걸 막는다 — 이 앱엔 자동완성이 필요한 입력이 없다.
        findViewById<View>(android.R.id.content).importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
    }
}

@Composable
private fun BusNowBarRoot() {
    val context = LocalContext.current
    val settingsStore = remember { BusSettingsStore(context) }

    var settings by remember { mutableStateOf(settingsStore.load()) }

    // 설정 화면(별도 Activity)에서 바뀐 값은 돌아왔을 때 다시 읽어 반영한다.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                settings = settingsStore.load()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    HomeScreen(
        state = HomeState(
            apiKey = settings.apiKey,
            cartoApiKey = settings.cartoApiKey,
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
        onOpenSettings = { context.startActivity(Intent(context, SettingsActivity::class.java)) },
        onStopsChanged = { updatedStops ->
            val updated = settings.copy(stops = updatedStops)
            settingsStore.save(updated)
            settings = updated
        },
    )
}

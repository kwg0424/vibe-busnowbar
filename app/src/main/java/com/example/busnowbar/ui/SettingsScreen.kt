package com.example.busnowbar.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.busnowbar.data.BusSettings
import java.time.DayOfWeek

private val DAY_ORDER = listOf(
    DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY,
    DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY,
)

private fun dayLabel(day: DayOfWeek): String = when (day) {
    DayOfWeek.MONDAY -> "월"
    DayOfWeek.TUESDAY -> "화"
    DayOfWeek.WEDNESDAY -> "수"
    DayOfWeek.THURSDAY -> "목"
    DayOfWeek.FRIDAY -> "금"
    DayOfWeek.SATURDAY -> "토"
    DayOfWeek.SUNDAY -> "일"
}

private fun formatTime(hour: Int, minute: Int): String = "%02d:%02d".format(hour, minute)

data class PermissionState(
    val notificationGranted: Boolean,
    val exactAlarmGranted: Boolean,
    val promotedNotificationGranted: Boolean,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    initial: BusSettings,
    permissionState: PermissionState,
    onRequestPermission: () -> Unit,
    onTestPolling: () -> Unit,
    onSave: (BusSettings) -> Unit,
    onClose: () -> Unit,
) {
    var enabled by remember { mutableStateOf(initial.enabled) }
    var apiKeyInput by remember { mutableStateOf("") }
    var editingApiKey by remember { mutableStateOf(initial.apiKey.isBlank()) }
    var startHour by remember { mutableStateOf(initial.startHour) }
    var startMinute by remember { mutableStateOf(initial.startMinute) }
    var endHour by remember { mutableStateOf(initial.endHour) }
    var endMinute by remember { mutableStateOf(initial.endMinute) }
    var activeDays by remember { mutableStateOf(initial.activeDays) }
    var pollIntervalSeconds by remember { mutableStateOf(initial.pollIntervalSeconds) }
    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    Scaffold(
        containerColor = Ink,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "설정",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                actions = { TextButton(onClick = onClose) { Text("닫기") } },
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
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // 전체 기능 스위치는 항상 눌릴 수 있어야 하므로 아래 음영 처리 블록 밖에 둔다.
            SectionCard {
                LabeledRow(
                    title = "전체 기능",
                    subtitle = if (enabled) "자동으로 알림을 갱신합니다" else "꺼져 있으면 자동 갱신을 하지 않습니다",
                ) {
                    Switch(checked = enabled, onCheckedChange = { enabled = it })
                }
            }

            Column(
                modifier = Modifier.alpha(if (enabled) 1f else 0.4f),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SectionCard {
                    Text("권한", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                    PermissionRow("알림", permissionState.notificationGranted)
                    PermissionRow("정확한 알람", permissionState.exactAlarmGranted)
                    PermissionRow("Now Bar 승격", permissionState.promotedNotificationGranted)
                    if (!permissionState.promotedNotificationGranted) {
                        Text(
                            "이게 꺼져 있으면 알림은 뜨지만 Now Bar(잠금화면 상단)에는 올라가지 않습니다.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted,
                        )
                    }
                    if (!permissionState.notificationGranted ||
                        !permissionState.exactAlarmGranted ||
                        !permissionState.promotedNotificationGranted
                    ) {
                        Button(
                            onClick = onRequestPermission,
                            enabled = enabled,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("권한 요청") }
                    }
                }

                SectionCard {
                    Text("서울시 버스 API", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                    Text(
                        "data.go.kr에서 발급받은 \"서울시 버스운행정보 공유서비스\" 서비스키(디코딩 키)를 입력하세요.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted,
                    )
                    if (editingApiKey) {
                        OutlinedTextField(
                            value = apiKeyInput,
                            onValueChange = { apiKeyInput = it },
                            label = { Text("API 키") },
                            singleLine = true,
                            enabled = enabled,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    else {
                        LabeledRow(title = "API 키", subtitle = "설정되어 있습니다") {
                            TextButton(
                                enabled = enabled,
                                onClick = {
                                    apiKeyInput = ""
                                    editingApiKey = true
                                },
                            ) { Text("변경") }
                        }
                    }
                }

                SectionCard {
                    Text("알림 요일/시간대", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                    Text(
                        "고른 요일의 이 시간대 동안 ${pollIntervalSeconds}초 간격으로 알림을 갱신합니다.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        DAY_ORDER.forEach { day ->
                            DayChip(
                                label = dayLabel(day),
                                selected = activeDays.contains(day),
                                enabled = enabled,
                                onClick = {
                                    activeDays = if (activeDays.contains(day)) {
                                        activeDays - day
                                    }
                                    else {
                                        activeDays + day
                                    }
                                },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        OutlinedButton(
                            onClick = { showStartPicker = true },
                            enabled = enabled,
                            modifier = Modifier.weight(1f),
                        ) { Text("시작 ${formatTime(startHour, startMinute)}") }
                        OutlinedButton(
                            onClick = { showEndPicker = true },
                            enabled = enabled,
                            modifier = Modifier.weight(1f),
                        ) { Text("종료 ${formatTime(endHour, endMinute)}") }
                    }

                    Text("갱신 주기", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        BusSettings.POLL_INTERVAL_OPTIONS.forEach { seconds ->
                            DayChip(
                                label = "${seconds}초",
                                selected = pollIntervalSeconds == seconds,
                                enabled = enabled,
                                onClick = { pollIntervalSeconds = seconds },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }

            statusMessage?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = Amber)
            }

            OutlinedButton(
                onClick = onTestPolling,
                enabled = initial.isConfigured,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("지금 폴링 테스트") }

            Button(
                onClick = {
                    val resolvedApiKey = if (editingApiKey && apiKeyInput.isNotBlank()) apiKeyInput else initial.apiKey
                    val settings = BusSettings(
                        enabled = enabled,
                        apiKey = resolvedApiKey,
                        stops = initial.stops,
                        startHour = startHour,
                        startMinute = startMinute,
                        endHour = endHour,
                        endMinute = endMinute,
                        activeDays = activeDays,
                        pollIntervalSeconds = pollIntervalSeconds,
                    )
                    if (enabled && !settings.isConfigured) {
                        statusMessage = "API 키를 입력하고 정류소를 1개 이상 추가해야 합니다"
                    }
                    else {
                        onSave(settings)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("저장") }
        }
    }

    if (showStartPicker) {
        TimePickerDialog(
            initialHour = startHour,
            initialMinute = startMinute,
            onDismiss = { showStartPicker = false },
            onConfirm = { hour, minute ->
                startHour = hour
                startMinute = minute
                showStartPicker = false
            },
        )
    }

    if (showEndPicker) {
        TimePickerDialog(
            initialHour = endHour,
            initialMinute = endMinute,
            onDismiss = { showEndPicker = false },
            onConfirm = { hour, minute ->
                endHour = hour
                endMinute = minute
                showEndPicker = false
            },
        )
    }
}

@Composable
private fun PermissionRow(label: String, granted: Boolean) {
    LabeledRow(
        title = label,
        subtitle = if (granted) "허용됨" else "필요함",
    ) {
        Dot(if (granted) Teal else Amber)
    }
}

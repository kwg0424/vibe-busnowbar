package com.example.busnowbar.ui

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.busnowbar.data.AlertSchedule
import com.example.busnowbar.data.BusSettings

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
    var mapsApiKeyInput by remember { mutableStateOf("") }
    var editingMapsApiKey by remember { mutableStateOf(initial.mapsApiKey.isBlank()) }
    var schedules by remember { mutableStateOf(initial.schedules) }
    // 시간 선택 중인 일정(id)과 시작/종료 구분. null 이면 다이얼로그가 닫힌 상태.
    var pickerTarget by remember { mutableStateOf<Pair<Long, Boolean>?>(null) }
    var pollIntervalSeconds by remember { mutableStateOf(initial.pollIntervalSeconds) }
    // 테스트 요청은 서비스 시작만 하고 결과 콜백이 없어서, 요청했다는 걸 보여주려고 잠깐만 돌린다.
    var testing by remember { mutableStateOf(false) }
    LaunchedEffect(testing) {
        if (testing) {
            delay(4000)
            testing = false
        }
    }

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
                    val allGranted = permissionState.notificationGranted &&
                        permissionState.exactAlarmGranted &&
                        permissionState.promotedNotificationGranted
                    Text("권한", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                    if (allGranted) {
                        Text("모두 허용되었습니다", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                    }
                    else {
                        // 아직 허용 안 된 권한만 보여준다.
                        if (!permissionState.notificationGranted) {
                            PermissionRow("알림", false)
                        }
                        if (!permissionState.exactAlarmGranted) {
                            PermissionRow("정확한 알람", false)
                        }
                        if (!permissionState.promotedNotificationGranted) {
                            PermissionRow("Now Bar 승격", false)
                            Text(
                                "이게 꺼져 있으면 알림은 뜨지만 Now Bar(잠금화면 상단)에는 올라가지 않습니다.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextMuted,
                            )
                        }
                        Button(
                            onClick = onRequestPermission,
                            enabled = enabled,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("권한 요청") }
                    }
                }

                SectionCard {
                    Text("API 키", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                    ApiKeyField(
                        title = "서울시 버스",
                        description = "data.go.kr 서울시 버스운행정보 서비스키(디코딩 키)",
                        label = "서울시 버스 API 키",
                        editing = editingApiKey,
                        input = apiKeyInput,
                        enabled = enabled,
                        onInputChange = { apiKeyInput = it },
                        onStartEdit = {
                            apiKeyInput = ""
                            editingApiKey = true
                        },
                    )
                    HorizontalDivider(color = Surface2)
                    ApiKeyField(
                        title = "구글 지도",
                        description = "Maps SDK for Android 키",
                        label = "구글 지도 API 키",
                        editing = editingMapsApiKey,
                        input = mapsApiKeyInput,
                        enabled = enabled,
                        onInputChange = { mapsApiKeyInput = it },
                        onStartEdit = {
                            mapsApiKeyInput = ""
                            editingMapsApiKey = true
                        },
                    )
                }

                OutlinedButton(
                    onClick = {
                        testing = true
                        onTestPolling()
                    },
                    enabled = initial.isConfigured && !testing,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (testing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = Teal,
                        )
                        Spacer(Modifier.width(10.dp))
                        Text("요청 중… 알림을 확인하세요")
                    }
                    else {
                        Text("버스 알림 테스트")
                    }
                }

                SectionCard {
                    Text("알림 일정",style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                    Text(
                        "일정마다 요일과 시간대를 정합니다. 일정 시간대 동안 ${pollIntervalSeconds}초 간격으로 알림을 갱신합니다.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted,
                    )
                    if (schedules.isEmpty()) {
                        Text("등록된 일정이 없습니다", style = MaterialTheme.typography.bodySmall, color = Amber)
                    }
                    schedules.forEachIndexed { index, schedule ->
                        if (index > 0) {
                            HorizontalDivider(color = Surface2)
                        }
                        ScheduleEditor(
                            schedule = schedule,
                            enabled = enabled,
                            onChange = { updated ->
                                schedules = schedules.map { if (it.id == updated.id) updated else it }
                            },
                            onDelete = { schedules = schedules.filter { it.id != schedule.id } },
                            onPickTime = { isStart -> pickerTarget = schedule.id to isStart },
                        )
                    }
                    OutlinedButton(
                        onClick = {
                            val nextId = (schedules.maxOfOrNull { it.id } ?: 0L) + 1
                            schedules = schedules + AlertSchedule(id = nextId)
                        },
                        enabled = enabled,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("+ 일정 추가") }
                }

                SectionCard {
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

            Button(
                onClick = {
                    val resolvedApiKey = if (editingApiKey && apiKeyInput.isNotBlank()) apiKeyInput else initial.apiKey
                    val resolvedMapsApiKey =
                        if (editingMapsApiKey && mapsApiKeyInput.isNotBlank()) mapsApiKeyInput else initial.mapsApiKey
                    val settings = BusSettings(
                        enabled = enabled,
                        apiKey = resolvedApiKey,
                        mapsApiKey = resolvedMapsApiKey,
                        stops = initial.stops,
                        schedules = schedules,
                        pollIntervalSeconds = pollIntervalSeconds,
                    )
                    onSave(settings)
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("저장") }
        }
    }

    pickerTarget?.let { (scheduleId, isStart) ->
        val target = schedules.firstOrNull { it.id == scheduleId }
        if (target == null) {
            pickerTarget = null
        }
        else {
            TimePickerDialog(
                initialHour = if (isStart) target.startHour else target.endHour,
                initialMinute = if (isStart) target.startMinute else target.endMinute,
                onDismiss = { pickerTarget = null },
                onConfirm = { hour, minute ->
                    val updated = if (isStart) {
                        target.copy(startHour = hour, startMinute = minute)
                    }
                    else {
                        target.copy(endHour = hour, endMinute = minute)
                    }
                    schedules = schedules.map { if (it.id == scheduleId) updated else it }
                    pickerTarget = null
                },
            )
        }
    }
}

/**
 * 일정 하나의 편집 UI. 평소엔 요약(시간대/상태)과 일시정지 스위치만 보이고,
 * 항목을 누르면 요일 칩·시작/종료 시각·삭제 버튼이 펼쳐진다.
 */
@Composable
private fun ScheduleEditor(
    schedule: AlertSchedule,
    enabled: Boolean,
    onChange: (AlertSchedule) -> Unit,
    onDelete: () -> Unit,
    onPickTime: (isStart: Boolean) -> Unit,
) {
    var expanded by remember(schedule.id) { mutableStateOf(false) }

    Column(
        modifier = Modifier.alpha(if (schedule.paused) 0.5f else 1f),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = enabled) { expanded = !expanded },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                val daysPrefix = if (schedule.days.isEmpty()) "" else "${daysSummaryLabel(schedule.days)} "
                Text(
                    "$daysPrefix${formatTime(schedule.startHour, schedule.startMinute)} ~ ${formatTime(schedule.endHour, schedule.endMinute)}",
                    style = MaterialTheme.typography.titleSmall,
                    color = TextPrimary,
                )
                Text(
                    when {
                        schedule.paused -> "일시정지됨"
                        schedule.days.isEmpty() -> "요일을 선택하세요"
                        else -> "동작 중"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted,
                )
            }
            if (expanded) {
                TextButton(onClick = onDelete, enabled = enabled) { Text("삭제", color = Danger) }
            }
            else {
                Switch(
                    checked = !schedule.paused,
                    onCheckedChange = { onChange(schedule.copy(paused = !it)) },
                    enabled = enabled,
                )
            }
        }
        if (expanded) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                DAY_ORDER.forEach { day ->
                    DayChip(
                        label = dayLabel(day),
                        selected = schedule.days.contains(day),
                        enabled = enabled,
                        onClick = {
                            val days = if (schedule.days.contains(day)) schedule.days - day else schedule.days + day
                            onChange(schedule.copy(days = days))
                        },
                        modifier = Modifier.weight(1f),
                        day = day,
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(
                    onClick = { onPickTime(true) },
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                ) { Text("시작 ${formatTime(schedule.startHour, schedule.startMinute)}") }
                OutlinedButton(
                    onClick = { onPickTime(false) },
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                ) { Text("종료 ${formatTime(schedule.endHour, schedule.endMinute)}") }
            }
        }
    }
}

/** API 키 하나의 입력 UI — 저장된 키가 있으면 "설정되어 있습니다 [변경]", 변경을 누르면 입력칸. */
@Composable
private fun ApiKeyField(
    title: String,
    description: String,
    label: String,
    editing: Boolean,
    input: String,
    enabled: Boolean,
    onInputChange: (String) -> Unit,
    onStartEdit: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
        Text(description, style = MaterialTheme.typography.bodySmall, color = TextMuted)
        if (editing) {
            OutlinedTextField(
                value = input,
                onValueChange = onInputChange,
                label = { Text(label) },
                singleLine = true,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        else {
            LabeledRow(title = label, subtitle = "설정되어 있습니다") {
                TextButton(enabled = enabled, onClick = onStartEdit) { Text("변경") }
            }
        }
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

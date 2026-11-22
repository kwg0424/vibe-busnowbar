# BusNowBar

출근 시간대에 등록해 둔 정류소의 버스 도착정보를 **삼성 Now Bar(Android 16 Live Updates)** 알림으로 띄우는 안드로이드 앱.

---

## 작업 규칙 (중요 — 토큰 절약)

- **화면 검증을 하지 말 것.** 스크린샷 캡처·`uiautomator dump`·좌표 탭 같은 에뮬레이터 조작은 토큰 소모가 매우 크다.
  코드 수정 → 빌드까지만 하고 끝낸다. **동작 확인은 사용자가 직접 한다.**
- **푸시 금지.** 커밋은 해도 되지만 `git push`는 사용자가 명시적으로 지시할 때만.
- 작업 끝나면 빌드를 돌려서 통과시킨 뒤 보고한다.

## 환경

| 항목 | 값 |
|---|---|
| git | **PATH에 없음.** `D:\work\util\git\cmd`, `D:\work\util\git\bin`, `D:\work\util\git\mingw64\bin` 를 `$env:PATH` 앞에 붙여서 쓴다 |
| 원격 | `https://github.com/kwg0424/vibe-busnowbar.git` (origin, master) |
| 에뮬레이터 | AVD `Galaxy_S25` |
| 셸 | PowerShell 5.1 (`&&`/`??`/삼항 없음. `;` + `if ($?)` 사용) |

GitHub 인증 정보가 저장돼 있지 않아 이 환경에서는 푸시가 불가능하다(브라우저 로그인/프롬프트를 띄울 수 없음).

## 빌드

```powershell
cd D:\work\andriod\busnowbar
.\gradlew.bat assembleDebug testDebugUnitTest lintDebug assembleRelease
```

산출물: `app/build/outputs/apk/release/app-release.apk`

- lint 에러는 빌드를 **중단시킨다**(특히 Compose의 `RememberReturnType` — `remember{}`가 Unit 반환 금지, `SideEffect`나 다른 위치로 옮길 것).
- 릴리스는 `keystore.properties` + `*.jks`로 서명한다(둘 다 gitignore).

## 비밀값

- `local.properties` — 둘 다 gitignore 대상, 빌드 시점에 `BuildConfig`로 주입(현재 앱은 이 값을 자동으로 읽어 쓰지 않는다 — 아래 참고).
  - `BUS_API_KEY` — 서울시 버스 Open API 서비스키 → `BuildConfig.BUS_API_KEY`
  - `CARTO_API_KEY` — CARTO 베이스맵 API 키 → `BuildConfig.CARTO_API_KEY`
- 설정 화면(API 섹션)에서 두 키를 입력한다 — `BusSettingsStore`에 저장되고, **처음 설치하면 둘 다 빈 값**이라 사용자가 직접 넣어야 한다(`BusSettings.apiKey`/`cartoApiKey` 기본값이 `""`). `BuildConfig`의 값으로 자동 채워지지 않는다 — 앱을 배포할 때 개발자 키가 그대로 설치되는 걸 막기 위함.
- `keystore.properties`, `busnowbar-release.jks` — 릴리스 서명

## 아키텍처

단일 `ComponentActivity` + Compose. 화면 전환은 `Screen` enum 상태로만.

```
MainActivity.kt          Screen.Home / Screen.Settings 전환, 설정 저장·알람 재예약
ui/HomeScreen.kt         정류소 추가(GPS·지도) + 내 정류소 목록
ui/SettingsScreen.kt     전체 토글 / 권한 / 알림 일정 / 갱신 주기
ui/StationMapDialog.kt   OpenStreetMap(osmdroid) 지도에서 정류소 고르기
ui/Components.kt         SectionCard, LabeledRow, DayChip, TimePickerDialog, 요일 헬퍼
ui/Theme.kt              snapvault 팔레트(Teal/Ink/Surface1·2/Amber/Danger …)
data/BusSettings.kt      설정 + SharedPreferences 저장소
data/BusApi.kt           ws.bus.go.kr 직접 호출 (XmlPullParser 파싱)
work/BusPollingService.kt  시간대 동안 주기 폴링 → 알림 갱신
work/Notifications.kt      ProgressStyle + Now Bar 승격 요청
work/BusAlarmScheduler.kt  다음 일정 시작 시각에 AlarmManager 예약
util/DailySchedule.kt      일정 계산 (순수 JVM — 유닛테스트 대상)
```

설정 저장 시 `BusAlarmScheduler.scheduleNextRun()`을 **반드시** 호출해야 한다. 빼먹으면 예전 시각으로 알람이 울린다.

## 플랫폼 제약 (재발 방지)

- **AGP 9.x**: `org.jetbrains.kotlin.android` 플러그인을 쓰면 빌드 실패. `kotlin.plugin.compose`만 쓴다.
- **JVM target 17**. 11로 두면 osmdroid 등 17 바이트코드 라이브러리에서 `INLINE_FROM_HIGHER_PLATFORM` 에러.
- **R8 partial shrinking**: `optimization.packageScope`에 `kotlin.**`을 넣지 말 것 — `androidx.startup.InitializationProvider` 초기화 중 `IllegalAccessError`로 크래시한다. `androidx.**`, `kotlinx.**`만.
- **minSdk 36 / targetSdk 37**. `setRequestPromotedOngoing`·`hasPromotableCharacteristics`·`FLAG_PROMOTED_ONGOING`은 **API 36.1**부터라 `Build.VERSION.SDK_INT_FULL >= Build.VERSION_CODES_FULL.BAKLAVA_1` 가드가 필요하다.

## 외부 API 주의사항

### 서울시 버스 (ws.bus.go.kr)
- `getStationByPos`의 `tmX`/`tmY`는 이름과 달리 **TM 좌표가 아니라 WGS84 경도/위도를 그대로** 받는다(실측 확인).
- 응답 필드명은 `stationId`/`stationNm` (문서의 `stId`/`stNm` 아님).
- http만 제공 → `network_security_config.xml`에서 이 호스트에만 평문 허용.

### OpenStreetMap 타일 (osmdroid)
- `tile.openstreetmap.org`(osm.org 메인 서버, `TileSourceFactory.MAPNIK`)는 **가벼운 테스트용도만** 허용하는 정책이라, UA/캐시를 다 맞춰도 앱 트래픽이면 결국 막힌다. 차단 응답은 두 형태 모두 나올 수 있다 — "Access blocked" 이미지가 **HTTP 200**으로 오거나(코드로 구분 불가), 아예 **HTTP 403**으로 막히거나.
  → 그래서 타일 소스를 `StationMapDialog.kt`의 `OsmTileSource`(CARTO Voyager, OSM 데이터 기반이지만 앱 상시 사용이 허용된 별도 CDN)로 교체했다. 메인 OSM 서버로 **되돌리지 말 것**. 저작자 표시("© OpenStreetMap")는 데이터 출처가 OSM이라 소스를 바꿔도 계속 유지해야 한다.
- API 키 불필요. 대신 **타일 사용 정책**을 지켜야 하며, 어기면 타일 대신 "Access blocked" 이미지가 **HTTP 200으로** 내려와 코드로는 실패를 구분할 수 없다.
- User-Agent는 `BusNowBar/<ver> (+저장소 URL)` 형식. 패키지명(`com.example.*`)을 쓰면 "설정 안 한 샘플 앱"으로 차단된다.
- `MapView` 생성 중 osmdroid가 설정을 다시 읽어 UA를 되돌리므로, 생성 **직후에도 한 번 더** UA를 확정한다.
- `DisposableEffect` 키에 `mapView`를 넣지 말 것 — factory에서 값이 채워지는 순간 이펙트가 재시작되며 `onDispose`가 방금 만든 MapView에 `onDetach()`를 걸어 타일 캐시 writer를 닫아버린다(`mWriter being null`).
- 지도 위 "© OpenStreetMap" 저작자 표시는 정책상 필수.

## Now Bar 현황

앱 쪽 구현은 Android 16 Live Updates 요구사항(`POST_PROMOTED_NOTIFICATIONS`, `ProgressStyle`, `setRequestPromotedOngoing`)을 모두 충족한 상태다.
실기기에서 안 뜬다면 삼성 쪽 **설정 > 잠금화면 및 AOD > Now bar** 의 지원 앱 목록(OEM 허용목록) 문제일 가능성이 크다 — 앱에서 제어할 수 있는 공개 API가 없다.
진단은 `BusPollingService`가 남기는 `promotable=... postedPromotedFlag=...` 로그로 한다.

## 아이콘

`busnowbar_icon_round_preview.png`를 픽셀 스캔해 좌표/색을 역산한 벡터다(1024px ↔ 뷰포트 108, 9.4815 px/dp).
깊이 벡터 (+10,−6) → 기울기 −0.6을 옆면·지붕·옆띠·**뒷바퀴**가 모두 따른다. 뒷바퀴는 직사각형이 아니라 평행사변형이다.
`ic_stat_bus.xml`(상태표시줄 아이콘)은 24dp 단색 실루엣이라 런처 아이콘과 별개 자산이다.

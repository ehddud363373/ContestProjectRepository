# 쉼표 (Shimpyo)

졸음쉼터에서 [쪽잠 시작] 버튼 하나만 누르면, 공기·온도·깨우기까지 차가 알아서 챙겨 주는 Pleos Connect 쪽잠 앱.

- 팀명: 눈만감았어
- 패키지: `com.shimpyo.app` · 버전 1.0.0

P단에서 쪽잠 시간(10~30분, 1분 단위, 권장 15~20분)을 정하고 시작하면 차가 바깥 공기로 바꾸고 계절에 맞는 쪽잠 온도를 맞춥니다.
쉬는 동안 내기순환으로 바뀌면 다시 바깥 공기로 되돌리고, 끝나기 3분 전부터 바람·온도·앞유리 김서림 제거·뒷유리 열선으로 몸과 차를 먼저 깨운 뒤 음성으로 깨웁니다.
깨고 나면 "아직 피곤하신가요?"에서 연장 시간을 정해 더 쉬거나(앞서 쉰 시간과 합쳐 최대 30분) [출발]을 고르고, 출발하면 공조를 쪽잠 전 설정으로 되돌립니다.

## 주요 기능

| 기능 | 내용 |
|---|---|
| 쪽잠 시간 정하기 | 10~30분 사이 1분 단위로 직접 정함 (누르고 있으면 빠르게 넘어감, 권장 15~20분 표시) |
| 공기 지키기 | 쉬는 동안 10초마다 차 상태를 다시 읽어, 내기순환으로 바뀌면 바깥 공기로 되돌림. 외기 제어가 안 되는 차는 창문 1~2cm |
| 계절별 온도 | 바깥 온도로 여름·겨울·간절기를 판단해 온도·열선을 맞춤. 듀얼 공조 차는 동승석도 같이 |
| 단계적 깨우기 | 끝나기 3분 전부터 바람·온도·앞유리 김서림·뒷유리 열선 → 음성 → 반응 없으면 경보. 김서림 중 차가 켜는 에어컨은 차에 맡김 |
| 연장·출발 | 합쳐서 30분을 넘지 않게 연장, 출발하면 공조와 앱이 연 창문을 원래대로 되돌리고 한 번 더 읽어 확인 |
| 안전 장치 | P단이 아니거나 문이 열리면 멈춤, 전원 차단 경고·공조가 계속 꺼짐(시동 꺼짐)·비(와이퍼) 때 깨움, 차가 오래 꺼졌다 켜지면 깨우지 않고 공조만 되돌림 |
| 차종별 안내 | 연료 종류를 읽어 내연기관(배기구 확인)·하이브리드(스테이 모드 안내)·전기차별 시작 전 확인을 보여 줌 |

## 실행 방법

### 제출용 APK로 바로 설치
1. Pleos Connect Emulator v2.1 (API 34)를 **Cold Boot**로 실행 (빠른 부팅이면 차량 기능(VHAL)을 쓸 수 없음)
2. Virtual Console › Power System을 **On**으로 (공조가 꺼져 있으면 공조 제어가 거부됨)
3. 설치
   ```
   adb install apk/눈만감았어_쉼표_release.apk
   ```
4. 에뮬레이터 앱 목록에서 **쉼표** 실행

### 소스에서 빌드
- JDK 17, Android Studio(또는 Gradle 8.10.2)
- `./gradlew installDebug`
- release 빌드는 `local.properties`에 서명 정보(`SHIMPYO_KEYSTORE` 등)가 있어야 서명됩니다. 서명 키는 저장소에 넣지 않습니다.

### 시연 모드
- 준비 화면 왼쪽 위 **"쉼표"를 길게 누르면** 1분 = 3초로 줄어듭니다 (15분 쪽잠이 45초). 다시 길게 누르면 꺼집니다.
- 시연 모드에서 상단 차종 칩을 누르면 내연기관(디 올 뉴 아반떼) → 하이브리드(더 뉴 그랜저)로 가정해 볼 수 있습니다 (에뮬레이터 차량은 전기차로 고정).
- release는 실제 시간으로 시작하고, 디버그 빌드는 처음부터 시연 모드로 시작합니다.

## 테스트 환경

- Pleos Connect Emulator v2.1.0, Android 14 (API 34), 화면 2560×1440
- Vehicle SDK 2.3.2, TextToSpeech SDK 3.3.1
- 기어·공조·문 바꾸기: Pleos Connect Virtual Console › Emulation Controls
- 주간/야간 확인: `adb shell cmd uimode night yes` / `adb shell cmd uimode night no`
- 단위 테스트: `./gradlew testDebugUnitTest` (쪽잠 규칙·공조 계획·김서림·비·시동 꺼짐·전체 흐름 50개)

## APK 위치

`apk/눈만감았어_쉼표_release.apk` (release 서명, R8 최적화)

## 오픈소스 라이선스

| 라이브러리 | 라이선스 |
|---|---|
| AndroidX (Core, Lifecycle, Activity, Compose, Material3) | Apache License 2.0 |
| Kotlin, kotlinx.coroutines | Apache License 2.0 |
| JUnit 4 (테스트) | Eclipse Public License 1.0 |
| Pleos Vehicle SDK, Pleos TextToSpeech SDK | Pleos Playground 이용 약관 |

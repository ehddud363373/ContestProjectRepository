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
| 연장·출발 | "아직 피곤하신가요?"에서 1분 단위(최소 5분)로 연장, 여러 번 가능하지만 앞서 쉰 시간과 합쳐 30분까지. 출발하면 공조와 앱이 연 창문을 쪽잠 전 설정으로 되돌리고 한 번 더 읽어 확인 |
| 안전 장치 | P단·정차일 때만 시작, 쉬는 중 기어가 P에서 벗어나면 바로 끝내고 공조 복원. 운전석 문이 열리면 깬 것으로 보고 확인 화면으로. 전원 차단 경고·공조가 계속 꺼짐(시동 꺼짐) 때 깨움. 차가 꺼졌다 켜지면 이어서 깨우고, 깨울 시각이 30분 넘게 지났으면 깨우지 않고 공조·창문만 되돌림 |
| 차종별 안내 | 연료 종류를 읽어 내연기관(배기구 확인)·하이브리드(스테이 모드 안내)·전기차별 시작 전 확인을 보여 줌 |

## 실행 방법

### 제출용 APK로 바로 설치
1. Pleos Connect Emulator v2.1 (API 34)를 **처음부터 부팅(Cold Boot)** 되게 켭니다. 아래 방법 중 아무거나 쓰면 됩니다.
   - **Device Manager ▶ (실행 버튼)**: 에뮬레이터 설정의 부팅 옵션이 Cold boot이면 ▶만 눌러도 됩니다.
     설정 방법: Device Manager › 에뮬레이터 ⋮ › Edit › Show Advanced Settings(More settings) › Boot option = **Cold boot**
   - **Device Manager ⋮ › Cold Boot Now**: 설정을 바꾸지 않고 이번 한 번만 처음부터 켭니다.
   - **Android Studio ▶ Run**: 실행 대상 기기로 Pleos Connect Emulator를 고르고 Run (부팅 방식은 위 Boot option을 따릅니다).
   - **명령어**: `emulator -avd <AVD 이름> -no-snapshot-load`
   - 주의: 부팅 옵션이 Quick boot인 상태에서 ▶를 누르면 저장해 둔 상태에서 이어 켜집니다. 이때는 가상 차량(VHAL)과의 연결이 다시 맺어지지 않아 공조·기어 같은 차량 값을 읽거나 바꿀 수 없습니다. 차량 값이 바뀌지 않으면 Cold Boot Now로 다시 켜 주세요.
2. Virtual Console › Power System을 **On**으로 바꿉니다. (차 전원이 꺼져 있으면 공조 제어가 거부됩니다)
3. APK를 설치합니다. 둘 중 하나:
   - `apk/눈만감았어_쉼표_release.apk`를 내려받아 에뮬레이터 화면에 **끌어다 놓기**
   - 또는 `adb install apk/눈만감았어_쉼표_release.apk`
4. 하단바 앱 목록(⊞)에서 **쉼표**(주황 배경의 흰 쉼표 아이콘)를 실행합니다.

### 빠르게 확인해 보기 (약 1분)
1. 준비 화면 왼쪽 위 **"쉼표"를 길게 눌러** 시연 모드를 켭니다 (1분 = 3초).
2. 기어가 P인지 확인하고 [쪽잠 시작]을 누릅니다. 기본 15분 → 45초.
3. Virtual Console에서 내기순환(Recirculation)을 켜 보면 앱이 바로 바깥 공기로 되돌리고 화면에 기록합니다.
4. 끝나기 3분 전(시연 모드 9초 전) 바람·온도·김서림 제거가 바뀌고, 시간이 되면 깨우기 화면이 나옵니다.
5. [일어났어요] → "아직 피곤하신가요?"에서 연장하거나 [괜찮아요·출발]을 누르면 공조가 쪽잠 전 설정으로 돌아갑니다.

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
- 단위 테스트: `./gradlew testDebugUnitTest` (쪽잠 규칙·공조 계획·김서림·시동 꺼짐·전체 흐름 50개)

## APK 위치

`apk/눈만감았어_쉼표_release.apk` (release 서명, R8 최적화)

## 오픈소스 라이선스

| 라이브러리 | 라이선스 |
|---|---|
| AndroidX (Core, Lifecycle, Activity, Compose, Material3) | Apache License 2.0 |
| Kotlin, kotlinx.coroutines | Apache License 2.0 |
| JUnit 4 (테스트) | Eclipse Public License 1.0 |
| Pleos Vehicle SDK, Pleos TextToSpeech SDK | Pleos Playground 이용 약관 |

package com.contestproject.app.domain

/** 쪽잠 규칙. 기획서 F1·F5·F6의 숫자를 한곳에 모았다. */
object NapPolicy {
    val DURATION_OPTIONS_MIN = listOf(15, 20)
    const val DEFAULT_DURATION_MIN = 15
    const val MAX_DURATION_MIN = 20
    const val EXTENSION_MIN = 10
    const val PRE_WAKE_LEAD_MIN = 3

    const val VOICE_REPEAT_SEC = 30
    /** 이만큼 반복해도 반응이 없으면 경보 화면으로 올린다. */
    const val VOICE_REPEATS_BEFORE_ALERT = 6
    const val ALERT_SNOOZE_SEC = 60

    const val MINUTE_MS = 60_000L
    /** 시연 영상용: 1분을 4초로 줄여 15분 쪽잠을 1분에 보여 준다. 디버그 빌드에서만 켤 수 있다. */
    const val DEMO_MINUTE_MS = 4_000L
}

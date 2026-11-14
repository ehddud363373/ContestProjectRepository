package com.contestproject.app.domain

/** 쪽잠 규칙. 기획서 F1·F5·F6의 숫자를 한곳에 모았다. */
object NapPolicy {
    /**
     * 쪽잠 길이(분). 사용자가 MIN~MAX 사이에서 STEP 단위로 고른다.
     * - 10분: 5분은 효과가 거의 없고 10분이 깬 직후 효과가 가장 컸다(Brooks & Lack, 2006, Sleep).
     * - 30분: 넘기면 깊은 잠에 들어 깬 뒤 한동안 멍하다(수면 관성) → 바로 운전하기 위험하다.
     * - 권장 15~20분: 미국흉부학회·한국도로공사 권고.
     * MAX는 연장까지 합친 총 쉰 시간의 상한이기도 하다.
     */
    const val MIN_DURATION_MIN = 10
    const val MAX_DURATION_MIN = 30
    const val DURATION_STEP_MIN = 5
    const val DEFAULT_DURATION_MIN = 15
    val RECOMMENDED_DURATION_MIN = 15..20
    /** 이 길이 이상이면 시작 전에 "깬 뒤 잠시 멍할 수 있어요"를 보여 준다. */
    const val LONG_NAP_FROM_MIN = 25

    /** 연장 길이(분). 앞서 쉰 시간과 합쳐 MAX_DURATION_MIN을 넘지 않는 안에서 STEP 단위로 고른다. */
    const val MIN_EXTENSION_MIN = 5
    const val EXTENSION_STEP_MIN = 5
    const val DEFAULT_EXTENSION_MIN = 5

    fun isValidDuration(minutes: Int): Boolean =
        minutes in MIN_DURATION_MIN..MAX_DURATION_MIN && (minutes - MIN_DURATION_MIN) % DURATION_STEP_MIN == 0

    const val PRE_WAKE_LEAD_MIN = 3

    /**
     * 차가 꺼졌다 켜졌을 때, 깨울 시각이 이만큼 넘게 지났으면 이어서 깨우지 않고 끝난 쪽잠으로 정리한다.
     * 그 사이 차를 켠 건 이미 깬 사람이고, 꺼진 동안엔 어차피 깨울 수 없었다.
     * 짧은 재부팅(몇 분)은 이어서 깨운다.
     */
    const val STALE_AFTER_END_MIN = 30

    const val VOICE_REPEAT_SEC = 30
    /** 이만큼 반복해도 반응이 없으면 경보 화면으로 올린다. */
    const val VOICE_REPEATS_BEFORE_ALERT = 6
    const val ALERT_SNOOZE_SEC = 60

    const val MINUTE_MS = 60_000L
    /** 시연 영상용: 1분을 4초로 줄여 15분 쪽잠을 1분에 보여 준다. 디버그 빌드에서만 켤 수 있다. */
    const val DEMO_MINUTE_MS = 4_000L
}

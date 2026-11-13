package com.contestproject.app.data.voice

/** 깨우기 음성 + 알림음. 구현을 바꿔도(Pleos TTS ↔ Android TTS) 나머지 코드는 그대로다. */
interface WakeVoice {
    fun prepare()
    /** level 0..n: 반복될수록 알림음을 키운다. */
    fun announce(text: String, level: Int = 0)
    fun stop()
    fun release()
}

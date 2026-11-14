package com.contestproject.app.data.voice

import android.content.res.Resources
import com.contestproject.app.R
import com.contestproject.app.domain.AlertReason

/** 음성 문구. 컨트롤러가 Android 리소스를 직접 몰라도 되게(단위 테스트 가능하게) 분리했다. */
interface VoiceLines {
    fun start(minutes: Int): String
    fun extend(minutes: Int): String
    fun wake(minutes: Int): String
    fun wakeRepeat(): String
    fun alert(reason: AlertReason): String
}

class ResourceVoiceLines(private val res: Resources) : VoiceLines {
    override fun start(minutes: Int) = res.getString(R.string.voice_start, minutes)
    override fun extend(minutes: Int) = res.getString(R.string.voice_extend, minutes)
    override fun wake(minutes: Int) = res.getString(R.string.voice_wake, minutes)
    override fun wakeRepeat() = res.getString(R.string.voice_wake_repeat)
    override fun alert(reason: AlertReason) = res.getString(
        when (reason) {
            AlertReason.VEHICLE_WARNING -> R.string.voice_alert_vehicle
            AlertReason.FRESH_AIR_LOST -> R.string.voice_alert_air
            AlertReason.NO_RESPONSE -> R.string.voice_alert_no_response
            AlertReason.CLIMATE_OFF -> R.string.voice_alert_climate_off
            AlertReason.RAIN -> R.string.voice_alert_rain
        }
    )
}

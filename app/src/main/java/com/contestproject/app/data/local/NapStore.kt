package com.contestproject.app.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.contestproject.app.BuildConfig
import com.contestproject.app.data.vehicle.ClimateSnapshot
import com.contestproject.app.domain.NapPolicy
import com.contestproject.app.domain.NapRecord
import com.contestproject.app.domain.NapSession

/**
 * 진행 중인 쪽잠과 시작 전 공조 상태를 기기에 저장한다.
 * 전원이 꺼졌다 켜지거나 앱 프로세스가 정리돼도 이어서 깨우고 원래대로 되돌리기 위해서다.
 * 값이 몇 개뿐이라 Room·DataStore 대신 SharedPreferences로 충분하다.
 */
interface NapStore {
    fun save(session: NapSession, snapshot: ClimateSnapshot)
    fun updateSession(session: NapSession)
    fun load(): Pair<NapSession, ClimateSnapshot>?
    fun clearSession()
    /** 쪽잠 기록(되돌림·점검 횟수 등). 세션과 함께 지워진다. */
    fun saveRecord(record: NapRecord)
    fun loadRecord(): NapRecord?
    var demoMode: Boolean
    var lastDurationMin: Int
    /** 시연 모드에서만 쓰는 동력원 가정(에뮬레이터가 전기차로 고정돼 있어서). null이면 차에서 읽은 값. */
    var powertrainOverride: String?
}

class PrefsNapStore(context: Context) : NapStore {

    private val prefs = context.applicationContext.getSharedPreferences("nap", Context.MODE_PRIVATE)

    override fun save(session: NapSession, snapshot: ClimateSnapshot) = prefs.edit {
        putSession(session)
        putBool(K_S_POWER, snapshot.hvacPower)
        putBool(K_S_RECIRC, snapshot.recirculation)
        putBool(K_S_AC, snapshot.ac)
        snapshot.temperatureC?.let { putFloat(K_S_TEMP, it) } ?: remove(K_S_TEMP)
        putInt(K_S_FAN, snapshot.fanSpeed)
        putBool(K_S_DEFROST, snapshot.defroster)
        putBool(K_S_REAR_DEFROST, snapshot.rearDefroster)
        snapshot.passengerTemperatureC?.let { putFloat(K_S_TEMP2, it) } ?: remove(K_S_TEMP2)
        putBool(K_S_DUAL, snapshot.dualOn)
        putInt(K_S_SEAT, snapshot.seatHeat)
        putInt(K_S_STEER, snapshot.steeringHeat)
        putInt(K_S_WINDOW, snapshot.driverWindow)
    }

    override fun updateSession(session: NapSession) = prefs.edit { putSession(session) }

    override fun saveRecord(record: NapRecord) = prefs.edit { putString(K_RECORD, record.encode()) }

    override fun loadRecord(): NapRecord? = NapRecord.decode(prefs.getString(K_RECORD, null))

    override fun load(): Pair<NapSession, ClimateSnapshot>? {
        if (!prefs.contains(K_END)) return null
        val session = NapSession(
            startedAtMs = prefs.getLong(K_START, 0),
            endAtMs = prefs.getLong(K_END, 0),
            plannedMinutes = prefs.getInt(K_PLANNED, NapPolicy.DEFAULT_DURATION_MIN),
            extensionUsed = prefs.getBoolean(K_EXTENDED, false),
            minuteMs = prefs.getLong(K_MINUTE, NapPolicy.MINUTE_MS),
            priorRestMinutes = prefs.getInt(K_PRIOR_REST, 0),
        )
        val snapshot = ClimateSnapshot(
            hvacPower = bool(K_S_POWER),
            recirculation = bool(K_S_RECIRC),
            ac = bool(K_S_AC),
            temperatureC = if (prefs.contains(K_S_TEMP)) prefs.getFloat(K_S_TEMP, 0f) else null,
            fanSpeed = int(K_S_FAN),
            defroster = bool(K_S_DEFROST),
            rearDefroster = bool(K_S_REAR_DEFROST),
            passengerTemperatureC = if (prefs.contains(K_S_TEMP2)) prefs.getFloat(K_S_TEMP2, 0f) else null,
            dualOn = bool(K_S_DUAL),
            seatHeat = int(K_S_SEAT),
            steeringHeat = int(K_S_STEER),
            driverWindow = int(K_S_WINDOW),
        )
        return session to snapshot
    }

    /** 세션 관련 값만 지운다. 사용자 설정(마지막 시간, 시연 모드)은 남긴다. */
    override fun clearSession() = prefs.edit {
        SESSION_KEYS.forEach { remove(it) }
    }

    override var demoMode: Boolean
        // 출시 전 개발 중에는 디버그 빌드를 시연 모드로 시작한다(릴리스는 기존처럼 꺼진 채 시작).
        get() = prefs.getBoolean(K_DEMO, BuildConfig.DEBUG)
        set(value) = prefs.edit { putBoolean(K_DEMO, value) }

    override var lastDurationMin: Int
        get() = prefs.getInt(K_LAST_DURATION, NapPolicy.DEFAULT_DURATION_MIN)
            .takeIf { NapPolicy.isValidDuration(it) } ?: NapPolicy.DEFAULT_DURATION_MIN
        set(value) = prefs.edit { putInt(K_LAST_DURATION, value) }

    override var powertrainOverride: String?
        get() = prefs.getString(K_POWERTRAIN_OVERRIDE, null)
        set(value) = prefs.edit { if (value == null) remove(K_POWERTRAIN_OVERRIDE) else putString(K_POWERTRAIN_OVERRIDE, value) }

    private fun SharedPreferences.Editor.putSession(session: NapSession) {
        putLong(K_START, session.startedAtMs)
        putLong(K_END, session.endAtMs)
        putInt(K_PLANNED, session.plannedMinutes)
        putBoolean(K_EXTENDED, session.extensionUsed)
        putLong(K_MINUTE, session.minuteMs)
        putInt(K_PRIOR_REST, session.priorRestMinutes)
    }

    private fun SharedPreferences.Editor.putBool(key: String, value: Boolean?) {
        if (value == null) remove(key) else putBoolean(key, value)
    }

    private fun SharedPreferences.Editor.putInt(key: String, value: Int?) {
        if (value == null) remove(key) else putInt(key, value)
    }

    private fun bool(key: String): Boolean? = if (prefs.contains(key)) prefs.getBoolean(key, false) else null
    private fun int(key: String): Int? = if (prefs.contains(key)) prefs.getInt(key, 0) else null

    private companion object {
        const val K_START = "start"
        const val K_END = "end"
        const val K_PLANNED = "planned"
        const val K_EXTENDED = "extended"
        const val K_MINUTE = "minute_ms"
        const val K_PRIOR_REST = "prior_rest"
        const val K_RECORD = "record"
        const val K_S_POWER = "s_power"
        const val K_S_RECIRC = "s_recirc"
        const val K_S_AC = "s_ac"
        const val K_S_TEMP = "s_temp"
        const val K_S_FAN = "s_fan"
        const val K_S_DEFROST = "s_defrost"
        const val K_S_SEAT = "s_seat"
        const val K_S_STEER = "s_steer"
        const val K_S_WINDOW = "s_window"
        const val K_S_REAR_DEFROST = "s_rear_defrost"
        const val K_S_TEMP2 = "s_temp_passenger"
        const val K_S_DUAL = "s_dual"
        const val K_DEMO = "demo_mode"
        const val K_LAST_DURATION = "last_duration"
        const val K_POWERTRAIN_OVERRIDE = "powertrain_override"

        val SESSION_KEYS = listOf(
            K_START, K_END, K_PLANNED, K_EXTENDED, K_MINUTE, K_PRIOR_REST, K_RECORD,
            K_S_POWER, K_S_RECIRC, K_S_AC, K_S_TEMP, K_S_FAN, K_S_DEFROST, K_S_SEAT, K_S_STEER, K_S_WINDOW,
            K_S_REAR_DEFROST, K_S_TEMP2, K_S_DUAL,
        )
    }
}

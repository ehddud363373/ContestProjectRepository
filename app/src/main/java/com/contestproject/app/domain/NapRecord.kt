package com.contestproject.app.domain

import com.contestproject.app.data.vehicle.ClimateSnapshot

/** 쪽잠 중 앱이 실제로 한 일. 출발 전 리포트와 타임라인은 이 기록만으로 그린다. */
data class NapEvent(val atMs: Long, val kind: Kind, val item: ClimateItem? = null) {
    enum class Kind {
        STARTED,
        /** 내기순환으로 바뀐 것을 바깥 공기로 되돌림 */
        AIR_REVERTED,
        /** 외기 전환이 안 돼 창문 틈으로 대신함 */
        WINDOW_OPENED,
        /** 점검에서 앱이 정한 값과 달라 다시 맞춤 */
        CORRECTED,
        PRE_WAKE,
        WAKE_CALL,
        EXTENDED,
        AWAKE,
    }
}

enum class ClimateItem { POWER, AIR, AC, TEMPERATURE, FAN, DEFROST, SEAT_HEAT, WINDOW }

/**
 * 쪽잠 한 번(연장 포함)의 누적 기록. 차가 꺼졌다 켜져도 리포트가 이어지도록 저장한다.
 * @param checks 차 값을 다시 읽어 확인한 횟수
 * @param freshAirOkChecks 그중 바깥 공기(또는 창문 틈)가 유지되고 있던 횟수
 */
data class NapRecord(
    val events: List<NapEvent> = emptyList(),
    val checks: Int = 0,
    val freshAirOkChecks: Int = 0,
    val defrostStartedAtMs: Long? = null,
    val defrostEndedAtMs: Long? = null,
) {
    fun with(event: NapEvent) = copy(events = (events + event).takeLast(MAX_EVENTS))

    fun count(kind: NapEvent.Kind) = events.count { it.kind == kind }

    val startedAtMs: Long? get() = events.firstOrNull { it.kind == NapEvent.Kind.STARTED }?.atMs

    /** 마지막 깨우기 묶음에서 몇 번 불렀는지 (일어나기 전에 깬 경우 0) */
    val wakeCallsLastRound: Int
        get() {
            val lastBoundary = events.indexOfLast { it.kind == NapEvent.Kind.STARTED || it.kind == NapEvent.Kind.EXTENDED }
            return events.drop(lastBoundary + 1).count { it.kind == NapEvent.Kind.WAKE_CALL }
        }

    fun defrostRunMs(nowMs: Long): Long? = defrostStartedAtMs?.let { (defrostEndedAtMs ?: nowMs) - it }

    /** SharedPreferences 한 칸에 넣기 위한 간단한 문자열. 형식이 바뀌어도 읽을 수 없는 항목은 건너뛴다. */
    fun encode(): String = buildString {
        append(checks).append('|').append(freshAirOkChecks).append('|')
        append(defrostStartedAtMs ?: "").append('|').append(defrostEndedAtMs ?: "").append('|')
        append(events.joinToString(";") { "${it.atMs},${it.kind.name},${it.item?.name ?: ""}" })
    }

    companion object {
        const val MAX_EVENTS = 60

        fun decode(text: String?): NapRecord? {
            if (text.isNullOrEmpty()) return null
            val parts = text.split('|')
            if (parts.size < 5) return null
            val events = parts[4].split(';').filter { it.isNotEmpty() }.mapNotNull { raw ->
                val f = raw.split(',')
                if (f.size < 3) return@mapNotNull null
                val at = f[0].toLongOrNull() ?: return@mapNotNull null
                val kind = runCatching { NapEvent.Kind.valueOf(f[1]) }.getOrNull() ?: return@mapNotNull null
                NapEvent(at, kind, f[2].takeIf { it.isNotEmpty() }?.let { runCatching { ClimateItem.valueOf(it) }.getOrNull() })
            }
            return NapRecord(
                events = events,
                checks = parts[0].toIntOrNull() ?: 0,
                freshAirOkChecks = parts[1].toIntOrNull() ?: 0,
                defrostStartedAtMs = parts[2].toLongOrNull(),
                defrostEndedAtMs = parts[3].toLongOrNull(),
            )
        }
    }
}

/** 화면에 보여 줄 '지금 차 상태'. reading은 차에서 다시 읽은 값, target은 앱이 지키려는 값. */
data class NapMonitor(
    val reading: ClimateSnapshot? = null,
    val cabinTempC: Float? = null,
    val readAtMs: Long? = null,
    val target: ClimateSnapshot = ClimateSnapshot(),
    val original: ClimateSnapshot = ClimateSnapshot(),
    /** 기상 연출 때 바꿀 값(화면의 "다음" 줄) */
    val wakePlan: ClimateSnapshot = ClimateSnapshot(),
    val record: NapRecord = NapRecord(),
)

/** 앱이 정한 값과 차에서 읽은 값을 비교한다. 읽지 못한 값(null)이나 앱이 신경 쓰지 않는 값은 비교하지 않는다. */
object ClimateCheck {
    private const val TEMP_TOLERANCE_C = 0.25f

    fun mismatches(target: ClimateSnapshot, reading: ClimateSnapshot): List<ClimateItem> = buildList {
        fun <T> differs(want: T?, got: T?) = want != null && got != null && want != got
        if (differs(target.hvacPower, reading.hvacPower)) add(ClimateItem.POWER)
        if (differs(target.recirculation, reading.recirculation)) add(ClimateItem.AIR)
        if (differs(target.ac, reading.ac)) add(ClimateItem.AC)
        val t = target.temperatureC
        val r = reading.temperatureC
        if (t != null && r != null && kotlin.math.abs(t - r) > TEMP_TOLERANCE_C) add(ClimateItem.TEMPERATURE)
        if (differs(target.fanSpeed, reading.fanSpeed)) add(ClimateItem.FAN)
        if (differs(target.defroster, reading.defroster)) add(ClimateItem.DEFROST)
        if (differs(target.seatHeat, reading.seatHeat)) add(ClimateItem.SEAT_HEAT)
        // 창문은 '틈이 열려 있는지'만 본다(차마다 단계가 달라 정확한 값 비교는 하지 않는다).
        val w = target.driverWindow
        val rw = reading.driverWindow
        if (w != null && rw != null && w > 0 && rw <= 0) add(ClimateItem.WINDOW)
    }

    /** 바깥 공기가 들어오고 있는지. 알 수 없으면 null */
    fun freshAirOk(mode: FreshAirMode, reading: ClimateSnapshot): Boolean? = when (mode) {
        FreshAirMode.OUTSIDE_AIR -> reading.recirculation?.not()
        FreshAirMode.WINDOW_GAP -> reading.driverWindow?.let { it > 0 }
        FreshAirMode.NONE -> null
    }
}

package com.contestproject.app.domain

/** 쪽잠 중 바깥 공기를 어떻게 지키고 있는지 */
enum class FreshAirMode { OUTSIDE_AIR, WINDOW_GAP, NONE }

data class RestClimate(
    val freshAir: FreshAirMode,
    val temperatureC: Float?,
    val fanLevel: Int?,
    val seatHeatOn: Boolean,
)

enum class WakeStepKind { FAN_UP, TEMPERATURE, DEFROST, STEERING_HEAT, VOICE }
enum class StepStatus { PENDING, RUNNING, DONE, SKIPPED }

data class WakeStep(
    val kind: WakeStepKind,
    val status: StepStatus,
    val from: Float? = null,
    val to: Float? = null,
    /** 차가 '적용됨'이라고 답한 시각 */
    val doneAtMs: Long? = null,
)

/**
 * 경보 이유.
 * CLIMATE_OFF: 공조가 꺼졌는데 다시 켜지지 않음(내연기관은 시동이 꺼지면 이렇게 된다. 엔진 상태는 공개 API로 읽을 수 없다).
 * RAIN: 창문 틈으로 바깥 공기를 넣던 중 와이퍼가 움직여(비) 창문을 닫음 → 바깥 공기가 끊김.
 */
enum class AlertReason { VEHICLE_WARNING, FRESH_AIR_LOST, NO_RESPONSE, CLIMATE_OFF, RAIN }

sealed interface NapState {
    data object Idle : NapState

    sealed interface Active : NapState {
        val session: NapSession
    }

    data class Resting(
        override val session: NapSession,
        val climate: RestClimate,
    ) : Active

    data class PreWake(
        override val session: NapSession,
        val climate: RestClimate,
        val steps: List<WakeStep>,
    ) : Active

    data class Waking(
        override val session: NapSession,
        val repeats: Int,
        val freshAir: FreshAirMode,
        /** 마지막으로 부른 시각과 다음 부르기까지 간격(화면의 '다음 부르기까지 n초') */
        val lastCallAtMs: Long,
        val callIntervalMs: Long,
    ) : Active

    data class DepartCheck(
        override val session: NapSession,
        val restedMinutes: Int,
        /** 깬 시각. 연장 상한은 이 시각까지 쉰 시간으로 센다. */
        val awakeAtMs: Long,
        /** 지금 연장할 수 있는 최대 분. 0이면 연장 불가(최대 시간에 닿았거나 차량 경고 뒤). */
        val maxExtensionMin: Int,
    ) : Active

    data class Alert(
        override val session: NapSession,
        val reason: AlertReason,
        val snoozedUntilMs: Long?,
    ) : Active
}

/** 화면 위에 잠깐 띄우는 안내 */
enum class NapNotice { ENDED_BY_GEAR, RESUMED_AFTER_RESTART, ENDED_WHILE_OFF, START_FAILED, DEPARTED }
